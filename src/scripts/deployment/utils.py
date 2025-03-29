"""
Utility module providing common functions and classes for deployment operations in the Payment API Security Enhancement project.
This module includes functions for executing commands, interacting with Terraform and Kubernetes, checking service health, and sending notifications.
"""

import os
import sys
import subprocess
import time
import json
import glob
import requests
import yaml

from config import LOGGER, ENVIRONMENTS, DEFAULT_DEPLOYMENT_TIMEOUT

# Command execution timeout in seconds
COMMAND_TIMEOUT = int(os.environ.get('COMMAND_TIMEOUT', '300'))

# Paths to binaries
TERRAFORM_BIN = os.environ.get('TERRAFORM_BIN', 'terraform')
KUBECTL_BIN = os.environ.get('KUBECTL_BIN', 'kubectl')

# Health check configuration
HEALTH_CHECK_TIMEOUT = int(os.environ.get('HEALTH_CHECK_TIMEOUT', '60'))
HEALTH_CHECK_RETRIES = int(os.environ.get('HEALTH_CHECK_RETRIES', '3'))


def run_command(command, cwd=None, timeout=COMMAND_TIMEOUT, capture_output=True):
    """
    Executes a shell command and returns the result
    
    Args:
        command (list): Command to execute as a list of strings
        cwd (str): Working directory for command execution
        timeout (int): Command execution timeout in seconds
        capture_output (bool): Whether to capture stdout and stderr
        
    Returns:
        tuple: Tuple containing (return_code, stdout, stderr)
    """
    LOGGER.debug(f"Executing command: {' '.join(command)}")
    
    try:
        # Execute command with subprocess.run
        result = subprocess.run(
            command,
            cwd=cwd,
            timeout=timeout,
            check=False,
            capture_output=capture_output,
            text=True
        )
        
        # Get stdout and stderr if capture_output is True
        stdout = result.stdout if capture_output else None
        stderr = result.stderr if capture_output else None
        
        LOGGER.debug(f"Command completed with return code: {result.returncode}")
        
        return result.returncode, stdout, stderr
    
    except subprocess.TimeoutExpired:
        LOGGER.error(f"Command timed out after {timeout} seconds: {' '.join(command)}")
        return 124, None, f"Command timed out after {timeout} seconds"
    
    except Exception as e:
        LOGGER.error(f"Error executing command: {' '.join(command)}, Error: {str(e)}")
        return 1, None, str(e)


def terraform_init(terraform_dir, backend_config=None, reconfigure=False):
    """
    Initializes a Terraform working directory
    
    Args:
        terraform_dir (str): Path to Terraform directory
        backend_config (dict): Backend configuration variables
        reconfigure (bool): Whether to reconfigure the backend
        
    Returns:
        bool: True if initialization was successful, False otherwise
    """
    # Construct command as a list
    command = [TERRAFORM_BIN, "init"]
    
    # Add reconfigure flag if specified
    if reconfigure:
        command.append("-reconfigure")
    
    # Add backend-config options
    if backend_config:
        for key, value in backend_config.items():
            command.extend(["-backend-config", f"{key}={value}"])
    
    # Execute terraform init command
    return_code, stdout, stderr = run_command(command, cwd=terraform_dir)
    
    # Check if command was successful
    if return_code == 0:
        LOGGER.info(f"Terraform initialization successful in {terraform_dir}")
        return True
    else:
        LOGGER.error(f"Terraform initialization failed in {terraform_dir}: {stderr}")
        return False


def terraform_apply(terraform_dir, var_file=None, variables=None, auto_approve=False):
    """
    Applies a Terraform configuration
    
    Args:
        terraform_dir (str): Path to Terraform directory
        var_file (str): Path to Terraform variable file
        variables (dict): Terraform variables
        auto_approve (bool): Whether to auto-approve the apply
        
    Returns:
        bool: True if apply was successful, False otherwise
    """
    # Construct command as a list
    command = [TERRAFORM_BIN, "apply"]
    
    # Add auto-approve flag if specified
    if auto_approve:
        command.append("-auto-approve")
    
    # Add var-file if specified
    if var_file:
        command.extend(["-var-file", var_file])
    
    # Add variables
    if variables:
        for key, value in variables.items():
            command.extend(["-var", f"{key}={value}"])
    
    # Execute terraform apply command
    return_code, stdout, stderr = run_command(command, cwd=terraform_dir)
    
    # Check if command was successful
    if return_code == 0:
        LOGGER.info(f"Terraform apply successful in {terraform_dir}")
        return True
    else:
        LOGGER.error(f"Terraform apply failed in {terraform_dir}: {stderr}")
        return False


