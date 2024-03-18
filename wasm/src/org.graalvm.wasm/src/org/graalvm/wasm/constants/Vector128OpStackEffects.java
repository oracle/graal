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
package org.graalvm.wasm.constants;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.wasm.nodes.WasmFunctionNode;

/**
 * The data stored in this class tells us how much the size of the operand stack changes after
 * executing a single {@code v128} instruction. This is useful in {@link WasmFunctionNode}, since it
 * allows us to execute {@code v128} instructions in a separate method without needing to return the
 * new value of the stack pointer from that method.
 */
public final class Vector128OpStackEffects {

    private static final byte NO_EFFECT = 0;
    private static final byte PUSH_1 = 1;
    private static final byte POP_1 = -1;
    private static final byte POP_2 = -2;

    @CompilationFinal(dimensions = 1) private static final byte[] vector128OpStackEffects = new byte[256];

    static {
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD8X8_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD8X8_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD16X4_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD16X4_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD32X2_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD32X2_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD8_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD16_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD32_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD64_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD32_ZERO] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD64_ZERO] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_STORE] = POP_2;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD8_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD16_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD32_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_LOAD64_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_STORE8_LANE] = POP_2;
        vector128OpStackEffects[Bytecode.VECTOR_V128_STORE16_LANE] = POP_2;
        vector128OpStackEffects[Bytecode.VECTOR_V128_STORE32_LANE] = POP_2;
        vector128OpStackEffects[Bytecode.VECTOR_V128_STORE64_LANE] = POP_2;
        vector128OpStackEffects[Bytecode.VECTOR_V128_CONST] = PUSH_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SHUFFLE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_EXTRACT_LANE_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_EXTRACT_LANE_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_REPLACE_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTRACT_LANE_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTRACT_LANE_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_REPLACE_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTRACT_LANE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_REPLACE_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTRACT_LANE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_REPLACE_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_EXTRACT_LANE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_REPLACE_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_EXTRACT_LANE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_REPLACE_LANE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SWIZZLE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_SPLAT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_EQ] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_NE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_LT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_LT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_GT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_GT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_LE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_LE_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_GE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_GE_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EQ] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_NE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_LT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_LT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_GT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_GT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_LE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_LE_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_GE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_GE_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EQ] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_NE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_LT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_LT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_GT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_GT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_LE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_LE_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_GE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_GE_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EQ] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_NE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_LT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_GT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_LE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_GE_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_EQ] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_NE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_LT] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_GT] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_LE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_GE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_EQ] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_NE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_LT] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_GT] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_LE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_GE] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_NOT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_V128_AND] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_ANDNOT] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_OR] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_XOR] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_V128_BITSELECT] = POP_2;
        vector128OpStackEffects[Bytecode.VECTOR_V128_ANY_TRUE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_ABS] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_NEG] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_POPCNT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_ALL_TRUE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_BITMASK] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_NARROW_I16X8_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_NARROW_I16X8_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SHL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SHR_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SHR_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_ADD] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_ADD_SAT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_ADD_SAT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SUB] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SUB_SAT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_SUB_SAT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_MIN_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_MIN_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_MAX_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_MAX_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I8X16_AVGR_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_ABS] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_NEG] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_Q15MULR_SAT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_ALL_TRUE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_BITMASK] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_NARROW_I32X4_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_NARROW_I32X4_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_SHL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_SHR_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_SHR_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_ADD] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_ADD_SAT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_ADD_SAT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_SUB] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_SUB_SAT_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_SUB_SAT_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_MUL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_MIN_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_MIN_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_MAX_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_MAX_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_AVGR_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_ABS] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_NEG] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_ALL_TRUE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_BITMASK] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_SHL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_SHR_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_SHR_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_ADD] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_SUB] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_MUL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_MIN_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_MIN_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_MAX_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_MAX_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_DOT_I16X8_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_ABS] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_NEG] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_ALL_TRUE] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_BITMASK] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_SHL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_SHR_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_SHR_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_ADD] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_SUB] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_MUL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_CEIL] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_FLOOR] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_TRUNC] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_NEAREST] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_ABS] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_NEG] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_SQRT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_ADD] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_SUB] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_MUL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_DIV] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_MIN] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_MAX] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_PMIN] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_PMAX] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_CEIL] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_FLOOR] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_TRUNC] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_NEAREST] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_ABS] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_NEG] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_SQRT] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_ADD] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_SUB] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_MUL] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_DIV] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_MIN] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_MAX] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_PMIN] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_PMAX] = POP_1;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_CONVERT_I32X4_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_CONVERT_I32X4_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F32X4_DEMOTE_F64X2_ZERO] = NO_EFFECT;
        vector128OpStackEffects[Bytecode.VECTOR_F64X2_PROMOTE_LOW_F32X4] = NO_EFFECT;
    }

    private Vector128OpStackEffects() {
    }

    /**
     * Indicates by how much the stack grows (positive return value) or shrinks (negative return
     * value) after executing the {@code v128} instruction with opcode {@code vectorOpcode}.
     * 
     * @param vectorOpcode the {@code v128} instruction being executed
     * @return the difference between the stack size after executing the instruction and before
     *         executing the instruction
     */
    public static byte getVector128OpStackEffect(int vectorOpcode) {
        assert vectorOpcode < 256;
        return vector128OpStackEffects[vectorOpcode];
    }
}
