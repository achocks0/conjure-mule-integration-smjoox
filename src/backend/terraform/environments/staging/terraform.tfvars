# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Staging Environment Variables
# ------------------------------------------------------------------------------
# This file defines configuration values for the staging environment
# ------------------------------------------------------------------------------

# Core deployment variables
environment = "staging"
region      = "us-east-1"

# Networking variables
vpc_cidr = "10.1.0.0/16"
subnet_cidrs = {
  public  = ["10.1.0.0/24", "10.1.1.0/24", "10.1.2.0/24"]
  private = ["10.1.10.0/24", "10.1.11.0/24", "10.1.12.0/24"]
  data    = ["10.1.20.0/24", "10.1.21.0/24", "10.1.22.0/24"]
}
availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

# Kubernetes configuration
kubernetes_version = "1.22"
kubernetes_node_groups = {
  application = {
    instance_types = ["m5.large"]
    scaling_config = {
      desired_size = 3
      min_size     = 3
      max_size     = 6
    }
  }
  data = {
    instance_types = ["r5.large"]
    scaling_config = {
      desired_size = 2
      min_size     = 2
      max_size     = 4
    }
  }
}

# Conjur vault configuration
conjur_version      = "5.0"
conjur_instance_type = "m5.large"
conjur_ha_enabled   = true

# Redis cache configuration
redis_version        = "6.2"
redis_node_type      = "cache.m5.large"
redis_cluster_enabled = true
redis_node_count     = 3

# Database configuration
db_instance_class    = "db.m5.large"
db_allocated_storage = 100
db_multi_az          = true

# Monitoring configuration
monitoring_enabled        = true
prometheus_retention_days = 30
slack_webhook_url         = "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"
pagerduty_service_key     = "00000000000000000000000000000000"
grafana_admin_password    = "staging-grafana-password"

# Service configuration
eapi_replicas = {
  min     = 3
  max     = 6
  desired = 3
}

sapi_replicas = {
  min     = 3
  max     = 6
  desired = 3
}

# Resource tagging
tags = {
  Environment = "staging"
  Project     = "Payment API Security Enhancement"
  ManagedBy   = "Terraform"
  CostCenter  = "IT-Security"
  Owner       = "Payment Processing Team"
  Compliance  = "PCI-DSS,SOC2"
}