/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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

import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import static com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableDebugAccess.getManagedValue;
import static com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableDebugAccess.getNativeLocation;
import static com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableDebugAccess.isInNative;
import static com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableDebugAccess.isInitialized;

final class LLVMConstantGlobalPointerProvider implements LLVMDebugValue {

    private final LLVMGlobal global;
    private final LLVMContext context;
    private final LLVMMemory memory;
    private final LLVMDebugValue.Builder valueBuilder;

    LLVMConstantGlobalPointerProvider(LLVMMemory memory, LLVMGlobal global, LLVMContext context, Builder valueBuilder) {
        this.memory = memory;
        this.global = global;
        this.context = context;
        this.valueBuilder = valueBuilder;
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        return isInitialized(context, global);
    }

    private Object doRead(long offset, int size, Function<LLVMDebugValue, Object> readOperation) {
        final LLVMDebugValue ptr = asPointer();
        if (ptr != null) {
            return readOperation.apply(ptr);
        }
        return describeValue(offset, size);
    }

    @Override
    @TruffleBoundary
    public String describeValue(long bitOffset, int bitSize) {
        final StringBuilder builder = new StringBuilder(global.getSourceName());
        if (bitOffset != 0 || bitSize != LLVMDebugTypeConstants.ADDRESS_SIZE) {
            builder.append('(').append(bitSize).append(" bits at offset ").append(bitOffset).append(')');
        }
        builder.append(" (LLVM-IR global variable").append(global.getName());
        final LLVMContext.ExternalLibrary library = global.getLibrary();
        if (library != null) {
            builder.append(" in ").append(library.getName());
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public Object readBoolean(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE, ptr -> ptr.readBoolean(bitOffset));
    }

    @Override
    public Object readFloat(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE, ptr -> ptr.readFloat(bitOffset));
    }

    @Override
    public Object readDouble(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE, ptr -> ptr.readDouble(bitOffset));
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL, ptr -> ptr.read80BitFloat(bitOffset));
    }

    @Override
    public Object readAddress(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE, ptr -> ptr.readAddress(bitOffset));
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        return doRead(bitOffset, bitSize, ptr -> ptr.readUnknown(bitOffset, bitSize));
    }

    @Override
    @TruffleBoundary
    public Object computeAddress(long bitOffset) {
        final LLVMDebugValue ptr = asPointer();
        if (ptr != null) {
            return ptr.computeAddress(bitOffset);
        }

        final String value = describeValue(0, LLVMDebugTypeConstants.ADDRESS_SIZE);
        return String.format("%d bits + %s", bitOffset, value);
    }

    @Override
    public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
        final LLVMDebugValue ptr = asPointer();
        if (ptr != null) {
            return ptr.readBigInteger(bitOffset, bitSize, signed);
        }

        return describeValue(bitOffset, bitSize);
    }

    @Override
    public LLVMDebugValue dereferencePointer(long bitOffset) {
        if (bitOffset != 0) {
            return null;

        } else if (isInNative(context, global)) {
            final LLVMNativePointer ptr = getNativeLocation(context, global);
            return new LLVMAllocationValueProvider(memory, ptr);

        } else {
            return valueBuilder.build(getManagedValue(context, global));
        }
    }

    @Override
    public boolean isInteropValue() {
        return false;
    }

    @Override
    public Object asInteropValue() {
        return null;
    }

    private LLVMDebugValue asPointer() {
        if (isInNative(context, global)) {
            return new LLVMConstantValueProvider.Pointer(memory, getNativeLocation(context, global));
        }
        return null;
    }
}
