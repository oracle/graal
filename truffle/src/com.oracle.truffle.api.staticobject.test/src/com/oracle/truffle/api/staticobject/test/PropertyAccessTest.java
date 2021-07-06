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
import com.oracle.truffle.api.staticobject.StaticPropertyKind;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class PropertyAccessTest extends StaticObjectModelTest {
    @DataPoints //
    public static TestEnvironment[] environments;
    @DataPoints //
    public static TestDescriptor[] descriptors;

    static class TestDescriptor {
        final StaticPropertyKind kind;
        final Object testValue;
        final Object defaultValue;
        final PropertyGetter getter;
        final PropertySetter setter;

        TestDescriptor(StaticPropertyKind kind, Object testValue, Object defaultValue, PropertyGetter getter, PropertySetter setter) {
            this.kind = kind;
            this.testValue = testValue;
            this.defaultValue = defaultValue;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public String toString() {
            return kind.name();
        }
    }

    @FunctionalInterface
    interface PropertyGetter {
        Object get(StaticProperty property, Object receiver);
    }

    @FunctionalInterface
    interface PropertySetter {
        void set(StaticProperty property, Object receiver, Object value);
    }

    @BeforeClass
    public static void setup() {
        environments = getTestEnvironments();
        descriptors = new TestDescriptor[StaticPropertyKind.values().length];

        for (StaticPropertyKind kind : StaticPropertyKind.values()) {
            int i = kind.ordinal();
            switch (kind) {
                case Boolean:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Boolean,
                                    true,
                                    false,
                                    (p, obj) -> p.getBoolean(obj),
                                    (p, obj, val) -> p.setBoolean(obj, (boolean) val));
                    break;
                case Byte:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Byte,
                                    (byte) 0x01,
                                    (byte) 0,
                                    (p, obj) -> p.getByte(obj),
                                    (p, obj, val) -> p.setByte(obj, (byte) val));
                    break;
                case Char:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Char,
                                    (char) 0x0203,
                                    (char) 0,
                                    (p, obj) -> p.getChar(obj),
                                    (p, obj, val) -> p.setChar(obj, (char) val));
                    break;
                case Double:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Double,
                                    Double.longBitsToDouble(0x161718191a1b1c1dL),
                                    0D,
                                    (p, obj) -> p.getDouble(obj),
                                    (p, obj, val) -> p.setDouble(obj, (double) val));
                    break;
                case Float:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Float,
                                    Float.intBitsToFloat(0x12131415),
                                    0F,
                                    (p, obj) -> p.getFloat(obj),
                                    (p, obj, val) -> p.setFloat(obj, (float) val));
                    break;
                case Int:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Int,
                                    0x0607_0809,
                                    0,
                                    (p, obj) -> p.getInt(obj),
                                    (p, obj, val) -> p.setInt(obj, (int) val));
                    break;
                case Long:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Long,
                                    0x0a0b_0c0d_0e0f_10_11L,
                                    0L,
                                    (p, obj) -> p.getLong(obj),
                                    (p, obj, val) -> p.setLong(obj, (long) val));
                    break;
                case Short:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Short,
                                    (short) 0x0405,
                                    (short) 0,
                                    (p, obj) -> p.getShort(obj),
                                    (p, obj, val) -> p.setShort(obj, (short) val));
                    break;
                case Object:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Object,
                                    new Object(),
                                    null,
                                    (p, obj) -> p.getObject(obj),
                                    (p, obj, val) -> p.setObject(obj, val));
                    break;
                default:
                    Assert.fail();
            }
        }
    }

    @AfterClass
    public static void teardown() {
        for (TestEnvironment env : environments) {
            env.close();
        }
    }

    @Theory
    public void correctAccessors(TestEnvironment te, TestDescriptor descriptor) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty property = new DefaultStaticProperty("property", descriptor.kind, false);
        builder.property(property);
        StaticShape<DefaultStaticObjectFactory> shape = builder.build();
        Object object = shape.getFactory().create();

        // Check the default value
        Object actualValue = descriptor.getter.get(property, object);
        Assert.assertEquals(descriptor.defaultValue, actualValue);
        // Check property accesses
        descriptor.setter.set(property, object, descriptor.testValue);
        actualValue = descriptor.getter.get(property, object);
        Assert.assertEquals(descriptor.testValue, actualValue);
    }

    @Theory
    public void wrongAccessors(TestEnvironment te, TestDescriptor expectedDescriptor, TestDescriptor actualDescriptor) {
        Assume.assumeFalse(expectedDescriptor.equals(actualDescriptor));

        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty property = new DefaultStaticProperty("property", expectedDescriptor.kind, false);
        builder.property(property);
        StaticShape<DefaultStaticObjectFactory> shape = builder.build();
        Object object = shape.getFactory().create();

        // Check that wrong getters throw exceptions
        String expectedExceptionMessage = "Static property 'property' of kind '" + expectedDescriptor.kind.name() + "' cannot be accessed as '" + actualDescriptor.kind + "'";
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

    @Theory
    @SuppressWarnings("unused")
    public void wrongShape(TestEnvironment te, TestDescriptor descriptor) {
        StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
        StaticProperty p1 = new DefaultStaticProperty("property", descriptor.kind, false);
        b1.property(p1);
        StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

        StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
        StaticProperty p2 = new DefaultStaticProperty("property", descriptor.kind, false);
        b2.property(p2);
        StaticShape<DefaultStaticObjectFactory> s2 = b2.build();
        Object o2 = s2.getFactory().create();

        try {
            descriptor.setter.set(p1, o2, descriptor.testValue);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            if (te.arrayBased) {
                Assert.assertTrue(e.getMessage().startsWith("Incompatible shape on property access."));
            } else {
                Assert.assertTrue(e.getMessage().matches("Object '.*' of class '.*' does not have the expected shape"));
            }
        }
    }

    @Theory
    @SuppressWarnings("unused")
    public void wrongObject(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        builder.property(property);
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

    @Test
    public void dummy() {
        // to make sure this file is recognized as a test
    }
}
