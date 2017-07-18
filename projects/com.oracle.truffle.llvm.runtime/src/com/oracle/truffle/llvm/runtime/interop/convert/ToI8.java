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
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

abstract class ToI8 extends ForeignToLLVM {

    @Child private ToI8 toI8;

    @Specialization
    public byte fromInt(int value) {
        return (byte) value;
    }

    @Specialization
    public byte fromChar(char value) {
        return (byte) value;
    }

    @Specialization
    public byte fromLong(long value) {
        return (byte) value;
    }

    @Specialization
    public byte fromShort(short value) {
        return (byte) value;
    }

    @Specialization
    public byte fromByte(byte value) {
        return value;
    }

    @Specialization
    public byte fromFloat(float value) {
        return (byte) value;
    }

    @Specialization
    public byte fromDouble(double value) {
        return (byte) value;
    }

    @Specialization
    public byte fromBoolean(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    @Specialization
    public byte fromForeignPrimitive(LLVMBoxedPrimitive boxed) {
        return recursiveConvert(boxed.getValue());
    }

    @Specialization(guards = "notLLVM(obj)")
    public byte fromTruffleObject(TruffleObject obj) {
        return recursiveConvert(fromForeign(obj));
    }

    @Specialization
    public byte fromString(String value) {
        return (byte) getSingleStringCharacter(value);
    }

    @Specialization
    public byte fromLLVMTruffleAddress(LLVMTruffleAddress obj) {
        return (byte) obj.getAddress().getVal();
    }

    @Specialization
    public byte fromLLVMFunctionDescriptor(LLVMFunctionDescriptor fd) {
        return (byte) fd.getFunctionPointer();
    }

    @Specialization
    public byte fromSharedDescriptor(LLVMSharedGlobalVariable shared, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess access) {
        return (byte) access.getNativeLocation(shared.getDescriptor()).getVal();
    }

    private byte recursiveConvert(Object o) {
        if (toI8 == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toI8 = ToI8NodeGen.create();
        }
        return (byte) toI8.executeWithTarget(o);
    }

    protected static boolean notLLVM(TruffleObject value) {
        return LLVMExpressionNode.notLLVM(value);
    }

    @TruffleBoundary
    static byte slowPathPrimitiveConvert(ForeignToLLVM thiz, Object value) {
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        } else if (value instanceof Boolean) {
            return (byte) ((boolean) value ? 1 : 0);
        } else if (value instanceof Character) {
            return (byte) (char) value;
        } else if (value instanceof String) {
            return (byte) thiz.getSingleStringCharacter((String) value);
        } else if (value instanceof LLVMFunctionDescriptor) {
            return (byte) ((LLVMFunctionDescriptor) value).getFunctionPointer();
        } else if (value instanceof LLVMBoxedPrimitive) {
            return slowPathPrimitiveConvert(thiz, ((LLVMBoxedPrimitive) value).getValue());
        } else if (value instanceof LLVMTruffleAddress) {
            return (byte) ((LLVMTruffleAddress) value).getAddress().getVal();
        } else if (value instanceof LLVMSharedGlobalVariable) {
            return (byte) createGlobalAccess().getNativeLocation(((LLVMSharedGlobalVariable) value).getDescriptor()).getVal();
        } else if (value instanceof TruffleObject && notLLVM((TruffleObject) value)) {
            return slowPathPrimitiveConvert(thiz, thiz.fromForeign((TruffleObject) value));
        } else {
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }
}
