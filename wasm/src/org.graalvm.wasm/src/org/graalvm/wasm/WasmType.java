/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
    private static final int TYPE_INDEX_BITS = 30;
    private static final int TYPE_VALUE_MASK = (1 << TYPE_INDEX_BITS) - 1;
    private static final int TYPE_NULLABLE_MASK = 1 << TYPE_INDEX_BITS;
    private static final int TYPE_PREDEFINED_MASK = 1 << (TYPE_INDEX_BITS + 1);

    public static final int MAX_TYPE_INDEX = TYPE_VALUE_MASK;
    /**
     * Number Types.
     */
    public static final int I32_TYPE = -0x01;
    @CompilationFinal(dimensions = 1) public static final int[] I32_TYPE_ARRAY = {I32_TYPE};

    public static final int I64_TYPE = -0x02;
    @CompilationFinal(dimensions = 1) public static final int[] I64_TYPE_ARRAY = {I64_TYPE};

    public static final int F32_TYPE = -0x03;
    @CompilationFinal(dimensions = 1) public static final int[] F32_TYPE_ARRAY = {F32_TYPE};

    public static final int F64_TYPE = -0x04;
    @CompilationFinal(dimensions = 1) public static final int[] F64_TYPE_ARRAY = {F64_TYPE};

    /**
     * Vector Type.
     */
    public static final int V128_TYPE = -0x05;
    @CompilationFinal(dimensions = 1) public static final int[] V128_TYPE_ARRAY = {V128_TYPE};

    /**
     * Reference Types.
     */
    public static final int FUNC_HEAPTYPE = -0x10;
    public static final int EXTERN_HEAPTYPE = -0x11;
    public static final int EXN_HEAPTYPE = -0x17;

    public static final int FUNCREF_TYPE = FUNC_HEAPTYPE;
    @CompilationFinal(dimensions = 1) public static final int[] FUNCREF_TYPE_ARRAY = {FUNCREF_TYPE};

    public static final int EXTERNREF_TYPE = EXTERN_HEAPTYPE;
    @CompilationFinal(dimensions = 1) public static final int[] EXTERNREF_TYPE_ARRAY = {EXTERNREF_TYPE};

    public static final int EXNREF_TYPE = EXN_HEAPTYPE;
    @CompilationFinal(dimensions = 1) public static final int[] EXNREF_TYPE_ARRAY = {EXNREF_TYPE};

    /**
     * Implementation-specific Types.
     */
    public static final int TOP = -0x7e;
    public static final int BOT = -0x7f;

    /**
     * Bytes used in the binary encoding of types.
     */
    public static final byte REF_TYPE_HEADER = -0x1c;
    public static final byte REF_NULL_TYPE_HEADER = -0x1d;
    // -0x40 is what the void block type byte 0x40 looks like when read as a signed LEB128 value.
    public static final byte VOID_BLOCK_TYPE = -0x40;
    @CompilationFinal(dimensions = 1) public static final int[] VOID_TYPE_ARRAY = {};

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
        return switch (valueType) {
            case I32_TYPE -> "i32";
            case I64_TYPE -> "i64";
            case F32_TYPE -> "f32";
            case F64_TYPE -> "f64";
            case V128_TYPE -> "v128";
            case TOP -> "top";
            case BOT -> "bot";
            default -> {
                assert WasmType.isReferenceType(valueType);
                boolean nullable = WasmType.isNullable(valueType);
                yield switch (WasmType.getAbstractHeapType(valueType)) {
                    case FUNC_HEAPTYPE -> nullable ? "funcref" : "(ref func)";
                    case EXTERN_HEAPTYPE -> nullable ? "externref" : "(ref extern)";
                    case EXN_HEAPTYPE -> nullable ? "exnref" : "(ref exn)";
                    default -> {
                        assert WasmType.isConcreteReferenceType(valueType);
                        StringBuilder sb = new StringBuilder(16);
                        sb.append("(ref ");
                        if (nullable) {
                            sb.append("null ");
                        }
                        sb.append(getTypeIndex(valueType));
                        sb.append(")");
                        yield sb.toString();
                    }
                };
            }
        };
    }

    public static boolean isNumberType(int type) {
        return type == I32_TYPE || type == I64_TYPE || type == F32_TYPE || type == F64_TYPE || isBottomType(type);
    }

    public static boolean isVectorType(int type) {
        return type == V128_TYPE || isBottomType(type);
    }

    public static boolean isReferenceType(int type) {
        return isConcreteReferenceType(type) || withNullable(true, type) == FUNC_HEAPTYPE || withNullable(true, type) == EXTERN_HEAPTYPE || withNullable(true, type) == EXN_HEAPTYPE ||
                        isBottomType(type);
    }

    public static boolean isBottomType(int type) {
        return withNullable(true, type) == BOT;
    }

    public static boolean isConcreteReferenceType(int type) {
        return type >= 0 || isBottomType(type);
    }

    public static int getTypeIndex(int type) {
        assert isConcreteReferenceType(type);
        return type & TYPE_VALUE_MASK;
    }

    public static int getAbstractHeapType(int type) {
        assert isReferenceType(type);
        return withNullable(true, type);
    }

    public static boolean isNullable(int type) {
        assert isReferenceType(type);
        return (type & TYPE_NULLABLE_MASK) != 0;
    }

    public static int withNullable(boolean nullable, int type) {
        return nullable ? type | TYPE_NULLABLE_MASK : type & ~TYPE_NULLABLE_MASK;
    }

    public static boolean hasDefaultValue(int type) {
        return !(isReferenceType(type) && !isNullable(type));
    }

    public static int getCommonValueType(int[] types) {
        int type = 0;
        for (int resultType : types) {
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
