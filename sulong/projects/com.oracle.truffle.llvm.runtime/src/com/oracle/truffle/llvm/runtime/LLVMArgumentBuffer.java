/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public final class LLVMArgumentBuffer implements TruffleObject {

    private final byte[] bytes;

    public LLVMArgumentBuffer(String str) {
        this.bytes = encodeFromString(str);
    }

    public LLVMArgumentBuffer(byte[] bytes) {
        this.bytes = bytes;
    }

    @ExportMessage
    boolean hasArrayElements() {
        assert bytes != null;
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return bytes.length;
    }

    @ExportMessage
    boolean isArrayElementReadable(long idx) {
        return Long.compareUnsigned(idx, bytes.length) < 0;
    }

    @ExportMessage
    byte readArrayElement(long idx,
                    @Cached BranchProfile oob) {
        if (isArrayElementReadable(idx)) {
            return bytes[(int) idx];
        } else {
            oob.enter();
            return 0; // simulate zero-terminators
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isString() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    String asString() {
        return decodeToString(bytes);
    }

    @TruffleBoundary
    private static String decodeToString(byte[] bytes) {
        return new String(bytes);
    }

    @TruffleBoundary
    protected static byte[] encodeFromString(String str) {
        return str.getBytes();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class LLVMArgumentArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final LLVMArgumentBuffer[] args;

        LLVMArgumentArray(LLVMArgumentBuffer[] args) {
            this.args = args;
        }

        @ExportMessage
        boolean hasArrayElements() {
            assert args != null;
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return args.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return Long.compareUnsigned(idx, args.length) < 0;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Cached BranchProfile exception) {
            if (isArrayElementReadable(idx)) {
                return args[(int) idx];
            } else {
                exception.enter();
                return InvalidArrayIndexException.create(idx);
            }
        }
    }
}
