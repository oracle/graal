/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.struct;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.staticobject.StaticProperty;
import org.graalvm.wasm.WasmTypedHeapObject;
import org.graalvm.wasm.api.InteropArray;
import org.graalvm.wasm.constants.Mutability;
import org.graalvm.wasm.types.DefinedType;
import org.graalvm.wasm.types.NumberType;
import org.graalvm.wasm.types.PackedType;
import org.graalvm.wasm.types.StorageType;
import org.graalvm.wasm.types.ValueType;

@ExportLibrary(InteropLibrary.class)
public class WasmStruct extends WasmTypedHeapObject {

    public WasmStruct(DefinedType type) {
        super(type);
        assert type.isStructType();
    }

    @ExportMessage
    protected static boolean hasMembers(@SuppressWarnings("unused") WasmStruct receiver) {
        return true;
    }

    @ExportMessage
    protected InteropArray getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        Object[] members = new Object[type().asStructType().fieldTypes().length];
        for (int i = 0; i < members.length; i++) {
            members[i] = integerToString(i);
        }
        return InteropArray.create(members);
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        try {
            return parseInt(member) < type().asStructType().fieldTypes().length;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        try {
            int fieldIndex = parseInt(member);
            if (fieldIndex >= type().asStructType().fieldTypes().length) {
                return false;
            }
            if (type().asStructType().fieldTypes()[fieldIndex].mutability() == Mutability.CONSTANT) {
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @ExportMessage
    protected Object readMember(String member) throws UnknownIdentifierException {
        assert isMemberReadable(member);
        try {
            int fieldIndex = parseInt(member);
            if (fieldIndex >= type().asStructType().fieldTypes().length) {
                throw UnknownIdentifierException.create(member);
            }
            return readField(fieldIndex);
        } catch (NumberFormatException e) {
            throw UnknownIdentifierException.create(member);
        }
    }

    private Object readField(int fieldIndex) {
        StorageType fieldType = type().asStructType().fieldTypes()[fieldIndex].storageType();
        StaticProperty property = type().structAccess().properties()[fieldIndex];
        return switch (fieldType.storageKind()) {
            case Packed -> switch ((PackedType) fieldType) {
                case I8 -> property.getByte(this);
                case I16 -> property.getShort(this);
            };
            case Value -> switch (((ValueType) fieldType).valueKind()) {
                case Number -> switch ((NumberType) fieldType) {
                    case I32 -> property.getInt(this);
                    case I64 -> property.getLong(this);
                    case F32 -> property.getFloat(this);
                    case F64 -> property.getDouble(this);
                };
                case Vector, Reference -> property.getObject(this);
            };
        };
    }

    @ExportMessage
    protected void writeMember(String member, Object value) throws UnknownIdentifierException, UnsupportedTypeException {
        assert isMemberModifiable(member);
        try {
            int fieldIndex = parseInt(member);
            if (fieldIndex >= type().asStructType().fieldTypes().length) {
                throw UnknownIdentifierException.create(member);
            }
            if (type().asStructType().fieldTypes()[fieldIndex].mutability() == Mutability.CONSTANT) {
                throw UnknownIdentifierException.create(member);
            }
            writeField(fieldIndex, value);
        } catch (NumberFormatException e) {
            throw UnknownIdentifierException.create(member);
        }
    }

    private void writeField(int fieldIndex, Object value) throws UnsupportedTypeException {
        StorageType fieldType = type().asStructType().fieldTypes()[fieldIndex].storageType();
        StaticProperty property = type().structAccess().properties()[fieldIndex];
        if (!fieldType.javaClass().isInstance(value)) {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
        switch (fieldType.storageKind()) {
            case Packed -> {
                switch ((PackedType) fieldType) {
                    case I8 -> property.setByte(this, (byte) value);
                    case I16 -> property.setShort(this, (short) value);
                }
            }
            case Value -> {
                switch (((ValueType) fieldType).valueKind()) {
                    case Number -> {
                        switch ((NumberType) fieldType) {
                            case I32 -> property.setInt(this, (int) value);
                            case I64 -> property.setLong(this, (long) value);
                            case F32 -> property.setFloat(this, (float) value);
                            case F64 -> property.setDouble(this, (double) value);
                        }
                    }
                    case Vector, Reference -> property.setObject(this, value);
                }
            }
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    protected static boolean isMemberInsertable(WasmStruct receiver, String member) {
        return false;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("wasm-struct:<");
        int numFields = type().asStructType().fieldTypes().length;
        for (int fieldIndex = 0; fieldIndex < numFields; fieldIndex++) {
            sb.append(readField(fieldIndex));
            if (fieldIndex < numFields - 1) {
                sb.append(", ");
            }
        }
        sb.append(">");
        return sb.toString();
    }

    @ExportMessage
    protected String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return toString();
    }

    @TruffleBoundary
    private static int parseInt(String member) {
        return Integer.parseInt(member);
    }

    @TruffleBoundary
    private static String integerToString(int fieldIndex) {
        return Integer.toString(fieldIndex);
    }
}
