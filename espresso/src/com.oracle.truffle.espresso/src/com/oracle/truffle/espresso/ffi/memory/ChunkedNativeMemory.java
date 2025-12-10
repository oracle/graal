/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.ffi.memory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.espresso.jni.HandleStorage.HandlesStack;

/**
 * Abstract class for dividing memory into chunks, where each chunk is an object of type T. Each
 * address encodes a chunk index (upper bits) and an offset within that chunk (lower bits). The
 * chunk index selects an object from the {@code chunks} list; the chunk offset selects the location
 * inside that chunk.
 *
 * @param <T> the chunk type
 */
public abstract class ChunkedNativeMemory<T> implements NativeMemory {

    protected static final int OFFSET_BITS = 38; // 256GB max buffer size
    protected static final int CHUNK_BITS = Long.SIZE - OFFSET_BITS; // 64M chunks
    protected static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;
    protected static final long ALIGNMENT = Integer.BYTES;

    /*
     * invariant: for all allocated addresses, 0 < chunkIndex(address) < chunks.size()
     */
    protected final List<T> chunks = new ArrayList<>();
    /*
     * Should only contain ints which are valid indices into chunks.
     */
    protected final HandlesStack freeChunkIndices = new HandlesStack();

    protected abstract T allocateChunk(long bytes) throws MemoryAllocationException;

    protected ChunkedNativeMemory() {
        // Sentinel block, cannot be freed.
        synchronized (this) {
            chunks.add(null);
        }
    }

    protected int getAndCheckChunkIndex(long address) throws IllegalMemoryAccessException {
        int index = (int) (address >>> OFFSET_BITS);
        if (!(index > 0 && index < chunks.size())) {
            throw new IllegalMemoryAccessException("invalid address (chunkIndex out of bounds)!");
        }
        return index;
    }

    protected T getChunk(long address) throws IllegalMemoryAccessException {
        int chunkIndex = getAndCheckChunkIndex(address);
        return chunks.get(chunkIndex);
    }

    protected long getChunkOffset(long address) {
        return address & OFFSET_MASK;
    }

    protected void checkOffset(long address) throws IllegalMemoryAccessException {
        if (getChunkOffset(address) != 0) {
            throw new IllegalMemoryAccessException("address offset must be 0");
        }
    }

    protected long encodeAddress(int chunkIndex, long chunkOffset) {
        assert (Long.compareUnsigned(chunkIndex, chunks.size()) < 0);
        assert (Long.compareUnsigned(chunkOffset, OFFSET_MASK) <= 0);
        return (((long) chunkIndex) << OFFSET_BITS) | chunkOffset;
    }

    @Override
    public synchronized long allocateMemory(long bytes) throws MemoryAllocationException {
        if (bytes < 0) {
            // We will probably not reach here but just to be safe.
            throw new MemoryAllocationException("Can not allocate negative amount of bytes");
        }
        /*
         * We always align bytes up according to ALIGNMENT. We need to determine the maximum
         * allocation size such that alignment does not cause this operation to overflow.
         */
        if (bytes > (Long.MAX_VALUE & -ALIGNMENT)) {
            throw new MemoryAllocationException("Can not allocate more than: " + (Long.MAX_VALUE & -ALIGNMENT));
        }
        int chunkIndex = freeChunkIndices.pop();
        if (chunkIndex == -1) {
            if (chunks.size() == 1 << CHUNK_BITS) {
                throw new MemoryAllocationException("Out of memory (no chunk available)!");
            }
            chunks.add(null);
            chunkIndex = chunks.size() - 1;
        }
        assert chunkIndex > 0 && chunkIndex < chunks.size();
        /*
         * Ensure memory is aligned so that guest code using sub-word CAS or similar operations,
         * which may temporarily access memory just beyond the explicitly requested region (but
         * still inside the allocated, implicitly aligned chunk), will not encounter out-of-bounds
         * errors.
         */
        long size = alignUp(bytes);
        T chunk = allocateChunk(size);
        chunks.set(chunkIndex, chunk);
        return encodeAddress(chunkIndex, 0);
    }

    private static long alignUp(long bytes) {
        return (bytes + (ALIGNMENT - 1)) & -(ALIGNMENT);
    }

    @Override
    public synchronized void freeMemory(long address) throws IllegalMemoryAccessException {
        if (address == 0) {
            return;
        }
        checkOffset(address);
        int chunkIndex = getAndCheckChunkIndex(address);
        chunks.set(chunkIndex, null);
        freeChunkIndices.push(chunkIndex);
    }

    public long reallocateMemory(long address, long bytes) throws MemoryAllocationException, IllegalMemoryAccessException {
        if (bytes < 0) {
            // We will probably not reach here but just to be safe.
            throw new MemoryAllocationException("Can not allocate negative amount of bytes");
        }
        if (address == 0) {
            return allocateMemory(bytes);
        }
        checkOffset(address);
        int oldChunkIndex = getAndCheckChunkIndex(address);
        if (getChunkSize(chunks.get(oldChunkIndex)) == bytes) {
            return address; // no change
        }

        long newAddress = allocateMemory(bytes);
        long newSize = getChunkSize(getChunk(newAddress));
        copyMemory(address, newAddress, Math.min(getChunkSize(chunks.get(oldChunkIndex)), newSize));
        freeMemory(address);
        return newAddress;
    }

