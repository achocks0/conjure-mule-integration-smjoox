import os
import logging
import json
import yaml
from datetime import datetime
from dataclasses import dataclass
from pathlib import Path

# Logger setup
LOGGER = logging.getLogger('payment_testing')
DEFAULT_LOG_LEVEL = logging.INFO
DEFAULT_LOG_FORMAT = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'

# Default directory paths
DEFAULT_CONFIG_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), 'config')
DEFAULT_TEST_DATA_DIR = os.path.join(os.path.dirname(__file__), 'test_data')
DEFAULT_TEST_REPORT_DIR = os.path.join(os.path.dirname(__file__), 'test_reports')

# Test scenarios and thresholds
TEST_SCENARIOS = {}
PERFORMANCE_TEST_THRESHOLDS = {
    'p95_response_time': 500,  # milliseconds
    'p99_response_time': 1000, # milliseconds
    'error_rate': 0.01,        # 1% error rate
    'min_throughput': 50       # requests per second
}


def setup_logging(log_level=DEFAULT_LOG_LEVEL, log_format=DEFAULT_LOG_FORMAT):
    """
    Sets up logging configuration for test execution
    
    Args:
        log_level (str): Logging level to use
        log_format (str): Format string for log messages
        
    Returns:
        logging.Logger: Configured logger instance
    """
    logger = logging.getLogger('payment_testing')
    
    # Clear existing handlers
    if logger.handlers:
        logger.handlers.clear()
        
    handler = logging.StreamHandler()
    handler.setLevel(log_level)
    logger.setLevel(log_level)
    formatter = logging.Formatter(log_format)
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    return logger


def load_config_from_file(file_path):
    """
    Loads configuration from a JSON or YAML file
    
    Args:
        file_path (str): Path to the configuration file
        
    Returns:
        dict: Configuration dictionary
    """
    if not os.path.exists(file_path):
        LOGGER.error(f"Configuration file not found: {file_path}")
        return {}
    
    try:
        with open(file_path, 'r') as file:
            file_extension = os.path.splitext(file_path)[1].lower()
            if file_extension == '.json':
                return json.load(file)
            elif file_extension in ['.yml', '.yaml']:
                return yaml.safe_load(file)
            else:
                LOGGER.error(f"Unsupported configuration file type: {file_extension}")
                return {}
    except (json.JSONDecodeError, yaml.YAMLError) as e:
        LOGGER.error(f"Error parsing configuration file {file_path}: {str(e)}")
        return {}
    except Exception as e:
        LOGGER.error(f"Error loading configuration file {file_path}: {str(e)}")
        return {}


def load_config_from_env(prefix='PAYMENT_TEST_'):
    """
    Loads configuration from environment variables
    
    Args:
        prefix (str): Prefix for environment variables to include
        
    Returns:
        dict: Configuration dictionary
    """
    config = {}
    for key, value in os.environ.items():
        if key.startswith(prefix):
            config_key = key[len(prefix):].lower()
            
            # Try to convert to appropriate type
            if value.lower() in ['true', 'yes', '1']:
                config[config_key] = True
            elif value.lower() in ['false', 'no', '0']:
                config[config_key] = False
            else:
                try:
                    # Try to convert to int or float
                    if '.' in value:
                        config[config_key] = float(value)
                    else:
                        config[config_key] = int(value)
                except ValueError:
                    # Keep as string if conversion fails
                    config[config_key] = value
                    
    return config


def load_test_scenarios(file_path):
    """
    Loads test scenarios from a configuration file
    
    Args:
        file_path (str): Path to the test scenarios file
        
    Returns:
        dict: Test scenarios dictionary
    """
    if not os.path.exists(file_path):
        LOGGER.error(f"Test scenarios file not found: {file_path}")
        return {}
    
    config = load_config_from_file(file_path)
    scenarios = config.get('test_scenarios', {})
    return scenarios


