/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.MinInvokeThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.OSRCompilationThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SingleTierCompilationThreshold;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;
import com.oracle.truffle.runtime.OptimizedOSRLoopNode;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;

@RunWith(Theories.class)
public class OptimizedOSRLoopNodeTest extends TestWithSynchronousCompiling {

    private static final OptimizedTruffleRuntime runtime = (OptimizedTruffleRuntime) Truffle.getRuntime();

    @DataPoint public static final OSRLoopFactory DEFAULT = (_, repeating) -> (OptimizedOSRLoopNode) OptimizedOSRLoopNode.create(repeating);

    private int osrThreshold;
    private Context context;
    private TruffleLanguage.Env languageEnv;

    @BeforeClass
    public static void doBefore() {
        // ensure that all classes are properly loaded
        int defaultThreshold = OSRCompilationThreshold.getDefaultValue();
        TestRootNode rootNode = TestRootNode.create(defaultThreshold, DEFAULT, new TestRepeatingNode());
        CallTarget target = rootNode.getCallTarget();
        target.call(1);
    }

    @Before
    @Override
    public void before() {
        context = setupContext("engine.MultiTier", "false");
        OptimizedCallTarget target = (OptimizedCallTarget) RootNode.createConstantNode(0).getCallTarget();
        osrThreshold = target.getOptionValue(OSRCompilationThreshold);
        context.initialize(ProxyLanguage.ID);
        languageEnv = ProxyLanguage.LanguageContext.get(null).getEnv();
    }

    /*
     * Test that we achieve compilation on the first execution with a loop invoked
     */
    @Theory
    public void testOSRSingleInvocation(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
        CallTarget target = rootNode.getCallTarget();
        target.call(osrThreshold + 1);
        assertCompiled(rootNode.getOSRTarget());
        target.call(2);
        assertCompiled(rootNode.getOSRTarget());
        Assert.assertTrue(rootNode.wasRepeatingCalledCompiled());
    }

