package com.iflytek.tps.foun.web;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import com.alibaba.fastjson.util.IOUtils;
import org.springframework.http.MediaType;

import java.util.Arrays;

public final class FastJsonMessageConverter extends FastJsonHttpMessageConverter {
    public static final FastJsonMessageConverter INSTANCE = new FastJsonMessageConverter();

    private FastJsonMessageConverter() {
        FastJsonConfig config = new FastJsonConfig();
        config.setCharset(IOUtils.UTF8);
        config.setSerializerFeatures(SerializerFeature.SkipTransientField,
                SerializerFeature.QuoteFieldNames,
                SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteNullListAsEmpty,
                SerializerFeature.WriteNullNumberAsZero,
                SerializerFeature.WriteNullStringAsEmpty,
                SerializerFeature.WriteNullBooleanAsFalse,
                SerializerFeature.WriteDateUseDateFormat);

        super.setFastJsonConfig(config);

        super.setDefaultCharset(IOUtils.UTF8);

        super.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON,
                MediaType.TEXT_HTML,
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_OCTET_STREAM));

    }
}
