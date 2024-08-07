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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompilerLoggingTest extends TestWithPolyglotOptions {

    private static final String FORMAT_FAILURE = "Failed to compile %s due to %s";
    private static final String FORMAT_SUCCESS = "Compiled %s";
    private static final String MESSAGE_TO_STREAM = "begin";
    private static final String MESSAGE_TO_TTY = "end";

    @Test
    public void testLogging() throws IOException {
        if (!TruffleOptions.AOT) {
            OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
            TruffleCompiler compiler = runtime.newTruffleCompiler();
            Assume.assumeTrue("Not supported in isolated compiler.", compiler instanceof TruffleCompilerImpl);
        }
        try (ByteArrayOutputStream logOut = new ByteArrayOutputStream()) {
            setupContext(Context.newBuilder().logHandler(logOut).option("engine.CompileImmediately", "true").option("engine.MultiTier", "false").option("engine.BackgroundCompilation", "false"));
            OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
            TestListener listener = new TestListener();
            try {
                runtime.addListener(listener);
                OptimizedCallTarget compilable = (OptimizedCallTarget) RootNode.createConstantNode(true).getCallTarget();
                compilable.call();
                String logContent = logOut.toString();
                String expected = String.format(FORMAT_SUCCESS + "%s%s%n", compilable.getName(), MESSAGE_TO_STREAM, MESSAGE_TO_TTY);
                Assert.assertEquals("Expected " + expected + " in " + logContent, expected, logContent);
            } finally {
                runtime.removeListener(listener);
            }
        }
    }

    private static final class TestListener implements OptimizedTruffleRuntimeListener {

        @Override
        public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph,
                        TruffleCompilerListener.CompilationResultInfo result) {
            TTY.printf(FORMAT_SUCCESS, target.getName());
            printCommon();
        }

        @Override
        public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> serializedException) {
            TTY.printf(FORMAT_FAILURE, target.getName(), reason);
            printCommon();
        }

        private static void printCommon() {
            TTY.out().out().print(MESSAGE_TO_STREAM);
            TTY.println(MESSAGE_TO_TTY);
        }
    }

    @Test
    public void testGR46391() throws InterruptedException, BrokenBarrierException, TimeoutException {
        // Test log stream
        Context.Builder builder = Context.newBuilder();
        ByteArrayOutputStream logContent = new ByteArrayOutputStream();
        builder.logHandler(logContent);
        testGR46391Impl("stream", builder);
        assertTrue(logContent.toString().contains(LogAfterEngineClose.BEFORE_CLOSE));
        assertFalse(logContent.toString().contains(LogAfterEngineClose.AFTER_CLOSE));

        // Test log handler
        builder = Context.newBuilder();
        CollectingHandler handler = new CollectingHandler();
        builder.logHandler(handler);
        testGR46391Impl("handler", builder);
        assertTrue(handler.getContent().contains(LogAfterEngineClose.BEFORE_CLOSE));
        assertFalse(handler.getContent().contains(LogAfterEngineClose.AFTER_CLOSE));
    }

    private void testGR46391Impl(String testName, Context.Builder builder) throws InterruptedException, BrokenBarrierException, TimeoutException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        String rootName = String.format("%s.%s", TestRootNode.class.getName(), testName);
        OptimizedTruffleRuntimeListener listener = new LogAfterEngineClose(rootName, barrier);
        var runtime = OptimizedTruffleRuntime.getRuntime();
        runtime.addListener(listener);
        try {
            builder.option("engine.CompileImmediately", "true");
            builder.option("engine.BackgroundCompilation", "true");
            builder.option("engine.CompilationFailureAction", "Throw");
            Context context = setupContext(builder);
            OptimizedCallTarget callTarget = (OptimizedCallTarget) new TestRootNode(rootName).getCallTarget();
            callTarget.call();
            // Wait for TestRootNode compilation
            barrier.await(2, TimeUnit.MINUTES);
            context.close();
            // Signal Engine closed
            barrier.await(2, TimeUnit.MINUTES);
            // Wait for logging to the closed Engine's handler finished
            barrier.await(2, TimeUnit.MINUTES);
        } finally {
            runtime.removeListener(listener);
        }
    }

    private static final class TestRootNode extends RootNode {

        private final String name;

        TestRootNode(String name) {
            super(null);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }
    }

    private static final class LogAfterEngineClose implements OptimizedTruffleRuntimeListener {
        private static final String BEFORE_CLOSE = String.format("%s#BEFORE CLOSE", LogAfterEngineClose.class.getSimpleName());
        private static final String AFTER_CLOSE = String.format("%s#AFTER CLOSE", LogAfterEngineClose.class.getSimpleName());
        private final String expectedRootName;
        private final CyclicBarrier barrier;

        LogAfterEngineClose(String expectedRootName, CyclicBarrier barrier) {
            this.expectedRootName = expectedRootName;
            this.barrier = barrier;
        }

        @Override
        public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph) {
            if (expectedRootName.equals(target.getRootNode().getName())) {
                target.log(BEFORE_CLOSE);
                try {
                    // Signal TestRootNode compilation
                    barrier.await(30, TimeUnit.SECONDS);
                    // Wait for Engine close
                    barrier.await(30, TimeUnit.SECONDS);
                    target.log(AFTER_CLOSE);
                    // Signal logging to closed Engine's handler finished
                    barrier.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    private static final class CollectingHandler extends Handler {

        private boolean closed;
        private StringBuilder content = new StringBuilder();

        @Override
        public synchronized void publish(LogRecord record) {
            if (closed) {
                throw new IllegalStateException("Handler closed");
            }
            content.append(record.getMessage()).append("\n");
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
            closed = true;
        }

        synchronized String getContent() {
            return content.toString();
        }
    }
}
