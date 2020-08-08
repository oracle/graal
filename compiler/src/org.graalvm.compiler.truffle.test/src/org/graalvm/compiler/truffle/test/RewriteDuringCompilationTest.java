/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class RewriteDuringCompilationTest extends AbstractPolyglotTest {
    abstract static class BaseNode extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    static final class DetectInvalidCodeNode extends BaseNode {
        private volatile boolean valid = true;
        private boolean invalidTwice = false;

        @Override
        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            if (!valid) {
                /*
                 * In the interpreter, setting valid to false might happen just before the if
                 * statement, yielding false test failures, and so we fail only if the invalid node
                 * is executed twice.
                 */
                if (invalidTwice) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Obsolete code got executed!");
                } else {
                    invalidTwice = true;
                }
            }
            return false;
        }
    }

    static final class WhileLoopNode extends BaseNode {

        @Child private LoopNode loop;

        @CompilerDirectives.CompilationFinal FrameSlot loopIndexSlot;
        @CompilerDirectives.CompilationFinal FrameSlot loopResultSlot;

        WhileLoopNode(Object loopCount, BaseNode child) {
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, child));
        }

        FrameSlot getLoopIndex() {
            if (loopIndexSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                loopIndexSlot = getRootNode().getFrameDescriptor().findOrAddFrameSlot("loopIndex" + getLoopDepth());
            }
            return loopIndexSlot;
        }

        FrameSlot getResult() {
            if (loopResultSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                loopResultSlot = getRootNode().getFrameDescriptor().findOrAddFrameSlot("loopResult" + getLoopDepth());
            }
            return loopResultSlot;
        }

        private int getLoopDepth() {
            Node node = getParent();
            int count = 0;
            while (node != null) {
                if (node instanceof WhileLoopNode) {
                    count++;
                }
                node = node.getParent();
            }
            return count;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(getResult(), InstrumentationTestLanguage.Null.INSTANCE);
            frame.setInt(getLoopIndex(), 0);
            loop.execute(frame);
            try {
                return frame.getObject(loopResultSlot);
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
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

    @Test
    public void testRootCompilation() throws IOException, InterruptedException, ExecutionException {
        DetectInvalidCodeNode detectInvalidCodeNode = new DetectInvalidCodeNode();
        testCompilation(detectInvalidCodeNode, null, detectInvalidCodeNode, 1000, 20);
    }

    @Test
    public void testLoopCompilation() throws IOException, InterruptedException, ExecutionException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        DetectInvalidCodeNode detectInvalidCodeNode = new DetectInvalidCodeNode();
        WhileLoopNode testedCode = new WhileLoopNode(10000000, detectInvalidCodeNode);
        testCompilation(testedCode, testedCode.loop, detectInvalidCodeNode, 1000, 40);
    }

    private volatile boolean rewriting = false;

    private void testCompilation(BaseNode testedCode, LoopNode loopNode, DetectInvalidCodeNode nodeToRewrite, int rewriteCount, int maxDelayBeforeRewrite)
                    throws IOException, InterruptedException, ExecutionException {
        setupEnv(Context.create(), new ProxyLanguage() {
            private final List<CallTarget> targets = new LinkedList<>(); // To prevent from GC

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                com.oracle.truffle.api.source.Source source = request.getSource();
                CallTarget target = Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {

                    @Node.Child private volatile BaseNode child = testedCode;

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return child.execute(frame);
                    }

                    @Override
                    public SourceSection getSourceSection() {
                        return source.createSection(1);
                    }

                });
                targets.add(target);
                return target;
            }
        });

        AtomicReference<DetectInvalidCodeNode> nodeToRewriteReference = new AtomicReference<>(nodeToRewrite);
        Random rnd = new Random();
        CountDownLatch nodeRewritingLatch = new CountDownLatch(1);
        List<Object> callTargetsToCheck = new ArrayList<>();
        rewriting = true;
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Future<?> future = executor.submit(() -> {
            try {
                for (int i = 1; i <= rewriteCount && rewriting; i++) {
                    try {
                        Thread.sleep(rnd.nextInt(maxDelayBeforeRewrite));
                        nodeRewritingLatch.await();
                    } catch (InterruptedException ie) {
                    }
                    Object loopNodeCallTarget = getLoopNodeCallTarget(loopNode);
                    if (loopNodeCallTarget != null) {
                        callTargetsToCheck.add(loopNodeCallTarget);
                    }
                    DetectInvalidCodeNode previousNode = nodeToRewriteReference.get();
                    DetectInvalidCodeNode newNode = new DetectInvalidCodeNode();
                    nodeToRewriteReference.set(newNode);
                    previousNode.replace(newNode);
                    previousNode.valid = false;
                }
            } finally {
                rewriting = false;
            }
        });
        Source source = Source.newBuilder(ProxyLanguage.ID, "", "DummySource").build();
        try {
            for (;;) {
                context.eval(source);
                nodeRewritingLatch.countDown();
                if (!rewriting) {
                    break;
                }
            }
        } finally {
            rewriting = false;
            future.get();
            executor.shutdownNow();
            executor.awaitTermination(100, TimeUnit.SECONDS);
        }
        for (Object callTarget : callTargetsToCheck) {
            Assert.assertFalse("Obsolete loop call target is still valid", isLoopNodeCallTargetValid(callTarget));
        }
    }

    private static Object getLoopNodeCallTarget(LoopNode loopNode) {
        Object toRet = null;
        if (loopNode instanceof ReplaceObserver) {
            try {
                Field callTargetField = loopNode.getClass().getSuperclass().getDeclaredField("compiledOSRLoop");
                callTargetField.setAccessible(true);
                toRet = callTargetField.get(loopNode);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new AssertionError("Unable to obtain OSR call target of a loop node!", e);
            }
        }
        return toRet;
    }

    private static boolean isLoopNodeCallTargetValid(Object callTarget) {
        boolean toRet = false;
        if (callTarget != null) {
            try {
                Method isValidMethod = callTarget.getClass().getMethod("isValid");
                toRet = (Boolean) isValidMethod.invoke(callTarget);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new AssertionError("Unable to call isValid on OSR call target of a loop node!", e);
            }
        }
        return toRet;
    }
}
