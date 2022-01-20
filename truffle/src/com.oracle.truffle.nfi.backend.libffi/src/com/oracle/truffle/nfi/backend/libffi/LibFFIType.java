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

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.backend.libffi.ClosureArgumentNode.InjectedClosureArgumentNode;
import com.oracle.truffle.nfi.backend.libffi.ClosureArgumentNodeFactory.BufferClosureArgumentNodeGen;
import com.oracle.truffle.nfi.backend.libffi.ClosureArgumentNodeFactory.ObjectClosureArgumentNodeGen;
import com.oracle.truffle.nfi.backend.libffi.ClosureArgumentNodeFactory.StringClosureArgumentNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeDoubleNode;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeEnvNode;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeFloatNode;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeInt16Node;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeInt32Node;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeInt64Node;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeInt8Node;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNode.SerializeObjectNode;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeArrayNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeNullableNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializePointerNodeGen;
import com.oracle.truffle.nfi.backend.libffi.SerializeArgumentNodeFactory.SerializeStringNodeGen;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

/**
 * Runtime object representing native types. Instances of this class can not be cached in shared AST
 * nodes, since they contain references to native datastructures of libffi.
 *
 * All information that is context and process independent is collected in a separate object,
 * {@link CachedTypeInfo}. Two {@link LibFFIType} objects that have the same {@link CachedTypeInfo}
 * are guaranteed to behave the same semantically.
 */
final class LibFFIType {

    static CachedTypeInfo createSimpleTypeInfo(LibFFILanguage language, NativeSimpleType simpleType, int size, int alignment) {
        switch (simpleType) {
            case VOID:
                return new VoidType(language, size, alignment);
            case UINT8:
            case SINT8:
            case UINT16:
            case SINT16:
            case UINT32:
            case SINT32:
            case UINT64:
            case SINT64:
            case FLOAT:
            case DOUBLE:
                return new SimpleType(language, simpleType, size, alignment);
            case POINTER:
                return new PointerType(language, size, alignment);
            case STRING:
                return new StringType(language, size, alignment);
            case OBJECT:
                return new ObjectType(language, size, alignment);
            case NULLABLE:
                return new NullableType(language, size, alignment);
            default:
                throw new AssertionError(simpleType.name());
        }
    }

    static CachedTypeInfo createArrayTypeInfo(CachedTypeInfo ptrType, NativeSimpleType simpleType) {
        switch (simpleType) {
            case UINT8:
            case SINT8:
            case UINT16:
            case SINT16:
            case UINT32:
            case SINT32:
            case UINT64:
            case SINT64:
            case FLOAT:
            case DOUBLE:
                return new ArrayType(ptrType, simpleType);
            default:
                return null;
        }
    }

    protected final long type; // native pointer
    protected final CachedTypeInfo typeInfo;

    protected LibFFIType(CachedTypeInfo typeInfo, long type) {
        this.typeInfo = typeInfo;
        this.type = type;
    }

    enum Direction {
        JAVA_TO_NATIVE_ONLY,
        NATIVE_TO_JAVA_ONLY,
        BOTH;

        Direction reverse() {
            switch (this) {
                case JAVA_TO_NATIVE_ONLY:
                    return NATIVE_TO_JAVA_ONLY;
                case NATIVE_TO_JAVA_ONLY:
                    return JAVA_TO_NATIVE_ONLY;
                case BOTH:
                    return BOTH;
                default:
                    return null;
            }
        }
    }

    /**
     * This class contains all information about native types that can be shared across contexts.
     * Instances of this class can safely be cached in shared AST. Code that is specialized for
     * operating on particular types can only rely on information in this class. That way, the
     * specialized code can also be shared.
     */
    abstract static class CachedTypeInfo {
        protected final int size;
        protected final int alignment;
        protected final int objectCount;

        protected final Direction allowedDataFlowDirection;
        protected final boolean injectedArgument;

        protected CachedTypeInfo(int size, int alignment, int objectCount, Direction direction, boolean injectedArgument) {
            this.size = size;
            this.alignment = alignment;
            this.objectCount = objectCount;
            this.allowedDataFlowDirection = direction;
            this.injectedArgument = injectedArgument;
        }

        public abstract SerializeArgumentNode createSerializeArgumentNode();

        public abstract ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg);

