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

package com.oracle.truffle.wasm.test.next.arithmetic;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import com.oracle.truffle.wasm.test.next.WasmSuiteBase;
import org.junit.Test;

public class Integer64Suite extends WasmSuiteBase {
    private WasmStringTestCase[] testCases = {
            testCase("CONST_SMALL", expected(42L),
                        "(module (func (result i64) i64.const 42))"),
            testCase("CONST_LARGE", expected(Long.MAX_VALUE),
                        "(module (func (result i64) i64.const 9223372036854775807))"),
            testCase("DROP", expected(889L),
                        "(module (func (result i64) i64.const 889 i64.const 42 drop))"),
            testCase("EQZ_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0x12345678 i64.eqz))"),
            testCase("EQZ_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0x0 i64.eqz))"),
            testCase("EQ_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0x1234 i64.const 0x1234 i64.eq))"),
            testCase("EQ_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0x1234 i64.const 0xbaba i64.eq))"),
            testCase("NE_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0x1234 i64.const 0x1234 i64.ne))"),
            testCase("NE_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0x1234 i64.const 0xbaba i64.ne))"),
            testCase("LT_S_TRUE_NEGATIVE", expected(1),
                        "(module (func (result i32) i64.const 0xdedababadadababa i64.const 0xbaba i64.lt_s))"),
            testCase("LT_S_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.lt_s))"),
            testCase("LT_S_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0x0dedbabadababa i64.const 0xbaba i64.lt_s))"),
            testCase("LT_U_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.lt_u))"),
            testCase("LT_U_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.lt_u))"),
            testCase("LT_U_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.lt_u))"),
            testCase("GT_S_FALSE_NEGATIVE", expected(0),
                        "(module (func (result i32) i64.const 0xdedababadadababa i64.const 0xbaba i64.gt_s))"),
            testCase("GT_S_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.gt_s))"),
            testCase("GT_S_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0x0dedbabadadababa i64.const 0xbaba i64.gt_s))"),
            testCase("GT_U_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.gt_u))"),
            testCase("GT_U_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.gt_u))"),
            testCase("GT_U_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.gt_u))"),
            testCase("LE_S_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xbaba i64.le_s))"),
            testCase("LE_S_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.le_s))"),
            testCase("LE_S_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0x0dedbaba i64.const 0xbaba i64.le_s))"),
            testCase("LE_U_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.le_u))"),
            testCase("LE_U_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.le_u))"),
            testCase("LE_U_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.le_u))"),
            testCase("GE_S_FALSE_NEGATIVE", expected(0),
                        "(module (func (result i32) i64.const 0xdedababadadababa i64.const 0xbaba i64.ge_s))"),
            testCase("GE_S_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.ge_s))"),
            testCase("GE_S_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0x0dedbabadadababa i64.const 0xbaba i64.ge_s))"),
            testCase("GE_U_TRUE", expected(1),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.ge_u))"),
            testCase("GE_U_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.ge_u))"),
            testCase("GE_U_FALSE", expected(0),
                        "(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.ge_u))"),
            testCase("COUNT_LEADING_ZEROES", expected(35L),
                        "(module (func (result i64) i64.const 0x12345678 i64.clz))"),
            testCase("COUNT_TRAILING_ZEROES_1", expected(2L),
                        "(module (func (result i64) i64.const 0x1234567C i64.ctz))"),
            testCase("COUNT_TRAILING_ZEROES_2", expected(4L),
                        "(module (func (result i64) i64.const 0x12345670 i64.ctz))"),
            testCase("COUNT_ONES_POPCNT", expected(29L),
                        "(module (func (result i64) i64.const 0xdedbabababa i64.popcnt))"),
            testCase("ADD", expected(59L),
                        "(module (func (result i64) i64.const 42 i64.const 17 i64.add))"),
            testCase("SUB", expected(25L),
                        "(module (func (result i64) i64.const 42 i64.const 17 i64.sub))"),
            testCase("SUB_NEG_RESULT", expected(-1L),
                        "(module (func (result i64) i64.const 42 i64.const 43 i64.sub))"),
            testCase("MUL", expected(132L),
                        "(module (func (result i64) i64.const 11 i64.const 12 i64.mul))"),
            testCase("DIV_S", expected(3L),
                        "(module (func (result i64) i64.const 11 i64.const 3 i64.div_s))"),
            testCase("DIV_S_NEG_RESULT", expected(-3L),
                        "(module (func (result i64) i64.const -11 i64.const 3 i64.div_s))"),
            testCase("DIV_U", expected(10L),
                        "(module (func (result i64) i64.const 118 i64.const 11 i64.div_u))"),
            testCase("DIV_U_NEG", expected(34_359_738_368L),
                        "(module (func (result i64) i64.const 0x8000000000000001 i64.const 0x10000000 i64.div_u))"),
            testCase("REM_S_BOTH_POS", expected(2L),
                        "(module (func (result i64) i64.const 11 i64.const 3 i64.rem_s))"),
            testCase("REM_S_POS_NEG", expected(2L),
                        "(module (func (result i64) i64.const 11 i64.const -3 i64.rem_s))"),
            testCase("REM_S_BOTH_NEG", expected(-2L),
                        "(module (func (result i64) i64.const -11 i64.const -3 i64.rem_s))"),
            testCase("REM_S_NEG_POS", expected(-2L),
                        "(module (func (result i64) i64.const -11 i64.const 3 i64.rem_s))"),
            testCase("REM_U_NEG", expected(129L),
                        "(module (func (result i64) i64.const 0x8000000000000001 i64.const 0x10000001 i64.rem_u))"),
            testCase("LOGICAL_AND", expected(2L),
                        "(module (func (result i64) i64.const 10 i64.const 7 i64.and))"),
            testCase("LOGICAL_AND_NEG", expected(7L),
                        "(module (func (result i64) i64.const -1 i64.const 7 i64.and))"),
            testCase("LOGICAL_OR_NEG", expected(-1L),
                        "(module (func (result i64) i64.const -2 i64.const 7 i64.or))"),
            testCase("LOGICAL_OR", expected(15L),
                        "(module (func (result i64) i64.const 6 i64.const 9 i64.or))"),
            testCase("LOGICAL_XOR", expected(15L),
                        "(module (func (result i64) i64.const 11 i64.const 4 i64.xor))"),
            testCase("LOGICAL_XOR_NEG", expected(-3L),
                        "(module (func (result i64) i64.const -2 i64.const 3 i64.xor))"),
            testCase("SHIFT_LEFT", expected(-2L),
                        "(module (func (result i64) i64.const -1 i64.const 1 i64.shl))"),
            testCase("SHIFT_RIGHT_SIGNED", expected(-1L),
                        "(module (func (result i64) i64.const -1 i64.const 1 i64.shr_s))"),
            testCase("SHIFT_RIGHT_UNSIGNED", expected(Long.MAX_VALUE),
                        "(module (func (result i64) i64.const -1 i64.const 1 i64.shr_u))"),
            testCase("ROTATE_LEFT", expected(-3L),
                        "(module (func (result i64) i64.const -2 i64.const 1 i64.rotl))"),
            testCase("ROTATE_RIGHT", expected(-2L),
                        "(module (func (result i64) i64.const -3 i64.const 1 i64.rotr))"),
    };

    @Override
    protected Collection<? extends WasmTestCase> collectStringTestCases() {
        return Arrays.asList(testCases);
    }

    @Test
    public void test() throws IOException {
        // This is here just to make mx aware of the test suite class.
        super.test();
    }
}
