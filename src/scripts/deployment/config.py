import os
import logging
import yaml
import json
from dataclasses import dataclass

# Set up the logger
LOGGER = logging.getLogger(__name__)

# Default configuration values
DEFAULT_CONFIG_FILE = os.environ.get('DEPLOYMENT_CONFIG_FILE', os.path.join(os.path.dirname(__file__), '../..', 'config', 'deployment.yml'))
DEFAULT_LOG_LEVEL = os.environ.get('LOG_LEVEL', 'INFO')
DEFAULT_LOG_FORMAT = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
DEFAULT_DEPLOYMENT_TIMEOUT = int(os.environ.get('DEPLOYMENT_TIMEOUT', '600'))

# Supported environments
ENVIRONMENTS = ['development', 'test', 'staging', 'production']

# Kubernetes namespaces for each environment
KUBERNETES_NAMESPACES = {
    'development': 'payment-dev',
    'test': 'payment-test',
    'staging': 'payment-staging',
    'production': 'payment-prod'
}

# Terraform directories for each environment
TERRAFORM_DIRS = {
    'development': 'src/backend/terraform/environments/dev',
    'staging': 'src/backend/terraform/environments/staging',
    'production': 'src/backend/terraform/environments/prod'
}

# Service URLs for each environment
SERVICE_URLS = {
    'development': {
        'payment-eapi': 'http://payment-eapi-dev.example.com',
        'payment-sapi': 'http://payment-sapi-dev.example.com',
        'conjur': 'http://conjur-dev.example.com'
    },
    'test': {
        'payment-eapi': 'http://payment-eapi-test.example.com',
        'payment-sapi': 'http://payment-sapi-test.example.com',
        'conjur': 'http://conjur-test.example.com'
    },
    'staging': {
        'payment-eapi': 'http://payment-eapi-staging.example.com',
        'payment-sapi': 'http://payment-sapi-staging.example.com',
        'conjur': 'http://conjur-staging.example.com'
    },
    'production': {
        'payment-eapi': 'https://payment-eapi.example.com',
        'payment-sapi': 'https://payment-sapi.example.com',
        'conjur': 'https://conjur.example.com'
    }
}

# Notification channels for deployment events
NOTIFICATION_CHANNELS = {
    'slack': {
        'webhook_url': os.environ.get('SLACK_WEBHOOK_URL', ''),
        'channel': os.environ.get('SLACK_CHANNEL', '#deployments')
    },
    'email': {
        'smtp_server': os.environ.get('SMTP_SERVER', 'smtp.example.com'),
        'smtp_port': int(os.environ.get('SMTP_PORT', '587')),
        'smtp_user': os.environ.get('SMTP_USER', ''),
        'smtp_password': os.environ.get('SMTP_PASSWORD', ''),
        'from_address': os.environ.get('EMAIL_FROM', 'deployment@example.com'),
        'recipients': os.environ.get('EMAIL_RECIPIENTS', 'operations@example.com').split(',')
    }
}

# Backup directory
BACKUP_DIR = os.environ.get('BACKUP_DIR', os.path.join(os.path.dirname(__file__), '../../backups'))

def setup_logging(log_level=DEFAULT_LOG_LEVEL, log_format=DEFAULT_LOG_FORMAT):
    """
    Sets up logging configuration for deployment operations
    
    Args:
        log_level (str): Logging level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        log_format (str): Logging message format
        
    Returns:
        logging.Logger: Configured logger instance
    """
    # Create a logger instance for deployment operations
    logger = logging.getLogger('deployment')
    
    # Create a console handler
    console_handler = logging.StreamHandler()
    
    # Set log level on handler and logger
    log_level_obj = getattr(logging, log_level.upper(), logging.INFO)
    logger.setLevel(log_level_obj)
    console_handler.setLevel(log_level_obj)
    
    # Create formatter with the specified format
    formatter = logging.Formatter(log_format)
    
    # Add formatter to handler
    console_handler.setFormatter(formatter)
    
    # Add handler to logger
    logger.addHandler(console_handler)
    
    return logger

