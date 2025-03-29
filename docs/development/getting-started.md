# Getting Started with Payment API Security Enhancement

## Introduction

This document provides a comprehensive guide for developers to get started with the Payment API Security Enhancement project. It covers environment setup, project structure, build instructions, and development workflows.

The Payment API Security Enhancement project aims to strengthen the security of the existing payment processing system by implementing more secure authentication mechanisms while maintaining backward compatibility with current vendor integrations. The project leverages Conjur vault for credential management and token-based authentication for internal service communication.

## Project Overview

The Payment API Security Enhancement project implements a secure authentication mechanism using Conjur vault for credential management and token-based authentication for internal service communication, while maintaining the existing API contract with vendors.

### Key Features

- Secure credential storage using Conjur vault
- Token-based authentication for internal service communication
- Backward compatibility with existing vendor integrations
- Credential rotation without service disruption
- Comprehensive monitoring and observability

### Architecture

The system follows a layered architecture with clear separation between external-facing and internal components:

- **Payment-EAPI**: External-facing API that authenticates vendor requests using Client ID/Secret headers and forwards them to internal services using JWT tokens
- **Payment-SAPI**: Internal service that processes payment transactions with token-based authentication
- **Credential Rotation**: Service for managing secure credential rotation without disrupting existing integrations
- **Monitoring**: Service for monitoring, metrics collection, and alerting
- **Common**: Shared utilities, models, and services used across other modules

For more detailed information about the architecture, refer to the [High-Level Architecture](../architecture/high-level-architecture.md) document.

### Technology Stack

The project uses the following technology stack:

- **Programming Language**: Java 11
- **Framework**: Spring Boot 2.6.x with Spring Security 5.6.x
- **Build Tool**: Maven 3.8.x
- **API Documentation**: Swagger/OpenAPI 3.0
- **Token Management**: JJWT (Java JWT) 0.11.x
- **Credential Management**: Conjur Vault with Java client
- **Caching**: Redis 6.2.x
- **Database**: PostgreSQL 13.x
- **Containerization**: Docker with Kubernetes orchestration
- **Monitoring**: Prometheus, Grafana, ELK Stack
- **CI/CD**: Jenkins, Maven, SonarQube

## Development Environment Setup

This section provides instructions for setting up your development environment for the Payment API Security Enhancement project.

### Prerequisites

Ensure you have the following tools installed on your development machine:

- Java 11 or later
- Maven 3.8.x or later
- Docker and Docker Compose
- Git
- IDE of your choice (IntelliJ IDEA or Eclipse recommended)
- Postman or similar API testing tool

Optional tools for enhanced development experience:

- Kubernetes CLI (kubectl) for Kubernetes deployment
- Redis CLI for Redis cache inspection
- PostgreSQL client for database operations

### Repository Setup

Clone the repository and set up your local environment:

```bash
# Clone the repository
git clone https://github.com/your-organization/payment-api-security.git
cd payment-api-security

# Set up Git hooks (optional)
./scripts/setup-git-hooks.sh
```

### IDE Configuration

Configure your IDE for optimal development experience:

**IntelliJ IDEA**:
1. Import the project as a Maven project
2. Install the Lombok plugin and enable annotation processing
3. Install the CheckStyle plugin and import the project's checkstyle.xml
4. Install the SonarLint plugin for real-time code quality feedback

**Eclipse**:
1. Import the project as a Maven project
2. Install the Lombok plugin and enable annotation processing
3. Install the CheckStyle plugin and import the project's checkstyle.xml
4. Install the SonarLint plugin for real-time code quality feedback

Recommended IDE settings:
- Use spaces for indentation (4 spaces)
- Enable automatic import optimization
- Set line ending to LF (Unix-style)
- Set file encoding to UTF-8

### Local Environment Setup

Set up your local development environment using Docker Compose:

```bash
# Start the local development environment
cd src/backend
docker-compose up -d

# Verify that all services are running
docker-compose ps
```

This will start the following services:
- PostgreSQL database
- Redis cache
- Conjur vault (development instance)
- Mock services for development

You can access the services at the following URLs:
- Conjur UI: http://localhost:8080/ui
- Redis Commander: http://localhost:8081
- PostgreSQL: localhost:5432

Default credentials for development environment:
- Conjur admin: admin/admin
- PostgreSQL: postgres/postgres
- Redis: No authentication in development mode

### Environment Configuration

