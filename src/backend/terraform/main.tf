# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Main Terraform Configuration
# ------------------------------------------------------------------------------
# This file serves as the entry point for the Terraform infrastructure deployment,
# configuring providers, backend state storage, and orchestrating the various
# infrastructure modules for the Payment API Security Enhancement project.
# ------------------------------------------------------------------------------

# Define required Terraform version and providers
terraform {
  required_version = ">= 1.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.10"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.5"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.1"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 3.1"
    }
  }

  # Remote state configuration
  backend "s3" {
    bucket         = "${var.environment}-payment-terraform-state"
    key            = "payment-api-security/terraform.tfstate"
    region         = "${var.region}"
    encrypt        = true
    dynamodb_table = "${var.environment}-payment-terraform-locks"
  }
}

# Configure AWS provider
provider "aws" {
  region = var.region
  
  default_tags {
    tags = var.tags
  }
}

# Configure Kubernetes provider - uses EKS cluster created in module.kubernetes
provider "kubernetes" {
  host                   = module.kubernetes.kubernetes_cluster_endpoint
  cluster_ca_certificate = base64decode(module.kubernetes.kubernetes_cluster_certificate_authority_data)
  
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", module.kubernetes.kubernetes_cluster_name, "--region", var.region]
  }
}

# Configure Helm provider - uses same EKS cluster
provider "helm" {
  kubernetes {
    host                   = module.kubernetes.kubernetes_cluster_endpoint
    cluster_ca_certificate = base64decode(module.kubernetes.kubernetes_cluster_certificate_authority_data)
    
    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.kubernetes.kubernetes_cluster_name, "--region", var.region]
    }
  }
}

# Local variables for naming consistency and common tags
locals {
  name_prefix = "${var.environment}-payment"
  common_tags = {
    Project     = "Payment API Security Enhancement"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Create S3 bucket for Terraform state storage
resource "aws_s3_bucket" "terraform_state" {
  bucket = "${var.environment}-payment-terraform-state"
  acl    = "private"
  
  versioning {
    enabled = true
  }
  
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm = "AES256"
      }
    }
  }
  
  tags = merge(var.tags, local.common_tags, { Name = "${var.environment}-payment-terraform-state" })
}

# Create DynamoDB table for Terraform state locking
resource "aws_dynamodb_table" "terraform_locks" {
  name         = "${var.environment}-payment-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"
  
  attribute {
    name = "LockID"
    type = "S"
  }
  
  tags = merge(var.tags, local.common_tags, { Name = "${var.environment}-payment-terraform-locks" })
}

# Data sources for current AWS region and account
data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

# Networking module - creates VPC, subnets, security groups, etc.
module "networking" {
  source = "./networking.tf"
  
  environment        = var.environment
  region             = var.region
  vpc_cidr           = var.vpc_cidr
  subnet_cidrs       = var.subnet_cidrs
  availability_zones = var.availability_zones
  tags               = merge(var.tags, local.common_tags)
}

# Kubernetes module - creates EKS cluster, node groups, etc.
module "kubernetes" {
  source = "./kubernetes.tf"
  
  environment           = var.environment
  vpc_id                = module.networking.vpc_id
  subnet_ids            = module.networking.subnet_ids
  security_group_ids    = module.networking.security_group_ids
  kubernetes_version    = var.kubernetes_version
  kubernetes_node_groups = var.kubernetes_node_groups
  eapi_replicas         = var.eapi_replicas
  sapi_replicas         = var.sapi_replicas
  tags                  = merge(var.tags, local.common_tags)
  
  depends_on = [module.networking]
}

# Conjur module - creates Conjur vault infrastructure
module "conjur" {
  source = "./conjur.tf"
  
  environment         = var.environment
  vpc_id              = module.networking.vpc_id
  subnet_ids          = module.networking.subnet_ids
  security_group_ids  = module.networking.security_group_ids
  conjur_version      = var.conjur_version
  conjur_instance_type = var.conjur_instance_type
  conjur_ha_enabled   = var.conjur_ha_enabled
  tags                = merge(var.tags, local.common_tags)
  
  depends_on = [module.networking]
}

# Redis module - creates Redis cache infrastructure
module "redis" {
  source = "./redis.tf"
  
  environment          = var.environment
  vpc_id               = module.networking.vpc_id
  subnet_ids           = module.networking.subnet_ids
  security_group_ids   = module.networking.security_group_ids
  redis_version        = var.redis_version
  redis_node_type      = var.redis_node_type
  redis_cluster_enabled = var.redis_cluster_enabled
  redis_node_count     = var.redis_node_count
  tags                 = merge(var.tags, local.common_tags)
  
  depends_on = [module.networking]
}

# Monitoring module - creates monitoring infrastructure
module "monitoring" {
  source = "./monitoring.tf"
  
  environment               = var.environment
  kubernetes_namespace      = module.kubernetes.kubernetes_namespace
  monitoring_enabled        = var.monitoring_enabled
  prometheus_retention_days = var.prometheus_retention_days
  slack_webhook_url         = var.slack_webhook_url
  pagerduty_service_key     = var.pagerduty_service_key
  grafana_admin_password    = var.grafana_admin_password
  tags                      = merge(var.tags, local.common_tags)
  
  depends_on = [module.kubernetes]
}