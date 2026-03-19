import json
import time
from typing import Any, Dict, Optional

import numpy as np
import structlog

from app.core.config import settings
from app.models.fraud_model import FraudModel
from app.models.schemas import FraudScoreRequest, FraudScoreResponse
from app.services.feature_engineering import FeatureEngineer

logger = structlog.get_logger(__name__)

# Recommendation constants
_REC_BLOCK: str = "BLOCK_TRANSACTION"
_REC_REVIEW: str = "MANUAL_REVIEW"
_REC_VERIFY: str = "ADDITIONAL_VERIFICATION"
_REC_APPROVE: str = "APPROVE"


class ScoringService:
    """Orchestrates feature extraction, model inference and result publishing.

    A module-level singleton instance *scoring_service* is exported at the
    bottom of this file and shared across the entire application.
    """

    def __init__(self) -> None:
        self.model: FraudModel = FraudModel()
        self.feature_engineer: FeatureEngineer = FeatureEngineer()
        self._kafka_producer: Optional[Any] = None  # lazy-initialised

    # ------------------------------------------------------------------
    # Kafka producer (lazy init so app starts even without a broker)
    # ------------------------------------------------------------------

    def _get_kafka_producer(self) -> Optional[Any]:
        """Return a shared KafkaProducer, initialising it on first call."""
        if self._kafka_producer is not None:
            return self._kafka_producer

        try:
            from kafka import KafkaProducer  # type: ignore[import-untyped]

            self._kafka_producer = KafkaProducer(
                bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
                value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                acks="all",
                retries=3,
            )
            logger.info(
                "KafkaProducer initialised",
                bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            )
        except Exception as exc:
            logger.warning(
                "KafkaProducer could not be initialised — alert publishing disabled",
                error=str(exc),
            )
            self._kafka_producer = None

        return self._kafka_producer

    # ------------------------------------------------------------------
    # Core scoring logic
    # ------------------------------------------------------------------

    def score_transaction(self, request: FraudScoreRequest) -> FraudScoreResponse:
        """Score a single transaction and return a FraudScoreResponse.

        Pipeline
        --------
        1. Record wall-clock start time.
        2. Extract raw feature vector via FeatureEngineer.
        3. Scale features using model.scaler (StandardScaler).
        4. Obtain (score, risk_level) from FraudModel.predict().
        5. Derive triggered rules via FeatureEngineer.
        6. Build recommendation string.
        7. Determine shouldBlock flag.
        8. Compute processing latency.
        9. Return FraudScoreResponse.
        """
        start: float = time.perf_counter()

        # Step 2 — raw features
        raw_features: np.ndarray = self.feature_engineer.extract_features(request)

        # Step 3 — scale features
        if self.model.scaler is None:
            raise RuntimeError("Model scaler is not available — was load() called?")
        scaled_features: np.ndarray = (
            self.model.scaler.transform(raw_features.reshape(1, -1)).flatten()
        )

        # Step 4 — model prediction (takes pre-scaled features)
        score, risk_level = self.model.predict(scaled_features)

        # Step 5 — rule evaluation
        triggered_rules = self.feature_engineer.get_triggered_rules(request, score)

        # Step 7 — block flag
        should_block: bool = score >= settings.FRAUD_SCORE_THRESHOLD_BLOCK

        # Step 6 — recommendation
        if should_block:
            recommendation: str = _REC_BLOCK
        elif risk_level == "HIGH":
            recommendation = _REC_REVIEW
        elif risk_level == "MEDIUM":
            recommendation = _REC_VERIFY
        else:
            recommendation = _REC_APPROVE

        # Step 8 — latency
        processing_time_ms: float = (time.perf_counter() - start) * 1_000.0

        response = FraudScoreResponse(
            transactionId=request.transactionId,
            score=round(score, 6),
            riskLevel=risk_level,
            triggeredRules=triggered_rules,
            recommendation=recommendation,
            shouldBlock=should_block,
            processingTimeMs=round(processing_time_ms, 3),
        )

        logger.info(
            "Transaction scored",
            transactionId=request.transactionId,
            tenantId=request.tenantId,
            score=score,
            riskLevel=risk_level,
            shouldBlock=should_block,
            processingTimeMs=processing_time_ms,
        )

        return response

    # ------------------------------------------------------------------
    # Async Kafka event handler
    # ------------------------------------------------------------------

    def score_transaction_async(self, event: Dict[str, Any]) -> None:
        """Deserialise a Kafka event and score it for monitoring purposes.

        The REST API path in transaction-service is the authoritative fraud
        decision maker.  This async consumer scores independently for model
        monitoring and drift detection — it intentionally does NOT publish
        to 'fraud.alert' to avoid duplicate alerts.

        This method is called from the Kafka consumer daemon thread.
        All exceptions are caught and logged so a single bad message does
        not crash the consumer loop.
        """
        try:
            request = FraudScoreRequest(**event)
        except Exception as exc:
            logger.error(
                "Failed to deserialise Kafka event into FraudScoreRequest",
                error=str(exc),
                event=event,
            )
            return

        try:
            result: FraudScoreResponse = self.score_transaction(request)
        except Exception as exc:
            logger.error(
                "score_transaction raised an error for Kafka event",
                error=str(exc),
                transactionId=event.get("transactionId"),
            )
            return

        # Log high-risk scores for model monitoring — no Kafka publish to avoid
        # duplicate fraud.alert events (the REST path already handles this).
        if result.score >= settings.FRAUD_SCORE_THRESHOLD_HOLD:
            logger.info(
                "Async fraud score (monitoring only — not published)",
                transactionId=result.transactionId,
                score=result.score,
                riskLevel=result.riskLevel,
                shouldBlock=result.shouldBlock,
            )


# ---------------------------------------------------------------------------
# Module-level singleton — import this everywhere instead of instantiating
# ---------------------------------------------------------------------------
scoring_service: ScoringService = ScoringService()
