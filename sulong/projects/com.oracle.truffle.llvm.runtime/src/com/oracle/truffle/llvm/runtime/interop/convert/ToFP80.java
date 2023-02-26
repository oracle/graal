/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.api.SerializableLibrary;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Buffer;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class ToFP80 extends ForeignToLLVM {

    @Specialization
    protected LLVM80BitFloat fromInt(int value) {
        return LLVM80BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM80BitFloat fromChar(char value) {
        return LLVM80BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM80BitFloat fromLong(long value) {
        return LLVM80BitFloat.fromLong(value);
    }

    @Specialization
    protected LLVM80BitFloat fromByte(byte value) {
        return LLVM80BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM80BitFloat fromShort(short value) {
        return LLVM80BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM80BitFloat fromFloat(float value) {
        return LLVM80BitFloat.fromFloat(value);
    }

    @Specialization
    protected LLVM80BitFloat fromDouble(double value) {
        return LLVM80BitFloat.fromDouble(value);
    }

    @Specialization(limit = "1", guards = "serialize.isSerializable(value)")
    protected LLVM80BitFloat from80BitFloat(Object value,
                    @CachedLibrary("value") SerializableLibrary serialize) {
        try {
            // this is an FP80 value from the NFI, we have to serialize it
            byte[] buffer = new byte[10];
            LLVMPointer ptr = LLVMManagedPointer.create(buffer).export(new Buffer(true, 10));
            serialize.serialize(value, ptr);
            return LLVM80BitFloat.fromBytes(buffer);
        } catch (UnsupportedMessageException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    @Specialization(limit = "5", guards = {"foreigns.isForeign(obj)", "interop.isNumber(foreigns.asForeign(obj))"})
    @GenerateAOT.Exclude
    protected LLVM80BitFloat fromForeign(Object obj,
                    @CachedLibrary("obj") LLVMAsForeignLibrary foreigns,
                    @CachedLibrary(limit = "3") InteropLibrary interop,
                    @Cached BranchProfile exception) {
        try {
            Object foreign = foreigns.asForeign(obj);
            if (interop.fitsInLong(foreign)) {
                return LLVM80BitFloat.fromLong(interop.asLong(foreign));
            } else if (interop.fitsInDouble(foreign)) {
                return LLVM80BitFloat.fromDouble(interop.asDouble(foreign));
            }
        } catch (UnsupportedMessageException ex) {
        }
        exception.enter();
        throw new LLVMPolyglotException(this, "Polyglot number can't be converted to double.");
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @TruffleBoundary
    static LLVM80BitFloat slowPathPrimitiveConvert(Object value) throws UnsupportedTypeException {
        try {
            if (INTEROP.fitsInLong(value)) {
                return LLVM80BitFloat.fromLong(INTEROP.asLong(value));
            } else if (INTEROP.fitsInDouble(value)) {
                return LLVM80BitFloat.fromDouble(INTEROP.asDouble(value));
            }
        } catch (UnsupportedMessageException ex) {
        }
        throw UnsupportedTypeException.create(new Object[]{value});
    }
}
