# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Networking Infrastructure
# ------------------------------------------------------------------------------
# This Terraform configuration file provisions networking resources including
# VPC, subnets, security groups, and network ACLs to implement the secure 
# network segmentation required for the Payment API Security Enhancement project.
# ------------------------------------------------------------------------------

# Local variables for naming and tagging consistency
locals {
  name_prefix  = "${var.environment}-payment"
  network_tags = {
    Component = "networking"
    Service   = "infrastructure"
    ManagedBy = "terraform"
  }
}

# ------------------------------------------------------------------------------
# VPC and Subnets
# ------------------------------------------------------------------------------

# Main VPC for the Payment API infrastructure
resource "aws_vpc" "payment_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-vpc"
  })
}

# Public subnets for DMZ components (API Gateway, Load Balancers)
resource "aws_subnet" "public_subnets" {
  count                   = length(var.subnet_cidrs.public)
  vpc_id                  = aws_vpc.payment_vpc.id
  cidr_block              = var.subnet_cidrs.public[count.index]
  availability_zone       = var.availability_zones[count.index % length(var.availability_zones)]
  map_public_ip_on_launch = true
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-public-${count.index + 1}"
    Tier = "public"
  })
}

# Private subnets for application components (Payment-EAPI, Payment-SAPI)
resource "aws_subnet" "private_subnets" {
  count                   = length(var.subnet_cidrs.private)
  vpc_id                  = aws_vpc.payment_vpc.id
  cidr_block              = var.subnet_cidrs.private[count.index]
  availability_zone       = var.availability_zones[count.index % length(var.availability_zones)]
  map_public_ip_on_launch = false
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-private-${count.index + 1}"
    Tier = "private"
  })
}

# Data subnets for data storage components (Redis, Conjur, Database)
resource "aws_subnet" "data_subnets" {
  count                   = length(var.subnet_cidrs.data)
  vpc_id                  = aws_vpc.payment_vpc.id
  cidr_block              = var.subnet_cidrs.data[count.index]
  availability_zone       = var.availability_zones[count.index % length(var.availability_zones)]
  map_public_ip_on_launch = false
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-data-${count.index + 1}"
    Tier = "data"
  })
}

# ------------------------------------------------------------------------------
# Internet Gateway and NAT Gateways
# ------------------------------------------------------------------------------

# Internet Gateway for public subnet internet access
resource "aws_internet_gateway" "payment_igw" {
  vpc_id = aws_vpc.payment_vpc.id
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-igw"
  })
}

# Elastic IPs for NAT Gateways
resource "aws_eip" "nat_eips" {
  count = length(var.availability_zones)
  vpc   = true
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-nat-eip-${count.index + 1}"
  })
}

# NAT Gateways for private subnet internet access
resource "aws_nat_gateway" "payment_nat_gateways" {
  count         = length(var.availability_zones)
  allocation_id = aws_eip.nat_eips[count.index].id
  subnet_id     = aws_subnet.public_subnets[count.index].id
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-nat-${count.index + 1}"
  })
}

# ------------------------------------------------------------------------------
# Route Tables
# ------------------------------------------------------------------------------

# Route table for public subnets
resource "aws_route_table" "public_route_table" {
  vpc_id = aws_vpc.payment_vpc.id
  
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.payment_igw.id
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-public-rt"
  })
}

# Route tables for private subnets (one per AZ for NAT Gateway fault isolation)
resource "aws_route_table" "private_route_tables" {
  count  = length(var.availability_zones)
  vpc_id = aws_vpc.payment_vpc.id
  
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.payment_nat_gateways[count.index].id
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-private-rt-${count.index + 1}"
  })
}

# Route tables for data subnets (one per AZ for NAT Gateway fault isolation)
resource "aws_route_table" "data_route_tables" {
  count  = length(var.availability_zones)
  vpc_id = aws_vpc.payment_vpc.id
  
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.payment_nat_gateways[count.index].id
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-data-rt-${count.index + 1}"
  })
}

# ------------------------------------------------------------------------------
# Route Table Associations
# ------------------------------------------------------------------------------

# Associate public subnets with public route table
resource "aws_route_table_association" "public_route_table_associations" {
  count          = length(var.subnet_cidrs.public)
  subnet_id      = aws_subnet.public_subnets[count.index].id
  route_table_id = aws_route_table.public_route_table.id
}

