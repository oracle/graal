/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.memory;

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import java.util.Arrays;

import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

class WasmMultiByteArrayBuffer implements WasmByteArrayBuffer {
    private static final long MAX_BUFFER_SIZE = Sizes.MAX_MEMORY_64_INSTANCE_BYTE_SIZE;
    private static final int SEGMENT_LENGTH = 0x3fff_ffff;
    private static final long SEGMENT_MASK = 0xffff_ffff_ffff_ffffL - SEGMENT_LENGTH;
    private static final long OFFSET_MASK = SEGMENT_LENGTH;
    private static final int SEGMENT_SHIFT = 32 - Integer.numberOfLeadingZeros(SEGMENT_LENGTH);

    private byte[][] buffer;
    private byte[] mainBuffer;

    private long bufferByteSize;
    private int segmentCount;
    private int lastSegmentLength;

    WasmMultiByteArrayBuffer() {
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void allocate(long byteSize) {
        assert byteSize <= MAX_BUFFER_SIZE;
        segmentCount = (int) ((byteSize & SEGMENT_MASK) >> SEGMENT_SHIFT) + 1;
        lastSegmentLength = (int) (byteSize & OFFSET_MASK);
        try {
            buffer = new byte[segmentCount][];
            for (int i = 0; i < segmentCount - 1; i++) {
                buffer[i] = new byte[SEGMENT_LENGTH];
            }
            buffer[segmentCount - 1] = new byte[lastSegmentLength];
            mainBuffer = buffer[0];
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
        bufferByteSize = byteSize;
    }

    @Override
    public byte[] segment(long address) {
        final int segmentIndex = (int) ((address & SEGMENT_MASK) >> SEGMENT_SHIFT);
        if (segmentIndex == 0) {
            return mainBuffer;
        }
        return buffer[segmentIndex];
    }

    @Override
    public long segmentOffsetAsLong(long address) {
        return address & OFFSET_MASK;
    }

    @Override
    public int segmentOffsetAsInt(long address) {
        return (int) (address & OFFSET_MASK);
    }

    @Override
    public long size() {
        return bufferByteSize / MEMORY_PAGE_SIZE;
    }

    @Override
    public long byteSize() {
        return bufferByteSize;
    }

    @Override
    public void grow(long targetSize) {
        final int currentSegmentCount = segmentCount;
        final int currentLastSegmentLength = lastSegmentLength;
        final byte[][] currentBuffer = buffer;
        allocate(targetSize);
        for (int i = 0; i < currentSegmentCount - 1; i++) {
            System.arraycopy(currentBuffer[i], 0, buffer[i], 0, SEGMENT_LENGTH);
        }
        System.arraycopy(currentBuffer[currentSegmentCount - 1], 0, buffer[currentSegmentCount - 1], 0, currentLastSegmentLength);
    }

    @Override
    public void reset(long byteSize) {
        allocate(byteSize);
    }

    @Override
    public void close() {
        buffer = null;
        mainBuffer = null;
    }

    @Override
    public void copyTo(WasmByteArrayBuffer other) {
        for (int i = 0; i < segmentCount - 1; i++) {
            System.arraycopy(buffer[i], 0, other.segment((long) i * SEGMENT_LENGTH), 0, SEGMENT_LENGTH);
        }
        System.arraycopy(buffer[segmentCount - 1], 0, other.segment((long) segmentCount * SEGMENT_LENGTH), 0, lastSegmentLength);
    }

    @Override
    public void copyFrom(WasmByteArrayBuffer other, long sourceAddress, long destinationAddress, long length) {
        long currentSrcAddress = sourceAddress;
        long currentDstAddress = destinationAddress;
        long remainingLength = length;
        while (remainingLength > 0) {
            final int srcOffset = segmentOffsetAsInt(currentSrcAddress);
            final int dstOffset = segmentOffsetAsInt(currentDstAddress);
            final int sourceChunkSize = SEGMENT_LENGTH - srcOffset;
            final int destinationChunkSize = SEGMENT_LENGTH - dstOffset;
            // Since the first value is an integer, this is guaranteed to be an int.
            final int chunkSize = (int) Long.min(Integer.min(sourceChunkSize, destinationChunkSize), remainingLength);
            System.arraycopy(segment(currentSrcAddress), srcOffset, other.segment(currentDstAddress), dstOffset, chunkSize);
            remainingLength -= chunkSize;
            currentSrcAddress += chunkSize;
            currentDstAddress += chunkSize;
        }
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void fill(long address, long length, byte value) {
        for (int i = 0; i < segmentCount - 1; i++) {
            Arrays.fill(buffer[i], 0, SEGMENT_LENGTH, value);
        }
        Arrays.fill(buffer[segmentCount - 1], 0, lastSegmentLength, value);
    }
}