Configure your environment variables for local development:

**Linux/macOS**:
```bash
# Create a .env file for development
cp .env.example .env

# Edit the .env file with your local settings
vim .env

# Load the environment variables
source .env
```

**Windows**:
```powershell
# Create a .env file for development
copy .env.example .env

# Edit the .env file with your local settings
notepad .env

# Load the environment variables (PowerShell)
Get-Content .env | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }
```

Alternatively, you can configure these settings in your IDE's run configurations.

### Conjur Vault Setup

Set up Conjur vault for local development:

```bash
# Initialize Conjur vault with development policies
cd src/scripts/conjur
./setup-vault.py --env dev
```

This script will:
1. Initialize the Conjur vault instance
2. Load the necessary policies for development
3. Create test credentials for development
4. Configure the necessary permissions

You can verify the setup by accessing the Conjur UI at http://localhost:8080/ui and logging in with the admin credentials.

## Project Structure

The Payment API Security Enhancement project is organized as a multi-module Maven project with a clear separation of concerns.

### Repository Structure

The repository is organized as follows:

```
payment-api-security/
├── .github/                  # GitHub workflows and templates
├── docs/                     # Project documentation
│   ├── api/                  # API documentation
│   ├── architecture/         # Architecture documentation
│   ├── development/          # Development guides
│   └── operations/           # Operational guides
├── infrastructure/           # Infrastructure configuration
│   ├── conjur/               # Conjur vault configuration
│   ├── elk/                  # ELK stack configuration
│   ├── grafana/              # Grafana dashboards
│   ├── prometheus/           # Prometheus configuration
│   └── docker-compose.yml    # Docker Compose for infrastructure
├── src/                      # Source code
│   ├── backend/              # Backend services
│   └── scripts/              # Utility scripts
└── README.md                 # Project overview
```

### Backend Modules

The backend is organized as a multi-module Maven project:

```
src/backend/
├── common/                  # Shared utilities and models
├── payment-eapi/            # External API service
├── payment-sapi/            # Internal service
├── credential-rotation/     # Credential rotation service
├── monitoring/              # Monitoring service
├── kubernetes/              # Kubernetes deployment files
├── scripts/                 # Build and deployment scripts
├── terraform/               # Infrastructure as code
├── docker-compose.yml       # Local development environment
└── pom.xml                  # Parent POM file
```

Each module follows a standard Maven project structure with src/main/java, src/main/resources, and src/test directories.

### Common Module

The common module contains shared utilities, models, and services used across other modules:

```
common/
├── src/main/java/com/payment/common/
│   ├── config/              # Common configuration classes
│   ├── exception/           # Common exception classes
│   ├── model/               # Shared data models
│   ├── monitoring/          # Monitoring utilities
│   └── util/                # Utility classes
└── pom.xml                  # Module POM file
```

Key components in the common module:
- **TokenGenerator**: Utility for generating JWT tokens
- **TokenValidator**: Utility for validating JWT tokens
- **SecurityUtils**: Security-related utility functions
- **TokenClaims**: Model for JWT token claims
- **ErrorResponse**: Standardized error response model

### Payment-EAPI Module

The payment-eapi module implements the external-facing API that maintains backward compatibility with existing vendor integrations:

```
payment-eapi/
├── src/main/java/com/payment/eapi/
│   ├── config/              # Configuration classes
│   ├── controller/          # REST controllers
│   ├── exception/           # Exception handling
│   ├── filter/              # Request filters
│   ├── model/               # Data models
│   ├── repository/          # Data repositories
│   ├── service/             # Service interfaces
│   ├── service/impl/        # Service implementations
│   └── util/                # Utility classes
├── src/main/resources/      # Configuration files
└── pom.xml                  # Module POM file
```

Key components in the payment-eapi module:
- **AuthenticationController**: Handles authentication requests
- **PaymentController**: Handles payment requests
- **ClientCredentialsAuthenticationFilter**: Authenticates requests using Client ID/Secret headers
- **ConjurService**: Interfaces with Conjur vault for credential management
- **TokenService**: Generates and manages JWT tokens
- **ForwardingService**: Forwards authenticated requests to Payment-SAPI

### Payment-SAPI Module

The payment-sapi module implements the internal service that processes payment transactions with token-based authentication:

