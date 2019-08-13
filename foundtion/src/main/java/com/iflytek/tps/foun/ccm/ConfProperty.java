package com.iflytek.tps.foun.ccm;

import java.io.Serializable;
import java.util.Properties;


public class ConfProperty implements Serializable{
    private static final long serialVersionUID = -8614764037755257254L;

    public String srvName;

    public String version;

    public String oldVersion;

    public Properties properties;

    public ConfProperty() {
    }

    private ConfProperty(String srvName, String version, Properties properties) {
        this.srvName = srvName;
        this.version = version;
        this.properties = properties;
    }

    public static ConfProperty create(String srvName, String version, Properties properties){
        return new ConfProperty(srvName, version, properties);
    }
}
