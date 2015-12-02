/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.utilities;

import com.oracle.truffle.api.utilities.ValueProfile;
import java.lang.reflect.Method;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ExactClassValueProfileTest {

    @DataPoint public static final String O1 = new String();
    @DataPoint public static final String O2 = new String();
    @DataPoint public static final Object O3 = new Object();
    @DataPoint public static final Integer O4 = new Integer(1);
    @DataPoint public static final Integer O5 = null;

    private ValueProfile profile;

    @Before
    public void create() {
        profile = ValueProfile.createClassProfile();
    }

    @Test
    public void testInitial() throws Exception {
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        assertNull(getCachedClass(profile));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOne(Object value) throws Exception {
        Object result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedClass(profile), expectedClass(value));
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwo(Object value0, Object value1) throws Exception {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));

        Object expectedClass = expectedClass(value0) == expectedClass(value1) ? expectedClass(value0) : Object.class;

        assertEquals(getCachedClass(profile), expectedClass);
        assertThat(isUninitialized(profile), is(false));
        assertThat(isGeneric(profile), is(expectedClass == Object.class));
        profile.toString(); // test that it is not crashing
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

        assertEquals(getCachedClass(profile), expectedClass);
        assertThat(isUninitialized(profile), is(false));
        assertThat(isGeneric(profile), is(expectedClass == Object.class));
        profile.toString(); // test that it is not crashing
    }

    private static Class<?> expectedClass(Object value) {
        return value == null ? Object.class : value.getClass();
    }

    private static Object get(String name, ValueProfile profile) throws Exception {
        final Method m = profile.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(profile);
    }

    private static Object getCachedClass(ValueProfile profile) throws Exception {
        return get("getCachedClass", profile);
    }

    private static boolean isUninitialized(ValueProfile profile) throws Exception {
        return (Boolean) get("isUninitialized", profile);
    }

    private static boolean isGeneric(ValueProfile profile) throws Exception {
        return (Boolean) get("isGeneric", profile);
    }
}