```
payment-sapi/
├── src/main/java/com/payment/sapi/
│   ├── config/              # Configuration classes
│   ├── controller/          # REST controllers
│   ├── exception/           # Exception handling
│   ├── filter/              # Request filters
│   ├── model/               # Data models
│   ├── repository/          # Data repositories
│   ├── service/             # Service interfaces
│   ├── service/impl/        # Service implementations
│   └── util/                # Utility classes
├── src/main/resources/      # Configuration files
└── pom.xml                  # Module POM file
```

Key components in the payment-sapi module:
- **PaymentController**: Handles payment processing requests
- **TokenController**: Handles token validation and renewal
- **TokenAuthenticationFilter**: Authenticates requests using JWT tokens
- **TokenValidationService**: Validates JWT tokens
- **PaymentService**: Processes payment transactions
- **AuditService**: Logs security events for audit purposes

### Credential Rotation Module

The credential-rotation module implements the credential rotation service:

```
credential-rotation/
├── src/main/java/com/payment/rotation/
│   ├── config/              # Configuration classes
│   ├── controller/          # REST controllers
│   ├── model/               # Data models
│   ├── scheduler/           # Scheduled tasks
│   ├── service/             # Service interfaces
│   └── service/impl/        # Service implementations
├── src/main/resources/      # Configuration files
└── pom.xml                  # Module POM file
```

Key components in the credential-rotation module:
- **RotationController**: Handles credential rotation requests
- **RotationService**: Manages credential rotation process
- **ConjurService**: Interfaces with Conjur vault for credential management
- **NotificationService**: Sends notifications about rotation events
- **RotationScheduler**: Schedules automatic credential rotation

### Monitoring Module

The monitoring module implements the monitoring service:

```
monitoring/
├── src/main/java/com/payment/monitoring/
│   ├── config/              # Configuration classes
│   ├── controller/          # REST controllers
│   ├── service/             # Service interfaces
│   └── service/impl/        # Service implementations
├── src/main/resources/      # Configuration files
└── pom.xml                  # Module POM file
```

Key components in the monitoring module:
- **MetricsController**: Exposes metrics endpoints
- **AlertService**: Manages alerts based on metrics
- **MetricsCollectionService**: Collects metrics from various sources

## Build and Run Instructions

This section provides instructions for building and running the Payment API Security Enhancement project.

### Building the Project

Build the project using Maven:

```bash
# Navigate to the backend directory
cd src/backend

# Build all modules
./mvnw clean package

# Build a specific module
./mvnw clean package -pl payment-eapi

# Build with skipping tests
./mvnw clean package -DskipTests

# Build with code coverage report
./mvnw clean package jacoco:report
```

The build artifacts (JAR files) will be created in the target directory of each module.

### Running with Docker Compose

Run the project using Docker Compose for local development:

```bash
# Navigate to the backend directory
cd src/backend

# Build Docker images
docker-compose build

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# View logs for a specific service
docker-compose logs -f payment-eapi

# Stop all services
docker-compose down
```

This will start all the necessary services including Payment-EAPI, Payment-SAPI, Credential Rotation service, Monitoring service, Redis Cache, PostgreSQL database, and Conjur vault.

### Running Individual Services

Run individual services directly using Maven for development and debugging:

```bash
# Navigate to the backend directory
cd src/backend

# Run Payment-EAPI
./mvnw spring-boot:run -pl payment-eapi -Dspring-boot.run.profiles=dev

# Run Payment-SAPI
./mvnw spring-boot:run -pl payment-sapi -Dspring-boot.run.profiles=dev

# Run Credential Rotation service
./mvnw spring-boot:run -pl credential-rotation -Dspring-boot.run.profiles=dev

# Run Monitoring service
./mvnw spring-boot:run -pl monitoring -Dspring-boot.run.profiles=dev
```

Note that when running services individually, you need to ensure that the required dependencies (Redis, PostgreSQL, Conjur vault) are available. You can start these dependencies using Docker Compose:

```bash
# Start only the dependencies
docker-compose up -d postgres redis conjur
```

### Running Tests

Run tests using Maven:

```bash
# Run all tests
./mvnw test

# Run tests for a specific module
./mvnw test -pl payment-eapi

# Run a specific test class
./mvnw test -pl payment-eapi -Dtest=AuthenticationServiceTest

# Run with code coverage report
./mvnw test jacoco:report
```

The project includes several types of tests:
- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test interactions between components
- **End-to-End Tests**: Test complete workflows across all components

