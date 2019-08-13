package com.iflytek.tps.foun.cluster;

import com.alibaba.fastjson.util.IOUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import com.iflytek.tps.foun.ccm.ConfConstant;
import com.iflytek.tps.foun.ccm.ConfigCenter;
import com.iflytek.tps.foun.dto.AppResponse;
import com.iflytek.tps.foun.dto.CommonCode;
import com.iflytek.tps.foun.dto.SrvInspectInfo;
import com.iflytek.tps.foun.helper.SpringBeanHelper;
import com.iflytek.tps.foun.util.*;
import org.apache.commons.lang3.StringUtils;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MembershipListener;
import org.jgroups.View;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.stack.AddressGenerator;
import org.jgroups.util.Buffer;
import org.jgroups.util.ExtendedUUID;
import org.jgroups.util.RspList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 同类型服务集群
 **/
public class JGroupsCluster {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsCluster.class);

    private final ConfigCenter configCenter = new ConfigCenter();

    private final Multimap<String, SrvMemberInfo> srvMap = HashMultimap.create();

    private final JChannel channel;
    private final MessageDispatcher messageDispatcher;

    public JGroupsCluster() {
        try {
            channel = new JChannel(new ClassPathResource("jgroups-cluster.xml").getInputStream());
            messageDispatcher = messageDispatcher();
            messageDispatcher.setMembershipListener(membershipListener());
            channel.addAddressGenerator(addressGenerator());
            channel.setDiscardOwnMessages(false);
            channel.connect(clusterName());
        } catch (Exception e) {
            throw new RuntimeException("create jgroups cluster error: ", e);
        }
    }

    public String serviceName() {
        return StringUtils.defaultIfBlank(SpringBeanHelper.applicationName(), "SRV");
    }

    protected String servicePort() {
        return SpringBeanHelper.applicationPort();
    }

    protected String clusterName() {
        return "Cluster@" + StringUtils.defaultIfBlank(SpringBeanHelper.applicationEnv(), "ENV");
    }

    /** 加入集群 **/
    public void join() {
        try {
            if(channel.isConnecting() || channel.isConnected()) {
                return;
            }
            channel.connect(clusterName());
        } catch (Exception e) {
            throw new RuntimeException("join jgroups cluster error: ", e);
        }
    }

    /** 离开集群 **/
    public void leave() {
        if (null != channel) {
            channel.disconnect();
        }
    }

    /** 同步给指定 member 发送消息 **/
    public List<Object> syncSend(ClusterMessage cm, Address... address) {
        assertSendAddress(address);
        return syncSend(cm, Sets.newHashSet(address));
    }

    /** 同步给指定 member 发送消息 **/
    public List<Object> syncSend(ClusterMessage cm, Collection<Address> address) {
        return sendClusterMessage(cm, address, RequestOptions.SYNC());
    }

    /** 异步给指定 member 发送消息 **/
    public void asyncSend(ClusterMessage cm, Address... address) {
        assertSendAddress(address);
        asyncSend(cm, Sets.newHashSet(address));
    }

    /** 异步给指定 member 发送消息 **/
    public void asyncSend(ClusterMessage cm, Collection<Address> address) {
        sendClusterMessage(cm, address, RequestOptions.ASYNC());
    }

    /** 所有服务 **/
    public Collection<SrvMemberInfo> allSrvInfo() {
        return Collections.unmodifiableCollection(srvMap.values());
    }

    /** 指定名称的服务 **/
    public Collection<SrvMemberInfo> srvInfoAgg(String serviceName){
        return srvInfoAgg(serviceName, (sm) -> true);
    }

    /** 指定名称的服务 **/
    public Collection<SrvMemberInfo> srvInfoAgg(String serviceName, String version){
        return srvInfoAgg(serviceName, (sm) -> sm.version.equals(version));
    }

    /** 选择指定服务一致性 Hash 负载均衡 **/
    public SrvMemberInfo choose(String serviceName, String version){
        return choose(serviceName, (sm)-> sm.version.equals(version));
    }

    /** 选择指定服务一致性 Hash 负载均衡 **/
    public SrvMemberInfo choose(String serviceName){
        return choose(serviceName, (smi)-> true);
    }

    /** 取 channel 的 DumpStatus **/
    public Map<String, Object> dumpStatus() {
        return channel.dumpStats();
    }

    /** 取当前 JGroup View **/
    public View view() {
        return channel.view();
    }

    @PreDestroy
    public void shutdown() {
        if (null != channel) {
            channel.close();
        }
        if(null != messageDispatcher){
            messageDispatcher.stop();
        }
    }

    /** 发送集群消息 **/
    private List<Object> sendClusterMessage(ClusterMessage cm, Collection<Address> address, RequestOptions options) {
        List<Object> rs = Lists.newArrayList();
        //当地址为空或只有自己时返回空 LIST
        if(CollectionUtils.isNullOrEmpty(address) || (1 == address.size() && address.contains(channel.address()))){
            return rs;
        }
        try {
            List<Address> adList = address.stream().filter(ad -> !ad.equals(channel.address())).collect(Collectors.toList());
            if(!CollectionUtils.isNullOrEmpty(adList)){
                Buffer buffer = new Buffer(JsonUtils.toJSONBytes(cm));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("syncSend send msg: {}", JsonUtils.toJSONString(cm));
                }
                RspList<Object> rspList = messageDispatcher.castMessage(adList, buffer, options);
                return null != rspList && adList.size() == rspList.numReceived() ? rspList.getResults() : rs;
            }
            LOG.info("send cluster msg address can not be empty.....");
            return rs;
        } catch (Exception e) {
            throw new RuntimeException("send join msg error: {}", e);
        }
    }

    /** 集群 Message 分发 **/
    private MessageDispatcher messageDispatcher() {
        return new MessageDispatcher(channel, msg -> {
            try {
                ClusterMessage cm = JsonUtils.parseObject(msg.getBuffer(), ClusterMessage.class);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("receive from member: {}, msg: {}", msg.getSrc(), JsonUtils.toJSONString(cm));
                }
                if(ConfConstant.CONF_PUSH.equals(cm.category) || ConfConstant.CONF_SYNC.equals(cm.category)){
                    return configCenter.handle(cm);
                }else {
                    ICMHandler cmh = SpringBeanHelper.getBean(ICMHandler.class);
                    if (null == cmh) {
                        LOG.info("ignore custom jgroups cluster msg.....");
                        return AppResponse.success();
                    }else {
                        return cmh.handle(cm);
                    }
                }
            } catch (Exception e) {
                LOG.error("handle receive msg error: ", e);
                return AppResponse.failed(CommonCode.Error);
            }
        });
    }

    /** 集群成员感知 **/
    private MembershipListener membershipListener() {
        return new MembershipListener() {
            @Override
            public void viewAccepted(View view) {
                if(LOG.isDebugEnabled()) {
                    LOG.info("viewAccepted:{}", view.toString());
                }
                updateSrvMap(view.getMembers());
            }

            @Override
            public void suspect(Address suspected) {
                LOG.warn("suspect:{}", suspected);
            }
        };
    }

    /** 集群地址生成器 **/
    private AddressGenerator addressGenerator() {
        return () -> ExtendedUUID.randomUUID()
                .put(ClusterConstant.SRV_NAME_KEY, serviceName().getBytes(IOUtils.UTF8))
                .put(ClusterConstant.VERSION_KEY, SrvInspectInfo.info().version.getBytes(IOUtils.UTF8))
                .put(ClusterConstant.HOST_KEY, NetworkUtils.ofInnerIp().getBytes(IOUtils.UTF8))
                .put(ClusterConstant.PORT_KEY, servicePort().getBytes(IOUtils.UTF8));
    }

    /** 更新本地服务信息 **/
    private void updateSrvMap(List<Address> addressList) {
        if (!CollectionUtils.isNullOrEmpty(addressList)) {
            Multimap<String, SrvMemberInfo> tmpMap = HashMultimap.create();
            for (Address address : addressList) {
                SrvMemberInfo smi = SrvMemberInfo.create((ExtendedUUID) address);
                tmpMap.put(smi.srvName, smi);
            }
            for(String key: tmpMap.keySet()){
                synchronized (srvMap) {
                    srvMap.replaceValues(key, tmpMap.get(key));
                }
            }
        }
    }

    /** 根据条件选择服务集合 **/
    public Collection<SrvMemberInfo> srvInfoAgg(String serviceName, Function<SrvMemberInfo, Boolean> condition){
        Collection<SrvMemberInfo> smiList = Collections.unmodifiableCollection(srvMap.get(serviceName));
        if(CollectionUtils.isNullOrEmpty(smiList)){
            return Lists.newArrayList();
        }
        return null == condition
                ? smiList
                : smiList.stream().filter(mi -> MathUtils.nvl(condition.apply(mi))).collect(Collectors.toList());
    }

    /** 根据条件选择服务 **/
    private SrvMemberInfo choose(String name, Function<SrvMemberInfo, Boolean> condition){
        Collection<SrvMemberInfo> smiCollection = Collections.unmodifiableCollection(srvMap.get(name));
        if(!CollectionUtils.isNullOrEmpty(smiCollection)) {
            Set<String> msiSet = smiCollection.stream()
                    .filter(mi -> MathUtils.nvl(condition.apply(mi)))
                    .map((mi) -> mi.getId()).collect(Collectors.toSet());
            SortedMap<Long, String> hashRingMap = DestHashUtils.makeHashRing(msiSet);
            long hash = DestHashUtils.hash(String.valueOf(DateUtils.time()));
            for (SrvMemberInfo smi : smiCollection) {
                if (smi.getId().equals(DestHashUtils.targetNode(hash, hashRingMap))) {
                    return smi;
                }
            }
        }
        throw new RuntimeException("can not choose available service for: " + name);
    }

    /** 判断发送消息的 Address 不能为空 **/
    private void assertSendAddress(Address[] address) {
        if(CollectionUtils.isNullOrEmpty(address)){
            throw new RuntimeException("send cluster msg address can not be empty...");
        }
    }
}
