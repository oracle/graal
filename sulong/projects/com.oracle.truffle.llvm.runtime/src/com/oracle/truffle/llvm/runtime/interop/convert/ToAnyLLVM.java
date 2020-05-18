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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class ToAnyLLVM extends ForeignToLLVM {

    @Specialization
    protected int fromInt(int value) {
        return value;
    }

    @Specialization
    protected char fromChar(char value) {
        return value;
    }

    @Specialization
    protected long fromLong(long value) {
        return value;
    }

    @Specialization
    protected byte fromByte(byte value) {
        return value;
    }

    @Specialization
    protected short fromShort(short value) {
        return value;
    }

    @Specialization
    protected float fromFloat(float value) {
        return value;
    }

    @Specialization
    protected double fromDouble(double value) {
        return value;
    }

    @Specialization
    protected boolean fromBoolean(boolean value) {
        return value;
    }

    @Specialization
    protected String fromString(String obj) {
        return obj;
    }

    @Specialization
    protected LLVMPointer fromPointer(LLVMPointer pointer) {
        return pointer;
    }

    @Specialization(guards = {"foreigns.isForeign(obj)"})
    protected LLVMManagedPointer fromUnknownObject(Object obj,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns) {
        return LLVMManagedPointer.create(obj);
    }

    @Specialization
    protected LLVMManagedPointer fromInternal(LLVMInternalTruffleObject object) {
        return LLVMManagedPointer.create(object);
    }

    @TruffleBoundary
    static Object slowPathPrimitiveConvert(Object value) throws UnsupportedTypeException {
        if (value instanceof Number) {
            return value;
        } else if (value instanceof Boolean) {
            return value;
        } else if (value instanceof Character) {
            return value;
        } else if (value instanceof String) {
            return value;
        } else if (LLVMPointer.isInstance(value)) {
            return value;
        } else if (value instanceof LLVMInternalTruffleObject) {
            return LLVMManagedPointer.create(value);
        } else if (LLVMAsForeignLibrary.getFactory().getUncached().isForeign(value)) {
            return LLVMManagedPointer.create(value);
        } else {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }
}
