# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Conjur Vault Infrastructure
# ------------------------------------------------------------------------------
# This Terraform configuration file provisions and manages the Conjur vault 
# infrastructure for the Payment API Security Enhancement project.
# ------------------------------------------------------------------------------

# Provider references are managed in the main provider.tf file

# Local variables for naming consistency and configuration
locals {
  name_prefix = "${var.environment}-payment"
  conjur_tags = {
    Component = "conjur-vault"
    Service   = "security"
    ManagedBy = "terraform"
  }
  conjur_domain = "conjur.${var.environment}.payment.internal"
}

# ------------------------------------------------------------------------------
# Security Group
# ------------------------------------------------------------------------------

# Security group for Conjur vault
resource "aws_security_group" "conjur_security_group" {
  name        = "${local.name_prefix}-conjur-sg"
  description = "Security group for Conjur vault"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTPS from Payment-EAPI"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [var.security_group_ids.eapi]
  }

  ingress {
    description     = "HTTPS from Rotation Service"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [var.security_group_ids.rotation]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-sg" })
}

# ------------------------------------------------------------------------------
# IAM Roles and Policies
# ------------------------------------------------------------------------------

# IAM role for Conjur vault EC2 instances
resource "aws_iam_role" "conjur_instance_role" {
  name               = "${local.name_prefix}-conjur-role"
  assume_role_policy = data.aws_iam_policy_document.conjur_assume_role.json
  tags               = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-role" })
}

# IAM policy document for EC2 assume role
data "aws_iam_policy_document" "conjur_assume_role" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

# IAM policy for Conjur vault instances
resource "aws_iam_role_policy" "conjur_policy" {
  name   = "${local.name_prefix}-conjur-policy"
  role   = aws_iam_role.conjur_instance_role.id
  policy = data.aws_iam_policy_document.conjur_policy.json
}

# IAM policy document for Conjur vault permissions
data "aws_iam_policy_document" "conjur_policy" {
  statement {
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret"
    ]
    resources = [aws_secretsmanager_secret.conjur_admin_credentials.arn]
  }

  statement {
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:GenerateDataKey"
    ]
    resources = [aws_kms_key.conjur_kms_key.arn]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      aws_s3_bucket.conjur_backup_bucket.arn,
      "${aws_s3_bucket.conjur_backup_bucket.arn}/*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams"
    ]
    resources = ["${aws_cloudwatch_log_group.conjur_log_group.arn}:*"]
  }
}

# IAM instance profile for Conjur vault instances
resource "aws_iam_instance_profile" "conjur_instance_profile" {
  name = "${local.name_prefix}-conjur-profile"
  role = aws_iam_role.conjur_instance_role.name
  tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-profile" })
}

# ------------------------------------------------------------------------------
# KMS Encryption
# ------------------------------------------------------------------------------

# KMS key for encrypting sensitive data
resource "aws_kms_key" "conjur_kms_key" {
  description             = "KMS key for Conjur vault encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  tags                    = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-kms" })
}

# KMS alias for easier reference
resource "aws_kms_alias" "conjur_kms_alias" {
  name          = "alias/${local.name_prefix}-conjur-key"
  target_key_id = aws_kms_key.conjur_kms_key.key_id
}

# ------------------------------------------------------------------------------
# TLS Certificates
# ------------------------------------------------------------------------------

# CA key for self-signed certificates
resource "tls_private_key" "conjur_ca_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

# CA certificate for signing server certificates
resource "tls_self_signed_cert" "conjur_ca_cert" {
  key_algorithm   = tls_private_key.conjur_ca_key.algorithm
  private_key_pem = tls_private_key.conjur_ca_key.private_key_pem

  subject {
    common_name  = "${local.name_prefix}-conjur-ca"
    organization = "Payment API Security Enhancement"
  }

  validity_period_hours = 8760 # 1 year
  is_ca_certificate     = true

  allowed_uses = [
    "cert_signing",
    "key_encipherment",
    "digital_signature"
  ]
}

# ------------------------------------------------------------------------------
# Admin Credentials
# ------------------------------------------------------------------------------

# Secret for storing Conjur admin credentials
resource "aws_secretsmanager_secret" "conjur_admin_credentials" {
  name        = "${local.name_prefix}-conjur-admin-credentials"
  description = "Admin credentials for Conjur vault"
  kms_key_id  = aws_kms_key.conjur_kms_key.arn
  tags        = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-admin-credentials" })
}

# Random password for admin user
resource "random_password" "conjur_admin_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Secret version containing admin credentials
resource "aws_secretsmanager_secret_version" "conjur_admin_credentials_version" {
  secret_id = aws_secretsmanager_secret.conjur_admin_credentials.id
  secret_string = jsonencode({
    "username": "admin",
    "password": "${random_password.conjur_admin_password.result}"
  })
}

# ------------------------------------------------------------------------------
# EC2 Launch Template
# ------------------------------------------------------------------------------

