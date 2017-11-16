/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.LanguageInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContextsEventsTest {

    @Test
    public void testSingleContext() {
        final List<ContextEvent> events;
        try (Context context = Context.create()) {
            Instrument testContexsInstrument = context.getEngine().getInstruments().get("testContexsInstrument");
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            events = test.events;

            context.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
            assertEquals(2, events.size());
            assertTrue(events.get(1).languageInitialized);
            assertEquals(InstrumentationTestLanguage.ID, events.get(0).language.getId());
            assertNotNull(events.get(0).context);
            assertTrue(events.get(1).languageInitialized);
        }
        assertEquals(5, events.size());
        assertTrue(events.get(2).languageFinalized);
        assertEquals(InstrumentationTestLanguage.ID, events.get(2).language.getId());
        assertEquals(events.get(0).context, events.get(2).context);
        assertFalse(events.get(3).created);
        assertEquals(InstrumentationTestLanguage.ID, events.get(3).language.getId());
        assertEquals(events.get(0).context, events.get(3).context);
        assertFalse(events.get(4).created);
        assertNull(events.get(4).language);
        assertEquals(events.get(0).context, events.get(4).context);
    }

    @Test
    public void testSingleEngineContext() {
        Engine engine = Engine.create();
        Instrument testContexsInstrument = engine.getInstruments().get("testContexsInstrument");
        TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
        final List<ContextEvent> events = test.events;

        try (Context context = Context.newBuilder().engine(engine).build()) {
            assertEquals(1, events.size());
            assertTrue(events.get(0).created);
            assertNotNull(events.get(0).context);
            assertNull(events.get(0).language);

            context.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
            assertEquals(3, events.size());
            assertTrue(events.get(1).created);
            assertNotNull(events.get(1).language);
            assertEquals(events.get(0).context, events.get(1).context);
            assertTrue(events.get(2).languageInitialized);
            assertEquals(InstrumentationTestLanguage.ID, events.get(2).language.getId());
            assertEquals(events.get(0).context, events.get(2).context);
        }
        assertEquals(6, events.size());
        assertTrue(events.get(3).languageFinalized);
        assertFalse(events.get(4).created);
        assertNotNull(events.get(4).language);
        assertFalse(events.get(5).created);
        assertNull(events.get(5).language);
    }

    @Test
    public void testInnerContext() {
        final List<ContextEvent> events;
        try (Context context = Context.create()) {
            Instrument testContexsInstrument = context.getEngine().getInstruments().get("testContexsInstrument");
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            events = test.events;

            context.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(STATEMENT(), CONTEXT(STATEMENT()))"));
            assertTrue(events.get(0).created);
            assertTrue(events.get(1).languageInitialized);
            assertTrue(events.get(2).created);
            assertNotEquals(events.get(0).context, events.get(2).context);
            assertEquals(events.get(0).context, events.get(2).context.getParent());
            assertNull(events.get(2).language);
            assertTrue(events.get(3).created);
            assertEquals(events.get(2).context, events.get(3).context);
            assertNotNull(events.get(3).language);
            assertTrue(events.get(4).languageInitialized);
            assertTrue(events.get(5).languageFinalized);
            assertFalse(events.get(6).created);
            assertEquals(InstrumentationTestLanguage.ID, events.get(6).language.getId());
            assertFalse(events.get(7).created);
            assertNull(events.get(7).language);
        }
        assertEquals(11, events.size());
        assertTrue(events.get(8).languageFinalized);
        assertEquals(events.get(0).context, events.get(8).context);
        assertFalse(events.get(9).created);
        assertEquals(InstrumentationTestLanguage.ID, events.get(9).language.getId());
        assertFalse(events.get(10).created);
        assertNull(events.get(10).language);
        assertEquals(events.get(0).context, events.get(10).context);
    }

    @Test
    public void testMultipleContexts() {
        final int numContexts = 5;
        final List<ContextEvent> events;
        try (Engine engine = Engine.create()) {
            Instrument testContexsInstrument = engine.getInstruments().get("testContexsInstrument");
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            events = test.events;

            Source source = Source.create(InstrumentationTestLanguage.ID, "STATEMENT()");
            for (int i = 0; i < numContexts; i++) {
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    assertEquals(6 * i + 1, events.size());
                    context.eval(source);
                }
                assertEquals(6 * i + 6, events.size());
            }
            assertEquals(6 * numContexts, events.size());
            TruffleContext lastContext = null;
            for (int i = 0; i < numContexts; i++) {
                int ci = 6 * i;
                assertTrue(events.get(ci).created);
                assertNull(events.get(ci).language);
                assertNotEquals(lastContext, events.get(ci).context);
                lastContext = events.get(ci).context;
                assertTrue(events.get(ci + 1).created);
                assertNotNull(events.get(ci + 1).language);
                assertEquals(lastContext, events.get(ci + 1).context);
                assertTrue(events.get(ci + 2).languageInitialized);
                assertTrue(events.get(ci + 3).languageFinalized);
                assertNotNull(events.get(ci + 4).language);
                assertNull(events.get(ci + 5).language);
            }
        }
        // No more events
        assertEquals(6 * numContexts, events.size());
    }

    @Test
    public void testGetActiveContexts() {
        try {
            TestContextsInstrument.includeActiveContexts = true;
            final List<ContextEvent> events;
            try (Context context = Context.create()) {
                Instrument testContexsInstrument = context.getEngine().getInstruments().get("testContexsInstrument");
                TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
                events = test.events;
                context.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
                assertTrue(Integer.toString(events.size()), events.size() > 1);
                // Have creation of polyglot context and a language
                assertEquals(3, events.size());
                assertTrue(events.get(0).created);
                assertNotNull(events.get(0).context);
                assertNull(events.get(0).language);
                assertTrue(events.get(1).created);
                assertEquals(InstrumentationTestLanguage.ID, events.get(1).language.getId());
                assertTrue(events.get(2).languageInitialized);
            }
            assertEquals(6, events.size());
            Engine engine = Engine.create();
            // Contexts created and closed:
            try (Context context = Context.newBuilder().engine(engine).build()) {
                context.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
                context.eval(Source.create(InstrumentationTestLanguage.ID, "CONTEXT(STATEMENT())"));
            }
            Instrument testBlockOnStatementsInstrument = engine.getInstruments().get("testBlockOnStatementsInstrument");
            ThreadsEventsTest.TestBlockOnStatementsInstrument testBlock = testBlockOnStatementsInstrument.lookup(ThreadsEventsTest.TestBlockOnStatementsInstrument.class);
            testBlock.runOnBlock = new Runnable() {
                @Override
                // We want to hide 'events' from the outer context not to use it by a mistake
                @SuppressWarnings("hiding")
                public void run() {
                    Instrument testContexsInstrument = engine.getInstruments().get("testContexsInstrument");
                    TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
                    final List<ContextEvent> events = test.events;
                    assertEquals(3, events.size());
                    assertTrue(events.get(0).created);
                    assertNull(events.get(0).language);
                    assertTrue(events.get(1).created);
                    assertEquals(InstrumentationTestLanguage.ID, events.get(1).language.getId());
                    assertTrue(events.get(2).languageInitialized);
                    testBlock.blockOnStatements.set(false);
                }
            };
            Context context = Context.newBuilder().engine(engine).build();
            context.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(CONTEXT(EXPRESSION()), CONTEXT(STATEMENT))"));
        } finally {
            TestContextsInstrument.includeActiveContexts = false;
        }
    }

    @Registration(id = "testContexsInstrument", services = TestContextsInstrument.class)
    public static class TestContextsInstrument extends TruffleInstrument implements ContextsListener {

        static boolean includeActiveContexts = false;
        final List<ContextEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachContextsListener(this, includeActiveContexts);
            env.registerService(this);
        }

        @Override
        public void onContextCreated(TruffleContext context) {
            events.add(new ContextEvent(true, context, null));
        }

        @Override
        public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(true, context, language));
        }

        @Override
        public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(false, context, language, true, false));
        }

        @Override
        public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(false, context, language, false, true));
        }

        @Override
        public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(false, context, language));
        }

        @Override
        public void onContextClosed(TruffleContext context) {
            events.add(new ContextEvent(false, context, null));
        }

    }

    private static class ContextEvent {

        final boolean created;
        final TruffleContext context;
        final LanguageInfo language;
        final boolean languageInitialized;
        final boolean languageFinalized;

        ContextEvent(boolean created, TruffleContext context, LanguageInfo language) {
            this(created, context, language, false, false);
        }

        ContextEvent(boolean created, TruffleContext context, LanguageInfo language, boolean languageInitialized, boolean languageFinalized) {
            this.created = created;
            this.context = context;
            this.language = language;
            this.languageInitialized = languageInitialized;
            this.languageFinalized = languageFinalized;
        }
    }
}
