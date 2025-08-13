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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

abstract class ChunkedNativeMemory<T> implements NativeMemory {

    protected static final int OFFSET_BITS = 38; // 256GB max buffer size
    protected static final int CHUNK_BITS = Long.SIZE - OFFSET_BITS; // 64M chunks
    protected static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;

    protected final List<T> chunks = new ArrayList<>();
    protected final Queue<Integer> freeList = new ArrayDeque<>();

    protected abstract T allocateChunk(long bytes);

    protected ChunkedNativeMemory() {
        allocateMemory(0); // Sentinel block, cannot be freed.
    }

    protected static int getChunkIndex(long address) {
        return (int) (address >>> OFFSET_BITS);
    }

    protected T getChunk(long address) {
        int chunkIndex = getChunkIndex(address);
        return chunks.get(chunkIndex);
    }

    protected long getChunkOffset(long address) {
        return address & OFFSET_MASK;
    }

    protected long encodeAddress(int chunkIndex, long chunkOffset) {
        if (!(Long.compareUnsigned(chunkIndex, chunks.size()) < 0)) {
            throw new IllegalStateException("invalid chunk index");
        }
        if (!(Long.compareUnsigned(chunkOffset, OFFSET_MASK) <= 0)) {
            throw new IllegalStateException("invalid chunk offset");
        }
        return (((long) chunkIndex) << OFFSET_BITS) | chunkOffset;
    }

    @Override
    public synchronized long allocateMemory(long bytes) {
        Integer chunkIndex = freeList.poll();
        if (chunkIndex == null) {
            if (chunks.size() == 1 << CHUNK_BITS) {
                throw new OutOfMemoryError("cannot allocate chunk");
            }
            chunks.add(null);
            chunkIndex = chunks.size() - 1;
        }
        T chunk = allocateChunk(bytes);
        chunks.set(chunkIndex, chunk);
        return encodeAddress(chunkIndex, 0);
    }

    @Override
    public synchronized void freeMemory(long address) {
        if (getChunkOffset(address) != 0) {
            throw new IllegalStateException("invalid address");
        }
        int chunkIndex = getChunkIndex(address);
        chunks.set(chunkIndex, null);
        freeList.add(chunkIndex);
    }

    public long reallocateMemory(long address, long bytes) {
        if (getChunkOffset(address) != 0) {
            throw new IllegalStateException("invalid address");
        }
        int oldChunkIndex = getChunkIndex(address);
        if (oldChunkIndex == 0) {
            throw new IllegalStateException("realloc NULL");
        }

        if (getChunkSize(address) == bytes) {
            return address; // no change
        }

        long newAddress = allocateMemory(bytes);
        copyBytes(address, newAddress, Math.min(getChunkSize(address), getChunkSize(newAddress)));
        freeMemory(address);
        return newAddress;
    }

    protected abstract long getChunkSize(long address);

    protected abstract void copyBytes(long fromAddress, long toAddress, long byteSize);
}
