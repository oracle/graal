/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;

public class TruffleExceptionPartialEvaluationTest extends PartialEvaluationTest {
    public static Object constant42() {
        return 42;
    }

    @Test
    public void testTruffleException() {
        assertPartialEvalEquals("constant42", createCallerChain(0, 0));
        assertPartialEvalEquals("constant42", createCallerChain(3, 0));
        assertPartialEvalEquals("constant42", createCallerChain(0, 3));
        assertPartialEvalEquals("constant42", createCallerChain(4, 4));
    }

    private static RootTestNode createCallerChain(int framesAbove, int framesBelow) {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode calleeNode = new ThrowTruffleExceptionTestNode(-1, true);
        RootTestNode calleeRoot = new RootTestNode(fd, "testTruffleException", calleeNode);
        for (int i = 0; i < framesAbove; i++) {
            AbstractTestNode call = new CallTestNode(Truffle.getRuntime().createCallTarget(calleeRoot));
            calleeRoot = new RootTestNode(fd, "testTruffleException", call);
        }
        AbstractTestNode callerNode = new CallTestNode(Truffle.getRuntime().createCallTarget(calleeRoot));
        AbstractTestNode catchNode = new CatchTruffleExceptionTestNode(callerNode);
        RootTestNode callerRoot = new RootTestNode(fd, "testTruffleException", catchNode);
        for (int i = 0; i < framesBelow; i++) {
            AbstractTestNode call = new CallTestNode(Truffle.getRuntime().createCallTarget(callerRoot));
            callerRoot = new RootTestNode(fd, "testTruffleException", call);
        }
        return callerRoot;
    }

    private static final class TestTruffleException extends RuntimeException implements TruffleException {

        private static final long serialVersionUID = -6105288741119318027L;

        private final int stackTraceElementLimit;
        private final Node location;
        private final boolean property;

        TestTruffleException(int stackTraceElementLimit, Node location, boolean property) {
            this.stackTraceElementLimit = stackTraceElementLimit;
            this.location = location;
            this.property = property;
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        @Override
        public Node getLocation() {
            return location;
        }

        @Override
        public int getStackTraceElementLimit() {
            return stackTraceElementLimit;
        }
    }

    public static class CatchTruffleExceptionTestNode extends AbstractTestNode {

        @Child private AbstractTestNode child;

        public CatchTruffleExceptionTestNode(AbstractTestNode child) {
            this.child = child;
        }

        @Override
        public int execute(VirtualFrame frame) {
            try {
                return child.execute(frame);
            } catch (TestTruffleException e) {
                if (e.property) {
                    return 42;
                }
                throw e;
            }
        }
    }

    public static class ThrowTruffleExceptionTestNode extends AbstractTestNode {

        private final int limit;
        private final boolean property;

        public ThrowTruffleExceptionTestNode(int limit, boolean property) {
            this.limit = limit;
            this.property = property;
        }

        @Override
        public int execute(VirtualFrame frame) {
            throw new TestTruffleException(limit, this, property);
        }
    }

    public static class CallTestNode extends AbstractTestNode {
        @Child private DirectCallNode callNode;

        public CallTestNode(CallTarget callTarget) {
            this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
            this.callNode.forceInlining();
        }

        @Override
        public int execute(VirtualFrame frame) {
            return (int) callNode.call(new Object[0]);
        }
    }
}
