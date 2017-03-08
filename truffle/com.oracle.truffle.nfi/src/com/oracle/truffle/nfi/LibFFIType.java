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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.nfi.ClosureArgumentNode.ObjectClosureArgumentNode;
import com.oracle.truffle.nfi.ClosureArgumentNode.StringClosureArgumentNode;
import com.oracle.truffle.nfi.ClosureArgumentNodeFactory.BufferClosureArgumentNodeGen;
import com.oracle.truffle.nfi.NativeArgumentBuffer.TypeTag;
import com.oracle.truffle.nfi.SerializeArgumentNodeFactory.SerializeArrayArgumentNodeGen;
import com.oracle.truffle.nfi.SerializeArgumentNodeFactory.SerializeClosureArgumentNodeGen;
import com.oracle.truffle.nfi.SerializeArgumentNodeFactory.SerializeObjectArgumentNodeGen;
import com.oracle.truffle.nfi.SerializeArgumentNodeFactory.SerializePointerArgumentNodeGen;
import com.oracle.truffle.nfi.SerializeArgumentNodeFactory.SerializeSimpleArgumentNodeGen;
import com.oracle.truffle.nfi.SerializeArgumentNodeFactory.SerializeStringArgumentNodeGen;
import com.oracle.truffle.nfi.types.NativeArrayTypeMirror;
import com.oracle.truffle.nfi.types.NativeFunctionTypeMirror;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import com.oracle.truffle.nfi.types.NativeSimpleTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror.Kind;
import java.nio.ByteOrder;

abstract class LibFFIType {

    @CompilationFinal(dimensions = 1) static final LibFFIType[] simpleTypeMap = new LibFFIType[NativeSimpleType.values().length];
    @CompilationFinal(dimensions = 1) static final LibFFIType[] arrayTypeMap = new LibFFIType[NativeSimpleType.values().length];

    public static LibFFIType lookupArgType(NativeTypeMirror type) {
        return lookup(type, false);
    }

    public static LibFFIType lookupRetType(NativeTypeMirror type) {
        return lookup(type, true);
    }

    private static LibFFIType lookup(NativeTypeMirror type, boolean asRetType) {
        switch (type.getKind()) {
            case SIMPLE:
                NativeSimpleTypeMirror simpleType = (NativeSimpleTypeMirror) type;
                return simpleTypeMap[simpleType.getSimpleType().ordinal()];

            case ARRAY:
                NativeArrayTypeMirror arrayType = (NativeArrayTypeMirror) type;
                NativeTypeMirror elementType = arrayType.getElementType();

                LibFFIType ret = null;
                if (elementType.getKind() == Kind.SIMPLE) {
                    ret = arrayTypeMap[((NativeSimpleTypeMirror) elementType).getSimpleType().ordinal()];
                }

                if (ret == null) {
                    throw new AssertionError("unsupported array type");
                } else {
                    return ret;
                }

            case FUNCTION:
                NativeFunctionTypeMirror functionType = (NativeFunctionTypeMirror) type;
                LibFFISignature signature = LibFFISignature.create(functionType.getSignature());
                return new ClosureType(signature, asRetType);
        }
        throw new AssertionError("unsupported type");
    }

    protected static void initializeSimpleType(NativeSimpleType simpleType, int size, int alignment, long ffiType) {
        assert simpleTypeMap[simpleType.ordinal()] == null : "initializeSimpleType called twice for " + simpleType;
        simpleTypeMap[simpleType.ordinal()] = createSimpleType(simpleType, size, alignment, ffiType);
        arrayTypeMap[simpleType.ordinal()] = createArrayType(simpleType);
    }

    private static LibFFIType createSimpleType(NativeSimpleType simpleType, int size, int alignment, long ffiType) {
        switch (simpleType) {
            case VOID:
                return new VoidType(size, alignment, ffiType);
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
                return new SimpleType(simpleType, size, alignment, ffiType);
            case POINTER:
                return new PointerType(size, alignment, ffiType);
            case STRING:
                return new StringType(size, alignment, ffiType);
            case OBJECT:
                return new ObjectType(size, alignment, ffiType);
            default:
                throw new AssertionError(simpleType.name());
        }
    }

