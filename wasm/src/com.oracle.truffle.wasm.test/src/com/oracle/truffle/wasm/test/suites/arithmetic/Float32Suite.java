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
import static com.oracle.truffle.wasm.utils.cases.WasmCase.expectedFloat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

import com.oracle.truffle.wasm.test.WasmSuiteBase;
import com.oracle.truffle.wasm.utils.cases.WasmCase;
import com.oracle.truffle.wasm.utils.cases.WasmStringCase;

public class Float32Suite extends WasmSuiteBase {
    private WasmStringCase[] testCases = {
                    WasmCase.create("CONST", expectedFloat(340.75f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 340.75))"),
                    WasmCase.create("EQ_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.5 f32.eq))"),
                    WasmCase.create("EQ_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.1 f32.eq))"),
                    WasmCase.create("EQ_TRUE_NEG_ZEROES", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -0.0 f32.const 0.0 f32.eq))"),
                    WasmCase.create("EQ_FALSE_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.eq))"),
                    WasmCase.create("NE_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.5 f32.ne))"),
                    WasmCase.create("NE_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.1 f32.ne))"),
                    WasmCase.create("NE_FALSE_NEG_ZEROES", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -0.0 f32.const 0.0 f32.ne))"),
                    WasmCase.create("NE_TRUE_NAN", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.ne))"),
                    WasmCase.create("LT_FALSE_BOTH_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_PLUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.lt))"),
                    WasmCase.create("LT_TRUE_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.lt))"),
                    WasmCase.create("LT_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const inf f32.lt))"),
                    WasmCase.create("LT_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const nan f32.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.lt))"),
                    WasmCase.create("LT_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.lt))"),
                    WasmCase.create("LT_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.0 f32.lt))"),
                    WasmCase.create("LT_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.lt))"),
                    WasmCase.create("GT_FALSE_BOTH_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_PLUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.gt))"),
                    WasmCase.create("GT_FALSE_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.gt))"),
                    WasmCase.create("GT_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const -inf f32.gt))"),
                    WasmCase.create("GT_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const nan f32.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.gt))"),
                    WasmCase.create("GT_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.gt))"),
                    WasmCase.create("GT_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.5 f32.gt))"),
                    WasmCase.create("GT_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.gt))"),
                    WasmCase.create("LE_TRUE_BOTH_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.le))"),
                    WasmCase.create("LE_TRUE_BOTH_PLUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.le))"),
                    WasmCase.create("LE_TRUE_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.le))"),
                    WasmCase.create("LE_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const inf f32.le))"),
                    WasmCase.create("LE_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const nan f32.le))"),
                    WasmCase.create("LE_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.le))"),
                    WasmCase.create("LE_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.le))"),
                    WasmCase.create("LE_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.0 f32.le))"),
                    WasmCase.create("LE_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.le))"),
                    WasmCase.create("GE_TRUE_BOTH_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.ge))"),
                    WasmCase.create("GE_TRUE_BOTH_PLUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.ge))"),
                    WasmCase.create("GE_FALSE_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.ge))"),
                    WasmCase.create("GE_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const -inf f32.ge))"),
                    WasmCase.create("GE_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const nan f32.ge))"),
                    WasmCase.create("GE_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.ge))"),
                    WasmCase.create("GE_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.ge))"),
                    WasmCase.create("GE_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.5 f32.ge))"),
                    WasmCase.create("GE_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.ge))"),
                    WasmCase.create("ABS_NEG", expectedFloat(122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.abs))"),
                    WasmCase.create("ABS_POS", expectedFloat(122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.abs))"),
                    WasmCase.create("NEGATE_NEG", expectedFloat(122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.neg))"),
                    WasmCase.create("NEGATE_POS", expectedFloat(-122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.neg))"),
                    WasmCase.create("CEIL_NEG", expectedFloat(-122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.ceil))"),
                    WasmCase.create("CEIL_POS", expectedFloat(123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.ceil))"),
                    WasmCase.create("FLOOR_NEG", expectedFloat(-123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.floor))"),
                    WasmCase.create("FLOOR_POS", expectedFloat(122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.floor))"),
                    WasmCase.create("TRUNC_NEG", expectedFloat(-122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.trunc))"),
                    WasmCase.create("TRUNC_POS", expectedFloat(122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.trunc))"),
                    WasmCase.create("NEAREST_NEG", expectedFloat(-123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.54 f32.nearest))"),
                    WasmCase.create("NEAREST_POS", expectedFloat(123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.54 f32.nearest))"),
                    WasmCase.create("SQRT", expectedFloat(11.0698f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.54 f32.sqrt))"),
                    WasmCase.create("ADD", expectedFloat(-6.5f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.add))"),
                    WasmCase.create("SUB", expectedFloat(2.5f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.sub))"),
                    WasmCase.create("MUL", expectedFloat(9.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.mul))"),
                    WasmCase.create("DIV", expectedFloat(0.4444f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.div))"),
                    WasmCase.create("MIN", expectedFloat(-4.5f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.min))"),
                    WasmCase.create("MAX", expectedFloat(-2.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.max))"),
                    WasmCase.create("COPYSIGN", expectedFloat(-2.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 2.0 f32.const -4.5 f32.copysign))"),
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