def load_config_from_file(file_path):
    """
    Loads configuration from a JSON or YAML file
    
    Args:
        file_path (str): Path to the configuration file
        
    Returns:
        dict: Configuration dictionary
    """
    try:
        # Check if file exists
        if not os.path.exists(file_path):
            LOGGER.error(f"Config file not found: {file_path}")
            return {}
            
        # Determine file type based on extension
        file_extension = os.path.splitext(file_path)[1].lower()
        
        # Open and read the file
        with open(file_path, 'r') as f:
            file_content = f.read()
            
        # Parse file content based on type (JSON or YAML)
        if file_extension in ['.yaml', '.yml']:
            config = yaml.safe_load(file_content)
        elif file_extension == '.json':
            config = json.loads(file_content)
        else:
            LOGGER.warning(f"Unsupported file type: {file_extension}. Using YAML parser.")
            config = yaml.safe_load(file_content)
            
        return config or {}
    except Exception as e:
        LOGGER.error(f"Error loading config from file {file_path}: {str(e)}")
        return {}

def load_config_from_env(prefix='DEPLOYMENT_'):
    """
    Loads configuration from environment variables
    
    Args:
        prefix (str): Prefix for relevant environment variables
        
    Returns:
        dict: Configuration dictionary
    """
    # Initialize empty configuration dictionary
    config = {}
    
    # Iterate through environment variables
    for key, value in os.environ.items():
        # Filter variables that start with the specified prefix
        if key.startswith(prefix):
            # Remove prefix and convert to lowercase for config keys
            config_key = key[len(prefix):].lower()
            
            # Add environment variable values to config dictionary
            config[config_key] = value
            
    return config

def get_environment_config(environment, config_file=DEFAULT_CONFIG_FILE):
    """
    Gets configuration for a specific environment
    
    Args:
        environment (str): Target environment (development, test, staging, production)
        config_file (str): Path to the configuration file
        
    Returns:
        dict: Environment-specific configuration
    """
    # Validate that environment is one of the supported environments
    if environment not in ENVIRONMENTS:
        LOGGER.warning(f"Unsupported environment: {environment}. Using development.")
        environment = 'development'
    
    # Initialize config with default values
    config = {
        'environment': environment,
        'kubernetes_namespace': KUBERNETES_NAMESPACES.get(environment),
        'terraform_dir': TERRAFORM_DIRS.get(environment),
        'service_urls': SERVICE_URLS.get(environment, {}),
        'notification_channels': NOTIFICATION_CHANNELS,
        'backup_dir': BACKUP_DIR,
        'deployment_timeout': DEFAULT_DEPLOYMENT_TIMEOUT,
        'rollback_on_failure': True
    }
    
    # If config_file is provided, load and merge configuration from file
    if config_file:
        file_config = load_config_from_file(config_file)
        env_specific_config = file_config.get(environment, {})
        config.update(env_specific_config)
    
    # Load and merge configuration from environment variables with 'DEPLOYMENT_' prefix
    env_config = load_config_from_env('DEPLOYMENT_')
    config.update(env_config)
    
    return config

def get_kubernetes_namespace(environment):
    """
    Gets the Kubernetes namespace for a specific environment
    
    Args:
        environment (str): Target environment (development, test, staging, production)
        
    Returns:
        str: Kubernetes namespace
    """
    # Validate that environment is one of the supported environments
    if environment not in ENVIRONMENTS:
        LOGGER.warning(f"Unsupported environment: {environment}. Using development.")
        environment = 'development'
    
    # Return the namespace from KUBERNETES_NAMESPACES dictionary for the specified environment
    namespace = KUBERNETES_NAMESPACES.get(environment)
    
    # If environment not found in dictionary, log warning and return default namespace
    if not namespace:
        LOGGER.warning(f"No namespace defined for environment: {environment}. Using default.")
        namespace = f"payment-{environment}"
    
    return namespace