def terraform_destroy(terraform_dir, var_file=None, variables=None, auto_approve=False):
    """
    Destroys resources created by Terraform
    
    Args:
        terraform_dir (str): Path to Terraform directory
        var_file (str): Path to Terraform variable file
        variables (dict): Terraform variables
        auto_approve (bool): Whether to auto-approve the destroy
        
    Returns:
        bool: True if destroy was successful, False otherwise
    """
    # Construct command as a list
    command = [TERRAFORM_BIN, "destroy"]
    
    # Add auto-approve flag if specified
    if auto_approve:
        command.append("-auto-approve")
    
    # Add var-file if specified
    if var_file:
        command.extend(["-var-file", var_file])
    
    # Add variables
    if variables:
        for key, value in variables.items():
            command.extend(["-var", f"{key}={value}"])
    
    # Execute terraform destroy command
    return_code, stdout, stderr = run_command(command, cwd=terraform_dir)
    
    # Check if command was successful
    if return_code == 0:
        LOGGER.info(f"Terraform destroy successful in {terraform_dir}")
        return True
    else:
        LOGGER.error(f"Terraform destroy failed in {terraform_dir}: {stderr}")
        return False


def terraform_output(terraform_dir, output_name=None):
    """
    Gets outputs from a Terraform state
    
    Args:
        terraform_dir (str): Path to Terraform directory
        output_name (str): Specific output to retrieve
        
    Returns:
        dict: Dictionary of outputs or specific output value
    """
    # Construct command as a list
    command = [TERRAFORM_BIN, "output", "-json"]
    
    # Add output name if specified
    if output_name:
        command.append(output_name)
    
    # Execute terraform output command
    return_code, stdout, stderr = run_command(command, cwd=terraform_dir)
    
    # Check if command was successful
    if return_code == 0:
        try:
            # Parse JSON output
            outputs = json.loads(stdout)
            
            # If output_name was specified, return the value
            if output_name and isinstance(outputs, dict) and "value" in outputs:
                return outputs["value"]
            
            return outputs
        except json.JSONDecodeError as e:
            LOGGER.error(f"Error parsing Terraform output: {str(e)}")
            return {}
    else:
        LOGGER.error(f"Terraform output failed in {terraform_dir}: {stderr}")
        return {}


def kubectl_apply(manifest_file, namespace=None, context=None, wait=True):
    """
    Applies Kubernetes manifests
    
    Args:
        manifest_file (str): Path to Kubernetes manifest file
        namespace (str): Kubernetes namespace
        context (str): Kubernetes context
        wait (bool): Whether to wait for resources to be ready
        
    Returns:
        bool: True if apply was successful, False otherwise
    """
    # Construct command as a list
    command = [KUBECTL_BIN, "apply", "-f", manifest_file]
    
    # Add namespace if specified
    if namespace:
        command.extend(["--namespace", namespace])
    
    # Add context if specified
    if context:
        command.extend(["--context", context])
    
    # Add wait flag if specified
    if wait:
        command.append("--wait")
    
    # Execute kubectl apply command
    return_code, stdout, stderr = run_command(command)
    
    # Check if command was successful
    if return_code == 0:
        LOGGER.info(f"Applied Kubernetes manifest: {manifest_file}")
        return True
    else:
        LOGGER.error(f"Failed to apply Kubernetes manifest {manifest_file}: {stderr}")
        return False


def kubectl_delete(manifest_file, namespace=None, context=None, wait=True):
    """
    Deletes Kubernetes resources
    
    Args:
        manifest_file (str): Path to Kubernetes manifest file
        namespace (str): Kubernetes namespace
        context (str): Kubernetes context
        wait (bool): Whether to wait for resources to be deleted
        
    Returns:
        bool: True if delete was successful, False otherwise
    """
    # Construct command as a list
    command = [KUBECTL_BIN, "delete", "-f", manifest_file]
    
    # Add namespace if specified
    if namespace:
        command.extend(["--namespace", namespace])
    
    # Add context if specified
    if context:
        command.extend(["--context", context])
    
    # Add wait flag if specified
    if wait:
        command.append("--wait")
    
    # Execute kubectl delete command
    return_code, stdout, stderr = run_command(command)
    
    # Check if command was successful
    if return_code == 0:
        LOGGER.info(f"Deleted Kubernetes resources from manifest: {manifest_file}")
        return True
    else:
        LOGGER.error(f"Failed to delete Kubernetes resources from manifest {manifest_file}: {stderr}")
        return False


