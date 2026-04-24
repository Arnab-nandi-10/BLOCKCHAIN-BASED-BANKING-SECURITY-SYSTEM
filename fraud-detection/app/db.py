from collections.abc import Generator

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.core.config import settings


class Base(DeclarativeBase):
    """Shared SQLAlchemy declarative base."""


engine = create_engine(
    settings.DATABASE_URL,
    pool_pre_ping=True,
    connect_args={"connect_timeout": 5},
)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False, expire_on_commit=False)


def init_db() -> None:
    """Create database tables required by the fraud service."""
    import app.models.behavior_profile  # noqa: F401
    import app.models.fraud_assessment  # noqa: F401
    import app.models.fraud_alert  # noqa: F401

    Base.metadata.create_all(bind=engine)
    _apply_compatibility_migrations()


def _apply_compatibility_migrations() -> None:
    """Apply additive schema upgrades for existing local/dev databases."""
    fraud_alert_columns = {
        "decision": "VARCHAR(16) NOT NULL DEFAULT 'HOLD'",
        "triggered_rules": "JSONB NOT NULL DEFAULT '[]'::jsonb",
        "explanations": "JSONB NOT NULL DEFAULT '[]'::jsonb",
        "signal_breakdown": "JSONB NOT NULL DEFAULT '[]'::jsonb",
        "recommendation": "VARCHAR(100) NOT NULL DEFAULT 'MANUAL_REVIEW'",
        "review_required": "BOOLEAN NOT NULL DEFAULT FALSE",
        "fallback_used": "BOOLEAN NOT NULL DEFAULT FALSE",
        "should_block": "BOOLEAN NOT NULL DEFAULT FALSE",
    }

    with engine.begin() as connection:
        inspector = inspect(connection)
        _ensure_columns(connection, inspector, "fraud_alerts", fraud_alert_columns)


def _ensure_columns(connection, inspector, table_name: str, columns: dict[str, str]) -> None:
    if not inspector.has_table(table_name):
        return

    existing_columns = {column["name"] for column in inspector.get_columns(table_name)}
    for column_name, ddl in columns.items():
        if column_name not in existing_columns:
            connection.execute(
                text(f"ALTER TABLE {table_name} ADD COLUMN {column_name} {ddl}")
            )


def close_db() -> None:
    """Dispose the shared SQLAlchemy engine."""
    engine.dispose()


def get_db_session() -> Generator[Session, None, None]:
    """Yield a short-lived SQLAlchemy session for request handlers."""
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
