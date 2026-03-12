/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.globals;

import org.graalvm.wasm.EmbedderDataHolder;
import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmNamesObject;
import org.graalvm.wasm.types.NumberType;
import org.graalvm.wasm.types.ValueType;
import org.graalvm.wasm.vector.Vector128;
import org.graalvm.wasm.constants.Mutability;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressWarnings("static-method")
@ExportLibrary(InteropLibrary.class)
public final class WasmGlobal implements TruffleObject, EmbedderDataHolder {

    private final ValueType valueType;
    private final boolean mutable;

    private long globalValue;
    private Object globalObjectValue;

    private Object embedderData = WasmConstant.VOID;

    private WasmGlobal(ValueType valueType, boolean mutable) {
        this.valueType = valueType;
        this.mutable = mutable;
    }

    public static WasmGlobal alloc32(ValueType valueType, boolean mutable, int value) {
        return alloc64(valueType, mutable, value);
    }

    public static WasmGlobal alloc64(ValueType valueType, boolean mutable, long value) {
        assert valueType.isNumberType();
        WasmGlobal result = new WasmGlobal(valueType, mutable);
        result.globalValue = value;
        return result;
    }

    public static WasmGlobal allocRef(ValueType valueType, boolean mutable, Object value) {
        assert valueType.isReferenceType();
        WasmGlobal result = new WasmGlobal(valueType, mutable);
        result.globalObjectValue = value;
        return result;
    }

    public WasmGlobal(int globalIndex, SymbolTable symbolTable, Object value) {
        this(symbolTable.closedTypeOf(symbolTable.globalValueType(globalIndex)), symbolTable.isGlobalMutable(globalIndex));
        assert symbolTable.globalExternal(globalIndex);
        this.globalValue = switch (valueType.valueKind()) {
            case Number -> switch ((NumberType) valueType) {
                case I32 -> (int) value;
                case I64 -> (long) value;
                case F32 -> Float.floatToRawIntBits((float) value);
                case F64 -> Double.doubleToRawLongBits((double) value);
            };
            default -> 0;
        };
        this.globalObjectValue = switch (valueType.valueKind()) {
            case Vector, Reference -> value;
            default -> null;
        };
    }

    public ValueType getValueType() {
        return valueType;
    }

    public boolean isMutable() {
        return mutable;
    }

    public byte getMutability() {
        return mutable ? Mutability.MUTABLE : Mutability.CONSTANT;
    }

    public int loadAsInt() {
        assert valueType.isNumberType();
        return (int) globalValue;
    }

    public long loadAsLong() {
        assert valueType.isNumberType();
        return globalValue;
    }

    public Vector128 loadAsVector128() {
        assert valueType.isVectorType();
        assert globalObjectValue != null;
        return (Vector128) globalObjectValue;
    }

    public Object loadAsReference() {
        assert valueType.isReferenceType();
        assert globalObjectValue != null;
        return globalObjectValue;
    }

    public void storeInt(int value) {
        assert valueType.isNumberType();
        this.globalValue = value;
    }

    public void storeLong(long value) {
        assert valueType.isNumberType();
        this.globalValue = value;
    }

    public void storeVector128(Vector128 value) {
        assert valueType.isVectorType();
        this.globalObjectValue = value;
    }

    public void storeReference(Object value) {
        assert valueType.isReferenceType();
        this.globalObjectValue = value;
    }

    public static final String VALUE_MEMBER = "value";

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        return VALUE_MEMBER.equals(member);
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        if (!isMemberReadable(member)) {
            throw UnknownIdentifierException.create(member);
        }
        assert VALUE_MEMBER.equals(member) : member;
        return switch (valueType.valueKind()) {
            case Number -> switch ((NumberType) valueType) {
                case I32 -> loadAsInt();
                case I64 -> loadAsLong();
                case F32 -> Float.intBitsToFloat(loadAsInt());
                case F64 -> Double.longBitsToDouble(loadAsLong());
            };
            case Vector -> loadAsVector128();
            case Reference -> loadAsReference();
        };
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberModifiable(String member) {
        return VALUE_MEMBER.equals(member) && isMutable();
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    void writeMember(String member, Object value,
                    @CachedLibrary(limit = "5") InteropLibrary valueLibrary) throws UnknownIdentifierException, UnsupportedMessageException {
        if (!isMemberReadable(member)) {
            throw UnknownIdentifierException.create(member);
        }
        if (!mutable) {
            // Constant variables cannot be modified after linking.
            throw UnsupportedMessageException.create();
        }
        switch (valueType.valueKind()) {
            case Number -> {
                switch ((NumberType) valueType) {
                    case I32 -> storeInt(valueLibrary.asInt(value));
                    case I64 -> storeLong(valueLibrary.asLong(value));
                    case F32 -> storeInt(Float.floatToRawIntBits(valueLibrary.asFloat(value)));
                    case F64 -> storeLong(Double.doubleToRawLongBits(valueLibrary.asDouble(value)));
                }
            }
            case Vector -> {
                if (!valueType.matchesValue(value)) {
                    throw UnsupportedMessageException.create();
                }
                storeVector128((Vector128) value);
            }
            case Reference -> {
                if (!valueType.matchesValue(value)) {
                    throw UnsupportedMessageException.create();
                }
                storeReference(value);
            }
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new WasmNamesObject(new String[]{VALUE_MEMBER});
    }

    @Override
    public Object getEmbedderData() {
        return embedderData;
    }

    @Override
    public void setEmbedderData(Object embedderData) {
        this.embedderData = embedderData;
    }
}