# Associate private subnets with corresponding AZ route tables
resource "aws_route_table_association" "private_route_table_associations" {
  count          = length(var.subnet_cidrs.private)
  subnet_id      = aws_subnet.private_subnets[count.index].id
  route_table_id = aws_route_table.private_route_tables[count.index % length(var.availability_zones)].id
}

# Associate data subnets with corresponding AZ route tables
resource "aws_route_table_association" "data_route_table_associations" {
  count          = length(var.subnet_cidrs.data)
  subnet_id      = aws_subnet.data_subnets[count.index].id
  route_table_id = aws_route_table.data_route_tables[count.index % length(var.availability_zones)].id
}

# ------------------------------------------------------------------------------
# Network ACLs
# ------------------------------------------------------------------------------

# Network ACL for public subnets
resource "aws_network_acl" "public_nacl" {
  vpc_id     = aws_vpc.payment_vpc.id
  subnet_ids = aws_subnet.public_subnets[*].id
  
  # Allow HTTPS inbound
  ingress {
    rule_no    = 100
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 443
    to_port    = 443
  }
  
  # Allow HTTP inbound (for redirect to HTTPS)
  ingress {
    rule_no    = 110
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 80
    to_port    = 80
  }
  
  # Allow ephemeral ports inbound for return traffic
  ingress {
    rule_no    = 120
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }
  
  # Allow all outbound traffic
  egress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-public-nacl"
  })
}

# Network ACL for private subnets
resource "aws_network_acl" "private_nacl" {
  vpc_id     = aws_vpc.payment_vpc.id
  subnet_ids = aws_subnet.private_subnets[*].id
  
  # Allow all inbound traffic from within VPC
  ingress {
    rule_no    = 100
    protocol   = "tcp"
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 65535
  }
  
  # Allow ephemeral ports inbound for return traffic
  ingress {
    rule_no    = 110
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }
  
  # Allow all outbound traffic
  egress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-private-nacl"
  })
}

# Network ACL for data subnets
resource "aws_network_acl" "data_nacl" {
  vpc_id     = aws_vpc.payment_vpc.id
  subnet_ids = aws_subnet.data_subnets[*].id
  
  # Allow all inbound traffic from within VPC
  ingress {
    rule_no    = 100
    protocol   = "tcp"
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 65535
  }
  
  # Allow ephemeral ports inbound for return traffic
  ingress {
    rule_no    = 110
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }
  
  # Allow all outbound traffic
  egress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-data-nacl"
  })
}

# ------------------------------------------------------------------------------
# Security Groups
# ------------------------------------------------------------------------------

# Security group for Payment-EAPI service
resource "aws_security_group" "eapi_sg" {
  name        = "${local.name_prefix}-eapi-sg"
  description = "Security group for Payment-EAPI service"
  vpc_id      = aws_vpc.payment_vpc.id
  
  # Allow HTTPS from API Gateway
  ingress {
    description     = "HTTPS from API Gateway"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.api_gateway_sg.id]
  }
  
  # Allow health check from API Gateway
  ingress {
    description     = "Health check from API Gateway"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.api_gateway_sg.id]
  }
  
  # Allow all outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-eapi-sg"
  })
}

# Security group for Payment-SAPI service
resource "aws_security_group" "sapi_sg" {
  name        = "${local.name_prefix}-sapi-sg"
  description = "Security group for Payment-SAPI service"
  vpc_id      = aws_vpc.payment_vpc.id
  
  # Allow HTTPS from Payment-EAPI
  ingress {
    description     = "HTTPS from Payment-EAPI"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.eapi_sg.id]
  }
  
  # Allow health check from Payment-EAPI
  ingress {
    description     = "Health check from Payment-EAPI"
    from_port       = 8081
    to_port         = 8081
    protocol        = "tcp"
    security_groups = [aws_security_group.eapi_sg.id]
  }
  
  # Allow all outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-sapi-sg"
  })
}

# Security group for Credential Rotation service
resource "aws_security_group" "rotation_sg" {
  name        = "${local.name_prefix}-rotation-sg"
  description = "Security group for Credential Rotation service"
  vpc_id      = aws_vpc.payment_vpc.id
  
  # Allow HTTPS from internal services
  ingress {
    description = "HTTPS from internal services"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  
  # Allow all outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-rotation-sg"
  })
}

