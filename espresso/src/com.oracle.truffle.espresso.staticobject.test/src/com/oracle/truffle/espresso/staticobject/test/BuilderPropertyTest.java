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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BuilderPropertyTest {
    @Test
    public void sameBuilderSameProperty() {
        StaticShape.Builder builder = StaticShape.newBuilder();
        StaticProperty property = new StaticProperty(StaticPropertyKind.Int);
        builder.property(property, "p1", false);
        try {
            // You cannot add the same property twice
            builder.property(property, "p2", false);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("This builder already contains this property", e.getMessage());
        }
    }

    @Test
    public void sameBuilderSameName() throws IllegalArgumentException {
        StaticShape.Builder builder = StaticShape.newBuilder();
        StaticProperty p1 = new StaticProperty(StaticPropertyKind.Int);
        StaticProperty p2 = new StaticProperty(StaticPropertyKind.Int);
        builder.property(p1, "p1", false);
        try {
            // You cannot add two properties with the same name
            builder.property(p2, "p1", false);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("This builder already contains a property named 'p1'", e.getMessage());
        }
    }

    @Test
    public void differentBuildersSameProperty() {
        StaticShape.Builder b1 = StaticShape.newBuilder();
        StaticShape.Builder b2 = StaticShape.newBuilder();
        StaticProperty property = new StaticProperty(StaticPropertyKind.Int);
        b1.property(property, "p1", false);
        b2.property(property, "p2", false);
        b1.build();
        try {
            // You cannot build shapes that share properties
            b2.build();
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertEquals("Attempt to reinitialize the offset of a static property. Was it added to more than one builder?", e.getMessage());
        }
    }

    @Test
    public void propertyName() throws NoSuchFieldException {
        Assume.assumeFalse(StorageLayout.ARRAY_BASED);

        StaticShape.Builder builder = StaticShape.newBuilder();
        StaticProperty property = new StaticProperty(StaticPropertyKind.Int);
        String propertyName = "p1";
        builder.property(property, propertyName, false);
        StaticShape<DefaultStaticObject.Factory> shape = builder.build();
        DefaultStaticObject object = shape.getFactory().create();
        Field field = object.getClass().getField(propertyName);
        Assert.assertEquals(propertyName, field.getName());
    }

    @Test
    public void propertyFinal() throws NoSuchFieldException {
        Assume.assumeFalse(StorageLayout.ARRAY_BASED);

        StaticShape.Builder builder = StaticShape.newBuilder();
        StaticProperty p1 = new StaticProperty(StaticPropertyKind.Int);
        StaticProperty p2 = new StaticProperty(StaticPropertyKind.Int);
        String p1Name = "p1";
        String p2Name = "p2";
        builder.property(p1, p1Name, true);
        builder.property(p2, p2Name, false);
        StaticShape<DefaultStaticObject.Factory> shape = builder.build();
        DefaultStaticObject object = shape.getFactory().create();
        Field f1 = object.getClass().getField(p1Name);
        Field f2 = object.getClass().getField(p2Name);
        Assert.assertTrue(Modifier.isFinal(f1.getModifiers()));
        Assert.assertFalse(Modifier.isFinal(f2.getModifiers()));
    }

    @Test
    public void propertyKind() throws NoSuchFieldException {
        Assume.assumeFalse(StorageLayout.ARRAY_BASED);

        StaticShape.Builder builder = StaticShape.newBuilder();
        for (StaticPropertyKind kind : StaticPropertyKind.values()) {
            builder.property(new StaticProperty(kind), kind.name(), false);
        }
        StaticShape<DefaultStaticObject.Factory> shape = builder.build();
        DefaultStaticObject object = shape.getFactory().create();
        for (StaticPropertyKind kind : StaticPropertyKind.values()) {
            Class<?> expectedType;
            switch (kind) {
                case Boolean:
                    expectedType = boolean.class;
                    break;
                case Byte:
                    expectedType = byte.class;
                    break;
                case Char:
                    expectedType = char.class;
                    break;
                case Double:
                    expectedType = double.class;
                    break;
                case Float:
                    expectedType = float.class;
                    break;
                case Int:
                    expectedType = int.class;
                    break;
                case Long:
                    expectedType = long.class;
                    break;
                case Object:
                    expectedType = Object.class;
                    break;
                case Short:
                    expectedType = short.class;
                    break;
                default:
                    expectedType = null;
                    Assert.fail("Unexpected type: " + kind);
            }
            Assert.assertEquals(expectedType, object.getClass().getField(kind.name()).getType());
        }
    }
}
