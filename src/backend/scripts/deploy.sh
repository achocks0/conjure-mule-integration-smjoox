#!/bin/bash
#
# deploy.sh - Deployment script for Payment API Security Enhancement project
#
# This script deploys the Payment API Security Enhancement components to Kubernetes,
# including Payment-EAPI, Payment-SAPI, Conjur vault, Redis cache, and Monitoring services.
# It handles preparing deployment files, applying them to the cluster, verifying health,
# and automatic rollback on failures.
#
# Requirements:
# - kubectl 1.22+
# - bash 4.0+
# - jq 1.6+ (for parsing deployment status)
#
# Usage:
#   ./deploy.sh [options]
#
# Options:
#   -e, --environment ENV   Target environment (dev, test, staging, prod) (default: dev)
#   -n, --namespace NS      Kubernetes namespace (default: payment-system)
#   -r, --registry URL      Container registry URL (default: localhost:5000)
#   -v, --version VERSION   Version tag for images (default: latest)
#   --eapi                  Deploy Payment-EAPI component (default: true)
#   --sapi                  Deploy Payment-SAPI component (default: true)
#   --conjur                Deploy Conjur vault component (default: false)
#   --redis                 Deploy Redis cache component (default: false)
#   --monitoring            Deploy Monitoring component (default: false)
#   --no-verify             Skip deployment verification
#   --no-rollback           Disable automatic rollback on failure
#   --dry-run               Show what would be deployed without making changes
#   -h, --help              Show usage information

# Determine script location
SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
KUBERNETES_DIR="$PROJECT_ROOT/kubernetes"

# Configuration variables with default values
ENVIRONMENT=${ENVIRONMENT:-dev}
NAMESPACE=${NAMESPACE:-payment-system}
REGISTRY_URL=${REGISTRY_URL:-localhost:5000}
VERSION=${VERSION:-latest}
DEPLOY_EAPI=${DEPLOY_EAPI:-true}
DEPLOY_SAPI=${DEPLOY_SAPI:-true}
DEPLOY_CONJUR=${DEPLOY_CONJUR:-false}
DEPLOY_REDIS=${DEPLOY_REDIS:-false}
DEPLOY_MONITORING=${DEPLOY_MONITORING:-false}
VERIFY_DEPLOYMENT=${VERIFY_DEPLOYMENT:-true}
ROLLBACK_ON_FAILURE=${ROLLBACK_ON_FAILURE:-true}
HEALTH_CHECK_TIMEOUT=${HEALTH_CHECK_TIMEOUT:-300}
DRY_RUN=${DRY_RUN:-false}
LOG_FILE="$PROJECT_ROOT/logs/deploy-$(date +%Y%m%d%H%M%S).log"

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

# Check prerequisites
check_prerequisites() {
    log_info "Checking deployment prerequisites..."

    # Check if kubectl is installed
    if ! command -v kubectl >/dev/null 2>&1; then
        log_error "kubectl not found, please install it"
        return 1
    fi

    # Check if kubectl can connect to the cluster
    if ! kubectl cluster-info >/dev/null 2>&1; then
        log_error "Unable to connect to Kubernetes cluster, please check your configuration"
        return 1
    fi

    # Check if jq is installed (used for parsing kubectl output)
    if ! command -v jq >/dev/null 2>&1; then
        log_error "jq not found, please install it for proper deployment status parsing"
        return 1
    fi

    # Check if namespace exists, create if not
    if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
        log_info "Namespace $NAMESPACE does not exist, creating it..."
        
        if [[ "$DRY_RUN" == "true" ]]; then
            log_info "[DRY RUN] Would create namespace: $NAMESPACE"
        else
            kubectl create namespace "$NAMESPACE"
            if [[ $? -ne 0 ]]; then
                log_error "Failed to create namespace $NAMESPACE"
                return 1
            fi
        fi
    fi

    log_info "Prerequisites check completed successfully"
    return 0
}

