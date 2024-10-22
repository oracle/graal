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
package com.oracle.truffle.nfi;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@ExportLibrary(InteropLibrary.class)
final class NFILibrary implements TruffleObject {

    final Object library;
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
    Symbols getMemberObjects() {
        return new Symbols(symbols.keySet().toArray(new String[]{}));
    }

    @ExportMessage
    static class IsMemberReadable {

        @Specialization(limit = "3")
        static boolean isReadable(NFILibrary receiver, SymbolMember member,
                        @CachedLibrary("receiver.getLibrary()") InteropLibrary recursive) {
            Object symbol = member.getMemberSimpleName();
            return recursive.isMemberReadable(receiver.getLibrary(), symbol);
        }

        @Specialization(guards = "interop.isString(member)", limit = "3")
        static boolean isReadable(NFILibrary receiver, Object member,
                        @SuppressWarnings("unused") @Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop,
                        @CachedLibrary("receiver.getLibrary()") InteropLibrary recursive) {
            return recursive.isMemberReadable(receiver.getLibrary(), member);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean isReadable(NFILibrary receiver, Object unknownObj) {
            return false;
        }
    }

    @ExportMessage
    static class ReadMember {

        @Specialization(limit = "3")
        static Object read(NFILibrary receiver, SymbolMember member,
                        @Shared("simpleNameLibrary") @CachedLibrary(limit = "2") InteropLibrary simpleNameLibrary,
                        @CachedLibrary("receiver.getLibrary()") InteropLibrary recursive) throws UnsupportedMessageException, UnknownMemberException {
            Object symbol = member.getMemberSimpleName();
            String symbolName = simpleNameLibrary.asString(symbol);
            Object preBound = receiver.findSymbol(symbolName);
            if (preBound != null) {
                return preBound;
            } else {
                return recursive.readMember(receiver.getLibrary(), symbol);
            }
        }

        @Specialization(guards = "interop.isString(member)", limit = "3")
        static Object read(NFILibrary receiver, Object member,
                        @Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop,
                        @CachedLibrary("receiver.getLibrary()") InteropLibrary recursive) throws UnsupportedMessageException, UnknownMemberException {
            String symbolName = interop.asString(member);
            Object preBound = receiver.findSymbol(symbolName);
            if (preBound != null) {
                return preBound;
            } else {
                return recursive.readMember(receiver.getLibrary(), member);
            }
        }

        @Fallback
        static Object read(@SuppressWarnings("unused") NFILibrary receiver, Object unknownObj) throws UnknownMemberException {
            throw UnknownMemberException.create(unknownObj);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInvocable(@SuppressWarnings("unused") Object symbol) {
        return true; // avoid expensive truffle boundary
    }

    @ExportMessage
    static class InvokeMember {

        @Specialization
        static Object invoke(NFILibrary receiver, SymbolMember member, Object[] args,
                        @Shared("simpleNameLibrary") @CachedLibrary(limit = "2") InteropLibrary simpleNameLibrary,
                        @Bind("$node") Node node,
                        @Shared("executables") @CachedLibrary(limit = "3") InteropLibrary executables,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws UnsupportedMessageException, UnknownMemberException, ArityException, UnsupportedTypeException {
            Object symbol = member.getMemberSimpleName();
            String symbolName = simpleNameLibrary.asString(symbol);
            Object preBound = receiver.findSymbol(symbolName);
            if (preBound == null) {
                exception.enter(node);
                throw UnknownMemberException.create(member);
            }
            return executables.execute(preBound, args);
        }

        @Specialization(guards = "interop.isString(member)")
        static Object invoke(NFILibrary receiver, Object member, Object[] args,
                        @Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop,
                        @Bind("$node") Node node,
                        @Shared("executables") @CachedLibrary(limit = "3") InteropLibrary executables,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws UnsupportedMessageException, UnknownMemberException, ArityException, UnsupportedTypeException {
            String symbolName = interop.asString(member);
            Object preBound = receiver.findSymbol(symbolName);
            if (preBound == null) {
                exception.enter(node);
                throw UnknownMemberException.create(member);
            }
            return executables.execute(preBound, args);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object invoke(NFILibrary receiver, Object unknownObj, Object[] args) throws UnknownMemberException {
            throw UnknownMemberException.create(unknownObj);
        }
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

    @ExportMessage
    boolean isPointer(@CachedLibrary("this.library") InteropLibrary interop) {
        return interop.isPointer(this.library);
    }

    @ExportMessage
    long asPointer(@CachedLibrary("this.library") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asPointer(this.library);
    }

    @ExportMessage
    void toNative(@CachedLibrary("this.library") InteropLibrary interop) {
        interop.toNative(this.library);
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class SymbolMember implements TruffleObject {

        private final String symbol;

        SymbolMember(String symbol) {
            this.symbol = symbol;
        }

        @ExportMessage
        boolean isMember() {
            return true;
        }

        @ExportMessage
        Object getMemberSimpleName() {
            return symbol;
        }

        @ExportMessage
        Object getMemberQualifiedName() {
            return symbol;
        }

        @ExportMessage
        boolean isMemberKindField() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMethod() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMetaObject() {
            return false;
        }

        String getSymbol() {
            return symbol;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Symbols implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final String[] symbols;

        Symbols(String... keys) {
            this.symbols = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return symbols.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < symbols.length;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Bind("$node") Node node,
                        @Cached InlinedBranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter(node);
                throw InvalidArrayIndexException.create(idx);
            }
            return new SymbolMember(symbols[(int) idx]);
        }

        @ExportMessage
        static boolean hasLanguage(@SuppressWarnings("unused") Symbols receiver) {
            return true;
        }

        @ExportMessage
        static Class<? extends TruffleLanguage<?>> getLanguage(@SuppressWarnings("unused") Symbols receiver) {
            return NFILanguage.class;
        }

        @ExportMessage
        static Object toDisplayString(@SuppressWarnings("unused") Symbols receiver, @SuppressWarnings("unused") boolean allowSideEffects) {
            return "Native Members";
        }
    }
}
