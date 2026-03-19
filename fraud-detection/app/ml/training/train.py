"""Standalone training script for the BBSS fraud-detection model.

Run from the repository root:
    python app/ml/training/train.py

Artifacts are written to:
    app/ml/artifacts/fraud_model.pkl
    app/ml/artifacts/scaler.pkl

These paths match the defaults in app/core/config.py and are also baked
into the Docker image via the RUN instruction in the Dockerfile.
"""

import logging
import os
import sys
from typing import Tuple

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

# ---------------------------------------------------------------------------
# Path configuration — resolve artifacts relative to this file so the script
# works regardless of where it is invoked from.
# ---------------------------------------------------------------------------
_SCRIPT_DIR: str = os.path.dirname(os.path.abspath(__file__))
ARTIFACT_DIR: str = os.path.normpath(os.path.join(_SCRIPT_DIR, "..", "artifacts"))
MODEL_PATH: str = os.path.join(ARTIFACT_DIR, "fraud_model.pkl")
SCALER_PATH: str = os.path.join(ARTIFACT_DIR, "scaler.pkl")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    stream=sys.stdout,
)
logger = logging.getLogger(__name__)

# Random seed for reproducibility across all stages
_RNG_SEED: int = 42


# ---------------------------------------------------------------------------
# Synthetic data generation
# ---------------------------------------------------------------------------

def generate_synthetic_data(n_samples: int = 10_000) -> pd.DataFrame:
    """Generate a synthetic transaction dataset with realistic fraud patterns.

    Feature layout (50 columns + fraud_label)
    ------------------------------------------
    0  amount               — log-normal, clipped to [1, 1 000 000]
    1  amount_log           — log1p(amount)
    2  is_high_amount       — 1 if amount > 50 000
    3  is_very_high_amount  — 1 if amount > 500 000
    4  hour_of_day          — uniform int [0, 23]
    5  is_unusual_hour      — 1 if hour <= 5
    6  is_weekend           — 1 if weekday in {5, 6}
    7  currency_risk        — 0 = USD/EUR/GBP, 1 = other
    8  transaction_type     — 0=TRANSFER 1=PAYMENT 2=WITHDRAWAL 3=DEPOSIT
    9  from_account_hash    — uniform float [0, 1)
    10 to_account_hash      — uniform float [0, 1)
    11 same_account         — 1 if from == to  (~1 % of rows)
    12-49 feature_12..49    — random noise (future feature slots)
    fraud_label             — binary target (~3-8 % fraud rate)
    """
    rng = np.random.RandomState(_RNG_SEED)

    # --- Core numeric features -------------------------------------------
    amount: np.ndarray = np.clip(
        rng.lognormal(mean=6.0, sigma=2.5, size=n_samples), 1.0, 1_000_000.0
    )
    amount_log: np.ndarray = np.log1p(amount)
    is_high_amount: np.ndarray = (amount > 50_000.0).astype(int)
    is_very_high_amount: np.ndarray = (amount > 500_000.0).astype(int)

    # --- Time features ---------------------------------------------------
    hour_of_day: np.ndarray = rng.randint(0, 24, size=n_samples)
    is_unusual_hour: np.ndarray = (hour_of_day <= 5).astype(int)
    day_of_week: np.ndarray = rng.randint(0, 7, size=n_samples)
    is_weekend: np.ndarray = (day_of_week >= 5).astype(int)

    # --- Categorical features --------------------------------------------
    # 85 % of transactions use low-risk currencies
    currency_risk: np.ndarray = rng.choice([0, 1], size=n_samples, p=[0.85, 0.15])
    # Transaction type distribution: 40 % transfer, 30 % payment, 20 % withdrawal, 10 % deposit
    transaction_type: np.ndarray = rng.choice(
        [0, 1, 2, 3], size=n_samples, p=[0.40, 0.30, 0.20, 0.10]
    )

    # --- Account features ------------------------------------------------
    from_account_hash: np.ndarray = rng.uniform(0.0, 1.0, size=n_samples)
    to_account_hash: np.ndarray = rng.uniform(0.0, 1.0, size=n_samples)
    same_account: np.ndarray = (rng.random(n_samples) < 0.01).astype(int)

    # --- Noise features (12-49) ------------------------------------------
    n_noise: int = 50 - 12  # 38 noise columns
    noise: np.ndarray = rng.randn(n_samples, n_noise)

    # --- Fraud label generation ------------------------------------------
    # Base fraud probability for every transaction
    fraud_prob: np.ndarray = np.full(n_samples, 0.02, dtype=float)

    # Additive risk factors (order matters for readability, not computation)
    fraud_prob += is_unusual_hour * 0.05          # late-night transactions
    fraud_prob += is_high_amount * 0.03           # large amounts
    fraud_prob += is_very_high_amount * 0.15      # extremely large amounts
    fraud_prob += currency_risk * 0.04            # high-risk currencies
    fraud_prob += same_account * 0.20             # self-transfer (money-laundering signal)
    fraud_prob += (transaction_type == 2) * 0.03  # withdrawals slightly riskier
    fraud_prob += (is_unusual_hour & is_high_amount) * 0.10  # combined pattern

    fraud_prob = np.clip(fraud_prob, 0.0, 1.0)
    fraud_label: np.ndarray = (rng.random(n_samples) < fraud_prob).astype(int)

    # --- Assemble DataFrame ----------------------------------------------
    df = pd.DataFrame(
        {
            "amount": amount,
            "amount_log": amount_log,
            "is_high_amount": is_high_amount,
            "is_very_high_amount": is_very_high_amount,
            "hour_of_day": hour_of_day,
            "is_unusual_hour": is_unusual_hour,
            "is_weekend": is_weekend,
            "currency_risk": currency_risk,
            "transaction_type": transaction_type,
            "from_account_hash": from_account_hash,
            "to_account_hash": to_account_hash,
            "same_account": same_account,
        }
    )

    for i in range(n_noise):
        df[f"feature_{12 + i}"] = noise[:, i]

    df["fraud_label"] = fraud_label

    fraud_count: int = int(fraud_label.sum())
    logger.info(
        "Synthetic dataset generated: %d samples, %d fraud (%.2f %%)",
        n_samples,
        fraud_count,
        100.0 * fraud_count / n_samples,
    )
    return df


