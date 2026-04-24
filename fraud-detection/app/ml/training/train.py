"""Standalone training script for the Civic Savings fraud-detection model.

Run from the repository root:
    python app/ml/training/train.py

Artifacts are written to:
    app/ml/artifacts/fraud_model.pkl
    app/ml/artifacts/scaler.pkl
    app/ml/artifacts/model_metadata.json
"""

from __future__ import annotations

import json
import logging
import os
import sys
from datetime import datetime, timezone

import joblib
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, f1_score, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

from app.core.config import settings
from app.services.feature_engineering import FEATURE_NAMES, TOTAL_FEATURES

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ARTIFACT_DIR = os.path.normpath(os.path.join(_SCRIPT_DIR, "..", "artifacts"))
MODEL_PATH = os.path.join(ARTIFACT_DIR, "fraud_model.pkl")
SCALER_PATH = os.path.join(ARTIFACT_DIR, "scaler.pkl")
METADATA_PATH = os.path.join(ARTIFACT_DIR, "model_metadata.json")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    stream=sys.stdout,
)
logger = logging.getLogger(__name__)


def generate_synthetic_data(n_samples: int = 20_000) -> tuple[np.ndarray, np.ndarray]:
    """Generate synthetic fraud data aligned with the online feature vector."""
    rng = np.random.default_rng(42)

    X = np.zeros((n_samples, TOTAL_FEATURES), dtype=np.float64)

    amount = np.clip(rng.lognormal(mean=8.5, sigma=1.15, size=n_samples), 10.0, 1_200_000.0)
    avg_amount = np.clip(rng.lognormal(mean=7.9, sigma=0.7, size=n_samples), 50.0, 400_000.0)
    amount_ratio = amount / np.maximum(avg_amount, 1.0)
    unusual_hour = rng.binomial(1, 0.14, size=n_samples)
    same_account = rng.binomial(1, 0.01, size=n_samples)
    cross_border = rng.binomial(1, 0.10, size=n_samples)
    kyc_high = rng.binomial(1, 0.12, size=n_samples)
    receiver_new = rng.binomial(1, 0.18, size=n_samples)
    velocity_10m = rng.poisson(lam=0.4, size=n_samples)
    velocity_1h = velocity_10m + rng.poisson(lam=1.2, size=n_samples)
    structuring = rng.binomial(1, 0.05, size=n_samples)
    wallet_high = rng.binomial(1, 0.08, size=n_samples)
    sanctions = rng.binomial(1, 0.002, size=n_samples)

    X[:, FEATURE_NAMES.index("amount")] = amount
    X[:, FEATURE_NAMES.index("amount_log")] = np.log1p(amount)
    X[:, FEATURE_NAMES.index("amount_sqrt")] = np.sqrt(amount)
    X[:, FEATURE_NAMES.index("amount_gt_50k")] = (amount >= 50_000.0).astype(float)
    X[:, FEATURE_NAMES.index("amount_gt_500k")] = (amount >= 500_000.0).astype(float)
    X[:, FEATURE_NAMES.index("amount_gt_1m")] = (amount >= 1_000_000.0).astype(float)
    X[:, FEATURE_NAMES.index("hour_of_day")] = rng.integers(0, 24, size=n_samples)
    X[:, FEATURE_NAMES.index("is_unusual_hour")] = unusual_hour
    X[:, FEATURE_NAMES.index("tx_type_transfer")] = rng.binomial(1, 0.45, size=n_samples)
    X[:, FEATURE_NAMES.index("tx_type_payment")] = rng.binomial(1, 0.30, size=n_samples)
    X[:, FEATURE_NAMES.index("tx_type_withdrawal")] = rng.binomial(1, 0.15, size=n_samples)
    X[:, FEATURE_NAMES.index("tx_type_deposit")] = rng.binomial(1, 0.10, size=n_samples)
    currency_low_risk = rng.binomial(1, 0.82, size=n_samples)
    currency_high_risk = (1 - currency_low_risk) * rng.binomial(1, 0.35, size=n_samples)
    X[:, FEATURE_NAMES.index("currency_low_risk")] = currency_low_risk
    X[:, FEATURE_NAMES.index("currency_high_risk")] = currency_high_risk
    X[:, FEATURE_NAMES.index("network_permissioned")] = rng.binomial(1, 0.75, size=n_samples)
    X[:, FEATURE_NAMES.index("network_public")] = 1.0 - X[:, FEATURE_NAMES.index("network_permissioned")]
    X[:, FEATURE_NAMES.index("same_account")] = same_account
    X[:, FEATURE_NAMES.index("cross_border")] = cross_border
    X[:, FEATURE_NAMES.index("from_hash_mod")] = rng.random(n_samples)
    X[:, FEATURE_NAMES.index("to_hash_mod")] = rng.random(n_samples)
    X[:, FEATURE_NAMES.index("account_prefix_match")] = rng.binomial(1, 0.03, size=n_samples)
    X[:, FEATURE_NAMES.index("from_digit_ratio")] = rng.uniform(0.1, 0.9, size=n_samples)
    X[:, FEATURE_NAMES.index("to_digit_ratio")] = rng.uniform(0.1, 0.9, size=n_samples)
    X[:, FEATURE_NAMES.index("from_pattern_score")] = rng.uniform(0.0, 1.0, size=n_samples)
    X[:, FEATURE_NAMES.index("to_pattern_score")] = rng.uniform(0.0, 1.0, size=n_samples)
    X[:, FEATURE_NAMES.index("origin_country_high_risk")] = rng.binomial(1, 0.05, size=n_samples)
    X[:, FEATURE_NAMES.index("destination_country_high_risk")] = rng.binomial(1, 0.06, size=n_samples)
    X[:, FEATURE_NAMES.index("kyc_verified")] = 1.0 - kyc_high
    X[:, FEATURE_NAMES.index("kyc_risk_low")] = rng.binomial(1, 0.50, size=n_samples)
    X[:, FEATURE_NAMES.index("kyc_risk_medium")] = rng.binomial(1, 0.38, size=n_samples)
    X[:, FEATURE_NAMES.index("kyc_risk_high")] = kyc_high
    X[:, FEATURE_NAMES.index("receiver_verified")] = 1.0 - receiver_new
    X[:, FEATURE_NAMES.index("wallet_risk_low")] = rng.binomial(1, 0.65, size=n_samples)
    X[:, FEATURE_NAMES.index("wallet_risk_medium")] = rng.binomial(1, 0.27, size=n_samples)
    X[:, FEATURE_NAMES.index("wallet_risk_high")] = wallet_high
    X[:, FEATURE_NAMES.index("sanctions_hit")] = sanctions
    X[:, FEATURE_NAMES.index("pep_flag")] = rng.binomial(1, 0.03, size=n_samples)
    X[:, FEATURE_NAMES.index("travel_rule_received")] = rng.binomial(1, 0.72, size=n_samples)
    X[:, FEATURE_NAMES.index("onboarding_age_log")] = np.log1p(rng.integers(1, 4_000, size=n_samples))
    X[:, FEATURE_NAMES.index("history_total_log")] = np.log1p(rng.integers(0, 2_000, size=n_samples))
    X[:, FEATURE_NAMES.index("avg_amount")] = avg_amount
    X[:, FEATURE_NAMES.index("amount_to_avg_ratio")] = amount_ratio
    X[:, FEATURE_NAMES.index("amount_zscore")] = rng.normal(loc=np.log1p(amount_ratio), scale=0.7, size=n_samples)
    X[:, FEATURE_NAMES.index("recent_10m_count")] = velocity_10m
    X[:, FEATURE_NAMES.index("recent_1h_count")] = velocity_1h
    X[:, FEATURE_NAMES.index("recent_24h_count")] = velocity_1h + rng.poisson(lam=4.0, size=n_samples)
    X[:, FEATURE_NAMES.index("recent_24h_amount_log")] = np.log1p(amount * rng.uniform(1.0, 5.0, size=n_samples))
    X[:, FEATURE_NAMES.index("velocity_to_daily_baseline")] = np.maximum(velocity_10m / 0.2, 0.0)
    X[:, FEATURE_NAMES.index("high_value_24h_count")] = rng.poisson(lam=0.6, size=n_samples)
    X[:, FEATURE_NAMES.index("near_threshold_24h_count")] = structuring * rng.integers(3, 7, size=n_samples)
    X[:, FEATURE_NAMES.index("receiver_seen_count")] = (1 - receiver_new) * rng.integers(1, 15, size=n_samples)
    X[:, FEATURE_NAMES.index("new_receiver_flag")] = receiver_new
    X[:, FEATURE_NAMES.index("recent_unique_receivers")] = rng.integers(1, 40, size=n_samples)
    X[:, FEATURE_NAMES.index("seen_device_before")] = rng.binomial(1, 0.78, size=n_samples)
    X[:, FEATURE_NAMES.index("seen_ip_before")] = rng.binomial(1, 0.84, size=n_samples)
    X[:, FEATURE_NAMES.index("seen_country_before")] = rng.binomial(1, 0.88, size=n_samples)
    X[:, FEATURE_NAMES.index("days_since_last_tx")] = rng.exponential(scale=4.0, size=n_samples)
    X[:, FEATURE_NAMES.index("night_ratio")] = rng.uniform(0.0, 0.35, size=n_samples)
    X[:, FEATURE_NAMES.index("weekend_ratio")] = rng.uniform(0.0, 0.45, size=n_samples)
    X[:, FEATURE_NAMES.index("cross_border_ratio")] = rng.uniform(0.0, 0.25, size=n_samples)

    fraud_score = np.full(n_samples, 0.02, dtype=np.float64)
    fraud_score += X[:, FEATURE_NAMES.index("amount_gt_50k")] * 0.02
    fraud_score += X[:, FEATURE_NAMES.index("amount_gt_500k")] * 0.10
    fraud_score += unusual_hour * 0.05
    fraud_score += receiver_new * 0.04
    fraud_score += (velocity_10m >= 3).astype(float) * 0.08
    fraud_score += structuring * 0.12
    fraud_score += wallet_high * 0.05
    fraud_score += cross_border * 0.03
    fraud_score += kyc_high * 0.07
    fraud_score += same_account * 0.15
    fraud_score += sanctions * 0.70
    fraud_score += (amount_ratio >= 3.0).astype(float) * 0.06
    fraud_score = np.clip(fraud_score, 0.0, 0.995)
    y = (rng.random(n_samples) < fraud_score).astype(int)

    logger.info(
        "Synthetic training dataset generated: samples=%d feature_count=%d fraud_rate=%.4f",
        n_samples,
        TOTAL_FEATURES,
        float(y.mean()),
    )
    return X, y


