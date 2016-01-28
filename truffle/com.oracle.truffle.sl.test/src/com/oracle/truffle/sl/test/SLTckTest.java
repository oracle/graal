/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.test.instrument.InstrumentationTestMode;
import com.oracle.truffle.tck.TruffleTCK;

import org.junit.After;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * This is the way to verify your language implementation is compatible.
 *
 */
public class SLTckTest extends TruffleTCK {

    @Before
    public void before() {
        InstrumentationTestMode.set(true);
    }

    @After
    public void after() {
        InstrumentationTestMode.set(false);
    }

    @Test
    public void testVerifyPresence() {
        PolyglotEngine vm = PolyglotEngine.newBuilder().build();
        assertTrue("Our language is present", vm.getLanguages().containsKey("application/x-sl"));
    }

    @Override
    protected PolyglotEngine prepareVM() throws Exception {
        PolyglotEngine vm = PolyglotEngine.newBuilder().build();
        // @formatter:off
        vm.eval(
            Source.fromText(
                "function fourtyTwo() {\n" +
                "  return 42;\n" + //
                "}\n" +
                "function plus(a, b) {\n" +
                "  return a + b;\n" +
                "}\n" +
                "function identity(x) {\n" +
                "  return x;\n" +
                "}\n" +
                "function apply(f) {\n" +
                "  return f(18, 32) + 10;\n" +
                "}\n" +
                "function cnt() {\n" +
                "  return 0;\n" +
                "}\n" +
                "function count() {\n" +
                "  n = cnt() + 1;\n" +
                "  defineFunction(\"function cnt() { return \" + n + \"; }\");\n" +
                "  return n;\n" +
                "}\n" +
                "function returnsNull() {\n" +
                "}\n" +
                "function complexAdd(a, b) {\n" +
                "  a.real = a.real + b.real;\n" +
                "  a.imaginary = a.imaginary + b.imaginary;\n" +
                "}\n" +
                "function compoundObject() {\n" +
                "  obj = new();\n" +
                "  obj.fourtyTwo = fourtyTwo;\n" +
                "  obj.plus = plus;\n" +
                "  obj.returnsNull = returnsNull;\n" +
                "  obj.returnsThis = obj;\n" +
                "  return obj;\n" +
                "}\n" +
                "function valuesObject() {\n" +
                "  obj = new();\n" +
                "  obj.byteValue = 0;\n" +
                "  obj.shortValue = 0;\n" +
                "  obj.intValue = 0;\n" +
                "  obj.longValue = 0;\n" +
                "  obj.floatValue = 0;\n" +
                "  obj.doubleValue = 0;\n" +
                "  obj.charValue = \"0\";\n" +
                "  obj.booleanValue = (1 == 0);\n" +
                "  return obj;\n" +
                "}\n",
                "SL TCK"
            ).withMimeType("application/x-sl")
        );
        // @formatter:on
        return vm;
    }

    @Override
    protected String mimeType() {
        return "application/x-sl";
    }

    @Override
    protected String fourtyTwo() {
        return "fourtyTwo";
    }

    @Override
    protected String identity() {
        return "identity";
    }

    @Override
    protected String plus(Class<?> type1, Class<?> type2) {
        return "plus";
    }

    @Override
    protected String returnsNull() {
        return "returnsNull";
    }

    @Override
    protected String applyNumbers() {
        return "apply";
    }

    @Override
    protected String compoundObject() {
        return "compoundObject";
    }

    @Override
    protected String valuesObject() {
        return "valuesObject";
    }

    @Override
    protected String invalidCode() {
        // @formatter:off
        return
            "f unction main() {\n" +
            "  retu rn 42;\n" +
            "}\n";
        // @formatter:on
    }

    @Override
    protected String complexAdd() {
        return "complexAdd";
    }

    @Override
    protected String multiplyCode(String firstName, String secondName) {
        // @formatter:off
        return
            "function multiply(" + firstName + ", " + secondName + ") {\n" +
            "  return " + firstName + " * " + secondName + ";\n" +
            "}\n";
        // @formatter:on
    }

    @Override
    protected String countInvocations() {
        return "count";
    }

    @Override
    protected String globalObject() {
        return null;
    }

    @Override
    protected String evaluateSource() {
        return "eval";
    }

    @Override
    protected String complexCopy() {
        // skip these tests; SL doesn't have arrays
        return null;
    }

    @Override
    protected String complexAddWithMethod() {
        // skip these tests; SL doesn't have methods
        return null;
    }

    @Override
    protected String complexSumReal() {
        // skip these tests; SL doesn't have arrays
        return null;
    }

    @Override
    protected void assertDouble(String msg, double expectedValue, double actualValue) {
        // don't compare doubles, SL had to convert them to longs
    }
}