def kubectl_get(resource_type, resource_name=None, namespace=None, context=None, output_format="json"):
    """
    Gets Kubernetes resources
    
    Args:
        resource_type (str): Type of resource to get
        resource_name (str): Name of specific resource to get
        namespace (str): Kubernetes namespace
        context (str): Kubernetes context
        output_format (str): Output format (json, yaml, etc.)
        
    Returns:
        dict: Resource information as dictionary if output_format is json, otherwise raw output
    """
    # Construct command as a list
    command = [KUBECTL_BIN, "get", resource_type]
    
    # Add resource name if specified
    if resource_name:
        command.append(resource_name)
    
    # Add namespace if specified
    if namespace:
        command.extend(["--namespace", namespace])
    
    # Add context if specified
    if context:
        command.extend(["--context", context])
    
    # Add output format
    command.extend(["-o", output_format])
    
    # Execute kubectl get command
    return_code, stdout, stderr = run_command(command)
    
    # Check if command was successful
    if return_code == 0:
        if output_format == "json":
            try:
                # Parse JSON output
                resources = json.loads(stdout)
                return resources
            except json.JSONDecodeError as e:
                LOGGER.error(f"Error parsing kubectl output: {str(e)}")
                return None
        else:
            return stdout
    else:
        LOGGER.error(f"Failed to get Kubernetes resources: {stderr}")
        return None


def check_service_health(service_url, health_endpoint="/health", timeout=HEALTH_CHECK_TIMEOUT, retries=HEALTH_CHECK_RETRIES):
    """
    Checks the health of a service by making HTTP request to its health endpoint
    
    Args:
        service_url (str): Base URL of the service
        health_endpoint (str): Health check endpoint path
        timeout (int): Request timeout in seconds
        retries (int): Number of retry attempts
        
    Returns:
        bool: True if service is healthy, False otherwise
    """
    # Construct full URL for health check
    health_url = service_url.rstrip("/") + "/" + health_endpoint.lstrip("/")
    
    LOGGER.info(f"Checking health of service at: {health_url}")
    
    # Try health check with retries
    for attempt in range(retries):
        try:
            # Make HTTP request to health endpoint
            response = requests.get(health_url, timeout=timeout)
            
            # Check response status code
            if response.status_code == 200:
                try:
                    # Try to parse response as JSON
                    health_data = response.json()
                    
                    # Check for common health indicators in response
                    status = health_data.get("status", "").lower()
                    if status in ["up", "ok", "healthy"]:
                        LOGGER.info(f"Service is healthy: {health_url}")
                        return True
                    else:
                        LOGGER.warning(f"Service health check returned status: {status}")
                except ValueError:
                    # If response is not JSON, check if it contains "UP" or "OK"
                    if "up" in response.text.lower() or "ok" in response.text.lower():
                        LOGGER.info(f"Service is healthy: {health_url}")
                        return True
            
            LOGGER.warning(f"Health check failed (attempt {attempt + 1}/{retries}): {health_url}, Status: {response.status_code}")
        
        except (requests.RequestException, ConnectionError) as e:
            LOGGER.warning(f"Health check failed (attempt {attempt + 1}/{retries}): {health_url}, Error: {str(e)}")
        
        # Wait before retrying
        if attempt < retries - 1:
            time.sleep(2)
    
    LOGGER.error(f"Health check failed after {retries} attempts: {health_url}")
    return False


