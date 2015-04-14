/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.JustFrameTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.NoParameterTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.ObjectInterfaceNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.ObjectPrimitiveTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.ObjectStringTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.PrimitiveTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.TypesNotInTypeSystemTestNodeGen;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class NoTypeSystemTest {

    abstract static class NoParameterTestNode extends Node {
        abstract void execute();

        @Specialization
        void s1() {
        }
    }

    @Test
    public void testNoParameter() {
        NoParameterTestNodeGen.create().execute();
    }

    abstract static class JustFrameTestNode extends Node {

        abstract void execute(VirtualFrame frames);

        @Specialization
        void s1(@SuppressWarnings("unused") VirtualFrame frame) {
        }
    }

    @Test
    public void testJustFrame() {
        JustFrameTestNodeGen.create().execute(null);
    }

    abstract static class PrimitiveTestNode extends Node {

        abstract int execute(int primitive);

        @Specialization
        int test(int primitive) {
            return primitive;
        }

    }

    @Test
    public void testPrimitive() {
        Assert.assertEquals(42, PrimitiveTestNodeGen.create().execute(42));
    }

    abstract static class ObjectInterfaceNode extends Node {

        abstract CharSequence execute(Object operand);

        @Specialization
        CharSequence s1(CharSequence operandSpecial) {
            return operandSpecial;
        }
    }

    @Test
    public void testObjectInterface() {
        Assert.assertEquals("42", ObjectInterfaceNodeGen.create().execute("42"));
    }

    abstract static class ObjectPrimitiveTestNode extends Node {

        abstract int execute(Object primitive);

        @Specialization
        int s1(int primitive) {
            return primitive;
        }

    }

    @Test
    public void testObjectPrimitiveTest() {
        Assert.assertEquals(42, ObjectPrimitiveTestNodeGen.create().execute(42));
    }

    abstract static class ObjectStringTestNode extends Node {

        abstract String execute(Object operand);

        @Specialization
        String s1(String operand) {
            return operand;
        }
    }

    @Test
    public void testObjectStringTest() {
        Assert.assertEquals("42", ObjectStringTestNodeGen.create().execute("42"));
    }

    abstract static class TypesNotInTypeSystemTest extends Node {

        abstract Object execute(Object primitive);

        abstract int executeInt(Object primitive) throws UnexpectedResultException;

        abstract double executeDouble(Object primitive) throws UnexpectedResultException;

        abstract String executeString(Object primitive) throws UnexpectedResultException;

        abstract int[] executeIntArray(Object primitive) throws UnexpectedResultException;

        abstract void executeVoid(Object primitive) throws UnexpectedResultException;

        abstract void executeChar(Object primitive) throws UnexpectedResultException;

        abstract int executeInt(int primitive) throws UnexpectedResultException;

        abstract double executeDouble(double primitive) throws UnexpectedResultException;

        abstract String executeString(String primitive) throws UnexpectedResultException;

        abstract int[] executeIntArray(int[] primitive) throws UnexpectedResultException;

        abstract void executeChar(char primitive) throws UnexpectedResultException;

        @Specialization
        int s1(int primitive) {
            return primitive;
        }

        @Specialization
        double s2(double primitive) {
            return (int) primitive;
        }

        @Specialization
        String s3(String object) {
            return object;
        }

        @Specialization
        int[] s4(int[] object) {
            return object;
        }

        @Specialization
        void s5(@SuppressWarnings("unused") char object) {
        }

    }

    // make nodes replacable
    private static <T extends Node> T createRoot(final T node) {
        new RootNode() {
            @Child T child = node;

            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.adoptChildren();
        return node;
    }

    @Test
    public void testTypesNotInTypeSystem() throws UnexpectedResultException {
        int[] someArray = {1, 2, 3};
        Assert.assertEquals(42, createTypesNotInTypeSystem().execute(42));
        Assert.assertEquals(42d, createTypesNotInTypeSystem().execute(42d));
        Assert.assertEquals(someArray, createTypesNotInTypeSystem().execute(someArray));
        Assert.assertNull(createTypesNotInTypeSystem().execute((char) 42));

        Assert.assertEquals(42, createTypesNotInTypeSystem().executeInt((Object) 42));
        Assert.assertEquals(42d, createTypesNotInTypeSystem().executeDouble((Object) 42d), 0d);
        Assert.assertEquals(someArray, createTypesNotInTypeSystem().executeIntArray((Object) someArray));
        createTypesNotInTypeSystem().executeChar((Object) (char) 42);

        Assert.assertEquals(42, createTypesNotInTypeSystem().executeInt(42));
        Assert.assertEquals(42d, createTypesNotInTypeSystem().executeDouble(42d), 0d);
        Assert.assertEquals(someArray, createTypesNotInTypeSystem().executeIntArray(someArray));
        createTypesNotInTypeSystem().executeChar((char) 42);

        try {
            createTypesNotInTypeSystem().executeInt("a");
            Assert.fail();
        } catch (UnexpectedResultException e) {
        }

        try {
            createTypesNotInTypeSystem().executeDouble("a");
            Assert.fail();
        } catch (UnexpectedResultException e) {
        }

        try {
            createTypesNotInTypeSystem().executeIntArray("a");
            Assert.fail();
        } catch (UnexpectedResultException e) {
        }

        createTypesNotInTypeSystem().executeChar("a");

    }

    private static TypesNotInTypeSystemTest createTypesNotInTypeSystem() {
        return createRoot(TypesNotInTypeSystemTestNodeGen.create());
    }

    abstract static class ErrorImpossibleTypes1 extends Node {

        abstract int execute(int primitive);

        @Specialization
        int test(int primitive) {
            return primitive;
        }

        @ExpectError("The provided return type \"Object\" does not match expected return type \"int\".%")
        @Specialization
        Object s2(int arg0) {
            return arg0;
        }
    }

    abstract static class ErrorImpossibleTypes2 extends Node {

        abstract int execute(int primitive);

        @Specialization
        int test(int primitive) {
            return primitive;
        }

        @ExpectError("Method signature (Object) does not match to the expected signature: %")
        @Specialization
        int s2(Object arg0) {
            return (int) arg0;
        }
    }

    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. "
                    + "The following execute methods do not provide all evaluated values for the expected signature size 1: [execute()].")
    abstract static class ErrorMissingNodeChild1 extends Node {

        abstract int execute();

        @Specialization
        int s1(int arg0) {
            return arg0;
        }
    }

    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. "
                    + "The following execute methods do not provide all evaluated values for the expected signature size 2: [execute(int)].")
    @NodeChild
    abstract static class ErrorMissingNodeChild2 extends Node {

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0, int arg1) {
            return arg0 + arg1;
        }
    }

    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. "
                    + "The following execute methods do not provide all evaluated values for the expected signature size 1: [execute()].")
    abstract static class ErrorMissingNodeChild3 extends Node {

        abstract int execute();

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0) {
            return arg0;
        }

    }

    @ExpectError("Unnecessary @NodeChild declaration. All evaluated child values are provided as parameters in execute methods.")
    @NodeChild
    abstract static class ErrorAdditionalNodeChild1 extends Node {

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0) {
            return arg0;
        }
    }

    @NodeChild
    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. "
                    + "The following execute methods do not provide all evaluated values for the expected signature size 2: [execute(int)].")
    abstract static class ErrorAdditionalNodeChild2 extends Node {

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0, int arg1) {
            return arg0 + arg1;
        }
    }

}
