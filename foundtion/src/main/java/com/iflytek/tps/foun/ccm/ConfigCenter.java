package com.iflytek.tps.foun.ccm;

import com.google.common.base.Joiner;

import com.iflytek.tps.foun.cluster.ClusterMessage;
import com.iflytek.tps.foun.cluster.ICMHandler;
import com.iflytek.tps.foun.cluster.JGroupsCluster;
import com.iflytek.tps.foun.cluster.SrvMemberInfo;
import com.iflytek.tps.foun.dto.AppResponse;
import com.iflytek.tps.foun.dto.CommonCode;
import com.iflytek.tps.foun.dto.IMessageCode;
import com.iflytek.tps.foun.helper.SpringBeanHelper;
import com.iflytek.tps.foun.util.CollectionUtils;
import com.iflytek.tps.foun.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerCompressionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 配置消息处理
 */
public class ConfigCenter implements ICMHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigCenter.class);

    private final DB mapDB = DBMaker.fileDB("application.conf.db")
            .closeOnJvmShutdownWeakReference()
            .fileMmapEnableIfSupported()
            .closeOnJvmShutdown()
            .transactionEnable().make();

    private final ConcurrentMap<String, String> confMap = mapDB.hashMap("services.conf")
            .keySerializer(Serializer.STRING)
            .valueSerializer(new SerializerCompressionWrapper<>(Serializer.STRING))
            .createOrOpen();

    /** 保存配置 **/
    public void storeConf(ConfProperty confP){
        upsertConf(confP, cp -> {
            JGroupsCluster cluster = SpringBeanHelper.getBean(JGroupsCluster.class);
            //同步配置中心兄弟节点数据
            Collection<SrvMemberInfo> smList = cluster.srvInfoAgg(cluster.serviceName());
            if(smList.size() > 1) {
                ClusterMessage scm = ClusterMessage.create(ConfConstant.CONF_SYNC, JsonUtils.toJSONString(confP));
                List<Object> rs = cluster.syncSend(scm, smList.stream().map(sm -> sm.address).collect(Collectors.toList()));
                if(CollectionUtils.isNullOrEmpty(rs)){
                    throw new RuntimeException("sync conf to sibling nodes error.....");
                }
            }
            //刷新服务节点配置
            Collection<SrvMemberInfo> srvList = cluster.srvInfoAgg(confP.srvName, confP.oldVersion);
            if(!CollectionUtils.isNullOrEmpty(srvList)){
                ClusterMessage pcm = ClusterMessage.create(ConfConstant.CONF_PUSH, JsonUtils.toJSONString(confP));
                List<Object> rs = cluster.syncSend(pcm, srvList.stream().map(sm -> sm.address).collect(Collectors.toList()));
                if(CollectionUtils.isNullOrEmpty(rs)){
                    throw new RuntimeException("refresh conf to service: " + confP.srvName + " error.....");
                }
            }
        });
    }

    /** 获取服务指定版本的配置信息 **/
    public String pullConf(String srvName, String version){
        String conf = confMap.get(srvConfKey(srvName, version));
        return StringUtils.isBlank(conf) ? StringUtils.EMPTY : conf;
    }

    /** 接收并处理配置消息 **/
    @Override
    public AppResponse handle(ClusterMessage msg) {
        if(ConfConstant.CONF_PUSH.equals(msg.category)){
            return refreshSrvConf(msg.body);
        }else if(ConfConstant.CONF_SYNC.equals(msg.category)){
            return ccmSync(msg.body);
        }
        return AppResponse.failed(CommonCode.Error);
    }

    /** 配置中心数据修改刷新客户端 **/
    private AppResponse refreshSrvConf(String body) {
        try {
            ConfigurableEnvironment env = SpringBeanHelper.getBean(ConfigurableEnvironment.class);
            MutablePropertySources mps = env.getPropertySources();
            String confName = ConfConstant.applicationName(env);
            if (mps.contains(confName)) {
                ConfProperty cp = JsonUtils.parseObject(body, ConfProperty.class);
                String mineName = SpringBeanHelper.applicationName();
                if(mineName.equals(cp.srvName)){
                    mps.replace(confName, new PropertiesPropertySource(confName, cp.properties));
                }else {
                    LOG.error("config center push srv: {} not mine: {}", cp.srvName, mineName);
                    return AppResponse.failed(new IMessageCode() {
                        @Override public String code() {
                            return ConfConstant.PUSH_CONF_E;
                        }
                        @Override public String msg() {
                            return "推送了非本服务的配置信息";
                        }
                    });
                }
            }
            return AppResponse.success();
        } catch (Exception e){
            return AppResponse.failed(new IMessageCode() {
                @Override public String code() {
                    return ConfConstant.REFRESH_CONF_E;
                }
                @Override public String msg() {
                    return "刷新服务配置信息出错";
                }
            });
        }
    }

    /** 集群配置中心数据同步 **/
    private AppResponse ccmSync(String body) {
        try {
            upsertConf(JsonUtils.parseObject(body, ConfProperty.class), cp -> LOG.info("sync srv config success....."));
            return AppResponse.success();
        }catch (Exception e){
            LOG.error("sync srv config: {}, error: ", body, e);
            return AppResponse.failed(new IMessageCode() {
                @Override public String code() {
                    return ConfConstant.SYNC_CONF_E;
                }
                @Override public String msg() {
                    return "配置服务之间同步配置信息出错";
                }
            });
        }
    }

    /** 保存或更新配置信息 **/
    private void upsertConf(ConfProperty cp, Consumer<ConfProperty> consumer) {
        try {
            confMap.put(srvConfKey(cp.srvName, cp.version), JsonUtils.toJSONString(cp.properties));
            if(null != consumer){
                consumer.accept(cp);
            }
            mapDB.commit();
        }catch (Exception e){
            LOG.error("save or update conf error: ", e);
            mapDB.rollback();
        }
    }

    /** 生成配置 KEY **/
    private String srvConfKey(String srvName, String version){
        return Joiner.on(":").join(srvName, version);
    }
}