    @Theory
    public void testNoInliningForLatency(OSRLoopFactory factory) {
        setupContext("engine.MultiTier", "false", "engine.Mode", "latency");
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new CallsTargetRepeatingNode(
                        (OptimizedDirectCallNode) runtime.createDirectCallNode(new RootNode(null) {
                            @Override
                            public Object execute(VirtualFrame frame) {
                                if (CompilerDirectives.inCompiledCode() && !CompilerDirectives.inCompilationRoot()) {
                                    Assert.fail("Must not inline into OSR compilation on latency mode.");
                                }
                                return 42;
                            }
                        }.getCallTarget())));
        CallTarget target = rootNode.getCallTarget();
        target.call(osrThreshold + 1);
        assertCompiled(rootNode.getOSRTarget());
    }

    @Theory
    public void testOSRAndRewriteDoesNotSuppressTargetCompilation(OSRLoopFactory factory) {
        setupContext("engine.SingleTierCompilationThreshold", "3");
        TestRootNodeWithReplacement rootNode = TestRootNodeWithReplacement.create(osrThreshold, factory, new TestRepeatingNode());
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        target.call(osrThreshold + 1);
        assertCompiled(rootNode.getOSRTarget());
        assertNotCompiled(target);
        target.nodeReplaced(rootNode.toReplace, new TestRepeatingNode(), "test");
        for (int i = 0; i < target.getOptionValue(SingleTierCompilationThreshold) + 3 - 1; i++) {
            target.call(2);
        }
        assertCompiled(rootNode.getOSRTarget());
        assertCompiled(target);
    }

    /*
     * Test OSR is not triggered just below the osr threshold.
     */
    @Theory
    public void testNonOSR(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
        rootNode.getCallTarget().call(osrThreshold);
        assertNotCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test that if osr compilation is forced without any execution we do not deoptimize on first
     * execution.
     */
    @Theory
    public void testNoInvalidationWithoutFirstExecution(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
        RootCallTarget target = rootNode.getCallTarget();
        rootNode.forceOSR();
        assertCompiled(rootNode.getOSRTarget());
        target.call(1); // should not invalidate OSR
        assertCompiled(rootNode.getOSRTarget());
        Assert.assertTrue(rootNode.wasRepeatingCalledCompiled());
    }

    /*
     * Test that OSR compilation also works if the loop node is not embedded in a CallTarget, but
     * just called directly with the node's execute method.
     */
    @Theory
    public void testExecutionWithoutCallTarget(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
        executeNoCallTarget(rootNode, osrThreshold + 1);
        assertCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test behavior of invalidations when they come from the outside.
     */
    @Theory
    public void testExternalInvalidations(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
        executeNoCallTarget(rootNode, osrThreshold + 1);
        assertCompiled(rootNode.getOSRTarget());

        for (int i = 0; i < 10; i++) {
            rootNode.getOSRTarget().invalidate("test");
            Assert.assertNotNull(rootNode.getOSRTarget());
            assertNotCompiled(rootNode.getOSRTarget());
            Assert.assertNotNull(rootNode.getOSRTarget()); // no eager cleanup for thread safety

            executeNoCallTarget(rootNode, osrThreshold - 1);
            assertNotCompiled(rootNode.getOSRTarget());
            Assert.assertNull(rootNode.getOSRTarget()); // cleaned up after further call

            executeNoCallTarget(rootNode, osrThreshold + 1);
            assertCompiled(rootNode.getOSRTarget());
        }
    }

    /*
     * Test behavior of OSR compile loops if the invalidate internally during loop execution. Also
     * test that it respects the invalidation reprofile count.
     */
    @Theory
    public void testInternalInvalidations(OSRLoopFactory factory) {
        TestRepeatingNode repeating = new TestRepeatingNode();
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, repeating);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        target.call(osrThreshold + 1);
        target.resetCompilationProfile();
        assertCompiled(rootNode.getOSRTarget());

        repeating.invalidationCounter = 5;
        target.call(4);
        assertCompiled(rootNode.getOSRTarget());
        target.call(2); // should trigger invalidation
        assertNotCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test outer invalidation still triggers OSR.
     */
    @Theory
    public void testOuterInvalidationTriggersOSR(OSRLoopFactory factory) {
        TestRepeatingNode repeating = new TestRepeatingNode();
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, repeating);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();

        // compile inner
        target.call(osrThreshold + 1);
        OptimizedCallTarget osrTarget = rootNode.getOSRTarget();
        assertCompiled(osrTarget);

        int i;
        for (i = 0; i < target.getOptionValue(MinInvokeThreshold) - 2; i++) {
            target.call(0);
            assertNotCompiled(target);
            assertCompiled(rootNode.getOSRTarget());
        }

        target.call(0);
        assertCompiled(target);
        assertCompiled(rootNode.getOSRTarget());
        assertSame(rootNode.getOSRTarget(), osrTarget);

        target.invalidate("test");
        assertNotCompiled(target);
        assertCompiled(rootNode.getOSRTarget());
        assertSame(rootNode.getOSRTarget(), osrTarget);

        // after invalidating the outer method the osr target should still be valid and used
        target.resetCompilationProfile();
        target.call(15);

        assertCompiled(rootNode.getOSRTarget());
        assertSame(rootNode.getOSRTarget(), osrTarget);
        assertNotCompiled(target);

        // now externally invalidate the osr target and see if we compile the osr target again
        rootNode.getOSRTarget().invalidate("test");
        target.call(osrThreshold + 1);
        assertCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test that if a call target is called a min invocation theshold times it is unlikely that it
     * needs OSR at all.
     */
    @Theory
    public void testNoOSRAfterMinInvocationThreshold(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        int i;
        for (i = 0; i < target.getOptionValue(MinInvokeThreshold); i++) {
            target.call(0);
            assertNotCompiled(rootNode.getOSRTarget());
        }
        target.call(osrThreshold);
        assertNotCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test that loop counts of osr loops propagate loop counts the parent call target.
     */
    @Theory
    public void testOSRMinInvocationThresholdPropagateLoopCounts(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        int thresholdForOsr = this.osrThreshold;
        int truffleMinInvokes = target.getOptionValue(MinInvokeThreshold);

        int i;
        int invokesleft = thresholdForOsr;
        for (i = 0; i < truffleMinInvokes - 1; i++) {
            int invokes = thresholdForOsr / truffleMinInvokes;
            invokesleft -= invokes;
            target.call(invokes);
            assertNotCompiled(rootNode.getOSRTarget());
        }
        assertNotCompiled(target);
        /*
         * This should trigger OSR, but since the parent is compiling/compiled already we won't do
         * OSR.
         */
        target.call(invokesleft + 1);
        assertNotCompiled(rootNode.getOSRTarget());
        assertCompiled(target);
    }

    /*
     * Test that we are not crashing in multi threaded environments.
     */
    @Theory
    public void testThreadSafety(OSRLoopFactory factory) {
        int threshold = osrThreshold;
        IntStream.generate(() -> 10).limit(10).parallel().forEach(_ -> {
            TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
            IntStream.generate(() -> threshold).limit(10).parallel().forEach(_ -> executeNoCallTarget(rootNode, threshold + 1));
            waitForCompiled(rootNode.getOSRTarget());
        });

        IntStream.generate(() -> 10).limit(10).parallel().forEach(_ -> {
            TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());
            IntStream.generate(() -> threshold).limit(10).parallel().forEach(_ -> executeNoCallTarget(rootNode, threshold));
            waitForCompiled(rootNode.getOSRTarget());
        });
    }

    /*
     * Test that two silbling loops are compiled independently.
     */
    @Theory
    public void testTwoLoopsSilblings(OSRLoopFactory factory) {
        TwoSilblingLoopNodesTest rootNode = TwoSilblingLoopNodesTest.create(osrThreshold, factory, new TestRepeatingNode(), new TestRepeatingNode());
        CallTarget target = rootNode.getCallTarget();
        target.call(osrThreshold + 1, 1);
        waitForCompiled(rootNode.getOSRTarget());
        waitForCompiled(rootNode.getOSRTarget2());
        assertCompiled(rootNode.getOSRTarget());
        assertNotCompiled(rootNode.getOSRTarget2());
        target.call(1, osrThreshold + 1);
        waitForCompiled(rootNode.getOSRTarget());
        waitForCompiled(rootNode.getOSRTarget2());
        assertCompiled(rootNode.getOSRTarget());
        assertCompiled(rootNode.getOSRTarget2());
        Assert.assertTrue(rootNode.getOSRTarget() != rootNode.getOSRTarget2());
    }

    private static class TwoSilblingLoopNodesTest extends TestRootNode {
        @Child OptimizedOSRLoopNode loopNode2;

        protected TwoSilblingLoopNodesTest(FrameDescriptor descriptor, int threshold, OSRLoopFactory factory, TestRepeatingNode repeating1, TestRepeatingNode repeating2, int param1, int param2) {
            super(descriptor, threshold, factory, repeating1, param1, param2);
            loopNode2 = factory.createOSRLoop(threshold, repeating2);
            repeating2.param1 = param2;
        }

        static TwoSilblingLoopNodesTest create(int threshold, OSRLoopFactory factory, TestRepeatingNode repeating1, TestRepeatingNode repeating2) {
            var builder = FrameDescriptor.newBuilder();
            int param1 = builder.addSlot(FrameSlotKind.Int, "param1", null);
            int param2 = builder.addSlot(FrameSlotKind.Int, "param1", null);
            return new TwoSilblingLoopNodesTest(builder.build(), threshold, factory, repeating1, repeating2, param1, param2);
        }

        public OptimizedCallTarget getOSRTarget2() {
            return loopNode2.getCompiledOSRLoop();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            super.execute(frame);
            loopNode2.execute(frame);
            return null;
        }
    }

    /*
     * Test that two loops in a parent child relationship are propagating loop counts.
     */
    @Theory
    public void testTwoLoopsParentChild1(OSRLoopFactory factory) {
        ChildLoopRepeatingNode childLoop = new ChildLoopRepeatingNode(osrThreshold, factory, new TestRepeatingNode(), loop -> {
            assertNotCompiled(loop.getOSRTarget());
            return null;
        });
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, childLoop);
        CallTarget target = rootNode.getCallTarget();

        target.call(1, osrThreshold);
        assertCompiled(rootNode.getOSRTarget());
        assertNotCompiled(childLoop.getOSRTarget());
    }

    @Theory
    public void testTwoLoopsParentChild2(OSRLoopFactory factory) {
        ChildLoopRepeatingNode childLoop = new ChildLoopRepeatingNode(osrThreshold, factory, new TestRepeatingNode(), loop -> {
            assertCompiled(loop.getOSRTarget());
            return null;
        });
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, childLoop);
        CallTarget target = rootNode.getCallTarget();

        target.call(1, osrThreshold + 1);
        assertCompiled(rootNode.getOSRTarget());
        assertCompiled(childLoop.getOSRTarget());
    }

    private static class ChildLoopRepeatingNode extends TestRepeatingNode {

        @Child OptimizedOSRLoopNode loopNode2;

        private final Function<ChildLoopRepeatingNode, Void> onBackedge;

        protected ChildLoopRepeatingNode(int treshold, OSRLoopFactory factory, TestRepeatingNode child, Function<ChildLoopRepeatingNode, Void> onBackedge) {
            this.loopNode2 = factory.createOSRLoop(treshold, child);
            this.onBackedge = onBackedge;

        }

        public OptimizedCallTarget getOSRTarget() {
            return loopNode2.getCompiledOSRLoop();
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                ((TestRepeatingNode) loopNode2.getRepeatingNode()).param1 = param2;
            }
            boolean next = super.executeRepeating(frame);
            if (next) {
                loopNode2.execute(frame);
            } else {
                onBackedge.apply(this);
            }
            return next;
        }

    }

    /*
     * Test that a custom loop reported using LoopNode#reportLoopCount contributes to the OSR
     * compilation heuristic.
     */
    @Theory
    public void testCustomLoopContributingToOSR1(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new CustomInnerLoopRepeatingNode());
        rootNode.getCallTarget().call(10, osrThreshold / 10 - 1); // triggers
        assertNotCompiled(rootNode.getOSRTarget());
        rootNode.getCallTarget().call(10, osrThreshold / 10); // triggers
        assertCompiled(rootNode.getOSRTarget());
    }

    @Theory
    public void testCustomLoopContributingToOSR2(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new CustomInnerLoopRepeatingNode());
        rootNode.getCallTarget().call(1, osrThreshold - 1);
        assertNotCompiled(rootNode.getOSRTarget());
        rootNode.getCallTarget().call(1, osrThreshold);
        assertCompiled(rootNode.getOSRTarget());
    }

    @Theory
    public void testCustomLoopContributingToOSR3(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new CustomInnerLoopRepeatingNode());
        rootNode.getCallTarget().call(2, osrThreshold / 2);
        assertCompiled(rootNode.getOSRTarget());
    }

    @Theory
    public void testCustomLoopContributingToOSR4(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new CustomInnerLoopRepeatingNode());
        rootNode.getCallTarget().call(2, osrThreshold / 2 - 1);
        assertNotCompiled(rootNode.getOSRTarget());
    }

    private static final class CustomInnerLoopRepeatingNode extends TestRepeatingNode {

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            boolean next = super.executeRepeating(frame);
            if (next) {
                // its like beeing in body
                try {
                    int count = frame.getInt(param2);

                    // imagine loop work with size count here

                    LoopNode.reportLoopCount(this, count);

                } catch (FrameSlotTypeException e) {
                    throw new AssertionError();
                }
            }
            return next;
        }

    }

    /*
     * OSR stack frames should not show up.
     */
    @Theory
    public void testStackTraceDoesNotShowOSR(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestOSRStackTrace());
        CallTarget target = rootNode.getCallTarget();
        target.call(1);
        rootNode.forceOSR();
        assertCompiled(rootNode.getOSRTarget());
        target.call(1);
        assertCompiled(rootNode.getOSRTarget());
    }

    /**
     * Test that graal stack frame instances have call nodes associated even when there are OSR
     * frames on the stack.
     */
    @Theory
    public void testStackFrameNodes(OSRLoopFactory factory) {
        TestOSRStackTraceFromAbove testOSRStackTrace = new TestOSRStackTraceFromAbove();
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, testOSRStackTrace);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        rootNode.forceOSR();
        target.call(1);
    }

    /*
     * Test that thread local actions never expose the OSR root node.
     */
    @Theory
    public void testSafepointLocationInLoop(OSRLoopFactory factory) {
        TestRootNode rootNode = TestRootNode.create(osrThreshold, factory, new TestRepeatingNode());

        // avoid thread local action being processed right away
        context.leave();
        AtomicBoolean locationAssertEnabled = new AtomicBoolean(false);
        AtomicInteger safepointCounter = new AtomicInteger(0);
        Future<?> f = languageEnv.submitThreadLocal(null, new ThreadLocalAction(false, false, true) {

            @SuppressWarnings("deprecation")
            @Override
            protected void perform(Access access) { // recurring
                if (locationAssertEnabled.get()) {
                    // we need to make sure we never observe the OSR root node here.
                    // we expect either the loop node or the root node of the loop node as location.
                    Assert.assertTrue(access.getLocation().toString(), access.getLocation() == rootNode || access.getLocation() == rootNode.loopNode);
                    Assert.assertSame(Truffle.getRuntime().iterateFrames((frame) -> frame, 0).getCallTarget(), rootNode.getCallTarget());
                    safepointCounter.incrementAndGet();
                }
            }
        });
        context.enter();

        // enter or close might trigger safepoints, but we have no guarantees on the location there
        locationAssertEnabled.set(true);
        try {
            assertNotCompiled(rootNode.getOSRTarget());
            rootNode.getCallTarget().call(osrThreshold + 1); // triggers
            assertCompiled(rootNode.getOSRTarget());
            assertTrue(safepointCounter.get() > 0);
        } finally {
            f.cancel(true);
        }
    }

    private static class TestOSRStackTrace extends TestRepeatingNode {

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            boolean next = super.executeRepeating(frame);
            if (!next) {
                checkStackTrace();
            }
            return next;
        }

        @TruffleBoundary
        protected void checkStackTrace() {
            final OptimizedOSRLoopNode loop = (OptimizedOSRLoopNode) getParent();
            final OptimizedCallTarget compiledLoop = loop.getCompiledOSRLoop();

            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {

                private boolean first = true;

                @Override
                public Void visitFrame(FrameInstance frameInstance) {
                    Assert.assertNotSame(compiledLoop, frameInstance.getCallTarget());
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

    private static final class TestOSRStackTraceFromAbove extends TestOSRStackTrace {

        @Child DirectCallNode callNode;

        @Override
        @TruffleBoundary
        protected void checkStackTrace() {
            // Check the stack from an additional frame created on top
            RootNode root = new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    TestOSRStackTraceFromAbove.super.checkStackTrace();
                    return null;
                }
            };
            callNode = runtime.createDirectCallNode(root.getCallTarget());
            adoptChildren();
            callNode.call(new Object[]{});
        }

    }

    /* Useful to test no dependencies on a root call target. */
    private static void executeNoCallTarget(TestRootNode rootNode, int count) {
        rootNode.adoptChildren();
        rootNode.execute(Truffle.getRuntime().createVirtualFrame(new Object[]{count}, rootNode.getFrameDescriptor()));
    }

    private interface OSRLoopFactory {
        OptimizedOSRLoopNode createOSRLoop(int threshold, RepeatingNode repeating);
    }

    private static void waitForCompiled(OptimizedCallTarget target) {
        if (target != null) {
            try {
                runtime.waitForCompilation(target, 10000);
            } catch (ExecutionException | TimeoutException e) {
                Assert.fail("timeout");
            }
        }
    }

    private static class TestRootNodeWithReplacement extends TestRootNode {
        @Child OptimizedOSRLoopNode toReplace;

        TestRootNodeWithReplacement(FrameDescriptor descriptor, int threshold, OSRLoopFactory factory, TestRepeatingNode repeating, int param1, int param2) {
            super(descriptor, threshold, factory, repeating, param1, param2);
            toReplace = factory.createOSRLoop(threshold, repeating);
        }

        static TestRootNodeWithReplacement create(int threshold, OSRLoopFactory factory, TestRepeatingNode repeating) {
            var builder = FrameDescriptor.newBuilder();
            int param1 = builder.addSlot(FrameSlotKind.Int, "param1", null);
            int param2 = builder.addSlot(FrameSlotKind.Int, "param1", null);
            return new TestRootNodeWithReplacement(builder.build(), threshold, factory, repeating, param1, param2);
        }

    }

    static class TestRootNode extends RootNode {

        @Child OptimizedOSRLoopNode loopNode;

        final int param1;
        final int param2;

        protected TestRootNode(FrameDescriptor descriptor, int threshold, OSRLoopFactory factory, TestRepeatingNode repeating, int param1, int param2) {
            super(null, descriptor);
            this.param1 = param1;
            this.param2 = param2;
            loopNode = factory.createOSRLoop(threshold, repeating);
            repeating.param1 = param1;
            repeating.param2 = param2;
        }

        static TestRootNode create(int threshold, OSRLoopFactory factory, TestRepeatingNode repeating) {
            var builder = FrameDescriptor.newBuilder();
            int param1 = builder.addSlot(FrameSlotKind.Int, "param1", null);
            int param2 = builder.addSlot(FrameSlotKind.Int, "param1", null);
            return new TestRootNode(builder.build(), threshold, factory, repeating, param1, param2);
        }

        public void forceOSR() {
            loopNode.forceOSR();
        }

        public OptimizedCallTarget getOSRTarget() {
            return loopNode.getCompiledOSRLoop();
        }

        public boolean wasRepeatingCalledCompiled() {
            return ((TestRepeatingNode) loopNode.getRepeatingNode()).compiled;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int nextLoopCount = (int) frame.getArguments()[0];
            frame.setInt(param1, nextLoopCount);
            if (frame.getArguments().length > 1) {
                frame.setInt(param2, (int) frame.getArguments()[1]);
            } else {
                frame.setInt(param2, 0);
            }
            loopNode.execute(frame);
            return null;
        }
    }

    private static class TestRepeatingNode extends Node implements RepeatingNode {
        int invalidationCounter = -1;

        @CompilationFinal int param1;
        @CompilationFinal int param2;

        boolean compiled;

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            try {
                if (invalidationCounter >= 0) {
                    if (invalidationCounter == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invalidationCounter = -1; // disable
                    } else {
                        invalidationCounter--;
                    }
                }

                if (CompilerDirectives.inCompiledCode()) {
                    compiled = true;
                } else {
                    compiled = false;
                }

                int counter = frame.getInt(param1);
                frame.setInt(param1, counter - 1);
                return counter != 0;
            } catch (FrameSlotTypeException e) {
                return false;
            }
        }

    }

    private static final class CallsTargetRepeatingNode extends TestRepeatingNode implements RepeatingNode {

        final OptimizedDirectCallNode callNode;
        int count;

        private CallsTargetRepeatingNode(OptimizedDirectCallNode callNode) {
            this.callNode = callNode;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            callNode.call(frame.getArguments());
            if (CompilerDirectives.inCompiledCode() && count++ < 5) {
                return false;
            }
            return true;
        }
    }
}