# Prepare deployment files
prepare_deployment_files() {
    log_info "Preparing deployment files..."
    
    # Create a temporary directory for modified deployment files
    TMP_DIR=$(mktemp -d)
    log_debug "Created temporary directory: $TMP_DIR"
    
    # List of deployment files to process
    local deployment_files=()
    
    if [[ "$DEPLOY_EAPI" == "true" ]]; then
        deployment_files+=("eapi-deployment.yaml")
    fi
    
    if [[ "$DEPLOY_SAPI" == "true" ]]; then
        deployment_files+=("sapi-deployment.yaml")
    fi
    
    if [[ "$DEPLOY_CONJUR" == "true" ]]; then
        deployment_files+=("conjur-deployment.yaml")
    fi
    
    if [[ "$DEPLOY_REDIS" == "true" ]]; then
        deployment_files+=("redis-statefulset.yaml")
    fi
    
    if [[ "$DEPLOY_MONITORING" == "true" ]]; then
        deployment_files+=("monitoring-deployment.yaml")
    fi
    
    # Process each deployment file
    for file in "${deployment_files[@]}"; do
        log_debug "Processing deployment file: $file"
        
        local src_file="$KUBERNETES_DIR/$file"
        local dst_file="$TMP_DIR/$file"
        
        if [[ ! -f "$src_file" ]]; then
            log_error "Deployment file not found: $src_file"
            return 1
        fi
        
        # Replace image tags with proper version
        cat "$src_file" | sed "s|image: payment-registry/|image: $REGISTRY_URL/|g" | sed "s|:latest|:$VERSION|g" > "$dst_file"
        
        # Apply environment-specific configurations
        if [[ "$ENVIRONMENT" == "prod" ]]; then
            # For production, increase resource limits
            sed -i 's|cpu: "1"|cpu: "2"|g' "$dst_file"
            sed -i 's|memory: "2Gi"|memory: "4Gi"|g' "$dst_file"
            # Update replicas for higher availability
            sed -i 's|replicas: 2|replicas: 3|g' "$dst_file"
        elif [[ "$ENVIRONMENT" == "staging" ]]; then
            # For staging, use moderate resources
            sed -i 's|cpu: "500m"|cpu: "1"|g' "$dst_file"
            sed -i 's|memory: "1Gi"|memory: "2Gi"|g' "$dst_file"
        fi
        
        # Update environment in ConfigMap if needed
        if [[ "$file" == *"configmap.yaml" ]]; then
            sed -i "s|SPRING_PROFILES_ACTIVE: dev|SPRING_PROFILES_ACTIVE: $ENVIRONMENT|g" "$dst_file"
        fi
        
        log_debug "Prepared deployment file: $dst_file"
    done
    
    # Store the temp directory path for later use
    DEPLOYMENT_FILES_DIR="$TMP_DIR"
    
    log_info "Deployment files prepared successfully"
    return 0
}

# Deploy a component
deploy_component() {
    local component="$1"
    local deployment_file="$2"
    
    log_info "Deploying component: $component"
    
    if [[ ! -f "$deployment_file" ]]; then
        log_error "Deployment file not found: $deployment_file"
        return 1
    fi
    
    # Deploy the component
    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "[DRY RUN] Would apply deployment file: $deployment_file"
        kubectl apply -f "$deployment_file" --dry-run=client -o yaml
        return 0
    else
        log_debug "Applying deployment file: $deployment_file"
        kubectl apply -f "$deployment_file" -n "$NAMESPACE"
        if [[ $? -ne 0 ]]; then
            log_error "Failed to deploy component: $component"
            return 1
        fi
        
        # Wait for rollout to complete
        log_info "Waiting for $component rollout to complete..."
        
        # Determine the resource type (deployment or statefulset)
        local resource_type
        if [[ "$component" == "redis" ]]; then
            resource_type="statefulset"
        elif [[ "$component" == "conjur" ]]; then
            resource_type="statefulset"
        else
            resource_type="deployment"
        fi
        
        # Wait for rollout to complete
        kubectl rollout status $resource_type/$component -n "$NAMESPACE" --timeout=5m
        if [[ $? -ne 0 ]]; then
            log_error "Rollout failed for component: $component"
            return 1
        fi
    fi
    
    log_info "Component deployed successfully: $component"
    return 0
}

