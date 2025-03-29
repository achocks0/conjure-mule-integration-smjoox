import os
import logging
import json
import yaml
from dataclasses import dataclass
from typing import Dict, Optional, Any

# Global constants and variables
LOGGER = logging.getLogger('conjur')
DEFAULT_CONJUR_URL = None
DEFAULT_CONJUR_ACCOUNT = "payment-system"
DEFAULT_CONJUR_AUTHN_LOGIN = "payment-eapi-service"
DEFAULT_CONJUR_CERT_PATH = None
DEFAULT_CREDENTIAL_PATH_TEMPLATE = "secrets/{account}/variable/payment/credentials/{client_id}"
DEFAULT_LOG_LEVEL = logging.INFO
ENV_PREFIX = "CONJUR_"


@dataclass
class ConjurConfig:
    """
    Configuration class for Conjur vault integration.
    
    Contains all necessary configuration parameters for connecting to and 
    interacting with the Conjur vault for secure credential management.
    """
    url: Optional[str] = None
    account: Optional[str] = None
    authn_login: Optional[str] = None
    cert_path: Optional[str] = None
    credential_path_template: Optional[str] = None
    additional_config: Dict[str, Any] = None

    def __post_init__(self):
        """Initialize default values if not provided."""
        self.url = self.url or DEFAULT_CONJUR_URL
        self.account = self.account or DEFAULT_CONJUR_ACCOUNT
        self.authn_login = self.authn_login or DEFAULT_CONJUR_AUTHN_LOGIN
        self.cert_path = self.cert_path or DEFAULT_CONJUR_CERT_PATH
        self.credential_path_template = self.credential_path_template or DEFAULT_CREDENTIAL_PATH_TEMPLATE
        self.additional_config = self.additional_config or {}

    def validate(self) -> bool:
        """
        Validates the configuration values.
        
        Returns:
            bool: True if configuration is valid, False otherwise.
        """
        is_valid = True
        
        if not self.url:
            LOGGER.error("Conjur URL is not set")
            is_valid = False
        
        if not self.account:
            LOGGER.error("Conjur account is not set")
            is_valid = False
            
        if not self.authn_login:
            LOGGER.error("Conjur authentication login is not set")
            is_valid = False
            
        if not self.cert_path:
            LOGGER.warning("Conjur certificate path is not set, TLS verification may be disabled")
        elif not os.path.isfile(self.cert_path):
            LOGGER.error(f"Conjur certificate file not found: {self.cert_path}")
            is_valid = False
            
        return is_valid
    
    def to_dict(self) -> Dict[str, Any]:
        """
        Converts the configuration to a dictionary.
        
        Returns:
            dict: Dictionary representation of the configuration.
        """
        return {
            'url': self.url,
            'account': self.account,
            'authn_login': self.authn_login,
            'cert_path': self.cert_path,
            'credential_path_template': self.credential_path_template,
            **self.additional_config
        }
    
    @classmethod
    def from_dict(cls, config_dict: Dict[str, Any]) -> 'ConjurConfig':
        """
        Creates a ConjurConfig instance from a dictionary.
        
        Args:
            config_dict: Dictionary containing configuration values.
            
        Returns:
            ConjurConfig: A new ConjurConfig instance.
        """
        # Extract known configuration keys
        known_keys = ['url', 'account', 'authn_login', 'cert_path', 'credential_path_template']
        base_config = {k: config_dict.get(k) for k in known_keys}
        
        # Store remaining keys in additional_config
        additional_config = {k: v for k, v in config_dict.items() if k not in known_keys}
        base_config['additional_config'] = additional_config
        
        return cls(**base_config)


