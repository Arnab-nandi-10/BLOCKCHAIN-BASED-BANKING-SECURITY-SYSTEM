from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from app.core.config import settings


def _csv_to_set(value: str) -> set[str]:
    return {item.strip().upper() for item in value.split(",") if item.strip()}


@dataclass(slots=True)
class TenantRuntimeConfig:
    block_threshold: float
    hold_threshold: float
    max_transaction_amount: float
    unusual_hour_start: int
    unusual_hour_end: int
    high_risk_currencies: set[str]
    high_risk_countries: set[str]
    blockchain_mode: str
    require_real_fabric: bool
    fallback_allowed: bool

    @classmethod
    def from_metadata(cls, metadata: Mapping[str, str] | None) -> "TenantRuntimeConfig":
        source = metadata or {}
        return cls(
            block_threshold=_float_value(
                source,
                "fraud.threshold.block",
                default=settings.FRAUD_SCORE_THRESHOLD_BLOCK,
                min_value=0.01,
                max_value=0.99,
            ),
            hold_threshold=_float_value(
                source,
                "fraud.threshold.hold",
                default=settings.FRAUD_SCORE_THRESHOLD_HOLD,
                min_value=0.01,
                max_value=0.99,
            ),
            max_transaction_amount=_float_value(
                source,
                "fraud.maxTransactionAmount",
                default=settings.MAX_TRANSACTION_AMOUNT,
                min_value=1.0,
                max_value=10_000_000_000.0,
            ),
            unusual_hour_start=_int_value(
                source,
                "fraud.unusualHourStart",
                default=settings.UNUSUAL_HOUR_START,
                min_value=0,
                max_value=23,
            ),
            unusual_hour_end=_int_value(
                source,
                "fraud.unusualHourEnd",
                default=settings.UNUSUAL_HOUR_END,
                min_value=0,
                max_value=23,
            ),
            high_risk_currencies=_csv_set_value(
                source,
                "fraud.highRiskCurrencies",
                default=settings.HIGH_RISK_CURRENCY_CODES,
            ),
            high_risk_countries=_csv_set_value(
                source,
                "fraud.highRiskCountries",
                default=settings.HIGH_RISK_COUNTRY_CODES,
            ),
            blockchain_mode=_str_value(source, "blockchain.mode", default="AUTO"),
            require_real_fabric=_bool_value(
                source,
                "blockchain.requireRealFabric",
                default=False,
            ),
            fallback_allowed=_bool_value(
                source,
                "blockchain.fallbackAllowed",
                default=True,
            ),
        ).normalized()

    def normalized(self) -> "TenantRuntimeConfig":
        hold_threshold = min(self.hold_threshold, self.block_threshold)
        return TenantRuntimeConfig(
            block_threshold=max(self.block_threshold, hold_threshold),
            hold_threshold=hold_threshold,
            max_transaction_amount=self.max_transaction_amount,
            unusual_hour_start=self.unusual_hour_start,
            unusual_hour_end=self.unusual_hour_end,
            high_risk_currencies=self.high_risk_currencies,
            high_risk_countries=self.high_risk_countries,
            blockchain_mode=self.blockchain_mode.strip().upper(),
            require_real_fabric=self.require_real_fabric,
            fallback_allowed=self.fallback_allowed,
        )

    def is_unusual_hour(self, hour: int) -> bool:
        if self.unusual_hour_start <= self.unusual_hour_end:
            return self.unusual_hour_start <= hour <= self.unusual_hour_end
        return hour >= self.unusual_hour_start or hour <= self.unusual_hour_end


def _str_value(metadata: Mapping[str, str], key: str, *, default: str) -> str:
    raw = metadata.get(key)
    if raw is None:
        return default
    stripped = raw.strip()
    return stripped or default


def _bool_value(metadata: Mapping[str, str], key: str, *, default: bool) -> bool:
    raw = metadata.get(key)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "y", "on"}


def _int_value(
    metadata: Mapping[str, str],
    key: str,
    *,
    default: int,
    min_value: int,
    max_value: int,
) -> int:
    raw = metadata.get(key)
    if raw is None:
        return default
    try:
        parsed = int(raw.strip())
    except (TypeError, ValueError):
        return default
    return max(min_value, min(max_value, parsed))


def _float_value(
    metadata: Mapping[str, str],
    key: str,
    *,
    default: float,
    min_value: float,
    max_value: float,
) -> float:
    raw = metadata.get(key)
    if raw is None:
        return default
    try:
        parsed = float(raw.strip())
    except (TypeError, ValueError):
        return default
    return max(min_value, min(max_value, parsed))


def _csv_set_value(metadata: Mapping[str, str], key: str, *, default: str) -> set[str]:
    raw = metadata.get(key)
    if raw is None or not raw.strip():
        return _csv_to_set(default)
    return _csv_to_set(raw)
