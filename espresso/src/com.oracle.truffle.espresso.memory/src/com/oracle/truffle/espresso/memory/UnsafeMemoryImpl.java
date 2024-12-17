package com.oracle.truffle.espresso.memory;

import com.oracle.truffle.espresso.vm.UnsafeAccess;
import sun.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class UnsafeMemoryImpl extends MemorySegmentChunkedMemoryImpl {

    private static final MemorySegment ALL = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);
    private static final Unsafe UNSAFE = UnsafeAccess.get();

    protected UnsafeMemoryImpl(Arena arena) {
        super(arena);
    }

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
    protected MemorySegment getChunk(long address) {
        return ALL;
    }

    @Override
    protected long getChunkOffset(long address) {
        return address;
    }

    @Override
    protected MemorySegment allocateChunk(long bytes) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void copyBytes(long fromAddress, long toAddress, long byteSize) {
        throw new UnsupportedOperationException();
    }
}
