package com.iflytek.tps.foun.hibernate;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentMap;

@Aspect
@Order(0)
public class DataSourceInterceptor {
    private final ConcurrentMap<Method, String> dsMap = Maps.newConcurrentMap();

    @Around("execution(* com.iflytek.*.mapper..*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) {
        Object rs = null;
        try {
            if (null == rs) {
                String originalDS = DataSourceManager.get().getDataSource();
                String ds = dataSource(joinPoint);
                if(!ds.equals(originalDS)) {
                    DataSourceManager.get().setDataSource(ds);
                }
                rs = joinPoint.proceed(joinPoint.getArgs());
                if(!ds.equals(originalDS)){
                    DataSourceManager.get().setDataSource(originalDS);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("dynamic datasource invoke error", e);
        }
        return rs;
    }

    private String dataSource(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        Method method = getMethod(signature);
        String ds = dsMap.get(method);
        if (!StringUtils.isBlank(ds)) {
            return ds;
        }
        //method datasource over then class datasource over then default datasource
        DynamicSource ibs = method.getAnnotation(DynamicSource.class);
        if (null == ibs) {
            ibs = (DynamicSource) signature.getDeclaringType().getAnnotation(DynamicSource.class);
        }
        ds = null != ibs ? ibs.ds() : IDynamicDS.DEFAULT;
        dsMap.put(method, ds);
        return ds;
    }

    private Method getMethod(Signature signature) {
        return ((MethodSignature) signature).getMethod();
    }
}