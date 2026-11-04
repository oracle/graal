/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;
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
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
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
    public void testDirectCallExplicitBytecodeIndexOnException() {
        assertExplicitBytecodeIndexOnException(new DirectExplicitBytecodeIndexCallerRootNode());
    }

    @Test
    public void testInlinedDirectCallExplicitBytecodeIndexOnException() {
        DirectExplicitBytecodeIndexCallerRootNode root = new DirectExplicitBytecodeIndexCallerRootNode();
        root.callNode.forceInlining();
        assertExplicitBytecodeIndexOnException(root);
        assertTrue(((ThrowErrorRootNode) root.calleeRoot).compiled);
    }

    @Test
    public void testIndirectCallExplicitBytecodeIndexOnException() {
        assertExplicitBytecodeIndexOnException(new IndirectExplicitBytecodeIndexCallerRootNode());
    }

    @Test
    public void testDirectRootCallExplicitBytecodeIndexOnException() {
        DirectRootExplicitBytecodeIndexCallerRootNode root = new DirectRootExplicitBytecodeIndexCallerRootNode();
        root.getCallTarget();
        assertTrue(root.callNode.cloneCallTarget());
        assertTrue(root.callNode.isCallTargetCloned());
        assertExplicitBytecodeIndexOnException(root);
    }

    @Test
    public void testIndirectRootCallExplicitBytecodeIndexOnException() {
        assertExplicitBytecodeIndexOnException(new IndirectRootExplicitBytecodeIndexCallerRootNode());
    }

    @Test
    public void testDirectRootCallImplicitBytecodeIndexOnException() {
        DirectRootImplicitBytecodeIndexCallerRootNode root = new DirectRootImplicitBytecodeIndexCallerRootNode();
        root.getCallTarget();
        assertTrue(root.callNode.cloneCallTarget());
        assertTrue(root.callNode.isCallTargetCloned());

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        OptimizedCallTarget middleTarget = (OptimizedCallTarget) root.callNode.getCurrentCallTarget();
        CloningCaptureCallerRootNode middleRoot = (CloningCaptureCallerRootNode) middleTarget.getRootNode();
        @SuppressWarnings("unchecked")
        List<TruffleStackTraceElement> trace = (List<TruffleStackTraceElement>) target.call();
        assertEquals(3, trace.size());

        TruffleStackTraceElement middle = trace.get(1);
        assertSame(middleTarget, middle.getTarget());
        assertSame(middleRoot, middle.getLocation());
        assertEquals(0b11, middle.getBytecodeIndex());
        assertEquals(1, middleRoot.interpretedCount);

        TruffleStackTraceElement caller = trace.get(2);
        assertSame(target, caller.getTarget());
        assertSame(root.getCallNode(), caller.getLocation());
        assertEquals(DirectRootImplicitBytecodeIndexCallerRootNode.BYTECODE_INDEX, caller.getBytecodeIndex());
        assertEquals(1, root.captureCount);
    }

    private static void assertExplicitBytecodeIndexOnException(ExplicitBytecodeIndexCallerRootNode root) {
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        assertExplicitBytecodeIndexOnException(root, target, false);

        target.compile(true);
        target.waitForCompilation();
        assertTrue(target.isValid());
        assertExplicitBytecodeIndexOnException(root, target, true);
    }

    private static void assertExplicitBytecodeIndexOnException(ExplicitBytecodeIndexCallerRootNode root, OptimizedCallTarget target, boolean compiled) {
        assertFails(() -> target.call(root.getArguments()), TestException.class, (e) -> {
            List<TruffleStackTraceElement> trace = TruffleStackTrace.getStackTrace(e);
            assertEquals(2, trace.size());
            TruffleStackTraceElement caller = trace.get(1);
            assertSame(target, caller.getTarget());
            assertSame(root.getCallNode(), caller.getLocation());
            assertEquals(ExplicitBytecodeIndexCallerRootNode.BYTECODE_INDEX, caller.getBytecodeIndex());
        });
        assertEquals(compiled, root.compiled);
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

    abstract static class ExplicitBytecodeIndexCallerRootNode extends RootNode {

        static final int BYTECODE_INDEX = 42;

        final RootNode calleeRoot;
        final CallTarget callee;
        boolean compiled;

        ExplicitBytecodeIndexCallerRootNode() {
            this(new ThrowErrorRootNode());
        }

        ExplicitBytecodeIndexCallerRootNode(RootNode calleeRoot) {
            super(null);
            this.calleeRoot = calleeRoot;
            this.callee = calleeRoot.getCallTarget();
        }

        abstract Node getCallNode();

        Object[] getArguments() {
            return new Object[0];
        }

        final void recordExecutionMode() {
            compiled = CompilerDirectives.inCompiledCode();
        }

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            return 7;
        }
    }

    static final class DirectExplicitBytecodeIndexCallerRootNode extends ExplicitBytecodeIndexCallerRootNode {

        @Child private DirectCallNode callNode = DirectCallNode.create(callee);

        @Override
        Node getCallNode() {
            return callNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            recordExecutionMode();
            return callNode.call(BYTECODE_INDEX, new Object[0]);
        }
    }

    static final class IndirectExplicitBytecodeIndexCallerRootNode extends ExplicitBytecodeIndexCallerRootNode {

        @Child private IndirectCallNode callNode = IndirectCallNode.create();

        @Override
        Node getCallNode() {
            return callNode;
        }

        @Override
        Object[] getArguments() {
            return new Object[]{callee};
        }

        @Override
        public Object execute(VirtualFrame frame) {
            recordExecutionMode();
            CallTarget target = (CallTarget) frame.getArguments()[0];
            return callNode.call(target, BYTECODE_INDEX, new Object[0]);
        }
    }

    static final class DirectRootExplicitBytecodeIndexCallerRootNode extends ExplicitBytecodeIndexCallerRootNode {

        @Child private DirectCallNode callNode = DirectCallNode.create(callee);

        DirectRootExplicitBytecodeIndexCallerRootNode() {
            super(new CloningThrowErrorRootNode());
        }

        @Override
        Node getCallNode() {
            return callNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            recordExecutionMode();
            return callNode.call(CloningThrowErrorRootNode.class, BYTECODE_INDEX, new Object[0]);
        }
    }

    static final class DirectRootImplicitBytecodeIndexCallerRootNode extends ExplicitBytecodeIndexCallerRootNode {

        static final int BYTECODE_INDEX = 7;

        @Child private DirectCallNode callNode = DirectCallNode.create(callee);
        int captureCount;

        DirectRootImplicitBytecodeIndexCallerRootNode() {
            super(new CloningCaptureCallerRootNode());
        }

        @Override
        Node getCallNode() {
            return callNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            recordExecutionMode();
            return callNode.call(CloningCaptureCallerRootNode.class, -1, new Object[0]);
        }

        @Override
        protected boolean isCaptureFramesForTrace(boolean compiledFrame) {
            captureCount++;
            return true;
        }

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            return frame == null ? -1 : BYTECODE_INDEX;
        }
    }

    static final class IndirectRootExplicitBytecodeIndexCallerRootNode extends ExplicitBytecodeIndexCallerRootNode {

        @Child private IndirectCallNode callNode = IndirectCallNode.create();

        @Override
        Node getCallNode() {
            return callNode;
        }

        @Override
        Object[] getArguments() {
            return new Object[]{calleeRoot};
        }

        @Override
        public Object execute(VirtualFrame frame) {
            recordExecutionMode();
            ThrowErrorRootNode root = (ThrowErrorRootNode) frame.getArguments()[0];
            return callNode.call(root, BYTECODE_INDEX, new Object[0]);
        }
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
    public void testCachedInlinedTargetPreparedOnce() {
        CountingPrepareRootNode callee = new CountingPrepareRootNode();
        callee.getCallTarget().call(); // ensure initialized for inlining
        TwoDirectCallsRootNode caller = new TwoDirectCallsRootNode(callee, callee);
        OptimizedCallTarget target = (OptimizedCallTarget) caller.getCallTarget();

        target.compile(true);
        target.waitForCompilation();

        assertTrue(target.isValidLastTier());
        assertEquals(2, target.call());
        assertEquals(1, callee.prepareCount);
    }

    @Test
    public void testDistinctInlinedTargetsPreparedOnceEach() {
        CountingPrepareRootNode firstCallee = new CountingPrepareRootNode();
        CountingPrepareRootNode secondCallee = new CountingPrepareRootNode();
        firstCallee.getCallTarget().call(); // ensure initialized for inlining
        secondCallee.getCallTarget().call(); // ensure initialized for inlining
        TwoDirectCallsRootNode caller = new TwoDirectCallsRootNode(firstCallee, secondCallee);
        OptimizedCallTarget target = (OptimizedCallTarget) caller.getCallTarget();

        target.compile(true);
        target.waitForCompilation();

        assertTrue(target.isValidLastTier());
        assertEquals(2, target.call());
        assertEquals(1, firstCallee.prepareCount);
        assertEquals(1, secondCallee.prepareCount);
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

    static final class CountingPrepareRootNode extends RootNode {

        private volatile int prepareCount;

        CountingPrepareRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 1;
        }

        @Override
        protected boolean isTrivial() {
            return true;
        }

        @Override
        protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            prepareCount++;
            return true;
        }
    }

    static final class TwoDirectCallsRootNode extends RootNode {

        @Child private DirectCallNode firstCall;
        @Child private DirectCallNode secondCall;

        TwoDirectCallsRootNode(RootNode firstCallee, RootNode secondCallee) {
            super(null);
            firstCall = DirectCallNode.create(firstCallee.getCallTarget());
            firstCall.forceInlining();
            secondCall = DirectCallNode.create(secondCallee.getCallTarget());
            secondCall.forceInlining();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return (int) firstCall.call() + (int) secondCall.call();
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

        boolean compiled;

        @Override
        public Object execute(VirtualFrame frame) {
            compiled = CompilerDirectives.inCompiledCode();
            throw new TestException(this);
        }

    }

    static final class CloningThrowErrorRootNode extends ThrowErrorRootNode {

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    static class CaptureStackRootNode extends BaseRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return TruffleStackTrace.getStackTrace(new TestException(this));
        }

    }

    static final class CloningCaptureCallerRootNode extends BaseRootNode {

        private final CallTarget target = new CaptureStackRootNode().getCallTarget();

        @Override
        public Object execute(VirtualFrame frame) {
            return target.call(this);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    @SuppressWarnings("serial")
    static class TestException extends AbstractTruffleException {

        TestException(Node location) {
            super(location);
        }

    }

}
