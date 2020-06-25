/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecuteSourceEvent;
import com.oracle.truffle.api.instrumentation.ExecuteSourceListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * Test that instrumentation does not deadlock with language execution.
 */
public class DeadlockTest {

    private static final String CODE_WAIT = "wait materialized";
    private static final String CODE_LATCH = "getExecLatch";

    @Test
    public void testNoDeadlockOnSourceLoadBindingInstall() throws InterruptedException {
        testNoDeadlockOnBindingInstall(true);
    }

    @Test
    public void testNoDeadlockOnSourceExecuteBindingInstall() throws InterruptedException {
        testNoDeadlockOnBindingInstall(false);
    }

    private void testNoDeadlockOnBindingInstall(boolean load) throws InterruptedException {
        Engine engine = Engine.create();
        Instrument instrument = engine.getInstruments().get(TestDeadlockInstrument.ID);
        TestDeadlockInstrument deadlockInstrument = instrument.lookup(TestDeadlockInstrument.class);
        InstrumentationThread instrumentationThread = new InstrumentationThread(engine, deadlockInstrument.instrumentEnv, load);

        Context context = Context.newBuilder().engine(engine).build();
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(LockingLanguage.ID, "test");
        // Run a source which is instrumented later on from the instrumentation thread:
        context.eval(source1);

        // Start the instrumentation in parallel with execution of source2:
        instrumentationThread.start();

        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(LockingLanguage.ID, CODE_WAIT);
        context.eval(source2);
        instrumentationThread.join();
        int numSources = load ? 3 : 2;
        Assert.assertEquals(numSources, instrumentationThread.sources.size());
        Assert.assertEquals(source1.getURI(), instrumentationThread.sources.get(0).getURI());
        Assert.assertEquals(source2.getURI(), instrumentationThread.sources.get(1).getURI());
        if (load) {
            Assert.assertEquals("materialized", instrumentationThread.sources.get(2).getName());
        }
    }

    @TruffleInstrument.Registration(id = TestDeadlockInstrument.ID, services = TestDeadlockInstrument.class)
    public static class TestDeadlockInstrument extends TruffleInstrument {

        static final String ID = "testNoDeadlock";
        Env instrumentEnv;

        @Override
        protected void onCreate(Env env) {
            env.registerService(this);
            this.instrumentEnv = env;
        }

    }

    private class InstrumentationThread extends Thread {

        private final Engine engine;
        private final TruffleInstrument.Env env;
        private final boolean load;
        private final List<Source> sources = new ArrayList<>();
        private CountDownLatch executionLatch;

        InstrumentationThread(Engine engine, TruffleInstrument.Env env, boolean load) {
            super("Instrumentation Thread");
            this.engine = engine;
            this.env = env;
            this.load = load;
        }

        void setExecutionLatch(CountDownLatch latch) {
            this.executionLatch = latch;
        }

        @Override
        public void run() {
            try (Context c = Context.newBuilder().engine(engine).build()) {
                c.eval(LockingLanguage.ID, CODE_LATCH);
            }
            // Wait for the execution of source2 to assure we run in parallel:
            try {
                executionLatch.await();
            } catch (InterruptedException ex) {
            }
            if (load) {
                env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
                    @Override
                    public void onLoad(LoadSourceEvent event) {
                        sources.add(event.getSource());
                    }
                }, true);
                env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, e -> {
                }, true);
            } else {
                env.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
                    @Override
                    public void onExecute(ExecuteSourceEvent event) {
                        sources.add(event.getSource());
                    }
                }, true);
                env.getInstrumenter().attachExecutionEventFactory(SourceSectionFilter.ANY, context -> null);
            }
        }

    }

    @TruffleLanguage.Registration(id = LockingLanguage.ID, name = "Locking Test Language", version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    @ProvidedTags(StandardTags.StatementTag.class)
    public static class LockingLanguage extends ProxyLanguage {

        static final String ID = "truffle-locking-test-language";

        private final Object lock = new Object();
        private final CountDownLatch executionLatch = new CountDownLatch(1);
        private final CountDownLatch materializationLatch = new CountDownLatch(1);
        private final List<CallTarget> targets = new LinkedList<>(); // To prevent from GC

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final Source code = request.getSource();
            final CharSequence codeCharacters = code.getCharacters();
            if (CODE_LATCH.equals(codeCharacters)) {
                ((InstrumentationThread) Thread.currentThread()).setExecutionLatch(executionLatch);
                return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return Boolean.TRUE;
                    }
                });
            }
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerDirectives.transferToInterpreter();
                    RootNode codeExecRoot = new RootNode(LockingLanguage.this) {

                        @Node.Child private StatementNode statement = new StatementNode(LockingLanguage.this, code);

                        @Override
                        public Object execute(VirtualFrame f) {
                            return statement.execute(f, codeCharacters);
                        }

                        @Override
                        public SourceSection getSourceSection() {
                            return code.createSection(1);
                        }

                    };
                    CallTarget codeExec;
                    synchronized (lock) {
                        if (CODE_WAIT.equals(code.getCharacters())) {
                            executionLatch.countDown();
                            // Execution of source2 started, wait for instrumentation to kick in:
                            try {
                                materializationLatch.await();
                            } catch (InterruptedException ex) {
                            }
                        }
                        codeExec = Truffle.getRuntime().createCallTarget(codeExecRoot);
                    }
                    targets.add(codeExec);
                    return codeExec.call();
                }

            });
        }

        @GenerateWrapper
        public static class StatementNode extends Node implements InstrumentableNode {

            private final LockingLanguage language;
            private final Source source;
            private CallTarget statementCall;

            StatementNode(LockingLanguage language, Source source) {
                this.language = language;
                this.source = source;
            }

            StatementNode(StatementNode copy) {
                this.language = copy.language;
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
                return new StatementNodeWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag.equals(StandardTags.StatementTag.class);
            }

            @Override
            public SourceSection getSourceSection() {
                if (source != null) {
                    return source.createSection(1);
                } else {
                    return Source.newBuilder(ID, "", "materialized").build().createUnavailableSection();
                }
            }

            @Override
            public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
                if (statementCall == null && source != null) {
                    language.materializationLatch.countDown();
                    final CharSequence sourceCharacters = source.getCharacters();
                    RootNode codeExecRoot = new RootNode(language) {

                        @Node.Child private StatementNode statement = new StatementNode(language, null);

                        @Override
                        public Object execute(VirtualFrame frame) {
                            return statement.execute(frame, sourceCharacters);
                        }

                        @Override
                        public SourceSection getSourceSection() {
                            return source.createSection(1);
                        }

                    };
                    synchronized (language.lock) {
                        this.statementCall = Truffle.getRuntime().createCallTarget(codeExecRoot);
                    }
                }
                return this;
            }

        }

    }
}
