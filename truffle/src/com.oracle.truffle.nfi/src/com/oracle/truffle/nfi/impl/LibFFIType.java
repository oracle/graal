/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.ClosureArgumentNode.BufferClosureArgumentNode;
import com.oracle.truffle.nfi.impl.ClosureArgumentNode.ObjectClosureArgumentNode;
import com.oracle.truffle.nfi.impl.ClosureArgumentNode.StringClosureArgumentNode;
import com.oracle.truffle.nfi.impl.LibFFIType.ArrayType.HostObjectHelperNode.WrongTypeException;
import com.oracle.truffle.nfi.impl.LibFFITypeFactory.ArrayTypeFactory.CachedHostObjectHelperNodeGen;
import com.oracle.truffle.nfi.impl.NativeArgumentBuffer.TypeTag;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

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
            case POINTER:
                return new SimpleType(ctx, simpleType, size, alignment, ffiType);
            case STRING:
                return new StringType(ctx, size, alignment, ffiType);
            case OBJECT:
                return new ObjectType(ctx, size, alignment, ffiType);
            case NULLABLE:
                return new NullableType(ctx, size, alignment, ffiType);
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

    @ExportLibrary(NativeArgumentLibrary.class)
    abstract static class BasicType extends LibFFIType {

        final NFIContext ctx;
        final NativeSimpleType simpleType;

        BasicType(NFIContext ctx, NativeSimpleType simpleType, int size, int alignment, int objectCount, long ffiType, Direction direction) {
            super(size, alignment, objectCount, ffiType, direction, false);
            this.ctx = ctx;
            this.simpleType = simpleType;
        }

        BasicType(NFIContext ctx, NativeSimpleType simpleType, int size, int alignment, int objectCount, long ffiType) {
            this(ctx, simpleType, size, alignment, objectCount, ffiType, Direction.BOTH);
        }

        @ExportMessage
        boolean accepts(@Shared("cachedType") @Cached("this.simpleType") NativeSimpleType cachedType) {
            return cachedType == simpleType;
        }

        @ExportMessage
        void serialize(NativeArgumentBuffer buffer, Object value,
                        @Shared("cachedType") @Cached("this.simpleType") NativeSimpleType cachedType,
                        @CachedLibrary(limit = "3") SerializeArgumentLibrary serialize,
                        @CachedLibrary(limit = "1") InteropLibrary interop) throws UnsupportedTypeException {
            buffer.align(alignment);
            switch (cachedType) {
                case UINT8:
                    serialize.putUByte(value, buffer);
                    break;
                case SINT8:
                    serialize.putByte(value, buffer);
                    break;
                case UINT16:
                    serialize.putUShort(value, buffer);
                    break;
                case SINT16:
                    serialize.putShort(value, buffer);
                    break;
                case UINT32:
                    serialize.putUInt(value, buffer);
                    break;
                case SINT32:
                    serialize.putInt(value, buffer);
                    break;
                case UINT64:
                    serialize.putULong(value, buffer);
                    break;
                case SINT64:
                    serialize.putLong(value, buffer);
                    break;
                case FLOAT:
                    serialize.putFloat(value, buffer);
                    break;
                case DOUBLE:
                    serialize.putDouble(value, buffer);
                    break;
                case POINTER:
                    serialize.putPointer(value, buffer, size);
                    break;
                case STRING:
                    serialize.putString(value, buffer, size);
                    break;
                case OBJECT:
                    buffer.putObject(TypeTag.OBJECT, value, size);
                    break;
                case NULLABLE:
                    if (interop.isNull(value)) {
                        buffer.putPointer(0L, size);
                    } else {
                        buffer.putObject(TypeTag.OBJECT, value, size);
                    }
                    break;
                case VOID:
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(simpleType.name());
            }
        }

        @ExportMessage
        Object deserialize(NativeArgumentBuffer buffer,
                        @Shared("cachedType") @Cached("this.simpleType") NativeSimpleType cachedType,
                        @CachedLanguage NFILanguageImpl language) {
            buffer.align(alignment);
            switch (cachedType) {
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
                    return NativePointer.create(language, buffer.getPointer(size));
                case STRING:
                    return new NativeString(buffer.getPointer(size));
                case NULLABLE:
                case OBJECT:
                    Object ret = buffer.getObject(size);
                    if (ret == null) {
                        return NativePointer.create(language, 0);
                    } else {
                        return ret;
                    }

                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(simpleType.name());
            }
        }

        @Override
        public Object deserializeRet(NativeArgumentBuffer buffer, NFILanguageImpl language) {
            return deserialize(buffer, simpleType, language);
        }
    }

    static final class SimpleType extends BasicType {

        SimpleType(NFIContext ctx, NativeSimpleType simpleType, int size, int alignment, long ffiType) {
            super(ctx, simpleType, size, alignment, 0, ffiType);
        }

        public Object fromPrimitive(long primitive) {
            switch (simpleType) {
                case VOID:
                    return NativePointer.create(ctx.language, 0);
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
                    return NativePointer.create(ctx.language, primitive);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(simpleType.name());
            }
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return new BufferClosureArgumentNode(this);
        }

        @Override
        public LibFFIType overrideClosureRetType() {
            // special handling for small integers: return them as long (native type: ffi_arg)
            switch (simpleType) {
                case UINT8:
                case UINT16:
                case UINT32:
                    return ctx.lookupSimpleType(NativeSimpleType.UINT64);
                case SINT8:
                case SINT16:
                case SINT32:
                    return ctx.lookupSimpleType(NativeSimpleType.SINT64);
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

        private VoidType(NFIContext ctx, int size, int alignment, long ffiType) {
            super(ctx, NativeSimpleType.VOID, size, alignment, 0, ffiType);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            throw new AssertionError("invalid argument type VOID");
        }

        @Override
        public Object deserializeRet(NativeArgumentBuffer buffer, NFILanguageImpl language) {
            return NativePointer.create(language, 0);
        }
    }

    static final class StringType extends BasicType {

        private StringType(NFIContext ctx, int size, int alignment, long ffiType) {
            super(ctx, NativeSimpleType.STRING, size, alignment, 1, ffiType);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return new StringClosureArgumentNode();
        }
    }

    static final class ObjectType extends BasicType {

        ObjectType(NFIContext ctx, int size, int alignment, long ffiType) {
            super(ctx, NativeSimpleType.OBJECT, size, alignment, 1, ffiType);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return new ObjectClosureArgumentNode();
        }
    }

    static final class NullableType extends BasicType {

        NullableType(NFIContext ctx, int size, int alignment, long ffiType) {
            super(ctx, NativeSimpleType.NULLABLE, size, alignment, 1, ffiType, Direction.JAVA_TO_NATIVE_ONLY);
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return new ObjectClosureArgumentNode();
        }
    }

    abstract static class BasePointerType extends LibFFIType {

        protected BasePointerType(LibFFIType pointerType, Direction direction, boolean injectedArgument) {
            super(pointerType.size, pointerType.alignment, 1, pointerType.type, direction, injectedArgument);
        }
    }

    @ExportLibrary(NativeArgumentLibrary.class)
    static final class ArrayType extends BasePointerType {

        final NativeSimpleType elementType;
        final HostObjectHelperNode uncachedHelper;

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
            uncachedHelper = new UncachedHostObjectHelperNode(size, elementType);
        }

        @ExportMessage
        boolean accepts(@Cached("this.elementType") NativeSimpleType cachedType) {
            return cachedType == elementType;
        }

        @ExportMessage
        void serialize(NativeArgumentBuffer buffer, Object value,
                        @Cached SerializeHelperNode serializeHelper) throws UnsupportedTypeException {
            buffer.align(alignment);
            serializeHelper.execute(this, buffer, value);
        }

        @ImportStatic(NFILanguageImpl.class)
        @GenerateUncached
        @SuppressWarnings("unused")
        abstract static class SerializeHelperNode extends Node {

            abstract void execute(LibFFIType.ArrayType type, NativeArgumentBuffer buffer, Object value) throws UnsupportedTypeException;

            static boolean isHostObject(NFIContext ctx, Object value) {
                return ctx.env.isHostObject(value) && ctx.env.asHostObject(value) != null;
            }

            @Specialization(guards = "isHostObject(ctx, value)", rewriteOn = WrongTypeException.class)
            static void doHostObject(LibFFIType.ArrayType type, NativeArgumentBuffer buffer, Object value,
                            @CachedContext(NFILanguageImpl.class) NFIContext ctx,
                            @Cached(parameters = "type") HostObjectHelperNode helper) throws UnsupportedTypeException, WrongTypeException {
                Object hostObject = ctx.env.asHostObject(value);
                helper.execute(buffer, hostObject);
            }

            @Specialization(guards = "!isHostObject(ctx, value)", limit = "3")
            static void doInteropObject(LibFFIType.ArrayType type, NativeArgumentBuffer buffer, Object value,
                            @CachedContext(NFILanguageImpl.class) NFIContext ctx,
                            @CachedLibrary("value") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
                serialize.putPointer(value, buffer, type.size);
            }

            @Specialization(limit = "3", replaces = {"doHostObject", "doInteropObject"})
            static void doGeneric(LibFFIType.ArrayType type, NativeArgumentBuffer buffer, Object value,
                            @CachedContext(NFILanguageImpl.class) NFIContext ctx,
                            @CachedLibrary("value") SerializeArgumentLibrary serialize,
                            @Cached(parameters = "type") HostObjectHelperNode helper) throws UnsupportedTypeException {
                if (isHostObject(ctx, value)) {
                    try {
                        doHostObject(type, buffer, value, ctx, helper);
                        return;
                    } catch (WrongTypeException e) {
                        // fall back to "doInteropObject" case
                    }
                }
                doInteropObject(type, buffer, value, ctx, serialize);
            }
        }

        abstract static class HostObjectHelperNode extends Node {

            final class WrongTypeException extends ControlFlowException {

                private static final long serialVersionUID = 1L;
            }

            final int size;
            final Class<?> arrayClass1;
            final Class<?> arrayClass2;

            static HostObjectHelperNode create(LibFFIType.ArrayType type) {
                return CachedHostObjectHelperNodeGen.create(type.size, type.elementType);
            }

            static HostObjectHelperNode getUncached(LibFFIType.ArrayType type) {
                return type.uncachedHelper;
            }

            protected HostObjectHelperNode(int size, NativeSimpleType elementType) {
                this.size = size;
                switch (elementType) {
                    case UINT8:
                    case SINT8:
                        arrayClass1 = byte[].class;
                        arrayClass2 = boolean[].class;
                        break;
                    case UINT16:
                    case SINT16:
                        arrayClass1 = short[].class;
                        arrayClass2 = char[].class;
                        break;
                    case UINT32:
                    case SINT32:
                        arrayClass1 = int[].class;
                        arrayClass2 = null;
                        break;
                    case UINT64:
                    case SINT64:
                        arrayClass1 = long[].class;
                        arrayClass2 = null;
                        break;
                    case FLOAT:
                        arrayClass1 = float[].class;
                        arrayClass2 = null;
                        break;
                    case DOUBLE:
                        arrayClass1 = double[].class;
                        arrayClass2 = null;
                        break;
                    default:
                        arrayClass1 = null;
                        arrayClass2 = null;
                }
            }

            abstract void execute(NativeArgumentBuffer buffer, Object value) throws UnsupportedTypeException, WrongTypeException;
        }

        abstract static class CachedHostObjectHelperNode extends HostObjectHelperNode {

            protected CachedHostObjectHelperNode(int size, NativeSimpleType elementType) {
                super(size, elementType);
            }

            @Specialization(guards = "arrayClass1 == value.getClass()", limit = "1")
            void doHostArray1(NativeArgumentBuffer buffer, Object value,
                            @Exclusive @CachedLibrary("value") SerializeArgumentLibrary lib) throws UnsupportedTypeException {
                lib.putPointer(CompilerDirectives.castExact(value, arrayClass1), buffer, size);
            }

            @Specialization(guards = "arrayClass2 == value.getClass()", limit = "1")
            void doHostArray2(NativeArgumentBuffer buffer, Object value,
                            @Exclusive @CachedLibrary("value") SerializeArgumentLibrary lib) throws UnsupportedTypeException {
                lib.putPointer(CompilerDirectives.castExact(value, arrayClass2), buffer, size);
            }

            @Fallback
            @SuppressWarnings("unused")
            void doOther(NativeArgumentBuffer buffer, Object value) throws WrongTypeException {
                throw new WrongTypeException();
            }
        }

        static final class UncachedHostObjectHelperNode extends HostObjectHelperNode {

            private final SerializeArgumentLibrary uncachedLib1;
            private final SerializeArgumentLibrary uncachedLib2;

            UncachedHostObjectHelperNode(int size, NativeSimpleType elementType) {
                super(size, elementType);
                if (arrayClass1 != null) {
                    uncachedLib1 = SerializeArgumentLibrary.getFactory().getUncached(Array.newInstance(arrayClass1.getComponentType(), 0));
                } else {
                    uncachedLib1 = null;
                }
                if (arrayClass2 != null) {
                    uncachedLib2 = SerializeArgumentLibrary.getFactory().getUncached(Array.newInstance(arrayClass2.getComponentType(), 0));
                } else {
                    uncachedLib2 = null;
                }
            }

            @Override
            void execute(NativeArgumentBuffer buffer, Object value) throws UnsupportedTypeException, WrongTypeException {
                if (arrayClass1 == value.getClass()) {
                    assert uncachedLib1.accepts(value);
                    uncachedLib1.putPointer(CompilerDirectives.castExact(value, arrayClass1), buffer, size);
                } else if (arrayClass2 == value.getClass()) {
                    assert uncachedLib2.accepts(value);
                    uncachedLib2.putPointer(CompilerDirectives.castExact(value, arrayClass2), buffer, size);
                } else {
                    throw new WrongTypeException();
                }
            }
        }

        @Override
        @ExportMessage(name = "deserialize")
        public Object deserializeRet(NativeArgumentBuffer buffer,
                        @CachedLanguage NFILanguageImpl language) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Arrays can only be passed from Java to native");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            throw new AssertionError("Arrays can only be passed from Java to native");
        }
    }

    @ExportLibrary(NativeArgumentLibrary.class)
    static final class ClosureType extends BasePointerType {

        final LibFFISignature signature;
        final boolean asRetType;

        ClosureType(LibFFIType pointerType, LibFFISignature signature, boolean asRetType) {
            super(pointerType, signature.getAllowedCallDirection().reverse(), false);
            this.signature = signature;
            this.asRetType = asRetType;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        static class Serialize {

            @Specialization
            static void doClosure(LibFFIType.ClosureType type, NativeArgumentBuffer buffer, LibFFIClosure closure) {
                buffer.align(type.alignment);
                if (type.asRetType) {
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

                buffer.putPointer(closure.nativePointer.getCodePointer(), type.size);
            }

            static boolean isOther(Object value) {
                return !(value instanceof LibFFIClosure);
            }

            @Specialization(limit = "3", guards = {"value == cachedValue", "isOther(cachedValue)", "interop.isExecutable(cachedValue)"})
            static void doExecutableCached(LibFFIType.ClosureType type, NativeArgumentBuffer buffer, Object value,
                            @Cached("value") Object cachedValue,
                            @CachedLibrary("cachedValue") InteropLibrary interop,
                            @CachedContext(NFILanguageImpl.class) ContextReference<NFIContext> ctxRef,
                            @Cached("create(type.signature, value, ctxRef)") LibFFIClosure cachedClosure) {
                doClosure(type, buffer, cachedClosure);
            }

            @Specialization(limit = "0", replaces = "doExecutableCached", guards = {"isOther(value)", "interop.isExecutable(value)"})
            @TruffleBoundary
            static void doExecutableSlowPath(LibFFIType.ClosureType type, NativeArgumentBuffer buffer, Object value,
                            @CachedContext(NFILanguageImpl.class) ContextReference<NFIContext> ctxRef,
                            @CachedLibrary("value") InteropLibrary interop) {
                // TODO performance warning
                LibFFIClosure closure = LibFFIClosure.create(type.signature, value, ctxRef);
                doClosure(type, buffer, closure);
            }

            @Specialization(limit = "3", guards = {"isOther(value)", "!interop.isExecutable(value)"})
            static void doPointer(LibFFIType.ClosureType type, NativeArgumentBuffer buffer, Object value,
                            @CachedLibrary("value") InteropLibrary interop,
                            @CachedLibrary("value") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
                buffer.align(type.alignment);
                serialize.putPointer(value, buffer, type.size);
            }
        }

        @ExportMessage(name = "deserialize")
        @Override
        public Object deserializeRet(NativeArgumentBuffer buffer,
                        @CachedLanguage NFILanguageImpl language) {
            long functionPointer = buffer.getPointer(size);
            if (functionPointer == 0) {
                // cannot bind null pointer
                return NativePointer.create(language, 0);
            } else {
                return NativePointer.createBound(language, functionPointer, signature);
            }
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            return new BufferClosureArgumentNode(this);
        }
    }

    @ExportLibrary(NativeArgumentLibrary.class)
    @SuppressWarnings("unused")
    static final class EnvType extends BasePointerType {

        EnvType(LibFFIType pointerType) {
            super(pointerType, Direction.BOTH, true);
        }

        @ExportMessage
        protected void serialize(NativeArgumentBuffer buffer, Object value) {
            buffer.putObject(TypeTag.ENV, null, size);
        }

        @ExportMessage(name = "deserialize")
        @Override
        public Object deserializeRet(NativeArgumentBuffer buffer,
                        @CachedLanguage NFILanguageImpl language) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("environment pointer can not be used as return type");
        }

        @Override
        public ClosureArgumentNode createClosureArgumentNode() {
            throw new AssertionError("createClosureArgumentNode should not be called on injected argument");
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

    public abstract ClosureArgumentNode createClosureArgumentNode();

    public abstract Object deserializeRet(NativeArgumentBuffer buffer, NFILanguageImpl language);

    public LibFFIType overrideClosureRetType() {
        return this;
    }
}
