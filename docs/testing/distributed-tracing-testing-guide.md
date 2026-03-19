# Distributed Tracing Testing Guide

## Overview
This guide provides step-by-step instructions for testing the distributed tracing implementation across all microservices using Jaeger.

---

## Phase 1.1: Distributed Tracing System — COMPLETED

### What Was Implemented

**1. Spring Cloud Sleuth Dependencies (Micrometer Tracing)**
- Added `micrometer-tracing-bridge-brave` to all backend services
- Added `zipkin-reporter-brave` for Zipkin-compatible trace export
- All traces exported to Jaeger via Zipkin-compatible endpoint (port 9411)

**2. Jaeger All-in-One Container**
- Container: `jaegertracing/all-in-one:1.62`
- Jaeger UI: http://localhost:16686
- Collector endpoints:
  - HTTP: http://localhost:14268
  - Zipkin-compatible: http://localhost:9411
  - gRPC: http://localhost:14250

**3. Tracing Configuration**
- Sampling rate: 100% (all requests traced in development)
- Trace/span IDs propagated via W3C Trace Context headers
- Custom logging pattern includes `[traceId/spanId]` in all services

**4. Custom Business Context Tagging**

**API Gateway (`TracingConfig.java`):**
- `tenant.id` — Extracted from X-Tenant-Id header
- `user.id` — Extracted from X-User-Id header
- `transaction.id` — Extracted from X-Transaction-Id header
- `http.target_service` — Downstream service name
- `http.client_ip` — Client IP (X-Forwarded-For aware)
- `http.method` — HTTP method (GET, POST, etc.)
- `http.path` — Request path
- `environment` — Deployment environment (dev, staging, prod)
- `service.version` — Service version
- `deployment.region` — Deployment region

**Transaction Service (`TracingConfig.java` with AOP aspects):**
- `transaction.amount` — Transaction amount in BigDecimal
- `transaction.currency` — Currency code (USD, EUR, etc.)
- `transaction.type` — Transaction type (TRANSFER, PAYMENT, etc.)
- `fraud.score` — Fraud detection score (0.0 - 1.0)
- `fraud.risk_level` — Risk level (LOW, MEDIUM, HIGH)
- `fraud.verdict` — Fraud decision (VERIFIED, FRAUD_HOLD, BLOCKED)
- `blockchain.operation` — Operation performed (record_transaction)
- `blockchain.status` — Status (SUCCESS, FAILED)
- `blockchain.tx_hash` — Blockchain transaction hash
- `service.name` — Service name (transaction-service)
- `service.layer` — Layer (backend)

---

## Prerequisites

**Before testing, ensure:**
1. All services are stopped: `docker-compose down`
2. Images are rebuilt with new dependencies: `docker-compose build`
3. All containers start fresh: `docker-compose up -d`
4. Jaeger UI is accessible: http://localhost:16686

---

## Test Scenario 1: Basic Trace Propagation

### Objective
Verify traces flow from API Gateway through all downstream services.

### Steps

**1.1 Start all services**
```powershell
cd "C:\Users\Arnab Nandi\OneDrive\Desktop\blockchain-banking-security"
docker-compose down
docker-compose build
docker-compose up -d
```

**1.2 Wait for services to be healthy**
```powershell
# Wait ~90 seconds for all healthchecks to pass
Start-Sleep -Seconds 90

# Verify all services healthy
docker-compose ps
```

**Expected**: All services show "healthy" status

**1.3 Authenticate to get JWT token**
```powershell
$loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body '{
  "email": "admin@example.com",
  "password": "Admin@123"
}'

$token = $loginResponse.token
Write-Host "Token: $token"
```

**1.4 Create a transaction (generates multi-service trace)**
```powershell
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-Id" = "tenant-uuid-123"
    "X-User-Id" = "user-uuid-456"
}

$txRequest = @{
    fromWalletId = "wallet-sender-001"
    toWalletId = "wallet-receiver-002"
    amount = 5000.00
    currency = "USD"
    type = "TRANSFER"
    description = "Distributed tracing test transaction"
} | ConvertTo-Json

$txResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/transactions" -Method POST -Headers $headers -Body $txRequest

Write-Host "Transaction created: $($txResponse.id)"
Write-Host "Status: $($txResponse.status)"
Write-Host "Fraud Score: $($txResponse.fraudScore)"
```

