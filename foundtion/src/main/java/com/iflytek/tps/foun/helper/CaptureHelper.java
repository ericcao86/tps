package com.iflytek.tps.foun.helper;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iflytek.tps.foun.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created by losyn on 3/31/17.
 */
public final class CaptureHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CaptureHelper.class);

    private static final ExecutorService CAPTURE_ES = Executors.newFixedThreadPool(2,
                                                                                   ThreadFactoryHelper.threadFactoryOf("CAPTURE_ES-%d"));

    private CaptureHelper() {
    }

    /** 异步执行多个操作，默认超时时间 5s */
    public static List<Object> execute(List<Supplier> captureList){
        final List<Object> resultList = Lists.newArrayList();
        if (!CollectionUtils.isNullOrEmpty(captureList)) {
            final List<Future<?>> futureList = Lists.newArrayList();
            try {
                captureList.forEach((cf) -> futureList.add(CAPTURE_ES.submit(() -> cf.get())));
                for (Future<?> future : futureList) {
                    resultList.add(future.get(5L, TimeUnit.SECONDS));
                }
            }catch (Exception e){
                resultList.clear();
                throw new RuntimeException("capture execute error: ", e);
            }finally {
                for (Future<?> future : futureList) {
                    if(null != future){
                        future.cancel(true);
                    }
                }
            }
        }
        return resultList;
    }

    /** 异步执行多个操作，可以对每个操作设置一个超时时间 */
    public static List<Object> execute(LinkedHashMap<Supplier, Long> captureMap){
        final List<Object> resultList = Lists.newArrayList();
        if (!CollectionUtils.isNullOrEmpty(captureMap)) {
            final LinkedHashMap<Future<?>, Long> futureMap = Maps.newLinkedHashMap();
            try {
                captureMap.forEach((s, t) -> futureMap.put(CAPTURE_ES.submit(() -> s.get()), t));
                for (Map.Entry<Future<?>, Long> entry: futureMap.entrySet()) {
                    resultList.add(entry.getKey().get(entry.getValue(), TimeUnit.SECONDS));
                }
            }catch (Exception e){
                resultList.clear();
                throw new RuntimeException("capture execute error: ", e);
            }finally {
                for (Map.Entry<Future<?>, Long> entry: futureMap.entrySet()) {
                    if(null != entry.getKey()){
                        entry.getKey().cancel(true);
                    }
                }
            }
        }
        return resultList;
    }

    public static <T> void async(final Supplier<T> supplier, final Consumer<ResultSet<T>> consumer){
        CAPTURE_ES.submit(() -> {
            Throwable cause = null;
            T t = null;
            try {
                t = supplier.get();
            }catch (Exception e){
                cause = e;
            }
            doConsumer(consumer, cause, t);
        });
    }

    public static <T> void async(final Runner runner, final Consumer<ResultSet<T>> consumer){
        CAPTURE_ES.submit(() -> {
            Throwable cause = null;
            try {
                runner.run();
            }catch (Exception e){
                cause = e;
            }
            doConsumer(consumer, cause, null);
        });

    }

    private static <T> void doConsumer(Consumer<ResultSet<T>> consumer, Throwable cause, T t) {
        if(null == consumer) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("async process supplier ignore consumer");
            }
            if (null != cause){
                LOG.error("async process supplier error: ", cause);
            }
        }else {
            try {
                consumer.accept(ResultSet.create(null == cause, t, cause));
            }catch (Exception e){
                LOG.error("async process supplier do consumer error: ", e);
            }
        }
    }

    public interface Runner {
        void run();
    }

    public static class ResultSet<T> {
        public boolean result;

        public T data;

        public Throwable cause;

        private ResultSet(boolean result, T data, Throwable cause) {
            this.result = result;
            this.data = data;
            this.cause = cause;
        }

        public static <R> ResultSet<R> create(boolean result, R data, Throwable cause){
            return new ResultSet<>(result, data, cause);
        }
    }
}
