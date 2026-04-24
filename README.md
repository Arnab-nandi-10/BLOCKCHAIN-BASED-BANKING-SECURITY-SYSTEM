# Blockchain Banking Security System (Civic Savings)

A production-grade, multi-tenant banking security platform that anchors
financial transactions on a permissioned Hyperledger Fabric blockchain,
enforces real-time fraud detection with a machine-learning pipeline, and
exposes a unified API surface via a Spring Cloud Gateway.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Browser / Mobile                        │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTPS
                    ┌───────────▼───────────┐
                    │      API Gateway      │  :8080
                    │  (Spring Cloud GW)    │
                    └───┬───┬───┬───┬───┬──┘
          ┌─────────────┘   │   │   │   └──────────────┐
          │             ┌───┘   │   └───┐              │
     ┌────▼───┐   ┌─────▼─┐  ┌─▼────┐  ┌▼──────┐    ┌──▼────┐
     │  Auth  │   │Tenant │  │  Tx  │  │Audit  │    │Fraud  │
     │Service │   │Service│  │Service│ │Service│    │Detect │
     │ :8081  │   │ :8082 │  │ :8083│  │ :8085│     │ :8000 │
     └────┬───┘   └───┬───┘  └──┬───┘  └──┬───┘     └───┬───┘
          │           │         │          │            │
          └───────────┴────┬────┴──────────┘            │
                           │ Kafka (event bus)          │
                    ┌──────▼──────┐                     │
                    │  Blockchain │◄────────────────────┘
                    │  Service    │  :8084
                    └──────┬──────┘
                           │ Fabric SDK / gRPC
                    ┌──────▼──────┐
                    │ Hyperledger │
                    │   Fabric    │
                    └─────────────┘
```

All services write to **PostgreSQL** and emit domain events to **Apache Kafka**.
State changes are anchored on Hyperledger Fabric for immutable provenance.
**Prometheus + Grafana** provide observability across the entire stack.

---

## Tech Stack

| Layer              | Technology                          | Version  |
|--------------------|-------------------------------------|----------|
| API Gateway        | Spring Cloud Gateway                | 3.x      |
| Backend Services   | Spring Boot + Spring Security       | 3.2.x    |
| Fraud Detection    | FastAPI + scikit-learn / XGBoost    | Python 3.11 |
| Frontend Dashboard | Next.js + Tailwind CSS              | 14.x     |
| Blockchain Ledger  | Hyperledger Fabric                  | 2.5.x    |
| Chaincode Language | Go                                  | 1.21+    |
| Message Broker     | Apache Kafka (Confluent Platform)   | 7.6.0    |
| Relational DB      | PostgreSQL                          | 16       |
| Session Cache      | Redis                               | 7.2      |
| Observability      | Prometheus + Grafana                | 2.50 / 10.3 |
| Containerisation   | Docker + Docker Compose             | v3.8     |
| Build (Java)       | Apache Maven                        | 3.9+     |

---

## Prerequisites

Make sure the following tools are installed and available on your PATH before
you attempt to start the stack:

| Tool           | Minimum Version | Notes                          |
|----------------|-----------------|--------------------------------|
| Java (JDK)     | 25              | Required for all Spring services |
| Apache Maven   | 3.9+            | Java build tool                |
| Python         | 3.11+           | Fraud detection service        |
| Node.js        | 20 LTS          | Next.js dashboard              |
| Docker         | 24+             | Container runtime              |
| Docker Compose | 2.24+           | Multi-container orchestration  |
| Go             | 1.21+           | Hyperledger Fabric chaincode   |

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-org/blockchain-banking-security.git
cd blockchain-banking-security
```

### 2. Configure environment variables

```bash
cp .env.example .env
# Open .env and replace every placeholder value with real secrets.
# At minimum set POSTGRES_PASSWORD, REDIS_PASSWORD, and JWT_SECRET.
```

Generate a secure 512-bit JWT secret:

```bash
openssl rand -base64 64
```

### 3. Start the full stack

```bash
make dev-up
```

