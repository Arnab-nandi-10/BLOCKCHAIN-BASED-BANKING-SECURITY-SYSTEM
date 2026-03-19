from typing import Annotated, Dict, List

import structlog
from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.core.security import get_current_token
from app.models.schemas import FraudScoreRequest, FraudScoreResponse
from app.services.scoring_service import scoring_service

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
    if not scoring_service.model.is_loaded():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="ML model is not yet loaded — please retry in a moment",
        )

    from app.core.config import settings

    if request.amount > settings.MAX_TRANSACTION_AMOUNT:
        raise ValueError(
            f"Transaction amount {request.amount} exceeds the maximum allowed "
            f"value of {settings.MAX_TRANSACTION_AMOUNT}"
        )

    logger.info(
        "Scoring transaction",
        transactionId=request.transactionId,
        tenantId=request.tenantId,
        amount=request.amount,
        currency=request.currency,
        transactionType=request.transactionType,
    )

    return scoring_service.score_transaction(request)


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

    if len(requests) > 100:
        raise ValueError(
            f"Batch size {len(requests)} exceeds the maximum of 100 transactions"
        )

    if not scoring_service.model.is_loaded():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="ML model is not yet loaded — please retry in a moment",
        )

    logger.info("Scoring batch", batch_size=len(requests))

    results: List[FraudScoreResponse] = [
        scoring_service.score_transaction(req) for req in requests
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
) -> Dict:
    """List fraud alerts with pagination.

    This is a stub endpoint that returns an empty list.
    Full implementation requires persistent storage for alerts.
    """
    logger.info("Fetching fraud alerts", page=page, size=size)

    return {
        "success": True,
        "data": {
            "content": [],
            "pageable": {
                "pageNumber": page,
                "pageSize": size,
                "sort": [],
                "offset": page * size,
                "paged": True,
                "unpaged": False,
            },
            "totalPages": 0,
            "totalElements": 0,
            "last": True,
            "numberOfElements": 0,
            "size": size,
            "number": page,
            "sort": [],
            "first": True,
            "empty": True,
        },
    }


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
        "version": "1.0.0",
        "modelLoaded": loaded,
        "status": "UP" if loaded else "DEGRADED",
    }
