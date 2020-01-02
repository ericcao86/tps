package com.iflytek.tps.foun.redis;

import com.google.common.base.Splitter;
import com.iflytek.tps.foun.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Created by losyn on 3/30/17.
 */
public abstract class RedisConfigurationSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RedisConfigurationSupport.class);
    protected String defaultRedisUri;

    @PostConstruct
    protected abstract void initConfiguration();

    @Primary
    @Bean(name = IDynamicRedis.DEFAULT)
    public RedisPairOperations defaultRedisPairOperations() {
        return new RedisPairOperations(defaultStringRedisTemplate());
    }

    @Bean
    @Primary
    public StringRedisTemplate defaultStringRedisTemplate(){
        return new StringRedisTemplate(jedisConnectionFactory(defaultRedisUri));
    }

    /** JEDIS://password@hostname:port/database */
    protected RedisConnectionFactory jedisConnectionFactory(String jedisUri) {
        LOG.info("jedis url: {}", jedisUri);

        List<String> uriList = Splitter.on("@").splitToList(jedisUri);
        if(CollectionUtils.isNullOrEmpty(uriList) || 2 != uriList.size()){
            throw new RuntimeException("jedis uri：" + jedisUri + "  error.....");
        }
        List<String> dbHost = Splitter.on("/").splitToList(uriList.get(1));
        if(CollectionUtils.isNullOrEmpty(uriList) || 2 != uriList.size()){
            throw new RuntimeException("jedis uri：" + jedisUri + "  error.....");
        }
        List<String> hostPost = Splitter.on(":").splitToList(dbHost.get(0));
        if(CollectionUtils.isNullOrEmpty(uriList) || 2 != uriList.size()){
            throw new RuntimeException("jedis uri：" + jedisUri + "  error.....");
        }

        JedisConnectionFactory jedis = new JedisConnectionFactory();
        jedis.setHostName(hostPost.get(0));
        jedis.setPort(Integer.parseInt(hostPost.get(1)));
        String password = StringUtils.substringAfter(uriList.get(0), "JEDIS://");
        if (!StringUtils.isBlank(password)) {
            jedis.setPassword(password);
        }
        jedis.setDatabase(Integer.parseInt(dbHost.get(1)));
        jedis.setPoolConfig(new JedisPoolConfig());
        jedis.afterPropertiesSet();
        return jedis;
    }
}
