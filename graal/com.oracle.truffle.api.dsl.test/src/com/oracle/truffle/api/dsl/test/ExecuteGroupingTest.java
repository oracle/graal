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
import org.junit.experimental.theories.*;
import org.junit.runner.*;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.ExecuteGroupingTestFactory.ExecuteGrouping1NodeGen;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/*
 * This test aims to test the reuse of execute methods with evaluated parameters as much as possible.
 */
@SuppressWarnings("unused")
@RunWith(Theories.class)
public class ExecuteGroupingTest {

    @DataPoints public static final Object[] parameters = new Object[]{1, 2};

    static final class ExecuteGroupingChild extends Node {

        int invocationCount = 0;

        private final Object returnValue;

        public ExecuteGroupingChild(Object returnValue) {
            this.returnValue = returnValue;
        }

        Object execute() {
            invocationCount++;
            return returnValue;
        }

    }

    @Theory
    public void testExecuteGrouping1Node(Object a, Object b, Object c) throws UnexpectedResultException {
        ExecuteGroupingChild child0 = new ExecuteGroupingChild(a);
        ExecuteGroupingChild child1 = new ExecuteGroupingChild(b);
        ExecuteGroupingChild child2 = new ExecuteGroupingChild(c);

        int result = ((int) a) + ((int) b) + ((int) c);

        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(child0, child1, child2)).execute());
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(child0, child1, child2)).execute((VirtualFrame) null));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, child1, child2)).execute(a));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, child1, child2)).executeInt(a));

        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, child2)).execute(a, b));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, child2)).execute((int) a, b));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, child2)).execute(a, (int) b));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, child2)).execute((int) a, (int) b));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, child2)).executeInt((int) a, (int) b));

        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, null)).execute(a, b, c));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, null)).execute((int) a, (int) b, c));
        assertEquals(result, TestHelper.createRoot(ExecuteGrouping1NodeGen.create(null, null, null)).execute((int) a, (int) b, (int) c));

    }

    @NodeChildren({@NodeChild(type = ExecuteGroupingChild.class), @NodeChild(type = ExecuteGroupingChild.class), @NodeChild(type = ExecuteGroupingChild.class)})
    abstract static class ExecuteGrouping1Node extends Node {

        abstract Object execute();

        int executeInt() throws UnexpectedResultException {
            Object value = execute();
            if (value instanceof Integer) {
                return (int) value;
            }
            throw new UnexpectedResultException(value);
        }

        abstract double executeDouble() throws UnexpectedResultException;

        abstract Object execute(VirtualFrame frame);

        abstract Object execute(Object o1);

        abstract int executeInt(Object o1) throws UnexpectedResultException;

        abstract Object execute(Object o1, Object o2);

        abstract Object execute(int o1, int o2);

        abstract Object execute(int o1, int o2, Object o3);

        abstract int executeInt(int o1, int o2) throws UnexpectedResultException;

        abstract Object execute(Object o1, int o2);

        abstract Object execute(int o1, Object o2);

        abstract Object execute(Object o1, Object o2, Object o3);

        abstract Object execute(int o1, int o2, int o3);

        @Specialization
        int s1(int a, int b, int c) {
            return a + b + c;
        }

        @Specialization
        int s2(Object a, Object b, Object c) {
            return ((int) a) + ((int) b) + ((int) c);
        }

    }

    abstract static class StrangeReturnCase extends Node {

        // we don't know how to implement executeDouble
        public abstract double executeDouble();

        public int executeInt() {
            return 42;
        }

        @Specialization(rewriteOn = RuntimeException.class)
        double s1() {
            return 42;
        }

        @Specialization
        double s2() {
            return 42;
        }

    }

    @ExpectError("Incompatible abstract execute methods found %")
    abstract static class IncompatibleAbstract1 extends Node {

        // we don't know how to implement executeDouble
        abstract double executeDouble();

        abstract int executeInt();

        @Specialization
        double s1() {
            return 42;
        }

    }

    abstract static class IncompatibleAbstract2 extends Node {

        abstract double executeDouble();

        // we can resolve duplicate path errors by making an execute method final
        @SuppressWarnings("static-method")
        public final int executeInt() {
            return 42;
        }

        @ExpectError("The provided return type \"int\" does not match expected return type \"double\".%")
        @Specialization(rewriteOn = RuntimeException.class)
        int s1() {
            return 42;
        }

        @Specialization
        double s2() {
            return 42;
        }

    }

}
