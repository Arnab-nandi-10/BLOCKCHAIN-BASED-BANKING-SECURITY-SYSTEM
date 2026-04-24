from datetime import datetime, timezone

from sqlalchemy import JSON, Boolean, DateTime, Float, Index, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class FraudAlert(Base):
    """Persisted fraud alert surfaced to the dashboard."""

    __tablename__ = "fraud_alerts"
    __table_args__ = (
        UniqueConstraint("transaction_id", name="uq_fraud_alerts_transaction_id"),
        Index("idx_fraud_alerts_transaction_id", "transaction_id"),
        Index("idx_fraud_alerts_tenant_detected_at", "tenant_id", "detected_at"),
        Index("idx_fraud_alerts_tenant_risk_level", "tenant_id", "risk_level"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    transaction_id: Mapped[str] = mapped_column(String(100), nullable=False)
    tenant_id: Mapped[str] = mapped_column(String(100), nullable=False)
    score: Mapped[float] = mapped_column(Float, nullable=False)
    risk_level: Mapped[str] = mapped_column(String(20), nullable=False)
    decision: Mapped[str] = mapped_column(String(16), nullable=False, default="HOLD")
    triggered_rules: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    explanations: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    signal_breakdown: Mapped[list[dict]] = mapped_column(JSON, nullable=False, default=list)
    recommendation: Mapped[str] = mapped_column(String(100), nullable=False)
    review_required: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    fallback_used: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    should_block: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    detected_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
    )
