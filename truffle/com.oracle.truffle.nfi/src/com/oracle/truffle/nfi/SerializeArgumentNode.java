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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;

abstract class SerializeArgumentNode extends Node {

    abstract Object execute(NativeArgumentBuffer buffer, Object arg);

    protected static Node createIsNull() {
        return Message.IS_NULL.createNode();
    }

    protected static boolean checkNull(Node isNull, TruffleObject object) {
        return ForeignAccess.sendIsNull(isNull, object);
    }

    abstract static class SerializeUnboxingArgumentNode extends SerializeArgumentNode {

        protected final LibFFIType argType;

        SerializeUnboxingArgumentNode(LibFFIType argType) {
            this.argType = argType;
        }

        @SuppressWarnings("unused")
        protected boolean isSpecialized(TruffleObject arg) {
            return false;
        }

        @Specialization(guards = {"!isSpecialized(arg)", "checkNull(isNull, arg)"})
        @SuppressWarnings("unused")
        protected Object serializeNull(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsNull()") Node isNull) {
            argType.serialize(buffer, null);
            return null;
        }

        @Specialization(guards = {"!isSpecialized(arg)", "!checkNull(isNull, arg)"})
        @SuppressWarnings("unused")
        protected Object serializeUnbox(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsNull()") Node isNull,
                        @Cached("createUnbox()") Node unbox,
                        @Cached("argType.createSerializeArgumentNode()") SerializeArgumentNode serialize) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, arg);
                serialize.execute(buffer, unboxed);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.raise(ex, new Object[]{arg});
            }
            return null;
        }

        protected static Node createUnbox() {
            return Message.UNBOX.createNode();
        }
    }

    abstract static class SerializeSimpleArgumentNode extends SerializeUnboxingArgumentNode {

        SerializeSimpleArgumentNode(LibFFIType argType) {
            super(argType);
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeByte(NativeArgumentBuffer buffer, byte arg) {
            argType.serialize(buffer, arg);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeBoolean(NativeArgumentBuffer buffer, boolean arg) {
            argType.serialize(buffer, arg);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeShort(NativeArgumentBuffer buffer, short arg) {
            argType.serialize(buffer, arg);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeChar(NativeArgumentBuffer buffer, char arg) {
            argType.serialize(buffer, arg);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeInt(NativeArgumentBuffer buffer, int arg) {
            argType.serialize(buffer, arg);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeLong(NativeArgumentBuffer buffer, long arg) {
            argType.serialize(buffer, arg);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeFloat(NativeArgumentBuffer buffer, float arg) {
            argType.serialize(buffer, arg);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeDouble(NativeArgumentBuffer buffer, double arg) {
            argType.serialize(buffer, arg);
            return null;
        }
    }

    abstract static class SerializePointerArgumentNode extends SerializeSimpleArgumentNode {

        SerializePointerArgumentNode(LibFFIType type) {
            super(type);
        }

        @Override
        protected boolean isSpecialized(TruffleObject arg) {
            return arg instanceof NativeString || arg instanceof NativePointer;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeNativeString(NativeArgumentBuffer buffer, NativeString string) {
            argType.serialize(buffer, string);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeNativePointer(NativeArgumentBuffer buffer, NativePointer ptr) {
            argType.serialize(buffer, ptr);
            return null;
        }
    }

    abstract static class SerializeStringArgumentNode extends SerializeUnboxingArgumentNode {

        SerializeStringArgumentNode(LibFFIType type) {
            super(type);
        }

        @Override
        protected boolean isSpecialized(TruffleObject arg) {
            return arg instanceof NativeString;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeString(NativeArgumentBuffer buffer, String string) {
            argType.serialize(buffer, string);
            return null;
        }

        @Specialization(insertBefore = "serializeNull")
        protected Object serializeNativeString(NativeArgumentBuffer buffer, NativeString string) {
            argType.serialize(buffer, string);
            return null;
        }
    }

    static class SerializeObjectArgumentNode extends SerializeArgumentNode {

        private final LibFFIType argType;

        SerializeObjectArgumentNode(LibFFIType argType) {
            this.argType = argType;
        }

        @Override
        Object execute(NativeArgumentBuffer buffer, Object object) {
            argType.serialize(buffer, object);
            return null;
        }
    }

    abstract static class SerializeArrayArgumentNode extends SerializeArgumentNode {

        final LibFFIType.ArrayType argType;

        SerializeArrayArgumentNode(LibFFIType.ArrayType argType) {
            this.argType = argType;
        }

        @Specialization(guards = "checkNull(isNull, arg)")
        @SuppressWarnings("unused")
        protected Object serializeNull(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsNull()") Node isNull) {
            argType.serialize(buffer, null);
            return null;
        }

        @Specialization(guards = "isJavaObject(arrayType, object)")
        protected Object serializeArray(NativeArgumentBuffer buffer, TruffleObject object, @Cached("argType.getArrayType(object)") Class<?> arrayType) {
            argType.serialize(buffer, JavaInterop.asJavaObject(arrayType, object));
            return null;
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object error(NativeArgumentBuffer buffer, Object object) {
            throw UnsupportedTypeException.raise(new Object[]{object});
        }

        protected static boolean isJavaObject(Class<?> arrayType, TruffleObject object) {
            if (arrayType != null) {
                return JavaInterop.isJavaObject(arrayType, object);
            } else {
                return false;
            }
        }
    }

    abstract static class SerializeClosureArgumentNode extends SerializeArgumentNode {

        private final LibFFIType argType;
        private final LibFFISignature signature;

        SerializeClosureArgumentNode(LibFFIType argType, LibFFISignature signature) {
            this.argType = argType;
            this.signature = signature;
        }

        @Specialization(limit = "5", guards = "object == cachedObject")
        @SuppressWarnings("unused")
        protected Object serializeCached(NativeArgumentBuffer buffer, TruffleObject object,
                        @Cached("object") TruffleObject cachedObject,
                        @Cached("createClosure(object)") LibFFIClosure closure) {
            argType.serialize(buffer, closure);
            return null;
        }

        @Specialization
        protected Object serializeFallback(NativeArgumentBuffer buffer, TruffleObject object) {
            argType.serialize(buffer, createClosure(object));
            return null;
        }

        @TruffleBoundary
        protected LibFFIClosure createClosure(TruffleObject object) {
            return LibFFIClosure.create(signature, object);
        }
    }
}
