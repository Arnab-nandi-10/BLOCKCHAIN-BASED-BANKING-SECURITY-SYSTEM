import hashlib
import math
from datetime import datetime, timezone
from typing import List

import numpy as np

from app.models.schemas import FraudScoreRequest

# Total feature vector width — must match the training pipeline
TOTAL_FEATURES: int = 50

# Currencies considered low-risk for currency_risk feature
_LOW_RISK_CURRENCIES: frozenset = frozenset({"USD", "EUR", "GBP"})

# Mapping of transaction type strings to integer codes
_TX_TYPE_ENCODING: dict = {
    "TRANSFER": 0,
    "PAYMENT": 1,
    "WITHDRAWAL": 2,
    "DEPOSIT": 3,
}


class FeatureEngineer:
    """Converts a FraudScoreRequest into a fixed-length numpy feature vector
    and derives human-readable fraud rules from the same request."""

    # ------------------------------------------------------------------
    # Feature extraction
    # ------------------------------------------------------------------

    def extract_features(self, request: FraudScoreRequest) -> np.ndarray:
        """Return a float64 ndarray of shape (TOTAL_FEATURES,).

        Feature index map
        -----------------
        0   amount (raw value)
        1   amount_log   = log1p(amount)
        2   is_high_amount        (amount > 50 000)
        3   is_very_high_amount   (amount > 500 000)
        4   hour_of_day   (0-23, UTC)
        5   is_unusual_hour       (hour in 0..5)
        6   is_weekend            (weekday in 5, 6)
        7   currency_risk         (0 = USD/EUR/GBP, 1 = other)
        8   transaction_type_encoded
        9   fromAccount_hash_mod  (MD5 % 1000 / 1000)
        10  toAccount_hash_mod    (MD5 % 1000 / 1000)
        11  same_account          (fromAccount == toAccount)
        12-49 zeros (future feature slots)
        """
        features: List[float] = []

        amount: float = request.amount

        # 0 — amount (raw)
        features.append(float(amount))

        # 1 — log1p(amount)
        features.append(math.log1p(amount))

        # 2 — is_high_amount
        features.append(1.0 if amount > 50_000.0 else 0.0)

        # 3 — is_very_high_amount
        features.append(1.0 if amount > 500_000.0 else 0.0)

        # 4 — hour_of_day  (simulate using current UTC time)
        now: datetime = datetime.now(timezone.utc)
        hour: int = now.hour
        features.append(float(hour))

        # 5 — is_unusual_hour  (midnight … 5 AM inclusive)
        features.append(1.0 if 0 <= hour <= 5 else 0.0)

        # 6 — is_weekend  (Saturday=5, Sunday=6)
        features.append(1.0 if now.weekday() in (5, 6) else 0.0)

        # 7 — currency_risk
        features.append(
            0.0 if request.currency.upper() in _LOW_RISK_CURRENCIES else 1.0
        )

        # 8 — transaction_type_encoded
        features.append(
            float(_TX_TYPE_ENCODING.get(request.transactionType.upper(), 0))
        )

        # 9 — fromAccount hash mod
        from_hash: int = int(
            hashlib.md5(request.fromAccount.encode("utf-8"), usedforsecurity=False).hexdigest(),
            16,
        )
        features.append(float(from_hash % 1000) / 1000.0)

        # 10 — toAccount hash mod
        to_hash: int = int(
            hashlib.md5(request.toAccount.encode("utf-8"), usedforsecurity=False).hexdigest(),
            16,
        )
        features.append(float(to_hash % 1000) / 1000.0)

        # 11 — same_account
        features.append(1.0 if request.fromAccount == request.toAccount else 0.0)

        # 12-49 — pad with zeros to reach TOTAL_FEATURES
        while len(features) < TOTAL_FEATURES:
            features.append(0.0)

        return np.array(features[:TOTAL_FEATURES], dtype=np.float64)

    # ------------------------------------------------------------------
    # Rule derivation
    # ------------------------------------------------------------------

    def get_triggered_rules(
        self, request: FraudScoreRequest, score: float
    ) -> List[str]:
        """Return a list of human-readable rule names that fire for this request.

        Rules are deterministic (based on request fields + current time) so they
        can be audited independently of the ML model score.
        """
        rules: List[str] = []
        amount: float = request.amount
        tx_type: str = request.transactionType.upper()
        now: datetime = datetime.now(timezone.utc)

        # Amount-based rules
        if amount > 500_000.0:
            rules.append("EXTREMELY_HIGH_AMOUNT")

        if amount > 50_000.0 and tx_type == "TRANSFER":
            rules.append("LARGE_AMOUNT_TRANSFER")

        if amount > 10_000.0 and tx_type == "WITHDRAWAL":
            rules.append("HIGH_AMOUNT_WITHDRAWAL")

        # Time-based rules
        if 0 <= now.hour <= 5:
            rules.append("UNUSUAL_HOUR_TRANSACTION")

        # Combined high-risk pattern
        if 0 <= now.hour <= 5 and amount > 50_000.0:
            rules.append("UNUSUAL_HOUR_HIGH_AMOUNT")

        # Currency risk
        if request.currency.upper() not in _LOW_RISK_CURRENCIES:
            rules.append("HIGH_RISK_CURRENCY")

        # Account pattern
        if request.fromAccount == request.toAccount:
            rules.append("SUSPICIOUS_ACCOUNT_PATTERN")

        # Score-based rules (appended last so the list stays deterministic for
        # any given feature set regardless of rule order)
        if score >= 0.8:
            rules.append("HIGH_FRAUD_SCORE")
        elif score >= 0.5:
            rules.append("ELEVATED_FRAUD_SCORE")

        return rules