def send_notification(message, level="info", notification_config=None, additional_data=None):
    """
    Sends a notification about deployment events
    
    Args:
        message (str): Notification message
        level (str): Message level (info, warning, error)
        notification_config (dict): Notification configuration
        additional_data (dict): Additional data to include in notification
        
    Returns:
        bool: True if notification was sent successfully, False otherwise
    """
    if not notification_config:
        LOGGER.warning("No notification configuration provided, skipping notification")
        return False
    
    # Ensure level is lowercase
    level = level.lower()
    
    # Check which notification channels are enabled
    channels = notification_config.get("channels", {})
    success = False
    
    # Add timestamp to additional data
    if additional_data is None:
        additional_data = {}
    
    additional_data["timestamp"] = time.strftime("%Y-%m-%d %H:%M:%S")
    
    # Send notification to enabled channels
    for channel, config in channels.items():
        if not config.get("enabled", False):
            continue
        
        try:
            # Slack notification
            if channel == "slack":
                webhook_url = config.get("webhook_url")
                if webhook_url:
                    slack_message = {
                        "text": message,
                        "attachments": [
                            {
                                "color": "good" if level == "info" else "warning" if level == "warning" else "danger",
                                "fields": [
                                    {"title": key, "value": str(value), "short": True}
                                    for key, value in additional_data.items()
                                ]
                            }
                        ]
                    }
                    
                    response = requests.post(webhook_url, json=slack_message)
                    if response.status_code == 200:
                        LOGGER.info("Sent Slack notification")
                        success = True
                    else:
                        LOGGER.warning(f"Failed to send Slack notification: {response.text}")
                else:
                    LOGGER.warning("Slack webhook URL not configured")
            
            # Email notification
            elif channel == "email":
                # Implementation for email notification would go here
                # This is a placeholder - actual implementation would require email library
                LOGGER.info("Email notification not implemented yet")
            
            # Custom notification channel
            else:
                handler = config.get("handler")
                if handler and callable(handler):
                    handler_result = handler(message, level, additional_data)
                    if handler_result:
                        LOGGER.info(f"Sent notification via custom channel: {channel}")
                        success = True
                    else:
                        LOGGER.warning(f"Failed to send notification via custom channel: {channel}")
                else:
                    LOGGER.warning(f"No handler function for custom channel: {channel}")
        
        except Exception as e:
            LOGGER.error(f"Error sending notification via {channel}: {str(e)}")
    
    return success


def find_manifest_files(manifest_dir, environment=None, file_patterns=None):
    """
    Finds Kubernetes manifest files in a directory
    
    Args:
        manifest_dir (str): Directory containing manifest files
        environment (str): Environment name to filter manifests
        file_patterns (list): List of file patterns to match
        
    Returns:
        list: List of manifest file paths
    """
    if not os.path.isdir(manifest_dir):
        LOGGER.error(f"Manifest directory does not exist: {manifest_dir}")
        return []
    
    # Default file patterns if not specified
    if not file_patterns:
        file_patterns = ["*.yaml", "*.yml"]
    
    # Find all files matching patterns
    manifest_files = []
    for pattern in file_patterns:
        # Construct full pattern path
        full_pattern = os.path.join(manifest_dir, pattern)
        
        # Find files matching pattern
        pattern_files = glob.glob(full_pattern)
        
        # Filter by environment if specified
        if environment:
            pattern_files = [f for f in pattern_files if environment in f]
        
        manifest_files.extend(pattern_files)
    
    # Remove duplicates and sort
    manifest_files = sorted(list(set(manifest_files)))
    
    LOGGER.info(f"Found {len(manifest_files)} manifest files in {manifest_dir}")
    return manifest_files


def validate_environment(environment):
    """
    Validates that an environment name is valid
    
    Args:
        environment (str): Environment name to validate
        
    Returns:
        bool: True if environment is valid, False otherwise
    """
    is_valid = environment in ENVIRONMENTS
    
    if is_valid:
        LOGGER.debug(f"Environment {environment} is valid")
    else:
        LOGGER.error(f"Invalid environment: {environment}. Valid environments are: {', '.join(ENVIRONMENTS)}")
    
    return is_valid


class DeploymentError(Exception):
    """
    Exception raised for deployment errors
    """
    
    def __init__(self, message, component=None, details=None):
        """
        Initializes a new DeploymentError instance
        
        Args:
            message (str): Error message
            component (str): Component where the error occurred
            details (dict): Additional error details
        """
        super().__init__(message)
        self.message = message
        self.component = component
        self.details = details or {}


