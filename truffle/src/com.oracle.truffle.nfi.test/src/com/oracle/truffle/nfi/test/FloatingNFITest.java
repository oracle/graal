/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collection;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class FloatingNFITest extends NFITest {

    private static void addTest(ArrayList<Object[]> tests, Object value, double exactNumericValue, float roundedNumericValue, String label) {
        tests.add(new Object[]{value, exactNumericValue, roundedNumericValue, label});
    }

    private static void addTests(ArrayList<Object[]> tests, double value) {
        addTest(tests, value, value, (float) value, "double:" + value);

        float floatValue = (float) value;
        addTest(tests, floatValue, floatValue, floatValue, "float:" + floatValue);
    }

    @Parameters(name = "{3}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();

        addTests(ret, 3.0); // integer
        addTests(ret, 0.5); // exact fraction
        addTests(ret, 0.1); // rounding error in binary representation

        return ret;
    }

    @Parameter(0) public Object value;
    @Parameter(1) public double exactNumericValue;
    @Parameter(2) public float roundedNumericValue;
    @Parameter(3) public String label;

    static final class FloatingMatcher extends BaseMatcher<Object> {

        private final double expected;

        FloatingMatcher(double expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(Object item) {
            try {
                return UNCACHED_INTEROP.asDouble(item) == expected;
            } catch (UnsupportedMessageException ex) {
                return false;
            }
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(Double.toString(expected));
        }
    }

    private static Matcher<Object> number(double expected) {
        return new FloatingMatcher(expected);
    }

    public class IncrementDoubleNode extends SendExecuteNode {

        public IncrementDoubleNode() {
            super("increment_DOUBLE", "(DOUBLE):DOUBLE");
        }
    }

    @Test
    public void testDoubleParameter(@Inject(IncrementDoubleNode.class) CallTarget increment) {
        Object ret = increment.call(value);
        assertThat("return value", ret, is(number(exactNumericValue + 1)));
    }

    public class IncrementFloatNode extends SendExecuteNode {

        public IncrementFloatNode() {
            super("increment_FLOAT", "(FLOAT):FLOAT");
        }
    }

    @Test
    public void testFloatParameter(@Inject(IncrementFloatNode.class) CallTarget increment) {
        Object ret = increment.call(value);
        assertThat("return value", ret, is(number(roundedNumericValue + 1)));
    }

    public class IncrementIntNode extends SendExecuteNode {

        public IncrementIntNode() {
            super("increment_SINT32", "(SINT32):SINT32");
        }
    }

    @Test
    public void testIntParameter(@Inject(IncrementIntNode.class) CallTarget increment) {
        long intValue = (long) exactNumericValue;
        Runnable testRunnable = () -> {
            Object ret = increment.call(value);
            assertThat("return value", ret, is(number(intValue + 1)));
        };
        if (intValue == exactNumericValue) {
            testRunnable.run();
        } else {
            AssertionError error = Assert.assertThrows(AssertionError.class, testRunnable::run);
            MatcherAssert.assertThat(error.getCause(), IsInstanceOf.instanceOf(UnsupportedTypeException.class));
        }
    }
}
