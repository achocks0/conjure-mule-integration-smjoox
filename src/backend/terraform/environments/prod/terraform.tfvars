# Payment API Security Enhancement - Production Environment Configuration
# ------------------------------------------------------------------------------
# This file defines production-specific values for infrastructure resources
# including networking, Kubernetes, Conjur vault, Redis cache, and monitoring
# components with high availability, redundancy, and security configurations.
# ------------------------------------------------------------------------------

# Core deployment variables
environment = "prod"
region      = "us-east-1"

# Networking configuration
vpc_cidr = "10.2.0.0/16"
subnet_cidrs = {
  public  = ["10.2.0.0/24", "10.2.1.0/24", "10.2.2.0/24"]
  private = ["10.2.10.0/24", "10.2.11.0/24", "10.2.12.0/24"]
  data    = ["10.2.20.0/24", "10.2.21.0/24", "10.2.22.0/24"]
}
availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

# Kubernetes configuration - production sizing with high availability
kubernetes_version = "1.22"
kubernetes_node_groups = {
  application = {
    instance_types = ["m5.xlarge"]
    scaling_config = {
      desired_size = 3
      min_size     = 3
      max_size     = 10
    }
  }
  data = {
    instance_types = ["r5.xlarge"]
    scaling_config = {
      desired_size = 3
      min_size     = 3
      max_size     = 6
    }
  }
  infrastructure = {
    instance_types = ["m5.large"]
    scaling_config = {
      desired_size = 3
      min_size     = 3
      max_size     = 5
    }
  }
}

# Conjur vault configuration - high availability for credential management
conjur_version      = "5.0"
conjur_instance_type = "m5.xlarge"
conjur_ha_enabled   = true

# Redis cache configuration - clustered for high throughput and redundancy
redis_version        = "6.2"
redis_node_type      = "cache.m5.xlarge"
redis_cluster_enabled = true
redis_node_count     = 3

# Database configuration - multi-AZ with large instance for production workloads
db_instance_class     = "db.m5.xlarge"
db_allocated_storage  = 200
db_multi_az           = true

# Monitoring configuration - extended retention for production
monitoring_enabled        = true
prometheus_retention_days = 90
slack_webhook_url         = "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"
pagerduty_service_key     = "00000000000000000000000000000000"
grafana_admin_password    = "prod-grafana-password"

# Service configuration - production-sized with auto-scaling
eapi_replicas = {
  min     = 3
  max     = 10
  desired = 5
}

sapi_replicas = {
  min     = 3
  max     = 8
  desired = 5
}

# Resource tagging
tags = {
  Environment         = "prod"
  Project             = "Payment API Security Enhancement"
  ManagedBy           = "Terraform"
  CostCenter          = "IT-Security"
  Owner               = "Payment Processing Team"
  Compliance          = "PCI-DSS,SOC2"
  BusinessCriticality = "High"
}