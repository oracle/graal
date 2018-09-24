/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.JustFrameTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.NoParameterTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.ObjectInterfaceNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.ObjectPrimitiveTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.ObjectStringTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.PrimitiveTestNodeGen;
import com.oracle.truffle.api.dsl.test.NoTypeSystemTestFactory.TypesNotInTypeSystemTestNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class NoTypeSystemTest {

    abstract static class DummyChild extends Node {
        abstract Object execute();
    }

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

        abstract void executeVoid(Object primitive);

        abstract void executeChar(Object primitive);

        abstract int executeInt(int primitive) throws UnexpectedResultException;

        abstract double executeDouble(double primitive) throws UnexpectedResultException;

        abstract String executeString(String primitive) throws UnexpectedResultException;

        abstract int[] executeIntArray(int[] primitive) throws UnexpectedResultException;

        abstract void executeChar(char primitive);

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
        return TestHelper.createRoot(TypesNotInTypeSystemTestNodeGen.create());
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

    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                    "The following execute methods do not provide all evaluated values for the expected signature size 1: [execute()].")
    abstract static class ErrorMissingNodeChild1 extends Node {

        abstract int execute();

        @Specialization
        int s1(int arg0) {
            return arg0;
        }
    }

    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                    "The following execute methods do not provide all evaluated values for the expected signature size 2: [execute(int)].")
    @NodeChild(type = DummyChild.class)
    abstract static class ErrorMissingNodeChild2 extends Node {

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0, int arg1) {
            return arg0 + arg1;
        }
    }

    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                    "The following execute methods do not provide all evaluated values for the expected signature size 1: [execute()].")
    abstract static class ErrorMissingNodeChild3 extends Node {

        abstract int execute();

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0) {
            return arg0;
        }

    }

    @ExpectError("Unnecessary @NodeChild declaration. All evaluated child values are provided as parameters in execute methods.")
    @NodeChild(type = DummyChild.class)
    abstract static class ErrorAdditionalNodeChild1 extends Node {

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0) {
            return arg0;
        }
    }

    @NodeChild(type = DummyChild.class)
    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                    "The following execute methods do not provide all evaluated values for the expected signature size 2: [execute(int)].")
    abstract static class ErrorAdditionalNodeChild2 extends Node {

        abstract int execute(int arg0);

        @Specialization
        int s1(int arg0, int arg1) {
            return arg0 + arg1;
        }
    }

}
