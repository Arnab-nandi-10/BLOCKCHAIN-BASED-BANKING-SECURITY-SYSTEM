# Health Check Testing Plan

## Overview
This document provides step-by-step instructions for testing the production-grade health checks implemented across all backend services. Testing validates that health indicators correctly detect failures and that Docker Compose orchestration properly responds to unhealthy states.

---

## Prerequisites

**Before testing:**
- All services running: `docker-compose ps`
- Verify health endpoints are accessible:
  ```powershell
  curl http://localhost:8081/actuator/health/readiness  # auth-service
  curl http://localhost:8082/actuator/health/readiness  # tenant-service
  curl http://localhost:8083/actuator/health/readiness  # transaction-service
  curl http://localhost:8084/actuator/health/readiness  # blockchain-service
  curl http://localhost:8085/actuator/health/readiness  # audit-service
  curl http://localhost:8000/health/ready              # fraud-detection
  curl http://localhost:8080/actuator/health/readiness  # api-gateway
  ```

---

## Test Scenario 1: Database Health Check

### Objective
Verify services correctly detect PostgreSQL unavailability and report unhealthy status.

### Steps

**1.1 Verify baseline health**
```powershell
# All services should report UP
curl http://localhost:8083/actuator/health | ConvertFrom-Json | Select-Object status
```

**1.2 Stop PostgreSQL container**
```powershell
docker stop bbss-postgres
```

**1.3 Wait for health check failure (2-5 seconds)**
```powershell
Start-Sleep -Seconds 5
```

**1.4 Check transaction-service health**
```powershell
# Expected: Status DOWN, database component UNHEALTHY
$response = Invoke-WebRequest -Uri "http://localhost:8083/actuator/health" -ErrorAction SilentlyContinue
$response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

**Expected Results:**
- Health status: `DOWN`
- Database component status: `DOWN`
- Error message indicating connection failure

**1.5 Verify Docker healthcheck failure**
```powershell
# Container should show unhealthy status
docker ps --format "table {{.Names}}\t{{.Status}}" | Select-String "transaction-service"
```

**Expected**: Status shows "(unhealthy)"

**1.6 Start PostgreSQL and verify recovery**
```powershell
docker start bbss-postgres

# Wait for PostgreSQL to be ready
Start-Sleep -Seconds 10

# Check health again
curl http://localhost:8083/actuator/health/readiness | ConvertFrom-Json
```

**Expected**: Status `UP`, database component `UP`

---

## Test Scenario 2: Kafka Health Check

### Objective
Verify services correctly detect Kafka cluster unavailability.

### Steps

**2.1 Verify baseline health**
```powershell
curl http://localhost:8083/actuator/health/readiness | ConvertFrom-Json
```

**2.2 Stop Kafka container**
```powershell
docker stop bbss-kafka
```

**2.3 Wait for health check failure (3-5 seconds)**
```powershell
Start-Sleep -Seconds 5
```

**2.4 Check service health**
```powershell
# Expected: Kafka component DOWN
curl http://localhost:8083/actuator/health | ConvertFrom-Json | Select-Object -ExpandProperty components
```

**Expected Results:**
- Kafka component status: `DOWN`
- Error message about cluster connectivity

**2.5 Check multiple services**
```powershell
# All services depending on Kafka should show unhealthy
$services = @("transaction-service:8083", "blockchain-service:8084", "audit-service:8085")
foreach ($svc in $services) {
    Write-Host "`n=== Checking $svc ==="
    curl "http://$svc/actuator/health" | ConvertFrom-Json | Select-Object status
}
```

**2.6 Start Kafka and verify recovery**
```powershell
docker start bbss-kafka

# Wait for Kafka to be ready
Start-Sleep -Seconds 15

# Verify health recovery
curl http://localhost:8083/actuator/health/readiness | ConvertFrom-Json
```

---

## Test Scenario 3: Fabric Gateway Health Check

### Objective
Verify blockchain-service correctly detects Hyperledger Fabric Gateway issues.

### Steps

**3.1 Verify Fabric Gateway health (if enabled)**
```powershell
curl http://localhost:8084/actuator/health | ConvertFrom-Json | Select-Object -ExpandProperty components
```

**Expected** (if FABRIC_SKIP_CONNECT=true):
- FabricGateway component not present (conditional health indicator)

**Expected** (if Fabric network running):
- FabricGateway component status: `UP`
- Contract details: chaincodeId, channelName, networkName

**3.2 Simulate Fabric configuration mismatch**

Restart blockchain-service with incorrect channel name:
```powershell
docker-compose stop blockchain-service

