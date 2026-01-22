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

import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.array.WasmArray;
import org.graalvm.wasm.exception.WasmRuntimeException;
import org.graalvm.wasm.struct.WasmStruct;

public enum AbstractHeapType implements HeapType {

    NOEXN(WasmType.NOEXN_HEAPTYPE),
    NOFUNC(WasmType.NOFUNC_HEAPTYPE),
    NOEXTERN(WasmType.NOEXTERN_HEAPTYPE),
    NONE(WasmType.NONE_HEAPTYPE),
    FUNC(WasmType.FUNC_HEAPTYPE),
    EXTERN(WasmType.EXTERN_HEAPTYPE),
    ANY(WasmType.ANY_HEAPTYPE),
    EQ(WasmType.EQ_HEAPTYPE),
    I31(WasmType.I31_HEAPTYPE),
    STRUCT(WasmType.STRUCT_HEAPTYPE),
    ARRAY(WasmType.ARRAY_HEAPTYPE),
    EXN(WasmType.EXN_HEAPTYPE);

    private final int value;

    AbstractHeapType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    @Override
    public HeapKind heapKind() {
        return HeapKind.Abstract;
    }

    @Override
    public boolean isSubtypeOf(HeapType that) {
        return switch (this) {
            case NOEXN -> that == NOEXN || that == EXN;
            case NOFUNC -> that.isFunctionType();
            case NOEXTERN -> that == NOEXTERN || that == EXTERN;
            case NONE -> that.isStructType() || that.isArrayType() || that == I31 || that == EQ || that == ANY;
            case FUNC -> that == FUNC;
            case EXTERN -> that == EXTERN;
            case ANY -> that == ANY;
            case EQ -> that == EQ || that == ANY;
            case I31 -> that == I31 || that == EQ || that == ANY;
            case STRUCT -> that == STRUCT || that == EQ || that == ANY;
            case ARRAY -> that == ARRAY || that == EQ || that == ANY;
            case EXN -> that == EXN;
        };
    }

    @Override
    public boolean isArrayType() {
        return this == ARRAY || this == NONE;
    }

    @Override
    public boolean isStructType() {
        return this == STRUCT || this == NONE;
    }

    @Override
    public boolean isFunctionType() {
        return this == FUNC || this == NOFUNC;
    }

    @Override
    public boolean matchesValue(Object val) {
        return switch (this) {
            case NOEXN, NOFUNC, NOEXTERN, NONE -> false;
            case FUNC -> val instanceof WasmFunctionInstance;
            case EXTERN, ANY -> val != WasmConstant.NULL;
            case EQ -> val instanceof WasmArray || val instanceof WasmStruct || (val instanceof Integer integer && integer >= 0);
            case I31 -> val instanceof Integer integer && integer >= 0;
            case STRUCT -> val instanceof WasmStruct;
            case ARRAY -> val instanceof WasmArray;
            case EXN -> val instanceof WasmRuntimeException;
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case NOEXN -> "noexn";
            case NOFUNC -> "nofunc";
            case NOEXTERN -> "noextern";
            case NONE -> "none";
            case FUNC -> "func";
            case EXTERN -> "extern";
            case ANY -> "any";
            case EQ -> "eq";
            case I31 -> "i31";
            case STRUCT -> "struct";
            case ARRAY -> "array";
            case EXN -> "exn";
        };
    }
}
