package com.payment.eapi.config;

import com.payment.eapi.repository.CredentialRepository;
import com.payment.eapi.repository.TokenRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

/**
 * Configuration class for database connections and JPA repositories in the Payment-Eapi service.
 * Configures database connection properties, connection pooling, transaction management,
 * and entity scanning for the Payment API Security Enhancement project.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.payment.eapi.repository")
public class DatabaseConfig {

    /**
     * Creates and configures the EntityManagerFactory for JPA
     *
     * @param dataSource the DataSource to be used by the EntityManagerFactory
     * @param env the Spring Environment for accessing configuration properties
     * @return a configured EntityManagerFactory bean
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Environment env) {
        LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan("com.payment.eapi.repository.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(Boolean.parseBoolean(env.getProperty("spring.jpa.generate-ddl", "false")));
        vendorAdapter.setShowSql(Boolean.parseBoolean(env.getProperty("spring.jpa.show-sql", "false")));
        vendorAdapter.setDatabasePlatform(env.getProperty("spring.jpa.database-platform"));
        entityManagerFactory.setJpaVendorAdapter(vendorAdapter);
        
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.hbm2ddl.auto", env.getProperty("spring.jpa.hibernate.ddl-auto", "none"));
        jpaProperties.put("hibernate.dialect", env.getProperty("spring.jpa.properties.hibernate.dialect"));
        jpaProperties.put("hibernate.format_sql", env.getProperty("spring.jpa.properties.hibernate.format_sql", "false"));
        entityManagerFactory.setJpaProperties(jpaProperties);
        
        return entityManagerFactory;
    }

    /**
     * Creates and configures the JPA transaction manager
     *
     * @param entityManagerFactory the EntityManagerFactory to be used by the transaction manager
     * @return a configured PlatformTransactionManager
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}