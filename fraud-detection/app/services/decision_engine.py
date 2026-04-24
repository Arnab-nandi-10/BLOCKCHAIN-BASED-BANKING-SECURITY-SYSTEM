from __future__ import annotations

from dataclasses import dataclass

from app.services.tenant_runtime_config import TenantRuntimeConfig


@dataclass(slots=True)
class DecisionOutcome:
    risk_level: str
    decision: str
    recommendation: str
    review_required: bool


class DecisionEngine:
    """Maps the hybrid score into an operational risk decision."""

    def evaluate(
        self,
        score: float,
        *,
        force_review: bool = False,
        metadata: dict[str, str] | None = None,
    ) -> DecisionOutcome:
        runtime_config = TenantRuntimeConfig.from_metadata(metadata)
        risk_level = self._risk_level_for_score(score, runtime_config)
        if force_review:
            return DecisionOutcome(
                risk_level=risk_level,
                decision="HOLD",
                recommendation="MANUAL_REVIEW",
                review_required=True,
            )

        if risk_level == "LOW":
            return DecisionOutcome(
                risk_level="LOW",
                decision="ALLOW",
                recommendation="ALLOW",
                review_required=False,
            )
        if risk_level == "MEDIUM":
            return DecisionOutcome(
                risk_level="MEDIUM",
                decision="MONITOR",
                recommendation="MONITOR",
                review_required=False,
            )
        if risk_level == "HIGH":
            return DecisionOutcome(
                risk_level="HIGH",
                decision="HOLD",
                recommendation="MANUAL_REVIEW",
                review_required=True,
            )
        return DecisionOutcome(
            risk_level="CRITICAL",
            decision="BLOCK",
            recommendation="BLOCK_TRANSACTION",
            review_required=True,
        )

    def _risk_level_for_score(self, score: float, runtime_config: TenantRuntimeConfig) -> str:
        low_upper_bound = max(0.10, min(0.50, runtime_config.hold_threshold / 2.0))
        if score <= low_upper_bound:
            return "LOW"
        if score <= runtime_config.hold_threshold:
            return "MEDIUM"
        if score <= runtime_config.block_threshold:
            return "HIGH"
        return "CRITICAL"
