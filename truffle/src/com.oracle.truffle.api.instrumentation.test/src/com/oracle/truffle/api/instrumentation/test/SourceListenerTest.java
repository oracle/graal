/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecuteSourceEvent;
import com.oracle.truffle.api.instrumentation.ExecuteSourceListener;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;

public class SourceListenerTest extends AbstractInstrumentationTest {

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
        int initialQueryCount = InstrumentationTestLanguage.getRootSourceSectionQueryCount();

        Instrument instrument = context.getEngine().getInstruments().get("testLoadExecuteSource");
        Source source1 = lines("STATEMENT(EXPRESSION, EXPRESSION)");
        // running the same source multiple times should not have any effect on the test result.
        for (int i = 0; i < runTimes; i++) {
            run(source1);
        }

        Assert.assertEquals("unexpected getSourceSection calls without source listeners", initialQueryCount, InstrumentationTestLanguage.getRootSourceSectionQueryCount());

        TestLoadExecuteSource impl = instrument.lookup(TestLoadExecuteSource.class);
        assertTrue("Lookup of registered service enables the instrument", isCreated(instrument));
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

        Assert.assertNotEquals("expecting getSourceSection calls because of source listeners", initialQueryCount, InstrumentationTestLanguage.getRootSourceSectionQueryCount());

        assertEvents(impl.onlyNewEvents, source2);
        assertEvents(impl.allEvents, source1, source2);

        // Load an internal source
        Source source3 = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT", "test").internal(true).build();
        for (int i = 0; i < runTimes; i++) {
            run(source3);
        }

        assertEvents(impl.onlyNewEvents, source2, source3);
        assertEvents(impl.allEvents, source1, source2, source3);
        assertEvents(impl.allNotInternalEvents, source1, source2);

        // Disable the instrument by closing the engine.
        teardown();
        setup();

        Source source4 = lines("STATEMENT(EXPRESSION, EXPRESSION, EXPRESSION)");
        for (int i = 0; i < runTimes; i++) {
            run(source4);
        }

        assertEvents(impl.onlyNewEvents, source2, source3);
        assertEvents(impl.allEvents, source1, source2, source3);

        instrument = engine.getInstruments().get("testLoadExecuteSource");
        impl = instrument.lookup(TestLoadExecuteSource.class);
        if (load) {
            impl.attachLoad();
        } else {
            impl.attachExecute();
        }

        assertEvents(impl.onlyNewEvents);
        assertEvents(impl.allEvents, source4);
    }

    private static void assertEvents(List<com.oracle.truffle.api.source.Source> actualSources) {
        Assert.assertEquals(0, actualSources.size());
    }

    private void assertEvents(List<com.oracle.truffle.api.source.Source> actualSources, Source... expectedSources) {
        Assert.assertEquals(expectedSources.length, actualSources.size());
        for (int i = 0; i < expectedSources.length; i++) {
            Assert.assertEquals("index " + i, getSourceImpl(expectedSources[i]), actualSources.get(i));
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
        List<com.oracle.truffle.api.source.Source> onlyNewEvents = new ArrayList<>();
        List<com.oracle.truffle.api.source.Source> allEvents = new ArrayList<>();
        List<com.oracle.truffle.api.source.Source> allNotInternalEvents = new ArrayList<>();

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
        run("");
        Assert.assertTrue(getErr().contains("TestLoadSourceExceptionClass"));
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
            SourceSection unavailable = com.oracle.truffle.api.source.Source.newBuilder("").name("a").mimeType("").build().createUnavailableSection();
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
        impl.attachLoad();
        testNoRootSectionImpl(impl);
    }

    @Test
    public void testExecuteSourceNoRootSection() throws Exception {
        context.initialize(InstrumentationTestLanguage.ID);
        Instrument instrument = engine.getInstruments().get("testLoadExecuteSource");
        TestLoadExecuteSource impl = instrument.lookup(TestLoadExecuteSource.class);
        impl.attachExecute();
        testNoRootSectionImpl(impl);
    }

    private static void testNoRootSectionImpl(TestLoadExecuteSource impl) throws Exception {
        com.oracle.truffle.api.source.Source source1 = com.oracle.truffle.api.source.Source.newBuilder("", "line1\nline2", null).name("NoName1").build();
        com.oracle.truffle.api.source.Source source2 = com.oracle.truffle.api.source.Source.newBuilder("", "line3\nline4", null).name("NoName2").build();
        com.oracle.truffle.api.source.Source source3 = com.oracle.truffle.api.source.Source.newBuilder("", "line5\nline6", null).name("NoName3").build();
        Node node1 = new SourceSectionFilterTest.SourceSectionNode(source1.createSection(1));
        RootNode rootA = SourceSectionFilterTest.createRootNode(null, Boolean.FALSE, node1);
        assertEvents(impl.allEvents);
        Truffle.getRuntime().createCallTarget(rootA).call();
        assertEvents(impl.allEvents, source1);

        Node node2 = new SourceSectionFilterTest.SourceSectionNode(source2.createSection(2));
        Node node3 = new SourceSectionFilterTest.SourceSectionNode(source3.createSection(2));
        RootNode rootB = SourceSectionFilterTest.createRootNode(null, Boolean.FALSE, node2, node3);
        assertEvents(impl.allEvents, source1);
        Truffle.getRuntime().createCallTarget(rootB).call();
        assertEvents(impl.allEvents, source1, source2, source3);
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
        Truffle.getRuntime().createCallTarget(root2).call();
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

}
