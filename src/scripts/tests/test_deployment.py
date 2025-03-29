#!/usr/bin/env python3
"""
Test module for deployment functionality in the Payment API Security Enhancement project.
Contains unit and integration tests for environment setup, infrastructure provisioning,
Kubernetes deployment, and service verification operations.
"""

import os
import json
import pytest
import tempfile
from unittest.mock import MagicMock, patch, mock_open

from src.scripts.deployment.config import (
    ENVIRONMENTS, 
    DeploymentConfig, 
    create_deployment_config
)
from src.scripts.deployment.utils import (
    TerraformDeployer,
    KubernetesDeployer,
    check_service_health,
    DeploymentError,
    validate_environment
)
from src.scripts.deployment.setup_environments import (
    setup_environment,
    setup_infrastructure,
    setup_kubernetes,
    verify_environment
)


@pytest.mark.unit
def test_validate_environment():
    """Tests the validate_environment function"""
    # Test with valid environment names from ENVIRONMENTS list
    for env in ENVIRONMENTS:
        assert validate_environment(env) is True
    
    # Test with invalid environment names
    assert validate_environment("invalid_env") is False
    assert validate_environment("") is False
    assert validate_environment(None) is False


@pytest.mark.unit
def test_deployment_config_creation():
    """Tests the creation of a DeploymentConfig instance"""
    # Create a DeploymentConfig with test parameters
    config = DeploymentConfig(
        environment="development",
        kubernetes_namespace="payment-dev",
        kubernetes_context="dev-context",
        terraform_dir="/path/to/terraform",
        service_urls={"payment-eapi": "http://example.com"},
        notification_channels={"slack": {"webhook_url": "http://slack.com"}},
        backup_dir="/path/to/backup",
        deployment_timeout=600,
        rollback_on_failure=True,
        additional_config={"test_key": "test_value"}
    )
    
    # Verify that all attributes are set correctly
    assert config.environment == "development"
    assert config.kubernetes_namespace == "payment-dev"
    assert config.kubernetes_context == "dev-context"
    assert config.terraform_dir == "/path/to/terraform"
    assert config.service_urls == {"payment-eapi": "http://example.com"}
    assert config.notification_channels == {"slack": {"webhook_url": "http://slack.com"}}
    assert config.backup_dir == "/path/to/backup"
    assert config.deployment_timeout == 600
    assert config.rollback_on_failure is True
    assert config.additional_config == {"test_key": "test_value"}
    
    # Verify that the validate method returns True for valid configuration
    assert config.validate() is True
    
    # Create a DeploymentConfig with missing required attributes
    invalid_config = DeploymentConfig(
        environment="invalid_env"
    )
    # Verify that the validate method returns False for invalid configuration
    assert invalid_config.validate() is False


@pytest.mark.unit
def test_create_deployment_config(tmp_path):
    """Tests the create_deployment_config function"""
    # Create a temporary configuration file with test parameters
    config_file = tmp_path / "test_config.json"
    config_data = {
        "development": {
            "kubernetes_namespace": "payment-dev",
            "terraform_dir": "/path/to/terraform",
            "service_urls": {
                "payment-eapi": "http://payment-eapi-dev.example.com",
                "payment-sapi": "http://payment-sapi-dev.example.com",
                "conjur": "http://conjur-dev.example.com"
            }
        }
    }
    
    with open(config_file, 'w') as f:
        json.dump(config_data, f)
    
    # Call create_deployment_config with the file path and environment
    config = create_deployment_config("development", str(config_file))
    
    # Verify that the returned DeploymentConfig has the correct attributes
    assert config.environment == "development"
    assert config.kubernetes_namespace == "payment-dev"
    assert config.terraform_dir == "/path/to/terraform"
    assert config.service_urls == {
        "payment-eapi": "http://payment-eapi-dev.example.com",
        "payment-sapi": "http://payment-sapi-dev.example.com",
        "conjur": "http://conjur-dev.example.com"
    }
    
    # Create a file with invalid configuration
    invalid_config_file = tmp_path / "invalid_config.json"
    with open(invalid_config_file, 'w') as f:
        f.write("invalid json")
    
    # Verify that appropriate error handling occurs
    config = create_deployment_config("development", str(invalid_config_file))
    assert config.environment == "development"
    # Default values should be used for invalid configuration


