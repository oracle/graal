/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
final class FunctionsObject implements TruffleObject {

    static final int LIMIT = 3;
    final Map<TruffleString, SLFunction> functions = new HashMap<>();

    FunctionsObject() {
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return SLLanguage.class;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    static class ReadMember {

        @Specialization
        static Object read(@SuppressWarnings("unused") FunctionsObject fo, FunctionMemberObject member) {
            return member.getFunction();
        }

        @Specialization(guards = "memberLibrary.isString(member)", limit = "LIMIT")
        static Object read(FunctionsObject fo, Object member,
                        @CachedLibrary("member") InteropLibrary memberLibrary,
                        @Bind("$node") Node node, @Cached InlinedBranchProfile error) throws UnknownMemberException {
            TruffleString name = FunctionsObject.toString(member, memberLibrary);
            Object value = doRead(fo, name);
            if (value != null) {
                return value;
            }
            error.enter(node);
            throw UnknownMemberException.create(name);
        }

        @Fallback
        static Object read(@SuppressWarnings("unused") FunctionsObject fo, Object member) throws UnknownMemberException {
            throw UnknownMemberException.create(member);
        }

        @TruffleBoundary
        static Object doRead(FunctionsObject fo, TruffleString name) {
            return fo.functions.get(name);
        }
    }

    @ExportMessage
    static class IsMemberReadable {

        @Specialization
        @SuppressWarnings("unused")
        static boolean isReadable(FunctionsObject fo, FunctionMemberObject member) {
            return true;
        }

        @Specialization(guards = "memberLibrary.isString(member)", limit = "LIMIT")
        static boolean isReadable(FunctionsObject fo, Object member,
                        @CachedLibrary("member") InteropLibrary memberLibrary) {
            TruffleString name = FunctionsObject.toString(member, memberLibrary);
            return doIsReadable(fo, name);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean isReadable(FunctionsObject fo, Object member) {
            return false;
        }

        @TruffleBoundary
        static boolean doIsReadable(FunctionsObject fo, TruffleString name) {
            return fo.functions.containsKey(name);
        }
    }

    private static TruffleString toString(Object member, InteropLibrary memberLibrary) {
        assert memberLibrary.isString(member) : member;
        try {
            return memberLibrary.asTruffleString(member);
        } catch (UnsupportedMessageException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    @ExportMessage
    Object getMemberObjects() {
        return new FunctionNamesObject(functions);
    }

    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject() {
        return SLType.OBJECT;
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "global";
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof FunctionsObject;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class FunctionNamesObject implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final TruffleString[] names;
        @CompilationFinal(dimensions = 1) private final SLFunction[] functions;

        @TruffleBoundary
        FunctionNamesObject(Map<TruffleString, SLFunction> functions) {
            this.names = new TruffleString[functions.size()];
            this.functions = new SLFunction[functions.size()];
            int i = 0;
            for (Map.Entry<TruffleString, SLFunction> entry : functions.entrySet()) {
                this.names[i] = entry.getKey();
                this.functions[i] = entry.getValue();
                i++;
            }
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < names.length;
        }

        @ExportMessage
        long getArraySize() {
            return names.length;
        }

        @ExportMessage
        Object readArrayElement(long index, @Bind("$node") Node node, @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            return new FunctionMemberObject(names[(int) index], functions[(int) index]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class FunctionMemberObject implements TruffleObject {

        private final TruffleString name;
        private final SLFunction function;

        FunctionMemberObject(TruffleString name, SLFunction function) {
            this.name = name;
            this.function = function;
        }

        @ExportMessage
        boolean isMember() {
            return true;
        }

        @ExportMessage
        Object getMemberSimpleName() {
            return name;
        }

        @ExportMessage
        Object getMemberQualifiedName() {
            return name;
        }

        @ExportMessage
        boolean isMemberKindField() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMethod() {
            return true;
        }

        @ExportMessage
        boolean isMemberKindMetaObject() {
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FunctionMemberObject other = (FunctionMemberObject) obj;
            return Objects.equals(this.name, other.name);
        }

        TruffleString getName() {
            return name;
        }

        SLFunction getFunction() {
            return function;
        }
    }
}
