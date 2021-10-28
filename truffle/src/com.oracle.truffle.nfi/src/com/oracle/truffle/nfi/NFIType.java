/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.api.SignatureLibrary;

final class NFIType {

    final TypeCachedState cachedState;
    final Object backendType;

    final Object runtimeData;

    NFIType(TypeCachedState cachedState, Object backendType) {
        this(cachedState, backendType, null);
    }

    NFIType(TypeCachedState cachedState, Object backendType, Object runtimeData) {
        this.cachedState = cachedState;
        this.backendType = backendType;
        this.runtimeData = runtimeData;
    }

    abstract static class TypeCachedState {

        final int managedArgCount;

        TypeCachedState(int managedArgCount) {
            this.managedArgCount = managedArgCount;
        }
    }

    static final TypeCachedState SIMPLE = new SimpleTypeCachedState();
    static final TypeCachedState CLOSURE = new ClosureTypeCachedState();
    static final TypeCachedState INJECTED = new InjectedTypeCachedState();

    @ExportLibrary(value = NFITypeLibrary.class, useForAOT = true, useForAOTPriority = 0)
    static final class SimpleTypeCachedState extends TypeCachedState {

        private SimpleTypeCachedState() {
            super(1);
            // singleton
        }

        @ExportMessage
        Object convertToNative(NFIType type, Object value) {
            assert type.cachedState == this;
            return value;
        }

        @ExportMessage
        Object convertFromNative(NFIType type, Object value) {
            assert type.cachedState == this;
            return value;
        }
    }

    @ExportLibrary(value = NFITypeLibrary.class, useForAOT = true, useForAOTPriority = 0)
    static final class ClosureTypeCachedState extends TypeCachedState {

        private ClosureTypeCachedState() {
            super(1);
            // singleton
        }

        @ExportMessage
        static class ConvertToNative {

            @Specialization(limit = "3", guards = "interop.isExecutable(value)")
            @GenerateAOT.Exclude
            static Object convertToNative(ClosureTypeCachedState state, NFIType type, Object value,
                            @SuppressWarnings("unused") @CachedLibrary("value") InteropLibrary interop,
                            @CachedLibrary("type.runtimeData") SignatureLibrary library) {
                assert type.cachedState == state;
                return library.createClosure(type.runtimeData, value);
            }

            @Fallback
            static Object convertToNative(ClosureTypeCachedState state, NFIType type, Object value) {
                // it's not executable, so assume it's already a function pointer
                assert type.cachedState == state;
                return value;
            }
        }

        @ExportMessage
        static class ConvertFromNative {

            @Specialization(limit = "3", guards = "interop.isNull(nullValue)")
            @GenerateAOT.Exclude
            static Object doNull(ClosureTypeCachedState state, NFIType type, Object nullValue,
                            @SuppressWarnings("unused") @CachedLibrary("nullValue") InteropLibrary interop) {
                assert type.cachedState == state;
                return nullValue;
            }

            @Specialization(limit = "3", guards = "!interop.isNull(value)")
            @GenerateAOT.Exclude
            static Object doBind(ClosureTypeCachedState state, NFIType type, Object value,
                            @SuppressWarnings("unused") @CachedLibrary("value") InteropLibrary interop,
                            @CachedLibrary("type.runtimeData") SignatureLibrary library) {
                assert type.cachedState == state;
                return library.bind(type.runtimeData, value);
            }
        }
    }

    @ExportLibrary(value = NFITypeLibrary.class, useForAOT = true, useForAOTPriority = 0)
    static final class InjectedTypeCachedState extends TypeCachedState {

        private InjectedTypeCachedState() {
            super(0);
        }

        @ExportMessage
        Object convertFromNative(NFIType type, @SuppressWarnings("unused") Object value) {
            assert type.cachedState == this;
            return null;
        }

        @ExportMessage
        Object convertToNative(NFIType type, Object value) {
            assert type.cachedState == this && value == null;
            return type.runtimeData;
        }
    }
}