def get_test_config(config_file=None, environment=None):
    """
    Creates a TestConfig instance from configuration sources
    
    Args:
        config_file (str): Path to configuration file
        environment (str): Environment name for specific configuration
        
    Returns:
        TestConfig: Configured TestConfig instance
    """
    # Start with default configuration
    config = {
        'eapi_url': 'http://localhost:8080',
        'sapi_url': 'http://localhost:8081',
        'conjur_url': 'http://localhost:8082',
        'test_client_id': 'test-client',
        'test_client_secret': 'test-secret',
        'test_data_dir': DEFAULT_TEST_DATA_DIR,
        'test_report_dir': DEFAULT_TEST_REPORT_DIR,
    }
    
    # Load from file if provided
    if config_file:
        file_config = load_config_from_file(config_file)
        config.update(file_config)
    
    # Load from environment variables
    env_config = load_config_from_env()
    config.update(env_config)
    
    # Apply environment-specific config if provided
    if environment and 'environments' in config and environment in config['environments']:
        env_specific_config = config['environments'][environment]
        config.update(env_specific_config)
    
    # Extract known keys for TestConfig and store the rest in additional_config
    known_keys = ['eapi_url', 'sapi_url', 'conjur_url', 'test_client_id', 
                  'test_client_secret', 'test_data_dir', 'test_report_dir']
    additional_config = {k: v for k, v in config.items() if k not in known_keys}
    
    # Create TestConfig instance
    test_config = TestConfig(
        eapi_url=config.get('eapi_url'),
        sapi_url=config.get('sapi_url'),
        conjur_url=config.get('conjur_url'),
        test_client_id=config.get('test_client_id'),
        test_client_secret=config.get('test_client_secret'),
        test_data_dir=config.get('test_data_dir'),
        test_report_dir=config.get('test_report_dir'),
        additional_config=additional_config
    )
    
    return test_config


def get_performance_test_config(config_file=None, environment=None):
    """
    Creates a PerformanceTestConfig instance from configuration sources
    
    Args:
        config_file (str): Path to configuration file
        environment (str): Environment name for specific configuration
        
    Returns:
        PerformanceTestConfig: Configured PerformanceTestConfig instance
    """
    # Start with default configuration
    config = {
        'duration_seconds': 60,
        'concurrent_users': 10,
        'ramp_up_seconds': 5,
        'thresholds': PERFORMANCE_TEST_THRESHOLDS.copy(),
    }
    
    # Load from file if provided
    if config_file:
        file_config = load_config_from_file(config_file)
        if 'performance_test' in file_config:
            perf_config = file_config['performance_test']
            config.update(perf_config)
    
    # Load from environment variables
    env_config = load_config_from_env('PAYMENT_PERF_TEST_')
    config.update(env_config)
    
    # Apply environment-specific config if provided
    if environment and 'environments' in config and environment in config['environments']:
        env_specific_config = config['environments'][environment]
        config.update(env_specific_config)
    
    # Extract known keys for PerformanceTestConfig and store the rest in additional_config
    known_keys = ['duration_seconds', 'concurrent_users', 'ramp_up_seconds', 'thresholds']
    additional_config = {k: v for k, v in config.items() if k not in known_keys}
    
    # Create PerformanceTestConfig instance
    perf_test_config = PerformanceTestConfig(
        duration_seconds=config.get('duration_seconds'),
        concurrent_users=config.get('concurrent_users'),
        ramp_up_seconds=config.get('ramp_up_seconds'),
        thresholds=config.get('thresholds'),
        additional_config=additional_config
    )
    
    return perf_test_config


def generate_test_report_path(test_type, format='json'):
    """
    Generates a file path for test reports
    
    Args:
        test_type (str): Type of test (e.g., 'unit', 'integration', 'performance')
        format (str): Report format (e.g., 'json', 'xml', 'html')
        
    Returns:
        str: Generated file path for test report
    """
    # Ensure report directory exists
    report_dir = Path(DEFAULT_TEST_REPORT_DIR)
    report_dir.mkdir(parents=True, exist_ok=True)
    
    # Generate timestamp for unique filenames
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    
    # Create filename
    filename = f"{test_type}_report_{timestamp}.{format}"
    
    # Return full path
    return str(report_dir / filename)


def ensure_directory_exists(directory_path):
    """
    Ensures that a directory exists, creating it if necessary
    
    Args:
        directory_path (str): Path to directory
        
    Returns:
        bool: True if directory exists or was created successfully
    """
    try:
        Path(directory_path).mkdir(parents=True, exist_ok=True)
        return True
    except Exception as e:
        LOGGER.error(f"Error creating directory {directory_path}: {str(e)}")
        return False


