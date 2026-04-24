from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import AliasChoices, BaseModel, Field, field_validator


class CustomerContext(BaseModel):
    customerId: Optional[str] = Field(None, description="Stable customer identifier")
    kycVerified: bool = Field(True, description="Whether KYC checks are complete")
    kycRiskBand: str = Field("MEDIUM", description="LOW | MEDIUM | HIGH")
    onboardingAgeDays: int = Field(365, ge=0, description="Customer age since onboarding")
    pepFlag: bool = Field(False, description="Politically exposed person indicator")
    sanctionsHit: bool = Field(False, description="Sanctions/watchlist hit indicator")

    @field_validator("kycRiskBand")
    @classmethod
    def validate_kyc_risk_band(cls, value: str) -> str:
        allowed = {"LOW", "MEDIUM", "HIGH"}
        normalised = value.strip().upper()
        if normalised not in allowed:
            raise ValueError(f"kycRiskBand must be one of {sorted(allowed)}")
        return normalised


class CounterpartyContext(BaseModel):
    receiverVerified: bool = Field(False, description="Counterparty onboarding/verification flag")
    receiverAgeDays: int = Field(0, ge=0, description="Receiver relationship age")
    walletRiskLevel: str = Field("LOW", description="LOW | MEDIUM | HIGH")

    @field_validator("walletRiskLevel")
    @classmethod
    def validate_wallet_risk_level(cls, value: str) -> str:
        allowed = {"LOW", "MEDIUM", "HIGH"}
        normalised = value.strip().upper()
        if normalised not in allowed:
            raise ValueError(f"walletRiskLevel must be one of {sorted(allowed)}")
        return normalised


class ChannelContext(BaseModel):
    deviceId: Optional[str] = Field(None, description="Device fingerprint or mobile device id")
    channel: str = Field("API", description="API | MOBILE | WEB | BACKOFFICE | ATM")
    originCountry: str = Field("UNKNOWN", description="Origin country ISO code")
    destinationCountry: str = Field("UNKNOWN", description="Destination country ISO code")
    blockchainNetwork: str = Field("permissioned", description="Blockchain network or rail")
    travelRuleReceived: bool = Field(False, description="Whether virtual asset travel rule data was received")

    @field_validator("channel")
    @classmethod
    def validate_channel(cls, value: str) -> str:
        return value.strip().upper()

    @field_validator("originCountry", "destinationCountry")
    @classmethod
    def validate_country(cls, value: str) -> str:
        return value.strip().upper()


class FraudScoreRequest(BaseModel):
    """Inbound payload for a single fraud-scoring request."""

    transactionId: str = Field(..., description="Unique transaction identifier")
    tenantId: str = Field(..., description="Tenant/organisation identifier")
    fromAccount: str = Field(..., description="Source account identifier")
    toAccount: str = Field(..., description="Destination account identifier")
    amount: float = Field(..., gt=0, description="Transaction amount (must be positive)")
    currency: str = Field(..., min_length=3, max_length=12, description="ISO-4217 or asset code")
    transactionType: str = Field(
        ...,
        description="TRANSFER | PAYMENT | WITHDRAWAL | DEPOSIT",
        validation_alias=AliasChoices("transactionType", "type"),
    )
    transactionTimestamp: Optional[datetime] = Field(
        None,
        description="Timestamp supplied by upstream service; defaults to now if omitted",
    )
    ipAddress: Optional[str] = Field(None, description="Originating IP address")
    customer: Optional[CustomerContext] = None
    counterparty: Optional[CounterpartyContext] = None
    channel: Optional[ChannelContext] = None
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


class SignalBreakdown(BaseModel):
    key: str
    source: str
    weight: float = Field(..., ge=0.0, le=1.0)
    severity: str
    explanation: str
    evidence: Dict[str, Any] = Field(default_factory=dict)


class FraudScoreResponse(BaseModel):
    """Hybrid fraud scoring response exposed to upstream services."""

    transactionId: str
    score: float = Field(..., ge=0.0, le=1.0, description="Final hybrid fraud probability [0, 1]")
    mlScore: float = Field(..., ge=0.0, le=1.0)
    ruleScore: float = Field(..., ge=0.0, le=1.0)
    behavioralScore: float = Field(..., ge=0.0, le=1.0)
    riskLevel: str = Field(..., description="LOW | MEDIUM | HIGH | CRITICAL")
    decision: str = Field(..., description="ALLOW | MONITOR | HOLD | BLOCK")
    triggeredRules: List[str] = Field(default_factory=list, description="Triggered rule/signal identifiers")
    explanations: List[str] = Field(default_factory=list, description="Human-readable explanations")
    signalBreakdown: List[SignalBreakdown] = Field(default_factory=list)
    recommendation: str = Field(
        ...,
        description="ALLOW | MONITOR | MANUAL_REVIEW | BLOCK_TRANSACTION",
    )
    reviewRequired: bool = Field(False, description="Manual review required due to risk or degraded mode")
    shouldBlock: bool = Field(..., description="True when decision is BLOCK")
    fallbackUsed: bool = Field(False, description="True when degraded scoring was used")
    modelVersion: str = Field(..., description="Loaded ML model version")
    processingTimeMs: float = Field(..., ge=0.0, description="End-to-end scoring latency in ms")


class FraudScoreResult(BaseModel):
    """Internal result model that also carries the raw feature vector for auditing."""

    response: FraudScoreResponse
    featureVector: List[float] = Field(..., description="Raw feature vector")


class FraudAlertResponse(BaseModel):
    transactionId: str
    tenantId: str
    score: float
    riskLevel: str
    decision: str
    recommendation: str
    reviewRequired: bool
    shouldBlock: bool
    fallbackUsed: bool
    triggeredRules: List[str]
    explanations: List[str]
    signalBreakdown: List[SignalBreakdown]
    detectedAt: datetime


class FraudAlertSummaryResponse(BaseModel):
    tenantId: str
    totalAlerts: int
    reviewRequiredCount: int
    decisionCounts: Dict[str, int]
    riskLevelCounts: Dict[str, int]
    fromDate: Optional[datetime] = None
    toDate: Optional[datetime] = None


class HealthResponse(BaseModel):
    """Generic health-check response model."""

    status: str
    modelLoaded: bool
    version: str
    uptime: float = Field(..., description="Service uptime in seconds")