**1.5 Open Jaeger UI and search for trace**
```powershell
Start-Process "http://localhost:16686"
```

**In Jaeger UI:**
1. Select service: `api-gateway`
2. Click "Find Traces"
3. Click on the most recent trace
4. Verify span hierarchy:
   ```
   api-gateway: POST /api/transactions
   ├─ transaction-service: createTransaction
   │  ├─ fraud-detection: POST /fraud/check
   │  ├─ blockchain-service: POST /blockchain/record
   │  └─ database: INSERT transaction
   ├─ auth-service: validateToken (if JWT validation happened)
   └─ kafka: publish tx.submitted event
   ```

**Expected Results:**
- Trace shows complete request flow across services
- All spans have same `traceId`
- Each span has unique `spanId`
- Parent-child relationships correctly established
- Total trace duration < 2 seconds (for healthy system)

---

## Test Scenario 2: Custom Tag Verification

### Objective
Verify custom business context tags are correctly attached to spans.

### Steps

**2.1 Execute same transaction from Scenario 1**

**2.2 In Jaeger UI, examine span tags**

**API Gateway span tags should include:**
```
tenant.id: tenant-uuid-123
user.id: user-uuid-456
http.target_service: transaction-service
http.method: POST
http.path: /api/transactions
http.client_ip: 172.x.x.x (Docker container IP)
environment: development
```

**Transaction Service span tags should include:**
```
transaction.amount: 5000.00
transaction.currency: USD
transaction.type: TRANSFER
fraud.score: 0.XXXX (depends on fraud model)
fraud.risk_level: LOW / MEDIUM / HIGH
fraud.verdict: VERIFIED / FRAUD_HOLD / BLOCKED
blockchain.operation: record_transaction
blockchain.status: SUCCESS
service.name: transaction-service
service.layer: backend
```

**Expected**: All custom tags present and accurately reflect transaction details

---

## Test Scenario 3: Trace Search by Custom Tags

### Objective
Demonstrate powerful trace querying using custom business tags.

### Steps

**3.1 Create multiple test transactions with different characteristics**

**High-value transaction (should trigger fraud detection):**
```powershell
$highValueTx = @{
    fromWalletId = "wallet-001"
    toWalletId = "wallet-002"
    amount = 950000.00  # Above fraud threshold
    currency = "USD"
    type = "TRANSFER"
    description = "High-value transaction for trace testing"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/transactions" -Method POST -Headers $headers -Body $highValueTx
```

**Medium-value transaction:**
```powershell
$mediumValueTx = @{
    fromWalletId = "wallet-003"
    toWalletId = "wallet-004"
    amount = 500000.00  # Should trigger FRAUD_HOLD
    currency = "USD"
    type = "PAYMENT"
    description = "Medium-value transaction for trace testing"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/transactions" -Method POST -Headers $headers -Body $mediumValueTx
```

**Low-value transaction:**
```powershell
$lowValueTx = @{
    fromWalletId = "wallet-005"
    toWalletId = "wallet-006"
    amount = 100.00
    currency = "USD"
    type = "REFUND"
    description = "Low-value transaction for trace testing"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/transactions" -Method POST -Headers $headers -Body $lowValueTx
```

**3.2 Search traces by custom tags in Jaeger UI**

**Find all blocked transactions:**
1. Service: `transaction-service`
2. Tags: `fraud.verdict=BLOCKED`
3. Click "Find Traces"

**Expected**: Only high-value transaction ($950,000) appears

**Find all high-risk transactions:**
1. Service: `transaction-service`
2. Tags: `fraud.risk_level=HIGH`
3. Click "Find Traces"

**Expected**: Transactions with fraud scores > 0.60

**Find all transactions > $100,000:**
1. Service: `transaction-service`
2. Tags: `transaction.amount>100000`
3. Click "Find Traces"

**Expected**: High and medium value transactions appear

**Find all transactions for specific tenant:**
1. Service: `api-gateway`
2. Tags: `tenant.id=tenant-uuid-123`
3. Click "Find Traces"

