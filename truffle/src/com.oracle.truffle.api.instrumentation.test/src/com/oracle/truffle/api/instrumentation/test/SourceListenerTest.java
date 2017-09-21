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

import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;

public class SourceListenerTest extends AbstractInstrumentationTest {

    @Test
    public void testLoadSource1() throws IOException {
        testLoadSourceImpl(1);
    }

    @Test
    public void testLoadSource2() throws IOException {
        testLoadSourceImpl(2);
    }

    @Test
    public void testLoadSource3() throws IOException {
        testLoadSourceImpl(5);
    }

    private void testLoadSourceImpl(int runTimes) throws IOException {
        int initialQueryCount = InstrumentationTestLanguage.getRootSourceSectionQueryCount();

        Instrument instrument = context.getEngine().getInstruments().get("testLoadSource1");
        Source source1 = lines("STATEMENT(EXPRESSION, EXPRESSION)");
        // running the same source multiple times should not have any effect on the test result.
        for (int i = 0; i < runTimes; i++) {
            run(source1);
        }

        Assert.assertEquals("unexpected getSourceSection calls without source listeners", initialQueryCount, InstrumentationTestLanguage.getRootSourceSectionQueryCount());

        TestLoadSource1 impl = instrument.lookup(TestLoadSource1.class);
        assertTrue("Lookup of registered service enables the instrument", isCreated(instrument));

        Source source2 = lines("ROOT(DEFINE(f1, STATEMENT(EXPRESSION)), DEFINE(f2, STATEMENT)," +
                        "BLOCK(CALL(f1), CALL(f2)))");
        for (int i = 0; i < runTimes; i++) {
            run(source2);
        }

        Assert.assertNotEquals("expecting getSourceSection calls because of source listeners", initialQueryCount, InstrumentationTestLanguage.getRootSourceSectionQueryCount());

        assertEvents(impl.onlyNewEvents, source2);
        assertEvents(impl.allEvents, source1, source2);

        // Disable the instrument by closing the engine.
        engine.close();
        engine = null;
        engine = getEngine();

        Source source3 = lines("STATEMENT(EXPRESSION, EXPRESSION, EXPRESSION)");
        for (int i = 0; i < runTimes; i++) {
            run(source3);
        }

        assertEvents(impl.onlyNewEvents, source2);
        assertEvents(impl.allEvents, source1, source2);

        instrument = engine.getInstruments().get("testLoadSource1");
        impl = instrument.lookup(TestLoadSource1.class);

        assertEvents(impl.onlyNewEvents);
        assertEvents(impl.allEvents, source3);
    }

    private void assertEvents(List<com.oracle.truffle.api.source.Source> actualSources, Source... expectedSources) {
        Assert.assertEquals(expectedSources.length, actualSources.size());
        for (int i = 0; i < expectedSources.length; i++) {
            Assert.assertSame("index " + i, getSourceImpl(expectedSources[i]), actualSources.get(i));
        }
    }

    @Registration(id = "testLoadSource1", services = SourceListenerTest.TestLoadSource1.class)
    public static class TestLoadSource1 extends TruffleInstrument {
        List<com.oracle.truffle.api.source.Source> onlyNewEvents = new ArrayList<>();
        List<com.oracle.truffle.api.source.Source> allEvents = new ArrayList<>();

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.ANY, new LoadSourceListener() {
                public void onLoad(LoadSourceEvent event) {
                    onlyNewEvents.add(event.getSource());
                }
            }, false);

            env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.ANY, new LoadSourceListener() {
                public void onLoad(LoadSourceEvent event) {
                    allEvents.add(event.getSource());
                }
            }, true);
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
            env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.ANY, new LoadSourceListener() {
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
    public static class TestAllowOnlySourceQueries extends TruffleInstrument {

        boolean success;

        @Override
        protected void onCreate(Env env) {
            LoadSourceListener dummySourceListener = new LoadSourceListener() {
                public void onLoad(LoadSourceEvent source) {
                }
            };
            env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().sourceIs(linesImpl("")).build(), dummySourceListener, true);
            env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().sourceIs(linesImpl("")).build(), dummySourceListener, true);
            env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.newBuilder().mimeTypeIs(InstrumentationTestLanguage.MIME_TYPE).build(), dummySourceListener, true);

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

}