    @Override
    public void putByte(long address, byte value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Byte.BYTES);
        putByteImpl(chunk, chunkOffset, value, accessMode);
    }

    @Override
    public void putShort(long address, short value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Short.BYTES);
        putShortImpl(chunk, chunkOffset, value, accessMode);
    }

    @Override
    public void putInt(long address, int value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Integer.BYTES);
        putIntImpl(chunk, chunkOffset, value, accessMode);
    }

    @Override
    public void putLong(long address, long value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Long.BYTES);
        putLongImpl(chunk, chunkOffset, value, accessMode);
    }

    @Override
    public byte getByte(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Byte.BYTES);
        return getByteImpl(chunk, chunkOffset, accessMode);
    }

    @Override
    public short getShort(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Short.BYTES);
        return getShortImpl(chunk, chunkOffset, accessMode);
    }

    @Override
    public int getInt(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Integer.BYTES);
        return getIntImpl(chunk, chunkOffset, accessMode);
    }

    @Override
    public long getLong(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Long.BYTES);
        return getLongImpl(chunk, chunkOffset, accessMode);
    }

    @Override
    public boolean compareAndSetInt(long address, int expected, int newValue) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Integer.BYTES);
        return compareAndSetIntImpl(chunk, chunkOffset, expected, newValue);
    }

    @Override
    public boolean compareAndSetLong(long address, long expected, long newValue) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Long.BYTES);
        return compareAndSetLongImpl(chunk, chunkOffset, expected, newValue);
    }

    @Override
    public void setMemory(long address, long bytes, byte value) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, 0);
        setMemoryImpl(chunk, chunkOffset, bytes, value);
    }

    @Override
    public void copyMemory(long fromAddress, long toAddress, long byteSize) throws IllegalMemoryAccessException {
        long fromOffset = getChunkOffset(fromAddress);
        T fromChunk = getChunk(fromAddress);
        validateAccess(fromChunk, fromOffset + byteSize, 0);

        long toOffset = getChunkOffset(toAddress);
        T toChunk = getChunk(toAddress);
        validateAccess(toChunk, toOffset + byteSize, 0);

        copyMemoryImpl(fromChunk, fromOffset, toChunk, toOffset, byteSize);
    }

    @Override
    public void writeMemory(long address, byte[] buf) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, buf.length);
        writeMemoryImpl(chunk, chunkOffset, buf);
    }

    @Override
    public void readMemory(long address, byte[] buf) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, buf.length);
        readMemoryImpl(chunk, chunkOffset, buf);
    }

    @Override
    public ByteBuffer wrapNativeMemory(long address, int bytes) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        T chunk = getChunk(address);
        validateAccess(chunk, chunkOffset + bytes, 0);
        return wrapChunk(chunk, chunkOffset, bytes);
    }

    /**
     * Validates the memory access and as a side effect enforces int lengths for byte-array chunks
     * by using {@link ChunkedNativeMemory#getChunkSize(Object)}.
     *
     * @param chunk the chunk that is accessed.
     * @param byteIndex the biggest index accessed in the chunk.
     * @param accessByteSize the byte size of the access.
     */
    protected void validateAccess(T chunk, long byteIndex, int accessByteSize) throws IllegalMemoryAccessException {
        validateAccess(getChunkSize(chunk), byteIndex, accessByteSize);
    }

    protected void validateAccess(long length, long byteIndex, int accessByteSize) throws IllegalMemoryAccessException {
        if (byteIndex < 0 || byteIndex > length - accessByteSize) {
            throw new IllegalMemoryAccessException("Memory access is outside the boundaries of the allocated memory region");
        }
    }

    protected abstract void putByteImpl(T chunk, long chunkOffset, byte value, MemoryAccessMode accessMode);

    protected abstract void putShortImpl(T chunk, long chunkOffset, short value, MemoryAccessMode accessMode);

    protected abstract void putIntImpl(T chunk, long chunkOffset, int value, MemoryAccessMode accessMode);

    protected abstract void putLongImpl(T chunk, long chunkOffset, long value, MemoryAccessMode accessMode);

    protected abstract byte getByteImpl(T chunk, long chunkOffset, MemoryAccessMode accessMode);

    protected abstract short getShortImpl(T chunk, long chunkOffset, MemoryAccessMode accessMode);

    protected abstract int getIntImpl(T chunk, long chunkOffset, MemoryAccessMode accessMode);

    protected abstract long getLongImpl(T chunk, long chunkOffset, MemoryAccessMode accessMode);

    protected abstract boolean compareAndSetLongImpl(T chunk, long address, long expected, long newValue);

    protected abstract boolean compareAndSetIntImpl(T chunk, long address, int expected, int newValue);

    protected abstract void setMemoryImpl(T chunk, long chunkOffset, long bytes, byte value);

    protected abstract void copyMemoryImpl(T fromChunk, long fromOffset, T toChunk, long toOffset, long byteSize);

    protected abstract void writeMemoryImpl(T chunk, long chunkOffset, byte[] buf);

    protected abstract void readMemoryImpl(T chunk, long chunkOffset, byte[] buf);

    protected abstract ByteBuffer wrapChunk(T chunk, long chunkOffset, int size);

    /**
     * Returns how many bytes the chunk can hold.
     */
    protected abstract long getChunkSize(T chunk);

}
