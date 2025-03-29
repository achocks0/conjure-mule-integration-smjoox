# Code Standards for Payment API Security Enhancement

## Introduction

This document defines the coding standards and guidelines for the Payment API Security Enhancement project. These standards ensure code quality, maintainability, security, and consistency across the codebase. All developers working on the project must adhere to these standards.

The Payment API Security Enhancement project implements secure authentication mechanisms using Conjur vault for credential management and token-based authentication for internal service communication, while maintaining backward compatibility with existing vendor integrations. Due to the security-critical nature of the project, strict adherence to coding standards is essential.

### Purpose

The purpose of these coding standards is to:

- Ensure consistent, high-quality code across the project
- Facilitate code reviews and knowledge sharing
- Improve code maintainability and readability
- Enhance security through standardized practices
- Reduce bugs and vulnerabilities
- Support efficient onboarding of new developers

These standards apply to all code written for the Payment API Security Enhancement project, including application code, tests, scripts, and configuration files.

### Enforcement

These coding standards are enforced through:

1. **Automated Tools**: Checkstyle, SpotBugs, SonarQube, and other static analysis tools integrated into the CI/CD pipeline
2. **Code Reviews**: Peer review process that verifies adherence to standards
3. **Build Process**: Quality gates that prevent merging code that violates critical standards
4. **IDE Configuration**: Shared IDE settings that help developers follow standards during development

Violations of these standards must be addressed before code can be merged into the main branches.

## Code Style and Formatting

The project follows the Google Java Style Guide with project-specific modifications. Consistent formatting makes code easier to read and maintain.

### Java Code Style

Java code must follow these style guidelines:

- **Indentation**: 4 spaces (not tabs)
- **Line Length**: Maximum 100 characters
- **Line Wrapping**: Break after operators, indent continuation lines by 8 spaces
- **Braces**: Opening braces on the same line as the statement, closing braces on their own line
- **Whitespace**: One space after keywords, around operators, and after commas
- **Imports**: No wildcard imports, organized in blocks (static, java, javax, org, com)
- **Naming Conventions**:
  - Classes: `PascalCase`
  - Methods and variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Package names: lowercase, no underscores

Example:

```java
public class AuthenticationService {
    private static final int MAX_RETRY_COUNT = 3;
    
    private final ConjurService conjurService;
    private final TokenService tokenService;
    
    public AuthenticationService(ConjurService conjurService, TokenService tokenService) {
        this.conjurService = conjurService;
        this.tokenService = tokenService;
    }
    
    public AuthenticationResponse authenticate(String clientId, String clientSecret) {
        // Method implementation
        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("Client ID and Client Secret must not be null");
        }
        
        // More implementation...
        return new AuthenticationResponse(token, expiresAt);
    }
}
```

### XML and YAML Formatting

XML and YAML files must follow these formatting guidelines:

- **Indentation**: 2 spaces (not tabs)
- **Line Length**: Maximum 100 characters
- **Attributes**: One attribute per line for multiple attributes
- **Comments**: Include comments for non-obvious configurations

Example XML (pom.xml):

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
  <!-- Use the version from the parent -->
</dependency>
```

Example YAML (application.yml):

```yaml
spring:
  application:
    name: payment-eapi
  datasource:
    url: jdbc:postgresql://localhost:5432/payment
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    # Connection pool settings
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
```

### Code Formatting Tools

The project uses the following tools to enforce code formatting:

1. **Checkstyle**: Enforces Java code style rules
2. **EditorConfig**: Maintains consistent coding styles across different editors
3. **Maven Formatter Plugin**: Formats code during the build process

Developers should configure their IDEs to use these tools:

- **IntelliJ IDEA**: Import the project's code style settings and install the Checkstyle plugin
- **Eclipse**: Import the project's code style settings and install the Checkstyle plugin
- **VS Code**: Install the Java Extension Pack and Checkstyle extension

The project repository includes configuration files for these tools in the root directory.

## Code Organization

Proper code organization improves maintainability, readability, and helps enforce separation of concerns. The project follows a structured approach to code organization.

### Package Structure

The project uses the following package structure:

```
com.payment
├── common          # Shared utilities and models
│   ├── config      # Common configuration
│   ├── exception   # Common exceptions
│   ├── model       # Shared data models
│   ├── util        # Utility classes
│   └── monitoring  # Monitoring utilities
├── eapi            # External API module
│   ├── config      # Configuration classes
│   ├── controller  # REST controllers
│   ├── exception   # Exception handling
│   ├── filter      # Request filters
│   ├── model       # Data models
│   ├── repository  # Data repositories
│   ├── service     # Service interfaces
│   └── service.impl # Service implementations
├── sapi            # System API module
│   ├── config      # Configuration classes
│   ├── controller  # REST controllers
│   ├── exception   # Exception handling
│   ├── filter      # Request filters
│   ├── model       # Data models
│   ├── repository  # Data repositories
│   ├── service     # Service interfaces
│   └── service.impl # Service implementations
└── rotation        # Credential rotation module
    ├── config      # Configuration classes
    ├── controller  # REST controllers
    ├── model       # Data models
    ├── scheduler   # Scheduled tasks
    ├── service     # Service interfaces
    └── service.impl # Service implementations