Integration tests use TestContainers to spin up required dependencies (Redis, PostgreSQL, etc.) automatically.

### API Documentation

Access the API documentation using Swagger UI:

1. Start the services using Docker Compose or Maven
2. Access the Swagger UI for each service:
   - Payment-EAPI: http://localhost:8080/swagger-ui.html
   - Payment-SAPI: http://localhost:8081/swagger-ui.html
   - Credential Rotation: http://localhost:8083/swagger-ui.html
   - Monitoring: http://localhost:8084/swagger-ui.html

The OpenAPI specifications are also available at:
- Payment-EAPI: http://localhost:8080/v3/api-docs
- Payment-SAPI: http://localhost:8081/v3/api-docs
- Credential Rotation: http://localhost:8083/v3/api-docs
- Monitoring: http://localhost:8084/v3/api-docs

### Debugging

Debug the application using your IDE:

**IntelliJ IDEA**:
1. Create a Remote JVM Debug configuration
2. Set the host to localhost and port to the debug port (default: 5005)
3. Start the service with debugging enabled:
   ```bash
   ./mvnw spring-boot:run -pl payment-eapi -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
   ```
4. Start the debugger in IntelliJ IDEA

**Eclipse**:
1. Create a Remote Java Application debug configuration
2. Set the host to localhost and port to the debug port (default: 5005)
3. Start the service with debugging enabled (same command as above)
4. Start the debugger in Eclipse

Alternatively, you can debug the application using Docker Compose by adding the debug configuration to the docker-compose.yml file and exposing the debug port.

## Development Workflow

This section describes the recommended development workflow for the Payment API Security Enhancement project.

### Branching Strategy

The project follows a modified Git Flow branching strategy:

- `main`: Production-ready code
- `develop`: Integration branch for feature development
- `feature/*`: Feature branches for new features
- `bugfix/*`: Bugfix branches for bug fixes
- `release/*`: Release branches for release preparation
- `hotfix/*`: Hotfix branches for urgent production fixes

Workflow:
1. Create a feature or bugfix branch from `develop`
2. Implement your changes with appropriate tests
3. Submit a pull request to merge back to `develop`
4. After review and approval, the changes are merged to `develop`
5. Periodically, a release branch is created from `develop`
6. After testing, the release branch is merged to `main` and tagged with a version number

For urgent fixes, create a hotfix branch from `main`, implement the fix, and merge back to both `main` and `develop`.

### Code Standards

The project follows these coding standards and practices:

- Use the Google Java Style Guide with project-specific modifications
- Follow the package structure and naming conventions
- Use appropriate design patterns and architectural principles
- Write comprehensive unit and integration tests
- Document public APIs with Javadoc
- Follow security best practices as defined in the [Security Guidelines](security-guidelines.md) document

The project uses the following tools to enforce code standards:
- Checkstyle for style checking
- SpotBugs for static analysis
- SonarQube for code quality analysis
- OWASP Dependency Check for security vulnerability scanning

### Pull Request Process

Follow these steps when submitting a pull request:

1. Ensure your code follows the project's code standards
2. Write appropriate tests for your changes
3. Update documentation as necessary
4. Run the build locally to ensure it passes
5. Create a pull request with a clear description of the changes
6. Reference any related issues in the pull request description
7. Respond to review comments and make necessary changes
8. Once approved, the pull request will be merged

Pull requests are automatically built and tested by the CI/CD pipeline. The pipeline runs the following checks:
- Build and unit tests
- Code style checks
- Static analysis
- Security vulnerability scanning
- Integration tests

All checks must pass before a pull request can be merged.

### Testing Strategy

Follow these testing guidelines:

- Write unit tests for all business logic
- Write integration tests for critical paths
- Use TestContainers for integration tests that require external dependencies
- Follow the AAA pattern (Arrange, Act, Assert) for test structure
- Use descriptive test method names that explain the test scenario and expected outcome
- Test both positive and negative scenarios
- Aim for at least 85% code coverage

The project uses the following testing frameworks:
- JUnit 5 for unit and integration testing
- Mockito for mocking dependencies
- AssertJ for fluent assertions
- TestContainers for integration testing with real dependencies
- Spring Boot Test for Spring component testing

### Security Considerations

Follow these security guidelines when developing:

