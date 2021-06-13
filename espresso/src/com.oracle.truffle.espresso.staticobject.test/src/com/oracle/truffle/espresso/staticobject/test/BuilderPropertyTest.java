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

import com.oracle.truffle.espresso.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.espresso.staticobject.DefaultStaticProperty;
import com.oracle.truffle.espresso.staticobject.StaticProperty;
import com.oracle.truffle.espresso.staticobject.StaticPropertyKind;
import com.oracle.truffle.espresso.staticobject.StaticShape;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BuilderPropertyTest extends StaticObjectTest {
    @Test
    public void sameBuilderSameProperty() {
        StaticShape.Builder builder = StaticShape.newBuilder(this);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        builder.property(property);
        try {
            // You cannot add the same property twice
            builder.property(property);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // You cannot add the same property twice
            Assert.assertEquals("This builder already contains a property with id 'property'", e.getMessage());
        }
    }

    @Test
    public void sameBuilderSameName() throws IllegalArgumentException {
        StaticShape.Builder builder = StaticShape.newBuilder(this);
        StaticProperty p1 = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        StaticProperty p2 = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        builder.property(p1);
        try {
            // You cannot add two properties with the same name
            builder.property(p2);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("This builder already contains a property with id 'property'", e.getMessage());
        }
    }

    @Test
    public void differentBuildersSameProperty() {
        StaticShape.Builder b1 = StaticShape.newBuilder(this);
        StaticShape.Builder b2 = StaticShape.newBuilder(this);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        b1.property(property);
        b2.property(property);
        b1.build();
        try {
            // You cannot build shapes that share properties
            b2.build();
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertEquals("Attempt to reinitialize the offset of static property 'property' of kind 'Int'.\nWas it added to more than one builder or multiple times to the same builder?",
                            e.getMessage());
        }
    }

    @Test
    public void propertyName() throws NoSuchFieldException {
        Assume.assumeFalse(ARRAY_BASED_STORAGE);

        StaticShape.Builder builder = StaticShape.newBuilder(this);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        builder.property(property);
        StaticShape<DefaultStaticObjectFactory> shape = builder.build();
        Object object = shape.getFactory().create();
        object.getClass().getField(guessGeneratedFieldName(property));
    }

    @Test
    public void propertyFinal() throws NoSuchFieldException {
        Assume.assumeFalse(ARRAY_BASED_STORAGE);

        StaticShape.Builder builder = StaticShape.newBuilder(this);
        StaticProperty p1 = new DefaultStaticProperty("p1", StaticPropertyKind.Int, true);
        StaticProperty p2 = new DefaultStaticProperty("p2", StaticPropertyKind.Int, false);
        builder.property(p1);
        builder.property(p2);
        StaticShape<DefaultStaticObjectFactory> shape = builder.build();
        Object object = shape.getFactory().create();
        Field f1 = object.getClass().getField(guessGeneratedFieldName(p1));
        Field f2 = object.getClass().getField(guessGeneratedFieldName(p2));
        Assert.assertTrue(Modifier.isFinal(f1.getModifiers()));
        Assert.assertFalse(Modifier.isFinal(f2.getModifiers()));
    }

    @Test
    public void propertyKind() throws NoSuchFieldException {
        Assume.assumeFalse(ARRAY_BASED_STORAGE);

        StaticShape.Builder builder = StaticShape.newBuilder(this);
        StaticPropertyKind[] kinds = StaticPropertyKind.values();
        StaticProperty[] properties = new StaticProperty[kinds.length];
        for (int i = 0; i < properties.length; i++) {
            properties[i] = new DefaultStaticProperty(kinds[i].name(), kinds[i], false);
            builder.property(properties[i]);
        }
        StaticShape<DefaultStaticObjectFactory> shape = builder.build();
        Object object = shape.getFactory().create();
        for (int i = 0; i < properties.length; i++) {
            Class<?> expectedType;
            switch (kinds[i]) {
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
                    Assert.fail("Unexpected type: " + kinds[i]);
            }
            Assert.assertEquals(expectedType, object.getClass().getField(guessGeneratedFieldName(properties[i])).getType());
        }
    }
}
