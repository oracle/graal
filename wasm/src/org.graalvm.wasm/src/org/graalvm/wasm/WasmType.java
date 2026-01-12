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
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * <p>
 * Wasm value types are represented as {@code int}s. Predefined types are negative values, while
 * user-defined types are non-negative. The second-highest bit is used to signal if a reference type
 * is nullable. For the predefined non-reference types (numbers and vectors), that bit is always
 * set.
 * </p>
 * <p>
 * For predefined types, the negative values are the LEB128 decodings of the bytes that represent
 * these predefined types in the wasm binary format. E.g., {@code i32} is represented as
 * {@code 0x7f} in the binary format, which decodes into {@code -1}, and that is the internal
 * representation we use in {@link #I32_TYPE}.
 * </p>
 * <p>
 * For user-defined types, the non-negative value is the type index which points to the type
 * definition in the module's {@link SymbolTable}.
 * </p>
 * <p>
 * Wasm heap types are represented using the same schema (a union of negative predefined types and
 * user-defined non-negative type indices), but without using a special nullability bit. To build a
 * reference type out of a heap type, set the nullability bit using {@link #withNullable}:
 * </p>
 * 
 * <pre>
 *     boolean nullable = ...;
 *     int heapType = ...;
 *     int referenceType = WasmType.withNullable(nullable, heapType);
 * </pre>
 * <p>
 * During type checking, it is not enough to compare types by equality, as this does not take into
 * account type aliases and subtyping. Instead, use {@link SymbolTable#matchesType(int, int)}.
 * </p>
 * <p>
 * For an example of how to do case analysis on a Wasm value type, check the source of
 * {@link #toString(int)}. NB: The types {@link #TOP} and {@link #BOT} only occur during module
 * validation.
 * </p>
 */
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

    // Implementation-specific Types.
    /**
     * The common supertype of all types, the universal type.
     */
    public static final int TOP = -0x7e;
    /**
     * The common subtype of all types, the impossible type.
     */
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
        return type == I32_TYPE || type == I64_TYPE || type == F32_TYPE || type == F64_TYPE || type == BOT;
    }

    public static boolean isVectorType(int type) {
        return type == V128_TYPE || type == BOT;
    }

    public static boolean isReferenceType(int type) {
        return isConcreteReferenceType(type) || withNullable(true, type) == FUNC_HEAPTYPE || withNullable(true, type) == EXTERN_HEAPTYPE || withNullable(true, type) == EXN_HEAPTYPE || type == BOT;
    }

    /**
     * Indicates whether this is a user-defined reference type.
     */
    public static boolean isConcreteReferenceType(int type) {
        return type >= 0 || type == BOT;
    }

    /**
     * Returns the type index of this user-defined reference type. This must be used together with
     * the appropriate {@link SymbolTable} to be able to expand the type's definition.
     */
    public static int getTypeIndex(int type) {
        assert isConcreteReferenceType(type);
        return type & TYPE_VALUE_MASK;
    }

    /**
     * Returns the "payload" of this reference type, which can be matched against the predefined
     * abstract heap types (such as {@link #FUNC_HEAPTYPE} or {@link #EXTERN_HEAPTYPE}) in a switch
     * statement.
     */
    public static int getAbstractHeapType(int type) {
        assert isReferenceType(type);
        return withNullable(true, type);
    }

    /**
     * Indicates whether this value types admits the value {@link WasmConstant#NULL}. Can only be
     * called on reference types.
     */
    public static boolean isNullable(int type) {
        assert isReferenceType(type);
        return (type & TYPE_NULLABLE_MASK) != 0;
    }

    /**
     * Updates the nullability bit of this reference type. Can also be used to create a reference
     * type from a heap type.
     * 
     * @param nullable whether the resulting reference type should be nullable
     * @param type a reference type or a heap type (one of the {@code *_HEAPTYPE} constants or a
     *            type index)
     */
    public static int withNullable(boolean nullable, int type) {
        if (type == BOT) {
            return BOT;
        }
        return nullable ? type | TYPE_NULLABLE_MASK : type & ~TYPE_NULLABLE_MASK;
    }

    /**
     * Indicates whether this type has a default value (this is the case for all value types except
     * for non-nullable reference types). Locals of such types do not have to be initialized prior
     * to first access.
     */
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
    boolean hasLanguageId() {
        return true;
    }

    @ExportMessage
    String getLanguageId() {
        return WasmLanguage.ID;
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