# Verify deployment health
verify_deployment() {
    log_info "Verifying deployment health..."
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "[DRY RUN] Would verify deployment health"
        return 0
    fi
    
    # Source the health check script to use its functions
    source "$SCRIPT_DIR/health-check.sh"
    
    # Setup environment variables for health check
    export ENVIRONMENT=$ENVIRONMENT
    export NAMESPACE=$NAMESPACE
    export HEALTH_CHECK_TIMEOUT=$HEALTH_CHECK_TIMEOUT
    
    # Wait for services to become healthy
    wait_for_healthy "$HEALTH_CHECK_TIMEOUT"
    local status=$?
    
    if [[ $status -eq 0 ]]; then
        log_info "Deployment verification successful"
        return 0
    else
        log_error "Deployment verification failed"
        return 1
    fi
}

# Rollback deployment if verification fails
rollback_deployment() {
    local component=$1
    
    log_info "Rolling back deployment: $component"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "[DRY RUN] Would rollback deployment: $component"
        return 0
    fi
    
    # Determine the resource type (deployment or statefulset)
    local resource_type
    if [[ "$component" == "redis" ]]; then
        resource_type="statefulset"
    elif [[ "$component" == "conjur" ]]; then
        resource_type="statefulset"
    else
        resource_type="deployment"
    fi
    
    # Execute rollback
    kubectl rollout undo $resource_type/$component -n "$NAMESPACE"
    if [[ $? -ne 0 ]]; then
        log_error "Failed to rollback deployment: $component"
        return 1
    fi
    
    # Wait for rollback to complete
    log_info "Waiting for rollback to complete..."
    kubectl rollout status $resource_type/$component -n "$NAMESPACE" --timeout=5m
    if [[ $? -ne 0 ]]; then
        log_error "Rollback failed for component: $component"
        return 1
    fi
    
    log_info "Rollback completed successfully for: $component"
    return 0
}