**Expected**: All transactions from test scenarios appear

---

## Test Scenario 4: Multi-Service Error Tracing

### Objective
Verify trace propagation and error tagging when services fail.

### Steps

**4.1 Simulate blockchain service failure**
```powershell
# Stop blockchain service
docker-compose stop blockchain-service
Start-Sleep -Seconds 5
```

**4.2 Create transaction (should trigger circuit breaker)**
```powershell
$txRequest = @{
    fromWalletId = "wallet-007"
    toWalletId = "wallet-008"
    amount = 1000.00
    currency = "USD"
    type = "TRANSFER"
    description = "Transaction during blockchain service failure"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/transactions" -Method POST -Headers $headers -Body $txRequest
} catch {
    Write-Host "Expected error: $($_.Exception.Message)"
}
```

**4.3 Examine trace in Jaeger UI**

**Expected observations:**
- Trace shows full span hierarchy up to failure point
- Blockchain service span marked with error (red color)
- Error tags present:
  ```
  error: true
  error.kind: BlockchainServiceException
  blockchain.status: FAILED
  http.status_code: 503 (Service Unavailable)
  ```
- Circuit breaker fallback logic visible in spans
- Total trace duration reflects timeout settings (30s for blockchain)

**4.4 Restore blockchain service**
```powershell
docker-compose start blockchain-service
Start-Sleep -Seconds 30  # Wait for healthcheck
```

---

## Test Scenario 5: Log Correlation with Trace IDs

### Objective
Verify trace/span IDs appear in application logs for correlation.

### Steps

**5.1 Create transaction**
```powershell
# Use same transaction creation from Scenario 1
```

**5.2 View transaction-service logs**
```powershell
docker logs bbss-transaction-service --tail 50
```

**Expected log format:**
```
2026-03-09 10:15:30.123 [http-nio-8083-exec-5] INFO  [1a2b3c4d5e6f7g8h/9i0j1k2l] [tenant-uuid-123] [tx-uuid] com.bbss.transaction.service.TransactionService - Creating transaction: amount=5000.00, currency=USD
```

**Verify:**
- `[traceId/spanId]` present in all log entries
- `traceId` matches Jaeger trace ID
- Logs from different services share same `traceId` for single request
- Tenant ID and transaction ID also present for business context

**5.3 Search logs by trace ID**
```powershell
# Extract trace ID from Jaeger UI (e.g., 1a2b3c4d5e6f7g8h)
$traceId = "1a2b3c4d5e6f7g8h"

# Search across all services
docker logs bbss-api-gateway 2>&1 | Select-String $traceId
docker logs bbss-transaction-service 2>&1 | Select-String $traceId
docker logs bbss-blockchain-service 2>&1 | Select-String $traceId
docker logs bbss-fraud-detection 2>&1 | Select-String $traceId
```

**Expected**: All service logs for the request display with trace ID correlation

---

## Test Scenario 6: Sampling and Performance Impact

### Objective
Validate 100% sampling works correctly and measure performance baseline.

### Steps

**6.1 Generate 10 concurrent transactions**
```powershell
1..10 | ForEach-Object -Parallel {
    $token = $using:token
    $headers = @{
        "Authorization" = "Bearer $token"
        "Content-Type" = "application/json"
    }
    
    $txRequest = @{
        fromWalletId = "wallet-$_-sender"
        toWalletId = "wallet-$_-receiver"
        amount = (Get-Random -Minimum 100 -Maximum 10000)
        currency = "USD"
        type = "TRANSFER"
        description = "Load test transaction $_"
    } | ConvertTo-Json
    
    Invoke-RestMethod -Uri "http://localhost:8080/api/transactions" -Method POST -Headers $headers -Body $txRequest
} -ThrottleLimit 5
```

**6.2 Verify all traces in Jaeger**
```
Service: api-gateway
Lookback: Last 15 minutes
Limit: 100
```

**Expected**: All 10+1 (previous tests) transactions have traces

**6.3 Measure p95 latency**

In Jaeger UI:
1. Select service: `api-gateway`
2. Operation: `POST /api/transactions`
3. Observe latency distribution histogram

