#!/bin/bash
#
# health-check.sh - Health check script for Payment API Security Enhancement components
#
# This script performs health checks on the Payment API Security Enhancement components,
# including Payment-EAPI, Payment-SAPI, Conjur vault, and Redis cache. It provides functions
# to verify service health, wait for services to become healthy, and report health status.
#
# Requirements:
# - bash 4.0+
# - curl 7.0+
# - jq 1.6+
# - kubectl 1.22+ (for Kubernetes deployments)
#
# Usage:
#   ./health-check.sh [options]
#
# Options:
#   -s, --service SERVICE  Specific service to check (eapi, sapi, conjur, redis)
#   -w, --wait            Wait for services to become healthy
#   -t, --timeout SECONDS Timeout in seconds when waiting for health (default: 300)
#   -e, --environment ENV Target environment (dev, test, staging, prod) (default: dev)
#   -n, --namespace NS    Kubernetes namespace (default: payment-system)
#   -i, --interval SECONDS Interval in seconds between health checks (default: 5)
#   -v, --verbose         Enable verbose logging
#   -h, --help            Show usage information

# Determine script location
SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

# Configuration variables with default values
ENVIRONMENT=${ENVIRONMENT:-dev}
NAMESPACE=${NAMESPACE:-payment-system}
EAPI_SERVICE=${EAPI_SERVICE:-payment-eapi}
SAPI_SERVICE=${SAPI_SERVICE:-payment-sapi}
CONJUR_SERVICE=${CONJUR_SERVICE:-conjur}
REDIS_SERVICE=${REDIS_SERVICE:-redis}
EAPI_PORT=${EAPI_PORT:-8080}
SAPI_PORT=${SAPI_PORT:-8081}
CONJUR_PORT=${CONJUR_PORT:-443}
REDIS_PORT=${REDIS_PORT:-6379}
HEALTH_CHECK_TIMEOUT=${HEALTH_CHECK_TIMEOUT:-300}
HEALTH_CHECK_INTERVAL=${HEALTH_CHECK_INTERVAL:-5}
VERBOSE=${VERBOSE:-false}
LOG_FILE="$PROJECT_ROOT/logs/health-check-$(date +%Y%m%d%H%M%S).log"

# Logging functions
log_info() {
    local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
    echo -e "[INFO] ${timestamp} - $1"
    echo "[INFO] ${timestamp} - $1" >> "$LOG_FILE"
}

log_error() {
    local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
    echo -e "[ERROR] ${timestamp} - $1" >&2
    echo "[ERROR] ${timestamp} - $1" >> "$LOG_FILE"
}

log_debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
        echo -e "[DEBUG] ${timestamp} - $1"
        echo "[DEBUG] ${timestamp} - $1" >> "$LOG_FILE"
    fi
}

# Function to check Payment-EAPI health
check_eapi_health() {
    log_info "Checking Payment-EAPI health..."
    
    local eapi_url
    if [[ "$ENVIRONMENT" == "local" ]]; then
        eapi_url="http://localhost:${EAPI_PORT}/actuator/health"
    else
        eapi_url="http://${EAPI_SERVICE}.${NAMESPACE}.svc.cluster.local:${EAPI_PORT}/actuator/health"
    fi
    
    log_debug "EAPI health URL: $eapi_url"
    
    local response
    local status_code
    
    # Make a request to the health endpoint
    response=$(curl -s -o /dev/null -w "%{http_code}:%{stdout}" -X GET "$eapi_url" 2>/dev/null)
    status_code=$(echo "$response" | cut -d':' -f1)
    
    if [[ "$status_code" == "200" ]]; then
        # Extract the response body
        local body=$(echo "$response" | cut -d':' -f2-)
        local status=$(echo "$body" | jq -r '.status' 2>/dev/null)
        
        if [[ "$status" == "UP" ]]; then
            log_info "Payment-EAPI is healthy"
            return 0
        else
            log_error "Payment-EAPI is not healthy. Status: $status"
            return 1
        fi
    else
        log_error "Failed to check Payment-EAPI health. Status code: $status_code"
        return 1
    fi
}

