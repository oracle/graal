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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.TypeBoxingTestFactory.TypeBoxingTest1NodeGen;
import com.oracle.truffle.api.dsl.test.TypeBoxingTestFactory.TypeBoxingTest2NodeGen;
import com.oracle.truffle.api.dsl.test.TypeBoxingTestFactory.TypeBoxingTest3NodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class TypeBoxingTest {

    @Test
    public void testTypeBoxing11() {
        ConstantNode constantNode = new ConstantNode();
        TypeBoxingTest1 test = TypeBoxingTest1NodeGen.create(constantNode);

        test.execute();

        assertEquals(1, constantNode.executeInvoked);
        assertEquals(0, constantNode.executeIntInvoked);

        test.execute();

        assertEquals(1, constantNode.executeInvoked);
        assertEquals(1, constantNode.executeIntInvoked);

    }

    @Test
    public void testTypeBoxing12() throws UnexpectedResultException {
        ConstantNode constantNode = new ConstantNode();
        TypeBoxingTest1 test = TypeBoxingTest1NodeGen.create(constantNode);

        test.executeInt();

        assertEquals(0, constantNode.executeInvoked);
        assertEquals(1, constantNode.executeIntInvoked);

        test.executeInt();

        assertEquals(0, constantNode.executeInvoked);
        assertEquals(2, constantNode.executeIntInvoked);
    }

    @NodeChild
    abstract static class TypeBoxingTest1 extends TestNode {

        @Specialization
        protected int doInt(int value) {
            return value;
        }

        @Specialization
        protected Object doInt(Object value) {
            return value;
        }

    }

    @Test
    public void testTypeBoxing21() {
        ConstantNode constantNode = new ConstantNode();
        TypeBoxingTest2 test = TypeBoxingTest2NodeGen.create(constantNode);

        test.execute();

        assertEquals(1, constantNode.executeInvoked);
        assertEquals(0, constantNode.executeIntInvoked);

        test.execute();

        assertEquals(1, constantNode.executeInvoked);
        assertEquals(1, constantNode.executeIntInvoked);

    }

    @Test
    public void testTypeBxoing22() throws UnexpectedResultException {
        ConstantNode constantNode = new ConstantNode();
        TypeBoxingTest2 test = TypeBoxingTest2NodeGen.create(constantNode);

        test.executeInt();

        assertEquals(1, constantNode.executeInvoked);
        assertEquals(0, constantNode.executeIntInvoked);

        test.executeInt();

        assertEquals(1, constantNode.executeInvoked);
        assertEquals(1, constantNode.executeIntInvoked);
    }

    @NodeChild
    abstract static class TypeBoxingTest2 extends TestNode {

        @Specialization
        protected int doInt(int value) {
            return value;
        }

        @Specialization
        protected int doInt(Object value) {
            return (int) value;
        }

    }

    @Test
    public void testTypeBoxing31() throws UnexpectedResultException {
        ConstantNode arg1 = new ConstantNode();
        ConstantNode arg2 = new ConstantNode();
        TypeBoxingTest3 test = TypeBoxingTest3NodeGen.create(arg1, arg2);

        arg1.value = 1;
        arg2.value = 1;

        // first time we don't know the types we need to execute
        test.executeInt();
        assertEquals(1, arg1.executeInvoked);
        assertEquals(0, arg1.executeIntInvoked);
        assertEquals(1, arg2.executeInvoked);
        assertEquals(0, arg2.executeIntInvoked);

        // next time int,int is active both children must be executed
        // with executeInt
        test.executeInt();
        assertEquals(1, arg1.executeInvoked);
        assertEquals(1, arg1.executeIntInvoked);
        assertEquals(1, arg2.executeInvoked);
        assertEquals(1, arg2.executeIntInvoked);

        arg1.value = -1;
        arg2.value = 1;

        // now int, Object should become active, but the node
        // still believes it is in int,int so executeInt is executed
        // for both children
        test.executeInt();
        assertEquals(1, arg1.executeInvoked);
        assertEquals(2, arg1.executeIntInvoked);
        assertEquals(1, arg2.executeInvoked);
        assertEquals(2, arg2.executeIntInvoked);

        // now we are rewritten to int,Object and the node needs
        // to execute the second child with Object.
        test.executeInt();
        assertEquals(1, arg1.executeInvoked);
        assertEquals(3, arg1.executeIntInvoked);
        assertEquals(2, arg2.executeInvoked);
        assertEquals(2, arg2.executeIntInvoked);

        arg1.value = 1;
        arg2.value = -1;

        // now Object, int should become activate the node
        // still believes iut is in int, Object so executeInt is
        // executed for the first argument.
        test.executeInt();
        assertEquals(1, arg1.executeInvoked);
        assertEquals(4, arg1.executeIntInvoked);
        assertEquals(3, arg2.executeInvoked);
        assertEquals(2, arg2.executeIntInvoked);

        test.executeInt();
        assertEquals(2, arg1.executeInvoked);
        assertEquals(4, arg1.executeIntInvoked);
        assertEquals(4, arg2.executeInvoked);
        assertEquals(2, arg2.executeIntInvoked);

    }

    @NodeChildren({@NodeChild, @NodeChild})
    abstract static class TypeBoxingTest3 extends TestNode {

        @Specialization(guards = "value1 < 0")
        protected Object doInt(int value1, Object value2) {
            return value1 + (int) value2;
        }

        @Specialization(guards = "value2 < 0")
        protected Object doInt(Object value1, int value2) {
            return (int) value1 + value2;
        }

        @Specialization(guards = {"value1 >= 0", "value2 >= 0"})
        protected Object doInt(int value1, int value2) {
            return value1 + value2;
        }

    }

    @TypeSystemReference(TypeBoxingTypeSystem.class)
    abstract static class TestNode extends Node {

        public abstract Object execute();

        public abstract int executeInt() throws UnexpectedResultException;

    }

    static class ConstantNode extends TestNode {

        int executeInvoked;
        int executeIntInvoked;

        int value = 1;

        @Override
        public Object execute() {
            executeInvoked++;
            return value;
        }

        @Override
        public int executeInt() throws UnexpectedResultException {
            executeIntInvoked++;
            return value;
        }

    }

    @TypeSystem
    static class TypeBoxingTypeSystem {

    }

}
