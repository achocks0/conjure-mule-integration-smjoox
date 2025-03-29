# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Development Environment Variables
# ------------------------------------------------------------------------------
# This file contains environment-specific configuration values for the
# development environment of the Payment API Security Enhancement project.
# ------------------------------------------------------------------------------

# Core deployment variables
environment = "dev"
region      = "us-east-1"

# Networking configuration
vpc_cidr = "10.0.0.0/16"
subnet_cidrs = {
  public  = ["10.0.0.0/24", "10.0.1.0/24", "10.0.2.0/24"]
  private = ["10.0.10.0/24", "10.0.11.0/24", "10.0.12.0/24"]
  data    = ["10.0.20.0/24", "10.0.21.0/24", "10.0.22.0/24"]
}
availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

# Kubernetes configuration
# Using smaller instance types for development environment
kubernetes_version = "1.22"
kubernetes_node_groups = {
  application = {
    instance_types = ["t3.medium"]  # Smaller instances for dev
    scaling_config = {
      desired_size = 2
      min_size     = 2
      max_size     = 4
    }
  }
  data = {
    instance_types = ["t3.large"]  # Smaller instances for dev
    scaling_config = {
      desired_size = 2
      min_size     = 2
      max_size     = 3
    }
  }
}

# Conjur vault configuration
# Development environment uses single instance without HA
conjur_version       = "5.0"
conjur_instance_type = "t3.medium"  # Smaller instance for dev
conjur_ha_enabled    = false  # Disable HA for cost savings in dev

# Redis cache configuration
# Smaller instance size with minimal node count for development
redis_version         = "6.2"
redis_node_type       = "cache.t3.medium"  # Smaller instances for dev
redis_cluster_enabled = true
redis_node_count      = 2  # Fewer nodes for dev

# Database configuration
# Smaller instance with reduced storage and no multi-AZ for development
db_instance_class    = "db.t3.medium"  # Smaller instance for dev
db_allocated_storage = 50  # Reduced storage for dev
db_multi_az          = false  # Single AZ for dev to reduce costs

# Monitoring configuration
monitoring_enabled        = true
prometheus_retention_days = 15  # Shorter retention for dev
slack_webhook_url         = "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"
pagerduty_service_key     = "00000000000000000000000000000000"
grafana_admin_password    = "dev-grafana-password"  # Simple password for dev only

# Service configuration
# Fewer replicas for development environment
eapi_replicas = {
  min     = 2
  max     = 4
  desired = 2
}

sapi_replicas = {
  min     = 2
  max     = 4
  desired = 2
}

# Resource tagging
tags = {
  Environment = "dev"
  Project     = "Payment API Security Enhancement"
  ManagedBy   = "Terraform"
  CostCenter  = "IT-Security"
  Owner       = "Payment Processing Team"
}