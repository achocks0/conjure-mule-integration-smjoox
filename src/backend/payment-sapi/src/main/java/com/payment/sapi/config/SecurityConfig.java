package com.payment.sapi.config;

import com.payment.sapi.filter.TokenAuthenticationFilter;
import com.payment.sapi.service.TokenValidationService;
import com.payment.sapi.service.TokenRenewalService;
import com.payment.common.model.ErrorResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.http.SessionCreationPolicy;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Arrays;

/**
 * Spring Security configuration class for the Payment-Sapi component that implements
 * token-based authentication for internal service communication. This class configures
 * security filters, access control rules, and token validation mechanisms to ensure
 * secure communication between services while maintaining the enhanced security posture
 * of the system.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final TokenValidationService tokenValidationService;
    private final TokenRenewalService tokenRenewalService;
    private final String audience;
    private final List<String> issuers;

    /**
     * Initializes the security configuration with required services and properties.
     *
     * @param tokenValidationService Service for validating JWT tokens
     * @param tokenRenewalService Service for renewing expired JWT tokens
     * @param audience The expected audience value for incoming tokens
     * @param issuers List of trusted token issuers
     */
    public SecurityConfig(
            TokenValidationService tokenValidationService,
            TokenRenewalService tokenRenewalService,
            @Value("${token.audience}") String audience,
            @Value("#{'${token.issuers}'.split(',')}") List<String> issuers) {
        
        super();
        this.tokenValidationService = tokenValidationService;
        this.tokenRenewalService = tokenRenewalService;
        this.audience = audience;
        this.issuers = issuers;
        
        logger.info("Initializing security configuration with audience '{}' and issuers: {}", 
                audience, String.join(", ", issuers));
    }

    /**
     * Configures HTTP security for the application, defining access rules,
     * CSRF protection, and authentication filters.
     *
     * @param http The HttpSecurity to modify
     * @throws Exception if an error occurs during configuration
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // Disable CSRF protection as the API uses token-based authentication
            .csrf().disable()
            
            // Configure request authorization rules
            .authorizeRequests()
                // Allow unauthenticated access to health and metrics endpoints
                .antMatchers("/health/**", "/metrics/**").permitAll()
                // Require authentication for all other endpoints
                .anyRequest().authenticated()
            .and()
                
            // Add TokenAuthenticationFilter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(tokenAuthenticationFilter(), 
                            UsernamePasswordAuthenticationFilter.class)
            
            // Configure custom authentication entry point for handling authentication failures
            .exceptionHandling()
                .authenticationEntryPoint(customAuthenticationEntryPoint())
            .and()
            
            // Configure session management to be stateless
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    /**
     * Creates a bean for the TokenAuthenticationFilter that authenticates requests using JWT tokens.
     *
     * @return Configured authentication filter
     */
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter() {
        return new TokenAuthenticationFilter(
                tokenValidationService,
                tokenRenewalService,
                audience,
                issuers);
    }

    /**
     * Creates a bean for a custom authentication entry point that handles authentication failures.
     *
     * @return Custom authentication entry point
     */
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, 
                org.springframework.security.core.AuthenticationException authException) -> {
            
            logger.warn("Authentication failed: {}", authException.getMessage());
            
            // Create standardized error response
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode("AUTH_ERROR")
                    .message("Authentication failed: " + authException.getMessage())
                    .requestId(request.getHeader("X-Request-ID"))
                    .timestamp(new java.util.Date())
                    .build();
            
            // Set response content type and status
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            
            // Write error response as JSON
            objectMapper().writeValue(response.getOutputStream(), errorResponse);
        };
    }

    /**
     * Creates a bean for Jackson ObjectMapper used for JSON serialization/deserialization.
     *
     * @return Configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}