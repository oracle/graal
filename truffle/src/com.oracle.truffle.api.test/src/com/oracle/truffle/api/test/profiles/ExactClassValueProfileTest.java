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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.profiles.ValueProfile;

@RunWith(Theories.class)
public class ExactClassValueProfileTest {

    @SuppressWarnings("deprecation")
    private static Integer newInteger(int value) {
        return new Integer(value);
    }

    @DataPoint public static final String O1 = new String();
    @DataPoint public static final String O2 = new String();
    @DataPoint public static final Object O3 = new Object();
    @DataPoint public static final Integer O4 = newInteger(1);
    @DataPoint public static final Integer O5 = null;
    @DataPoint public static final TestBaseClass O6 = new TestBaseClass();
    @DataPoint public static final TestSubClass O7 = new TestSubClass();

    private ValueProfile profile;

    private static class TestBaseClass {
    }

    private static class TestSubClass extends TestBaseClass {
    }

    @Before
    public void create() {
        profile = (ValueProfile) invokeStatic(loadRelative(ExactClassValueProfileTest.class, "ValueProfile$ExactClass"), "create");
    }

    private static boolean isGeneric(ValueProfile profile) {
        return (boolean) invoke(profile, "isGeneric");
    }

    private static boolean isUninitialized(ValueProfile profile) {
        return (boolean) invoke(profile, "isUninitialized");
    }

    private static Object getCachedClass(ValueProfile profile) {
        return invoke(profile, "getCachedClass");
    }

    @Test
    public void testInitial() throws Exception {
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        assertNull(getCachedClass(profile));
        assertNotNull(profile.toString());
    }

    @Theory
    public void testProfileOne(Object value) throws Exception {
        Object result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(expectedClass(value), getCachedClass(profile));
        assertThat(isUninitialized(profile), is(false));
        assertNotNull(profile.toString());
    }

    @Theory
    public void testProfileTwo(Object value0, Object value1) throws Exception {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));

        Object expectedClass = expectedClass(value0) == expectedClass(value1) ? expectedClass(value0) : Object.class;

        assertEquals(expectedClass, getCachedClass(profile));
        assertThat(isUninitialized(profile), is(false));
        assertThat(isGeneric(profile), is(expectedClass == Object.class));
        assertNotNull(profile.toString());
    }

    @Theory
    public void testProfileThree(Object value0, Object value1, Object value2) throws Exception {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);
        Object result2 = profile.profile(value2);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(result2, is(value2));

        Object expectedClass = expectedClass(value0) == expectedClass(value1) && expectedClass(value1) == expectedClass(value2) ? expectedClass(value0) : Object.class;

        assertEquals(expectedClass, getCachedClass(profile));
        assertThat(isUninitialized(profile), is(false));
        assertThat(isGeneric(profile), is(expectedClass == Object.class));
        assertNotNull(profile.toString());
    }

    private static Class<?> expectedClass(Object value) {
        return value == null ? Object.class : value.getClass();
    }

}
