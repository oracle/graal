/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.nfi.NFIType.TypeCachedState;
import com.oracle.truffle.nfi.SignatureTypeCachedStateFactory.ClosureToNativeFactory;
import com.oracle.truffle.nfi.SignatureTypeCachedStateFactory.FunctionPtrFromNativeFactory;
import com.oracle.truffle.nfi.api.SignatureLibrary;

final class SignatureTypeCachedState {

    static final TypeCachedState INSTANCE = new TypeCachedState(1, ClosureToNativeFactory.getInstance(), FunctionPtrFromNativeFactory.getInstance());

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class ClosureToNative extends ConvertTypeNode {

        @Specialization(limit = "3", guards = "interop.isExecutable(value)")
        @GenerateAOT.Exclude
        static Object convertToNative(NFIType type, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("value") InteropLibrary interop,
                        @CachedLibrary("type.runtimeData") SignatureLibrary library) {
            return library.createClosure(type.runtimeData, value);
        }

        @Fallback
        static Object convertToNative(@SuppressWarnings("unused") NFIType type, Object value) {
            // it's not executable, so assume it's already a function pointer
            return value;
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class FunctionPtrFromNative extends ConvertTypeNode {

        @Specialization(guards = "nullValue == 0")
        @SuppressWarnings("unused")
        static Object doNull(NFIType type, long nullValue) {
            return NFIPointer.nullPtr();
        }

        @Specialization(guards = "nullValue == null")
        @SuppressWarnings("unused")
        static Object doNull(NFIType type, Object nullValue) {
            return NFIPointer.nullPtr();
        }

        @Specialization(limit = "3", guards = "interop.isNull(nullValue)")
        @GenerateAOT.Exclude
        static Object doNull(@SuppressWarnings("unused") NFIType type, Object nullValue,
                        @SuppressWarnings("unused") @CachedLibrary("nullValue") InteropLibrary interop) {
            return nullValue;
        }

        @Specialization(limit = "3", guards = "ptr != 0")
        @GenerateAOT.Exclude
        static Object doBind(NFIType type, long ptr,
                        @CachedLibrary("type.runtimeData") SignatureLibrary library) {
            return library.bind(type.runtimeData, NFIPointer.create(ptr));
        }

        @Specialization(limit = "3", guards = "!interop.isNull(value)")
        @GenerateAOT.Exclude
        static Object doBind(NFIType type, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("value") InteropLibrary interop,
                        @CachedLibrary("type.runtimeData") SignatureLibrary library) {
            return library.bind(type.runtimeData, value);
        }
    }
}