# Function to check Payment-SAPI health
check_sapi_health() {
    log_info "Checking Payment-SAPI health..."
    
    local sapi_url
    if [[ "$ENVIRONMENT" == "local" ]]; then
        sapi_url="http://localhost:${SAPI_PORT}/actuator/health"
    else
        sapi_url="http://${SAPI_SERVICE}.${NAMESPACE}.svc.cluster.local:${SAPI_PORT}/actuator/health"
    fi
    
    log_debug "SAPI health URL: $sapi_url"
    
    local response
    local status_code
    
    # Make a request to the health endpoint
    response=$(curl -s -o /dev/null -w "%{http_code}:%{stdout}" -X GET "$sapi_url" 2>/dev/null)
    status_code=$(echo "$response" | cut -d':' -f1)
    
    if [[ "$status_code" == "200" ]]; then
        # Extract the response body
        local body=$(echo "$response" | cut -d':' -f2-)
        local status=$(echo "$body" | jq -r '.status' 2>/dev/null)
        
        if [[ "$status" == "UP" ]]; then
            log_info "Payment-SAPI is healthy"
            return 0
        else
            log_error "Payment-SAPI is not healthy. Status: $status"
            return 1
        fi
    else
        log_error "Failed to check Payment-SAPI health. Status code: $status_code"
        return 1
    fi
}

# Function to check Conjur vault health
check_conjur_health() {
    log_info "Checking Conjur vault health..."
    
    local conjur_url
    if [[ "$ENVIRONMENT" == "local" ]]; then
        conjur_url="http://localhost:${CONJUR_PORT}/health"
    else
        conjur_url="https://${CONJUR_SERVICE}.${NAMESPACE}.svc.cluster.local:${CONJUR_PORT}/health"
    fi
    
    log_debug "Conjur health URL: $conjur_url"
    
    local response
    local status_code
    
    # Make a request to the health endpoint
    response=$(curl -s -o /dev/null -w "%{http_code}:%{stdout}" -k -X GET "$conjur_url" 2>/dev/null)
    status_code=$(echo "$response" | cut -d':' -f1)
    
    if [[ "$status_code" == "200" ]]; then
        # Extract the response body
        local body=$(echo "$response" | cut -d':' -f2-)
        local status=$(echo "$body" | jq -r '.status' 2>/dev/null)
        
        if [[ "$status" == "ok" ]]; then
            log_info "Conjur vault is healthy"
            return 0
        else
            log_error "Conjur vault is not healthy. Status: $status"
            return 1
        fi
    else
        log_error "Failed to check Conjur vault health. Status code: $status_code"
        return 1
    fi
}

# Function to check Redis cache health
check_redis_health() {
    log_info "Checking Redis cache health..."
    
    local result
    
    if [[ "$ENVIRONMENT" == "local" ]]; then
        # For local environment, try to connect to Redis directly
        if command -v redis-cli >/dev/null 2>&1; then
            result=$(redis-cli -h localhost -p ${REDIS_PORT} ping 2>/dev/null)
        else
            log_error "redis-cli not found for local Redis health check"
            return 1
        fi
    else
        # For Kubernetes environment, use kubectl to execute a Redis PING command
        result=$(kubectl -n ${NAMESPACE} exec -it svc/${REDIS_SERVICE} -- redis-cli ping 2>/dev/null)
    fi
    
    log_debug "Redis PING result: $result"
    
    if [[ "$result" == "PONG" ]]; then
        log_info "Redis cache is healthy"
        return 0
    else
        log_error "Redis cache is not healthy. Result: $result"
        return 1
    fi
}

# Function to check pod status for a specific deployment
check_pod_status() {
    local deployment_name="$1"
    log_info "Checking pod status for deployment: $deployment_name"
    
    # Get pods for the specified deployment
    local pods
    pods=$(kubectl -n ${NAMESPACE} get pods -l app="${deployment_name}" -o json 2>/dev/null)
    
    if [[ $? -ne 0 ]]; then
        log_error "Failed to get pods for deployment: $deployment_name"
        return 1
    fi
    
    # Check if any pods exist
    local pod_count
    pod_count=$(echo "$pods" | jq '.items | length')
    
    if [[ "$pod_count" -eq 0 ]]; then
        log_error "No pods found for deployment: $deployment_name"
        return 1
    fi
    
    # Check if all pods are running and ready
    local running_count
    local ready_count
    
    running_count=$(echo "$pods" | jq '[.items[] | select(.status.phase=="Running")] | length')
    ready_count=$(echo "$pods" | jq '[.items[] | select(.status.containerStatuses != null) | select(.status.containerStatuses[].ready==true)] | length')
    
    log_debug "Deployment: $deployment_name, Total: $pod_count, Running: $running_count, Ready: $ready_count"
    
    if [[ "$pod_count" -eq "$running_count" && "$pod_count" -eq "$ready_count" ]]; then
        log_info "All pods for deployment $deployment_name are running and ready"
        return 0
    else
        log_error "Not all pods for deployment $deployment_name are healthy. Total: $pod_count, Running: $running_count, Ready: $ready_count"
        # Get details of problematic pods
        echo "$pods" | jq -r '.items[] | select(.status.phase!="Running" or .status.containerStatuses[].ready==false) | "Pod \(.metadata.name) - Phase: \(.status.phase), Ready: \(.status.containerStatuses[].ready)"' | while read line; do
            log_error "$line"
        done
        return 1
    fi
}

