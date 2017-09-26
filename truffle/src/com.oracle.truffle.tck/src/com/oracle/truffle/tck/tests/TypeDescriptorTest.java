/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import java.util.Arrays;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.junit.Assert;
import org.junit.Test;

public class TypeDescriptorTest {

    private static final TypeDescriptor[] PREDEFINED = new TypeDescriptor[]{
                    TypeDescriptor.ARRAY,
                    TypeDescriptor.BOOLEAN,
                    TypeDescriptor.HOST_OBJECT,
                    TypeDescriptor.NATIVE_POINTER,
                    TypeDescriptor.NULL,
                    TypeDescriptor.NUMBER,
                    TypeDescriptor.OBJECT,
                    TypeDescriptor.STRING
    };

    @Test
    public void testCreate() {
        TypeDescriptor t = TypeDescriptor.union(TypeDescriptor.array(TypeDescriptor.STRING), TypeDescriptor.array(TypeDescriptor.NUMBER));
        Assert.assertEquals(
                        TypeDescriptor.array(TypeDescriptor.union(TypeDescriptor.STRING, TypeDescriptor.NUMBER)),
                        t);
        t = TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.array(TypeDescriptor.STRING), TypeDescriptor.array(TypeDescriptor.NUMBER));
        Assert.assertEquals(
                        TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.array(TypeDescriptor.union(TypeDescriptor.STRING, TypeDescriptor.NUMBER))),
                        t);
        t = TypeDescriptor.union(TypeDescriptor.ARRAY, TypeDescriptor.array(TypeDescriptor.NUMBER));
        Assert.assertEquals(TypeDescriptor.ARRAY, t);
    }

    @Test
    public void testNarrowPrimitive() {
        for (TypeDescriptor td1 : PREDEFINED) {
            for (TypeDescriptor td2 : PREDEFINED) {
                Assert.assertTrue(td1 == td2 || td1.narrow(td2) == null);
            }
        }
    }

    @Test
    public void testNarrowArray() {
        final TypeDescriptor numArray = TypeDescriptor.array(TypeDescriptor.NUMBER);
        final TypeDescriptor strArray = TypeDescriptor.array(TypeDescriptor.STRING);
        final TypeDescriptor numArrayArray = TypeDescriptor.array(TypeDescriptor.array(TypeDescriptor.NUMBER));

        for (TypeDescriptor td : PREDEFINED) {
            Assert.assertNull(numArray.narrow(td));
            Assert.assertNull(strArray.narrow(td));
            Assert.assertNull(numArrayArray.narrow(td));
        }

        Arrays.stream(PREDEFINED).filter((td) -> td != TypeDescriptor.ARRAY).forEach((td) -> {
            Assert.assertNull(td.narrow(numArray));
            Assert.assertNull(td.narrow(strArray));
            Assert.assertNull(td.narrow(numArrayArray));
        });
        Assert.assertEquals(numArray, TypeDescriptor.ARRAY.narrow(numArray));
        Assert.assertEquals(strArray, TypeDescriptor.ARRAY.narrow(strArray));
        Assert.assertEquals(numArrayArray, TypeDescriptor.ARRAY.narrow(numArrayArray));

        Assert.assertNull(numArray.narrow(strArray));
        Assert.assertNull(numArray.narrow(numArrayArray));
        Assert.assertNull(strArray.narrow(numArray));
        Assert.assertNull(strArray.narrow(numArrayArray));
        Assert.assertNull(numArrayArray.narrow(numArray));
        Assert.assertNull(numArrayArray.narrow(strArray));
        Assert.assertEquals(numArray, numArray.narrow(numArray));
        Assert.assertEquals(strArray, strArray.narrow(strArray));
        Assert.assertEquals(numArrayArray, numArrayArray.narrow(numArrayArray));

        final TypeDescriptor objOrArrayNum = TypeDescriptor.union(
                        TypeDescriptor.OBJECT,
                        numArray);
        Assert.assertEquals(numArray, TypeDescriptor.ARRAY.narrow(objOrArrayNum));
    }

    @Test
    public void testNarrowUnion() {
        final TypeDescriptor numOrBool = TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.BOOLEAN);
        final TypeDescriptor numOrBoolOrStr = TypeDescriptor.union(numOrBool, TypeDescriptor.STRING);
        Arrays.stream(PREDEFINED).filter((td) -> td != TypeDescriptor.NUMBER && td != TypeDescriptor.BOOLEAN).forEach((td) -> {
            Assert.assertNull(td.narrow(numOrBool));
            Assert.assertNull(numOrBool.narrow(td));
        });

        Assert.assertEquals(TypeDescriptor.BOOLEAN, numOrBool.narrow(TypeDescriptor.BOOLEAN));
        Assert.assertEquals(TypeDescriptor.BOOLEAN, numOrBoolOrStr.narrow(TypeDescriptor.BOOLEAN));
        Assert.assertEquals(TypeDescriptor.BOOLEAN, TypeDescriptor.BOOLEAN.narrow(numOrBool));
        Assert.assertEquals(TypeDescriptor.BOOLEAN, TypeDescriptor.BOOLEAN.narrow(numOrBoolOrStr));
        Assert.assertEquals(TypeDescriptor.NUMBER, numOrBool.narrow(TypeDescriptor.NUMBER));
        Assert.assertEquals(TypeDescriptor.NUMBER, numOrBoolOrStr.narrow(TypeDescriptor.NUMBER));
        Assert.assertEquals(TypeDescriptor.NUMBER, TypeDescriptor.NUMBER.narrow(numOrBool));
        Assert.assertEquals(TypeDescriptor.NUMBER, TypeDescriptor.NUMBER.narrow(numOrBoolOrStr));
        Assert.assertEquals(TypeDescriptor.STRING, numOrBoolOrStr.narrow(TypeDescriptor.STRING));
        Assert.assertEquals(TypeDescriptor.STRING, TypeDescriptor.STRING.narrow(numOrBoolOrStr));

        Assert.assertEquals(numOrBool, numOrBoolOrStr.narrow(numOrBool));
        Assert.assertEquals(numOrBool, numOrBool.narrow(numOrBoolOrStr));

        final TypeDescriptor arrNumberOrBool = TypeDescriptor.union(
                        TypeDescriptor.array(TypeDescriptor.NUMBER),
                        TypeDescriptor.BOOLEAN);
        final TypeDescriptor arrNumberOrString = TypeDescriptor.union(
                        TypeDescriptor.array(TypeDescriptor.NUMBER),
                        TypeDescriptor.STRING);
        final TypeDescriptor arrBoolOrString = TypeDescriptor.union(
                        TypeDescriptor.array(TypeDescriptor.BOOLEAN),
                        TypeDescriptor.STRING);
        Assert.assertEquals(TypeDescriptor.array(TypeDescriptor.NUMBER),
                        arrNumberOrBool.narrow(arrNumberOrString));
        Assert.assertNull(arrNumberOrBool.narrow(arrBoolOrString));

        final TypeDescriptor arrNumBool = TypeDescriptor.array(numOrBool);
        final TypeDescriptor arrNum = TypeDescriptor.array(TypeDescriptor.NUMBER);
        final TypeDescriptor numOrBoolOrArrNumBool = TypeDescriptor.union(numOrBool, arrNumBool);
        Assert.assertEquals(arrNum,
                        numOrBoolOrArrNumBool.narrow(arrNum));

        final TypeDescriptor objOrArrNum = TypeDescriptor.union(TypeDescriptor.OBJECT, arrNum);
        Assert.assertEquals(arrNum,
                        numOrBoolOrArrNumBool.narrow(objOrArrNum));
    }
}