**Expected baseline (with 100% sampling, no load):**
- p50: < 500ms
- p95: < 1000ms
- p99: < 2000ms

**Note**: These are development environment baselines. Production performance will differ.

---

## Advanced: Jaeger Query Language (JQL) Examples

### Find transactions by business criteria

**All blocked transactions in last hour:**
```
service=transaction-service 
fraud.verdict=BLOCKED 
minDuration>0
```

**High-value USD transfers:**
```
service=transaction-service 
transaction.currency=USD 
transaction.type=TRANSFER 
transaction.amount>50000
```

**Failed blockchain operations:**
```
service=transaction-service 
blockchain.status=FAILED 
error=true
```

**Slow transactions (> 2 seconds):**
```
service=api-gateway 
operation="POST /api/transactions" 
minDuration>2s
```

**Transactions for specific tenant:**
```
service=api-gateway 
tenant.id=tenant-uuid-123 
http.path=/api/transactions
```

---

## Verification Checklist

After completing all test scenarios, verify:

- [ ] **Trace propagation** works across Gateway → Transaction → Blockchain → Audit
- [ ] **Custom tags** present in API Gateway spans (tenant.id, user.id, http.*)
- [ ] **Transaction tags** present in Transaction Service spans (amount, currency, type)
- [ ] **Fraud tags** present in Transaction Service spans (score, risk_level, verdict)
- [ ] **Blockchain tags** present when blockchain operations execute
- [ ] **Trace IDs** appear in application logs with format `[traceId/spanId]`
- [ ] **Log correlation** works — can search logs by trace ID
- [ ] **Error tracing** captures failures and propagates error context
- [ ] **Jaeger UI search** by custom tags returns correct results
- [ ] **100% sampling** confirmed — all requests have traces
- [ ] **Performance baseline** measured (p50, p95, p99 latencies)

---

## Troubleshooting

### No traces appearing in Jaeger

**Check Jaeger container logs:**
```powershell
docker logs bbss-jaeger
```

**Verify services can reach Jaeger:**
```powershell
docker exec bbss-transaction-service curl -f http://jaeger:9411 || echo "Cannot reach Jaeger"
```

**Verify tracing configuration:**
```powershell
docker exec bbss-transaction-service curl -s http://localhost:8083/actuator/configprops | Select-String "tracing"
```

### Trace IDs not in logs

**Verify logging pattern includes traceId/spanId:**
```powershell
# Check application.yml
Get-Content backend/transaction-service/src/main/resources/application.yml | Select-String "traceId"
```

**Expected**: `%X{traceId}/%X{spanId}` in console logging pattern

### Custom tags missing

**For API Gateway tags:**
- Verify `TracingConfig.java` bean is loaded
- Check X-Tenant-Id, X-User-Id headers are sent in request
- Review API Gateway logs for "Enhanced trace with business context" debug messages

**For Transaction Service tags:**
- Verify `spring-boot-starter-aop` dependency present in pom.xml
- Check `@Aspect` class is registered as Spring component
- Review transaction service logs for "Tagged transaction span" debug messages

### High latency with tracing

**Expected overhead with 100% sampling:** ~5-10ms per request

**If overhead > 50ms:**
- Reduce sampling rate in production: `management.tracing.sampling.probability: 0.1` (10%)
- Check Jaeger collector performance (should handle 1000+ spans/second)
- Verify network latency between services and Jaeger

---

## Next Steps After Verification

1. **Adjust sampling rate for production:**
   - Update all application.yml: `probability: 0.1` (10% sampling)
   - Use higher sampling for specific operations (fraud alerts: 100%, normal: 10%)

2. **Configure Jaeger storage backend:**
   - Current: In-memory (development only)
   - Production: Elasticsearch, Cassandra, or Badger

3. **Set up alerts on trace latencies:**
   - Use Prometheus metrics: `http_server_requests_seconds_bucket`
   - Alert on p95 > threshold

4. **Create Grafana dashboards for trace metrics:**
   - Request rate by service
   - Error rate by service
   - Latency distribution (p50, p95, p99)
   - Trace sampling rate

5. **Move to Phase 1.2: Comprehensive Circuit Breaker Coverage**