# Create temporary override
$env:FABRIC_CHANNEL_NAME = "incorrect-channel"

docker-compose up -d blockchain-service

# Wait for startup
Start-Sleep -Seconds 30
```

**3.3 Check health**
```powershell
curl http://localhost:8084/actuator/health | ConvertFrom-Json
```

**Expected:**
- FabricGateway status: `DOWN`
- Error message about channel name mismatch

**3.4 Restore correct configuration**
```powershell
Remove-Item Env:\FABRIC_CHANNEL_NAME
docker-compose restart blockchain-service
```

---

## Test Scenario 4: Docker Compose Dependency Management

### Objective
Verify Docker Compose properly waits for services to be healthy before starting dependent services.

### Steps

**4.1 Stop all services**
```powershell
docker-compose down
```

**4.2 Start services with status monitoring**
```powershell
# In one terminal, watch container status
docker-compose up -d
while ($true) {
    Clear-Host
    docker ps --format "table {{.Names}}\t{{.Status}}" | Select-String "bbss-"
    Start-Sleep -Seconds 2
}
```

**Expected Startup Sequence:**
1. **Infrastructure services start first:**
   - bbss-postgres (healthy)
   - bbss-redis (healthy)
   - bbss-kafka (healthy after ~15s)

2. **Auth service starts:**
   - Waits for postgres, kafka healthy
   - Shows "starting" → "healthy" after 60s

3. **Domain services start:**
   - tenant-service, blockchain-service (depend on postgres, kafka)
   - Wait for dependencies to be healthy

4. **Transaction & audit services start:**
   - transaction-service depends on blockchain-service healthy
   - audit-service depends on postgres, kafka healthy

5. **API Gateway starts last:**
   - Waits for all backend services to be healthy
   - Longest start_period (90s) due to most dependencies

**4.3 Verify startup logs**
```powershell
# Check transaction-service waited for blockchain-service
docker logs bbss-transaction-service | Select-String "Started TransactionServiceApplication"
docker logs bbss-blockchain-service | Select-String "Started BlockchainServiceApplication"
```

**Expected**: transaction-service starts AFTER blockchain-service is healthy

---

## Test Scenario 5: Readiness vs Liveness Probes

### Objective
Validate difference between liveness (process alive) and readiness (dependencies healthy).

### Steps

**5.1 Check liveness endpoint**
```powershell
# Should ALWAYS return 200 if process is running
curl http://localhost:8083/actuator/health/liveness | ConvertFrom-Json
```

**Expected**: `{"status": "UP"}`

**5.2 Stop PostgreSQL (break dependency)**
```powershell
docker stop bbss-postgres
Start-Sleep -Seconds 5
```

**5.3 Check both probes**
```powershell
# Liveness should still be UP (process alive)
curl http://localhost:8083/actuator/health/liveness | ConvertFrom-Json

# Readiness should be DOWN (dependencies unhealthy)
curl http://localhost:8083/actuator/health/readiness -ErrorAction SilentlyContinue
```

**Expected:**
- Liveness: `UP` (process running)
- Readiness: `503 Service Unavailable` (not ready for traffic)

**5.4 Restore PostgreSQL**
```powershell
docker start bbss-postgres
Start-Sleep -Seconds 10

# Both probes should be UP
curl http://localhost:8083/actuator/health/liveness | ConvertFrom-Json
curl http://localhost:8083/actuator/health/readiness | ConvertFrom-Json
```

---

## Test Scenario 6: Fraud Detection Health Check

### Objective
Verify FastAPI fraud-detection service health checks work correctly.

### Steps

**6.1 Check fraud-detection health**
```powershell
curl http://localhost:8000/health | ConvertFrom-Json
curl http://localhost:8000/health/ready | ConvertFrom-Json
```

**Expected:**
- `/health`: Always UP (liveness)
- `/health/ready`: Checks model loaded + database connectivity

**6.2 Stop PostgreSQL**
```powershell
docker stop bbss-postgres
Start-Sleep -Seconds 5
```

**6.3 Check readiness probe**
```powershell
# Expected: 503 Service Unavailable
Invoke-WebRequest -Uri "http://localhost:8000/health/ready" -ErrorAction SilentlyContinue | Select-Object StatusCode
```

**Expected:**
- Status code: `503`
- Response body shows `database: false`

**6.4 Restore PostgreSQL**
```powershell
docker start bbss-postgres
Start-Sleep -Seconds 10

