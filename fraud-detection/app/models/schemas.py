from typing import Dict, List, Optional

from pydantic import BaseModel, Field, field_validator


class FraudScoreRequest(BaseModel):
    """Inbound payload for a single fraud-scoring request."""

    transactionId: str = Field(..., description="Unique transaction identifier")
    tenantId: str = Field(..., description="Tenant/organisation identifier")
    fromAccount: str = Field(..., description="Source account identifier")
    toAccount: str = Field(..., description="Destination account identifier")
    amount: float = Field(..., gt=0, description="Transaction amount (must be positive)")
    currency: str = Field(..., min_length=3, max_length=3, description="ISO-4217 currency code")
    transactionType: str = Field(..., description="TRANSFER | PAYMENT | WITHDRAWAL | DEPOSIT")
    ipAddress: Optional[str] = Field(None, description="Originating IP address")
    metadata: Optional[Dict[str, str]] = Field(None, description="Arbitrary key-value metadata")

    @field_validator("transactionType")
    @classmethod
    def validate_transaction_type(cls, value: str) -> str:
        allowed = {"TRANSFER", "PAYMENT", "WITHDRAWAL", "DEPOSIT"}
        normalised = value.strip().upper()
        if normalised not in allowed:
            raise ValueError(
                f"transactionType must be one of {sorted(allowed)}, got '{value}'"
            )
        return normalised

    @field_validator("currency")
    @classmethod
    def validate_currency(cls, value: str) -> str:
        return value.strip().upper()


class FraudScoreResponse(BaseModel):
    """Outbound payload returned for every scored transaction."""

    transactionId: str
    score: float = Field(..., ge=0.0, le=1.0, description="Fraud probability [0.0, 1.0]")
    riskLevel: str = Field(..., description="LOW | MEDIUM | HIGH | CRITICAL")
    triggeredRules: List[str] = Field(..., description="Human-readable rule names that fired")
    recommendation: str = Field(
        ...,
        description="APPROVE | ADDITIONAL_VERIFICATION | MANUAL_REVIEW | BLOCK_TRANSACTION",
    )
    shouldBlock: bool = Field(..., description="True when score >= FRAUD_SCORE_THRESHOLD_BLOCK")
    processingTimeMs: float = Field(..., ge=0.0, description="End-to-end scoring latency in ms")


class FraudScoreResult(BaseModel):
    """Internal result model that also carries the raw feature vector for auditing."""

    transactionId: str
    score: float = Field(..., ge=0.0, le=1.0)
    riskLevel: str
    triggeredRules: List[str]
    recommendation: str
    shouldBlock: bool
    processingTimeMs: float
    featureVector: List[float] = Field(..., description="Raw 50-dimensional feature vector")


class HealthResponse(BaseModel):
    """Generic health-check response model."""

    status: str
    modelLoaded: bool
    version: str
    uptime: float = Field(..., description="Service uptime in seconds")
