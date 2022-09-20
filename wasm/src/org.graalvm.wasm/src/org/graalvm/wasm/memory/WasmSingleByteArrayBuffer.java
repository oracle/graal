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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import java.util.Arrays;

import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

class WasmSingleByteArrayBuffer implements WasmByteArrayBuffer {
    private static final int MAX_CONSTANT_ATTEMPTS = 5;

    @CompilationFinal private Assumption constantMemoryBufferAssumption;

    @CompilationFinal(dimensions = 0) private byte[] constantBuffer;

    private byte[] dynamicBuffer;

    private int constantAttempts = 0;

    WasmSingleByteArrayBuffer() {
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void allocate(long byteSize) {
        assert byteSize <= Integer.MAX_VALUE;
        final int effectiveByteSize = (int) byteSize;
        constantBuffer = null;
        dynamicBuffer = null;
        if (constantAttempts < MAX_CONSTANT_ATTEMPTS) {
            constantMemoryBufferAssumption = Assumption.create("ConstantMemoryBuffer");
            constantAttempts++;
        }
        try {
            if (constantMemoryBufferAssumption.isValid()) {
                constantBuffer = new byte[effectiveByteSize];
            } else {
                dynamicBuffer = new byte[effectiveByteSize];
            }
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    @Override
    public byte[] segment(long address) {
        return buffer();
    }

    private byte[] buffer() {
        if (constantMemoryBufferAssumption.isValid()) {
            return constantBuffer;
        }
        return dynamicBuffer;
    }

    @Override
    public long segmentOffsetAsLong(long address) {
        assert address <= Integer.MAX_VALUE;
        return address;
    }

    @Override
    public int segmentOffsetAsInt(long address) {
        assert address <= Integer.MAX_VALUE;
        return (int) address;
    }

    @Override
    public long size() {
        return buffer().length / MEMORY_PAGE_SIZE;
    }

    @Override
    public long byteSize() {
        return buffer().length;
    }

    @Override
    public void grow(long targetSize) {
        final byte[] currentBuffer = buffer();
        constantMemoryBufferAssumption.invalidate("Memory grow");
        allocate(targetSize);
        System.arraycopy(currentBuffer, 0, buffer(), 0, currentBuffer.length);
    }

    @Override
    public void reset(long byteSize) {
        constantMemoryBufferAssumption.invalidate("Memory reset");
        allocate(byteSize);
    }

    @Override
    public void close() {
        constantBuffer = null;
        dynamicBuffer = null;
    }

    @Override
    public void copyTo(WasmByteArrayBuffer other) {
        final byte[] currentBuffer = buffer();
        final byte[] otherBuffer = other.segment(0L);
        System.arraycopy(currentBuffer, 0, otherBuffer, 0, currentBuffer.length);
    }

    @Override
    public void copyFrom(WasmByteArrayBuffer other, long sourceAddress, long destinationAddress, long length) {
        assert length <= Integer.MAX_VALUE;
        System.arraycopy(other.segment(sourceAddress), other.segmentOffsetAsInt(sourceAddress), segment(destinationAddress), segmentOffsetAsInt(destinationAddress), (int) length);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void fill(long address, long length, byte value) {
        Arrays.fill(buffer(), segmentOffsetAsInt(address), segmentOffsetAsInt(address + length), value);
    }
}
