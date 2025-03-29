# Payment API Security Enhancement

Backend implementation for enhancing the security of the payment API authentication mechanism while maintaining backward compatibility with existing vendor integrations.

## Project Overview

This project implements a secure authentication mechanism using Conjur vault for credential management and token-based authentication for internal service communication, while maintaining the existing API contract with vendors.

### Key Features

- Secure credential storage using Conjur vault
- Token-based authentication for internal service communication
- Backward compatibility with existing vendor integrations
- Credential rotation without service disruption
- Comprehensive monitoring and observability

### Architecture

The system follows a layered architecture with clear separation between external-facing and internal components:

- **Payment-Eapi**: External-facing API that authenticates vendor requests using Client ID/Secret headers and forwards them to internal services using JWT tokens
- **Payment-Sapi**: Internal service that processes payment transactions with token-based authentication
- **Credential Rotation**: Service for managing secure credential rotation without disrupting existing integrations
- **Monitoring**: Service for monitoring, metrics collection, and alerting
- **Common**: Shared utilities, models, and services used across other modules

## Project Structure

The backend is organized as a multi-module Maven project with the following components:

### Modules

- **common**: Shared utilities, models, and services for authentication, token handling, security, and monitoring
- **payment-eapi**: External-facing API service that implements backward compatibility with existing vendor integrations
- **payment-sapi**: Internal service that processes payment transactions with token-based authentication
- **credential-rotation**: Service for managing secure credential rotation
- **monitoring**: Service for monitoring, metrics collection, and alerting

### Key Files

