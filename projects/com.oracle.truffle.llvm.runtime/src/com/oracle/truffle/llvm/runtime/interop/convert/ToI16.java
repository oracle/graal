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

abstract class ToI16 extends ForeignToLLVM {

    @Child private ToI16 toI16;

    @Specialization
    public short fromInt(int value) {
        return (short) value;
    }

    @Specialization
    public short fromChar(char value) {
        return (short) value;
    }

    @Specialization
    public short fromShort(short value) {
        return value;
    }

    @Specialization
    public short fromLong(long value) {
        return (short) value;
    }

    @Specialization
    public short fromByte(byte value) {
        return value;
    }

    @Specialization
    public short fromFloat(float value) {
        return (short) value;
    }

    @Specialization
    public short fromDouble(double value) {
        return (short) value;
    }

    @Specialization
    public short fromBoolean(boolean value) {
        return (short) (value ? 1 : 0);
    }

    @Specialization
    public short fromForeignPrimitive(LLVMBoxedPrimitive boxed) {
        return recursiveConvert(boxed.getValue());
    }

    @Specialization(guards = "notLLVM(obj)")
    public short fromTruffleObject(TruffleObject obj) {
        return recursiveConvert(fromForeign(obj));
    }

    @Specialization
    public short fromString(String value) {
        return (short) getSingleStringCharacter(value);
    }

    @Specialization
    public short fromLLVMFunctionDescriptor(LLVMFunctionDescriptor fd) {
        return (short) fd.getFunctionPointer();
    }

    @Specialization
    public short fromLLVMTruffleAddress(LLVMTruffleAddress obj) {
        return (short) obj.getAddress().getVal();
    }

    @Specialization
    public short fromSharedDescriptor(LLVMSharedGlobalVariable shared, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess access) {
        return (short) access.getNativeLocation(shared.getDescriptor()).getVal();
    }

    private short recursiveConvert(Object o) {
        if (toI16 == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toI16 = ToI16NodeGen.create();
        }
        return (short) toI16.executeWithTarget(o);
    }

    @TruffleBoundary
    static short slowPathPrimitiveConvert(ForeignToLLVM thiz, Object value) {
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        } else if (value instanceof Boolean) {
            return (short) ((boolean) value ? 1 : 0);
        } else if (value instanceof Character) {
            return (short) (char) value;
        } else if (value instanceof String) {
            return (short) thiz.getSingleStringCharacter((String) value);
        } else if (value instanceof LLVMFunctionDescriptor) {
            return (short) ((LLVMFunctionDescriptor) value).getFunctionPointer();
        } else if (value instanceof LLVMBoxedPrimitive) {
            return slowPathPrimitiveConvert(thiz, ((LLVMBoxedPrimitive) value).getValue());
        } else if (value instanceof LLVMTruffleAddress) {
            return (short) ((LLVMTruffleAddress) value).getAddress().getVal();
        } else if (value instanceof LLVMSharedGlobalVariable) {
            return (short) createGlobalAccess().getNativeLocation(((LLVMSharedGlobalVariable) value).getDescriptor()).getVal();
        } else if (value instanceof TruffleObject && notLLVM((TruffleObject) value)) {
            return slowPathPrimitiveConvert(thiz, thiz.fromForeign((TruffleObject) value));
        } else {
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }
}
