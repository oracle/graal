/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.arithmetic;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.graalvm.wasm.test.WasmSuiteBase;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.graalvm.wasm.utils.cases.WasmStringCase;
import org.junit.Test;

public class Float32Suite extends WasmSuiteBase {
    private WasmStringCase[] testCases = {
                    WasmCase.create("CONST", WasmCase.expectedFloat(340.75f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 340.75))"),
                    WasmCase.create("EQ_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.5 f32.eq))"),
                    WasmCase.create("EQ_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.1 f32.eq))"),
                    WasmCase.create("EQ_TRUE_NEG_ZEROES", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -0.0 f32.const 0.0 f32.eq))"),
                    WasmCase.create("EQ_FALSE_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.eq))"),
                    WasmCase.create("NE_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.5 f32.ne))"),
                    WasmCase.create("NE_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 0.5 f32.const 0.1 f32.ne))"),
                    WasmCase.create("NE_FALSE_NEG_ZEROES", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -0.0 f32.const 0.0 f32.ne))"),
                    WasmCase.create("NE_TRUE_NAN", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.ne))"),
                    WasmCase.create("LT_FALSE_BOTH_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_PLUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.lt))"),
                    WasmCase.create("LT_TRUE_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.lt))"),
                    WasmCase.create("LT_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const inf f32.lt))"),
                    WasmCase.create("LT_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const nan f32.lt))"),
                    WasmCase.create("LT_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.lt))"),
                    WasmCase.create("LT_FALSE_EQUAL", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.lt))"),
                    WasmCase.create("LT_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.0 f32.lt))"),
                    WasmCase.create("LT_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.lt))"),
                    WasmCase.create("GT_FALSE_BOTH_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_PLUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.gt))"),
                    WasmCase.create("GT_FALSE_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.gt))"),
                    WasmCase.create("GT_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const -inf f32.gt))"),
                    WasmCase.create("GT_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const nan f32.gt))"),
                    WasmCase.create("GT_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.gt))"),
                    WasmCase.create("GT_FALSE_EQUAL", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.gt))"),
                    WasmCase.create("GT_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.5 f32.gt))"),
                    WasmCase.create("GT_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.gt))"),
                    WasmCase.create("LE_TRUE_BOTH_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.le))"),
                    WasmCase.create("LE_TRUE_BOTH_PLUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.le))"),
                    WasmCase.create("LE_TRUE_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.le))"),
                    WasmCase.create("LE_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const inf f32.le))"),
                    WasmCase.create("LE_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const nan f32.le))"),
                    WasmCase.create("LE_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.le))"),
                    WasmCase.create("LE_TRUE_EQUAL", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.le))"),
                    WasmCase.create("LE_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.0 f32.le))"),
                    WasmCase.create("LE_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.le))"),
                    WasmCase.create("GE_TRUE_BOTH_MINUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -inf f32.ge))"),
                    WasmCase.create("GE_TRUE_BOTH_PLUS_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const inf f32.ge))"),
                    WasmCase.create("GE_FALSE_MINUS_INF", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const -inf f32.const -1 f32.ge))"),
                    WasmCase.create("GE_TRUE_INF", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const -inf f32.ge))"),
                    WasmCase.create("GE_FALSE_INF_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const inf f32.const nan f32.ge))"),
                    WasmCase.create("GE_FALSE_BOTH_NAN", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const nan f32.const nan f32.ge))"),
                    WasmCase.create("GE_TRUE_EQUAL", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.0 f32.ge))"),
                    WasmCase.create("GE_TRUE", WasmCase.expected(1),
                                    "(module (func (export \"_main\") (result i32) f32.const 2.0 f32.const 1.5 f32.ge))"),
                    WasmCase.create("GE_FALSE", WasmCase.expected(0),
                                    "(module (func (export \"_main\") (result i32) f32.const 1.0 f32.const 1.5 f32.ge))"),
                    WasmCase.create("ABS_NEG", WasmCase.expectedFloat(122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.abs))"),
                    WasmCase.create("ABS_POS", WasmCase.expectedFloat(122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.abs))"),
                    WasmCase.create("NEGATE_NEG", WasmCase.expectedFloat(122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.neg))"),
                    WasmCase.create("NEGATE_POS", WasmCase.expectedFloat(-122.34f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.neg))"),
                    WasmCase.create("CEIL_NEG", WasmCase.expectedFloat(-122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.ceil))"),
                    WasmCase.create("CEIL_POS", WasmCase.expectedFloat(123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.ceil))"),
                    WasmCase.create("FLOOR_NEG", WasmCase.expectedFloat(-123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.floor))"),
                    WasmCase.create("FLOOR_POS", WasmCase.expectedFloat(122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.floor))"),
                    WasmCase.create("TRUNC_NEG", WasmCase.expectedFloat(-122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.34 f32.trunc))"),
                    WasmCase.create("TRUNC_POS", WasmCase.expectedFloat(122.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.34 f32.trunc))"),
                    WasmCase.create("NEAREST_NEG", WasmCase.expectedFloat(-123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -122.54 f32.nearest))"),
                    WasmCase.create("NEAREST_POS", WasmCase.expectedFloat(123.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.54 f32.nearest))"),
                    WasmCase.create("SQRT", WasmCase.expectedFloat(11.0698f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 122.54 f32.sqrt))"),
                    WasmCase.create("ADD", WasmCase.expectedFloat(-6.5f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.add))"),
                    WasmCase.create("SUB", WasmCase.expectedFloat(2.5f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.sub))"),
                    WasmCase.create("MUL", WasmCase.expectedFloat(9.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.mul))"),
                    WasmCase.create("DIV", WasmCase.expectedFloat(0.4444f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.div))"),
                    WasmCase.create("MIN", WasmCase.expectedFloat(-4.5f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.min))"),
                    WasmCase.create("MAX", WasmCase.expectedFloat(-2.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const -2.0 f32.const -4.5 f32.max))"),
                    WasmCase.create("COPYSIGN", WasmCase.expectedFloat(-2.0f, 0.0001f),
                                    "(module (func (export \"_main\") (result f32) f32.const 2.0 f32.const -4.5 f32.copysign))"),
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
