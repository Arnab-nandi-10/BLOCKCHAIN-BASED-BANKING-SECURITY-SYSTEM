from __future__ import annotations

from datetime import datetime, timezone

import pytest

from app.models.schemas import FraudScoreRequest, FraudScoreResponse
from app.services.behavioral_engine import BehavioralEngine
from app.services.decision_engine import DecisionEngine
from app.services.explainability import ExplainabilityService
from app.services.feature_engineering import FEATURE_NAMES, FeatureEngineer, TOTAL_FEATURES
from app.services.history_repository import HistoryRepository
from app.services.rule_engine import RuleEngine
from app.services.risk_context import HistorySummary, StoredAssessment
from app.services.scoring_service import ScoringService


class FakeModel:
    def __init__(self, score: float = 0.05, *, should_fail: bool = False) -> None:
        self.score = score
        self.should_fail = should_fail
        self.version = "test-model-v1"

    def predict(self, raw_features):  # type: ignore[no-untyped-def]
        if self.should_fail:
            raise RuntimeError("model unavailable")
        return self.score

    def is_loaded(self) -> bool:
        return True


class FakeHistoryRepository(HistoryRepository):
    def __init__(self, history: HistorySummary | None = None) -> None:
        self.history = history or HistorySummary()
        self.saved: list[tuple[str, FraudScoreResponse]] = []
        self.existing: StoredAssessment | None = None

    def get_existing_assessment(self, transaction_id: str) -> StoredAssessment | None:
        return self.existing

    def build_history_summary(self, tx):  # type: ignore[no-untyped-def]
        return self.history

    def save_assessment(self, tx, feature_vector, response):  # type: ignore[no-untyped-def]
        self.saved.append((tx.transaction_id, response))

    def list_alerts(self, tenant_id: str, page: int, size: int):  # type: ignore[no-untyped-def]
        return [], 0


def make_request(**overrides) -> FraudScoreRequest:
    defaults = {
        "transactionId": "txn-test-001",
        "tenantId": "tenant-alpha",
        "fromAccount": "ACC-SOURCE-001",
        "toAccount": "ACC-DEST-002",
        "amount": 100.0,
        "currency": "USD",
        "transactionType": "TRANSFER",
        "transactionTimestamp": datetime(2026, 4, 22, 14, 30, tzinfo=timezone.utc),
        "metadata": {"customerId": "cust-001", "channel": "WEB"},
    }
    defaults.update(overrides)
    return FraudScoreRequest(**defaults)


def make_service(
    *,
    model: FakeModel | None = None,
    history: HistorySummary | None = None,
) -> tuple[ScoringService, FakeHistoryRepository]:
    repository = FakeHistoryRepository(history)
    service = ScoringService.__new__(ScoringService)
    service.model = model or FakeModel()
    service.feature_engineer = FeatureEngineer()
    service.rule_engine = RuleEngine()
    service.behavioral_engine = BehavioralEngine()
    service.decision_engine = DecisionEngine()
    service.explainability = ExplainabilityService()
    service.history_repository = repository
    return service, repository


def test_score_low_amount_transaction_returns_allow() -> None:
    service, repository = make_service(model=FakeModel(score=0.05))

    result = service.score_transaction(make_request(amount=50.0))

    assert isinstance(result, FraudScoreResponse)
    assert result.score == pytest.approx(0.05, rel=1e-3)
    assert result.riskLevel == "LOW"
    assert result.decision == "ALLOW"
    assert result.recommendation == "ALLOW"
    assert result.shouldBlock is False
    assert result.reviewRequired is False
    assert result.fallbackUsed is False
    assert repository.saved, "Expected assessment to be persisted"


def test_score_weighted_behavioral_and_rule_signals_hold_transaction() -> None:
    history = HistorySummary(
        total_transactions=20,
        avg_amount=25_000.0,
        amount_stddev=8_000.0,
        avg_daily_transactions=4.0,
        recent_10m_count=5,
        recent_1h_count=8,
        recent_24h_count=12,
        recent_24h_total_amount=180_000.0,
        near_threshold_24h_count=4,
        receiver_seen_count=0,
        recent_unique_receivers=3,
        seen_device_before=False,
        seen_ip_before=False,
        seen_country_before=True,
        night_ratio=0.02,
        weekend_ratio=0.10,
        cross_border_ratio=0.02,
        last_transaction_at=datetime(2026, 4, 20, 11, 0, tzinfo=timezone.utc),
        first_transaction_at=datetime(2025, 12, 1, 9, 0, tzinfo=timezone.utc),
    )
    service, _ = make_service(model=FakeModel(score=0.42), history=history)
    request = make_request(
        amount=120_000.0,
        currency="XMR",
        transactionTimestamp=datetime(2026, 4, 22, 2, 10, tzinfo=timezone.utc),
        metadata={
            "customerId": "cust-001",
            "receiverVerified": "false",
            "walletRiskLevel": "HIGH",
            "deviceId": "device-new",
            "originCountry": "IN",
            "destinationCountry": "IR",
            "travelRuleReceived": "false",
        },
    )

    result = service.score_transaction(request)

    assert result.score > 0.6
    assert result.riskLevel in {"HIGH", "CRITICAL"}
    assert result.decision in {"HOLD", "BLOCK"}
    assert "velocity_spike" in result.triggeredRules
    assert "new_receiver" in result.triggeredRules
    assert any("higher than the customer's usual average" in text for text in result.explanations)


