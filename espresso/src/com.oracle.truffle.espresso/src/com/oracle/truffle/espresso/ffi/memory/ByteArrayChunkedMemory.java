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
import java.util.Arrays;

import com.oracle.truffle.espresso.libs.LibsState;

/**
 * Abstract class for shared code between classes which use a chunked byte-array native memory.
 */
public abstract class ByteArrayChunkedMemory extends ChunkedNativeMemory<byte[]> {

    @Override
    protected byte[] allocateChunk(long bytes) throws MemoryAllocationException {
        /*
         * Checks if bytes fit into an int and throws a MemoryAllocationException if not. Therefore,
         * it is okay to cast bytes to int without a check.
         */
        if (bytes > Integer.MAX_VALUE) {
            LibsState.getLogger().warning("Native memory has maximum memory allocation size Integer.MAX_VALUE! " + //
                            "Use MemorySegmentChunkedMemory as memory backend for larger allocation size.");
            throw new MemoryAllocationException("Exceeded maximum allocation size (Integer.MAX_VALUE)");
        }
        try {
            return new byte[(int) bytes];
        } catch (OutOfMemoryError e) {
            throw new MemoryAllocationException(e);
        }
    }

    @Override
    protected long getChunkSize(byte[] chunk) {
        return chunk.length;
    }

    @Override
    public void setMemoryImpl(byte[] chunk, long chunkOffset, long bytes, byte value) {
        /*
         * Unchecked casts are safe as the memory access has already been validated in
         * ChunkedNativeMemory.setMemory
         */
        assert bytes <= Integer.MAX_VALUE;
        Arrays.fill(chunk, 0, (int) bytes, value);
    }

    @Override
    public void copyMemoryImpl(byte[] fromChunk, long fromOffset, byte[] toChunk, long toOffset, long byteSize) {
        /*
         * Unchecked casts are safe as the memory access has already been validated in
         * ChunkedNativeMemory.copyMemory
         */
        assert fromOffset <= Integer.MAX_VALUE;
        assert toOffset <= Integer.MAX_VALUE;
        assert byteSize <= Integer.MAX_VALUE;
        System.arraycopy(fromChunk, (int) fromOffset, toChunk, (int) toOffset, (int) byteSize);
    }

    @Override
    public void writeMemoryImpl(byte[] chunk, long chunkOffset, byte[] buf) {
        /*
         * Unchecked casts are safe as the memory access has already been validated in
         * ChunkedNativeMemory.writeMemory
         */
        assert chunkOffset <= Integer.MAX_VALUE;
        System.arraycopy(buf, 0, chunk, (int) chunkOffset, buf.length);
    }

    @Override
    public void readMemoryImpl(byte[] chunk, long chunkOffset, byte[] buf) {
        /*
         * Unchecked casts are safe as the memory access has already been validated in
         * ChunkedNativeMemory.readMemory
         */
        assert chunkOffset <= Integer.MAX_VALUE;
        System.arraycopy(chunk, (int) chunkOffset, buf, 0, buf.length);
    }

    @Override
    public ByteBuffer wrapChunk(byte[] chunk, long chunkOffset, int bytes) {
        /*
         * Unchecked casts are safe as the memory access has already been validated in
         * ChunkedNativeMemory.wrapNativeMemory
         */
        assert chunkOffset <= Integer.MAX_VALUE;
        return ByteBuffer.wrap(chunk, (int) chunkOffset, bytes);
    }
}
