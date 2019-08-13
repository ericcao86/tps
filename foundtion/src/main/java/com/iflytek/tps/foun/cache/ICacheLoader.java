package com.iflytek.tps.foun.cache;

import java.util.Collection;
import java.util.List;

public interface ICacheLoader<K, T> {
    T load(K key);

    List<T> loadList(Collection<K> keys);
}