@pytest.mark.unit
def test_terraform_deployer(tmp_path):
    """Tests the TerraformDeployer class"""
    # Create a temporary directory for Terraform files
    tf_dir = tmp_path / "terraform"
    tf_dir.mkdir()
    
    # Create a TerraformDeployer instance with the directory
    deployer = TerraformDeployer(
        terraform_dir=str(tf_dir),
        var_file="vars.tfvars",
        variables={"var1": "value1"},
        backend_config={"bucket": "tf-state"},
        auto_approve=True
    )
    
    # Verify attribute values
    assert deployer.terraform_dir == str(tf_dir)
    assert deployer.var_file == "vars.tfvars"
    assert deployer.variables == {"var1": "value1"}
    assert deployer.backend_config == {"bucket": "tf-state"}
    assert deployer.auto_approve is True
    
    # Mock the run_command function to simulate Terraform commands
    with patch("src.scripts.deployment.utils.run_command") as mock_run:
        # Mock successful return values
        mock_run.return_value = (0, "terraform output", "")
        
        # Test the init method with various parameters
        assert deployer.init() is True
        mock_run.assert_called_once()
        args, _ = mock_run.call_args
        assert args[0][0] == "terraform"
        assert args[0][1] == "init"
        assert "-backend-config" in args[0]
        
        # Reset mock for next test
        mock_run.reset_mock()
        
        # Test the apply method with various parameters
        assert deployer.apply() is True
        mock_run.assert_called_once()
        args, _ = mock_run.call_args
        assert args[0][0] == "terraform"
        assert args[0][1] == "apply"
        assert "-auto-approve" in args[0]
        
        # Reset mock for next test
        mock_run.reset_mock()
        
        # Test the destroy method with various parameters
        assert deployer.destroy() is True
        mock_run.assert_called_once()
        args, _ = mock_run.call_args
        assert args[0][0] == "terraform"
        assert args[0][1] == "destroy"
        
        # Reset mock for next test
        mock_run.reset_mock()
        
        # Test the get_outputs method with various parameters
        mock_run.return_value = (0, '{"output1": {"value": "value1"}}', "")
        outputs = deployer.get_outputs()
        assert outputs == {"output1": {"value": "value1"}}
        
        # Test get_outputs with specific output
        outputs = deployer.get_outputs("output1")
        assert outputs == "value1"
        
        # Test error handling
        mock_run.return_value = (1, "", "Error executing terraform")
        assert deployer.init() is False
        assert deployer.apply() is False
        assert deployer.destroy() is False
        assert deployer.get_outputs() == {}


@pytest.mark.unit
def test_kubernetes_deployer(tmp_path):
    """Tests the KubernetesDeployer class"""
    # Create temporary manifest files
    manifest_dir = tmp_path / "manifests"
    manifest_dir.mkdir()
    
    manifest_file1 = manifest_dir / "manifest1.yaml"
    manifest_file1.write_text("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test-config")
    
    manifest_file2 = manifest_dir / "manifest2.yaml"
    manifest_file2.write_text("apiVersion: v1\nkind: Secret\nmetadata:\n  name: test-secret")
    
    # Create a KubernetesDeployer instance with test parameters
    deployer = KubernetesDeployer(
        namespace="payment-dev",
        context="dev-context",
        manifest_files=[str(manifest_file1)],
        wait=True
    )
    
    # Verify attribute values
    assert deployer.namespace == "payment-dev"
    assert deployer.context == "dev-context"
    assert deployer.manifest_files == [str(manifest_file1)]
    assert deployer.wait is True
    
    # Test the add_manifest method
    deployer.add_manifest(str(manifest_file2))
    assert len(deployer.manifest_files) == 2
    assert str(manifest_file2) in deployer.manifest_files
    
    # Test the add_manifests_from_dir method
    with patch("src.scripts.deployment.utils.find_manifest_files") as mock_find:
        mock_find.return_value = [str(manifest_file1), str(manifest_file2)]
        
        # Both files should already be in the list, so should add 0 new files
        count = deployer.add_manifests_from_dir(str(manifest_dir))
        assert count == 0
        
        # Now mock a new file that should be added
        mock_find.return_value = [str(manifest_file1), str(manifest_file2), "/new/path/manifest3.yaml"]
        count = deployer.add_manifests_from_dir(str(manifest_dir))
        assert count == 1
        assert len(deployer.manifest_files) == 3
    
    # Test the deploy method with various parameters
    with patch("src.scripts.deployment.utils.kubectl_apply") as mock_apply:
        mock_apply.return_value = True
        
        # Should be successful if all manifests apply successfully
        assert deployer.deploy() is True
        assert mock_apply.call_count == 3
        
        # Reset mock
        mock_apply.reset_mock()
        
        # Test failure case
        mock_apply.return_value = False
        assert deployer.deploy() is False
    
    # Test the delete method with various parameters
    with patch("src.scripts.deployment.utils.kubectl_delete") as mock_delete:
        mock_delete.return_value = True
        
        # Should be successful if all manifests delete successfully
        assert deployer.delete() is True
        assert mock_delete.call_count == 3
        
        # Reset mock
        mock_delete.reset_mock()
        
        # Test failure case
        mock_delete.return_value = False
        assert deployer.delete() is False
    
    # Test the get_resources method with various parameters
    with patch("src.scripts.deployment.utils.kubectl_get") as mock_get:
        mock_get.return_value = {"items": [{"metadata": {"name": "test-pod"}}]}
        
        resources = deployer.get_resources("pods")
        assert resources == {"items": [{"metadata": {"name": "test-pod"}}]}
        mock_get.assert_called_once_with("pods", None, "payment-dev", "dev-context", "json")


