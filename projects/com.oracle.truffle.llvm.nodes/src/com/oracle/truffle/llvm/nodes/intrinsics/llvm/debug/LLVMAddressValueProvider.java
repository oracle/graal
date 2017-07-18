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

import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

final class LLVMAddressValueProvider implements LLVMDebugValueProvider {

    private static final String UNAVAILABLE = "<unavailable>";

    private final LLVMAddress baseAddress;

    LLVMAddressValueProvider(LLVMAddress baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    public Object readBoolean(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getI1(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readByteSigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getI8(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readByteUnsigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return Byte.toUnsignedInt(LLVMMemory.getI8(baseAddress.increment(offset)));
        }
    }

    @Override
    public Object readCharSigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return (char) LLVMMemory.getI8(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readCharUnsigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return (char) Byte.toUnsignedInt(LLVMMemory.getI8(baseAddress.increment(offset)));
        }
    }

    @Override
    public Object readShortSigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getI16(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readShortUnsigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return Short.toUnsignedInt(LLVMMemory.getI16(baseAddress.increment(offset)));
        }
    }

    @Override
    public Object readIntSigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getI32(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readIntUnsigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return Integer.toUnsignedLong(LLVMMemory.getI32(baseAddress.increment(offset)));
        }
    }

    @Override
    public Object readLongSigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getI64(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readLongUnsigned(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return Long.toUnsignedString(LLVMMemory.getI64(baseAddress.increment(offset)));
        }
    }

    @Override
    public Object readFloat(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getFloat(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readDouble(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getDouble(baseAddress.increment(offset));
        }
    }

    @Override
    public Object read80BitFloat(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.get80BitFloat(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readAddress(long offset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return LLVMMemory.getAddress(baseAddress.increment(offset));
        }
    }

    @Override
    public Object readUnknown(long offset, long size) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return UNAVAILABLE;
        } else {
            return String.format("Unknown %d bits at %s", size, baseAddress.increment(offset));
        }
    }

    @Override
    public boolean canReadId(long offset, int size) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
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
            return UNAVAILABLE;
        } else {
            return baseAddress.increment(offset);
        }
    }
}
