/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.ShortCircuitTestFactory.DoubleChildNodeFactory;
import com.oracle.truffle.api.dsl.test.ShortCircuitTestFactory.SingleChildNodeFactory;
import com.oracle.truffle.api.dsl.test.ShortCircuitTestFactory.VarArgsNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ArgumentNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class ShortCircuitTest {

    @Test
    public void testSingleChild1() {
        ArgumentNode arg0 = new ArgumentNode(0);
        CallTarget callTarget = TestHelper.createCallTarget(SingleChildNodeFactory.create(arg0));
        SingleChildNode.needsChild = true;
        assertEquals(42, callTarget.call(new Object[]{42}));
        assertEquals(1, arg0.getInvocationCount());
    }

    @Test
    public void testSingleChild2() {
        ArgumentNode arg0 = new ArgumentNode(0);
        CallTarget callTarget = TestHelper.createCallTarget(SingleChildNodeFactory.create(arg0));
        SingleChildNode.needsChild = false;
        assertEquals(0, callTarget.call(new Object[]{42}));
        assertEquals(0, arg0.getInvocationCount());
    }

    @NodeChild("child0")
    abstract static class SingleChildNode extends ValueNode {

        static boolean needsChild;

        @ShortCircuit("child0")
        boolean needsChild0() {
            return needsChild;
        }

        @Specialization
        int doIt(boolean hasChild0, int child0) {
            assert hasChild0 == needsChild0();
            return child0;
        }

    }

    @Test
    public void testDoubleChild1() {
        ArgumentNode arg0 = new ArgumentNode(0);
        ArgumentNode arg1 = new ArgumentNode(1);
        CallTarget callTarget = TestHelper.createCallTarget(DoubleChildNodeFactory.create(arg0, arg1));
        assertEquals(42, callTarget.call(new Object[]{41, 42}));
        assertEquals(1, arg1.getInvocationCount());
    }

    @Test
    public void testDoubleChild2() {
        ArgumentNode arg0 = new ArgumentNode(0);
        ArgumentNode arg1 = new ArgumentNode(1);
        CallTarget callTarget = TestHelper.createCallTarget(DoubleChildNodeFactory.create(arg0, arg1));
        assertEquals(0, callTarget.call(new Object[]{42, 42}));
        assertEquals(0, arg1.getInvocationCount());
    }

    @NodeChildren({@NodeChild("child0"), @NodeChild("child1")})
    @SuppressWarnings("unused")
    abstract static class DoubleChildNode extends ValueNode {

        @ShortCircuit("child1")
        boolean needsChild1(Object leftValue) {
            return leftValue.equals(41);
        }

        @Specialization
        int doIt(int child0, boolean hasChild1, int child1) {
            return child1;
        }

    }

    @Test
    public void testVarArgs1() {
        ArgumentNode arg0 = new ArgumentNode(0);
        ArgumentNode arg1 = new ArgumentNode(1);
        CallTarget callTarget = TestHelper.createCallTarget(VarArgsNodeFactory.create(new ValueNode[]{arg0, arg1}));
        assertEquals(42, callTarget.call(new Object[]{41, 42}));
        assertEquals(1, arg1.getInvocationCount());
    }

    @Test
    public void testVarArgs2() {
        ArgumentNode arg0 = new ArgumentNode(0);
        ArgumentNode arg1 = new ArgumentNode(1);
        CallTarget callTarget = TestHelper.createCallTarget(VarArgsNodeFactory.create(new ValueNode[]{arg0, arg1}));
        assertEquals(0, callTarget.call(new Object[]{42, 42}));
        assertEquals(0, arg1.getInvocationCount());
    }

    @NodeChild(value = "children", type = ValueNode[].class)
    abstract static class VarArgsNode extends ValueNode {

        @ShortCircuit("children[1]")
        boolean needsChild1(Object leftValue) {
            return leftValue.equals(41);
        }

        @Specialization
        @SuppressWarnings("unused")
        int doIt(int child0, boolean hasChild1, int child1) {
            return child1;
        }

    }

}
