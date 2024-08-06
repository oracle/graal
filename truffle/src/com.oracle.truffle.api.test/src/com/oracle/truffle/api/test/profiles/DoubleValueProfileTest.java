/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.profiles;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.dsl.InlineSupport.InlinableField;
import com.oracle.truffle.api.profiles.DoubleValueProfile;
import com.oracle.truffle.api.profiles.InlinedDoubleValueProfile;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class DoubleValueProfileTest extends AbstractProfileTest {

    private static double[] VALUES = new double[]{Double.MIN_VALUE, -0.0d, +0.0d, 14.5f, Double.MAX_VALUE};

    private static final double FLOAT_DELTA = 0.00001f;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Override
    protected InlinableField[] getInlinedFields() {
        return createInlinedFields(1, 0, 0, 2, 0);
    }

    public static boolean exactCompare(double a, double b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    @Test
    public void testNotCrashing() {
        DoubleValueProfile profile = createEnabled(DoubleValueProfile.class);
        profile.disable();
        profile.reset();
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitial() {
        DoubleValueProfile profile = createEnabled(DoubleValueProfile.class);
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testProfileOneFloat() {
        for (double value : VALUES) {
            DoubleValueProfile profile = createEnabled(DoubleValueProfile.class);
            double result = profile.profile(value);

            assertThat(result, is(value));
            assertEquals((double) getCachedValue(profile), value, FLOAT_DELTA);
            assertThat(isUninitialized(profile), is(false));
            profile.toString(); // test that it is not crashing
        }
    }

    @Test
    public void testProfileTwoFloat() {
        for (double value0 : VALUES) {
            for (double value1 : VALUES) {
                DoubleValueProfile profile = createEnabled(DoubleValueProfile.class);
                double result0 = profile.profile(value0);
                double result1 = profile.profile(value1);

                assertEquals(result0, value0, FLOAT_DELTA);
                assertEquals(result1, value1, FLOAT_DELTA);

                if (exactCompare(value0, value1)) {
                    assertEquals((double) getCachedValue(profile), value0, FLOAT_DELTA);
                    assertThat(isGeneric(profile), is(false));
                } else {
                    assertThat(isGeneric(profile), is(true));
                }
                assertThat(isUninitialized(profile), is(false));
                profile.toString(); // test that it is not crashing
            }
        }
    }

    @Test
    public void testProfileThreeFloat() {
        for (double value0 : VALUES) {
            for (double value1 : VALUES) {
                for (double value2 : VALUES) {
                    DoubleValueProfile profile = createEnabled(DoubleValueProfile.class);
                    double result0 = profile.profile(value0);
                    double result1 = profile.profile(value1);
                    double result2 = profile.profile(value2);

                    assertEquals(result0, value0, FLOAT_DELTA);
                    assertEquals(result1, value1, FLOAT_DELTA);
                    assertEquals(result2, value2, FLOAT_DELTA);

                    if (exactCompare(value0, value1) && exactCompare(value1, value2)) {
                        assertEquals((double) getCachedValue(profile), value0, FLOAT_DELTA);
                        assertThat(isGeneric(profile), is(false));
                    } else {
                        assertThat(isGeneric(profile), is(true));
                    }
                    assertThat(isUninitialized(profile), is(false));
                    profile.toString(); // test that it is not crashing
                }
            }
        }
    }

    @Test
    public void testNotCrashingInlined() {
        InlinedDoubleValueProfile profile = createEnabled(InlinedDoubleValueProfile.class);
        profile.disable(state);
        profile.reset(state);
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitialInlined() {
        DoubleValueProfile profile = createEnabled(DoubleValueProfile.class);
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testProfileOneFloatInlined() {
        for (double value : VALUES) {
            InlinedDoubleValueProfile profile = createEnabled(InlinedDoubleValueProfile.class);
            double result = profile.profile(state, value);

            assertThat(result, is(value));
            assertEquals((double) getCachedValue(profile), value, FLOAT_DELTA);
            assertThat(isUninitialized(profile), is(false));
            profile.toString(); // test that it is not crashing
        }
    }

    @Test
    public void testProfileTwoFloatInlined() {
        for (double value0 : VALUES) {
            for (double value1 : VALUES) {
                InlinedDoubleValueProfile profile = createEnabled(InlinedDoubleValueProfile.class);
                double result0 = profile.profile(state, value0);
                double result1 = profile.profile(state, value1);

                assertEquals(result0, value0, FLOAT_DELTA);
                assertEquals(result1, value1, FLOAT_DELTA);

                if (exactCompare(value0, value1)) {
                    assertEquals((double) getCachedValue(profile), value0, FLOAT_DELTA);
                    assertThat(isGeneric(profile), is(false));
                } else {
                    assertThat(isGeneric(profile), is(true));
                }
                assertThat(isUninitialized(profile), is(false));
                profile.toString(); // test that it is not crashing
            }
        }
    }

    @Test
    public void testProfileThreeFloatInlined() {
        for (double value0 : VALUES) {
            for (double value1 : VALUES) {
                for (double value2 : VALUES) {
                    InlinedDoubleValueProfile profile = createEnabled(InlinedDoubleValueProfile.class);
                    double result0 = profile.profile(state, value0);
                    double result1 = profile.profile(state, value1);
                    double result2 = profile.profile(state, value2);

                    assertEquals(result0, value0, FLOAT_DELTA);
                    assertEquals(result1, value1, FLOAT_DELTA);
                    assertEquals(result2, value2, FLOAT_DELTA);

                    if (exactCompare(value0, value1) && exactCompare(value1, value2)) {
                        assertEquals((double) getCachedValue(profile), value0, FLOAT_DELTA);
                        assertThat(isGeneric(profile), is(false));
                    } else {
                        assertThat(isGeneric(profile), is(true));
                    }
                    assertThat(isUninitialized(profile), is(false));
                    profile.toString(); // test that it is not crashing
                }
            }
        }
    }

    @Test
    public void testDisabled() {
        DoubleValueProfile p = DoubleValueProfile.getUncached();
        for (double value : VALUES) {
            assertThat(p.profile(value), is(value));
        }
        p.toString(); // test that it is not crashing
    }

    @Test
    public void testDisabledInlined() {
        InlinedDoubleValueProfile p = InlinedDoubleValueProfile.getUncached();
        for (double value : VALUES) {
            assertThat(p.profile(state, value), is(value));
        }
        p.toString(); // test that it is not crashing
    }

}
