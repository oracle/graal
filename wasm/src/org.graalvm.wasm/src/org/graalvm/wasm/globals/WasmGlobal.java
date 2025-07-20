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
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmNamesObject;
import org.graalvm.wasm.api.ValueType;
import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.constants.GlobalModifier;

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
public final class WasmGlobal extends EmbedderDataHolder implements TruffleObject {

    private final ValueType valueType;
    private final boolean mutable;

    private long globalValue;
    private Object globalObjectValue;

    private WasmGlobal(ValueType valueType, boolean mutable) {
        this.valueType = valueType;
        this.mutable = mutable;
    }

    public WasmGlobal(ValueType valueType, boolean mutable, int value) {
        this(valueType, mutable, (long) value);
    }

    public WasmGlobal(ValueType valueType, boolean mutable, long value) {
        this(valueType, mutable);
        assert ValueType.isNumberType(getValueType());
        this.globalValue = value;
    }

    public WasmGlobal(ValueType valueType, boolean mutable, Object value) {
        this(valueType, mutable);
        this.globalValue = switch (valueType) {
            case i32 -> (int) value;
            case i64 -> (long) value;
            case f32 -> Float.floatToRawIntBits((float) value);
            case f64 -> Double.doubleToRawLongBits((double) value);
            default -> 0;
        };
        this.globalObjectValue = switch (valueType) {
            case v128, anyfunc, externref -> value;
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
        return mutable ? GlobalModifier.MUTABLE : GlobalModifier.CONSTANT;
    }

    public int loadAsInt() {
        assert ValueType.isNumberType(getValueType());
        return (int) globalValue;
    }

    public long loadAsLong() {
        assert ValueType.isNumberType(getValueType());
        return globalValue;
    }

    public Vector128 loadAsVector128() {
        assert ValueType.isVectorType(getValueType());
        assert globalObjectValue != null;
        return (Vector128) globalObjectValue;
    }

    public Object loadAsReference() {
        assert ValueType.isReferenceType(getValueType());
        assert globalObjectValue != null;
        return globalObjectValue;
    }

    public void storeInt(int value) {
        assert ValueType.isNumberType(getValueType());
        this.globalValue = value;
    }

    public void storeLong(long value) {
        assert ValueType.isNumberType(getValueType());
        this.globalValue = value;
    }

    public void storeVector128(Vector128 value) {
        assert ValueType.isVectorType(getValueType());
        this.globalObjectValue = value;
    }

    public void storeReference(Object value) {
        assert ValueType.isReferenceType(getValueType());
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
        return switch (getValueType()) {
            case i32 -> loadAsInt();
            case i64 -> loadAsLong();
            case f32 -> Float.intBitsToFloat(loadAsInt());
            case f64 -> Double.longBitsToDouble(loadAsLong());
            case v128 -> loadAsVector128();
            case anyfunc, externref -> loadAsReference();
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
        switch (getValueType()) {
            case i32 -> storeInt(valueLibrary.asInt(value));
            case i64 -> storeLong(valueLibrary.asLong(value));
            case f32 -> storeInt(Float.floatToRawIntBits(valueLibrary.asFloat(value)));
            case f64 -> storeLong(Double.doubleToRawLongBits(valueLibrary.asDouble(value)));
            case v128 -> {
                if (value instanceof Vector128 vector) {
                    storeVector128(vector);
                }
                throw UnsupportedMessageException.create();
            }
            case anyfunc -> {
                if (value == WasmConstant.NULL || value instanceof WasmFunctionInstance) {
                    storeReference(value);
                }
                throw UnsupportedMessageException.create();
            }
            case externref -> {
                if (value instanceof TruffleObject) {
                    storeReference(value);
                }
                throw UnsupportedMessageException.create();
            }
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new WasmNamesObject(new String[]{VALUE_MEMBER});
    }
}
