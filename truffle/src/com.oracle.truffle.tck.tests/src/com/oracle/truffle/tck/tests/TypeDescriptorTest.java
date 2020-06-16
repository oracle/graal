/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
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
                    TypeDescriptor.EXECUTABLE_ANY,
                    TypeDescriptor.INSTANTIABLE,
                    TypeDescriptor.INSTANTIABLE_ANY
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
                Assert.assertTrue(
                                td1 == td2 ||
                                                td1 == TypeDescriptor.EXECUTABLE_ANY && td2 == TypeDescriptor.EXECUTABLE ||
                                                td1 == TypeDescriptor.INSTANTIABLE_ANY && td2 == TypeDescriptor.INSTANTIABLE ||
                                                !td1.isAssignable(td2));
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
    public void testInstantiable() {
        TypeDescriptor instantiableBottom = TypeDescriptor.INSTANTIABLE;
        TypeDescriptor instantiableTop = TypeDescriptor.INSTANTIABLE_ANY;
        TypeDescriptor instantiableAnyNoArgs = TypeDescriptor.instantiable(TypeDescriptor.ANY, true);
        TypeDescriptor instantiableAnyStr = TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.STRING);
        TypeDescriptor instantiableAnyStrNum = TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.STRING, TypeDescriptor.NUMBER);
        TypeDescriptor instantiableStrNoArgs = TypeDescriptor.instantiable(TypeDescriptor.STRING, true);
        TypeDescriptor instantiableStrStr = TypeDescriptor.instantiable(TypeDescriptor.STRING, true, TypeDescriptor.STRING);
        TypeDescriptor instantiableAnyUnionUnion = TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.STRING),
                        TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.OBJECT));
        List<TypeDescriptor> instantiables = new ArrayList<>();
        Collections.addAll(instantiables, instantiableBottom, instantiableAnyNoArgs, instantiableAnyStr, instantiableAnyStrNum, instantiableStrNoArgs, instantiableStrStr, instantiableAnyUnionUnion);
        List<TypeDescriptor> otherTypes = new ArrayList<>();
        Collections.addAll(otherTypes, PREDEFINED);
        otherTypes.remove(TypeDescriptor.INSTANTIABLE);
        otherTypes.remove(TypeDescriptor.INSTANTIABLE_ANY);
        otherTypes.add(TypeDescriptor.array(TypeDescriptor.BOOLEAN));
        otherTypes.add(TypeDescriptor.union(TypeDescriptor.BOOLEAN, TypeDescriptor.NUMBER));
        for (TypeDescriptor td : otherTypes) {
            for (TypeDescriptor instantiable : instantiables) {
                Assert.assertFalse(instantiable.isAssignable(td));
                Assert.assertFalse(td.isAssignable(instantiable));
            }
        }
        Assert.assertTrue(instantiableTop.isAssignable(instantiableTop));
        Assert.assertTrue(instantiableTop.isAssignable(instantiableBottom));
        Assert.assertTrue(instantiableTop.isAssignable(instantiableAnyNoArgs));
        Assert.assertTrue(instantiableTop.isAssignable(instantiableAnyStr));
        Assert.assertTrue(instantiableTop.isAssignable(instantiableAnyStrNum));
        Assert.assertTrue(instantiableTop.isAssignable(instantiableStrNoArgs));
        Assert.assertTrue(instantiableTop.isAssignable(instantiableStrStr));
        Assert.assertTrue(instantiableTop.isAssignable(instantiableAnyUnionUnion));
        Assert.assertFalse(instantiableBottom.isAssignable(instantiableTop));
        Assert.assertTrue(instantiableBottom.isAssignable(instantiableAnyNoArgs));
        Assert.assertFalse(instantiableBottom.isAssignable(instantiableAnyStr));
        Assert.assertFalse(instantiableBottom.isAssignable(instantiableAnyStrNum));
        Assert.assertTrue(instantiableBottom.isAssignable(instantiableStrNoArgs));
        Assert.assertFalse(instantiableBottom.isAssignable(instantiableStrStr));
        Assert.assertFalse(instantiableBottom.isAssignable(instantiableAnyUnionUnion));
        Assert.assertFalse(instantiableAnyNoArgs.isAssignable(instantiableTop));
        Assert.assertTrue(instantiableAnyNoArgs.isAssignable(instantiableBottom));
        Assert.assertFalse(instantiableAnyNoArgs.isAssignable(instantiableAnyStr));
        Assert.assertFalse(instantiableAnyNoArgs.isAssignable(instantiableAnyStrNum));
        Assert.assertTrue(instantiableAnyNoArgs.isAssignable(instantiableStrNoArgs));
        Assert.assertFalse(instantiableAnyNoArgs.isAssignable(instantiableStrStr));
        Assert.assertFalse(instantiableAnyNoArgs.isAssignable(instantiableAnyUnionUnion));
        Assert.assertFalse(instantiableAnyStr.isAssignable(instantiableTop));
        Assert.assertTrue(instantiableAnyStr.isAssignable(instantiableBottom));
        Assert.assertTrue(instantiableAnyStr.isAssignable(instantiableAnyNoArgs));
        Assert.assertFalse(instantiableAnyStr.isAssignable(instantiableAnyStrNum));
        Assert.assertTrue(instantiableAnyStr.isAssignable(instantiableStrNoArgs));
        Assert.assertTrue(instantiableAnyStr.isAssignable(instantiableStrStr));
        Assert.assertFalse(instantiableAnyStr.isAssignable(instantiableAnyUnionUnion));
        Assert.assertFalse(instantiableAnyStrNum.isAssignable(instantiableTop));
        Assert.assertTrue(instantiableAnyStrNum.isAssignable(instantiableBottom));
        Assert.assertTrue(instantiableAnyStrNum.isAssignable(instantiableAnyNoArgs));
        Assert.assertTrue(instantiableAnyStrNum.isAssignable(instantiableAnyStr));
        Assert.assertTrue(instantiableAnyStrNum.isAssignable(instantiableStrNoArgs));
        Assert.assertTrue(instantiableAnyStrNum.isAssignable(instantiableStrStr));
        Assert.assertTrue(instantiableAnyStrNum.isAssignable(instantiableAnyUnionUnion));
        Assert.assertFalse(instantiableStrNoArgs.isAssignable(instantiableTop));
        Assert.assertFalse(instantiableStrNoArgs.isAssignable(instantiableBottom));
        Assert.assertFalse(instantiableStrNoArgs.isAssignable(instantiableAnyNoArgs));
        Assert.assertFalse(instantiableStrNoArgs.isAssignable(instantiableAnyStr));
        Assert.assertFalse(instantiableStrNoArgs.isAssignable(instantiableAnyStrNum));
        Assert.assertFalse(instantiableStrNoArgs.isAssignable(instantiableStrStr));
        Assert.assertFalse(instantiableStrNoArgs.isAssignable(instantiableAnyUnionUnion));
        Assert.assertFalse(instantiableStrStr.isAssignable(instantiableTop));
        Assert.assertFalse(instantiableStrStr.isAssignable(instantiableBottom));
        Assert.assertFalse(instantiableStrStr.isAssignable(instantiableAnyNoArgs));
        Assert.assertFalse(instantiableStrStr.isAssignable(instantiableAnyStr));
        Assert.assertFalse(instantiableStrStr.isAssignable(instantiableAnyStrNum));
        Assert.assertTrue(instantiableStrStr.isAssignable(instantiableStrNoArgs));
        Assert.assertFalse(instantiableStrStr.isAssignable(instantiableAnyUnionUnion));
        Assert.assertFalse(instantiableAnyUnionUnion.isAssignable(instantiableTop));
        Assert.assertTrue(instantiableAnyUnionUnion.isAssignable(instantiableBottom));
        Assert.assertTrue(instantiableAnyUnionUnion.isAssignable(instantiableAnyNoArgs));
        Assert.assertFalse(instantiableAnyUnionUnion.isAssignable(instantiableAnyStr));
        Assert.assertFalse(instantiableAnyUnionUnion.isAssignable(instantiableAnyStrNum));
        Assert.assertTrue(instantiableAnyUnionUnion.isAssignable(instantiableStrNoArgs));
        Assert.assertFalse(instantiableAnyUnionUnion.isAssignable(instantiableStrStr));
        // Arrays
        TypeDescriptor arrInstantiableBottom = TypeDescriptor.array(TypeDescriptor.INSTANTIABLE);
        TypeDescriptor arrInstantiableUnit = TypeDescriptor.array(TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.BOOLEAN));
        TypeDescriptor arrInstantiableTop = TypeDescriptor.array(TypeDescriptor.INSTANTIABLE_ANY);
        TypeDescriptor arrBoolean = TypeDescriptor.array(TypeDescriptor.BOOLEAN);
        Assert.assertFalse(arrInstantiableBottom.isAssignable(arrInstantiableUnit));
        Assert.assertFalse(arrInstantiableBottom.isAssignable(arrInstantiableTop));
        Assert.assertFalse(arrInstantiableBottom.isAssignable(arrBoolean));
        Assert.assertTrue(arrInstantiableUnit.isAssignable(arrInstantiableBottom));
        Assert.assertFalse(arrInstantiableUnit.isAssignable(arrInstantiableTop));
        Assert.assertFalse(arrInstantiableUnit.isAssignable(arrBoolean));
        Assert.assertTrue(arrInstantiableTop.isAssignable(arrInstantiableBottom));
        Assert.assertTrue(arrInstantiableTop.isAssignable(arrInstantiableUnit));
        Assert.assertFalse(arrInstantiableTop.isAssignable(arrBoolean));
        // Unions
        TypeDescriptor uinstantiable1 = TypeDescriptor.union(TypeDescriptor.INSTANTIABLE, TypeDescriptor.OBJECT);
        TypeDescriptor uinstantiable2 = TypeDescriptor.union(TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.BOOLEAN), TypeDescriptor.STRING);
        TypeDescriptor uinstantiable3 = TypeDescriptor.union(TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.BOOLEAN), TypeDescriptor.STRING, TypeDescriptor.OBJECT);
        TypeDescriptor uinstantiable4 = TypeDescriptor.union(TypeDescriptor.INSTANTIABLE_ANY, TypeDescriptor.STRING, TypeDescriptor.OBJECT);
        TypeDescriptor uprimitive = TypeDescriptor.union(TypeDescriptor.BOOLEAN, TypeDescriptor.NUMBER);
        Assert.assertFalse(uinstantiable1.isAssignable(uinstantiable2));
        Assert.assertFalse(uinstantiable1.isAssignable(uinstantiable3));
        Assert.assertFalse(uinstantiable1.isAssignable(uprimitive));
        Assert.assertFalse(uinstantiable1.isAssignable(uinstantiable4));
        Assert.assertFalse(uinstantiable2.isAssignable(uinstantiable1));
        Assert.assertFalse(uinstantiable2.isAssignable(uinstantiable3));
        Assert.assertFalse(uinstantiable2.isAssignable(uprimitive));
        Assert.assertFalse(uinstantiable2.isAssignable(uinstantiable4));
        Assert.assertTrue(uinstantiable3.isAssignable(uinstantiable1));
        Assert.assertTrue(uinstantiable3.isAssignable(uinstantiable2));
        Assert.assertFalse(uinstantiable3.isAssignable(uprimitive));
        Assert.assertFalse(uinstantiable3.isAssignable(uinstantiable4));
        Assert.assertTrue(uinstantiable4.isAssignable(uinstantiable1));
        Assert.assertTrue(uinstantiable4.isAssignable(uinstantiable2));
        Assert.assertTrue(uinstantiable4.isAssignable(uinstantiable3));
        Assert.assertFalse(uinstantiable4.isAssignable(uprimitive));
        // strictParameterCount
        TypeDescriptor instantiableStrictAnyAny = TypeDescriptor.instantiable(TypeDescriptor.ANY, false, TypeDescriptor.ANY);
        TypeDescriptor instantiableAnyNum = TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.NUMBER);
        TypeDescriptor instantiableAnyNumNum = TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.NUMBER, TypeDescriptor.NUMBER);
        Assert.assertTrue(instantiableAnyNum.isAssignable(instantiableStrictAnyAny));
        Assert.assertFalse(instantiableAnyNumNum.isAssignable(instantiableStrictAnyAny));
        TypeDescriptor instantiableStrictAny = TypeDescriptor.executable(TypeDescriptor.ANY, false);
        Assert.assertFalse(instantiableAnyNum.isAssignable(instantiableStrictAny));
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
        Assert.assertFalse(TypeDescriptor.INSTANTIABLE.isAssignable(TypeDescriptor.ANY));
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(TypeDescriptor.INSTANTIABLE));
        Assert.assertFalse(TypeDescriptor.instantiable(TypeDescriptor.ANY, true).isAssignable(TypeDescriptor.ANY));
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(TypeDescriptor.instantiable(TypeDescriptor.ANY, true)));
        Assert.assertTrue(TypeDescriptor.ANY.isAssignable(TypeDescriptor.instantiable(TypeDescriptor.ANY, true, TypeDescriptor.STRING, TypeDescriptor.NUMBER)));
        Assert.assertTrue(TypeDescriptor.INSTANTIABLE.isAssignable(TypeDescriptor.instantiable(TypeDescriptor.ANY, true)));
        Assert.assertTrue(TypeDescriptor.instantiable(TypeDescriptor.ANY, true).isAssignable(TypeDescriptor.INSTANTIABLE));

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

        TypeDescriptor instantiableAndHostObject = TypeDescriptor.intersection(
                        TypeDescriptor.instantiable(TypeDescriptor.HOST_OBJECT, false),
                        TypeDescriptor.HOST_OBJECT);
        TypeDescriptor instantiableAndHostObjectAndObject = TypeDescriptor.intersection(
                        TypeDescriptor.instantiable(TypeDescriptor.intersection(TypeDescriptor.HOST_OBJECT, TypeDescriptor.OBJECT), false),
                        TypeDescriptor.HOST_OBJECT,
                        TypeDescriptor.OBJECT);
        Assert.assertTrue(TypeDescriptor.INSTANTIABLE_ANY.isAssignable(instantiableAndHostObject));
        Assert.assertTrue(TypeDescriptor.INSTANTIABLE_ANY.isAssignable(instantiableAndHostObjectAndObject));
        Assert.assertTrue(instantiableAndHostObject.isAssignable(instantiableAndHostObjectAndObject));
        Assert.assertFalse(instantiableAndHostObjectAndObject.isAssignable(instantiableAndHostObject));
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

    @Test
    public void testInstantiablesWithExecutables() {
        Assert.assertFalse(TypeDescriptor.EXECUTABLE_ANY.isAssignable(TypeDescriptor.INSTANTIABLE));
        Assert.assertFalse(TypeDescriptor.EXECUTABLE_ANY.isAssignable(TypeDescriptor.INSTANTIABLE_ANY));
        Assert.assertFalse(TypeDescriptor.EXECUTABLE.isAssignable(TypeDescriptor.INSTANTIABLE));
        Assert.assertFalse(TypeDescriptor.EXECUTABLE.isAssignable(TypeDescriptor.INSTANTIABLE_ANY));
        Assert.assertFalse(TypeDescriptor.INSTANTIABLE_ANY.isAssignable(TypeDescriptor.EXECUTABLE));
        Assert.assertFalse(TypeDescriptor.INSTANTIABLE_ANY.isAssignable(TypeDescriptor.EXECUTABLE_ANY));
        Assert.assertFalse(TypeDescriptor.INSTANTIABLE.isAssignable(TypeDescriptor.EXECUTABLE));
        Assert.assertFalse(TypeDescriptor.INSTANTIABLE.isAssignable(TypeDescriptor.EXECUTABLE_ANY));
        TypeDescriptor executable = TypeDescriptor.executable(TypeDescriptor.OBJECT, true, TypeDescriptor.STRING);
        TypeDescriptor instantiable = TypeDescriptor.instantiable(TypeDescriptor.OBJECT, true, TypeDescriptor.STRING);
        Assert.assertFalse(executable.isAssignable(instantiable));
        Assert.assertFalse(instantiable.isAssignable(executable));
        Assert.assertFalse(TypeDescriptor.EXECUTABLE_ANY.isAssignable(instantiable));
        Assert.assertFalse(TypeDescriptor.INSTANTIABLE_ANY.isAssignable(executable));
    }

    @Test
    public void testForValue() {
        try (Context ctx = Context.newBuilder().allowAllAccess(true).build()) {
            Value v = ctx.asValue(1);
            Assert.assertTrue(TypeDescriptor.NUMBER.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(true);
            Assert.assertTrue(TypeDescriptor.BOOLEAN.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue("a");
            Assert.assertTrue(TypeDescriptor.STRING.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(null);
            Assert.assertTrue(TypeDescriptor.NULL.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(ProxyObject.fromMap(Collections.singletonMap("key", "value")));
            Assert.assertTrue(TypeDescriptor.OBJECT.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(new Function<Object, Object>() {
                @Override
                public Object apply(Object t) {
                    return null;
                }
            });
            Assert.assertTrue(TypeDescriptor.EXECUTABLE.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(Object.class);
            Assert.assertTrue(TypeDescriptor.INSTANTIABLE.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(new Object());
            Assert.assertTrue(TypeDescriptor.HOST_OBJECT.isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(ProxyArray.fromArray(1));
            Assert.assertTrue(TypeDescriptor.array(TypeDescriptor.NUMBER).isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(ProxyArray.fromArray(true));
            Assert.assertTrue(TypeDescriptor.array(TypeDescriptor.BOOLEAN).isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(ProxyArray.fromArray(1, true, "value"));
            Assert.assertTrue(TypeDescriptor.array(TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.BOOLEAN, TypeDescriptor.STRING)).isAssignable(TypeDescriptor.forValue(v)));
            v = ctx.asValue(ProxyArray.fromArray());
            Assert.assertTrue(TypeDescriptor.array(TypeDescriptor.intersection(TypeDescriptor.NULL, TypeDescriptor.BOOLEAN, TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.HOST_OBJECT,
                            TypeDescriptor.NATIVE_POINTER, TypeDescriptor.OBJECT, TypeDescriptor.ARRAY, TypeDescriptor.EXECUTABLE, TypeDescriptor.INSTANTIABLE)).isAssignable(
                                            TypeDescriptor.forValue(v)));
        }
    }
}
