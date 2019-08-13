package com.iflytek.tps.foun.helper;

import com.iflytek.tps.foun.util.DateUtils;
import org.springframework.http.server.ServerHttpResponse;

import javax.servlet.http.HttpServletResponse;

/**
 * Created by losyn on 4/17/17.
 */
public final class RestPerformsHelper {
    private static final String X_REQUEST_TIME = "X-Request-Time";
    private static final String X_RESPONSE_TIME = "X-Response-Time";

    private RestPerformsHelper() {
    }

    public static void requestTime(HttpServletResponse response){
        response.setHeader(X_REQUEST_TIME, String.valueOf(DateUtils.time()));
    }

    public static void responseTime(HttpServletResponse response){
        response.setHeader(X_RESPONSE_TIME, String.valueOf(DateUtils.time()));
    }

    public static void responseTime(ServerHttpResponse response){
        response.getHeaders().set(X_RESPONSE_TIME, String.valueOf(DateUtils.time()));
    }
}
