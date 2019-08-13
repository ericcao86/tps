package com.iflytek.tps.foun.authority;

import com.google.common.collect.Maps;

import java.util.Map;

public class AuthoritySession {
    public static final String X_Authority_Header = "X-Authority";

    /** 租户ID **/
    public String tenantId;

    /** 用户 ID */
    public String userId;

    /** 用户角色 ID */
    public String roleId;

    /** app 版本 */
    public String vNum;

    /** 下载渠道 */
    public String channel;

    /** 设备: IOS, Android, PC */
    public String device;

    /** 设备编号 */
    public String udid;

    /** 请求时的客户端时间 */
    public String clientTime;

    /** 客户端IP **/
    public String clientIp;

    /** 扩展数据 **/
    public Map<String, String> extMap = Maps.newHashMap();
}