Docker Compose will pull images, build service containers, create Kafka topics,
and run PostgreSQL init scripts automatically.

### 4. Verify services

```bash
docker compose ps
```

All services should show status **healthy** or **running** within 90 seconds.

### 5. Enable Real Hyperledger Fabric

`make dev-up` keeps the platform available even before the Fabric network has
been started. When you want the blockchain service to use the real Fabric
gateway instead of the fallback controller, run:

```bash
make fabric-up
make fabric-deploy
make dev-up-fabric
```

The `dev-up-fabric` target applies [`docker-compose.fabric.yml`](docker-compose.fabric.yml),
which mounts the generated org admin certificates into `blockchain-service` and
attaches it to the `bbss-fabric-network`.

---

## Service URLs

| Service             | URL                              | Notes                        |
|---------------------|----------------------------------|------------------------------|
| Dashboard (UI)      | http://localhost:5000            | Next.js frontend             |
| API Gateway         | http://localhost:8080            | Entry point for all REST APIs |
| Auth Service        | http://localhost:8081/actuator   | Health, metrics              |
| Tenant Service      | http://localhost:8082/actuator   | Health, metrics              |
| Transaction Service | http://localhost:8083/actuator   | Health, metrics              |
| Blockchain Service  | http://localhost:8084/actuator   | Health, metrics              |
| Audit Service       | http://localhost:8085/actuator   | Health, metrics              |
| Fraud Detection     | http://localhost:8000/docs       | FastAPI Swagger UI           |
| Prometheus          | http://localhost:9090            | Metrics scraping             |
| Grafana             | http://localhost:3001            | Dashboards (admin/admin)     |

---

## Makefile Targets

```
make help             Print all available targets
make setup            Copy .env.example and scaffold directories
make dev-up           Start all services in detached mode
make dev-up-fabric    Restart blockchain-service with the real Fabric override
make dev-down         Stop all services (keeps volumes)
make dev-build        Rebuild all Docker images
make dev-logs         Stream logs from all services
make dev-clean        Stop services and remove all volumes

make fabric-up        Bring up the Hyperledger Fabric network
make fabric-down      Tear down the Fabric network
make fabric-deploy    Deploy / upgrade chaincodes

make test-backend     Run Java unit and integration tests (Maven)
make test-fraud       Run Python fraud-detection tests (pytest)
make migrate          Execute Flyway database migrations

make lint-frontend    Lint the Next.js dashboard
make build-frontend   Build the Next.js dashboard for production
```

---

## Project Structure

```
blockchain-banking-security/
├── .env.example                   # Environment variable template
├── docker-compose.yml             # Full-stack container orchestration
├── Makefile                       # Developer workflow shortcuts
│
├── backend/
│   ├── api-gateway/               # Spring Cloud Gateway
│   ├── auth-service/              # JWT + MFA authentication
│   ├── tenant-service/            # Multi-tenant management
│   ├── transaction-service/       # Transaction lifecycle
│   ├── blockchain-service/        # Fabric SDK integration
│   └── audit-service/             # Immutable audit log
│
├── fraud-detection/               # FastAPI + ML pipeline
│
├── frontend/
│   └── dashboard/                 # Next.js 14 admin dashboard
│
├── blockchain/
│   ├── network/                   # Fabric network scripts
│   └── chaincode/
│       ├── transaction-cc/        # Transaction chaincode (Go)
│       └── audit-cc/              # Audit chaincode (Go)
│
└── infrastructure/
    ├── docker/
    │   ├── postgres/init/         # Database bootstrap SQL
    │   └── kafka/                 # Topic bootstrap script
    └── monitoring/
        ├── prometheus/            # prometheus.yml
        └── grafana/               # Provisioning dashboards
```

---

## Contributing

1. Fork the repository and create a feature branch.
2. Ensure all tests pass: `make test-backend && make test-fraud`.
3. Open a pull request describing your change.

---

## License

This project is licensed under the **MIT License**.
See [LICENSE](LICENSE) for the full text.
