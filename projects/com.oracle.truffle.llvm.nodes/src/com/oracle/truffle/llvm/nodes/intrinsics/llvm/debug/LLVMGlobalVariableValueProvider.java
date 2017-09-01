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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.Container;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;

import java.math.BigInteger;

public final class LLVMGlobalVariableValueProvider implements LLVMDebugValueProvider {

    private final String varName;

    private final LLVMGlobalVariable global;

    private final LLVMGlobalVariableAccess globalAccess;

    LLVMGlobalVariableValueProvider(String varName, LLVMGlobalVariable global, LLVMGlobalVariableAccess globalAccess) {
        this.varName = varName;
        this.global = global;
        this.globalAccess = globalAccess;
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        return !global.isUninitialized();
    }

    private static final int BOOLEAN_SIZE = 1;

    @Override
    public boolean readBoolean(long bitOffset) {
        if (!canRead(bitOffset, BOOLEAN_SIZE)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + global);

        }

        final Container originalContainer = global.getContainer();
        boolean result;
        if (bitOffset == 0) {
            result = globalAccess.getI1(global);
        } else {
            result = toAddress().readBoolean(bitOffset);
        }
        global.setContainer(originalContainer);
        return result;
    }

    @Override
    public Object readFloat(long bitOffset) {
        if (!canRead(bitOffset, Float.SIZE)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + global);

        }

        final Container originalContainer = global.getContainer();
        Object result;
        if (bitOffset == 0) {
            result = globalAccess.getFloat(global);
        } else {
            result = toAddress().readFloat(bitOffset);
        }
        global.setContainer(originalContainer);
        return result;
    }

    @Override
    public Object readDouble(long bitOffset) {
        if (!canRead(bitOffset, Double.SIZE)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + global);

        }

        final Container originalContainer = global.getContainer();
        Object result;
        if (bitOffset == 0) {
            result = globalAccess.getDouble(global);
        } else {
            result = toAddress().readDouble(bitOffset);
        }
        global.setContainer(originalContainer);
        return result;
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        if (!canRead(bitOffset, LLVM80BitFloat.BIT_WIDTH)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + global);

        }

        final Container originalContainer = global.getContainer();
        Object result = toAddress().read80BitFloat(bitOffset);
        global.setContainer(originalContainer);
        return result;
    }

    @Override
    public Object readAddress(long bitOffset) {
        if (!canRead(bitOffset, LLVMAddress.WORD_LENGTH_BIT)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + global);

        }

        final Container originalContainer = global.getContainer();
        final Object result = toAddress().readAddress(bitOffset);
        global.setContainer(originalContainer);
        return result;
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        if (!canRead(bitOffset, LLVMAddress.WORD_LENGTH_BIT)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + global);

        }

        final Container originalContainer = global.getContainer();
        Object result = toAddress().readUnknown(bitOffset, bitSize);
        global.setContainer(originalContainer);
        return result;
    }

    @Override
    @TruffleBoundary
    public Object computeAddress(long bitOffset) {
        return bitOffset == 0 ? varName : String.format("%s + %d bits", varName, bitOffset);
    }

    @Override
    public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
        if (!canRead(bitOffset, bitSize)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + global);
        }

        final Container originalContainer = global.getContainer();
        BigInteger result = null;
        if (bitOffset == 0) {
            if (signed) {
                switch (bitSize) {
                    case Byte.SIZE:
                        result = BigInteger.valueOf(globalAccess.getI8(global));
                        break;

                    case Short.SIZE:
                        result = BigInteger.valueOf(globalAccess.getI16(global));
                        break;

                    case Integer.SIZE:
                        result = BigInteger.valueOf(globalAccess.getI32(global));
                        break;

                    case Long.SIZE:
                        result = BigInteger.valueOf(globalAccess.getI64(global));
                        break;
                }

            } else {
                switch (bitSize) {
                    case Byte.SIZE:
                        result = BigInteger.valueOf(Byte.toUnsignedInt(globalAccess.getI8(global)));
                        break;

                    case Short.SIZE:
                        result = BigInteger.valueOf(Short.toUnsignedInt(globalAccess.getI16(global)));
                        break;

                    case Integer.SIZE:
                        result = BigInteger.valueOf(Integer.toUnsignedLong(globalAccess.getI32(global)));
                        break;

                    case Long.SIZE:
                        result = new BigInteger(Long.toUnsignedString(globalAccess.getI64(global)));
                        break;
                }
            }
        }

        if (result == null) {
            result = toAddress().readInteger(bitOffset, bitSize, signed);
        }

        global.setContainer(originalContainer);
        return result;
    }

    @Override
    public LLVMDebugValueProvider dereferencePointer(long bitOffset) {
        if (!canRead(bitOffset, LLVMAddress.WORD_LENGTH_BIT)) {
            return null;
        }

        final Container originalContainer = global.getContainer();
        final LLVMAddress address;
        if (bitOffset == 0) {
            address = LLVMAddress.fromLong(globalAccess.getI64(global));
        } else {
            address = LLVMAddress.fromLong(toAddress().readInteger(bitOffset, LLVMAddress.WORD_LENGTH_BIT, false).longValue());
        }
        global.setContainer(originalContainer);
        return new LLVMAddressValueProvider(address);
    }

    @Override
    public boolean isInteropValue() {
        final Container originalContainer = global.getContainer();
        final Object value = globalAccess.get(global);
        global.setContainer(originalContainer);
        return value instanceof TruffleObject || value instanceof LLVMTruffleObject;
    }

    @Override
    public Object asInteropValue() {
        final Container originalContainer = global.getContainer();
        final Object value = globalAccess.get(global);
        global.setContainer(originalContainer);
        return value;
    }

    private LLVMAddressValueProvider toAddress() {
        return new LLVMAddressValueProvider(globalAccess.getNativeLocation(global));
    }
}