def train_model() -> tuple[RandomForestClassifier, StandardScaler]:
    os.makedirs(ARTIFACT_DIR, exist_ok=True)

    X, y = generate_synthetic_data()
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=0.20,
        random_state=42,
        stratify=y,
    )

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    model = RandomForestClassifier(
        n_estimators=180,
        max_depth=12,
        min_samples_leaf=4,
        class_weight="balanced_subsample",
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_train_scaled, y_train)

    y_proba = model.predict_proba(X_test_scaled)[:, 1]
    y_pred = (y_proba >= 0.50).astype(int)

    logger.info("Training complete")
    print()
    print("=" * 64)
    print(" Hybrid Fraud Model Metrics")
    print("=" * 64)
    print(f" Precision : {precision_score(y_test, y_pred, zero_division=0):.4f}")
    print(f" Recall    : {recall_score(y_test, y_pred, zero_division=0):.4f}")
    print(f" F1 Score  : {f1_score(y_test, y_pred, zero_division=0):.4f}")
    print(f" AUC-ROC   : {roc_auc_score(y_test, y_proba):.4f}")
    print("=" * 64)
    print(classification_report(y_test, y_pred, target_names=["Legitimate", "Fraud"]))

    joblib.dump(model, MODEL_PATH, compress=3)
    joblib.dump(scaler, SCALER_PATH, compress=3)
    with open(METADATA_PATH, "w", encoding="utf-8") as handle:
        json.dump(
            {
                "version": settings.MODEL_VERSION,
                "trainedAt": datetime.now(timezone.utc).isoformat(),
                "featureCount": TOTAL_FEATURES,
                "featureNames": FEATURE_NAMES,
                "featureSchemaVersion": settings.FEATURE_SCHEMA_VERSION,
                "algorithm": "RandomForestClassifier",
            },
            handle,
            indent=2,
        )

    logger.info("Artifacts saved: model=%s scaler=%s", MODEL_PATH, SCALER_PATH)
    return model, scaler


if __name__ == "__main__":
    train_model()
