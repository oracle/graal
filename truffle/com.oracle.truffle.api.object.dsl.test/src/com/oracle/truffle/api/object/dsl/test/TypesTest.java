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
import com.oracle.truffle.api.object.dsl.Layout;

import java.math.BigInteger;

import org.junit.Assert;

public class TypesTest {

    @Layout
    public interface TypesTestLayout {

        DynamicObject createTypesTest(
                        int i,
                        long l,
                        boolean b,
                        Object o,
                        int[] ia,
                        int[][] iaa,
                        BigInteger bi);

        int getI(DynamicObject object);

        void setI(DynamicObject object, int value);

        long getL(DynamicObject object);

        void setL(DynamicObject object, long value);

        boolean getB(DynamicObject object);

        void setB(DynamicObject object, boolean value);

        Object getO(DynamicObject object);

        void setO(DynamicObject object, Object value);

        int[] getIa(DynamicObject object);

        void setIa(DynamicObject object, int[] value);

        int[][] getIaa(DynamicObject object);

        void setIaa(DynamicObject object, int[][] value);

        BigInteger getBi(DynamicObject object);

        void setBi(DynamicObject object, BigInteger value);

    }

    private static final TypesTestLayout LAYOUT = TypesTestLayoutImpl.INSTANCE;

    private static DynamicObject create() {
        return LAYOUT.createTypesTest(
                        3,
                        4,
                        true,
                        Runtime.getRuntime(),
                        new int[]{14},
                        new int[][]{{1}, {2}},
                        BigInteger.TEN);

    }

    @Test
    public void testInt() {
        final DynamicObject object = create();
        Assert.assertEquals(3, LAYOUT.getI(object));
        LAYOUT.setI(object, 30);
        Assert.assertEquals(30, LAYOUT.getI(object));
    }

    @Test
    public void testLong() {
        final DynamicObject object = create();
        Assert.assertEquals(4, LAYOUT.getL(object));
        LAYOUT.setL(object, 40);
        Assert.assertEquals(40, LAYOUT.getL(object));
    }

    @Test
    public void testBoolean() {
        final DynamicObject object = create();
        Assert.assertEquals(true, LAYOUT.getB(object));
        LAYOUT.setB(object, false);
        Assert.assertEquals(false, LAYOUT.getB(object));
    }

    @Test
    public void testObject() {
        final DynamicObject object = create();
        Assert.assertEquals(Runtime.getRuntime(), LAYOUT.getO(object));
        LAYOUT.setO(object, Object.class);
        Assert.assertEquals(Object.class, LAYOUT.getO(object));
    }

    @Test
    public void testIntArray() {
        final DynamicObject object = create();
        Assert.assertArrayEquals(new int[]{14}, LAYOUT.getIa(object));
        LAYOUT.setIa(object, new int[]{22});
        Assert.assertArrayEquals(new int[]{22}, LAYOUT.getIa(object));
    }

    @Test
    public void testIntArrayArray() {
        final DynamicObject object = create();
        Assert.assertArrayEquals(new int[][]{{1}, {2}}, LAYOUT.getIaa(object));
        LAYOUT.setIaa(object, new int[][]{{11}, {22}});
        Assert.assertArrayEquals(new int[][]{{11}, {22}}, LAYOUT.getIaa(object));
    }

    @Test
    public void testBigInteger() {
        final DynamicObject object = create();
        Assert.assertEquals(BigInteger.TEN, LAYOUT.getBi(object));
        LAYOUT.setBi(object, BigInteger.ONE);
        Assert.assertEquals(BigInteger.ONE, LAYOUT.getBi(object));
    }

}
