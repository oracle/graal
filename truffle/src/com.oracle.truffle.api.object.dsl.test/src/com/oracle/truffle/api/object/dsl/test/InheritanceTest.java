/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