```

Each module should maintain this structure for consistency. New packages should only be added when they represent a distinct functional area.

### Class Organization

Classes should be organized according to these guidelines:

1. **Single Responsibility Principle**: Each class should have a single responsibility
2. **Size Limits**: Classes should generally not exceed 500 lines of code
3. **Method Limits**: Methods should generally not exceed 50 lines of code
4. **Class Structure**:
   - Static fields
   - Instance fields
   - Constructors
   - Public methods
   - Protected methods
   - Private methods
   - Inner classes/interfaces
5. **Visibility**: Use the most restrictive visibility possible

Example class structure:

```java
public class TokenServiceImpl implements TokenService {
    // Static fields
    private static final Logger log = LoggerFactory.getLogger(TokenServiceImpl.class);
    private static final int DEFAULT_EXPIRATION_SECONDS = 3600;
    
    // Instance fields
    private final String issuer;
    private final String audience;
    private final Key signingKey;
    private final CacheService cacheService;
    
    // Constructor
    public TokenServiceImpl(String issuer, String audience, Key signingKey, 
                           CacheService cacheService) {
        this.issuer = issuer;
        this.audience = audience;
        this.signingKey = signingKey;
        this.cacheService = cacheService;
    }
    
    // Public methods (implementing interface)
    @Override
    public Token generateToken(String clientId) {
        return generateToken(clientId, Collections.emptyList());
    }
    
    @Override
    public Token generateToken(String clientId, List<String> permissions) {
        // Implementation
    }
    
    @Override
    public boolean validateToken(String tokenString) {
        // Implementation
    }
    
    // Private methods
    private Claims createClaims(String clientId, List<String> permissions) {
        // Implementation
    }
    
    private String signToken(Claims claims) {
        // Implementation
    }
}
```

### Interface and Implementation Separation

The project follows the interface-implementation separation pattern:

1. **Interfaces**: Define the contract for a service or component
2. **Implementations**: Implement the interface contract
3. **Dependency Injection**: Components depend on interfaces, not implementations

Interfaces should be placed in the service package, while implementations should be in the service.impl package. This separation facilitates testing, promotes loose coupling, and supports the dependency inversion principle.

Example:

```java
// Interface in com.payment.eapi.service
public interface AuthenticationService {
    AuthenticationResponse authenticate(String clientId, String clientSecret);
    boolean validateToken(String token);
}

// Implementation in com.payment.eapi.service.impl
@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    // Implementation
}

// Usage with dependency injection
@RestController
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    
    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    // Controller methods using the service
}
```

### Design Patterns

The project uses the following design patterns:

1. **Dependency Injection**: Spring's constructor injection for dependencies
2. **Factory Pattern**: For creating complex objects
3. **Builder Pattern**: For objects with many optional parameters
4. **Strategy Pattern**: For interchangeable algorithms
5. **Adapter Pattern**: For integrating with external systems
6. **Circuit Breaker Pattern**: For resilience in external service calls
7. **Repository Pattern**: For data access abstraction

These patterns should be used consistently throughout the codebase. New patterns should only be introduced when they provide clear benefits and are documented appropriately.

Example of Builder Pattern:

```java
public class Token {
    private final String tokenString;
    private final String clientId;
    private final String jti;
    private final Instant issuedAt;
    private final Instant expirationTime;
    
    private Token(Builder builder) {
        this.tokenString = builder.tokenString;
        this.clientId = builder.clientId;
        this.jti = builder.jti;
        this.issuedAt = builder.issuedAt;
        this.expirationTime = builder.expirationTime;
    }
    
    // Getters
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String tokenString;
        private String clientId;
        private String jti;
        private Instant issuedAt;
        private Instant expirationTime;
        
        public Builder tokenString(String tokenString) {
            this.tokenString = tokenString;
            return this;
        }
        
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }
        
        public Builder jti(String jti) {
            this.jti = jti;
            return this;
        }
        
        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }
        
        public Builder expirationTime(Instant expirationTime) {
            this.expirationTime = expirationTime;
            return this;
        }
        
        public Token build() {
            return new Token(this);
        }
    }
}
```

## Documentation Standards

Proper documentation is essential for code maintainability and knowledge sharing. All code must be appropriately documented according to these standards.

### Javadoc Standards

Javadoc comments must be provided for:

1. **All public classes and interfaces**
2. **All public and protected methods**
3. **Public fields (if any)**
4. **Package declarations (package-info.java)**

Javadoc comments should include:

- A clear description of the purpose and behavior
- `@param` tags for all parameters with descriptions
- `@return` tag with description (if the method returns a value)
- `@throws` tags for all checked exceptions with descriptions
- `@see` tags for related classes or methods
- `@since` tags indicating the version when the item was added

Example:

```java
/**
 * Service for authenticating clients and generating JWT tokens.
 * <p>
 * This service validates client credentials against Conjur vault and
 * generates JWT tokens for authenticated clients. It also provides
 * methods for token validation and renewal.
 * </p>
 *
 * @see TokenService
 * @see ConjurService
 * @since 1.0.0
 */
public interface AuthenticationService {
    
    /**
     * Authenticates a client using Client ID and Client Secret credentials.
     * <p>
     * This method validates the provided credentials against those stored in
     * Conjur vault. If validation succeeds, it generates a JWT token for the
     * client. If validation fails, it throws an AuthenticationException.
     * </p>
     *
     * @param clientId the client identifier
     * @param clientSecret the client secret
     * @return an AuthenticationResponse containing the JWT token and expiration time
     * @throws AuthenticationException if authentication fails
     * @throws ConjurException if there is an error communicating with Conjur vault
     * @since 1.0.0
     */
    AuthenticationResponse authenticate(String clientId, String clientSecret);
    
