"""pytest test suite for the BBSS Fraud Detection scoring pipeline.

All tests mock the FraudModel at the sklearn level so no real model artifact
is required.  Two model fixtures are provided:

  mock_model_low_risk   — predict_proba always returns [[0.95, 0.05]]  (score ≈ 0.05)
  mock_model_high_risk  — predict_proba always returns [[0.05, 0.95]]  (score ≈ 0.95)

A third fixture (mock_model_real) trains a tiny real RandomForest on 200
synthetic samples for structural / integration tests that need a genuine
estimator.
"""

from typing import List
from unittest.mock import MagicMock

import numpy as np
import pytest
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler

from app.models.fraud_model import FraudModel
from app.models.schemas import FraudScoreRequest, FraudScoreResponse
from app.services.feature_engineering import FeatureEngineer
from app.services.scoring_service import ScoringService


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def make_request(**overrides) -> FraudScoreRequest:
    """Return a FraudScoreRequest with sensible defaults, overridden by kwargs."""
    defaults = {
        "transactionId": "txn-test-001",
        "tenantId": "tenant-alpha",
        "fromAccount": "ACC-SOURCE-001",
        "toAccount": "ACC-DEST-002",
        "amount": 100.0,
        "currency": "USD",
        "transactionType": "TRANSFER",
    }
    defaults.update(overrides)
    return FraudScoreRequest(**defaults)


# ---------------------------------------------------------------------------
# Model fixtures
# ---------------------------------------------------------------------------

def _make_mock_model(fraud_proba: float) -> FraudModel:
    """Build a FraudModel whose predict_proba always returns *fraud_proba* for
    the fraud class, regardless of input features."""
    model = FraudModel.__new__(FraudModel)
    model._loaded = True
    model._model_path = ""
    model._scaler_path = ""

    # Scaler: transform is identity (returns zeros of same shape)
    mock_scaler = MagicMock(spec=StandardScaler)
    mock_scaler.transform = MagicMock(
        side_effect=lambda x: np.zeros_like(x)
    )
    model.scaler = mock_scaler

    # Classifier: predict_proba returns fixed probabilities
    mock_clf = MagicMock(spec=RandomForestClassifier)
    mock_clf.predict_proba = MagicMock(
        return_value=np.array([[1.0 - fraud_proba, fraud_proba]])
    )
    model.model = mock_clf

    return model


@pytest.fixture
def mock_model_low_risk() -> FraudModel:
    """FraudModel that always predicts a very low fraud probability (≈ 0.05)."""
    return _make_mock_model(fraud_proba=0.05)


@pytest.fixture
def mock_model_high_risk() -> FraudModel:
    """FraudModel that always predicts a very high fraud probability (≈ 0.95)."""
    return _make_mock_model(fraud_proba=0.95)


@pytest.fixture
def mock_model_real() -> FraudModel:
    """FraudModel backed by a genuine but tiny RandomForestClassifier (200 samples,
    50 features).  Used for structural tests that require a real estimator."""
    rng = np.random.RandomState(99)
    n_samples = 200
    n_features = 50

    X = rng.randn(n_samples, n_features)
    y = (rng.random(n_samples) < 0.15).astype(int)

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    clf = RandomForestClassifier(n_estimators=5, max_depth=4, random_state=99)
    clf.fit(X_scaled, y)

    model = FraudModel.__new__(FraudModel)
    model.model = clf
    model.scaler = scaler
    model._loaded = True
    model._model_path = ""
    model._scaler_path = ""
    return model


# ---------------------------------------------------------------------------
# ScoringService fixture factory
# ---------------------------------------------------------------------------

def _make_scoring_svc(model: FraudModel) -> ScoringService:
    svc = ScoringService.__new__(ScoringService)
    svc.model = model
    svc.feature_engineer = FeatureEngineer()
    svc._kafka_producer = None
    return svc


# ---------------------------------------------------------------------------
# Test 1 — low-amount transaction
# ---------------------------------------------------------------------------

def test_score_low_amount_transaction(mock_model_low_risk: FraudModel) -> None:
    """A small, routine transfer should return LOW risk and APPROVE recommendation."""
    svc = _make_scoring_svc(mock_model_low_risk)
    request = make_request(amount=50.0, transactionType="TRANSFER", currency="USD")

    result: FraudScoreResponse = svc.score_transaction(request)

    assert isinstance(result, FraudScoreResponse)
    assert 0.0 <= result.score <= 1.0
    assert result.riskLevel == "LOW"
    assert result.recommendation == "APPROVE"
    assert result.shouldBlock is False
    assert result.processingTimeMs >= 0.0


# ---------------------------------------------------------------------------
# Test 2 — high-amount transaction
# ---------------------------------------------------------------------------

def test_score_high_amount_transaction(mock_model_high_risk: FraudModel) -> None:
    """A very large transfer with a high fraud score should return HIGH/CRITICAL."""
    svc = _make_scoring_svc(mock_model_high_risk)
    request = make_request(amount=900_000.0, transactionType="TRANSFER")

    result: FraudScoreResponse = svc.score_transaction(request)

    assert isinstance(result, FraudScoreResponse)
    assert result.score >= 0.8, "Expected score >= 0.8 for mock_model_high_risk"
    assert result.riskLevel in ("HIGH", "CRITICAL")
    assert result.shouldBlock is True
    assert result.recommendation == "BLOCK_TRANSACTION"

    # Feature-based rules should have fired for the high amount
    assert "EXTREMELY_HIGH_AMOUNT" in result.triggeredRules
    assert "LARGE_AMOUNT_TRANSFER" in result.triggeredRules


