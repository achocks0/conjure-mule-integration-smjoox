# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Terraform Variables
# ------------------------------------------------------------------------------
# This file defines all variables needed for infrastructure provisioning 
# including networking, Kubernetes, Conjur vault, Redis cache, and monitoring.
# ------------------------------------------------------------------------------

# Core deployment variables
variable "environment" {
  type        = string
  description = "Deployment environment name (dev, test, staging, prod)"
  default     = "dev"
}

variable "region" {
  type        = string
  description = "AWS region for resource deployment"
  default     = "us-east-1"
}

# Networking variables
variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the VPC"
  default     = "10.0.0.0/16"
}

variable "subnet_cidrs" {
  type        = object({
    public  = list(string)
    private = list(string)
    data    = list(string)
  })
  description = "CIDR blocks for different subnet types"
  default     = {
    public  = ["10.0.0.0/24", "10.0.1.0/24", "10.0.2.0/24"]
    private = ["10.0.10.0/24", "10.0.11.0/24", "10.0.12.0/24"]
    data    = ["10.0.20.0/24", "10.0.21.0/24", "10.0.22.0/24"]
  }
}

variable "availability_zones" {
  type        = list(string)
  description = "List of availability zones to use for multi-AZ deployment"
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

# Kubernetes configuration
variable "kubernetes_version" {
  type        = string
  description = "Version of Kubernetes to deploy"
  default     = "1.22"
}

variable "kubernetes_node_groups" {
  type = object({
    application = object({
      instance_types = list(string)
      scaling_config = object({
        desired_size = number
        min_size     = number
        max_size     = number
      })
    })
    data = object({
      instance_types = list(string)
      scaling_config = object({
        desired_size = number
        min_size     = number
        max_size     = number
      })
    })
    infrastructure = object({
      instance_types = list(string)
      scaling_config = object({
        desired_size = number
        min_size     = number
        max_size     = number
      })
    })
  })
  description = "Configuration for Kubernetes node groups"
  default = {
    application = {
      instance_types = ["m5.large"]
      scaling_config = {
        desired_size = 2
        min_size     = 2
        max_size     = 5
      }
    }
    data = {
      instance_types = ["r5.large"]
      scaling_config = {
        desired_size = 2
        min_size     = 2
        max_size     = 3
      }
    }
    infrastructure = {
      instance_types = ["m5.large"]
      scaling_config = {
        desired_size = 2
        min_size     = 2
        max_size     = 3
      }
    }
  }
}

# Conjur vault configuration
variable "conjur_version" {
  type        = string
  description = "Version of Conjur vault to deploy"
  default     = "5.0"
}

variable "conjur_instance_type" {
  type        = string
  description = "EC2 instance type for Conjur vault"
  default     = "m5.large"
}

variable "conjur_ha_enabled" {
  type        = bool
  description = "Flag to enable high availability for Conjur vault"
  default     = true
}

# Redis cache configuration
variable "redis_version" {
  type        = string
  description = "Version of Redis to deploy"
  default     = "6.2"
}

variable "redis_node_type" {
  type        = string
  description = "Instance type for Redis nodes"
  default     = "cache.m5.large"
}

variable "redis_cluster_enabled" {
  type        = bool
  description = "Flag to enable Redis clustering"
  default     = true
}

variable "redis_node_count" {
  type        = number
  description = "Number of Redis nodes to deploy"
  default     = 3
}

# Database configuration
variable "db_instance_class" {
  type        = string
  description = "Instance class for the database"
  default     = "db.m5.large"
}

variable "db_allocated_storage" {
  type        = number
  description = "Allocated storage for the database in GB"
  default     = 100
}

variable "db_multi_az" {
  type        = bool
  description = "Flag to enable multi-AZ deployment for the database"
  default     = true
}

# Monitoring configuration
variable "monitoring_enabled" {
  type        = bool
  description = "Flag to enable monitoring infrastructure"
  default     = true
}

variable "prometheus_retention_days" {
  type        = number
  description = "Number of days to retain Prometheus metrics"
  default     = 30
}

variable "slack_webhook_url" {
  type        = string
  description = "Webhook URL for Slack notifications"
  default     = ""
}

variable "pagerduty_service_key" {
  type        = string
  description = "Service key for PagerDuty integration"
  default     = ""
}

variable "grafana_admin_password" {
  type        = string
  description = "Admin password for Grafana"
  default     = ""
  sensitive   = true
}

# Service configuration
variable "eapi_replicas" {
  type = object({
    min     = number
    max     = number
    desired = number
  })
  description = "Replica configuration for Payment-EAPI service"
  default = {
    min     = 2
    max     = 5
    desired = 2
  }
}

variable "sapi_replicas" {
  type = object({
    min     = number
    max     = number
    desired = number
  })
  description = "Replica configuration for Payment-SAPI service"
  default = {
    min     = 2
    max     = 4
    desired = 2
  }
}

# Resource tagging
variable "tags" {
  type        = map(string)
  description = "Common tags to apply to all resources"
  default = {
    Project   = "Payment API Security Enhancement"
    ManagedBy = "Terraform"
  }
}