# Function to check all services
check_all_services() {
    log_info "Performing comprehensive health check of all services..."
    
    local status=0
    
    # Check Payment-EAPI health
    check_eapi_health
    if [[ $? -ne 0 ]]; then
        status=1
    fi
    
    # Check Payment-SAPI health
    check_sapi_health
    if [[ $? -ne 0 ]]; then
        status=1
    fi
    
    # Check Conjur vault health
    check_conjur_health
    if [[ $? -ne 0 ]]; then
        status=1
    fi
    
    # Check Redis cache health
    check_redis_health
    if [[ $? -ne 0 ]]; then
        status=1
    fi
    
    if [[ $status -eq 0 ]]; then
        log_info "All services are healthy"
    else
        log_error "One or more services are not healthy"
    fi
    
    return $status
}

# Function to wait for services to become healthy
wait_for_healthy() {
    local timeout_seconds=$1
    log_info "Waiting for all services to become healthy (timeout: ${timeout_seconds}s)..."
    
    local end_time
    end_time=$(( $(date +%s) + timeout_seconds ))
    
    while [[ $(date +%s) -lt $end_time ]]; do
        check_all_services
        
        if [[ $? -eq 0 ]]; then
            log_info "All services are now healthy"
            return 0
        fi
        
        local remaining
        remaining=$(( end_time - $(date +%s) ))
        log_info "Not all services are healthy yet. Retrying in ${HEALTH_CHECK_INTERVAL}s... (${remaining}s remaining)"
        sleep "$HEALTH_CHECK_INTERVAL"
    done
    
    log_error "Timeout reached. Not all services became healthy within ${timeout_seconds}s"
    return 1
}

# Function to check a specific service
check_specific_service() {
    local service_name=$1
    log_info "Checking health of specific service: $service_name"
    
    case "$service_name" in
        eapi)
            check_eapi_health
            return $?
            ;;
        sapi)
            check_sapi_health
            return $?
            ;;
        conjur)
            check_conjur_health
            return $?
            ;;
        redis)
            check_redis_health
            return $?
            ;;
        *)
            log_error "Unknown service: $service_name"
            return 1
            ;;
    esac
}

# Function to wait for a specific service to become healthy
wait_for_service() {
    local service_name=$1
    local timeout_seconds=$2
    log_info "Waiting for service $service_name to become healthy (timeout: ${timeout_seconds}s)..."
    
    local end_time
    end_time=$(( $(date +%s) + timeout_seconds ))
    
    while [[ $(date +%s) -lt $end_time ]]; do
        check_specific_service "$service_name"
        
        if [[ $? -eq 0 ]]; then
            log_info "Service $service_name is now healthy"
            return 0
        fi
        
        local remaining
        remaining=$(( end_time - $(date +%s) ))
        log_info "Service $service_name is not healthy yet. Retrying in ${HEALTH_CHECK_INTERVAL}s... (${remaining}s remaining)"
        sleep "$HEALTH_CHECK_INTERVAL"
    done
    
    log_error "Timeout reached. Service $service_name did not become healthy within ${timeout_seconds}s"
    return 1
}

# Function to display usage information
usage() {
    echo "Usage: $0 [options]"
    echo
    echo "Health check script for Payment API Security Enhancement components"
    echo
    echo "Options:"
    echo "  -s, --service SERVICE  Specific service to check (eapi, sapi, conjur, redis)"
    echo "  -w, --wait            Wait for services to become healthy"
    echo "  -t, --timeout SECONDS Timeout in seconds when waiting for health (default: 300)"
    echo "  -e, --environment ENV Target environment (dev, test, staging, prod) (default: dev)"
    echo "  -n, --namespace NS    Kubernetes namespace (default: payment-system)"
    echo "  -i, --interval SECONDS Interval in seconds between health checks (default: 5)"
    echo "  -v, --verbose         Enable verbose logging"
    echo "  -h, --help            Show this help message"
    echo
    echo "Examples:"
    echo "  $0                     # Check health of all services"
    echo "  $0 -s eapi             # Check health of EAPI service only"
    echo "  $0 -w -t 600           # Wait for all services to be healthy with 10-min timeout"
    echo "  $0 -s redis -w -t 60   # Wait for Redis to be healthy with 1-min timeout"
    echo "  $0 -e prod -n payment  # Check services in prod environment and payment namespace"
}

