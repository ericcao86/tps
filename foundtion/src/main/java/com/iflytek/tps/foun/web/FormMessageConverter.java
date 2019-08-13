package com.iflytek.tps.foun.web;

import com.google.common.collect.Lists;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;

import java.util.List;

public final class FormMessageConverter extends FormHttpMessageConverter {

    public static final FormMessageConverter INSTANCE = new FormMessageConverter();

    private FormMessageConverter(){
        setCharset(DEFAULT_CHARSET);
        List<HttpMessageConverter<?>> partConverters = Lists.newArrayList();
        partConverters.add(new ByteArrayHttpMessageConverter());
        partConverters.add(StringMessageConverter.INSTANCE);
        partConverters.add(new SourceHttpMessageConverter());
        partConverters.add(FastJsonMessageConverter.INSTANCE);
        super.setPartConverters(partConverters);
        setSupportedMediaTypes(Lists.newArrayList(MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA));
    }
}