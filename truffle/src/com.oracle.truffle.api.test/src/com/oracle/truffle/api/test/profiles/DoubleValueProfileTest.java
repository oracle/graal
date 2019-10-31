/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.ReflectionUtils.getStaticField;
import static com.oracle.truffle.api.test.ReflectionUtils.invoke;
import static com.oracle.truffle.api.test.ReflectionUtils.invokeStatic;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.profiles.DoubleValueProfile;

@RunWith(Theories.class)
public class DoubleValueProfileTest {

    @DataPoint public static final double VALUE0 = Double.MIN_VALUE;
    @DataPoint public static final double VALUE1 = -0.0f;
    @DataPoint public static final double VALUE2 = +0.0f;
    @DataPoint public static final double VALUE3 = 14.5f;
    @DataPoint public static final double VALUE4 = Double.MAX_VALUE;

    private static final double FLOAT_DELTA = 0.00001f;

    private DoubleValueProfile profile;

    @Before
    public void create() {
        profile = (DoubleValueProfile) invokeStatic(loadRelative(DoubleValueProfileTest.class, "DoubleValueProfile$Enabled"), "create");
    }

    private static boolean isGeneric(DoubleValueProfile profile) {
        return (boolean) invoke(profile, "isGeneric");
    }

    private static boolean isUninitialized(DoubleValueProfile profile) {
        return (boolean) invoke(profile, "isUninitialized");
    }

    private static double getCachedValue(DoubleValueProfile profile) {
        return (double) invoke(profile, "getCachedValue");
    }

    public static boolean exactCompare(double a, double b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    @Test
    public void testInitial() {
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneFloat(double value) {
        double result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value, FLOAT_DELTA);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @SuppressWarnings("deprecation")
    @Theory
    public void testProfileTwoFloat(double value0, double value1) {
        double result0 = profile.profile(value0);
        double result1 = profile.profile(value1);

        assertEquals(result0, value0, FLOAT_DELTA);
        assertEquals(result1, value1, FLOAT_DELTA);

        if (exactCompare(value0, value1)) {
            assertEquals(getCachedValue(profile), value0, FLOAT_DELTA);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @SuppressWarnings("deprecation")
    @Theory
    public void testProfileThreeFloat(double value0, double value1, double value2) {
        double result0 = profile.profile(value0);
        double result1 = profile.profile(value1);
        double result2 = profile.profile(value2);

        assertEquals(result0, value0, FLOAT_DELTA);
        assertEquals(result1, value1, FLOAT_DELTA);
        assertEquals(result2, value2, FLOAT_DELTA);

        if (exactCompare(value0, value1) && exactCompare(value1, value2)) {
            assertEquals(getCachedValue(profile), value0, FLOAT_DELTA);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testDisabled() {
        DoubleValueProfile p = (DoubleValueProfile) getStaticField(loadRelative(DoubleValueProfileTest.class, "DoubleValueProfile$Disabled"), "INSTANCE");
        assertThat(p.profile(VALUE0), is(VALUE0));
        assertThat(p.profile(VALUE1), is(VALUE1));
        assertThat(p.profile(VALUE2), is(VALUE2));
        assertThat(p.profile(VALUE3), is(VALUE3));
        assertThat(p.profile(VALUE4), is(VALUE4));
        p.toString(); // test that it is not crashing
    }

}
