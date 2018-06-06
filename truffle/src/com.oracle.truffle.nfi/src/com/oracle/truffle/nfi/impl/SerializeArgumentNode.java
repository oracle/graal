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
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.TypeConversion.AsPointerNode;
import com.oracle.truffle.nfi.impl.TypeConversion.AsStringNode;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsPointerNodeGen;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsStringNodeGen;

abstract class SerializeArgumentNode extends Node {

    /**
     * @return whether the argument was consumed
     */
    abstract boolean execute(NativeArgumentBuffer buffer, Object arg);

    protected static Node createIsNull() {
        return Message.IS_NULL.createNode();
    }

    protected static boolean checkNull(Node isNull, TruffleObject object) {
        return ForeignAccess.sendIsNull(isNull, object);
    }

    protected static Node createIsBoxed() {
        return Message.IS_BOXED.createNode();
    }

    protected static Node createUnbox() {
        return Message.UNBOX.createNode();
    }

    abstract static class SerializeSimpleArgumentNode extends SerializeArgumentNode {

        protected final LibFFIType argType;

        SerializeSimpleArgumentNode(LibFFIType argType) {
            this.argType = argType;
        }

        @Specialization
        protected boolean serializeByte(NativeArgumentBuffer buffer, byte arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeBoolean(NativeArgumentBuffer buffer, boolean arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeShort(NativeArgumentBuffer buffer, short arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeChar(NativeArgumentBuffer buffer, char arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeInt(NativeArgumentBuffer buffer, int arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeLong(NativeArgumentBuffer buffer, long arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeFloat(NativeArgumentBuffer buffer, float arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeDouble(NativeArgumentBuffer buffer, double arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization(guards = {"checkIsBoxed(isBoxed, arg)"})
        @SuppressWarnings("unused")
        protected boolean serializeUnbox(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createIsBoxed()") Node isBoxed,
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

        protected static boolean checkIsBoxed(Node isBoxed, TruffleObject object) {
            return ForeignAccess.sendIsBoxed(isBoxed, object);
        }
    }

    abstract static class SerializePointerArgumentNode extends SerializeArgumentNode {

        final LibFFIType argType;

        SerializePointerArgumentNode(LibFFIType type) {
            this.argType = type;
        }

        @Specialization
        protected boolean serializeLong(NativeArgumentBuffer buffer, long arg) {
            argType.serialize(buffer, arg);
            return true;
        }

        @Specialization
        protected boolean serializeNativeString(NativeArgumentBuffer buffer, NativeString string) {
            argType.serialize(buffer, string);
            return true;
        }

        @Specialization
        protected boolean serializeNativePointer(NativeArgumentBuffer buffer, NativePointer ptr) {
            argType.serialize(buffer, ptr);
            return true;
        }

        @Specialization(replaces = {"serializeNativeString", "serializeNativePointer"})
        protected boolean serializeTruffleObject(NativeArgumentBuffer buffer, TruffleObject arg,
                        @Cached("createAsPointer()") AsPointerNode asPointer) {
            argType.serialize(buffer, asPointer.execute(arg));
            return true;
        }

        static AsPointerNode createAsPointer() {
            return AsPointerNodeGen.create();
        }
    }

    abstract static class SerializeStringArgumentNode extends SerializeArgumentNode {

        final LibFFIType argType;

        SerializeStringArgumentNode(LibFFIType type) {
            this.argType = type;
        }

        @Specialization
        protected boolean serializeNativeString(NativeArgumentBuffer buffer, NativeString string) {
            argType.serialize(buffer, string);
            return true;
        }

        @Specialization(guards = "notNativeString(string)")
        protected boolean serializeOther(NativeArgumentBuffer buffer, Object string,
                        @Cached("createAsString()") AsStringNode asString) {
            argType.serialize(buffer, asString.execute(string));
            return true;
        }

        static boolean notNativeString(Object arg) {
            return !(arg instanceof NativeString);
        }

        static AsStringNode createAsString() {
            return AsStringNodeGen.create(false);
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
        final ContextReference<NFIContext> contextRef = NFILanguageImpl.getCurrentContextReference();

        SerializeArrayArgumentNode(LibFFIType.ArrayType argType) {
            this.argType = argType;
        }

        @Override
        final boolean execute(NativeArgumentBuffer buffer, Object arg) {
            NFIContext context = contextRef.get();
            Object hostObject = null;
            if (context.env.isHostObject(arg)) {
                hostObject = context.env.asHostObject(arg);
            }
            return execute(buffer, arg, hostObject);
        }

        abstract boolean execute(NativeArgumentBuffer buffer, Object arg, Object hostObject);

        @Specialization(guards = "checkNull(isNull, arg)", limit = "1")
        @SuppressWarnings("unused")
        protected boolean serializeNull(NativeArgumentBuffer buffer, TruffleObject arg, Object hostObject,
                        @Cached("createIsNull()") Node isNull) {
            argType.serialize(buffer, null);
            return true;
        }

        @Specialization(guards = "isInstanceOf(arrayType, hostObject)", limit = "1")
        @SuppressWarnings("unused")
        protected boolean serializeArray1(NativeArgumentBuffer buffer, TruffleObject object, Object hostObject,
                        @Cached("argType.getArrayType(hostObject)") Class<?> arrayType) {
            argType.serialize(buffer, arrayType.cast(hostObject));
            return true;
        }

        @Specialization(guards = "isInstanceOf(arrayType, hostObject)", limit = "1")
        @SuppressWarnings("unused")
        protected boolean serializeArray2(NativeArgumentBuffer buffer, TruffleObject object, Object hostObject,
                        @Cached("argType.getArrayType(hostObject)") Class<?> arrayType) {
            argType.serialize(buffer, arrayType.cast(hostObject));
            return true;
        }

        @Fallback
        @SuppressWarnings("unused")
        protected boolean error(NativeArgumentBuffer buffer, Object object, Object hostObject) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.raise(new Object[]{object});
        }

        protected static boolean isInstanceOf(Class<?> arrayType, Object hostObject) {
            return arrayType != null && arrayType.isInstance(hostObject);
        }
    }

    abstract static class SerializeClosureArgumentNode extends SerializeArgumentNode {

        private final LibFFIType argType;
        private final LibFFISignature signature;

        private final ContextReference<NFIContext> ctxRef;

        SerializeClosureArgumentNode(LibFFIType argType, LibFFISignature signature) {
            this.argType = argType;
            this.signature = signature;
            this.ctxRef = NFILanguageImpl.getCurrentContextReference();
        }

        @Specialization
        protected boolean serializeNativePointer(NativeArgumentBuffer buffer, NativePointer object) {
            argType.serialize(buffer, object);
            return true;
        }

        @Specialization(limit = "5", guards = {"checkExecutable(isExecutable, cachedObject)", "object == cachedObject"})
        @SuppressWarnings("unused")
        protected boolean serializeCached(NativeArgumentBuffer buffer, TruffleObject object,
                        @Cached("object") TruffleObject cachedObject,
                        @Cached("createIsExecutable()") Node isExecutable,
                        @Cached("createClosure(object)") LibFFIClosure closure) {
            argType.serialize(buffer, closure);
            return true;
        }

        @Specialization(guards = "checkExecutable(isExecutable, object)")
        protected boolean serializeExecutable(NativeArgumentBuffer buffer, TruffleObject object,
                        @SuppressWarnings("unused") @Cached("createIsExecutable()") Node isExecutable) {
            argType.serialize(buffer, createClosure(object));
            return true;
        }

        @TruffleBoundary
        protected LibFFIClosure createClosure(TruffleObject object) {
            return LibFFIClosure.create(ctxRef.get(), signature, object);
        }

        @Specialization(replaces = "serializeNativePointer", guards = "!checkExecutable(isExecutable, object)")
        protected boolean serializePointer(NativeArgumentBuffer buffer, TruffleObject object,
                        @SuppressWarnings("unused") @Cached("createIsExecutable()") Node isExecutable,
                        @Cached("createAsPointer()") AsPointerNode asPointer) {
            argType.serialize(buffer, asPointer.execute(object));
            return true;
        }

        static Node createIsExecutable() {
            return Message.IS_EXECUTABLE.createNode();
        }

        static boolean checkExecutable(Node isExecutable, TruffleObject obj) {
            return ForeignAccess.sendIsExecutable(isExecutable, obj);
        }

        static AsPointerNode createAsPointer() {
            return AsPointerNodeGen.create();
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
