from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application configuration loaded from environment variables or .env file."""

    APP_VERSION: str = "2.0.0"

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
    MODEL_METADATA_PATH: str = "app/ml/artifacts/model_metadata.json"
    MODEL_VERSION: str = "fraud-rf-v2.0.0"
    FEATURE_SCHEMA_VERSION: str = "fraud-features-v2.1.0"

    # Decisioning
    FRAUD_SCORE_THRESHOLD_BLOCK: float = 0.80
    FRAUD_SCORE_THRESHOLD_HOLD: float = 0.60
    NEUTRAL_FALLBACK_SCORE: float = 0.50
    MAX_BATCH_SIZE: int = 100

    # Transaction limits
    MAX_TRANSACTION_AMOUNT: float = 1_000_000.0

    # Unusual hour window (inclusive)
    UNUSUAL_HOUR_START: int = 0
    UNUSUAL_HOUR_END: int = 5

    # Configurable compliance lists. These should be managed by compliance teams.
    LOW_RISK_CURRENCY_CODES: str = "USD,EUR,GBP,CHF,SGD"
    HIGH_RISK_CURRENCY_CODES: str = "XMR,ZEC,IRR,KPW"
    HIGH_RISK_COUNTRY_CODES: str = "IR,KP,SY,MM"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def LOW_RISK_CURRENCY_CODES_SET(self) -> set[str]:
        return self._csv_to_set(self.LOW_RISK_CURRENCY_CODES)

    @property
    def HIGH_RISK_CURRENCY_CODES_SET(self) -> set[str]:
        return self._csv_to_set(self.HIGH_RISK_CURRENCY_CODES)

    @property
    def HIGH_RISK_COUNTRY_CODES_SET(self) -> set[str]:
        return self._csv_to_set(self.HIGH_RISK_COUNTRY_CODES)

    def _csv_to_set(self, value: str) -> set[str]:
        return {item.strip().upper() for item in value.split(",") if item.strip()}


settings = Settings()