# Deploy all enabled components
deploy_all() {
    log_info "Starting deployment of components..."
    
    # Prepare deployment files
    prepare_deployment_files
    if [[ $? -ne 0 ]]; then
        log_error "Failed to prepare deployment files"
        return 1
    fi
    
    local deployed_components=()
    local deployment_status=0
    
    # Deploy Conjur vault first (if enabled) as other components depend on it
    if [[ "$DEPLOY_CONJUR" == "true" ]]; then
        deploy_component "conjur" "$DEPLOYMENT_FILES_DIR/conjur-deployment.yaml"
        if [[ $? -ne 0 ]]; then
            log_error "Failed to deploy Conjur vault"
            deployment_status=1
        else
            deployed_components+=("conjur")
        fi
    fi
    
    # Deploy Redis cache next (if enabled) as it's used for token caching
    if [[ "$DEPLOY_REDIS" == "true" ]]; then
        deploy_component "redis" "$DEPLOYMENT_FILES_DIR/redis-statefulset.yaml"
        if [[ $? -ne 0 ]]; then
            log_error "Failed to deploy Redis cache"
            deployment_status=1
        else
            deployed_components+=("redis")
        fi
    fi
    
    # Deploy Payment-EAPI (if enabled)
    if [[ "$DEPLOY_EAPI" == "true" ]]; then
        deploy_component "payment-eapi" "$DEPLOYMENT_FILES_DIR/eapi-deployment.yaml"
        if [[ $? -ne 0 ]]; then
            log_error "Failed to deploy Payment-EAPI"
            deployment_status=1
        else
            deployed_components+=("payment-eapi")
        fi
    fi
    
    # Deploy Payment-SAPI (if enabled)
    if [[ "$DEPLOY_SAPI" == "true" ]]; then
        deploy_component "payment-sapi" "$DEPLOYMENT_FILES_DIR/sapi-deployment.yaml"
        if [[ $? -ne 0 ]]; then
            log_error "Failed to deploy Payment-SAPI"
            deployment_status=1
        else
            deployed_components+=("payment-sapi")
        fi
    fi
    
    # Deploy Monitoring (if enabled)
    if [[ "$DEPLOY_MONITORING" == "true" ]]; then
        deploy_component "monitoring" "$DEPLOYMENT_FILES_DIR/monitoring-deployment.yaml"
        if [[ $? -ne 0 ]]; then
            log_error "Failed to deploy Monitoring"
            deployment_status=1
        else
            deployed_components+=("monitoring")
        fi
    fi
    
    # Verify deployment if enabled
    if [[ "$VERIFY_DEPLOYMENT" == "true" && "$deployment_status" -eq 0 ]]; then
        verify_deployment
        if [[ $? -ne 0 ]]; then
            log_error "Deployment verification failed"
            deployment_status=1
            
            # Rollback deployments if enabled
            if [[ "$ROLLBACK_ON_FAILURE" == "true" ]]; then
                log_info "Rolling back deployments due to verification failure"
                for component in "${deployed_components[@]}"; do
                    rollback_deployment "$component"
                done
            fi
        fi
    fi
    
    # Clean up temporary directory
    if [[ -d "$DEPLOYMENT_FILES_DIR" ]]; then
        rm -rf "$DEPLOYMENT_FILES_DIR"
        log_debug "Removed temporary directory: $DEPLOYMENT_FILES_DIR"
    fi
    
    if [[ "$deployment_status" -eq 0 ]]; then
        log_info "Deployment completed successfully"
    else
        log_error "Deployment failed"
    fi
    
    return $deployment_status
}

# Show usage information
usage() {
    echo "Usage: $0 [options]"
    echo
    echo "Deployment script for Payment API Security Enhancement project"
    echo
    echo "Options:"
    echo "  -e, --environment ENV   Target environment (dev, test, staging, prod) (default: dev)"
    echo "  -n, --namespace NS      Kubernetes namespace (default: payment-system)"
    echo "  -r, --registry URL      Container registry URL (default: localhost:5000)"
    echo "  -v, --version VERSION   Version tag for images (default: latest)"
    echo "  --eapi                  Deploy Payment-EAPI component (default: true)"
    echo "  --no-eapi               Skip Payment-EAPI component deployment"
    echo "  --sapi                  Deploy Payment-SAPI component (default: true)"
    echo "  --no-sapi               Skip Payment-SAPI component deployment"
    echo "  --conjur                Deploy Conjur vault component"
    echo "  --no-conjur             Skip Conjur vault component deployment (default)"
    echo "  --redis                 Deploy Redis cache component"
    echo "  --no-redis              Skip Redis cache component deployment (default)"
    echo "  --monitoring            Deploy Monitoring component"
    echo "  --no-monitoring         Skip Monitoring component deployment (default)"
    echo "  --no-verify             Skip deployment verification"
    echo "  --no-rollback           Disable automatic rollback on failure"
    echo "  --dry-run               Show what would be deployed without making changes"
    echo "  --verbose               Enable verbose logging"
    echo "  -h, --help              Show this help message"
    echo
    echo "Examples:"
    echo "  $0 -e prod -n payment-prod -r registry.example.com -v 1.0.0"
    echo "  $0 --conjur --redis --eapi --sapi"
    echo "  $0 --dry-run --eapi --no-verify"
}

