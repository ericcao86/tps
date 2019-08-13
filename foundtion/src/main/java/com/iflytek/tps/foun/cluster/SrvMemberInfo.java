package com.iflytek.tps.foun.cluster;

import com.alibaba.fastjson.util.IOUtils;
import org.jgroups.Address;
import org.jgroups.util.ExtendedUUID;

import java.util.UUID;

/**
 * Created by losyn on 5/9/17.
 */
public class SrvMemberInfo {
    private final String id;

    public String srvName;

    public String version;

    public String host;

    public String port;

    public Address address;

    public String getId(){
        return this.id;
    }

    public SrvMemberInfo() {
        this.id = UUID.randomUUID().toString();
    }

    private SrvMemberInfo(String srvName, String version, String host, String port, Address address) {
        this.srvName = srvName;
        this.version = version;
        this.host = host;
        this.port = port;
        this.address = address;
        this.id = srvName + ":" + version + "@" + host + ":" + port;
    }

    public static SrvMemberInfo create(ExtendedUUID address){
        return new SrvMemberInfo(new String(address.get(ClusterConstant.SRV_NAME_KEY), IOUtils.UTF8),
                                 new String(address.get(ClusterConstant.VERSION_KEY), IOUtils.UTF8),
                                 new String(address.get(ClusterConstant.HOST_KEY), IOUtils.UTF8),
                                 new String(address.get(ClusterConstant.PORT_KEY), IOUtils.UTF8),
                                 address);
    }
}
