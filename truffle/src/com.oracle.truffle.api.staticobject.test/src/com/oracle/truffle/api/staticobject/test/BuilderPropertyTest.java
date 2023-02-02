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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

@RunWith(Parameterized.class)
public class BuilderPropertyTest extends StaticObjectModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static TestConfiguration[] data() {
        return getTestConfigurations();
    }

    @Parameterized.Parameter public TestConfiguration config;

    @Test
    public void sameBuilderSameProperty() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            builder.property(property, int.class, false);
            try {
                // You cannot add the same property twice
                builder.property(property, int.class, false);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                // You cannot add the same property twice
                Assert.assertEquals("This builder already contains a property with id 'property'", e.getMessage());
            }
        }
    }

    @Test
    public void sameBuilderSameId() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p1 = new DefaultStaticProperty("property");
            StaticProperty p2 = new DefaultStaticProperty("property");
            builder.property(p1, int.class, false);
            try {
                // You cannot add two properties with the same id
                builder.property(p2, int.class, false);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertEquals("This builder already contains a property with id 'property'", e.getMessage());
            }
        }
    }

    @Test
    public void differentBuildersSameProperty() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
            StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            b1.property(property, int.class, false);
            b2.property(property, int.class, false);
            b1.build();
            try {
                // You cannot build shapes that share properties
                b2.build();
                Assert.fail();
            } catch (RuntimeException e) {
                Assert.assertEquals("Attempt to reinitialize the offset of static property 'property' of type 'int'.\nWas it added to more than one builder or multiple times to the same builder?",
                                e.getMessage());
            }
        }
    }

    @Test
    public void buildTwice() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            builder.property(new DefaultStaticProperty("property"), int.class, false);
            builder.build();
            try {
                builder.build();
                Assert.fail();
            } catch (IllegalStateException e) {
                Assert.assertEquals("This Builder instance has already built a StaticShape. It is not possible to add static properties or build other shapes", e.getMessage());
            }
        }
    }

    @Test
    public void addPropertyAgain() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            builder.property(new DefaultStaticProperty("property1"), int.class, false);
            builder.build();
            try {
                builder.property(new DefaultStaticProperty("property2"), int.class, false);
                Assert.fail();
            } catch (IllegalStateException e) {
                Assert.assertEquals("This Builder instance has already built a StaticShape. It is not possible to add static properties or build other shapes", e.getMessage());
            }
        }
    }

    @Test
    public void propertyId() throws NoSuchFieldException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            Assume.assumeTrue(te.supportsReflectiveAccesses());
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            builder.property(property, int.class, false);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();
            object.getClass().getField(guessGeneratedFieldName(property));
        }
    }

    @Test
    public void propertyIdWithForbiddenChars() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p1 = new DefaultStaticProperty("forbidden.char");
            StaticProperty p2 = new DefaultStaticProperty("forbidden;char");
            StaticProperty p3 = new DefaultStaticProperty("forbidden[char");
            StaticProperty p4 = new DefaultStaticProperty("forbidden/char");
            builder.property(p1, int.class, false);
            builder.property(p2, int.class, false);
            builder.property(p3, int.class, false);
            builder.property(p4, int.class, false);
            builder.build();
        }
    }

    @Test
    public void propertyIdTooLong() throws NoSuchFieldException, IllegalAccessException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            byte[] longId = new byte[65536];
            Arrays.fill(longId, (byte) 120);

            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p1 = new DefaultStaticProperty("property1");
            StaticProperty p2 = new DefaultStaticProperty(new String(longId));
            builder.property(p1, int.class, false);
            builder.property(p2, int.class, false);
            Object staticObject = builder.build().getFactory().create();
            p1.setInt(staticObject, 1);
            p2.setInt(staticObject, 2);

            Assume.assumeTrue(te.supportsReflectiveAccesses());
            Class<?> staticObjectClass = staticObject.getClass();
            Assert.assertEquals(1, staticObjectClass.getField("field0").get(staticObject));
            Assert.assertEquals(2, staticObjectClass.getField("field1").get(staticObject));
        }
    }

    @Test
    public void propertyFinal() throws NoSuchFieldException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            Assume.assumeTrue(te.supportsReflectiveAccesses());

            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p1 = new DefaultStaticProperty("p1");
            StaticProperty p2 = new DefaultStaticProperty("p2");
            builder.property(p1, int.class, true);
            builder.property(p2, int.class, false);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();
            Field f1 = object.getClass().getField(guessGeneratedFieldName(p1));
            Field f2 = object.getClass().getField(guessGeneratedFieldName(p2));
            Assert.assertTrue(Modifier.isFinal(f1.getModifiers()));
            Assert.assertFalse(Modifier.isFinal(f2.getModifiers()));
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void propertyKind() throws NoSuchFieldException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            Assume.assumeTrue(te.supportsReflectiveAccesses());
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            Class[] types = new Class[]{
                            boolean.class,
                            byte.class,
                            char.class,
                            double.class,
                            float.class,
                            int.class,
                            long.class,
                            Object.class,
                            short.class};
            StaticProperty[] properties = new StaticProperty[types.length];
            for (int i = 0; i < properties.length; i++) {
                properties[i] = new DefaultStaticProperty("property" + i);
                builder.property(properties[i], types[i], false);
            }
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();
            for (int i = 0; i < types.length; i++) {
                Assert.assertEquals(types[i], object.getClass().getField(guessGeneratedFieldName(properties[i])).getType());
            }
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void privateClass() throws NoSuchFieldException {
        Class<?> privateType = VisibilityTest.getPrivateClass();
        testPrivatePropertyType(config, privateType);
    }

    @Test
    public void packagePrivateClass() throws NoSuchFieldException, ClassNotFoundException {
        Class<?> privateType = Class.forName("com.oracle.truffle.api.staticobject.test.external.PrivateClass");
        testPrivatePropertyType(config, privateType);
    }

    private static void testPrivatePropertyType(TestConfiguration config, Class<?> privateType) throws NoSuchFieldException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            Assume.assumeTrue(te.supportsReflectiveAccesses());
            // We run unit tests with Graal on a GraalJDK with disabled Locator and the Truffle API
            // jar in the boot class path. As a consequence, generated classes do not have
            // visibility of classes loaded by the application class loader.
            Assume.assumeNotNull(DefaultStaticObjectFactory.class.getClassLoader());
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            Class<?> propertyType = privateType;
            StaticProperty property = new DefaultStaticProperty("property");
            builder.property(property, propertyType, false);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();

            Assert.assertEquals(propertyType, object.getClass().getField(guessGeneratedFieldName(property)).getType());
        }
    }

    @Test
    public void maxProperties() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            for (int i = 0; i <= 65535; i++) {
                try {
                    builder.property(new DefaultStaticProperty("property" + i), int.class, false);
                } catch (IllegalArgumentException e) {
                    Assert.assertEquals("This builder already contains the maximum number of properties: 65535", e.getMessage());
                    Assert.assertEquals(65535, i);
                    return;
                }
            }
            Assert.fail();
        }
    }

    @Test
    public void propertyOfArrayType() throws NoSuchFieldException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            Assume.assumeTrue(te.supportsReflectiveAccesses());
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            DefaultStaticProperty arrayProperty = new DefaultStaticProperty("intArray");
            builder.property(arrayProperty, int[].class, false);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object staticObject = shape.getFactory().create();
            staticObject.getClass().getField("intArray").getType().getName().equals("[I");
            int[] intArray = new int[10];
            arrayProperty.setObject(staticObject, intArray);
            Assert.assertEquals(intArray, arrayProperty.getObject(staticObject));
        }
    }
}
