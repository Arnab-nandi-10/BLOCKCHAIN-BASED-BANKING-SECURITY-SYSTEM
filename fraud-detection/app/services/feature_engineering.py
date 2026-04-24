from __future__ import annotations

import hashlib
import math
import re
from datetime import datetime, timezone
from typing import Mapping

import numpy as np

from app.core.config import settings
from app.models.schemas import FraudScoreRequest
from app.services.risk_context import HistorySummary, NormalizedTransaction
from app.services.tenant_runtime_config import TenantRuntimeConfig

FEATURE_NAMES: list[str] = [
    "amount",
    "amount_log",
    "amount_sqrt",
    "amount_gt_50k",
    "amount_gt_500k",
    "amount_gt_1m",
    "hour_of_day",
    "hour_sin",
    "hour_cos",
    "weekday",
    "is_weekend",
    "is_unusual_hour",
    "tx_type_transfer",
    "tx_type_payment",
    "tx_type_withdrawal",
    "tx_type_deposit",
    "currency_low_risk",
    "currency_high_risk",
    "network_permissioned",
    "network_public",
    "same_account",
    "cross_border",
    "from_hash_mod",
    "to_hash_mod",
    "account_prefix_match",
    "from_digit_ratio",
    "to_digit_ratio",
    "from_pattern_score",
    "to_pattern_score",
    "origin_country_high_risk",
    "destination_country_high_risk",
    "kyc_verified",
    "kyc_risk_low",
    "kyc_risk_medium",
    "kyc_risk_high",
    "receiver_verified",
    "wallet_risk_low",
    "wallet_risk_medium",
    "wallet_risk_high",
    "sanctions_hit",
    "pep_flag",
    "travel_rule_received",
    "onboarding_age_log",
    "history_total_log",
    "avg_amount",
    "amount_to_avg_ratio",
    "amount_zscore",
    "recent_10m_count",
    "recent_1h_count",
    "recent_24h_count",
    "recent_24h_amount_log",
    "velocity_to_daily_baseline",
    "high_value_24h_count",
    "near_threshold_24h_count",
    "receiver_seen_count",
    "new_receiver_flag",
    "recent_unique_receivers",
    "seen_device_before",
    "seen_ip_before",
    "seen_country_before",
    "days_since_last_tx",
    "night_ratio",
    "weekend_ratio",
    "cross_border_ratio",
]
TOTAL_FEATURES = len(FEATURE_NAMES)

_ACCOUNT_PREFIX_RE = re.compile(r"[^A-Z0-9]")
_DIGIT_RE = re.compile(r"\d")