    // Other methods...
}
```

### Code Comments

In addition to Javadoc, use regular comments as follows:

1. **Implementation Comments**: Explain complex algorithms or non-obvious code
2. **TODO Comments**: Mark code that needs future attention (include JIRA ticket number)
3. **FIXME Comments**: Mark code that needs to be fixed (include JIRA ticket number)

Comments should explain why the code is doing something, not what it's doing (which should be clear from the code itself).

Example:

```java
// Use constant-time comparison to prevent timing attacks
if (!securityUtils.constantTimeEquals(hashedSecret, storedCredential.getHashedSecret())) {
    // Log authentication failure (without credentials)
    securityLogger.warn("Authentication failed for client ID: {}", maskClientId(clientId));
    return false;
}

// TODO: Implement cache invalidation strategy (JIRA-123)
token.setExpirationTime(Instant.now().plusSeconds(expirationSeconds));

// FIXME: This is a temporary workaround until the proper solution is implemented (JIRA-456)
if (conjurService == null) {
    return fallbackAuthentication(clientId, clientSecret);
}
```

### API Documentation

REST API endpoints must be documented using OpenAPI (Swagger) annotations:

1. **@Tag**: Group related endpoints
2. **@Operation**: Describe the operation
3. **@Parameter**: Document request parameters
4. **@RequestBody**: Document request body
5. **@ApiResponse**: Document possible responses
6. **@Schema**: Define the structure of models

Example:

```java
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication API endpoints")
public class AuthenticationController {
    
    private final AuthenticationService authenticationService;
    
    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    @PostMapping("/token")
    @Operation(
        summary = "Authenticate and get token",
        description = "Authenticates a client using Client ID and Client Secret headers and returns a JWT token"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestHeader("X-Client-ID") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret) {
        
        AuthenticationResponse response = authenticationService.authenticate(clientId, clientSecret);
        return ResponseEntity.ok(response);
    }
}
```

### README Files

Each module must have a README.md file that includes:

1. **Module Purpose**: Brief description of the module's purpose
2. **Key Components**: List of key components and their responsibilities
3. **Dependencies**: External and internal dependencies
4. **Configuration**: Configuration options and environment variables
5. **Usage Examples**: Examples of how to use the module

The main project README.md should provide an overview of the entire project, setup instructions, and links to module-specific documentation.

Example module README.md structure:

```markdown
# Payment-EAPI Module

## Purpose
The Payment-EAPI module implements the external-facing API that maintains backward compatibility with existing vendor integrations while enhancing security through Conjur vault integration and token-based authentication.

## Key Components
- **AuthenticationController**: Handles authentication requests
- **PaymentController**: Handles payment requests
- **AuthenticationService**: Authenticates vendors and generates tokens
- **ConjurService**: Interfaces with Conjur vault for credential management
- **TokenService**: Generates and manages JWT tokens

## Dependencies
- Spring Boot 2.6.x
- Spring Security 5.6.x
- JJWT 0.11.x
- Conjur Java API Client
- Redis Client

## Configuration
The module is configured through application.yml and environment variables:

| Property | Environment Variable | Description | Default |
|----------|----------------------|-------------|--------|
| `conjur.url` | `CONJUR_URL` | Conjur vault URL | - |
| `conjur.account` | `CONJUR_ACCOUNT` | Conjur account name | - |
| `token.expiration-seconds` | `TOKEN_EXPIRATION_SECONDS` | Token expiration time in seconds | 3600 |

## Usage Examples
See the API documentation at `/swagger-ui.html` for usage examples.
```

## Testing Standards

Comprehensive testing is essential for ensuring code quality and preventing regressions. All code must be thoroughly tested according to these standards.

### Test Coverage Requirements

The project requires the following test coverage:

1. **Minimum Line Coverage**: 85% overall, 90% for security-critical components
2. **Minimum Branch Coverage**: 80% overall, 90% for security-critical components
3. **Critical Components**: 100% coverage for authentication, token handling, and credential management

Security-critical components include:
- Authentication services
- Token generation and validation
- Credential management
- Security filters
- Conjur vault integration

Test coverage is measured using JaCoCo and enforced in the CI/CD pipeline.

### Unit Testing

Unit tests must follow these guidelines:

1. **Test Framework**: JUnit 5
2. **Mocking Framework**: Mockito
3. **Assertion Library**: AssertJ
4. **Naming Convention**: `should[ExpectedBehavior]When[StateUnderTest]`
5. **Structure**: Follow the Arrange-Act-Assert (AAA) pattern
6. **Isolation**: Tests should not depend on external resources
7. **Test Data**: Use meaningful test data that represents real-world scenarios

Example unit test:

```java
@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {
    
    @Mock
    private CacheService cacheService;
    
    @InjectMocks
    private TokenServiceImpl tokenService;
    
    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl("issuer", "audience", TestKeys.getSigningKey(), cacheService);
    }
    
    @Test
    void shouldGenerateValidTokenWhenClientIdIsProvided() {
        // Arrange
        String clientId = "test-client";
        
        // Act
        Token token = tokenService.generateToken(clientId);
        
        // Assert
        assertThat(token).isNotNull();
        assertThat(token.getClientId()).isEqualTo(clientId);
        assertThat(token.getTokenString()).isNotBlank();
        assertThat(token.getExpirationTime()).isAfter(Instant.now());
        
        // Verify token can be validated
        boolean isValid = tokenService.validateToken(token.getTokenString());
        assertThat(isValid).isTrue();
    }
    
    @Test
    void shouldThrowExceptionWhenClientIdIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> tokenService.generateToken(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Client ID must not be null");
    }
}
```

### Integration Testing

Integration tests must follow these guidelines:

1. **Test Framework**: Spring Boot Test
2. **Test Slices**: Use appropriate test slices (@WebMvcTest, @DataJpaTest, etc.)
3. **External Dependencies**: Use TestContainers for database, Redis, etc.
4. **Test Data**: Use dedicated test data sets
5. **Cleanup**: Tests should clean up after themselves

Example integration test:

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ConjurService conjurService;
    
    @Test
    void shouldAuthenticateSuccessfullyWithValidCredentials() throws Exception {
        // Arrange
        String clientId = "test-client";
        String clientSecret = "test-secret";
        
        Credential storedCredential = new Credential();
        storedCredential.setClientId(clientId);
        storedCredential.setHashedSecret(BCrypt.hashpw(clientSecret, BCrypt.gensalt()));
        storedCredential.setActive(true);
        
        when(conjurService.getCredential(clientId)).thenReturn(storedCredential);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/token")
                .header("X-Client-ID", clientId)
                .header("X-Client-Secret", clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
    }
}
```

