#!/bin/bash
# =============================================================================
# create-channel.sh — Create the bbss-channel and join all four peers
#
# Steps:
#   1. Create the application channel using peer0.org1 admin identity
#   2. Join peer0.org1 to the channel
#   3. Join peer1.org1 to the channel
#   4. Join peer0.org2 to the channel
#   5. Join peer1.org2 to the channel
#   6. Update anchor peer for Org1
#   7. Update anchor peer for Org2
#
# Prerequisites:
#   - network-up.sh must have run successfully (crypto material + artifacts ready)
#   - All Docker containers must be healthy
#
# Usage:
#   cd network && ./scripts/create-channel.sh
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Directory resolution
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_DIR="$(dirname "${SCRIPT_DIR}")"
ORG_DIR="${NETWORK_DIR}/organizations/peerOrganizations"
ARTIFACTS_DIR="${NETWORK_DIR}/channel-artifacts"

CHANNEL_ID="bbss-channel"
ORDERER_CA="${NETWORK_DIR}/organizations/ordererOrganizations/orderer.bbss.com/orderers/orderer.bbss.com/msp/tlscacerts/tlsca.orderer.bbss.com-cert.pem"
ORDERER_ADDRESS="localhost:7050"

# ---------------------------------------------------------------------------
# Retry configuration — peer commands occasionally fail on first attempt
# during early network startup
# ---------------------------------------------------------------------------
MAX_RETRY=5
DELAY=3   # seconds between retries

# ---------------------------------------------------------------------------
# Logging helpers
# ---------------------------------------------------------------------------
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${GREEN}[INFO ]${NC}  $*"; }
log_warn()    { echo -e "${YELLOW}[WARN ]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC}  $*" >&2; }
log_section() { echo -e "\n${CYAN}=== $* ===${NC}\n"; }

# ---------------------------------------------------------------------------
# retry <description> <command ...>
# Retries a command up to MAX_RETRY times with DELAY seconds between attempts.
# ---------------------------------------------------------------------------
retry() {
    local desc="$1"; shift
    local attempt=1
    while true; do
        log_info "  ${desc} (attempt ${attempt}/${MAX_RETRY})..."
        if "$@"; then
            return 0
        fi
        if [ "${attempt}" -ge "${MAX_RETRY}" ]; then
            log_error "  Failed after ${MAX_RETRY} attempts: ${desc}"
            return 1
        fi
        log_warn "  Retrying in ${DELAY}s..."
        sleep "${DELAY}"
        attempt=$((attempt + 1))
    done
}

# ---------------------------------------------------------------------------
# Environment helpers — set CORE_PEER_* for a given peer
# ---------------------------------------------------------------------------
set_org1_peer0_env() {
    export CORE_PEER_LOCALMSPID="Org1MSP"
    export CORE_PEER_TLS_ENABLED=true
    export CORE_PEER_TLS_ROOTCERT_FILE="${ORG_DIR}/org1.bbss.com/peers/peer0.org1.bbss.com/tls/ca.crt"
    export CORE_PEER_MSPCONFIGPATH="${ORG_DIR}/org1.bbss.com/users/Admin@org1.bbss.com/msp"
    export CORE_PEER_ADDRESS="localhost:7051"
}

set_org1_peer1_env() {
    export CORE_PEER_LOCALMSPID="Org1MSP"
    export CORE_PEER_TLS_ENABLED=true
    export CORE_PEER_TLS_ROOTCERT_FILE="${ORG_DIR}/org1.bbss.com/peers/peer1.org1.bbss.com/tls/ca.crt"
    export CORE_PEER_MSPCONFIGPATH="${ORG_DIR}/org1.bbss.com/users/Admin@org1.bbss.com/msp"
    export CORE_PEER_ADDRESS="localhost:8051"
}

set_org2_peer0_env() {
    export CORE_PEER_LOCALMSPID="Org2MSP"
    export CORE_PEER_TLS_ENABLED=true
    export CORE_PEER_TLS_ROOTCERT_FILE="${ORG_DIR}/org2.bbss.com/peers/peer0.org2.bbss.com/tls/ca.crt"
    export CORE_PEER_MSPCONFIGPATH="${ORG_DIR}/org2.bbss.com/users/Admin@org2.bbss.com/msp"
    export CORE_PEER_ADDRESS="localhost:9051"
}

