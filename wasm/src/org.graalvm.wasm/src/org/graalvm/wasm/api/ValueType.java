/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.api;

import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerAsserts;

public enum ValueType {
    i32(WasmType.I32_TYPE),
    i64(WasmType.I64_TYPE),
    f32(WasmType.F32_TYPE),
    f64(WasmType.F64_TYPE),
    v128(WasmType.V128_TYPE),
    anyfunc(WasmType.FUNCREF_TYPE),
    externref(WasmType.EXTERNREF_TYPE);

    private final byte byteValue;

    ValueType(byte byteValue) {
        this.byteValue = byteValue;
    }

    public static ValueType fromByteValue(byte value) {
        CompilerAsserts.neverPartOfCompilation();
        switch (value) {
            case WasmType.I32_TYPE:
                return i32;
            case WasmType.I64_TYPE:
                return i64;
            case WasmType.F32_TYPE:
                return f32;
            case WasmType.F64_TYPE:
                return f64;
            case WasmType.V128_TYPE:
                return v128;
            case WasmType.FUNCREF_TYPE:
                return anyfunc;
            case WasmType.EXTERNREF_TYPE:
                return externref;
            default:
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, null, "Unknown value type: 0x" + Integer.toHexString(value));
        }
    }

    public byte byteValue() {
        return byteValue;
    }

    public static boolean isNumberType(ValueType valueType) {
        return valueType == i32 || valueType == i64 || valueType == f32 || valueType == f64;
    }

    public static boolean isVectorType(ValueType valueType) {
        return valueType == v128;
    }

    public static boolean isReferenceType(ValueType valueType) {
        return valueType == anyfunc || valueType == externref;
    }
}
