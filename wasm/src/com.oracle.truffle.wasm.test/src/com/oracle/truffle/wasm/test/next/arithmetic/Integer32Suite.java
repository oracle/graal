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

import java.util.Arrays;
import java.util.Collection;

import com.oracle.truffle.wasm.test.next.WasmSuiteBase;

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
    };

    @Override
    protected Collection<? extends WasmTestCase> collectTestCases() {
        return Arrays.asList(testCases);
    }
}