@pytest.mark.unit
def test_setup_infrastructure():
    """Tests the setup_infrastructure function"""
    # Create a mock DeploymentConfig instance
    mock_config = MagicMock(spec=DeploymentConfig)
    mock_config.environment = "development"
    mock_config.terraform_dir = "/path/to/terraform"
    mock_config.additional_config = {
        "terraform_var_file": "vars.tfvars",
        "terraform_variables": {"var1": "value1"},
        "terraform_backend_config": {"bucket": "tf-state"},
        "terraform_auto_approve": True
    }
    
    # Mock the TerraformDeployer class and its methods
    mock_deployer = MagicMock(spec=TerraformDeployer)
    mock_deployer.init.return_value = True
    mock_deployer.apply.return_value = True
    mock_deployer.get_outputs.return_value = {"output1": "value1"}
    
    with patch("src.scripts.deployment.setup_environments.TerraformDeployer", return_value=mock_deployer) as mock_tf_deployer_class:
        # Call setup_infrastructure with the mock config
        result = setup_infrastructure(mock_config)
        
        # Verify that TerraformDeployer is initialized with correct parameters
        mock_tf_deployer_class.assert_called_once_with(
            terraform_dir="/path/to/terraform",
            var_file="vars.tfvars",
            variables={"var1": "value1"},
            backend_config={"bucket": "tf-state"},
            auto_approve=True
        )
        
        # Verify that init and apply methods are called
        mock_deployer.init.assert_called_once()
        mock_deployer.apply.assert_called_once()
        
        # Verify that get_outputs is called to retrieve outputs
        mock_deployer.get_outputs.assert_called_once()
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "success"
        assert result["details"]["outputs"] == {"output1": "value1"}
        
        # Test error handling when Terraform operations fail
        mock_deployer.init.return_value = False
        
        # Should return failed status if initialization fails
        result = setup_infrastructure(mock_config)
        assert result["status"] == "failed"
        
        # Reset mock for apply failure
        mock_deployer.init.return_value = True
        mock_deployer.apply.return_value = False
        
        # Should return failed status if apply fails
        result = setup_infrastructure(mock_config)
        assert result["status"] == "failed"


