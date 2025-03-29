# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Kubernetes Infrastructure
# ------------------------------------------------------------------------------
# This Terraform configuration provisions and manages the Kubernetes (EKS) 
# infrastructure for the Payment API Security Enhancement project, implementing 
# the secure, scalable environment required for running the Payment-EAPI and
# Payment-SAPI services with appropriate security controls.
# ------------------------------------------------------------------------------

# Provider configurations
provider "kubernetes" {
  host                   = aws_eks_cluster.payment_cluster.endpoint
  cluster_ca_certificate = base64decode(aws_eks_cluster.payment_cluster.certificate_authority[0].data)
  token                  = data.aws_eks_cluster_auth.payment_cluster.token
  version                = "~> 2.10"
}

provider "helm" {
  kubernetes {
    host                   = aws_eks_cluster.payment_cluster.endpoint
    cluster_ca_certificate = base64decode(aws_eks_cluster.payment_cluster.certificate_authority[0].data)
    token                  = data.aws_eks_cluster_auth.payment_cluster.token
  }
  version = "~> 2.5"
}

# Fetch authentication token for EKS cluster
data "aws_eks_cluster_auth" "payment_cluster" {
  name = aws_eks_cluster.payment_cluster.name
}

# Local variables for naming conventions and tagging
locals {
  cluster_name    = "${var.environment}-payment-cluster"
  kubernetes_tags = {
    Component = "kubernetes"
    Service   = "infrastructure"
    ManagedBy = "terraform"
  }
}

# ------------------------------------------------------------------------------
# EKS Cluster and Node Groups
# ------------------------------------------------------------------------------

# EKS Cluster
resource "aws_eks_cluster" "payment_cluster" {
  name     = local.cluster_name
  role_arn = aws_iam_role.eks_cluster_role.arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids         = var.subnet_ids.private
    security_group_ids = [aws_security_group.eks_cluster_sg.id]
    endpoint_private_access = true
    endpoint_public_access  = true
    public_access_cidrs     = ["0.0.0.0/0"]
  }

  enabled_cluster_log_types = [
    "api",
    "audit",
    "authenticator",
    "controllerManager",
    "scheduler"
  ]

  # Ensure IAM role is created before cluster
  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy
  ]

  tags = merge(var.tags, local.kubernetes_tags, {
    Name = local.cluster_name
  })
}

# EKS Node Group for application workloads (Payment-EAPI, Payment-SAPI)
resource "aws_eks_node_group" "application_nodes" {
  cluster_name    = aws_eks_cluster.payment_cluster.name
  node_group_name = "${local.cluster_name}-application"
  node_role_arn   = aws_iam_role.eks_node_role.arn
  subnet_ids      = var.subnet_ids.private
  instance_types  = var.kubernetes_node_groups.application.instance_types

  scaling_config {
    desired_size = var.kubernetes_node_groups.application.scaling_config.desired_size
    min_size     = var.kubernetes_node_groups.application.scaling_config.min_size
    max_size     = var.kubernetes_node_groups.application.scaling_config.max_size
  }

  labels = {
    role        = "application"
    environment = var.environment
  }

  # Ensure IAM role is created before node group
  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.ecr_read_only
  ]

  tags = merge(var.tags, local.kubernetes_tags, {
    Name = "${local.cluster_name}-application-node-group"
  })
}

# EKS Node Group for data workloads (Redis, stateful services)
resource "aws_eks_node_group" "data_nodes" {
  cluster_name    = aws_eks_cluster.payment_cluster.name
  node_group_name = "${local.cluster_name}-data"
  node_role_arn   = aws_iam_role.eks_node_role.arn
  subnet_ids      = var.subnet_ids.data
  instance_types  = var.kubernetes_node_groups.data.instance_types

  scaling_config {
    desired_size = var.kubernetes_node_groups.data.scaling_config.desired_size
    min_size     = var.kubernetes_node_groups.data.scaling_config.min_size
    max_size     = var.kubernetes_node_groups.data.scaling_config.max_size
  }

  labels = {
    role        = "data"
    environment = var.environment
  }

  # Ensure IAM role is created before node group
  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.ecr_read_only
  ]

  tags = merge(var.tags, local.kubernetes_tags, {
    Name = "${local.cluster_name}-data-node-group"
  })
}

# EKS Node Group for infrastructure workloads (monitoring, logging)
resource "aws_eks_node_group" "infrastructure_nodes" {
  cluster_name    = aws_eks_cluster.payment_cluster.name
  node_group_name = "${local.cluster_name}-infrastructure"
  node_role_arn   = aws_iam_role.eks_node_role.arn
  subnet_ids      = var.subnet_ids.private
  instance_types  = var.kubernetes_node_groups.infrastructure.instance_types

  scaling_config {
    desired_size = var.kubernetes_node_groups.infrastructure.scaling_config.desired_size
    min_size     = var.kubernetes_node_groups.infrastructure.scaling_config.min_size
    max_size     = var.kubernetes_node_groups.infrastructure.scaling_config.max_size
  }

  labels = {
    role        = "infrastructure"
    environment = var.environment
  }

  # Ensure IAM role is created before node group
  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.ecr_read_only
  ]

  tags = merge(var.tags, local.kubernetes_tags, {
    Name = "${local.cluster_name}-infrastructure-node-group"
  })
}

