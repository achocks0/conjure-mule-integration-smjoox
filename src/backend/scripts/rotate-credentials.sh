#!/bin/bash
#
# rotate-credentials.sh - Credential rotation script for Payment API Security Enhancement
#
# This script provides a command-line interface for secure credential rotation,
# allowing operations teams to rotate client credentials without service disruption.
# It implements a zero-downtime rotation process that maintains backward compatibility
# by supporting a transition period where both old and new credentials are valid.

# Exit on error
set -e

# Global variables
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PYTHON_SCRIPT_DIR="${SCRIPT_DIR}/../../scripts/conjur"
CONFIG_FILE="${SCRIPT_DIR}/../config/conjur.yml"
API_URL="http://localhost:8080/api/v1/rotations"
LOG_FILE="/var/log/payment/rotation.log"

# Display usage information
usage() {
    echo "Usage: $(basename "$0") COMMAND [OPTIONS]"
    echo
    echo "Credential rotation script for Payment API Security Enhancement"
    echo
    echo "Commands:"
    echo "  initiate CLIENT_ID    Initiate credential rotation for the specified client"
    echo "  monitor ROTATION_ID   Monitor the status of a rotation in progress"
    echo "  complete ROTATION_ID  Complete the rotation process"
    echo "  execute CLIENT_ID     Execute the complete rotation workflow"
    echo "  status CLIENT_ID      Get the current rotation status for a client"
    echo
    echo "Options:"
    echo "  --reason TEXT               Reason for rotation (required for initiate/execute)"
    echo "  --transition-period MINUTES Duration of transition period in minutes (default: 1440)"
    echo "  --force                     Force rotation even if one is already in progress"
    echo "  --wait                      Wait for rotation to complete (for execute command)"
    echo "  --api                       Use REST API instead of direct script execution"
    echo "  --script                    Use direct script execution (default)"
    echo "  --config FILE               Path to configuration file"
    echo "  --help                      Display this help message"
    echo
    echo "Examples:"
    echo "  $(basename "$0") initiate client-123 --reason \"Regular rotation\" --transition-period 60"
    echo "  $(basename "$0") monitor rotation-abc-123"
    echo "  $(basename "$0") complete rotation-abc-123"
    echo "  $(basename "$0") execute client-123 --reason \"Regular rotation\" --wait"
    echo "  $(basename "$0") status client-123"
}

# Log messages to both stdout and the log file
log() {
    local timestamp
    timestamp=$(date "+%Y-%m-%d %H:%M:%S")
    echo "[$timestamp] $1"
    
    # Ensure log directory exists
    mkdir -p "$(dirname "$LOG_FILE")"
    echo "[$timestamp] $1" >> "$LOG_FILE"
}

# Check if required dependencies are installed
check_dependencies() {
    local missing=0

    # Check for curl
    if ! command -v curl &> /dev/null; then
        log "ERROR: curl is not installed"
        missing=1
    fi

    # Check for jq
    if ! command -v jq &> /dev/null; then
        log "ERROR: jq is not installed"
        missing=1
    fi

    # Check for python3
    if ! command -v python3 &> /dev/null; then
        log "ERROR: python3 is not installed"
        missing=1
    fi

    # Check for required Python modules
    if ! python3 -c "import requests, json" &> /dev/null; then
        log "ERROR: Required Python modules (requests, json) are not installed"
        missing=1
    fi

    return $missing
}

# Initiate credential rotation using the REST API
initiate_rotation_api() {
    local client_id="$1"
    local reason="$2"
    local transition_period_minutes="$3"
    local force_rotation="$4"

    log "Initiating credential rotation for client_id: $client_id via API"

    # Convert transition period from minutes to seconds
    local transition_period_seconds=$((transition_period_minutes * 60))

    # Construct JSON payload
    local payload
    payload=$(cat <<EOF
{
    "client_id": "$client_id",
    "reason": "$reason",
    "transition_period_seconds": $transition_period_seconds,
    "force": $force_rotation
}
EOF
    )

    # Make API request
    local response
    response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        "${API_URL}/initiate")

    log "Rotation initiated. Response: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Initiate credential rotation using the Python script
