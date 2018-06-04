/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
        profile = (LoopConditionProfile) invokeStatic(loadRelative(PrimitiveValueProfileTest.class, "LoopConditionProfile$Enabled"), "create");
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
