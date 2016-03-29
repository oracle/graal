/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.object.dsl.test;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.dsl.Layout;

import java.math.BigInteger;

import org.junit.Assert;

public class InheritanceTest {

    @Layout
    public interface BaseLayout {

        DynamicObject createBase(int a);

        boolean isBase(DynamicObject object);

        boolean isBase(Object object);

        boolean isBase(ObjectType objectType);

        int getA(DynamicObject object);

        void setA(DynamicObject object, int value);

    }

    @Layout
    public interface MiddleLayout extends BaseLayout {

        DynamicObject createMiddle(int a, String b);

        boolean isMiddle(DynamicObject object);

        boolean isMiddle(Object object);

        boolean isMiddle(ObjectType objectType);

        String getB(DynamicObject object);

        void setB(DynamicObject object, String value);

    }

    @Layout
    public interface TopLayout extends MiddleLayout {

        DynamicObject createTop(int a, String b, BigInteger c);

        boolean isTop(DynamicObject object);

        boolean isTop(Object object);

        boolean isTop(ObjectType objectType);

        BigInteger getC(DynamicObject object);

        void setC(DynamicObject object, BigInteger value);

    }

    private static final BaseLayout BASE_LAYOUT = BaseLayoutImpl.INSTANCE;
    private static final MiddleLayout MIDDLE_LAYOUT = MiddleLayoutImpl.INSTANCE;
    private static final TopLayout TOP_LAYOUT = TopLayoutImpl.INSTANCE;

    @Test
    public void testBase() {
        final DynamicObject object = BASE_LAYOUT.createBase(14);
        Assert.assertEquals(14, BASE_LAYOUT.getA(object));
        BASE_LAYOUT.setA(object, 22);
        Assert.assertEquals(22, BASE_LAYOUT.getA(object));
    }

    @Test
    public void testBaseGuardsOnBase() {
        final DynamicObject object = BASE_LAYOUT.createBase(14);
        Assert.assertTrue(BASE_LAYOUT.isBase(object));
        Assert.assertTrue(BASE_LAYOUT.isBase((Object) object));
        Assert.assertTrue(BASE_LAYOUT.isBase(object.getShape().getObjectType()));
        Assert.assertTrue(BASE_LAYOUT.isBase(object));
        Assert.assertTrue(BASE_LAYOUT.isBase((Object) object));
        Assert.assertTrue(BASE_LAYOUT.isBase(object.getShape().getObjectType()));
    }

    @Test
    public void testMiddleGuardsOnBase() {
        final DynamicObject object = BASE_LAYOUT.createBase(14);
        Assert.assertFalse(MIDDLE_LAYOUT.isMiddle(object));
        Assert.assertFalse(MIDDLE_LAYOUT.isMiddle((Object) object));
        Assert.assertFalse(MIDDLE_LAYOUT.isMiddle(object.getShape().getObjectType()));
        Assert.assertFalse(MIDDLE_LAYOUT.isMiddle(object));
        Assert.assertFalse(MIDDLE_LAYOUT.isMiddle((Object) object));
        Assert.assertFalse(MIDDLE_LAYOUT.isMiddle(object.getShape().getObjectType()));
    }

    @Test
    public void testTopGuardsOnBase() {
        final DynamicObject object = BASE_LAYOUT.createBase(14);
        Assert.assertFalse(TOP_LAYOUT.isTop(object));
        Assert.assertFalse(TOP_LAYOUT.isTop((Object) object));
        Assert.assertFalse(TOP_LAYOUT.isTop(object.getShape().getObjectType()));
        Assert.assertFalse(TOP_LAYOUT.isTop(object));
        Assert.assertFalse(TOP_LAYOUT.isTop((Object) object));
        Assert.assertFalse(TOP_LAYOUT.isTop(object.getShape().getObjectType()));
    }

    @Test
    public void testMiddle() {
        final DynamicObject object = MIDDLE_LAYOUT.createMiddle(14, "foo");
        Assert.assertEquals(14, MIDDLE_LAYOUT.getA(object));
        MIDDLE_LAYOUT.setA(object, 22);
        Assert.assertEquals(22, MIDDLE_LAYOUT.getA(object));
        Assert.assertEquals("foo", MIDDLE_LAYOUT.getB(object));
        MIDDLE_LAYOUT.setB(object, "bar");
        Assert.assertEquals("bar", MIDDLE_LAYOUT.getB(object));
    }

    @Test
    public void testBaseOperationsOnMiddle() {
        final DynamicObject object = MIDDLE_LAYOUT.createMiddle(14, "foo");
        Assert.assertEquals(14, BASE_LAYOUT.getA(object));
        BASE_LAYOUT.setA(object, 22);
        Assert.assertEquals(22, BASE_LAYOUT.getA(object));
    }

