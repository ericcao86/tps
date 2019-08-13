package com.iflytek.tps.foun.ccm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

/**
 * 配置需要的常量数据
 */
public interface ConfConstant {
    /** 配置名称 **/
    String CONF_NAME = "application.conf";

    /** 配置版本 **/
    String CONF_V = "version=";

    /** 拉取配置接口 **/
    String CONF_PULL = "config/pull";

    /** 推送配置信息 **/
    String CONF_PUSH = "==>cluster.ConfigCenter.PUSH";

    /** 配置服务间同步数据 **/
    String CONF_SYNC = "==>cluster.ConfigCenter.SYNC";

    /** 推送了非本服务的配置信息 **/
    String PUSH_CONF_E = "pushNotMineConf:ERROR";

    /** 刷新服务配置信息出错 **/
    String REFRESH_CONF_E = "refreshSrvConf:ERROR";

    /** 配置服务之间同步配置信息出错 **/
    String SYNC_CONF_E = "syncSrvConf:ERROR";

    /** 服务名 **/
    static String applicationName(Environment env){
        return StringUtils.defaultIfBlank(env.getProperty("spring.application.name"), "srv");
    }

    /** 服务端口 **/
    static String applicationPort(Environment env){
        return StringUtils.defaultIfBlank(env.getProperty("server.port"), "0");
    }

    /** 启用的环境 **/
    static String applicationProfile(Environment env){
        return StringUtils.defaultString(env.getProperty("spring.profiles.active"));
    }
}