# ------------------------------------------------------------------------------
# IAM Roles and Policies
# ------------------------------------------------------------------------------

# IAM Role for EKS Cluster
resource "aws_iam_role" "eks_cluster_role" {
  name = "${local.cluster_name}-cluster-role"

  assume_role_policy = data.aws_iam_policy_document.eks_cluster_assume_role.json

  tags = merge(var.tags, local.kubernetes_tags, {
    Name = "${local.cluster_name}-cluster-role"
  })
}

# IAM Policy Document for EKS Cluster assume role
data "aws_iam_policy_document" "eks_cluster_assume_role" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

# Attach AmazonEKSClusterPolicy to the cluster role
resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  role       = aws_iam_role.eks_cluster_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# IAM Role for EKS Node Groups
resource "aws_iam_role" "eks_node_role" {
  name = "${local.cluster_name}-node-role"

  assume_role_policy = data.aws_iam_policy_document.eks_node_assume_role.json

  tags = merge(var.tags, local.kubernetes_tags, {
    Name = "${local.cluster_name}-node-role"
  })
}

# IAM Policy Document for EKS Node assume role
data "aws_iam_policy_document" "eks_node_assume_role" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

# Attach required policies to the node role
resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  role       = aws_iam_role.eks_node_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  role       = aws_iam_role.eks_node_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "ecr_read_only" {
  role       = aws_iam_role.eks_node_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# ------------------------------------------------------------------------------
# Security Groups
# ------------------------------------------------------------------------------

# Security Group for EKS Cluster
resource "aws_security_group" "eks_cluster_sg" {
  name        = "${local.cluster_name}-cluster-sg"
  description = "Security group for EKS cluster control plane"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, local.kubernetes_tags, {
    Name = "${local.cluster_name}-cluster-sg"
  })
}

# Security Group Rule for EKS Cluster
resource "aws_security_group_rule" "eks_cluster_ingress" {
  security_group_id = aws_security_group.eks_cluster_sg.id
  type              = "ingress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = [var.vpc_cidr]
}

# ------------------------------------------------------------------------------
# Kubernetes Resources
# ------------------------------------------------------------------------------

# Namespace for Payment API Security Enhancement components
resource "kubernetes_namespace" "payment_system" {
  metadata {
    name = "payment-system"
    labels = {
      name        = "payment-system"
      environment = var.environment
    }
    annotations = {
      description = "Namespace for Payment API Security Enhancement components"
    }
  }

  depends_on = [
    aws_eks_cluster.payment_cluster,
    aws_eks_node_group.application_nodes,
    aws_eks_node_group.data_nodes,
    aws_eks_node_group.infrastructure_nodes
  ]
}

# Service Account for Payment-EAPI
resource "kubernetes_service_account" "eapi_service_account" {
  metadata {
    name      = "payment-eapi-sa"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
    labels = {
      app         = "payment-eapi"
      environment = var.environment
    }
  }
}

# Service Account for Payment-SAPI
resource "kubernetes_service_account" "sapi_service_account" {
  metadata {
    name      = "payment-sapi-sa"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
    labels = {
      app         = "payment-sapi"
      environment = var.environment
    }
  }
}

