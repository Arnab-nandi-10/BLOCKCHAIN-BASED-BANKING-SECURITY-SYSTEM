# =============================================================================
# Blockchain Banking Security System — Makefile
# =============================================================================
# Usage: make <target>
# Run `make help` to see all available targets.
# =============================================================================

SHELL := /bin/bash
.DEFAULT_GOAL := help

# Colours for terminal output
CYAN  := \033[0;36m
GREEN := \033[0;32m
RESET := \033[0m

.PHONY: help \
        setup \
        dev-up dev-down dev-build dev-logs dev-clean \
        fabric-up fabric-down fabric-deploy \
        test-backend test-fraud \
        migrate \
        lint-frontend build-frontend

# -----------------------------------------------------------------------------
# help
# -----------------------------------------------------------------------------
help: ## Print this help message
	@printf "\n$(CYAN)Blockchain Banking Security System$(RESET)\n\n"
	@printf "Usage:  make $(CYAN)<target>$(RESET)\n\n"
	@printf "Targets:\n"
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*##/ { printf "  $(CYAN)%-22s$(RESET) %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@printf "\n"

# -----------------------------------------------------------------------------
# setup
# -----------------------------------------------------------------------------
setup: ## Copy .env.example to .env and create required local directories
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		printf "$(GREEN)Created .env from .env.example — please edit the secrets.$(RESET)\n"; \
	else \
		printf ".env already exists, skipping copy.\n"; \
	fi
	@mkdir -p \
		infrastructure/docker/postgres/init \
		infrastructure/docker/kafka \
		infrastructure/monitoring/prometheus \
		infrastructure/monitoring/grafana/provisioning/datasources \
		infrastructure/monitoring/grafana/provisioning/dashboards \
		blockchain/network/scripts \
		blockchain/chaincode/transaction-cc \
		blockchain/chaincode/audit-cc \
		backend/auth-service \
		backend/tenant-service \
		backend/transaction-service \
		backend/blockchain-service \
		backend/audit-service \
		backend/api-gateway \
		fraud-detection \
		frontend/dashboard
	@printf "$(GREEN)Directory structure ready.$(RESET)\n"

# -----------------------------------------------------------------------------
# Docker Compose — development lifecycle
# -----------------------------------------------------------------------------
dev-up: ## Start all services in detached mode
	docker compose up -d
	@printf "$(GREEN)All services started. Dashboard: http://localhost:3000  Gateway: http://localhost:8080$(RESET)\n"

dev-down: ## Stop all running services (keeps volumes)
	docker compose down
	@printf "Services stopped.\n"

dev-build: ## Rebuild all service images
	docker compose build --parallel
	@printf "$(GREEN)All images built.$(RESET)\n"

dev-logs: ## Stream logs from all services (Ctrl-C to exit)
	docker compose logs -f

dev-clean: ## Stop services, remove containers, volumes, and orphans
	docker compose down -v --remove-orphans
	@printf "$(GREEN)Environment cleaned (volumes removed).$(RESET)\n"

# -----------------------------------------------------------------------------
# Hyperledger Fabric
# -----------------------------------------------------------------------------
fabric-up: ## Bring up the Hyperledger Fabric network
	cd blockchain/network && bash scripts/network-up.sh

fabric-down: ## Tear down the Hyperledger Fabric network
	cd blockchain/network && bash scripts/network-down.sh

fabric-deploy: ## Deploy / upgrade chaincodes on the Fabric channel
	cd blockchain/network && bash scripts/deploy-chaincode.sh

# -----------------------------------------------------------------------------
# Testing
# -----------------------------------------------------------------------------
test-backend: ## Run all Java/Spring Boot unit & integration tests
	mvn --file backend/pom.xml test -pl . --also-make
	@printf "$(GREEN)Backend tests complete.$(RESET)\n"

test-fraud: ## Run Python fraud-detection tests with pytest
	cd fraud-detection && pytest --tb=short -q
	@printf "$(GREEN)Fraud-detection tests complete.$(RESET)\n"

# -----------------------------------------------------------------------------
# Database Migrations
# -----------------------------------------------------------------------------
migrate: ## Run Flyway database migrations for all services
	@printf "Running Flyway migrations...\n"
	mvn --file backend/pom.xml flyway:migrate \
		-Dflyway.url=jdbc:postgresql://$${POSTGRES_HOST:-localhost}:$${POSTGRES_PORT:-5432}/bbss_auth \
		-Dflyway.user=$${POSTGRES_USER} \
		-Dflyway.password=$${POSTGRES_PASSWORD} \
		-pl auth-service
	mvn --file backend/pom.xml flyway:migrate \
		-Dflyway.url=jdbc:postgresql://$${POSTGRES_HOST:-localhost}:$${POSTGRES_PORT:-5432}/bbss_tenant \
		-Dflyway.user=$${POSTGRES_USER} \
		-Dflyway.password=$${POSTGRES_PASSWORD} \
		-pl tenant-service
	mvn --file backend/pom.xml flyway:migrate \
		-Dflyway.url=jdbc:postgresql://$${POSTGRES_HOST:-localhost}:$${POSTGRES_PORT:-5432}/bbss_transaction \
		-Dflyway.user=$${POSTGRES_USER} \
		-Dflyway.password=$${POSTGRES_PASSWORD} \
		-pl transaction-service
	mvn --file backend/pom.xml flyway:migrate \
		-Dflyway.url=jdbc:postgresql://$${POSTGRES_HOST:-localhost}:$${POSTGRES_PORT:-5432}/bbss_audit \
		-Dflyway.user=$${POSTGRES_USER} \
		-Dflyway.password=$${POSTGRES_PASSWORD} \
		-pl audit-service
	mvn --file backend/pom.xml flyway:migrate \
		-Dflyway.url=jdbc:postgresql://$${POSTGRES_HOST:-localhost}:$${POSTGRES_PORT:-5432}/bbss_fraud \
		-Dflyway.user=$${POSTGRES_USER} \
		-Dflyway.password=$${POSTGRES_PASSWORD} \
		-pl fraud-service
	@printf "$(GREEN)All migrations complete.$(RESET)\n"

# -----------------------------------------------------------------------------
# Frontend
# -----------------------------------------------------------------------------
lint-frontend: ## Run ESLint on the Next.js dashboard
	cd frontend/dashboard && npm run lint

build-frontend: ## Build the Next.js dashboard for production
	cd frontend/dashboard && npm run build
	@printf "$(GREEN)Frontend build complete.$(RESET)\n"