def get_terraform_dir(environment):
    """
    Gets the Terraform directory for a specific environment
    
    Args:
        environment (str): Target environment (development, test, staging, production)
        
    Returns:
        str: Terraform directory path
    """
    # Validate that environment is one of the supported environments
    if environment not in ENVIRONMENTS:
        LOGGER.warning(f"Unsupported environment: {environment}. Using development.")
        environment = 'development'
    
    # Return the directory path from TERRAFORM_DIRS dictionary for the specified environment
    tf_dir = TERRAFORM_DIRS.get(environment)
    
    # If environment not found in dictionary, log warning and return default directory
    if not tf_dir:
        LOGGER.warning(f"No Terraform directory defined for environment: {environment}. Using default.")
        tf_dir = f"src/backend/terraform/environments/{environment}"
    
    return tf_dir

def get_service_url(environment, service):
    """
    Gets the URL for a specific service in an environment
    
    Args:
        environment (str): Target environment (development, test, staging, production)
        service (str): Service name
        
    Returns:
        str: Service URL
    """
    # Validate that environment is one of the supported environments
    if environment not in ENVIRONMENTS:
        LOGGER.warning(f"Unsupported environment: {environment}. Using development.")
        environment = 'development'
    
    # Check if service exists in SERVICE_URLS dictionary for the specified environment
    env_urls = SERVICE_URLS.get(environment, {})
    service_url = env_urls.get(service)
    
    # Return the service URL if found
    if service_url:
        return service_url
    
    # If service not found, log warning and return None
    LOGGER.warning(f"No URL defined for service '{service}' in environment: {environment}")
    return None

def create_deployment_config(environment, config_file=DEFAULT_CONFIG_FILE):
    """
    Creates a DeploymentConfig instance from configuration sources
    
    Args:
        environment (str): Target environment (development, test, staging, production)
        config_file (str): Path to the configuration file
        
    Returns:
        DeploymentConfig: Configured DeploymentConfig instance
    """
    # Get environment-specific configuration using get_environment_config
    config = get_environment_config(environment, config_file)
    
    # Create and return a DeploymentConfig instance with the configuration
    return DeploymentConfig(**config)