# Role for Payment-EAPI
resource "kubernetes_role" "eapi_role" {
  metadata {
    name      = "payment-eapi-role"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  rule {
    api_groups = [""]
    resources  = ["secrets", "configmaps"]
    verbs      = ["get", "list"]
  }
}

# Role for Payment-SAPI
resource "kubernetes_role" "sapi_role" {
  metadata {
    name      = "payment-sapi-role"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  rule {
    api_groups = [""]
    resources  = ["secrets", "configmaps"]
    verbs      = ["get", "list"]
  }
}

# RoleBinding for Payment-EAPI
resource "kubernetes_role_binding" "eapi_role_binding" {
  metadata {
    name      = "payment-eapi-role-binding"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role.eapi_role.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.eapi_service_account.metadata[0].name
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }
}

# RoleBinding for Payment-SAPI
resource "kubernetes_role_binding" "sapi_role_binding" {
  metadata {
    name      = "payment-sapi-role-binding"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role.sapi_role.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.sapi_service_account.metadata[0].name
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }
}

# ------------------------------------------------------------------------------
# Network Policies
# ------------------------------------------------------------------------------

# Network Policy for Payment-EAPI
resource "kubernetes_network_policy" "eapi_network_policy" {
  metadata {
    name      = "payment-eapi-network-policy"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = {
        app = "payment-eapi"
      }
    }

    ingress {
      from {
        namespace_selector {
          match_labels = {
            name = "kube-system"
          }
        }
      }

      ports {
        port     = 8080
        protocol = "TCP"
      }

      ports {
        port     = 8081
        protocol = "TCP"
      }
    }

    egress {
      to {
        pod_selector {
          match_labels = {
            app = "payment-sapi"
          }
        }
      }

      ports {
        port     = 8081
        protocol = "TCP"
      }
    }

    egress {
      to {
        pod_selector {
          match_labels = {
            app = "redis"
          }
        }
      }

      ports {
        port     = 6379
        protocol = "TCP"
      }
    }

    egress {
      to {
        pod_selector {
          match_labels = {
            app = "conjur"
          }
        }
      }

      ports {
        port     = 443
        protocol = "TCP"
      }
    }

    policy_types = ["Ingress", "Egress"]
  }
}

# Network Policy for Payment-SAPI
resource "kubernetes_network_policy" "sapi_network_policy" {
  metadata {
    name      = "payment-sapi-network-policy"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = {
        app = "payment-sapi"
      }
    }

    ingress {
      from {
        pod_selector {
          match_labels = {
            app = "payment-eapi"
          }
        }
      }

      ports {
        port     = 8081
        protocol = "TCP"
      }
    }

    egress {
      to {
        pod_selector {
          match_labels = {
            app = "redis"
          }
        }
      }

      ports {
        port     = 6379
        protocol = "TCP"
      }
    }

    egress {
      to {
        pod_selector {
          match_labels = {
            app = "conjur"
          }
        }
      }

      ports {
        port     = 443
        protocol = "TCP"
      }
    }

    policy_types = ["Ingress", "Egress"]
  }
}

# ------------------------------------------------------------------------------
# Auto-scaling Configuration
# ------------------------------------------------------------------------------

# Horizontal Pod Autoscaler for Payment-EAPI
resource "kubernetes_horizontal_pod_autoscaler" "eapi_hpa" {
  metadata {
    name      = "payment-eapi-hpa"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  spec {
    scale_target_ref {
      api_version = "apps/v1"
      kind        = "Deployment"
      name        = "payment-eapi"
    }
    
    min_replicas = var.eapi_replicas.min
    max_replicas = var.eapi_replicas.max
    
    metric {
      type = "Resource"
      resource {
        name = "cpu"
        target {
          type               = "Utilization"
          average_utilization = 70
        }
      }
    }
  }
}

# Horizontal Pod Autoscaler for Payment-SAPI
resource "kubernetes_horizontal_pod_autoscaler" "sapi_hpa" {
  metadata {
    name      = "payment-sapi-hpa"
    namespace = kubernetes_namespace.payment_system.metadata[0].name
  }

  spec {
    scale_target_ref {
      api_version = "apps/v1"
      kind        = "Deployment"
      name        = "payment-sapi"
    }
    
    min_replicas = var.sapi_replicas.min
    max_replicas = var.sapi_replicas.max
    
    metric {
      type = "Resource"
      resource {
        name = "cpu"
        target {
          type               = "Utilization"
          average_utilization = 70
        }
      }
    }
  }
}

# ------------------------------------------------------------------------------
# Helm Charts Installation
# ------------------------------------------------------------------------------

# Metrics Server - Required for HPA functionality
resource "helm_release" "metrics_server" {
  name       = "metrics-server"
  repository = "https://kubernetes-sigs.github.io/metrics-server/"
  chart      = "metrics-server"
  namespace  = "kube-system"
  version    = "3.8.2"

  set {
    name  = "args[0]"
    value = "--kubelet-preferred-address-types=InternalIP"
  }

  set {
    name  = "args[1]"
    value = "--kubelet-insecure-tls"
  }

  depends_on = [
    aws_eks_cluster.payment_cluster,
    aws_eks_node_group.application_nodes,
    aws_eks_node_group.data_nodes,
    aws_eks_node_group.infrastructure_nodes
  ]
}

# ------------------------------------------------------------------------------
# Outputs
# ------------------------------------------------------------------------------

output "kubernetes_cluster_name" {
  description = "Name of the EKS cluster"
  value       = aws_eks_cluster.payment_cluster.name
}

output "kubernetes_cluster_endpoint" {
  description = "Endpoint for the EKS cluster"
  value       = aws_eks_cluster.payment_cluster.endpoint
}

output "kubernetes_cluster_certificate_authority_data" {
  description = "Certificate authority data for the EKS cluster"
  value       = aws_eks_cluster.payment_cluster.certificate_authority[0].data
}

output "kubernetes_namespace" {
  description = "Kubernetes namespace for the payment system"
  value       = kubernetes_namespace.payment_system.metadata[0].name
}