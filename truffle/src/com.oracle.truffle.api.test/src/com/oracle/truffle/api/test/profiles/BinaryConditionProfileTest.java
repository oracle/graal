/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.ReflectionUtils.invoke;
import static com.oracle.truffle.api.test.ReflectionUtils.invokeStatic;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.profiles.ConditionProfile;

@RunWith(Theories.class)
public class BinaryConditionProfileTest {

    @DataPoints public static boolean[] data = new boolean[]{true, false};

    private ConditionProfile profile;

    @Before
    public void create() {
        profile = (ConditionProfile) invokeStatic(loadRelative(BinaryConditionProfileTest.class, "ConditionProfile$Binary"), "create");
    }

    private static boolean wasTrue(ConditionProfile profile) {
        return (boolean) invoke(profile, "wasTrue");
    }

    private static boolean wasFalse(ConditionProfile profile) {
        return (boolean) invoke(profile, "wasFalse");
    }

    @Test
    public void testInitial() {
        assertThat(wasTrue(profile), is(false));
        assertThat(wasFalse(profile), is(false));
        profile.toString();
    }

    @Theory
    public void testProfileOne(boolean value) {
        boolean result = profile.profile(value);

        assertThat(result, is(value));
        assertThat(wasTrue(profile), is(value));
        assertThat(wasFalse(profile), is(!value));
        profile.toString();
    }

    @Theory
    public void testProfileTwo(boolean value0, boolean value1) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(wasTrue(profile), is(value0 || value1));
        assertThat(wasFalse(profile), is(!value0 || !value1));
        profile.toString();
    }

    @Theory
    public void testProfileThree(boolean value0, boolean value1, boolean value2) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);
        boolean result2 = profile.profile(value2);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(result2, is(value2));
        assertThat(wasTrue(profile), is(value0 || value1 || value2));
        assertThat(wasFalse(profile), is(!value0 || !value1 || !value2));
        profile.toString();
    }

}
