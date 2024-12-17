package com.oracle.truffle.espresso.ffi.memory;

import java.nio.ByteOrder;
import java.util.Arrays;

import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public class ProtoChunkedMemoryImpl extends ChunkedNativeMemory<byte[]> {

    private static final ByteArraySupport BYTES = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
            ? ByteArraySupport.littleEndian()
            : ByteArraySupport.bigEndian();

    private static void validateAccess(int length, int byteIndex, int accessByteSize) {
        if (byteIndex < 0 || byteIndex > length - accessByteSize) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public void setMemory(long address, long bytes, byte value) {
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(bytes), Byte.BYTES);
        Arrays.fill(chunk, 0, Math.toIntExact(bytes), value);
    }

    @Override
    public void putBoolean(long address, boolean value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        switch (accessMode) {
            case PLAIN -> BYTES.putByte(chunk, chunkOffset, value ? (byte) 1 : (byte) 0);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                    BYTES.putByteVolatile(chunk, chunkOffset, value ? (byte) 1 : (byte) 0);
        }
    }

    @Override
    public void putByte(long address, byte value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putByte(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putByteVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putChar(long address, char value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Character.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putShort(chunk, chunkOffset, (short) value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putShortVolatile(chunk, chunkOffset, (short) value);
        }
    }

    @Override
    public void putShort(long address, short value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Short.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putShort(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putShortVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putInt(long address, int value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Integer.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putInt(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putIntVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putFloat(long address, float value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Float.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putFloat(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putIntVolatile(chunk, chunkOffset, Float.floatToRawIntBits(value));
        }
    }

    @Override
    public void putDouble(long address, double value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Double.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putDouble(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putLongVolatile(chunk, chunkOffset, Double.doubleToRawLongBits(value));
        }
    }

    @Override
    public void putLong(long address, long value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Long.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putLong(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putLongVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public boolean getBoolean(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getByte(chunk, chunkOffset) != 0;
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getByteVolatile(chunk, chunkOffset) != 0;
        };
    }

    @Override
    public byte getByte(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getByte(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getByteVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public char getChar(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Character.BYTES);
        return switch (accessMode) {
            case PLAIN -> (char) BYTES.getShort(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> (char) BYTES.getShortVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public short getShort(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Short.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getShort(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getShortVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public int getInt(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Integer.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getInt(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getIntVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public float getFloat(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Float.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getFloat(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> Float.intBitsToFloat(BYTES.getIntVolatile(chunk, chunkOffset));
        };
    }

    @Override
    public double getDouble(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Double.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getDouble(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> Double.longBitsToDouble(BYTES.getLongVolatile(chunk, chunkOffset));
        };
    }

    @Override
    public boolean compareAndSetLong(long address, long expected, long newValue) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        return BYTES.compareAndExchangeLong(chunk, chunkOffset, expected, newValue) == expected;
    }

    @Override
    public boolean compareAndSetInt(long address, int expected, int newValue) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        return BYTES.compareAndExchangeInt(chunk, chunkOffset, expected, newValue) == expected;
    }

    @Override
    public long getLong(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Long.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getLong(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getLongVolatile(chunk, chunkOffset);
        };
    }

    @Override
    protected byte[] allocateChunk(long bytes) {
        return new byte[Math.toIntExact(bytes)];
    }

    @Override
    protected long getChunkSize(long address) {
        return getChunk(address).length;
    }

    @Override
    protected void copyBytes(long fromAddress, long toAddress, long byteSize) {
        int intByteSize = Math.toIntExact(byteSize);

        byte[] fromChunk = getChunk(fromAddress);
        int fromOffset = Math.toIntExact(getChunkOffset(fromAddress));
        validateAccess(fromChunk.length, fromOffset, intByteSize);

        byte[] toChunk = getChunk(toAddress);
        int toOffset = Math.toIntExact(getChunkOffset(fromAddress));
        validateAccess(toChunk.length, toOffset, intByteSize);

        System.arraycopy(fromChunk, fromOffset, toChunk, toOffset, intByteSize);
    }
}