@dataclass
class RotationConfig:
    """
    Configuration class for credential rotation.
    
    Contains configuration parameters for managing credential rotation
    including transition periods and monitoring settings.
    """
    transition_period_seconds: int = 86400  # Default: 1 day
    monitoring_interval_seconds: int = 300  # Default: 5 minutes
    notification_endpoint: Optional[str] = None
    monitoring_endpoint: Optional[str] = None
    additional_config: Dict[str, Any] = None

    def __post_init__(self):
        """Initialize default values if not provided."""
        self.additional_config = self.additional_config or {}

    def validate(self) -> bool:
        """
        Validates the rotation configuration values.
        
        Returns:
            bool: True if configuration is valid, False otherwise.
        """
        is_valid = True
        
        if self.transition_period_seconds <= 0:
            LOGGER.error("Transition period must be positive")
            is_valid = False
            
        if self.monitoring_interval_seconds <= 0:
            LOGGER.error("Monitoring interval must be positive")
            is_valid = False
            
        if self.monitoring_interval_seconds >= self.transition_period_seconds:
            LOGGER.error("Monitoring interval must be less than transition period")
            is_valid = False
            
        return is_valid
    
    def to_dict(self) -> Dict[str, Any]:
        """
        Converts the rotation configuration to a dictionary.
        
        Returns:
            dict: Dictionary representation of the configuration.
        """
        return {
            'transition_period_seconds': self.transition_period_seconds,
            'monitoring_interval_seconds': self.monitoring_interval_seconds,
            'notification_endpoint': self.notification_endpoint,
            'monitoring_endpoint': self.monitoring_endpoint,
            **self.additional_config
        }
    
    @classmethod
    def from_dict(cls, config_dict: Dict[str, Any]) -> 'RotationConfig':
        """
        Creates a RotationConfig instance from a dictionary.
        
        Args:
            config_dict: Dictionary containing configuration values.
            
        Returns:
            RotationConfig: A new RotationConfig instance.
        """
        # Extract known configuration keys
        known_keys = ['transition_period_seconds', 'monitoring_interval_seconds', 
                     'notification_endpoint', 'monitoring_endpoint']
        base_config = {k: config_dict.get(k) for k in known_keys}
        
        # Store remaining keys in additional_config
        additional_config = {k: v for k, v in config_dict.items() if k not in known_keys}
        base_config['additional_config'] = additional_config
        
        return cls(**base_config)


def setup_logging(log_level: str = None, log_format: str = None) -> logging.Logger:
    """
    Sets up logging configuration for Conjur operations.
    
    Args:
        log_level: Logging level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        log_format: Format string for log messages
        
    Returns:
        logging.Logger: Configured logger instance
    """
    # Convert string log level to logging constant if provided as string
    if isinstance(log_level, str):
        log_level = getattr(logging, log_level.upper(), DEFAULT_LOG_LEVEL)
    else:
        log_level = log_level or DEFAULT_LOG_LEVEL
        
    # Default log format if not provided
    if not log_format:
        log_format = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    
    # Create logger
    logger = logging.getLogger('conjur')
    logger.setLevel(log_level)
    
    # Create console handler if no handlers exist
    if not logger.handlers:
        handler = logging.StreamHandler()
        handler.setLevel(log_level)
        
        # Create formatter
        formatter = logging.Formatter(log_format)
        handler.setFormatter(formatter)
        
        # Add handler to logger
        logger.addHandler(handler)
    
    return logger


def load_config_from_file(file_path: str) -> Dict[str, Any]:
    """
    Loads configuration from a JSON or YAML file.
    
    Args:
        file_path: Path to the configuration file
        
    Returns:
        dict: Configuration dictionary
        
    Raises:
        FileNotFoundError: If the file doesn't exist
        ValueError: If the file format is not supported or parsing fails
    """
    if not os.path.isfile(file_path):
        LOGGER.error(f"Configuration file not found: {file_path}")
        raise FileNotFoundError(f"Configuration file not found: {file_path}")
    
    # Determine file type based on extension
    file_ext = os.path.splitext(file_path)[1].lower()
    
    try:
        with open(file_path, 'r') as f:
            if file_ext == '.json':
                config = json.load(f)
            elif file_ext in ('.yaml', '.yml'):
                config = yaml.safe_load(f)
            else:
                LOGGER.error(f"Unsupported configuration file format: {file_ext}")
                raise ValueError(f"Unsupported configuration file format: {file_ext}")
                
        return config
    except (json.JSONDecodeError, yaml.YAMLError) as e:
        LOGGER.error(f"Error parsing configuration file {file_path}: {str(e)}")
        raise ValueError(f"Error parsing configuration file: {str(e)}")


