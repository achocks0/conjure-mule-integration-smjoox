# ------------------------------------------------------------------------------
# Payment API Security Enhancement - Monitoring Infrastructure
# ------------------------------------------------------------------------------
# This Terraform configuration provisions and manages the monitoring infrastructure
# for the Payment API Security Enhancement project. It defines resources for 
# Prometheus, Grafana, AlertManager, and ELK stack to provide comprehensive 
# monitoring, alerting, and logging capabilities.
# ------------------------------------------------------------------------------

# Define local variables for configuration
locals {
  # Chart versions for consistent deployment
  chart_versions = {
    prometheus        = "15.10.1"
    grafana           = "6.32.2"
    elasticsearch     = "7.17.3"
    logstash          = "7.17.3"
    kibana            = "7.17.3"
    filebeat          = "7.17.3"
    prometheus_adapter = "3.3.1"
  }
  
  # Storage configuration based on environment
  prometheus_storage_size     = var.environment == "prod" ? "50Gi" : "20Gi"
  grafana_storage_size        = var.environment == "prod" ? "10Gi" : "5Gi"
  elasticsearch_storage_size  = var.environment == "prod" ? "30Gi" : "10Gi"
  elasticsearch_replicas      = var.environment == "prod" ? 3 : 1
  logstash_replicas           = var.environment == "prod" ? 2 : 1
  
  # Common tags for monitoring resources
  monitoring_tags = {
    Component = "monitoring"
    Service   = "infrastructure"
    ManagedBy = "terraform"
  }
}

# Create monitoring namespace if monitoring is enabled
resource "kubernetes_namespace" "monitoring_namespace" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name = "monitoring"
    
    labels = {
      name        = "monitoring"
      environment = var.environment
      managed-by  = "terraform"
    }
    
    annotations = {
      description = "Namespace for monitoring components"
    }
  }
}

# Generate random password for Grafana if not provided
resource "random_password" "grafana_password" {
  count   = var.monitoring_enabled && var.grafana_admin_password == "" ? 1 : 0
  length  = 16
  special = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Create Grafana admin credentials secret
resource "kubernetes_secret" "grafana_admin_credentials" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "grafana-admin-credentials"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "grafana"
      environment = var.environment
    }
  }
  
  data = {
    admin-user     = "YWRtaW4=" # "admin" in base64
    admin-password = var.grafana_admin_password != "" ? base64encode(var.grafana_admin_password) : base64encode(random_password.grafana_password[0].result)
  }
  
  type = "Opaque"
}

# Create AlertManager configuration secret
resource "kubernetes_secret" "alertmanager_config" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "alertmanager-config"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "alertmanager"
      environment = var.environment
    }
  }
  
  data = {
    "alertmanager.yml" = base64encode(templatefile("${path.module}/templates/alertmanager.yml.tpl", {
      slack_webhook_url    = var.slack_webhook_url
      pagerduty_service_key = var.pagerduty_service_key
      environment          = var.environment
    }))
  }
  
  type = "Opaque"
}

# Create Prometheus configuration
resource "kubernetes_config_map" "prometheus_config" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "prometheus-config"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "prometheus"
      environment = var.environment
    }
  }
  
  data = {
    "prometheus.yml" = templatefile("${path.module}/templates/prometheus.yml.tpl", {
      environment         = var.environment
      kubernetes_namespace = var.kubernetes_namespace
    })
    "alert-rules.yml" = file("${path.module}/templates/alert-rules.yml")
  }
}

# Create Grafana dashboards
resource "kubernetes_config_map" "grafana_dashboards" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "grafana-dashboards"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "grafana"
      environment = var.environment
    }
  }
  
  data = {
    "security-dashboard.json"        = file("${path.module}/templates/dashboards/security-dashboard.json")
    "service-health-dashboard.json"  = file("${path.module}/templates/dashboards/service-health-dashboard.json")
    "authentication-dashboard.json"  = file("${path.module}/templates/dashboards/authentication-dashboard.json")
  }
}

# Create Grafana datasources
resource "kubernetes_config_map" "grafana_datasources" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "grafana-datasources"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "grafana"
      environment = var.environment
    }
  }
  
  data = {
    "datasources.yaml" = templatefile("${path.module}/templates/datasources.yaml.tpl", {
      environment = var.environment
    })
  }
}

# Create Filebeat configuration
resource "kubernetes_config_map" "filebeat_config" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "filebeat-config"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "filebeat"
      environment = var.environment
    }
  }
  
  data = {
    "filebeat.yml" = templatefile("${path.module}/templates/filebeat.yml.tpl", {
      environment         = var.environment
      kubernetes_namespace = var.kubernetes_namespace
    })
  }
}

# Create Logstash configuration
resource "kubernetes_config_map" "logstash_config" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "logstash-config"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "logstash"
      environment = var.environment
    }
  }
  
  data = {
    "logstash.conf" = file("${path.module}/templates/logstash.conf")
  }
}

