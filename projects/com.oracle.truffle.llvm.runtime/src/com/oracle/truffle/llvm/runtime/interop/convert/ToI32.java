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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;

abstract class ToI32 extends ForeignToLLVM {

    @Child private ToI32 toI32;

    @Specialization
    public int fromInt(int value) {
        return value;
    }

    @Specialization
    public int fromChar(char value) {
        return value;
    }

    @Specialization
    public int fromShort(short value) {
        return value;
    }

    @Specialization
    public int fromLong(long value) {
        return (int) value;
    }

    @Specialization
    public int fromByte(byte value) {
        return value;
    }

    @Specialization
    public int fromFloat(float value) {
        return (int) value;
    }

    @Specialization
    public int fromDouble(double value) {
        return (int) value;
    }

    @Specialization
    public int fromBoolean(boolean value) {
        return value ? 1 : 0;
    }

    @Specialization
    public int fromForeignPrimitive(LLVMBoxedPrimitive boxed) {
        return recursiveConvert(boxed.getValue());
    }

    @Specialization(guards = "notLLVM(obj)")
    public int fromTruffleObject(TruffleObject obj) {
        return recursiveConvert(fromForeign(obj));
    }

    @Specialization
    public int fromString(String value) {
        return getSingleStringCharacter(value);
    }

    @Specialization
    public int fromLLVMTruffleAddress(LLVMTruffleAddress obj) {
        return (int) obj.getAddress().getVal();
    }

    @Specialization
    public int fromLLVMFunctionDescriptor(LLVMFunctionDescriptor fd) {
        return (int) fd.getFunctionPointer();
    }

    @Specialization
    public int fromSharedDescriptor(LLVMSharedGlobalVariable shared, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess access) {
        return (int) access.getNativeLocation(shared.getDescriptor()).getVal();
    }

    private int recursiveConvert(Object o) {
        if (toI32 == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toI32 = ToI32NodeGen.create();
        }
        return (int) toI32.executeWithTarget(o);
    }

    @TruffleBoundary
    static int slowPathPrimitiveConvert(ForeignToLLVM thiz, Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof Boolean) {
            return ((boolean) value ? 1 : 0);
        } else if (value instanceof Character) {
            return (char) value;
        } else if (value instanceof String) {
            return thiz.getSingleStringCharacter((String) value);
        } else if (value instanceof LLVMFunctionDescriptor) {
            return (int) ((LLVMFunctionDescriptor) value).getFunctionPointer();
        } else if (value instanceof LLVMBoxedPrimitive) {
            return slowPathPrimitiveConvert(thiz, ((LLVMBoxedPrimitive) value).getValue());
        } else if (value instanceof LLVMTruffleAddress) {
            return (int) ((LLVMTruffleAddress) value).getAddress().getVal();
        } else if (value instanceof LLVMSharedGlobalVariable) {
            return (int) createGlobalAccess().getNativeLocation(((LLVMSharedGlobalVariable) value).getDescriptor()).getVal();
        } else if (value instanceof TruffleObject && notLLVM((TruffleObject) value)) {
            return slowPathPrimitiveConvert(thiz, thiz.fromForeign((TruffleObject) value));
        } else {
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }
}
