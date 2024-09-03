/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.dsl.InlineSupport.InlinableField;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class LoopConditionProfileTest extends AbstractProfileTest {

    private static boolean[] VALUES = new boolean[]{true, false};

    private static long[] LENGTHS = new long[]{Long.MAX_VALUE, 0L, Long.MAX_VALUE / 2L, Long.MAX_VALUE / 2 + 1L, 1L};

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Override
    protected InlinableField[] getInlinedFields() {
        List<InlinableField> fields = new ArrayList<>();
        addInlinedFields(fields, 0, 0, 0, 1, 0);
        addInlinedFields(fields, 0, 0, 1, 0, 0);
        return fields.toArray(new InlinableField[fields.size()]);
    }

    private long getTrueCount(Object profile) {
        return (long) invokeProfileMethod(profile, "getTrueCount");
    }

    private int getFalseCount(Object profile) {
        return (int) invokeProfileMethod(profile, "getFalseCount");
    }

    @Test
    public void testNotCrashing() {
        LoopConditionProfile profile = createEnabled(LoopConditionProfile.class);
        profile.disable();
        profile.reset();
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitial() {
        LoopConditionProfile profile = createEnabled(LoopConditionProfile.class);
        assertThat(getTrueCount(profile), is(0L));
        assertThat(getFalseCount(profile), is(0));
        profile.toString();
    }

    @Test
    public void testProfileOne() {
        for (boolean value : VALUES) {
            LoopConditionProfile profile = createEnabled(LoopConditionProfile.class);
            boolean result = profile.profile(value);

            assertThat(result, is(value));
            assertThat(getTrueCount(profile), is(value ? 1L : 0L));
            assertThat(getFalseCount(profile), is(!value ? 1 : 0));
            profile.toString();
        }
    }

    @Test
    public void testProfileTwo() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                LoopConditionProfile profile = createEnabled(LoopConditionProfile.class);
                boolean result0 = profile.profile(value0);
                boolean result1 = profile.profile(value1);

                assertThat(result0, is(value0));
                assertThat(result1, is(value1));
                assertThat(getTrueCount(profile), is((value0 ? 1L : 0L) + (value1 ? 1L : 0L)));
                assertThat(getFalseCount(profile), is((!value0 ? 1 : 0) + (!value1 ? 1 : 0)));
                profile.toString();
            }
        }
    }

    @Test
    public void testProfileThree() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                for (boolean value2 : VALUES) {
                    LoopConditionProfile profile = createEnabled(LoopConditionProfile.class);
                    boolean result0 = profile.profile(value0);
                    boolean result1 = profile.profile(value1);
                    boolean result2 = profile.profile(value2);

                    assertThat(result0, is(value0));
                    assertThat(result1, is(value1));
                    assertThat(result2, is(value2));
                    assertThat(getTrueCount(profile), is((value0 ? 1L : 0L) + (value1 ? 1L : 0L) + (value2 ? 1L : 0L)));
                    assertThat(getFalseCount(profile), is((!value0 ? 1 : 0) + (!value1 ? 1 : 0) + (!value2 ? 1 : 0)));
                    profile.toString();
                }
            }
        }
    }

    @Test
    public void testNotCrashingInlined() {
        InlinedLoopConditionProfile profile = createEnabled(InlinedLoopConditionProfile.class);
        profile.disable(state);
        profile.reset(state);
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitialInlined() {
        InlinedLoopConditionProfile profile = createEnabled(InlinedLoopConditionProfile.class);
        assertThat(getTrueCount(profile), is(0L));
        assertThat(getFalseCount(profile), is(0));
        assertEquals(getTrueCount(profile) > 0, profile.wasTrue(state));
        assertEquals(getFalseCount(profile) > 0, profile.wasFalse(state));
        profile.toString();
        profile.toString(state);
    }

    @Test
    public void testProfileOneInlined() {
        for (boolean value : VALUES) {
            InlinedLoopConditionProfile profile = createEnabled(InlinedLoopConditionProfile.class);
            boolean result = profile.profile(state, value);

            assertThat(result, is(value));
            assertThat(getTrueCount(profile), is(value ? 1L : 0L));
            assertThat(getFalseCount(profile), is(!value ? 1 : 0));
            assertEquals(getTrueCount(profile) > 0, profile.wasTrue(state));
            assertEquals(getFalseCount(profile) > 0, profile.wasFalse(state));
            profile.toString();
            profile.toString(state);
        }
    }

    @Test
    public void testProfileTwoInlined() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                InlinedLoopConditionProfile profile = createEnabled(InlinedLoopConditionProfile.class);
                boolean result0 = profile.profile(state, value0);
                boolean result1 = profile.profile(state, value1);

                assertThat(result0, is(value0));
                assertThat(result1, is(value1));
                assertThat(getTrueCount(profile), is((value0 ? 1L : 0L) + (value1 ? 1L : 0L)));
                assertThat(getFalseCount(profile), is((!value0 ? 1 : 0) + (!value1 ? 1 : 0)));
                assertEquals(getTrueCount(profile) > 0, profile.wasTrue(state));
                assertEquals(getFalseCount(profile) > 0, profile.wasFalse(state));

                profile.toString();
                profile.toString(state);
            }
        }
    }

    @Test
    public void testProfileThreeInlined() {
        for (boolean value0 : VALUES) {
            for (boolean value1 : VALUES) {
                for (boolean value2 : VALUES) {
                    InlinedLoopConditionProfile profile = createEnabled(InlinedLoopConditionProfile.class);
                    boolean result0 = profile.profile(state, value0);
                    boolean result1 = profile.profile(state, value1);
                    boolean result2 = profile.profile(state, value2);

                    assertThat(result0, is(value0));
                    assertThat(result1, is(value1));
                    assertThat(result2, is(value2));
                    assertThat(getTrueCount(profile), is((value0 ? 1L : 0L) + (value1 ? 1L : 0L) + (value2 ? 1L : 0L)));
                    assertThat(getFalseCount(profile), is((!value0 ? 1 : 0) + (!value1 ? 1 : 0) + (!value2 ? 1 : 0)));
                    assertEquals(getTrueCount(profile) > 0, profile.wasTrue(state));
                    assertEquals(getFalseCount(profile) > 0, profile.wasFalse(state));
                    profile.toString();
                    profile.toString(state);
                }
            }
        }
    }

    @Test
    public void testProfilingLength() {
        for (long length1 : LENGTHS) {
            for (long length2 : LENGTHS) {
                for (long length3 : LENGTHS) {
                    LoopConditionProfile profile = createEnabled(LoopConditionProfile.class);

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
        }
    }

    @Test
    public void testProfilingLengthInlined() {
        for (long length1 : LENGTHS) {
            for (long length2 : LENGTHS) {
                for (long length3 : LENGTHS) {
                    InlinedLoopConditionProfile profile = createEnabled(InlinedLoopConditionProfile.class);

                    assertThat(profile.inject(state, false), is(false));

                    profile.toString(); // not crashing
                    profile.profileCounted(state, length1);
                    profile.toString(); // not crashing
                    long expectedLength = length1;
                    assertThat(getTrueCount(profile), is(expectedLength));
                    assertThat(getFalseCount(profile), is(1));
                    assertThat(profile.inject(state, false), is(false));

                    profile.profileCounted(state, length2);
                    profile.toString(); // not crashing
                    expectedLength += length2;
                    if (expectedLength < 0) {
                        assertThat(getTrueCount(profile), is(length1));
                        assertThat(getFalseCount(profile), is(1));
                        assertThat(profile.inject(state, false), is(false));

                        profile.profileCounted(state, length3);
                        expectedLength = length1 + length3;

                        if (expectedLength < 0) {
                            assertThat(getTrueCount(profile), is(length1));
                            assertThat(getFalseCount(profile), is(1));
                            assertThat(profile.inject(state, false), is(false));
                        } else {
                            assertThat(getTrueCount(profile), is(expectedLength));
                            assertThat(getFalseCount(profile), is(2));
                            assertThat(profile.inject(state, false), is(false));
                        }
                        return;
                    } else {
                        assertThat(getTrueCount(profile), is(expectedLength));
                        assertThat(getFalseCount(profile), is(2));
                        assertThat(profile.inject(state, false), is(false));
                    }

                    profile.profileCounted(state, length3);
                    profile.toString(); // not crashing
                    expectedLength += length3;

                    if (expectedLength < 0) {
                        assertThat(getTrueCount(profile), is(length1 + length2));
                        assertThat(getFalseCount(profile), is(2));
                        assertThat(profile.inject(state, false), is(false));
                    } else {
                        assertThat(getTrueCount(profile), is(expectedLength));
                        assertThat(getFalseCount(profile), is(3));
                        assertThat(profile.inject(state, false), is(false));
                    }
                    profile.toString();
                    profile.toString(state);

                    assertEquals(getTrueCount(profile) > 0, profile.wasTrue(state));
                    assertEquals(getFalseCount(profile) > 0, profile.wasFalse(state));
                }
            }
        }
    }

    @Test
    public void testDisabled() {
        LoopConditionProfile p = LoopConditionProfile.getUncached();
        for (boolean value : VALUES) {
            assertThat(p.profile(value), is(value));
        }
        p.toString(); // test that it is not crashing
    }

    @Test
    public void testDisabledInlined() {
        InlinedLoopConditionProfile p = InlinedLoopConditionProfile.getUncached();
        for (boolean value : VALUES) {
            assertThat(p.profile(state, value), is(value));
        }
        p.toString(state); // test that it is not crashing
        assertTrue(p.wasTrue(state));
        assertTrue(p.wasFalse(state));
    }

}
