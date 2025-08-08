/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.object.basic.test.DOTestAsserts.assertObjectLocation;
import static com.oracle.truffle.object.basic.test.DOTestAsserts.assertPrimitiveLocation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.object.ext.test.ObjectModelRegressionTest.TestDynamicObject;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class PolymorphicPrimitivesTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.values());
    }

    private static Shape newEmptyShape() {
        return Shape.newBuilder().layout(TestDynamicObject.class, MethodHandles.lookup()).build();
    }

    private static Shape newEmptyShapeWithImplicitCastIntToLong() {
        return Shape.newBuilder().layout(TestDynamicObject.class, MethodHandles.lookup()).allowImplicitCastIntToLong(true).build();
    }

    private static DynamicObject newInstance(Shape emptyShape) {
        return new TestDynamicObject(emptyShape);
    }

    @Test
    public void testIntLongBoxed() {
        Shape emptyShape = newEmptyShape();
        DynamicObject object1 = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object1);

        library.put(object1, "x", 42);
        library.put(object1, "x", 42L);
        assertObjectLocation(object1.getShape().getProperty("x").getLocation());

        DynamicObject object2 = newInstance(emptyShape);
        library.put(object2, "x", 42);
        assertObjectLocation(object2.getShape().getProperty("x").getLocation());
        assertSame(object1.getShape(), object2.getShape());
    }

    @Test
    public void testImplicitCastIntToLong() {
        Shape emptyShape = newEmptyShapeWithImplicitCastIntToLong();
        DynamicObject object1 = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object1);

        library.put(object1, "x", 42);
        library.put(object1, "x", 42L);
        assertPrimitiveLocation(long.class, object1.getShape().getProperty("x").getLocation());
        library.putIfPresent(object1, "x", 42);
        assertEquals(42L, library.getOrDefault(object1, "x", null));

        DynamicObject object2 = newInstance(emptyShape);
        library.put(object2, "x", 42);
        assertPrimitiveLocation(long.class, object2.getShape().getProperty("x").getLocation());
        assertSame(object1.getShape(), object2.getShape());
    }

    @Test
    public void testIntLongPolymorphic1() {
        Shape emptyShape = newEmptyShape();
        DynamicObject object1 = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object1);

        library.put(object1, "x", 42);
        library.put(object1, "x", 42L);
        assertObjectLocation(object1.getShape().getProperty("x").getLocation());
        library.put(object1, "x", 42);
        assertEquals(42, library.getOrDefault(object1, "x", null));

        DynamicObject object2 = newInstance(emptyShape);
        library.put(object2, "x", 42);
        assertObjectLocation(object2.getShape().getProperty("x").getLocation());
        assertEquals(42, library.getOrDefault(object1, "x", null));
    }

    @Test
    public void testIntLongPolymorphic2() {
        Shape emptyShape = newEmptyShape();
        DynamicObject object1 = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object1);

        library.put(object1, "x", 42);
        library.put(object1, "y", Integer.MAX_VALUE);
        library.putIfPresent(object1, "x", 42L);
        assertObjectLocation(object1.getShape().getProperty("x").getLocation());
        assertPrimitiveLocation(int.class, object1.getShape().getProperty("y").getLocation());
        assertEquals(42L, library.getOrDefault(object1, "x", null));
        assertEquals(Integer.MAX_VALUE, library.getOrDefault(object1, "y", null));

        DynamicObject object2 = newInstance(emptyShape);
        library.put(object2, "x", 42);
        library.put(object2, "y", Integer.MAX_VALUE);
        assertEquals(object1.getShape().getProperty("y").getLocation(), object2.getShape().getProperty("y").getLocation());

        object2 = newInstance(emptyShape);
        library = createLibrary(DynamicObjectLibrary.class, object2);

        library.put(object2, "x", 42L);
        library.put(object2, "y", Integer.MAX_VALUE);
        assertPrimitiveLocation(long.class, object2.getShape().getProperty("x").getLocation());
        assertPrimitiveLocation(int.class, object2.getShape().getProperty("y").getLocation());
        object2.getShape().tryMerge(object1.getShape());
        assertTrue(library.updateShape(object2));
        assertSame(object1.getShape(), object2.getShape());
        assertSame(object1.getShape(), object2.getShape());
        assertEquals(object1.getShape().getProperty("y").getLocation(), object2.getShape().getProperty("y").getLocation());
    }

    @Test
    public void testIntLongPolymorphic3() {
        Shape emptyShape = newEmptyShape();
        DynamicObject o = newInstance(emptyShape);
        DynamicObjectLibrary lib = createLibrary(DynamicObjectLibrary.class, o);
        for (int i = -6; i < 0; i++) {
            lib.put(o, i, 0);
        }
        for (int i = 0; i < 2; i++) {
            lib.put(o, i, 0L);
        }
        for (int i = 2; i <= 10; i++) {
            lib.put(o, i, 0);
        }

        Object[] v;
        v = new Object[]{0L, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        verifySet(o, v, 0, 37128688L);
        verifySet(o, v, 1, 37128691L);
        verifySet(o, v, 2, 0);
        verifySet(o, v, 3, 0);
        verifySet(o, v, 4, 0);
        verifySet(o, v, 5, 0);
        verifySet(o, v, 6, 0);
        verifySet(o, v, 7, 0);
        verifySet(o, v, 8, 0);
        verifySet(o, v, 9, 0);
        verifySet(o, v, 10, 0);
        verifySet(o, v, 2, 1);
        verifySet(o, v, 3, 1);
        verifySet(o, v, 4, 1);
        verifySet(o, v, 5, 1);
        verifySet(o, v, 6, 37128790L);
        verifySet(o, v, 7, 37128814L);
        verifySet(o, v, 8, 37128838L);
        verifySet(o, v, 9, 37128862L);
        verifySet(o, v, 10, 17);
        verifySet(o, v, 2, 2);
        verifySet(o, v, 3, 2);
        verifySet(o, v, 4, 2);
        verifySet(o, v, 5, 2);
        verifySet(o, v, 6, 37128790L);
        verifySet(o, v, 7, 37128814L);
        verifySet(o, v, 8, 37128838L);
        verifySet(o, v, 9, 37128862L);
        verifySet(o, v, 10, 53);
        verifySet(o, v, 2, 3);
        verifySet(o, v, 3, 3);
        verifySet(o, v, 4, 3);
        verifySet(o, v, 5, 3);
        verifySet(o, v, 6, 37128790L);
        verifySet(o, v, 7, 37128814L);
        verifySet(o, v, 8, 37128838L);
        verifySet(o, v, 9, 37128862L);
        verifySet(o, v, 10, 61);
        verifySet(o, v, 2, 4);
        verifySet(o, v, 3, 4);
        verifySet(o, v, 4, 4);
        verifySet(o, v, 5, 4);
        verifySet(o, v, 6, 37128790L);
        verifySet(o, v, 7, 37128814L);
        verifySet(o, v, 8, 37128838L);
        verifySet(o, v, 9, 37128862L);
        verifySet(o, v, 10, 89);
        verifySet(o, v, 2, 5);
        verifySet(o, v, 3, 5);
        verifySet(o, v, 4, 5);
        verifySet(o, v, 5, 5);
        verifySet(o, v, 6, 37128790L);
        verifySet(o, v, 7, 37128814L);
        verifySet(o, v, 8, 37128838L);
        verifySet(o, v, 9, 37128862L);
        verifySet(o, v, 10, 113);
        verifySet(o, v, 2, 6);
        verifySet(o, v, 3, 6);
        verifySet(o, v, 4, 6);
        verifySet(o, v, 5, 6);
        verifySet(o, v, 6, 37128790L);
        verifySet(o, v, 7, 37128814L);
        verifySet(o, v, 8, 37128838L);
        verifySet(o, v, 9, 37128862L);
        verifySet(o, v, 10, 145);
        verifySet(o, v, 2, 7);
        verifySet(o, v, 3, 7);
        verifySet(o, v, 4, 7);
        verifySet(o, v, 5, 7);
        verifySet(o, v, 6, 37128790L);
        verifySet(o, v, 7, 37128814L);
        verifySet(o, v, 8, 37128838L);
        verifySet(o, v, 9, 37128862L);
        verifySet(o, v, 10, 183);

        o = newInstance(emptyShape);
        for (int i = -6; i < 0; i++) {
            lib.put(o, i, 0);
        }
        for (int i = 0; i < 2; i++) {
            lib.put(o, i, 0L);
        }
        for (int i = 2; i <= 10; i++) {
            lib.put(o, i, 0);
        }

        v = new Object[]{0L, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        verifySet(o, v, 0, 37128910L);
        verifySet(o, v, 1, 37128913L);
        verifySet(o, v, 2, 0);
        verifySet(o, v, 3, 0);
        verifySet(o, v, 4, 0);
        verifySet(o, v, 5, 0);
        verifySet(o, v, 6, 0);
        verifySet(o, v, 7, 0);
        verifySet(o, v, 8, 0);
        verifySet(o, v, 9, 0);
        verifySet(o, v, 10, 0);
        verifySet(o, v, 2, 1);
        verifySet(o, v, 3, 1);
        verifySet(o, v, 4, 1);
        verifySet(o, v, 5, 1);
        verifySet(o, v, 6, 37129012L);
        verifySet(o, v, 7, 37129036L);
        verifySet(o, v, 8, 37129060L);
        verifySet(o, v, 9, 37129084L);
        verifySet(o, v, 10, 45);
        verifySet(o, v, 2, 2);
        verifySet(o, v, 3, 2);
        verifySet(o, v, 4, 2);
        verifySet(o, v, 5, 2);
        verifySet(o, v, 6, 37129012L);
        verifySet(o, v, 7, 37129036L);
        verifySet(o, v, 8, 37129060L);
    }

    private void verifySet(DynamicObject o, Object[] v, int i, Object value) {
        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, o);

        for (int j = 0; j < v.length; j++) {
            assertEquals(v[j], library.getOrDefault(o, j, null));
        }
        v[i] = value;
        library.putIfPresent(o, i, value);
        for (int j = 0; j < v.length; j++) {
            assertEquals(v[j], library.getOrDefault(o, j, null));
        }
    }
}