curl http://localhost:8000/health/ready | ConvertFrom-Json
```

---

## Test Scenario 7: Prometheus Health Metrics Scraping

### Objective
Verify Prometheus can scrape health metrics and trigger alerts.

### Steps

**7.1 Check Prometheus targets**
```powershell
# Open Prometheus UI
Start-Process "http://localhost:9090/targets"
```

**Expected:**
- All service targets show `UP`
- Last scrape time < 30s ago

**7.2 Query health indicator metrics**
```powershell
# In Prometheus UI, run query:
# health_component_status{component="database"}
```

**Expected:**
- All services show value `1` (healthy)

**7.3 Simulate database failure**
```powershell
docker stop bbss-postgres
Start-Sleep -Seconds 10
```

**7.4 Query health metrics again**
```
health_component_status{component="database"}
```

**Expected:**
- Services show value `0` (unhealthy)

**7.5 Check alert rules**
```powershell
# In Prometheus UI, go to: http://localhost:9090/alerts
# Look for "DatabaseHealthCheckFailing" alert
```

**Expected:**
- Alert status: `PENDING` (after 30s)
- Alert status: `FIRING` (after alert duration threshold)

**7.6 Restore PostgreSQL**
```powershell
docker start bbss-postgres
Start-Sleep -Seconds 15
```

**Expected:**
- Metrics return to `1`
- Alert resolves automatically

---

## Verification Checklist

After completing all test scenarios, verify:

- [ ] **Database health checks** detect PostgreSQL failures within 2-5 seconds
- [ ] **Kafka health checks** detect cluster failures within 3-5 seconds
- [ ] **Fabric Gateway health checks** detect configuration mismatches (blockchain-service)
- [ ] **Docker healthchecks** properly mark containers as unhealthy
- [ ] **Docker Compose** dependency orchestration waits for `service_healthy` conditions
- [ ] **Liveness probes** remain UP when process is running (even with failed dependencies)
- [ ] **Readiness probes** return 503 when dependencies are unavailable
- [ ] **Fraud detection** health checks validate model + database connectivity
- [ ] **Prometheus** successfully scrapes health metrics from all services
- [ ] **Alert rules** trigger when health checks fail

---

## Troubleshooting

### Health check always returns DOWN
- Check application logs: `docker logs bbss-{service-name}`
- Verify custom health indicators are properly registered: Look for `DatabaseHealthIndicator`, `KafkaHealthIndicator` in logs
- Check application.yml: Ensure `management.health.{component}.enabled: true`

### Docker healthcheck not executing
- Verify curl is available in container: `docker exec bbss-auth-service curl --version`
- Check healthcheck interval/timeout: `docker inspect bbss-auth-service | Select-String "Health"`
- Review container logs for healthcheck failures: `docker logs bbss-auth-service 2>&1 | Select-String "health"`

### Service starts before dependencies ready
- Verify docker-compose.yml uses `condition: service_healthy` (not `service_started`)
- Check dependency service has healthcheck configured
- Increase `start_period` if service needs more initialization time

### Prometheus alerts not firing
- Verify alert rules loaded: Check Prometheus UI `/config` page
- Check metric names match alert queries: Query `up{job="transaction-service"}` manually
- Verify alert evaluation interval: Check `evaluation_interval` in prometheus.yml

---

## Next Steps

After successful testing:

1. **Extend health indicators to all services:**
   - auth-service
   - tenant-service
   - audit-service
   - api-gateway

2. **Add custom health indicators:**
   - Redis connectivity check (api-gateway)
   - External API health checks (if applicable)

3. **Configure Alertmanager:**
   - Set up email/Slack notifications for critical alerts
   - Configure alert routing and grouping

4. **Implement health dashboards:**
   - Create Grafana dashboard showing real-time health status
   - Display dependency health matrix (service × component)

5. **Move to Phase 1.1: Distributed Tracing**
   - Implement Spring Cloud Sleuth + Jaeger
   - Trace request flows across microservices
