package com.payment.sapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.sapi.service.CacheService;
import com.payment.sapi.service.TokenValidationService;
import com.payment.sapi.service.impl.RedisCacheServiceImpl;
import com.payment.sapi.model.Token;

import java.time.Duration;

/**
 * Configuration class for Redis cache in the Payment-SAPI module.
 * This class configures the Redis connection, serialization, and cache management for token 
 * storage and validation. It implements the caching strategy defined in the technical 
 * specifications to optimize token validation performance and reduce authentication overhead.
 */
@Configuration
@EnableRedisRepositories
public class RedisConfig {

    /**
     * Creates and configures a Redis connection factory with appropriate security settings
     * 
     * @return Configured Redis connection factory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory(RedisProperties redisProperties, 
                                                         RedisPoolProperties poolProperties) {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = 
            LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redisProperties.getTimeout()));
        
        if (redisProperties.isSsl()) {
            builder.useSsl();
        }
        
        // Configure connection pool if properties are provided
        if (poolProperties != null) {
            builder.clientOptions(org.springframework.data.redis.connection.lettuce.LettuceClientOptions.builder()
                .build());
        }
        
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        config.setDatabase(redisProperties.getDatabase());
        
        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        
        return new LettuceConnectionFactory(config, builder.build());
    }
    
    /**
     * Creates and configures a Redis template for operations with appropriate serialization
     * 
     * @return Configured Redis template
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        
        // Use StringRedisSerializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JdkSerializationRedisSerializer for values to handle Token objects
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.setHashValueSerializer(new JdkSerializationRedisSerializer());
        
        // Set default serializers
        template.setDefaultSerializer(new JdkSerializationRedisSerializer());
        
        template.afterPropertiesSet();
        
        return template;
    }
    
    /**
     * Creates and configures an ObjectMapper for JSON serialization
     * 
     * @return Configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JavaTimeModule for proper date/time handling
        mapper.registerModule(new JavaTimeModule());
        
        // Disable writing dates as timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return mapper;
    }
    
    /**
     * Creates a cache service bean that uses Redis for token storage and validation
     * 
     * @param redisTemplate Redis template for cache operations
     * @param objectMapper ObjectMapper for JSON serialization
     * @param tokenValidationService Service for token validation
     * @return Redis-based cache service implementation
     */
    @Bean
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate, 
                                   ObjectMapper objectMapper,
                                   TokenValidationService tokenValidationService) {
        return new RedisCacheServiceImpl(redisTemplate, objectMapper, tokenValidationService);
    }
}

/**
 * Configuration properties for Redis connection in Payment-SAPI
 */
@ConfigurationProperties(prefix = "spring.redis")
class RedisProperties {
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private boolean ssl = false;
    private int timeout = 2000;
    private int database = 0;
    
    public RedisProperties() {
        // Default constructor with default values
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public boolean isSsl() {
        return ssl;
    }
    
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public int getDatabase() {
        return database;
    }
    
    public void setDatabase(int database) {
        this.database = database;
    }
}

/**
 * Configuration properties for Redis connection pool in Payment-SAPI
 */
@ConfigurationProperties(prefix = "spring.redis.lettuce.pool")
class RedisPoolProperties {
    private int minIdle = 2;
    private int maxIdle = 10;
    private int maxActive = 20;
    private long timeBetweenEvictionRuns = 300000;
    
    public RedisPoolProperties() {
        // Default constructor with default values
    }
    
    public int getMinIdle() {
        return minIdle;
    }
    
    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }
    
    public int getMaxIdle() {
        return maxIdle;
    }
    
    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }
    
    public int getMaxActive() {
        return maxActive;
    }
    
    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }
    
    public long getTimeBetweenEvictionRuns() {
        return timeBetweenEvictionRuns;
    }
    
    public void setTimeBetweenEvictionRuns(long timeBetweenEvictionRuns) {
        this.timeBetweenEvictionRuns = timeBetweenEvictionRuns;
    }
}