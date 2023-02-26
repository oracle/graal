/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.api.SerializableLibrary;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.ArrayType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.BasicType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.CachedTypeInfo;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.EnvType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.NullableType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.ObjectType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.StringType;
import com.oracle.truffle.nfi.backend.libffi.NativeArgumentBuffer.TypeTag;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.GetByteArrayTagNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.GetDoubleArrayTagNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.GetFloatArrayTagNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.GetIntArrayTagNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.GetLongArrayTagNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.GetShortArrayTagNodeGen;

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

    static final class SerializeInt8VarargsNode extends SerializeArgumentNode {

        SerializeInt8VarargsNode(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            // for varargs, int8 needs to be promoted to int32
            buffer.putInt32((byte) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class SerializeUInt8VarargsNode extends SerializeArgumentNode {

        SerializeUInt8VarargsNode(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            // for varargs, uint8 needs to be promoted to uint32
            buffer.putInt32((byte) arg & 0xFF);
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

    static final class SerializeInt16VarargsNode extends SerializeArgumentNode {

        SerializeInt16VarargsNode(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            // for varargs, int16 needs to be promoted to int32
            buffer.putInt32((short) arg);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class SerializeUInt16VarargsNode extends SerializeArgumentNode {

        SerializeUInt16VarargsNode(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            // for varargs, uint16 needs to be promoted to uint32
            buffer.putInt32((short) arg & 0xFFFF);
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

    static final class SerializeFloatVarargsNode extends SerializeArgumentNode {

        SerializeFloatVarargsNode(BasicType type) {
            super(type);
        }

        @Override
        void execute(Object arg, NativeArgumentBuffer buffer) {
            // for varargs, float needs to be promoted to double
            buffer.putDouble((float) arg);
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

    @ValueType
    @ExportLibrary(InteropLibrary.class)
    static final class BufferSlice implements TruffleObject {

        private final NativeArgumentBuffer buffer;
        private final int startOffset;
        private final int limit;

        private BufferSlice(NativeArgumentBuffer buffer, int startOffset, int limit) {
            this.buffer = buffer;
            this.startOffset = startOffset;
            this.limit = limit;
        }

        private void setPosition(long offset, int size) throws InvalidBufferOffsetException {
            if (0 <= offset && (offset + size) <= limit) {
                buffer.position(startOffset + (int) offset);
            } else {
                throw InvalidBufferOffsetException.create(offset, size);
            }
        }

        @ExportMessage
        boolean hasBufferElements() {
            return true;
        }

        @ExportMessage
        boolean isBufferWritable() {
            return true;
        }

        @ExportMessage
        long getBufferSize() {
            return limit;
        }

        @ExportMessage
        void writeBufferByte(long offset, byte value) throws InvalidBufferOffsetException {
            setPosition(offset, Byte.BYTES);
            buffer.putInt8(value);
        }

        @ExportMessage
        void writeBufferShort(ByteOrder order, long offset, short value) throws InvalidBufferOffsetException {
            assert order == ByteOrder.nativeOrder();
            setPosition(offset, Short.BYTES);
            buffer.putInt16(value);
        }

        @ExportMessage
        void writeBufferInt(ByteOrder order, long offset, int value) throws InvalidBufferOffsetException {
            assert order == ByteOrder.nativeOrder();
            setPosition(offset, Integer.BYTES);
            buffer.putInt32(value);
        }

        @ExportMessage
        void writeBufferLong(ByteOrder order, long offset, long value) throws InvalidBufferOffsetException {
            assert order == ByteOrder.nativeOrder();
            setPosition(offset, Long.BYTES);
            buffer.putInt64(value);
        }

        @ExportMessage
        void writeBufferFloat(ByteOrder order, long offset, float value) throws InvalidBufferOffsetException {
            assert order == ByteOrder.nativeOrder();
            setPosition(offset, Float.BYTES);
            buffer.putFloat(value);
        }

        @ExportMessage
        void writeBufferDouble(ByteOrder order, long offset, double value) throws InvalidBufferOffsetException {
            assert order == ByteOrder.nativeOrder();
            setPosition(offset, Double.BYTES);
            buffer.putDouble(value);
        }

        @ExportMessage
        byte readBufferByte(long offset) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage(name = "readBufferShort")
        @ExportMessage(name = "readBufferInt")
        @ExportMessage(name = "readBufferLong")
        @ExportMessage(name = "readBufferFloat")
        @ExportMessage(name = "readBufferDouble")
        short readOther(ByteOrder order, long offset) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    abstract static class SerializeSerializableNode extends SerializeArgumentNode {

        SerializeSerializableNode(CachedTypeInfo type) {
            super(type);
        }

        @Specialization(limit = "3", guards = "serialize.isSerializable(arg)")
        void doSerializable(Object arg, NativeArgumentBuffer buffer,
                        @CachedLibrary("arg") SerializableLibrary serialize) {
            BufferSlice b = new BufferSlice(buffer, buffer.position(), type.size);
            try {
                serialize.serialize(arg, b);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
            buffer.position(b.startOffset + type.size);
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

    abstract static class GetTypeTagNode extends Node {

        abstract TypeTag execute(Object value);

        @Fallback
        TypeTag doOther(@SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    abstract static class SerializeArrayNode extends SerializeArgumentNode {

        @Child GetTypeTagNode getTypeTag;

        SerializeArrayNode(ArrayType type) {
            super(type);
            switch (type.elementType) {
                case UINT8:
                case SINT8:
                    getTypeTag = GetByteArrayTagNodeGen.create();
                    break;
                case UINT16:
                case SINT16:
                    getTypeTag = GetShortArrayTagNodeGen.create();
                    break;
                case UINT32:
                case SINT32:
                    getTypeTag = GetIntArrayTagNodeGen.create();
                    break;
                case UINT64:
                case SINT64:
                    getTypeTag = GetLongArrayTagNodeGen.create();
                    break;
                case FLOAT:
                    getTypeTag = GetFloatArrayTagNodeGen.create();
                    break;
                case DOUBLE:
                    getTypeTag = GetDoubleArrayTagNodeGen.create();
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere(type.elementType.name());
            }
        }

        final boolean isHostObject(Object value) {
            return LibFFIContext.get(this).env.isHostObject(value);
        }

        final Object asHostObject(Object value) {
            return LibFFIContext.get(this).env.asHostObject(value);
        }

        @Specialization(guards = {"isHostObject(value)", "tag != null"})
        void doHostObject(@SuppressWarnings("unused") Object value, NativeArgumentBuffer buffer,
                        @Bind("asHostObject(value)") Object hostObject,
                        @Bind("getTypeTag.execute(hostObject)") TypeTag tag) {
            buffer.putObject(tag, hostObject, type.size);
        }

        @Specialization(guards = "tag != null")
        void doArray(Object value, NativeArgumentBuffer buffer,
                        @Bind("getTypeTag.execute(value)") TypeTag tag) {
            buffer.putObject(tag, value, type.size);
        }

        @Fallback
        void doInteropObject(Object value, NativeArgumentBuffer buffer,
                        @Cached(parameters = "type") SerializePointerNode serialize) throws UnsupportedTypeException {
            serialize.execute(value, buffer);
        }
    }

    abstract static class GetByteArrayTagNode extends GetTypeTagNode {

        @Specialization
        TypeTag doBooleanArray(boolean[] array) {
            assert array != null;
            return TypeTag.BOOLEAN_ARRAY;
        }

        @Specialization
        TypeTag doByteArray(byte[] array) {
            assert array != null;
            return TypeTag.BYTE_ARRAY;
        }
    }

    abstract static class GetShortArrayTagNode extends GetTypeTagNode {

        @Specialization
        TypeTag doShortArray(short[] array) {
            assert array != null;
            return TypeTag.SHORT_ARRAY;
        }

        @Specialization
        TypeTag doCharArray(char[] array) {
            assert array != null;
            return TypeTag.CHAR_ARRAY;
        }
    }

    abstract static class GetIntArrayTagNode extends GetTypeTagNode {

        @Specialization
        TypeTag doIntArray(int[] array) {
            assert array != null;
            return TypeTag.INT_ARRAY;
        }
    }

    abstract static class GetLongArrayTagNode extends GetTypeTagNode {

        @Specialization
        TypeTag doLongArray(long[] array) {
            assert array != null;
            return TypeTag.LONG_ARRAY;
        }
    }

    abstract static class GetFloatArrayTagNode extends GetTypeTagNode {

        @Specialization
        TypeTag doFloatArray(float[] array) {
            assert array != null;
            return TypeTag.FLOAT_ARRAY;
        }
    }

    abstract static class GetDoubleArrayTagNode extends GetTypeTagNode {

        @Specialization
        TypeTag doDoubleArray(double[] array) {
            assert array != null;
            return TypeTag.DOUBLE_ARRAY;
        }
    }
}
