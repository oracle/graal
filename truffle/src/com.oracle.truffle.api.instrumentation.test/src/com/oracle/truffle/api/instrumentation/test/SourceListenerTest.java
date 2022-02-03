/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecuteSourceEvent;
import com.oracle.truffle.api.instrumentation.ExecuteSourceListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class SourceListenerTest extends AbstractInstrumentationTest {

    private static final ExecutionEventListener EMPTY_EXECUTION_EVENT_LISTENER = new ExecutionEventListener() {
        @Override
        public void onEnter(EventContext c, VirtualFrame frame) {

        }

        @Override
        public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

        }

        @Override
        public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

        }
    };

    @BeforeClass
    public static void beforeClass() {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
    }

    public SourceListenerTest() {
        needsInstrumentEnv = true;
    }

    @Test
    public void testLoadSource1() throws IOException {
        testLoadExecuteSourceImpl(true, 1);
    }

    @Test
    public void testLoadSource2() throws IOException {
        testLoadExecuteSourceImpl(true, 2);
    }

    @Test
    public void testLoadSource3() throws IOException {
        testLoadExecuteSourceImpl(true, 5);
    }

    @Test
    public void testExecuteSource1() throws IOException {
        testLoadExecuteSourceImpl(false, 1);
    }

    @Test
    public void testExecuteSource2() throws IOException {
        testLoadExecuteSourceImpl(false, 2);
    }

    @Test
    public void testExecuteSource3() throws IOException {
        testLoadExecuteSourceImpl(false, 5);
    }

    private void testLoadExecuteSourceImpl(boolean load, int runTimes) throws IOException {
        Instrument instrument = context.getEngine().getInstruments().get("testLoadExecuteSource");
        TestLoadExecuteSource impl = instrument.lookup(TestLoadExecuteSource.class);
        assertTrue("Lookup of registered service enables the instrument", isCreated(instrument));
        impl.createBindings();
        Source source1 = lines("STATEMENT(EXPRESSION, EXPRESSION)");
        // running the same source multiple times should not have any effect on the test result.
        for (int i = 0; i < runTimes; i++) {
            run(source1);
        }

        impl.assertAllEvents();

        if (load) {
            impl.attachLoad();
        } else {
            impl.attachExecute();
        }

        Source source2 = lines("ROOT(DEFINE(f1, STATEMENT(EXPRESSION)), DEFINE(f2, STATEMENT)," +
                        "BLOCK(CALL(f1), CALL(f2)))");
        for (int i = 0; i < runTimes; i++) {
            run(source2);
        }

        impl.assertOnlyNewEvents(source2);
        impl.assertAllEvents(source1, source2);

        // Load an internal source
        Source source3 = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT", "test").internal(true).build();
        for (int i = 0; i < runTimes; i++) {
            run(source3);
        }

        impl.assertOnlyNewEvents(source2, source3);
        impl.assertAllEvents(source1, source2, source3);
        impl.assertAllNotInternalEvents(source1, source2);

        // Disable the instrument by closing the engine.
        teardown();
        setup();

        Source source4 = lines("STATEMENT(EXPRESSION, EXPRESSION, EXPRESSION)");
        for (int i = 0; i < runTimes; i++) {
            run(source4);
        }

        impl.assertOnlyNewEvents(source2, source3);
        impl.assertAllEvents(source1, source2, source3);

        instrument = engine.getInstruments().get("testLoadExecuteSource");
        impl = instrument.lookup(TestLoadExecuteSource.class);
        impl.createBindings();
        if (load) {
            impl.attachLoad();
        } else {
            impl.attachExecute();
        }

        assertEvents(impl.onlyNewEvents);
        impl.assertAllEvents(source4);
    }

    private static void assertEvents(List<com.oracle.truffle.api.source.Source> actualSources) {
        Assert.assertEquals(0, actualSources.size());
    }

    private static void assertEvents(List<com.oracle.truffle.api.source.Source> actualSources, Source... expectedSources) {
        Assert.assertEquals(expectedSources.length, actualSources.size());
        for (int i = 0; i < expectedSources.length; i++) {
            Assert.assertEquals("index " + i, sourceToImpl(expectedSources[i]), actualSources.get(i));
        }
    }

    private static void assertEvents(List<com.oracle.truffle.api.source.Source> actualSources, com.oracle.truffle.api.source.Source... expectedSources) {
        Assert.assertEquals(expectedSources.length, actualSources.size());
        for (int i = 0; i < expectedSources.length; i++) {
            Assert.assertEquals("index " + i, expectedSources[i], actualSources.get(i));
        }
    }

    @Registration(id = "testLoadExecuteSource", services = SourceListenerTest.TestLoadExecuteSource.class)
    public static class TestLoadExecuteSource extends TruffleInstrument {
        private Env env;
        private List<com.oracle.truffle.api.source.Source> onlyNewEvents = new ArrayList<>();
        private List<com.oracle.truffle.api.source.Source> allEvents = new ArrayList<>();
        private List<com.oracle.truffle.api.source.Source> allNotInternalEvents = new ArrayList<>();
        private final List<EventBinding<?>> createdBindings = new ArrayList<>();
        private List<com.oracle.truffle.api.source.Source> onlyNewEvents2 = new ArrayList<>();
        private List<com.oracle.truffle.api.source.Source> allEvents2 = new ArrayList<>();
        private List<com.oracle.truffle.api.source.Source> allNotInternalEvents2 = new ArrayList<>();

        void createBindings() {
            // 3 load bindings
            createdBindings.add(env.getInstrumenter().createLoadSourceBinding(SourceFilter.ANY, new LoadSourceListener() {
                @Override
                public void onLoad(LoadSourceEvent event) {
                    onlyNewEvents2.add(event.getSource());
                }
            }, false));

            createdBindings.add(env.getInstrumenter().createLoadSourceBinding(SourceFilter.ANY, new LoadSourceListener() {
                @Override
                public void onLoad(LoadSourceEvent event) {
                    allEvents2.add(event.getSource());
                }
            }, true));

            createdBindings.add(env.getInstrumenter().createLoadSourceBinding(SourceFilter.newBuilder().includeInternal(false).build(), new LoadSourceListener() {
                @Override
                public void onLoad(LoadSourceEvent event) {
                    allNotInternalEvents2.add(event.getSource());
                }
            }, true));

            // 3 execute bindings
            createdBindings.add(env.getInstrumenter().createExecuteSourceBinding(SourceFilter.ANY, new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    onlyNewEvents2.add(event.getSource());
                }
            }, false));

            createdBindings.add(env.getInstrumenter().createExecuteSourceBinding(SourceFilter.ANY, new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    allEvents2.add(event.getSource());
                }
            }, true));

            createdBindings.add(env.getInstrumenter().createExecuteSourceBinding(SourceFilter.newBuilder().includeInternal(false).build(), new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    allNotInternalEvents2.add(event.getSource());
                }
            }, true));
        }

        void attachLoad() {
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
                @Override
                public void onLoad(LoadSourceEvent event) {
                    onlyNewEvents.add(event.getSource());
                }
            }, false);

            env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
                @Override
                public void onLoad(LoadSourceEvent event) {
                    allEvents.add(event.getSource());
                }
            }, true);

            env.getInstrumenter().attachLoadSourceListener(SourceFilter.newBuilder().includeInternal(false).build(), new LoadSourceListener() {
                @Override
                public void onLoad(LoadSourceEvent event) {
                    allNotInternalEvents.add(event.getSource());
                }
            }, true);

            // Attach the first 3 load bindings
            for (int i = 0; i < 3; i++) {
                createdBindings.get(i).attach();
            }
        }

        void attachExecute() {
            env.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    onlyNewEvents.add(event.getSource());
                }
            }, false);

            env.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    allEvents.add(event.getSource());
                }
            }, true);

            env.getInstrumenter().attachExecuteSourceListener(SourceFilter.newBuilder().includeInternal(false).build(), new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    allNotInternalEvents.add(event.getSource());
                }
            }, true);

            // Attach the last 3 execute bindings
            for (int i = 3; i < 6; i++) {
                createdBindings.get(i).attach();
            }
        }

        void assertOnlyNewEvents(Source... expectedSources) {
            assertEvents(onlyNewEvents, expectedSources);
            assertEvents(onlyNewEvents2, expectedSources);
        }

        void assertAllEvents() {
            assertEvents(allEvents);
            assertEvents(allEvents2);
        }

        void assertAllEvents(Source... expectedSources) {
            assertEvents(allEvents, expectedSources);
            assertEvents(allEvents2, expectedSources);
        }

        void assertAllEvents(com.oracle.truffle.api.source.Source... expectedSources) {
            assertEvents(allEvents, expectedSources);
            assertEvents(allEvents2, expectedSources);
        }

        void assertAllNotInternalEvents(Source... expectedSources) {
            assertEvents(allNotInternalEvents, expectedSources);
            assertEvents(allNotInternalEvents2, expectedSources);
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

    }

    @Test
    public void testLoadSourceException() throws IOException {
        assureEnabled(engine.getInstruments().get("testLoadSourceException"));
        try {
            run("");
            Assert.fail("No exception was thrown.");
        } catch (PolyglotException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("TestLoadSourceExceptionClass"));
        }
    }

    private static class TestLoadSourceExceptionClass extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }

    @Registration(id = "testLoadSourceException", services = Object.class)
    public static class TestLoadSourceException extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
                public void onLoad(LoadSourceEvent source) {
                    throw new TestLoadSourceExceptionClass();
                }
            }, true);
        }

        @Override
        protected void onDispose(Env env) {
        }
    }

    @Test
    public void testAllowOnlySourceQueries() throws IOException {
        Instrument instrument = engine.getInstruments().get("testAllowOnlySourceQueries");
        assureEnabled(instrument);
        Source source = lines("");
        run(source);

        TestAllowOnlySourceQueries impl = instrument.lookup(TestAllowOnlySourceQueries.class);
        Assert.assertTrue(impl.success);
    }

    @Registration(id = "testAllowOnlySourceQueries", services = {Object.class, TestAllowOnlySourceQueries.class})
    @SuppressWarnings("deprecation")
    public static class TestAllowOnlySourceQueries extends TruffleInstrument {

        boolean success;

        @Override
        protected void onCreate(Env env) {
            LoadSourceListener dummySourceListener = new LoadSourceListener() {
                public void onLoad(LoadSourceEvent source) {
                }
            };
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.newBuilder().sourceIs(linesImpl("")).build(), dummySourceListener, true);
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.newBuilder().sourceIs(linesImpl("")).build(), dummySourceListener, true);
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.newBuilder().sourceIs((s) -> true).build(), dummySourceListener, true);
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.newBuilder().languageIs(InstrumentationTestLanguage.ID).build(), dummySourceListener, true);
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.newBuilder().includeInternal(false).build(), dummySourceListener, true);

            try {
                env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().indexIn(IndexRange.between(0, 1)).build(), dummySourceListener, true);
                throw new AssertionError();
            } catch (IllegalArgumentException e) {
            }
            try {
                env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.between(1, 2)).build(), dummySourceListener, true);
            } catch (IllegalArgumentException e) {
            }
            SourceSection unavailable = com.oracle.truffle.api.source.Source.newBuilder("", "", "a").build().createUnavailableSection();
            try {
                env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().sourceSectionEquals(unavailable).build(), dummySourceListener, true);
            } catch (IllegalArgumentException e) {
            }
            try {
                env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().rootSourceSectionEquals(unavailable).build(), dummySourceListener,
                                true);
            } catch (IllegalArgumentException e) {
            }
            try {
                env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().lineIs(1).build(), dummySourceListener, true);
            } catch (IllegalArgumentException e) {
            }

            success = true;

            env.registerService(this);
        }

        @Override
        protected void onDispose(Env env) {
        }
    }

    @Test
    public void testLoadSourceNoRootSection() throws Exception {
        context.initialize(InstrumentationTestLanguage.ID);
        Instrument instrument = engine.getInstruments().get("testLoadExecuteSource");
        TestLoadExecuteSource impl = instrument.lookup(TestLoadExecuteSource.class);
        impl.createBindings();
        impl.attachLoad();
        testNoRootSectionImpl(impl);
    }

    @Test
    public void testExecuteSourceNoRootSection() throws Exception {
        context.initialize(InstrumentationTestLanguage.ID);
        Instrument instrument = engine.getInstruments().get("testLoadExecuteSource");
        TestLoadExecuteSource impl = instrument.lookup(TestLoadExecuteSource.class);
        impl.createBindings();
        impl.attachExecute();
        testNoRootSectionImpl(impl);
    }

    private static void testNoRootSectionImpl(TestLoadExecuteSource impl) throws Exception {
        com.oracle.truffle.api.source.Source source1 = com.oracle.truffle.api.source.Source.newBuilder("", "line1\nline2", null).name("NoName1").build();
        com.oracle.truffle.api.source.Source source2 = com.oracle.truffle.api.source.Source.newBuilder("", "line3\nline4", null).name("NoName2").build();
        com.oracle.truffle.api.source.Source source3 = com.oracle.truffle.api.source.Source.newBuilder("", "line5\nline6", null).name("NoName3").build();
        Node node1 = new SourceSectionFilterTest.SourceSectionNode(source1.createSection(1));
        RootNode rootA = SourceSectionFilterTest.createRootNode(null, Boolean.FALSE, node1);
        impl.assertAllEvents();
        rootA.getCallTarget().call();
        impl.assertAllEvents(source1);

        Node node2 = new SourceSectionFilterTest.SourceSectionNode(source2.createSection(2));
        Node node3 = new SourceSectionFilterTest.SourceSectionNode(source3.createSection(2));
        RootNode rootB = SourceSectionFilterTest.createRootNode(null, Boolean.FALSE, node2, node3);
        impl.assertAllEvents(source1);
        rootB.getCallTarget().call();
        impl.assertAllEvents(source1, source2, source3);
    }

    @Test
    public void testExecutionAndSourceListeners() throws Exception {
        context.initialize(InstrumentationTestLanguage.ID);
        com.oracle.truffle.api.source.Source source1 = com.oracle.truffle.api.source.Source.newBuilder("", "line1\nline2", null).name("Name1").build();
        com.oracle.truffle.api.source.Source source2 = com.oracle.truffle.api.source.Source.newBuilder("", "line3\nline4", null).name("Name2").build();
        Node node1 = new SourceSectionFilterTest.SourceSectionNode(source1.createSection(1));
        Node node2 = new SourceSectionFilterTest.SourceSectionNode(source2.createSection(1));
        RootNode root1 = SourceSectionFilterTest.createRootNode(source1.createSection(1, 1, 2, 5), Boolean.FALSE, node1);
        RootNode root2 = SourceSectionFilterTest.createRootNode(source2.createSection(1, 1, 2, 5), Boolean.FALSE, node2);
        root1.getCallTarget().call();
        root2.getCallTarget();
        // source1 was loaded and executed, source 2 was loaded only

        Set<String> executedSources = new TreeSet<>();
        Set<String> loadedSources = new TreeSet<>();
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
            @Override
            public void onExecute(ExecuteSourceEvent event) {
                executedSources.add(event.getSource().getName());
            }
        }, true).dispose();
        Assert.assertEquals("[Name1]", executedSources.toString());
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
            @Override
            public void onLoad(LoadSourceEvent event) {
                loadedSources.add(event.getSource().getName());
            }
        }, true).dispose();
        Assert.assertEquals("[Name1, Name2]", loadedSources.toString());
        loadedSources.clear();
        instrumentEnv.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.ANY, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                loadedSources.add(event.getSourceSection().getSource().getName());
            }
        });
        Assert.assertEquals("[Name1, Name2]", loadedSources.toString());
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
            @Override
            public void onExecute(ExecuteSourceEvent event) {
                executedSources.add(event.getSource().getName());
            }
        }, true).dispose();
        Assert.assertEquals("[Name1]", executedSources.toString());
    }

    @Test
    public void testLoadBindingDisposal() throws Exception {
        testBindingDisposalImpl(true);
    }

    @Test
    public void testExecuteBindingDisposal() throws Exception {
        testBindingDisposalImpl(false);
    }

    private void testBindingDisposalImpl(boolean load) throws Exception {
        context.initialize(InstrumentationTestLanguage.ID);
        Instrument instrument = engine.getInstruments().get("testBindingDisposal");
        TestBindingDisposal impl = instrument.lookup(TestBindingDisposal.class);
        impl.doAttach(load);
        Source source1 = lines("STATEMENT");
        run(source1);
        assertEvents(impl.onlyNewEvents, source1);
        assertEvents(impl.allEvents, source1);

        impl.onlyNewBinding.dispose();
        impl.allBinding.dispose();
        // No new events received after bindings are disposed
        com.oracle.truffle.api.source.Source source2a = com.oracle.truffle.api.source.Source.newBuilder("", "line2a", null).name("NoName2a").build();
        com.oracle.truffle.api.source.Source source2b = com.oracle.truffle.api.source.Source.newBuilder("", "line2b", null).name("NoName2b").build();
        Node node2a = new SourceSectionFilterTest.SourceSectionNode(source2a.createSection(1));
        Node node2b = new SourceSectionFilterTest.SourceSectionNode(source2b.createSection(1));
        RootNode root2 = SourceSectionFilterTest.createRootNode(null, Boolean.FALSE, node2a, node2b);
        root2.getCallTarget().call();
        assertEvents(impl.onlyNewEvents, source1);
        assertEvents(impl.allEvents, source1);

        // Clear and reattach
        impl.onlyNewEvents.clear();
        impl.allEvents.clear();
        impl.doAttach(load);
        Source source3 = lines("VARIABLE(a, 10)");
        run(source3);
        assertEvents(impl.onlyNewEvents, source3);
        assertEvents(impl.allEvents, getSourceImpl(source1), source2a, source2b, getSourceImpl(source3));
    }

    @Registration(id = "testBindingDisposal", services = SourceListenerTest.TestBindingDisposal.class)
    public static class TestBindingDisposal extends TruffleInstrument {

        private Env env;
        List<com.oracle.truffle.api.source.Source> onlyNewEvents = new ArrayList<>();
        EventBinding<?> onlyNewBinding;
        List<com.oracle.truffle.api.source.Source> allEvents = new ArrayList<>();
        EventBinding<?> allBinding;

        void doAttach(boolean load) {
            if (load) {
                onlyNewBinding = env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
                    @Override
                    public void onLoad(LoadSourceEvent event) {
                        onlyNewEvents.add(event.getSource());
                    }
                }, false);

                allBinding = env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
                    @Override
                    public void onLoad(LoadSourceEvent event) {
                        allEvents.add(event.getSource());
                    }
                }, true);
            } else {
                onlyNewBinding = env.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
                    @Override
                    public void onExecute(ExecuteSourceEvent event) {
                        onlyNewEvents.add(event.getSource());
                    }
                }, false);

                allBinding = env.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
                    @Override
                    public void onExecute(ExecuteSourceEvent event) {
                        allEvents.add(event.getSource());
                    }
                }, true);
            }
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

    }

    @Test
    public void testLoadBindingEarlyDisposal() throws Exception {
        testBindingEarlyDisposalImpl(true);
    }

    @Test
    public void testExecuteBindingEarlyDisposal() throws Exception {
        testBindingEarlyDisposalImpl(false);
    }

    private void testBindingEarlyDisposalImpl(boolean load) throws Exception {
        context.initialize(InstrumentationTestLanguage.ID);
        Instrument instrument = engine.getInstruments().get("testBindingEarlyDisposal");
        TestBindingEarlyDisposal impl = instrument.lookup(TestBindingEarlyDisposal.class);
        impl.createBindings(load, source -> {
            assertEquals("STATEMENT\n", source.getCharacters());
            impl.allBinding.dispose();
        });
        Source source1 = lines("STATEMENT");
        Source source2 = lines("EXPRESSION");
        Source source3 = lines("EXPRESSION(EXPRESSION)");
        run(source1);
        run(source2);
        run(source3);
        assertFalse(impl.allBinding.isAttached());
        assertFalse(impl.allBinding.isDisposed());
        impl.doAttach();
        assertTrue(impl.allBinding.isDisposed());
        assertFalse(impl.allBinding.isAttached());
    }

    @Registration(id = "testBindingEarlyDisposal", services = SourceListenerTest.TestBindingEarlyDisposal.class)
    public static class TestBindingEarlyDisposal extends TruffleInstrument {

        private Env env;
        EventBinding<?> allBinding;

        void createBindings(boolean load, Consumer<com.oracle.truffle.api.source.Source> sourceConsumer) {
            if (load) {
                allBinding = env.getInstrumenter().createLoadSourceBinding(SourceFilter.ANY, new LoadSourceListener() {
                    @Override
                    public void onLoad(LoadSourceEvent event) {
                        sourceConsumer.accept(event.getSource());
                    }
                }, true);
            } else {
                allBinding = env.getInstrumenter().createExecuteSourceBinding(SourceFilter.ANY, new ExecuteSourceListener() {
                    @Override
                    public void onExecute(ExecuteSourceEvent event) {
                        sourceConsumer.accept(event.getSource());
                    }
                }, true);
            }
        }

        void doAttach() {
            allBinding.attach();
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

    }

    @Test
    public void testMultiThreadedLoadSource() throws InterruptedException, ExecutionException {
        testMultiThreadedSourceBindings(true);
    }

    @Test
    public void testMultiThreadedExecuteSource() throws InterruptedException, ExecutionException {
        testMultiThreadedSourceBindings(false);
    }

    private void testMultiThreadedSourceBindings(boolean load) throws InterruptedException, ExecutionException {
        int numInstrumentationThreads = 10;
        int numExecutionThreads = 20;
        int numRepeats = 500;
        ExecutorService threadPool = Executors.newFixedThreadPool(numInstrumentationThreads + numExecutionThreads);
        try {
            for (int i = 0; i < numRepeats; i++) {
                testMultiThreadedSourceBindings(load, numInstrumentationThreads, numExecutionThreads, threadPool);
            }
        } finally {
            threadPool.shutdown();
            assertTrue(threadPool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void testMultiThreadedSourceBindings(boolean load, int numInstrumentationThreads, int numExecutionThreads, ExecutorService threadPool) throws InterruptedException, ExecutionException {
        Engine testEngine = Engine.create();
        Instrument instrument = testEngine.getInstruments().get(TestSourceListenerInstrument.ID);
        TestSourceListenerInstrument testInstrument = instrument.lookup(TestSourceListenerInstrument.class);
        InstrumentationRunnable[] instrumentationRunnables = new InstrumentationRunnable[numInstrumentationThreads];
        for (int i = 0; i < numInstrumentationThreads; i++) {
            instrumentationRunnables[i] = new InstrumentationRunnable(testInstrument.instrumentEnv, load);
        }

        Runnable[] executionRunnables = new Runnable[numExecutionThreads];
        int sourceNumDigits = Integer.toString(numExecutionThreads - 1).length();
        for (int i = 0; i < numExecutionThreads; i++) {
            final int fi = i;
            executionRunnables[i] = new Runnable() {
                @Override
                public void run() {
                    Context threadContext = Context.newBuilder().engine(testEngine).build();
                    String code = "ROOT(DEFINE(f1, STATEMENT(EXPRESSION)), DEFINE(f2, STATEMENT)," +
                                    "BLOCK(CALL(f1), CALL(f2)))";
                    Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, sourceName(fi, sourceNumDigits)).buildLiteral();
                    threadContext.eval(source);
                }
            };
        }

        int numExec1 = numExecutionThreads / 4;
        for (int i = 0; i < numExec1; i++) {
            threadPool.submit(executionRunnables[i]).get();
        }
        int numInstr1 = numInstrumentationThreads / 4;
        for (int i = 0; i < numInstr1; i++) {
            threadPool.submit(instrumentationRunnables[i]).get();
        }
        List<Future<?>> futures = new ArrayList<>();
        for (int i = numInstr1; i < numInstrumentationThreads; i++) {
            futures.add(threadPool.submit(instrumentationRunnables[i]));
        }
        for (int i = numExec1; i < numExecutionThreads; i++) {
            futures.add(threadPool.submit(executionRunnables[i]));
        }
        for (Future<?> f : futures) {
            f.get();
        }

        List<String> previousNamesUnsorted = null;
        for (int i = 0; i < numInstrumentationThreads; i++) {
            List<com.oracle.truffle.api.source.Source> sourceList = instrumentationRunnables[i].sources;
            Assert.assertEquals("Instrument " + i + " : " + sourceList.toString(), numExecutionThreads, sourceList.size());
            Set<String> namesSorted = new TreeSet<>();
            List<String> namesUnsorted = new ArrayList<>();
            for (int t = 0; t < numExecutionThreads; t++) {
                namesSorted.add(sourceList.get(t).getName());
                namesUnsorted.add(sourceList.get(t).getName());
            }
            if (previousNamesUnsorted != null) {
                assertEquals(previousNamesUnsorted, namesUnsorted);
            }
            int t = 0;
            for (String name : namesSorted) {
                Assert.assertEquals(namesSorted.toString(), sourceName(t++, sourceNumDigits), name);
            }
            previousNamesUnsorted = namesUnsorted;
        }
    }

    private static String sourceName(int n, int digits) {
        String ns = Integer.toString(n);
        while (ns.length() < digits) {
            ns = "0" + ns;
        }
        return "source " + ns;
    }

    @TruffleInstrument.Registration(id = TestSourceListenerInstrument.ID, services = TestSourceListenerInstrument.class)
    public static class TestSourceListenerInstrument extends TruffleInstrument {

        static final String ID = "testSourceListenerInstrument";
        Env instrumentEnv;

        @Override
        protected void onCreate(Env env) {
            env.registerService(this);
            this.instrumentEnv = env;
        }

    }

    private class InstrumentationRunnable implements Runnable {

        private final TruffleInstrument.Env env;
        private final boolean load;
        private final List<com.oracle.truffle.api.source.Source> sources = Collections.synchronizedList(new ArrayList<>());

        InstrumentationRunnable(TruffleInstrument.Env env, boolean load) {
            this.env = env;
            this.load = load;
        }

        @Override
        public void run() {
            if (load) {
                env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
                    @Override
                    public void onLoad(LoadSourceEvent event) {
                        sources.add(event.getSource());
                    }
                }, true);
            } else {
                env.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, new ExecuteSourceListener() {
                    @Override
                    public void onExecute(ExecuteSourceEvent event) {
                        sources.add(event.getSource());
                    }
                }, true);
            }
        }
    }

    @Test
    public void testNoMaterializationOnSourceListener() {
        setupEnv(Context.create(), new ProxyLanguage() {

            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                com.oracle.truffle.api.source.Source source = request.getSource();
                return new RootNode(languageInstance) {
                    @Node.Child private NeverMaterializedNode child = new NeverMaterializedNode();

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
        });
        context.eval(Source.create(ProxyLanguage.ID, "a"));
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> {
        }, true);
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> {
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, "b"));
        // Verify that it fails when materialized:
        try {
            instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, e -> {
            }, true);
            Assert.fail();
        } catch (IllegalStateException ex) {
            // O.K.
        }
    }

    @GenerateWrapper
    static class NeverMaterializedNode extends Node implements InstrumentableNode {

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new NeverMaterializedNodeWrapper(this, probe);
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            throw new IllegalStateException("Should not be materialized.");
        }

        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            return true;
        }
    }

    @Test
    public void testDifferentSourcesInAST() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "abcd";
        StringBuilder loadedCode = new StringBuilder();
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testPreLoadedSourcesReported() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code1 = "abcd";
        String code2 = "efgh";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code1));
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code2));
        Assert.assertEquals(code1 + code1 + code2 + code2, loadedCode.toString());
    }

    @Test
    public void testPreExecutedSourcesReported() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code1 = "abcd";
        String code2 = "efgh";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code1));
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code2));
        Assert.assertEquals(code1 + code1 + code2 + code2, loadedCode.toString());
    }

    @Test
    public void testPreLoadedSourcesNotReported() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code1 = "abcd";
        String code2 = "efgh";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code1));
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), false);
        context.eval(Source.create(ProxyLanguage.ID, code2));
        Assert.assertEquals(code2 + code2, loadedCode.toString());
    }

    @Test
    public void testPreExecutedSourcesNotReported() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code1 = "abcd";
        String code2 = "efgh";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code1));
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), false);
        context.eval(Source.create(ProxyLanguage.ID, code2));
        Assert.assertEquals(code2 + code2, loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesInAST() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Mabcd";
        StringBuilder loadedCode = new StringBuilder();
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        // Not materialized yet:
        Assert.assertEquals(code + "M", loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, e -> {
        }, true);
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesInAST2() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Mabcd";
        StringBuilder loadedCode = new StringBuilder();
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        // Not materialized yet:
        Assert.assertEquals(code + "M", loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.ANY, e -> {
        });
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesInAST3() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Mabcd";
        StringBuilder loadedCode = new StringBuilder();
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        // Not materialized yet:
        Assert.assertEquals(code + "M", loadedCode.toString());
        // Force materialization:
        attachAnySourceSectionExecutionEventListener();
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    private void attachAnySourceSectionExecutionEventListener() {
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, EMPTY_EXECUTION_EVENT_LISTENER);
    }

    @Test
    public void testMaterializedSourcesInAST4() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        // Expression tag does not trigger materialization
        Set<com.oracle.truffle.api.source.Source> sources = new HashSet<>();
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), e -> {
            com.oracle.truffle.api.source.Source s = e.getSourceSection().getSource();
            if (sources.add(s)) {
                loadedCode.append(s.getCharacters());
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, e -> {
        }, true);
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesInAST5() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        // Expression tag does not trigger materialization
        Set<com.oracle.truffle.api.source.Source> sources = new HashSet<>();
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), e -> {
            com.oracle.truffle.api.source.Source s = e.getSourceSection().getSource();
            if (sources.add(s)) {
                loadedCode.append(s.getCharacters());
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.ANY, e -> {
        });
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesInAST6() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        // Expression tag does not trigger materialization
        Set<com.oracle.truffle.api.source.Source> sources = new HashSet<>();
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), e -> {
            com.oracle.truffle.api.source.Source s = e.getSourceSection().getSource();
            if (sources.add(s)) {
                loadedCode.append(s.getCharacters());
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        attachAnySourceSectionExecutionEventListener();
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesExecutedInAST() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code));
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, e -> {
        }, true);
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesExecutedInAST2() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code));
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.ANY, e -> {
        });
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesExecutedInAST3() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code));
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        attachAnySourceSectionExecutionEventListener();
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesExecutedInAST4() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        Source source = Source.create(ProxyLanguage.ID, code);
        // Expression tag does not trigger materialization
        attachExpressionTagExecutionEventListener(loadedCode);
        context.eval(source);
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.ANY, e -> {
        });
        context.eval(source);
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    private void attachExpressionTagExecutionEventListener(StringBuilder loadedCode) {
        Set<com.oracle.truffle.api.source.Source> sources = new HashSet<>();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext c, VirtualFrame frame) {
                com.oracle.truffle.api.source.Source s = c.getInstrumentedSourceSection().getSource();
                if (sources.add(s)) {
                    loadedCode.append(s.getCharacters());
                }
            }

            @Override
            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

            }

            @Override
            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

            }
        });
    }

    @Test
    public void testMaterializedSourcesExecutedInAST5() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        Source source = Source.create(ProxyLanguage.ID, code);
        // Expression tag does not trigger materialization
        attachExpressionTagExecutionEventListener(loadedCode);
        context.eval(source);
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, e -> {
        }, true);
        context.eval(source);
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesExecutedInAST6() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        Source source = Source.create(ProxyLanguage.ID, code);
        // Expression tag does not trigger materialization
        attachExpressionTagExecutionEventListener(loadedCode);
        context.eval(source);
        // Not materialized yet:
        Assert.assertEquals(code, loadedCode.toString());
        // Force materialization:
        attachAnySourceSectionExecutionEventListener();
        context.eval(source);
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesExecutedInAST7() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "MRabcd";
        StringBuilder loadedCode = new StringBuilder();
        Source source = Source.create(ProxyLanguage.ID, code);
        // Expression tag does not trigger materialization
        Set<com.oracle.truffle.api.source.Source> sources = new HashSet<>();
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), e -> {
            com.oracle.truffle.api.source.Source s = e.getSourceSection().getSource();
            if (sources.add(s)) {
                loadedCode.append(s.getCharacters());
            }
        }, true);
        // Force materialization:
        attachAnySourceSectionExecutionEventListener();
        context.eval(source);
        Assert.assertEquals(code + code.substring(2), loadedCode.toString());
    }

    @Test
    public void testMaterializedSourcesExecutedInAST8() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Mabcd";
        StringBuilder loadedCode = new StringBuilder();
        Source source = Source.create(ProxyLanguage.ID, code);
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        // Force materialization:
        attachAnySourceSectionExecutionEventListener();
        context.eval(source);
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInsertedSourcesInAST() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Iabcd";
        StringBuilder loadedCode = new StringBuilder();
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInsertedSourcesInAST2() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Iabcd";
        StringBuilder loadedCode = new StringBuilder();
        Set<com.oracle.truffle.api.source.Source> sources = new HashSet<>();
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), e -> {
            com.oracle.truffle.api.source.Source s = e.getSourceSection().getSource();
            if (sources.add(s)) {
                loadedCode.append(s.getCharacters());
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code, loadedCode.toString());
    }

    @Test
    public void testInsertedSourcesExecutedInAST() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Iabcd";
        StringBuilder loadedCode = new StringBuilder();
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInsertedSourcesExecutedInAST2() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "Iabcd";
        StringBuilder loadedCode = new StringBuilder();
        Set<com.oracle.truffle.api.source.Source> sources = new HashSet<>();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext c, VirtualFrame frame) {
                com.oracle.truffle.api.source.Source s = c.getInstrumentedSourceSection().getSource();
                if (sources.add(s)) {
                    loadedCode.append(s.getCharacters());
                }
            }

            @Override
            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

            }

            @Override
            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

            }
        });
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code, loadedCode.toString());
    }

    @Test
    public void testInstallSourceLoadedListenerFromSourceSectionNotification() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "abcd";
        StringBuilder loadedCode = new StringBuilder();
        boolean[] sourceListenerInstalled = new boolean[1];
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, loadSourceSectionEvent -> {
            if (!sourceListenerInstalled[0]) {
                instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
                sourceListenerInstalled[0] = true;
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInstallSourceExecutedListenerFromSourceSectionNotification() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "abcd";
        StringBuilder loadedCode = new StringBuilder();
        boolean[] sourceListenerInstalled = new boolean[1];
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, loadSourceSectionEvent -> {
            if (!sourceListenerInstalled[0]) {
                instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
                sourceListenerInstalled[0] = true;
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInstallSourceLoadedListenerFromSourceLoadedListener() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "abcd";
        StringBuilder loadedCode = new StringBuilder();
        boolean[] sourceListenerInstalled = new boolean[1];
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, loadSourceEvent -> {
            if (!sourceListenerInstalled[0]) {
                instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
                sourceListenerInstalled[0] = true;
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInstallSourceLoadedListenerFromSourceExecutedListener() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "abcd";
        StringBuilder loadedCode = new StringBuilder();
        boolean[] sourceListenerInstalled = new boolean[1];
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, loadSourceEvent -> {
            if (!sourceListenerInstalled[0]) {
                instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
                sourceListenerInstalled[0] = true;
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInstallSourceExecutedListenerFromSourceExecutedListener() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "abcd";
        StringBuilder loadedCode = new StringBuilder();
        boolean[] sourceListenerInstalled = new boolean[1];
        instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, loadSourceEvent -> {
            if (!sourceListenerInstalled[0]) {
                instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
                sourceListenerInstalled[0] = true;
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    @Test
    public void testInstallSourceExecutedListenerFromSourceLoadListener() {
        setupEnv(Context.create(), new MultiSourceASTLanguage());
        String code = "abcd";
        StringBuilder loadedCode = new StringBuilder();
        boolean[] sourceListenerInstalled = new boolean[1];
        instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, loadSourceEvent -> {
            if (!sourceListenerInstalled[0]) {
                instrumentEnv.getInstrumenter().attachExecuteSourceListener(SourceFilter.ANY, s -> loadedCode.append(s.getSource().getCharacters()), true);
                sourceListenerInstalled[0] = true;
            }
        }, true);
        context.eval(Source.create(ProxyLanguage.ID, code));
        Assert.assertEquals(code + code, loadedCode.toString());
    }

    static class MultiSourceASTLanguage extends ProxyLanguage {

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
            return new RootNode(languageInstance) {
                @Node.Child private MultiSourceBlock block = new MultiSourceBlock(source, source.getCharacters().toString());

                @Override
                public Object execute(VirtualFrame frame) {
                    return block.execute(frame);
                }

                @Override
                public SourceSection getSourceSection() {
                    return source.createSection(1);
                }
            }.getCallTarget();
        }

        @GenerateWrapper
        static class MultiSourceBlock extends Node implements InstrumentableNode {

            @Child private MultiSourceBlock child;
            private final boolean materialize;
            private final boolean materializeUnderSameSourceRoot;
            private final boolean insert;
            private final com.oracle.truffle.api.source.Source rootSource;
            private final com.oracle.truffle.api.source.Source mineSource;
            private final String childrenCode;

            MultiSourceBlock(com.oracle.truffle.api.source.Source rootSource, String code) {
                this.rootSource = rootSource;
                this.materialize = code.startsWith("M");
                this.materializeUnderSameSourceRoot = code.startsWith("MR");
                this.insert = code.startsWith("I");
                this.mineSource = materializeUnderSameSourceRoot ? rootSource : com.oracle.truffle.api.source.Source.newBuilder(ProxyLanguage.ID, code.substring(0, 1), "block").build();
                this.childrenCode = code.substring(materializeUnderSameSourceRoot ? 2 : 1);
                if (!(materialize || insert) && !childrenCode.isEmpty()) {
                    child = new MultiSourceBlock(rootSource, childrenCode);
                }
            }

            MultiSourceBlock(com.oracle.truffle.api.source.Source rootSource, com.oracle.truffle.api.source.Source source, String childrenCode) {
                this.rootSource = rootSource;
                this.materialize = false;
                this.materializeUnderSameSourceRoot = false;
                this.insert = false;
                this.mineSource = source;
                this.childrenCode = childrenCode;
                if (!childrenCode.isEmpty()) {
                    child = new MultiSourceBlock(rootSource, childrenCode);
                }
            }

            MultiSourceBlock(MultiSourceBlock copy) {
                this.rootSource = copy.rootSource;
                this.materialize = copy.materialize;
                this.materializeUnderSameSourceRoot = copy.materializeUnderSameSourceRoot;
                this.insert = copy.insert;
                this.mineSource = copy.mineSource;
                this.childrenCode = copy.childrenCode;
            }

            @Override
            public SourceSection getSourceSection() {
                return mineSource.createSection(1);
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
                return new MultiSourceBlockWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.ExpressionTag.class == tag || StandardTags.StatementTag.class == tag || StandardTags.RootBodyTag.class == tag || StandardTags.RootTag.class == tag;
            }

            @Override
            public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
                if (materialize && materializedTags.contains(StandardTags.StatementTag.class)) {
                    return new MultiSourceBlock(rootSource, mineSource, childrenCode);
                }
                return this;
            }

            public Object execute(VirtualFrame frame) {
                if (insert) {
                    CompilerDirectives.transferToInterpreter();
                    child = insert(new MultiSourceBlock(rootSource, childrenCode));
                    notifyInserted(child);
                }
                if (child != null) {
                    return child.execute(frame);
                } else {
                    return true;
                }
            }
        }
    }
}
