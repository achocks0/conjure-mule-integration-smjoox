package com.payment.eapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.payment.eapi.filter.ClientCredentialsAuthenticationFilter;
import com.payment.eapi.service.ConjurService;
import com.payment.eapi.service.TokenService;
import com.payment.eapi.service.CacheService;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;

/**
 * Spring Security configuration class for the Payment-Eapi component that implements
 * the security framework for the Payment API Security Enhancement project.
 * This class configures authentication mechanisms, security filters, and access control rules
 * to maintain backward compatibility with existing vendor integrations while enhancing
 * internal security through token-based authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    private final ConjurService conjurService;
    private final TokenService tokenService;
    private final CacheService cacheService;
    
    /**
     * Initializes the security configuration with required services
     * 
     * @param conjurService Service for retrieving and validating credentials from Conjur vault
     * @param tokenService Service for generating and managing JWT tokens
     * @param cacheService Service for caching tokens to reduce Conjur vault requests
     */
    public SecurityConfig(ConjurService conjurService, TokenService tokenService, CacheService cacheService) {
        super();
        this.conjurService = conjurService;
        this.tokenService = tokenService;
        this.cacheService = cacheService;
        logger.info("Initializing Security Configuration");
    }
    
    /**
     * Configures HTTP security for the application, defining access rules, CSRF protection,
     * and authentication filters
     * 
     * @param http the HttpSecurity to configure
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
                .antMatchers("/api/health/**").permitAll()
                .antMatchers("/api/metrics/**").hasRole("MONITORING")
                // Require authentication for all other endpoints
                .anyRequest().authenticated()
            .and()
                // Add ClientCredentialsAuthenticationFilter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(clientCredentialsAuthenticationFilter(), 
                                UsernamePasswordAuthenticationFilter.class)
            
            // Configure authentication failure handling
            .exceptionHandling()
                .authenticationEntryPoint(customAuthenticationEntryPoint())
            .and()
                
            // Configure session management to be stateless
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
    
    /**
     * Creates a bean for the ClientCredentialsAuthenticationFilter that authenticates
     * requests using Client ID and Client Secret headers
     * 
     * @return Configured authentication filter
     */
    @Bean
    public ClientCredentialsAuthenticationFilter clientCredentialsAuthenticationFilter() {
        return new ClientCredentialsAuthenticationFilter(conjurService, tokenService, cacheService);
    }
    
    /**
     * Creates a bean for a custom authentication entry point that handles authentication failures
     * 
     * @return Custom authentication entry point
     */
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                                org.springframework.security.core.AuthenticationException authException)
                                throws IOException, ServletException {
                
                logger.warn("Authentication failed: {}", authException.getMessage());
                
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                
                String errorResponse = String.format(
                    "{\"errorCode\":\"AUTH_ERROR\",\"message\":\"%s\",\"requestId\":\"%s\",\"timestamp\":\"%s\"}",
                    authException.getMessage().replace("\"", "\\\""),
                    request.getHeader("X-Request-ID") != null ? request.getHeader("X-Request-ID") : "unknown",
                    Instant.now().toString()
                );
                
                response.getWriter().write(errorResponse);
            }
        };
    }
    
    /**
     * Creates a bean for Jackson ObjectMapper used for JSON serialization/deserialization
     * 
     * @return Configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}