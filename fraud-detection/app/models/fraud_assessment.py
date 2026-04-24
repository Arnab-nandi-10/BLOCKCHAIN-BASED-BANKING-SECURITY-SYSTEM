from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import JSON, Boolean, DateTime, Float, Index, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class FraudAssessment(Base):
    """Immutable fraud scoring outcome used for behavior analytics and audit."""

    __tablename__ = "fraud_assessments"
    __table_args__ = (
        UniqueConstraint("transaction_id", name="uq_fraud_assessments_transaction_id"),
        Index("idx_fraud_assessments_tenant_customer_ts", "tenant_id", "customer_id", "transaction_timestamp"),
        Index("idx_fraud_assessments_tenant_from_ts", "tenant_id", "from_account", "transaction_timestamp"),
        Index("idx_fraud_assessments_tenant_score", "tenant_id", "final_score"),
        Index("idx_fraud_assessments_decision", "decision"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    transaction_id: Mapped[str] = mapped_column(String(100), nullable=False)
    tenant_id: Mapped[str] = mapped_column(String(100), nullable=False)
    customer_id: Mapped[str] = mapped_column(String(120), nullable=False)
    from_account: Mapped[str] = mapped_column(String(150), nullable=False)
    to_account: Mapped[str] = mapped_column(String(150), nullable=False)
    amount: Mapped[float] = mapped_column(Float, nullable=False)
    currency: Mapped[str] = mapped_column(String(12), nullable=False)
    transaction_type: Mapped[str] = mapped_column(String(40), nullable=False)
    transaction_timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ip_address: Mapped[str | None] = mapped_column(String(64), nullable=True)
    device_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    channel: Mapped[str] = mapped_column(String(32), nullable=False)
    origin_country: Mapped[str] = mapped_column(String(12), nullable=False)
    destination_country: Mapped[str] = mapped_column(String(12), nullable=False)
    blockchain_network: Mapped[str] = mapped_column(String(32), nullable=False)
    wallet_risk_level: Mapped[str] = mapped_column(String(16), nullable=False)
    kyc_verified: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    kyc_risk_band: Mapped[str] = mapped_column(String(16), nullable=False)
    sanctions_hit: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    pep_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    request_metadata: Mapped[dict] = mapped_column("metadata", JSON, nullable=False, default=dict)
    feature_vector: Mapped[list[float]] = mapped_column(JSON, nullable=False, default=list)
    ml_score: Mapped[float] = mapped_column(Float, nullable=False)
    rule_score: Mapped[float] = mapped_column(Float, nullable=False)
    behavioral_score: Mapped[float] = mapped_column(Float, nullable=False)
    final_score: Mapped[float] = mapped_column(Float, nullable=False)
    risk_level: Mapped[str] = mapped_column(String(16), nullable=False)
    decision: Mapped[str] = mapped_column(String(16), nullable=False)
    recommendation: Mapped[str] = mapped_column(String(64), nullable=False)
    review_required: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    fallback_used: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    triggered_rules: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    explanations: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    signal_breakdown: Mapped[list[dict]] = mapped_column(JSON, nullable=False, default=list)
    model_version: Mapped[str] = mapped_column(String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
    )
