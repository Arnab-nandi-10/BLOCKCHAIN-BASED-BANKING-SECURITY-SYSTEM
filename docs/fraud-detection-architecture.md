# Hybrid Fraud Detection Architecture

## Overview

The `fraud-detection` service implements a production-inspired hybrid engine for a blockchain banking platform. It combines:

- A scikit-learn `RandomForestClassifier`
- A weighted rule engine for AML and transaction monitoring
- A behavioral analytics layer backed by PostgreSQL history
- Real-time decisioning for `ALLOW`, `MONITOR`, `HOLD`, and `BLOCK`

The design is optimized for low false positives by keeping most individual signals low weight and relying on score aggregation instead of binary rules.

## Runtime Flow

1. `transaction-service` calls `POST /api/v1/fraud/score` synchronously before blockchain submission.
2. The fraud service normalizes the request into a risk context with KYC, channel, geography, and counterparty attributes.
3. PostgreSQL history is queried for behavior baselines such as average amount, velocity, dormancy, and receiver novelty.
4. `FeatureEngineer` produces a 64-feature vector.
5. `FraudModel` scores the feature vector and returns `mlScore`.
6. `RuleEngine` applies weighted AML and fraud monitoring signals and returns `ruleScore`.
7. `BehavioralEngine` scores deviations from historical customer behavior and returns `behavioralScore`.
8. `ScoringService` computes:

```text
final_score = ml_score + rule_score + behavioral_score
final_score = min(1.0, final_score)
```

9. `DecisionEngine` maps the final score:

- `0.00 - 0.30` -> `LOW` / `ALLOW`
- `0.30 - 0.60` -> `MEDIUM` / `MONITOR`
- `0.60 - 0.80` -> `HIGH` / `HOLD`
- `> 0.80` -> `CRITICAL` / `BLOCK`

10. Results, explanations, and signal breakdown are persisted for audit and future behavioral baselines.

## Components

### FeatureEngineer

Produces 64 features including:

- Amount, `log(amount)`, `sqrt(amount)`
- Threshold flags for `50k`, `500k`, and `1m`
- Hour, weekday, weekend, unusual hour
- Transaction type one-hot encoding
- Currency and network risk flags
- Account hash and account-pattern features
- Same-account transfer detection
- KYC, sanctions, PEP, travel rule, wallet risk
- Historical amount ratio and z-score
- 10-minute, 1-hour, and 24-hour velocity counts
- Receiver novelty and device/IP/country familiarity

### RuleEngine

Weighted signals include:

- High amount and very high amount
- Unusual hour
- New receiver
- Velocity spike
- Same-account looping
- High-risk country or asset exposure
- Possible structuring
- PEP high-value/cross-border escalation
- Travel Rule gap on public-chain transfers

Extreme-case single-condition blocking is only used for sanctions/watchlist hits.

### BehavioralEngine

Compares current activity against prior behavior:

- Amount multiple versus customer average
- Frequency spike versus normal hourly pace
- Dormancy breakout
- New device/IP fingerprint
- Counterparty novelty
- Time-of-day shift
- Cross-border deviation

### Persistence

PostgreSQL tables:

- `fraud_assessments`: immutable scoring outcomes and feature vectors
- `behavior_profiles`: rolling customer baselines
- `fraud_alerts`: hold/block/review alerts for dashboards and audit

### Kafka

- `tx.submitted`: async scoring or drift-monitoring input
- `fraud.alert`: downstream alert topic for review workflows
- `tx.verified`, `tx.blocked`: existing lifecycle topics remain unchanged

## Example Request

```json
{
  "transactionId": "txn-7f3b4f0d",
  "tenantId": "tenant-alpha",
  "fromAccount": "WALLET-001245",
  "toAccount": "EXT-998877",
  "amount": 120000.0,
  "currency": "XMR",
  "transactionType": "TRANSFER",
  "transactionTimestamp": "2026-04-22T02:10:00Z",
  "ipAddress": "203.0.113.11",
  "customer": {
    "customerId": "cust-001",
    "kycVerified": true,
    "kycRiskBand": "HIGH",
    "onboardingAgeDays": 120,
    "pepFlag": false,
    "sanctionsHit": false
  },
  "counterparty": {
    "receiverVerified": false,
    "receiverAgeDays": 0,
    "walletRiskLevel": "HIGH"
  },
  "channel": {
    "deviceId": "device-new-99",
    "channel": "MOBILE",
    "originCountry": "IN",
    "destinationCountry": "IR",
    "blockchainNetwork": "permissioned",
    "travelRuleReceived": false
  },
  "metadata": {
    "customerId": "cust-001"
  }
}
```

## Example Response

```json
{
  "transactionId": "txn-7f3b4f0d",
  "score": 0.87,
  "mlScore": 0.42,
  "ruleScore": 0.27,
  "behavioralScore": 0.18,
  "riskLevel": "CRITICAL",
  "decision": "BLOCK",
  "triggeredRules": [
    "velocity_spike",
    "new_receiver",
    "high_risk_jurisdiction",
    "amount_deviation",
    "schedule_shift"
  ],
  "explanations": [
    "5 transactions were initiated in the last 10 minutes, well above the usual pace.",
    "Funds are being sent to a receiver with no prior transaction history on this account.",
    "Transaction involves a country that the compliance ruleset treats as high risk.",
    "Amount is 4.8x higher than the customer's usual average.",
    "Transaction timing differs sharply from the customer's historical activity window."
  ],
  "signalBreakdown": [
    {
      "key": "velocity_spike",
      "source": "RULE",
      "weight": 0.11,
      "severity": "HIGH",
      "explanation": "5 transactions were initiated in the last 10 minutes, well above the usual pace.",
      "evidence": {
        "velocityRatio": 18.0
      }
    }
  ],
  "recommendation": "BLOCK_TRANSACTION",
  "reviewRequired": true,
  "shouldBlock": true,
  "fallbackUsed": false,
  "modelVersion": "fraud-rf-v2.0.0",
  "processingTimeMs": 24.8
}
```

## Sample Score Breakdown

```text
ml_score          = 0.42
rule_score        = 0.27
behavioral_score  = 0.18
final_score       = 0.87
decision          = BLOCK
```

## Sample Scenarios

### Low

- Amount close to average
- Known receiver
- Daytime transfer
- Familiar device and IP
- Final score around `0.08`
- Decision: `ALLOW`

### Medium

- Slightly larger amount than usual
- New device but known receiver
- No AML escalation flags
- Final score around `0.41`
- Decision: `MONITOR`

### High

- Amount `3x` customer average
- New receiver
- Unusual hour
- Velocity increase in last hour
- Final score around `0.72`
- Decision: `HOLD`

### Critical

- Very high amount
- Cross-border into a high-risk jurisdiction
- New receiver and new device
- Strong velocity spike or sanctions hit
- Final score around `0.87` or higher
- Decision: `BLOCK`

## Fallback Logic

When the fraud service or model layer cannot produce a normal result:

- Assign a neutral score of `0.50`
- Return `decision = HOLD`
- Set `reviewRequired = true`
- Set `fallbackUsed = true`
- Persist an auditable explanation for the degraded path

This keeps the platform operating while ensuring manual review for cases that were not fully scored.
