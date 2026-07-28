/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject.test;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.api.test.SubprocessTestUtils;

public class BuilderSafetyChecksTest extends StaticObjectModelTest {

    record TestShape(DefaultStaticObjectFactory objectFactory, StaticProperty intProperty) {

        static TestShape create(TestEnvironment te, boolean safetyChecks) {
            StaticProperty property = new DefaultStaticProperty("property");
            StaticShape<DefaultStaticObjectFactory> shape = StaticShape.newBuilder(te.testLanguage).property(property, int.class, false).safetyChecks(safetyChecks).build();
            return new TestShape(shape.getFactory(), property);
        }
    }

    @Test
    public void builderSafetyChecks() throws IOException, InterruptedException {
        runWithoutStaticObjectAssertions(() -> {
            builderCanEnableSafetyChecksWithRelaxedEngine();
            builderCanDisableSafetyChecksWithDefaultEngine();
            forceSafetyChecksOverridesBuilderConfiguration();
        });
    }

    private static void builderCanEnableSafetyChecksWithRelaxedEngine() {
        for (boolean arrayBased : new boolean[]{true, false}) {
            try (TestEnvironment te = new TestEnvironment(arrayBased, true, false)) {
                if (te.supportsSafetyChecks()) {
                    TestShape shapeA = TestShape.create(te, true);
                    TestShape shapeB = TestShape.create(te, true);
                    Object objectB = shapeB.objectFactory().create();

                    assertWrongShapeAccessFails(shapeA.intProperty(), objectB);
                }
            }
        }
    }

    private static void builderCanDisableSafetyChecksWithDefaultEngine() {
        for (boolean arrayBased : new boolean[]{true, false}) {
            try (TestEnvironment te = new TestEnvironment(arrayBased, false, false)) {
                TestShape shapeA = TestShape.create(te, false);
                TestShape shapeB = TestShape.create(te, false);
                Object objectB = shapeB.objectFactory().create();

                // The shapes differ, but their identical layouts make this unchecked access safe.
                shapeA.intProperty().setInt(objectB, 42);
                Assert.assertEquals(42, shapeA.intProperty().getInt(objectB));
            }
        }
    }

    private static void forceSafetyChecksOverridesBuilderConfiguration() {
        for (boolean arrayBased : new boolean[]{true, false}) {
            try (TestEnvironment te = new TestEnvironment(arrayBased, false, true)) {
                if (te.supportsSafetyChecks()) {
                    TestShape shapeA = TestShape.create(te, false);
                    TestShape shapeB = TestShape.create(te, false);
                    Object objectB = shapeB.objectFactory().create();

                    assertWrongShapeAccessFails(shapeA.intProperty(), objectB);
                }
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void forceAndRelaxSafetyChecksAreMutuallyExclusive() {
        try (TestEnvironment te = new TestEnvironment(true, true, true)) {
            Assert.fail();
        } catch (IllegalStateException e) {
            Assert.assertEquals("Option engine.RelaxStaticObjectSafetyChecks can not be true at the same time as engine.ForceStaticObjectSafetyChecks.", e.getMessage());
        }
    }

    private static void runWithoutStaticObjectAssertions(Runnable test) throws IOException, InterruptedException {
        if (!StaticShape.class.desiredAssertionStatus()) {
            test.run();
            return;
        }
        Assume.assumeFalse("Cannot change assertion status in a native image", TruffleOptions.AOT);
        SubprocessTestUtils.newBuilder(BuilderSafetyChecksTest.class, test).postfixVmOption("-da:com.oracle.truffle.api.staticobject...").run();
    }

    private static void assertWrongShapeAccessFails(StaticProperty property, Object incompatibleObject) {
        try {
            property.setInt(incompatibleObject, 42);
            Assert.fail("Expected an incompatible shape access to fail");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }
}
