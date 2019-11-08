/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.graalvm.wasm.test.suites.arithmetic;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.graalvm.wasm.test.WasmSuiteBase;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.graalvm.wasm.utils.cases.WasmStringCase;
import org.junit.Test;

public class Float64Suite extends WasmSuiteBase {
    private WasmStringCase[] testCases = {
                    WasmCase.create("CONST", WasmCase.expectedDouble(340.75, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 340.75))"),
                    WasmCase.create("EQ_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.5 f64.eq))"),
                    WasmCase.create("EQ_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.1 f64.eq))"),
                    WasmCase.create("EQ_TRUE_NEG_ZEROES", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -0.0 f64.const 0.0 f64.eq))"),
                    WasmCase.create("EQ_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.eq))"),
                    WasmCase.create("NE_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.5 f64.ne))"),
                    WasmCase.create("NE_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 0.5 f64.const 0.1 f64.ne))"),
                    WasmCase.create("NE_FALSE_NEG_ZEROES", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -0.0 f64.const 0.0 f64.ne))"),
                    WasmCase.create("NE_TRUE_BOTH_NAN", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.ne))"),
                    WasmCase.create("LT_FALSE_BOTH_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_PLUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.lt))"),
                    WasmCase.create("LT_TRUE_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.lt))"),
                    WasmCase.create("LT_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const inf f64.lt))"),
                    WasmCase.create("LT_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const nan f64.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.lt))"),
                    WasmCase.create("LT_FALSE_EQUAL", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.lt))"),
                    WasmCase.create("LT_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.0 f64.lt))"),
                    WasmCase.create("LT_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.lt))"),
                    WasmCase.create("GT_FALSE_BOTH_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_PLUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.gt))"),
                    WasmCase.create("GT_FALSE_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.gt))"),
                    WasmCase.create("GT_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const -inf f64.gt))"),
                    WasmCase.create("GT_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const nan f64.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.gt))"),
                    WasmCase.create("GT_FALSE_EQUAL", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.gt))"),
                    WasmCase.create("GT_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.5 f64.gt))"),
                    WasmCase.create("GT_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.gt))"),
                    WasmCase.create("LE_TRUE_BOTH_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.le))"),
                    WasmCase.create("LE_TRUE_BOTH_PLUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.le))"),
                    WasmCase.create("LE_TRUE_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.le))"),
                    WasmCase.create("LE_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const inf f64.le))"),
                    WasmCase.create("LE_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const nan f64.le))"),
                    WasmCase.create("LE_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.le))"),
                    WasmCase.create("LE_TRUE_EQUAL", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.le))"),
                    WasmCase.create("LE_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.0 f64.le))"),
                    WasmCase.create("LE_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.le))"),
                    WasmCase.create("GE_TRUE_BOTH_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -inf f64.ge))"),
                    WasmCase.create("GE_TRUE_BOTH_PLUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const inf f64.ge))"),
                    WasmCase.create("GE_FALSE_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const -inf f64.const -1 f64.ge))"),
                    WasmCase.create("GE_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const -inf f64.ge))"),
                    WasmCase.create("GE_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const inf f64.const nan f64.ge))"),
                    WasmCase.create("GE_FALSE_NAN_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const nan f64.const nan f64.ge))"),
                    WasmCase.create("GE_TRUE_EQUAL", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.0 f64.ge))"),
                    WasmCase.create("GE_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f64.const 2.0 f64.const 1.5 f64.ge))"),
                    WasmCase.create("GE_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f64.const 1.0 f64.const 1.5 f64.ge))"),
                    WasmCase.create("ABS_NEG", WasmCase.expectedDouble(122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.abs))"),
                    WasmCase.create("ABS_POS", WasmCase.expectedDouble(122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.abs))"),
                    WasmCase.create("NEGATE_NEG", WasmCase.expectedDouble(122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.neg))"),
                    WasmCase.create("NEGATE_POS", WasmCase.expectedDouble(-122.34, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.neg))"),
                    WasmCase.create("CEIL_NEG", WasmCase.expectedDouble(-122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.ceil))"),
                    WasmCase.create("CEIL_POS", WasmCase.expectedDouble(123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.ceil))"),
                    WasmCase.create("FLOOR_NEG", WasmCase.expectedDouble(-123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.floor))"),
                    WasmCase.create("FLOOR_POS", WasmCase.expectedDouble(122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.floor))"),
                    WasmCase.create("TRUNC_NEG", WasmCase.expectedDouble(-122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.34 f64.trunc))"),
                    WasmCase.create("TRUNC_POS", WasmCase.expectedDouble(122.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.34 f64.trunc))"),
                    WasmCase.create("NEAREST_NEG", WasmCase.expectedDouble(-123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -122.54 f64.nearest))"),
                    WasmCase.create("NEAREST_POS", WasmCase.expectedDouble(123.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.54 f64.nearest))"),
                    WasmCase.create("SQRT", WasmCase.expectedDouble(11.0698, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 122.54 f64.sqrt))"),
                    WasmCase.create("ADD", WasmCase.expectedDouble(-6.5, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.add))"),
                    WasmCase.create("SUB", WasmCase.expectedDouble(2.5, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.sub))"),
                    WasmCase.create("MUL", WasmCase.expectedDouble(9.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.mul))"),
                    WasmCase.create("DIV", WasmCase.expectedDouble(0.4444, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.div))"),
                    WasmCase.create("MIN", WasmCase.expectedDouble(-4.5, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.min))"),
                    WasmCase.create("MAX", WasmCase.expectedDouble(-2.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const -2.0 f64.const -4.5 f64.max))"),
                    WasmCase.create("COPYSIGN", WasmCase.expectedDouble(-2.0, 0.0001f),
                                    "(module (func (export \"_main\") (result f64) f64.const 2.0 f64.const -4.5 f64.copysign))"),
    };

    @Override
    protected Collection<? extends WasmCase> collectStringTestCases() {
        return Arrays.asList(testCases);
    }

    @Override
    @Test
    public void test() throws IOException {
        // This is here just to make mx aware of the test suite class.
        super.test();
    }
}
