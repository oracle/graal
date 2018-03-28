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

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.nfi.impl.ClosureArgumentNode.ObjectClosureArgumentNode;
import com.oracle.truffle.nfi.impl.ClosureArgumentNode.StringClosureArgumentNode;
import com.oracle.truffle.nfi.impl.ClosureArgumentNodeFactory.BufferClosureArgumentNodeGen;
import com.oracle.truffle.nfi.impl.NativeArgumentBuffer.TypeTag;
import com.oracle.truffle.nfi.impl.SerializeArgumentNode.SerializeEnvArgumentNode;
import com.oracle.truffle.nfi.impl.SerializeArgumentNode.SerializeObjectArgumentNode;
import com.oracle.truffle.nfi.impl.SerializeArgumentNodeFactory.SerializeArrayArgumentNodeGen;
import com.oracle.truffle.nfi.impl.SerializeArgumentNodeFactory.SerializeClosureArgumentNodeGen;
import com.oracle.truffle.nfi.impl.SerializeArgumentNodeFactory.SerializePointerArgumentNodeGen;
import com.oracle.truffle.nfi.impl.SerializeArgumentNodeFactory.SerializeSimpleArgumentNodeGen;
import com.oracle.truffle.nfi.impl.SerializeArgumentNodeFactory.SerializeStringArgumentNodeGen;
import com.oracle.truffle.nfi.types.NativeSimpleType;

abstract class LibFFIType {

    static LibFFIType createSimpleType(NFIContext ctx, NativeSimpleType simpleType, int size, int alignment, long ffiType) {
        switch (simpleType) {
            case VOID:
                return new VoidType(ctx, size, alignment, ffiType);
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
                return new SimpleType(ctx, simpleType, size, alignment, ffiType);
            case POINTER:
                return new PointerType(ctx, size, alignment, ffiType);
            case STRING:
                return new StringType(size, alignment, ffiType);
            case OBJECT:
                return new ObjectType(size, alignment, ffiType);
            default:
                throw new AssertionError(simpleType.name());
        }
    }

