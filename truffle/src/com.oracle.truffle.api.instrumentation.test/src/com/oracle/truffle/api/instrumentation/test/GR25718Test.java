/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecuteSourceEvent;
import com.oracle.truffle.api.instrumentation.ExecuteSourceListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.test.DeadlockTest.TestDeadlockInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * Test that instrumentation does not deadlock when installed into a running application. We test a
 * fix of deadlock that occurs when addSourceExecutionBinding() fires notifications synchronously.
 * <p>
 * There is a main thread running the application code and an instrumentation thread, that adds
 * instrumentation in parallel. To verify the deadlock, we need to assure that the relevant code
 * really executes in parallel. This is achieved by several uses of CountDownLatch.
 * <p>
 * 1. The instrumentation thread waits for the execution to create and execute some nodes.<br>
 * 2. The execution thread then waits for the instrumentation to attach ExecuteSourceListener.<br>
 * 3. During synchronous notifications of executed sources, register ExecutionEventFactory. <br>
 * 4. Visit loaded source sections.
 */
public class GR25718Test {

    private static final String CODE_LATCHES = "getExecLatches";
    private static final String CODE_MULTI_SOURCE = "multiSource";

    @Test
    public void testNoDeadlockOnParallelSourceVisit() throws InterruptedException {
        ProxyLanguage.setDelegate(new RunningAppLanguage());
        try (Engine engine = Engine.create()) {
            Instrument instrument = engine.getInstruments().get(TestDeadlockInstrument.ID);
            TestDeadlockInstrument deadlockInstrument = instrument.lookup(TestDeadlockInstrument.class);
            InstrumentationThread instrumentationThread = new InstrumentationThread(engine, deadlockInstrument.instrumentEnv);

            try (Context context = Context.newBuilder().engine(engine).build()) {
                // Start the instrumentation in parallel with execution of source2:
                instrumentationThread.start();

                org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(ProxyLanguage.ID, CODE_MULTI_SOURCE);
                context.eval(source2);
                instrumentationThread.join();
            }
        }
    }

    private class InstrumentationThread extends Thread {

        private final Engine engine;
        private final TruffleInstrument.Env env;
        private Iterator<CountDownLatch> executionLatches;

        InstrumentationThread(Engine engine, TruffleInstrument.Env env) {
            super("Instrumentation Thread");
            this.engine = engine;
            this.env = env;
        }

        void setExecutionLatches(List<CountDownLatch> latches) {
            this.executionLatches = latches.iterator();
        }

        @Override
        public void run() {
            try (Context c = Context.newBuilder().engine(engine).build()) {
                c.eval(RunningAppLanguage.ID, CODE_LATCHES);
            }
            // Wait for the execution of source2 to execute some nodes
            try {
                executionLatches.next().await();
            } catch (InterruptedException ex) {
            }
            AtomicBoolean onExecuteCalled = new AtomicBoolean(false);
            AtomicBoolean createEventNodeCalled = new AtomicBoolean(false);
            CountDownLatch executionEventLatch = new CountDownLatch(1);

            env.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    env.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.newBuilder().sourceIs(event.getSource()).build(), s -> {
                    });
                    env.getInstrumenter().attachExecutionEventFactory(SourceSectionFilter.ANY, context -> {
                        if (!createEventNodeCalled.getAndSet(true)) {
                            executionEventLatch.countDown();
                        }
                        env.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.newBuilder().sourceIs(context.getInstrumentedSourceSection().getSource()).build(), s -> {
                        });
                        return new ExecutionEventNode() {
                        };
                    });
                    if (!onExecuteCalled.getAndSet(true)) {
                        // Resume execution of the application
                        executionLatches.next().countDown();
                        // We need to wait till the execution thread produces execution event
                        try {
                            executionEventLatch.await();
                        } catch (InterruptedException ex) {
                            throw CompilerDirectives.shouldNotReachHere(ex);
                        }
                    }
                }
            }, true);
        }

    }

    static class RunningAppLanguage extends ProxyLanguage {

        private final List<CountDownLatch> executionLatchList = Arrays.asList(new CountDownLatch(1), new CountDownLatch(1));
        private final Iterator<CountDownLatch> executionLatches = executionLatchList.iterator();

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final Source code = request.getSource();
            final CharSequence codeCharacters = code.getCharacters();
            if (CODE_LATCHES.equals(codeCharacters)) {
                ((InstrumentationThread) Thread.currentThread()).setExecutionLatches(executionLatchList);
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return Boolean.TRUE;
                    }
                });
            }
            return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return runApp();
                }

                @TruffleBoundary
                private Object runApp() {
                    RootNode codeExecRoot = new RootNode(languageInstance) {

                        @Node.Child private StatementToRun statement = new StatementToRun(code);

                        @Override
                        public Object execute(VirtualFrame f) {
                            return statement.execute(f, codeCharacters);
                        }

                        @Override
                        public SourceSection getSourceSection() {
                            return code.createSection(1);
                        }

                    };
                    CallTarget codeExec = Truffle.getRuntime().createCallTarget(codeExecRoot);
                    codeExec.call();
                    // Resume instrumentation thread
                    executionLatches.next().countDown();
                    // Wait for onExecute() callback in the instrumentation
                    try {
                        executionLatches.next().await();
                    } catch (InterruptedException ex) {
                    }
                    Object ret = codeExec.call();
                    return ret;
                }

                @Override
                public SourceSection getSourceSection() {
                    return Source.newBuilder(ID, "test", "name").build().createSection(1);
                }

            });
        }

        @GenerateWrapper
        public static class StatementToRun extends Node implements InstrumentableNode {

            private final Source source;

            StatementToRun(Source source) {
                this.source = source;
            }

            StatementToRun(StatementToRun copy) {
                this.source = copy.source;
            }

            public Object execute(@SuppressWarnings("unused") VirtualFrame frame, CharSequence code) {
                return toUpper(code);
            }

            @TruffleBoundary
            private static String toUpper(CharSequence code) {
                return code.toString().toUpperCase();
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
                return new StatementToRunWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag.equals(StandardTags.StatementTag.class);
            }

            @Override
            public SourceSection getSourceSection() {
                return source.createSection(1);
            }
        }
    }
}
