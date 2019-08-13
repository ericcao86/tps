package com.iflytek.tps.foun.web;

import org.springframework.http.converter.StringHttpMessageConverter;

import java.nio.charset.Charset;

public final class StringMessageConverter extends StringHttpMessageConverter {
    public static final StringMessageConverter INSTANCE = new StringMessageConverter();

    private StringMessageConverter(){
        super(Charset.forName("UTF-8"));
        super.setWriteAcceptCharset(false);
    }
}
