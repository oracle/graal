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

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.test.GraalTest;
import org.graalvm.compiler.truffle.runtime.BytecodeOSRMetadata;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("deprecation")
public class BytecodeOSRNodeTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    @Rule public TestRule timeout = GraalTest.createTimeout(30, TimeUnit.SECONDS);

    private int osrThreshold;

    @Before
    @Override
    public void before() {
        // Use a multiple of the poll interval, so OSR triggers immediately when it hits the
        // threshold.
        osrThreshold = 10 * BytecodeOSRMetadata.OSR_POLL_INTERVAL;
        setupContext("engine.MultiTier", "false", "engine.OSR", "true", "engine.OSRCompilationThreshold", String.valueOf(osrThreshold));
    }

    /*
     * Test that an infinite interpreter loop triggers OSR.
     */
    @Test
    public void testSimpleInterpreterLoop() {
        RootNode rootNode = new Program(new InfiniteInterpreterLoop(), new FrameDescriptor());
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        // Interpreter invocation should be OSR compiled and break out of the interpreter loop.
        Assert.assertEquals(42, target.call());
    }

    /*
     * Test that a loop which just exceeds the threshold triggers OSR.
     */
    @Test
    public void testFixedIterationLoop() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FixedIterationLoop osrNode = new FixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
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
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
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
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(TwoFixedIterationLoops.OSR_IN_FIRST_LOOP, target.call(osrThreshold + 1));

        // Each loop runs for osrThreshold/2 + 1 iterations, so the second loop should trigger OSR.
        frameDescriptor = new FrameDescriptor();
        osrNode = new TwoFixedIterationLoops(frameDescriptor);
        rootNode = new Program(osrNode, frameDescriptor);
        target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(TwoFixedIterationLoops.OSR_IN_SECOND_LOOP, target.call(osrThreshold / 2 + 1));

        // Each loop runs for osrThreshold/2 iterations, so OSR should not get triggered.
        frameDescriptor = new FrameDescriptor();
        osrNode = new TwoFixedIterationLoops(frameDescriptor);
        rootNode = new Program(osrNode, frameDescriptor);
        target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(TwoFixedIterationLoops.NO_OSR, target.call(osrThreshold / 2));
    }

    /*
     * Test that OSR fails if the code cannot be compiled.
     */
    @Test
    public void testFailedCompilation() {
        Context.Builder builder = newContextBuilder().logHandler(new ByteArrayOutputStream());
        builder.option("engine.MultiTier", "false");
        builder.option("engine.OSR", "true");
        builder.option("engine.OSRCompilationThreshold", String.valueOf(osrThreshold));
        setupContext(builder);
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        UncompilableFixedIterationLoop osrNode = new UncompilableFixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(FixedIterationLoop.NORMAL_RESULT, target.call(osrThreshold + 1));
        // Compilation should be disabled after a compilation failure.
        Assert.assertEquals(osrNode.getGraalOSRMetadata(), BytecodeOSRMetadata.DISABLED);
    }

    /*
     * Test that a deoptimized OSR target can recompile.
     */
    @Test
    public void testDeoptimizeAndRecompile() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        DeoptimizingFixedIterationLoop osrNode = new DeoptimizingFixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        // After osrThreshold+1 iterations, it should trigger OSR and deoptimize. OSR should not be
        // disabled, but the target should be invalid pending recompilation.
        Assert.assertEquals(FixedIterationLoop.NORMAL_RESULT, target.call(osrThreshold + 1));
        Assert.assertNotEquals(osrNode.getGraalOSRMetadata(), BytecodeOSRMetadata.DISABLED);
        BytecodeOSRMetadata osrMetadata = osrNode.getGraalOSRMetadata();
        OptimizedCallTarget osrTarget = osrMetadata.getOSRCompilations().get(BytecodeOSRTestNode.DEFAULT_TARGET);
        Assert.assertFalse(target.isValid());
        // If we call it again, it should recompile, and the same call target should be used.
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold + 1));
        Assert.assertNotEquals(osrNode.getGraalOSRMetadata(), BytecodeOSRMetadata.DISABLED);
        OptimizedCallTarget newOSRTarget = osrMetadata.getOSRCompilations().get(BytecodeOSRTestNode.DEFAULT_TARGET);
        Assert.assertTrue(osrTarget.isValid());
        Assert.assertEquals(newOSRTarget, osrTarget);
    }

    /*
     * Test that node replacement in the base node invalidates the OSR target.
     */
    @Test
    public void testInvalidateOnNodeReplaced() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        Node childToReplace = new Node() {
        };
        FixedIterationLoop osrNode = new FixedIterationLoopWithChild(frameDescriptor, childToReplace);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold + 1));
        BytecodeOSRMetadata osrMetadata = osrNode.getGraalOSRMetadata();
        OptimizedCallTarget osrTarget = osrMetadata.getOSRCompilations().get(BytecodeOSRTestNode.DEFAULT_TARGET);
        Assert.assertNotNull(osrTarget);
        Assert.assertTrue(osrTarget.isValid());

        childToReplace.replace(new Node() {
        });
        Assert.assertFalse(osrTarget.isValid());
        // Invalidating a target on node replace should not disable compilation or remove the target
        Assert.assertNotEquals(osrNode.getGraalOSRMetadata(), BytecodeOSRMetadata.DISABLED);
        Assert.assertFalse(osrMetadata.getOSRCompilations().isEmpty());
        // Calling the node will eventually trigger OSR again.
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold + 1));
        Assert.assertNotEquals(osrNode.getGraalOSRMetadata(), BytecodeOSRMetadata.DISABLED);
        OptimizedCallTarget newOSRTarget = osrMetadata.getOSRCompilations().get(BytecodeOSRTestNode.DEFAULT_TARGET);
        Assert.assertTrue(newOSRTarget.isValid());
        Assert.assertEquals(osrTarget, newOSRTarget);
    }

    /*
     * Test that OSR succeeds even if a Frame with the given FrameDescriptor has been materialized
     * before.
     */
    @Test
    public void testOSRWithMaterializeableFrame() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        runtime.markFrameMaterializeCalled(frameDescriptor);
        MaterializedFixedIterationLoop osrNode = new MaterializedFixedIterationLoop(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        // OSR should succeed.
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold + 1));
        // Since the frame could be materialized, we should reuse the parent frame instead of
        // copying.
        Assert.assertFalse(osrNode.frameWasCopied);
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
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(42, target.call());
        BytecodeOSRMetadata osrMetadata = osrNode.getGraalOSRMetadata();
        int backEdgeCount = osrMetadata.getBackEdgeCount();
        Assert.assertTrue(backEdgeCount > osrThreshold);
        Assert.assertEquals(0, backEdgeCount % BytecodeOSRMetadata.OSR_POLL_INTERVAL);
    }

    /*
     * Test that a state object can be constructed and passed to OSR code.
     */
    @Test
    public void testInterpreterStateObject() {
        RootNode rootNode = new Program(new InterpreterStateInfiniteLoop(), new FrameDescriptor());
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(42, target.call());
    }

    /*
     * Test that a callback can be passed and invoked before the OSR transfer.
     */
    @Test
    public void testBeforeTransferCallback() {
        RootNode rootNode = new Program(new BeforeTransferInfiniteLoop(), new FrameDescriptor());
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(42, target.call());
    }

    /*
     * Test that the OSR call target does not get included in the Truffle stack trace.
     */
    @Test
    public void testStackTraceHidesOSRCallTarget() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        CheckStackWalkCallTarget osrNode = new CheckStackWalkCallTarget(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
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
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
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
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        osrNode.callTarget = target; // set the call target so stack walking can use it
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(3 * osrThreshold));
        Assert.assertTrue(osrNode.hasDeoptimizedYet);
    }

    /*
     * Test that getCallerFrame returns the correct frame when OSR is involved.
     *
     * Specifically, if X calls Y, and Y is OSRed, it should correctly skip over both the OSR and
     * original Y frames, returning X's frame.
     */
    @Test
    public void testGetCallerFrameSkipsOSR() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        CheckGetCallerFrameSkipsOSR osrNode = new CheckGetCallerFrameSkipsOSR(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        RootNode caller = new CheckGetCallerFrameSkipsOSR.Caller(target);
        OptimizedCallTarget callerTarget = (OptimizedCallTarget) caller.getCallTarget();
        osrNode.caller = callerTarget;
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, callerTarget.call(osrThreshold + 1));
    }

    /*
     * Test that the frame transfer helper works as expected, both on OSR enter and exit.
     */
    @Test
    public void testFrameTransfer() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        RootNode rootNode = new Program(new FrameTransferringNode(frameDescriptor), frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(42, target.call());
    }

    /*
     * Test that the frame transfer helper works even if a tag changes inside the OSR code. When
     * restoring the frame, we should detect the tag difference and deoptimize.
     */
    @Test
    public void testFrameTransferWithTagUpdate() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        RootNode rootNode = new Program(new FrameTransferringNodeWithTagUpdate(frameDescriptor), frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(42, target.call());
    }

    /*
     * Test that frame transferring works (without deoptimizing) for a frame with uninitialized
     * slots. An uninitialized slot's tag may differ from the declared tag (since slots are given a
     * default value), but the tag speculation should not deoptimize because of this difference.
     *
     * (Concretely, this means we should speculate on a Frame's concrete tags instead of its
     * declared tags)
     */
    @Test
    public void testFrameTransferWithUninitializedSlots() {
        // use a non-null default value to make sure it gets copied properly.
        FrameDescriptor frameDescriptor = new FrameDescriptor(new Object());
        RootNode rootNode = new Program(new FrameTransferringNodeWithUninitializedSlots(frameDescriptor), frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(42, target.call());
    }

    /*
     * Test that when the frame is updated after OSR compilation, OSR will deopt and eventually
     * retry.
     */
    @Test
    public void testFrameChanges() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FrameChangingNode osrNode = new FrameChangingNode(frameDescriptor);
        RootNode rootNode = new Program(osrNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(42, target.call());
        Assert.assertEquals(2, osrNode.osrCount);
    }

    // Bytecode programs
    /*
     * do { input1 -= 1; result += 3; } while (input1); return result;
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
     * do { input1--; temp = input2; do { temp--; result++; } while(temp); } while(input1); return
     * result;
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
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        // note: requires an extra iteration due to an awkward interaction with enterprise loop
        // peeling.
        Assert.assertEquals(3 * (osrThreshold + 2), target.call(osrThreshold + 2, 0));
        Assert.assertTrue(bytecodeNode.compiled);
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) bytecodeNode.getOSRMetadata();
        Assert.assertNotEquals(osrMetadata, BytecodeOSRMetadata.DISABLED);
        Assert.assertTrue(osrMetadata.getOSRCompilations().containsKey(0));
        Assert.assertTrue(osrMetadata.getOSRCompilations().get(0).isValid());
    }

    @Test
    public void testNoOSRInBytecodeLoop() {
        // osrThreshold back-edges -> not compiled
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        BytecodeNode bytecodeNode = new BytecodeNode(3, frameDescriptor, tripleInput1);
        RootNode rootNode = new Program(bytecodeNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(3 * osrThreshold, target.call(osrThreshold, 0));
        Assert.assertFalse(bytecodeNode.compiled);
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) bytecodeNode.getOSRMetadata();
        Assert.assertNotEquals(osrMetadata, BytecodeOSRMetadata.DISABLED);
    }

    @Test
    public void testOSRInBytecodeOuterLoop() {
        // computes osrThreshold * 2
        // Inner loop contributes 1 back-edge, so each outer loop contributes 2 back-edges, and
        // the even-valued osrThreshold gets hit by the outer loop back-edge.
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        BytecodeNode bytecodeNode = new BytecodeNode(4, frameDescriptor, multiplyInputs);
        RootNode rootNode = new Program(bytecodeNode, frameDescriptor);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(2 * osrThreshold, target.call(osrThreshold, 2));
        Assert.assertTrue(bytecodeNode.compiled);
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) bytecodeNode.getOSRMetadata();
        Assert.assertNotEquals(osrMetadata, BytecodeOSRMetadata.DISABLED);
        Assert.assertTrue(osrMetadata.getOSRCompilations().containsKey(0));
        Assert.assertTrue(osrMetadata.getOSRCompilations().get(0).isValid());
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
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertEquals(2 * (osrThreshold - 1), target.call(2, osrThreshold - 1));
        Assert.assertTrue(bytecodeNode.compiled);
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) bytecodeNode.getOSRMetadata();
        Assert.assertNotEquals(osrMetadata, BytecodeOSRMetadata.DISABLED);
        Assert.assertTrue(osrMetadata.getOSRCompilations().containsKey(5));
        Assert.assertTrue(osrMetadata.getOSRCompilations().get(5).isValid());
    }

    public static class Program extends RootNode {
        @Child BytecodeOSRTestNode osrNode;

        public Program(BytecodeOSRTestNode osrNode, FrameDescriptor frameDescriptor) {
            super(null, frameDescriptor);
            this.osrNode = osrNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return osrNode.execute(frame);
        }
    }

    abstract static class BytecodeOSRTestNode extends Node implements BytecodeOSRNode {
        public static final int DEFAULT_TARGET = -1;
        @CompilationFinal Object osrMetadata;

        @Override
        public Object getOSRMetadata() {
            return osrMetadata;
        }

        @Override
        public void setOSRMetadata(Object osrMetadata) {
            this.osrMetadata = osrMetadata;
        }

        BytecodeOSRMetadata getGraalOSRMetadata() {
            return (BytecodeOSRMetadata) getOSRMetadata();
        }

        protected int getInt(Frame frame, com.oracle.truffle.api.frame.FrameSlot frameSlot) {
            try {
                return frame.getInt(frameSlot);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException("Error accessing frame slot " + frameSlot);
            }
        }

        protected void setInt(Frame frame, com.oracle.truffle.api.frame.FrameSlot frameSlot, int value) {
            frame.setInt(frameSlot, value);
        }

        // Prevent assertion code from being compiled.
        @TruffleBoundary
        public void assertEquals(Object expected, Object actual) {
            Assert.assertEquals(expected, actual);
        }

        @TruffleBoundary
        void assertDoubleEquals(double expected, double actual) {
            Assert.assertEquals(expected, actual, 0);
        }

        abstract Object execute(VirtualFrame frame);
    }

    public static class InfiniteInterpreterLoop extends BytecodeOSRTestNode {
        @Override
        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            return execute(osrFrame);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // This node only terminates in compiled code.
            while (true) {
                if (CompilerDirectives.inCompiledCode()) {
                    return 42;
                }
                if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                    Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
    }

    public static class FixedIterationLoop extends BytecodeOSRTestNode {
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot indexSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot numIterationsSlot;

        static final String OSR_RESULT = "osr result";
        static final String NORMAL_RESULT = "normal result";

        public FixedIterationLoop(FrameDescriptor frameDescriptor) {
            indexSlot = frameDescriptor.addFrameSlot("i", FrameSlotKind.Int);
            numIterationsSlot = frameDescriptor.addFrameSlot("n", FrameSlotKind.Int);
        }

        @Override
        public void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, int target) {
            setInt(osrFrame, indexSlot, getInt(parentFrame, indexSlot));
            setInt(osrFrame, numIterationsSlot, getInt(parentFrame, numIterationsSlot));
        }

        @Override
        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            return executeLoop(osrFrame, getInt(osrFrame, numIterationsSlot));
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
                        if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                            Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                            if (result != null) {
                                return result;
                            }
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
                        if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                            Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                            if (result != null) {
                                return OSR_IN_FIRST_LOOP;
                            }
                        }
                    }
                }
                for (int i = frame.getInt(indexSlot); i < 2 * numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    if (i + 1 < 2 * numIterations) { // back-edge will be taken
                        if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                            Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                            if (result != null) {
                                return OSR_IN_SECOND_LOOP;
                            }
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
                    if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                        Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
            return CompilerDirectives.inCompiledCode() ? OSR_RESULT : NORMAL_RESULT;
        }
    }

    public static class DeoptimizingFixedIterationLoop extends FixedIterationLoop {
        @CompilationFinal boolean loaded;

        public DeoptimizingFixedIterationLoop(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
            loaded = false;
        }

        @Override
        protected Object executeLoop(VirtualFrame frame, int numIterations) {
            checkField();
            try {
                for (int i = frame.getInt(indexSlot); i < numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    if (i + 1 < numIterations) { // back-edge will be taken
                        if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                            Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
                return CompilerDirectives.inCompiledCode() ? OSR_RESULT : NORMAL_RESULT;
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }

        @TruffleBoundary
        void boundaryCall() {
        }

        void checkField() {
            if (CompilerDirectives.inCompiledCode() && !loaded) {
                // the boundary call prevents Truffle from moving the deopt earlier,
                // which ensures this branch is taken.
                boundaryCall();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                loaded = true;
            }
        }
    }

    public static class FixedIterationLoopWithChild extends FixedIterationLoop {
        @Child Node child;

        public FixedIterationLoopWithChild(FrameDescriptor frameDescriptor, Node child) {
            super(frameDescriptor);
            this.child = child;
        }
    }

    public static class MaterializedFixedIterationLoop extends FixedIterationLoop {
        boolean frameWasCopied = false;

        public MaterializedFixedIterationLoop(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        public void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
            super.restoreParentFrame(osrFrame, parentFrame);
            frameWasCopied = true;
        }
    }

    public static class InterpreterStateInfiniteLoop extends BytecodeOSRTestNode {

        static final class InterpreterState {
            final int foo;
            final int bar;

            InterpreterState(int foo, int bar) {
                this.foo = foo;
                this.bar = bar;
            }
        }

        @Override
        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            InterpreterState state = (InterpreterState) interpreterState;
            return executeLoop(osrFrame, state.foo, state.bar);
        }

        @Override
        Object execute(VirtualFrame frame) {
            return executeLoop(frame, 1, 20);
        }

        protected Object executeLoop(VirtualFrame frame, int foo, int bar) {
            CompilerAsserts.partialEvaluationConstant(foo);
            CompilerAsserts.partialEvaluationConstant(bar);
            // This node only terminates in compiled code.
            while (true) {
                if (CompilerDirectives.inCompiledCode()) {
                    return foo + bar;
                }
                if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                    Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, new InterpreterState(2 * foo, 2 * bar), null, frame);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
    }

    public static class BeforeTransferInfiniteLoop extends BytecodeOSRTestNode {
        boolean callbackInvoked = false;

        @Override
        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            assertEquals(true, callbackInvoked);
            return execute(osrFrame);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // This node only terminates in compiled code.
            while (true) {
                if (CompilerDirectives.inCompiledCode()) {
                    return 42;
                }
                if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                    Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, () -> {
                        callbackInvoked = true;
                    }, frame);
                    if (result != null) {
                        return result;
                    }
                }
            }
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
                        if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                            Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                            if (result != null) {
                                return result;
                            }
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
                    BytecodeOSRMetadata metadata = getGraalOSRMetadata();
                    if (metadata != null) {
                        // We should never see the OSR call target in a stack trace.
                        Assert.assertTrue(metadata.getOSRCompilations() == null ||
                                        metadata.getOSRCompilations().get(DEFAULT_TARGET) != frameInstance.getCallTarget());
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
                            assertEquals(index, indexInFrame);
                            if (CompilerDirectives.inCompiledCode()) {
                                Assert.assertTrue(frameInstance.isVirtualFrame());
                            }
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
        void boundaryCall() {
        }

        @Override
        void checkStackTrace(int index) {
            if (CompilerDirectives.inCompiledCode() && !hasDeoptimizedYet) {
                // the boundary call prevents Truffle from moving the deopt earlier,
                // which ensures this branch is taken.
                boundaryCall();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasDeoptimizedYet = true;
            }
            super.checkStackTrace(index);
        }
    }

    public static class CheckGetCallerFrameSkipsOSR extends FixedIterationLoop {
        CallTarget caller; // set after construction

        public CheckGetCallerFrameSkipsOSR(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        protected Object executeLoop(VirtualFrame frame, int numIterations) {
            try {
                for (int i = frame.getInt(indexSlot); i < numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    checkCallerFrame();
                    if (i + 1 < numIterations) { // back-edge will be taken
                        if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                            Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
                return CompilerDirectives.inCompiledCode() ? OSR_RESULT : NORMAL_RESULT;
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }

        @TruffleBoundary
        void checkCallerFrame() {
            Assert.assertEquals(caller, Truffle.getRuntime().getCallerFrame().getCallTarget());
        }

        public static class Caller extends RootNode {
            CallTarget toCall;

            protected Caller(CallTarget toCall) {
                super(null);
                this.toCall = toCall;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return toCall.call(frame.getArguments());
            }
        }
    }

    public static class FrameTransferringNode extends BytecodeOSRTestNode {
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot booleanSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot byteSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot doubleSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot floatSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot intSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot longSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot objectSlot;
        @CompilationFinal Object o1;
        @CompilationFinal Object o2;

        public FrameTransferringNode(FrameDescriptor frameDescriptor) {
            booleanSlot = frameDescriptor.addFrameSlot("booleanValue", FrameSlotKind.Boolean);
            byteSlot = frameDescriptor.addFrameSlot("byteValue", FrameSlotKind.Byte);
            doubleSlot = frameDescriptor.addFrameSlot("doubleValue", FrameSlotKind.Double);
            floatSlot = frameDescriptor.addFrameSlot("floatValue", FrameSlotKind.Float);
            intSlot = frameDescriptor.addFrameSlot("intValue", FrameSlotKind.Int);
            longSlot = frameDescriptor.addFrameSlot("longValue", FrameSlotKind.Long);
            objectSlot = frameDescriptor.addFrameSlot("objectValue", FrameSlotKind.Object);
            o1 = new Object();
            o2 = new Object();
        }

        @Override
        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            checkRegularState(osrFrame);
            setOSRState(osrFrame);
            return 42;
        }

        @Override
        public void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, int target) {
            super.copyIntoOSRFrame(osrFrame, parentFrame, target);
            // Copying should not trigger a deopt.
            Assert.assertTrue(CompilerDirectives.inCompiledCode());
        }

        @Override
        public void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
            super.restoreParentFrame(osrFrame, parentFrame);
            // Copying should not trigger a deopt.
            Assert.assertTrue(CompilerDirectives.inCompiledCode());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Assert.assertFalse(CompilerDirectives.inCompiledCode());
            setRegularState(frame);
            return executeLoop(frame);
        }

        public Object executeLoop(VirtualFrame frame) {
            // This node only terminates in compiled code.
            while (true) {
                if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                    Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                    if (result != null) {
                        checkOSRState(frame);
                        return result;
                    }
                }
            }
        }

        public void setRegularState(VirtualFrame frame) {
            frame.setBoolean(booleanSlot, true);
            frame.setByte(byteSlot, Byte.MIN_VALUE);
            frame.setDouble(doubleSlot, Double.MIN_VALUE);
            frame.setFloat(floatSlot, Float.MIN_VALUE);
            frame.setInt(intSlot, Integer.MIN_VALUE);
            frame.setLong(longSlot, Long.MIN_VALUE);
            frame.setObject(objectSlot, o1);
        }

        public void checkRegularState(VirtualFrame frame) {
            try {
                assertEquals(true, frame.getBoolean(booleanSlot));
                assertEquals(Byte.MIN_VALUE, frame.getByte(byteSlot));
                assertDoubleEquals(Double.MIN_VALUE, frame.getDouble(doubleSlot));
                assertDoubleEquals(Float.MIN_VALUE, frame.getFloat(floatSlot));
                assertEquals(Integer.MIN_VALUE, frame.getInt(intSlot));
                assertEquals(Long.MIN_VALUE, frame.getLong(longSlot));
                assertEquals(o1, frame.getObject(objectSlot));
            } catch (FrameSlotTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }

        public void setOSRState(VirtualFrame frame) {
            frame.setBoolean(booleanSlot, false);
            frame.setByte(byteSlot, Byte.MAX_VALUE);
            frame.setDouble(doubleSlot, Double.MAX_VALUE);
            frame.setFloat(floatSlot, Float.MAX_VALUE);
            frame.setInt(intSlot, Integer.MAX_VALUE);
            frame.setLong(longSlot, Long.MAX_VALUE);
            frame.setObject(objectSlot, o2);
        }

        public void checkOSRState(VirtualFrame frame) {
            try {
                assertEquals(false, frame.getBoolean(booleanSlot));
                assertEquals(Byte.MAX_VALUE, frame.getByte(byteSlot));
                assertDoubleEquals(Double.MAX_VALUE, frame.getDouble(doubleSlot));
                assertDoubleEquals(Float.MAX_VALUE, frame.getFloat(floatSlot));
                assertEquals(Integer.MAX_VALUE, frame.getInt(intSlot));
                assertEquals(Long.MAX_VALUE, frame.getLong(longSlot));
                assertEquals(o2, frame.getObject(objectSlot));
            } catch (FrameSlotTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }
    }

    public static class FrameTransferringNodeWithTagUpdate extends FrameTransferringNode {
        public FrameTransferringNodeWithTagUpdate(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
        }

        @Override
        public void setOSRState(VirtualFrame frame) {
            super.setOSRState(frame);
            frame.setObject(intSlot, o2);
        }

        @Override
        public void checkOSRState(VirtualFrame frame) {
            try {
                assertEquals(false, frame.getBoolean(booleanSlot));
                assertEquals(Byte.MAX_VALUE, frame.getByte(byteSlot));
                assertDoubleEquals(Double.MAX_VALUE, frame.getDouble(doubleSlot));
                assertDoubleEquals(Float.MAX_VALUE, frame.getFloat(floatSlot));
                assertEquals(o2, frame.getObject(intSlot));
                assertEquals(Long.MAX_VALUE, frame.getLong(longSlot));
                assertEquals(o2, frame.getObject(objectSlot));
            } catch (FrameSlotTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }

        @Override
        public void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
            // The parent implementation asserts we are in compiled code, so we instead explicitly
            // do the transfer here.
            ((BytecodeOSRMetadata) osrMetadata).transferFrame((FrameWithoutBoxing) osrFrame, (FrameWithoutBoxing) parentFrame);
            // Since the intSlot tag changed inside the compiled code, the tag speculation should
            // fail and cause a deopt.
            Assert.assertFalse(CompilerDirectives.inCompiledCode());
        }
    }

    public static class FrameTransferringNodeWithUninitializedSlots extends FrameTransferringNode {
        final Object defaultValue;

        public FrameTransferringNodeWithUninitializedSlots(FrameDescriptor frameDescriptor) {
            super(frameDescriptor);
            defaultValue = frameDescriptor.getDefaultValue();
        }

        @Override
        public void setRegularState(VirtualFrame frame) {
            frame.setBoolean(booleanSlot, true);
            // everything else is uninitialized
        }

        @Override
        public void checkRegularState(VirtualFrame frame) {
            try {
                assertEquals(true, frame.getBoolean(booleanSlot));
                // these slots are uninitialized
                assertEquals(defaultValue, frame.getObject(byteSlot));
                assertEquals(defaultValue, frame.getObject(byteSlot));
                assertEquals(defaultValue, frame.getObject(doubleSlot));
                assertEquals(defaultValue, frame.getObject(floatSlot));
                assertEquals(defaultValue, frame.getObject(intSlot));
                assertEquals(defaultValue, frame.getObject(longSlot));
                assertEquals(defaultValue, frame.getObject(objectSlot));
            } catch (FrameSlotTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }

        @Override
        public void setOSRState(VirtualFrame frame) {
            frame.setBoolean(booleanSlot, false);
            // everything else is uninitialized
        }

        @Override
        public void checkOSRState(VirtualFrame frame) {
            try {
                assertEquals(false, frame.getBoolean(booleanSlot));
                // these slots are uninitialized
                assertEquals(defaultValue, frame.getObject(byteSlot));
                assertEquals(defaultValue, frame.getObject(byteSlot));
                assertEquals(defaultValue, frame.getObject(doubleSlot));
                assertEquals(defaultValue, frame.getObject(floatSlot));
                assertEquals(defaultValue, frame.getObject(intSlot));
                assertEquals(defaultValue, frame.getObject(longSlot));
                assertEquals(defaultValue, frame.getObject(objectSlot));
            } catch (FrameSlotTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }
    }

    public static class FrameChangingNode extends BytecodeOSRTestNode {
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot intSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot longSlot;

        public int osrCount = 0;

        public FrameChangingNode(FrameDescriptor frameDescriptor) {
            intSlot = frameDescriptor.addFrameSlot("intValue", FrameSlotKind.Int);
            longSlot = null;
        }

        @Override
        public void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, int target) {
            changeFrame(osrFrame.getFrameDescriptor()); // only changes the first time
            super.copyIntoOSRFrame(osrFrame, parentFrame, target);
        }

        @Override
        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            osrCount += 1;
            return executeLoop(osrFrame);
        }

        @TruffleBoundary
        public void changeFrame(FrameDescriptor frameDescriptor) {
            // By convention, when we change this @CompilationFinal field we *should* transfer to
            // interpreter, but we omit that directive to test that the OSR mechanism detects the
            // frame change.
            if (longSlot == null) {
                longSlot = frameDescriptor.addFrameSlot("longValue", FrameSlotKind.Long);
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Assert.assertFalse(CompilerDirectives.inCompiledCode());
            setRegularState(frame);
            return executeLoop(frame);
        }

        public Object executeLoop(VirtualFrame frame) {
            // This node only terminates in compiled code.
            while (true) {
                if (CompilerDirectives.inCompiledCode()) {
                    checkRegularState(frame);
                    setOSRState(frame);
                    return 42;
                }
                if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                    Object result = BytecodeOSRNode.tryOSR(this, DEFAULT_TARGET, null, null, frame);
                    if (result != null) {
                        checkOSRState(frame);
                        return result;
                    }
                }
            }
        }

        public void setRegularState(VirtualFrame frame) {
            frame.setInt(intSlot, Integer.MIN_VALUE);
        }

        public void checkRegularState(VirtualFrame frame) {
            try {
                assertEquals(Integer.MIN_VALUE, frame.getInt(intSlot));
            } catch (FrameSlotTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }

        public void setOSRState(VirtualFrame frame) {
            frame.setInt(intSlot, Integer.MAX_VALUE);
            if (longSlot != null) {
                frame.setLong(longSlot, Long.MAX_VALUE);
            }
        }

        public void checkOSRState(VirtualFrame frame) {
            try {
                assertEquals(Integer.MAX_VALUE, frame.getInt(intSlot));
                if (longSlot != null) {
                    assertEquals(Long.MAX_VALUE, frame.getLong(longSlot));
                }
            } catch (FrameSlotTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
        }
    }

    public static class BytecodeNode extends BytecodeOSRTestNode implements BytecodeOSRNode {
        @CompilationFinal(dimensions = 1) private final byte[] bytecodes;
        @CompilationFinal(dimensions = 1) private final com.oracle.truffle.api.frame.FrameSlot[] regs;

        boolean compiled;

        public static class Bytecode {
            public static final byte RETURN = 0;
            public static final byte INC = 1;
            public static final byte DEC = 2;
            public static final byte JMPNONZERO = 3;
            public static final byte COPY = 4;
        }

        public BytecodeNode(int numLocals, FrameDescriptor frameDescriptor, byte[] bytecodes) {
            this.bytecodes = bytecodes;
            this.regs = new com.oracle.truffle.api.frame.FrameSlot[numLocals];
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
        public void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, int target) {
            for (int i = 0; i < regs.length; i++) {
                setInt(osrFrame, i, getInt(parentFrame, i));
            }
        }

        @Override
        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            return executeFromBCI(osrFrame, target);
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
                                if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                                    Object result = BytecodeOSRNode.tryOSR(this, target, null, null, frame);
                                    if (result != null) {
                                        return result;
                                    }
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
