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
public class BinaryConditionProfileTest {

    @DataPoints public static boolean[] data = new boolean[]{true, false};

    private BinaryConditionProfile profile;

    @Before
    public void create() {
        profile = (BinaryConditionProfile) ConditionProfile.createBinaryProfile();
    }

    @Test
    public void testInitial() {
        assertThat(profile.wasTrue(), is(false));
        assertThat(profile.wasFalse(), is(false));
    }

    @Theory
    public void testProfileOne(boolean value) {
        boolean result = profile.profile(value);

        assertThat(result, is(value));
        assertThat(profile.wasTrue(), is(value));
        assertThat(profile.wasFalse(), is(!value));
    }

    @Theory
    public void testProfileTwo(boolean value0, boolean value1) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(profile.wasTrue(), is(value0 || value1));
        assertThat(profile.wasFalse(), is(!value0 || !value1));
    }

    @Theory
    public void testProfileThree(boolean value0, boolean value1, boolean value2) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);
        boolean result2 = profile.profile(value2);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(result2, is(value2));
        assertThat(profile.wasTrue(), is(value0 || value1 || value2));
        assertThat(profile.wasFalse(), is(!value0 || !value1 || !value2));
    }

}
