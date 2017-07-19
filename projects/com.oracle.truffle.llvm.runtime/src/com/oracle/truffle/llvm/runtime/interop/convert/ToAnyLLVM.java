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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;

abstract class ToAnyLLVM extends ForeignToLLVM {

    @Specialization
    public int fromInt(int value) {
        return value;
    }

    @Specialization
    public char fromChar(char value) {
        return value;
    }

    @Specialization
    public long fromLong(long value) {
        return value;
    }

    @Specialization
    public byte fromByte(byte value) {
        return value;
    }

    @Specialization
    public short fromShort(short value) {
        return value;
    }

    @Specialization
    public float fromFloat(float value) {
        return value;
    }

    @Specialization
    public double fromDouble(double value) {
        return value;
    }

    @Specialization
    public boolean fromBoolean(boolean value) {
        return value;
    }

    @Specialization
    public String fromString(String obj) {
        return obj;
    }

    @Specialization
    public LLVMBoxedPrimitive fromBoxedPrimitive(LLVMBoxedPrimitive boxed) {
        return boxed;
    }

    @Specialization
    public LLVMAddress fromLLVMTruffleAddress(LLVMTruffleAddress obj) {
        return obj.getAddress();
    }

    @Specialization
    public LLVMFunctionDescriptor fromLLVMFunctionDescriptor(LLVMFunctionDescriptor fd) {
        return fd;
    }

    @Specialization
    public LLVMGlobalVariable fromSharedDescriptor(LLVMSharedGlobalVariable shared) {
        return shared.getDescriptor();
    }

    @Specialization(guards = {"checkIsPointer(obj)", "notLLVM(obj)"})
    public LLVMAddress fromNativePointer(TruffleObject obj) {
        try {
            long raw = ForeignAccess.sendAsPointer(asPointer, obj);
            return LLVMAddress.fromLong(raw);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Foreign value is not a pointer!", ex);
        }
    }

    @Specialization(guards = {"!checkIsPointer(obj)", "notLLVM(obj)"})
    public TruffleObject fromTruffleObject(TruffleObject obj) {
        return obj;
    }

    @TruffleBoundary
    static Object slowPathPrimitiveConvert(ForeignToLLVM thiz, Object value) {
        if (value instanceof Number) {
            return value;
        } else if (value instanceof Boolean) {
            return value;
        } else if (value instanceof Character) {
            return value;
        } else if (value instanceof String) {
            return value;
        } else if (value instanceof LLVMBoxedPrimitive) {
            return value;
        } else if (value instanceof LLVMFunctionDescriptor) {
            return value;
        } else if (value instanceof LLVMTruffleAddress) {
            return ((LLVMTruffleAddress) value).getAddress();
        } else if (value instanceof LLVMSharedGlobalVariable) {
            return ((LLVMSharedGlobalVariable) value).getDescriptor();
        } else if (value instanceof TruffleObject && thiz.checkIsPointer((TruffleObject) value) && notLLVM((TruffleObject) value)) {
            try {
                long raw = ForeignAccess.sendAsPointer(thiz.asPointer, (TruffleObject) value);
                return LLVMAddress.fromLong(raw);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Foreign value is not a pointer!", ex);
            }
        } else if (value instanceof TruffleObject && !thiz.checkIsPointer((TruffleObject) value) && notLLVM((TruffleObject) value)) {
            return value;
        } else {
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }
}
