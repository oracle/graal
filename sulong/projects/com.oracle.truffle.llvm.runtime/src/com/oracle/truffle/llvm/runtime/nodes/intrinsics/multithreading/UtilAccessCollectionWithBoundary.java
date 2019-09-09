package com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class UtilAccessCollectionWithBoundary {
    @CompilerDirectives.TruffleBoundary
    public static <T> void add(List<T> list, T object) {
        list.add(object);
    }

    @CompilerDirectives.TruffleBoundary
    public static <K, V> void put(ConcurrentMap<K, V> c, K key, V value) {
        c.put(key, value);
    }

    @CompilerDirectives.TruffleBoundary
    public static <K, V> V get(ConcurrentMap<K, V> c, K key) {
        return c.get(key);
    }

    @CompilerDirectives.TruffleBoundary
    public static <K, V> void remove(ConcurrentMap<K, V> c, K key) {
        c.remove(key);
    }
}
