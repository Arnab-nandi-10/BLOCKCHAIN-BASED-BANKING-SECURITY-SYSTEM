from __future__ import annotations

import math
import re

from app.core.config import settings
from app.services.risk_context import EngineResult, HistorySummary, NormalizedTransaction, RiskSignal
from app.services.tenant_runtime_config import TenantRuntimeConfig

_ACCOUNT_COMPACT_RE = re.compile(r"[^A-Z0-9]")
_SEQUENTIAL_ACCOUNT_TOKENS = ("0000", "1111", "1234", "4321", "9999", "ABCDE")


class RuleEngine:
    """Weighted AML and fraud rule engine with additive risk contributions."""

    def evaluate(
        self,
        tx: NormalizedTransaction,
        history: HistorySummary,
    ) -> EngineResult:
        signals: list[RiskSignal] = []
        score = 0.0
        runtime_config = TenantRuntimeConfig.from_metadata(tx.metadata)

        if tx.sanctions_hit:
            signals.append(
                RiskSignal(
                    key="sanctions_or_watchlist_match",
                    source="RULE",
                    weight=0.95,
                    severity="CRITICAL",
                    explanation="Customer or counterparty matched a sanctions/watchlist screening result.",
                )
            )
            return EngineResult(score=0.95, signals=signals)

        if not tx.kyc_verified:
            score += self._add(
                signals,
                key="kyc_incomplete",
                weight=0.12,
                severity="HIGH",
                explanation="KYC profile is incomplete, so the transaction requires additional scrutiny.",
            )

        if tx.amount >= 500_000.0:
            score += self._add(
                signals,
                key="very_high_amount",
                weight=0.12,
                severity="HIGH",
                explanation=f"Transaction amount {tx.amount:,.2f} exceeds the 500k high-risk review threshold.",
            )
        elif tx.amount >= 50_000.0:
            score += self._add(
                signals,
                key="high_amount",
                weight=0.05,
                severity="MEDIUM",
                explanation=f"Transaction amount {tx.amount:,.2f} exceeds the 50k enhanced-monitoring threshold.",
            )

        if 0 < history.total_transactions < 5 and history.avg_amount > 0.0:
            amount_ratio = tx.amount / max(history.avg_amount, 1.0)
            if amount_ratio >= 3.0 and tx.amount >= max(history.avg_amount * 3.0, 20_000.0):
                weight = min(
                    0.09,
                    0.03 + 0.015 * math.log1p(amount_ratio) + 0.005 * history.total_transactions,
                )
                score += self._add(
                    signals,
                    key="adaptive_amount_spike",
                    weight=weight,
                    severity="MEDIUM" if amount_ratio < 5.0 else "HIGH",
                    explanation=(
                        f"Amount is {amount_ratio:.1f}x above the customer's early baseline, "
                        "so it is treated as a weighted adaptive-threshold breach."
                    ),
                    evidence={
                        "amountRatio": round(amount_ratio, 2),
                        "historyTransactions": history.total_transactions,
                    },
                )

        if runtime_config.is_unusual_hour(tx.hour_of_day):
            weight = 0.02 if history.night_ratio >= 0.20 else 0.05
            score += self._add(
                signals,
                key="unusual_hour",
                weight=weight,
                severity="MEDIUM",
                explanation=f"Transaction was submitted at {tx.hour_of_day:02d}:00 UTC, outside the normal business pattern.",
                evidence={"historicalNightRatio": round(history.night_ratio, 3)},
            )

        if history.total_transactions > 0 and history.receiver_seen_count == 0:
            weight = 0.05 + (0.03 if tx.amount >= max(history.avg_amount * 2.0, 10_000.0) else 0.0)
            if not tx.receiver_verified:
                weight += 0.02
            if tx.receiver_age_days <= 30:
                weight += 0.02
            score += self._add(
                signals,
                key="new_receiver",
                weight=min(weight, 0.12),
                severity="HIGH" if tx.amount >= 50_000.0 else "MEDIUM",
                explanation=(
                    "Funds are being sent to a receiver with no prior transaction history on this account."
                ),
                evidence={
                    "receiverSeenCount": history.receiver_seen_count,
                    "receiverAgeDays": tx.receiver_age_days,
                    "receiverVerified": tx.receiver_verified,
                },
            )

        baseline_10m = max(history.avg_daily_transactions / 144.0, 0.05)
        if history.recent_10m_count >= 3 and history.recent_10m_count > baseline_10m * 4.0:
            ratio = history.recent_10m_count / baseline_10m
            weight = min(0.14, 0.04 + 0.02 * math.log1p(ratio))
            score += self._add(
                signals,
                key="velocity_spike",
                weight=weight,
                severity="HIGH",
                explanation=(
                    f"{history.recent_10m_count} transactions were initiated in the last 10 minutes, "
                    f"well above the usual pace."
                ),
                evidence={"velocityRatio": round(ratio, 2)},
            )

        suspicious_pattern = tx.from_account == tx.to_account
        if suspicious_pattern:
            score += self._add(
                signals,
                key="same_account_loop",
                weight=0.18,
                severity="HIGH",
                explanation="Source and destination accounts are the same, which is a common layering/redirection signal.",
            )

        pattern_score, pattern_evidence = self._account_pattern_risk(tx)
        if pattern_score >= 0.65:
            weight = 0.04 if pattern_score < 0.85 else 0.07
            if not tx.receiver_verified:
                weight += 0.02
            score += self._add(
                signals,
                key="suspicious_account_pattern",
                weight=min(weight, 0.10),
                severity="MEDIUM" if pattern_score < 0.85 else "HIGH",
                explanation=(
                    "Account identifiers show synthetic or recycled-looking patterns that warrant enhanced review."
                ),
                evidence=pattern_evidence,
            )

        if tx.origin_country in runtime_config.high_risk_countries or tx.destination_country in runtime_config.high_risk_countries:
            score += self._add(
                signals,
                key="high_risk_jurisdiction",
                weight=0.08,
                severity="HIGH",
                explanation="Transaction involves a country that the compliance ruleset treats as high risk.",
                evidence={
                    "originCountry": tx.origin_country,
                    "destinationCountry": tx.destination_country,
                },
            )

        if tx.currency in runtime_config.high_risk_currencies or tx.wallet_risk_level == "HIGH":
            score += self._add(
                signals,
                key="high_risk_asset_or_wallet",
                weight=0.06,
                severity="MEDIUM",
                explanation="Currency or wallet profile is classified as elevated risk under AML monitoring rules.",
            )

        if (
            history.near_threshold_24h_count >= 3
            and history.recent_24h_total_amount >= 120_000.0
        ):
            score += self._add(
                signals,
                key="possible_structuring",
                weight=0.14,
                severity="HIGH",
                explanation=(
                    "Recent transactions cluster just below the enhanced-review threshold, "
                    "which can indicate structuring to evade monitoring."
                ),
                evidence={
                    "nearThresholdCount24h": history.near_threshold_24h_count,
                    "totalAmount24h": round(history.recent_24h_total_amount, 2),
                },
            )

        if tx.pep_flag and (tx.origin_country != tx.destination_country or tx.amount >= 50_000.0):
            score += self._add(
                signals,
                key="pep_cross_border_activity",
                weight=0.09,
                severity="HIGH",
                explanation="PEP-linked account initiated a high-value or cross-border transfer and requires enhanced due diligence.",
            )

        public_chain = any(
            token in tx.blockchain_network.lower() for token in ("bitcoin", "ethereum", "solana", "public")
        )
        if public_chain and not tx.travel_rule_received and tx.amount >= 10_000.0:
            score += self._add(
                signals,
                key="travel_rule_gap",
                weight=0.05,
                severity="MEDIUM",
                explanation="Travel Rule data was not present for a public-chain transfer that merits enhanced monitoring.",
            )

        return EngineResult(score=min(score, 0.45), signals=signals)

    def _account_pattern_risk(
        self,
        tx: NormalizedTransaction,
    ) -> tuple[float, dict[str, float | bool | str]]:
        from_score, from_digit_ratio, from_sequential = self._account_pattern_metrics(
            tx.from_account
        )
        to_score, to_digit_ratio, to_sequential = self._account_pattern_metrics(tx.to_account)
        max_score = max(from_score, to_score)
        return (
            max_score,
            {
                "fromPatternScore": round(from_score, 3),
                "toPatternScore": round(to_score, 3),
                "fromDigitRatio": round(from_digit_ratio, 3),
                "toDigitRatio": round(to_digit_ratio, 3),
                "sequentialPattern": from_sequential or to_sequential,
            },
        )

    def _account_pattern_metrics(self, account_id: str) -> tuple[float, float, bool]:
        compact = _ACCOUNT_COMPACT_RE.sub("", account_id.upper())
        if not compact:
            return 0.0, 0.0, False

        digit_ratio = sum(char.isdigit() for char in compact) / len(compact)
        repeat_ratio = max(compact.count(char) for char in set(compact)) / len(compact)
        sequential = any(token in compact for token in _SEQUENTIAL_ACCOUNT_TOKENS)
        score = repeat_ratio
        if digit_ratio >= 0.60:
            score += 0.18
        elif digit_ratio >= 0.40:
            score += 0.08
        if sequential:
            score += 0.20
        return min(score, 1.0), digit_ratio, sequential

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
                source="RULE",
                weight=round(weight, 4),
                severity=severity,
                explanation=explanation,
                evidence=evidence or {},
            )
        )
        return weight
