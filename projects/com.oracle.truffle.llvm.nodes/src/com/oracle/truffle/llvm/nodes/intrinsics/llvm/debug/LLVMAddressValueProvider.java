/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

final class LLVMAddressValueProvider implements LLVMDebugValueProvider {

    private final LLVMAddress baseAddress;

    LLVMAddressValueProvider(LLVMAddress baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    public boolean canRead() {
        return !LLVMAddress.nullPointer().equals(baseAddress);
    }

    @Override
    @TruffleBoundary
    public boolean readBoolean(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getI1(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public byte readByteSigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getI8(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public int readByteUnsigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return Byte.toUnsignedInt(LLVMMemory.getI8(baseAddress.increment(offset)));
        }
    }

    @Override
    @TruffleBoundary
    public char readCharSigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return (char) LLVMMemory.getI8(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public char readCharUnsigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return (char) Byte.toUnsignedInt(LLVMMemory.getI8(baseAddress.increment(offset)));
        }
    }

    @Override
    @TruffleBoundary
    public short readShortSigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getI16(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public int readShortUnsigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return Short.toUnsignedInt(LLVMMemory.getI16(baseAddress.increment(offset)));
        }
    }

    @Override
    @TruffleBoundary
    public int readIntSigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getI32(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public long readIntUnsigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return Integer.toUnsignedLong(LLVMMemory.getI32(baseAddress.increment(offset)));
        }
    }

    @Override
    @TruffleBoundary
    public long readLongSigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getI64(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public String readLongUnsigned(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return Long.toUnsignedString(LLVMMemory.getI64(baseAddress.increment(offset)));
        }
    }

    @Override
    @TruffleBoundary
    public float readFloat(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getFloat(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public double readDouble(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getDouble(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public LLVM80BitFloat read80BitFloat(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.get80BitFloat(baseAddress.increment(offset));
        }
    }

    @Override
    @TruffleBoundary
    public LLVMAddress readAddress(long offset) {
        if (!canRead()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        } else {
            return LLVMMemory.getAddress(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readUnknown(long offset, long size) {
        return String.format("Unknown %d bits at %s", size, baseAddress.increment(offset));
    }

    @Override
    public boolean canReadId(long offset, int size) {
        if (!canRead()) {
            return false;
        }

        switch (size) {
            case Byte.SIZE:
            case Short.SIZE:
            case Integer.SIZE:
            case Long.SIZE:
                return true;

            default:
                return false;
        }
    }

    @Override
    @TruffleBoundary
    public long readId(long offset, int size) {
        switch (size) {
            case Byte.SIZE:
                return LLVMMemory.getI8(baseAddress.increment(offset));

            case Short.SIZE:
                return LLVMMemory.getI16(baseAddress.increment(offset));

            case Integer.SIZE:
                return LLVMMemory.getI32(baseAddress.increment(offset));

            case Long.SIZE:
                return LLVMMemory.getI64(baseAddress.increment(offset));
        }
        return -1L;
    }

    @Override
    public Object computeAddress(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return baseAddress;
        } else {
            return baseAddress.increment(offset);
        }
    }

    @Override
    public String toString() {
        return baseAddress.toString();
    }
}
