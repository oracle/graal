/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.dsl.ExecuteTracingSupport;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.ConstNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.DivNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.OverloadedExecuteNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.TraceDisabledNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class ExecuteTracingSupportTest {

    private static int traceOnEnterCalled;
    private static int traceOnExceptionCalled;
    private static Object[] capturedArgs;
    private static Throwable capturedThrowable;

    @Before
    public final void setup() {
        traceOnEnterCalled = 0;
        traceOnExceptionCalled = 0;
        capturedArgs = null;
        capturedThrowable = null;
    }

    abstract static class TracingBaseNode extends Node implements ExecuteTracingSupport {
        @Override
        public boolean isTracingEnabled() {
            return true;
        }

        @Override
        public void traceOnEnter(String[] argumentNames, Object... arguments) {
            traceOnEnterCalled++;
            capturedArgs = arguments;
        }

        @Override
        public void traceOnException(Throwable t) {
            traceOnExceptionCalled++;
            capturedThrowable = t;
        }
    }

    abstract static class BaseNode extends TracingBaseNode {
        abstract Object execute(VirtualFrame frame);
    }

    @NodeChild("left")
    @NodeChild("right")
    abstract static class DivNode extends BaseNode {
        @Specialization
        int doIt(int left, int right) {
            return left / right;
        }
    }

    abstract static class ConstNode extends BaseNode {

        private final int value;

        ConstNode(int value) {
            this.value = value;
        }

        @Specialization
        int doIt(VirtualFrame frame) {
            return value;
        }
    }

    @GenerateUncached
    abstract static class OverloadedExecuteNode extends TracingBaseNode {

        abstract Object execute(int x, Object y);

        abstract Object executeX(long a, Object b);

        @Specialization
        int doIt(int a0, int a1) {
            return a0 / a1;
        }

        @Specialization
        String doIt(int b0, String b1) {
            return b0 + b1;
        }

        @Specialization
        double doIt(long c0, double c1) {
            return c0 + c1;
        }
    }

    abstract static class TraceDisabledNode extends TracingBaseNode {
        abstract Object execute(int x);

        @Override
        public boolean isTracingEnabled() {
            return false;
        }

        @Specialization
        Object doIt(int x) {
            return 24 / x;
        }
    }

    @Test
    public void testOnEnterChildren() {
        assertEquals(14, DivNodeGen.create(ConstNodeGen.create(42), ConstNodeGen.create(3)).execute(null));
        assertEquals(3, traceOnEnterCalled);
        assertArrayEquals(new Object[]{42, 3}, capturedArgs);
    }

    @Test
    public void testOnEnterOverloaded1() {
        assertEquals(2, OverloadedExecuteNodeGen.create().execute(14, 7));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14, 7}, capturedArgs);
    }

    @Test
    public void testOnEnterOverloaded2() {
        assertEquals("14abc", OverloadedExecuteNodeGen.create().execute(14, "abc"));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14, "abc"}, capturedArgs);
    }

    @Test
    public void testOnEnterOverloaded3() {
        assertEquals(17.14, OverloadedExecuteNodeGen.create().executeX(14, 3.14));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14L, 3.14}, capturedArgs);
    }

    @Test
    public void testOnEnterUncached() {
        assertEquals(7, OverloadedExecuteNodeGen.getUncached().execute(14, 2));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14, 2}, capturedArgs);
    }

    @Test
    public void testOnEnterDisabled() {
        assertEquals(6, TraceDisabledNodeGen.create().execute(4));
        assertEquals(0, traceOnEnterCalled);
        assertNull(capturedArgs);
    }

    @Test
    public void testOnExceptionChildren() {
        try {
            DivNodeGen.create(ConstNodeGen.create(42), ConstNodeGen.create(0)).execute(null);
            fail();
        } catch (ArithmeticException e) {
            assertEquals(1, traceOnExceptionCalled);
            assertSame(e, capturedThrowable);
        }
    }

    @Test
    public void testOnExceptionOverloaded() {
        try {
            OverloadedExecuteNodeGen.create().execute(14, 0);
            fail();
        } catch (ArithmeticException e) {
            assertEquals(1, traceOnExceptionCalled);
            assertSame(e, capturedThrowable);
        }
    }

    @Test
    public void testOnExceptionUncached() {
        try {
            OverloadedExecuteNodeGen.getUncached().execute(14, 0);
            fail();
        } catch (ArithmeticException e) {
            assertEquals(1, traceOnExceptionCalled);
            assertSame(e, capturedThrowable);
        }
    }

    @Test
    public void testOnExceptionDisabled() {
        try {
            TraceDisabledNodeGen.create().execute(0);
            fail();
        } catch (ArithmeticException e) {
            assertEquals(0, traceOnExceptionCalled);
            assertNull(capturedThrowable);
        }
    }
}
