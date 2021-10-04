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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class PropertyAccessTest extends StaticObjectModelTest {
    private static TestDescriptor[] descriptors;

    @FunctionalInterface
    interface PropertyGetter {
        Object get(StaticProperty property, Object receiver);
    }

    @FunctionalInterface
    interface PropertySetter {
        void set(StaticProperty property, Object receiver, Object value);
    }

    static class TestDescriptor {
        final Class<?> type;
        final String typeName;
        final Object testValue;
        final Object defaultValue;
        final PropertyGetter getter;
        final PropertySetter setter;

        TestDescriptor(Class<?> type, String typeName, Object testValue, Object defaultValue, PropertyGetter getter, PropertySetter setter) {
            this.type = type;
            this.typeName = typeName;
            this.testValue = testValue;
            this.defaultValue = defaultValue;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public String toString() {
            return type.getName();
        }
    }

    @SuppressWarnings("rawtypes")
    @Parameterized.Parameters(name = "{0} and {1} property access")
    public static Collection<Object[]> data() {
        TestConfiguration[] configs = getTestConfigurations();

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

        descriptors = new TestDescriptor[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == boolean.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "boolean",
                                true,
                                false,
                                (p, obj) -> p.getBoolean(obj),
                                (p, obj, val) -> p.setBoolean(obj, (boolean) val));
            } else if (types[i] == byte.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "byte",
                                (byte) 0x01,
                                (byte) 0,
                                (p, obj) -> p.getByte(obj),
                                (p, obj, val) -> p.setByte(obj, (byte) val));
            } else if (types[i] == char.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "char",
                                (char) 0x0203,
                                (char) 0,
                                (p, obj) -> p.getChar(obj),
                                (p, obj, val) -> p.setChar(obj, (char) val));
            } else if (types[i] == double.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "double",
                                Double.longBitsToDouble(0x161718191a1b1c1dL),
                                0D,
                                (p, obj) -> p.getDouble(obj),
                                (p, obj, val) -> p.setDouble(obj, (double) val));
            } else if (types[i] == float.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "float",
                                Float.intBitsToFloat(0x12131415),
                                0F,
                                (p, obj) -> p.getFloat(obj),
                                (p, obj, val) -> p.setFloat(obj, (float) val));
            } else if (types[i] == int.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "int",
                                0x0607_0809,
                                0,
                                (p, obj) -> p.getInt(obj),
                                (p, obj, val) -> p.setInt(obj, (int) val));
            } else if (types[i] == long.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "long",
                                0x0a0b_0c0d_0e0f_10_11L,
                                0L,
                                (p, obj) -> p.getLong(obj),
                                (p, obj, val) -> p.setLong(obj, (long) val));
            } else if (types[i] == short.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "short",
                                (short) 0x0405,
                                (short) 0,
                                (p, obj) -> p.getShort(obj),
                                (p, obj, val) -> p.setShort(obj, (short) val));
            } else if (types[i] == Object.class) {
                descriptors[i] = new TestDescriptor(
                                types[i],
                                "java.lang.Object",
                                new Object(),
                                null,
                                (p, obj) -> p.getObject(obj),
                                (p, obj, val) -> p.setObject(obj, val));
            }
        }

        ArrayList<Object[]> data = new ArrayList<>();
        for (TestConfiguration config : configs) {
            for (TestDescriptor td : descriptors) {
                data.add(new Object[]{config, td});
            }
        }
        return data;
    }

    @Parameterized.Parameter(0) public TestConfiguration config;
    @Parameterized.Parameter(1) public TestDescriptor td;

    @Test
    public void correctAccessors() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            builder.property(property, td.type, false);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();

            // Check the default value
            Object actualValue = td.getter.get(property, object);
            Assert.assertEquals(td.defaultValue, actualValue);
            // Check property accesses
            td.setter.set(property, object, td.testValue);
            actualValue = td.getter.get(property, object);
            Assert.assertEquals(td.testValue, actualValue);
        }
    }

    @Test
    public void wrongAccessors() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            TestDescriptor expectedDescriptor = td;
            for (TestDescriptor actualDescriptor : descriptors) {
                if (!expectedDescriptor.equals(actualDescriptor)) {
                    StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
                    StaticProperty property = new DefaultStaticProperty("property");
                    builder.property(property, expectedDescriptor.type, false);
                    StaticShape<DefaultStaticObjectFactory> shape = builder.build();
                    Object object = shape.getFactory().create();

                    // Check that wrong getters throw exceptions
                    String expectedExceptionMessage = "Static property 'property' of type '" + expectedDescriptor.typeName + "' cannot be accessed as '" + actualDescriptor.typeName + "'";
                    try {
                        actualDescriptor.getter.get(property, object);
                        Assert.fail();
                    } catch (RuntimeException e) {
                        Assert.assertEquals(expectedExceptionMessage, e.getMessage());
                    }
                    try {
                        actualDescriptor.setter.set(property, object, actualDescriptor.testValue);
                        Assert.fail();
                    } catch (RuntimeException e) {
                        Assert.assertEquals(expectedExceptionMessage, e.getMessage());
                    }
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void wrongShape() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p1 = new DefaultStaticProperty("property");
            b1.property(p1, td.type, false);
            StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

            StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p2 = new DefaultStaticProperty("property");
            b2.property(p2, td.type, false);
            StaticShape<DefaultStaticObjectFactory> s2 = b2.build();
            Object o2 = s2.getFactory().create();

            try {
                td.setter.set(p1, o2, td.testValue);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                if (te.isArrayBased()) {
                    Assert.assertTrue(e.getMessage().startsWith("Incompatible shape on property access."));
                } else {
                    Assert.assertTrue(e.getMessage().matches("Object '.*' of class '.*' does not have the expected shape"));
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void wrongObject() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            builder.property(property, int.class, false);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object staticObject = shape.getFactory().create();
            Object wrongObject = new Object();

            try {
                property.setInt(wrongObject, 42);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().matches("Object '.*' of class '.*' does not have the expected shape"));
            }
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void wrongType() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            builder.property(property, String.class, false);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object staticObject = shape.getFactory().create();
            Object wrongValue = new Object();

            try {
                property.setObject(staticObject, wrongValue);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertEquals("Static property 'property' of type 'java.lang.String' cannot be accessed as 'java.lang.Object'", e.getMessage());
            }
        }
    }
}
