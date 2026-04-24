from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from math import sqrt
from typing import Callable, Iterator

from sqlalchemy import and_, distinct, func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db import SessionLocal
from app.models.behavior_profile import BehaviorProfile
from app.models.fraud_alert import FraudAlert
from app.models.fraud_assessment import FraudAssessment
from app.models.schemas import FraudAlertResponse, FraudAlertSummaryResponse, FraudScoreResponse
from app.services.risk_context import HistorySummary, NormalizedTransaction, StoredAssessment
from app.services.tenant_runtime_config import TenantRuntimeConfig


class HistoryRepository:
    """SQL-backed repository for prior assessments and behavior profiles."""

    def __init__(self, session_factory: Callable[[], Session] = SessionLocal) -> None:
        self._session_factory = session_factory

    @contextmanager
    def _session_scope(self) -> Iterator[Session]:
        session = self._session_factory()
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
        finally:
            session.close()

    def get_existing_assessment(self, transaction_id: str) -> StoredAssessment | None:
        with self._session_scope() as session:
            assessment = session.scalar(
                select(FraudAssessment).where(FraudAssessment.transaction_id == transaction_id)
            )
            if assessment is None:
                return None

            payload = {
                "transactionId": assessment.transaction_id,
                "score": assessment.final_score,
                "mlScore": assessment.ml_score,
                "ruleScore": assessment.rule_score,
                "behavioralScore": assessment.behavioral_score,
                "riskLevel": assessment.risk_level,
                "decision": assessment.decision,
                "triggeredRules": assessment.triggered_rules,
                "explanations": assessment.explanations,
                "signalBreakdown": assessment.signal_breakdown,
                "recommendation": assessment.recommendation,
                "reviewRequired": assessment.review_required,
                "shouldBlock": assessment.decision == "BLOCK",
                "fallbackUsed": assessment.fallback_used,
                "modelVersion": assessment.model_version,
                "processingTimeMs": 0.0,
            }
            return StoredAssessment(
                transaction_id=assessment.transaction_id,
                response_payload=payload,
                created_at=assessment.created_at,
            )

    def build_history_summary(self, tx: NormalizedTransaction) -> HistorySummary:
        with self._session_scope() as session:
            profile = session.scalar(
                select(BehaviorProfile).where(
                    BehaviorProfile.tenant_id == tx.tenant_id,
                    BehaviorProfile.customer_id == tx.customer_id,
                )
            )

            base_filter = and_(
                FraudAssessment.tenant_id == tx.tenant_id,
                FraudAssessment.customer_id == tx.customer_id,
                FraudAssessment.transaction_timestamp < tx.transaction_timestamp,
            )

            history = HistorySummary()
            if profile is not None and profile.total_transactions > 0:
                observed_days = 1.0
                if profile.first_transaction_at and profile.last_transaction_at:
                    observed_days = max(
                        (profile.last_transaction_at - profile.first_transaction_at).total_seconds()
                        / 86_400.0,
                        1.0,
                    )

                history.total_transactions = profile.total_transactions
                history.avg_amount = profile.amount_mean
                history.amount_stddev = (
                    sqrt(profile.amount_m2 / (profile.total_transactions - 1))
                    if profile.total_transactions > 1 and profile.amount_m2 > 0
                    else 0.0
                )
                history.max_amount = profile.max_amount
                history.avg_daily_transactions = profile.total_transactions / observed_days
                history.night_ratio = profile.night_transaction_count / profile.total_transactions
                history.weekend_ratio = profile.weekend_transaction_count / profile.total_transactions
                history.high_risk_currency_ratio = (
                    profile.high_risk_currency_count / profile.total_transactions
                )
                history.cross_border_ratio = profile.cross_border_count / profile.total_transactions
                history.last_transaction_at = profile.last_transaction_at
                history.first_transaction_at = profile.first_transaction_at

            history.recent_10m_count = self._count_since(session, base_filter, tx, minutes=10)
            history.recent_1h_count = self._count_since(session, base_filter, tx, hours=1)
            history.recent_24h_count = self._count_since(session, base_filter, tx, hours=24)
            history.recent_24h_total_amount = self._sum_since(session, base_filter, tx, hours=24)
            history.recent_high_value_24h_count = self._count_since(
                session,
                and_(base_filter, FraudAssessment.amount >= 50_000.0),
                tx,
                hours=24,
            )
            history.near_threshold_24h_count = self._count_since(
                session,
                and_(
                    base_filter,
                    FraudAssessment.amount >= 40_000.0,
                    FraudAssessment.amount < 50_000.0,
                ),
                tx,
                hours=24,
            )

            history.receiver_seen_count = int(
                session.scalar(
                    select(func.count())
                    .select_from(FraudAssessment)
                    .where(base_filter, FraudAssessment.to_account == tx.to_account)
                )
                or 0
            )
            history.recent_unique_receivers = int(
                session.scalar(
                    select(func.count(distinct(FraudAssessment.to_account)))
                    .select_from(FraudAssessment)
                    .where(
                        base_filter,
                        FraudAssessment.transaction_timestamp
                        >= tx.transaction_timestamp - timedelta(days=30),
                    )
                )
                or 0
            )

            if tx.device_id:
                history.seen_device_before = bool(
                    session.scalar(
                        select(func.count())
                        .select_from(FraudAssessment)
                        .where(base_filter, FraudAssessment.device_id == tx.device_id)
                    )
                )
            if tx.ip_address:
                history.seen_ip_before = bool(
                    session.scalar(
                        select(func.count())
                        .select_from(FraudAssessment)
                        .where(base_filter, FraudAssessment.ip_address == tx.ip_address)
                    )
                )
            if tx.origin_country and tx.origin_country != "UNKNOWN":
                history.seen_country_before = bool(
                    session.scalar(
                        select(func.count())
                        .select_from(FraudAssessment)
                        .where(base_filter, FraudAssessment.origin_country == tx.origin_country)
                    )
                )

            return history

    def save_assessment(
        self,
        tx: NormalizedTransaction,
        feature_vector: list[float],
        response: FraudScoreResponse,
    ) -> None:
        with self._session_scope() as session:
            existing = session.scalar(
                select(FraudAssessment).where(
                    FraudAssessment.transaction_id == tx.transaction_id
                )
            )
            if existing is not None:
                return

            assessment = FraudAssessment(
                transaction_id=tx.transaction_id,
                tenant_id=tx.tenant_id,
                customer_id=tx.customer_id,
                from_account=tx.from_account,
                to_account=tx.to_account,
                amount=tx.amount,
                currency=tx.currency,
                transaction_type=tx.transaction_type,
                transaction_timestamp=tx.transaction_timestamp,
                ip_address=tx.ip_address,
                device_id=tx.device_id,
                channel=tx.channel,
                origin_country=tx.origin_country,
                destination_country=tx.destination_country,
                blockchain_network=tx.blockchain_network,
                wallet_risk_level=tx.wallet_risk_level,
                kyc_verified=tx.kyc_verified,
                kyc_risk_band=tx.kyc_risk_band,
                sanctions_hit=tx.sanctions_hit,
                pep_flag=tx.pep_flag,
                request_metadata=tx.metadata,
                feature_vector=feature_vector,
                ml_score=response.mlScore,
                rule_score=response.ruleScore,
                behavioral_score=response.behavioralScore,
                final_score=response.score,
                risk_level=response.riskLevel,
                decision=response.decision,
                recommendation=response.recommendation,
                review_required=response.reviewRequired,
                fallback_used=response.fallbackUsed,
                triggered_rules=response.triggeredRules,
                explanations=response.explanations,
                signal_breakdown=[signal.model_dump() for signal in response.signalBreakdown],
                model_version=response.modelVersion,
            )
            session.add(assessment)
            try:
                session.flush()
            except IntegrityError as exc:
                session.rollback()
                error_text = str(exc.orig) if exc.orig is not None else str(exc)
                if "uq_fraud_assessments_transaction_id" in error_text or "duplicate key value" in error_text:
                    return
                raise

            if self._should_update_behavior_profile(response):
                self._upsert_behavior_profile(session, tx)

            if response.reviewRequired or response.decision in {"HOLD", "BLOCK"}:
                alert = session.scalar(
                    select(FraudAlert).where(FraudAlert.transaction_id == tx.transaction_id)
                )
                if alert is None:
                    alert = FraudAlert(
                        transaction_id=tx.transaction_id,
                        tenant_id=tx.tenant_id,
                        score=response.score,
                        risk_level=response.riskLevel,
                        decision=response.decision,
                        triggered_rules=response.triggeredRules,
                        explanations=response.explanations,
                        signal_breakdown=[
                            signal.model_dump() for signal in response.signalBreakdown
                        ],
                        recommendation=response.recommendation,
                        review_required=response.reviewRequired,
                        fallback_used=response.fallbackUsed,
                        should_block=response.shouldBlock,
                    )
                    session.add(alert)
                else:
                    alert.score = response.score
                    alert.risk_level = response.riskLevel
                    alert.decision = response.decision
                    alert.triggered_rules = response.triggeredRules
                    alert.explanations = response.explanations
                    alert.signal_breakdown = [
                        signal.model_dump() for signal in response.signalBreakdown
                    ]
                    alert.recommendation = response.recommendation
                    alert.review_required = response.reviewRequired
                    alert.fallback_used = response.fallbackUsed
                    alert.should_block = response.shouldBlock

    def list_alerts(
        self,
        tenant_id: str,
        page: int,
        size: int,
        *,
        risk_level: str | None = None,
        decision: str | None = None,
        review_required: bool | None = None,
        search: str | None = None,
        from_date: datetime | None = None,
        to_date: datetime | None = None,
    ) -> tuple[list[FraudAlertResponse], int]:
        with self._session_scope() as session:
            filters = self._alert_filters(
                tenant_id=tenant_id,
                risk_level=risk_level,
                decision=decision,
                review_required=review_required,
                search=search,
                from_date=from_date,
                to_date=to_date,
            )
            total = int(
                session.scalar(
                    select(func.count()).select_from(FraudAlert).where(*filters)
                )
                or 0
            )
            alerts = list(
                session.scalars(
                    select(FraudAlert)
                    .where(*filters)
                    .order_by(FraudAlert.detected_at.desc())
                    .offset(page * size)
                    .limit(size)
                )
            )

            payload = [
                FraudAlertResponse(
                    transactionId=alert.transaction_id,
                    tenantId=alert.tenant_id,
                    score=alert.score,
                    riskLevel=alert.risk_level,
                    decision=alert.decision,
                    recommendation=alert.recommendation,
                    reviewRequired=alert.review_required,
                    shouldBlock=alert.should_block,
                    fallbackUsed=alert.fallback_used,
                    triggeredRules=alert.triggered_rules,
                    explanations=alert.explanations,
                    signalBreakdown=alert.signal_breakdown,
                    detectedAt=alert.detected_at,
                )
                for alert in alerts
            ]
            return payload, total

    def get_alert_by_transaction_id(
        self,
        tenant_id: str,
        transaction_id: str,
    ) -> FraudAlertResponse | None:
        with self._session_scope() as session:
            alert = session.scalar(
                select(FraudAlert).where(
                    FraudAlert.tenant_id == tenant_id,
                    FraudAlert.transaction_id == transaction_id,
                )
            )
            if alert is None:
                return None
            return self._to_alert_response(alert)

    def summarize_alerts(
        self,
        tenant_id: str,
        *,
        risk_level: str | None = None,
        decision: str | None = None,
        review_required: bool | None = None,
        search: str | None = None,
        from_date: datetime | None = None,
        to_date: datetime | None = None,
    ) -> FraudAlertSummaryResponse:
        with self._session_scope() as session:
            filters = self._alert_filters(
                tenant_id=tenant_id,
                risk_level=risk_level,
                decision=decision,
                review_required=review_required,
                search=search,
                from_date=from_date,
                to_date=to_date,
            )
            alerts = list(
                session.scalars(
                    select(FraudAlert)
                    .where(*filters)
                    .order_by(FraudAlert.detected_at.desc())
                )
            )

            decision_counts: dict[str, int] = {"ALLOW": 0, "MONITOR": 0, "HOLD": 0, "BLOCK": 0}
            risk_counts: dict[str, int] = {"LOW": 0, "MEDIUM": 0, "HIGH": 0, "CRITICAL": 0}

            for alert in alerts:
                decision_counts[alert.decision] = decision_counts.get(alert.decision, 0) + 1
                risk_counts[alert.risk_level] = risk_counts.get(alert.risk_level, 0) + 1

            return FraudAlertSummaryResponse(
                tenantId=tenant_id,
                totalAlerts=len(alerts),
                reviewRequiredCount=sum(1 for alert in alerts if alert.review_required),
                decisionCounts=decision_counts,
                riskLevelCounts=risk_counts,
                fromDate=from_date,
                toDate=to_date,
            )

    def _should_update_behavior_profile(self, response: FraudScoreResponse) -> bool:
        return (
            not response.fallbackUsed
            and not response.reviewRequired
            and response.decision in {"ALLOW", "MONITOR"}
        )

    def _alert_filters(
        self,
        *,
        tenant_id: str,
        risk_level: str | None,
        decision: str | None,
        review_required: bool | None,
        search: str | None,
        from_date: datetime | None,
        to_date: datetime | None,
    ) -> list:
        filters = [FraudAlert.tenant_id == tenant_id]

        if risk_level:
            filters.append(FraudAlert.risk_level == risk_level.strip().upper())
        if decision:
            filters.append(FraudAlert.decision == decision.strip().upper())
        if review_required is not None:
            filters.append(FraudAlert.review_required == review_required)
        if from_date is not None:
            filters.append(FraudAlert.detected_at >= from_date)
        if to_date is not None:
            filters.append(FraudAlert.detected_at <= to_date)
        if search:
            pattern = f"%{search.strip().lower()}%"
            filters.append(
                or_(
                    func.lower(FraudAlert.transaction_id).like(pattern),
                    func.lower(FraudAlert.recommendation).like(pattern),
                )
            )
        return filters

    def _to_alert_response(self, alert: FraudAlert) -> FraudAlertResponse:
        return FraudAlertResponse(
            transactionId=alert.transaction_id,
            tenantId=alert.tenant_id,
            score=alert.score,
            riskLevel=alert.risk_level,
            decision=alert.decision,
            recommendation=alert.recommendation,
            reviewRequired=alert.review_required,
            shouldBlock=alert.should_block,
            fallbackUsed=alert.fallback_used,
            triggeredRules=alert.triggered_rules,
            explanations=alert.explanations,
            signalBreakdown=alert.signal_breakdown,
            detectedAt=alert.detected_at,
        )

    def _count_since(
        self,
        session: Session,
        base_filter,
        tx: NormalizedTransaction,
        *,
        minutes: int = 0,
        hours: int = 0,
    ) -> int:
        cutoff = tx.transaction_timestamp - timedelta(minutes=minutes, hours=hours)
        return int(
            session.scalar(
                select(func.count())
                .select_from(FraudAssessment)
                .where(base_filter, FraudAssessment.transaction_timestamp >= cutoff)
            )
            or 0
        )

    def _sum_since(
        self,
        session: Session,
        base_filter,
        tx: NormalizedTransaction,
        *,
        hours: int,
    ) -> float:
        cutoff = tx.transaction_timestamp - timedelta(hours=hours)
        return float(
            session.scalar(
                select(func.coalesce(func.sum(FraudAssessment.amount), 0.0))
                .select_from(FraudAssessment)
                .where(base_filter, FraudAssessment.transaction_timestamp >= cutoff)
            )
            or 0.0
        )

    def _upsert_behavior_profile(self, session: Session, tx: NormalizedTransaction) -> None:
        profile = session.scalar(
            select(BehaviorProfile).where(
                BehaviorProfile.tenant_id == tx.tenant_id,
                BehaviorProfile.customer_id == tx.customer_id,
            )
        )
        if profile is None:
            profile = BehaviorProfile(
                tenant_id=tx.tenant_id,
                customer_id=tx.customer_id,
                total_transactions=0,
                amount_mean=0.0,
                amount_m2=0.0,
                max_amount=0.0,
                night_transaction_count=0,
                weekend_transaction_count=0,
                high_risk_currency_count=0,
                cross_border_count=0,
            )
            session.add(profile)

        prior_count = profile.total_transactions or 0
        profile.amount_mean = profile.amount_mean or 0.0
        profile.amount_m2 = profile.amount_m2 or 0.0
        profile.max_amount = profile.max_amount or 0.0
        profile.night_transaction_count = profile.night_transaction_count or 0
        profile.weekend_transaction_count = profile.weekend_transaction_count or 0
        profile.high_risk_currency_count = profile.high_risk_currency_count or 0
        profile.cross_border_count = profile.cross_border_count or 0

        new_count = prior_count + 1
        delta = tx.amount - profile.amount_mean
        new_mean = profile.amount_mean + (delta / new_count)
        delta2 = tx.amount - new_mean

        profile.total_transactions = new_count
        profile.amount_mean = new_mean
        profile.amount_m2 = profile.amount_m2 + (delta * delta2)
        profile.max_amount = max(profile.max_amount, tx.amount)
        profile.first_transaction_at = profile.first_transaction_at or tx.transaction_timestamp
        profile.last_transaction_at = tx.transaction_timestamp
        if 0 <= tx.hour_of_day <= 5:
            profile.night_transaction_count += 1
        if tx.is_weekend:
            profile.weekend_transaction_count += 1
        runtime_config = TenantRuntimeConfig.from_metadata(tx.metadata)
        if tx.currency in runtime_config.high_risk_currencies:
            profile.high_risk_currency_count += 1
        if tx.origin_country != tx.destination_country:
            profile.cross_border_count += 1
        profile.updated_at = datetime.now(timezone.utc)
