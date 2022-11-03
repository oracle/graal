/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InheritanceTest extends StaticObjectModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static TestConfiguration[] data() {
        return getTestConfigurations();
    }

    @Parameterized.Parameter public TestConfiguration config;

    public static class CustomStaticObject1 {
        public byte field1;
        public boolean field2;
    }

    public interface CustomStaticObjectFactory1 {
        CustomStaticObject1 create();
    }

    @Test
    public void baseClassInheritance() throws NoSuchFieldException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("field1");
            builder.property(property, int.class, false);
            StaticShape<CustomStaticObjectFactory1> shape = builder.build(CustomStaticObject1.class, CustomStaticObjectFactory1.class);
            CustomStaticObject1 object = shape.getFactory().create();

            // Set to the field declared in the super class
            object.field1 = 42;
            // Set to the field declared in the generated class
            property.setInt(object, 24);
            // Get the value of the field declared in the super class
            Assert.assertEquals(42, object.field1);
            // Get the value of the field declared in the generated class
            Assert.assertEquals(24, property.getInt(object));

            Assume.assumeTrue(te.supportsReflectiveAccesses());
            // `CustomStaticObject1.field1` is shadowed
            Assert.assertEquals(int.class, object.getClass().getField("field1").getType());
            // `CustomStaticObject1.field2` is visible
            Assert.assertEquals(boolean.class, object.getClass().getField("field2").getType());
        }
    }

    @Test
    public void baseShapeInheritance() throws NoSuchFieldException, IllegalAccessException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s1p1 = new DefaultStaticProperty("field1");
            StaticProperty s1p2 = new DefaultStaticProperty("field2");
            b1.property(s1p1, int.class, false);
            b1.property(s1p2, int.class, false);
            // StaticShape s1 declares 2 properties: s1p1 and s1p2
            StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

            StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s2p1 = new DefaultStaticProperty("field1");
            b2.property(s2p1, int.class, false);
            // StaticShape s2:
            // 1. extends s1
            // 2. declares one property: s2p1
            // 3. inherits one property from s1: s1p2
            StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1);
            Object object = s2.getFactory().create();

            s1p1.setInt(object, 1);
            Assert.assertEquals(1, s1p1.getInt(object));
            Assert.assertEquals(0, s2p1.getInt(object));
            s1p2.setInt(object, 2);
            Assert.assertEquals(2, s1p2.getInt(object));
            s2p1.setInt(object, 3);
            // s1p1 accesses the field declared in s1
            Assert.assertEquals(1, s1p1.getInt(object));
            Assert.assertEquals(3, s2p1.getInt(object));

            Assume.assumeTrue(te.supportsReflectiveAccesses());
            Assert.assertEquals(3, object.getClass().getField("field1").getInt(object));
        }
    }

    public static class CustomStaticObject2 {
        private final long longField;
        private final Object objField;

        protected CustomStaticObject2(long longField, Object objField) {
            this.longField = longField;
            this.objField = objField;
        }

        long getLongField() {
            return longField;
        }

        Object getObjField() {
            return objField;
        }
    }

    public interface CustomStaticObjectFactory2 {
        CustomStaticObject2 create(long longField, Object objField);
    }

    @Test
    @SuppressWarnings("unused")
    public void accessObjField() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            for (int i = 0; i < 50_000; i++) {
                long longArg = 12345L;
                Object objArg = new Object();

                // 1. Create a parent shape that can allocate static objects that extend
                // `CustomStaticObject2`
                StaticShape.Builder parentBuilder = StaticShape.newBuilder(te.testLanguage);
                StaticShape<CustomStaticObjectFactory2> parentShape = parentBuilder.build(CustomStaticObject2.class, CustomStaticObjectFactory2.class);

                // 2. Allocate a static object of the parent shape.
                // Arguments are passed to the static object factory, which passes them to the
                // constructor of the static object, which passes them to the constructor of its
                // super class (`CustomStaticObject2`).
                CustomStaticObject2 parentObject = parentShape.getFactory().create(longArg, objArg);

                // 3. Create a child static shape that extends the parent shape.
                // To reproduce GR-41414 it is necessary that the child shape is created after we
                // allocated the static object of the parent shape. It is not necessary to allocate
                // a static object of the child shape.
                StaticShape.Builder childBuilder = StaticShape.newBuilder(te.testLanguage);
                StaticShape<CustomStaticObjectFactory2> childShape = childBuilder.build(parentShape);

                // 4. Check that `getObjField()` returns the same instance that we passed to the
                // factory.
                Object obj = parentObject.getObjField();
                if (obj != objArg) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Error at iteration ").append(i).append(": Fields do not match\n");
                    sb.append("Expected class: ").append(objArg.getClass());
                    sb.append("\tGot: ").append(obj == null ? "null" : obj.getClass()).append("\n");
                    sb.append("Expected hashCode: ").append(System.identityHashCode(objArg));
                    sb.append("\tGot: ").append(System.identityHashCode(obj));
                    Assert.fail(sb.toString());
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void accessLongField() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            for (int i = 0; i < 50_000; i++) {
                long longArg = 12345L;
                Object objArg = new Object();

                // 1. Create a parent shape that can allocate static objects that extend
                // `CustomStaticObject2`
                StaticShape.Builder parentBuilder = StaticShape.newBuilder(te.testLanguage);
                StaticShape<CustomStaticObjectFactory2> parentShape = parentBuilder.build(CustomStaticObject2.class, CustomStaticObjectFactory2.class);

                // 2. Allocate a static object of the parent shape.
                // `objArg` is passed to the static object factory, which passes it to the
                // constructor of the static object, which passes it to the constructor of its
                // super class (`CustomStaticObject2`).
                CustomStaticObject2 parentObject = parentShape.getFactory().create(longArg, objArg);

                // 3. Create a child static shape that extends the parent shape.
                StaticShape.Builder childBuilder = StaticShape.newBuilder(te.testLanguage);
                StaticShape<CustomStaticObjectFactory2> childShape = childBuilder.build(parentShape);

                // 4. Check that `getLongField()` returns the same instance that we passed to the
                // factory.
                long l = parentObject.getLongField();
                if (l != longArg) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Error at iteration ").append(i).append(": Fields do not match\n");
                    sb.append("Expected value: ").append(longArg).append("\tGot: ").append(l);
                    Assert.fail(sb.toString());
                }
            }
        }
    }
}