    private static LibFFIType createArrayType(NativeSimpleType simpleType) {
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
                return new ArrayType(simpleType);
            default:
                return null;
        }
    }

    static class SimpleType extends LibFFIType {

        final NativeSimpleType simpleType;

        SimpleType(NativeSimpleType simpleType, int size, int alignment, long ffiType) {
            super(size, alignment, 0, ffiType, Direction.BOTH);
            this.simpleType = simpleType;
        }

        private static Number asNumber(Object object) {
            if (object instanceof Number) {
                return (Number) object;
            } else if (object instanceof Boolean) {
                return (Boolean) object ? 1 : 0;
            } else if (object instanceof Character) {
                return (int) (Character) object;
            } else {
                throw UnsupportedTypeException.raise(new Object[]{object});
            }
        }

        @Override
        protected void doSerialize(NativeArgumentBuffer buffer, Object value) {
            Number number = asNumber(value);
            switch (simpleType) {
                case UINT8:
                case SINT8:
                    buffer.putInt8(number.byteValue());
                    break;
                case UINT16:
                case SINT16:
                    buffer.putInt16(number.shortValue());
                    break;
                case UINT32:
                case SINT32:
                    buffer.putInt32(number.intValue());
                    break;
                case UINT64:
                case SINT64:
                    buffer.putInt64(number.longValue());
                    break;
                case FLOAT:
                    buffer.putFloat(number.floatValue());
                    break;
                case DOUBLE:
                    buffer.putDouble(number.doubleValue());
                    break;
                case POINTER:
                    buffer.putPointer(number.longValue(), size);
                    break;
                default:
                    throw new AssertionError(simpleType.name());
            }
        }

        @Override
        protected Object doDeserialize(NativeArgumentBuffer buffer) {
            switch (simpleType) {
                case VOID:
                    return null;
                case UINT8:
                case SINT8:
                    return buffer.getInt8();
                case UINT16:
                case SINT16:
                    return buffer.getInt16();
                case UINT32:
                case SINT32:
                    return buffer.getInt32();
                case UINT64:
                case SINT64:
                    return buffer.getInt64();
                case FLOAT:
                    return buffer.getFloat();
                case DOUBLE:
                    return buffer.getDouble();
                default:
                    throw new AssertionError(simpleType.name());
            }
        }

        public final Object fromPrimitive(long primitive) {
            switch (simpleType) {
                case VOID:
                    return null;
                case UINT8:
                case SINT8:
                    return (byte) primitive;
                case UINT16:
                case SINT16:
                    return (short) primitive;
                case UINT32:
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
                    return new NativePointer(primitive);
                default:
                    throw new AssertionError(simpleType.name());
            }
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeSimpleArgumentNodeGen.create(this);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return BufferClosureArgumentNodeGen.create(this);
        }

        @Override
        public SerializeArgumentNode createClosureReturnNode() {
            // special handling for small integers: return them as long (native type: ffi_arg)
            switch (simpleType) {
                case UINT8:
                case UINT16:
                case UINT32:
                    return SerializeSimpleArgumentNodeGen.create(simpleTypeMap[NativeSimpleType.UINT64.ordinal()]);
                case SINT8:
                case SINT16:
                case SINT32:
                    return SerializeSimpleArgumentNodeGen.create(simpleTypeMap[NativeSimpleType.SINT64.ordinal()]);
                default:
                    return super.createClosureReturnNode();
            }
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            // we always need an unbox here
            return null;
        }
    }

    static final class VoidType extends SimpleType {

        private VoidType(int size, int alignment, long ffiType) {
            super(NativeSimpleType.VOID, size, alignment, ffiType);
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            throw new AssertionError("invalid argument type VOID");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            throw new AssertionError("invalid argument type VOID");
        }
    }

    static final class PointerType extends SimpleType {

        private PointerType(int size, int alignment, long ffiType) {
            super(NativeSimpleType.POINTER, size, alignment, ffiType);
        }

        @Override
        protected void doSerialize(NativeArgumentBuffer buffer, Object value) {
            if (value == null) {
                buffer.putPointer(0, size);
            } else if (value instanceof NativePointer) {
                NativePointer ptr = (NativePointer) value;
                buffer.putPointer(ptr.nativePointer, size);
            } else if (value instanceof NativeString) {
                NativeString str = (NativeString) value;
                buffer.putPointer(str.nativePointer, size);
            } else {
                super.doSerialize(buffer, value);
            }
        }

        @Override
        protected Object doDeserialize(NativeArgumentBuffer buffer) {
            return new NativePointer(buffer.getPointer(size));
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializePointerArgumentNodeGen.create(this);
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            if (value instanceof NativePointer || value instanceof NativeString) {
                return value;
            } else {
                return super.slowpathPrepareArgument(value);
            }
        }
    }

    static final class StringType extends LibFFIType {

        private StringType(int size, int alignment, long ffiType) {
            super(size, alignment, 1, ffiType, Direction.BOTH);
        }

        @Override
        protected void doSerialize(NativeArgumentBuffer buffer, Object value) {
            if (value == null) {
                buffer.putPointer(0, size);
            } else if (value instanceof String) {
                String str = (String) value;
                buffer.putObject(TypeTag.STRING, str, size);
            } else if (value instanceof NativeString) {
                NativeString str = (NativeString) value;
                buffer.putPointer(str.nativePointer, size);
            } else {
                throw UnsupportedTypeException.raise(new Object[]{value});
            }
        }

        @Override
        protected Object doDeserialize(NativeArgumentBuffer buffer) {
            long ptr = buffer.getPointer(size);
            return new NativeString(ptr);
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeStringArgumentNodeGen.create(this);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return new StringClosureArgumentNode();
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            if (value instanceof NativeString) {
                return value;
            } else {
                return null;
            }
        }
    }

    static class ObjectType extends LibFFIType {

        ObjectType(int size, int alignment, long ffiType) {
            super(size, alignment, 1, ffiType, Direction.BOTH);
        }

        @Override
        protected void doSerialize(NativeArgumentBuffer buffer, Object value) {
            TruffleObject object = (TruffleObject) value;
            buffer.putObject(TypeTag.OBJECT, object, size);
        }

        @Override
        protected Object doDeserialize(NativeArgumentBuffer buffer) {
            Object ret = buffer.getObject(size);
            if (ret == null) {
                return new NativePointer(0);
            } else {
                return ret;
            }
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeObjectArgumentNodeGen.create(this);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return new ObjectClosureArgumentNode();
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            return value;
        }
    }

    abstract static class BasePointerType extends LibFFIType {

        private static final LibFFIType POINTER;

        static {
            // make sure simpleTypeMap is initialized
            NativeAccess.ensureInitialized();
            POINTER = simpleTypeMap[NativeSimpleType.POINTER.ordinal()];
        }

        protected BasePointerType(Direction direction) {
            super(POINTER.size, POINTER.alignment, 1, POINTER.type, direction);
        }
    }

    static class ArrayType extends BasePointerType {

        final NativeSimpleType elementType;

        ArrayType(NativeSimpleType elementType) {
            super(Direction.JAVA_TO_NATIVE_ONLY);
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
        protected void doSerialize(NativeArgumentBuffer buffer, Object value) {
            if (value == null) {
                buffer.putPointer(0, size);
                return;
            }

            TypeTag tag = null;
            switch (elementType) {
                case UINT8:
                case SINT8:
                    if (value instanceof byte[]) {
                        tag = TypeTag.BYTE_ARRAY;
                    } else if (value instanceof boolean[]) {
                        tag = TypeTag.BOOLEAN_ARRAY;
                    }
                    break;
                case UINT16:
                case SINT16:
                    if (value instanceof short[]) {
                        tag = TypeTag.SHORT_ARRAY;
                    } else if (value instanceof char[]) {
                        tag = TypeTag.CHAR_ARRAY;
                    }
                    break;
                case UINT32:
                case SINT32:
                    if (value instanceof int[]) {
                        tag = TypeTag.INT_ARRAY;
                    }
                    break;
                case UINT64:
                case SINT64:
                    if (value instanceof long[]) {
                        tag = TypeTag.LONG_ARRAY;
                    }
                    break;
                case FLOAT:
                    if (value instanceof float[]) {
                        tag = TypeTag.FLOAT_ARRAY;
                    }
                    break;
                case DOUBLE:
                    if (value instanceof double[]) {
                        tag = TypeTag.DOUBLE_ARRAY;
                    }
                    break;
                default:
                    throw new AssertionError(elementType.name());
            }

            if (tag != null) {
                buffer.putObject(tag, value, size);
            } else {
                throw UnsupportedTypeException.raise(new Object[]{value});
            }
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeArrayArgumentNodeGen.create(this);
        }

        @Override
        protected Object doDeserialize(NativeArgumentBuffer buffer) {
            throw new AssertionError("Arrays can only be passed from Java to native");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            throw new AssertionError("Arrays can only be passed from Java to native");
        }

        protected Class<?> getArrayType(TruffleObject object) {
            switch (elementType) {
                case UINT8:
                case SINT8:
                    if (JavaInterop.isJavaObject(byte[].class, object)) {
                        return byte[].class;
                    } else if (JavaInterop.isJavaObject(boolean[].class, object)) {
                        return boolean[].class;
                    }
                    break;
                case UINT16:
                case SINT16:
                    if (JavaInterop.isJavaObject(short[].class, object)) {
                        return short[].class;
                    } else if (JavaInterop.isJavaObject(char[].class, object)) {
                        return char[].class;
                    }
                    break;
                case UINT32:
                case SINT32:
                    if (JavaInterop.isJavaObject(int[].class, object)) {
                        return int[].class;
                    }
                    break;
                case UINT64:
                case SINT64:
                    if (JavaInterop.isJavaObject(long[].class, object)) {
                        return long[].class;
                    }
                    break;
                case FLOAT:
                    if (JavaInterop.isJavaObject(float[].class, object)) {
                        return float[].class;
                    }
                    break;
                case DOUBLE:
                    if (JavaInterop.isJavaObject(double[].class, object)) {
                        return double[].class;
                    }
                    break;
            }
            return null;
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            Class<?> arrayType = getArrayType(value);
            if (arrayType == null) {
                return null;
            } else {
                return JavaInterop.asJavaObject(arrayType, value);
            }
        }
    }

    static class ClosureType extends BasePointerType {

        private final LibFFISignature signature;
        private final boolean asRetType;

        ClosureType(LibFFISignature signature, boolean asRetType) {
            super(signature.getAllowedCallDirection().reverse());
            this.signature = signature;
            this.asRetType = asRetType;
        }

        @Override
        protected void doSerialize(NativeArgumentBuffer buffer, Object value) {
            if (value instanceof NativePointer) {
                NativePointer pointer = (NativePointer) value;
                buffer.putPointer(pointer.nativePointer, size);
            } else {
                LibFFIClosure closure;
                if (value instanceof LibFFIClosure) {
                    closure = (LibFFIClosure) value;
                } else if (value instanceof TruffleObject) {
                    closure = LibFFIClosure.create(signature, (TruffleObject) value);
                } else {
                    throw UnsupportedTypeException.raise(new Object[]{value});
                }

                if (asRetType) {
                    /*
                     * The closure is passed as return type from a callback down to native code. In
                     * that case we transfer ownership of the native pointer to the caller, so we
                     * have to increase the reference count.
                     */
                    closure.nativePointer.addRef();
                } else {
                    /*
                     * The closure is passed as argument down to native code. Keep it alive for the
                     * duration of the call by storing it in the object arguments array. The native
                     * code will ignore this entry in the array.
                     */
                    buffer.putObject(TypeTag.CLOSURE, closure, 0);
                }

                buffer.putPointer(closure.nativePointer.getCodePointer(), size);
            }
        }

        @Override
        protected Object doDeserialize(NativeArgumentBuffer buffer) {
            long functionPointer = buffer.getPointer(size);
            NativePointer symbol = new NativePointer(functionPointer);
            if (functionPointer == 0) {
                // cannot bind null pointer
                return symbol;
            } else {
                return new LibFFIFunction(symbol, signature);
            }
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return SerializeClosureArgumentNodeGen.create(this, signature);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return BufferClosureArgumentNodeGen.create(this);
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            return value;
        }
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

    protected final int size;
    protected final int alignment;
    protected final int objectCount;

    protected final long type;

    protected final Direction allowedDataFlowDirection;

    protected LibFFIType(int size, int alignment, int objectCount, long type, Direction direction) {
        this.size = size;
        this.alignment = alignment;
        this.objectCount = objectCount;
        this.type = type;
        this.allowedDataFlowDirection = direction;
    }

    protected abstract void doSerialize(NativeArgumentBuffer buffer, Object value);

    protected abstract Object doDeserialize(NativeArgumentBuffer buffer);

    public final void serialize(NativeArgumentBuffer buffer, Object value) {
        buffer.align(alignment);
        doSerialize(buffer, value);
    }

    public final Object deserialize(NativeArgumentBuffer buffer) {
        buffer.align(alignment);
        return doDeserialize(buffer);
    }

    public abstract SerializeArgumentNode createSerializeArgumentNode();

    public abstract ClosureArgumentNode createClosureArgumentNode();

    public SerializeArgumentNode createClosureReturnNode() {
        return createSerializeArgumentNode();
    }

    /**
     * Prepare the argument so it can be passed to the {@link #serialize} method. This should only
     * be called from the slow-path, on the fast-path the node created by
     * {@link #createSerializeArgumentNode()} will do this already in a more efficient way. If this
     * method returns {@code null}, you should send an {@link Message#UNBOX} message to the object
     * and try again.
     */
    @TruffleBoundary
    public abstract Object slowpathPrepareArgument(TruffleObject value);
}
