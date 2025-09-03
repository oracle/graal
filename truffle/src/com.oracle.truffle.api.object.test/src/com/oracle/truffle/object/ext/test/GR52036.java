/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.ext.test;

import static com.oracle.truffle.object.basic.test.DOTestAsserts.getTypeAssumption;
import static com.oracle.truffle.object.basic.test.DOTestAsserts.getTypeAssumptionRecord;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

public class GR52036 {

    private static final DynamicObjectLibrary OBJLIB = DynamicObjectLibrary.getUncached();

    /**
     * Regression test for a case of two locations sharing the same type assumption, where one
     * location invalidates the type assumption and the other location is assigned a value for which
     * the old type assumption would still hold, in which case the assumption needs to be renewed
     * and made valid again. Otherwise, the invalid assumption might cause a deopt loop.
     */
    @SuppressWarnings("try")
    @Test
    public void testGR52036Reproducer() throws Throwable {

        class ObjType1 extends DynamicObject {
            protected ObjType1(Shape shape) {
                super(shape);
            }
        }

        class ObjType2 extends DynamicObject {
            protected ObjType2(Shape shape) {
                super(shape);
            }
        }

        Shape initialShape = Shape.newBuilder().build();
        try (Engine engine = Engine.create(); Context context = Context.newBuilder().engine(engine).build()) {
            var o1 = new ObjType1(initialShape);
            var o2 = new ObjType1(initialShape);

            OBJLIB.put(o1, "a", new ObjType1(initialShape));
            OBJLIB.put(o1, "b", new ObjType1(initialShape));
            OBJLIB.put(o1, "c", new ObjType1(initialShape));

            OBJLIB.put(o2, "a", new ObjType1(initialShape));
            OBJLIB.put(o2, "b", new ObjType1(initialShape));
            OBJLIB.put(o2, "c", new ObjType1(initialShape));

            assertSame("Objects must have the same shape", o1.getShape(), o2.getShape());

            // Remove property "b", shifting the location of "c".
            OBJLIB.removeKey(o1, "b");

            var location1 = o1.getShape().getProperty("c").getLocation();
            var location2 = o2.getShape().getProperty("c").getLocation();
            assumeTrue("Same type assumption expected for this test!", getTypeAssumptionRecord(location1) == getTypeAssumptionRecord(location2));

            var commonTypeAssumptionRecord = getTypeAssumptionRecord(location1);
            var commonTypeAssumption = getTypeAssumption(location1);
            assertTrue(commonTypeAssumption.isValid());

            // Change the type of "c", invalidating the type assumption.
            OBJLIB.put(o1, "c", new ObjType2(initialShape));
            assertFalse("Invalidated type assumption", commonTypeAssumption.isValid());
            assertTrue("New type assumption should be valid", getTypeAssumption(location1).isValid());
            assertNotSame("Type assumption of location1 has been replaced", getTypeAssumptionRecord(location1), commonTypeAssumptionRecord);

            assertFalse("Type assumption of location2 is still invalid", getTypeAssumption(location2).isValid());
            assertSame("Type assumption of location2 has not been replaced", getTypeAssumptionRecord(location2), commonTypeAssumptionRecord);

            // Assign "c" a value still compatible with the invalidated type assumption.
            OBJLIB.put(o2, "c", new ObjType1(initialShape));
            assertTrue("New type assumption of location2 should be valid", getTypeAssumption(location2).isValid());
        }
    }

}