- Never store credentials in code or configuration files
- Use Conjur vault for credential storage and retrieval
- Implement proper token validation for all protected endpoints
- Apply the principle of least privilege
- Never log sensitive data (credentials, tokens, personal information)
- Use encryption for sensitive data at rest and in transit
- Implement proper input validation to prevent injection attacks
- Sanitize data before using it in logs, error messages, or responses

Refer to the [Security Guidelines](security-guidelines.md) document for detailed security guidelines.

### Logging and Monitoring

Follow these logging and monitoring guidelines:

- Use SLF4J with Logback for logging
- Use the `@Slf4j` Lombok annotation to create loggers
- Use appropriate log levels for different event types:
  - ERROR: Use for errors that prevent normal operation
  - WARN: Use for unexpected conditions that don't prevent operation
  - INFO: Use for significant events in normal operation
  - DEBUG: Use for detailed information useful for debugging
  - TRACE: Use for very detailed debugging information
- Include relevant context in log messages (request ID, user ID, etc.)
- Never log sensitive information (credentials, tokens, personal information)
- Use structured logging (JSON format) in production

For monitoring:
- Expose metrics using Micrometer and Prometheus
- Create appropriate dashboards in Grafana
- Set up alerts for critical conditions
- Monitor authentication success/failure rates, token operations, and API performance

## Common Development Tasks

This section provides guidance for common development tasks in the Payment API Security Enhancement project.

### Adding a New API Endpoint

Follow these steps to add a new API endpoint:

1. Define the request and response models in the appropriate module
2. Create or update the controller to handle the new endpoint
3. Implement the service logic for the endpoint
4. Add appropriate authentication and authorization checks
5. Write unit and integration tests for the endpoint
6. Update the API documentation with Swagger annotations
7. Update any relevant documentation

Example controller method:

```java
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment API", description = "Payment processing endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Process a payment", description = "Processes a payment transaction")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestBody @Valid PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }
}
```

### Implementing Authentication Logic

Follow these steps to implement authentication logic:

1. Use the existing authentication framework in the Payment-EAPI module
2. For header-based authentication, use the ClientCredentialsAuthenticationFilter
3. For token-based authentication, use the TokenAuthenticationFilter
4. Implement proper error handling for authentication failures
5. Follow the security guidelines for credential validation and token generation
6. Write comprehensive tests for authentication logic

Example authentication service implementation:

```java
@Service
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final ConjurService conjurService;
    private final TokenService tokenService;
    private final CacheService cacheService;

    public AuthenticationServiceImpl(ConjurService conjurService, 
                                    TokenService tokenService,
                                    CacheService cacheService) {
        this.conjurService = conjurService;
        this.tokenService = tokenService;
        this.cacheService = cacheService;
    }

    @Override
    public AuthenticationResponse authenticate(String clientId, String clientSecret) {
        // Check for cached token
        Token cachedToken = cacheService.getToken(clientId);
        if (cachedToken != null && !tokenService.isTokenExpired(cachedToken)) {
            log.debug("Using cached token for client ID: {}", maskClientId(clientId));
            return createAuthenticationResponse(cachedToken);
        }

        // Retrieve credentials from Conjur vault
        Credential credential = conjurService.getCredential(clientId);
        if (credential == null) {
            log.warn("Authentication failed: Unknown client ID: {}", maskClientId(clientId));
            throw new AuthenticationException("Invalid credentials");
        }

        // Validate credentials
        if (!conjurService.validateCredentials(clientId, clientSecret, credential)) {
            log.warn("Authentication failed: Invalid credentials for client ID: {}", 
                    maskClientId(clientId));
            throw new AuthenticationException("Invalid credentials");
        }

        // Generate token
        Token token = tokenService.generateToken(clientId);

        // Cache token
        cacheService.cacheToken(clientId, token);

        log.info("Authentication successful for client ID: {}", maskClientId(clientId));
        return createAuthenticationResponse(token);
    }

    private AuthenticationResponse createAuthenticationResponse(Token token) {
        return AuthenticationResponse.builder()
                .token(token.getTokenString())
                .expiresAt(token.getExpirationTime().toString())
                .build();
    }

    private String maskClientId(String clientId) {
        // Implementation of client ID masking for logging
    }
}
```

### Working with Conjur Vault

Follow these steps to work with Conjur vault:

1. Use the ConjurService in the Payment-EAPI module for credential management
2. Follow the security guidelines for credential storage and retrieval
3. Implement proper error handling for Conjur vault operations
4. Use the circuit breaker pattern for resilience
5. Implement caching to reduce load on Conjur vault

