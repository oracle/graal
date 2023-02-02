/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class CodeInvalidationTest extends AbstractPolyglotTest {

    private static final String NODE_REPLACE_SUCCESSFUL = "Node replace successful!";

    abstract static class BaseNode extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    static final class NodeToInvalidate extends BaseNode {
        private final ThreadLocal<Boolean> valid;
        private final CountDownLatch latch;
        private final ThreadLocal<Boolean> latchCountedDown = ThreadLocal.withInitial(() -> false);

        NodeToInvalidate(ThreadLocal<Boolean> valid, CountDownLatch latch) {
            this.valid = valid;
            this.latch = latch;
        }

        @Override
        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                if (!isValid()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Code invalidated!");
                }
            }
            return false;
        }

        @CompilerDirectives.TruffleBoundary
        boolean isValid() {
            if (!latchCountedDown.get()) {
                latch.countDown();
                latchCountedDown.set(true);
            }
            return valid.get();
        }
    }

    static final class AlwaysThrowNode extends BaseNode {
        @Override
        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new RuntimeException(NODE_REPLACE_SUCCESSFUL);
        }
    }

    static final class WhileLoopNode extends BaseNode {

        @Child private LoopNode loop;

        @CompilerDirectives.CompilationFinal int loopIndexSlot;
        @CompilerDirectives.CompilationFinal int loopResultSlot;

        WhileLoopNode(Object loopCount, BaseNode child, FrameDescriptor.Builder frameBuilder) {
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, child));
            this.loopIndexSlot = frameBuilder.addSlot(FrameSlotKind.Int, "loopIndex", frameBuilder);
            this.loopResultSlot = frameBuilder.addSlot(FrameSlotKind.Int, "loopResult", frameBuilder);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(loopResultSlot, false);
            frame.setInt(loopIndexSlot, 0);
            loop.execute(frame);
            try {
                return frame.getObject(loopResultSlot);
            } catch (FrameSlotTypeException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        final class LoopConditionNode extends BaseNode implements RepeatingNode {

            @Child private volatile BaseNode child;

            private final int loopCount;
            private final boolean infinite;

            LoopConditionNode(Object loopCount, BaseNode child) {
                this.child = child;
                boolean inf = false;
                if (loopCount instanceof Double) {
                    if (((Double) loopCount).isInfinite()) {
                        inf = true;
                    }
                    this.loopCount = ((Double) loopCount).intValue();
                } else if (loopCount instanceof Integer) {
                    this.loopCount = (int) loopCount;
                } else {
                    this.loopCount = 0;
                }
                this.infinite = inf;

            }

            @Override
            public boolean executeRepeating(VirtualFrame frame) {
                int i;
                try {
                    i = frame.getInt(loopIndexSlot);
                } catch (FrameSlotTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(e);
                }
                if (infinite || i < loopCount) {
                    Object resultValue = execute(frame);
                    frame.setInt(loopIndexSlot, i + 1);
                    frame.setObject(loopResultSlot, resultValue);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            Object execute(VirtualFrame frame) {
                return child.execute(frame);
            }
        }
    }

    private static class RunCode implements Runnable {
        private final NodeToInvalidate nodeToInvalidate;
        private final Context context;
        private final Source code;

        RunCode(Context context, Source code, NodeToInvalidate nodeToInvalidate) {
            this.context = context;
            this.code = code;
            this.nodeToInvalidate = nodeToInvalidate;
        }

        @Override
        public void run() {
            try {
                if (nodeToInvalidate != null) {
                    nodeToInvalidate.valid.set(false);
                }
                context.eval(code);
            } catch (PolyglotException e) {
                if ("java.lang.AssertionError: Code invalidated!".equals(e.getMessage())) {
                    nodeToInvalidate.replace(new AlwaysThrowNode());
                } else {
                    throw e;
                }
            }
        }
    }

    @Test
    public void testInvalidation() throws IOException, InterruptedException {
        // with compile immediately this test does not trigger OSR
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());

        /*
         * The test runs the same compiled code in two threads. Invalidation in one thread using
         * CompilerDirectives#transferToInterpreterAndInvalidate causes deopt in that thread and
         * makes the code non-enterable. However, the other thread does not necessarily deopt and
         * can still continue executing the invalidated code, so a subsequent node replace must also
         * deopt the other thread in order for the replace to have effect. This test checks whether
         * this works properly.
         */
        CountDownLatch latch = new CountDownLatch(1);
        NodeToInvalidate nodeToInvalidate = new NodeToInvalidate(ThreadLocal.withInitial(() -> true), latch);
        FrameDescriptor.Builder frameBuilder = FrameDescriptor.newBuilder();
        WhileLoopNode testedCode = new WhileLoopNode(1000000000, nodeToInvalidate, frameBuilder);
        LoopNode loopNode = testedCode.loop;

        setupEnv(Context.create(), new ProxyLanguage() {
            /**
             * Makes sure we use the same call target for the single source that we use. Otherwise
             * storing the frame slots in member fields of WhileLoopNode wouldn't work as the
             * WhileLoopNode is pre-created before the parsing, and so it can be used only in one
             * call target (root node).
             */
            private CallTarget target;

            @Override
            protected synchronized CallTarget parse(ParsingRequest request) {
                com.oracle.truffle.api.source.Source source = request.getSource();
                if (target == null) {
                    target = new RootNode(languageInstance, frameBuilder.build()) {

                        @Node.Child private volatile BaseNode child = testedCode;

                        @Override
                        public Object execute(VirtualFrame frame) {
                            return child.execute(frame);
                        }

                        @Override
                        public SourceSection getSourceSection() {
                            return source.createSection(1);
                        }

                    }.getCallTarget();
                }
                return target;
            }

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });

        Source source = Source.newBuilder(ProxyLanguage.ID, "", "DummySource").build();
        Future<?> future1;
        Future<?> future2;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            future1 = executor.submit(new RunCode(context, source, null));
            nodeToInvalidate.latch.await();
            /*
             * The latch is counted down only in compiled code, so the code should be compiled now.
             */
            OptimizedCallTarget loopCallTarget = ((OptimizedOSRLoopNode) loopNode).getCompiledOSRLoop();
            Assert.assertNotNull(loopCallTarget);
            Assert.assertTrue(loopCallTarget.isValid());
            future2 = executor.submit(new RunCode(context, source, nodeToInvalidate));
            future1.get();
            future2.get();
            Assert.fail();
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof PolyglotException);
            Assert.assertEquals("java.lang.RuntimeException: " + NODE_REPLACE_SUCCESSFUL, e.getCause().getMessage());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(100, TimeUnit.SECONDS);
        }
    }
}
