#!/bin/bash
# =============================================================================
# network-up.sh — Start the Civic Savings Hyperledger Fabric development network
#
# Responsibilities:
#   1. Verify all prerequisite tools are available
#   2. Remove any leftover crypto material and channel artifacts
#   3. Generate cryptographic material with cryptogen
#   4. Generate channel artifacts (genesis block, channel tx, anchor updates)
#   5. Bring up all Docker containers via docker-compose
#   6. Wait for the network to stabilise
#   7. Delegate channel creation and peer joining to create-channel.sh
#
# Usage:
#   cd network && ./scripts/network-up.sh
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Resolve directories relative to this script regardless of cwd
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_DIR="$(dirname "${SCRIPT_DIR}")"
CONFIG_DIR="${NETWORK_DIR}/config"
ORG_DIR="${NETWORK_DIR}/organizations"
ARTIFACTS_DIR="${NETWORK_DIR}/channel-artifacts"

CHANNEL_ID="bbss-channel"
SYSTEM_CHANNEL_ID="system-channel"
COMPOSE_FILE="${CONFIG_DIR}/docker-compose-fabric.yml"

# ---------------------------------------------------------------------------
# Logging helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${GREEN}[INFO ]${NC}  $*"; }
log_warn()    { echo -e "${YELLOW}[WARN ]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC}  $*" >&2; }
log_section() { echo -e "\n${CYAN}=== $* ===${NC}\n"; }

# ---------------------------------------------------------------------------
# 1. Prerequisite check
# ---------------------------------------------------------------------------
check_prerequisites() {
    log_section "Checking prerequisites"

    local missing=0
    for cmd in docker docker-compose cryptogen configtxgen peer; do
        if command -v "${cmd}" &>/dev/null; then
            log_info "  found: ${cmd} ($(command -v "${cmd}"))"
        else
            log_error "  missing: ${cmd}"
            missing=$((missing + 1))
        fi
    done

    if ! docker info &>/dev/null; then
        log_error "Docker daemon is not running. Please start Docker and retry."
        missing=$((missing + 1))
    else
        log_info "  Docker daemon is running"
    fi

    if [ "${missing}" -gt 0 ]; then
        log_error "Install the ${missing} missing prerequisite(s) and retry."
        exit 1
    fi
    log_info "All prerequisites satisfied."
}

# ---------------------------------------------------------------------------
# 2. Clean previous artifacts
# ---------------------------------------------------------------------------
clean_previous_artifacts() {
    log_section "Cleaning previous artifacts"

    if [ -d "${ORG_DIR}" ]; then
        rm -rf "${ORG_DIR}"
        log_info "Removed: ${ORG_DIR}"
    fi

    if [ -d "${ARTIFACTS_DIR}" ]; then
        rm -rf "${ARTIFACTS_DIR}"
        log_info "Removed: ${ARTIFACTS_DIR}"
    fi

    # Remove any leftover chaincode container images from a prior run
    local cc_images
    cc_images=$(docker images --filter "reference=dev-peer*" --quiet 2>/dev/null || true)
    if [ -n "${cc_images}" ]; then
        # shellcheck disable=SC2086
        docker rmi ${cc_images} 2>/dev/null || true
        log_info "Removed stale chaincode Docker images."
    fi

    log_info "Previous artifacts cleaned."
}

# ---------------------------------------------------------------------------
# 3. Generate cryptographic material
# ---------------------------------------------------------------------------
generate_crypto_material() {
    log_section "Generating cryptographic material"

    cryptogen generate \
        --config="${CONFIG_DIR}/crypto-config.yaml" \
        --output="${ORG_DIR}"

    log_info "Crypto material written to: ${ORG_DIR}"
}

