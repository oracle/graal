/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
                        @Cached("createRecursive()") AsPointerNode recursive) {
            try {
                Object nativeObj = ForeignAccess.sendToNative(toNative, arg);
                return recursive.execute((TruffleObject) nativeObj);
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
