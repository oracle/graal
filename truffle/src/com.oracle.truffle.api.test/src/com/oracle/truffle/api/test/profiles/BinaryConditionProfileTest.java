/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.dsl.InlineSupport.InlinableField;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class BinaryConditionProfileTest extends AbstractProfileTest {

    private static boolean[] VALUES = new boolean[]{true, false};

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Override
    protected InlinableField[] getInlinedFields() {
        return createInlinedFields(1, 0, 0, 0, 0);
    }

    private boolean wasTrue(Object profile) {
        return (boolean) invokeProfileMethod(profile, "wasTrue");
    }

    private boolean wasFalse(Object profile) {
        return (boolean) invokeProfileMethod(profile, "wasFalse");
    }

    @Test
    public void testNotCrashing() {
        ConditionProfile profile = createEnabled(ConditionProfile.class);
        profile.disable();
        profile.reset();
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitial() {
        ConditionProfile profile = createEnabled(ConditionProfile.class);
        assertThat(wasTrue(profile), is(false));
        assertThat(wasFalse(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testProfileOne() {
        for (boolean value : VALUES) {
            ConditionProfile profile = createEnabled(ConditionProfile.class);
            boolean result = profile.profile(value);

            assertThat(result, is(value));
            assertThat(wasTrue(profile), is(value));
            assertThat(wasFalse(profile), is(!value));
            profile.toString();
        }
    }

    @Test
    public void testProfileTwo() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                ConditionProfile profile = createEnabled(ConditionProfile.class);

                boolean result0 = profile.profile(value0);
                boolean result1 = profile.profile(value1);

                assertThat(result0, is(value0));
                assertThat(result1, is(value1));
                assertThat(wasTrue(profile), is(value0 || value1));
                assertThat(wasFalse(profile), is(!value0 || !value1));
                profile.toString();
            }
        }
    }

    @Test
    public void testProfileThree() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                for (boolean value2 : VALUES) {
                    ConditionProfile profile = createEnabled(ConditionProfile.class);

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
        }

    }

    @Test
    public void testNotCrashingInlined() {
        InlinedConditionProfile profile = createEnabled(InlinedConditionProfile.class);
        profile.disable(state);
        profile.reset(state);
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitialInlined() {
        InlinedConditionProfile profile = createEnabled(InlinedConditionProfile.class);
        assertThat(wasTrue(profile), is(false));
        assertThat(wasFalse(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testProfileOneInlined() {
        for (boolean value : VALUES) {
            InlinedConditionProfile profile = createEnabled(InlinedConditionProfile.class);
            boolean result = profile.profile(state, value);

            assertThat(result, is(value));
            assertThat(wasTrue(profile), is(value));
            assertThat(wasFalse(profile), is(!value));
            profile.toString();
        }
    }

    @Test
    public void testProfileTwoInlined() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                InlinedConditionProfile profile = createEnabled(InlinedConditionProfile.class);

                boolean result0 = profile.profile(state, value0);
                boolean result1 = profile.profile(state, value1);

                assertThat(result0, is(value0));
                assertThat(result1, is(value1));
                assertThat(wasTrue(profile), is(value0 || value1));
                assertThat(wasFalse(profile), is(!value0 || !value1));
                profile.toString();
            }
        }
    }

    @Test
    public void testProfileThreeInlined() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                for (boolean value2 : VALUES) {
                    InlinedConditionProfile profile = createEnabled(InlinedConditionProfile.class);

                    boolean result0 = profile.profile(state, value0);
                    boolean result1 = profile.profile(state, value1);
                    boolean result2 = profile.profile(state, value2);

                    assertThat(result0, is(value0));
                    assertThat(result1, is(value1));
                    assertThat(result2, is(value2));
                    assertThat(wasTrue(profile), is(value0 || value1 || value2));
                    assertThat(wasFalse(profile), is(!value0 || !value1 || !value2));
                    profile.toString();
                }
            }
        }

    }

    @Test
    public void testDisabled() {
        ConditionProfile p = ConditionProfile.getUncached();
        for (boolean value : VALUES) {
            assertThat(p.profile(value), is(value));
        }
        p.toString(); // test that it is not crashing
    }

    @Test
    public void testDisabledInlined() {
        InlinedConditionProfile p = InlinedConditionProfile.getUncached();
        for (boolean value : VALUES) {
            assertThat(p.profile(state, value), is(value));
        }
        p.toString(); // test that it is not crashing
    }

}
