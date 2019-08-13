package com.iflytek.tps.foun.ccm;

import com.google.common.base.Joiner;
import com.iflytek.tps.foun.util.CollectionUtils;
import com.iflytek.tps.foun.util.HttpRestUtils;
import com.iflytek.tps.foun.util.NetworkUtils;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.http.MediaType;

import java.util.Properties;

/**
 * 配置初始化，主要用于项目中配置外移
 */
public class ConfInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    /** 配置初始化方法 **/
    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        ConfigurableEnvironment env = ctx.getEnvironment();

        String name = ConfConstant.applicationName(env);
        String port = ConfConstant.applicationPort(env);
        String profile = ConfConstant.applicationProfile(env);
        String ctxId = Joiner.on(":").join(name, profile, port);
        //当前服务的 ctxId 则取远程配置
        if (ctx.getId().equals(ctxId)) {
            Properties properties = new Properties();
            properties.setProperty("server.address", NetworkUtils.ofInnerIp());
            if("true".equals("config.center.enable") && "true".equals("jgroups.cluster.enable")) {
                Properties conf = pullConf(profile, name, port);
                if (!CollectionUtils.isNullOrEmpty(conf)) {
                    properties.putAll(conf);
                }
            }
            PropertiesPropertySource ps = new PropertiesPropertySource(ConfConstant.CONF_NAME, properties);
            env.getPropertySources().addFirst(ps);
        }
    }

    /** 客户端拉取所有配置信息 **/
    public Properties pullConf(String profile, String srvName, String port) {
        Properties properties = new Properties();
        if(profile.startsWith("http://") || profile.startsWith("https://")){
            String [] pq = profile.split("？");
            int index = pq[1].indexOf(ConfConstant.CONF_V) + ConfConstant.CONF_V.length();
            String version = StringUtils.substring(pq[1], index);
            String suffix = pq[0].endsWith("/") ? "" : "/";
            StringBuilder url = new StringBuilder(pq[0]).append(suffix).append(ConfConstant.CONF_PULL);
            HttpRestUtils.post(url.toString(), body(version, srvName, port), Properties.class);
        }
        return properties;
    }

    /** 系统启动拉取配置请求体 **/
    private RequestBody body(String version, String srvName, String port) {
        return HttpRestUtils.buildBody(MediaType.APPLICATION_JSON, ConfReqBody.create(srvName, version, port));
    }
}