    static LibFFIType createArrayType(NFIContext ctx, NativeSimpleType simpleType) {
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
                return new ArrayType(ctx.lookupSimpleType(NativeSimpleType.POINTER), simpleType);
            default:
                return null;
        }
    }

    static class SimpleType extends LibFFIType {

        final NFIContext ctx;
        final NativeSimpleType simpleType;

        SimpleType(NFIContext ctx, NativeSimpleType simpleType, int size, int alignment, long ffiType) {
            super(size, alignment, 0, ffiType, Direction.BOTH, false);
            this.ctx = ctx;
            this.simpleType = simpleType;
        }

        private static Number asNumber(Object object) {
            if (object instanceof Byte ||
                            object instanceof Short ||
                            object instanceof Integer ||
                            object instanceof Long ||
                            object instanceof Float ||
                            object instanceof Double) {
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
                    CompilerDirectives.transferToInterpreter();
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
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(simpleType.name());
            }
        }

        public final Object fromPrimitive(long primitive) {
            switch (simpleType) {
                case VOID:
                    return new NativePointer(0);
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
                    CompilerDirectives.transferToInterpreter();
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
                    return SerializeSimpleArgumentNodeGen.create(ctx.lookupSimpleType(NativeSimpleType.UINT64));
                case SINT8:
                case SINT16:
                case SINT32:
                    return SerializeSimpleArgumentNodeGen.create(ctx.lookupSimpleType(NativeSimpleType.SINT64));
                default:
                    return super.createClosureReturnNode();
            }
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            // we always need an unbox here
            return PrepareArgument.UNBOX;
        }
    }

    static final class VoidType extends SimpleType {

        private VoidType(NFIContext ctx, int size, int alignment, long ffiType) {
            super(ctx, NativeSimpleType.VOID, size, alignment, ffiType);
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

        private PointerType(NFIContext ctx, int size, int alignment, long ffiType) {
            super(ctx, NativeSimpleType.POINTER, size, alignment, ffiType);
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
                return PrepareArgument.POINTER;
            }
        }
    }

    static final class StringType extends LibFFIType {

        private StringType(int size, int alignment, long ffiType) {
            super(size, alignment, 1, ffiType, Direction.BOTH, false);
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
                return PrepareArgument.UNBOX;
            }
        }
    }

    static class ObjectType extends LibFFIType {

        ObjectType(int size, int alignment, long ffiType) {
            super(size, alignment, 1, ffiType, Direction.BOTH, false);
        }

        @Override
        protected void doSerialize(NativeArgumentBuffer buffer, Object object) {
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
            return new SerializeObjectArgumentNode(this);
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

        protected BasePointerType(LibFFIType pointerType, Direction direction, boolean injectedArgument) {
            super(pointerType.size, pointerType.alignment, 1, pointerType.type, direction, injectedArgument);
        }
    }

    static class ArrayType extends BasePointerType {

        final NativeSimpleType elementType;

        ArrayType(LibFFIType pointerType, NativeSimpleType elementType) {
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
                    CompilerDirectives.transferToInterpreter();
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
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Arrays can only be passed from Java to native");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            throw new AssertionError("Arrays can only be passed from Java to native");
        }

        protected Class<?> getArrayType(Object hostObject) {
            switch (elementType) {
                case UINT8:
                case SINT8:
                    if (hostObject instanceof byte[]) {
                        return byte[].class;
                    } else if (hostObject instanceof boolean[]) {
                        return boolean[].class;
                    }
                    break;
                case UINT16:
                case SINT16:
                    if (hostObject instanceof short[]) {
                        return short[].class;
                    } else if (hostObject instanceof char[]) {
                        return char[].class;
                    }
                    break;
                case UINT32:
                case SINT32:
                    if (hostObject instanceof int[]) {
                        return int[].class;
                    }
                    break;
                case UINT64:
                case SINT64:
                    if (hostObject instanceof long[]) {
                        return long[].class;
                    }
                    break;
                case FLOAT:
                    if (hostObject instanceof float[]) {
                        return float[].class;
                    }
                    break;
                case DOUBLE:
                    if (hostObject instanceof double[]) {
                        return double[].class;
                    }
                    break;
            }
            return null;
        }

        @Override
        public Object slowpathPrepareArgument(TruffleObject value) {
            Env env = NFILanguageImpl.getCurrentContextReference().get().env;
            Object hostObject = null;
            if (env.isHostObject(value)) {
                hostObject = env.asHostObject(value);
            }
            Class<?> arrayType = getArrayType(hostObject);
            if (arrayType == null) {
                return PrepareArgument.POINTER;
            } else {
                return hostObject;
            }
        }
    }

    static class ClosureType extends BasePointerType {

        private final LibFFISignature signature;
        private final boolean asRetType;

        ClosureType(LibFFIType pointerType, LibFFISignature signature, boolean asRetType) {
            super(pointerType, signature.getAllowedCallDirection().reverse(), false);
            this.signature = signature;
            this.asRetType = asRetType;
        }

        @TruffleBoundary
        private static RuntimeException shouldNotReachHere() {
            throw new IllegalArgumentException("should not reach here from compiled code");
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
                    /*
                     * If we enter this branch, that means the LibFFIClosure was not cached. This
                     * should only happen on the slow-path.
                     */
                    if (CompilerDirectives.inCompiledCode()) {
                        throw shouldNotReachHere();
                    }
                    closure = LibFFIClosure.createSlowPath(signature, (TruffleObject) value);
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
            return PrepareArgument.EXECUTABLE;
        }
    }

    static class EnvType extends BasePointerType {

        EnvType(LibFFIType pointerType) {
            super(pointerType, Direction.BOTH, true);
        }

        @Override
        protected void doSerialize(NativeArgumentBuffer buffer, Object value) {
            buffer.putObject(TypeTag.ENV, null, size);
        }

        @Override
        protected Object doDeserialize(NativeArgumentBuffer buffer) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("environment pointer can not be used as return type");
        }

        @Override
        public SerializeArgumentNode createSerializeArgumentNode() {
            return new SerializeEnvArgumentNode(this);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            throw new AssertionError("createClosureArgumentNode should not be called on injected argument");
        }

        @Override
        public SerializeArgumentNode createClosureReturnNode() {
            throw new AssertionError("environment pointer can not be used as return type");
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
    protected final boolean injectedArgument;

    protected LibFFIType(int size, int alignment, int objectCount, long type, Direction direction, boolean injectedArgument) {
        this.size = size;
        this.alignment = alignment;
        this.objectCount = objectCount;
        this.type = type;
        this.allowedDataFlowDirection = direction;
        this.injectedArgument = injectedArgument;
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

    public enum PrepareArgument {
        /**
         * The {@link TruffleObject} should be unboxed, and the result should be passed on.
         */
        UNBOX,
        /**
         * If the {@link TruffleObject} is a pointer ({@link Message#IS_POINTER}, it should be sent
         * the {@link Message#AS_POINTER} message, and the result passed on. Otherwise, the object
         * should be transformed to a pointer with {@link Message#TO_NATIVE}.
         */
        POINTER,
        /**
         * The caller should check whether the object is {@link Message#IS_EXECUTABLE}. If it is, it
         * should be directly passed to the {@link #serialize} method. Otherwise, it should be
         * treated as {@link #POINTER}.
         */
        EXECUTABLE
    }

    /**
     * Prepare the argument so it can be passed to the {@link #serialize} method. This should only
     * be called from the slow-path, on the fast-path the node created by
     * {@link #createSerializeArgumentNode()} will do this already in a more efficient way. If this
     * returns one of the {@link PrepareArgument} enum values, special handling is required (see
     * documentation of {@link PrepareArgument}). Otherwise, the return value of this function
     * should be passed directly to the {@link #serialize} method.
     */
    @TruffleBoundary
    public abstract Object slowpathPrepareArgument(TruffleObject value);
}