@dataclass
class TestConfig:
    """
    Configuration class for test execution
    """
    eapi_url: str = None
    sapi_url: str = None 
    conjur_url: str = None
    test_client_id: str = None
    test_client_secret: str = None
    test_data_dir: str = DEFAULT_TEST_DATA_DIR
    test_report_dir: str = DEFAULT_TEST_REPORT_DIR
    additional_config: dict = None
    
    def __post_init__(self):
        """Initialize additional_config if not provided"""
        if self.additional_config is None:
            self.additional_config = {}
    
    def validate(self):
        """
        Validates the configuration values
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        valid = True
        
        if not self.eapi_url:
            LOGGER.error("EAPI URL is not configured")
            valid = False
        
        if not self.sapi_url:
            LOGGER.error("SAPI URL is not configured")
            valid = False
            
        if not self.test_client_id:
            LOGGER.error("Test client ID is not configured")
            valid = False
            
        if not self.test_client_secret:
            LOGGER.error("Test client secret is not configured")
            valid = False
            
        return valid
    
    def to_dict(self):
        """
        Converts the configuration to a dictionary
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        return {
            'eapi_url': self.eapi_url,
            'sapi_url': self.sapi_url,
            'conjur_url': self.conjur_url,
            'test_client_id': self.test_client_id,
            'test_client_secret': self.test_client_secret,
            'test_data_dir': self.test_data_dir,
            'test_report_dir': self.test_report_dir,
            **self.additional_config
        }
    
    @classmethod
    def from_dict(cls, config_dict):
        """
        Creates a TestConfig instance from a dictionary
        
        Args:
            config_dict (dict): Configuration dictionary
            
        Returns:
            TestConfig: TestConfig instance
        """
        known_keys = ['eapi_url', 'sapi_url', 'conjur_url', 'test_client_id', 
                     'test_client_secret', 'test_data_dir', 'test_report_dir']
        
        # Extract known keys
        extracted = {k: config_dict.get(k) for k in known_keys if k in config_dict}
        
        # Store remaining keys in additional_config
        additional_config = {k: v for k, v in config_dict.items() if k not in known_keys}
        
        return cls(**extracted, additional_config=additional_config)


@dataclass
class PerformanceTestConfig:
    """
    Configuration class for performance testing
    """
    duration_seconds: int = 60
    concurrent_users: int = 10
    ramp_up_seconds: int = 5
    thresholds: dict = None
    additional_config: dict = None
    
    def __post_init__(self):
        """Initialize dictionaries if not provided"""
        if self.thresholds is None:
            self.thresholds = PERFORMANCE_TEST_THRESHOLDS.copy()
        if self.additional_config is None:
            self.additional_config = {}
    
    def validate(self):
        """
        Validates the performance test configuration values
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        valid = True
        
        if self.duration_seconds <= 0:
            LOGGER.error("Duration must be a positive value")
            valid = False
            
        if self.concurrent_users <= 0:
            LOGGER.error("Concurrent users must be a positive value")
            valid = False
            
        if self.ramp_up_seconds >= self.duration_seconds:
            LOGGER.error("Ramp-up time must be less than total duration")
            valid = False
            
        required_thresholds = ['p95_response_time', 'p99_response_time', 'error_rate']
        for threshold in required_thresholds:
            if threshold not in self.thresholds:
                LOGGER.error(f"Required threshold '{threshold}' is missing")
                valid = False
                
        return valid
    
    def to_dict(self):
        """
        Converts the performance test configuration to a dictionary
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        return {
            'duration_seconds': self.duration_seconds,
            'concurrent_users': self.concurrent_users,
            'ramp_up_seconds': self.ramp_up_seconds,
            'thresholds': self.thresholds,
            **self.additional_config
        }
    
    @classmethod
    def from_dict(cls, config_dict):
        """
        Creates a PerformanceTestConfig instance from a dictionary
        
        Args:
            config_dict (dict): Configuration dictionary
            
        Returns:
            PerformanceTestConfig: PerformanceTestConfig instance
        """
        known_keys = ['duration_seconds', 'concurrent_users', 'ramp_up_seconds', 'thresholds']
        
        # Extract known keys
        extracted = {k: config_dict.get(k) for k in known_keys if k in config_dict}
        
        # Store remaining keys in additional_config
        additional_config = {k: v for k, v in config_dict.items() if k not in known_keys}
        
        return cls(**extracted, additional_config=additional_config)