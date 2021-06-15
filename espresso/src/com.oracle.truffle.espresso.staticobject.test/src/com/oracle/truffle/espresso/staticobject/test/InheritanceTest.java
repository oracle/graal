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

public class InheritanceTest extends StaticObjectTest {
    public static class CustomStaticObject {
        public byte field1;
        public boolean field2;
    }

    public interface CustomStaticObjectFactory {
        CustomStaticObject create();
    }

    @Test
    public void baseClassInheritance() throws NoSuchFieldException {
        StaticShape.Builder builder = StaticShape.newBuilder(this);
        StaticProperty property = new DefaultStaticProperty("field1", StaticPropertyKind.Int, false);
        builder.property(property);
        StaticShape<CustomStaticObjectFactory> shape = builder.build(CustomStaticObject.class, CustomStaticObjectFactory.class);
        CustomStaticObject object = shape.getFactory().create();

        // Set to the field declared in the super class
        object.field1 = 42;
        // Set to the field declared in the generated class
        property.setInt(object, 24);
        // Get the value of the field declared in the super class
        Assert.assertEquals(42, object.field1);
        // Get the value of the field declared in the generated class
        Assert.assertEquals(24, property.getInt(object));

        Assume.assumeFalse(ARRAY_BASED_STORAGE);
        // `CustomStaticObject.field1` is shadowed
        Assert.assertEquals(int.class, object.getClass().getField("field1").getType());
        // `CustomStaticObject.field2` is visible
        Assert.assertEquals(boolean.class, object.getClass().getField("field2").getType());
    }

    @Test
    public void baseShapeInheritance() throws NoSuchFieldException, IllegalAccessException {
        StaticShape.Builder b1 = StaticShape.newBuilder(this);
        StaticProperty s1p1 = new DefaultStaticProperty("field1", StaticPropertyKind.Int, false);
        StaticProperty s1p2 = new DefaultStaticProperty("field2", StaticPropertyKind.Int, false);
        b1.property(s1p1);
        b1.property(s1p2);
        // StaticShape s1 declares 2 properties: s1p1 and s1p2
        StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

        StaticShape.Builder b2 = StaticShape.newBuilder(this);
        StaticProperty s2p1 = new DefaultStaticProperty("field1", StaticPropertyKind.Int, false);
        b2.property(s2p1);
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

        Assume.assumeFalse(ARRAY_BASED_STORAGE);
        Assert.assertEquals(3, object.getClass().getField("field1").getInt(object));
    }
}
