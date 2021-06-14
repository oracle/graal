package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
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

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.OSRCompilationThreshold;

public class OnStackReplaceableNodeTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    // 20s timeout
    @Rule public TestRule timeout = new Timeout(20, TimeUnit.SECONDS);

    private int osrThreshold;

    @Before
    @Override
    public void before() {
        setupContext("engine.MultiTier", "false", "engine.OSR", "true");
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        osrThreshold = target.getOptionValue(OSRCompilationThreshold);
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
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold+1));
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
        Assert.assertEquals(TwoFixedIterationLoops.OSR_IN_SECOND_LOOP, target.call(osrThreshold/2 + 1));

        // Each loop runs for osrThreshold/2 iterations, so OSR should not get triggered.
        frameDescriptor = new FrameDescriptor();
        osrNode = new TwoFixedIterationLoops(frameDescriptor);
        rootNode = new Program(osrNode, frameDescriptor);
        target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        Assert.assertEquals(TwoFixedIterationLoops.NO_OSR, target.call(osrThreshold/2));
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
        Assert.assertEquals(FixedIterationLoop.NORMAL_RESULT, target.call(osrThreshold+1));
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
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold+1));
        OptimizedCallTarget.OSRState osrState = (OptimizedCallTarget.OSRState) osrNode.getOSRState();
        OptimizedCallTarget osrTarget = osrState.getOSRCompilations().get(-1);
        Assert.assertNotNull(osrTarget);
        Assert.assertTrue(osrTarget.isValid());
        osrNode.nodeReplaced(osrNode, new FixedIterationLoop(new FrameDescriptor()), "something changed");
        Assert.assertTrue(osrState.getOSRCompilations().isEmpty());
        Assert.assertFalse(osrTarget.isValid());
        // Calling the node will eventually trigger OSR again (after polling interval)
        Assert.assertEquals(FixedIterationLoop.OSR_RESULT, target.call(osrThreshold+1));
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
        Assert.assertEquals(FixedIterationLoop.NORMAL_RESULT, target.call(osrThreshold+1));
    }

    // TODO:
    // test polling mechanism
    // test frame walking
    // validate different OSR targets using a bytecode interpreter

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

    public static class InfiniteInterpreterLoop extends OnStackReplaceableNode {
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
                Object result = reportOSRBackEdge(frame, -1);
                if (result != null) {
                    return result;
                }
            }
        }
    }

    public static class FixedIterationLoop extends OnStackReplaceableNode {
        @CompilationFinal FrameSlot indexSlot;

        static final String OSR_RESULT = "osr result";
        static final String NORMAL_RESULT = "normal result";

        public FixedIterationLoop(FrameDescriptor frameDescriptor) {
            super(null);
            indexSlot = frameDescriptor.addFrameSlot("i", FrameSlotKind.Int);
        }

        @Override
        public Object doOSR(VirtualFrame innerFrame, Frame parentFrame, int target) {
            try {
                innerFrame.setInt(indexSlot, parentFrame.getInt(indexSlot));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
            int numIterations = (Integer) parentFrame.getArguments()[0];
            return executeLoop(innerFrame, numIterations);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setInt(indexSlot, 0);
            int numIterations = (Integer) frame.getArguments()[0];
            return executeLoop(frame, numIterations);
        }

        protected Object executeLoop(VirtualFrame frame, int numIterations) {
            try {
                for (int i = frame.getInt(indexSlot); i < numIterations; i++) {
                   frame.setInt(indexSlot, i);
                   if (i + 1 < numIterations) { // back edge will be taken
                       Object result = reportOSRBackEdge(frame, -1);
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

    public static class TwoFixedIterationLoops extends OnStackReplaceableNode {
        @CompilationFinal FrameSlot indexSlot;

        static final String NO_OSR = "no osr";
        static final String OSR_IN_FIRST_LOOP = "osr in first loop";
        static final String OSR_IN_SECOND_LOOP = "osr in second loop";

        public TwoFixedIterationLoops(FrameDescriptor frameDescriptor) {
            super(null);
            indexSlot = frameDescriptor.addFrameSlot("i", FrameSlotKind.Int);
        }

        @Override
        public Object doOSR(VirtualFrame innerFrame, Frame parentFrame, int target) {
            try {
                innerFrame.setInt(indexSlot, parentFrame.getInt(indexSlot));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Error accessing index slot");
            }
            int numIterations = (Integer) parentFrame.getArguments()[0];
            return executeLoop(innerFrame, numIterations);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setInt(indexSlot, 0);
            int numIterations = (Integer) frame.getArguments()[0];
            return executeLoop(frame, numIterations);
        }

        private Object executeLoop(VirtualFrame frame, int numIterations) {
            try {
                for (int i = frame.getInt(indexSlot); i < numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    if (i + 1 < numIterations) { // back edge will be taken
                        Object result = reportOSRBackEdge(frame, -1);
                        if (result != null) {
                            return OSR_IN_FIRST_LOOP;
                        }
                    }
                }
                for (int i = frame.getInt(indexSlot); i < 2*numIterations; i++) {
                    frame.setInt(indexSlot, i);
                    if (i + 1 < 2*numIterations) { // back edge will be taken
                        Object result = reportOSRBackEdge(frame, -1);
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
                if (i + 1 < numIterations) { // back edge will be taken
                    Object result = reportOSRBackEdge(frame, -1);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return CompilerDirectives.inCompiledCode() ? OSR_RESULT : NORMAL_RESULT;
        }
    }
}
