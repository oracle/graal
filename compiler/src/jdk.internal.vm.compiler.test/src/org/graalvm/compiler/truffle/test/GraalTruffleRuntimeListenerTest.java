/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public final class GraalTruffleRuntimeListenerTest extends TestWithPolyglotOptions {

    @Override
    protected Context.Builder newContextBuilder() {
        return super.newContextBuilder().option("compiler.EncodedGraphCache", "false");
    }

    @Test
    public void testCompilationSuccess() {
        setupContext("engine.CompileImmediately", "true", "engine.BackgroundCompilation", "false");
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        OptimizedCallTarget compilable = (OptimizedCallTarget) RootNode.createConstantNode(true).getCallTarget();
        TestListener listener = new TestListener(compilable);
        try {
            runtime.addListener(listener);
            compilable.call();
            listener.assertEvents(
                            EventType.ENQUEUED,
                            EventType.COMPILATION_STARTED,
                            EventType.TRUFFLE_TIER_FINISHED,
                            EventType.GRAAL_TIER_FINISHED,
                            EventType.COMPILATION_SUCCESS);
        } finally {
            runtime.removeListener(listener);
        }
    }

    @Test
    public void testCompilationFailure() throws IOException, InterruptedException {
        // Run in a subprocess to clean dumped graal graphs
        executeInSubprocess(() -> {
            Context.Builder builder = newContextBuilder().logHandler(new ByteArrayOutputStream());
            builder.option("engine.CompileImmediately", "true");
            builder.option("engine.BackgroundCompilation", "false");
            setupContext(builder);
            OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
            OptimizedCallTarget compilable = (OptimizedCallTarget) createFailureNode().getCallTarget();
            TestListener listener = new TestListener(compilable);
            try {
                runtime.addListener(listener);
                compilable.call();
                listener.assertEvents(
                                EventType.ENQUEUED,
                                EventType.COMPILATION_STARTED,
                                EventType.COMPILATION_FAILURE);
            } finally {
                runtime.removeListener(listener);
            }
        });
    }

    @Test
    public void testCompilationFailureRetry() throws IOException, InterruptedException {
        // Run in a subprocess to clean dumped graal graphs
        executeInSubprocess(() -> {
            setupContext(Context.newBuilder().logHandler(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            }).allowExperimentalOptions(true).option("engine.CompileImmediately", "true").option("engine.BackgroundCompilation", "false").option("engine.CompilationFailureAction", "Diagnose"));
            OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
            OptimizedCallTarget compilable = (OptimizedCallTarget) createFailureNode().getCallTarget();
            TestListener listener = new TestListener(compilable);
            try {
                runtime.addListener(listener);
                compilable.call();
                listener.assertEvents(
                                EventType.ENQUEUED,
                                EventType.COMPILATION_STARTED,
                                EventType.COMPILATION_FAILURE,
                                EventType.ENQUEUED,
                                EventType.COMPILATION_STARTED,
                                EventType.COMPILATION_FAILURE);
            } finally {
                runtime.removeListener(listener);
            }
        });
    }

    @Test
    public void testBlockCompilation() {
        setupContext("engine.CompileImmediately", "true",
                        "engine.BackgroundCompilation", "false",
                        "engine.PartialBlockCompilationSize", "1");
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        OptimizedCallTarget compilable = (OptimizedCallTarget) createBlocks().getCallTarget();
        compilable.computeBlockCompilations();
        TestListener listener = new TestListener(compilable);
        try {
            runtime.addListener(listener);
            compilable.compile(!compilable.engine.multiTier);
            listener.assertEvents(
                            // Main Call Target - enqueued
                            EventType.ENQUEUED,
                            // Partial Block 1
                            EventType.ENQUEUED,
                            EventType.COMPILATION_STARTED,
                            EventType.TRUFFLE_TIER_FINISHED,
                            EventType.GRAAL_TIER_FINISHED,
                            EventType.COMPILATION_SUCCESS,
                            // Partial Block 2
                            EventType.ENQUEUED,
                            EventType.COMPILATION_STARTED,
                            EventType.TRUFFLE_TIER_FINISHED,
                            EventType.GRAAL_TIER_FINISHED,
                            EventType.COMPILATION_SUCCESS,
                            // Main Call Target - rest
                            EventType.COMPILATION_STARTED,
                            EventType.TRUFFLE_TIER_FINISHED,
                            EventType.GRAAL_TIER_FINISHED,
                            EventType.COMPILATION_SUCCESS);
        } finally {
            runtime.removeListener(listener);
        }
    }

    @Test
    public void testBlockCompilationLargeBlocks() {
        setupContext("engine.CompileImmediately", "true",
                        "engine.BackgroundCompilation", "false",
                        "engine.PartialBlockCompilationSize", "1",
                        "engine.PartialBlockMaximumSize", "0");
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        OptimizedCallTarget compilable = (OptimizedCallTarget) createBlocks().getCallTarget();
        compilable.computeBlockCompilations();
        TestListener listener = new TestListener(compilable);
        try {
            runtime.addListener(listener);
            compilable.compile(!compilable.engine.multiTier);
            listener.assertEvents(
                            // Main Call Target - enqueued
                            EventType.ENQUEUED,
                            // Partial Block 1
                            EventType.ENQUEUED,
                            EventType.DEQUEUED,
                            // Partial Block 2
                            EventType.ENQUEUED,
                            EventType.DEQUEUED,
                            // Main Call Target - rest
                            EventType.COMPILATION_STARTED,
                            EventType.TRUFFLE_TIER_FINISHED,
                            EventType.GRAAL_TIER_FINISHED,
                            EventType.COMPILATION_SUCCESS);
        } finally {
            runtime.removeListener(listener);
        }
    }

    @Test
    public void testBlockCompilationMaximumGraalGraphSize() {
        int blockSize = 100;
        int nodeCount = 1000;
        setupContext("engine.CompileImmediately", "true",
                        "engine.BackgroundCompilation", "false",
                        "engine.PartialBlockCompilationSize", String.valueOf(blockSize),
                        "compiler.MaximumGraalGraphSize", "20000");
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        AbstractTestNode[] children = new AbstractTestNode[nodeCount];
        for (int i = 0; i < children.length; i++) {
            children[i] = new ExpensiveTestNode();
        }
        BlockNode<AbstractTestNode> block = BlockNode.create(children, new NodeExecutor());
        OptimizedCallTarget compilable = (OptimizedCallTarget) new TestRootNode(block).getCallTarget();
        TestListener listener = new TestListener(compilable);
        try {
            runtime.addListener(listener);
            compilable.compile(!compilable.engine.multiTier);
            List<EventType> expectedEvents = new ArrayList<>();
            // Main CallTarget
            expectedEvents.add(EventType.ENQUEUED);
            expectedEvents.add(EventType.COMPILATION_STARTED);
            expectedEvents.add(EventType.COMPILATION_FAILURE);
            expectedEvents.add(EventType.DEQUEUED);
            // Main CallTarget enqueued for re-compilation
            expectedEvents.add(EventType.ENQUEUED);
            // New partial blocks CallTargets
            for (int i = 0; i < nodeCount / blockSize; i++) {
                expectedEvents.add(EventType.ENQUEUED);
                expectedEvents.add(EventType.COMPILATION_STARTED);
                expectedEvents.add(EventType.TRUFFLE_TIER_FINISHED);
                expectedEvents.add(EventType.GRAAL_TIER_FINISHED);
                expectedEvents.add(EventType.COMPILATION_SUCCESS);
            }
            // Main CallTarget re-compilation
            expectedEvents.add(EventType.COMPILATION_STARTED);
            expectedEvents.add(EventType.TRUFFLE_TIER_FINISHED);
            expectedEvents.add(EventType.GRAAL_TIER_FINISHED);
            expectedEvents.add(EventType.COMPILATION_SUCCESS);
            listener.assertEvents(expectedEvents.toArray(new EventType[expectedEvents.size()]));
        } finally {
            runtime.removeListener(listener);
        }
    }

    private static RootNode createFailureNode() {
        CompilerAssertsTest.NeverPartOfCompilationTestNode result = new CompilerAssertsTest.NeverPartOfCompilationTestNode();
        return new RootTestNode(new FrameDescriptor(), "neverPartOfCompilation", result);
    }

    private static RootNode createBlocks() {
        BlockNode<AbstractTestNode> block = createBlocks(1, 2);
        return new TestRootNode(block);
    }

    private static BlockNode<AbstractTestNode> createBlocks(int depth, int blockSize) {
        if (depth == 0) {
            return null;
        }
        NodeExecutor nodeExecutor = new NodeExecutor();
        AbstractTestNode[] children = new AbstractTestNode[blockSize];
        for (int i = 0; i < children.length; i++) {
            children[i] = new NestedTestNode(createBlocks(depth - 1, blockSize));
        }
        return BlockNode.create(children, nodeExecutor);
    }

    @SuppressWarnings("unchecked")
    private static List<OptimizedCallTarget> getBlocks(OptimizedCallTarget callTarget) {
        try {
            Field blockCompilations = OptimizedCallTarget.class.getDeclaredField("blockCompilations");
            blockCompilations.setAccessible(true);
            return (List<OptimizedCallTarget>) blockCompilations.get(callTarget);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read blockCompilations", e);
        }
    }

    private abstract static class AbstractTestNode extends Node {

        public abstract Object execute(VirtualFrame frame);

    }

    private static final class NestedTestNode extends AbstractTestNode {

        @Child private BlockNode<?> block;

        NestedTestNode(BlockNode<?> block) {
            this.block = block;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (block != null) {
                block.executeGeneric(frame, BlockNode.NO_ARGUMENT);
            }
            return true;
        }
    }

    private static final class ExpensiveTestNode extends AbstractTestNode {

        static volatile int res;

        ExpensiveTestNode() {
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int a = res;
            int b = other(a);
            a = (int) Math.sqrt(a * a - b * b);
            res = a;
            return res;
        }

        @CompilerDirectives.TruffleBoundary
        private static int other(int limit) {
            return (int) (Math.random() * limit);
        }
    }

    private static final class TestRootNode extends RootNode {
        @Child private BlockNode<?> child;

        TestRootNode(BlockNode<?> child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            child.executeVoid(frame, BlockNode.NO_ARGUMENT);
            return true;
        }
    }

    private static final class NodeExecutor implements BlockNode.ElementExecutor<AbstractTestNode> {

        @Override
        public void executeVoid(VirtualFrame frame, AbstractTestNode node, int index, int argument) {
            node.execute(frame);
        }
    }

    private enum EventType {
        ENQUEUED,
        COMPILATION_STARTED,
        TRUFFLE_TIER_FINISHED,
        GRAAL_TIER_FINISHED,
        COMPILATION_SUCCESS,
        COMPILATION_FAILURE,
        DEQUEUED,
    }

    private static final class TestListener implements OptimizedTruffleRuntimeListener {

        private static final long TIMEOUT = 10_000;

        private final OptimizedCallTarget initialCallTarget;
        private final List<EventType> events;
        private boolean initialCallTargetEnqueued = false;

        TestListener(OptimizedCallTarget callTarget) {
            this.initialCallTarget = callTarget;
            this.events = Collections.synchronizedList(new ArrayList<EventType>());
        }

        @Override
        public void onCompilationQueued(OptimizedCallTarget target, int tier) {
            if (isImportant(target)) {
                if (!initialCallTarget.equals(target)) {
                    waitForInitialTarget();
                }
                events.add(EventType.ENQUEUED);
                notifyInitialCallTarget();
            }
        }

        @Override
        public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
            if (isImportant(target)) {
                events.add(EventType.DEQUEUED);
            }
        }

        @Override
        public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
            if (isImportant(target)) {
                waitForInitialTarget();
                events.add(EventType.COMPILATION_STARTED);
            }
        }

        @Override
        public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph) {
            if (isImportant(target)) {
                events.add(EventType.TRUFFLE_TIER_FINISHED);
            }
        }

        @Override
        public void onCompilationGraalTierFinished(OptimizedCallTarget target, TruffleCompilerListener.GraphInfo graph) {
            if (isImportant(target)) {
                events.add(EventType.GRAAL_TIER_FINISHED);
            }
        }

        @Override
        public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph,
                        TruffleCompilerListener.CompilationResultInfo result) {
            if (isImportant(target)) {
                events.add(EventType.COMPILATION_SUCCESS);
            }
        }

        @Override
        public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> serializedException) {
            if ((isImportant(target))) {
                events.add(EventType.COMPILATION_FAILURE);
            }
        }

        void assertEvents(EventType... expectedEvents) {
            assertEquals(Arrays.asList(expectedEvents), events);
        }

        private boolean isImportant(OptimizedCallTarget target) {
            return initialCallTarget.equals(target) || getBlocks(initialCallTarget).contains(target);
        }

        private synchronized void waitForInitialTarget() {
            try {
                long deadline = System.currentTimeMillis() + TIMEOUT;
                while (!initialCallTargetEnqueued && System.currentTimeMillis() < deadline) {
                    wait(TIMEOUT);
                }
                if (initialCallTargetEnqueued) {
                    return;
                }
            } catch (InterruptedException ie) {
                // pass
            }
            throw new IllegalStateException("Did not received the initial call target with in a time limit.");
        }

        private synchronized void notifyInitialCallTarget() {
            initialCallTargetEnqueued = true;
            notifyAll();
        }
    }

    private static void executeInSubprocess(Runnable action) throws IOException, InterruptedException {
        Path tmpDir = SubprocessTestUtils.isSubprocess() ? null : Files.createTempDirectory(GraalTruffleRuntimeListenerTest.class.getSimpleName());
        try {
            SubprocessTestUtils.executeInSubprocess(GraalTruffleRuntimeListenerTest.class, action, String.format("-Dgraal.DumpPath=%s", tmpDir));
        } finally {
            if (tmpDir != null) {
                Files.walk(tmpDir).sorted(Comparator.reverseOrder()).forEach(GraalTruffleRuntimeListenerTest::delete);
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ioException) {
            throw new AssertionError(ioException.getMessage(), ioException);
        }
    }
}