# ---------------------------------------------------------------------------
# Model training
# ---------------------------------------------------------------------------

def train_model(df: pd.DataFrame) -> Tuple[RandomForestClassifier, StandardScaler]:
    """Train a RandomForestClassifier on *df* and persist artifacts.

    Steps
    -----
    1. Split features / labels.
    2. Stratified 80/20 train-test split.
    3. Fit StandardScaler on training set.
    4. Train RandomForestClassifier(n_estimators=100, class_weight='balanced').
    5. Evaluate and print: accuracy, precision, recall, F1, AUC-ROC,
       and a full classification report.
    6. Save model and scaler to ARTIFACT_DIR with joblib.

    Returns
    -------
    (model, scaler) — both fitted objects.
    """
    os.makedirs(ARTIFACT_DIR, exist_ok=True)

    feature_cols = [c for c in df.columns if c != "fraud_label"]
    X: np.ndarray = df[feature_cols].values
    y: np.ndarray = df["fraud_label"].values

    logger.info(
        "Training on %d samples, %d features", X.shape[0], X.shape[1]
    )

    X_train, X_test, y_train, y_test = train_test_split(
        X, y,
        test_size=0.20,
        random_state=_RNG_SEED,
        stratify=y,
    )

    # Scale
    scaler: StandardScaler = StandardScaler()
    X_train_scaled: np.ndarray = scaler.fit_transform(X_train)
    X_test_scaled: np.ndarray = scaler.transform(X_test)

    # Train
    model: RandomForestClassifier = RandomForestClassifier(
        n_estimators=100,
        max_depth=12,
        min_samples_leaf=5,
        class_weight="balanced",
        random_state=_RNG_SEED,
        n_jobs=-1,
    )
    model.fit(X_train_scaled, y_train)
    logger.info("RandomForestClassifier training complete")

    # Evaluate
    y_pred: np.ndarray = model.predict(X_test_scaled)
    y_proba: np.ndarray = model.predict_proba(X_test_scaled)[:, 1]

    acc: float = accuracy_score(y_test, y_pred)
    prec: float = precision_score(y_test, y_pred, zero_division=0)
    rec: float = recall_score(y_test, y_pred, zero_division=0)
    f1: float = f1_score(y_test, y_pred, zero_division=0)
    auc: float = roc_auc_score(y_test, y_proba)

    print()
    print("=" * 55)
    print(" Model Training Results")
    print("=" * 55)
    print(f"  Accuracy  : {acc:.4f}")
    print(f"  Precision : {prec:.4f}")
    print(f"  Recall    : {rec:.4f}")
    print(f"  F1 Score  : {f1:.4f}")
    print(f"  AUC-ROC   : {auc:.4f}")
    print("=" * 55)
    print()
    print(classification_report(y_test, y_pred, target_names=["Legitimate", "Fraud"]))
    print("=" * 55)

    # Persist artifacts
    joblib.dump(model, MODEL_PATH, compress=3)
    joblib.dump(scaler, SCALER_PATH, compress=3)

    logger.info("Model saved  → %s", MODEL_PATH)
    logger.info("Scaler saved → %s", SCALER_PATH)

    return model, scaler


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    logger.info("Generating synthetic training data (n_samples=10 000)…")
    df: pd.DataFrame = generate_synthetic_data(n_samples=10_000)

    logger.info("Training fraud-detection model…")
    train_model(df)

    logger.info("Training pipeline complete.  Artifacts written to: %s", ARTIFACT_DIR)
