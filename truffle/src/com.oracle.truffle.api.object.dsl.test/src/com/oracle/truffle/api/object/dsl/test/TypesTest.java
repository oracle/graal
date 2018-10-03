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