Example Conjur service implementation:

```java
@Service
@Slf4j
public class ConjurServiceImpl implements ConjurService {

    private final ConjurClient conjurClient;
    private final CacheService cacheService;

    public ConjurServiceImpl(ConjurClient conjurClient, CacheService cacheService) {
        this.conjurClient = conjurClient;
        this.cacheService = cacheService;
    }

    @Override
    @CircuitBreaker(name = "conjur", fallbackMethod = "getCredentialFromCache")
    public Credential getCredential(String clientId) {
        try {
            // Check cache first
            Credential cachedCredential = cacheService.getCredential(clientId);
            if (cachedCredential != null) {
                log.debug("Using cached credential for client ID: {}", maskClientId(clientId));
                return cachedCredential;
            }

            // Retrieve from Conjur vault
            String path = String.format("payment/credentials/%s", clientId);
            String credentialJson = conjurClient.retrieveSecret(path);

            // Parse credential
            Credential credential = objectMapper.readValue(credentialJson, Credential.class);

            // Cache credential
            cacheService.cacheCredential(clientId, credential);

            log.debug("Retrieved credential from Conjur for client ID: {}", 
                    maskClientId(clientId));
            return credential;
        } catch (Exception e) {
            log.error("Failed to retrieve credential from Conjur: {}", e.getMessage());
            throw new ConjurException("Failed to retrieve credential", e);
        }
    }

    // Fallback method for circuit breaker
    public Credential getCredentialFromCache(String clientId, Exception e) {
        log.warn("Using cached credential due to Conjur vault unavailability");
        Credential cachedCredential = cacheService.getCredential(clientId);
        if (cachedCredential == null) {
            throw new ConjurException("No cached credential available", e);
        }
        return cachedCredential;
    }

    // Other methods...
}
```

### Implementing Token Management

Follow these steps to implement token management:

1. Use the TokenService in the Payment-EAPI module for token generation and validation
2. Follow the security guidelines for token handling
3. Implement proper error handling for token operations
4. Use the appropriate token claims for authorization
5. Implement token caching for performance

Example token service implementation:

```java
@Service
public class TokenServiceImpl implements TokenService {

    private final String issuer;
    private final String audience;
    private final long expirationSeconds;
    private final Key signingKey;

    public TokenServiceImpl(@Value("${token.issuer}") String issuer,
                           @Value("${token.audience}") String audience,
                           @Value("${token.expiration-seconds}") long expirationSeconds,
                           @Qualifier("jwtSigningKey") Key signingKey) {
        this.issuer = issuer;
        this.audience = audience;
        this.expirationSeconds = expirationSeconds;
        this.signingKey = signingKey;
    }

    @Override
    public Token generateToken(String clientId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + (expirationSeconds * 1000));
        String jti = UUID.randomUUID().toString();

        String tokenString = Jwts.builder()
                .setSubject(clientId)
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .setId(jti)
                .claim("permissions", getClientPermissions(clientId))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        return Token.builder()
                .tokenString(tokenString)
                .clientId(clientId)
                .jti(jti)
                .issuedAt(now.toInstant())
                .expirationTime(expiration.toInstant())
                .build();
    }

    @Override
    public boolean validateToken(String tokenString) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseClaimsJws(tokenString);

            return !isTokenExpired(claims);
        } catch (JwtException e) {
            return false;
        }
    }

    @Override
    public boolean isTokenExpired(Token token) {
        return token.getExpirationTime().isBefore(Instant.now());
    }

    private boolean isTokenExpired(Jws<Claims> claims) {
        return claims.getBody().getExpiration().before(new Date());
    }

    private List<String> getClientPermissions(String clientId) {
        // Implementation to retrieve client permissions
    }
}
```

### Implementing Credential Rotation

Follow these steps to implement credential rotation:

1. Use the RotationService in the Credential Rotation module
2. Follow the state machine model for credential rotation
3. Implement proper error handling and rollback procedures
4. Ensure atomicity of state transitions
5. Log all rotation events for audit purposes

Example rotation service implementation:

