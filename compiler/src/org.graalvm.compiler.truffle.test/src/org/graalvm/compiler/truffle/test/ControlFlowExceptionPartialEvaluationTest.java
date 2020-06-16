/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.SlowPathException;

public class ControlFlowExceptionPartialEvaluationTest extends PartialEvaluationTest {
    public static Object constant42() {
        return 42;
    }

    @Test
    public void catchControlFlowException() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new CatchControlFlowExceptionTestNode(new ThrowControlFlowExceptionTestNode());
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "catchControlFlowException", result));
    }

    @Test
    public void catchSlowPathAndControlFlowException() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new CatchSlowPathAndControlFlowExceptionTestNode(new ThrowControlFlowExceptionTestNode());
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "catchSlowPathAndControlFlowException", result));
    }

    @Test
    public void catchControlFlowExceptionWithLoopExplosion() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new CatchControlFlowExceptionTestNode(new BlockTestNode(new ThrowControlFlowExceptionTestNode()));
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "catchControlFlowExceptionWithLoopExplosion", result));
    }

    @Test
    public void catchControlFlowExceptionFromCall() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.Inlining", "true").build());
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new RootTestNode(new FrameDescriptor(), "throwControlFlowException", new ThrowControlFlowExceptionTestNode()));
        AbstractTestNode result = new CatchControlFlowExceptionTestNode(new CallTestNode(callTarget));
        assertPartialEvalEquals("constant42", new RootTestNode(new FrameDescriptor(), "catchControlFlowExceptionFromCall", result));
    }

    public static class ThrowControlFlowExceptionTestNode extends AbstractTestNode {
        @Override
        public int execute(VirtualFrame frame) {
            throw new ControlFlowException();
        }
    }

    public static class CatchControlFlowExceptionTestNode extends AbstractTestNode {
        @Child private AbstractTestNode child;

        public CatchControlFlowExceptionTestNode(AbstractTestNode child) {
            this.child = child;
        }

        @Override
        public int execute(VirtualFrame frame) {
            try {
                return child.execute(frame);
            } catch (ControlFlowException e) {
                return 42;
            }
        }
    }

    public static class CatchSlowPathAndControlFlowExceptionTestNode extends AbstractTestNode {
        @Child private AbstractTestNode child;

        public CatchSlowPathAndControlFlowExceptionTestNode(AbstractTestNode child) {
            this.child = child;
        }

        @Override
        public int execute(VirtualFrame frame) {
            try {
                return executeChild(frame);
            } catch (SlowPathException spe) {
                return -1;
            } catch (ControlFlowException e) {
                return 42;
            }
        }

        @SuppressWarnings("unused")
        private int executeChild(VirtualFrame frame) throws SlowPathException {
            return child.execute(frame);
        }
    }

    public static class BlockTestNode extends AbstractTestNode {
        @Children private final AbstractTestNode[] statements;

        public BlockTestNode(AbstractTestNode... statements) {
            this.statements = statements;
        }

        @Override
        public int execute(VirtualFrame frame) {
            return executeSpecial(frame);
        }

        /*
         * A statically resolvable method, so that ExplodeLoop annotation is visible during parsing.
         */
        @ExplodeLoop
        private int executeSpecial(VirtualFrame frame) {
            int result = 0;
            for (AbstractTestNode statement : statements) {
                result = statement.execute(frame);
            }
            return result;
        }
    }

    public static class CallTestNode extends AbstractTestNode {
        @Child private DirectCallNode callNode;

        public CallTestNode(CallTarget callTarget) {
            this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        }

        @Override
        public int execute(VirtualFrame frame) {
            return (int) callNode.call(new Object[0]);
        }
    }
}
