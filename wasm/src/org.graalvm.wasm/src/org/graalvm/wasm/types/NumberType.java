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
package org.graalvm.wasm.types;

import org.graalvm.wasm.WasmType;

public enum NumberType implements ValueType {

    I32(WasmType.I32_TYPE),
    I64(WasmType.I64_TYPE),
    F32(WasmType.F32_TYPE),
    F64(WasmType.F64_TYPE);

    private final int value;

    NumberType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.Number;
    }

    @Override
    public boolean isSubtypeOf(ValueType that) {
        return this == that;
    }

    @Override
    public boolean isSubtypeOf(StorageType that) {
        return this == that;
    }

    @Override
    public Class<?> javaClass() {
        return switch (this) {
            case I32 -> int.class;
            case I64 -> long.class;
            case F32 -> float.class;
            case F64 -> double.class;
        };
    }

    @Override
    public boolean matchesValue(Object val) {
        return switch (this) {
            case I32 -> val instanceof Integer;
            case I64 -> val instanceof Long;
            case F32 -> val instanceof Float;
            case F64 -> val instanceof Double;
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case I32 -> "i32";
            case I64 -> "i64";
            case F32 -> "f32";
            case F64 -> "f64";
        };
    }
}
