# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Redis Cache Infrastructure
# ------------------------------------------------------------------------------
# This file defines the AWS ElastiCache Redis cluster infrastructure for secure
# token caching and credential metadata storage, with high availability,
# encryption, and performance monitoring capabilities.
# ------------------------------------------------------------------------------

locals {
  name_prefix = "${var.environment}-payment"
  redis_tags = {
    Component = "redis"
    Service   = "cache"
    ManagedBy = "terraform"
  }
}

# ---------------------------------------------------------------------------------------------------------------------
# Redis subnet group - places Redis nodes in the data subnet tier
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_elasticache_subnet_group" "redis_subnet_group" {
  name        = "${local.name_prefix}-redis-subnet-group"
  subnet_ids  = var.subnet_ids.data
  description = "Subnet group for Redis cache"
  tags        = merge(var.tags, local.redis_tags)
}

# ---------------------------------------------------------------------------------------------------------------------
# Redis security group - restricts access to Redis cluster
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_security_group" "redis_security_group" {
  name        = "${local.name_prefix}-redis-sg"
  description = "Security group for Redis cache"
  vpc_id      = var.vpc_id

  # Allow connections from Payment-EAPI service
  ingress {
    description     = "Redis from Payment-EAPI"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.security_group_ids.eapi]
  }

  # Allow connections from Payment-SAPI service
  ingress {
    description     = "Redis from Payment-SAPI"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.security_group_ids.sapi]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, local.redis_tags)
}

# ---------------------------------------------------------------------------------------------------------------------
# Redis authentication - generates a secure random token for Redis authentication
# ---------------------------------------------------------------------------------------------------------------------
resource "random_password" "redis_auth_token" {
  length  = 32
  special = false
}

# Store the auth token in Secrets Manager
resource "aws_secretsmanager_secret" "redis_auth_secret" {
  name        = "${local.name_prefix}-redis-auth"
  description = "Authentication token for Redis cache"
  tags        = merge(var.tags, local.redis_tags)
}

resource "aws_secretsmanager_secret_version" "redis_auth_secret_version" {
  secret_id     = aws_secretsmanager_secret.redis_auth_secret.id
  secret_string = random_password.redis_auth_token.result
}

# ---------------------------------------------------------------------------------------------------------------------
# Redis parameter group - defines Redis-specific configuration
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_elasticache_parameter_group" "redis_params" {
  name        = "${local.name_prefix}-redis-params"
  family      = "redis6.x"
  description = "Parameter group for Redis cache"

  parameter {
    name  = "maxmemory-policy"
    value = "volatile-lru"  # Evict only keys with TTL when memory is full
  }

  parameter {
    name  = "notify-keyspace-events"
    value = "Ex"  # Enable notifications for expired keys
  }

  tags = merge(var.tags, local.redis_tags)
}

# ---------------------------------------------------------------------------------------------------------------------
# Redis cluster - the main ElastiCache replication group
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_elasticache_replication_group" "redis_cluster" {
  replication_group_id       = "${local.name_prefix}-redis"
  description                = "Redis cache for Payment API Security Enhancement"
  node_type                  = var.redis_node_type
  engine                     = "redis"
  engine_version             = var.redis_version
  port                       = 6379
  parameter_group_name       = aws_elasticache_parameter_group.redis_params.name
  subnet_group_name          = aws_elasticache_subnet_group.redis_subnet_group.name
  security_group_ids         = [aws_security_group.redis_security_group.id]
  
  # Security enhancements - encryption at rest and in transit
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth_token.result

  # High availability configuration
  automatic_failover_enabled = true
  multi_az_enabled           = true
  num_cache_clusters         = var.redis_node_count
  
  # Backup and maintenance
  snapshot_retention_limit   = 7  # 7 days of backups
  snapshot_window            = "03:00-05:00"  # 3-5 AM UTC
  maintenance_window         = "sun:05:00-sun:07:00"  # Sunday 5-7 AM UTC
  
  # Update behavior
  apply_immediately          = false  # Apply changes during maintenance window
  auto_minor_version_upgrade = true   # Automatically apply minor version upgrades

  tags = merge(var.tags, local.redis_tags)
}

# ---------------------------------------------------------------------------------------------------------------------
# DNS record - for easy access within the internal network
# ---------------------------------------------------------------------------------------------------------------------
data "aws_route53_zone" "private_zone" {
  name         = "${var.environment}.payment.internal"
  private_zone = true
}

resource "aws_route53_record" "redis_dns" {
  zone_id = data.aws_route53_zone.private_zone.zone_id
  name    = "redis.${var.environment}.payment.internal"
  type    = "CNAME"
  ttl     = 300
  records = [aws_elasticache_replication_group.redis_cluster.primary_endpoint_address]
}

# ---------------------------------------------------------------------------------------------------------------------
# Monitoring - CloudWatch alarms for Redis metrics
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_sns_topic" "monitoring_alerts" {
  name = "${local.name_prefix}-monitoring-alerts"
  tags = merge(var.tags, local.redis_tags)
}

# CPU utilization alarm
resource "aws_cloudwatch_metric_alarm" "redis_cpu_utilization_alarm" {
  alarm_name          = "${local.name_prefix}-redis-cpu-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 70
  alarm_description   = "This metric monitors Redis CPU utilization"
  dimensions = {
    CacheClusterId = "${aws_elasticache_replication_group.redis_cluster.id}-001"
  }
  alarm_actions = [aws_sns_topic.monitoring_alerts.arn]
  ok_actions    = [aws_sns_topic.monitoring_alerts.arn]
  tags          = merge(var.tags, local.redis_tags)
}

# Memory utilization alarm
resource "aws_cloudwatch_metric_alarm" "redis_memory_utilization_alarm" {
  alarm_name          = "${local.name_prefix}-redis-memory-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "DatabaseMemoryUsagePercentage"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "This metric monitors Redis memory utilization"
  dimensions = {
    CacheClusterId = "${aws_elasticache_replication_group.redis_cluster.id}-001"
  }
  alarm_actions = [aws_sns_topic.monitoring_alerts.arn]
  ok_actions    = [aws_sns_topic.monitoring_alerts.arn]
  tags          = merge(var.tags, local.redis_tags)
}

# ---------------------------------------------------------------------------------------------------------------------
# Outputs - expose important information about the Redis cluster
# ---------------------------------------------------------------------------------------------------------------------
output "redis_endpoint" {
  description = "The primary endpoint of the Redis cluster"
  value       = aws_elasticache_replication_group.redis_cluster.primary_endpoint_address
}

output "redis_port" {
  description = "The port of the Redis cluster"
  value       = aws_elasticache_replication_group.redis_cluster.port
}

output "redis_security_group_id" {
  description = "The ID of the Redis security group"
  value       = aws_security_group.redis_security_group.id
}

output "redis_auth_secret_arn" {
  description = "The ARN of the Redis authentication secret"
  value       = aws_secretsmanager_secret.redis_auth_secret.arn
}