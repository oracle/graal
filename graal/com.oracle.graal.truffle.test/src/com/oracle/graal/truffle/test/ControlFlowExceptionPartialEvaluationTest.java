/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import org.junit.*;

import com.oracle.graal.truffle.test.nodes.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

@Ignore("Currently ignored due to problems with code coverage tools.")
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

    public static class ThrowControlFlowExceptionTestNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            throw new ControlFlowException();
        }
    }

    public static class CatchControlFlowExceptionTestNode extends AbstractTestNode {

        @Child private AbstractTestNode child;

        public CatchControlFlowExceptionTestNode(AbstractTestNode child) {
            this.child = adoptChild(child);
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
            this.child = adoptChild(child);
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
}
