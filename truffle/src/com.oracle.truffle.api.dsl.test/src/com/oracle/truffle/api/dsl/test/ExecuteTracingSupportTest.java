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

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.dsl.ExecuteTracingSupport;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.ConstNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.DivNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.ObjectArrayArgNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.OverloadedExecuteNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.TraceDisabledNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.VoidExecuteWithNonVoidSpecializationNodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteTracingSupportTestFactory.VoidNoArgsNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class ExecuteTracingSupportTest {

    private static int traceOnEnterCalled;
    private static int traceOnExceptionCalled;
    private static int traceOnReturnCalled;
    private static Object[] capturedArgs;
    private static Throwable capturedThrowable;
    private static Object capturedReturnValue;

    @Before
    @SuppressWarnings("static-method")
    public final void setup() {
        traceOnEnterCalled = 0;
        traceOnExceptionCalled = 0;
        traceOnReturnCalled = 0;
    }

    abstract static class TracingBaseNode extends Node implements ExecuteTracingSupport {
        @Override
        public boolean isTracingEnabled() {
            return true;
        }

        @Override
        public void traceOnEnter(Object[] arguments) {
            traceOnEnterCalled++;
            capturedArgs = arguments;
        }

        @Override
        public void traceOnReturn(Object returnValue) {
            traceOnReturnCalled++;
            capturedReturnValue = returnValue;
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
        int doIt(@SuppressWarnings("unused") VirtualFrame frame) {
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

    abstract static class VoidNoArgsNode extends TracingBaseNode {
        abstract void execute();

        @Specialization
        void doIt() {
        }
    }

    abstract static class VoidExecuteWithNonVoidSpecializationNode extends TracingBaseNode {
        abstract void execute(int a);

        abstract long execute(long a);

        @Specialization
        long doInt(int a) {
            return a;
        }

        @Specialization
        long doLong(long a) {
            return a;
        }
    }

    abstract static class ObjectArrayArgNode extends TracingBaseNode {
        abstract Object execute(Object[] arg);

        @Specialization
        Object doInt(Object[] arg) {
            return arg;
        }
    }

    @Test
    public void testChildren() {
        assertEquals(14, DivNodeGen.create(ConstNodeGen.create(42), ConstNodeGen.create(3)).execute(null));
        assertEquals(3, traceOnEnterCalled);
        assertArrayEquals(new Object[]{42, 3}, capturedArgs);
        assertEquals(3, traceOnReturnCalled);
        assertEquals(14, capturedReturnValue);
    }

    @Test
    public void testOverloaded1() {
        assertEquals(2, OverloadedExecuteNodeGen.create().execute(14, 7));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14, 7}, capturedArgs);
        assertEquals(1, traceOnReturnCalled);
        assertEquals(2, capturedReturnValue);
    }

    @Test
    public void testOverloaded2() {
        assertEquals("14abc", OverloadedExecuteNodeGen.create().execute(14, "abc"));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14, "abc"}, capturedArgs);
        assertEquals(1, traceOnReturnCalled);
        assertEquals("14abc", capturedReturnValue);
    }

    @Test
    public void testOverloaded3() {
        assertEquals(17.14, OverloadedExecuteNodeGen.create().executeX(14, 3.14));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14L, 3.14}, capturedArgs);
        assertEquals(1, traceOnReturnCalled);
        assertEquals(17.14, capturedReturnValue);
    }

    @Test
    public void testUncached() {
        assertEquals(7, OverloadedExecuteNodeGen.getUncached().execute(14, 2));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14, 2}, capturedArgs);
        assertEquals(1, traceOnReturnCalled);
        assertEquals(7, capturedReturnValue);
    }

    @Test
    public void testDisabled() {
        assertEquals(6, TraceDisabledNodeGen.create().execute(4));
        assertEquals(0, traceOnEnterCalled);
        assertEquals(0, traceOnReturnCalled);
    }

    @Test
    public void testVoidNoArgs() {
        VoidNoArgsNodeGen.create().execute();
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{}, capturedArgs);
        assertEquals(1, traceOnReturnCalled);
        assertNull(capturedReturnValue);
    }

    @Test
    public void testVoidExecuteWithNonVoidSpecialization() {
        // A void execute() overload is being invoked here, so traceOnReturn should be called with
        // null even though a @Specialization actually returns a value.
        VoidExecuteWithNonVoidSpecializationNodeGen.create().execute(14);
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{14}, capturedArgs);
        assertEquals(1, traceOnReturnCalled);
        assertNull(capturedReturnValue);
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
        }
    }

    @Test
    public void testObjectArrayArg() {
        Object[] arg = {42, "abc"};
        assertSame(arg, ObjectArrayArgNodeGen.create().execute(arg));
        assertEquals(1, traceOnEnterCalled);
        assertArrayEquals(new Object[]{arg}, capturedArgs);
        assertEquals(1, traceOnReturnCalled);
        assertSame(arg, capturedReturnValue);
    }

    @GenerateLibrary
    public abstract static class ALibrary extends Library {
        public abstract Object aMessage(Object receiver, Object arg);
    }

    @ExportLibrary(ALibrary.class)
    public static final class ALibraryImpl {
        @ExportMessage
        @ExpectError("@ExportMessage annotated nodes do not support execute tracing. Remove the ExecuteTracingSupport interface to resolve this.")
        public static class AMessage implements ExecuteTracingSupport {
            @Specialization
            static int doIt(@SuppressWarnings("unused") ALibraryImpl receiver, int arg) {
                return arg;
            }

            @Override
            public boolean isTracingEnabled() {
                return true;
            }
        }
    }
}