# Parse command-line arguments
parse_args() {
    local args=("$@")
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--help)
                usage
                exit 0
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
            -r|--registry)
                if [[ -z "$2" || "$2" == -* ]]; then
                    log_error "Error: Argument for $1 is missing"
                    usage
                    return 1
                fi
                REGISTRY_URL="$2"
                shift 2
                ;;
            -v|--version)
                if [[ -z "$2" || "$2" == -* ]]; then
                    log_error "Error: Argument for $1 is missing"
                    usage
                    return 1
                fi
                VERSION="$2"
                shift 2
                ;;
            --eapi)
                DEPLOY_EAPI=true
                shift
                ;;
            --no-eapi)
                DEPLOY_EAPI=false
                shift
                ;;
            --sapi)
                DEPLOY_SAPI=true
                shift
                ;;
            --no-sapi)
                DEPLOY_SAPI=false
                shift
                ;;
            --conjur)
                DEPLOY_CONJUR=true
                shift
                ;;
            --no-conjur)
                DEPLOY_CONJUR=false
                shift
                ;;
            --redis)
                DEPLOY_REDIS=true
                shift
                ;;
            --no-redis)
                DEPLOY_REDIS=false
                shift
                ;;
            --monitoring)
                DEPLOY_MONITORING=true
                shift
                ;;
            --no-monitoring)
                DEPLOY_MONITORING=false
                shift
                ;;
            --no-verify)
                VERIFY_DEPLOYMENT=false
                shift
                ;;
            --no-rollback)
                ROLLBACK_ON_FAILURE=false
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --verbose)
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
    
    # Validate environment
    case "$ENVIRONMENT" in
        dev|test|staging|prod)
            # Valid environment
            ;;
        *)
            log_error "Invalid environment: $ENVIRONMENT. Must be one of: dev, test, staging, prod"
            return 1
            ;;
    esac
    
    # Ensure at least one component is enabled
    if [[ "$DEPLOY_EAPI" == "false" && "$DEPLOY_SAPI" == "false" && "$DEPLOY_CONJUR" == "false" && "$DEPLOY_REDIS" == "false" && "$DEPLOY_MONITORING" == "false" ]]; then
        log_error "No components selected for deployment. Please enable at least one component."
        return 1
    fi
    
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
    
    log_info "Starting deployment script for Payment API Security Enhancement"
    log_info "Environment: $ENVIRONMENT"
    log_info "Namespace: $NAMESPACE"
    log_info "Registry URL: $REGISTRY_URL"
    log_info "Version: $VERSION"
    log_debug "Configuration:"
    log_debug "  DEPLOY_EAPI: $DEPLOY_EAPI"
    log_debug "  DEPLOY_SAPI: $DEPLOY_SAPI"
    log_debug "  DEPLOY_CONJUR: $DEPLOY_CONJUR"
    log_debug "  DEPLOY_REDIS: $DEPLOY_REDIS"
    log_debug "  DEPLOY_MONITORING: $DEPLOY_MONITORING"
    log_debug "  VERIFY_DEPLOYMENT: $VERIFY_DEPLOYMENT"
    log_debug "  ROLLBACK_ON_FAILURE: $ROLLBACK_ON_FAILURE"
    log_debug "  DRY_RUN: $DRY_RUN"
    log_debug "  LOG_FILE: $LOG_FILE"
    
    # Check prerequisites
    check_prerequisites
    if [[ $? -ne 0 ]]; then
        log_error "Prerequisites check failed"
        return 1
    fi
    
    # Deploy all components
    deploy_all
    local deployment_status=$?
    
    if [[ "$deployment_status" -eq 0 ]]; then
        log_info "Deployment completed successfully"
    else
        log_error "Deployment failed"
    fi
    
    return $deployment_status
}

# Set up trap to catch script interruptions
trap 'log_info "Deployment script interrupted"; [[ -d "$DEPLOYMENT_FILES_DIR" ]] && rm -rf "$DEPLOYMENT_FILES_DIR"; exit 1' INT TERM

# Execute main function
main "$@"
exit $?