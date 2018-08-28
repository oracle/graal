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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;

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
        run("STATEMENT");
        Assert.assertTrue(getErr().contains("TestLoadSourceSectionExceptionClass"));
    }

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

}
