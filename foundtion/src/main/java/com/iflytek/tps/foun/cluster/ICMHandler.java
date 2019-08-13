package com.iflytek.tps.foun.cluster;


import com.iflytek.tps.foun.dto.AppResponse;

/**
 * 集群消息处理
 */
public interface ICMHandler {
    /** 集群消息处理方法 **/
    default AppResponse handle(ClusterMessage msg){
        return AppResponse.success();
    }
}