set_org2_peer1_env() {
    export CORE_PEER_LOCALMSPID="Org2MSP"
    export CORE_PEER_TLS_ENABLED=true
    export CORE_PEER_TLS_ROOTCERT_FILE="${ORG_DIR}/org2.bbss.com/peers/peer1.org2.bbss.com/tls/ca.crt"
    export CORE_PEER_MSPCONFIGPATH="${ORG_DIR}/org2.bbss.com/users/Admin@org2.bbss.com/msp"
    export CORE_PEER_ADDRESS="localhost:10051"
}

# ---------------------------------------------------------------------------
# Step 1 — Create the channel
# ---------------------------------------------------------------------------
create_channel() {
    log_section "Creating channel '${CHANNEL_ID}'"

    set_org1_peer0_env

    retry "peer channel create" \
        peer channel create \
            -o "${ORDERER_ADDRESS}" \
            -c "${CHANNEL_ID}" \
            -f "${ARTIFACTS_DIR}/${CHANNEL_ID}.tx" \
            --outputBlock "${ARTIFACTS_DIR}/${CHANNEL_ID}.block" \
            --tls \
            --cafile "${ORDERER_CA}"

    log_info "Channel '${CHANNEL_ID}' created."
}

# ---------------------------------------------------------------------------
# Step 2–5 — Join peers to the channel
# ---------------------------------------------------------------------------
join_peer() {
    local peer_label="$1"
    log_info "Joining ${peer_label} to channel '${CHANNEL_ID}'..."

    retry "peer channel join (${peer_label})" \
        peer channel join \
            -b "${ARTIFACTS_DIR}/${CHANNEL_ID}.block"

    log_info "${peer_label} joined channel '${CHANNEL_ID}'."
}

join_all_peers() {
    log_section "Joining all peers to channel '${CHANNEL_ID}'"

    set_org1_peer0_env
    join_peer "peer0.org1"

    set_org1_peer1_env
    join_peer "peer1.org1"

    set_org2_peer0_env
    join_peer "peer0.org2"

    set_org2_peer1_env
    join_peer "peer1.org2"
}

# ---------------------------------------------------------------------------
# Steps 6–7 — Update anchor peers
# ---------------------------------------------------------------------------
update_anchor_peers() {
    log_section "Updating anchor peers"

    # Org1 anchor peer update
    set_org1_peer0_env
    log_info "Updating anchor peer for Org1MSP..."
    retry "anchor peer update Org1" \
        peer channel update \
            -o "${ORDERER_ADDRESS}" \
            -c "${CHANNEL_ID}" \
            -f "${ARTIFACTS_DIR}/Org1MSPanchors.tx" \
            --tls \
            --cafile "${ORDERER_CA}"
    log_info "Org1MSP anchor peer updated: peer0.org1.bbss.com:7051"

    # Org2 anchor peer update
    set_org2_peer0_env
    log_info "Updating anchor peer for Org2MSP..."
    retry "anchor peer update Org2" \
        peer channel update \
            -o "${ORDERER_ADDRESS}" \
            -c "${CHANNEL_ID}" \
            -f "${ARTIFACTS_DIR}/Org2MSPanchors.tx" \
            --tls \
            --cafile "${ORDERER_CA}"
    log_info "Org2MSP anchor peer updated: peer0.org2.bbss.com:9051"
}

# ---------------------------------------------------------------------------
# Verify channel membership
# ---------------------------------------------------------------------------
verify_channel() {
    log_section "Verifying channel membership"

    set_org1_peer0_env
    local channels
    channels=$(peer channel list 2>/dev/null || true)
    log_info "Channels on peer0.org1: ${channels}"

    set_org2_peer0_env
    channels=$(peer channel list 2>/dev/null || true)
    log_info "Channels on peer0.org2: ${channels}"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log_section "Civic Savings — Channel Setup"

    create_channel
    join_all_peers
    update_anchor_peers
    verify_channel

    log_section "Channel setup complete"
    log_info "Channel '${CHANNEL_ID}' is ready."
    log_info "Next step: ./scripts/deploy-chaincode.sh"
}

main "$@"
