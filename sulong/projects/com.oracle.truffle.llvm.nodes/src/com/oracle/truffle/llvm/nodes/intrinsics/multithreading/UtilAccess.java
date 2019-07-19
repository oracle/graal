package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UtilAccess {
    @CompilerDirectives.TruffleBoundary
    public static void putLLVMPointerObj(ConcurrentMap<LLVMPointer, Object> c, LLVMPointer key, Object value) {
        c.put(key, value);
    }

    @CompilerDirectives.TruffleBoundary
    public static void putLongObj(ConcurrentMap<Long, Object> c, long key, Object value) {
        c.put(key, value);
    }

    @CompilerDirectives.TruffleBoundary
    public static void putLongThread(ConcurrentMap<Long, Thread> c, long key, Thread value) {
        c.put(key, value);
    }

    @CompilerDirectives.TruffleBoundary
    static Object getLLVMPointerObj(ConcurrentMap<LLVMPointer, Object> c, LLVMPointer key) {
        return c.get(key);
    }

    @CompilerDirectives.TruffleBoundary
    static Object getLongObj(ConcurrentMap<Long, Object> c, long key) {
        return c.get(key);
    }

    @CompilerDirectives.TruffleBoundary
    static Thread getLongThread(ConcurrentMap<Long, Thread> c, long key) {
        return c.get(key);
    }

    @CompilerDirectives.TruffleBoundary
    public static void removeLLVMPointerObj(ConcurrentMap<LLVMPointer, Object> c, LLVMPointer key) {
        c.remove(key);
    }

    @CompilerDirectives.TruffleBoundary
    public static void removeLongObj(ConcurrentMap<Long, Object> c, long key) {
        c.remove(key);
    }

    @CompilerDirectives.TruffleBoundary
    public static void removeLongThread(ConcurrentMap<Long, Thread> c, long key) {
        c.remove(key);
    }
}
