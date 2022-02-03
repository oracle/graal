/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.ReflectionUtils.invoke;
import static com.oracle.truffle.api.test.ReflectionUtils.invokeStatic;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@RunWith(Theories.class)
public class EqualityValueProfileTest {

    @SuppressWarnings("deprecation")
    private static Integer newInteger(int value) {
        return new Integer(value);
    }

    @DataPoint public static final String O1 = new String();
    @DataPoint public static final String O2 = O1;
    @DataPoint public static final Object O3 = new Object();
    @DataPoint public static final Integer O4 = newInteger(1);
    @DataPoint public static final Integer O5 = newInteger(1);
    @DataPoint public static final Integer O6 = null;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private ValueProfile profile;

    @Before
    public void create() {
        profile = (ValueProfile) invokeStatic(loadRelative(EqualityValueProfileTest.class, "ValueProfile$Equality"), "create");
    }

    private static boolean isGeneric(ValueProfile profile) {
        return (boolean) invoke(profile, "isGeneric");
    }

    private static boolean isUninitialized(ValueProfile profile) {
        return (boolean) invoke(profile, "isUninitialized");
    }

    private static Object getCachedValue(ValueProfile profile) {
        return invoke(profile, "getCachedValue");
    }

    @Test
    public void testInitial() throws Exception {
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOne(Object value) throws Exception {
        Object result = profile.profile(value);
        assertThat(result, is(equalTo(value)));
        if (value == null) {
            assertThat(isUninitialized(profile), is(false));
            assertThat(isGeneric(profile), is(true));
        } else {
            assertEquals(getCachedValue(profile), value);
            assertThat(isUninitialized(profile), is(false));
            assertThat(isGeneric(profile), is(false));
        }
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwo(Object value0, Object value1) throws Exception {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertThat(result0, is(equalTo(value0)));
        assertThat(result1, is(equalTo(value1)));

        if (value0 != null && value0.equals(value1)) {
            assertThat(getCachedValue(profile), is(equalTo(value0)));
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThree(Object value0, Object value1, Object value2) throws Exception {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);
        Object result2 = profile.profile(value2);

        assertThat(result0, is(equalTo(value0)));
        assertThat(result1, is(equalTo(value1)));
        assertThat(result2, is(equalTo(value2)));

        if (value0 != null && value0.equals(value1) && value1 != null && value1.equals(value2)) {
            assertThat(getCachedValue(profile), is(equalTo(value0)));
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

}
