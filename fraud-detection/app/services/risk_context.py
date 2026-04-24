from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any


@dataclass(slots=True)
class NormalizedTransaction:
    transaction_id: str
    tenant_id: str
    customer_id: str
    from_account: str
    to_account: str
    amount: float
    currency: str
    transaction_type: str
    transaction_timestamp: datetime
    hour_of_day: int
    weekday: int
    is_weekend: bool
    ip_address: str | None
    device_id: str | None
    channel: str
    origin_country: str
    destination_country: str
    blockchain_network: str
    wallet_risk_level: str
    kyc_verified: bool
    kyc_risk_band: str
    onboarding_age_days: int
    receiver_verified: bool
    receiver_age_days: int
    sanctions_hit: bool
    pep_flag: bool
    travel_rule_received: bool
    metadata: dict[str, str] = field(default_factory=dict)


@dataclass(slots=True)
class HistorySummary:
    total_transactions: int = 0
    avg_amount: float = 0.0
    amount_stddev: float = 0.0
    max_amount: float = 0.0
    avg_daily_transactions: float = 0.0
    recent_10m_count: int = 0
    recent_1h_count: int = 0
    recent_24h_count: int = 0
    recent_24h_total_amount: float = 0.0
    recent_high_value_24h_count: int = 0
    near_threshold_24h_count: int = 0
    receiver_seen_count: int = 0
    recent_unique_receivers: int = 0
    seen_device_before: bool = False
    seen_ip_before: bool = False
    seen_country_before: bool = False
    night_ratio: float = 0.0
    weekend_ratio: float = 0.0
    high_risk_currency_ratio: float = 0.0
    cross_border_ratio: float = 0.0
    last_transaction_at: datetime | None = None
    first_transaction_at: datetime | None = None


@dataclass(slots=True)
class RiskSignal:
    key: str
    source: str
    weight: float
    explanation: str
    severity: str = "INFO"
    evidence: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class EngineResult:
    score: float
    signals: list[RiskSignal] = field(default_factory=list)


@dataclass(slots=True)
class StoredAssessment:
    transaction_id: str
    response_payload: dict[str, Any]
    created_at: datetime