@dataclass
class DeploymentConfig:
    """
    Configuration class for deployment operations
    """
    environment: str
    kubernetes_namespace: str = None
    kubernetes_context: str = None
    terraform_dir: str = None
    service_urls: dict = None
    notification_channels: dict = None
    backup_dir: str = None
    deployment_timeout: int = DEFAULT_DEPLOYMENT_TIMEOUT
    rollback_on_failure: bool = True
    additional_config: dict = None
    
    def __post_init__(self):
        """
        Post-initialization to set default values
        """
        # Set default values for None attributes
        if self.kubernetes_namespace is None:
            self.kubernetes_namespace = KUBERNETES_NAMESPACES.get(self.environment)
        
        if self.terraform_dir is None:
            self.terraform_dir = TERRAFORM_DIRS.get(self.environment)
        
        if self.service_urls is None:
            self.service_urls = SERVICE_URLS.get(self.environment, {})
        
        if self.notification_channels is None:
            self.notification_channels = NOTIFICATION_CHANNELS
        
        if self.backup_dir is None:
            self.backup_dir = BACKUP_DIR
        
        if self.additional_config is None:
            self.additional_config = {}
    
    def validate(self):
        """
        Validates the configuration values
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        is_valid = True
        
        # Check if environment is one of the supported environments
        if self.environment not in ENVIRONMENTS:
            LOGGER.error(f"Unsupported environment: {self.environment}")
            is_valid = False
        
        # Check if kubernetes_namespace is set
        if not self.kubernetes_namespace:
            LOGGER.error("Kubernetes namespace is not set")
            is_valid = False
        
        # Check if terraform_dir is set and directory exists
        if not self.terraform_dir:
            LOGGER.error("Terraform directory is not set")
            is_valid = False
        elif not os.path.isdir(self.terraform_dir):
            LOGGER.error(f"Terraform directory does not exist: {self.terraform_dir}")
            is_valid = False
        
        # Check if service_urls contains required services
        required_services = ['payment-eapi', 'payment-sapi', 'conjur']
        for service in required_services:
            if service not in self.service_urls:
                LOGGER.error(f"Missing URL for required service: {service}")
                is_valid = False
        
        return is_valid
    
    def to_dict(self):
        """
        Converts the configuration to a dictionary
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        # Create a dictionary with all configuration properties
        config_dict = {
            'environment': self.environment,
            'kubernetes_namespace': self.kubernetes_namespace,
            'kubernetes_context': self.kubernetes_context,
            'terraform_dir': self.terraform_dir,
            'service_urls': self.service_urls,
            'notification_channels': self.notification_channels,
            'backup_dir': self.backup_dir,
            'deployment_timeout': self.deployment_timeout,
            'rollback_on_failure': self.rollback_on_failure,
            'additional_config': self.additional_config
        }
        
        return config_dict
    
    @classmethod
    def from_dict(cls, config_dict):
        """
        Creates a DeploymentConfig instance from a dictionary
        
        Args:
            config_dict (dict): Configuration dictionary
            
        Returns:
            DeploymentConfig: DeploymentConfig instance
        """
        # Extract known configuration keys from dictionary
        environment = config_dict.get('environment')
        kubernetes_namespace = config_dict.get('kubernetes_namespace')
        kubernetes_context = config_dict.get('kubernetes_context')
        terraform_dir = config_dict.get('terraform_dir')
        service_urls = config_dict.get('service_urls')
        notification_channels = config_dict.get('notification_channels')
        backup_dir = config_dict.get('backup_dir')
        deployment_timeout = config_dict.get('deployment_timeout')
        rollback_on_failure = config_dict.get('rollback_on_failure', True)
        
        # Store remaining keys in additional_config
        known_keys = {
            'environment', 'kubernetes_namespace', 'kubernetes_context',
            'terraform_dir', 'service_urls', 'notification_channels',
            'backup_dir', 'deployment_timeout', 'rollback_on_failure'
        }
        additional_config = {k: v for k, v in config_dict.items() if k not in known_keys}
        
        # Create and return a new DeploymentConfig instance with the extracted values
        return cls(
            environment=environment,
            kubernetes_namespace=kubernetes_namespace,
            kubernetes_context=kubernetes_context,
            terraform_dir=terraform_dir,
            service_urls=service_urls,
            notification_channels=notification_channels,
            backup_dir=backup_dir,
            deployment_timeout=deployment_timeout,
            rollback_on_failure=rollback_on_failure,
            additional_config=additional_config
        )
    
    def get_service_url(self, service):
        """
        Gets the URL for a specific service
        
        Args:
            service (str): Service name
            
        Returns:
            str: Service URL
        """
        # Check if service exists in service_urls dictionary
        service_url = self.service_urls.get(service)
        
        # Return the service URL if found
        if service_url:
            return service_url
        
        # If service not found, log warning and return None
        LOGGER.warning(f"No URL defined for service '{service}' in environment: {self.environment}")
        return None

