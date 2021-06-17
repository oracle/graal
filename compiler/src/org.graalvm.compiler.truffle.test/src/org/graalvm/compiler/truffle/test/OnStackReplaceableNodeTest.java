/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.OnStackReplaceableNode;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;

public class OnStackReplaceableNodeTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    // 20s timeout
//    @Rule public TestRule timeout = new Timeout(20, TimeUnit.SECONDS);

    private int osrThreshold;

    @Before
    @Override
    public void before() {
        // Use a multiple of the poll interval, so OSR triggers immediately when it hits the
        // threshold.
        osrThreshold = 10 * OptimizedCallTarget.OSR_POLL_INTERVAL;
        setupContext("engine.MultiTier", "false", "engine.OSR", "true", "engine.OSRCompilationThreshold", String.valueOf(osrThreshold));
    }

    /*
     * Test that an infinite interpreter loop triggers OSR.
     */
    @Test
    public void testSimpleInterpreterLoop() {
        RootNode rootNode = new Program(new InfiniteInterpreterLoop(), null);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        // Interpreter invocation should be OSR compiled and break out of the interpreter loop.
        Assert.assertEquals(42, target.call());
        Assert.assertTrue("reportOSRBackEdge should increment loop counts", target.getCallAndLoopCount() - target.getCallCount() >= osrThreshold);
    }

    /*
     * Test that a loop which just exceeds the threshold triggers OSR.
     */
    @Test
    public void testFixedIterationLoop() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FixedIterationLoop osrNode = new FixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold + 1));
    }

    /*
     * Test that a loop just below the OSR threshold does not trigger OSR.
     */
    @Test
    public void testFixedIterationLoopBelowThreshold() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FixedIterationLoop osrNode = new FixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(FixedIterationLoop.NORMAL_RESULT, target.call(osrThreshold));
    }

    /*
     * Test that OSR is triggered in the expected location when multiple loops are involved.
     */
    @Test
    public void testMultipleLoops() {
        // Each loop runs for osrThreshold + 1 iterations, so the first loop should trigger OSR.
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        TwoFixedIterationLoops osrNode = new TwoFixedIterationLoops(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(TwoFixedIterationLoops.OSR_IN_FIRST_LOOP, target.call(osrThreshold + 1));

        // Each loop runs for osrThreshold/2 + 1 iterations, so the second loop should trigger OSR.
        frameDescriptor = new FrameDescriptor();
        osrNode = new TwoFixedIterationLoops(frameDescriptor);
        rootNode = new Program(osrNode, frameDescriptor);
        target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(TwoFixedIterationLoops.OSR_IN_SECOND_LOOP, target.call(osrThreshold / 2 + 1));

        // Each loop runs for osrThreshold/2 iterations, so OSR should not get triggered.
        frameDescriptor = new FrameDescriptor();
        osrNode = new TwoFixedIterationLoops(frameDescriptor);
        rootNode = new Program(osrNode, frameDescriptor);
        target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(TwoFixedIterationLoops.NO_OSR, target.call(osrThreshold / 2));
    }

    /*
     * Test that OSR fails if the code cannot be compiled.
     */
    @Test
    public void testFailedCompilation() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        UncompilableFixedIterationLoop osrNode = new UncompilableFixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(FixedIterationLoop.NORMAL_RESULT, target.call(osrThreshold + 1));
    }

    /*
     * Test that node replacement in the base node invalidates the OSR target.
     */
    @Test
    public void testInvalidateOnNodeReplaced() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FixedIterationLoop osrNode = new FixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold + 1));
        OptimizedCallTarget.OSRState osrState = osrNode.getGraalOSRState();
        OptimizedCallTarget osrTarget = osrState.getOSRCompilations().get(-1);
        Assert.assertNotNull(osrTarget);
        Assert.assertTrue(osrTarget.isValid());
        osrNode.nodeReplaced(osrNode, new FixedIterationLoop(new FrameDescriptor()), "something changed");
        Assert.assertTrue(osrState.getOSRCompilations().isEmpty());
        Assert.assertFalse(osrTarget.isValid());
        // Calling the node will eventually trigger OSR again (after OSR_POLL_INTERVAL back-edges)
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(OptimizedCallTarget.OSR_POLL_INTERVAL + 1));
        osrTarget = osrState.getOSRCompilations().get(-1);
        Assert.assertNotNull(osrTarget);
        Assert.assertTrue(osrTarget.isValid());
    }

    /*
     * Test that OSR will not proceed if the frame has been materialized.
     */
    @Test
    public void testOSRWithMaterializedFrame() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FixedIterationLoop osrNode = new FixedIterationLoop(frameDescriptor);
        RootNode rootNode = new MaterializedFrameProgram(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(FixedIterationLoop.NORMAL_RESULT, target.call(osrThreshold + 1));
        // Compiled call target is still valid.
        OptimizedCallTarget.OSRState osrState = osrNode.getGraalOSRState();
        OptimizedCallTarget osrTarget = osrState.getOSRCompilations().get(-1);
        Assert.assertNotNull(osrTarget);
        Assert.assertTrue(osrTarget.isValid());

    }

    /*
     * Test that OSR compilation gets polled when compilation is asynchronous.
     */
    @Test
    public void testOSRPolling() {
        setupContext(
                        "engine.MultiTier", "false",
                        "engine.OSR", "true",
                        "engine.OSRCompilationThreshold", String.valueOf(osrThreshold),
                        "engine.BackgroundCompilation", Boolean.TRUE.toString() // override defaults
        );
        InfiniteInterpreterLoop osrNode = new InfiniteInterpreterLoop();
        RootNode rootNode = new Program(osrNode, new FrameDescriptor());
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(42, target.call());
        OptimizedCallTarget.OSRState osrState = osrNode.getGraalOSRState();
        int backEdgeCount = osrState.getBackEdgeCount();
        Assert.assertTrue(backEdgeCount > osrThreshold);
        Assert.assertEquals(0, backEdgeCount % OptimizedCallTarget.OSR_POLL_INTERVAL);
    }

    /*
     * Test that the OSR call target does not get included in the Truffle stack trace.
     */
    @Test
    public void testStackTraceHidesOSRCallTarget() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        CheckStackWalkCallTarget osrNode = new CheckStackWalkCallTarget(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(2 * osrThreshold));
    }

    /*
     * Test that the OSR frame is used in the Truffle stack trace.
     */
    @Test
    public void testStackTraceUsesOSRFrame() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        CheckStackWalkFrame osrNode = new CheckStackWalkFrame(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        osrNode.callTarget = target; // set the call target so stack walking can use it
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(2 * osrThreshold));
    }

    /*
     * Test that the topmost OSR frame is used in the Truffle stack trace when there are multiple
     * levels of OSR.
     */
    @Test
    public void testStackTraceUsesNewestOSRFrame() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        CheckStackWalkFrameNested osrNode = new CheckStackWalkFrameNested(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        osrNode.callTarget = target; // set the call target so stack walking can use it
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(3 * osrThreshold));
        Assert.assertTrue(osrNode.hasDeoptimizedYet);
    }

    // Bytecode programs
    /*
     * do {
     *   input1 -= 1;
     *   result += 3;
     * } while (input1);
     * return result;
     */
    byte[] tripleInput1 = new byte[]{
                    /* 0: */BytecodeNode.Bytecode.DEC, 0,
                    /* 2: */BytecodeNode.Bytecode.INC, 2,
                    /* 4: */BytecodeNode.Bytecode.INC, 2,
                    /* 6: */BytecodeNode.Bytecode.INC, 2,
                    /* 8: */BytecodeNode.Bytecode.JMPNONZERO, 0, -8,
                    /* 11: */BytecodeNode.Bytecode.RETURN, 2
    };

    /*
     * do {
     *   input1--;
     *   temp = input2;
     *   do {
     *     temp--;
     *     result++;
     *   } while(temp);
     * } while(input1);
     * return result;
     */
    byte[] multiplyInputs = new byte[]{
                    /* 0: */BytecodeNode.Bytecode.DEC, 0,
                    /* 2: */BytecodeNode.Bytecode.COPY, 1, 2,
                    /* 5: */BytecodeNode.Bytecode.DEC, 2,
                    /* 7: */BytecodeNode.Bytecode.INC, 3,
                    /* 9: */BytecodeNode.Bytecode.JMPNONZERO, 2, -4,
                    /* 12: */BytecodeNode.Bytecode.JMPNONZERO, 0, -12,
                    /* 15: */BytecodeNode.Bytecode.RETURN, 3
    };

    /*
     * Tests to validate the OSR mechanism with bytecode interpreters.
     */
    @Test
    public void testOSRInBytecodeLoop() {
        // osrThreshold + 1 back-edges -> compiled
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        BytecodeNode bytecodeNode = new BytecodeNode(3, frameDescriptor, tripleInput1);
        RootNode rootNode = new Program(bytecodeNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(3 * (osrThreshold + 1), target.call(osrThreshold + 1, 0));
        Assert.assertTrue(bytecodeNode.compiled);
        OptimizedCallTarget.OSRState osrState = (OptimizedCallTarget.OSRState) bytecodeNode.getOSRState();
        Assert.assertTrue(osrState.getOSRCompilations().containsKey(0));
        Assert.assertTrue(osrState.getOSRCompilations().get(0).isValid());
    }

    @Test
    public void testNoOSRInBytecodeLoop() {
        // osrThreshold back-edges -> not compiled
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        BytecodeNode bytecodeNode = new BytecodeNode(3, frameDescriptor, tripleInput1);
        RootNode rootNode = new Program(bytecodeNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(3 * osrThreshold, target.call(osrThreshold, 0));
        Assert.assertFalse(bytecodeNode.compiled);
        OptimizedCallTarget.OSRState osrState = (OptimizedCallTarget.OSRState) bytecodeNode.getOSRState();
        Assert.assertTrue(osrState.getOSRCompilations().isEmpty());
    }

    @Test
    public void testOSRInBytecodeOuterLoop() {
        // computes osrThreshold * 2
        // Inner loop contributes 1 back-edge, so each outer loop contributes 2 back-edges, and
        // the even-valued osrThreshold gets hit by the outer loop back-edge.
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        BytecodeNode bytecodeNode = new BytecodeNode(4, frameDescriptor, multiplyInputs);
        RootNode rootNode = new Program(bytecodeNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(2 * osrThreshold, target.call(osrThreshold, 2));
        Assert.assertTrue(bytecodeNode.compiled);
        OptimizedCallTarget.OSRState osrState = (OptimizedCallTarget.OSRState) bytecodeNode.getOSRState();
        Assert.assertTrue(osrState.getOSRCompilations().containsKey(0));
        Assert.assertTrue(osrState.getOSRCompilations().get(0).isValid());
    }

    @Test
    public void testOSRInBytecodeInnerLoop() {
        // computes 2 * (osrThreshold - 1)
        // Inner loop contributes osrThreshold-2 back-edges, so the first outer loop contributes
        // osrThreshold-1 back-edges, then the next back-edge (which triggers OSR) is from the inner
        // loop.
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        BytecodeNode bytecodeNode = new BytecodeNode(4, frameDescriptor, multiplyInputs);
        RootNode rootNode = new Program(bytecodeNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(2 * (osrThreshold - 1), target.call(2, osrThreshold - 1));
        Assert.assertTrue(bytecodeNode.compiled);
        OptimizedCallTarget.OSRState osrState = (OptimizedCallTarget.OSRState) bytecodeNode.getOSRState();
        Assert.assertTrue(osrState.getOSRCompilations().containsKey(5));
        Assert.assertTrue(osrState.getOSRCompilations().get(5).isValid());
    }

    public static class Program extends RootNode {
        @Child OnStackReplaceableNode osrNode;

        public Program(OnStackReplaceableNode osrNode, FrameDescriptor frameDescriptor) {
            super(null, frameDescriptor);
            this.osrNode = osrNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return osrNode.execute(frame);
        }
    }

    public static class MaterializedFrameProgram extends Program {
        MaterializedFrame frame;

        public MaterializedFrameProgram(OnStackReplaceableNode osrNode, FrameDescriptor frameDescriptor) {
            super(osrNode, frameDescriptor);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            this.frame = frame.materialize();
            return osrNode.execute(this.frame);
        }
    }

    abstract static class OnStackReplaceableTestNode extends OnStackReplaceableNode {
        public static final int DEFAULT_TARGET = -1;

        protected OnStackReplaceableTestNode(TruffleLanguage<?> language) {
            super(language);
        }

        OptimizedCallTarget.OSRState getGraalOSRState() {
            return (OptimizedCallTarget.OSRState) super.getOSRState();
        }

        protected int getInt(Frame frame, FrameSlot frameSlot) {
            try {
                return frame.getInt(frameSlot);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException("Error accessing frame slot " + frameSlot);
            }
        }

        protected void setInt(Frame frame, FrameSlot frameSlot, int value) {
            frame.setInt(frameSlot, value);
        }
    }

    public static class InfiniteInterpreterLoop extends OnStackReplaceableTestNode {
        public InfiniteInterpreterLoop() {
            super(null);
        }

        @Override
        public Object doOSR(VirtualFrame innerFrame, Frame parentFrame, int target) {
            return execute(innerFrame);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // This node only terminates in compiled code.
            while (true) {
                if (CompilerDirectives.inCompiledCode()) {
                    return 42;
                }
                Object result = reportOSRBackEdge(frame, DEFAULT_TARGET);
                if (result != null) {
                    return result;
                }
            }
        }
    }

    public static class FixedIterationLoop extends OnStackReplaceableTestNode {
        @CompilationFinal FrameSlot indexSlot;
        @CompilationFinal FrameSlot numIterationsSlot;

        static final String OSR_RESULT = "osr result";
        static final String NORMAL_RESULT = "normal result";

        public FixedIterationLoop(FrameDescriptor frameDescriptor) {
            super(null);
            indexSlot = frameDescriptor.addFrameSlot("i", FrameSlotKind.Int);
            numIterationsSlot = frameDescriptor.addFrameSlot("n", FrameSlotKind.Int);
        }

        @Override
        public Object doOSR(VirtualFrame innerFrame, Frame parentFrame, int target) {
            setInt(innerFrame, indexSlot, getInt(parentFrame, indexSlot));
            int numIterations = getInt(parentFrame, numIterationsSlot);
            setInt(innerFrame, numIterationsSlot, numIterations);
            return executeLoop(innerFrame, numIterations);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setInt(indexSlot, 0);
            int numIterations = (Integer) frame.getArguments()[0];
            frame.setInt(numIterationsSlot, numIterations);
            return executeLoop(frame, numIterations);
        }

        protected Object executeLoop(VirtualFrame frame, int numIterations) {
            try {
                for (int i = frame.getInt(indexSlot); i < numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    if (i + 1 < numIterations) { // back-edge will be taken
                        Object result = reportOSRBackEdge(frame, DEFAULT_TARGET);
                        if (result != null) {
                            return result;
                        }
                    }
                }
                return CompilerDirectives.inCompiledCode() ? OSR_RESULT : NORMAL_RESULT;
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }
    }

    public static class TwoFixedIterationLoops extends FixedIterationLoop {
        static final String NO_OSR = "no osr";
        static final String OSR_IN_FIRST_LOOP = "osr in first loop";
        static final String OSR_IN_SECOND_LOOP = "osr in second loop";

        public TwoFixedIterationLoops(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        protected Object executeLoop(VirtualFrame frame, int numIterations) {
            try {
                for (int i = frame.getInt(indexSlot); i < numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    if (i + 1 < numIterations) { // back-edge will be taken
                        Object result = reportOSRBackEdge(frame, DEFAULT_TARGET);
                        if (result != null) {
                            return OSR_IN_FIRST_LOOP;
                        }
                    }
                }
                for (int i = frame.getInt(indexSlot); i < 2 * numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    if (i + 1 < 2 * numIterations) { // back-edge will be taken
                        Object result = reportOSRBackEdge(frame, DEFAULT_TARGET);
                        if (result != null) {
                            return OSR_IN_SECOND_LOOP;
                        }
                    }
                }
                return NO_OSR;
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }
    }

    public static class UncompilableFixedIterationLoop extends FixedIterationLoop {
        public UncompilableFixedIterationLoop(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        protected Object executeLoop(VirtualFrame frame, int numIterations) {
            for (int i = 0; i < numIterations; i++) {
                CompilerAsserts.neverPartOfCompilation();
                if (i + 1 < numIterations) { // back-edge will be taken
                    Object result = reportOSRBackEdge(frame, DEFAULT_TARGET);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return CompilerDirectives.inCompiledCode() ? OSR_RESULT : NORMAL_RESULT;
        }
    }

    public abstract static class StackWalkingFixedIterationLoop extends FixedIterationLoop {
        public StackWalkingFixedIterationLoop(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        protected Object executeLoop(VirtualFrame frame, int numIterations) {
            try {
                for (int i = frame.getInt(indexSlot); i < numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    checkStackTrace(i);
                    if (i + 1 < numIterations) { // back-edge will be taken
                        Object result = reportOSRBackEdge(frame, DEFAULT_TARGET);
                        if (result != null) {
                            return result;
                        }
                    }
                }
                return CompilerDirectives.inCompiledCode() ? OSR_RESULT : NORMAL_RESULT;
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }

        abstract void checkStackTrace(int index);
    }

    public static class CheckStackWalkCallTarget extends StackWalkingFixedIterationLoop {
        public CheckStackWalkCallTarget(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        void checkStackTrace(int index) {
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
                private boolean first = true;

                @Override
                public Void visitFrame(FrameInstance frameInstance) {
                    if (getGraalOSRState() != null) {
                        // We should never see the OSR call target in a stack trace.
                        Assert.assertNotSame(getGraalOSRState().getOSRCompilations().get(DEFAULT_TARGET), frameInstance.getCallTarget());
                    }
                    if (first) {
                        first = false;
                    } else {
                        Assert.assertNotNull(frameInstance.getCallNode());
                    }
                    return null;
                }
            });
        }

    }

    public static class CheckStackWalkFrame extends StackWalkingFixedIterationLoop {
        public CallTarget callTarget; // call target containing this node (must be set after
                                      // construction due to circular dependence)

        public CheckStackWalkFrame(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        void checkStackTrace(int index) {
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
                @Override
                public Void visitFrame(FrameInstance frameInstance) {
                    if (frameInstance.getCallTarget() == callTarget) {
                        try {
                            // The OSR frame will be up to date; the parent frame will not. We
                            // should get the OSR frame here.
                            int indexInFrame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY).getInt(indexSlot);
                            Assert.assertEquals(index, indexInFrame);
                        } catch (FrameSlotTypeException e) {
                            throw new IllegalStateException("Error accessing index slot");
                        }
                    }
                    return null;
                }
            });
        }
    }

    public static class CheckStackWalkFrameNested extends CheckStackWalkFrame {
        // Trigger a deoptimization once so that there are multiple OSR nodes in the call stack.
        @CompilationFinal public boolean hasDeoptimizedYet;

        public CheckStackWalkFrameNested(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
            hasDeoptimizedYet = false;
        }

        @TruffleBoundary
        void boundaryCall() {}

        @Override
        void checkStackTrace(int index) {
            if (CompilerDirectives.inCompiledCode() && !hasDeoptimizedYet) {
                // TODO: without this boundary call, the code never runs, and we get stuck in a compile-interpret-deoptimize loop. Why?
                boundaryCall();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasDeoptimizedYet = true;
            }
            super.checkStackTrace(index);
        }
    }

    public static class BytecodeNode extends OnStackReplaceableNode {
        @CompilationFinal(dimensions = 1) private final byte[] bytecodes;
        @CompilationFinal(dimensions = 1) private final FrameSlot[] regs;

        boolean compiled;

        public static class Bytecode {
            public static final byte RETURN = 0;
            public static final byte INC = 1;
            public static final byte DEC = 2;
            public static final byte JMPNONZERO = 3;
            public static final byte COPY = 4;
        }

        public BytecodeNode(int numLocals, FrameDescriptor frameDescriptor, byte[] bytecodes) {
            super(null);
            this.bytecodes = bytecodes;
            this.regs = new FrameSlot[numLocals];
            for (int i = 0; i < numLocals; i++) {
                this.regs[i] = frameDescriptor.addFrameSlot("$" + i, FrameSlotKind.Int);
            }
            this.compiled = false;
        }

        protected void setInt(Frame frame, int stackIndex, int value) {
            frame.setInt(regs[stackIndex], value);
        }

        protected int getInt(Frame frame, int stackIndex) {
            try {
                return frame.getInt(regs[stackIndex]);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException("Error accessing stack slot " + stackIndex);
            }
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            for (int i = 0; i < regs.length; i++) {
                if (i < args.length) {
                    frame.setInt(regs[i], (Integer) args[i]);
                } else {
                    frame.setInt(regs[i], 0);
                }
            }

            return executeFromBCI(frame, 0);
        }

        @Override
        @ExplodeLoop
        public Object doOSR(VirtualFrame innerFrame, Frame parentFrame, int target) {
            for (int i = 0; i < regs.length; i++) {
                setInt(innerFrame, i, getInt(parentFrame, i));
            }
            return executeFromBCI(innerFrame, target);
        }

        @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
        public Object executeFromBCI(VirtualFrame frame, int startBCI) {
            this.compiled = CompilerDirectives.inCompiledCode();
            CompilerAsserts.partialEvaluationConstant(startBCI);
            int bci = startBCI;
            while (true) {
                CompilerAsserts.partialEvaluationConstant(bci);
                switch (bytecodes[bci]) {
                    case Bytecode.RETURN: {
                        byte idx = bytecodes[bci + 1];
                        return getInt(frame, idx);
                    }
                    case Bytecode.INC: {
                        byte idx = bytecodes[bci + 1];
                        setInt(frame, idx, getInt(frame, idx) + 1);

                        bci = bci + 2;
                        continue;
                    }
                    case Bytecode.DEC: {
                        byte idx = bytecodes[bci + 1];
                        setInt(frame, idx, getInt(frame, idx) - 1);

                        bci = bci + 2;
                        continue;
                    }
                    case Bytecode.JMPNONZERO: {
                        byte idx = bytecodes[bci + 1];
                        int value = getInt(frame, idx);
                        if (value != 0) {
                            int target = bci + bytecodes[bci + 2];
                            if (target < bci) { // back-edge
                                Object result = reportOSRBackEdge(frame, target);
                                if (result != null) {
                                    return result;
                                }
                            }
                            bci = target;
                        } else {
                            bci = bci + 3;
                        }
                        continue;
                    }
                    case Bytecode.COPY: {
                        byte src = bytecodes[bci + 1];
                        byte dest = bytecodes[bci + 2];
                        setInt(frame, dest, getInt(frame, src));
                        bci = bci + 3;
                    }
                }
            }
        }
    }
}
