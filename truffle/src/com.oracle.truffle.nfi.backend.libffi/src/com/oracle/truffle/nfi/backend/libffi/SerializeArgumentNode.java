/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.ArrayType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.BasicType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.CachedTypeInfo;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.EnvType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.NullableType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.ObjectType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.StringType;
import com.oracle.truffle.nfi.backend.libffi.NativeArgumentBuffer.TypeTag;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeByteArrayNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeDoubleArrayNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeFloatArrayNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeIntArrayNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeLongArrayNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeShortArrayNodeGen;

abstract class SerializeArgumentNode extends Node {

    final CachedTypeInfo type;

    SerializeArgumentNode(CachedTypeInfo type) {
        this.type = type;
    }

    public final void serialize(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException {
        buffer.align(type.alignment);
        execute(arg, buffer);
    }

    abstract void execute(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException;

    static final class SerializeInt8Node extends SerializeArgumentNode {

        SerializeInt8Node(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            buffer.putInt8((byte) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class SerializeInt16Node extends SerializeArgumentNode {

        SerializeInt16Node(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            buffer.putInt16((short) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class SerializeInt32Node extends SerializeArgumentNode {

        SerializeInt32Node(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            buffer.putInt32((int) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class SerializeInt64Node extends SerializeArgumentNode {

        SerializeInt64Node(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            buffer.putInt64((long) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class SerializeFloatNode extends SerializeArgumentNode {

        SerializeFloatNode(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            buffer.putFloat((float) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class SerializeDoubleNode extends SerializeArgumentNode {

        SerializeDoubleNode(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            buffer.putDouble((double) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    abstract static class SerializePointerNode extends SerializeArgumentNode {

        SerializePointerNode(CachedTypeInfo type) {
            super(type);
        }

        @Specialization(limit = "3", guards = "interop.isPointer(arg)", rewriteOn = UnsupportedMessageException.class)
        void putPointer(Object arg, NativeArgumentBuffer buffer,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            buffer.putPointerKeepalive(arg, interop.asPointer(arg), type.size);
        }

        @Specialization(limit = "3", guards = {"!interop.isPointer(arg)", "interop.isNull(arg)"})
        void putNull(@SuppressWarnings("unused") Object arg, NativeArgumentBuffer buffer,
                        @SuppressWarnings("unused") @CachedLibrary("arg") InteropLibrary interop) {
            buffer.putPointer(0, type.size);
        }

        @Specialization(limit = "3", replaces = {"putPointer", "putNull"})
        void putGeneric(Object arg, NativeArgumentBuffer buffer,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (!interop.isPointer(arg)) {
                    interop.toNative(arg);
                }
                if (interop.isPointer(arg)) {
                    buffer.putPointerKeepalive(arg, interop.asPointer(arg), type.size);
                    return;
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter();
            if (interop.isNull(arg)) {
                buffer.putPointer(0, type.size);
                return;
            } else {
                try {
                    if (interop.isNumber(arg)) {
                        buffer.putPointer(interop.asLong(arg), type.size);
                        return;
                    }
                } catch (UnsupportedMessageException ex2) {
                    // fallthrough
                }
                try {
                    // workaround: some objects do not yet adhere to the contract of
                    // toNative/isPointer/asPointer, ask for pointer one more time
                    buffer.putPointerKeepalive(arg, interop.asPointer(arg), type.size);
                    return;
                } catch (UnsupportedMessageException e) {
                    // fallthrough
                }
            }
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }

    abstract static class SerializeStringNode extends SerializeArgumentNode {

        SerializeStringNode(StringType type) {
            super(type);
        }

        @Specialization(limit = "3", guards = "interop.isPointer(value)", rewriteOn = UnsupportedMessageException.class)
        void putPointer(Object value, NativeArgumentBuffer buffer,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            buffer.putPointerKeepalive(value, interop.asPointer(value), type.size);
        }

        @Specialization(limit = "3", guards = {"!interop.isPointer(value)", "interop.isString(value)"}, rewriteOn = UnsupportedMessageException.class)
        void putString(Object value, NativeArgumentBuffer buffer,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            buffer.putObject(TypeTag.STRING, interop.asString(value), type.size);
        }

        @Specialization(limit = "3", guards = {"!interop.isPointer(value)", "!interop.isString(value)", "interop.isNull(value)"})
        void putNull(@SuppressWarnings("unused") Object value, NativeArgumentBuffer buffer,
                        @SuppressWarnings("unused") @CachedLibrary("value") InteropLibrary interop) {
            buffer.putPointer(0, type.size);
        }

        @Specialization(limit = "3", replaces = {"putPointer", "putString", "putNull"})
        void putGeneric(Object value, NativeArgumentBuffer buffer,
                        @CachedLibrary("value") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (interop.isPointer(value)) {
                    buffer.putPointerKeepalive(value, interop.asPointer(value), type.size);
                    return;
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            try {
                if (interop.isString(value)) {
                    buffer.putObject(TypeTag.STRING, interop.asString(value), type.size);
                    return;
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter();
            if (interop.isNull(value)) {
                buffer.putPointer(0, type.size);
            } else {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    static final class SerializeObjectNode extends SerializeArgumentNode {

        SerializeObjectNode(ObjectType type) {
            super(type);
        }

        @Override
        void execute(Object value, NativeArgumentBuffer buffer) {
            buffer.putObject(TypeTag.OBJECT, value, type.size);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    abstract static class SerializeNullableNode extends SerializeArgumentNode {

        SerializeNullableNode(NullableType type) {
            super(type);
        }

        @Specialization(limit = "3")
        void putObject(Object value, NativeArgumentBuffer buffer,
                        @CachedLibrary("value") InteropLibrary interop) {
            if (interop.isNull(value)) {
                buffer.putPointer(0L, type.size);
            } else {
                buffer.putObject(TypeTag.OBJECT, value, type.size);
            }
        }
    }

    static final class SerializeEnvNode extends SerializeArgumentNode {

        SerializeEnvNode(EnvType type) {
            super(type);
        }

        @Override
        void execute(Object value, NativeArgumentBuffer buffer) {
            assert value == null;
            buffer.putObject(TypeTag.ENV, null, type.size);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    abstract static class UnwrapHostObjectNode extends Node {

        abstract Object execute(Object value);

        final boolean isHostObject(Object value) {
            LibFFIContext ctx = LibFFIContext.get(this);
            return ctx.env.isHostObject(value);
        }

        @Specialization(guards = "isHostObject(value)")
        Object doHostObject(Object value) {
            return LibFFIContext.get(this).env.asHostObject(value);
        }

        @Fallback
        Object doOther(@SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    abstract static class ObjectDummy extends Node {

        static ObjectDummy create() {
            return null;
        }

        abstract Object execute();
    }

    abstract static class BufferDummy extends Node {

        static BufferDummy create() {
            return null;
        }

        abstract NativeArgumentBuffer execute();
    }

    @NodeChild(value = "value", type = ObjectDummy.class, implicit = true)
    @NodeChild(value = "buffer", type = BufferDummy.class, implicit = true)
    @NodeChild(value = "hostObject", type = UnwrapHostObjectNode.class, executeWith = "value", implicit = true)
    abstract static class SerializeArrayNode extends SerializeArgumentNode {

        SerializeArrayNode(CachedTypeInfo type) {
            super(type);
        }

        static SerializeArrayNode create(ArrayType type) {
            switch (type.elementType) {
                case UINT8:
                case SINT8:
                    return SerializeByteArrayNodeGen.create(type);
                case UINT16:
                case SINT16:
                    return SerializeShortArrayNodeGen.create(type);
                case UINT32:
                case SINT32:
                    return SerializeIntArrayNodeGen.create(type);
                case UINT64:
                case SINT64:
                    return SerializeLongArrayNodeGen.create(type);
                case FLOAT:
                    return SerializeFloatArrayNodeGen.create(type);
                case DOUBLE:
                    return SerializeDoubleArrayNodeGen.create(type);
                default:
                    throw CompilerDirectives.shouldNotReachHere(type.elementType.name());
            }
        }

        @Specialization(guards = "hostObject == null")
        void doInteropObject(Object value, NativeArgumentBuffer buffer, Object hostObject,
                        @Cached(parameters = "type") SerializePointerNode serialize) throws UnsupportedTypeException {
            assert hostObject == null;
            serialize.execute(value, buffer);
        }
    }

    abstract static class SerializeByteArrayNode extends SerializeArrayNode {

        SerializeByteArrayNode(ArrayType type) {
            super(type);
        }

        @Specialization
        void putBooleanArray(Object value, NativeArgumentBuffer buffer, boolean[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.BOOLEAN_ARRAY, array, type.size);
        }

        @Specialization
        void putByteArray(Object value, NativeArgumentBuffer buffer, byte[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.BYTE_ARRAY, array, type.size);
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(Object value, NativeArgumentBuffer buffer, Object array) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }

    abstract static class SerializeShortArrayNode extends SerializeArrayNode {

        SerializeShortArrayNode(ArrayType type) {
            super(type);
        }

        @Specialization
        void putShortArray(Object value, NativeArgumentBuffer buffer, short[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.SHORT_ARRAY, array, type.size);
        }

        @Specialization
        void putCharArray(Object value, NativeArgumentBuffer buffer, char[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.CHAR_ARRAY, array, type.size);
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(Object value, NativeArgumentBuffer buffer, Object array) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }

    abstract static class SerializeIntArrayNode extends SerializeArrayNode {

        SerializeIntArrayNode(ArrayType type) {
            super(type);
        }

        @Specialization
        void putIntArray(Object value, NativeArgumentBuffer buffer, int[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.INT_ARRAY, array, type.size);
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(Object value, NativeArgumentBuffer buffer, Object array) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }

    abstract static class SerializeLongArrayNode extends SerializeArrayNode {

        SerializeLongArrayNode(ArrayType type) {
            super(type);
        }

        @Specialization
        void putLongArray(Object value, NativeArgumentBuffer buffer, long[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.LONG_ARRAY, array, type.size);
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(Object value, NativeArgumentBuffer buffer, Object array) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }

    abstract static class SerializeFloatArrayNode extends SerializeArrayNode {

        SerializeFloatArrayNode(ArrayType type) {
            super(type);
        }

        @Specialization
        void putFloatArray(Object value, NativeArgumentBuffer buffer, float[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.FLOAT_ARRAY, array, type.size);
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(Object value, NativeArgumentBuffer buffer, Object array) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }

    abstract static class SerializeDoubleArrayNode extends SerializeArrayNode {

        SerializeDoubleArrayNode(ArrayType type) {
            super(type);
        }

        @Specialization
        void putDoubleArray(Object value, NativeArgumentBuffer buffer, double[] array) {
            assert LibFFIContext.get(this).env.asHostObject(value) == array;
            buffer.putObject(TypeTag.DOUBLE_ARRAY, array, type.size);
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(Object value, NativeArgumentBuffer buffer, Object array) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }
}
