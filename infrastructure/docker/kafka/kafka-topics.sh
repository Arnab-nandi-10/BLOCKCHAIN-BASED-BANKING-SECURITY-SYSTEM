#!/usr/bin/env bash
# =============================================================================
# Blockchain Banking Security System — Kafka Topic Bootstrap Script
# =============================================================================
# Creates all application topics with 3 partitions and replication-factor 1.
# Intended to be run inside the Kafka container or from a host that has the
# Kafka CLI tools on PATH and can reach the broker.
#
# Usage:
#   ./kafka-topics.sh [bootstrap-server]
#
# Arguments:
#   bootstrap-server  Kafka broker address (default: kafka:9092)
# =============================================================================

set -euo pipefail

BOOTSTRAP_SERVER="${1:-kafka:9092}"
REPLICATION_FACTOR=1
PARTITIONS=3

# Colour helpers
GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
RESET='\033[0m'

info()    { printf "${CYAN}[INFO]${RESET}  %s\n" "$*"; }
success() { printf "${GREEN}[OK]${RESET}    %s\n" "$*"; }
error()   { printf "${RED}[ERROR]${RESET} %s\n" "$*" >&2; }

# ---------------------------------------------------------------------------
# Wait for the broker to become reachable
# ---------------------------------------------------------------------------
info "Waiting for Kafka broker at ${BOOTSTRAP_SERVER} ..."

MAX_RETRIES=30
RETRY_INTERVAL=5
attempt=0

until kafka-broker-api-versions \
        --bootstrap-server "${BOOTSTRAP_SERVER}" \
        > /dev/null 2>&1; do
    attempt=$(( attempt + 1 ))
    if [[ ${attempt} -ge ${MAX_RETRIES} ]]; then
        error "Kafka broker at ${BOOTSTRAP_SERVER} did not become available after ${MAX_RETRIES} attempts. Aborting."
        exit 1
    fi
    info "Broker not ready (attempt ${attempt}/${MAX_RETRIES}). Retrying in ${RETRY_INTERVAL}s ..."
    sleep "${RETRY_INTERVAL}"
done

success "Kafka broker is reachable."

# ---------------------------------------------------------------------------
# Topic definitions
# Each entry: "topic-name:partitions:replication-factor:description"
# ---------------------------------------------------------------------------
declare -a TOPICS=(
    "tx.submitted:${PARTITIONS}:${REPLICATION_FACTOR}:Raw transaction events submitted by clients"
    "tx.verified:${PARTITIONS}:${REPLICATION_FACTOR}:Transactions that have passed fraud & compliance checks"
    "tx.blocked:${PARTITIONS}:${REPLICATION_FACTOR}:Transactions blocked by the fraud-detection service"
    "fraud.alert:${PARTITIONS}:${REPLICATION_FACTOR}:High-severity fraud alerts raised by the ML pipeline"
    "audit.entry:${PARTITIONS}:${REPLICATION_FACTOR}:Immutable audit log entries for on-chain anchoring"
    "tenant.provisioned:${PARTITIONS}:${REPLICATION_FACTOR}:Lifecycle events for tenant onboarding & offboarding"
    "block.committed:${PARTITIONS}:${REPLICATION_FACTOR}:Notification of Hyperledger Fabric block commit events"
)

# ---------------------------------------------------------------------------
# Create topics
# ---------------------------------------------------------------------------
info "Creating Kafka topics on broker: ${BOOTSTRAP_SERVER}"
printf "\n"

CREATED=0
SKIPPED=0
FAILED=0

for entry in "${TOPICS[@]}"; do
    IFS=':' read -r topic parts repl desc <<< "${entry}"

    # Check if topic already exists
    if kafka-topics \
            --bootstrap-server "${BOOTSTRAP_SERVER}" \
            --describe \
            --topic "${topic}" \
            > /dev/null 2>&1; then
        info "Topic '${topic}' already exists — skipping."
        (( SKIPPED += 1 ))
        continue
    fi

    # Create the topic
    if kafka-topics \
            --bootstrap-server "${BOOTSTRAP_SERVER}" \
            --create \
            --topic "${topic}" \
            --partitions "${parts}" \
            --replication-factor "${repl}" \
            --config "retention.ms=604800000" \
            --config "min.insync.replicas=1" \
            > /dev/null 2>&1; then
        success "Created topic '${topic}' (partitions=${parts}, replication-factor=${repl})"
        success "  Description: ${desc}"
        (( CREATED += 1 ))
    else
        error "Failed to create topic '${topic}'."
        (( FAILED += 1 ))
    fi
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
printf "\n"
info "Topic bootstrap summary:"
info "  Created : ${CREATED}"
info "  Skipped : ${SKIPPED}"
info "  Failed  : ${FAILED}"
printf "\n"

if [[ ${FAILED} -gt 0 ]]; then
    error "One or more topics could not be created. Review the output above."
    exit 1
fi

# ---------------------------------------------------------------------------
# List all topics for verification
# ---------------------------------------------------------------------------
info "Current topics on broker ${BOOTSTRAP_SERVER}:"
kafka-topics \
    --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --list

success "Kafka topic bootstrap complete."
