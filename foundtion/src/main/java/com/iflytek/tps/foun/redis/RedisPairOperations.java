package com.iflytek.tps.foun.redis;

import com.alibaba.fastjson.util.IOUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iflytek.tps.foun.util.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RedisPairOperations {
    private static final Logger LOG = LoggerFactory.getLogger(RedisPairOperations.class);
    private static final ConcurrentMap<Class<?>, ConcurrentMap<String, Field>> cfMap = Maps.newConcurrentMap();
    private static final String SEQUENCE = "sequence";
    private static final String COLON_SPLIT = ":";

    private final StringRedisTemplate template;

    public RedisPairOperations(StringRedisTemplate template) {
        this.template = template;
    }

    /** 使用 Redis 生成顺序 ID */
    public Long sequence(String key) {
        String sequenceKey = Joiner.on(COLON_SPLIT).join(SEQUENCE, key);
        return template.execute(con -> con.incrBy(sequenceKey.getBytes(), 1L), true);
    }

    /** 使用 Redis 计数 */
    public Long incr(String key) {
        return template.execute(con -> con.incr(key.getBytes()), true);
    }

    /** 获取 Redis Key Value 存储的对象 */
    public <T> T get(String key, Class<T> clazz) {
        byte[] bytes = template.execute(con -> con.get(key.getBytes()), true);
        return ArrayUtils.isEmpty(bytes) ? null : JsonUtils.parseObject(bytes, clazz);
    }

    /** 获取 PHPString  存储的对象 */
    public String get(String key) {
        byte[] bytes = template.execute(con -> con.get(key.getBytes()), true);
        return ArrayUtils.isEmpty(bytes) ? null : new String(bytes);
    }

    /** 批量获取 Redis Key Value 存储的对象 */
    public <T> List<T> mGet(Collection<String> keys, Class<T> clazz) {
        if (CollectionUtils.isNullOrEmpty(keys)) {
            return Lists.newArrayList();
        }
        List<byte[]> rawValues = template.execute(con -> con.mGet(rawKeys(keys)), true);
        return convertOfT(clazz, rawValues);
    }

    /** 获取 Redis HashMap 存储对应的 field 的对象 */
    public <T> T hGet(String key, String field, Class<T> clazz) {
        byte[] bytes = template.execute(con -> con.hGet(key.getBytes(), field.getBytes()), true);
        return ArrayUtils.isEmpty(bytes) ? null : JsonUtils.parseObject(bytes, clazz);
    }

    /** 获取 Redis HashMap 存储的 所有 Value 值 */
    public List<byte[]> hVals(String key) {
        List<byte[]> byteList = template.execute(con -> con.hVals(key.getBytes()), true);
        return CollectionUtils.isNullOrEmpty(byteList) ? Lists.newArrayList() : byteList;
    }

    /** 获取 Redis HashMap 存储的 所有 Key 值 */
    public List<String> hKeys(String key) {
        Set<byte[]> byteList = template.execute(con -> con.hKeys(key.getBytes()), true);
        return byteList.stream().map(k -> new String(k, IOUtils.UTF8)).collect(Collectors.toList());
    }

    /** 判断 Redis HashMap 存储的是否有对应的 field */
    public boolean hExists(String key, String field) {
        return template.execute(con -> con.hExists(key.getBytes(), field.getBytes()), true);
    }

    /** 获取 Redis HashMap field 数量 */
    public Long hLen(String key) {
        return template.execute(con -> con.hLen(key.getBytes()), true);
    }

    /** 判断 Redis HashMap 存储对应的对象值 */
    public <T> T hGetAsBean(String key, Class<T> clazz) {
        Map<byte[], byte[]> byteMap = template.execute(con -> con.hGetAll(key.getBytes()), true);
        Map<String, Object> json = Maps.newHashMap();
        for (Map.Entry<byte[], byte[]> entry : byteMap.entrySet()) {
            String fieldName = new String(entry.getKey(), IOUtils.UTF8);
            Field field = BeanUtils.findField(clazz, fieldName);
            if (null != field) {
                if (Collection.class.isAssignableFrom(field.getType())) {
                    Class<?> fClass = GenericUtils.getSuperClassGenericType(field.getGenericType());
                    json.put(fieldName, JsonUtils.parseArray(new String(entry.getValue(), IOUtils.UTF8), fClass));
                } else {
                    json.put(fieldName, JsonUtils.parseObject(entry.getValue(), field.getType()));
                }
            }
        }
        return BeanUtils.map2Bean(json, clazz);
    }

    /** 判断 Redis HashMap 存储的 field value 值 */
    public Map<byte[], byte[]> hGetAll(String key) {
        return template.execute(con -> con.hGetAll(key.getBytes()), true);
    }

    /** Redis Key Value 存储的原子操作 */
    public <T> T getSet(String key, T t) {
        byte[] bytes = template.execute(con -> con.getSet(key.getBytes(), JsonUtils.toJSONBytes(t)), true);
        return ArrayUtils.isEmpty(bytes) ? null : JsonUtils.parseObject(bytes, (Class<T>) t.getClass());
    }

    /** 获取 Redis Key Value 存储的 List 对象 */
    public <T> List<T> getAsList(String key, Class<T> clazz) {
        BoundValueOperations<String, String> bo = boundOptions(key);
        String val = bo.get();
        return StringUtils.isBlank(val) ? Lists.newArrayList() : JsonUtils.parseArray(val, clazz);
    }

    /** Redis Key Value 存储的原子操作 */
    public <T> List<T> getSetAsList(String key, List<T> tList) {
        BoundValueOperations<String, String> bo = boundOptions(key);
        String val = bo.getAndSet(JsonUtils.toJSONString(tList));
        return StringUtils.isBlank(val)
                ? Lists.newArrayList()
                : JsonUtils.parseArray(val, GenericUtils.getSuperClassGenericType(tList.getClass()));
    }

    /** Redis Key Value 存储 带过期时间 */
    public <V> void put(String key, V value, long expire) {
        BoundValueOperations<String, String> bo = boundOptions(key);
        bo.set(JsonUtils.toJSONString(value));
        bo.expire(expire, TimeUnit.SECONDS);
    }

    /** Redis Key Value 存储 */
    public <V> void put(String key, V v) {
        boundOptions(key).set(JsonUtils.toJSONString(v));
    }

    /** Redis HashMap 存储 field value */
    public <V> void hSet(String key, String field, V v) {
        template.execute(con -> con.hSet(key.getBytes(), field.getBytes(), JsonUtils.toJSONBytes(v)), true);
    }

    /** Redis HashMap 存储 field value, 仅当 field 不存在时 */
    public <V> boolean hSetNX(String key, String field, V v) {
        return template.execute(con -> con.hSetNX(key.getBytes(), field.getBytes(), JsonUtils.toJSONBytes(v)), true);
    }

    /** Redis HashMap 存储 field value 带过期时间 */
    public <V> void hSet(String key, String field, V v, long expire) {
        hSet(key, field, v);
        template.execute(con -> con.expire(key.getBytes(), expire), true);
    }

    /** Redis HashMap 存储 Value 对象 */
    public <V> void hSet(String key, V v) {
        Map<String, Object> vMap = BeanUtils.bean2Map(v);
        for (Map.Entry<String, Object> entry : vMap.entrySet()) {
            hSet(key, entry.getKey(), entry.getValue());
        }
    }

    /** Redis HashMap field 整数值加上增量 */
    public Long hIncrBy(String key, String field, long delta) {
        return template.execute(con -> con.hIncrBy(key.getBytes(), field.getBytes(), delta), true);
    }

    /** Redis HashMap field 整数值加上增量 */
    public Double hIncrBy(String key, String field, double delta) {
        return template.execute(con -> con.hIncrBy(key.getBytes(), field.getBytes(), delta), true);
    }

    /** Redis List 存储的所有对象值 */
    public <T> List<T> zRange(String key, Class<T> clazz) {
        Set<byte[]> vList = template.execute(con -> con.zRange(key.getBytes(), 0, -1), true);
        return convertOfT(clazz, vList);
    }

    /** Redis zSet 默认时间维度添加 */
    public <V> void zAdd(String key, V v) {
        template.execute(con -> con.zAdd(key.getBytes(), DateUtils.second(), JsonUtils.toJSONBytes(v)), true);
    }

    /** Redis zSet 自定义维度添加 */
    public <V> void zAdd(String key, double score, V v) {
        template.execute(con -> con.zAdd(key.getBytes(), score, JsonUtils.toJSONBytes(v)), true);
    }

    /** Redis zSet 自定义维度添加 */
    public <V> Double zScore(String key, V v) {
        return template.execute(con -> con.zScore(key.getBytes(), JsonUtils.toJSONBytes(v)), true);
    }

    /** Redis zSet 自定义维度添加 */
    public <V> Double zIncrBy(String key, double delta, V v) {
        return template.execute(con -> con.zIncrBy(key.getBytes(), delta, JsonUtils.toJSONBytes(v)), true);
    }

    /** Redis zSet 合并 */
    public <V> void zUnion(String key, Collection<String> unionKeySet) {
        template.execute(con -> con.zUnionStore(key.getBytes(), rawKeys(unionKeySet)), true);
    }


    /** Redis zSet 中判断成员是否存在 */
    public <V> boolean zExists(String key, V val) {
        return template.execute(con -> con.zRank(key.getBytes(), JsonUtils.toJSONBytes(val)), true) != null;
    }

    /** Redis zSet 移除某些成员 */
    public <V> void zRem(String key, V... vals) {
        zRem(key, Arrays.asList(vals));
    }

    /** Redis zSet 移除某些成员 */
    public <V> void zRem(String key, Collection<V> vals) {
        template.execute(con -> con.zRem(key.getBytes(), rawValues(vals)), true);
    }

    /** Redis Set 所有成员值 */
    public <T> List<T> sMembers(String key, Class<T> clazz) {
        Set<byte[]> set = template.execute(con -> con.sMembers(key.getBytes()), true);
        return convertOfT(clazz, set);
    }

    /** Redis Set 添加成员 */
    public <V> void sAdd(String key, V member) {
        template.execute(con -> con.sAdd(key.getBytes(), JsonUtils.toJSONBytes(member)), true);
    }

    /** Redis Set 中是否存在成员 */
    public <V> boolean sisMember(String key, V member) {
        return template.execute(con -> con.sIsMember(key.getBytes(), JsonUtils.toJSONBytes(member)), true);
    }

    /** Redis Set 中移除成员 */
    public <V> void sRem(String key, V member) {
        template.execute(con -> con.sRem(key.getBytes(), JsonUtils.toJSONBytes(member)), true);
    }

    /** Redis 对某 Key 添加过期时间 */
    public void expire(String key, long timeout) {
        template.expire(key, timeout, TimeUnit.SECONDS);
    }

    /** Redis 判断某 Key 是否存在 */
    public boolean exists(String key) {
        return template.hasKey(key);
    }

    /** Redis 根据表达式获取 Key Set */
    public Set<String> keySet(String pattern) {
        return template.keys(pattern);
    }

    /** Redis 删除 Key */
    public void clearKey(String... keys) {
        clearKey(Arrays.asList(keys));
    }

    /** Redis 删除 Key */
    public void clearKey(Collection<String> keys) {
        if (!CollectionUtils.isNullOrEmpty(keys)) {
            template.execute(con -> con.del(rawKeys(keys)), true);
        }
    }

    /** Redis 删除 HashMap field */
    public void clearHKey(String key, String... fields) {
        clearHKey(key, Arrays.asList(fields));
    }

    /** Redis 删除 HashMap field */
    public void clearHKey(String key, Collection<String> keys) {
        template.execute(con -> con.hDel(key.getBytes(), rawKeys(keys)), true);
    }

    /** zRevRange 返回存储在键的排序元素集合在指定的范围 */
    public <T> List<T> zRevRange(Class<T> T , String key, long start, long end) {
        Set<byte[]> set =template.execute(connection -> connection.zRevRange(key.getBytes(),start,end),true);
        return convertOfT(T , set);
    }



    private BoundValueOperations<String, String> boundOptions(String key) {
        return template.boundValueOps(key);
    }

    private byte[][] rawKeys(Collection<String> keys) {
        final byte[][] rawKeys = new byte[keys.size()][];
        int counter = 0;
        for (String key : keys) {
            rawKeys[counter++] = key.getBytes();
        }
        return rawKeys;
    }

    private byte[][] rawValues(Collection vals) {
        final byte[][] rawVals = new byte[vals.size()][];
        int counter = 0;
        for (Object val : vals) {
            rawVals[counter++] = JsonUtils.toJSONBytes(val);
        }
        return rawVals;
    }

    private <T> List<T> convertOfT(Class<T> clazz, Collection<byte[]> rawValues) {
        return CollectionUtils.isNullOrEmpty(rawValues)
                ? Lists.newArrayList()
                : rawValues.stream()
                .filter(Objects::nonNull)
                .map(vb -> JsonUtils.parseObject(vb, clazz))
                .collect(Collectors.toList());
    }
}
