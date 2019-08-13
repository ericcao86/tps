package com.iflytek.tps.foun.ccm;


import com.iflytek.tps.foun.util.NetworkUtils;

/**
 * Created by losyn on 5/28/17.
 */
public class ConfReqBody {
    public String srvName;

    public String version;

    public String host;

    public String port;

    public ConfReqBody() {
    }

    private ConfReqBody(String srvName, String version, String port) {
        this.srvName = srvName;
        this.version = version;
        this.host = NetworkUtils.ofInnerIp();
        this.port = port;
    }

    public static ConfReqBody create(String srvName, String version, String port){
        return new ConfReqBody(srvName, version, port);
    }
}
