from typing import Dict

import structlog
from fastapi import APIRouter, Response, status
from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError

from app.services.scoring_service import scoring_service

logger = structlog.get_logger(__name__)

router = APIRouter(tags=["health"])


# ---------------------------------------------------------------------------
# GET /health  — basic liveness / version check
# ---------------------------------------------------------------------------

@router.get(
    "/health",
    summary="Basic health check",
)
async def health_check() -> Dict:
    """Always returns HTTP 200 with service metadata.

    Used by reverse proxies and monitoring dashboards that just need to know
    whether the process is alive and what version is running.
    """
    return {
        "status": "UP",
        "service": "fraud-detection",
        "version": "1.0.0",
    }


# ---------------------------------------------------------------------------
# GET /health/ready  — readiness probe (checks DB + model)
# ---------------------------------------------------------------------------

@router.get(
    "/health/ready",
    summary="Readiness probe — checks dependencies",
)
async def readiness_check(response: Response) -> Dict:
    """Return HTTP 200 when all dependencies are healthy, HTTP 503 otherwise.

    Checks performed:
    - ML model is loaded into memory.
    - PostgreSQL database is reachable (SELECT 1).

    Kubernetes / ECS should use this endpoint as a readiness probe so that
    traffic is only routed to instances that are fully initialised.
    """
    checks: Dict[str, bool] = {
        "model": scoring_service.model.is_loaded(),
        "database": False,
    }

    # Database connectivity check
    try:
        from app.core.config import settings

        engine = create_engine(
            settings.DATABASE_URL,
            pool_pre_ping=True,
            connect_args={"connect_timeout": 5},
        )
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        engine.dispose()
        checks["database"] = True
    except SQLAlchemyError as exc:
        logger.warning("Database readiness check failed", error=str(exc))
    except Exception as exc:
        logger.warning("Database readiness check raised unexpected error", error=str(exc))

    all_healthy: bool = all(checks.values())

    if not all_healthy:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        logger.warning("Readiness check FAILED", checks=checks)
    else:
        logger.debug("Readiness check passed", checks=checks)

    return {
        "status": "READY" if all_healthy else "NOT_READY",
        "checks": checks,
    }


# ---------------------------------------------------------------------------
# GET /health/live  — liveness probe (always 200)
# ---------------------------------------------------------------------------

@router.get(
    "/health/live",
    summary="Liveness probe — always 200",
)
async def liveness_check() -> Dict:
    """Return HTTP 200 unconditionally.

    Kubernetes uses this to decide whether to restart the container.  As long
    as the process is running and the event-loop is responsive, it should
    return 200 — dependency failures are the concern of the readiness probe.
    """
    return {"status": "ALIVE"}
