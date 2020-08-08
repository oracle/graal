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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class SourceSectionListenerTest extends AbstractInstrumentationTest {

    @Test
    public void testLoadSourceSection1() throws IOException {
        testLoadSourceSectionImpl(1);
    }

    @Test
    public void testLoadSourceSection2() throws IOException {
        testLoadSourceSectionImpl(2);
    }

    @Test
    public void testLoadSourceSection3() throws IOException {
        testLoadSourceSectionImpl(5);
    }

    private void testLoadSourceSectionImpl(int runTimes) throws IOException {
        Instrument instrument = engine.getInstruments().get("testLoadSourceSection1");
        SourceSection[] sourceSections1 = sections("STATEMENT(EXPRESSION, EXPRESSION)", "STATEMENT(EXPRESSION, EXPRESSION)", "EXPRESSION");

        final SourceSectionFilter statementFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build();
        final SourceSectionFilter exprFilter = SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build();

        Source source1 = sourceSections1[0].getSource();
        for (int i = 0; i < runTimes; i++) {
            run(source1);
        }

        assureEnabled(instrument);
        TestLoadSourceSection1 impl = instrument.lookup(TestLoadSourceSection1.class);

        assertSections(impl.query(SourceSectionFilter.ANY), sourceSections1);
        assertSections(impl.query(statementFilter), sourceSections1[0]);
        assertSections(impl.query(exprFilter), sourceSections1[2], sourceSections1[3]);

        SourceSection[] sourceSections2 = sections("STATEMENT(EXPRESSION)", "STATEMENT(EXPRESSION)", "EXPRESSION");
        Source source2 = sourceSections2[0].getSource();
        for (int i = 0; i < runTimes; i++) {
            run(source2);
        }

        assertEvents(impl.allEvents, merge(sourceSections1, sourceSections2));
        assertEvents(impl.onlyNewEvents, sourceSections2);
        assertEvents(impl.onlyStatements, sourceSections1[0], sourceSections2[0]);
        assertEvents(impl.onlyExpressions, sourceSections1[2], sourceSections1[3], sourceSections2[2]);

        assertSections(impl.query(SourceSectionFilter.ANY), merge(sourceSections1, sourceSections2));
        assertSections(impl.query(statementFilter), sourceSections1[0], sourceSections2[0]);
        assertSections(impl.query(exprFilter), sourceSections1[2], sourceSections1[3], sourceSections2[2]);

        teardown();
        setup();

        SourceSection[] sourceSections3 = sections("STATEMENT(EXPRESSION, EXPRESSION, EXPRESSION)", "STATEMENT(EXPRESSION, EXPRESSION, EXPRESSION)", "EXPRESSION");
        Source source3 = sourceSections3[0].getSource();
        for (int i = 0; i < runTimes; i++) {
            run(source3);
        }

        assertEvents(impl.allEvents, merge(sourceSections1, sourceSections2));
        assertEvents(impl.onlyNewEvents, sourceSections2);
        assertEvents(impl.onlyStatements, sourceSections1[0], sourceSections2[0]);
        assertEvents(impl.onlyExpressions, sourceSections1[2], sourceSections1[3], sourceSections2[2]);

        assertSections(impl.query(SourceSectionFilter.ANY), merge(sourceSections1, sourceSections2));
        assertSections(impl.query(statementFilter), sourceSections1[0], sourceSections2[0]);
        assertEvents(impl.onlyExpressions, sourceSections1[2], sourceSections1[3], sourceSections2[2]);

        instrument = engine.getInstruments().get("testLoadSourceSection1");
        assureEnabled(instrument);
        // new instrument needs update
        impl = instrument.lookup(TestLoadSourceSection1.class);

        assertEvents(impl.onlyNewEvents);
        assertEvents(impl.allEvents, sourceSections3);
        assertEvents(impl.onlyStatements, sourceSections3[0]);
        assertEvents(impl.onlyExpressions, sourceSections3[2], sourceSections3[3], sourceSections3[4]);
    }

    private SourceSection[] sections(String code, String... match) {
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, "sourceSectionTest").buildLiteral();

        List<SourceSection> sections = new ArrayList<>();
        sections.add(createSection(source, 0, code.length()));
        for (String matchExpression : match) {
            int index = -1;
            while ((index = code.indexOf(matchExpression, index + 1)) != -1) {
                sections.add(createSection(source, index, matchExpression.length()));
            }
        }
        return sections.toArray(new SourceSection[0]);
    }

    private static SourceSection[] merge(SourceSection[]... arrays) {
        int totalLength = 0;
        for (SourceSection[] array : arrays) {
            totalLength += array.length;
        }
        SourceSection[] newArray = new SourceSection[totalLength];

        int index = 0;
        for (SourceSection[] array : arrays) {
            System.arraycopy(array, 0, newArray, index, array.length);
            index += array.length;
        }
        return newArray;
    }

    private static void assertEvents(List<LoadSourceSectionEvent> actualEvents, SourceSection... expectedSourceSections) {
        Assert.assertEquals(expectedSourceSections.length, actualEvents.size());
        for (int i = 0; i < expectedSourceSections.length; i++) {
            LoadSourceSectionEvent actualEvent = actualEvents.get(i);
            SourceSection expectedSourceSection = expectedSourceSections[i];
            Assert.assertEquals("index " + i, expectedSourceSection, actualEvent.getSourceSection());
            Assert.assertSame("index " + i, actualEvent.getNode().getSourceSection(), actualEvent.getSourceSection());
        }
    }

    private static void assertSections(List<com.oracle.truffle.api.source.SourceSection> actualSections, SourceSection... expectedSections) {
        Assert.assertEquals(expectedSections.length, actualSections.size());
        for (int i = 0; i < expectedSections.length; i++) {
            Assert.assertEquals("index " + i, expectedSections[i], actualSections.get(i));
        }
    }

    @Registration(id = "testLoadSourceSection1", services = {TestLoadSourceSection1.class, Object.class})
    public static class TestLoadSourceSection1 extends TruffleInstrument {
        List<LoadSourceSectionEvent> onlyNewEvents = new ArrayList<>();
        List<LoadSourceSectionEvent> allEvents = new ArrayList<>();
        List<LoadSourceSectionEvent> onlyStatements = new ArrayList<>();
        List<LoadSourceSectionEvent> onlyExpressions = new ArrayList<>();
        Instrumenter instrumenter = null;

        @Override
        protected void onCreate(Env env) {
            instrumenter = env.getInstrumenter();
            instrumenter.attachLoadSourceSectionListener(SourceSectionFilter.ANY, new SourceSectionListener(allEvents), true);
            instrumenter.attachLoadSourceSectionListener(SourceSectionFilter.ANY,
                            new SourceSectionListener(onlyNewEvents), false);
            instrumenter.attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                            new SourceSectionListener(onlyStatements), true);
            instrumenter.attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(),
                            new SourceSectionListener(onlyExpressions), true);

            env.registerService(this);
        }

        private class SourceSectionListener implements LoadSourceSectionListener {

            private final List<LoadSourceSectionEvent> events;

            SourceSectionListener(List<LoadSourceSectionEvent> events) {
                this.events = events;
            }

            public void onLoad(LoadSourceSectionEvent event) {
                events.add(event);
            }
        }

        private List<com.oracle.truffle.api.source.SourceSection> query(SourceSectionFilter filter) {
            return instrumenter.querySourceSections(filter);
        }

    }

    @Test
    public void testVisitLoadedSourceSections() throws IOException {
        SourceSection[] sourceSections = sections("STATEMENT(EXPRESSION)\n", "STATEMENT(EXPRESSION)", "EXPRESSION");
        run(sourceSections[0].getSource());
        TestVisitLoadedSourceSections test = engine.getInstruments().get("testVisitLoadedSourceSections").lookup(TestVisitLoadedSourceSections.class);
        assertEvents(test.visitedEvents, sourceSections);
        // No more sections visited
        run("EXPRESSION(EXPRESSION,EXPRESSION)");
        Assert.assertEquals(sourceSections.length, test.visitedEvents.size());
    }

    @Registration(id = "testVisitLoadedSourceSections", services = {TestVisitLoadedSourceSections.class, Object.class})
    public static class TestVisitLoadedSourceSections extends TruffleInstrument {

        List<LoadSourceSectionEvent> visitedEvents = new ArrayList<>();

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().visitLoadedSourceSections(SourceSectionFilter.ANY, new LoadSourceSectionListener() {
                @Override
                public void onLoad(LoadSourceSectionEvent event) {
                    visitedEvents.add(event);
                }
            });
            env.registerService(this);
        }
    }

    @Test
    public void testLoadSourceSectionException() throws IOException {
        assureEnabled(engine.getInstruments().get("testLoadSourceSectionException"));
        try {
            run("STATEMENT");
            Assert.fail("No exception was thrown.");
        } catch (PolyglotException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("TestLoadSourceSectionExceptionClass"));
        }
    }

    @Test
    public void testNoDeadlock() throws InterruptedException {
        setupEnv(Context.create(), new SingleSourceSectionLanguage());
        String code1 = "one";
        String code2 = "two";
        StringBuilder sourceSectionNotifications = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code1));
        context.eval(Source.create(ProxyLanguage.ID, code2));
        CountDownLatch mainThreadLatch = new CountDownLatch(1);
        CountDownLatch secondThreadLatch = new CountDownLatch(1);
        Thread secondInstrumentationThread = new Thread(() -> instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, sse -> {
            if (code2.contentEquals(sse.getSourceSection().getCharacters())) {
                mainThreadLatch.countDown();
                try {
                    secondThreadLatch.await();
                } catch (InterruptedException ie) {
                }
                instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, EMPTY_EXECUTION_EVENT_LISTENER);
            }
        }, true));
        secondInstrumentationThread.start();
        mainThreadLatch.await();
        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, sse -> {
            if (code1.contentEquals(sse.getSourceSection().getCharacters())) {
                secondThreadLatch.countDown();
                instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, EMPTY_EXECUTION_EVENT_LISTENER);
            }
            sourceSectionNotifications.append(sse.getSourceSection().getCharacters());
        }, true);
        secondInstrumentationThread.join();
        Assert.assertEquals(code1 + code2, sourceSectionNotifications.toString());
    }

    @Test
    public void testNoDeadlock2() throws InterruptedException {
        setupEnv(Context.create(), new SingleSourceSectionLanguage());
        String code = "one";
        StringBuilder loadedCode = new StringBuilder();
        context.eval(Source.create(ProxyLanguage.ID, code));
        CountDownLatch mainThreadLatch = new CountDownLatch(1);
        CountDownLatch secondThreadLatch = new CountDownLatch(1);
        Thread secondInstrumentationThread = new Thread(() -> instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, sse -> {
            mainThreadLatch.countDown();
            try {
                secondThreadLatch.await();
            } catch (InterruptedException ie) {
            }
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, EMPTY_EXECUTION_EVENT_LISTENER);
        }, true));
        secondInstrumentationThread.start();
        mainThreadLatch.await();
        CountDownLatch mainThreadLatch2 = new CountDownLatch(1);
        Thread thirdInstrumentationThread = new Thread(() -> {
            mainThreadLatch2.countDown();
            instrumentEnv.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, se -> {
                secondThreadLatch.countDown();
                loadedCode.append(se.getSource().getCharacters());
            }, true);
        });
        thirdInstrumentationThread.start();
        mainThreadLatch2.await();
        /*
         * Wait for the third thread to stop on trying to acquire the AST lock. This should be
         * fairly reliable because mainThreadLatch2 guarantees the the third thread is already
         * running.
         */
        Thread.sleep(1000);
        secondThreadLatch.countDown();
        secondInstrumentationThread.join();
        thirdInstrumentationThread.join();
        Assert.assertEquals(code, loadedCode.toString());
    }

    static final ExecutionEventListener EMPTY_EXECUTION_EVENT_LISTENER = new ExecutionEventListener() {
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

    private static class TestLoadSourceSectionExceptionClass extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }

    @Registration(id = "testLoadSourceSectionException", services = Object.class)
    public static class TestLoadSourceSectionException extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, new LoadSourceSectionListener() {
                public void onLoad(LoadSourceSectionEvent event) {
                    throw new TestLoadSourceSectionExceptionClass();
                }
            }, true);
        }

    }

    static class SingleSourceSectionLanguage extends ProxyLanguage {
        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
            return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                @Node.Child private SingleSourceSectionNode onlyChild = new SingleSourceSectionNode(source);

                @Override
                public Object execute(VirtualFrame frame) {
                    return onlyChild.execute(frame);
                }

                @Override
                public com.oracle.truffle.api.source.SourceSection getSourceSection() {
                    return source.createSection(1);
                }
            });
        }

        @GenerateWrapper
        static class SingleSourceSectionNode extends Node implements InstrumentableNode {
            @Node.Child private SingleSourceSectionNode noChild = null;
            com.oracle.truffle.api.source.Source source;

            SingleSourceSectionNode(com.oracle.truffle.api.source.Source source) {
                this.source = source;
            }

            SingleSourceSectionNode(SingleSourceSectionNode copy) {
                this.source = copy.source;
                this.noChild = copy.noChild;
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.StatementTag.class.equals(tag);
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new SingleSourceSectionNodeWrapper(this, this, probe);
            }

            @Override
            public com.oracle.truffle.api.source.SourceSection getSourceSection() {
                return source.createSection(1);
            }

            public Object execute(VirtualFrame frame) {
                if (noChild != null) {
                    return noChild.execute(frame);
                }
                return true;
            }
        }
    }

}
