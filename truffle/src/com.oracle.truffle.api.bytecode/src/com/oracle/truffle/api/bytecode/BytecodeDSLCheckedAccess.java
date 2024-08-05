package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.memory.ByteArraySupport;

/**
 * Implementation of BytecodeDSLAccess that does not use Unsafe.
 */
final class BytecodeDSLCheckedAccess extends BytecodeDSLAccess {

    BytecodeDSLCheckedAccess() {
    }

    @Override
    public ByteArraySupport getByteArraySupport() {
        return BytecodeAccessor.MEMORY.getNativeChecked();
    }

    @Override
    public FrameExtensions getFrameExtensions() {
        return BytecodeAccessor.RUNTIME.getFrameExtensionsSafe();
    }

    @Override
    public <T> T readObject(T[] arr, int index) {
        return arr[index];
    }

    @Override
    public <T> void writeObject(T[] arr, int index, T value) {
        arr[index] = value;
    }

    @Override
    public <T> T uncheckedCast(Object obj, Class<T> clazz) {
        return clazz.cast(obj);
    }

    // Exposed for testing.

    public static short readShortBigEndian(byte[] arr, int index) {
        return (short) (((arr[index] & 0xFF) << 8) | (arr[index + 1] & 0xFF));
    }

    public static short readShortLittleEndian(byte[] arr, int index) {
        return (short) ((arr[index] & 0xFF) | ((arr[index + 1] & 0xFF) << 8));
    }

    public static int readIntBigEndian(byte[] arr, int index) {
        return ((arr[index] & 0xFF) << 24) | ((arr[index + 1] & 0xFF) << 16) | ((arr[index + 2] & 0xFF) << 8) | (arr[index + 3] & 0xFF);
    }

    public static int readIntLittleEndian(byte[] arr, int index) {
        return (arr[index] & 0xFF) | ((arr[index + 1] & 0xFF) << 8) | ((arr[index + 2] & 0xFF) << 16) | ((arr[index + 3] & 0xFF) << 24);
    }

    public static void writeShortBigEndian(byte[] arr, int index, short value) {
        arr[index] = (byte) (value >> 8);
        arr[index + 1] = (byte) value;
    }

    public static void writeShortLittleEndian(byte[] arr, int index, short value) {
        arr[index] = (byte) value;
        arr[index + 1] = (byte) (value >> 8);
    }

    public static void writeIntBigEndian(byte[] arr, int index, int value) {
        arr[index] = (byte) (value >> 24);
        arr[index + 1] = (byte) (value >> 16);
        arr[index + 2] = (byte) (value >> 8);
        arr[index + 3] = (byte) value;
    }

    public static void writeIntLittleEndian(byte[] arr, int index, int value) {
        arr[index] = (byte) (value);
        arr[index + 1] = (byte) (value >> 8);
        arr[index + 2] = (byte) (value >>> 16);
        arr[index + 3] = (byte) (value >> 24);
    }

}