from datetime import date, datetime, time, timezone
from typing import Annotated, Dict, List

import structlog
from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.core.config import settings
from app.core.security import get_current_token
from app.models.schemas import FraudAlertSummaryResponse, FraudScoreRequest, FraudScoreResponse
from app.services.scoring_service import scoring_service
from app.services.tenant_runtime_config import TenantRuntimeConfig

logger = structlog.get_logger(__name__)

router = APIRouter(tags=["fraud"])


# ---------------------------------------------------------------------------
# POST /score
# ---------------------------------------------------------------------------

@router.post(
    "/score",
    response_model=FraudScoreResponse,
    summary="Score a single transaction for fraud probability",
    response_description="Fraud score with risk level and recommendation",
)
async def score_transaction(
    request: FraudScoreRequest,
    token_claims: Annotated[Dict, Depends(get_current_token)],
) -> FraudScoreResponse:
    """Evaluate a single transaction and return a fraud risk assessment.

    Requires a valid JWT Bearer token.  The `tenantId` in the request body
    is informational; the authoritative tenant comes from the token claims.
    """
    tenant_runtime_config = TenantRuntimeConfig.from_metadata(request.metadata or {})
    if request.amount > tenant_runtime_config.max_transaction_amount:
        raise ValueError(
            f"Transaction amount {request.amount} exceeds the maximum allowed "
            f"value of {tenant_runtime_config.max_transaction_amount}"
        )

    logger.info(
        "Scoring transaction",
        transactionId=request.transactionId,
        tenantId=request.tenantId,
        amount=request.amount,
        currency=request.currency,
        transactionType=request.transactionType,
    )

    return scoring_service.score_transaction(
        _apply_authoritative_tenant(request, token_claims)
    )


# ---------------------------------------------------------------------------
# POST /batch-score
# ---------------------------------------------------------------------------

@router.post(
    "/batch-score",
    response_model=List[FraudScoreResponse],
    summary="Score a batch of transactions (max 100)",
    response_description="List of fraud scores in the same order as the input",
)
async def batch_score_transactions(
    requests: List[FraudScoreRequest],
    token_claims: Annotated[Dict, Depends(get_current_token)],
) -> List[FraudScoreResponse]:
    """Evaluate up to 100 transactions in a single call.

    Transactions are scored sequentially.  The response list preserves the
    same ordering as the input list.
    """
    if len(requests) == 0:
        raise ValueError("Batch must contain at least one transaction")

    if len(requests) > settings.MAX_BATCH_SIZE:
        raise ValueError(
            f"Batch size {len(requests)} exceeds the maximum of {settings.MAX_BATCH_SIZE} transactions"
        )

    logger.info("Scoring batch", batch_size=len(requests))

    for request in requests:
        tenant_runtime_config = TenantRuntimeConfig.from_metadata(request.metadata or {})
        if request.amount > tenant_runtime_config.max_transaction_amount:
            raise ValueError(
                f"Transaction amount {request.amount} exceeds the maximum allowed "
                f"value of {tenant_runtime_config.max_transaction_amount}"
            )

    results: List[FraudScoreResponse] = [
        scoring_service.score_transaction(_apply_authoritative_tenant(req, token_claims))
        for req in requests
    ]
    return results


# ---------------------------------------------------------------------------
# GET /alerts - Fraud alerts list (stub for now)
# ---------------------------------------------------------------------------