@pytest.mark.unit
def test_setup_kubernetes(tmp_path):
    """Tests the setup_kubernetes function"""
    # Create a mock DeploymentConfig instance
    mock_config = MagicMock(spec=DeploymentConfig)
    mock_config.environment = "development"
    mock_config.kubernetes_namespace = "payment-dev"
    mock_config.kubernetes_context = "dev-context"
    
    # Create temporary manifest files
    manifest_dir = tmp_path / "manifests"
    manifest_dir.mkdir()
    
    # Create environment-specific directory
    env_manifest_dir = manifest_dir / "development"
    env_manifest_dir.mkdir()
    
    # Create manifest files
    manifest_file = env_manifest_dir / "manifest.yaml"
    manifest_file.write_text("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test-config")
    
    # Mock the KubernetesDeployer class and its methods
    mock_deployer = MagicMock(spec=KubernetesDeployer)
    mock_deployer.add_manifests_from_dir.return_value = 1
    mock_deployer.deploy.return_value = True
    mock_deployer.get_resources.return_value = {"items": [{"metadata": {"name": "test-pod"}}]}
    
    with patch("src.scripts.deployment.setup_environments.KubernetesDeployer", return_value=mock_deployer) as mock_k8s_deployer_class:
        # Call setup_kubernetes with the mock config and manifest directory
        result = setup_kubernetes(mock_config, str(manifest_dir))
        
        # Verify that KubernetesDeployer is initialized with correct parameters
        mock_k8s_deployer_class.assert_called_once_with(
            namespace="payment-dev",
            context="dev-context",
            wait=True
        )
        
        # Verify that add_manifests_from_dir and deploy methods are called
        mock_deployer.add_manifests_from_dir.assert_called_once_with(str(env_manifest_dir))
        mock_deployer.deploy.assert_called_once()
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "success"
        assert result["details"]["manifest_count"] == 1
        
        # Test error handling when Kubernetes operations fail
        mock_deployer.add_manifests_from_dir.return_value = 0
        
        # Should return partial status if no manifests found
        result = setup_kubernetes(mock_config, str(manifest_dir))
        assert result["status"] == "partial"
        
        # Reset mock for deployment failure
        mock_deployer.add_manifests_from_dir.return_value = 1
        mock_deployer.deploy.return_value = False
        
        # Should return failed status if deployment fails
        result = setup_kubernetes(mock_config, str(manifest_dir))
        assert result["status"] == "failed"


@pytest.mark.unit
def test_verify_environment():
    """Tests the verify_environment function"""
    # Create a mock DeploymentConfig instance with service URLs
    mock_config = MagicMock(spec=DeploymentConfig)
    mock_config.environment = "development"
    mock_config.service_urls = {
        "payment-eapi": "http://payment-eapi-dev.example.com",
        "payment-sapi": "http://payment-sapi-dev.example.com",
        "conjur": "http://conjur-dev.example.com"
    }
    
    # Mock the check_service_health function to return different results
    with patch("src.scripts.deployment.setup_environments.check_service_health") as mock_check:
        # Test scenarios where all services are healthy
        mock_check.return_value = True
        
        result = verify_environment(mock_config)
        
        # Verify that check_service_health is called for each service
        assert mock_check.call_count == 3
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "success"
        assert result["details"]["services_health"] == {
            "payment-eapi": "healthy",
            "payment-sapi": "healthy",
            "conjur": "healthy"
        }
        
        # Test scenarios where some services are unhealthy
        mock_check.reset_mock()
        mock_check.side_effect = [True, False, True]
        
        result = verify_environment(mock_config)
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "partial"
        assert result["details"]["services_health"] == {
            "payment-eapi": "healthy",
            "payment-sapi": "unhealthy",
            "conjur": "healthy"
        }
        
        # Test scenarios where all services are unhealthy
        mock_check.reset_mock()
        mock_check.return_value = False
        
        result = verify_environment(mock_config)
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "partial"
        assert result["details"]["services_health"] == {
            "payment-eapi": "unhealthy",
            "payment-sapi": "unhealthy",
            "conjur": "unhealthy"
        }
        
        # Test scenarios where an exception occurs
        mock_check.reset_mock()
        mock_check.side_effect = Exception("Test error")
        
        result = verify_environment(mock_config)
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "failed"
        assert result["error"] == "Test error"