    @Test
    public void testBaseGuardsOnMiddle() {
        final DynamicObject object = MIDDLE_LAYOUT.createMiddle(14, "foo");
        Assert.assertTrue(BASE_LAYOUT.isBase(object));
        Assert.assertTrue(BASE_LAYOUT.isBase((Object) object));
        Assert.assertTrue(BASE_LAYOUT.isBase(object.getShape().getObjectType()));
        Assert.assertTrue(BASE_LAYOUT.isBase(object));
        Assert.assertTrue(BASE_LAYOUT.isBase((Object) object));
        Assert.assertTrue(BASE_LAYOUT.isBase(object.getShape().getObjectType()));
    }

    @Test
    public void testMiddleGuardsOnMiddle() {
        final DynamicObject object = MIDDLE_LAYOUT.createMiddle(14, "foo");
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle((Object) object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object.getShape().getObjectType()));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle((Object) object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object.getShape().getObjectType()));
    }

    @Test
    public void testTopGuardsOnMiddle() {
        final DynamicObject object = MIDDLE_LAYOUT.createMiddle(14, "foo");
        Assert.assertFalse(TOP_LAYOUT.isTop(object));
        Assert.assertFalse(TOP_LAYOUT.isTop((Object) object));
        Assert.assertFalse(TOP_LAYOUT.isTop(object.getShape().getObjectType()));
        Assert.assertFalse(TOP_LAYOUT.isTop(object));
        Assert.assertFalse(TOP_LAYOUT.isTop((Object) object));
        Assert.assertFalse(TOP_LAYOUT.isTop(object.getShape().getObjectType()));
    }

    @Test
    public void testTop() {
        final DynamicObject object = TOP_LAYOUT.createTop(14, "foo", BigInteger.TEN);
        Assert.assertEquals(14, TOP_LAYOUT.getA(object));
        BASE_LAYOUT.setA(object, 22);
        Assert.assertEquals(22, BASE_LAYOUT.getA(object));
        Assert.assertEquals("foo", TOP_LAYOUT.getB(object));
        MIDDLE_LAYOUT.setB(object, "bar");
        Assert.assertEquals("bar", MIDDLE_LAYOUT.getB(object));
        Assert.assertEquals(BigInteger.TEN, TOP_LAYOUT.getC(object));
        TOP_LAYOUT.setC(object, BigInteger.ONE);
        Assert.assertEquals(BigInteger.ONE, TOP_LAYOUT.getC(object));
    }

    @Test
    public void testBaseOperationsOnTop() {
        final DynamicObject object = TOP_LAYOUT.createTop(14, "foo", BigInteger.TEN);
        Assert.assertEquals(14, BASE_LAYOUT.getA(object));
        BASE_LAYOUT.setA(object, 22);
        Assert.assertEquals(22, BASE_LAYOUT.getA(object));
    }

    @Test
    public void testMiddleOperationsOnTop() {
        final DynamicObject object = TOP_LAYOUT.createTop(14, "foo", BigInteger.TEN);
        Assert.assertEquals(14, MIDDLE_LAYOUT.getA(object));
        MIDDLE_LAYOUT.setA(object, 22);
        Assert.assertEquals(22, MIDDLE_LAYOUT.getA(object));
        Assert.assertEquals("foo", MIDDLE_LAYOUT.getB(object));
        MIDDLE_LAYOUT.setB(object, "bar");
        Assert.assertEquals("bar", MIDDLE_LAYOUT.getB(object));
    }

    @Test
    public void testBaseGuardsOnTop() {
        final DynamicObject object = TOP_LAYOUT.createTop(14, "foo", BigInteger.TEN);
        Assert.assertTrue(BASE_LAYOUT.isBase(object));
        Assert.assertTrue(BASE_LAYOUT.isBase((Object) object));
        Assert.assertTrue(BASE_LAYOUT.isBase(object.getShape().getObjectType()));
        Assert.assertTrue(BASE_LAYOUT.isBase(object));
        Assert.assertTrue(BASE_LAYOUT.isBase((Object) object));
        Assert.assertTrue(BASE_LAYOUT.isBase(object.getShape().getObjectType()));
    }

    @Test
    public void testMiddleGuardsOnTop() {
        final DynamicObject object = TOP_LAYOUT.createTop(14, "foo", BigInteger.TEN);
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle((Object) object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object.getShape().getObjectType()));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle((Object) object));
        Assert.assertTrue(MIDDLE_LAYOUT.isMiddle(object.getShape().getObjectType()));
    }

    @Test
    public void testTopGuardsOnTop() {
        final DynamicObject object = TOP_LAYOUT.createTop(14, "foo", BigInteger.TEN);
        Assert.assertTrue(TOP_LAYOUT.isTop(object));
        Assert.assertTrue(TOP_LAYOUT.isTop((Object) object));
        Assert.assertTrue(TOP_LAYOUT.isTop(object.getShape().getObjectType()));
        Assert.assertTrue(TOP_LAYOUT.isTop(object));
        Assert.assertTrue(TOP_LAYOUT.isTop((Object) object));
        Assert.assertTrue(TOP_LAYOUT.isTop(object.getShape().getObjectType()));
    }

}