- **pom.xml**: Parent POM file defining project structure and common dependencies
- **Dockerfile-eapi**: Docker configuration for the Payment-Eapi service
- **Dockerfile-sapi**: Docker configuration for the Payment-Sapi service
- **Dockerfile-rotation**: Docker configuration for the Credential Rotation service
- **Dockerfile-monitoring**: Docker configuration for the Monitoring service
- **docker-compose.yml**: Docker Compose configuration for local development
- **kubernetes/**: Kubernetes deployment configurations

## Prerequisites

- Java 11 or later
- Maven 3.8.x or later
- Docker and Docker Compose
- Kubernetes (for production deployment)
- Conjur vault instance
- Redis instance
- PostgreSQL database

## Getting Started

Follow these steps to set up and run the project locally:

### Building the Project

```bash
# Clone the repository
git clone <repository-url>
cd payment-api-security/src/backend

# Build the project
./mvnw clean package
```

### Running with Docker Compose

```bash
# Start all services
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

### Running Individual Services

```bash
# Run Payment-Eapi
./mvnw spring-boot:run -pl payment-eapi -Dspring-boot.run.profiles=dev

# Run Payment-Sapi
./mvnw spring-boot:run -pl payment-sapi -Dspring-boot.run.profiles=dev

# Run Credential Rotation service
./mvnw spring-boot:run -pl credential-rotation -Dspring-boot.run.profiles=dev

# Run Monitoring service
./mvnw spring-boot:run -pl monitoring -Dspring-boot.run.profiles=dev
```

### Conjur Vault Setup

1. Install and configure Conjur vault
2. Create the necessary policies for credential storage
3. Store initial credentials for testing
4. Configure the application to connect to Conjur vault

Refer to the `docs/operations/credential-rotation-runbook.md` for detailed instructions.

## Configuration

The application uses Spring Boot's configuration system with environment-specific profiles:

### Environment Profiles

- **dev**: Development environment configuration
- **test**: Testing environment configuration
- **staging**: Staging environment configuration
- **prod**: Production environment configuration

### Configuration Files

- **application.yml**: Common configuration properties
- **application-{profile}.yml**: Environment-specific configuration properties

### Key Configuration Properties

```yaml
# Conjur Vault Configuration
conjur:
  url: https://conjur.example.com
  account: payment-system
  authn-login: payment-eapi-service
  ssl-certificate: /path/to/conjur/certificate.pem
  connection-timeout: 5000
  read-timeout: 3000
  retry-count: 3
  retry-backoff-multiplier: 1.5

# Token Configuration
token:
  issuer: payment-eapi
  audience: payment-sapi
  expiration-seconds: 3600
  renewal-enabled: true
  signing-key-path: conjur/path/to/signing-key

# Redis Configuration
spring.redis:
  host: redis.example.com
  port: 6379
  password: ${REDIS_PASSWORD}
  ssl: true
  timeout: 2000
  database: 0
```

## API Documentation

The API documentation is available through Swagger UI when running the application:

### Payment-Eapi

- **URL**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

### Payment-Sapi

- **URL**: http://localhost:8081/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8081/v3/api-docs

### Credential Rotation

- **URL**: http://localhost:8082/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8082/v3/api-docs

## Testing

The project includes comprehensive test coverage using JUnit 5, Mockito, and TestContainers:

### Running Tests

```bash
# Run all tests
./mvnw test

# Run tests for a specific module
./mvnw test -pl payment-eapi

# Run tests with coverage report
./mvnw test jacoco:report
```

### Test Categories

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test interactions between components
- **End-to-End Tests**: Test complete workflows across all components

### Test Configuration

Tests use the `test` profile with TestContainers for database, Redis, and other dependencies.

## Deployment

The application can be deployed using Docker and Kubernetes:

### Building Docker Images

```bash
# Build all images
docker build -f Dockerfile-eapi -t payment-eapi:latest .
docker build -f Dockerfile-sapi -t payment-sapi:latest .
docker build -f Dockerfile-rotation -t credential-rotation:latest .
docker build -f Dockerfile-monitoring -t monitoring:latest .
```

### Kubernetes Deployment

```bash
# Apply Kubernetes configurations
kubectl apply -f kubernetes/

# Check deployment status
kubectl get pods
```

### Deployment Environments

- **Development**: Local environment for development and testing
- **Staging**: Pre-production environment for validation
- **Production**: Production environment with high availability

## Monitoring

The application includes comprehensive monitoring and observability features:

### Metrics

- **Prometheus Endpoint**: http://localhost:8080/actuator/prometheus
- **Key Metrics**: Authentication success/failure rates, token generation/validation times, API response times

### Health Checks

- **Health Endpoint**: http://localhost:8080/actuator/health
- **Components**: Application health, database connectivity, Redis connectivity, Conjur vault connectivity

### Logging

- **Log Format**: JSON format for structured logging
- **Log Levels**: Configurable via application properties
- **Key Log Events**: Authentication events, token operations, credential access

## Security Considerations

The application implements several security measures:

### Authentication

- **External**: Client ID/Secret in headers for backward compatibility
- **Internal**: JWT tokens for secure service-to-service communication
- **Vault**: Certificate-based authentication to Conjur vault

### Encryption

- **Data in Transit**: TLS 1.2+ for all communications
- **Tokens**: HMAC SHA-256 signatures for JWT tokens
- **Sensitive Data**: Field-level encryption for sensitive data

### Credential Management

- **Storage**: Secure storage in Conjur vault
- **Rotation**: Zero-downtime credential rotation
- **Access Control**: Policy-based access control for credentials

## Contributing

Contributions to the project are welcome. Please follow these guidelines:

### Development Workflow

1. Create a feature branch from `develop`
2. Implement your changes with appropriate tests
3. Ensure all tests pass and code quality checks succeed
4. Submit a pull request for review

### Code Standards

- Follow Java code conventions
- Maintain test coverage above 85%
- Document public APIs with Javadoc
- Use meaningful commit messages

## Troubleshooting

Common issues and their solutions:

### Connection Issues

- **Conjur Vault**: Verify network connectivity, certificate validity, and authentication credentials
- **Redis**: Check connection settings and authentication
- **Database**: Verify connection settings and schema migrations

### Authentication Failures

- **Client Credentials**: Verify Client ID and Client Secret are correct
- **Token Validation**: Check token expiration, signature, and claims
- **Conjur Authentication**: Verify service account credentials and permissions

### Performance Issues

- **High Latency**: Check resource utilization, connection pooling, and caching configuration
- **Memory Usage**: Monitor heap usage and garbage collection metrics
- **Database Performance**: Review query performance and indexing

## License

This project is licensed under the [License Name] - see the LICENSE file for details.

## Additional Resources

- [Project Documentation](../docs/README.md)
- [API Documentation](../docs/api/)
- [Architecture Documentation](../docs/architecture/)
- [Operations Guide](../docs/operations/)
- [Development Guide](../docs/development/)