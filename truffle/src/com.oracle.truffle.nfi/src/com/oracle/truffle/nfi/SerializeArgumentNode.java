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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
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

    /**
     * @return whether the argument was consumed
     */
    abstract boolean execute(NativeArgumentBuffer buffer, Object arg);

    protected static Node createIsNull() {
        return Message.IS_NULL.createNode();
    }

    protected static Node createIsPointer() {
        return Message.IS_POINTER.createNode();
    }

    protected static Node createAsPointer() {
        return Message.AS_POINTER.createNode();
    }

    protected static boolean checkIsPointer(Node isPointer, TruffleObject object) {
        return ForeignAccess.sendIsPointer(isPointer, object);
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
        protected boolean serializeNull(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsNull()") Node isNull) {
            argType.serialize(buffer, null);
            return true;
        }

        @Specialization(guards = {"!isSpecialized(arg)", "checkIsPointer(isPointer, arg)"})
        @SuppressWarnings("unused")
        protected boolean serializePointer(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsPointer()") Node isPointer,
                        @Cached("createAsPointer()") Node asPointer,
                        @Cached("argType.createSerializeArgumentNode()") SerializeArgumentNode serialize) {
            try {
                long pointer = ForeignAccess.sendAsPointer(asPointer, arg);
                return serialize.execute(buffer, new NativePointer(pointer));
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(ex, new Object[]{arg});
            }
        }

        @Specialization(guards = {"!isSpecialized(arg)", "!checkNull(isNull, arg)", "!checkIsPointer(isPointer, arg)"})
        @SuppressWarnings("unused")
        protected boolean serializeUnbox(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsPointer()") Node isPointer,
                        @Cached("createIsNull()") Node isNull,
                        @Cached("createUnbox()") Node unbox,
                        @Cached("argType.createSerializeArgumentNode()") SerializeArgumentNode serialize) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, arg);
                return serialize.execute(buffer, unboxed);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(ex, new Object[]{arg});
            }
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
        protected boolean serializeByte(NativeArgumentBuffer buffer, byte arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeBoolean(NativeArgumentBuffer buffer, boolean arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeShort(NativeArgumentBuffer buffer, short arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeChar(NativeArgumentBuffer buffer, char arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeInt(NativeArgumentBuffer buffer, int arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeLong(NativeArgumentBuffer buffer, long arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeFloat(NativeArgumentBuffer buffer, float arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeDouble(NativeArgumentBuffer buffer, double arg) {
            argType.serialize(buffer, arg);
            return true;
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
        protected boolean serializeNativeString(NativeArgumentBuffer buffer, NativeString string) {
            argType.serialize(buffer, string);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeNativePointer(NativeArgumentBuffer buffer, NativePointer ptr) {
            argType.serialize(buffer, ptr);
            return true;
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
        protected boolean serializeString(NativeArgumentBuffer buffer, String string) {
            argType.serialize(buffer, string);
            return true;
        }

        @Specialization(insertBefore = "serializeNull")
        protected boolean serializeNativeString(NativeArgumentBuffer buffer, NativeString string) {
            argType.serialize(buffer, string);
            return true;
        }
    }

    static class SerializeObjectArgumentNode extends SerializeArgumentNode {

        private final LibFFIType argType;

        SerializeObjectArgumentNode(LibFFIType argType) {
            this.argType = argType;
        }

        @Override
        boolean execute(NativeArgumentBuffer buffer, Object object) {
            argType.serialize(buffer, object);
            return true;
        }
    }

    abstract static class SerializeArrayArgumentNode extends SerializeArgumentNode {

        final LibFFIType.ArrayType argType;

        SerializeArrayArgumentNode(LibFFIType.ArrayType argType) {
            this.argType = argType;
        }

        @Specialization(guards = "checkNull(isNull, arg)")
        @SuppressWarnings("unused")
        protected boolean serializeNull(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsNull()") Node isNull) {
            argType.serialize(buffer, null);
            return true;
        }

        @Specialization(guards = "isJavaObject(arrayType, object)")
        protected boolean serializeArray(NativeArgumentBuffer buffer, TruffleObject object, @Cached("argType.getArrayType(object)") Class<?> arrayType) {
            argType.serialize(buffer, JavaInterop.asJavaObject(arrayType, object));
            return true;
        }

        @Fallback
        @SuppressWarnings("unused")
        protected boolean error(NativeArgumentBuffer buffer, Object object) {
            CompilerDirectives.transferToInterpreter();
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

        private final ContextReference<NFIContext> ctxRef;

        SerializeClosureArgumentNode(LibFFIType argType, LibFFISignature signature) {
            this.argType = argType;
            this.signature = signature;
            this.ctxRef = NFILanguage.getCurrentContextReference();
        }

        protected boolean isSpecialized(TruffleObject arg) {
            return arg instanceof NativePointer;
        }

        @Specialization
        protected boolean serializeNativePointer(NativeArgumentBuffer buffer, NativePointer object) {
            argType.serialize(buffer, object);
            return true;
        }

        @Specialization(limit = "5", guards = {"!isSpecialized(object)", "object == cachedObject"})
        @SuppressWarnings("unused")
        protected boolean serializeCached(NativeArgumentBuffer buffer, TruffleObject object,
                        @Cached("object") TruffleObject cachedObject,
                        @Cached("createClosure(object)") LibFFIClosure closure) {
            argType.serialize(buffer, closure);
            return true;
        }

        @Specialization(guards = "!isSpecialized(object)")
        protected boolean serializeFallback(NativeArgumentBuffer buffer, TruffleObject object) {
            argType.serialize(buffer, createClosure(object));
            return true;
        }

        @TruffleBoundary
        protected LibFFIClosure createClosure(TruffleObject object) {
            return LibFFIClosure.create(ctxRef.get(), signature, object);
        }
    }

    static class SerializeEnvArgumentNode extends SerializeArgumentNode {

        private final LibFFIType argType;

        SerializeEnvArgumentNode(LibFFIType argType) {
            this.argType = argType;
        }

        @Override
        boolean execute(NativeArgumentBuffer buffer, Object arg) {
            argType.serialize(buffer, null);
            return false;
        }
    }
}
