package com.oracle.truffle.api.bytecode;

import java.lang.reflect.Field;

import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.memory.ByteArraySupport;

import sun.misc.Unsafe;

/**
 * Implementation of BytecodeDSLAccess that uses Unsafe.
 */
final class BytecodeDSLUncheckedAccess extends BytecodeDSLAccess {

    static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    @Override
    public FrameExtensions getFrameExtensions() {
        return BytecodeAccessor.RUNTIME.getFrameExtensionsSafe();
    }

    @Override
    public ByteArraySupport getByteArraySupport() {
        return BytecodeAccessor.MEMORY.getNativeUnsafe();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readObject(T[] arr, int index) {
        assert index >= 0 && index < arr.length;
        return (T) UNSAFE.getObject(arr, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
    }

    @Override
    public <T> void writeObject(T[] arr, int index, T value) {
        assert index >= 0 && index < arr.length;
        UNSAFE.putObject(arr, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T uncheckedCast(Object obj, Class<T> clazz) {
        return (T) obj;
    }

}