initiate_rotation_script() {
    local client_id="$1"
    local reason="$2"
    local transition_period_minutes="$3"
    local force_rotation="$4"

    log "Initiating credential rotation for client_id: $client_id via Python script"

    # Convert transition period from minutes to seconds
    local transition_period_seconds=$((transition_period_minutes * 60))

    # Execute Python script
    pushd "$PYTHON_SCRIPT_DIR" > /dev/null || exit 1
    local response
    response=$(python3 -c "
from rotate_credentials import initiate_rotation
from config import create_conjur_config, get_rotation_config
import json

config_file = '$CONFIG_FILE'
conjur_config = create_conjur_config(config_file)
rotation_config = get_rotation_config(config_file)
rotation_config.transition_period_seconds = $transition_period_seconds

result = initiate_rotation('$client_id', '$reason', conjur_config, rotation_config, $force_rotation)
print(json.dumps(result.to_dict()))
")
    popd > /dev/null || exit 1

    log "Rotation initiated. Response: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Monitor credential rotation status using the REST API
monitor_rotation_api() {
    local rotation_id="$1"

    log "Monitoring rotation status for rotation_id: $rotation_id via API"

    # Make API request
    local response
    response=$(curl -s -X GET "${API_URL}/${rotation_id}")

    log "Rotation status: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Monitor credential rotation status using the Python script
monitor_rotation_script() {
    local client_id="$1"

    log "Monitoring rotation status for client_id: $client_id via Python script"

    # Execute Python script
    pushd "$PYTHON_SCRIPT_DIR" > /dev/null || exit 1
    local response
    response=$(python3 -c "
from rotate_credentials import monitor_rotation
from config import create_conjur_config, get_rotation_config
import json

config_file = '$CONFIG_FILE'
conjur_config = create_conjur_config(config_file)
rotation_config = get_rotation_config(config_file)

result = monitor_rotation('$client_id', conjur_config, rotation_config)
print(json.dumps(result))
")
    popd > /dev/null || exit 1

    log "Rotation status: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Complete credential rotation using the REST API
complete_rotation_api() {
    local rotation_id="$1"

    log "Completing rotation for rotation_id: $rotation_id via API"

    # Make API request
    local response
    response=$(curl -s -X PUT "${API_URL}/${rotation_id}/complete")

    log "Rotation completed. Response: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Complete credential rotation using the Python script
complete_rotation_script() {
    local client_id="$1"

    log "Completing rotation for client_id: $client_id via Python script"

    # Execute Python script
    pushd "$PYTHON_SCRIPT_DIR" > /dev/null || exit 1
    local response
    response=$(python3 -c "
from rotate_credentials import complete_rotation
from config import create_conjur_config
import json

config_file = '$CONFIG_FILE'
conjur_config = create_conjur_config(config_file)

result = complete_rotation('$client_id', conjur_config)
print(json.dumps(result.to_dict()))
")
    popd > /dev/null || exit 1

    log "Rotation completed. Response: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Execute the complete rotation workflow using the REST API
execute_rotation_api() {
    local client_id="$1"
    local reason="$2"
    local transition_period_minutes="$3"
    local force_rotation="$4"
    local wait_for_completion="$5"

    log "Executing complete rotation workflow for client_id: $client_id via API"

    # Initiate rotation
    local initiate_response
    initiate_response=$(initiate_rotation_api "$client_id" "$reason" "$transition_period_minutes" "$force_rotation")

    # Extract rotation_id from response
    local rotation_id
    rotation_id=$(echo "$initiate_response" | jq -r '.rotation_id // empty')
    
    if [ -z "$rotation_id" ]; then
        log "ERROR: Failed to extract rotation_id from response: $initiate_response"
        echo "$initiate_response"
        return 1
    fi

    log "Rotation initiated with rotation_id: $rotation_id"

    # If not waiting for completion, return the initiation response
    if [ "$wait_for_completion" != "true" ]; then
        echo "$initiate_response"
        return 0
    fi

    log "Waiting for rotation to complete..."

    # Monitor rotation until it reaches appropriate state
    local status="INITIATED"
    while [ "$status" != "OLD_DEPRECATED" ] && [ "$status" != "NEW_ACTIVE" ] && [ "$status" != "FAILED" ]; do
        sleep 10
        local monitor_response
        monitor_response=$(monitor_rotation_api "$rotation_id")
        status=$(echo "$monitor_response" | jq -r '.state // empty')
        log "Current rotation state: $status"
    done

    # If rotation has failed, return the monitor response
    if [ "$status" = "FAILED" ]; then
        log "ERROR: Rotation failed with state: $status"
        echo "$monitor_response"
        return 1
    fi

    # If rotation has reached OLD_DEPRECATED state, complete it
    if [ "$status" = "OLD_DEPRECATED" ]; then
        log "Rotation reached OLD_DEPRECATED state, completing rotation..."
        local complete_response
        complete_response=$(complete_rotation_api "$rotation_id")
        echo "$complete_response"
        return 0
    fi

    # If rotation already reached NEW_ACTIVE state
    echo "$monitor_response"
    return 0
}

# Execute the complete rotation workflow using the Python script
execute_rotation_script() {
    local client_id="$1"
    local reason="$2"
    local transition_period_minutes="$3"
    local force_rotation="$4"
    local wait_for_completion="$5"

    log "Executing complete rotation workflow for client_id: $client_id via Python script"

    # Convert transition period from minutes to seconds
    local transition_period_seconds=$((transition_period_minutes * 60))

    # Execute Python script
    pushd "$PYTHON_SCRIPT_DIR" > /dev/null || exit 1
    local response
    response=$(python3 -c "
from rotate_credentials import execute_rotation_workflow
from config import create_conjur_config, get_rotation_config
import json

config_file = '$CONFIG_FILE'
conjur_config = create_conjur_config(config_file)
rotation_config = get_rotation_config(config_file)
rotation_config.transition_period_seconds = $transition_period_seconds

result = execute_rotation_workflow(
    '$client_id', 
    '$reason', 
    conjur_config, 
    rotation_config, 
    $force_rotation, 
    $wait_for_completion
)
print(json.dumps(result.to_dict()))
")
    popd > /dev/null || exit 1

    log "Rotation workflow executed. Response: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Get the current status of a rotation using the REST API
get_rotation_status_api() {
    local rotation_id="$1"

    log "Getting rotation status for rotation_id: $rotation_id via API"

    # Make API request
    local response
    response=$(curl -s -X GET "${API_URL}/${rotation_id}")

    log "Rotation status: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Get the current status of a rotation using the Python script
get_rotation_status_script() {
    local client_id="$1"

    log "Getting rotation status for client_id: $client_id via Python script"

    # Execute Python script
    pushd "$PYTHON_SCRIPT_DIR" > /dev/null || exit 1
    local response
    response=$(python3 -c "
from rotate_credentials import get_rotation_status
from config import create_conjur_config
import json

config_file = '$CONFIG_FILE'
conjur_config = create_conjur_config(config_file)

status = get_rotation_status('$client_id', conjur_config)
print(json.dumps(status))
")
    popd > /dev/null || exit 1

    log "Rotation status: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Get all rotations for a client using the REST API
get_client_rotations_api() {
    local client_id="$1"

    log "Getting all rotations for client_id: $client_id via API"

    # Make API request
    local response
    response=$(curl -s -X GET "${API_URL}/client/${client_id}")

    log "Client rotations: $(echo "$response" | jq -c 2>/dev/null || echo "$response")"
    echo "$response"
}

# Main function to process arguments and execute commands
main() {
    # Default values
    local command=""
    local client_id=""
    local rotation_id=""
    local reason="Scheduled rotation"
    local transition_period_minutes=1440  # 24 hours
    local force_rotation="false"
    local wait_for_completion="false"
    local mode="script"  # Default to script mode
    local config_file="$CONFIG_FILE"

    # Parse arguments
    if [ $# -eq 0 ]; then
        usage
        exit 1
    fi

    command="$1"
    shift

    case "$command" in
        initiate|execute|status)
            if [ $# -eq 0 ]; then
                echo "ERROR: CLIENT_ID is required for $command command"
                usage
                exit 1
            fi
            client_id="$1"
            shift
            ;;
        monitor|complete)
            if [ $# -eq 0 ]; then
                echo "ERROR: ROTATION_ID is required for $command command"
                usage
                exit 1
            fi
            rotation_id="$1"
            shift
            ;;
        help|--help|-h)
            usage
            exit 0
            ;;
        *)
            echo "ERROR: Unknown command: $command"
            usage
            exit 1
            ;;
    esac

    # Parse options
    while [ $# -gt 0 ]; do
        case "$1" in
            --reason)
                shift
                reason="$1"
                ;;
            --transition-period)
                shift
                transition_period_minutes="$1"
                ;;
            --force)
                force_rotation="true"
                ;;
            --wait)
                wait_for_completion="true"
                ;;
            --api)
                mode="api"
                ;;
            --script)
                mode="script"
                ;;
            --config)
                shift
                config_file="$1"
                CONFIG_FILE="$1"
                ;;
            *)
                echo "ERROR: Unknown option: $1"
                usage
                exit 1
                ;;
        esac
        shift
    done

    # Check dependencies
    if ! check_dependencies; then
        exit 1
    fi

    # Execute command
    case "$command" in
        initiate)
            # Validate required parameters
            if [ -z "$reason" ]; then
                echo "ERROR: --reason is required for initiate command"
                exit 1
            fi

            # Execute based on mode
            if [ "$mode" = "api" ]; then
                initiate_rotation_api "$client_id" "$reason" "$transition_period_minutes" "$force_rotation"
            else
                initiate_rotation_script "$client_id" "$reason" "$transition_period_minutes" "$force_rotation"
            fi
            ;;
        monitor)
            # Execute based on mode
            if [ "$mode" = "api" ]; then
                monitor_rotation_api "$rotation_id"
            else
                # For script mode, we need client_id
                if [ -z "$client_id" ]; then
                    # Try to extract client_id from rotation status
                    local status_response
                    status_response=$(get_rotation_status_api "$rotation_id")
                    client_id=$(echo "$status_response" | jq -r '.client_id // empty')
                    
                    if [ -z "$client_id" ]; then
                        echo "ERROR: Could not determine client_id for rotation_id: $rotation_id"
                        exit 1
                    fi
                fi
                
                monitor_rotation_script "$client_id"
            fi
            ;;
        complete)
            # Execute based on mode
            if [ "$mode" = "api" ]; then
                complete_rotation_api "$rotation_id"
            else
                # For script mode, we need client_id
                if [ -z "$client_id" ]; then
                    # Try to extract client_id from rotation status
                    local status_response
                    status_response=$(get_rotation_status_api "$rotation_id")
                    client_id=$(echo "$status_response" | jq -r '.client_id // empty')
                    
                    if [ -z "$client_id" ]; then
                        echo "ERROR: Could not determine client_id for rotation_id: $rotation_id"
                        exit 1
                    fi
                fi
                
                complete_rotation_script "$client_id"
            fi
            ;;
        execute)
            # Validate required parameters
            if [ -z "$reason" ]; then
                echo "ERROR: --reason is required for execute command"
                exit 1
            fi

            # Execute based on mode
            if [ "$mode" = "api" ]; then
                execute_rotation_api "$client_id" "$reason" "$transition_period_minutes" "$force_rotation" "$wait_for_completion"
            else
                execute_rotation_script "$client_id" "$reason" "$transition_period_minutes" "$force_rotation" "$wait_for_completion"
            fi
            ;;
        status)
            # Execute based on mode
            if [ "$mode" = "api" ]; then
                get_client_rotations_api "$client_id"
            else
                get_rotation_status_script "$client_id"
            fi
            ;;
    esac
}

# Execute main function with all script arguments
main "$@"