class FeatureEngineer:
    """Normalise incoming requests and build a 50+ feature vector."""

    def normalize_request(self, request: FraudScoreRequest) -> NormalizedTransaction:
        metadata = {
            str(key): str(value)
            for key, value in (request.metadata or {}).items()
            if value is not None
        }

        customer_id = (
            self._metadata_get(metadata, "customerId")
            or (request.customer.customerId if request.customer else None)
            or request.fromAccount
        )
        kyc_verified = self._metadata_get_bool(
            metadata,
            "kycVerified",
            default=request.customer.kycVerified if request.customer else True,
        )
        kyc_risk_band = (
            self._metadata_get(metadata, "kycRiskBand")
            or (request.customer.kycRiskBand if request.customer else "MEDIUM")
        ).upper()
        onboarding_age_days = self._metadata_get_int(
            metadata,
            "onboardingAgeDays",
            default=request.customer.onboardingAgeDays if request.customer else 365,
        )
        sanctions_hit = self._metadata_get_bool(
            metadata,
            "sanctionsHit",
            default=request.customer.sanctionsHit if request.customer else False,
        )
        pep_flag = self._metadata_get_bool(
            metadata,
            "pepFlag",
            default=request.customer.pepFlag if request.customer else False,
        )
        receiver_verified = self._metadata_get_bool(
            metadata,
            "receiverVerified",
            default=(
                request.counterparty.receiverVerified if request.counterparty else False
            ),
        )
        receiver_age_days = self._metadata_get_int(
            metadata,
            "receiverAgeDays",
            default=request.counterparty.receiverAgeDays if request.counterparty else 0,
        )
        wallet_risk_level = (
            self._metadata_get(metadata, "walletRiskLevel")
            or (request.counterparty.walletRiskLevel if request.counterparty else "LOW")
        ).upper()
        channel = (
            self._metadata_get(metadata, "channel")
            or (request.channel.channel if request.channel else "API")
        ).upper()
        origin_country = (
            self._metadata_get(metadata, "originCountry")
            or (request.channel.originCountry if request.channel else "UNKNOWN")
        ).upper()
        destination_country = (
            self._metadata_get(metadata, "destinationCountry")
            or (request.channel.destinationCountry if request.channel else origin_country)
        ).upper()
        blockchain_network = (
            self._metadata_get(metadata, "blockchainNetwork")
            or (request.channel.blockchainNetwork if request.channel else "permissioned")
        )
        travel_rule_received = self._metadata_get_bool(
            metadata,
            "travelRuleReceived",
            default=request.channel.travelRuleReceived if request.channel else False,
        )
        device_id = self._metadata_get(metadata, "deviceId") or (
            request.channel.deviceId if request.channel else None
        )

        timestamp = request.transactionTimestamp or datetime.now(timezone.utc)
        if timestamp.tzinfo is None:
            timestamp = timestamp.replace(tzinfo=timezone.utc)

        return NormalizedTransaction(
            transaction_id=request.transactionId,
            tenant_id=request.tenantId,
            customer_id=customer_id,
            from_account=request.fromAccount,
            to_account=request.toAccount,
            amount=float(request.amount),
            currency=request.currency.upper(),
            transaction_type=request.transactionType.upper(),
            transaction_timestamp=timestamp.astimezone(timezone.utc),
            hour_of_day=timestamp.astimezone(timezone.utc).hour,
            weekday=timestamp.astimezone(timezone.utc).weekday(),
            is_weekend=timestamp.astimezone(timezone.utc).weekday() >= 5,
            ip_address=request.ipAddress,
            device_id=device_id,
            channel=channel,
            origin_country=origin_country,
            destination_country=destination_country,
            blockchain_network=blockchain_network,
            wallet_risk_level=wallet_risk_level,
            kyc_verified=kyc_verified,
            kyc_risk_band=kyc_risk_band,
            onboarding_age_days=onboarding_age_days,
            receiver_verified=receiver_verified,
            receiver_age_days=receiver_age_days,
            sanctions_hit=sanctions_hit,
            pep_flag=pep_flag,
            travel_rule_received=travel_rule_received,
            metadata=metadata,
        )

    def extract_features(
        self,
        tx: NormalizedTransaction,
        history: HistorySummary,
    ) -> np.ndarray:
        avg_amount = max(history.avg_amount, 1.0)
        amount_ratio = tx.amount / avg_amount
        z_denominator = max(history.amount_stddev, max(history.avg_amount * 0.35, 100.0))
        amount_zscore = (tx.amount - history.avg_amount) / z_denominator
        daily_baseline = max(history.avg_daily_transactions, 0.25)
        velocity_baseline = max(daily_baseline / 144.0, 0.05)
        velocity_ratio = history.recent_10m_count / velocity_baseline
        last_gap_days = (
            max(
                (tx.transaction_timestamp - history.last_transaction_at).total_seconds()
                / 86_400.0,
                0.0,
            )
            if history.last_transaction_at
            else 365.0
        )
        runtime_config = TenantRuntimeConfig.from_metadata(tx.metadata)
        network_lower = tx.blockchain_network.lower()
        origin_country_high_risk = tx.origin_country in runtime_config.high_risk_countries
        destination_country_high_risk = tx.destination_country in runtime_config.high_risk_countries
        currency_low_risk = tx.currency in settings.LOW_RISK_CURRENCY_CODES_SET
        currency_high_risk = tx.currency in runtime_config.high_risk_currencies
        same_account = tx.from_account == tx.to_account
        new_receiver = history.total_transactions > 0 and history.receiver_seen_count == 0

        features = np.array(
            [
                tx.amount,
                math.log1p(tx.amount),
                math.sqrt(tx.amount),
                1.0 if tx.amount >= 50_000.0 else 0.0,
                1.0 if tx.amount >= 500_000.0 else 0.0,
                1.0 if tx.amount >= 1_000_000.0 else 0.0,
                float(tx.hour_of_day),
                math.sin((2.0 * math.pi * tx.hour_of_day) / 24.0),
                math.cos((2.0 * math.pi * tx.hour_of_day) / 24.0),
                float(tx.weekday),
                1.0 if tx.is_weekend else 0.0,
                1.0 if runtime_config.is_unusual_hour(tx.hour_of_day) else 0.0,
                1.0 if tx.transaction_type == "TRANSFER" else 0.0,
                1.0 if tx.transaction_type == "PAYMENT" else 0.0,
                1.0 if tx.transaction_type == "WITHDRAWAL" else 0.0,
                1.0 if tx.transaction_type == "DEPOSIT" else 0.0,
                1.0 if currency_low_risk else 0.0,
                1.0 if currency_high_risk else 0.0,
                1.0 if any(token in network_lower for token in ("fabric", "permissioned")) else 0.0,
                1.0 if any(token in network_lower for token in ("ethereum", "bitcoin", "solana", "public")) else 0.0,
                1.0 if same_account else 0.0,
                1.0 if tx.origin_country != tx.destination_country else 0.0,
                self._hash_mod(tx.from_account),
                self._hash_mod(tx.to_account),
                1.0 if self._prefix(tx.from_account) == self._prefix(tx.to_account) else 0.0,
                self._digit_ratio(tx.from_account),
                self._digit_ratio(tx.to_account),
                self._account_pattern_score(tx.from_account),
                self._account_pattern_score(tx.to_account),
                1.0 if origin_country_high_risk else 0.0,
                1.0 if destination_country_high_risk else 0.0,
                1.0 if tx.kyc_verified else 0.0,
                1.0 if tx.kyc_risk_band == "LOW" else 0.0,
                1.0 if tx.kyc_risk_band == "MEDIUM" else 0.0,
                1.0 if tx.kyc_risk_band == "HIGH" else 0.0,
                1.0 if tx.receiver_verified else 0.0,
                1.0 if tx.wallet_risk_level == "LOW" else 0.0,
                1.0 if tx.wallet_risk_level == "MEDIUM" else 0.0,
                1.0 if tx.wallet_risk_level == "HIGH" else 0.0,
                1.0 if tx.sanctions_hit else 0.0,
                1.0 if tx.pep_flag else 0.0,
                1.0 if tx.travel_rule_received else 0.0,
                math.log1p(max(tx.onboarding_age_days, 0)),
                math.log1p(history.total_transactions),
                history.avg_amount,
                amount_ratio,
                amount_zscore,
                float(history.recent_10m_count),
                float(history.recent_1h_count),
                float(history.recent_24h_count),
                math.log1p(history.recent_24h_total_amount),
                velocity_ratio,
                float(history.recent_high_value_24h_count),
                float(history.near_threshold_24h_count),
                float(history.receiver_seen_count),
                1.0 if new_receiver else 0.0,
                float(history.recent_unique_receivers),
                1.0 if history.seen_device_before else 0.0,
                1.0 if history.seen_ip_before else 0.0,
                1.0 if history.seen_country_before else 0.0,
                last_gap_days,
                history.night_ratio,
                history.weekend_ratio,
                history.cross_border_ratio,
            ],
            dtype=np.float64,
        )

        return features

    def _metadata_get(self, metadata: Mapping[str, str], key: str) -> str | None:
        value = metadata.get(key)
        if value is None:
            return None
        stripped = value.strip()
        return stripped or None

    def _metadata_get_bool(
        self,
        metadata: Mapping[str, str],
        key: str,
        *,
        default: bool,
    ) -> bool:
        raw = self._metadata_get(metadata, key)
        if raw is None:
            return default
        return raw.lower() in {"1", "true", "yes", "y", "on"}

    def _metadata_get_int(
        self,
        metadata: Mapping[str, str],
        key: str,
        *,
        default: int,
    ) -> int:
        raw = self._metadata_get(metadata, key)
        if raw is None:
            return default
        try:
            return max(int(raw), 0)
        except ValueError:
            return default

    def _hash_mod(self, value: str) -> float:
        digest = hashlib.md5(value.encode("utf-8"), usedforsecurity=False).hexdigest()
        return float(int(digest, 16) % 10_000) / 10_000.0

    def _prefix(self, value: str) -> str:
        normalised = _ACCOUNT_PREFIX_RE.sub("", value.upper())
        return normalised[:4]

    def _digit_ratio(self, value: str) -> float:
        if not value:
            return 0.0
        return len(_DIGIT_RE.findall(value)) / len(value)

    def _account_pattern_score(self, value: str) -> float:
        if not value:
            return 0.0

        compact = _ACCOUNT_PREFIX_RE.sub("", value.upper())
        if not compact:
            return 0.0

        repeated = max(compact.count(char) for char in set(compact)) / len(compact)
        sequential_bonus = 0.0
        if compact in {"0000", "1111", "1234"} or compact.endswith("0000"):
            sequential_bonus = 0.25
        return min(1.0, repeated + sequential_bonus)
