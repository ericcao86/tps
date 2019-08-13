package com.iflytek.tps.foun.hibernate;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE})
public @interface DynamicSource {
    String ds() default IDynamicDS.DEFAULT;
}
