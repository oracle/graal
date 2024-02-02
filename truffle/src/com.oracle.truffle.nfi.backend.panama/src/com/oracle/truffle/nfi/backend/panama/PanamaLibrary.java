/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.panama;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.Optional;

@ExportLibrary(InteropLibrary.class)
final class PanamaLibrary implements TruffleObject {

    private static final EmptyKeysArray KEYS = new EmptyKeysArray();

    private final @SuppressWarnings("preview") SymbolLookup library;

    static PanamaLibrary createDefault() {
        @SuppressWarnings("preview")
        SymbolLookup lookup = Linker.nativeLinker().defaultLookup();
        return new PanamaLibrary(lookup);
    }

    static PanamaLibrary create(@SuppressWarnings("preview") SymbolLookup library) {
        assert library != null;
        return new PanamaLibrary(library);
    }

    private PanamaLibrary(@SuppressWarnings("preview") SymbolLookup library) {
        this.library = library;
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

    @TruffleBoundary
    @SuppressWarnings("preview")
    Optional<MemorySegment> doLookup(String name) {
        return library.find(name);
    }

    @ExportMessage
    Object readMember(String symbol,
                    @Bind("$node") Node node,
                    @Cached InlinedBranchProfile exception) throws UnknownIdentifierException {
        @SuppressWarnings("preview")
        Optional<MemorySegment> ret = doLookup(symbol);
        if (ret.isEmpty()) {
            exception.enter(node);
            throw UnknownIdentifierException.create(symbol);
        }
        return new PanamaSymbol(ret.get());
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return PanamaNFILanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "PanamaLibrary(" + library + ")";
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
