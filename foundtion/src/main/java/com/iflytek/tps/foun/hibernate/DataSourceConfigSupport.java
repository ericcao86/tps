package com.iflytek.tps.foun.hibernate;

import com.google.common.collect.Maps;
import com.iflytek.tps.foun.util.CollectionUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MySQL5Dialect;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.type.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Map;
import java.util.Properties;

@EnableTransactionManagement
public abstract class DataSourceConfigSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DataSourceConfigSupport.class);

    private final Map<Object, Object> dsMap = Maps.newConcurrentMap();

    /** 需要扫描的 Entity 包 **/
    protected abstract String[] entityScanPackages();

    /** 打印SQL语句 **/
    protected boolean showSql(){
        return true;
    }

    /** 添加多个数据源 **/
    protected abstract void addMultiDataSource(Map<Object, Object> dsMap);

    /** 创建数据源方法 **/
    @SuppressWarnings("unused")
    protected HikariDataSource hikariDataSource(String dataSourceUri) {
        LOG.info("hikari datasource url: {}", dataSourceUri);
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(dataSourceUri);
        cfg.setDriverClassName("com.mysql.jdbc.Driver");
        cfg.addDataSourceProperty("autoReconnect", "true");
        cfg.addDataSourceProperty("useUnicode", "true");
        cfg.addDataSourceProperty("characterEncoding", "utf8");
        cfg.addDataSourceProperty("useSSL", false);
        cfg.addDataSourceProperty("zeroDateTimeBehavior", "convertToNull");
        cfg.addDataSourceProperty("transformedBitIsBoolean", "true");
        return new HikariDataSource(cfg);
    }

    /** 定义动态数据源 **/
    @Bean
    @Primary
    public DynamicDataSource dynamicDataSource() {
        addMultiDataSource(dsMap);
        DynamicDataSource dataSource = new DynamicDataSource();
        dataSource.setTargetDataSources(dsMap);
        dataSource.setDefaultTargetDataSource(dsMap.get(IDynamicDS.DEFAULT));
        return dataSource;
    }

    /**
     * Declare the JPA entity manager factory.
     */
    @Bean
    @Autowired
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DynamicDataSource dataSource) {
        LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        String[] packages = entityScanPackages();
        if(CollectionUtils.isNullOrEmpty(packages)){
            throw new RuntimeException("entity scan packages can not be empty.....");
        }
        entityManagerFactory.setPackagesToScan(packages);
        // Vendor adapter
        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        // Hibernate properties
        Properties properties = new Properties();
        properties.put(AvailableSettings.AUTOCOMMIT, false);
        properties.put(AvailableSettings.SHOW_SQL, showSql());
        properties.put(AvailableSettings.DIALECT, MySQL5LocalDialect.class.getName());
        properties.put(AvailableSettings.HBM2DDL_AUTO, "update");
        entityManagerFactory.setJpaProperties(properties);
        return entityManagerFactory;
    }

    /** Declare the transaction manager **/
    @Bean
    @Autowired
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }

    @Bean
    @Autowired
    public DataSourceInterceptor dataSourceInterceptor() {
        return new DataSourceInterceptor();
    }

    /**
     * PersistenceExceptionTranslationPostProcessor is a bean post processor
     * which adds an advisor to any bean annotated with Repository so that any
     * platform-specific exceptions are caught and then rethrown as one
     * Spring's unchecked data access exceptions (i.e. a subclass of
     * DataAccessException).
     */
    @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    public static class MySQL5LocalDialect extends MySQL5Dialect {
        public MySQL5LocalDialect() {
            super();
            registerFunction("CONVERT", new SQLFunctionTemplate(StringType.INSTANCE , "CONVERT(?1 USING ?2)"));
        }
    }
}