def test_sanctions_signal_blocks_even_with_low_ml_score() -> None:
    service, _ = make_service(model=FakeModel(score=0.10))
    request = make_request(
        amount=5_000.0,
        metadata={"customerId": "cust-001", "sanctionsHit": "true"},
    )

    result = service.score_transaction(request)

    assert result.riskLevel == "CRITICAL"
    assert result.decision == "BLOCK"
    assert result.shouldBlock is True
    assert "sanctions_or_watchlist_match" in result.triggeredRules


def test_model_failure_uses_neutral_fallback_and_review() -> None:
    service, _ = make_service(model=FakeModel(score=0.0, should_fail=True))

    result = service.score_transaction(make_request(amount=500.0))

    assert result.fallbackUsed is True
    assert result.decision == "HOLD"
    assert result.reviewRequired is True
    assert result.score >= 0.5
    assert "ml_model_fallback" in result.triggeredRules or "fraud_service_fallback" in result.triggeredRules


def test_feature_extraction_shape_and_same_account_flag() -> None:
    engineer = FeatureEngineer()
    request = make_request()
    normalized = engineer.normalize_request(request)
    features = engineer.extract_features(normalized, HistorySummary())

    assert features.shape == (TOTAL_FEATURES,)
    assert features.dtype.name == "float64"

    same_account = make_request(fromAccount="ACC-SAME", toAccount="ACC-SAME")
    same_normalized = engineer.normalize_request(same_account)
    same_features = engineer.extract_features(same_normalized, HistorySummary())
    assert same_features[20] == 1.0, "same-account feature should be set"


def test_decision_engine_thresholds() -> None:
    engine = DecisionEngine()

    assert engine.evaluate(0.10).decision == "ALLOW"
    assert engine.evaluate(0.45).decision == "MONITOR"
    assert engine.evaluate(0.75).decision == "HOLD"
    assert engine.evaluate(0.95).decision == "BLOCK"


def test_force_review_preserves_risk_band() -> None:
    engine = DecisionEngine()

    outcome = engine.evaluate(0.72, force_review=True)

    assert outcome.risk_level == "HIGH"
    assert outcome.decision == "HOLD"
    assert outcome.review_required is True


def test_sparse_history_and_suspicious_account_pattern_add_weighted_signals() -> None:
    history = HistorySummary(
        total_transactions=3,
        avg_amount=6_000.0,
        amount_stddev=2_000.0,
        avg_daily_transactions=1.5,
        receiver_seen_count=0,
    )
    service, _ = make_service(model=FakeModel(score=0.20), history=history)
    request = make_request(
        amount=30_000.0,
        toAccount="MULE-0000-1234",
        metadata={
            "customerId": "cust-001",
            "receiverVerified": "false",
            "receiverAgeDays": "4",
        },
    )

    result = service.score_transaction(request)

    assert "adaptive_amount_spike" in result.triggeredRules
    assert "suspicious_account_pattern" in result.triggeredRules
    assert "new_receiver" in result.triggeredRules
    assert result.score > 0.3


def test_high_risk_currency_shift_requires_behavioral_context() -> None:
    history = HistorySummary(
        total_transactions=12,
        avg_amount=2_000.0,
        amount_stddev=750.0,
        avg_daily_transactions=2.0,
        high_risk_currency_ratio=0.0,
    )
    service, _ = make_service(model=FakeModel(score=0.18), history=history)
    request = make_request(
        amount=2_500.0,
        currency="XMR",
        metadata={"customerId": "cust-001"},
    )

    result = service.score_transaction(request)

    assert "high_risk_currency_shift" in result.triggeredRules


def test_non_high_risk_currency_does_not_set_high_risk_feature_flag() -> None:
    engineer = FeatureEngineer()
    request = make_request(currency="AUD")

    normalized = engineer.normalize_request(request)
    features = engineer.extract_features(normalized, HistorySummary())

    assert features[FEATURE_NAMES.index("currency_low_risk")] == 0.0
    assert features[FEATURE_NAMES.index("currency_high_risk")] == 0.0
