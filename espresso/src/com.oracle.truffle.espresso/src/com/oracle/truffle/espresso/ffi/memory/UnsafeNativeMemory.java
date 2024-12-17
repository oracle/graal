package com.oracle.truffle.espresso.ffi.memory;

import com.oracle.truffle.espresso.vm.UnsafeAccess;
import sun.misc.Unsafe;

public class UnsafeNativeMemory implements NativeMemory {

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    @Override
    public long reallocateMemory(long address, long bytes) {
        return UNSAFE.reallocateMemory(address, bytes);
    }

    @Override
    public long allocateMemory(long bytes) {
        return UNSAFE.allocateMemory(bytes);
    }

    @Override
    public void freeMemory(long address) {
        UNSAFE.freeMemory(address);
    }

    @Override
    public void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    @Override
    public void putBoolean(long address, boolean value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putBoolean(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putBooleanVolatile(null, address, value);
        }
    }

    @Override
    public void putByte(long address, byte value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putByte(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putByteVolatile(null, address, value);
        }
    }

    @Override
    public void putChar(long address, char value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putChar(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putCharVolatile(null, address, value);
        }
    }

    @Override
    public void putShort(long address, short value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putShort(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putShortVolatile(null, address, value);
        }
    }

    @Override
    public void putInt(long address, int value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putInt(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putIntVolatile(null, address, value);
        }
    }

    @Override
    public void putFloat(long address, float value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putFloat(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putFloatVolatile(null, address, value);
        }
    }

    @Override
    public void putDouble(long address, double value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putDouble(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putDoubleVolatile(null, address, value);
        }
    }

    @Override
    public void putLong(long address, long value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putLong(null, address, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.putLongVolatile(null, address, value);
        }
    }

    @Override
    public boolean getBoolean(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getBoolean(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getBooleanVolatile(null, address);
        };
    }

    @Override
    public byte getByte(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getByte(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getByteVolatile(null, address);
        };
    }

    @Override
    public char getChar(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getChar(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getCharVolatile(null, address);
        };
    }

    @Override
    public short getShort(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getShort(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getShortVolatile(null, address);
        };
    }

    @Override
    public int getInt(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getInt(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getIntVolatile(null, address);
        };
    }

    @Override
    public float getFloat(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getFloat(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getFloatVolatile(null, address);
        };
    }

    @Override
    public double getDouble(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getDouble(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getDoubleVolatile(null, address);
        };
    }

    @Override
    public long getLong(long address, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getLong(null, address);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> UNSAFE.getLongVolatile(null, address);
        };
    }

    @Override
    public boolean compareAndSetInt(long address, int expected, int newValue) {
        return UNSAFE.compareAndSwapInt(null, address, expected, newValue);
    }

    @Override
    public boolean compareAndSetLong(long address, long expected, long newValue) {
        return UNSAFE.compareAndSwapLong(null, address, expected, newValue);
    }
}