# Latest Conjur AMI
data "aws_ami" "conjur_ami" {
  most_recent = true
  owners      = ["aws-marketplace"]

  filter {
    name   = "name"
    values = ["cyberark-conjur-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Current AWS region
data "aws_region" "current" {}

# Launch template for Conjur vault instances
resource "aws_launch_template" "conjur_launch_template" {
  name_prefix   = "${local.name_prefix}-conjur-"
  image_id      = data.aws_ami.conjur_ami.id
  instance_type = var.conjur_instance_type

  iam_instance_profile {
    name = aws_iam_instance_profile.conjur_instance_profile.name
  }

  vpc_security_group_ids = [aws_security_group.conjur_security_group.id]

  user_data = base64encode(templatefile("${path.module}/templates/conjur_user_data.sh.tpl", {
    conjur_version = var.conjur_version,
    environment = var.environment,
    admin_password_secret_arn = aws_secretsmanager_secret.conjur_admin_credentials.arn,
    ca_cert_pem = tls_self_signed_cert.conjur_ca_cert.cert_pem,
    region = data.aws_region.current.name
  }))

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 100
      volume_type           = "gp3"
      encrypted             = true
      kms_key_id            = aws_kms_key.conjur_kms_key.arn
      delete_on_termination = true
    }
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required" # IMDSv2 for enhanced security
  }

  tag_specifications {
    resource_type = "instance"
    tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur" })
  }

  tag_specifications {
    resource_type = "volume"
    tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-volume" })
  }
}

# ------------------------------------------------------------------------------
# Auto Scaling Group
# ------------------------------------------------------------------------------

# Auto scaling group for Conjur vault instances
resource "aws_autoscaling_group" "conjur_asg" {
  name_prefix         = "${local.name_prefix}-conjur-"
  min_size            = var.conjur_ha_enabled ? 2 : 1
  max_size            = var.conjur_ha_enabled ? 3 : 1
  desired_capacity    = var.conjur_ha_enabled ? 2 : 1
  vpc_zone_identifier = var.subnet_ids.data
  target_group_arns   = [aws_lb_target_group.conjur_target_group.arn]
  health_check_type   = "ELB"
  health_check_grace_period = 300

  launch_template {
    id      = aws_launch_template.conjur_launch_template.id
    version = "$Latest"
  }

  termination_policies = ["OldestInstance"]

  tags = [
    {
      key                 = "Name"
      value               = "${local.name_prefix}-conjur"
      propagate_at_launch = true
    },
    {
      key                 = "Environment"
      value               = var.environment
      propagate_at_launch = true
    },
    {
      key                 = "Component"
      value               = "conjur-vault"
      propagate_at_launch = true
    },
    {
      key                 = "ManagedBy"
      value               = "terraform"
      propagate_at_launch = true
    }
  ]
}

# ------------------------------------------------------------------------------
# Load Balancer
# ------------------------------------------------------------------------------

# Load balancer for Conjur vault
resource "aws_lb" "conjur_lb" {
  name_prefix        = "conjur-"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [aws_security_group.conjur_security_group.id]
  subnets            = var.subnet_ids.data

  enable_deletion_protection = true

  tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-lb" })
}

# Target group for Conjur vault instances
resource "aws_lb_target_group" "conjur_target_group" {
  name_prefix = "conjur-"
  port        = 443
  protocol    = "HTTPS"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    enabled             = true
    path                = "/health"
    port                = "443"
    protocol            = "HTTPS"
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-tg" })
}

# ACM certificate for HTTPS
resource "aws_acm_certificate" "conjur_cert" {
  domain_name       = local.conjur_domain
  validation_method = "DNS"

  tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-cert" })
}

# HTTPS listener for load balancer
resource "aws_lb_listener" "conjur_listener" {
  load_balancer_arn = aws_lb.conjur_lb.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-2-2017-01"
  certificate_arn   = aws_acm_certificate.conjur_cert.arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.conjur_target_group.arn
  }
}

# ------------------------------------------------------------------------------
# DNS Record
# ------------------------------------------------------------------------------

# Private Route53 zone data source
data "aws_route53_zone" "private_zone" {
  name         = "${var.environment}.payment.internal"
  private_zone = true
}

# DNS record for Conjur vault
resource "aws_route53_record" "conjur_dns" {
  zone_id = data.aws_route53_zone.private_zone.zone_id
  name    = local.conjur_domain
  type    = "A"

  alias {
    name                   = aws_lb.conjur_lb.dns_name
    zone_id                = aws_lb.conjur_lb.zone_id
    evaluate_target_health = true
  }
}

# ------------------------------------------------------------------------------
# Backup Configuration
# ------------------------------------------------------------------------------

# S3 bucket for Conjur backups
resource "aws_s3_bucket" "conjur_backup_bucket" {
  bucket_prefix = "${local.name_prefix}-conjur-backup-"
  force_destroy = false

  tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-backup" })
}

# Bucket encryption for backups
resource "aws_s3_bucket_server_side_encryption_configuration" "conjur_backup_encryption" {
  bucket = aws_s3_bucket.conjur_backup_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.conjur_kms_key.arn
    }
  }
}

# Bucket versioning for backups
resource "aws_s3_bucket_versioning" "conjur_backup_versioning" {
  bucket = aws_s3_bucket.conjur_backup_bucket.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Lifecycle configuration for backups
resource "aws_s3_bucket_lifecycle_configuration" "conjur_backup_lifecycle" {
  bucket = aws_s3_bucket.conjur_backup_bucket.id

  rule {
    id     = "backup-retention"
    status = "Enabled"

    filter {
      prefix = "backups/"
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    expiration {
      days = 90
    }
  }
}

# ------------------------------------------------------------------------------
# Monitoring and Logging
# ------------------------------------------------------------------------------

# CloudWatch log group for Conjur logs
resource "aws_cloudwatch_log_group" "conjur_log_group" {
  name              = "/aws/conjur/${var.environment}"
  retention_in_days = 90
  kms_key_id        = aws_kms_key.conjur_kms_key.arn

  tags = merge(var.tags, local.conjur_tags, { Name = "${local.name_prefix}-conjur-logs" })
}

# ------------------------------------------------------------------------------
# Outputs
# ------------------------------------------------------------------------------

# Conjur endpoint URL
output "conjur_endpoint" {
  description = "The endpoint URL for the Conjur vault"
  value       = "https://${local.conjur_domain}"
}

# Conjur CA certificate
output "conjur_certificate_authority" {
  description = "The CA certificate for the Conjur vault"
  value       = tls_self_signed_cert.conjur_ca_cert.cert_pem
}