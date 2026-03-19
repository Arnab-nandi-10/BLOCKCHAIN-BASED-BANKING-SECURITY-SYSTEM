import os
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application configuration loaded from environment variables or .env file."""

    # Database
    DATABASE_URL: str = "postgresql://postgres:password@localhost:5432/fraud_detection"

    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_GROUP_ID: str = "fraud-detection"

    # Security
    JWT_SECRET: str = "change-me-in-production-use-a-256-bit-random-secret"

    # ML model artifacts
    MODEL_PATH: str = "app/ml/artifacts/fraud_model.pkl"
    SCALER_PATH: str = "app/ml/artifacts/scaler.pkl"

    # Fraud scoring thresholds
    FRAUD_SCORE_THRESHOLD_BLOCK: float = 0.8
    FRAUD_SCORE_THRESHOLD_HOLD: float = 0.5

    # Transaction limits
    MAX_TRANSACTION_AMOUNT: float = 1_000_000.0

    # Unusual hour window (inclusive)
    UNUSUAL_HOUR_START: int = 0
    UNUSUAL_HOUR_END: int = 5

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


# Singleton: import settings from this module everywhere
settings = Settings()
