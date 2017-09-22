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
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

import java.math.BigInteger;
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

    private static final String INTEROP_VALUE = "<interop value>";
    private static final String INTEROP_VALUE_KEY = "Unindexed Interop Value";
    private static final Object[] INTEROP_KEYS = new Object[]{INTEROP_VALUE_KEY};

    protected final long offset;

    protected final LLVMDebugValueProvider value;

    private final LLVMSourceType type;

    private final LLVMSourceLocation location;

    LLVMDebugObject(LLVMDebugValueProvider value, long offset, LLVMSourceType type, LLVMSourceLocation location) {
        this.value = value;
        this.offset = offset;
        this.type = type;
        this.location = location;
    }

    /**
     * Get an object describing the referenced variable's type for the debugger to show.
     *
     * @return the type of the referenced object
     */
    public LLVMSourceType getType() {
        return type;
    }

    public LLVMSourceLocation getDeclaration() {
        return location;
    }

    /**
     * If this is a complex object return the identifiers for its members.
     *
     * @return the keys or null
     */
    public Object[] getKeys() {
        if (value == null) {
            return null;

        } else if (value.isInteropValue()) {
            return INTEROP_KEYS;

        } else {
            return getKeysSafe();
        }
    }

    protected abstract Object[] getKeysSafe();

    /**
     * If this is a complex object return the member that is identified by the given key.
     *
     * @param identifier the object identifying the member
     *
     * @return the member or {@code null} if the key does not identify a member
     */
    public Object getMember(Object identifier) {
        if (identifier == null) {
            return null;

        } else if (INTEROP_VALUE_KEY.equals(identifier)) {
            return value.asInteropValue();

        } else {
            return getMemberSafe(identifier);
        }
    }

    protected abstract Object getMemberSafe(Object identifier);

    /**
     * Return an object that represents the value of the referenced variable.
     *
     * @return the value of the referenced variable
     */
    protected Object getValue() {
        if (value == null) {
            return "";

        } else if (value.isInteropValue()) {
            return INTEROP_VALUE;

        } else {
            return getValueSafe();
        }
    }

    protected abstract Object getValueSafe();

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

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMDebugObjectMessageResolutionForeign.ACCESS;
    }

    private static final class Enum extends LLVMDebugObject {

        Enum(LLVMDebugValueProvider value, long offset, LLVMSourceType type, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);
        }

        @Override
        protected Object getValueSafe() {
            final int size = (int) getType().getSize();

            final Object idRead = value.readBigInteger(offset, size, false);
            final BigInteger id;
            if (idRead instanceof BigInteger) {
                id = (BigInteger) idRead;
            } else {
                return value.describeValue(offset, size);
            }

            if (size >= Long.SIZE) {
                return LLVMDebugValueProvider.toHexString(id);
            }

            final Object enumVal = getType().getElementName(id.longValue());
            return enumVal != null ? enumVal : LLVMDebugValueProvider.toHexString(id);
        }

        @Override
        public Object[] getKeysSafe() {
            return NO_KEYS;
        }

        @Override
        public Object getMemberSafe(Object identifier) {
            return null;
        }
    }

    private static final class Structured extends LLVMDebugObject {

        // in the order of their actual declaration in the containing type
        private final Object[] memberIdentifiers;

        Structured(LLVMDebugValueProvider value, long offset, LLVMSourceType type, Object[] memberIdentifiers, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);
            this.memberIdentifiers = memberIdentifiers;
        }

        @Override
        public Object[] getKeysSafe() {
            return memberIdentifiers;
        }

        @Override
        public Object getMemberSafe(Object key) {
            if (key instanceof String) {
                final LLVMSourceType elementType = getType().getElementType((String) key);
                final long newOffset = this.offset + elementType.getOffset();
                final LLVMSourceLocation declaration = getType().getElementDeclaration((String) key);
                return instantiate(elementType, newOffset, value, declaration);
            }
            return null;
        }

        @Override
        public Object getValueSafe() {
            return value.computeAddress(offset);
        }
    }

    private static final class Primitive extends LLVMDebugObject {

        Primitive(LLVMDebugValueProvider value, long offset, LLVMSourceType type, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);
        }

        @Override
        public Object[] getKeysSafe() {
            return NO_KEYS;
        }

        @Override
        public Object getMemberSafe(Object identifier) {
            return null;
        }

        @Override
        public Object getValueSafe() {
            final int size = (int) getType().getSize();

            LLVMSourceType actualType = getType();
            if (actualType instanceof LLVMSourceDecoratorType) {
                actualType = ((LLVMSourceDecoratorType) actualType).getTrueBaseType();
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
                        return value.readBigInteger(offset, size, true);

                    case SIGNED_CHAR: {
                        final Object intRead = value.readBigInteger(offset, size, true);
                        if (intRead instanceof BigInteger) {
                            return (char) ((BigInteger) intRead).byteValue();
                        } else {
                            return intRead;
                        }
                    }

                    case UNSIGNED:
                        return value.readBigInteger(offset, size, false);

                    case UNSIGNED_CHAR: {
                        final Object intRead = value.readBigInteger(offset, size, false);
                        if (intRead instanceof BigInteger) {
                            return (char) Byte.toUnsignedInt(((BigInteger) intRead).byteValue());
                        } else {
                            return intRead;
                        }
                    }
                }
            }

            return value.readUnknown(offset, size);
        }

        // clang uses the x86_fp80 datatype to represent the long double type which is indicated in
        // metadata to have 128 bits
        private static final int LONGDOUBLE_SIZE = 128;

        private Object readFloating() {
            final int size = (int) getType().getSize();
            try {
                switch (size) {
                    case Float.SIZE:
                        return value.readFloat(offset);

                    case Double.SIZE:
                        return value.readDouble(offset);

                    case LLVM80BitFloat.BIT_WIDTH:
                    case LONGDOUBLE_SIZE:
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

        Pointer(LLVMDebugValueProvider value, long offset, LLVMSourceType type, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);

            LLVMSourceType actualType = getType();
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
        public Object[] getKeysSafe() {
            final LLVMDebugObject target = dereference();
            return target == null ? null : target.getKeys();
        }

        @Override
        public Object getMemberSafe(Object identifier) {
            final LLVMDebugObject target = dereference();
            return target == null ? "Cannot dereference pointer!" : target.getMember(identifier);
        }

        @Override
        protected Object getValueSafe() {
            return value.readAddress(offset);
        }

        private LLVMDebugObject dereference() {
            // the pointer may change at runtime, so we cannot just cache the dereferenced object
            if (pointerType == null || !pointerType.isSafeToDereference()) {
                return null;
            }

            final LLVMDebugValueProvider targetValue = value.dereferencePointer(offset);
            if (targetValue == null) {
                return null;
            }
            return instantiate(pointerType.getBaseType(), 0L, targetValue, null);
        }
    }

    public static LLVMDebugObject instantiate(LLVMSourceType type, long baseOffset, LLVMDebugValueProvider value, LLVMSourceLocation declaration) {
        if (type.isAggregate()) {
            int elementCount = type.getElementCount();
            if (elementCount < 0) {
                // happens for dynamically initialized arrays
                elementCount = 0;
            }
            final Object[] memberIdentifiers = new Object[elementCount];
            for (int i = 0; i < elementCount; i++) {
                memberIdentifiers[i] = type.getElementName(i);
            }
            return new Structured(value, baseOffset, type, memberIdentifiers, declaration);

        } else if (type.isPointer()) {
            return new Pointer(value, baseOffset, type, declaration);

        } else if (type.isEnum()) {
            return new Enum(value, baseOffset, type, declaration);

        } else {
            return new Primitive(value, baseOffset, type, declaration);
        }
    }
}
