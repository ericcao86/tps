package com.iflytek.tps.foun.helper;


import com.iflytek.tps.foun.dto.AppException;
import com.iflytek.tps.foun.dto.AppResponse;
import com.iflytek.tps.foun.dto.CommonCode;
import com.iflytek.tps.foun.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.io.OutputStream;

/**
 * Created by losyn on 4/16/17.
 */
public final class ExceptionHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHelper.class);

    private ExceptionHelper() {
    }

    public static AppResponse<Void> response(int code){
        switch (code){
            case 403:
                return AppResponse.failed(CommonCode.Forbidden);
            case 404:
                return AppResponse.failed(CommonCode.NotFound);
            default:
                return AppResponse.failed(CommonCode.Error);
        }
    }

    public static void response(HttpServletResponse response, Throwable ex) {
        if(!response.isCommitted()){
            try {
                response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
                byte[] error = JsonUtils.toJSONBytes(response(ex));
                response.setContentLength(error.length);
                try (OutputStream os = response.getOutputStream()) {
                    os.write(error);
                }
            }catch (Exception e){
                LOG.error("write exception response error: {}", e);
            }
        }
    }

    @NotNull
    private static AppResponse<Void> response(Throwable ex) {
        if(ex instanceof AppException){
            AppException exception = (AppException) ex;
            if(null != exception.cause){
                LOG.error("ruochuchina response error:", ex);
            }
            return AppResponse.failed(exception.code);
        }
        LOG.error("ruochuchina response error:", ex);
        return AppResponse.failed(CommonCode.Error);
    }
}
