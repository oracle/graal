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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import java.util.HashMap;
import java.util.Map;

@ExportLibrary(InteropLibrary.class)
final class NFILibrary implements TruffleObject {

    private final Object library;
    private final Map<String, Object> symbols;

    @TruffleBoundary
    NFILibrary(Object library) {
        this.library = library;
        this.symbols = new HashMap<>();
    }

    Object getLibrary() {
        return library;
    }

    @TruffleBoundary
    Object findSymbol(String name) {
        return symbols.get(name);
    }

    @TruffleBoundary
    void preBindSymbol(String name, Object symbol) {
        symbols.put(name, symbol);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Keys getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new Keys(symbols.keySet().toArray());
    }

    @ExportMessage(limit = "3")
    boolean isMemberReadable(String symbol,
                    @CachedLibrary("this.getLibrary()") InteropLibrary recursive) {
        // no need to check the map, pre-bound symbols need to exist in the library, too
        return recursive.isMemberReadable(getLibrary(), symbol);
    }

    @ExportMessage(limit = "3")
    Object readMember(String symbol,
                    @CachedLibrary("this.getLibrary()") InteropLibrary recursive) throws UnsupportedMessageException, UnknownIdentifierException {
        Object preBound = findSymbol(symbol);
        if (preBound != null) {
            return preBound;
        } else {
            return recursive.readMember(getLibrary(), symbol);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInvocable(@SuppressWarnings("unused") String symbol) {
        return true; // avoid expensive truffle boundary
    }

    @ExportMessage
    Object invokeMember(String symbol, Object[] args,
                    @CachedLibrary(limit = "3") InteropLibrary executables,
                    @Cached BranchProfile exception) throws UnknownIdentifierException, ArityException, UnsupportedTypeException, UnsupportedMessageException {
        Object preBound = findSymbol(symbol);
        if (preBound == null) {
            exception.enter();
            throw UnknownIdentifierException.create(symbol);
        }
        return executables.execute(preBound, args);
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean hasLanguage(NFILibrary lib) {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static Class<? extends TruffleLanguage<?>> getLanguage(NFILibrary receiver) {
        return NFILanguage.class;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static Object toDisplayString(NFILibrary receiver, boolean allowSideEffects) {
        return "Native Library";
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Keys implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final Object[] keys;

        Keys(Object... keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < keys.length;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
            return keys[(int) idx];
        }

        @ExportMessage
        static boolean hasLanguage(@SuppressWarnings("unused") Keys receiver) {
            return true;
        }

        @ExportMessage
        static Class<? extends TruffleLanguage<?>> getLanguage(@SuppressWarnings("unused") Keys receiver) {
            return NFILanguage.class;
        }

        @ExportMessage
        static Object toDisplayString(@SuppressWarnings("unused") Keys receiver, @SuppressWarnings("unused") boolean allowSideEffects) {
            return "Native Members";
        }
    }
}
