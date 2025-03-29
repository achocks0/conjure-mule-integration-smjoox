# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Terraform Outputs
# ------------------------------------------------------------------------------
# This file defines all outputs from the infrastructure deployment that can be
# used by other systems or for reference. It consolidates critical information
# about the deployed infrastructure components.
# ------------------------------------------------------------------------------

# Consolidated infrastructure information
output "infrastructure_outputs" {
  description = "Consolidated output of all infrastructure components"
  value = {
    environment           = var.environment
    region                = var.region
    vpc_id                = module.networking.vpc_id
    kubernetes_cluster_name = module.kubernetes.kubernetes_cluster_name
    conjur_endpoint       = module.conjur.conjur_endpoint
    redis_primary_endpoint = module.redis.redis_primary_endpoint
    prometheus_endpoint   = module.monitoring.prometheus_endpoint
    grafana_endpoint      = module.monitoring.grafana_endpoint
  }
  sensitive   = false
}

# Network-related outputs
output "network_outputs" {
  description = "Network-related outputs including VPC, subnets, and security groups"
  value = {
    vpc_id             = module.networking.vpc_id
    subnet_ids         = module.networking.subnet_ids
    security_group_ids = module.networking.security_group_ids
  }
  sensitive   = false
}

# Kubernetes-related outputs
output "kubernetes_outputs" {
  description = "Kubernetes-related outputs including cluster endpoint and credentials"
  value = {
    cluster_name                  = module.kubernetes.kubernetes_cluster_name
    cluster_endpoint              = module.kubernetes.kubernetes_cluster_endpoint
    cluster_certificate_authority_data = module.kubernetes.kubernetes_cluster_certificate_authority_data
  }
  sensitive   = false
}

# Security-related outputs
output "security_outputs" {
  description = "Security-related outputs including Conjur vault endpoint and certificates"
  value = {
    conjur_endpoint            = module.conjur.conjur_endpoint
    conjur_certificate_authority = module.conjur.conjur_certificate_authority
  }
  sensitive   = false
}

# Cache-related outputs
output "cache_outputs" {
  description = "Cache-related outputs including Redis endpoints"
  value = {
    redis_primary_endpoint    = module.redis.redis_primary_endpoint
    redis_reader_endpoint     = module.redis.redis_reader_endpoint
    redis_security_group_id   = module.redis.redis_security_group_id
    redis_credentials_secret_arn = module.redis.redis_credentials_secret_arn
  }
  sensitive   = false
}

# Monitoring-related outputs
output "monitoring_outputs" {
  description = "Monitoring-related outputs including Prometheus and Grafana endpoints"
  value = {
    prometheus_endpoint = module.monitoring.prometheus_endpoint
    grafana_endpoint    = module.monitoring.grafana_endpoint
  }
  sensitive   = false
}

# Application configuration values
output "application_config" {
  description = "Application configuration values derived from infrastructure for deployment"
  value = {
    eapi_config = {
      conjur_url           = module.conjur.conjur_endpoint
      conjur_account       = var.environment
      redis_url            = module.redis.redis_primary_endpoint
      redis_credentials_arn = module.redis.redis_credentials_secret_arn
      sapi_url             = "https://payment-sapi.${var.environment}.payment.internal"
    }
    sapi_config = {
      redis_url            = module.redis.redis_reader_endpoint
      redis_credentials_arn = module.redis.redis_credentials_secret_arn
    }
    monitoring_config = {
      prometheus_url       = module.monitoring.prometheus_endpoint
      grafana_url          = module.monitoring.grafana_endpoint
    }
  }
  sensitive   = false
}

# Deployment information
output "deployment_info" {
  description = "Information needed for deployment scripts and CI/CD pipelines"
  value = {
    environment        = var.environment
    region             = var.region
    kubernetes_context = "aws eks update-kubeconfig --name ${module.kubernetes.kubernetes_cluster_name} --region ${var.region}"
    namespace          = "payment-system"
  }
  sensitive   = false
}