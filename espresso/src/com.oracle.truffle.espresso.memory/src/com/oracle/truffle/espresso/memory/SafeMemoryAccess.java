package com.oracle.truffle.espresso.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

final class SafeMemoryAccess extends ChunkedNativeMemory<MemorySegment> {

    private static final int OFFSET_BITS = 38; // 256GB max buffer size
    private static final int CHUNK_BITS = Long.SIZE - OFFSET_BITS; // 64M chunks
    private static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;

    private static int getChunkIndex(long address) {
        return (int) (address >>> OFFSET_BITS);
    }

    private final ArrayList<MemorySegment> chunks = new ArrayList<>();
    private final ConcurrentLinkedQueue<Integer> freeList = new ConcurrentLinkedQueue<>();

    public SafeMemoryAccess() {
        chunks.add(MemorySegment.NULL); // NULL sentinel
    }

    @Override
    public long reallocateMemory(long address, long bytes) {
        int oldChunkIndex = getChunkIndex(address);
        long newAddress = allocateMemory(bytes);
        int newChunkIndex = getChunkIndex(newAddress);
        MemorySegment oldChunk = chunks.get(oldChunkIndex);
        MemorySegment newChunk = chunks.get(newChunkIndex);
        MemorySegment.copy(oldChunk, 0, newChunk, 0, Math.min(oldChunk.byteSize(), newChunk.byteSize()));
        freeMemory(address);
        return newAddress;
    }

    @Override
    public long allocateMemory(long bytes) {
        Integer chunkIndex = null;
        while ((chunkIndex = freeList.poll()) == null) {
            synchronized (chunks) {
                chunks.add(null);
                freeList.add(chunks.size() - 1);
            }
        }
        MemorySegment chunk = allocateChunk(bytes);
        chunks.set(chunkIndex, chunk);
        return encodeAddress(chunkIndex, 0);
    }

    private static MemorySegment allocateChunk(long bytes) {
        return Arena.ofAuto().allocate(bytes);
    }

    @Override
    public void freeMemory(long address) {
        assert getChunkOffset(address) == 0 : "invalid address";
        int chunkIndex = getChunkIndex(address);
        chunks.set(chunkIndex, null);
        freeList.add(chunkIndex);
    }

    @Override
    protected MemorySegment getChunk(long address) {
        int chunkIndex = getChunkIndex(address);
        return chunks.get(chunkIndex);
    }

    @Override
    protected long getChunkOffset(long address) {
        return address & OFFSET_MASK;
    }

    private long encodeAddress(int chunkIndex, long chunkOffset) {
        assert Long.compareUnsigned(chunkIndex, chunks.size()) <= 0;
        assert Long.compareUnsigned(chunkOffset, OFFSET_MASK) <= 0;
        return (((long) chunkIndex) << OFFSET_BITS) | chunkOffset;
    }
}
