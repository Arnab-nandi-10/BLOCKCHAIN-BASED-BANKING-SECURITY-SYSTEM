from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, Index, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class BehaviorProfile(Base):
    """Rolling customer/account profile used by the behavioral engine."""

    __tablename__ = "behavior_profiles"
    __table_args__ = (
        UniqueConstraint("tenant_id", "customer_id", name="uq_behavior_profiles_tenant_customer"),
        Index("idx_behavior_profiles_tenant_customer", "tenant_id", "customer_id"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    tenant_id: Mapped[str] = mapped_column(String(100), nullable=False)
    customer_id: Mapped[str] = mapped_column(String(120), nullable=False)
    total_transactions: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    amount_mean: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    amount_m2: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    max_amount: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    first_transaction_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_transaction_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    night_transaction_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    weekend_transaction_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    high_risk_currency_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    cross_border_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
    )
