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
