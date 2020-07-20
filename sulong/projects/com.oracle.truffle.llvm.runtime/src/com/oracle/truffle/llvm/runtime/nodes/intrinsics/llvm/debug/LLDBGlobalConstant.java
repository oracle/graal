/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug;

import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

final class LLDBGlobalConstant implements LLVMDebugValue {

    private final LLVMGlobal global;
    private final LLVMContext context;

    LLDBGlobalConstant(LLVMGlobal global, LLVMContext context) {
        this.global = global;
        this.context = context;
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        return canRead(bitOffset, bits, getCurrentValue());
    }

    private boolean canRead(long bitOffset, int bits, LLVMDebugValue currentValue) {
        int index = global.getSymbolIndex(false);
        AssumedValue<LLVMPointer>[] globals = context.findSymbolTable(global.getBitcodeID(false));
        return globals[index].get() != null && currentValue != null && currentValue.canRead(bitOffset, bits);
    }

    private Object doRead(long offset, int size, String kind, Function<LLVMDebugValue, Object> readOperation) {
        final LLVMDebugValue value = getCurrentValue();
        if (value == null) {
            return UNAVAILABLE_VALUE;

        } else if (!canRead(offset, size, value)) {
            return cannotInterpret(kind, offset, size);

        } else {
            return readOperation.apply(value);
        }
    }

    @Override
    @TruffleBoundary
    public String describeValue(long bitOffset, int bitSize) {
        return String.format("%s (%s at offset %s)", global.getSourceName(), LLDBSupport.toSizeString(bitSize), LLDBSupport.toSizeString(bitOffset));
    }

    @Override
    public Object readBoolean(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE, LLVMDebugTypeConstants.BOOLEAN_NAME, value -> value.readBoolean(bitOffset));
    }

    @Override
    public Object readFloat(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE, LLVMDebugTypeConstants.FLOAT_NAME, value -> value.readFloat(bitOffset));
    }

    @Override
    public Object readDouble(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE, LLVMDebugTypeConstants.DOUBLE_NAME, value -> value.readDouble(bitOffset));
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL, LLVMDebugTypeConstants.LLVM80BIT_NAME, value -> value.read80BitFloat(bitOffset));
    }

    @Override
    public Object readAddress(long bitOffset) {
        return doRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE, LLVMDebugTypeConstants.ADDRESS_NAME, value -> value.readAddress(bitOffset));
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        final LLVMDebugValue value = getCurrentValue();
        if (value != null) {
            return value.readUnknown(bitOffset, bitSize);
        } else {
            return UNAVAILABLE_VALUE;
        }
    }

    @Override
    public Object computeAddress(long bitOffset) {
        final LLVMDebugValue value = getCurrentValue();
        if (value != null) {
            return value.computeAddress(bitOffset);
        } else {
            return describeValue(bitOffset, 0);
        }
    }

    @Override
    public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
        final String kind = LLVMDebugTypeConstants.getIntegerKind(bitSize, signed);
        return doRead(bitOffset, bitSize, kind, value -> value.readBigInteger(bitOffset, bitSize, signed));
    }

    @Override
    public boolean isAlwaysSafeToDereference(long bitOffset) {
        final LLVMDebugValue value = getCurrentValue();
        return value != null && value.isAlwaysSafeToDereference(bitOffset);
    }

    @Override
    public LLVMDebugValue dereferencePointer(long bitOffset) {
        final LLVMDebugValue value = getCurrentValue();
        if (value != null) {
            return value.dereferencePointer(bitOffset);
        } else {
            return null;
        }
    }

    @Override
    public boolean isInteropValue() {
        return asInteropValue() != null;
    }

    @Override
    public Object asInteropValue() {
        if (isInNative()) {
            return null;
        }
        final LLVMDebugValue value = getCurrentValue();
        if (value != null && value.isInteropValue()) {
            return value.asInteropValue();
        } else {
            return null;
        }
    }

    private LLVMDebugValue getCurrentValue() {
        int index = global.getSymbolIndex(false);
        AssumedValue<LLVMPointer>[] globals = context.findSymbolTable(global.getBitcodeID(false));
        if (isInNative()) {
            return new LLDBMemoryValue(LLVMNativePointer.cast(globals[index].get()));
        } else {
            return CommonNodeFactory.createDebugValueBuilder().build(globals[index].get());
        }
    }

    private boolean isInNative() {
        int index = global.getSymbolIndex(false);
        AssumedValue<LLVMPointer>[] globals = context.findSymbolTable(global.getBitcodeID(false));
        return LLVMNativePointer.isInstance(globals[index].get());
    }
}