```java
@Service
@Slf4j
public class RotationServiceImpl implements RotationService {

    private final ConjurService conjurService;
    private final NotificationService notificationService;
    private final RotationRepository rotationRepository;

    public RotationServiceImpl(ConjurService conjurService,
                              NotificationService notificationService,
                              RotationRepository rotationRepository) {
        this.conjurService = conjurService;
        this.notificationService = notificationService;
        this.rotationRepository = rotationRepository;
    }

    @Override
    @Transactional
    public RotationResponse initiateRotation(RotationRequest request) {
        String clientId = request.getClientId();
        String rotationId = UUID.randomUUID().toString();

        try {
            // Check if rotation already in progress
            if (isRotationInProgress(clientId)) {
                log.warn("Rotation already in progress for client ID: {}", 
                        maskClientId(clientId));
                return createErrorResponse(rotationId, clientId, 
                        "Rotation already in progress");
            }

            // Generate new credentials
            String newClientSecret = securityUtils.generateSecureSecret();
            String newVersion = UUID.randomUUID().toString();

            // Store new credentials in Conjur vault
            storeNewCredentialVersion(clientId, newClientSecret, newVersion);

            // Create transition configuration
            createTransitionConfiguration(clientId, rotationId, newVersion, 
                                        request.getTransitionPeriodMinutes());

            // Update rotation state
            RotationEntity rotation = new RotationEntity();
            rotation.setRotationId(rotationId);
            rotation.setClientId(clientId);
            rotation.setState(RotationState.DUAL_ACTIVE);
            rotation.setStartedAt(Instant.now());
            rotation.setOldVersion(getCurrentVersion(clientId));
            rotation.setNewVersion(newVersion);
            rotation.setTransitionPeriodMinutes(request.getTransitionPeriodMinutes());
            rotationRepository.save(rotation);

            // Log rotation event
            log.info("Credential rotation initiated for client ID: {}, rotation ID: {}", 
                    maskClientId(clientId), rotationId);

            // Notify about rotation
            notificationService.sendRotationNotification(clientId, rotationId, 
                    RotationState.DUAL_ACTIVE, newClientSecret);

            return createSuccessResponse(rotation);
        } catch (Exception e) {
            log.error("Failed to initiate rotation: {}", e.getMessage());
            return createErrorResponse(rotationId, clientId, 
                    "Failed to initiate rotation: " + e.getMessage());
        }
    }

    // Other methods...
}
```

## Troubleshooting

This section provides guidance for troubleshooting common issues in the Payment API Security Enhancement project.

### Common Issues and Solutions

### Build Issues

- **Maven build fails with compilation errors**
  - Ensure you have Java 11 or later installed
  - Check for missing dependencies
  - Verify that your code follows the project's code standards

- **Tests fail during build**
  - Check the test logs for specific failures
  - Ensure that required services (Redis, PostgreSQL, etc.) are running
  - Verify that your changes don't break existing functionality

### Runtime Issues

- **Application fails to start**
  - Check the application logs for error messages
  - Verify that required services (Redis, PostgreSQL, Conjur vault) are running
  - Check the configuration properties for errors

- **Authentication failures**
  - Verify that the Client ID and Client Secret are correct
  - Check that the credentials are properly stored in Conjur vault
  - Verify that the Conjur vault is accessible
  - Check the authentication service logs for specific errors

- **Token validation failures**
  - Verify that the token is properly generated and signed
  - Check that the token is not expired
  - Verify that the token claims are correct
  - Check the token validation service logs for specific errors

### Docker Issues

- **Docker Compose fails to start services**
  - Check the Docker Compose logs for error messages
  - Verify that the required ports are available
  - Check that the Docker images are properly built
  - Verify that the Docker Compose file is correctly configured

- **Services are not accessible**
  - Check that the services are running (`docker-compose ps`)
  - Verify that the ports are correctly mapped
  - Check the service logs for error messages (`docker-compose logs <service>`)
  - Verify that the network is properly configured

### Logging and Debugging

### Logging Configuration

The application uses SLF4J with Logback for logging. The logging configuration is defined in the `logback-spring.xml` file in each module's `src/main/resources` directory.

You can adjust the log levels in the application properties:

```yaml
logging:
  level:
    root: INFO
    com.payment: DEBUG
    org.springframework: INFO
```

Log files are written to the `logs` directory by default. In Docker, logs are written to the container's stdout/stderr and can be viewed using `docker-compose logs`.

### Debugging Techniques

- **Enable DEBUG logging**: Set the log level to DEBUG for specific packages to get more detailed logs

- **Use remote debugging**: Attach a debugger to the running application as described in the "Debugging" section

- **Inspect Redis cache**: Use Redis CLI or Redis Commander to inspect the cached tokens and credentials

