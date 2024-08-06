/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class RootNodeCompilationTest extends TestWithSynchronousCompiling {

    @Test
    public void testLazyStack() {
        var throw0 = new ThrowErrorRootNode();
        var root0 = new ConstantTargetRootNode(throw0);
        var root1 = new ConstantTargetRootNode(root0);

        OptimizedCallTarget target = (OptimizedCallTarget) root1.getCallTarget();
        assertFails(() -> target.call(), TestException.class, (e) -> {
            for (TruffleStackTraceElement stack : TruffleStackTrace.getStackTrace(e)) {
                BaseRootNode root = (BaseRootNode) stack.getTarget().getRootNode();
                assertEquals(0b11, stack.getBytecodeIndex());
                assertEquals(0, root.compiledCount);
                assertEquals(1, root.interpretedCount);

            }
        });
        target.compile(true);
        assertFails(() -> target.call(), TestException.class, (e) -> {
            for (TruffleStackTraceElement stack : TruffleStackTrace.getStackTrace(e)) {
                BaseRootNode root = (BaseRootNode) stack.getTarget().getRootNode();
                assertEquals(0b01, stack.getBytecodeIndex());
                assertEquals(1, root.compiledCount);
                assertEquals(1, root.interpretedCount);
            }
        });
        assertTrue(target.isValid());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCaptureStack() {
        var throw0 = new CaptureStackRootNode();
        var root0 = new ConstantTargetRootNode(throw0);
        var root1 = new ConstantTargetRootNode(root0);

        OptimizedCallTarget target = (OptimizedCallTarget) root1.getCallTarget();

        List<TruffleStackTraceElement> trace = (List<TruffleStackTraceElement>) target.call();
        for (TruffleStackTraceElement stack : trace) {
            BaseRootNode root = (BaseRootNode) stack.getTarget().getRootNode();
            assertEquals(0b11, stack.getBytecodeIndex());
            assertEquals(0, root.compiledCount);
            assertEquals(1, root.interpretedCount);
        }

        target.compile(true);

        trace = (List<TruffleStackTraceElement>) target.call();
        for (TruffleStackTraceElement stack : trace) {
            BaseRootNode root = (BaseRootNode) stack.getTarget().getRootNode();
            assertEquals(0b01, stack.getBytecodeIndex());
            assertEquals(1, root.compiledCount);
            assertEquals(1, root.interpretedCount);
        }

        assertTrue(target.isValid());
    }

    abstract static class BaseRootNode extends RootNode {

        int compiledCount;
        int interpretedCount;

        protected BaseRootNode() {
            super(null);
        }

        @Override
        protected boolean isCaptureFramesForTrace(boolean compiledFrame) {
            if (compiledFrame) {
                compiledCount++;
            } else {
                interpretedCount++;
            }
            return !compiledFrame;
        }

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            int index = 0;
            if (node != null) {
                index |= 0b1;
            }
            if (frame != null) {
                index |= 0b10;
            }
            return index;
        }
    }

    static class ConstantTargetRootNode extends BaseRootNode {

        // deliberately
        final CallTarget target;

        protected ConstantTargetRootNode(RootNode target) {
            this.target = target.getCallTarget();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (target != null) {
                return target.call(this);
            }
            return null;
        }

    }

    static class ThrowErrorRootNode extends BaseRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            throw new TestException(this);
        }

    }

    static class CaptureStackRootNode extends BaseRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return TruffleStackTrace.getStackTrace(new TestException(this));
        }

    }

    @SuppressWarnings("serial")
    static class TestException extends AbstractTruffleException {

        TestException(Node location) {
            super(location);
        }

    }

}
