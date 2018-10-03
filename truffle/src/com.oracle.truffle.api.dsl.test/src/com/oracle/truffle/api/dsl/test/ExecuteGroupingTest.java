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

import static org.junit.Assert.assertEquals;

import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ExecuteGroupingTestFactory.ExecuteGrouping1NodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/*
 * This test aims to test the reuse of execute methods with evaluated parameters as much as possible.
 */
@RunWith(Theories.class)
public class ExecuteGroupingTest {

    @DataPoints public static final Object[] parameters = new Object[]{1, 2};

    static final class ExecuteGroupingChild extends Node {

        int invocationCount = 0;

        private final Object returnValue;

        ExecuteGroupingChild(Object returnValue) {
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

    @NodeChild(type = ExecuteGroupingChild.class)
    @NodeChild(type = ExecuteGroupingChild.class)
    @NodeChild(type = ExecuteGroupingChild.class)
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