def load_config_from_env(prefix: str = ENV_PREFIX) -> Dict[str, Any]:
    """
    Loads configuration from environment variables.
    
    Args:
        prefix: Prefix for environment variables to consider
        
    Returns:
        dict: Configuration dictionary
    """
    config = {}
    
    for key, value in os.environ.items():
        # Check if the environment variable starts with the prefix
        if key.startswith(prefix):
            # Remove prefix and convert to lowercase for consistent config keys
            config_key = key[len(prefix):].lower()
            
            # Try to parse bool/int values
            if value.lower() in ('true', 'yes', '1'):
                config[config_key] = True
            elif value.lower() in ('false', 'no', '0'):
                config[config_key] = False
            elif value.isdigit():
                config[config_key] = int(value)
            else:
                config[config_key] = value
    
    return config


def read_certificate(cert_path: str) -> str:
    """
    Reads a certificate file and returns its content.
    
    Args:
        cert_path: Path to the certificate file
        
    Returns:
        str: Certificate content
        
    Raises:
        FileNotFoundError: If the certificate file doesn't exist
    """
    if not cert_path:
        LOGGER.error("Certificate path is not specified")
        raise ValueError("Certificate path is not specified")
    
    if not os.path.isfile(cert_path):
        LOGGER.error(f"Certificate file not found: {cert_path}")
        raise FileNotFoundError(f"Certificate file not found: {cert_path}")
    
    try:
        with open(cert_path, 'r') as f:
            return f.read()
    except Exception as e:
        LOGGER.error(f"Error reading certificate file {cert_path}: {str(e)}")
        raise IOError(f"Error reading certificate file: {str(e)}")


def get_credential_path(client_id: str, conjur_config: ConjurConfig) -> str:
    """
    Builds the path for a credential in Conjur vault.
    
    Args:
        client_id: Client ID for which to build the path
        conjur_config: Conjur configuration instance
        
    Returns:
        str: Full path to the credential in Conjur vault
    """
    template = conjur_config.credential_path_template or DEFAULT_CREDENTIAL_PATH_TEMPLATE
    return template.format(account=conjur_config.account, client_id=client_id)


def get_rotation_config(config_file: str = None) -> RotationConfig:
    """
    Creates a RotationConfig instance from configuration sources.
    
    Args:
        config_file: Path to configuration file (optional)
        
    Returns:
        RotationConfig: Configured RotationConfig instance
    """
    # Initialize with default values
    config = {}
    
    # Load from file if specified
    if config_file:
        try:
            file_config = load_config_from_file(config_file)
            config.update(file_config)
        except (FileNotFoundError, ValueError) as e:
            LOGGER.warning(f"Could not load configuration from file: {str(e)}")
    
    # Load from environment variables (overrides file config)
    env_config = load_config_from_env(ENV_PREFIX)
    config.update(env_config)
    
    # Create rotation config instance
    return RotationConfig.from_dict(config)


def create_conjur_config(config_file: str = None) -> ConjurConfig:
    """
    Creates a ConjurConfig instance from configuration sources.
    
    Args:
        config_file: Path to configuration file (optional)
        
    Returns:
        ConjurConfig: Configured ConjurConfig instance
    """
    # Initialize with default values
    config = {}
    
    # Load from file if specified
    if config_file:
        try:
            file_config = load_config_from_file(config_file)
            config.update(file_config)
        except (FileNotFoundError, ValueError) as e:
            LOGGER.warning(f"Could not load configuration from file: {str(e)}")
    
    # Load from environment variables (overrides file config)
    env_config = load_config_from_env(ENV_PREFIX)
    config.update(env_config)
    
    # Create conjur config instance
    return ConjurConfig.from_dict(config)