@router.get(
    "/alerts",
    summary="List fraud alerts with pagination",
    response_description="Paginated list of fraud alerts",
)
async def list_fraud_alerts(
    token_claims: Annotated[Dict, Depends(get_current_token)],
    page: int = Query(0, ge=0, description="Page number (0-indexed)"),
    size: int = Query(10, ge=1, le=100, description="Page size"),
    riskLevel: str | None = Query(None, description="LOW | MEDIUM | HIGH | CRITICAL"),
    decision: str | None = Query(None, description="ALLOW | MONITOR | HOLD | BLOCK"),
    reviewRequired: bool | None = Query(None, description="Whether manual review is required"),
    search: str | None = Query(None, description="Search by transaction ID or recommendation"),
    fromDate: date | None = Query(None, description="Inclusive start date"),
    toDate: date | None = Query(None, description="Inclusive end date"),
) -> Dict:
    """List persisted fraud alerts with pagination."""
    tenant_id = _token_tenant_id(token_claims)
    logger.info(
        "Fetching fraud alerts",
        tenantId=tenant_id,
        page=page,
        size=size,
        riskLevel=riskLevel,
        decision=decision,
        reviewRequired=reviewRequired,
    )

    alerts, total = scoring_service.history_repository.list_alerts(
        tenant_id,
        page,
        size,
        risk_level=riskLevel,
        decision=decision,
        review_required=reviewRequired,
        search=search,
        from_date=_to_datetime_start(fromDate),
        to_date=_to_datetime_end(toDate),
    )
    total_pages = 0 if total == 0 else ((total - 1) // size) + 1

    return {
        "success": True,
        "data": {
            "content": [alert.model_dump() for alert in alerts],
            "pageable": {
                "pageNumber": page,
                "pageSize": size,
                "sort": [],
                "offset": page * size,
                "paged": True,
                "unpaged": False,
            },
            "totalPages": total_pages,
            "totalElements": total,
            "last": page >= max(total_pages - 1, 0),
            "numberOfElements": len(alerts),
            "size": size,
            "number": page,
            "sort": [],
            "first": page == 0,
            "empty": len(alerts) == 0,
        },
    }


@router.get(
    "/alerts/summary",
    response_model=FraudAlertSummaryResponse,
    summary="Summarize fraud alerts",
    response_description="Fraud alert counts by risk level and decision",
)
async def summarize_fraud_alerts(
    token_claims: Annotated[Dict, Depends(get_current_token)],
    riskLevel: str | None = Query(None, description="LOW | MEDIUM | HIGH | CRITICAL"),
    decision: str | None = Query(None, description="ALLOW | MONITOR | HOLD | BLOCK"),
    reviewRequired: bool | None = Query(None, description="Whether manual review is required"),
    search: str | None = Query(None, description="Search by transaction ID or recommendation"),
    fromDate: date | None = Query(None, description="Inclusive start date"),
    toDate: date | None = Query(None, description="Inclusive end date"),
) -> FraudAlertSummaryResponse:
    tenant_id = _token_tenant_id(token_claims)
    return scoring_service.history_repository.summarize_alerts(
        tenant_id,
        risk_level=riskLevel,
        decision=decision,
        review_required=reviewRequired,
        search=search,
        from_date=_to_datetime_start(fromDate),
        to_date=_to_datetime_end(toDate),
    )


@router.get(
    "/alerts/{transaction_id}",
    summary="Get a fraud alert by transaction ID",
    response_description="Fraud alert detail for a single transaction",
)
async def get_fraud_alert(
    transaction_id: str,
    token_claims: Annotated[Dict, Depends(get_current_token)],
) -> Dict:
    tenant_id = _token_tenant_id(token_claims)
    alert = scoring_service.history_repository.get_alert_by_transaction_id(tenant_id, transaction_id)
    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Fraud alert not found for transaction {transaction_id}",
        )
    return {"success": True, "data": alert.model_dump()}


# ---------------------------------------------------------------------------
# GET /health  (no auth — used by load-balancers and dashboards)
# ---------------------------------------------------------------------------

@router.get(
    "/health",
    summary="Fraud service health with model status",
)
async def fraud_service_health() -> Dict:
    """Return model-load status, service name, and version.

    This endpoint does **not** require authentication so it can be polled
    by orchestration tools without credentials.
    """
    loaded: bool = scoring_service.model.is_loaded()
    return {
        "service": "fraud-detection",
        "version": settings.APP_VERSION,
        "modelLoaded": loaded,
        "mode": "HYBRID_ML_RULES",
        "fallbackEnabled": True,
        "status": "UP" if loaded else "DEGRADED",
    }


def _apply_authoritative_tenant(
    request: FraudScoreRequest,
    token_claims: Dict,
) -> FraudScoreRequest:
    tenant_id = _token_tenant_id(token_claims)
    if request.tenantId != tenant_id:
        logger.warning(
            "Request tenantId did not match token tenant; overriding with token tenant",
            requestTenantId=request.tenantId,
            tokenTenantId=tenant_id,
            transactionId=request.transactionId,
        )
    return request.model_copy(update={"tenantId": tenant_id})


def _token_tenant_id(token_claims: Dict) -> str:
    tenant_id = str(token_claims.get("tenantId") or "").strip()
    if not tenant_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token is missing required 'tenantId' claim",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return tenant_id


def _to_datetime_start(value: date | None) -> datetime | None:
    if value is None:
        return None
    return datetime.combine(value, time.min, tzinfo=timezone.utc)


def _to_datetime_end(value: date | None) -> datetime | None:
    if value is None:
        return None
    return datetime.combine(value, time.max, tzinfo=timezone.utc)
