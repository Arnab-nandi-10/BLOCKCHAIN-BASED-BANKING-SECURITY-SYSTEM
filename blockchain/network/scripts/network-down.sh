#!/bin/bash
# =============================================================================
# network-down.sh — Stop and clean the BBSS Hyperledger Fabric network
#
# What this script does:
#   1. Stops and removes all Fabric Docker containers (with volumes)
#   2. Removes any chaincode Docker images created during operation
#   3. Removes generated crypto material (organizations/)
#   4. Removes generated channel artifacts (channel-artifacts/)
#
# Usage:
#   cd network && ./scripts/network-down.sh
#
# Flags:
#   --keep-crypto   Skip removal of organisations/ and channel-artifacts/
#                   (useful when you want to restart the network with the same
#                    identities without re-running cryptogen/configtxgen)
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Resolve directories
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_DIR="$(dirname "${SCRIPT_DIR}")"
CONFIG_DIR="${NETWORK_DIR}/config"
COMPOSE_FILE="${CONFIG_DIR}/docker-compose-fabric.yml"

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
KEEP_CRYPTO=false
for arg in "$@"; do
    case "${arg}" in
        --keep-crypto) KEEP_CRYPTO=true ;;
        *) echo "[WARN] Unknown argument: ${arg}" ;;
    esac
done

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
# Stop Docker Compose services (with volume removal to free disk space)
# ---------------------------------------------------------------------------
stop_docker_services() {
    log_section "Stopping Fabric Docker services"

    if [ -f "${COMPOSE_FILE}" ]; then
        docker-compose -f "${COMPOSE_FILE}" down --volumes --remove-orphans 2>/dev/null || true
        log_info "Docker Compose services stopped and volumes removed."
    else
        log_warn "Compose file not found at ${COMPOSE_FILE}; skipping docker-compose down."
    fi
}

# ---------------------------------------------------------------------------
# Remove stale chaincode Docker images (dev-peer* images created by Fabric)
# ---------------------------------------------------------------------------
remove_chaincode_images() {
    log_section "Removing chaincode Docker images"

    local cc_images
    cc_images=$(docker images --filter "reference=dev-peer*" --quiet 2>/dev/null || true)

    if [ -n "${cc_images}" ]; then
        # shellcheck disable=SC2086
        docker rmi --force ${cc_images} 2>/dev/null || true
        log_info "Removed chaincode Docker images."
    else
        log_info "No chaincode Docker images found."
    fi

    # Also clean up any fabric-tools containers that may have been left running
    local tool_containers
    tool_containers=$(docker ps -a --filter "name=cli" --quiet 2>/dev/null || true)
    if [ -n "${tool_containers}" ]; then
        # shellcheck disable=SC2086
        docker rm --force ${tool_containers} 2>/dev/null || true
        log_info "Removed leftover CLI containers."
    fi
}

# ---------------------------------------------------------------------------
# Remove generated crypto material and channel artifacts
# ---------------------------------------------------------------------------
remove_generated_artifacts() {
    if [ "${KEEP_CRYPTO}" = true ]; then
        log_warn "--keep-crypto flag set; skipping removal of crypto material and channel artifacts."
        return
    fi

    log_section "Removing generated artifacts"

    if [ -d "${NETWORK_DIR}/organizations" ]; then
        rm -rf "${NETWORK_DIR}/organizations"
        log_info "Removed: ${NETWORK_DIR}/organizations"
    else
        log_info "organizations/ directory not found — nothing to remove."
    fi

    if [ -d "${NETWORK_DIR}/channel-artifacts" ]; then
        rm -rf "${NETWORK_DIR}/channel-artifacts"
        log_info "Removed: ${NETWORK_DIR}/channel-artifacts"
    else
        log_info "channel-artifacts/ directory not found — nothing to remove."
    fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log_section "BBSS Blockchain Banking Security System — Network Teardown"

    stop_docker_services
    remove_chaincode_images
    remove_generated_artifacts

    log_section "Network stopped and cleaned successfully"
    log_info "Run ./scripts/network-up.sh to start a fresh network."
}

main "$@"