@pytest.mark.integration
def test_setup_environment_integration(tmp_path):
    """Integration test for the setup_environment function"""
    # Create a temporary configuration file
    config_file = tmp_path / "test_config.json"
    config_data = {
        "development": {
            "kubernetes_namespace": "payment-dev",
            "terraform_dir": str(tmp_path / "terraform"),
            "service_urls": {
                "payment-eapi": "http://payment-eapi-dev.example.com",
                "payment-sapi": "http://payment-sapi-dev.example.com",
                "conjur": "http://conjur-dev.example.com"
            }
        }
    }
    
    with open(config_file, 'w') as f:
        json.dump(config_data, f)
    
    # Create manifest directory
    manifest_dir = tmp_path / "manifests"
    manifest_dir.mkdir()
    
    # Mock the setup_infrastructure, setup_kubernetes, and verify_environment functions
    with patch("src.scripts.deployment.setup_environments.setup_infrastructure") as mock_setup_infra, \
         patch("src.scripts.deployment.setup_environments.setup_kubernetes") as mock_setup_k8s, \
         patch("src.scripts.deployment.setup_environments.setup_conjur_vault") as mock_setup_conjur, \
         patch("src.scripts.deployment.setup_environments.verify_environment") as mock_verify, \
         patch("src.scripts.deployment.setup_environments.send_notification") as mock_notify:
        
        # Mock successful results
        mock_setup_infra.return_value = {"status": "success", "details": {}}
        mock_setup_k8s.return_value = {"status": "success", "details": {}}
        mock_setup_conjur.return_value = True
        mock_verify.return_value = {"status": "success", "details": {}}
        
        # Call setup_environment with test parameters
        result = setup_environment(
            "development",
            str(config_file),
            manifest_dir=str(manifest_dir)
        )
        
        # Verify that each component setup function is called with correct parameters
        mock_setup_infra.assert_called_once()
        mock_setup_k8s.assert_called_once()
        mock_setup_conjur.assert_called_once()
        mock_verify.assert_called_once()
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "success"
        
        # Test different combinations of setup flags
        mock_setup_infra.reset_mock()
        mock_setup_k8s.reset_mock()
        mock_setup_conjur.reset_mock()
        mock_verify.reset_mock()
        
        result = setup_environment(
            "development",
            str(config_file),
            manifest_dir=str(manifest_dir),
            setup_infrastructure=False,
            setup_kubernetes=False
        )
        
        # Verify that only enabled components were called
        mock_setup_infra.assert_not_called()
        mock_setup_k8s.assert_not_called()
        mock_setup_conjur.assert_called_once()
        mock_verify.assert_called_once()
        
        # Test error handling when component setup fails
        mock_setup_infra.return_value = {"status": "failed", "details": {}, "error": "Infra error"}
        
        result = setup_environment(
            "development",
            str(config_file),
            manifest_dir=str(manifest_dir)
        )
        
        # Verify that the function returns the expected result dictionary
        assert result["status"] == "partial"
        assert len(result["errors"]) == 1
        assert result["errors"][0]["component"] == "infrastructure"


@pytest.mark.unit
def test_deployment_error_handling():
    """Tests handling of deployment errors"""
    # Create a DeploymentError with various parameters
    error = DeploymentError("Test error", "component", {"key": "value"})
    
    # Verify that the error attributes are set correctly
    assert str(error) == "Test error"
    assert error.message == "Test error"
    assert error.component == "component"
    assert error.details == {"key": "value"}
    
    # Test error message formatting
    error = DeploymentError("Another error")
    assert str(error) == "Another error"
    assert error.message == "Another error"
    assert error.component is None
    assert error.details == {}
    
    # Test error representation in string format
    assert repr(error).startswith("DeploymentError")


@pytest.mark.unit
def test_check_service_health():
    """Tests the check_service_health function"""
    # Mock HTTP responses for service health endpoints
    with patch("requests.get") as mock_get:
        # Test with a healthy service that returns 200 OK
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"status": "UP"}
        mock_get.return_value = mock_response
        
        result = check_service_health("http://example.com")
        assert result is True
        
        # Test with an unhealthy service that returns non-200 status
        mock_response.status_code = 500
        result = check_service_health("http://example.com")
        assert result is False
        
        # Test with a service that times out
        mock_get.side_effect = requests.exceptions.ConnectionError()
        result = check_service_health("http://example.com")
        assert result is False
        
        # Test with a service that returns unexpected response format
        mock_get.side_effect = None
        mock_response.status_code = 200
        mock_response.json.side_effect = ValueError()  # Not JSON
        mock_response.text = "Service is up"
        mock_get.return_value = mock_response
        
        result = check_service_health("http://example.com")
        assert result is True
        
        # Test retry behavior with temporary failures
        mock_get.side_effect = [
            requests.exceptions.ConnectionError(),  # First attempt fails
            mock_response  # Second attempt succeeds
        ]
        
        result = check_service_health("http://example.com", retries=3)
        assert result is True