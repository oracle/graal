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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.llvm.runtime.debug.LLVMDebugBasicType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

import java.util.Map;
import java.util.Objects;

abstract class LLVMDebugObjectImpl implements LLVMDebugObject {

    private static final Object[] NO_KEYS = new Object[0];

    protected final long offset;

    protected final LLVMDebugValueProvider value;

    protected final LLVMDebugType type;

    LLVMDebugObjectImpl(LLVMDebugValueProvider value, long offset, LLVMDebugType type) {
        this.value = value;
        this.offset = offset;
        this.type = type;
    }

    abstract Object getValue();

    Object cannotRead() {
        return String.format("Cannot read %d bits from offset %d in %s", type.getSize(), offset, value);
    }

    @Override
    public Object getType() {
        return type;
    }

    @Override
    public String toString() {
        return Objects.toString(getValue());
    }

    static final class Enum extends LLVMDebugObjectImpl {

        Enum(LLVMDebugValueProvider value, long offset, LLVMDebugType type) {
            super(value, offset, type);
        }

        @Override
        Object getValue() {
            // TODO this needs to be improved
            if (!value.canReadId(offset / Byte.SIZE, (int) type.getSize())) {
                return "<unknown>";
            }
            final long id = value.readId(offset / Byte.SIZE, (int) type.getSize());
            final Object val = type.getElementName(id);
            return val != null ? val : "<unknown>";
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

    static final class Primitive extends LLVMDebugObjectImpl {

        Primitive(LLVMDebugValueProvider value, long offset, LLVMDebugType type) {
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
            if (!value.canRead()) {
                return cannotRead();
            }

            LLVMDebugType actualType = this.type;
            if (actualType instanceof LLVMDebugDecoratorType) {
                actualType = ((LLVMDebugDecoratorType) actualType).getTrueBaseType();
            }

            if (actualType.isPointer()) {
                return value.readAddress(offset / Byte.SIZE);
            }

            if (actualType.isAggregate()) {
                return actualType.getName();
            }

            if (actualType instanceof LLVMDebugBasicType) {
                switch (((LLVMDebugBasicType) actualType).getKind()) {
                    case ADDRESS:
                        return value.readAddress(offset / Byte.SIZE);

                    case BOOLEAN:
                        return value.readBoolean(offset / Byte.SIZE);

                    case FLOATING:
                        return readFloating();

                    case SIGNED:
                        return readSigned();

                    case SIGNED_CHAR:
                        return value.readCharSigned(offset / Byte.SIZE);

                    case UNSIGNED:
                        return readUnsigned();

                    case UNSIGNED_CHAR:
                        return value.readCharUnsigned(offset / Byte.SIZE);
                }
            }

            return value.readUnknown(offset / Byte.SIZE, type.getSize());
        }

        private Object readFloating() {
            switch ((int) type.getSize()) {
                case Float.SIZE:
                    return value.readFloat(offset / Byte.SIZE);

                case Double.SIZE:
                    return value.readDouble(offset / Byte.SIZE);

                case LLVM80BitFloat.BIT_WIDTH:
                    return value.read80BitFloat(offset / Byte.SIZE);

                default:
                    return value.readUnknown(offset / Byte.SIZE, type.getSize());
            }
        }

        private Object readSigned() {
            switch ((int) type.getSize()) {
                case Byte.SIZE:
                    return value.readByteSigned(offset / Byte.SIZE);

                case Short.SIZE:
                    return value.readShortSigned(offset / Byte.SIZE);

                case Integer.SIZE:
                    return value.readIntSigned(offset / Byte.SIZE);

                case Long.SIZE:
                    return value.readLongSigned(offset / Byte.SIZE);

                default:
                    return readBitFieldInteger(true);
            }
        }

        private Object readUnsigned() {
            switch ((int) type.getSize()) {
                case Byte.SIZE:
                    return value.readByteUnsigned(offset / Byte.SIZE);

                case Short.SIZE:
                    return value.readShortUnsigned(offset / Byte.SIZE);

                case Integer.SIZE:
                    return value.readIntUnsigned(offset / Byte.SIZE);

                case Long.SIZE:
                    return value.readLongUnsigned(offset / Byte.SIZE);

                default:
                    return readBitFieldInteger(false);
            }

        }

        private Object readBitFieldInteger(boolean signed) {
            // bitfields in a structured object may have arbitrary size
            long field;
            if (type.getSize() < Byte.SIZE) {
                field = value.readByteUnsigned(offset / Byte.SIZE);
            } else if (type.getSize() < Short.SIZE) {
                field = value.readShortUnsigned(offset / Byte.SIZE);
            } else if (type.getSize() < Integer.SIZE) {
                field = value.readIntUnsigned(offset / Byte.SIZE);
            } else if (type.getSize() < Long.SIZE) {
                field = value.readLongSigned(offset / Byte.SIZE);
            } else {
                return cannotRead();
            }

            long shift = offset & (Byte.SIZE - 1);
            if (shift != 0) {
                field >>= shift;
            }

            shift = Long.SIZE - type.getSize();
            field <<= shift;

            if (signed) {
                field >>= shift;
            } else {
                field >>>= shift;
            }

            return field;
        }
    }

    static final class Structured extends LLVMDebugObjectImpl {

        // in the order of their actual declaration in the containing type
        private final Object[] memberIdentifiers;

        private final Map<Object, LLVMDebugObject> members;

        Structured(LLVMDebugValueProvider value, long offset, LLVMDebugType type, Object[] memberIdentifiers, Map<Object, LLVMDebugObject> members) {
            super(value, offset, type);
            this.memberIdentifiers = memberIdentifiers;
            this.members = members;
        }

        @Override
        public Object[] getKeys() {
            return memberIdentifiers;
        }

        @Override
        public LLVMDebugObject getMember(Object key) {
            return members.get(key);
        }

        @Override
        Object getValue() {
            return value.computeAddress(offset / Byte.SIZE);
        }
    }
}
