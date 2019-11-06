/*
 *  Copyright (c) 2019, Oracle and/or its affiliates.
 *
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this list of
 *  conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *  conditions and the following disclaimer in the documentation and/or other materials provided
 *  with the distribution.
 *
 *  3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *  endorse or promote products derived from this software without specific prior written
 *  permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 *  OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 *  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 *  AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.wasm.test.suites.arithmetic;

import static com.oracle.truffle.wasm.utils.cases.WasmCase.expected;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

import com.oracle.truffle.wasm.test.WasmSuiteBase;
import com.oracle.truffle.wasm.utils.cases.WasmCase;
import com.oracle.truffle.wasm.utils.cases.WasmStringCase;

public class Integer64Suite extends WasmSuiteBase {
    private WasmStringCase[] testCases = {
                    WasmCase.create("CONST_SMALL", expected(42L),
                                    "(module (func (export \"_main\") (result i64) i64.const 42))"),
                    WasmCase.create("CONST_LARGE", expected(Long.MAX_VALUE),
                                    "(module (func (export \"_main\") (result i64) i64.const 9223372036854775807))"),
                    WasmCase.create("CONST_LARGE", expected(0xfffc000000000000L),
                                    "(module (func (export \"_main\") (result i64) i64.const -1125899906842624))"),
                    WasmCase.create("DROP", expected(889L),
                                    "(module (func (export \"_main\") (result i64) i64.const 889 i64.const 42 drop))"),
                    WasmCase.create("EQZ_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x12345678 i64.eqz))"),
                    WasmCase.create("EQZ_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x0 i64.eqz))"),
                    WasmCase.create("EQ_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x1234 i64.const 0x1234 i64.eq))"),
                    WasmCase.create("EQ_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x1234 i64.const 0xbaba i64.eq))"),
                    WasmCase.create("NE_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x1234 i64.const 0x1234 i64.ne))"),
                    WasmCase.create("NE_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x1234 i64.const 0xbaba i64.ne))"),
                    WasmCase.create("LT_S_TRUE_NEGATIVE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababadadababa i64.const 0xbaba i64.lt_s))"),
                    WasmCase.create("LT_S_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xbaba i64.const 0xbaba i64.lt_s))"),
                    WasmCase.create("LT_S_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x0dedbabadababa i64.const 0xbaba i64.lt_s))"),
                    WasmCase.create("LT_U_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.lt_u))"),
                    WasmCase.create("LT_U_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.lt_u))"),
                    WasmCase.create("LT_U_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.lt_u))"),
                    WasmCase.create("GT_S_FALSE_NEGATIVE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababadadababa i64.const 0xbaba i64.gt_s))"),
                    WasmCase.create("GT_S_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xbaba i64.const 0xbaba i64.gt_s))"),
                    WasmCase.create("GT_S_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x0dedbabadadababa i64.const 0xbaba i64.gt_s))"),
                    WasmCase.create("GT_U_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.gt_u))"),
                    WasmCase.create("GT_U_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.gt_u))"),
                    WasmCase.create("GT_U_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.gt_u))"),
                    WasmCase.create("LE_S_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xbaba i64.le_s))"),
                    WasmCase.create("LE_S_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xbaba i64.const 0xbaba i64.le_s))"),
                    WasmCase.create("LE_S_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x0dedbaba i64.const 0xbaba i64.le_s))"),
                    WasmCase.create("LE_U_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.le_u))"),
                    WasmCase.create("LE_U_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.le_u))"),
                    WasmCase.create("LE_U_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.le_u))"),
                    WasmCase.create("GE_S_FALSE_NEGATIVE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababadadababa i64.const 0xbaba i64.ge_s))"),
                    WasmCase.create("GE_S_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xbaba i64.const 0xbaba i64.ge_s))"),
                    WasmCase.create("GE_S_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0x0dedbabadadababa i64.const 0xbaba i64.ge_s))"),
                    WasmCase.create("GE_U_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.ge_u))"),
                    WasmCase.create("GE_U_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.ge_u))"),
                    WasmCase.create("GE_U_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.ge_u))"),
                    WasmCase.create("COUNT_LEADING_ZEROES", expected(35L),
                                    "(module (func (export \"_main\") (result i64) i64.const 0x12345678 i64.clz))"),
                    WasmCase.create("COUNT_TRAILING_ZEROES_1", expected(2L),
                                    "(module (func (export \"_main\") (result i64) i64.const 0x1234567C i64.ctz))"),
                    WasmCase.create("COUNT_TRAILING_ZEROES_2", expected(4L),
                                    "(module (func (export \"_main\") (result i64) i64.const 0x12345670 i64.ctz))"),
                    WasmCase.create("COUNT_ONES_POPCNT", expected(29L),
                                    "(module (func (export \"_main\") (result i64) i64.const 0xdedbabababa i64.popcnt))"),
                    WasmCase.create("ADD", expected(59L),
                                    "(module (func (export \"_main\") (result i64) i64.const 42 i64.const 17 i64.add))"),
                    WasmCase.create("SUB", expected(25L),
                                    "(module (func (export \"_main\") (result i64) i64.const 42 i64.const 17 i64.sub))"),
                    WasmCase.create("SUB_NEG_RESULT", expected(-1L),
                                    "(module (func (export \"_main\") (result i64) i64.const 42 i64.const 43 i64.sub))"),
                    WasmCase.create("MUL", expected(132L),
                                    "(module (func (export \"_main\") (result i64) i64.const 11 i64.const 12 i64.mul))"),
                    WasmCase.create("DIV_S", expected(3L),
                                    "(module (func (export \"_main\") (result i64) i64.const 11 i64.const 3 i64.div_s))"),
                    WasmCase.create("DIV_S_NEG_RESULT", expected(-3L),
                                    "(module (func (export \"_main\") (result i64) i64.const -11 i64.const 3 i64.div_s))"),
                    WasmCase.create("DIV_U", expected(10L),
                                    "(module (func (export \"_main\") (result i64) i64.const 118 i64.const 11 i64.div_u))"),
                    WasmCase.create("DIV_U_NEG", expected(34_359_738_368L),
                                    "(module (func (export \"_main\") (result i64) i64.const 0x8000000000000001 i64.const 0x10000000 i64.div_u))"),
                    WasmCase.create("REM_S_BOTH_POS", expected(2L),
                                    "(module (func (export \"_main\") (result i64) i64.const 11 i64.const 3 i64.rem_s))"),
                    WasmCase.create("REM_S_POS_NEG", expected(2L),
                                    "(module (func (export \"_main\") (result i64) i64.const 11 i64.const -3 i64.rem_s))"),
                    WasmCase.create("REM_S_BOTH_NEG", expected(-2L),
                                    "(module (func (export \"_main\") (result i64) i64.const -11 i64.const -3 i64.rem_s))"),
                    WasmCase.create("REM_S_NEG_POS", expected(-2L),
                                    "(module (func (export \"_main\") (result i64) i64.const -11 i64.const 3 i64.rem_s))"),
                    WasmCase.create("REM_U_NEG", expected(129L),
                                    "(module (func (export \"_main\") (result i64) i64.const 0x8000000000000001 i64.const 0x10000001 i64.rem_u))"),
                    WasmCase.create("LOGICAL_AND", expected(2L),
                                    "(module (func (export \"_main\") (result i64) i64.const 10 i64.const 7 i64.and))"),
                    WasmCase.create("LOGICAL_AND_NEG", expected(7L),
                                    "(module (func (export \"_main\") (result i64) i64.const -1 i64.const 7 i64.and))"),
                    WasmCase.create("LOGICAL_OR_NEG", expected(-1L),
                                    "(module (func (export \"_main\") (result i64) i64.const -2 i64.const 7 i64.or))"),
                    WasmCase.create("LOGICAL_OR", expected(15L),
                                    "(module (func (export \"_main\") (result i64) i64.const 6 i64.const 9 i64.or))"),
                    WasmCase.create("LOGICAL_XOR", expected(15L),
                                    "(module (func (export \"_main\") (result i64) i64.const 11 i64.const 4 i64.xor))"),
                    WasmCase.create("LOGICAL_XOR_NEG", expected(-3L),
                                    "(module (func (export \"_main\") (result i64) i64.const -2 i64.const 3 i64.xor))"),
                    WasmCase.create("SHIFT_LEFT", expected(-2L),
                                    "(module (func (export \"_main\") (result i64) i64.const -1 i64.const 1 i64.shl))"),
                    WasmCase.create("SHIFT_RIGHT_SIGNED", expected(-1L),
                                    "(module (func (export \"_main\") (result i64) i64.const -1 i64.const 1 i64.shr_s))"),
                    WasmCase.create("SHIFT_RIGHT_UNSIGNED", expected(Long.MAX_VALUE),
                                    "(module (func (export \"_main\") (result i64) i64.const -1 i64.const 1 i64.shr_u))"),
                    WasmCase.create("ROTATE_LEFT", expected(-3L),
                                    "(module (func (export \"_main\") (result i64) i64.const -2 i64.const 1 i64.rotl))"),
                    WasmCase.create("ROTATE_RIGHT", expected(-2L),
                                    "(module (func (export \"_main\") (result i64) i64.const -3 i64.const 1 i64.rotr))"),
    };

    @Override
    protected Collection<? extends WasmCase> collectStringTestCases() {
        return Arrays.asList(testCases);
    }

    @Test
    public void test() throws IOException {
        // This is here just to make mx aware of the test suite class.
        super.test();
    }
}
