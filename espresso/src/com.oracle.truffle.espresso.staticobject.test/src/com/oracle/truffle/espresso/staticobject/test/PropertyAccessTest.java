/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.staticobject.test;

import com.oracle.truffle.espresso.staticobject.DefaultStaticObject;
import com.oracle.truffle.espresso.staticobject.StaticProperty;
import com.oracle.truffle.espresso.staticobject.StaticPropertyKind;
import com.oracle.truffle.espresso.staticobject.StaticShape;
import com.oracle.truffle.espresso.staticobject.StaticShapeBuilder;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class PropertyAccessTest {
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
        Object get(StaticProperty property, DefaultStaticObject receiver);
    }

    @FunctionalInterface
    interface PropertySetter {
        void set(StaticProperty property, DefaultStaticObject receiver, Object value);
    }

    @BeforeClass
    public static void init() {
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
                                    (byte) 42,
                                    (byte) 0,
                                    (p, obj) -> p.getByte(obj),
                                    (p, obj, val) -> p.setByte(obj, (byte) val));
                    break;
                case Char:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Char,
                                    (char) 42,
                                    (char) 0,
                                    (p, obj) -> p.getChar(obj),
                                    (p, obj, val) -> p.setChar(obj, (char) val));
                    break;
                case Double:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Double,
                                    42D,
                                    0D,
                                    (p, obj) -> p.getDouble(obj),
                                    (p, obj, val) -> p.setDouble(obj, (double) val));
                    break;
                case Float:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Float,
                                    42F,
                                    0F,
                                    (p, obj) -> p.getFloat(obj),
                                    (p, obj, val) -> p.setFloat(obj, (float) val));
                    break;
                case Int:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Int,
                                    42,
                                    0,
                                    (p, obj) -> p.getInt(obj),
                                    (p, obj, val) -> p.setInt(obj, (int) val));
                    break;
                case Long:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Long,
                                    42L,
                                    0L,
                                    (p, obj) -> p.getLong(obj),
                                    (p, obj, val) -> p.setLong(obj, (long) val));
                    break;
                case Short:
                    descriptors[i] = new TestDescriptor(
                                    StaticPropertyKind.Short,
                                    (short) 42,
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

    @Theory
    public void correctAccessors(TestDescriptor descriptor) {
        StaticShapeBuilder builder = StaticShape.newBuilder();
        StaticProperty property = new StaticProperty(descriptor.kind);
        builder.property(property, "property", false);
        StaticShape<DefaultStaticObject.DefaultStaticObjectFactory> shape = builder.build();
        DefaultStaticObject object = shape.getFactory().create();

        // Check the default value
        Object actualValue = descriptor.getter.get(property, object);
        Assert.assertEquals(descriptor.defaultValue, actualValue);
        // Check property accesses
        descriptor.setter.set(property, object, descriptor.testValue);
        actualValue = descriptor.getter.get(property, object);
        Assert.assertEquals(descriptor.testValue, actualValue);
    }

    @Theory
    public void wrongAccessors(TestDescriptor expectedDescriptor, TestDescriptor actualDescriptor) {
        Assume.assumeFalse(expectedDescriptor.equals(actualDescriptor));

        StaticShapeBuilder builder = StaticShape.newBuilder();
        StaticProperty property = new StaticProperty(expectedDescriptor.kind);
        builder.property(property, "property", false);
        StaticShape<DefaultStaticObject.DefaultStaticObjectFactory> shape = builder.build();
        DefaultStaticObject object = shape.getFactory().create();

        // Check that wrong getters throw exceptions
        String expectedExceptionMessage = "Static property of '" + expectedDescriptor.kind.name() + "' kind cannot be accessed as '" + actualDescriptor.kind + "'";
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

    @Test
    public void dummy() {
        // to make sure this file is recognized as a test
    }
}
