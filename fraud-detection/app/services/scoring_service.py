from __future__ import annotations

import time
from datetime import datetime
from typing import Any, Dict

import structlog

from app.core.config import settings
from app.models.fraud_model import FraudModel
from app.models.schemas import FraudScoreRequest, FraudScoreResponse
from app.services.behavioral_engine import BehavioralEngine
from app.services.decision_engine import DecisionEngine
from app.services.explainability import ExplainabilityService
from app.services.feature_engineering import FeatureEngineer
from app.services.history_repository import HistoryRepository
from app.services.rule_engine import RuleEngine
from app.services.risk_context import RiskSignal

logger = structlog.get_logger(__name__)


class ScoringService:
    """Hybrid fraud scoring orchestrator."""

    def __init__(self) -> None:
        self.model = FraudModel()
        self.feature_engineer = FeatureEngineer()
        self.rule_engine = RuleEngine()
        self.behavioral_engine = BehavioralEngine()
        self.decision_engine = DecisionEngine()
        self.explainability = ExplainabilityService()
        self.history_repository = HistoryRepository()

    def score_transaction(self, request: FraudScoreRequest) -> FraudScoreResponse:
        start = time.perf_counter()

        try:
            normalized = self.feature_engineer.normalize_request(request)

            existing = self.history_repository.get_existing_assessment(
                normalized.transaction_id
            )
            if existing is not None:
                return FraudScoreResponse(
                    **{
                        **existing.response_payload,
                        "processingTimeMs": round(
                            (time.perf_counter() - start) * 1_000.0, 3
                        ),
                    }
                )

            history = self.history_repository.build_history_summary(normalized)
            feature_vector = self.feature_engineer.extract_features(normalized, history)

            fallback_used = False
            system_signals: list[RiskSignal] = []
            try:
                ml_score = self.model.predict(feature_vector)
            except Exception as exc:
                fallback_used = True
                ml_score = settings.NEUTRAL_FALLBACK_SCORE
                system_signals.append(
                    RiskSignal(
                        key="ml_model_fallback",
                        source="SYSTEM",
                        weight=0.05,
                        severity="MEDIUM",
                        explanation="ML model was unavailable, so the service used a neutral fallback score and routed the transaction to review.",
                        evidence={"error": str(exc)},
                    )
                )

            rule_result = self.rule_engine.evaluate(normalized, history)
            behavioral_result = self.behavioral_engine.evaluate(normalized, history)

            hybrid_signals = [*rule_result.signals, *behavioral_result.signals, *system_signals]
            hybrid_signals.extend(
                self._ml_pattern_signal(normalized, history, ml_score, hybrid_signals)
            )

            final_score = min(
                1.0,
                max(0.0, ml_score + rule_result.score + behavioral_result.score),
            )
            force_review = fallback_used and final_score < settings.FRAUD_SCORE_THRESHOLD_BLOCK
            decision = self.decision_engine.evaluate(
                final_score,
                force_review=force_review,
                metadata=normalized.metadata,
            )
            triggered_rules, explanations, breakdown = self.explainability.build(
                hybrid_signals
            )

            response = FraudScoreResponse(
                transactionId=normalized.transaction_id,
                score=round(final_score, 6),
                mlScore=round(ml_score, 6),
                ruleScore=round(rule_result.score, 6),
                behavioralScore=round(behavioral_result.score, 6),
                riskLevel=decision.risk_level,
                decision=decision.decision,
                triggeredRules=triggered_rules,
                explanations=explanations,
                signalBreakdown=breakdown,
                recommendation=decision.recommendation,
                reviewRequired=decision.review_required,
                shouldBlock=decision.decision == "BLOCK",
                fallbackUsed=fallback_used,
                modelVersion=self.model.version,
                processingTimeMs=round((time.perf_counter() - start) * 1_000.0, 3),
            )

            try:
                self.history_repository.save_assessment(
                    normalized,
                    feature_vector.tolist(),
                    response,
                )
            except Exception as exc:
                logger.warning(
                    "Assessment persistence failed; returning score without history update",
                    transaction_id=normalized.transaction_id,
                    error=str(exc),
                )

            logger.info(
                "Transaction scored",
                transaction_id=normalized.transaction_id,
                tenant_id=normalized.tenant_id,
                score=response.score,
                ml_score=response.mlScore,
                rule_score=response.ruleScore,
                behavioral_score=response.behavioralScore,
                risk_level=response.riskLevel,
                decision=response.decision,
                fallback_used=response.fallbackUsed,
            )
            return response
        except Exception as exc:
            logger.error(
                "Scoring pipeline failed; returning fallback review decision",
                error=str(exc),
                transaction_id=request.transactionId,
                exc_info=True,
            )
            return self._build_service_fallback(request, start_time=start, error=str(exc))

    def score_transaction_async(self, event: Dict[str, Any]) -> None:
        event = dict(event)
        timestamp_value = event.get("transactionTimestamp", event.get("timestamp"))
        if timestamp_value is not None:
            event["transactionTimestamp"] = self._normalize_event_timestamp(
                timestamp_value
            )
        try:
            request = FraudScoreRequest.model_validate(event)
        except Exception as exc:
            logger.error(
                "Failed to deserialize Kafka event into FraudScoreRequest",
                error=str(exc),
                kafka_payload=event,
            )
            return

        result = self.score_transaction(request)
        logger.info(
            "Async transaction scored",
            transaction_id=result.transactionId,
            score=result.score,
            risk_level=result.riskLevel,
            decision=result.decision,
        )

    @staticmethod
    def _normalize_event_timestamp(value: Any) -> Any:
        if isinstance(value, list) and len(value) >= 6:
            year, month, day, hour, minute, second = (int(part) for part in value[:6])
            nanoseconds = int(value[6]) if len(value) > 6 else 0
            microseconds = max(0, min(999_999, nanoseconds // 1_000))
            return datetime(
                year,
                month,
                day,
                hour,
                minute,
                second,
                microseconds,
            )
        return value

    def _ml_pattern_signal(
        self,
        normalized,
        history,
        ml_score: float,
        signals: list[RiskSignal],
    ) -> list[RiskSignal]:
        if ml_score < 0.65:
            return []

        signal_keys = {signal.key for signal in signals}
        explanations: list[str] = []
        if "amount_deviation" not in signal_keys and history.total_transactions > 0:
            ratio = normalized.amount / max(history.avg_amount, 1.0)
            if ratio > 2.0:
                explanations.append(f"amount ratio {ratio:.1f}x")
        if "velocity_spike" not in signal_keys and history.recent_10m_count >= 3:
            explanations.append(f"velocity {history.recent_10m_count} in 10m")
        if "unusual_hour" not in signal_keys and normalized.hour_of_day <= 5:
            explanations.append(f"time {normalized.hour_of_day:02d}:00 UTC")
        if "new_receiver" not in signal_keys and history.receiver_seen_count == 0:
            explanations.append("new receiver")

        detail = ", ".join(explanations) if explanations else "multiple weak signals"
        return [
            RiskSignal(
                key="ml_pattern_match",
                source="ML",
                weight=round(min(0.10, ml_score * 0.10), 4),
                severity="MEDIUM" if ml_score < 0.8 else "HIGH",
                explanation=f"ML model detected an elevated multi-feature pattern driven by {detail}.",
                evidence={"mlScore": round(ml_score, 4)},
            )
        ]

    def _build_service_fallback(
        self,
        request: FraudScoreRequest,
        *,
        start_time: float,
        error: str,
    ) -> FraudScoreResponse:
        processing_time_ms = round((time.perf_counter() - start_time) * 1_000.0, 3)
        return FraudScoreResponse(
            transactionId=request.transactionId,
            score=settings.NEUTRAL_FALLBACK_SCORE,
            mlScore=settings.NEUTRAL_FALLBACK_SCORE,
            ruleScore=0.0,
            behavioralScore=0.0,
            riskLevel="MEDIUM",
            decision="HOLD",
            triggeredRules=["fraud_service_fallback"],
            explanations=[
                "Fraud service fallback was used because the scoring pipeline failed; neutral score assigned and manual review required."
            ],
            signalBreakdown=[
                {
                    "key": "fraud_service_fallback",
                    "source": "SYSTEM",
                    "weight": 0.05,
                    "severity": "MEDIUM",
                    "explanation": "Fraud service fallback was used because the scoring pipeline failed; neutral score assigned and manual review required.",
                    "evidence": {"error": error},
                }
            ],
            recommendation="MANUAL_REVIEW",
            reviewRequired=True,
            shouldBlock=False,
            fallbackUsed=True,
            modelVersion=self.model.version,
            processingTimeMs=processing_time_ms,
        )


scoring_service = ScoringService()
