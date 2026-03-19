# Circuit Breaker Testing Guide

## Overview

This guide provides comprehensive testing procedures for the Resilience4j circuit breaker implementation across the Blockchain Banking Security System.

## Circuit Breaker Instances

### 1. Transaction Service
- **fabricGateway**: Protects blockchain-service Fabric Gateway gRPC calls
  - Failure threshold: 30%
  - Open duration: 20s
  - Slow call threshold: 15s
  - Bulkhead: 5 concurrent calls

### 2. Audit Service
- **blockchainClient**: Protects blockchain-service REST API calls
  - Failure threshold: 50%
  - Open duration: 30s
  - Slow call threshold: 25s
  - Bulkhead: 10 concurrent calls

---

## Prerequisites

Ensure all services are running:
```powershell
# From workspace root
docker-compose up -d
```

Verify Prometheus is scraping metrics:
```powershell
# Open Prometheus UI
Start-Process "http://localhost:9090/targets"
```

---

## Test Scenario 1: Blockchain-Service Circuit Breaker Opens on Fabric Failure

**Objective**: Verify that blockchain-service circuit breaker opens after Fabric Gateway failures exceed 30% threshold.

### 1.1 Setup

Start all services and verify baseline:
```powershell
# Check blockchain-service health
curl http://localhost:8084/actuator/health | ConvertFrom-Json | Select-Object -ExpandProperty status

# Verify circuit breaker is CLOSED
curl http://localhost:8084/actuator/health/circuitbreakers | ConvertFrom-Json
```

Expected output:
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "fabricGateway": {
          "state": "CLOSED",
          "failureRate": "0.0%",
          "slowCallRate": "0.0%"
        }
      }
    }
  }
}
```

### 1.2 Induce Fabric Gateway Failure

Stop the Fabric peer to simulate network outage:
```powershell
# Stop the Hyperledger Fabric peer container
docker stop $(docker ps -q --filter "name=peer0.org1.example.com")
```

### 1.3 Generate Traffic to Trigger Circuit Breaker

Submit 10 transactions to exceed minimum-number-of-calls (5) and trigger failure threshold (30%):

```powershell
$headers = @{
    "Authorization" = "Bearer YOUR_JWT_TOKEN"
    "Content-Type" = "application/json"
}

$tenant = "tenant1"
$body = @{
    amount = 100.00
    currency = "USD"
    fromAccount = "ACC001"
    toAccount = "ACC002"
    description = "Test transaction"
} | ConvertTo-Json

# Send 10 transactions (expect 3+ to fail)
1..10 | ForEach-Object {
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" `
            -Method POST `
            -Headers $headers `
            -Body $body `
            -ContentType "application/json"
    } catch {
        Write-Host "Transaction $_ failed: $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Seconds 1
}
```

### 1.4 Verify Circuit Breaker Opened

Check circuit breaker state:
```powershell
curl http://localhost:8084/actuator/health/circuitbreakers | ConvertFrom-Json
```

Expected output:
```json
{
  "status": "DOWN",
  "components": {
    "circuitBreakers": {
      "status": "DOWN",
      "details": {
        "fabricGateway": {
          "state": "OPEN",
          "failureRate": "100.0%",
          "slowCallRate": "0.0%"
        }
      }
    }
  }
}
```

Check Prometheus metrics:
```powershell
# Open Prometheus Graph UI
Start-Process "http://localhost:9090/graph"

# Run query:
# resilience4j_circuitbreaker_state{name="fabricGateway"}
# Value: 1 = OPEN, 0 = CLOSED, 0.5 = HALF_OPEN
```

### 1.5 Verify Fallback Behavior

Submit another transaction (should fail immediately without waiting for timeout):
```powershell
$start = Get-Date
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" `
        -Method POST `
        -Headers $headers `
        -Body $body `
        -ContentType "application/json"
} catch {
    $duration = (Get-Date) - $start
    Write-Host "Request failed in $($duration.TotalMilliseconds)ms (expected <500ms)" -ForegroundColor Yellow
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}
```

Expected: Request fails in <500ms (fallback returns immediately instead of 20s timeout).

### 1.6 Verify Automatic Recovery

Wait for circuit breaker to transition to HALF_OPEN (20s):
```powershell
Write-Host "Waiting 20 seconds for circuit to enter HALF_OPEN state..." -ForegroundColor Cyan
Start-Sleep -Seconds 20

# Check state
curl http://localhost:8084/actuator/health/circuitbreakers | ConvertFrom-Json
```

Restart Fabric peer:
```powershell
docker start $(docker ps -aq --filter "name=peer0.org1.example.com")

# Wait for peer to initialize
Start-Sleep -Seconds 10
```

Submit test transaction (should trigger HALF_OPEN → CLOSED transition):
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" `
    -Method POST `
    -Headers $headers `
    -Body $body `
    -ContentType "application/json"

# Verify circuit is now CLOSED
curl http://localhost:8084/actuator/health/circuitbreakers | ConvertFrom-Json
```

Expected: Circuit transitions from HALF_OPEN → CLOSED after successful call.

---

## Test Scenario 2: Audit-Service Circuit Breaker for Blockchain Client

**Objective**: Verify audit-service circuit breaker protects against blockchain-service failures.

### 2.1 Setup

Verify baseline:
```powershell
# Check audit-service circuit breaker health
curl http://localhost:8085/actuator/health/circuitbreakers | ConvertFrom-Json
```

Expected:
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "blockchainClient": {
          "state": "CLOSED",
          "failureRate": "0.0%"
        }
      }
    }
  }
}
```

### 2.2 Stop Blockchain Service

```powershell
docker stop blockchain-service
```

### 2.3 Generate Audit Events

Trigger audit entries that will attempt blockchain submission:
```powershell
# Publish audit events directly to Kafka
$auditEvent = @{
    eventId = "evt-$(New-Guid)"
    tenantId = "tenant1"
    entityType = "TRANSACTION"
    entityId = "tx-001"
    action = "TRANSACTION_VERIFIED"
    actorId = "user-001"
    payload = @{ amount = 100 }
    occurredAt = (Get-Date).ToString("o")
} | ConvertTo-Json

