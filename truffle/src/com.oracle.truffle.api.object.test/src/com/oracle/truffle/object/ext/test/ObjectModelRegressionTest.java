/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.object.basic.test.DOTestAsserts.invokeMethod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class ObjectModelRegressionTest extends AbstractParametrizedLibraryTest {
    @Parameter(1) public Class<? extends DynamicObject> layoutClass;
    @Parameter(2) public boolean useLookup;

    @Parameters(name = "{0},{1},{2}")
    public static Collection<Object[]> parameters() {
        List<Class<? extends DynamicObject>> layoutClasses = List.of(
                        TestDynamicObject.class,
                        TestDynamicObjectWithFields.class);
        var booleans = List.of(Boolean.FALSE, Boolean.TRUE);
        // @formatter:off
        return Arrays.stream(TestRun.values())
                        .flatMap(run -> layoutClasses.stream()
                        .flatMap(layout -> booleans.stream()
                        .map(useLookup -> new Object[]{run, layout, useLookup})))
                        .toList();
        // @formatter:on
    }

    private Shape newEmptyShape() {
        if (layoutClass == TestDynamicObjectWithFields.class) {
            if (useLookup) {
                return Shape.newBuilder().layout(TestDynamicObjectWithFields.class, MethodHandles.lookup()).build();
            } else {
                return Shape.newBuilder().layout(TestDynamicObjectWithFields.class).build();
            }
        } else {
            return Shape.newBuilder().build();
        }
    }

    private static DynamicObject newInstance(Shape emptyShape) {
        if (emptyShape.getLayoutClass() == TestDynamicObjectWithFields.class) {
            return new TestDynamicObjectWithFields(emptyShape);
        }
        return new TestDynamicObject(emptyShape);
    }

    @Test
    public void testDefinePropertyWithFlagsChangeAndFinalInvalidation() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);
        DynamicObject b = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.put(a, "r", 1);
        library.put(a, "x", 2);
        library.put(b, "r", 3);
        library.put(b, "x", 4);
        assertPrimitiveLocation(int.class, a.getShape().getProperty("r").getLocation());
        assertPrimitiveLocation(int.class, a.getShape().getProperty("x").getLocation());
        assertPrimitiveLocation(int.class, b.getShape().getProperty("r").getLocation());
        assertPrimitiveLocation(int.class, b.getShape().getProperty("x").getLocation());

        int newFlags = 7;
        library.putWithFlags(b, "r", 5, newFlags);
        library.putIfPresent(b, "x", 6.6);

        assertEquals(2, library.getOrDefault(a, "x", null));
        assertEquals(6.6, library.getOrDefault(b, "x", null));
        library.updateShape(a); // assertTrue(a.updateShape());
        assertEquals(2, library.getOrDefault(a, "x", null));
        assertTrue(a.getShape().isValid());
        library.putWithFlags(a, "r", 7, newFlags);
        assertEquals(2, library.getOrDefault(a, "x", null));
        assertTrue("expected valid shape after put", a.getShape().isValid());
        assertObjectLocation(b.getShape().getProperty("x").getLocation());
    }

    @Test
    public void testRemovePropertyOnObsoleteShape() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);
        DynamicObject b = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.put(a, "x", "");
        library.put(b, "x", "");
        library.put(a, "y", "");
        library.put(b, "y", "");
        library.put(a, "z", "");
        library.put(b, "z", "");

        library.put(a, "u", "");
        library.put(b, "v", "");

        library.put(b, "x", new StringBuilder("ab"));
        library.removeKey(a, "z");
        library.put(a, "y", new StringBuilder("ab"));
    }

    @Test
    public void testReplaceDeclaredProperty() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.putConstant(a, "a", null, 0);

        library.put(a, "b", 13);
        library.put(a, "c", 14);
        library.put(a, "d", 15);
        library.put(a, "e", 16);
        library.put(a, "f", 17);

        library.put(a, "a", 18);
    }

    @Test
    public void testReplaceDeclaredProperty2() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.putConstant(a, "a", null, 0);

        library.put(a, "b", 13);
        library.put(a, "c", 14);
        library.put(a, "d", 15);
        library.put(a, "e", 16);
        library.put(a, "f", 17);

        library.put(a, "a", "v");
        library.putWithFlags(a, "a", 42, 2);
    }

    @Test
    public void testDeclaredPropertyShapeTransitionCount() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        for (int i = 0; i < 26; i++) {
            library.putConstant(a, Character.toString((char) ('a' + i)), null, 0);
        }
        for (int i = 0; i < 26; i++) {
            assertTrue(library.putIfPresent(a, Character.toString((char) ('a' + i)), 42));
        }

        assertEquals(52, countTransitions(emptyShape));
    }

    private static int countTransitions(Shape shape) {
        var consumer = new BiConsumer<Object, Shape>() {
            int count = 0;

            @Override
            public void accept(Object t, Shape childShape) {
                count += 1;
                count += countTransitions(childShape);
            }
        };
        invokeMethod("forEachTransition", shape, consumer);
        return consumer.count;
    }

    @Test
    public void testChangePropertyFlagsWithObsolescence() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.putWithFlags(a, "s", 42, 0);
        library.putIfPresent(a, "s", 43);
        library.putWithFlags(a, "x", 42, 0);
        library.putIfPresent(a, "x", 43);

        DynamicObject b = newInstance(emptyShape);
        library.putWithFlags(b, "s", 42, 1);
        library.putIfPresent(b, "s", 43);
        library.put(b, "x", 42);
        library.put(b, "y", 42);

        library.putWithFlags(b, "s", 42, 0);
    }

    @Test
    public void testChangePropertyFlagsWithObsolescenceGR53902() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);
        DynamicObject b = newInstance(emptyShape);
        DynamicObject x = newInstance(emptyShape);
        DynamicObject y = newInstance(emptyShape);
        DynamicObject z = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.putWithFlags(a, "s", 42, 0);
        library.putIfPresent(a, "s", 43);
        library.putWithFlags(a, "x", x, 0);
        library.putWithFlags(a, "y", y, 0);

        library.putWithFlags(b, "s", 42, 0);
        library.putIfPresent(b, "s", 43);
        library.putWithFlags(b, "x", x, 0);
        library.putWithFlags(b, "y", y, 0);

        library.putIfPresent(b, "s", z);

        library.setPropertyFlags(a, "s", 3);

        assertEquals(43, library.getOrDefault(a, "s", null));
        assertEquals(x, library.getOrDefault(a, "x", null));
        assertEquals(y, library.getOrDefault(a, "y", null));
    }

    @Test
    public void testChangePropertyTypeWithObsolescence() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.putWithFlags(a, "s", 13.37, 0);
        library.putWithFlags(a, "x", 42, 0);
        library.putIfPresent(a, "s", 43);

        DynamicObject b = newInstance(emptyShape);
        library.put(b, "s", 42);
        library.put(b, "x", 42);
        library.put(b, "y", 42);

        library.putIfPresent(b, "s", new Object());
    }

    @Test
    public void testAssumedFinalLocationShapeTransitionCount() {
        Shape emptyShape = newEmptyShape();
        DynamicObject a = newInstance(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        for (int i = 0; i < 26; i++) {
            library.put(a, Character.toString((char) ('a' + i)), 0);
        }
        for (int i = 0; i < 26; i++) {
            assertTrue(library.putIfPresent(a, Character.toString((char) ('a' + i)), 42));
        }
        assertEquals(26, countTransitions(emptyShape));
    }

    /**
     * GR-31263.
     */
    @Test
    public void testChangeFlagsAndType() {
        Shape emptyShape = Shape.newBuilder().build();

        DynamicObject a = new TestDynamicObject(emptyShape);
        DynamicObject b = new TestDynamicObject(emptyShape);
        DynamicObject c = new TestDynamicObject(emptyShape);
        DynamicObject d = new TestDynamicObject(emptyShape);
        DynamicObject e = new TestDynamicObject(emptyShape);
        DynamicObject obj = new TestDynamicObject(emptyShape);

        DynamicObject temp = new TestDynamicObject(emptyShape);
        createLibrary(DynamicObjectLibrary.class, temp).putWithFlags(temp, "a", a, 8);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, obj);

        library.put(obj, "a", 42);
        library.put(obj, "b", b);
        library.put(obj, "c", c);
        library.put(obj, "d", d);
        library.put(obj, "e", e);

        library.putWithFlags(obj, "a", a, 8);

        assertNotNull(temp);
        assertSame("a", a, library.getOrDefault(obj, "a", null));
        assertSame("b", b, library.getOrDefault(obj, "b", null));
        assertSame("c", c, library.getOrDefault(obj, "c", null));
        assertSame("d", d, library.getOrDefault(obj, "d", null));
        assertSame("e", e, library.getOrDefault(obj, "e", null));
    }

    @Test
    public void testChangeFlagsConstantToNonConstant() {
        Shape emptyShape = Shape.newBuilder().build();

        DynamicObject a = new TestDynamicObject(emptyShape);
        DynamicObject b = new TestDynamicObject(emptyShape);
        DynamicObject c = new TestDynamicObject(emptyShape);
        DynamicObject d = new TestDynamicObject(emptyShape);
        DynamicObject e = new TestDynamicObject(emptyShape);
        DynamicObject obj = new TestDynamicObject(emptyShape);

        DynamicObject temp = new TestDynamicObject(emptyShape);
        createLibrary(DynamicObjectLibrary.class, temp).putWithFlags(temp, "a", a, 8);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, obj);

        library.putConstant(obj, "a", a, 3);
        library.put(obj, "b", b);
        library.put(obj, "c", c);
        library.put(obj, "d", d);
        library.put(obj, "e", e);

        assertTrue(obj.getShape().getProperty("a").getLocation().isConstant());

        library.putWithFlags(obj, "a", b, 8);

        assertFalse(obj.getShape().getProperty("a").getLocation().isConstant());

        assertNotNull(temp);
        assertSame("a", b, library.getOrDefault(obj, "a", null));
        assertSame("b", b, library.getOrDefault(obj, "b", null));
        assertSame("c", c, library.getOrDefault(obj, "c", null));
        assertSame("d", d, library.getOrDefault(obj, "d", null));
        assertSame("e", e, library.getOrDefault(obj, "e", null));
    }

    @Test
    public void testTryMergeShapes() {
        // Assume (MaxMergeDepth >= 5)
        Shape emptyShape = Shape.newBuilder().allowImplicitCastIntToDouble(true).build();

        DynamicObject a = new TestDynamicObject(emptyShape);
        DynamicObject b = new TestDynamicObject(emptyShape);
        DynamicObject c = new TestDynamicObject(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.setShapeFlags(a, 1);
        library.put(a, "a", 1);
        library.put(a, "b", 1);
        library.put(a, "c", 1);
        library.put(a, "d", 1);

        library.setShapeFlags(b, 2);
        library.put(b, "a", 1);
        library.put(b, "b", 1);
        library.put(b, "c", 1);
        library.put(b, "d", 1);

        library.put(c, "a", 1);
        Shape notObsoletedParent = c.getShape();
        library.put(c, "b", 1);
        Shape obsoletedParent = c.getShape();
        library.put(c, "c", 1);
        library.put(c, "d", 1);
        library.setShapeFlags(c, 1);

        assertNull(a.getShape().tryMerge(b.getShape()));
        assertNull(a.getShape().tryMerge(c.getShape()));
        assertNull(b.getShape().tryMerge(c.getShape()));

        DynamicObject d = new TestDynamicObject(emptyShape);
        library.put(d, "a", 1);
        library.put(d, "b", 3.14);
        library.put(d, "c", 1);
        library.put(d, "d", 1);
        library.setShapeFlags(d, 1);

        assertNotNull(c.getShape().tryMerge(d.getShape()));
        assertTrue(d.getShape().isValid());
        assertFalse(c.getShape().isValid());
        assertFalse(obsoletedParent.isValid());
        assertTrue(notObsoletedParent.isValid());
    }

    @Test
    public void testTryMergeShapes2() {
        // Assume (MaxMergeDepth >= 5 && MaxMergeDiff >= 2)

        Shape emptyShape = Shape.newBuilder().allowImplicitCastIntToDouble(true).build();

        DynamicObject a = new TestDynamicObject(emptyShape);
        DynamicObject b = new TestDynamicObject(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.put(a, "a", 1);
        Shape notObsoletedParent = a.getShape();
        library.put(a, "b", 1);
        Shape obsoletedParent = a.getShape();
        library.put(a, "c", 1);
        library.put(a, "d", 1);
        library.put(a, "e", 1);

        library.put(b, "a", 1);
        library.put(b, "b", 3.14);
        library.put(b, "c", 1);
        library.put(b, "d", 3.14);
        library.put(b, "e", 1);

        assertNotNull(a.getShape().tryMerge(b.getShape()));
        assertTrue(b.getShape().isValid());
        assertFalse(a.getShape().isValid());
        assertFalse(obsoletedParent.isValid());
        assertTrue(notObsoletedParent.isValid());
        library.updateShape(a);
        a.getShape().tryMerge(b.getShape());
        library.updateShape(a);
        assertSame(b.getShape(), a.getShape());
    }

    @Test
    public void testBooleanLocationTypeAssumption() {
        Shape emptyShape = Shape.newBuilder().build();

        DynamicObject obj = new TestDynamicObject(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, obj);

        library.put(obj, "b1", true);
        library.put(obj, "b2", true);
        library.put(obj, "b2", false);

        Shape shape = obj.getShape();
        MatcherAssert.assertThat(shape.getProperty("b1").getLocation().toString(), CoreMatchers.containsString("Boolean"));
        MatcherAssert.assertThat(shape.getProperty("b2").getLocation().toString(), CoreMatchers.containsString("Boolean"));
    }

    /**
     * Tests that onPropertyTransition is called by replace and remove property transitions.
     */
    @Test
    public void testPropertyAssumptionInvalidation() {
        Shape emptyShape = Shape.newBuilder().propertyAssumptions(true).build();

        DynamicObject a = new TestDynamicObject(emptyShape);

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, a);

        library.put(a, "a", 1);
        library.put(a, "b", 2);
        library.put(a, "c", 3);
        library.putConstant(a, "d", 4, 0);

        Assumption assumption = a.getShape().getPropertyAssumption("a");
        assertTrue(assumption.toString(), assumption.isValid());
        library.setPropertyFlags(a, "a", 1);
        assertFalse(assumption.toString(), assumption.isValid());

        assumption = a.getShape().getPropertyAssumption("b");
        assertTrue(assumption.toString(), assumption.isValid());
        library.removeKey(a, "b");
        assertFalse(assumption.toString(), assumption.isValid());

        assumption = a.getShape().getPropertyAssumption("c");
        assertTrue(assumption.toString(), assumption.isValid());
        library.put(a, "c", "val");
        assertFalse(assumption.toString(), assumption.isValid());

        assumption = a.getShape().getPropertyAssumption("d");
        assertTrue(assumption.toString(), assumption.isValid());
        library.putConstant(a, "d", 40, 0);
        assertFalse(assumption.toString(), assumption.isValid());
    }

    /**
     * Tests that property assumptions are blocked after remove property transitions.
     */
    @Test
    public void testPropertyAssumptionInvalidAfterRemove() {
        Shape emptyShape = Shape.newBuilder().propertyAssumptions(true).build();

        DynamicObject h1 = new TestDynamicObject(emptyShape);
        DynamicObjectLibrary on = createLibrary(DynamicObjectLibrary.class, h1);
        DynamicObjectLibrary off = createLibrary(DynamicObjectLibrary.class, h1);

        // initialize caches
        on.put(h1, "name", h1);
        on.put(h1, "alias", h1);
        off.removeKey(h1, "name");
        off.removeKey(h1, "alias");

        DynamicObject h2 = new TestDynamicObject(emptyShape);
        // repeat on another object with cached transitions
        on.put(h2, "name", h2);
        on.put(h2, "alias", h2);

        Assumption aliasAssumption = h2.getShape().getPropertyAssumption("alias");
        assertFalse("Property assumption for 'alias' should already be invalid: " + aliasAssumption, aliasAssumption.isValid());

        on.put(h2, "alias", h2);
        off.removeKey(h2, "name");
        off.removeKey(h2, "alias");
    }

    /**
     * Tests that property assumptions are blocked after replace property transitions.
     */
    @Test
    public void testPropertyAssumptionInvalidAfterReplace1() {
        Shape emptyShape = Shape.newBuilder().propertyAssumptions(true).build();

        int flag = 2;
        DynamicObject h1 = new TestDynamicObject(emptyShape);
        DynamicObjectLibrary on = createLibrary(DynamicObjectLibrary.class, h1);
        DynamicObjectLibrary off = createLibrary(DynamicObjectLibrary.class, h1);

        // initialize caches
        on.put(h1, "name", h1);
        on.put(h1, "alias", h1);
        off.setPropertyFlags(h1, "name", flag);
        off.setPropertyFlags(h1, "alias", flag);

        DynamicObject h2 = new TestDynamicObject(emptyShape);
        // repeat cached operations on another object
        on.put(h2, "name", h2);
        on.put(h2, "alias", h2);

        Assumption aliasAssumption = h2.getShape().getPropertyAssumption("alias");
        assertFalse("Property assumption for 'alias' should already be invalid: " + aliasAssumption, aliasAssumption.isValid());

        on.put(h2, "alias", h2);
        off.setPropertyFlags(h2, "name", flag);
        off.setPropertyFlags(h2, "alias", flag);

        assertEquals(flag, h2.getShape().getProperty("name").getFlags());
        assertEquals(flag, h2.getShape().getProperty("alias").getFlags());
    }

    /**
     * Tests that property assumptions are blocked after replace property transitions.
     */
    @Test
    public void testPropertyAssumptionInvalidAfterReplace2() {
        Shape emptyShape = Shape.newBuilder().propertyAssumptions(true).build();

        int flag = 2;
        DynamicObject h1 = new TestDynamicObject(emptyShape);
        DynamicObjectLibrary on = createLibrary(DynamicObjectLibrary.class, h1);
        DynamicObjectLibrary off = createLibrary(DynamicObjectLibrary.class, h1);

        // initialize caches
        on.put(h1, "name", h1);
        on.put(h1, "alias", h1);
        off.putWithFlags(h1, "name", h1, flag);
        off.putWithFlags(h1, "alias", h1, flag);

        DynamicObject h2 = new TestDynamicObject(emptyShape);
        // repeat cached operations on another object
        on.put(h2, "name", h2);
        on.put(h2, "alias", h2);

        Assumption aliasAssumption = h2.getShape().getPropertyAssumption("alias");
        assertFalse("Property assumption for 'alias' should already be invalid: " + aliasAssumption, aliasAssumption.isValid());

        on.put(h2, "alias", h2);
        off.putWithFlags(h2, "name", h2, flag);
        off.putWithFlags(h2, "alias", h2, flag);

        assertEquals(flag, h2.getShape().getProperty("name").getFlags());
        assertEquals(flag, h2.getShape().getProperty("alias").getFlags());
    }

    /**
     * Tests that property assumptions are invalid after value type transitions.
     */
    @Test
    public void testPropertyAssumptionInvalidAfterTypeTransition() {
        Shape emptyShape = Shape.newBuilder().propertyAssumptions(true).build();

        DynamicObject h1 = new TestDynamicObject(emptyShape);
        DynamicObjectLibrary lib = createLibrary(DynamicObjectLibrary.class, h1);

        // initialize caches
        lib.put(h1, "name", 42);
        lib.put(h1, "alias", 43);

        Assumption aliasAssumption = h1.getShape().getPropertyAssumption("alias");

        DynamicObject h2 = new TestDynamicObject(emptyShape);
        // repeat cached operations on another object
        lib.put(h2, "name", 42);
        lib.put(h2, "alias", h1);

        assertFalse("Property assumption for 'alias' should be invalid: " + aliasAssumption, aliasAssumption.isValid());
    }

    static class TestDynamicObject extends DynamicObject {
        protected TestDynamicObject(Shape shape) {
            super(shape);
        }
    }

    static class TestDynamicObjectWithFields extends DynamicObject {
        @DynamicField Object o0;
        @DynamicField Object o1;
        @DynamicField Object o2;
        @DynamicField Object o3;
        @DynamicField long p0;
        @DynamicField long p1;
        @DynamicField long p2;

        protected TestDynamicObjectWithFields(Shape shape) {
            super(shape);
        }
    }
}
