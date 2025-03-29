#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import io
from setuptools import setup, find_packages  # version 58.1.0

# Get the absolute path of the directory containing this file
here = os.path.abspath(os.path.dirname(__file__))

def read(file_path):
    """
    Reads the content of a file and returns it as a string.
    
    Args:
        file_path (str): Path to the file to read
        
    Returns:
        str: Content of the file
    """
    with io.open(os.path.join(here, file_path), encoding='utf-8') as f:
        return f.read()

def parse_requirements(file_path):
    """
    Parses requirements from requirements.txt file.
    
    Args:
        file_path (str): Path to the requirements file
        
    Returns:
        list: List of package requirements
    """
    content = read(file_path)
    requirements = []
    for line in content.split('\n'):
        line = line.strip()
        if line and not line.startswith('#'):
            requirements.append(line)
    return requirements

# Read the README file
try:
    README = read('../README.md')
except FileNotFoundError:
    README = read('../../README.md')

# Get the list of requirements
try:
    REQUIREMENTS = parse_requirements('../requirements.txt')
except FileNotFoundError:
    REQUIREMENTS = parse_requirements('../../requirements.txt')

setup(
    name='payment-api-security-scripts',
    version='1.0.0',
    description='Utility scripts for Payment API Security Enhancement project',
    long_description=README,
    long_description_content_type='text/markdown',
    author='Payment API Security Team',
    author_email='security@example.com',
    url='https://github.com/organization/payment-api-security-enhancement',
    packages=find_packages(where='src/scripts'),
    package_dir={'': 'src/scripts'},
    install_requires=REQUIREMENTS,
    python_requires='>=3.9',
    entry_points={
        'console_scripts': [
            # Conjur vault integration scripts
            'conjur-auth=conjur.authenticate:main',
            'conjur-retrieve=conjur.retrieve_credentials:main',
            'conjur-rotate=conjur.rotate_credentials:main',
            'conjur-setup=conjur.setup_vault:main',
            
            # Monitoring scripts
            'monitor-health=monitoring.health_check:main',
            'token-metrics=monitoring.token_usage_metrics:main',
            'credential-metrics=monitoring.credential_usage_metrics:main',
            
            # Deployment scripts
            'setup-env=deployment.setup_environments:main',
            'backup-metadata=deployment.backup_metadata:main',
            'restore-metadata=deployment.restore_metadata:main',
            'sync-env=deployment.sync_environments:main',
            
            # Testing scripts
            'test-auth=testing.test_authentication:main',
            'test-token=testing.test_token_generation:main',
            'test-rotation=testing.test_credential_rotation:main',
            'load-test=testing.load_test_auth:main',
            
            # Utility scripts
            'cleanup-tokens=utilities.cleanup_expired_tokens:main',
            'generate-creds=utilities.generate_test_credentials:main',
            'validate-tokens=utilities.validate_tokens:main',
            'db-maintenance=utilities.database_maintenance:main',
        ],
    },
    classifiers=[
        'Development Status :: 4 - Beta',
        'Intended Audience :: Developers',
        'Topic :: Security',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.9',
        'License :: OSI Approved :: MIT License',
        'Operating System :: OS Independent',
    ],
)