        public abstract Object deserializeRet(Node node, NativeArgumentBuffer buffer);

        public CachedTypeInfo overrideClosureRetType() {
            return this;
        }
    }

    abstract static class BasicType extends CachedTypeInfo {

        final LibFFILanguage nfiLanguage;
        final NativeSimpleType simpleType;

        BasicType(LibFFILanguage language, NativeSimpleType simpleType, int size, int alignment, int objectCount, Direction direction) {
            super(size, alignment, objectCount, direction, false);
            this.nfiLanguage = language;
            this.simpleType = simpleType;
        }

        BasicType(LibFFILanguage language, NativeSimpleType simpleType, int size, int alignment, int objectCount) {
            this(language, simpleType, size, alignment, objectCount, Direction.BOTH);
        }

        @Override
        public Object deserializeRet(Node node, NativeArgumentBuffer buffer) {
            buffer.align(alignment);
            switch (simpleType) {
                case VOID:
                    return null;
                case UINT8:
                    return buffer.getInt8() & (short) 0xFF;
                case SINT8:
                    return buffer.getInt8();
                case UINT16:
                    return buffer.getInt16() & 0xFFFF;
                case SINT16:
                    return buffer.getInt16();
                case UINT32:
                    return buffer.getInt32() & 0xFFFF_FFFFL;
                case SINT32:
                    return buffer.getInt32();
                case UINT64:
                case SINT64:
                    return buffer.getInt64();
                case FLOAT:
                    return buffer.getFloat();
                case DOUBLE:
                    return buffer.getDouble();
                case POINTER:
                    return NativePointer.create(LibFFILanguage.get(node), buffer.getPointer(size));
                case STRING:
                    return new NativeString(buffer.getPointer(size));
                case NULLABLE:
                case OBJECT:
                    Object ret = buffer.getObject(size);
                    if (ret == null) {
                        return NativePointer.create(LibFFILanguage.get(node), 0);
                    } else {
                        return ret;
                    }

                default:
                    throw CompilerDirectives.shouldNotReachHere(simpleType.name());
            }
        }
    }

    static final class SimpleType extends BasicType {

        private final SerializeArgumentNode sharedArgumentNode;

        SimpleType(LibFFILanguage language, NativeSimpleType simpleType, int size, int alignment) {
            super(language, simpleType, size, alignment, 0);
            this.sharedArgumentNode = createSharedArgumentNode();
            assert !this.sharedArgumentNode.isAdoptable();
        }

        public Object fromPrimitive(long primitive) {
            switch (simpleType) {
                case VOID:
                    return NativePointer.NULL;
                case UINT8:
                    return primitive & (short) 0xFF;
                case SINT8:
                    return (byte) primitive;
                case UINT16:
                    return primitive & 0xFFFF;
                case SINT16:
                    return (short) primitive;
                case UINT32:
                    return primitive & 0xFFFF_FFFFL;
                case SINT32:
                    return (int) primitive;
                case UINT64:
                case SINT64:
                    return primitive;
                case FLOAT:
                    int ret;
                    if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                        /*
                         * This isn't really a long, but a float stored in an 8-byte container. We
                         * want to get the first 4 byte out of it. On a little-endian machine that's
                         * just a cast to int, but on a big-endian machine we additionally need a
                         * shift.
                         */
                        ret = (int) (primitive >>> 32);
                    } else {
                        ret = (int) primitive;
                    }
                    return Float.intBitsToFloat(ret);
                case DOUBLE:
                    return Double.longBitsToDouble(primitive);
                case POINTER:
                    return NativePointer.create(nfiLanguage, primitive);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(simpleType.name());
            }
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return sharedArgumentNode;
        }

        private SerializeArgumentNode createSharedArgumentNode() {
            switch (simpleType) {
                case UINT8:
                case SINT8:
                    return new SerializeInt8Node(this);
                case UINT16:
                case SINT16:
                    return new SerializeInt16Node(this);
                case UINT32:
                case SINT32:
                    return new SerializeInt32Node(this);
                case UINT64:
                case SINT64:
                    return new SerializeInt64Node(this);
                case FLOAT:
                    return new SerializeFloatNode(this);
                case DOUBLE:
                    return new SerializeDoubleNode(this);
                default:
                    throw CompilerDirectives.shouldNotReachHere(simpleType.name());
            }
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            return BufferClosureArgumentNodeGen.create(this, arg);
        }

        @Override
        public CachedTypeInfo overrideClosureRetType() {
            // special handling for small integers: return them as long (native type: ffi_arg)
            switch (simpleType) {
                case UINT8:
                case UINT16:
                case UINT32:
                    return nfiLanguage.lookupSimpleTypeInfo(NativeSimpleType.UINT64);
                case SINT8:
                case SINT16:
                case SINT32:
                    return nfiLanguage.lookupSimpleTypeInfo(NativeSimpleType.SINT64);
                default:
                    return this;
            }
        }

        @Override
        public String toString() {
            return simpleType.toString();
        }
    }

    static final class VoidType extends BasicType {

        private VoidType(LibFFILanguage language, int size, int alignment) {
            super(language, NativeSimpleType.VOID, size, alignment, 0);
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            throw new AssertionError("invalid argument type VOID");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            throw new AssertionError("invalid argument type VOID");
        }

        @Override
        public Object deserializeRet(Node node, NativeArgumentBuffer buffer) {
            return NativePointer.NULL;
        }
    }

    static final class PointerType extends BasicType {

        private PointerType(LibFFILanguage language, int size, int alignment) {
            super(language, NativeSimpleType.POINTER, size, alignment, 1);
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializePointerNodeGen.create(this);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            return BufferClosureArgumentNodeGen.create(this, arg);
        }
    }

    static final class StringType extends BasicType {

        private StringType(LibFFILanguage language, int size, int alignment) {
            super(language, NativeSimpleType.STRING, size, alignment, 1);
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeStringNodeGen.create(this);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            return StringClosureArgumentNodeGen.create(arg);
        }
    }

    static final class ObjectType extends BasicType {

        private final SerializeArgumentNode sharedArgumentNode;

        ObjectType(LibFFILanguage language, int size, int alignment) {
            super(language, NativeSimpleType.OBJECT, size, alignment, 1);
            this.sharedArgumentNode = new SerializeObjectNode(this);
            assert !this.sharedArgumentNode.isAdoptable();
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return sharedArgumentNode;
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            return ObjectClosureArgumentNodeGen.create(arg);
        }
    }

    static final class NullableType extends BasicType {

        NullableType(LibFFILanguage language, int size, int alignment) {
            super(language, NativeSimpleType.NULLABLE, size, alignment, 1, Direction.JAVA_TO_NATIVE_ONLY);
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeNullableNodeGen.create(this);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            return ObjectClosureArgumentNodeGen.create(arg);
        }
    }

    abstract static class BasePointerType extends CachedTypeInfo {

        protected BasePointerType(CachedTypeInfo pointerType, Direction direction, boolean injectedArgument) {
            super(pointerType.size, pointerType.alignment, 1, direction, injectedArgument);
        }
    }

    static final class ArrayType extends BasePointerType {

        final NativeSimpleType elementType;

        ArrayType(CachedTypeInfo pointerType, NativeSimpleType elementType) {
            super(pointerType, Direction.JAVA_TO_NATIVE_ONLY, false);
            switch (elementType) {
                case UINT8:
                case SINT8:
                case UINT16:
                case SINT16:
                case UINT32:
                case SINT32:
                case UINT64:
                case SINT64:
                case FLOAT:
                case DOUBLE:
                    this.elementType = elementType;
                    break;
                default:
                    throw new IllegalArgumentException(String.format("only primitive array types are supported, got [%s]", elementType));
            }
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeArrayNodeGen.create(this);
        }

        @Override
        public Object deserializeRet(Node node, NativeArgumentBuffer buffer) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Arrays can only be passed from Java to native");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            throw new AssertionError("Arrays can only be passed from Java to native");
        }
    }

    @SuppressWarnings("unused")
    static final class EnvType extends BasePointerType {

        private final SerializeArgumentNode sharedArgumentNode;

        EnvType(CachedTypeInfo pointerType) {
            super(pointerType, Direction.BOTH, true);
            this.sharedArgumentNode = new SerializeEnvNode(this);
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return sharedArgumentNode;
        }

        @Override
        public Object deserializeRet(Node node, NativeArgumentBuffer buffer) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("environment pointer can not be used as return type");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
            return new InjectedClosureArgumentNode();
        }
    }
}
