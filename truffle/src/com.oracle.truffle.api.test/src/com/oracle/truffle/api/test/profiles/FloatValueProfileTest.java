/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.profiles.PrimitiveValueProfileTest.exactCompare;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.dsl.InlineSupport.InlinableField;
import com.oracle.truffle.api.profiles.FloatValueProfile;
import com.oracle.truffle.api.profiles.InlinedFloatValueProfile;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class FloatValueProfileTest extends AbstractProfileTest {

    private static float[] VALUES = new float[]{Float.MIN_VALUE, -0.0f, +0.0f, 14.5f, Float.MAX_VALUE};

    private static final float FLOAT_DELTA = 0.00001f;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Override
    protected InlinableField[] getInlinedFields() {
        return createInlinedFields(1, 0, 1, 0, 0);
    }

    @Test
    public void testNotCrashing() {
        FloatValueProfile profile = createEnabled(FloatValueProfile.class);
        profile.disable();
        profile.reset();
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitial() {
        FloatValueProfile profile = createEnabled(FloatValueProfile.class);
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testProfileOneFloat() {
        for (float value : VALUES) {
            FloatValueProfile profile = createEnabled(FloatValueProfile.class);
            float result = profile.profile(value);

            assertThat(result, is(value));
            assertEquals((float) getCachedValue(profile), value, FLOAT_DELTA);
            assertThat(isUninitialized(profile), is(false));
            profile.toString(); // test that it is not crashing
        }
    }

    @Test
    public void testProfileTwoFloat() {
        for (float value0 : VALUES) {
            for (float value1 : VALUES) {
                FloatValueProfile profile = createEnabled(FloatValueProfile.class);
                float result0 = profile.profile(value0);
                float result1 = profile.profile(value1);

                assertEquals(result0, value0, FLOAT_DELTA);
                assertEquals(result1, value1, FLOAT_DELTA);

                if (exactCompare(value0, value1)) {
                    assertEquals((float) getCachedValue(profile), value0, FLOAT_DELTA);
                    assertThat(isGeneric(profile), is(false));
                } else {
                    assertThat(isGeneric(profile), is(true));
                }
                assertThat(isUninitialized(profile), is(false));
                profile.toString(); // test that it is not crashing
            }
        }
    }

    @Test
    public void testProfileThreeFloat() {
        for (float value0 : VALUES) {
            for (float value1 : VALUES) {
                for (float value2 : VALUES) {
                    FloatValueProfile profile = createEnabled(FloatValueProfile.class);
                    float result0 = profile.profile(value0);
                    float result1 = profile.profile(value1);
                    float result2 = profile.profile(value2);

                    assertEquals(result0, value0, FLOAT_DELTA);
                    assertEquals(result1, value1, FLOAT_DELTA);
                    assertEquals(result2, value2, FLOAT_DELTA);

                    if (exactCompare(value0, value1) && exactCompare(value1, value2)) {
                        assertEquals((float) getCachedValue(profile), value0, FLOAT_DELTA);
                        assertThat(isGeneric(profile), is(false));
                    } else {
                        assertThat(isGeneric(profile), is(true));
                    }
                    assertThat(isUninitialized(profile), is(false));
                    profile.toString(); // test that it is not crashing
                }
            }
        }
    }

    @Test
    public void testNotCrashingInlined() {
        InlinedFloatValueProfile profile = createEnabled(InlinedFloatValueProfile.class);
        profile.disable(state);
        profile.reset(state);
        assertEquals(profile, profile);
        assertEquals(profile.hashCode(), profile.hashCode());
        assertNotNull(profile.toString());
    }

    @Test
    public void testInitialInlined() {
        InlinedFloatValueProfile profile = createEnabled(InlinedFloatValueProfile.class);
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Test
    public void testProfileOneFloatInlined() {
        for (float value : VALUES) {
            InlinedFloatValueProfile profile = createEnabled(InlinedFloatValueProfile.class);
            float result = profile.profile(state, value);

            assertThat(result, is(value));
            assertEquals((float) getCachedValue(profile), value, FLOAT_DELTA);
            assertThat(isUninitialized(profile), is(false));
            profile.toString(); // test that it is not crashing
        }
    }

    @Test
    public void testProfileTwoFloatInlined() {
        for (float value0 : VALUES) {
            for (float value1 : VALUES) {
                InlinedFloatValueProfile profile = createEnabled(InlinedFloatValueProfile.class);
                float result0 = profile.profile(state, value0);
                float result1 = profile.profile(state, value1);

                assertEquals(result0, value0, FLOAT_DELTA);
                assertEquals(result1, value1, FLOAT_DELTA);

                if (exactCompare(value0, value1)) {
                    assertEquals((float) getCachedValue(profile), value0, FLOAT_DELTA);
                    assertThat(isGeneric(profile), is(false));
                } else {
                    assertThat(isGeneric(profile), is(true));
                }
                assertThat(isUninitialized(profile), is(false));
                profile.toString(); // test that it is not crashing
            }
        }
    }

    @Test
    public void testProfileThreeFloatInlined() {
        for (float value0 : VALUES) {
            for (float value1 : VALUES) {
                for (float value2 : VALUES) {
                    InlinedFloatValueProfile profile = createEnabled(InlinedFloatValueProfile.class);
                    float result0 = profile.profile(state, value0);
                    float result1 = profile.profile(state, value1);
                    float result2 = profile.profile(state, value2);

                    assertEquals(result0, value0, FLOAT_DELTA);
                    assertEquals(result1, value1, FLOAT_DELTA);
                    assertEquals(result2, value2, FLOAT_DELTA);

                    if (exactCompare(value0, value1) && exactCompare(value1, value2)) {
                        assertEquals((float) getCachedValue(profile), value0, FLOAT_DELTA);
                        assertThat(isGeneric(profile), is(false));
                    } else {
                        assertThat(isGeneric(profile), is(true));
                    }
                    assertThat(isUninitialized(profile), is(false));
                    profile.toString(); // test that it is not crashing
                }
            }
        }
    }

    @Test
    public void testDisabled() {
        FloatValueProfile p = FloatValueProfile.getUncached();
        for (float value : VALUES) {
            assertThat(p.profile(value), is(value));
        }
        p.toString(); // test that it is not crashing
    }

    @Test
    public void testDisabledInlined() {
        InlinedFloatValueProfile p = InlinedFloatValueProfile.getUncached();
        for (float value : VALUES) {
            assertThat(p.profile(state, value), is(value));
        }
        p.toString(); // test that it is not crashing
    }

}
