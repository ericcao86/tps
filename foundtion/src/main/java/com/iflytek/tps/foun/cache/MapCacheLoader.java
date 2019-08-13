package com.iflytek.tps.foun.cache;

import com.google.common.collect.Lists;
import com.iflytek.tps.foun.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public abstract class MapCacheLoader<K, T> implements ICacheLoader<K, T> {
    private final ConcurrentMap<K, Callable<T>> loaderTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, T> cacheMap;

    public MapCacheLoader(ConcurrentMap<String, T> cacheMap) {
        this.cacheMap = cacheMap;
    }

    protected abstract T loadFrom(K key);

    protected abstract String keyGenerator(K key);

    private T fromCache(K key){
        String strKey = keyGenerator(key);
        T target = cacheMap.get(strKey);
        if(null == target) {
            target = loadFrom(key);
        }
        if(null != target){
            this.cacheMap.put(strKey, target);
        }
        return target;
    }

    private T loader(K key) {
        T target = cacheMap.get(keyGenerator(key));
        if(null != target) {
            return target;
        }
        // 这段代码保证相同的 Key 方法 loadFrom 只被执行一次，减小大请求量压垮 loadFrom 所执行的慢请求
        Callable<T> callable = loaderTasks.putIfAbsent(key, new Callable<T>() {
            private volatile boolean done = false;
            private volatile T rs;

            @Override
            public synchronized T call() throws Exception {
                if (done) {
                    return rs;
                } else {
                    rs = fromCache(key);
                    done = true;
                    loaderTasks.remove(key);
                    return rs;
                }
            }
        });
        try {
            return callable == null ? loaderTasks.get(key).call() : callable.call();
        } catch (Exception e) {
            throw new RuntimeException("cache loader invoke error.....", e);
        }
    }

    @Override
    public T load(K key) {
        return loader(key);
    }

    @Override
    public List<T> loadList(Collection<K> keys) {
        List<T> values = Lists.newArrayList();
        if(!CollectionUtils.isNullOrEmpty(keys)) {
            for (K key: keys){
                values.add(loader(key));
            }
        }
        return values;
    }
}
