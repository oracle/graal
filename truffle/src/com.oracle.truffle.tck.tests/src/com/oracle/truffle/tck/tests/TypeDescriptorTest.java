/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                    TypeDescriptor.STRING,
                    TypeDescriptor.EXECUTABLE,
                    TypeDescriptor.EXECUTABLE_ANY
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
    public void testPrimitive() {
        for (TypeDescriptor td1 : PREDEFINED) {
            for (TypeDescriptor td2 : PREDEFINED) {
                Assert.assertTrue(td1 == td2 || td1 == TypeDescriptor.EXECUTABLE_ANY && td2 == TypeDescriptor.EXECUTABLE || !td1.isAssignable(td2));
            }
        }
    }

    @Test
    public void testArray() {
        final TypeDescriptor numArray = TypeDescriptor.array(TypeDescriptor.NUMBER);
        final TypeDescriptor strArray = TypeDescriptor.array(TypeDescriptor.STRING);
        final TypeDescriptor numArrayArray = TypeDescriptor.array(TypeDescriptor.array(TypeDescriptor.NUMBER));

        for (TypeDescriptor td : PREDEFINED) {
            Assert.assertFalse(numArray.isAssignable(td));
            Assert.assertFalse(strArray.isAssignable(td));
            Assert.assertFalse(numArrayArray.isAssignable(td));
        }

        for (TypeDescriptor td : PREDEFINED) {
            Assert.assertFalse(td != TypeDescriptor.ARRAY && td.isAssignable(numArray));
            Assert.assertFalse(td != TypeDescriptor.ARRAY && td.isAssignable(strArray));
            Assert.assertFalse(td != TypeDescriptor.ARRAY && td.isAssignable(numArrayArray));
        }
        Assert.assertTrue(TypeDescriptor.ARRAY.isAssignable(numArray));
        Assert.assertTrue(TypeDescriptor.ARRAY.isAssignable(strArray));
        Assert.assertTrue(TypeDescriptor.ARRAY.isAssignable(numArrayArray));

        Assert.assertFalse(numArray.isAssignable(strArray));
        Assert.assertFalse(numArray.isAssignable(numArrayArray));
        Assert.assertFalse(strArray.isAssignable(numArray));
        Assert.assertFalse(strArray.isAssignable(numArrayArray));
        Assert.assertFalse(numArrayArray.isAssignable(numArray));
        Assert.assertFalse(numArrayArray.isAssignable(strArray));
        Assert.assertTrue(numArray.isAssignable(numArray));
        Assert.assertTrue(strArray.isAssignable(strArray));
        Assert.assertTrue(numArrayArray.isAssignable(numArrayArray));

        final TypeDescriptor objOrArrayNum = TypeDescriptor.union(
                        TypeDescriptor.OBJECT,
                        numArray);
        Assert.assertFalse(numArray.isAssignable(objOrArrayNum));
        Assert.assertTrue(objOrArrayNum.isAssignable(numArray));
    }

    @Test
    public void testUnion() {
        final TypeDescriptor numOrBool = TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.BOOLEAN);
        final TypeDescriptor numOrBoolOrStr = TypeDescriptor.union(numOrBool, TypeDescriptor.STRING);
        for (TypeDescriptor td : PREDEFINED) {
            Assert.assertFalse(td != TypeDescriptor.NUMBER && td != TypeDescriptor.BOOLEAN && td.isAssignable(numOrBool));
            Assert.assertFalse(td != TypeDescriptor.NUMBER && td != TypeDescriptor.BOOLEAN && numOrBool.isAssignable(td));
        }

        Assert.assertTrue(numOrBool.isAssignable(TypeDescriptor.BOOLEAN));
        Assert.assertTrue(numOrBoolOrStr.isAssignable(TypeDescriptor.BOOLEAN));
        Assert.assertFalse(TypeDescriptor.BOOLEAN.isAssignable(numOrBool));
        Assert.assertFalse(TypeDescriptor.BOOLEAN.isAssignable(numOrBoolOrStr));
        Assert.assertTrue(numOrBool.isAssignable(TypeDescriptor.NUMBER));
        Assert.assertTrue(numOrBoolOrStr.isAssignable(TypeDescriptor.NUMBER));
        Assert.assertFalse(TypeDescriptor.NUMBER.isAssignable(numOrBool));
        Assert.assertFalse(TypeDescriptor.NUMBER.isAssignable(numOrBoolOrStr));
        Assert.assertTrue(numOrBoolOrStr.isAssignable(TypeDescriptor.STRING));
        Assert.assertFalse(TypeDescriptor.STRING.isAssignable(numOrBoolOrStr));

        Assert.assertTrue(numOrBoolOrStr.isAssignable(numOrBool));
        Assert.assertFalse(numOrBool.isAssignable(numOrBoolOrStr));

        final TypeDescriptor arrNumberOrBool = TypeDescriptor.union(
                        TypeDescriptor.array(TypeDescriptor.NUMBER),
                        TypeDescriptor.BOOLEAN);
        final TypeDescriptor arrNumberOrString = TypeDescriptor.union(
                        TypeDescriptor.array(TypeDescriptor.NUMBER),
                        TypeDescriptor.STRING);
        final TypeDescriptor arrBoolOrString = TypeDescriptor.union(
                        TypeDescriptor.array(TypeDescriptor.BOOLEAN),
                        TypeDescriptor.STRING);
        final TypeDescriptor arrNumberOrBoolOrStr = TypeDescriptor.union(
                        TypeDescriptor.array(TypeDescriptor.NUMBER),
                        TypeDescriptor.BOOLEAN,
                        TypeDescriptor.STRING);
        Assert.assertFalse(arrNumberOrBool.isAssignable(arrNumberOrString));
        Assert.assertFalse(arrNumberOrBool.isAssignable(arrBoolOrString));
        Assert.assertTrue(arrNumberOrBoolOrStr.isAssignable(arrNumberOrString));

        final TypeDescriptor arrNumBool = TypeDescriptor.array(numOrBool);
        final TypeDescriptor arrNum = TypeDescriptor.array(TypeDescriptor.NUMBER);
        final TypeDescriptor numOrBoolOrArrNumBool = TypeDescriptor.union(numOrBool, arrNumBool);
        Assert.assertTrue(numOrBoolOrArrNumBool.isAssignable(arrNum));
        final TypeDescriptor objOrArrNum = TypeDescriptor.union(TypeDescriptor.OBJECT, arrNum);
        final TypeDescriptor boolOrArrNum = TypeDescriptor.union(TypeDescriptor.BOOLEAN, arrNum);
        Assert.assertFalse(numOrBoolOrArrNumBool.isAssignable(objOrArrNum));
        Assert.assertTrue(numOrBoolOrArrNumBool.isAssignable(boolOrArrNum));
    }

    @Test
    public void testExecutable() {
        final TypeDescriptor exeBottom = TypeDescriptor.EXECUTABLE;
        final TypeDescriptor exeTop = TypeDescriptor.EXECUTABLE_ANY;
        final TypeDescriptor exeAnyNoArgs = TypeDescriptor.executable(TypeDescriptor.ANY);
        final TypeDescriptor exeAnyStr = TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.STRING);
        final TypeDescriptor exeAnyStrNum = TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.STRING, TypeDescriptor.NUMBER);
        final TypeDescriptor exeStrNoArgs = TypeDescriptor.executable(TypeDescriptor.STRING);
        final TypeDescriptor exeStrStr = TypeDescriptor.executable(TypeDescriptor.STRING, TypeDescriptor.STRING);
        final TypeDescriptor exeAnyUnionUnion = TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.STRING),
                        TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.OBJECT));
        final List<TypeDescriptor> eds = new ArrayList<>();
        Collections.addAll(eds, exeBottom, exeAnyNoArgs, exeAnyStr, exeAnyStrNum, exeStrNoArgs, exeStrStr, exeAnyUnionUnion);
        final List<TypeDescriptor> otherTypes = new ArrayList<>();
        Collections.addAll(otherTypes, PREDEFINED);
        otherTypes.remove(TypeDescriptor.EXECUTABLE);
        otherTypes.remove(TypeDescriptor.EXECUTABLE_ANY);
        otherTypes.add(TypeDescriptor.array(TypeDescriptor.BOOLEAN));
        otherTypes.add(TypeDescriptor.union(TypeDescriptor.BOOLEAN, TypeDescriptor.NUMBER));
        for (TypeDescriptor td : otherTypes) {
            for (TypeDescriptor ed : eds) {
                Assert.assertFalse(ed.isAssignable(td));
                Assert.assertFalse(td.isAssignable(ed));
            }
        }
        Assert.assertTrue(exeTop.isAssignable(exeTop));
        Assert.assertTrue(exeTop.isAssignable(exeBottom));
        Assert.assertTrue(exeTop.isAssignable(exeAnyNoArgs));
        Assert.assertTrue(exeTop.isAssignable(exeAnyStr));
        Assert.assertTrue(exeTop.isAssignable(exeAnyStrNum));
        Assert.assertTrue(exeTop.isAssignable(exeStrNoArgs));
        Assert.assertTrue(exeTop.isAssignable(exeStrStr));
        Assert.assertTrue(exeTop.isAssignable(exeAnyUnionUnion));
        Assert.assertFalse(exeBottom.isAssignable(exeTop));
        Assert.assertTrue(exeBottom.isAssignable(exeAnyNoArgs));
        Assert.assertFalse(exeBottom.isAssignable(exeAnyStr));
        Assert.assertFalse(exeBottom.isAssignable(exeAnyStrNum));
        Assert.assertTrue(exeBottom.isAssignable(exeStrNoArgs));
        Assert.assertFalse(exeBottom.isAssignable(exeStrStr));
        Assert.assertFalse(exeBottom.isAssignable(exeAnyUnionUnion));
        Assert.assertFalse(exeAnyNoArgs.isAssignable(exeTop));
        Assert.assertTrue(exeAnyNoArgs.isAssignable(exeBottom));
        Assert.assertFalse(exeAnyNoArgs.isAssignable(exeAnyStr));
        Assert.assertFalse(exeAnyNoArgs.isAssignable(exeAnyStrNum));
        Assert.assertTrue(exeAnyNoArgs.isAssignable(exeStrNoArgs));
        Assert.assertFalse(exeAnyNoArgs.isAssignable(exeStrStr));
        Assert.assertFalse(exeAnyNoArgs.isAssignable(exeAnyUnionUnion));
        Assert.assertFalse(exeAnyStr.isAssignable(exeTop));
        Assert.assertTrue(exeAnyStr.isAssignable(exeBottom));
        Assert.assertTrue(exeAnyStr.isAssignable(exeAnyNoArgs));
        Assert.assertFalse(exeAnyStr.isAssignable(exeAnyStrNum));
        Assert.assertTrue(exeAnyStr.isAssignable(exeStrNoArgs));
        Assert.assertTrue(exeAnyStr.isAssignable(exeStrStr));
        Assert.assertFalse(exeAnyStr.isAssignable(exeAnyUnionUnion));
        Assert.assertFalse(exeAnyStrNum.isAssignable(exeTop));
        Assert.assertTrue(exeAnyStrNum.isAssignable(exeBottom));
        Assert.assertTrue(exeAnyStrNum.isAssignable(exeAnyNoArgs));
        Assert.assertTrue(exeAnyStrNum.isAssignable(exeAnyStr));
        Assert.assertTrue(exeAnyStrNum.isAssignable(exeStrNoArgs));
        Assert.assertTrue(exeAnyStrNum.isAssignable(exeStrStr));
        Assert.assertTrue(exeAnyStrNum.isAssignable(exeAnyUnionUnion));
        Assert.assertFalse(exeStrNoArgs.isAssignable(exeTop));
        Assert.assertFalse(exeStrNoArgs.isAssignable(exeBottom));
        Assert.assertFalse(exeStrNoArgs.isAssignable(exeAnyNoArgs));
        Assert.assertFalse(exeStrNoArgs.isAssignable(exeAnyStr));
        Assert.assertFalse(exeStrNoArgs.isAssignable(exeAnyStrNum));
        Assert.assertFalse(exeStrNoArgs.isAssignable(exeStrStr));
        Assert.assertFalse(exeStrNoArgs.isAssignable(exeAnyUnionUnion));
        Assert.assertFalse(exeStrStr.isAssignable(exeTop));
        Assert.assertFalse(exeStrStr.isAssignable(exeBottom));
        Assert.assertFalse(exeStrStr.isAssignable(exeAnyNoArgs));
        Assert.assertFalse(exeStrStr.isAssignable(exeAnyStr));
        Assert.assertFalse(exeStrStr.isAssignable(exeAnyStrNum));
        Assert.assertTrue(exeStrStr.isAssignable(exeStrNoArgs));
        Assert.assertFalse(exeStrStr.isAssignable(exeAnyUnionUnion));
        Assert.assertFalse(exeAnyUnionUnion.isAssignable(exeTop));
        Assert.assertTrue(exeAnyUnionUnion.isAssignable(exeBottom));
        Assert.assertTrue(exeAnyUnionUnion.isAssignable(exeAnyNoArgs));
        Assert.assertFalse(exeAnyUnionUnion.isAssignable(exeAnyStr));
        Assert.assertFalse(exeAnyUnionUnion.isAssignable(exeAnyStrNum));
        Assert.assertTrue(exeAnyUnionUnion.isAssignable(exeStrNoArgs));
        Assert.assertFalse(exeAnyUnionUnion.isAssignable(exeStrStr));
        // Arrays
        final TypeDescriptor ae1 = TypeDescriptor.array(TypeDescriptor.EXECUTABLE);
        final TypeDescriptor ae2 = TypeDescriptor.array(TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.BOOLEAN));
        final TypeDescriptor ae3 = TypeDescriptor.array(TypeDescriptor.EXECUTABLE_ANY);
        final TypeDescriptor ab = TypeDescriptor.array(TypeDescriptor.BOOLEAN);
        Assert.assertFalse(ae1.isAssignable(ae2));
        Assert.assertFalse(ae1.isAssignable(ae3));
        Assert.assertFalse(ae1.isAssignable(ab));
        Assert.assertTrue(ae2.isAssignable(ae1));
        Assert.assertFalse(ae2.isAssignable(ae3));
        Assert.assertFalse(ae2.isAssignable(ab));
        Assert.assertTrue(ae3.isAssignable(ae1));
        Assert.assertTrue(ae3.isAssignable(ae2));
        Assert.assertFalse(ae3.isAssignable(ab));
        // Unions
        final TypeDescriptor ue1 = TypeDescriptor.union(TypeDescriptor.EXECUTABLE, TypeDescriptor.OBJECT);
        final TypeDescriptor ue2 = TypeDescriptor.union(TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.BOOLEAN), TypeDescriptor.STRING);
        final TypeDescriptor ue3 = TypeDescriptor.union(TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.BOOLEAN), TypeDescriptor.STRING, TypeDescriptor.OBJECT);
        final TypeDescriptor ue4 = TypeDescriptor.union(TypeDescriptor.EXECUTABLE_ANY, TypeDescriptor.STRING, TypeDescriptor.OBJECT);
        final TypeDescriptor up = TypeDescriptor.union(TypeDescriptor.BOOLEAN, TypeDescriptor.NUMBER);
        Assert.assertFalse(ue1.isAssignable(ue2));
        Assert.assertFalse(ue1.isAssignable(ue3));
        Assert.assertFalse(ue1.isAssignable(up));
        Assert.assertFalse(ue1.isAssignable(ue4));
        Assert.assertFalse(ue2.isAssignable(ue1));
        Assert.assertFalse(ue2.isAssignable(ue3));
        Assert.assertFalse(ue2.isAssignable(up));
        Assert.assertFalse(ue2.isAssignable(ue4));
        Assert.assertTrue(ue3.isAssignable(ue1));
        Assert.assertTrue(ue3.isAssignable(ue2));
        Assert.assertFalse(ue3.isAssignable(up));
        Assert.assertFalse(ue3.isAssignable(ue4));
        Assert.assertTrue(ue4.isAssignable(ue1));
        Assert.assertTrue(ue4.isAssignable(ue2));
        Assert.assertTrue(ue4.isAssignable(ue3));
        Assert.assertFalse(ue4.isAssignable(up));
        // strictParameterCount
        final TypeDescriptor exeStrictAnyAny = TypeDescriptor.executable(TypeDescriptor.ANY, false, TypeDescriptor.ANY);
        final TypeDescriptor exeAnyNum = TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.NUMBER);
        final TypeDescriptor exeAnyNumNum = TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.NUMBER, TypeDescriptor.NUMBER);
        Assert.assertTrue(exeAnyNum.isAssignable(exeStrictAnyAny));
        Assert.assertFalse(exeAnyNumNum.isAssignable(exeStrictAnyAny));

        final TypeDescriptor exeStrictAny = TypeDescriptor.executable(TypeDescriptor.ANY, false);
        Assert.assertFalse(exeAnyNum.isAssignable(exeStrictAny));
    }

    @Test
    public void testAny() {
        Assert.assertTrue(TypeDescriptor.ARRAY.isAssignable(TypeDescriptor.array(TypeDescriptor.ANY)));
        Assert.assertTrue(TypeDescriptor.array(TypeDescriptor.ANY).isAssignable(TypeDescriptor.ARRAY));
        Assert.assertFalse(TypeDescriptor.EXECUTABLE.isAssignable(TypeDescriptor.ANY));
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(TypeDescriptor.EXECUTABLE));
        Assert.assertFalse(TypeDescriptor.executable(TypeDescriptor.ANY).isAssignable(TypeDescriptor.ANY));
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(TypeDescriptor.executable(TypeDescriptor.ANY)));
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(TypeDescriptor.executable(TypeDescriptor.ANY, TypeDescriptor.STRING, TypeDescriptor.NUMBER)));
        Assert.assertTrue(TypeDescriptor.EXECUTABLE.isAssignable(TypeDescriptor.executable(TypeDescriptor.ANY)));
        Assert.assertTrue(TypeDescriptor.executable(TypeDescriptor.ANY).isAssignable(TypeDescriptor.EXECUTABLE));
        for (TypeDescriptor td : PREDEFINED) {
            Assert.assertTrue(TypeDescriptor.ANY.isAssignable(td));
            Assert.assertFalse(td.isAssignable(TypeDescriptor.ANY));
        }
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(TypeDescriptor.ANY));
        final TypeDescriptor union = TypeDescriptor.union(PREDEFINED);
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(union));
        Assert.assertFalse(union.isAssignable(TypeDescriptor.ANY));
        final TypeDescriptor unionWithAny = TypeDescriptor.union(union, TypeDescriptor.ANY);
        Assert.assertTrue(unionWithAny.isAssignable(TypeDescriptor.ANY));
        final TypeDescriptor intersection = TypeDescriptor.intersection(PREDEFINED);
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(intersection));
        Assert.assertFalse(intersection.isAssignable(TypeDescriptor.ANY));
        final TypeDescriptor arrayNum = TypeDescriptor.array(TypeDescriptor.NUMBER);
        final TypeDescriptor arrayAny = TypeDescriptor.array(TypeDescriptor.ANY);
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(arrayNum));
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(arrayAny));
        Assert.assertFalse(arrayNum.isAssignable(TypeDescriptor.ANY));
        Assert.assertFalse(arrayAny.isAssignable(TypeDescriptor.ANY));
        Assert.assertTrue(arrayAny.isAssignable(arrayNum));
        Assert.assertFalse(arrayNum.isAssignable(arrayAny));
    }

    @Test
    public void testIntersection() {
        final TypeDescriptor strAndObj = TypeDescriptor.intersection(TypeDescriptor.STRING, TypeDescriptor.OBJECT);
        final TypeDescriptor strAndNum = TypeDescriptor.intersection(TypeDescriptor.STRING, TypeDescriptor.NUMBER);
        final TypeDescriptor strAndNumAndObj = TypeDescriptor.intersection(strAndObj, strAndNum);

        Assert.assertTrue(strAndObj.isAssignable(strAndObj));
        Assert.assertFalse(strAndObj.isAssignable(strAndNum));
        Assert.assertTrue(strAndObj.isAssignable(strAndNumAndObj));
        Assert.assertTrue(strAndNum.isAssignable(strAndNum));
        Assert.assertFalse(strAndNum.isAssignable(strAndObj));
        Assert.assertTrue(strAndNum.isAssignable(strAndNumAndObj));
        Assert.assertTrue(strAndNumAndObj.isAssignable(strAndNumAndObj));
        Assert.assertFalse(strAndNumAndObj.isAssignable(strAndNum));
        Assert.assertFalse(strAndNumAndObj.isAssignable(strAndObj));

        for (TypeDescriptor predefined : PREDEFINED) {
            Assert.assertFalse(strAndNum.isAssignable(predefined));
            Assert.assertFalse(strAndObj.isAssignable(predefined));
            Assert.assertFalse(strAndNumAndObj.isAssignable(predefined));
        }
        Assert.assertFalse(TypeDescriptor.ARRAY.isAssignable(strAndNum));
        Assert.assertFalse(TypeDescriptor.ARRAY.isAssignable(strAndObj));
        Assert.assertFalse(TypeDescriptor.ARRAY.isAssignable(strAndNumAndObj));
        Assert.assertFalse(TypeDescriptor.BOOLEAN.isAssignable(strAndNum));
        Assert.assertFalse(TypeDescriptor.BOOLEAN.isAssignable(strAndObj));
        Assert.assertFalse(TypeDescriptor.BOOLEAN.isAssignable(strAndNumAndObj));
        Assert.assertFalse(TypeDescriptor.HOST_OBJECT.isAssignable(strAndNum));
        Assert.assertFalse(TypeDescriptor.HOST_OBJECT.isAssignable(strAndObj));
        Assert.assertFalse(TypeDescriptor.HOST_OBJECT.isAssignable(strAndNumAndObj));
        Assert.assertFalse(TypeDescriptor.NATIVE_POINTER.isAssignable(strAndNum));
        Assert.assertFalse(TypeDescriptor.NATIVE_POINTER.isAssignable(strAndObj));
        Assert.assertFalse(TypeDescriptor.NATIVE_POINTER.isAssignable(strAndNumAndObj));
        Assert.assertFalse(TypeDescriptor.NULL.isAssignable(strAndNum));
        Assert.assertFalse(TypeDescriptor.NULL.isAssignable(strAndObj));
        Assert.assertFalse(TypeDescriptor.NULL.isAssignable(strAndNumAndObj));
        Assert.assertTrue(TypeDescriptor.NUMBER.isAssignable(strAndNum));
        Assert.assertFalse(TypeDescriptor.NUMBER.isAssignable(strAndObj));
        Assert.assertTrue(TypeDescriptor.NUMBER.isAssignable(strAndNumAndObj));
        Assert.assertFalse(TypeDescriptor.OBJECT.isAssignable(strAndNum));
        Assert.assertTrue(TypeDescriptor.OBJECT.isAssignable(strAndObj));
        Assert.assertTrue(TypeDescriptor.OBJECT.isAssignable(strAndNumAndObj));
        Assert.assertTrue(TypeDescriptor.STRING.isAssignable(strAndNum));
        Assert.assertTrue(TypeDescriptor.STRING.isAssignable(strAndObj));
        Assert.assertTrue(TypeDescriptor.STRING.isAssignable(strAndNumAndObj));

        final TypeDescriptor boolOrNum = TypeDescriptor.union(TypeDescriptor.BOOLEAN, TypeDescriptor.NUMBER);
        final TypeDescriptor strOrNum = TypeDescriptor.union(TypeDescriptor.STRING, TypeDescriptor.NUMBER);
        Assert.assertFalse(strAndNum.isAssignable(boolOrNum));
        Assert.assertFalse(strAndNum.isAssignable(strOrNum));
        Assert.assertTrue(boolOrNum.isAssignable(strAndNum));
        Assert.assertTrue(strOrNum.isAssignable(strAndNum));

        final TypeDescriptor product = TypeDescriptor.intersection(boolOrNum, strOrNum);
        // Should be [number | [boolean & number] | [boolean & string] | [string & number]]
        Assert.assertTrue(product.equals(TypeDescriptor.union(
                        TypeDescriptor.NUMBER,
                        TypeDescriptor.intersection(TypeDescriptor.BOOLEAN, TypeDescriptor.NUMBER),
                        TypeDescriptor.intersection(TypeDescriptor.BOOLEAN, TypeDescriptor.STRING),
                        TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING))));
        Assert.assertTrue(product.isAssignable(strAndNum));
        Assert.assertFalse(product.isAssignable(strAndObj));
        Assert.assertTrue(product.isAssignable(strAndNumAndObj));
        Assert.assertFalse(strAndNum.isAssignable(product));
        Assert.assertFalse(strAndObj.isAssignable(product));
        Assert.assertFalse(strAndNumAndObj.isAssignable(product));

        final TypeDescriptor numAndArrNum = TypeDescriptor.intersection(
                        TypeDescriptor.NUMBER,
                        TypeDescriptor.array(TypeDescriptor.NUMBER));
        Assert.assertFalse(numAndArrNum.isAssignable(TypeDescriptor.NUMBER));
        Assert.assertFalse(numAndArrNum.isAssignable(TypeDescriptor.array(TypeDescriptor.NUMBER)));
        Assert.assertTrue(TypeDescriptor.NUMBER.isAssignable(numAndArrNum));
        Assert.assertTrue(TypeDescriptor.array(TypeDescriptor.NUMBER).isAssignable(numAndArrNum));
        Assert.assertTrue(TypeDescriptor.union(TypeDescriptor.array(TypeDescriptor.NUMBER), TypeDescriptor.OBJECT).isAssignable(numAndArrNum));

        final TypeDescriptor arrAndArrNum = TypeDescriptor.intersection(
                        TypeDescriptor.ARRAY,
                        TypeDescriptor.array(TypeDescriptor.NUMBER));
        Assert.assertTrue(arrAndArrNum.isAssignable(TypeDescriptor.ARRAY));
        Assert.assertTrue(arrAndArrNum.isAssignable(TypeDescriptor.array(TypeDescriptor.NUMBER)));
        Assert.assertTrue(TypeDescriptor.ARRAY.isAssignable(arrAndArrNum));
        Assert.assertFalse(TypeDescriptor.array(TypeDescriptor.NUMBER).isAssignable(arrAndArrNum));
        final TypeDescriptor numAndStr = TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING);
        final TypeDescriptor numAndStrAndBool = TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.BOOLEAN);
        final TypeDescriptor numAndStrAndObj = TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.OBJECT);
        Assert.assertTrue(numAndStr.isAssignable(TypeDescriptor.union(numAndStrAndBool, numAndStrAndObj)));
    }

    @Test
    public void testUnionBothExecutables() {
        Assert.assertTrue(TypeDescriptor.EXECUTABLE_ANY.isAssignable(TypeDescriptor.EXECUTABLE));
        final TypeDescriptor objOrExecUp = TypeDescriptor.union(TypeDescriptor.OBJECT, TypeDescriptor.EXECUTABLE_ANY);
        final TypeDescriptor objOrExecLow = TypeDescriptor.union(TypeDescriptor.OBJECT, TypeDescriptor.EXECUTABLE);
        Assert.assertTrue(objOrExecUp.isAssignable(objOrExecLow));
        final TypeDescriptor objOrExecUpOrExecLow = TypeDescriptor.union(TypeDescriptor.OBJECT, TypeDescriptor.EXECUTABLE_ANY, TypeDescriptor.EXECUTABLE);
        Assert.assertTrue(objOrExecUp.isAssignable(objOrExecUpOrExecLow));
    }
}