# Install Prometheus with Helm
resource "helm_release" "prometheus" {
  count      = var.monitoring_enabled ? 1 : 0
  name       = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus"
  version    = local.chart_versions.prometheus
  namespace  = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
  
  values = [
    file("${path.module}/templates/prometheus-values.yaml")
  ]
  
  set {
    name  = "server.persistentVolume.size"
    value = local.prometheus_storage_size
  }
  
  set {
    name  = "server.retention"
    value = "${var.prometheus_retention_days}d"
  }
  
  set {
    name  = "server.configMapOverrideName"
    value = "prometheus-config"
  }
  
  set {
    name  = "alertmanager.enabled"
    value = "true"
  }
  
  set {
    name  = "alertmanager.configMapOverrideName"
    value = "alertmanager-config"
  }
  
  depends_on = [
    kubernetes_config_map.prometheus_config,
    kubernetes_secret.alertmanager_config
  ]
}

# Install Grafana with Helm
resource "helm_release" "grafana" {
  count      = var.monitoring_enabled ? 1 : 0
  name       = "grafana"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "grafana"
  version    = local.chart_versions.grafana
  namespace  = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
  
  values = [
    file("${path.module}/templates/grafana-values.yaml")
  ]
  
  set {
    name  = "admin.existingSecret"
    value = "grafana-admin-credentials"
  }
  
  set {
    name  = "persistence.enabled"
    value = "true"
  }
  
  set {
    name  = "persistence.size"
    value = local.grafana_storage_size
  }
  
  set {
    name  = "dashboards.default.security-dashboard.json"
    value = "true"
  }
  
  set {
    name  = "dashboards.default.service-health-dashboard.json"
    value = "true"
  }
  
  set {
    name  = "dashboards.default.authentication-dashboard.json"
    value = "true"
  }
  
  depends_on = [
    kubernetes_secret.grafana_admin_credentials,
    kubernetes_config_map.grafana_dashboards,
    kubernetes_config_map.grafana_datasources,
    helm_release.prometheus
  ]
}

# Install Elasticsearch with Helm
resource "helm_release" "elasticsearch" {
  count      = var.monitoring_enabled ? 1 : 0
  name       = "elasticsearch"
  repository = "https://helm.elastic.co"
  chart      = "elasticsearch"
  version    = local.chart_versions.elasticsearch
  namespace  = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
  
  values = [
    file("${path.module}/templates/elasticsearch-values.yaml")
  ]
  
  set {
    name  = "replicas"
    value = local.elasticsearch_replicas
  }
  
  set {
    name  = "volumeClaimTemplate.resources.requests.storage"
    value = local.elasticsearch_storage_size
  }
  
  set {
    name  = "resources.requests.cpu"
    value = "500m"
  }
  
  set {
    name  = "resources.requests.memory"
    value = "1Gi"
  }
  
  set {
    name  = "resources.limits.cpu"
    value = "1000m"
  }
  
  set {
    name  = "resources.limits.memory"
    value = "2Gi"
  }
}

# Install Logstash with Helm
resource "helm_release" "logstash" {
  count      = var.monitoring_enabled ? 1 : 0
  name       = "logstash"
  repository = "https://helm.elastic.co"
  chart      = "logstash"
  version    = local.chart_versions.logstash
  namespace  = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
  
  values = [
    file("${path.module}/templates/logstash-values.yaml")
  ]
  
  set {
    name  = "replicas"
    value = local.logstash_replicas
  }
  
  set {
    name  = "logstashConfig"
    value = "logstash-config"
  }
  
  set {
    name  = "resources.requests.cpu"
    value = "300m"
  }
  
  set {
    name  = "resources.requests.memory"
    value = "512Mi"
  }
  
  set {
    name  = "resources.limits.cpu"
    value = "500m"
  }
  
  set {
    name  = "resources.limits.memory"
    value = "1Gi"
  }
  
  depends_on = [
    kubernetes_config_map.logstash_config,
    helm_release.elasticsearch
  ]
}

# Install Kibana with Helm
resource "helm_release" "kibana" {
  count      = var.monitoring_enabled ? 1 : 0
  name       = "kibana"
  repository = "https://helm.elastic.co"
  chart      = "kibana"
  version    = local.chart_versions.kibana
  namespace  = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
  
  values = [
    file("${path.module}/templates/kibana-values.yaml")
  ]
  
  set {
    name  = "elasticsearchHosts"
    value = "http://elasticsearch-master:9200"
  }
  
  set {
    name  = "resources.requests.cpu"
    value = "200m"
  }
  
  set {
    name  = "resources.requests.memory"
    value = "512Mi"
  }
  
  set {
    name  = "resources.limits.cpu"
    value = "500m"
  }
  
  set {
    name  = "resources.limits.memory"
    value = "1Gi"
  }
  
  depends_on = [
    helm_release.elasticsearch
  ]
}