# ---------------------------------------------------------------------------
# Test 3 — response structure validation
# ---------------------------------------------------------------------------

def test_score_response_structure(mock_model_real: FraudModel) -> None:
    """FraudScoreResponse must have all required fields with the correct Python types."""
    svc = _make_scoring_svc(mock_model_real)
    result: FraudScoreResponse = svc.score_transaction(make_request())

    # Field presence
    assert hasattr(result, "transactionId")
    assert hasattr(result, "score")
    assert hasattr(result, "riskLevel")
    assert hasattr(result, "triggeredRules")
    assert hasattr(result, "recommendation")
    assert hasattr(result, "shouldBlock")
    assert hasattr(result, "processingTimeMs")

    # Types
    assert isinstance(result.transactionId, str)
    assert isinstance(result.score, float)
    assert isinstance(result.riskLevel, str)
    assert isinstance(result.triggeredRules, list)
    assert isinstance(result.recommendation, str)
    assert isinstance(result.shouldBlock, bool)
    assert isinstance(result.processingTimeMs, float)

    # Value constraints
    assert 0.0 <= result.score <= 1.0
    assert result.riskLevel in ("LOW", "MEDIUM", "HIGH", "CRITICAL")
    assert result.recommendation in (
        "APPROVE",
        "ADDITIONAL_VERIFICATION",
        "MANUAL_REVIEW",
        "BLOCK_TRANSACTION",
    )
    assert result.processingTimeMs >= 0.0
    assert result.transactionId == "txn-test-001"


# ---------------------------------------------------------------------------
# Test 4 — feature extraction shape
# ---------------------------------------------------------------------------

def test_feature_extraction() -> None:
    """FeatureEngineer.extract_features must return a float64 ndarray of shape (50,)."""
    engineer = FeatureEngineer()
    request = make_request()

    features = engineer.extract_features(request)

    assert isinstance(features, np.ndarray), "Expected np.ndarray"
    assert features.shape == (50,), f"Expected shape (50,), got {features.shape}"
    assert features.dtype == np.float64, f"Expected float64, got {features.dtype}"

    # Spot-check: same_account feature (index 11) should be 0 for different accounts
    assert features[11] == 0.0, "same_account feature should be 0 for different accounts"

    # Spot-check: same-account transaction
    same_acc_request = make_request(fromAccount="ACC-SAME", toAccount="ACC-SAME")
    same_features = engineer.extract_features(same_acc_request)
    assert same_features[11] == 1.0, "same_account feature should be 1 when accounts match"


# ---------------------------------------------------------------------------
# Test 5 — risk level thresholds
# ---------------------------------------------------------------------------

def test_risk_levels() -> None:
    """FraudModel.get_risk_level must map score ranges to the correct labels."""
    model = FraudModel()

    # LOW: [0.0, 0.3)
    assert model.get_risk_level(0.0) == "LOW"
    assert model.get_risk_level(0.10) == "LOW"
    assert model.get_risk_level(0.299) == "LOW"

    # MEDIUM: [0.3, 0.5)
    assert model.get_risk_level(0.3) == "MEDIUM"
    assert model.get_risk_level(0.40) == "MEDIUM"
    assert model.get_risk_level(0.499) == "MEDIUM"

    # HIGH: [0.5, 0.8)
    assert model.get_risk_level(0.5) == "HIGH"
    assert model.get_risk_level(0.65) == "HIGH"
    assert model.get_risk_level(0.799) == "HIGH"

    # CRITICAL: [0.8, 1.0]
    assert model.get_risk_level(0.8) == "CRITICAL"
    assert model.get_risk_level(0.95) == "CRITICAL"
    assert model.get_risk_level(1.0) == "CRITICAL"


# ---------------------------------------------------------------------------
# Test 6 — triggered rule derivation
# ---------------------------------------------------------------------------

def test_triggered_rules_large_transfer() -> None:
    """LARGE_AMOUNT_TRANSFER and EXTREMELY_HIGH_AMOUNT rules must fire for big transfers."""
    engineer = FeatureEngineer()

    request = make_request(amount=900_000.0, transactionType="TRANSFER")
    rules: List[str] = engineer.get_triggered_rules(request, score=0.9)

    assert "EXTREMELY_HIGH_AMOUNT" in rules
    assert "LARGE_AMOUNT_TRANSFER" in rules


def test_triggered_rules_high_risk_currency() -> None:
    """HIGH_RISK_CURRENCY must fire for non USD/EUR/GBP currencies."""
    engineer = FeatureEngineer()

    request = make_request(currency="XYZ")
    rules: List[str] = engineer.get_triggered_rules(request, score=0.1)

    assert "HIGH_RISK_CURRENCY" in rules


def test_triggered_rules_same_account() -> None:
    """SUSPICIOUS_ACCOUNT_PATTERN must fire when fromAccount == toAccount."""
    engineer = FeatureEngineer()

    request = make_request(fromAccount="ACC-LOOP", toAccount="ACC-LOOP")
    rules: List[str] = engineer.get_triggered_rules(request, score=0.2)

    assert "SUSPICIOUS_ACCOUNT_PATTERN" in rules
