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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.profiles.ByteValueProfile;

@RunWith(Theories.class)
public class ByteValueProfileTest {

    @DataPoint public static final byte VALUE0 = Byte.MIN_VALUE;
    @DataPoint public static final byte VALUE1 = 0;
    @DataPoint public static final byte VALUE2 = 14;
    @DataPoint public static final byte VALUE3 = Byte.MAX_VALUE;

    private ByteValueProfile profile;

    @Before
    public void create() {
        profile = (ByteValueProfile) invokeStatic(loadRelative(ByteValueProfileTest.class, "ByteValueProfile$Enabled"), "create");
    }

    private static boolean isGeneric(ByteValueProfile profile) {
        return (boolean) invoke(profile, "isGeneric");
    }

    private static boolean isUninitialized(ByteValueProfile profile) {
        return (boolean) invoke(profile, "isUninitialized");
    }

    private static byte getCachedValue(ByteValueProfile profile) {
        return (byte) invoke(profile, "getCachedValue");
    }

    @Test
    public void testInitial() {
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneObject(byte value) {
        byte result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoObject(byte value0, byte value1) {
        byte result0 = profile.profile(value0);
        byte result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));

        if (value0 == value1) {
            assertThat(getCachedValue(profile), is(value0));
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeObject(byte value0, byte value1, byte value2) {
        byte result0 = profile.profile(value0);
        byte result1 = profile.profile(value1);
        byte result2 = profile.profile(value2);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(result2, is(value2));

        if (value0 == value1 && value1 == value2) {
            assertThat(getCachedValue(profile), is(value0));
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testDisabled() {
        ByteValueProfile p = (ByteValueProfile) getStaticField(loadRelative(ByteValueProfileTest.class, "ByteValueProfile$Disabled"), "INSTANCE");
        assertThat(p.profile(VALUE0), is(VALUE0));
        assertThat(p.profile(VALUE1), is(VALUE1));
        assertThat(p.profile(VALUE2), is(VALUE2));
        assertThat(p.profile(VALUE3), is(VALUE3));
        p.toString(); // test that it is not crashing
    }

}
