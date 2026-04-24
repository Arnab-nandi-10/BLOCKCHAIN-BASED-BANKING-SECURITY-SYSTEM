from __future__ import annotations

import math

from app.services.risk_context import EngineResult, HistorySummary, NormalizedTransaction, RiskSignal
from app.services.tenant_runtime_config import TenantRuntimeConfig


class BehavioralEngine:
    """User-history-aware anomaly engine designed to reduce false positives."""

    def evaluate(
        self,
        tx: NormalizedTransaction,
        history: HistorySummary,
    ) -> EngineResult:
        if history.total_transactions == 0:
            return EngineResult(score=0.0, signals=[])

        signals: list[RiskSignal] = []
        score = 0.0
        runtime_config = TenantRuntimeConfig.from_metadata(tx.metadata)
        avg_amount = max(history.avg_amount, 1.0)
        ratio = tx.amount / avg_amount
        std_denominator = max(history.amount_stddev, max(history.avg_amount * 0.35, 100.0))
        z_score = (tx.amount - history.avg_amount) / std_denominator
        days_since_last = (
            max(
                (tx.transaction_timestamp - history.last_transaction_at).total_seconds()
                / 86_400.0,
                0.0,
            )
            if history.last_transaction_at
            else 0.0
        )

        if history.total_transactions >= 5 and ratio >= 3.0:
            weight = min(0.16, 0.04 * math.log2(ratio) + 0.03 * max(z_score, 0.0))
            score += self._add(
                signals,
                key="amount_deviation",
                weight=weight,
                severity="HIGH" if ratio >= 5.0 else "MEDIUM",
                explanation=f"Amount is {ratio:.1f}x higher than the customer's usual average.",
                evidence={"amountRatio": round(ratio, 2), "zScore": round(z_score, 2)},
            )

        hourly_baseline = max(history.avg_daily_transactions / 24.0, 0.1)
        hourly_ratio = history.recent_1h_count / hourly_baseline
        if history.total_transactions >= 5 and history.recent_1h_count >= 4 and hourly_ratio >= 3.0:
            weight = min(0.12, 0.03 + 0.02 * math.log1p(hourly_ratio))
            score += self._add(
                signals,
                key="frequency_anomaly",
                weight=weight,
                severity="HIGH",
                explanation=(
                    f"Recent activity frequency is {hourly_ratio:.1f}x above the customer's normal hourly pattern."
                ),
                evidence={"recent1hCount": history.recent_1h_count, "hourlyRatio": round(hourly_ratio, 2)},
            )

        if days_since_last >= 30.0 and tx.amount >= max(history.avg_amount * 2.0, 10_000.0):
            score += self._add(
                signals,
                key="dormancy_breakout",
                weight=0.07,
                severity="MEDIUM",
                explanation=(
                    f"Account resumed activity after {days_since_last:.0f} days of dormancy with a materially larger transaction."
                ),
                evidence={"daysSinceLast": round(days_since_last, 1)},
            )

        if not history.seen_device_before and not history.seen_ip_before and tx.amount >= max(history.avg_amount, 5_000.0):
            score += self._add(
                signals,
                key="new_channel_fingerprint",
                weight=0.06,
                severity="MEDIUM",
                explanation="Transaction originated from a new device and IP combination for this customer.",
            )

        if history.total_transactions >= 8 and history.receiver_seen_count == 0 and tx.amount >= max(history.avg_amount * 1.5, 5_000.0):
            score += self._add(
                signals,
                key="counterparty_novelty",
                weight=0.05,
                severity="MEDIUM",
                explanation="Counterparty is new relative to an otherwise established payment pattern.",
            )

        if runtime_config.is_unusual_hour(tx.hour_of_day) and history.night_ratio <= 0.10:
            score += self._add(
                signals,
                key="schedule_shift",
                weight=0.05,
                severity="MEDIUM",
                explanation="Transaction timing differs sharply from the customer's historical activity window.",
                evidence={"historicalNightRatio": round(history.night_ratio, 3)},
            )

        if tx.origin_country != tx.destination_country and history.cross_border_ratio <= 0.10:
            score += self._add(
                signals,
                key="cross_border_shift",
                weight=0.04,
                severity="MEDIUM",
                explanation="Cross-border activity is unusual for this account based on prior behavior.",
                evidence={"historicalCrossBorderRatio": round(history.cross_border_ratio, 3)},
            )

        if (
            history.total_transactions >= 5
            and tx.currency in runtime_config.high_risk_currencies
            and history.high_risk_currency_ratio <= 0.05
        ):
            score += self._add(
                signals,
                key="high_risk_currency_shift",
                weight=0.04,
                severity="MEDIUM",
                explanation="Use of a configured high-risk currency is unusual for this customer's prior activity.",
                evidence={
                    "currency": tx.currency,
                    "historicalHighRiskCurrencyRatio": round(
                        history.high_risk_currency_ratio,
                        3,
                    ),
                },
            )

        return EngineResult(score=min(score, 0.30), signals=signals)

    def _add(
        self,
        signals: list[RiskSignal],
        *,
        key: str,
        weight: float,
        severity: str,
        explanation: str,
        evidence: dict | None = None,
    ) -> float:
        signals.append(
            RiskSignal(
                key=key,
                source="BEHAVIOR",
                weight=round(weight, 4),
                severity=severity,
                explanation=explanation,
                evidence=evidence or {},
            )
        )
        return weight
