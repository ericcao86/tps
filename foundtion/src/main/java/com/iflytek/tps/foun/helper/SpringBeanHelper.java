package com.iflytek.tps.foun.helper;

import com.iflytek.tps.foun.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class SpringBeanHelper implements ApplicationContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(SpringBeanHelper.class);
    protected static ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringBeanHelper.ctx = applicationContext;
    }

    public static <T> T getBean(Class<T> clazz) {
        try {
            return ctx.getBean(clazz);
        } catch (NoSuchBeanDefinitionException e) {
            LOG.warn("no qualifying bean of type: {}", clazz);
            return null;
        }
    }

    public static <T> T getBean(String beanName, Class<T> clazz) {
        try {
            return ctx.getBean(beanName, clazz);
        } catch (NoSuchBeanDefinitionException e) {
            LOG.warn("no qualifying bean of name: {}, type: {}", beanName, clazz);
            return null;
        }
    }

    public static String projectDir(){
        return System.getProperty("user.dir");
    }

    public static String contextPath() {
        if(null == ctx || null == ctx.getEnvironment()){
            return StringUtils.EMPTY;
        }
        return StringUtils.defaultIfBlank(ctx.getEnvironment().getProperty("server.context-path"), StringUtils.EMPTY);
    }

    public static String applicationName() {
        if(null == ctx || null == ctx.getEnvironment()){
            return StringUtils.EMPTY;
        }
        return ctx.getEnvironment().getProperty("spring.application.name");
    }

    public static String applicationPort(){
        if(null == ctx || null == ctx.getEnvironment()){
            return "0";
        }
        return ctx.getEnvironment().getProperty("server.port");
    }

    public static String applicationEnv() {
        if(null == ctx || null == ctx.getEnvironment()){
            return StringUtils.EMPTY;
        }
        String[] envList = ctx.getEnvironment().getActiveProfiles();
        if (!CollectionUtils.isNullOrEmpty(envList)) {
            return envList[0];
        }
        return StringUtils.EMPTY;
    }
}