#!/bin/bash

# Exit on error
set -e

# Script directory and project root
SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

# Configuration with defaults
REGISTRY_URL=${REGISTRY_URL:-localhost:5000}
VERSION=${VERSION:-1.0.0}
BUILD_NUMBER=${BUILD_NUMBER:-$(date +%Y%m%d%H%M%S)}
SKIP_TESTS=${SKIP_TESTS:-false}  # Note: Tests are always skipped in Dockerfiles regardless of this setting
SCAN_IMAGES=${SCAN_IMAGES:-true}
PUSH_IMAGES=${PUSH_IMAGES:-false}
TRIVY_SEVERITY=${TRIVY_SEVERITY:-CRITICAL,HIGH}

# Define services
SERVICES=(
  "payment-eapi:Dockerfile-eapi"
  "payment-sapi:Dockerfile-sapi"
  "credential-rotation:Dockerfile-rotation"
  "monitoring:Dockerfile-monitoring"
)

# Utility functions for logging
log_info() {
  echo -e "\033[0;34m[INFO]\033[0m $(date +"%Y-%m-%d %H:%M:%S") - $1"
}

log_error() {
  echo -e "\033[0;31m[ERROR]\033[0m $(date +"%Y-%m-%d %H:%M:%S") - $1" >&2
}

# Check for prerequisites
check_prerequisites() {
  log_info "Checking prerequisites..."
  
  # Check if Docker is installed
  if ! command -v docker &> /dev/null; then
    log_error "Docker is not installed. Please install Docker to continue."
    return 1
  fi
  
  # Check if Docker daemon is running
  if ! docker info &> /dev/null; then
    log_error "Docker daemon is not running. Please start Docker to continue."
    return 1
  fi
  
  # Check if Trivy is installed if we're going to scan images
  if [ "$SCAN_IMAGES" = "true" ] && ! command -v trivy &> /dev/null; then
    log_error "Trivy is not installed but SCAN_IMAGES is enabled. Please install Trivy or disable scanning."
    return 1
  fi
  
  log_info "All prerequisites are satisfied."
  return 0
}

# Build Docker image
build_docker_image() {
  local service_name=$1
  local dockerfile_name=$2
  local tag="$VERSION-$BUILD_NUMBER"
  
  log_info "Building Docker image for $service_name with tag $tag..."
  
  cd "$PROJECT_ROOT"
  
  local dockerfile_path="$PROJECT_ROOT/$dockerfile_name"
  if [ ! -f "$dockerfile_path" ]; then
    log_error "Dockerfile not found at $dockerfile_path"
    return 1
  fi
  
  # Build Docker image with build args
  if docker build -f "$dockerfile_path" \
                 --build-arg VERSION="$VERSION" \
                 --build-arg BUILD_NUMBER="$BUILD_NUMBER" \
                 -t "$REGISTRY_URL/$service_name:$tag" \
                 .; then
    # Also tag as latest
    docker tag "$REGISTRY_URL/$service_name:$tag" "$REGISTRY_URL/$service_name:latest"
    log_info "Successfully built $service_name image with tag $tag"
    return 0
  else
    log_error "Failed to build $service_name image"
    return 1
  fi
}

# Scan Docker image for vulnerabilities
scan_docker_image() {
  local image_name=$1
  
  if [ "$SCAN_IMAGES" != "true" ]; then
    log_info "Skipping security scan for $image_name as SCAN_IMAGES is disabled"
    return 0
  fi
  
  log_info "Scanning $image_name for vulnerabilities with severity: $TRIVY_SEVERITY..."
  
  if trivy image --severity "$TRIVY_SEVERITY" --exit-code 1 "$image_name"; then
    log_info "No vulnerabilities found in $image_name"
    return 0
  else
    log_error "Vulnerabilities found in $image_name"
    return 1
  fi
}

# Push Docker image to registry
push_docker_image() {
  local image_name=$1
  
  if [ "$PUSH_IMAGES" != "true" ]; then
    log_info "Skipping push for $image_name as PUSH_IMAGES is disabled"
    return 0
  fi
  
  log_info "Pushing $image_name to registry..."
  
  if docker push "$image_name"; then
    log_info "Successfully pushed $image_name to registry"
    return 0
  else
    log_error "Failed to push $image_name to registry"
    return 1
  fi
}

# Build all Docker images
build_all_images() {
  local status=0
  
  for service_info in "${SERVICES[@]}"; do
    local service_name="${service_info%%:*}"
    local dockerfile="${service_info#*:}"
    
    if ! build_docker_image "$service_name" "$dockerfile"; then
      status=1
    fi
  done
  
  return $status
}

# Scan all Docker images
scan_all_images() {
  local status=0
  local tag="$VERSION-$BUILD_NUMBER"
  
  for service_info in "${SERVICES[@]}"; do
    local service_name="${service_info%%:*}"
    
    if ! scan_docker_image "$REGISTRY_URL/$service_name:$tag"; then
      status=1
    fi
  done
  
  return $status
}

# Push all Docker images
push_all_images() {
  local status=0
  local tag="$VERSION-$BUILD_NUMBER"
  
  for service_info in "${SERVICES[@]}"; do
    local service_name="${service_info%%:*}"
    
    if ! push_docker_image "$REGISTRY_URL/$service_name:$tag"; then
      status=1
    fi
    
    if ! push_docker_image "$REGISTRY_URL/$service_name:latest"; then
      status=1
    fi
  done
  
  return $status
}

# Main function
main() {
  local status=0
  
  log_info "Starting build process with the following configuration:"
  log_info "- Registry URL: $REGISTRY_URL"
  log_info "- Version: $VERSION"
  log_info "- Build Number: $BUILD_NUMBER"
  log_info "- Skip Tests: $SKIP_TESTS (Note: Tests are always skipped in Dockerfiles)"
  log_info "- Scan Images: $SCAN_IMAGES"
  log_info "- Push Images: $PUSH_IMAGES"
  log_info "- Trivy Severity: $TRIVY_SEVERITY"
  
  # Check prerequisites
  if ! check_prerequisites; then
    log_error "Failed to meet prerequisites."
    return 1
  fi
  
  # Build Docker images
  if ! build_all_images; then
    log_error "Failed to build one or more Docker images."
    status=1
  fi
  
  # Scan Docker images if builds were successful
  if [ $status -eq 0 ] && [ "$SCAN_IMAGES" = "true" ]; then
    if ! scan_all_images; then
      log_error "Security vulnerabilities found in one or more Docker images."
      status=1
    fi
  fi
  
  # Push Docker images if builds and scans were successful
  if [ $status -eq 0 ] && [ "$PUSH_IMAGES" = "true" ]; then
    if ! push_all_images; then
      log_error "Failed to push one or more Docker images."
      status=1
    fi
  fi
  
  if [ $status -eq 0 ]; then
    log_info "Build process completed successfully."
  else
    log_error "Build process completed with errors."
  fi
  
  return $status
}

# Execute main function and exit with its return code
main
exit $?