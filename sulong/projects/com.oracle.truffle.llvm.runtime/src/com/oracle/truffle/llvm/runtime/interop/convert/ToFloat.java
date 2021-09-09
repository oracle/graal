/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;

@GenerateUncached
public abstract class ToFloat extends ForeignToLLVM {

    @Specialization
    protected float fromInt(int value) {
        return value;
    }

    @Specialization
    protected float fromLong(long value) {
        return value;
    }

    @Specialization
    protected float fromChar(char value) {
        return value;
    }

    @Specialization
    protected float fromShort(short value) {
        return value;
    }

    @Specialization
    protected float fromByte(byte value) {
        return value;
    }

    @Specialization
    protected float fromFloat(float value) {
        return value;
    }

    @Specialization
    protected float fromDouble(double value) {
        return (float) value;
    }

    @Specialization
    protected float fromBoolean(boolean value) {
        return (value ? 1.0f : 0.0f);
    }

    @Specialization
    protected float fromString(String value,
                    @Cached BranchProfile exception) {
        return getSingleStringCharacter(value, exception);
    }

    @Specialization(limit = "5", guards = {"foreigns.isForeign(obj)", "interop.isNumber(foreigns.asForeign(obj))"})
    @GenerateAOT.Exclude
    protected float fromForeign(Object obj,
                    @CachedLibrary("obj") LLVMAsForeignLibrary foreigns,
                    @CachedLibrary(limit = "3") InteropLibrary interop,
                    @Cached BranchProfile exception) {
        try {
            return interop.asFloat(foreigns.asForeign(obj));
        } catch (UnsupportedMessageException ex) {
            exception.enter();
            throw new LLVMPolyglotException(this, "Polyglot number can't be converted to float.");
        }
    }

    @TruffleBoundary
    static float slowPathPrimitiveConvert(ForeignToLLVM thiz, Object value) throws UnsupportedTypeException {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else if (value instanceof Boolean) {
            return (boolean) value ? 1.0f : 0.0f;
        } else if (value instanceof Character) {
            return (char) value;
        } else if (value instanceof String) {
            return thiz.getSingleStringCharacter((String) value, BranchProfile.getUncached());
        } else {
            try {
                return InteropLibrary.getFactory().getUncached().asFloat(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }
}