# Send 10 audit events
1..10 | ForEach-Object {
    Write-Host "Sending audit event $_" -ForegroundColor Cyan
    # Publish to Kafka topic audit.entry
    # (Implementation depends on your Kafka client)
    Start-Sleep -Milliseconds 500
}
```

### 2.4 Verify Circuit Opens

After ~10 failed submissions to blockchain-service:
```powershell
curl http://localhost:8085/actuator/health/circuitbreakers | ConvertFrom-Json
```

Expected:
```json
{
  "status": "DOWN",
  "components": {
    "circuitBreakers": {
      "status": "DOWN",
      "details": {
        "blockchainClient": {
          "state": "OPEN",
          "failureRate": "100.0%"
        }
      }
    }
  }
}
```

### 2.5 Verify Audit Entries Marked as FAILED

Check audit entry status in database:
```powershell
# Connect to PostgreSQL
docker exec -it bbss_postgres psql -U postgres -d bbss_audit

# Query audit entries
SELECT audit_id, status, blockchain_tx_id FROM audit_entries ORDER BY created_at DESC LIMIT 10;
# Expected: status = 'FAILED', blockchain_tx_id = null
```

### 2.6 Restart Blockchain Service and Verify Recovery

```powershell
docker start blockchain-service

# Wait for service to initialize
Start-Sleep -Seconds 15

# Wait for circuit to enter HALF_OPEN (30s from open)
Start-Sleep -Seconds 30

# Check circuit state
curl http://localhost:8085/actuator/health/circuitbreakers | ConvertFrom-Json
```

Wait for scheduled retry job to process FAILED entries:
```powershell
# Audit-service retries FAILED entries every 60s
Write-Host "Waiting for retry scheduler (60s)..." -ForegroundColor Cyan
Start-Sleep -Seconds 60

# Check audit entries again
docker exec -it bbss_postgres psql -U postgres -d bbss_audit
SELECT audit_id, status, blockchain_tx_id FROM audit_entries WHERE status = 'FAILED' LIMIT 5;
# Expected: Some entries transitioned to COMMITTED
```

---

## Test Scenario 3: Bulkhead Pattern Limits Concurrent Calls

**Objective**: Verify bulkhead pattern prevents resource exhaustion.

### 3.1 Simulate Slow Fabric Gateway

Inject artificial delay in Fabric Gateway (requires code modification for testing):
```java
// FabricGatewayService.java (for testing only)
public byte[] submitTransaction(String chaincode, String function, String... args) {
    Thread.sleep(25000); // Simulate 25s slow call (exceeds 15s threshold)
    // ... rest of method
}
```

Rebuild and restart blockchain-service:
```powershell
cd backend/blockchain-service
mvn clean package -DskipTests
docker-compose up -d --build blockchain-service
```

### 3.2 Generate Concurrent Load

Send 10 concurrent transactions:
```powershell
$jobs = 1..10 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($iteration, $headers, $body)
        $start = Get-Date
        try {
            $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" `
                -Method POST `
                -Headers $headers `
                -Body $body `
                -ContentType "application/json" `
                -TimeoutSec 30
            $duration = (Get-Date) - $start
            Write-Output "[$iteration] Success in $($duration.TotalSeconds)s"
        } catch {
            $duration = (Get-Date) - $start
            Write-Output "[$iteration] Failed in $($duration.TotalSeconds)s: $($_.Exception.Message)"
        }
    } -ArgumentList $_, $headers, $body
}

