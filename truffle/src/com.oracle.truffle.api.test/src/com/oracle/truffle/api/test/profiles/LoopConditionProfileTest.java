/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.profiles.LoopConditionProfile;

@RunWith(Theories.class)
public class LoopConditionProfileTest {

    @DataPoints public static boolean[] data = new boolean[]{true, false};
    @DataPoints public static long[] lengthes = new long[]{Long.MAX_VALUE, 0L, Long.MAX_VALUE / 2L, Long.MAX_VALUE / 2 + 1L, 1L};

    private LoopConditionProfile profile;

    @Before
    public void create() {
        profile = (LoopConditionProfile) invokeStatic(loadRelative(PrimitiveValueProfileTest.class, "LoopConditionProfile$Enabled"), "createLazyLoadClass");
    }

    private static long getTrueCount(LoopConditionProfile profile) {
        return (long) invoke(profile, "getTrueCount");
    }

    private static int getFalseCount(LoopConditionProfile profile) {
        return (int) invoke(profile, "getFalseCount");
    }

    @Test
    public void testInitial() {
        assertThat(getTrueCount(profile), is(0L));
        assertThat(getFalseCount(profile), is(0));
        profile.toString(); // not crashing
    }

    @Test
    public void testToString() {
        profile.profile(true);
        profile.profile(true);
        profile.profile(true);
        profile.profile(false);
        assertThat(getTrueCount(profile), is(3L));
        assertThat(getFalseCount(profile), is(1));
        assertThat(profile.toString(), containsString("LoopConditionProfile(trueProbability=0.75 (trueCount=3, falseCount=1))"));
    }

    @Theory
    public void testDisabled(boolean value, long length) {
        LoopConditionProfile p = (LoopConditionProfile) getStaticField(loadRelative(PrimitiveValueProfileTest.class, "LoopConditionProfile$Disabled"), "INSTANCE");
        p.profileCounted(length); // not crashing
        assertThat(p.profile(value), is(value));
        assertThat(p.inject(value), is(value));
        p.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOne(boolean value) {
        boolean result = profile.profile(value);

        assertThat(result, is(value));
        assertThat(getTrueCount(profile), is(value ? 1L : 0L));
        assertThat(getFalseCount(profile), is(!value ? 1 : 0));
        profile.toString(); // not crashing
    }

    @Theory
    public void testProfileTwo(boolean value0, boolean value1) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(getTrueCount(profile), is((value0 ? 1L : 0L) + (value1 ? 1L : 0L)));
        assertThat(getFalseCount(profile), is((!value0 ? 1 : 0) + (!value1 ? 1 : 0)));
        profile.toString(); // not crashing
    }

    @Theory
    public void testProfileThree(boolean value0, boolean value1, boolean value2) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);
        boolean result2 = profile.profile(value2);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(result2, is(value2));
        assertThat(getTrueCount(profile), is((value0 ? 1L : 0L) + (value1 ? 1L : 0L) + (value2 ? 1L : 0L)));
        assertThat(getFalseCount(profile), is((!value0 ? 1 : 0) + (!value1 ? 1 : 0) + (!value2 ? 1 : 0)));
        profile.toString(); // not crashing
    }

    @Theory
    public void testLengthProfiling(long length1, long length2, long length3) {
        assertThat(profile.inject(false), is(false));

        profile.toString(); // not crashing
        profile.profileCounted(length1);
        profile.toString(); // not crashing
        long expectedLength = length1;
        assertThat(getTrueCount(profile), is(expectedLength));
        assertThat(getFalseCount(profile), is(1));
        assertThat(profile.inject(false), is(false));

        profile.profileCounted(length2);
        profile.toString(); // not crashing
        expectedLength += length2;
        if (expectedLength < 0) {
            assertThat(getTrueCount(profile), is(length1));
            assertThat(getFalseCount(profile), is(1));
            assertThat(profile.inject(false), is(false));

            profile.profileCounted(length3);
            expectedLength = length1 + length3;

            if (expectedLength < 0) {
                assertThat(getTrueCount(profile), is(length1));
                assertThat(getFalseCount(profile), is(1));
                assertThat(profile.inject(false), is(false));
            } else {
                assertThat(getTrueCount(profile), is(expectedLength));
                assertThat(getFalseCount(profile), is(2));
                assertThat(profile.inject(false), is(false));
            }
            return;
        } else {
            assertThat(getTrueCount(profile), is(expectedLength));
            assertThat(getFalseCount(profile), is(2));
            assertThat(profile.inject(false), is(false));
        }

        profile.profileCounted(length3);
        profile.toString(); // not crashing
        expectedLength += length3;

        if (expectedLength < 0) {
            assertThat(getTrueCount(profile), is(length1 + length2));
            assertThat(getFalseCount(profile), is(2));
            assertThat(profile.inject(false), is(false));
        } else {
            assertThat(getTrueCount(profile), is(expectedLength));
            assertThat(getFalseCount(profile), is(3));
            assertThat(profile.inject(false), is(false));
        }
        profile.toString(); // not crashing
    }

}
