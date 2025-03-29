package com.payment.eapi.config;

import com.payment.eapi.service.CacheService;
import com.payment.eapi.service.impl.RedisCacheServiceImpl;
import com.payment.eapi.model.Token;
import com.payment.eapi.model.Credential;

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
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Duration;
import org.springframework.util.StringUtils;

/**
 * Configuration class for Redis cache in the Payment-EAPI module. This class configures
 * the Redis connection, serialization, and cache management for token and credential storage.
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
    public RedisConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = 
            LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redisProperties.getTimeout()));
        
        if (redisProperties.isSsl()) {
            builder.useSsl();
        }
        
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        
        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        
        config.setDatabase(redisProperties.getDatabase());
        
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
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new JdkSerializationRedisSerializer());
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
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    /**
     * Creates a cache service bean that uses Redis for token and credential storage
     *
     * @param redisTemplate the Redis template for cache operations
     * @param objectMapper the object mapper for serialization/deserialization
     * @return Redis-based cache service implementation
     */
    @Bean
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        return new RedisCacheServiceImpl(redisTemplate, objectMapper);
    }
}

/**
 * Configuration properties for Redis connection in Payment-EAPI
 */
@ConfigurationProperties(prefix = "spring.redis")
class RedisProperties {
    private String host;
    private int port;
    private String password;
    private boolean ssl;
    private int timeout;
    private int database;
    
    /**
     * Default constructor for RedisProperties
     */
    public RedisProperties() {
        // Initialize with default values: host="localhost", port=6379, ssl=false, timeout=2000, database=0
        this.host = "localhost";
        this.port = 6379;
        this.ssl = false;
        this.timeout = 2000;
        this.database = 0;
    }
    
    /**
     * Returns the Redis host
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Sets the Redis host
     */
    public void setHost(String host) {
        this.host = host;
    }
    
    /**
     * Returns the Redis port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Sets the Redis port
     */
    public void setPort(int port) {
        this.port = port;
    }
    
    /**
     * Returns the Redis password
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * Sets the Redis password
     */
    public void setPassword(String password) {
        this.password = password;
    }
    
    /**
     * Returns whether SSL is enabled for Redis
     */
    public boolean isSsl() {
        return ssl;
    }
    
    /**
     * Sets whether SSL is enabled for Redis
     */
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }
    
    /**
     * Returns the Redis connection timeout in milliseconds
     */
    public int getTimeout() {
        return timeout;
    }
    
    /**
     * Sets the Redis connection timeout in milliseconds
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    /**
     * Returns the Redis database index
     */
    public int getDatabase() {
        return database;
    }
    
    /**
     * Sets the Redis database index
     */
    public void setDatabase(int database) {
        this.database = database;
    }
}

/**
 * Configuration properties for Redis connection pool in Payment-EAPI
 */
@ConfigurationProperties(prefix = "spring.redis.lettuce.pool")
class RedisPoolProperties {
    private int minIdle;
    private int maxIdle;
    private int maxActive;
    private long timeBetweenEvictionRuns;
    
    /**
     * Default constructor for RedisPoolProperties
     */
    public RedisPoolProperties() {
        // Initialize with default values: minIdle=2, maxIdle=10, maxActive=20, timeBetweenEvictionRuns=300000
        this.minIdle = 2;
        this.maxIdle = 10;
        this.maxActive = 20;
        this.timeBetweenEvictionRuns = 300000;
    }
    
    /**
     * Returns the minimum number of idle connections
     */
    public int getMinIdle() {
        return minIdle;
    }
    
    /**
     * Sets the minimum number of idle connections
     */
    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }
    
    /**
     * Returns the maximum number of idle connections
     */
    public int getMaxIdle() {
        return maxIdle;
    }
    
    /**
     * Sets the maximum number of idle connections
     */
    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }
    
    /**
     * Returns the maximum number of active connections
     */
    public int getMaxActive() {
        return maxActive;
    }
    
    /**
     * Sets the maximum number of active connections
     */
    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }
    
    /**
     * Returns the time between eviction runs in milliseconds
     */
    public long getTimeBetweenEvictionRuns() {
        return timeBetweenEvictionRuns;
    }
    
    /**
     * Sets the time between eviction runs in milliseconds
     */
    public void setTimeBetweenEvictionRuns(long timeBetweenEvictionRuns) {
        this.timeBetweenEvictionRuns = timeBetweenEvictionRuns;
    }
}