from __future__ import annotations

from app.models.schemas import SignalBreakdown
from app.services.risk_context import RiskSignal


class ExplainabilityService:
    """Formats weighted signals into audit-friendly explanations."""

    def build(
        self,
        signals: list[RiskSignal],
    ) -> tuple[list[str], list[str], list[SignalBreakdown]]:
        if not signals:
            return (
                [],
                ["No material fraud or AML monitoring signals exceeded the review thresholds."],
                [],
            )

        ordered = sorted(signals, key=lambda item: item.weight, reverse=True)
        triggered_rules = [signal.key for signal in ordered]
        explanations = [signal.explanation for signal in ordered[:6]]
        breakdown = [
            SignalBreakdown(
                key=signal.key,
                source=signal.source,
                weight=round(signal.weight, 4),
                severity=signal.severity,
                explanation=signal.explanation,
                evidence=signal.evidence,
            )
            for signal in ordered
        ]
        return triggered_rules, explanations, breakdown
