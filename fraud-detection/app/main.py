import threading
import time
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Any, AsyncGenerator, Dict, Optional

import structlog
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from prometheus_fastapi_instrumentator import Instrumentator

from app.api.routes import fraud, health
from app.core.config import settings
from app.services.scoring_service import scoring_service

logger = structlog.get_logger(__name__)

# ---------------------------------------------------------------------------
# Module-level state for the Kafka consumer background thread
# ---------------------------------------------------------------------------
_kafka_consumer: Optional[Any] = None
_consumer_thread: Optional[threading.Thread] = None

# Track application startup time for uptime calculations
_start_time: float = time.monotonic()


# ---------------------------------------------------------------------------
# Lifespan context manager
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Manage application startup and graceful shutdown.

    Startup:
      - Load the ML fraud-detection model from disk (falls back to dummy if missing).
      - Launch the Kafka consumer in a daemon background thread.

    Shutdown:
      - Signal the Kafka consumer to stop and let the thread finish.
    """
    global _kafka_consumer, _consumer_thread

    logger.info("BBSS Fraud Detection Service starting up", version="1.0.0")

    # --- Load ML model ---------------------------------------------------
    try:
        scoring_service.model.load(settings.MODEL_PATH, settings.SCALER_PATH)
        logger.info(
            "ML model ready",
            model_loaded=scoring_service.model.is_loaded(),
            model_path=settings.MODEL_PATH,
        )
    except Exception as exc:
        logger.error("ML model failed to load", error=str(exc))

    # --- Start Kafka consumer in daemon thread ----------------------------
    try:
        from app.core.kafka_consumer import FraudKafkaConsumer

        _kafka_consumer = FraudKafkaConsumer(scoring_service=scoring_service)
        _consumer_thread = threading.Thread(
            target=_kafka_consumer.start,
            daemon=True,
            name="fraud-kafka-consumer",
        )
        _consumer_thread.start()
        logger.info("Kafka consumer thread started")
    except Exception as exc:
        logger.warning(
            "Kafka consumer could not be started — Kafka events will not be processed",
            error=str(exc),
        )

    yield  # Application is running

    # --- Graceful shutdown -----------------------------------------------
    logger.info("BBSS Fraud Detection Service shutting down")

    if _kafka_consumer is not None:
        _kafka_consumer.stop()
        if _consumer_thread is not None and _consumer_thread.is_alive():
            _consumer_thread.join(timeout=10)
            if _consumer_thread.is_alive():
                logger.warning("Kafka consumer thread did not stop within 10 s")
        logger.info("Kafka consumer stopped")

    logger.info("Shutdown complete")


# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------

app = FastAPI(
    title="BBSS Fraud Detection Service",
    version="1.0.0",
    description=(
        "Real-time ML-powered fraud detection microservice for the "
        "Blockchain Banking Security System."
    ),
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# ---------------------------------------------------------------------------
# Middleware
# ---------------------------------------------------------------------------

# CORS is handled exclusively by the API Gateway (Spring Cloud Gateway).
# Enabling it here would cause duplicate Access-Control-Allow-Origin headers
# (one from this service and one from the gateway), which browsers reject.
# Do NOT add CORSMiddleware here.


@app.middleware("http")
async def add_process_time_header(request: Request, call_next):  # type: ignore[no-untyped-def]
    """Inject X-Process-Time header (in milliseconds) into every response."""
    start = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = (time.perf_counter() - start) * 1_000.0
    response.headers["X-Process-Time"] = f"{elapsed_ms:.3f}ms"
    return response


# ---------------------------------------------------------------------------
# Prometheus instrumentation
# ---------------------------------------------------------------------------

Instrumentator(
    should_group_status_codes=True,
    should_ignore_untemplated=True,
    should_respect_env_var=False,
    should_instrument_requests_inprogress=True,
    excluded_handlers=["/metrics"],
    inprogress_name="http_requests_inprogress",
    inprogress_labels=True,
).instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)

# ---------------------------------------------------------------------------
# Routers
# ---------------------------------------------------------------------------

app.include_router(fraud.router, prefix="/api/v1/fraud")
app.include_router(health.router, prefix="/api/v1")

# ---------------------------------------------------------------------------
# Global exception handlers
# ---------------------------------------------------------------------------


@app.exception_handler(ValueError)
async def value_error_handler(request: Request, exc: ValueError) -> JSONResponse:
    """Return HTTP 400 for any ValueError raised inside a route handler."""
    logger.warning(
        "ValueError in request handler",
        path=str(request.url.path),
        method=request.method,
        error=str(exc),
    )
    return JSONResponse(
        status_code=400,
        content={
            "success": False,
            "message": str(exc),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        },
    )


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """Return HTTP 500 for any unhandled exception."""
    logger.error(
        "Unhandled exception in request handler",
        path=str(request.url.path),
        method=request.method,
        error=str(exc),
        exc_info=True,
    )
    return JSONResponse(
        status_code=500,
        content={
            "success": False,
            "message": "An internal server error occurred",
            "timestamp": datetime.now(timezone.utc).isoformat(),
        },
    )
