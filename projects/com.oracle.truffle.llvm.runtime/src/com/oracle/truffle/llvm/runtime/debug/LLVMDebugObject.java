/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.debug;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * This class describes a source-level variable. Debuggers can use it to display the original
 * source-level state of an executed LLVM IR file.
 */
public abstract class LLVMDebugObject implements TruffleObject {

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMDebugObject;
    }

    private static final Object[] NO_KEYS = new Object[0];

    protected final long offset;

    protected final LLVMDebugValueProvider value;

    protected final LLVMSourceType type;

    private LLVMDebugObject(LLVMDebugValueProvider value, long offset, LLVMSourceType type) {
        this.value = value;
        this.offset = offset;
        this.type = type;
    }

    /**
     * Get an object describing the referenced variable's type for the debugger to show.
     *
     * @return the type of the referenced object
     */
    public Object getType() {
        return type;
    }

    /**
     * If this is a complex object return the identifiers for its members.
     *
     * @return the keys or null
     */
    public abstract Object[] getKeys();

    /**
     * If this is a complex object return the member that is identified by the given key.
     *
     * @param identifier the object identifying the member
     *
     * @return the member or {@code null} if the key does not identify a member
     */
    public abstract Object getMember(Object identifier);

    /**
     * Return an object that represents the value of the referenced variable.
     *
     * @return the value of the referenced variable
     */
    protected abstract Object getValue();

    /**
     * A representation of the current value of the referenced variable for the debugger to show.
     *
     * @return a string describing the referenced value
     */
    @Override
    @TruffleBoundary
    public String toString() {
        return Objects.toString(getValue());
    }

    @TruffleBoundary
    Object cannotRead() {
        return String.format("Cannot read %d bits from offset %d in %s", type.getSize(), offset, value);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMDebugObjectMessageResolutionForeign.ACCESS;
    }

    private static final class Enum extends LLVMDebugObject {

        Enum(LLVMDebugValueProvider value, long offset, LLVMSourceType type) {
            super(value, offset, type);
        }

        @Override
        protected Object getValue() {
            final int size = (int) type.getSize();
            if (!value.canRead(offset, size)) {
                return cannotRead();
            }

            final BigInteger id = value.readUnsignedInteger(offset, size);
            if (size >= Long.SIZE) {
                return LLVMDebugObject.toHexString(id);
            }

            final Object enumVal = type.getElementName(id.longValue());
            return enumVal != null ? enumVal : cannotRead();
        }

        @Override
        public Object[] getKeys() {
            return NO_KEYS;
        }

        @Override
        public Object getMember(Object identifier) {
            return null;
        }
    }

    private static final class Structured extends LLVMDebugObject {

        // in the order of their actual declaration in the containing type
        private final Object[] memberIdentifiers;

        private final Map<Object, LLVMDebugObject> members;

        Structured(LLVMDebugValueProvider value, long offset, LLVMSourceType type, Object[] memberIdentifiers, Map<Object, LLVMDebugObject> members) {
            super(value, offset, type);
            this.memberIdentifiers = memberIdentifiers;
            this.members = members;
        }

        @Override
        public Object[] getKeys() {
            return memberIdentifiers;
        }

        @Override
        @TruffleBoundary
        public LLVMDebugObject getMember(Object key) {
            return members.get(key);
        }

        @Override
        public Object getValue() {
            return value.computeAddress(offset);
        }
    }

    private static final class Primitive extends LLVMDebugObject {

        Primitive(LLVMDebugValueProvider value, long offset, LLVMSourceType type) {
            super(value, offset, type);
        }

        @Override
        public Object[] getKeys() {
            return NO_KEYS;
        }

        @Override
        public Object getMember(Object identifier) {
            return null;
        }

        @Override
        public Object getValue() {
            final int size = (int) type.getSize();

            if (!value.canRead(offset, size)) {
                return cannotRead();
            }

            LLVMSourceType actualType = this.type;
            if (actualType instanceof LLVMSourceDecoratorType) {
                actualType = ((LLVMSourceDecoratorType) actualType).getTrueBaseType();
            }

            if (actualType.isPointer()) {
                return value.readAddress(offset);
            }

            if (actualType.isAggregate()) {
                return actualType.getName();
            }

            if (actualType instanceof LLVMSourceBasicType) {
                switch (((LLVMSourceBasicType) actualType).getKind()) {
                    case ADDRESS:
                        return value.readAddress(offset);

                    case BOOLEAN:
                        return value.readBoolean(offset);

                    case FLOATING:
                        return readFloating();

                    case SIGNED:
                        return value.readSignedInteger(offset, size);

                    case SIGNED_CHAR:
                        return (char) value.readSignedInteger(offset, size).byteValue();

                    case UNSIGNED:
                        return value.readUnsignedInteger(offset, size);

                    case UNSIGNED_CHAR:
                        return (char) Byte.toUnsignedInt(value.readSignedInteger(offset, size).byteValue());
                }
            }

            return value.readUnknown(offset, size);
        }

        private Object readFloating() {
            final int size = (int) type.getSize();
            try {
                switch (size) {
                    case Float.SIZE:
                        return value.readFloat(offset);

                    case Double.SIZE:
                        return value.readDouble(offset);

                    case LLVM80BitFloat.BIT_WIDTH:
                        return value.read80BitFloat(offset);

                    default:
                        return value.readUnknown(offset, size);
                }
            } catch (IllegalStateException e) {
                CompilerDirectives.transferToInterpreter();
                return e.getMessage();
            }
        }
    }

    private static final class Pointer extends LLVMDebugObject {

        private final LLVMSourcePointerType pointerType;

        Pointer(LLVMDebugValueProvider value, long offset, LLVMSourceType type) {
            super(value, offset, type);

            LLVMSourceType actualType = this.type;
            if (type instanceof LLVMSourceDecoratorType) {
                actualType = ((LLVMSourceDecoratorType) actualType).getTrueBaseType();
            }
            if (actualType instanceof LLVMSourcePointerType) {
                this.pointerType = (LLVMSourcePointerType) actualType;
            } else {
                this.pointerType = null;
            }
        }

        @Override
        public Object[] getKeys() {
            final LLVMDebugObject target = dereference();
            return target == null ? null : target.getKeys();
        }

        @Override
        public Object getMember(Object identifier) {
            final LLVMDebugObject target = dereference();
            return target == null ? "Cannot dereference pointer!" : target.getMember(identifier);
        }

        @Override
        protected Object getValue() {
            if (!value.canRead(offset, (int) type.getSize())) {
                return cannotRead();
            }

            return value.readAddress(offset);
        }

        private LLVMDebugObject dereference() {
            // the pointer may change at runtime, so we cannot just cache the dereferenced object
            if (pointerType == null || !pointerType.isSafeToDereference() || !value.canRead(offset, (int) type.getSize())) {
                return null;
            }

            final LLVMDebugValueProvider targetValue = value.dereferencePointer(offset);
            if (targetValue == null) {
                return null;
            }
            return instantiate(pointerType.getBaseType(), 0L, targetValue);
        }
    }

    @TruffleBoundary
    private static String toHexString(BigInteger value) {
        final byte[] bytes = value.toByteArray();
        final StringBuilder builder = new StringBuilder(bytes.length * 2 + 2);
        builder.append("0x");
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static LLVMDebugObject instantiate(LLVMSourceType type, long baseOffset, LLVMDebugValueProvider value) {
        if (type.isAggregate()) {
            return instantiateAggregate(type, baseOffset, value);

        } else if (type.isPointer()) {
            return new Pointer(value, baseOffset, type);

        } else if (type.isEnum()) {
            return new Enum(value, baseOffset, type);

        } else {
            return new Primitive(value, baseOffset, type);
        }
    }

    @TruffleBoundary
    private static LLVMDebugObject instantiateAggregate(LLVMSourceType type, long baseOffset, LLVMDebugValueProvider value) {
        final Map<Object, LLVMDebugObject> members = new HashMap<>(type.getElementCount());
        final Object[] memberIdentifiers = new Object[type.getElementCount()];
        for (int i = 0; i < type.getElementCount(); i++) {
            final LLVMSourceType elementType = type.getElementType(i);
            final String elementName = type.getElementName(i);
            final long newOffset = baseOffset + elementType.getOffset();

            final LLVMDebugObject member = instantiate(elementType, newOffset, value);
            memberIdentifiers[i] = elementName;
            members.put(elementName, member);
        }
        return new Structured(value, baseOffset, type, memberIdentifiers, members);
    }
}