# Wait for all jobs and collect results
$jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
```

### 3.3 Verify Bulkhead Behavior

Expected behavior:
- **First 5 calls**: Accepted by bulkhead, wait 25s (slow call)
- **Next 5 calls**: Rejected immediately (bulkhead full)
- Circuit breaker opens after 80% slow call rate

Check Prometheus metrics:
```promql
# Bulkhead usage
resilience4j_bulkhead_available_concurrent_calls{name="fabricGateway"}

# Expected: Drops to 0 when all slots occupied
```

### 3.4 Revert Code Changes

Remove artificial delay and rebuild:
```powershell
# Revert code changes to FabricGatewayService.java
git checkout backend/blockchain-service/src/main/java/com/bbss/blockchain/service/FabricGatewayService.java

# Rebuild
cd backend/blockchain-service
mvn clean package -DskipTests
docker-compose up -d --build blockchain-service
```

---

## Test Scenario 4: Slow Call Detection

**Objective**: Verify slow calls (>15s for Fabric, >25s for REST) count as failures.

### 4.1 Configure Fabric Chaincode Delay (Testing Environment)

Modify transaction-cc chaincode to introduce artificial delay:
```go
// blockchain/chaincode/transaction-cc/transaction.go
func (t *TransactionContract) CreateTransaction(...) error {
    time.Sleep(20 * time.Second) // Exceeds 15s slow call threshold
    // ... rest of function
}
```

Redeploy chaincode:
```bash
# From blockchain/network directory
./scripts/deploy-chaincode.sh transaction-cc 2.0
```

### 4.2 Submit Transactions

```powershell
1..5 | ForEach-Object {
    $start = Get-Date
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" `
            -Method POST `
            -Headers $headers `
            -Body $body `
            -ContentType "application/json"
    } catch {
        Write-Host "Transaction $_ failed after $((Get-Date) - $start).TotalSeconds)s" -ForegroundColor Red
    }
}
```

### 4.3 Verify Slow Call Rate Metrics

```powershell
# Query Prometheus
Start-Process "http://localhost:9090/graph?g0.expr=resilience4j_circuitbreaker_slow_call_rate%7Bname%3D%22fabricGateway%22%7D"
```

Expected: Slow call rate increases to 100%.

### 4.4 Verify Circuit Opens on Slow Call Rate

After 5 slow calls (meeting minimum-number-of-calls threshold):
```powershell
curl http://localhost:8084/actuator/health/circuitbreakers | ConvertFrom-Json
```

Expected: Circuit opens (slow calls treated as failures).

---

## Test Scenario 5: Prometheus Metrics Validation

**Objective**: Verify all circuit breaker metrics are exposed and accurate.

### 5.1 Available Metrics

Query all circuit breaker metrics:
```promql
# Circuit breaker state (0=CLOSED, 0.5=HALF_OPEN, 1=OPEN)
resilience4j_circuitbreaker_state{name="fabricGateway"}

# Total calls by kind (successful, failed, not_permitted)
resilience4j_circuitbreaker_calls_seconds_count{name="fabricGateway",kind="successful"}
resilience4j_circuitbreaker_calls_seconds_count{name="fabricGateway",kind="failed"}
resilience4j_circuitbreaker_calls_seconds_count{name="fabricGateway",kind="not_permitted"}

# Failure rate (0.0 - 1.0)
resilience4j_circuitbreaker_failure_rate{name="fabricGateway"}

# Slow call rate (0.0 - 1.0)
resilience4j_circuitbreaker_slow_call_rate{name="fabricGateway"}

# Bulkhead metrics
resilience4j_bulkhead_available_concurrent_calls{name="fabricGateway"}
resilience4j_bulkhead_max_allowed_concurrent_calls{name="fabricGateway"}
```

### 5.2 Verify Alert Rules

Check Prometheus alerts:
```powershell
Start-Process "http://localhost:9090/alerts"
```

Expected alerts (from alerts.yml):
- `CircuitBreakerOpen`: Fires when circuit is OPEN
- `CircuitBreakerHalfOpen`: Fires when circuit is HALF_OPEN (testing recovery)
- `HighCircuitBreakerFailureRate`: Fires when failure rate > 50%

### 5.3 Test Alert Firing

Trigger circuit breaker to open (follow Scenario 1 steps). Verify alert appears in Prometheus UI within 30s.

---

## Test Scenario 6: Graceful Degradation Verification

**Objective**: Verify fallback methods return appropriate responses.

### 6.1 Trigger Circuit Open State

Follow Scenario 1 to open blockchain-service circuit breaker.

### 6.2 Submit Transaction with Circuit OPEN

```powershell
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" `
        -Method POST `
        -Headers $headers `
        -Body $body `
        -ContentType "application/json"
    
    Write-Host "Response: $($response | ConvertTo-Json)" -ForegroundColor Yellow
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}
```

Expected response:
```json
{
  "transactionId": "tx-12345",
  "status": "PENDING_VERIFICATION",
  "message": "Blockchain verification temporarily unavailable",
  "blockchainTxId": null,
  "blockNumber": null
}
```

### 6.3 Verify Audit Service Fallback

With blockchain-service stopped, check audit entry behavior:
```powershell
# Query audit entries
docker exec -it bbss_postgres psql -U postgres -d bbss_audit
SELECT audit_id, status FROM audit_entries WHERE status = 'FAILED' LIMIT 5;
```

Expected: Entries marked as `FAILED` (for eventual retry), not stuck in `PENDING`.

---

## Cleanup

Reset all services to normal state:
```powershell
# Restart all services
docker-compose restart

# Clear circuit breaker state (wait for wait-duration)
Write-Host "Waiting 30 seconds for circuits to reset..." -ForegroundColor Cyan
Start-Sleep -Seconds 30

# Verify all circuits CLOSED
@("8084", "8085") | ForEach-Object {
    $port = $_
    Write-Host "Checking service on port $port..." -ForegroundColor Cyan
    curl "http://localhost:${port}/actuator/health/circuitbreakers" | ConvertFrom-Json
}
```

---

## Prometheus Query Examples

### Dashboard Queries

**Circuit Breaker State Panel**:
```promql
resilience4j_circuitbreaker_state{name=~"fabricGateway|blockchainClient"}
```

**Failure Rate Panel**:
```promql
rate(resilience4j_circuitbreaker_calls_seconds_count{kind="failed"}[5m])
  /
rate(resilience4j_circuitbreaker_calls_seconds_count[5m])
```

**Slow Call Rate Panel**:
```promql
resilience4j_circuitbreaker_slow_call_rate{name=~"fabricGateway|blockchainClient"}
```

**Bulkhead Utilization**:
```promql
(
  resilience4j_bulkhead_max_allowed_concurrent_calls{name="fabricGateway"}
  -
  resilience4j_bulkhead_available_concurrent_calls{name="fabricGateway"}
) / resilience4j_bulkhead_max_allowed_concurrent_calls{name="fabricGateway"} * 100
```

---

## Troubleshooting

### Circuit Breaker Not Opening

**Symptom**: Circuit remains CLOSED despite failures.

**Possible Causes**:
1. Minimum number of calls not reached (need 5+ calls)
2. Exception not in `record-exceptions` list
3. Circuit breaker annotation missing on method

**Solution**:
```powershell
# Check logs for Resilience4j events
docker logs blockchain-service | Select-String "Circuit breaker"

# Verify configuration loaded
curl http://localhost:8084/actuator/configprops | ConvertFrom-Json | Select-Object -ExpandProperty resilience4j
```

### Metrics Not Appearing in Prometheus

**Symptom**: Circuit breaker metrics missing from Prometheus.

**Solution**:
```powershell
# Verify actuator endpoint exposes metrics
curl http://localhost:8084/actuator/prometheus | Select-String "resilience4j"

# Check Prometheus targets
Start-Process "http://localhost:9090/targets"
# Verify blockchain-service target is UP
```

### Fallback Method Not Called

**Symptom**: Exceptions thrown instead of fallback.

**Solution**:
- Verify fallback method signature matches:
  ```java
  private ReturnType fallbackMethod(RequestType request, Throwable throwable)
  ```
- Check `fallbackMethod` parameter in `@CircuitBreaker` annotation
- Ensure method is in same class or accessible

---

## Expected Behavior Summary

| Scenario | Circuit State | Response Time | Outcome |
|----------|---------------|---------------|---------|
| Normal operation | CLOSED | <2s | Success |
| Fabric Gateway down (1-4 calls) | CLOSED | 20s timeout | Exception |
| Fabric Gateway down (5+ calls, >30% failure) | OPEN | <500ms | Fallback |
| Circuit OPEN, wait 20s | HALF_OPEN | Variable | Test recovery |
| Recovery successful | CLOSED | <2s | Resume normal |
| Slow call (>15s) | Counts as failure | Variable | Triggers threshold |
| Bulkhead full (>5 concurrent) | Any | <100ms | Immediate rejection |

---

## Next Steps

After completing circuit breaker testing:
1. **Configure Grafana Dashboards** (Phase 1.4) — Visualize circuit breaker states
2. **Set up Alerting** — Configure Alertmanager for circuit breaker OPEN alerts
3. **Load Testing** — Use k6/JMeter to simulate production load patterns
4. **Chaos Engineering** — Use Chaos Mesh to introduce random failures
