/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.nfi.api.SerializableLibrary;

@GenerateUncached
public abstract class ToFP128 extends ForeignToLLVM {

    @Specialization
    protected LLVM128BitFloat fromInt(int value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromChar(char value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromByte(byte value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromShort(short value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromFloat(float value) {
        return LLVM128BitFloat.fromFloat(value);
    }

    @Specialization
    protected LLVM128BitFloat fromLong(long value) {
        return LLVM128BitFloat.fromLong(value);
    }

    @Specialization
    protected LLVM128BitFloat fromDouble(double value) {
        return LLVM128BitFloat.fromDouble(value);
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @Specialization(limit = "1", guards = "serialize.isSerializable(value)")
    protected LLVM128BitFloat from128BitFloat(Object value,
                    @CachedLibrary("value") SerializableLibrary serialize) {
        try {
            // this is an FP128 value from the NFI, we have to serialize it
            byte[] buffer = new byte[16];
            LLVMPointer ptr = LLVMManagedPointer.create(buffer).export(new LLVMInteropType.Buffer(true, 16));
            serialize.serialize(value, ptr);
            return LLVM128BitFloat.fromBytes(buffer);
        } catch (UnsupportedMessageException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    @CompilerDirectives.TruffleBoundary
    static LLVM128BitFloat slowPathPrimitiveConvert(Object value) throws UnsupportedTypeException {
        try {
            if (INTEROP.fitsInLong(value)) {
                return LLVM128BitFloat.fromLong(INTEROP.asLong(value));
            } else if (INTEROP.fitsInDouble(value)) {
                return LLVM128BitFloat.fromDouble(INTEROP.asDouble(value));
            }
        } catch (UnsupportedMessageException ex) {
        }
        throw UnsupportedTypeException.create(new Object[]{value});
    }

}
