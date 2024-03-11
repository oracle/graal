/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.wasm.constants.Instructions;

public enum Vector128Shape {

    I8X16(WasmType.I32_TYPE, 16),
    I16X8(WasmType.I32_TYPE, 8),
    I32X4(WasmType.I32_TYPE, 4),
    I64X2(WasmType.I64_TYPE, 2),
    F32X4(WasmType.F32_TYPE, 4),
    F64X2(WasmType.F64_TYPE, 2);

    private final byte unpackedType;
    private final int dimension;

    Vector128Shape(byte unpackedType, int dimension) {
        this.unpackedType = unpackedType;
        this.dimension = dimension;
    }

    public byte getUnpackedType() {
        return unpackedType;
    }

    public int getDimension() {
        return dimension;
    }

    public static Vector128Shape ofInstruction(int vectorOpcode) {
        return switch (vectorOpcode) {
            case Instructions.VECTOR_I8X16_SHUFFLE -> I8X16;

            case Instructions.VECTOR_I8X16_EXTRACT_LANE_S -> I8X16;
            case Instructions.VECTOR_I8X16_EXTRACT_LANE_U -> I8X16;
            case Instructions.VECTOR_I8X16_REPLACE_LANE -> I8X16;
            case Instructions.VECTOR_I16X8_EXTRACT_LANE_S -> I16X8;
            case Instructions.VECTOR_I16X8_EXTRACT_LANE_U -> I16X8;
            case Instructions.VECTOR_I16X8_REPLACE_LANE -> I16X8;
            case Instructions.VECTOR_I32X4_EXTRACT_LANE -> I32X4;
            case Instructions.VECTOR_I32X4_REPLACE_LANE -> I32X4;
            case Instructions.VECTOR_I64X2_EXTRACT_LANE -> I64X2;
            case Instructions.VECTOR_I64X2_REPLACE_LANE -> I64X2;
            case Instructions.VECTOR_F32X4_EXTRACT_LANE -> F32X4;
            case Instructions.VECTOR_F32X4_REPLACE_LANE -> F32X4;
            case Instructions.VECTOR_F64X2_EXTRACT_LANE -> F64X2;
            case Instructions.VECTOR_F64X2_REPLACE_LANE -> F64X2;

            case Instructions.VECTOR_I8X16_SWIZZLE -> I8X16;
            case Instructions.VECTOR_I8X16_SPLAT -> I8X16;
            case Instructions.VECTOR_I16X8_SPLAT -> I16X8;
            case Instructions.VECTOR_I32X4_SPLAT -> I32X4;
            case Instructions.VECTOR_I64X2_SPLAT -> I64X2;
            case Instructions.VECTOR_F32X4_SPLAT -> F32X4;
            case Instructions.VECTOR_F64X2_SPLAT -> F64X2;

            case Instructions.VECTOR_I8X16_EQ -> I8X16;
            case Instructions.VECTOR_I8X16_NE -> I8X16;
            case Instructions.VECTOR_I8X16_LT_S -> I8X16;
            case Instructions.VECTOR_I8X16_LT_U -> I8X16;
            case Instructions.VECTOR_I8X16_GT_S -> I8X16;
            case Instructions.VECTOR_I8X16_GT_U -> I8X16;
            case Instructions.VECTOR_I8X16_LE_S -> I8X16;
            case Instructions.VECTOR_I8X16_LE_U -> I8X16;
            case Instructions.VECTOR_I8X16_GE_S -> I8X16;
            case Instructions.VECTOR_I8X16_GE_U -> I8X16;

            case Instructions.VECTOR_I16X8_EQ -> I16X8;
            case Instructions.VECTOR_I16X8_NE -> I16X8;
            case Instructions.VECTOR_I16X8_LT_S -> I16X8;
            case Instructions.VECTOR_I16X8_LT_U -> I16X8;
            case Instructions.VECTOR_I16X8_GT_S -> I16X8;
            case Instructions.VECTOR_I16X8_GT_U -> I16X8;
            case Instructions.VECTOR_I16X8_LE_S -> I16X8;
            case Instructions.VECTOR_I16X8_LE_U -> I16X8;
            case Instructions.VECTOR_I16X8_GE_S -> I16X8;
            case Instructions.VECTOR_I16X8_GE_U -> I16X8;

            case Instructions.VECTOR_I32X4_EQ -> I32X4;
            case Instructions.VECTOR_I32X4_NE -> I32X4;
            case Instructions.VECTOR_I32X4_LT_S -> I32X4;
            case Instructions.VECTOR_I32X4_LT_U -> I32X4;
            case Instructions.VECTOR_I32X4_GT_S -> I32X4;
            case Instructions.VECTOR_I32X4_GT_U -> I32X4;
            case Instructions.VECTOR_I32X4_LE_S -> I32X4;
            case Instructions.VECTOR_I32X4_LE_U -> I32X4;
            case Instructions.VECTOR_I32X4_GE_S -> I32X4;
            case Instructions.VECTOR_I32X4_GE_U -> I32X4;

            case Instructions.VECTOR_I64X2_EQ -> I64X2;
            case Instructions.VECTOR_I64X2_NE -> I64X2;
            case Instructions.VECTOR_I64X2_LT_S -> I64X2;
            case Instructions.VECTOR_I64X2_GT_S -> I64X2;
            case Instructions.VECTOR_I64X2_LE_S -> I64X2;
            case Instructions.VECTOR_I64X2_GE_S -> I64X2;

            case Instructions.VECTOR_F32X4_EQ -> F32X4;
            case Instructions.VECTOR_F32X4_NE -> F32X4;
            case Instructions.VECTOR_F32X4_LT -> F32X4;
            case Instructions.VECTOR_F32X4_GT -> F32X4;
            case Instructions.VECTOR_F32X4_LE -> F32X4;
            case Instructions.VECTOR_F32X4_GE -> F32X4;

            case Instructions.VECTOR_F64X2_EQ -> F64X2;
            case Instructions.VECTOR_F64X2_NE -> F64X2;
            case Instructions.VECTOR_F64X2_LT -> F64X2;
            case Instructions.VECTOR_F64X2_GT -> F64X2;
            case Instructions.VECTOR_F64X2_LE -> F64X2;
            case Instructions.VECTOR_F64X2_GE -> F64X2;

            case Instructions.VECTOR_I8X16_ABS -> I8X16;
            case Instructions.VECTOR_I8X16_NEG -> I8X16;
            case Instructions.VECTOR_I8X16_POPCNT -> I8X16;
            case Instructions.VECTOR_I8X16_ALL_TRUE -> I8X16;
            case Instructions.VECTOR_I8X16_BITMASK -> I8X16;
            case Instructions.VECTOR_I8X16_NARROW_I16X8_S -> I8X16;
            case Instructions.VECTOR_I8X16_NARROW_I16X8_U -> I8X16;
            case Instructions.VECTOR_I8X16_SHL -> I8X16;
            case Instructions.VECTOR_I8X16_SHR_S -> I8X16;
            case Instructions.VECTOR_I8X16_SHR_U -> I8X16;
            case Instructions.VECTOR_I8X16_ADD -> I8X16;
            case Instructions.VECTOR_I8X16_ADD_SAT_S -> I8X16;
            case Instructions.VECTOR_I8X16_ADD_SAT_U -> I8X16;
            case Instructions.VECTOR_I8X16_SUB -> I8X16;
            case Instructions.VECTOR_I8X16_SUB_SAT_S -> I8X16;
            case Instructions.VECTOR_I8X16_SUB_SAT_U -> I8X16;
            case Instructions.VECTOR_I8X16_MIN_S -> I8X16;
            case Instructions.VECTOR_I8X16_MIN_U -> I8X16;
            case Instructions.VECTOR_I8X16_MAX_S -> I8X16;
            case Instructions.VECTOR_I8X16_MAX_U -> I8X16;
            case Instructions.VECTOR_I8X16_AVGR_U -> I8X16;

            case Instructions.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S -> I16X8;
            case Instructions.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U -> I16X8;
            case Instructions.VECTOR_I16X8_ABS -> I16X8;
            case Instructions.VECTOR_I16X8_NEG -> I16X8;
            case Instructions.VECTOR_I16X8_Q15MULR_SAT_S -> I16X8;
            case Instructions.VECTOR_I16X8_ALL_TRUE -> I16X8;
            case Instructions.VECTOR_I16X8_BITMASK -> I16X8;
            case Instructions.VECTOR_I16X8_NARROW_I32X4_S -> I16X8;
            case Instructions.VECTOR_I16X8_NARROW_I32X4_U -> I16X8;
            case Instructions.VECTOR_I16X8_EXTEND_LOW_I8X16_S -> I16X8;
            case Instructions.VECTOR_I16X8_EXTEND_HIGH_I8X16_S -> I16X8;
            case Instructions.VECTOR_I16X8_EXTEND_LOW_I8X16_U -> I16X8;
            case Instructions.VECTOR_I16X8_EXTEND_HIGH_I8X16_U -> I16X8;
            case Instructions.VECTOR_I16X8_SHL -> I16X8;
            case Instructions.VECTOR_I16X8_SHR_S -> I16X8;
            case Instructions.VECTOR_I16X8_SHR_U -> I16X8;
            case Instructions.VECTOR_I16X8_ADD -> I16X8;
            case Instructions.VECTOR_I16X8_ADD_SAT_S -> I16X8;
            case Instructions.VECTOR_I16X8_ADD_SAT_U -> I16X8;
            case Instructions.VECTOR_I16X8_SUB -> I16X8;
            case Instructions.VECTOR_I16X8_SUB_SAT_S -> I16X8;
            case Instructions.VECTOR_I16X8_SUB_SAT_U -> I16X8;
            case Instructions.VECTOR_I16X8_MUL -> I16X8;
            case Instructions.VECTOR_I16X8_MIN_S -> I16X8;
            case Instructions.VECTOR_I16X8_MIN_U -> I16X8;
            case Instructions.VECTOR_I16X8_MAX_S -> I16X8;
            case Instructions.VECTOR_I16X8_MAX_U -> I16X8;
            case Instructions.VECTOR_I16X8_AVGR_U -> I16X8;
            case Instructions.VECTOR_I16X8_EXTMUL_LOW_I8X16_S -> I16X8;
            case Instructions.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S -> I16X8;
            case Instructions.VECTOR_I16X8_EXTMUL_LOW_I8X16_U -> I16X8;
            case Instructions.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U -> I16X8;

            case Instructions.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S -> I32X4;
            case Instructions.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U -> I32X4;
            case Instructions.VECTOR_I32X4_ABS -> I32X4;
            case Instructions.VECTOR_I32X4_NEG -> I32X4;
            case Instructions.VECTOR_I32X4_ALL_TRUE -> I32X4;
            case Instructions.VECTOR_I32X4_BITMASK -> I32X4;
            case Instructions.VECTOR_I32X4_EXTEND_LOW_I16X8_S -> I32X4;
            case Instructions.VECTOR_I32X4_EXTEND_HIGH_I16X8_S -> I32X4;
            case Instructions.VECTOR_I32X4_EXTEND_LOW_I16X8_U -> I32X4;
            case Instructions.VECTOR_I32X4_EXTEND_HIGH_I16X8_U -> I32X4;
            case Instructions.VECTOR_I32X4_SHL -> I32X4;
            case Instructions.VECTOR_I32X4_SHR_S -> I32X4;
            case Instructions.VECTOR_I32X4_SHR_U -> I32X4;
            case Instructions.VECTOR_I32X4_ADD -> I32X4;
            case Instructions.VECTOR_I32X4_SUB -> I32X4;
            case Instructions.VECTOR_I32X4_MUL -> I32X4;
            case Instructions.VECTOR_I32X4_MIN_S -> I32X4;
            case Instructions.VECTOR_I32X4_MIN_U -> I32X4;
            case Instructions.VECTOR_I32X4_MAX_S -> I32X4;
            case Instructions.VECTOR_I32X4_MAX_U -> I32X4;
            case Instructions.VECTOR_I32X4_DOT_I16X8_S -> I32X4;
            case Instructions.VECTOR_I32X4_EXTMUL_LOW_I16X8_S -> I32X4;
            case Instructions.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S -> I32X4;
            case Instructions.VECTOR_I32X4_EXTMUL_LOW_I16X8_U -> I32X4;
            case Instructions.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U -> I32X4;

            case Instructions.VECTOR_I64X2_ABS -> I64X2;
            case Instructions.VECTOR_I64X2_NEG -> I64X2;
            case Instructions.VECTOR_I64X2_ALL_TRUE -> I64X2;
            case Instructions.VECTOR_I64X2_BITMASK -> I64X2;
            case Instructions.VECTOR_I64X2_EXTEND_LOW_I32X4_S -> I64X2;
            case Instructions.VECTOR_I64X2_EXTEND_HIGH_I32X4_S -> I64X2;
            case Instructions.VECTOR_I64X2_EXTEND_LOW_I32X4_U -> I64X2;
            case Instructions.VECTOR_I64X2_EXTEND_HIGH_I32X4_U -> I64X2;
            case Instructions.VECTOR_I64X2_SHL -> I64X2;
            case Instructions.VECTOR_I64X2_SHR_S -> I64X2;
            case Instructions.VECTOR_I64X2_SHR_U -> I64X2;
            case Instructions.VECTOR_I64X2_ADD -> I64X2;
            case Instructions.VECTOR_I64X2_SUB -> I64X2;
            case Instructions.VECTOR_I64X2_MUL -> I64X2;
            case Instructions.VECTOR_I64X2_EXTMUL_LOW_I32X4_S -> I64X2;
            case Instructions.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S -> I64X2;
            case Instructions.VECTOR_I64X2_EXTMUL_LOW_I32X4_U -> I64X2;
            case Instructions.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U -> I64X2;

            case Instructions.VECTOR_F32X4_CEIL -> F32X4;
            case Instructions.VECTOR_F32X4_FLOOR -> F32X4;
            case Instructions.VECTOR_F32X4_TRUNC -> F32X4;
            case Instructions.VECTOR_F32X4_NEAREST -> F32X4;
            case Instructions.VECTOR_F32X4_ABS -> F32X4;
            case Instructions.VECTOR_F32X4_NEG -> F32X4;
            case Instructions.VECTOR_F32X4_SQRT -> F32X4;
            case Instructions.VECTOR_F32X4_ADD -> F32X4;
            case Instructions.VECTOR_F32X4_SUB -> F32X4;
            case Instructions.VECTOR_F32X4_MUL -> F32X4;
            case Instructions.VECTOR_F32X4_DIV -> F32X4;
            case Instructions.VECTOR_F32X4_MIN -> F32X4;
            case Instructions.VECTOR_F32X4_MAX -> F32X4;
            case Instructions.VECTOR_F32X4_PMIN -> F32X4;
            case Instructions.VECTOR_F32X4_PMAX -> F32X4;

            case Instructions.VECTOR_F64X2_CEIL -> F64X2;
            case Instructions.VECTOR_F64X2_FLOOR -> F64X2;
            case Instructions.VECTOR_F64X2_TRUNC -> F64X2;
            case Instructions.VECTOR_F64X2_NEAREST -> F64X2;
            case Instructions.VECTOR_F64X2_ABS -> F64X2;
            case Instructions.VECTOR_F64X2_NEG -> F64X2;
            case Instructions.VECTOR_F64X2_SQRT -> F64X2;
            case Instructions.VECTOR_F64X2_ADD -> F64X2;
            case Instructions.VECTOR_F64X2_SUB -> F64X2;
            case Instructions.VECTOR_F64X2_MUL -> F64X2;
            case Instructions.VECTOR_F64X2_DIV -> F64X2;
            case Instructions.VECTOR_F64X2_MIN -> F64X2;
            case Instructions.VECTOR_F64X2_MAX -> F64X2;
            case Instructions.VECTOR_F64X2_PMIN -> F64X2;
            case Instructions.VECTOR_F64X2_PMAX -> F64X2;

            case Instructions.VECTOR_I32X4_TRUNC_SAT_F32X4_S -> I32X4;
            case Instructions.VECTOR_I32X4_TRUNC_SAT_F32X4_U -> I32X4;
            case Instructions.VECTOR_F32X4_CONVERT_I32X4_S -> F32X4;
            case Instructions.VECTOR_F32X4_CONVERT_I32X4_U -> F32X4;
            case Instructions.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO -> I32X4;
            case Instructions.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO -> I32X4;
            case Instructions.VECTOR_F64X2_CONVERT_LOW_I32X4_S -> F64X2;
            case Instructions.VECTOR_F64X2_CONVERT_LOW_I32X4_U -> F64X2;
            case Instructions.VECTOR_F32X4_DEMOTE_F64X2_ZERO -> F32X4;
            case Instructions.VECTOR_F64X2_PROMOTE_LOW_F32X4 -> F64X2;
            default -> null;
        };
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