class TerraformDeployer:
    """
    Class for managing Terraform deployments
    """
    
    def __init__(self, terraform_dir, var_file=None, variables=None, backend_config=None, auto_approve=False):
        """
        Initializes a new TerraformDeployer instance
        
        Args:
            terraform_dir (str): Path to Terraform directory
            var_file (str): Path to Terraform variable file
            variables (dict): Terraform variables
            backend_config (dict): Backend configuration variables
            auto_approve (bool): Whether to auto-approve Terraform operations
        """
        # Verify that terraform_dir exists
        if not os.path.isdir(terraform_dir):
            raise ValueError(f"Terraform directory does not exist: {terraform_dir}")
        
        self.terraform_dir = terraform_dir
        self.var_file = var_file
        self.variables = variables or {}
        self.backend_config = backend_config or {}
        self.auto_approve = auto_approve
    
    def init(self, reconfigure=False):
        """
        Initializes the Terraform working directory
        
        Args:
            reconfigure (bool): Whether to reconfigure the backend
            
        Returns:
            bool: True if initialization was successful
        """
        return terraform_init(self.terraform_dir, self.backend_config, reconfigure)
    
    def apply(self):
        """
        Applies the Terraform configuration
        
        Returns:
            bool: True if apply was successful
        """
        return terraform_apply(self.terraform_dir, self.var_file, self.variables, self.auto_approve)
    
    def destroy(self):
        """
        Destroys resources created by Terraform
        
        Returns:
            bool: True if destroy was successful
        """
        return terraform_destroy(self.terraform_dir, self.var_file, self.variables, self.auto_approve)
    
    def get_outputs(self, output_name=None):
        """
        Gets outputs from Terraform state
        
        Args:
            output_name (str): Specific output to retrieve
            
        Returns:
            dict: Dictionary of outputs or specific output value
        """
        return terraform_output(self.terraform_dir, output_name)


class KubernetesDeployer:
    """
    Class for managing Kubernetes deployments
    """
    
    def __init__(self, namespace, context=None, manifest_files=None, wait=True):
        """
        Initializes a new KubernetesDeployer instance
        
        Args:
            namespace (str): Kubernetes namespace
            context (str): Kubernetes context
            manifest_files (list): List of manifest files to deploy
            wait (bool): Whether to wait for resources to be ready
        """
        self.namespace = namespace
        self.context = context
        self.manifest_files = manifest_files or []
        self.wait = wait
    
    def add_manifest(self, manifest_file):
        """
        Adds a manifest file to the deployment
        
        Args:
            manifest_file (str): Path to manifest file
        """
        if not os.path.isfile(manifest_file):
            raise ValueError(f"Manifest file does not exist: {manifest_file}")
        
        if manifest_file not in self.manifest_files:
            self.manifest_files.append(manifest_file)
    
    def add_manifests_from_dir(self, manifest_dir, environment=None, file_patterns=None):
        """
        Adds all manifest files from a directory
        
        Args:
            manifest_dir (str): Directory containing manifest files
            environment (str): Environment name to filter manifests
            file_patterns (list): List of file patterns to match
            
        Returns:
            int: Number of manifest files added
        """
        manifest_files = find_manifest_files(manifest_dir, environment, file_patterns)
        
        initial_count = len(self.manifest_files)
        
        for manifest_file in manifest_files:
            if manifest_file not in self.manifest_files:
                self.manifest_files.append(manifest_file)
        
        return len(self.manifest_files) - initial_count
    
    def deploy(self, wait=None):
        """
        Deploys all manifest files
        
        Args:
            wait (bool): Whether to wait for resources to be ready (overrides instance setting)
            
        Returns:
            bool: True if all deployments were successful
        """
        if not self.manifest_files:
            LOGGER.warning("No manifest files to deploy")
            return False
        
        # Use instance wait setting if not overridden
        if wait is None:
            wait = self.wait
        
        success = True
        
        # Apply each manifest file
        for manifest_file in self.manifest_files:
            result = kubectl_apply(manifest_file, self.namespace, self.context, wait)
            if not result:
                success = False
        
        return success
    
    def delete(self, wait=None):
        """
        Deletes resources defined in manifest files
        
        Args:
            wait (bool): Whether to wait for resources to be deleted (overrides instance setting)
            
        Returns:
            bool: True if all deletions were successful
        """
        if not self.manifest_files:
            LOGGER.warning("No manifest files to delete")
            return False
        
        # Use instance wait setting if not overridden
        if wait is None:
            wait = self.wait
        
        success = True
        
        # Delete resources from each manifest file
        for manifest_file in self.manifest_files:
            result = kubectl_delete(manifest_file, self.namespace, self.context, wait)
            if not result:
                success = False
        
        return success
    
    def get_resources(self, resource_type, resource_name=None, output_format="json"):
        """
        Gets resources of a specific type
        
        Args:
            resource_type (str): Type of resource to get
            resource_name (str): Name of specific resource to get
            output_format (str): Output format (json, yaml, etc.)
            
        Returns:
            dict: Resource information
        """
        return kubectl_get(resource_type, resource_name, self.namespace, self.context, output_format)