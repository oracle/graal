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
import static com.oracle.truffle.wasm.utils.cases.WasmCase.expectedDouble;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

import com.oracle.truffle.wasm.test.WasmSuiteBase;
import com.oracle.truffle.wasm.utils.cases.WasmCase;
import com.oracle.truffle.wasm.utils.cases.WasmStringCase;

public class Float64Suite extends WasmSuiteBase {
    private WasmStringCase[] testCases = {
                    WasmCase.create("CONST", expectedDouble(340.75, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 340.75))"),
                    WasmCase.create("EQ_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.5 f64.eq))"),
                    WasmCase.create("EQ_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.1 f64.eq))"),
                    WasmCase.create("EQ_TRUE_NEG_ZEROES", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -0.0 f64.const 0.0 f64.eq))"),
                    WasmCase.create("EQ_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.eq))"),
                    WasmCase.create("NE_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.5 f64.ne))"),
                    WasmCase.create("NE_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.1 f64.ne))"),
                    WasmCase.create("NE_FALSE_NEG_ZEROES", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -0.0 f64.const 0.0 f64.ne))"),
                    WasmCase.create("NE_TRUE_BOTH_NAN", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.ne))"),
                    WasmCase.create("LT_FALSE_BOTH_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_PLUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.lt))"),
                    WasmCase.create("LT_TRUE_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.lt))"),
                    WasmCase.create("LT_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const inf f64.lt))"),
                    WasmCase.create("LT_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const nan f64.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.lt))"),
                    WasmCase.create("LT_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.lt))"),
                    WasmCase.create("LT_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.0 f64.lt))"),
                    WasmCase.create("LT_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.lt))"),
                    WasmCase.create("GT_FALSE_BOTH_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_PLUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.gt))"),
                    WasmCase.create("GT_FALSE_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.gt))"),
                    WasmCase.create("GT_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const -inf f64.gt))"),
                    WasmCase.create("GT_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const nan f64.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.gt))"),
                    WasmCase.create("GT_FALSE_EQUAL", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.gt))"),
                    WasmCase.create("GT_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.5 f64.gt))"),
                    WasmCase.create("GT_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.gt))"),
                    WasmCase.create("LE_TRUE_BOTH_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.le))"),
                    WasmCase.create("LE_TRUE_BOTH_PLUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.le))"),
                    WasmCase.create("LE_TRUE_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.le))"),
                    WasmCase.create("LE_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const inf f64.le))"),
                    WasmCase.create("LE_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const nan f64.le))"),
                    WasmCase.create("LE_FALSE_BOTH_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.le))"),
                    WasmCase.create("LE_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.le))"),
                    WasmCase.create("LE_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.0 f64.le))"),
                    WasmCase.create("LE_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.le))"),
                    WasmCase.create("GE_TRUE_BOTH_MINUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.ge))"),
                    WasmCase.create("GE_TRUE_BOTH_PLUS_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.ge))"),
                    WasmCase.create("GE_FALSE_MINUS_INF", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.ge))"),
                    WasmCase.create("GE_TRUE_INF", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const -inf f64.ge))"),
                    WasmCase.create("GE_FALSE_INF_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const nan f64.ge))"),
                    WasmCase.create("GE_FALSE_NAN_NAN", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.ge))"),
                    WasmCase.create("GE_TRUE_EQUAL", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.ge))"),
                    WasmCase.create("GE_TRUE", expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.5 f64.ge))"),
                    WasmCase.create("GE_FALSE", expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.ge))"),
                    WasmCase.create("ABS_NEG", expectedDouble(122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.abs))"),
                    WasmCase.create("ABS_POS", expectedDouble(122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.abs))"),
                    WasmCase.create("NEGATE_NEG", expectedDouble(122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.neg))"),
                    WasmCase.create("NEGATE_POS", expectedDouble(-122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.neg))"),
                    WasmCase.create("CEIL_NEG", expectedDouble(-122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.ceil))"),
                    WasmCase.create("CEIL_POS", expectedDouble(123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.ceil))"),
                    WasmCase.create("FLOOR_NEG", expectedDouble(-123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.floor))"),
                    WasmCase.create("FLOOR_POS", expectedDouble(122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.floor))"),
                    WasmCase.create("TRUNC_NEG", expectedDouble(-122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.trunc))"),
                    WasmCase.create("TRUNC_POS", expectedDouble(122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.trunc))"),
                    WasmCase.create("NEAREST_NEG", expectedDouble(-123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.54 f64.nearest))"),
                    WasmCase.create("NEAREST_POS", expectedDouble(123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.54 f64.nearest))"),
                    WasmCase.create("SQRT", expectedDouble(11.0698, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.54 f64.sqrt))"),
                    WasmCase.create("ADD", expectedDouble(-6.5, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.add))"),
                    WasmCase.create("SUB", expectedDouble(2.5, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.sub))"),
                    WasmCase.create("MUL", expectedDouble(9.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.mul))"),
                    WasmCase.create("DIV", expectedDouble(0.4444, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.div))"),
                    WasmCase.create("MIN", expectedDouble(-4.5, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.min))"),
                    WasmCase.create("MAX", expectedDouble(-2.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.max))"),
                    WasmCase.create("COPYSIGN", expectedDouble(-2.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 2.0 f64.const -4.5 f64.copysign))"),
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
