/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.instrumentation.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class RewriteDuringCompilationTest extends AbstractPolyglotTest {
    private static int staticRewriteCount = 0;
    private static List<Integer> nodeExecutionSequence = Collections.synchronizedList(new ArrayList<>());
    private static volatile UpdateStaticFieldNode updateStaticFieldNode;
    private static volatile int lastWrittenValue = -1;

    abstract static class BaseNode extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    static final class UpdateStaticFieldNode extends BaseNode {
        private final int c;

        UpdateStaticFieldNode(int c) {
            this.c = c;
        }

        @Override
        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            return addToList(c);
        }

        @CompilerDirectives.TruffleBoundary
        private static boolean addToList(int value) {
            if (nodeExecutionSequence.size() == 0 || nodeExecutionSequence.get(nodeExecutionSequence.size() - 1) != value) {
                System.out.println("Adding value " + value);
                nodeExecutionSequence.add(value);
                lastWrittenValue = value;
            }
            return value < staticRewriteCount;
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

            @Child private BaseNode child;

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
    public void testRootCompilation() throws IOException, InterruptedException {
        testCompilation(updateStaticFieldNode = new UpdateStaticFieldNode(0), 1000, 10);
    }

    @Test
    public void testLoopCompilation() throws IOException, InterruptedException {
        testCompilation(new WhileLoopNode(1000000, updateStaticFieldNode = new UpdateStaticFieldNode(0)), 1000, 20);
    }

    private void testCompilation(BaseNode testedCode, int rewriteCount, int maxDelayBeforeRewrite) throws IOException, InterruptedException {
        staticRewriteCount = rewriteCount;
        setupEnv(Context.create(), new ProxyLanguage() {
            private final List<CallTarget> targets = new LinkedList<>(); // To prevent from GC

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                com.oracle.truffle.api.source.Source source = request.getSource();
                CallTarget target = Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {

                    @Node.Child private BaseNode child = testedCode;

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

        Random rnd = new Random();
        Thread mainThread = Thread.currentThread();
        Thread t = new Thread(() -> {
            for (int i = 1; i <= rewriteCount; i++) {
                do {
                    try {
                        Thread.sleep(rnd.nextInt(maxDelayBeforeRewrite));
                    } catch (InterruptedException ie) {
                    }
                } while (lastWrittenValue < (i - 1));
                updateStaticFieldNode.replace(updateStaticFieldNode = new UpdateStaticFieldNode(i));
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
            }
            mainThread.interrupt();
        });
        t.start();
        Source source = Source.newBuilder(ProxyLanguage.ID, "", "DummySource").build();
        long iterationCount = 0;
        for (;;) {
            iterationCount++;
            context.eval(source);
            if (Thread.interrupted()) {
                break;
            }
        }
        t.join();
        int problemCount = 0;
        int prevInt = -1;
        for (int i : nodeExecutionSequence) {
            if (prevInt >= i) {
                System.out.println("Wrong order " + prevInt + ", " + i);
                problemCount++;
            }
            prevInt = i;
        }
        System.out.println("Iteration Count = " + iterationCount);
        System.out.println("Problem count = " + problemCount);
        // System.out.println("Execution sequence = " +
        // nodeExecutionSequence.stream().map(Object::toString).collect(Collectors.joining(", ")));
    }
}