# Security group for API Gateway
resource "aws_security_group" "api_gateway_sg" {
  name        = "${local.name_prefix}-api-gateway-sg"
  description = "Security group for API Gateway"
  vpc_id      = aws_vpc.payment_vpc.id
  
  # Allow HTTPS from internet
  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  # Allow all outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-api-gateway-sg"
  })
}

# Security group for monitoring services
resource "aws_security_group" "monitoring_sg" {
  name        = "${local.name_prefix}-monitoring-sg"
  description = "Security group for monitoring services"
  vpc_id      = aws_vpc.payment_vpc.id
  
  # Allow Prometheus access
  ingress {
    description = "Prometheus access"
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  
  # Allow Grafana access
  ingress {
    description = "Grafana access"
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  
  # Allow all outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-monitoring-sg"
  })
}

# ------------------------------------------------------------------------------
# VPC Flow Logs
# ------------------------------------------------------------------------------

# VPC Flow Logs for network traffic monitoring
resource "aws_flow_log" "vpc_flow_logs" {
  log_destination_type = "cloud-watch-logs"
  log_destination      = aws_cloudwatch_log_group.vpc_flow_logs.arn
  traffic_type         = "ALL"
  vpc_id               = aws_vpc.payment_vpc.id
  iam_role_arn         = aws_iam_role.vpc_flow_log_role.arn
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-vpc-flow-logs"
  })
}

# CloudWatch Log Group for VPC Flow Logs
resource "aws_cloudwatch_log_group" "vpc_flow_logs" {
  name              = "/aws/vpc-flow-logs/${var.environment}/payment"
  retention_in_days = 90
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-vpc-flow-logs"
  })
}

# IAM Role for VPC Flow Logs
resource "aws_iam_role" "vpc_flow_log_role" {
  name               = "${local.name_prefix}-vpc-flow-log-role"
  assume_role_policy = data.aws_iam_policy_document.vpc_flow_log_assume_role.json
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-vpc-flow-log-role"
  })
}

# IAM Policy for VPC Flow Logs
resource "aws_iam_role_policy" "vpc_flow_log_policy" {
  name   = "${local.name_prefix}-vpc-flow-log-policy"
  role   = aws_iam_role.vpc_flow_log_role.id
  policy = data.aws_iam_policy_document.vpc_flow_log_policy.json
}

# IAM Policy Document for VPC Flow Logs assume role
data "aws_iam_policy_document" "vpc_flow_log_assume_role" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

# IAM Policy Document for VPC Flow Logs permissions
data "aws_iam_policy_document" "vpc_flow_log_policy" {
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams"
    ]
    resources = ["*"]
  }
}

# ------------------------------------------------------------------------------
# Internal DNS
# ------------------------------------------------------------------------------

# Private Route53 zone for internal service discovery
resource "aws_route53_zone" "private_zone" {
  name = "${var.environment}.payment.internal"
  
  vpc {
    vpc_id = aws_vpc.payment_vpc.id
  }
  
  tags = merge(var.tags, local.network_tags, {
    Name = "${local.name_prefix}-private-zone"
  })
}

# ------------------------------------------------------------------------------
# Outputs
# ------------------------------------------------------------------------------

output "vpc_id" {
  description = "The ID of the VPC"
  value       = aws_vpc.payment_vpc.id
}

output "subnet_ids" {
  description = "The IDs of the subnets by type"
  value = {
    public  = aws_subnet.public_subnets[*].id
    private = aws_subnet.private_subnets[*].id
    data    = aws_subnet.data_subnets[*].id
  }
}

output "security_group_ids" {
  description = "The IDs of the security groups"
  value = {
    eapi        = aws_security_group.eapi_sg.id
    sapi        = aws_security_group.sapi_sg.id
    rotation    = aws_security_group.rotation_sg.id
    api_gateway = aws_security_group.api_gateway_sg.id
    monitoring  = aws_security_group.monitoring_sg.id
  }
}

output "nat_gateway_ids" {
  description = "The IDs of the NAT gateways"
  value       = aws_nat_gateway.payment_nat_gateways[*].id
}

output "route53_zone_id" {
  description = "The ID of the private Route53 zone"
  value       = aws_route53_zone.private_zone.zone_id
}