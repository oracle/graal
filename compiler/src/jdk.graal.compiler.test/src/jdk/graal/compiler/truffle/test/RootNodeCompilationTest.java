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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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

    @Test
    public void testPrepareForCompilationLastTier() {
        PrepareRootNode node = new PrepareRootNode(true);
        OptimizedCallTarget target = (OptimizedCallTarget) node.getCallTarget();
        target.compile(true);
        target.waitForCompilation();
        assertTrue(target.isValidLastTier());
        assertEquals(new CompilationData(true, 2, true), target.call());
        assertEquals(1, node.prepareCount);
        assertTrue(target.isValidLastTier());
    }

    @Test
    public void testPrepareForCompilationLastTierReprofile() {
        PrepareRootNode node = new PrepareRootNode(false);
        OptimizedCallTarget target = (OptimizedCallTarget) node.getCallTarget();
        target.compile(true);
        target.waitForCompilation();
        assertFalse(target.isValidLastTier());
        assertNull(target.call());
        assertEquals(new CompilationData(true, 2, true), node.compilationData);
        assertEquals(1, node.prepareCount);

        target.invalidate("test");
        node.returnValue = true;

        target.compile(true);
        target.waitForCompilation();
        assertEquals(new CompilationData(true, 2, true), target.call());
        assertEquals(2, node.prepareCount);
        assertTrue(target.isValidLastTier());
    }

    @Test
    public void testPrepareForCompilationFirstTier() {
        PrepareRootNode node = new PrepareRootNode(true);
        OptimizedCallTarget target = (OptimizedCallTarget) node.getCallTarget();
        target.compile(false);
        target.waitForCompilation();
        assertTrue(target.isValid());
        assertEquals(new CompilationData(true, 1, false), target.call());
        assertEquals(1, node.prepareCount);
        assertTrue(target.isValid());
    }

    @Test
    public void testPrepareForCompilationFirstTierReprofile() {
        PrepareRootNode node = new PrepareRootNode(false);
        OptimizedCallTarget target = (OptimizedCallTarget) node.getCallTarget();
        target.compile(false);
        target.waitForCompilation();
        assertFalse(target.isValid());
        assertNull(target.call());
        assertEquals(new CompilationData(true, 1, false), node.compilationData);
        assertEquals(1, node.prepareCount);

        target.invalidate("test");
        node.returnValue = true;

        target.compile(false);
        target.waitForCompilation();
        assertEquals(new CompilationData(true, 1, false), target.call());
        assertEquals(2, node.prepareCount);
        assertTrue(target.isValid());

    }

    @Test
    public void testPrepareForCompilationInlined() {
        PrepareRootNode node = new PrepareRootNode(true);
        node.getCallTarget().call(); // ensure initialized for inlining
        ConstantTargetRootNode call = new ConstantTargetRootNode(node);
        OptimizedCallTarget target = (OptimizedCallTarget) call.getCallTarget();
        target.compile(true);
        target.waitForCompilation();
        assertTrue(target.isValidLastTier());
        assertEquals(1, node.prepareCount);
        assertEquals(new CompilationData(false, 2, true), target.call());
        assertTrue(target.isValidLastTier());
    }

    @Test
    public void testPrepareForCompilationInlinedReprofile() {
        PrepareRootNode node = new PrepareRootNode(false);
        node.getCallTarget().call(); // ensure initialized for inlining
        ConstantTargetRootNode call = new ConstantTargetRootNode(node);
        OptimizedCallTarget target = (OptimizedCallTarget) call.getCallTarget();
        target.compile(true);
        target.waitForCompilation();
        assertTrue(target.isValidLastTier());
        assertNull(target.call());
        assertEquals(new CompilationData(false, 2, true), node.compilationData);
        assertEquals(1, node.prepareCount);

        target.invalidate("test");
        node.returnValue = true;

        target.compile(true);
        target.waitForCompilation();
        assertTrue(target.isValidLastTier());
        assertEquals(2, node.prepareCount);
        assertEquals(new CompilationData(false, 2, true), target.call());
        assertTrue(target.isValidLastTier());
    }

    record CompilationData(boolean rootCompilation, int compilationTier, boolean lastTier) {
    }

    static final class PrepareRootNode extends BaseRootNode {

        private volatile int prepareCount = 0;
        @CompilationFinal volatile boolean returnValue;
        @CompilationFinal volatile CompilationData compilationData;

        PrepareRootNode(boolean returnValue) {
            this.returnValue = returnValue;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                return compilationData;
            }
            return null;
        }

        @Override
        protected boolean isTrivial() {
            return true;
        }

        @SuppressWarnings("hiding")
        @Override
        protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            this.prepareCount++;
            this.compilationData = new CompilationData(rootCompilation, compilationTier, lastTier);
            return returnValue;
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