- **Check Conjur vault**: Use the Conjur UI or CLI to verify that credentials are properly stored

- **Monitor API calls**: Use tools like Postman or curl to make API calls and inspect the responses

- **Check metrics**: Use Prometheus and Grafana to monitor application metrics

- **Analyze logs**: Use the ELK stack to analyze application logs

### Getting Help

If you encounter issues that you can't resolve, you can get help from the following sources:

- **Project Documentation**: Check the documentation in the `docs` directory for guidance

- **Issue Tracker**: Search the project's issue tracker for similar issues and solutions

- **Team Communication**: Reach out to the team through the designated communication channels

- **Code Reviews**: Request a code review to get feedback on your implementation

- **Pair Programming**: Work with another developer to solve complex issues

- **Technical Lead**: Escalate to the technical lead for guidance on architectural or design issues

When seeking help, provide the following information:
- Clear description of the issue
- Steps to reproduce the issue
- Relevant logs and error messages
- Environment details (OS, Java version, etc.)
- Code snippets or pull request link if applicable

## Additional Resources

This section provides links to additional resources for the Payment API Security Enhancement project.

### Project Documentation

- [Project Overview](../../README.md): High-level overview of the project
- [Architecture Documentation](../architecture/): Detailed architecture documentation
  - [High-Level Architecture](../architecture/high-level-architecture.md): Overview of the system architecture
  - [Authentication Flow](../architecture/authentication-flow.md): Details of the authentication process
  - [Credential Rotation](../architecture/credential-rotation.md): Details of the credential rotation process
  - [Network Security](../architecture/network-security.md): Network security considerations
- [API Documentation](../api/): API specifications and documentation
  - [Payment-EAPI](../api/payment-eapi.yaml): OpenAPI specification for Payment-EAPI
  - [Payment-SAPI](../api/payment-sapi.yaml): OpenAPI specification for Payment-SAPI
- [Operations Documentation](../operations/): Operational guides
  - [Deployment Guide](../operations/deployment-guide.md): Guide for deploying the system
  - [Monitoring Guide](../operations/monitoring-guide.md): Guide for monitoring the system
  - [Credential Rotation Runbook](../operations/credential-rotation-runbook.md): Runbook for credential rotation
- [Development Documentation](../development/): Development guides
  - [Security Guidelines](../development/security-guidelines.md): Security guidelines for development
  - [Testing Guide](../development/testing-guide.md): Guide for testing the system

### External Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/): Official Spring Boot documentation
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/): Official Spring Security documentation
- [JWT Specification](https://tools.ietf.org/html/rfc7519): RFC 7519 - JSON Web Token specification
- [Conjur Documentation](https://docs.conjur.org/): Official documentation for Conjur vault
- [Redis Documentation](https://redis.io/documentation): Official Redis documentation
- [Docker Documentation](https://docs.docker.com/): Official Docker documentation
- [Kubernetes Documentation](https://kubernetes.io/docs/): Official Kubernetes documentation
- [Maven Documentation](https://maven.apache.org/guides/): Official Maven documentation

### Learning Resources

- [Spring Boot Guides](https://spring.io/guides): Official Spring Boot guides
- [Spring Security Guides](https://spring.io/guides/topicals/spring-security-architecture/): Spring Security architecture guide
- [JWT.io](https://jwt.io/): Interactive JWT debugger and documentation
- [OWASP Secure Coding Practices](https://owasp.org/www-project-secure-coding-practices-quick-reference-guide/): Secure coding practices reference
- [Docker Getting Started](https://docs.docker.com/get-started/): Docker getting started guide
- [Kubernetes Basics](https://kubernetes.io/docs/tutorials/kubernetes-basics/): Kubernetes basics tutorial
- [Redis University](https://university.redis.com/): Free Redis courses

### Tools and Utilities

- [Postman](https://www.postman.com/): API testing tool
- [JWT Debugger](https://jwt.io/): Tool for debugging JWT tokens
- [Redis Commander](https://github.com/joeferner/redis-commander): Redis web management tool
- [pgAdmin](https://www.pgadmin.org/): PostgreSQL administration tool
- [SonarLint](https://www.sonarlint.org/): IDE extension for code quality analysis
- [Prometheus](https://prometheus.io/): Monitoring and alerting toolkit
- [Grafana](https://grafana.com/): Analytics and monitoring platform