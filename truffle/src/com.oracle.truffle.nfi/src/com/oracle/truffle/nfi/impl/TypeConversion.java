/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsPointerNodeGen;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsStringNodeGen;

abstract class TypeConversion extends Node {

    protected static Node createIsNull() {
        return Message.IS_NULL.createNode();
    }

    protected static boolean checkNull(Node isNull, TruffleObject object) {
        return ForeignAccess.sendIsNull(isNull, object);
    }

    abstract static class AsPointerNode extends TypeConversion {

        abstract NativePointer execute(TruffleObject arg);

        @Specialization(guards = "checkIsPointer(isPointer, arg)")
        @SuppressWarnings("unused")
        protected NativePointer serializePointer(TruffleObject arg,
                        @Cached("createIsPointer()") Node isPointer,
                        @Cached("createAsPointer()") Node asPointer) {
            try {
                long pointer = ForeignAccess.sendAsPointer(asPointer, arg);
                return new NativePointer(pointer);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        @Specialization(guards = "checkNull(isNull, arg)")
        @SuppressWarnings("unused")
        NativePointer nullAsPointer(TruffleObject arg, @Cached("createIsNull()") Node isNull) {
            return new NativePointer(0);
        }

        @Specialization(guards = "!checkNull(isNull, arg)", replaces = "serializePointer")
        @SuppressWarnings("unused")
        protected NativePointer transitionToNative(TruffleObject arg,
                        @Cached("createIsNull()") Node isNull,
                        @Cached("createToNative()") Node toNative,
                        @Cached("createAsPointer()") Node asPointer) {
            try {
                Object nativeObj = ForeignAccess.sendToNative(toNative, arg);
                long pointer = ForeignAccess.sendAsPointer(asPointer, (TruffleObject) nativeObj);
                return new NativePointer(pointer);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(ex, new Object[]{arg});
            }
        }

        protected static boolean checkIsPointer(Node isPointer, TruffleObject object) {
            return ForeignAccess.sendIsPointer(isPointer, object);
        }

        static Node createIsPointer() {
            return Message.IS_POINTER.createNode();
        }

        static Node createAsPointer() {
            return Message.AS_POINTER.createNode();
        }

        static Node createToNative() {
            return Message.TO_NATIVE.createNode();
        }

        static AsPointerNode createRecursive() {
            return AsPointerNodeGen.create();
        }
    }

    abstract static class AsStringNode extends TypeConversion {

        final boolean acceptAnything;

        AsStringNode(boolean acceptAnything) {
            this.acceptAnything = acceptAnything;
        }

        abstract String execute(Object arg);

        @Specialization
        String stringAsString(String str) {
            return str;
        }

        @Specialization(guards = "checkNull(isNull, arg)")
        @SuppressWarnings("unused")
        String nullAsString(TruffleObject arg,
                        @Cached("createIsNull()") Node isNull) {
            return null;
        }

        @Specialization(guards = "checkIsBoxed(isBoxed, arg)")
        @SuppressWarnings("unused")
        String boxedAsString(TruffleObject arg,
                        @Cached("createIsBoxed()") Node isBoxed,
                        @Cached("createUnbox()") Node unbox,
                        @Cached("createRecursive()") AsStringNode asString) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, arg);
                return asString.execute(unboxed);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(ex, new Object[]{arg});
            }
        }

        @Specialization(guards = {"acceptAnything", "isOther(arg)"})
        @TruffleBoundary
        String otherAsString(Object arg) {
            return arg.toString();
        }

        protected static Node createIsBoxed() {
            return Message.IS_BOXED.createNode();
        }

        protected static Node createUnbox() {
            return Message.UNBOX.createNode();
        }

        protected static boolean checkIsBoxed(Node isBoxed, TruffleObject arg) {
            return ForeignAccess.sendIsBoxed(isBoxed, arg);
        }

        protected static boolean isOther(Object obj) {
            return !(obj instanceof String) && !(obj instanceof TruffleObject);
        }

        protected AsStringNode createRecursive() {
            return AsStringNodeGen.create(acceptAnything);
        }
    }
}
