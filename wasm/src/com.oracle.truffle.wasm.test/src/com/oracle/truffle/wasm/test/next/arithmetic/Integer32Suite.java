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

public class Integer32Suite extends WasmSuiteBase {
    private WasmStringTestCase[] testCases = {
            testCase("CONST_SMALL", expected(42),
                        "(module (func (result i32) i32.const 42))"),
            testCase("CONST_LARGE", expected(1_895_633),
                        "(module (func (result i32) i32.const 1895633))"),
            testCase("DROP", expected(42),
                        "(module (func (result i32) i32.const 42 i32.const 17 drop))"),
            testCase("EQZ_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0x12345678 i32.eqz))"),
            testCase("EQZ_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0x0 i32.eqz))"),
            testCase("EQ_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0x1234 i32.const 0x1234 i32.eq))"),
            testCase("EQ_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0x1234 i32.const 0xbaba i32.eq))"),
            testCase("NE_TRUE", expected(0),
                        "(module (func (result i32) i32.const 0x1234 i32.const 0x1234 i32.ne))"),
            testCase("NE_FALSE", expected(1),
                        "(module (func (result i32) i32.const 0x1234 i32.const 0xbaba i32.ne))"),
            testCase("LT_S_TRUE_NEGATIVE", expected(1),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.lt_s))"),
            testCase("LT_S_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.lt_s))"),
            testCase("LT_S_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.lt_s))"),
            testCase("LT_U_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.lt_u))"),
            testCase("LT_U_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.lt_u))"),
            testCase("LT_U_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.lt_u))"),
            testCase("GT_S_FALSE_NEGATIVE", expected(0),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.gt_s))"),
            testCase("GT_S_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.gt_s))"),
            testCase("GT_S_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.gt_s))"),
            testCase("GT_U_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.gt_u))"),
            testCase("GT_U_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.gt_u))"),
            testCase("GT_U_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.gt_u))"),
            testCase("LE_S_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.le_s))"),
            testCase("LE_S_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.le_s))"),
            testCase("LE_S_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.le_s))"),
            testCase("LE_U_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.le_u))"),
            testCase("LE_U_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.le_u))"),
            testCase("LE_U_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.le_u))"),
            testCase("GE_S_FALSE_NEGATIVE", expected(0),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.ge_s))"),
            testCase("GE_S_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.ge_s))"),
            testCase("GE_S_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.ge_s))"),
            testCase("GE_U_TRUE", expected(1),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.ge_u))"),
            testCase("GE_U_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.ge_u))"),
            testCase("GE_U_FALSE", expected(0),
                        "(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.ge_u))"),
            testCase("COUNT_LEADING_ZEROES", expected(3),
                        "(module (func (result i32) i32.const 0x12345678 i32.clz))"),
            testCase("COUNT_TRAILING_ZEROES_1", expected(2),
                        "(module (func (result i32) i32.const 0x1234567C i32.ctz))"),
            testCase("COUNT_TRAILING_ZEROES_2", expected(4),
                        "(module (func (result i32) i32.const 0x12345670 i32.ctz))"),
            testCase("COUNT_ONES_POPCNT", expected(20),
                        "(module (func (result i32) i32.const 0xbabababa i32.popcnt))"),
            testCase("ADD", expected(59),
                        "(module (func (result i32) i32.const 42 i32.const 17 i32.add))"),
            testCase("SUB", expected(25),
                        "(module (func (result i32) i32.const 42 i32.const 17 i32.sub))"),
            testCase("SUB_NEG_RESULT", expected(-1),
                        "(module (func (result i32) i32.const 42 i32.const 43 i32.sub))"),
            testCase("MUL", expected(132),
                        "(module (func (result i32) i32.const 11 i32.const 12 i32.mul))"),
            testCase("DIV_S", expected(3),
                        "(module (func (result i32) i32.const 11 i32.const 3 i32.div_s))"),
            testCase("DIV_S_NEG_RESULT", expected(-3),
                        "(module (func (result i32) i32.const -11 i32.const 3 i32.div_s))"),
            testCase("DIV_U", expected(10),
                        "(module (func (result i32) i32.const 118 i32.const 11 i32.div_u))"),
            testCase("DIV_U_NEG", expected(524_288),
                        "(module (func (result i32) i32.const 0x80000001 i32.const 0x1000 i32.div_u))"),
            testCase("REM_S_BOTH_POS", expected(2),
                        "(module (func (result i32) i32.const 11 i32.const 3 i32.rem_s))"),
            testCase("REM_S_POS_NEG", expected(2),
                        "(module (func (result i32) i32.const 11 i32.const -3 i32.rem_s))"),
            testCase("REM_S_BOTH_NEG", expected(-2),
                        "(module (func (result i32) i32.const -11 i32.const -3 i32.rem_s))"),
            testCase("REM_S_NEG_POS", expected(-2),
                        "(module (func (result i32) i32.const -11 i32.const 3 i32.rem_s))"),
            testCase("REM_U_NEG", expected(129),
                        "(module (func (result i32) i32.const 0x80000001 i32.const 0x1001 i32.rem_u))"),
            testCase("LOGICAL_AND", expected(2),
                        "(module (func (result i32) i32.const 10 i32.const 7 i32.and))"),
            testCase("LOGICAL_AND_NEG", expected(7),
                        "(module (func (result i32) i32.const -1 i32.const 7 i32.and))"),
            testCase("LOGICAL_OR_NEG_RESULT", expected(-1),
                        "(module (func (result i32) i32.const -2 i32.const 7 i32.or))"),
            testCase("LOGICAL_OR", expected(15),
                        "(module (func (result i32) i32.const 6 i32.const 9 i32.or))"),
            testCase("LOGICAL_XOR", expected(15),
                        "(module (func (result i32) i32.const 11 i32.const 4 i32.xor))"),
            testCase("LOGICAL_XOR_NEG_RESULT", expected(-3),
                        "(module (func (result i32) i32.const -2 i32.const 3 i32.xor))"),
            testCase("SHIFT_LEFT", expected(-2),
                        "(module (func (result i32) i32.const -1 i32.const 1 i32.shl))"),
            testCase("SHIFT_RIGHT_SIGNED", expected(-1),
                        "(module (func (result i32) i32.const -1 i32.const 1 i32.shr_s))"),
            testCase("SHIFT_RIGHT_UNSIGNED", expected(Integer.MAX_VALUE),
                        "(module (func (result i32) i32.const -1 i32.const 1 i32.shr_u))"),
            testCase("ROTATE_LEFT", expected(-3),
                        "(module (func (result i32) i32.const -2 i32.const 1 i32.rotl))"),
            testCase("ROTATE_RIGHT", expected(-2),
                        "(module (func (result i32) i32.const -3 i32.const 1 i32.rotr))"),
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
