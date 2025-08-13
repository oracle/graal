package com.oracle.truffle.espresso.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class MemorySegmentChunkedMemoryImpl implements ChunkedNativeMemory<MemorySegment> {

    private static final VarHandle VH_JAVA_BOOLEAN = ValueLayout.JAVA_BOOLEAN.varHandle();
    private static final VarHandle VH_JAVA_BYTE = ValueLayout.JAVA_BYTE.varHandle();
    private static final VarHandle VH_JAVA_CHAR = ValueLayout.JAVA_CHAR.varHandle();
    private static final VarHandle VH_JAVA_SHORT = ValueLayout.JAVA_SHORT.varHandle();
    private static final VarHandle VH_JAVA_INT = ValueLayout.JAVA_INT.varHandle();
    private static final VarHandle VH_JAVA_FLOAT = ValueLayout.JAVA_FLOAT.varHandle();
    private static final VarHandle VH_JAVA_DOUBLE = ValueLayout.JAVA_DOUBLE.varHandle();
    private static final VarHandle VH_JAVA_LONG = ValueLayout.JAVA_LONG.varHandle();

    protected final Arena arena;

    public Arena getArena() {
        return arena;
    }

    protected MemorySegmentChunkedMemoryImpl(Arena arena) {
        this.arena = arena;
    }

    @Override
    public void setMemory(long address, long bytes, byte value) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        chunk.asSlice(chunkOffset, bytes).fill(value);
    }

    @Override
    public void putBoolean(long address, boolean value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_BOOLEAN.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_BOOLEAN.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_BOOLEAN.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_BOOLEAN.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putByte(long address, byte value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_BYTE.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_BYTE.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_BYTE.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_BYTE.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putChar(long address, char value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_CHAR.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_CHAR.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_CHAR.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_CHAR.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putShort(long address, short value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_SHORT.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_SHORT.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_SHORT.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_SHORT.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putInt(long address, int value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_INT.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_INT.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_INT.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_INT.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putFloat(long address, float value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_FLOAT.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_FLOAT.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_FLOAT.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_FLOAT.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putDouble(long address, double value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_DOUBLE.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_DOUBLE.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_DOUBLE.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_DOUBLE.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putLong(long address, long value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_LONG.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_LONG.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_LONG.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_LONG.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public boolean getBoolean(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (boolean) VH_JAVA_BOOLEAN.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (boolean) VH_JAVA_BOOLEAN.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (boolean) VH_JAVA_BOOLEAN.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (boolean) VH_JAVA_BOOLEAN.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public byte getByte(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (byte) VH_JAVA_BYTE.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (byte) VH_JAVA_BYTE.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (byte) VH_JAVA_BYTE.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (byte) VH_JAVA_BYTE.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public char getChar(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (char) VH_JAVA_CHAR.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (char) VH_JAVA_CHAR.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (char) VH_JAVA_CHAR.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (char) VH_JAVA_CHAR.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public short getShort(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (short) VH_JAVA_SHORT.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (short) VH_JAVA_SHORT.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (short) VH_JAVA_SHORT.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (short) VH_JAVA_SHORT.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public int getInt(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (int) VH_JAVA_INT.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (int) VH_JAVA_INT.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (int) VH_JAVA_INT.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (int) VH_JAVA_INT.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public float getFloat(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (float) VH_JAVA_FLOAT.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (float) VH_JAVA_FLOAT.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (float) VH_JAVA_FLOAT.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (float) VH_JAVA_FLOAT.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public double getDouble(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (double) VH_JAVA_DOUBLE.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (double) VH_JAVA_DOUBLE.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (double) VH_JAVA_DOUBLE.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (double) VH_JAVA_DOUBLE.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public long getLong(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (long) VH_JAVA_LONG.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (long) VH_JAVA_LONG.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (long) VH_JAVA_LONG.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (long) VH_JAVA_LONG.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    protected MemorySegment allocateChunk(long bytes) {
        // At least 8 bytes to ensure aligned accesses work.
        return arena.allocate(bytes, Long.BYTES);
    }

    @Override
    protected long getChunkSize(long address) {
        return getChunk(address).byteSize();
    }

    @Override
    protected void copyBytes(long fromAddress, long toAddress, long byteSize) {
        MemorySegment.copy(getChunk(fromAddress), getChunkOffset(fromAddress), getChunk(toAddress), getChunkOffset(toAddress), byteSize);
    }
}