# Function to parse command-line arguments
parse_args() {
    local args=("$@")
    local wait_for_health=false
    local specific_service=""
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--help)
                usage
                exit 0
                ;;
            -s|--service)
                if [[ -z "$2" || "$2" == -* ]]; then
                    log_error "Error: Argument for $1 is missing"
                    usage
                    return 1
                fi
                specific_service="$2"
                shift 2
                ;;
            -w|--wait)
                wait_for_health=true
                shift
                ;;
            -t|--timeout)
                if [[ -z "$2" || "$2" == -* ]]; then
                    log_error "Error: Argument for $1 is missing"
                    usage
                    return 1
                fi
                HEALTH_CHECK_TIMEOUT="$2"
                shift 2
                ;;
            -e|--environment)
                if [[ -z "$2" || "$2" == -* ]]; then
                    log_error "Error: Argument for $1 is missing"
                    usage
                    return 1
                fi
                ENVIRONMENT="$2"
                shift 2
                ;;
            -n|--namespace)
                if [[ -z "$2" || "$2" == -* ]]; then
                    log_error "Error: Argument for $1 is missing"
                    usage
                    return 1
                fi
                NAMESPACE="$2"
                shift 2
                ;;
            -i|--interval)
                if [[ -z "$2" || "$2" == -* ]]; then
                    log_error "Error: Argument for $1 is missing"
                    usage
                    return 1
                fi
                HEALTH_CHECK_INTERVAL="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                return 1
                ;;
        esac
    done
    
    # Validate service name if specified
    if [[ -n "$specific_service" ]]; then
        case "$specific_service" in
            eapi|sapi|conjur|redis)
                # Valid service
                ;;
            *)
                log_error "Invalid service name: $specific_service. Must be one of: eapi, sapi, conjur, redis"
                usage
                return 1
                ;;
        esac
    fi
    
    # Store values in global variables
    SERVICE="$specific_service"
    WAIT_FOR_HEALTH="$wait_for_health"
    
    return 0
}

# Main function
main() {
    # Parse command-line arguments
    parse_args "$@"
    if [[ $? -ne 0 ]]; then
        return 1
    fi
    
    # Create logs directory if it doesn't exist
    mkdir -p "$(dirname "$LOG_FILE")"
    
    log_info "Starting health check script"
    log_info "Environment: $ENVIRONMENT"
    log_info "Namespace: $NAMESPACE"
    log_debug "Configuration:"
    log_debug "  EAPI_SERVICE: $EAPI_SERVICE"
    log_debug "  SAPI_SERVICE: $SAPI_SERVICE"
    log_debug "  CONJUR_SERVICE: $CONJUR_SERVICE"
    log_debug "  REDIS_SERVICE: $REDIS_SERVICE"
    log_debug "  HEALTH_CHECK_TIMEOUT: $HEALTH_CHECK_TIMEOUT"
    log_debug "  HEALTH_CHECK_INTERVAL: $HEALTH_CHECK_INTERVAL"
    log_debug "  VERBOSE: $VERBOSE"
    log_debug "  LOG_FILE: $LOG_FILE"
    
    local status=0
    
    # Check specific service if requested
    if [[ -n "$SERVICE" ]]; then
        log_info "Checking specific service: $SERVICE"
        
        if [[ "$WAIT_FOR_HEALTH" == "true" ]]; then
            wait_for_service "$SERVICE" "$HEALTH_CHECK_TIMEOUT"
            status=$?
        else
            check_specific_service "$SERVICE"
            status=$?
        fi
    else
        # Otherwise check all services
        log_info "Checking all services"
        
        if [[ "$WAIT_FOR_HEALTH" == "true" ]]; then
            wait_for_healthy "$HEALTH_CHECK_TIMEOUT"
            status=$?
        else
            check_all_services
            status=$?
        fi
    fi
    
    if [[ $status -eq 0 ]]; then
        log_info "Health check completed successfully"
    else
        log_error "Health check failed"
    fi
    
    return $status
}

# Execute main function with all script arguments
main "$@"
exit $?