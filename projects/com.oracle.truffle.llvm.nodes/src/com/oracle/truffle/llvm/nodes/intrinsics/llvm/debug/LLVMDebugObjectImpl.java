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

import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugBasicType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugType;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

import java.util.Map;
import java.util.Objects;

abstract class LLVMDebugObjectImpl implements LLVMDebugObject {

    private static final String UNAVAILABLE = "<unavailable>";

    private static final Object[] NO_KEYS = new Object[0];

    protected final LLVMAddress address;

    protected final LLVMDebugType type;

    LLVMDebugObjectImpl(LLVMAddress address, LLVMDebugType type) {
        this.address = address;
        this.type = type;
    }

    abstract Object getValue();

    @Override
    public Object getType() {
        return type;
    }

    @Override
    public String toString() {
        return Objects.toString(getValue());
    }

    static final class Enum extends LLVMDebugObjectImpl {

        Enum(LLVMAddress address, LLVMDebugType type) {
            super(address, type);
        }

        @Override
        Object getValue() {
            if (address.equals(LLVMAddress.nullPointer())) {
                return UNAVAILABLE;
            }

            long id;
            switch ((int) type.getSize()) {
                case Byte.SIZE:
                    id = LLVMMemory.getI8(address);
                    break;

                case Short.SIZE:
                    id = LLVMMemory.getI16(address);
                    break;

                case Integer.SIZE:
                    id = LLVMMemory.getI32(address);
                    break;

                case Long.SIZE:
                    id = LLVMMemory.getI64(address);
                    break;

                default:
                    return "<unknown>";
            }
            return type.getElementName(id);
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

        Primitive(LLVMAddress address, LLVMDebugType type) {
            super(address, type);
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
            if (address.equals(LLVMAddress.nullPointer())) {
                return UNAVAILABLE;
            }

            LLVMDebugType actualType = this.type;
            if (actualType instanceof LLVMDebugDecoratorType) {
                actualType = ((LLVMDebugDecoratorType) actualType).getTrueBaseType();
            }

            if (actualType.isPointer()) {
                return LLVMMemory.getAddress(address);
            }

            if (actualType.isAggregate()) {
                return actualType.getName();
            }

            if (actualType instanceof LLVMDebugBasicType) {
                switch (((LLVMDebugBasicType) actualType).getKind()) {
                    case ADDRESS:
                        return LLVMMemory.getAddress(address);

                    case BOOLEAN:
                        return LLVMMemory.getI1(address);

                    case FLOATING:
                        return readFloating();

                    case SIGNED:
                        return readSigned();

                    case SIGNED_CHAR:
                        return (char) LLVMMemory.getI8(address);

                    case UNSIGNED:
                        return readUnsigned();

                    case UNSIGNED_CHAR:
                        return (char) Byte.toUnsignedInt(LLVMMemory.getI8(address));
                }
            }

            return readUnknown();
        }

        private Object readFloating() {
            switch ((int) type.getSize()) {
                case Float.SIZE:
                    return LLVMMemory.getFloat(address);

                case Double.SIZE:
                    return LLVMMemory.getDouble(address);

                case LLVM80BitFloat.BIT_WIDTH:
                    return LLVMMemory.get80BitFloat(address);

                default:
                    return readUnknown();
            }
        }

        private Object readSigned() {
            switch ((int) type.getSize()) {
                case Byte.SIZE:
                    return LLVMMemory.getI8(address);

                case Short.SIZE:
                    return LLVMMemory.getI16(address);

                case Integer.SIZE:
                    return LLVMMemory.getI32(address);

                case Long.SIZE:
                    return LLVMMemory.getI64(address);

                default:
                    return readUnknown();
            }
        }

        private Object readUnsigned() {
            switch ((int) type.getSize()) {
                case Byte.SIZE:
                    return Byte.toUnsignedInt(LLVMMemory.getI8(address));

                case Short.SIZE:
                    return Short.toUnsignedInt(LLVMMemory.getI16(address));

                case Integer.SIZE:
                    return Integer.toUnsignedLong(LLVMMemory.getI32(address));

                case Long.SIZE:
                    return Long.toUnsignedString(LLVMMemory.getI64(address));

                default:
                    return readUnknown();
            }
        }

        private Object readUnknown() {
            return "unknown content of " + type.getSize() + " bits at " + address;
        }
    }

    static final class Complex extends LLVMDebugObjectImpl {

        // in the order of their actual declaration in the containing type
        private final Object[] memberIdentifiers;

        private final Map<Object, LLVMDebugObject> members;

        Complex(LLVMAddress address, LLVMDebugType type, Object[] memberIdentifiers, Map<Object, LLVMDebugObject> members) {
            super(address, type);
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
            return address;
        }
    }
}
