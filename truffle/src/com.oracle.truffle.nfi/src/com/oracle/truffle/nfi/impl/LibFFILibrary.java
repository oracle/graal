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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
final class LibFFILibrary implements TruffleObject {

    private static final EmptyKeysArray KEYS = new EmptyKeysArray();

    protected final long handle;

    static LibFFILibrary createDefault() {
        return new LibFFILibrary(0);
    }

    static LibFFILibrary create(long handle) {
        assert handle != 0;
        LibFFILibrary ret = new LibFFILibrary(handle);
        NativeAllocation.getGlobalQueue().registerNativeAllocation(ret, new Destructor(handle));
        return ret;
    }

    private LibFFILibrary(long handle) {
        this.handle = handle;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return KEYS;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberReadable(@SuppressWarnings("unused") String member) {
        return true;
    }

    @ExportMessage
    @ImportStatic(NFILanguageImpl.class)
    abstract static class ReadMember {

        @Specialization(limit = "3", guards = {"receiver == cachedReceiver", "symbol.equals(cachedSymbol)"})
        @SuppressWarnings("unused")
        static Object doCached(LibFFILibrary receiver, String symbol,
                        @Cached("receiver") LibFFILibrary cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @CachedContext(NFILanguageImpl.class) ContextReference<NFIContext> ctxRef,
                        @Cached("lookupCached(cachedReceiver, cachedSymbol, ctxRef)") Object cachedRet) {
            return cachedRet;
        }

        static Object lookupCached(LibFFILibrary receiver, String symbol, ContextReference<NFIContext> ctxRef) throws UnknownIdentifierException {
            return doGeneric(receiver, symbol, BranchProfile.getUncached(), ctxRef.get());
        }

        @Specialization(replaces = "doCached")
        static Object doGeneric(LibFFILibrary receiver, String symbol,
                        @Cached BranchProfile exception,
                        @CachedContext(NFILanguageImpl.class) NFIContext ctx) throws UnknownIdentifierException {
            try {
                return ctx.lookupSymbol(receiver, symbol);
            } catch (UnsatisfiedLinkError ex) {
                exception.enter();
                throw UnknownIdentifierException.create(symbol);
            }
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return NFILanguageImpl.class;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "LibFFILibrary(" + handle + ")";
    }

    private static final class Destructor extends NativeAllocation.Destructor {

        private final long handle;

        private Destructor(long handle) {
            this.handle = handle;
        }

        @Override
        protected void destroy() {
            NFIContext.freeLibrary(handle);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class EmptyKeysArray implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 0;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isArrayElementReadable(@SuppressWarnings("unused") long index) {
            return false;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(index);
        }
    }
}
