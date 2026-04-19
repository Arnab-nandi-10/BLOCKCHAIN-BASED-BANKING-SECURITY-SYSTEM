from collections.abc import Generator

from sqlalchemy import create_engine
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
    import app.models.fraud_alert  # noqa: F401

    Base.metadata.create_all(bind=engine)


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