# Install Filebeat with Helm
resource "helm_release" "filebeat" {
  count      = var.monitoring_enabled ? 1 : 0
  name       = "filebeat"
  repository = "https://helm.elastic.co"
  chart      = "filebeat"
  version    = local.chart_versions.filebeat
  namespace  = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
  
  values = [
    file("${path.module}/templates/filebeat-values.yaml")
  ]
  
  set {
    name  = "filebeatConfig.filebeat\\.yml"
    value = kubernetes_config_map.filebeat_config[0].data["filebeat.yml"]
  }
  
  set {
    name  = "resources.requests.cpu"
    value = "100m"
  }
  
  set {
    name  = "resources.requests.memory"
    value = "256Mi"
  }
  
  set {
    name  = "resources.limits.cpu"
    value = "300m"
  }
  
  set {
    name  = "resources.limits.memory"
    value = "512Mi"
  }
  
  depends_on = [
    kubernetes_config_map.filebeat_config,
    helm_release.logstash
  ]
}

# Install Prometheus Adapter with Helm
resource "helm_release" "prometheus_adapter" {
  count      = var.monitoring_enabled ? 1 : 0
  name       = "prometheus-adapter"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus-adapter"
  version    = local.chart_versions.prometheus_adapter
  namespace  = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
  
  values = [
    file("${path.module}/templates/prometheus-adapter-values.yaml")
  ]
  
  set {
    name  = "prometheus.url"
    value = "http://prometheus-server"
  }
  
  set {
    name  = "prometheus.port"
    value = "80"
  }
  
  depends_on = [
    helm_release.prometheus
  ]
}

# Create service for external Prometheus access
resource "kubernetes_service" "prometheus_service" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "prometheus-external"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "prometheus"
      environment = var.environment
    }
  }
  
  spec {
    selector = {
      app       = "prometheus"
      component = "server"
    }
    
    port {
      port        = 80
      target_port = 9090
      protocol    = "TCP"
      name        = "http"
    }
    
    type = "LoadBalancer"
  }
  
  depends_on = [
    helm_release.prometheus
  ]
}

# Create service for external Grafana access
resource "kubernetes_service" "grafana_service" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "grafana-external"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "grafana"
      environment = var.environment
    }
  }
  
  spec {
    selector = {
      "app.kubernetes.io/name" = "grafana"
    }
    
    port {
      port        = 80
      target_port = 3000
      protocol    = "TCP"
      name        = "http"
    }
    
    type = "LoadBalancer"
  }
  
  depends_on = [
    helm_release.grafana
  ]
}

# Create service for external AlertManager access
resource "kubernetes_service" "alertmanager_service" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "alertmanager-external"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "alertmanager"
      environment = var.environment
    }
  }
  
  spec {
    selector = {
      app       = "prometheus"
      component = "alertmanager"
    }
    
    port {
      port        = 80
      target_port = 9093
      protocol    = "TCP"
      name        = "http"
    }
    
    type = "LoadBalancer"
  }
  
  depends_on = [
    helm_release.prometheus
  ]
}

# Create service for external Kibana access
resource "kubernetes_service" "kibana_service" {
  count = var.monitoring_enabled ? 1 : 0
  
  metadata {
    name      = "kibana-external"
    namespace = var.monitoring_enabled ? kubernetes_namespace.monitoring_namespace[0].metadata[0].name : "monitoring"
    
    labels = {
      app         = "kibana"
      environment = var.environment
    }
  }
  
  spec {
    selector = {
      app = "kibana"
    }
    
    port {
      port        = 80
      target_port = 5601
      protocol    = "TCP"
      name        = "http"
    }
    
    type = "LoadBalancer"
  }
  
  depends_on = [
    helm_release.kibana
  ]
}

# Output URLs for monitoring services
output "prometheus_url" {
  description = "URL for accessing Prometheus"
  value       = var.monitoring_enabled ? kubernetes_service.prometheus_service[0].status[0].load_balancer[0].ingress[0].hostname : ""
}

output "grafana_url" {
  description = "URL for accessing Grafana"
  value       = var.monitoring_enabled ? kubernetes_service.grafana_service[0].status[0].load_balancer[0].ingress[0].hostname : ""
}

output "alertmanager_url" {
  description = "URL for accessing AlertManager"
  value       = var.monitoring_enabled ? kubernetes_service.alertmanager_service[0].status[0].load_balancer[0].ingress[0].hostname : ""
}

output "kibana_url" {
  description = "URL for accessing Kibana"
  value       = var.monitoring_enabled ? kubernetes_service.kibana_service[0].status[0].load_balancer[0].ingress[0].hostname : ""
}