# ---------------------------------------------------------------------------
# 4. Generate channel artifacts
# ---------------------------------------------------------------------------
generate_channel_artifacts() {
    log_section "Generating channel artifacts"

    mkdir -p "${ARTIFACTS_DIR}"
    export FABRIC_CFG_PATH="${CONFIG_DIR}"

    # ---- Genesis block for the system channel ----------------------------
    log_info "Generating orderer genesis block..."
    configtxgen \
        -profile TwoOrgsOrdererGenesis \
        -channelID "${SYSTEM_CHANNEL_ID}" \
        -outputBlock "${ARTIFACTS_DIR}/genesis.block"
    log_info "  -> ${ARTIFACTS_DIR}/genesis.block"

    # ---- Application channel creation transaction ------------------------
    log_info "Generating channel creation transaction for '${CHANNEL_ID}'..."
    configtxgen \
        -profile TwoOrgsChannel \
        -outputCreateChannelTx "${ARTIFACTS_DIR}/${CHANNEL_ID}.tx" \
        -channelID "${CHANNEL_ID}"
    log_info "  -> ${ARTIFACTS_DIR}/${CHANNEL_ID}.tx"

    # ---- Anchor peer updates ---------------------------------------------
    log_info "Generating anchor peer update for Org1MSP..."
    configtxgen \
        -profile TwoOrgsChannel \
        -outputAnchorPeersUpdate "${ARTIFACTS_DIR}/Org1MSPanchors.tx" \
        -channelID "${CHANNEL_ID}" \
        -asOrg Org1MSP
    log_info "  -> ${ARTIFACTS_DIR}/Org1MSPanchors.tx"

    log_info "Generating anchor peer update for Org2MSP..."
    configtxgen \
        -profile TwoOrgsChannel \
        -outputAnchorPeersUpdate "${ARTIFACTS_DIR}/Org2MSPanchors.tx" \
        -channelID "${CHANNEL_ID}" \
        -asOrg Org2MSP
    log_info "  -> ${ARTIFACTS_DIR}/Org2MSPanchors.tx"

    log_info "Channel artifacts generated."
}

# ---------------------------------------------------------------------------
# 5. Start Docker network
# ---------------------------------------------------------------------------
start_docker_network() {
    log_section "Starting Fabric Docker network"

    docker-compose -f "${COMPOSE_FILE}" up -d

    log_info "Waiting 10 seconds for the network to stabilise..."
    sleep 10

    # Quick sanity check — all containers should be running
    local failed
    failed=$(docker-compose -f "${COMPOSE_FILE}" ps --filter "status=exited" --quiet 2>/dev/null || true)
    if [ -n "${failed}" ]; then
        log_error "One or more containers exited unexpectedly. Check logs with:"
        log_error "  docker-compose -f ${COMPOSE_FILE} logs"
        exit 1
    fi

    log_info "All containers are running."
}

# ---------------------------------------------------------------------------
# 6. Create channel and join peers
# ---------------------------------------------------------------------------
create_and_join_channel() {
    log_section "Creating channel and joining peers"
    "${SCRIPT_DIR}/create-channel.sh"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log_section "Civic Savings Blockchain Banking Security System — Network Bootstrap"

    check_prerequisites
    clean_previous_artifacts
    generate_crypto_material
    generate_channel_artifacts
    start_docker_network
    create_and_join_channel

    log_section "Network started successfully"
    echo ""
    log_info "Peer endpoints:"
    log_info "  Org1  peer0 : localhost:7051"
    log_info "  Org1  peer1 : localhost:8051"
    log_info "  Org2  peer0 : localhost:9051"
    log_info "  Org2  peer1 : localhost:10051"
    log_info "  Orderer     : localhost:7050"
    echo ""
    log_info "Certificate Authority endpoints:"
    log_info "  CA Org1 : localhost:7054"
    log_info "  CA Org2 : localhost:8054"
    echo ""
    log_info "CouchDB admin UIs:"
    log_info "  couchdb0 (peer0.org1) : http://localhost:5984/_utils"
    log_info "  couchdb1 (peer1.org1) : http://localhost:6984/_utils"
    log_info "  couchdb2 (peer0.org2) : http://localhost:7984/_utils"
    log_info "  couchdb3 (peer1.org2) : http://localhost:8984/_utils"
    echo ""
    log_info "To deploy chaincodes run: ./scripts/deploy-chaincode.sh"
    log_info "To stop the network run:  ./scripts/network-down.sh"
}

main "$@"
