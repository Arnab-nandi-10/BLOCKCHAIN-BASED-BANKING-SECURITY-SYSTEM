#!/bin/bash
# =============================================================================
# deploy-chaincode.sh — Package, install, approve, and commit both chaincodes
#
# Chaincodes deployed:
#   1. transaction-cc  (version 1.0, sequence 1)
#   2. audit-cc        (version 1.0, sequence 1)
#
# Fabric 2.x lifecycle workflow per chaincode:
#   a) Package  — create a .tar.gz package from the chaincode source
#   b) Install  — install the package on every peer (all 4 peers)
#   c) Approve  — each org approves for the channel (Org1, then Org2)
#   d) Readiness— query commit readiness to confirm both orgs approved
#   e) Commit   — commit the chaincode definition to the channel
#   f) Verify   — query committed chaincode to confirm success
#
# Prerequisites:
#   - create-channel.sh must have run successfully
#   - GOPATH / Go toolchain must be available for source builds
#   - Peer binary must be in PATH
#
# Usage:
#   cd network && ./scripts/deploy-chaincode.sh
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Directory resolution
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_DIR="$(dirname "${SCRIPT_DIR}")"
CHAINCODE_DIR="$(dirname "${NETWORK_DIR}")/chaincode"   # ../chaincode relative to network/
ORG_DIR="${NETWORK_DIR}/organizations/peerOrganizations"
PACKAGES_DIR="${NETWORK_DIR}/chaincode-packages"

CHANNEL_ID="bbss-channel"
ORDERER_ADDRESS="localhost:7050"
ORDERER_CA="${NETWORK_DIR}/organizations/ordererOrganizations/orderer.bbss.com/orderers/orderer.bbss.com/msp/tlscacerts/tlsca.orderer.bbss.com-cert.pem"

# Chaincode policy: both orgs must endorse
CC_POLICY="AND('Org1MSP.peer','Org2MSP.peer')"

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
# Retry helper
# ---------------------------------------------------------------------------
MAX_RETRY=5
DELAY=3

retry() {
    local desc="$1"; shift
    local attempt=1
    while true; do
        log_info "  ${desc} (attempt ${attempt}/${MAX_RETRY})..."
        if "$@" 2>&1; then
            return 0
        fi
        if [ "${attempt}" -ge "${MAX_RETRY}" ]; then
            log_error "  FAILED after ${MAX_RETRY} attempts: ${desc}"
            return 1
        fi
        log_warn "  Retrying in ${DELAY}s..."
        sleep "${DELAY}"
        attempt=$((attempt + 1))
    done
}

