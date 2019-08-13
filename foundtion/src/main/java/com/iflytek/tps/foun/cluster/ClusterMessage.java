package com.iflytek.tps.foun.cluster;

import java.io.Serializable;
import java.util.UUID;

/**
 * 集群间消息通信
 */
public class ClusterMessage implements Serializable{
    private static final long serialVersionUID = 5880216175775351558L;

    /** 消息唯一ID **/
    public String messageId;

    /** 消息类别 **/
    public String category;

    /** 消息体 **/
    public String body;

    public ClusterMessage() {
        this.messageId = UUID.randomUUID().toString();
    }

    private ClusterMessage(String category, String body) {
        this.messageId = UUID.randomUUID().toString();
        this.category = category;
        this.body = body;
    }

    /** 构建消息体方法 **/
    public static ClusterMessage create(String category, String body) {
        return new ClusterMessage(category, body);
    }
}
