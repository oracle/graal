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
package com.oracle.truffle.llvm.spi;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.llvm.spi.ReferenceLibrary.InteropFallback;

/**
 * Library for objects that have reference semantics. Pointer equality comparisons will use that
 * library to determine if two objects are reference-equals.
 *
 * @deprecated Implement {@link InteropLibrary#isIdenticalOrUndefined} instead.
 */
@GenerateLibrary
@Deprecated
@DefaultExport(InteropFallback.class)
@SuppressWarnings("deprecation")
public abstract class ReferenceLibrary extends Library {

    /**
     * Check whether two objects are a reference to the same thing.
     *
     * All implementations of this method must be reflexive, symmetric and transitive.
     */
    @Abstract
    public abstract boolean isSame(Object receiver, Object other);

    private static final LibraryFactory<ReferenceLibrary> FACTORY = LibraryFactory.resolve(ReferenceLibrary.class);

    public static LibraryFactory<ReferenceLibrary> getFactory() {
        return FACTORY;
    }

    @ExportLibrary(value = ReferenceLibrary.class, receiverType = Object.class)
    @SuppressWarnings("deprecation")
    static class InteropFallback {

        @ExportMessage
        static boolean isSame(Object receiver, Object other,
                        @CachedLibrary("receiver") InteropLibrary receiverInterop,
                        @CachedLibrary(limit = "3") InteropLibrary otherInterop) {
            return receiverInterop.isIdentical(receiver, other, otherInterop);
        }
    }
}
