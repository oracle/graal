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
package com.oracle.truffle.api.test.utilities;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.*;
import org.junit.experimental.theories.*;
import org.junit.runner.*;

import com.oracle.truffle.api.utilities.*;

@RunWith(Theories.class)
public class ExactClassValueProfileTest {

    @DataPoint public static final String O1 = new String();
    @DataPoint public static final String O2 = new String();
    @DataPoint public static final Object O3 = new Object();
    @DataPoint public static final Integer O4 = new Integer(1);
    @DataPoint public static final Integer O5 = null;

    private ExactClassValueProfile profile;

    @Before
    public void create() {
        profile = (ExactClassValueProfile) ValueProfile.createClassProfile();
    }

    @Test
    public void testInitial() {
        assertThat(profile.isGeneric(), is(false));
        assertThat(profile.isUninitialized(), is(true));
        assertNull(profile.getCachedClass());
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOne(Object value) {
        Object result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedClass(), expectedClass(value));
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwo(Object value0, Object value1) {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));

        Object expectedClass = expectedClass(value0) == expectedClass(value1) ? expectedClass(value0) : Object.class;

        assertEquals(profile.getCachedClass(), expectedClass);
        assertThat(profile.isUninitialized(), is(false));
        assertThat(profile.isGeneric(), is(expectedClass == Object.class));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThree(Object value0, Object value1, Object value2) {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);
        Object result2 = profile.profile(value2);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(result2, is(value2));

        Object expectedClass = expectedClass(value0) == expectedClass(value1) && expectedClass(value1) == expectedClass(value2) ? expectedClass(value0) : Object.class;

        assertEquals(profile.getCachedClass(), expectedClass);
        assertThat(profile.isUninitialized(), is(false));
        assertThat(profile.isGeneric(), is(expectedClass == Object.class));
        profile.toString(); // test that it is not crashing
    }

    private static Class<?> expectedClass(Object value) {
        return value == null ? Object.class : value.getClass();
    }

}