# ---------------------------------------------------------------------------
# Peer environment setters
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
# deploy_chaincode <name> <version> <sequence> <source_path>
#
# Executes the full Fabric 2.x lifecycle for one chaincode.
# ---------------------------------------------------------------------------
deploy_chaincode() {
    local CC_NAME="$1"
    local CC_VERSION="$2"
    local CC_SEQUENCE="$3"
    local CC_SRC_PATH="$4"
    local PACKAGE_FILE="${PACKAGES_DIR}/${CC_NAME}.tar.gz"

    log_section "Deploying chaincode: ${CC_NAME} v${CC_VERSION} (sequence ${CC_SEQUENCE})"

    # ------------------------------------------------------------------
    # (a) Package
    # ------------------------------------------------------------------
    log_info "Step 1/6 — Packaging ${CC_NAME}..."
    mkdir -p "${PACKAGES_DIR}"

    set_org1_peer0_env   # env needed for FABRIC_CFG_PATH discovery
    peer lifecycle chaincode package "${PACKAGE_FILE}" \
        --path "${CC_SRC_PATH}" \
        --lang golang \
        --label "${CC_NAME}_${CC_VERSION}"

    log_info "Package created: ${PACKAGE_FILE}"

    # ------------------------------------------------------------------
    # (b) Install on all four peers
    # ------------------------------------------------------------------
    log_info "Step 2/6 — Installing ${CC_NAME} on all peers..."

    set_org1_peer0_env
    retry "install on peer0.org1" \
        peer lifecycle chaincode install "${PACKAGE_FILE}"

    set_org1_peer1_env
    retry "install on peer1.org1" \
        peer lifecycle chaincode install "${PACKAGE_FILE}"

    set_org2_peer0_env
    retry "install on peer0.org2" \
        peer lifecycle chaincode install "${PACKAGE_FILE}"

    set_org2_peer1_env
    retry "install on peer1.org2" \
        peer lifecycle chaincode install "${PACKAGE_FILE}"

    log_info "${CC_NAME} installed on all peers."

    # ------------------------------------------------------------------
    # Query the package ID (needed for approve commands)
    # ------------------------------------------------------------------
    set_org1_peer0_env
    local PACKAGE_ID
    PACKAGE_ID=$(peer lifecycle chaincode queryinstalled 2>/dev/null \
        | grep "${CC_NAME}_${CC_VERSION}" \
        | awk '{print $3}' \
        | sed 's/,$//')

    if [ -z "${PACKAGE_ID}" ]; then
        log_error "Could not determine package ID for ${CC_NAME}_${CC_VERSION}."
        return 1
    fi
    log_info "Package ID: ${PACKAGE_ID}"

    # ------------------------------------------------------------------
    # (c) Approve for Org1
    # ------------------------------------------------------------------
    log_info "Step 3/6 — Org1 approving ${CC_NAME}..."
    set_org1_peer0_env

    retry "approve for Org1" \
        peer lifecycle chaincode approveformyorg \
            -o "${ORDERER_ADDRESS}" \
            --ordererTLSHostnameOverride orderer.bbss.com \
            --channelID "${CHANNEL_ID}" \
            --name "${CC_NAME}" \
            --version "${CC_VERSION}" \
            --package-id "${PACKAGE_ID}" \
            --sequence "${CC_SEQUENCE}" \
            --signature-policy "${CC_POLICY}" \
            --tls \
            --cafile "${ORDERER_CA}"

    log_info "Org1 approved ${CC_NAME}."

    # ------------------------------------------------------------------
    # (c) Approve for Org2
    # ------------------------------------------------------------------
    log_info "Step 4/6 — Org2 approving ${CC_NAME}..."
    set_org2_peer0_env

    retry "approve for Org2" \
        peer lifecycle chaincode approveformyorg \
            -o "${ORDERER_ADDRESS}" \
            --ordererTLSHostnameOverride orderer.bbss.com \
            --channelID "${CHANNEL_ID}" \
            --name "${CC_NAME}" \
            --version "${CC_VERSION}" \
            --package-id "${PACKAGE_ID}" \
            --sequence "${CC_SEQUENCE}" \
            --signature-policy "${CC_POLICY}" \
            --tls \
            --cafile "${ORDERER_CA}"

    log_info "Org2 approved ${CC_NAME}."

    # ------------------------------------------------------------------
    # (d) Check commit readiness (both orgs must show true)
    # ------------------------------------------------------------------
    log_info "Step 5/6 — Checking commit readiness for ${CC_NAME}..."
    set_org1_peer0_env

    peer lifecycle chaincode checkcommitreadiness \
        --channelID "${CHANNEL_ID}" \
        --name "${CC_NAME}" \
        --version "${CC_VERSION}" \
        --sequence "${CC_SEQUENCE}" \
        --signature-policy "${CC_POLICY}" \
        --tls \
        --cafile "${ORDERER_CA}" \
        --output json

    # ------------------------------------------------------------------
    # (e) Commit
    # ------------------------------------------------------------------
    log_info "Step 6/6 — Committing ${CC_NAME} definition to channel..."
    set_org1_peer0_env

    retry "commit ${CC_NAME}" \
        peer lifecycle chaincode commit \
            -o "${ORDERER_ADDRESS}" \
            --ordererTLSHostnameOverride orderer.bbss.com \
            --channelID "${CHANNEL_ID}" \
            --name "${CC_NAME}" \
            --version "${CC_VERSION}" \
            --sequence "${CC_SEQUENCE}" \
            --signature-policy "${CC_POLICY}" \
            --tls \
            --cafile "${ORDERER_CA}" \
            --peerAddresses "localhost:7051" \
            --tlsRootCertFiles "${ORG_DIR}/org1.bbss.com/peers/peer0.org1.bbss.com/tls/ca.crt" \
            --peerAddresses "localhost:9051" \
            --tlsRootCertFiles "${ORG_DIR}/org2.bbss.com/peers/peer0.org2.bbss.com/tls/ca.crt"

    log_info "${CC_NAME} committed to channel '${CHANNEL_ID}'."

    # ------------------------------------------------------------------
    # (f) Verify
    # ------------------------------------------------------------------
    log_info "Verifying committed chaincode..."
    peer lifecycle chaincode querycommitted \
        --channelID "${CHANNEL_ID}" \
        --name "${CC_NAME}" \
        --tls \
        --cafile "${ORDERER_CA}"

    log_info "${CC_NAME} v${CC_VERSION} deployment complete."
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log_section "BBSS — Chaincode Deployment"

    # Deploy transaction-cc
    deploy_chaincode \
        "transaction-cc" \
        "1.0" \
        "1" \
        "${CHAINCODE_DIR}/transaction-cc"

    # Deploy audit-cc
    deploy_chaincode \
        "audit-cc" \
        "1.0" \
        "1" \
        "${CHAINCODE_DIR}/audit-cc"

    log_section "All chaincodes deployed successfully"
    log_info "transaction-cc v1.0 is live on channel '${CHANNEL_ID}'"
    log_info "audit-cc       v1.0 is live on channel '${CHANNEL_ID}'"
    echo ""
    log_info "Example invocations:"
    log_info ""
    log_info "  # Create a transaction"
    log_info "  peer chaincode invoke -C ${CHANNEL_ID} -n transaction-cc \\"
    log_info "    -c '{\"Args\":[\"CreateTransaction\",\"TX001\",\"TENANT1\",\"ACC001\",\"ACC002\",\"1000.00\",\"USD\",\"TRANSFER\",\"PENDING\"]}'"
    log_info ""
    log_info "  # Create an audit entry"
    log_info "  peer chaincode invoke -C ${CHANNEL_ID} -n audit-cc \\"
    log_info "    -c '{\"Args\":[\"CreateAuditEntry\",\"AUDIT001\",\"TENANT1\",\"TRANSACTION\",\"TX001\",\"CREATE\",\"USER1\",\"HUMAN\",\"10.0.0.1\",\"{}\"]}'"
}

main "$@"
