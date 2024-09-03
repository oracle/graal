/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings({"unused", "static-method"})
public class WasmType implements TruffleObject {
    public static final byte VOID_TYPE = 0x40;
    @CompilationFinal(dimensions = 1) public static final byte[] VOID_TYPE_ARRAY = {};
    public static final byte NULL_TYPE = 0x00;

    public static final byte UNKNOWN_TYPE = -1;

    /**
     * Number Types.
     */
    public static final byte I32_TYPE = 0x7F;
    @CompilationFinal(dimensions = 1) public static final byte[] I32_TYPE_ARRAY = {I32_TYPE};

    public static final byte I64_TYPE = 0x7E;
    @CompilationFinal(dimensions = 1) public static final byte[] I64_TYPE_ARRAY = {I64_TYPE};

    public static final byte F32_TYPE = 0x7D;
    @CompilationFinal(dimensions = 1) public static final byte[] F32_TYPE_ARRAY = {F32_TYPE};

    public static final byte F64_TYPE = 0x7C;
    @CompilationFinal(dimensions = 1) public static final byte[] F64_TYPE_ARRAY = {F64_TYPE};

    public static final byte V128_TYPE = 0x7B;
    @CompilationFinal(dimensions = 1) public static final byte[] V128_TYPE_ARRAY = {V128_TYPE};

    /**
     * Reference Types.
     */
    public static final byte FUNCREF_TYPE = 0x70;
    @CompilationFinal(dimensions = 1) public static final byte[] FUNCREF_TYPE_ARRAY = {FUNCREF_TYPE};
    public static final byte EXTERNREF_TYPE = 0x6F;
    @CompilationFinal(dimensions = 1) public static final byte[] EXTERNREF_TYPE_ARRAY = {EXTERNREF_TYPE};

    public static final WasmType VOID = new WasmType("void");
    public static final WasmType NULL = new WasmType("null");
    public static final WasmType MULTI_VALUE = new WasmType("multi-value");

    /**
     * Common Types.
     */
    public static final int NONE_COMMON_TYPE = 0;
    public static final int NUM_COMMON_TYPE = 1;
    public static final int OBJ_COMMON_TYPE = 2;
    public static final int MIX_COMMON_TYPE = NUM_COMMON_TYPE | OBJ_COMMON_TYPE;

    public static String toString(int valueType) {
        CompilerAsserts.neverPartOfCompilation();
        switch (valueType) {
            case I32_TYPE:
                return "i32";
            case I64_TYPE:
                return "i64";
            case F32_TYPE:
                return "f32";
            case F64_TYPE:
                return "f64";
            case V128_TYPE:
                return "v128";
            case VOID_TYPE:
                return "void";
            case FUNCREF_TYPE:
                return "funcref";
            case EXTERNREF_TYPE:
                return "externref";
            default:
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, null, "Unknown value type: 0x" + Integer.toHexString(valueType));
        }
    }

    public static boolean isNumberType(byte type) {
        return type == I32_TYPE || type == I64_TYPE || type == F32_TYPE || type == F64_TYPE || type == UNKNOWN_TYPE;
    }

    public static boolean isVectorType(byte type) {
        return type == V128_TYPE || type == UNKNOWN_TYPE;
    }

    public static boolean isReferenceType(byte type) {
        return type == FUNCREF_TYPE || type == EXTERNREF_TYPE || type == UNKNOWN_TYPE;
    }

    public static int getCommonValueType(byte[] types) {
        int type = 0;
        for (byte resultType : types) {
            type |= WasmType.isNumberType(resultType) ? NUM_COMMON_TYPE : 0;
            type |= WasmType.isVectorType(resultType) || WasmType.isReferenceType(resultType) ? OBJ_COMMON_TYPE : 0;
        }
        return type;
    }

    private final String name;

    public WasmType(String name) {
        this.name = name;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return WasmLanguage.class;
    }

    @ExportMessage
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage(name = "getMetaQualifiedName")
    @ExportMessage(name = "getMetaSimpleName")
    public Object getName() {
        return name;
    }

    @ExportMessage(name = "toDisplayString")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return toString();
    }

    @ExportMessage
    final boolean isMetaInstance(Object instance) {
        return instance instanceof WasmConstant;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return "wasm-value-type[" + name + "]";
    }
}