### Security Testing

Security testing must follow these guidelines:

1. **Authentication Tests**: Test all authentication flows (success and failure)
2. **Authorization Tests**: Test permission enforcement
3. **Input Validation**: Test boundary conditions and invalid inputs
4. **Error Handling**: Test error scenarios and verify secure error responses
5. **Token Tests**: Test token generation, validation, expiration, and renewal

Example security test:

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreMissing() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100.0, \"currency\": \"USD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }
    
    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                .header("X-Client-ID", "invalid-client")
                .header("X-Client-Secret", "invalid-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100.0, \"currency\": \"USD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }
    
    @Test
    void shouldRejectTokenWithInvalidSignature() throws Exception {
        String validToken = getValidToken();
        String tamperedToken = tamperWithSignature(validToken);
        
        mockMvc.perform(post("/api/v1/internal/payments")
                .header("Authorization", "Bearer " + tamperedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100.0, \"currency\": \"USD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }
}
```

### Test Data Management

Test data must be managed according to these guidelines:

1. **Test Fixtures**: Use fixture classes or files for common test data
2. **Test Data Builders**: Use builder pattern for complex test data
3. **Sensitive Data**: Never use real credentials or sensitive data in tests
4. **Database Tests**: Use TestContainers for database tests
5. **Cleanup**: Tests should clean up any data they create

Example test data management:

```java
public class TestCredentials {
    public static Credential createValidCredential(String clientId) {
        return Credential.builder()
                .clientId(clientId)
                .hashedSecret(BCrypt.hashpw("test-secret", BCrypt.gensalt()))
                .active(true)
                .version(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build();
    }
    
    public static Credential createInactiveCredential(String clientId) {
        return Credential.builder()
                .clientId(clientId)
                .hashedSecret(BCrypt.hashpw("test-secret", BCrypt.gensalt()))
                .active(false)
                .version(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build();
    }
}
```

## Security Standards

Security is a critical aspect of the Payment API Security Enhancement project. All code must adhere to these security standards to ensure the system's security posture.

### Authentication Implementation

Authentication code must follow these security standards:

1. **Credential Validation**: Use constant-time comparison for credential validation
2. **Password Storage**: Never store plain text passwords or secrets
3. **Token Security**: Use strong algorithms for token signing (HMAC-SHA256 minimum)
4. **Token Claims**: Include only necessary claims in tokens
5. **Token Validation**: Validate all aspects of tokens (signature, expiration, claims)

Refer to the [Security Guidelines](security-guidelines.md) document for detailed security implementation guidelines.

Example secure authentication implementation:

```java
@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    
    private final ConjurService conjurService;
    private final TokenService tokenService;
    private final SecurityUtils securityUtils;
    
    // Constructor
    
    @Override
    public AuthenticationResponse authenticate(String clientId, String clientSecret) {
        // Validate input
        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("Client ID and Client Secret must not be null");
        }
        
        // Get credential from Conjur vault
        Credential storedCredential = conjurService.getCredential(clientId);
        if (storedCredential == null) {
            // Log authentication failure (without credentials)
            securityLogger.warn("Authentication failed: Unknown client ID");
            throw new AuthenticationException("Authentication failed");
        }
        
        // Check if credential is active
        if (!storedCredential.isActive()) {
            securityLogger.warn("Authentication failed: Inactive credential for client ID: {}", 
                               securityUtils.maskClientId(clientId));
            throw new AuthenticationException("Authentication failed");
        }
        
        // Validate using constant-time comparison
        if (!securityUtils.constantTimeEquals(
                securityUtils.hashSecret(clientSecret),
                storedCredential.getHashedSecret())) {
            // Log authentication failure (without credentials)
            securityLogger.warn("Authentication failed: Invalid credentials for client ID: {}", 
                               securityUtils.maskClientId(clientId));
            throw new AuthenticationException("Authentication failed");
        }
        
        // Generate token
        Token token = tokenService.generateToken(clientId);
        
        // Log successful authentication
        securityLogger.info("Authentication successful for client ID: {}", 
                          securityUtils.maskClientId(clientId));
        
        // Return response
        return new AuthenticationResponse(token.getTokenString(), 
                                        token.getExpirationTime().toString());
    }
}
```

### Secure Coding Practices

All code must follow these secure coding practices:

1. **Input Validation**: Validate all input data for type, length, format, and range
2. **Output Encoding**: Encode output data appropriate to the context
3. **SQL Injection Prevention**: Use parameterized queries or ORM frameworks
4. **XSS Prevention**: Encode data for the appropriate context (HTML, JavaScript, etc.)
5. **CSRF Protection**: Implement CSRF protection for browser-based clients
6. **Error Handling**: Implement secure error handling that doesn't expose sensitive information
7. **Logging**: Never log sensitive data such as credentials or tokens

Refer to the [Security Guidelines](security-guidelines.md) document for detailed secure coding practices.

Example secure input validation:

```java
public void processClientId(String clientId) {
    // Validate format (alphanumeric, specific length)
    if (clientId == null || !clientId.matches("^[a-zA-Z0-9]{8,32}$")) {
        throw new IllegalArgumentException("Invalid client ID format");
    }
    
    // Proceed with validated clientId
}
```

### Conjur Vault Integration

Conjur vault integration must follow these security standards:

1. **Secure Connection**: Use TLS 1.2+ for all communication with Conjur vault
2. **Certificate Authentication**: Use certificate-based authentication to Conjur vault
3. **Credential Handling**: Never expose retrieved credentials in logs or responses
4. **Error Handling**: Implement proper error handling for Conjur vault operations
5. **Circuit Breaker**: Use circuit breaker pattern for resilience

Refer to the [Security Guidelines](security-guidelines.md) document for detailed Conjur vault integration guidelines.

Example secure Conjur vault integration:

```java
@Service
public class ConjurServiceImpl implements ConjurService {
    
    private final ConjurClient conjurClient;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    
    // Constructor
    
    @Override
    @CircuitBreaker(name = "conjur", fallbackMethod = "getCredentialFromCache")
    public Credential getCredential(String clientId) {
        try {
            // Check cache first
            Credential cachedCredential = cacheService.getCredential(clientId);
            if (cachedCredential != null) {
                log.debug("Using cached credential for client ID: {}", 
                         securityUtils.maskClientId(clientId));
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
                     securityUtils.maskClientId(clientId));
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
}
```

### Security Headers

All API responses must include appropriate security headers:

1. **Strict-Transport-Security**: Enforce HTTPS
2. **X-Content-Type-Options**: Prevent MIME type sniffing
3. **X-Frame-Options**: Prevent clickjacking
4. **X-XSS-Protection**: Enable browser XSS filters
5. **Cache-Control**: Prevent caching of sensitive data
6. **Content-Security-Policy**: Restrict resource loading

Example security header configuration:

```java
@Configuration
public class SecurityHeadersConfig {
    
    @Bean
    public FilterRegistrationBean<Filter> securityHeadersFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter((request, response, chain) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            // Set security headers
            httpResponse.setHeader("Strict-Transport-Security", 
                                 "max-age=31536000; includeSubDomains");
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
            httpResponse.setHeader("Cache-Control", "no-store");
            httpResponse.setHeader("Content-Security-Policy", 
                                 "default-src 'self'; script-src 'self'; object-src 'none'");
            
            chain.doFilter(request, response);
        });
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }
}
```

### Security Testing Requirements

Security testing must include:

1. **SAST**: Static Application Security Testing using SonarQube and SpotBugs
2. **DAST**: Dynamic Application Security Testing using OWASP ZAP
3. **Dependency Scanning**: OWASP Dependency Check for vulnerable dependencies
4. **Security Unit Tests**: Tests for security features and controls
5. **Penetration Testing**: Regular penetration testing of the application

Security testing is integrated into the CI/CD pipeline and must pass before deployment.

Example security test configuration:

```xml
<!-- OWASP Dependency Check for vulnerability scanning -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>7.1.1</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFiles>
            <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<!-- SpotBugs for static code analysis -->
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.5.3.0</version>
    <configuration>
        <effort>Max</effort>
        <threshold>High</threshold>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>1.12.0</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Performance Standards

Performance is an important aspect of the Payment API Security Enhancement project. Code must be written with performance in mind to ensure the system meets its performance requirements.

### Performance Requirements

The system must meet the following performance requirements:

1. **Response Time**: API response time < 500ms (95th percentile)
2. **Authentication Time**: Authentication processing < 200ms (95th percentile)
3. **Token Validation Time**: Token validation < 50ms (95th percentile)
4. **Throughput**: Support for 100+ transactions per second
5. **Resource Utilization**: Efficient use of CPU, memory, and network resources

Performance is measured using metrics collected during testing and production operation.

### Caching Strategy

Implement caching according to these guidelines:

1. **Token Caching**: Cache JWT tokens in Redis to reduce authentication overhead
2. **Credential Caching**: Cache credential metadata to reduce Conjur vault requests
3. **Cache Invalidation**: Implement proper invalidation during credential rotation
4. **Cache Configuration**: Configure appropriate TTL and size limits
5. **Cache Monitoring**: Monitor cache hit/miss rates and performance

Example caching implementation:

```java
@Service
public class RedisCacheServiceImpl implements CacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Constructor
    
    @Override
    public void cacheToken(String clientId, Token token) {
        String key = "token:" + clientId;
        redisTemplate.opsForValue().set(
            key,
            token,
            Duration.between(Instant.now(), token.getExpirationTime())
        );
    }
    
    @Override
    public Token getToken(String clientId) {
        String key = "token:" + clientId;
        return (Token) redisTemplate.opsForValue().get(key);
    }
    
    @Override
    public void invalidateToken(String clientId) {
        String key = "token:" + clientId;
        redisTemplate.delete(key);
    }
    
    // Other methods for credential caching
}
```

### Database Access

Database access must follow these performance guidelines:

1. **Connection Pooling**: Use connection pooling for database connections
2. **Query Optimization**: Optimize database queries for performance
3. **Indexing**: Create appropriate indexes for frequently queried fields
4. **Batch Operations**: Use batch operations for bulk updates
5. **Pagination**: Implement pagination for large result sets

Example database configuration:

```java
@Configuration
public class DatabaseConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        return new HikariConfig();
    }
    
    @Bean
    public DataSource dataSource(HikariConfig hikariConfig) {
        return new HikariDataSource(hikariConfig);
    }
    
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

### Asynchronous Processing

Use asynchronous processing for non-blocking operations:

1. **Async Methods**: Use Spring's @Async annotation for non-blocking operations
2. **CompletableFuture**: Use CompletableFuture for composable async operations
3. **Thread Pools**: Configure appropriate thread pools for async tasks
4. **Reactive Programming**: Consider reactive programming for high-throughput scenarios

Example asynchronous implementation:

```java
@Service
public class AuditServiceImpl implements AuditService {
    
    private final AuditRepository auditRepository;
    
    // Constructor
    
    @Async("auditExecutor")
    @Override
    public CompletableFuture<Void> logAuthenticationEvent(String clientId, boolean success) {
        return CompletableFuture.runAsync(() -> {
            AuthenticationEvent event = new AuthenticationEvent();
            event.setClientId(clientId);
            event.setSuccess(success);
            event.setTimestamp(Instant.now());
            event.setIpAddress(getClientIp());
            
            auditRepository.save(event);
        });
    }
}

@Configuration
public class AsyncConfig {
    
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-");
        executor.initialize();
        return executor;
    }
}
```

### Resource Management

Properly manage resources to prevent leaks and improve performance:

1. **Connection Closing**: Always close database connections, file handles, etc.
2. **Try-with-Resources**: Use try-with-resources for AutoCloseable resources
3. **Resource Pooling**: Use object pools for expensive resources
4. **Memory Management**: Avoid memory leaks and excessive object creation
5. **Garbage Collection**: Consider GC impact in performance-critical code

Example resource management:

```java
public byte[] readCertificate(String path) {
    try (InputStream is = new FileInputStream(path)) {
        return is.readAllBytes();
    } catch (IOException e) {
        throw new ConfigurationException("Failed to read certificate", e);
    }
}
```

## Error Handling Standards

Proper error handling is essential for system stability, security, and user experience. All code must follow these error handling standards.

### Exception Hierarchy

The project uses a structured exception hierarchy:

1. **Base Exception**: `PaymentApiException` as the base for all custom exceptions
2. **Functional Exceptions**: Specific exceptions for different functional areas
3. **Technical Exceptions**: Exceptions for technical issues

Example exception hierarchy:

```java
// Base exception
public class PaymentApiException extends RuntimeException {
    private final String errorCode;
    
    public PaymentApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public PaymentApiException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

// Functional exception
public class AuthenticationException extends PaymentApiException {
    public AuthenticationException(String message) {
        super(message, "AUTHENTICATION_FAILED");
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, "AUTHENTICATION_FAILED", cause);
    }
}

// Technical exception
public class ConjurException extends PaymentApiException {
    public ConjurException(String message) {
        super(message, "CONJUR_ERROR");
    }
    
    public ConjurException(String message, Throwable cause) {
        super(message, "CONJUR_ERROR", cause);
    }
}
```

### Exception Handling

Exception handling must follow these guidelines:

1. **Specific Exceptions**: Catch specific exceptions rather than generic ones
2. **Exception Translation**: Translate low-level exceptions to domain-specific ones
3. **Context Preservation**: Include relevant context in exceptions
4. **Resource Cleanup**: Ensure resources are properly closed in finally blocks or try-with-resources
5. **Logging**: Log exceptions with appropriate context and stack traces

Example exception handling:

```java
public Credential getCredential(String clientId) {
    try {
        // Retrieve from Conjur vault
        String path = String.format("payment/credentials/%s", clientId);
        String credentialJson = conjurClient.retrieveSecret(path);
        
        // Parse credential
        return objectMapper.readValue(credentialJson, Credential.class);
    } catch (ConjurApiException e) {
        // Translate Conjur API exception to domain exception
        log.error("Conjur API error for client ID {}: {}", 
                 securityUtils.maskClientId(clientId), e.getMessage());
        throw new ConjurException("Failed to retrieve credential from Conjur", e);
    } catch (JsonProcessingException e) {
        // Handle JSON parsing exception
        log.error("Failed to parse credential JSON for client ID {}: {}", 
                 securityUtils.maskClientId(clientId), e.getMessage());
        throw new ConjurException("Failed to parse credential data", e);
    } catch (Exception e) {
        // Handle unexpected exceptions
        log.error("Unexpected error retrieving credential for client ID {}: {}", 
                 securityUtils.maskClientId(clientId), e.getMessage());
        throw new ConjurException("Unexpected error retrieving credential", e);
    }
}
```

### Global Exception Handling

Use Spring's global exception handling for REST APIs:

1. **@ControllerAdvice**: Implement a global exception handler
2. **@ExceptionHandler**: Define handlers for specific exception types
3. **Standardized Responses**: Return standardized error responses
4. **HTTP Status Codes**: Use appropriate HTTP status codes
5. **Security Considerations**: Avoid exposing sensitive information in error responses

Example global exception handler:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            getRequestId(request),
            Instant.now().toString()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, WebRequest request) {
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            getRequestId(request),
            Instant.now().toString()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    @ExceptionHandler(ConjurException.class)
    public ResponseEntity<ErrorResponse> handleConjurException(
            ConjurException ex, WebRequest request) {
        
        // Log the detailed exception but return a generic message
        log.error("Conjur error: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode(),
            "An error occurred while processing your request",
            getRequestId(request),
            Instant.now().toString()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        // Log the detailed exception but return a generic message
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            getRequestId(request),
            Instant.now().toString()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    private String getRequestId(WebRequest request) {
        return request.getHeader("X-Request-ID");
    }
}
```

### Error Response Format

All error responses must follow a standardized format:

```json
{
  "errorCode": "string",
  "message": "string",
  "requestId": "string",
  "timestamp": "string"
}
```

Common error codes include:
- `AUTHENTICATION_FAILED`: Invalid credentials
- `INVALID_REQUEST`: Malformed request or missing required parameters
- `TOKEN_EXPIRED`: JWT token has expired
- `TOKEN_INVALID`: JWT token is invalid
- `PERMISSION_DENIED`: Insufficient permissions for the requested operation
- `CONJUR_ERROR`: Error communicating with Conjur vault
- `INTERNAL_ERROR`: Unexpected system error

Example error response model:

```java
public class ErrorResponse {
    private final String errorCode;
    private final String message;
    private final String requestId;
    private final String timestamp;
    
    public ErrorResponse(String errorCode, String message, String requestId, String timestamp) {
        this.errorCode = errorCode;
        this.message = message;
        this.requestId = requestId;
        this.timestamp = timestamp;
    }
    
    // Getters
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
}
```

## Logging Standards

Proper logging is essential for monitoring, debugging, and auditing. All code must follow these logging standards.

### Logging Framework

The project uses SLF4J with Logback as the logging framework:

1. **SLF4J**: API for logging
2. **Logback**: Logging implementation
3. **Lombok**: @Slf4j annotation for logger creation

Example logger declaration:

```java
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {
    // Logger is automatically created as 'log'
}

// Alternatively, without Lombok:
public class AuthenticationServiceImpl implements AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
}
```

### Log Levels

Use appropriate log levels for different types of events:

1. **ERROR**: Use for errors that prevent normal operation
2. **WARN**: Use for unexpected conditions that don't prevent operation
3. **INFO**: Use for significant events in normal operation
4. **DEBUG**: Use for detailed information useful for debugging
5. **TRACE**: Use for very detailed debugging information

Example log level usage:

```java
// ERROR: Critical errors that prevent normal operation
log.error("Failed to connect to Conjur vault: {}", e.getMessage(), e);

// WARN: Unexpected conditions that don't prevent operation
log.warn("Authentication failed for client ID: {}", securityUtils.maskClientId(clientId));

// INFO: Significant events in normal operation
log.info("Authentication successful for client ID: {}", securityUtils.maskClientId(clientId));

// DEBUG: Detailed information useful for debugging
log.debug("Generated token for client ID: {}, expires at: {}", 
         securityUtils.maskClientId(clientId), token.getExpirationTime());

// TRACE: Very detailed debugging information
log.trace("Token validation details: issuer={}, audience={}, expiration={}", 
         token.getIssuer(), token.getAudience(), token.getExpirationTime());
```

### Log Content

Log content must follow these guidelines:

1. **Context**: Include relevant context in log messages
2. **Correlation IDs**: Include request IDs or correlation IDs for request tracing
3. **Sensitive Data**: Never log sensitive data such as credentials or tokens
4. **Personally Identifiable Information (PII)**: Mask or exclude PII from logs
5. **Structured Logging**: Use structured logging format (JSON) in production

Example log content:

```java
// Good: Includes context and masks sensitive data
log.info("Authentication attempt for client ID: {}, request ID: {}", 
         securityUtils.maskClientId(clientId), requestId);

// Bad: Logs sensitive data
// log.info("Authentication attempt with client ID: {} and secret: {}", clientId, clientSecret);

// Good: Includes operation result and timing
log.info("Token validation completed: valid={}, took={}ms, request ID: {}", 
         isValid, stopwatch.elapsed(TimeUnit.MILLISECONDS), requestId);

// Good: Structured logging with MDC
MDC.put("requestId", requestId);
MDC.put("clientId", securityUtils.maskClientId(clientId));
log.info("Authentication attempt");
MDC.clear();
```

### Security Logging

Security events must be logged for audit purposes:

1. **Authentication Events**: Log all authentication attempts (success/failure)
2. **Authorization Events**: Log authorization decisions
3. **Token Operations**: Log token generation, validation, and renewal
4. **Credential Access**: Log credential access (without the actual credentials)
5. **Security Exceptions**: Log security-related exceptions

Example security logging:

```java
// Authentication events
securityLogger.info("Authentication attempt for client ID: {}, source IP: {}, request ID: {}", 
                  securityUtils.maskClientId(clientId), sourceIp, requestId);

securityLogger.info("Authentication successful for client ID: {}, source IP: {}, request ID: {}", 
                  securityUtils.maskClientId(clientId), sourceIp, requestId);

securityLogger.warn("Authentication failed for client ID: {}, source IP: {}, request ID: {}", 
                  securityUtils.maskClientId(clientId), sourceIp, requestId);

// Token operations
securityLogger.info("Token generated for client ID: {}, token ID: {}, request ID: {}", 
                  securityUtils.maskClientId(clientId), tokenId, requestId);

securityLogger.info("Token validated for client ID: {}, token ID: {}, request ID: {}", 
                  securityUtils.maskClientId(clientId), tokenId, requestId);

// Credential access
securityLogger.info("Credential accessed for client ID: {}, request ID: {}", 
                  securityUtils.maskClientId(clientId), requestId);
```

### Log Configuration

Log configuration must follow these guidelines:

1. **Environment-specific Configuration**: Different logging configuration for different environments
2. **Log Rotation**: Configure log rotation to manage log file size
3. **Asynchronous Logging**: Use asynchronous appenders for performance
4. **Log Aggregation**: Configure logging for centralized log aggregation
5. **Log Format**: Use a consistent log format across all components

Example Logback configuration (logback-spring.xml):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Console appender for development -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- File appender for production -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- JSON appender for production -->
    <appender name="JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.json</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    
    <!-- Security logger -->
    <appender name="SECURITY" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/security.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/security.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>90</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Async appenders for performance -->
    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="FILE" />
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
    </appender>
    
    <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="JSON" />
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
    </appender>
    
    <appender name="ASYNC_SECURITY" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="SECURITY" />
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
    </appender>
    
    <!-- Security logger -->
    <logger name="com.payment.security" level="INFO" additivity="false">
        <appender-ref ref="ASYNC_SECURITY" />
        <appender-ref ref="CONSOLE" />
    </logger>
    
    <!-- Environment-specific configuration -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
        </root>
    </springProfile>
    
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="ASYNC_JSON" />
        </root>
    </springProfile>
</configuration>
```

## Dependency Management

Proper dependency management is essential for maintainability, security, and compatibility. All dependencies must be managed according to these standards.

### Dependency Declaration

Dependencies must be declared according to these guidelines:

1. **Maven BOM**: Use Spring Boot BOM for version management
2. **Version Properties**: Define versions in properties section
3. **Scope**: Specify appropriate scope for dependencies
4. **Exclusions**: Exclude transitive dependencies when necessary
5. **Comments**: Document non-obvious dependencies

Example dependency declaration:

```xml
<properties>
    <java.version>11</java.version>
    <spring-boot.version>2.6.7</spring-boot.version>
    <jjwt.version>0.11.5</jjwt.version>
    <conjur-api.version>3.0.4</conjur-api.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- JWT Library -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Conjur API Client -->
    <dependency>
        <groupId>com.cyberark</groupId>
        <artifactId>conjur-api</artifactId>
        <version>${conjur-api.version}</version>
    </dependency>
    
    <!-- Lombok for boilerplate reduction -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
    
    <!-- Test Dependencies -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Dependency Versions

Dependency versions must be managed according to these guidelines:

1. **Version Compatibility**: Ensure compatibility between dependencies
2. **Security Updates**: Keep dependencies updated for security fixes
3. **LTS Versions**: Prefer Long-Term Support versions for stability
4. **Version Ranges**: Avoid version ranges to ensure reproducible builds
5. **Dependency Locking**: Use dependency locking for reproducible builds

Example version management:

```xml
<properties>
    <!-- Core versions -->
    <java.version>11</java.version>
    <spring-boot.version>2.6.7</spring-boot.version>
    
    <!-- Security-related dependencies -->
    <jjwt.version>0.11.5</jjwt.version>
    <conjur-api.version>3.0.4</conjur-api.version>
    <bcrypt.version>0.9.0</bcrypt.version>
    
    <!-- Utility libraries -->
    <commons-lang3.version>3.12.0</commons-lang3.version>
    <guava.version>31.1-jre</guava.version>
    
    <!-- Test dependencies -->
    <testcontainers.version>1.17.1</testcontainers.version>
</properties>
```

### Dependency Security

Dependencies must be secured according to these guidelines:

1. **Vulnerability Scanning**: Regularly scan dependencies for vulnerabilities
2. **Security Updates**: Promptly apply security updates
3. **Dependency Approval**: New dependencies must be approved
4. **Transitive Dependencies**: Be aware of and manage transitive dependencies
5. **Dependency Sources**: Use trusted sources for dependencies

Example security scanning configuration:

```xml
<!-- OWASP Dependency Check for vulnerability scanning -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>7.1.1</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFiles>
            <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Dependency Organization

Dependencies must be organized according to these guidelines:

1. **Logical Grouping**: Group related dependencies together
2. **Comments**: Add comments to explain dependency purpose
3. **Ordering**: Order dependencies logically (e.g., by function or alphabetically)
4. **Multi-module Projects**: Use parent POM for common dependencies
5. **BOM Files**: Use Bill of Materials (BOM) files for version management

Example multi-module dependency management:

```xml
<!-- Parent POM -->
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        
        <!-- Project modules -->
        <dependency>
            <groupId>com.payment</groupId>
            <artifactId>payment-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.payment</groupId>
            <artifactId>payment-eapi</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.payment</groupId>
            <artifactId>payment-sapi</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- Third-party dependencies with versions -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <!-- More dependencies... -->
    </dependencies>
</dependencyManagement>
```

## Conclusion

These coding standards provide a comprehensive guide for developers working on the Payment API Security Enhancement project. Adherence to these standards ensures code quality, maintainability, security, and consistency across the codebase.

The standards cover:
- Code style and formatting
- Code organization
- Documentation
- Testing
- Security
- Performance
- Error handling
- Logging
- Dependency management

All developers are expected to follow these standards and participate in their continuous improvement. Suggestions for improvements to these standards should be submitted as pull requests to the documentation repository.

## References

1. [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
2. [Spring Framework Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/)
3. [OWASP Secure Coding Practices](https://owasp.org/www-project-secure-coding-practices-quick-reference-guide/)
4. [Security Guidelines](security-guidelines.md)
5. [Getting Started Guide](getting-started.md)
6. [High-Level Architecture](../architecture/high-level-architecture.md)
7. [Authentication Flow](../architecture/authentication-flow.md)