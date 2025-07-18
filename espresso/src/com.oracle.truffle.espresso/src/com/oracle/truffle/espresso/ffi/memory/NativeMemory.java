package com.oracle.truffle.espresso.ffi.memory;

public interface NativeMemory {
    long reallocateMemory(long address, long bytes);
    long allocateMemory(long bytes);
    void freeMemory(long bytes);
    void setMemory(long address, long bytes, byte value);

    enum MemoryAccessMode {
        PLAIN,
        OPAQUE,
        RELEASE_ACQUIRE,
        VOLATILE
    }

    void putByte(long address, byte value, MemoryAccessMode accessMode);
    void putShort(long address, short value, MemoryAccessMode accessMode);
    void putInt(long address, int value, MemoryAccessMode accessMode);
    void putLong(long address, long value, MemoryAccessMode accessMode);

    default void putBoolean(long address, boolean value, MemoryAccessMode accessMode) {
        putByte(address, value ? (byte) 1 : (byte) 0, accessMode);
    }

    default void putChar(long address, char value, MemoryAccessMode accessMode) {
        putShort(address, (short) value, accessMode);
    }

    default void putFloat(long address, float value, MemoryAccessMode accessMode) {
        putInt(address, Float.floatToRawIntBits(value), accessMode);
    }

    default void putDouble(long address, double value, MemoryAccessMode accessMode) {
        putLong(address, Double.doubleToRawLongBits(value), accessMode);
    }

    byte getByte(long address, MemoryAccessMode accessMode);
    short getShort(long address, MemoryAccessMode accessMode);
    int getInt(long address, MemoryAccessMode accessMode);
    long getLong(long address, MemoryAccessMode accessMode);


    default boolean getBoolean(long address, MemoryAccessMode accessMode) {
        return getByte(address, accessMode) != 0;
    }

    default char getChar(long address, MemoryAccessMode accessMode) {
        return (char) getShort(address, accessMode);
    }

    default float getFloat(long address, MemoryAccessMode accessMode) {
        return Float.intBitsToFloat(getInt(address, accessMode));
    }

    default double getDouble(long address, MemoryAccessMode accessMode) {
        return Double.longBitsToDouble(getLong(address, accessMode));
    }

    boolean compareAndSetLong(long address, long expected, long newValue);
    boolean compareAndSetInt(long address, int expected, int newValue);

    default long compareAndExchangeLong(long address, long expected, long newValue) {
        long previous;
        do {
            previous = getLong(address, MemoryAccessMode.VOLATILE);
            if (previous != expected) {
                return previous;
            }
        } while (!compareAndSetLong(address, expected, newValue));
        return previous;
    }

    default int compareAndExchangeInt(long address, int expected, int newValue) {
        int previous;
        do {
            previous = getInt(address, MemoryAccessMode.VOLATILE);
            if (previous != expected) {
                return previous;
            }
        } while (!compareAndSetInt(address, expected, newValue));
        return previous;
    }

}