@dataclass
class KubernetesConfig:
    """
    Configuration class for Kubernetes deployments
    """
    namespace: str
    context: str = None
    kubeconfig: str = None
    manifests: list = None
    additional_config: dict = None
    
    def __post_init__(self):
        """
        Post-initialization to set default values
        """
        if self.manifests is None:
            self.manifests = []
        
        if self.additional_config is None:
            self.additional_config = {}
    
    def validate(self):
        """
        Validates the Kubernetes configuration values
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        is_valid = True
        
        # Check if namespace is set
        if not self.namespace:
            LOGGER.error("Kubernetes namespace is not set")
            is_valid = False
        
        # Check if manifests list is not empty
        if not self.manifests:
            LOGGER.error("No Kubernetes manifests specified")
            is_valid = False
        
        # Check if kubeconfig file exists if specified
        if self.kubeconfig and not os.path.isfile(self.kubeconfig):
            LOGGER.error(f"Kubeconfig file does not exist: {self.kubeconfig}")
            is_valid = False
        
        return is_valid
    
    def to_dict(self):
        """
        Converts the Kubernetes configuration to a dictionary
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        # Create a dictionary with all configuration properties
        config_dict = {
            'namespace': self.namespace,
            'context': self.context,
            'kubeconfig': self.kubeconfig,
            'manifests': self.manifests,
            'additional_config': self.additional_config
        }
        
        return config_dict
    
    @classmethod
    def from_dict(cls, config_dict):
        """
        Creates a KubernetesConfig instance from a dictionary
        
        Args:
            config_dict (dict): Configuration dictionary
            
        Returns:
            KubernetesConfig: KubernetesConfig instance
        """
        # Extract known configuration keys from dictionary
        namespace = config_dict.get('namespace')
        context = config_dict.get('context')
        kubeconfig = config_dict.get('kubeconfig')
        manifests = config_dict.get('manifests', [])
        
        # Store remaining keys in additional_config
        known_keys = {'namespace', 'context', 'kubeconfig', 'manifests'}
        additional_config = {k: v for k, v in config_dict.items() if k not in known_keys}
        
        # Create and return a new KubernetesConfig instance with the extracted values
        return cls(
            namespace=namespace,
            context=context,
            kubeconfig=kubeconfig,
            manifests=manifests,
            additional_config=additional_config
        )

@dataclass
class TerraformConfig:
    """
    Configuration class for Terraform deployments
    """
    terraform_dir: str
    var_file: str = None
    variables: dict = None
    backend_config: dict = None
    auto_approve: bool = False
    additional_config: dict = None
    
    def __post_init__(self):
        """
        Post-initialization to set default values
        """
        if self.variables is None:
            self.variables = {}
        
        if self.backend_config is None:
            self.backend_config = {}
        
        if self.additional_config is None:
            self.additional_config = {}
    
    def validate(self):
        """
        Validates the Terraform configuration values
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        is_valid = True
        
        # Check if terraform_dir is set and directory exists
        if not self.terraform_dir:
            LOGGER.error("Terraform directory is not set")
            is_valid = False
        elif not os.path.isdir(self.terraform_dir):
            LOGGER.error(f"Terraform directory does not exist: {self.terraform_dir}")
            is_valid = False
        
        # Check if var_file exists if specified
        if self.var_file and not os.path.isfile(self.var_file):
            LOGGER.error(f"Terraform variable file does not exist: {self.var_file}")
            is_valid = False
        
        return is_valid
    
    def to_dict(self):
        """
        Converts the Terraform configuration to a dictionary
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        # Create a dictionary with all configuration properties
        config_dict = {
            'terraform_dir': self.terraform_dir,
            'var_file': self.var_file,
            'variables': self.variables,
            'backend_config': self.backend_config,
            'auto_approve': self.auto_approve,
            'additional_config': self.additional_config
        }
        
        return config_dict
    
    @classmethod
    def from_dict(cls, config_dict):
        """
        Creates a TerraformConfig instance from a dictionary
        
        Args:
            config_dict (dict): Configuration dictionary
            
        Returns:
            TerraformConfig: TerraformConfig instance
        """
        # Extract known configuration keys from dictionary
        terraform_dir = config_dict.get('terraform_dir')
        var_file = config_dict.get('var_file')
        variables = config_dict.get('variables', {})
        backend_config = config_dict.get('backend_config', {})
        auto_approve = config_dict.get('auto_approve', False)
        
        # Store remaining keys in additional_config
        known_keys = {'terraform_dir', 'var_file', 'variables', 'backend_config', 'auto_approve'}
        additional_config = {k: v for k, v in config_dict.items() if k not in known_keys}
        
        # Create and return a new TerraformConfig instance with the extracted values
        return cls(
            terraform_dir=terraform_dir,
            var_file=var_file,
            variables=variables,
            backend_config=backend_config,
            auto_approve=auto_approve,
            additional_config=additional_config
        )