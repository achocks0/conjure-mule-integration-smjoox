package com.payment.sapi.config;

import com.payment.sapi.repository.PaymentRepository;
import com.payment.sapi.repository.TokenRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for database connections and JPA repositories in the Payment-Sapi service.
 * This class configures the database connection properties, connection pooling, transaction management,
 * and entity scanning for the Payment API Security Enhancement project.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.payment.sapi.repository")
public class DatabaseConfig {

    /**
     * Creates and configures the EntityManagerFactory for JPA.
     *
     * @param dataSource the data source to be used
     * @param env the Spring environment for accessing properties
     * @return a configured EntityManagerFactory bean
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Environment env) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.payment.sapi.repository.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(Boolean.parseBoolean(env.getProperty("spring.jpa.generate-ddl", "false")));
        vendorAdapter.setShowSql(Boolean.parseBoolean(env.getProperty("spring.jpa.show-sql", "false")));
        em.setJpaVendorAdapter(vendorAdapter);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", env.getProperty("spring.jpa.hibernate.ddl-auto", "none"));
        properties.put("hibernate.dialect", env.getProperty("spring.jpa.properties.hibernate.dialect"));
        properties.put("hibernate.format_sql", env.getProperty("spring.jpa.properties.hibernate.format_sql", "false"));
        properties.put("hibernate.default_schema", env.getProperty("spring.jpa.properties.hibernate.default_schema"));
        
        // Performance settings to meet SLA requirements
        properties.put("hibernate.jdbc.batch_size", env.getProperty("spring.jpa.properties.hibernate.jdbc.batch_size", "20"));
        properties.put("hibernate.order_inserts", env.getProperty("spring.jpa.properties.hibernate.order_inserts", "true"));
        properties.put("hibernate.order_updates", env.getProperty("spring.jpa.properties.hibernate.order_updates", "true"));
        properties.put("hibernate.jdbc.batch_versioned_data", env.getProperty("spring.jpa.properties.hibernate.jdbc.batch_versioned_data", "true"));
        
        // Connection pool settings
        properties.put("hibernate.connection.provider_class", env.getProperty("spring.jpa.properties.hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider"));
        properties.put("hibernate.hikari.maximumPoolSize", env.getProperty("spring.datasource.hikari.maximum-pool-size", "10"));
        properties.put("hibernate.hikari.minimumIdle", env.getProperty("spring.datasource.hikari.minimum-idle", "5"));
        properties.put("hibernate.hikari.connectionTimeout", env.getProperty("spring.datasource.hikari.connection-timeout", "30000"));
        properties.put("hibernate.hikari.idleTimeout", env.getProperty("spring.datasource.hikari.idle-timeout", "600000"));
        
        em.setJpaPropertyMap(properties);
        
        return em;
    }

    /**
     * Creates and configures the JPA transaction manager.
     *
     * @param entityManagerFactory the entity manager factory to be used
     * @return a configured transaction manager
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}