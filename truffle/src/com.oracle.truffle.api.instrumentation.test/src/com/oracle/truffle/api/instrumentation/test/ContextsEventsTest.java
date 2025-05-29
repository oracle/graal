/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class ContextsEventsTest extends AbstractPolyglotTest {

    private static final String CANCELLING_LANGUAGE_CONTEXT_CREATION = "Cancelling language context creation";
    private static final String CANCELLING_LANGUAGE_CONTEXT_INITIALIZATION = "Cancelling language context initialization";

    public ContextsEventsTest() {
        enterContext = false;
        needsInstrumentEnv = true;
    }

    @Test
    public void testSingleContext() {
        final List<ContextEvent> events;
        try (Context ctx = Context.create()) {
            Instrument testContexsInstrument = ctx.getEngine().getInstruments().get("testContexsInstrument");
            /*
             * The context listener is attached as a part of the lookup method, so the
             * onContextCreated event is not fired, because the context is already created and
             * TestContextsInstrument#includeActiveContexts is false.
             */
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            events = test.events;

            ctx.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
            assertEquals(4, events.size());
            assertEquals(InstrumentationTestLanguage.ID, events.get(1).language.getId());
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 0);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, 1);
            assertNotNull(events.get(1).context);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, 2);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZED, 3);
        }
        assertEquals(7, events.size());
        assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.FINALIZED, 4);
        assertEquals(InstrumentationTestLanguage.ID, events.get(4).language.getId());
        assertEquals(events.get(1).context, events.get(4).context);
        assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.DISPOSED, 5);
        assertEquals(InstrumentationTestLanguage.ID, events.get(5).language.getId());
        assertEquals(events.get(1).context, events.get(5).context);
        assertContextEventType(events, ContextEvent.ContextEventType.CLOSED, 6);
        assertNull(events.get(6).language);
        assertEquals(events.get(1).context, events.get(6).context);
    }

    private static void assertContextEventType(List<ContextEvent> events, ContextEvent.ContextEventType contextEventType, int i) {
        assertEquals(contextEventType, events.get(i).contextEventType);
    }

    private static void assertLanguageContextEventType(List<ContextEvent> events, ContextEvent.LanguageContextEventType languageContextEventType, int i) {
        assertEquals(languageContextEventType, events.get(i).languageContextEventType);
    }

    @Test
    public void testSingleContextFailInitialize() {
        List<ContextEvent> events = null;
        try (Context ctx = Context.create()) {
            Instrument testContexsInstrument = ctx.getEngine().getInstruments().get("testContexsInstrument");
            /*
             * The context listener is attached as a part of the lookup method, so the
             * onContextCreated event is not fired, because the context is already created and
             * TestContextsInstrument#includeActiveContexts is false.
             */
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            events = test.events;
            setupEnv(ctx, new LanguageContextFailureLanguage(false, true));
            fail();
        } catch (PolyglotException pe) {
            assertEquals(CancellationException.class.getName() + ": " + CANCELLING_LANGUAGE_CONTEXT_INITIALIZATION, pe.getMessage());
        } finally {
            assertNotNull(events);
            assertEquals(6, events.size());
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 0);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, 1);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, 2);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZEFAILED, 3);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.DISPOSED, 4);
            assertNotNull(events.get(4).language);
            assertContextEventType(events, ContextEvent.ContextEventType.CLOSED, 5);
            assertNull(events.get(5).language);
        }
    }

    @Test
    public void testSingleContextFailCreate() {
        List<ContextEvent> events = null;
        try (Context ctx = Context.create()) {
            Instrument testContexsInstrument = ctx.getEngine().getInstruments().get("testContexsInstrument");
            /*
             * The context listener is attached as a part of the lookup method, so the
             * onContextCreated event is not fired, because the context is already created and
             * TestContextsInstrument#includeActiveContexts is false.
             */
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            events = test.events;
            setupEnv(ctx, new LanguageContextFailureLanguage(true, false));
            fail();
        } catch (PolyglotException pe) {
            assertEquals(CancellationException.class.getName() + ": " + CANCELLING_LANGUAGE_CONTEXT_CREATION, pe.getMessage());
        } finally {
            assertNotNull(events);
            assertEquals(3, events.size());
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 0);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATEFAILED, 1);
            assertContextEventType(events, ContextEvent.ContextEventType.CLOSED, 2);
        }
    }

    @Test
    public void testSingleEngineContext() {
        Engine engine = Engine.create();
        Instrument testContexsInstrument = engine.getInstruments().get("testContexsInstrument");
        TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
        final List<ContextEvent> events = test.events;

        try (Context ctx = Context.newBuilder().engine(engine).build()) {
            assertEquals(1, events.size());
            assertContextEventType(events, ContextEvent.ContextEventType.CREATED, 0);
            assertNotNull(events.get(0).context);
            assertNull(events.get(0).language);

            ctx.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
            assertEquals(5, events.size());
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 1);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, 2);
            assertNotNull(events.get(2).language);
            assertEquals(events.get(0).context, events.get(2).context);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, 3);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZED, 4);
            assertEquals(InstrumentationTestLanguage.ID, events.get(4).language.getId());
            assertEquals(events.get(0).context, events.get(4).context);
        }
        assertEquals(8, events.size());
        assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.FINALIZED, 5);
        assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.DISPOSED, 6);
        assertNotNull(events.get(6).language);
        assertContextEventType(events, ContextEvent.ContextEventType.CLOSED, 7);
        assertNull(events.get(7).language);
    }

    @Test
    public void testInnerContext() {
        final List<ContextEvent> events;
        try (Context ctx = Context.create()) {
            Instrument testContexsInstrument = ctx.getEngine().getInstruments().get("testContexsInstrument");
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            events = test.events;

            ctx.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(STATEMENT(), CONTEXT(STATEMENT()))"));
            assertTrue(events.get(0).entered);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 0);
            assertTrue(events.get(1).entered);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, 1);
            assertTrue(events.get(2).entered);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, 2);
            assertTrue(events.get(3).entered);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZED, 3);

            assertContextEventType(events, ContextEvent.ContextEventType.CREATED, 4);
            assertFalse(events.get(4).entered);
            assertNotEquals(events.get(1).context, events.get(4).context);
            assertEquals(events.get(1).context, events.get(4).context.getParent());
            assertNull(events.get(4).language);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 5);
            assertTrue(events.get(5).entered);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, 6);
            assertTrue(events.get(6).entered);
            assertEquals(events.get(4).context, events.get(6).context);
            assertNotNull(events.get(6).language);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, 7);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZED, 8);
            assertTrue(events.get(8).entered);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.FINALIZED, 9);
            assertTrue(events.get(9).entered);
            assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.DISPOSED, 10);
            assertFalse(events.get(10).entered);
            assertEquals(InstrumentationTestLanguage.ID, events.get(10).language.getId());
            assertContextEventType(events, ContextEvent.ContextEventType.CLOSED, 11);
            assertFalse(events.get(11).entered);
            assertNull(events.get(11).language);
        }
        assertEquals(15, events.size());
        assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.FINALIZED, 12);
        assertEquals(events.get(1).context, events.get(12).context);
        assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.DISPOSED, 13);
        assertEquals(InstrumentationTestLanguage.ID, events.get(13).language.getId());
        assertContextEventType(events, ContextEvent.ContextEventType.CLOSED, 14);
        assertNull(events.get(14).language);
        assertEquals(events.get(1).context, events.get(14).context);
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
                try (Context ctx = Context.newBuilder().engine(engine).build()) {
                    assertEquals(8 * i + 1, events.size());
                    ctx.eval(source);
                }
                assertEquals(8 * i + 8, events.size());
            }
            assertEquals(8 * numContexts, events.size());
            TruffleContext lastContext = null;
            for (int i = 0; i < numContexts; i++) {
                int ci = 8 * i;
                assertContextEventType(events, ContextEvent.ContextEventType.CREATED, ci);
                assertNull(events.get(ci).language);
                assertNotEquals(lastContext, events.get(ci).context);
                lastContext = events.get(ci).context;
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, ci + 1);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, ci + 2);
                assertNotNull(events.get(ci + 2).language);
                assertEquals(lastContext, events.get(ci + 2).context);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, ci + 3);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZED, ci + 4);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.FINALIZED, ci + 5);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.DISPOSED, ci + 6);
                assertNotNull(events.get(ci + 6).language);
                assertContextEventType(events, ContextEvent.ContextEventType.CLOSED, ci + 7);
                assertNull(events.get(ci + 7).language);
            }
        }
        // No more events
        assertEquals(8 * numContexts, events.size());
    }

    @Test
    public void testGetActiveContexts() {
        try {
            TestContextsInstrument.includeActiveContexts = true;
            final List<ContextEvent> events;
            try (Context ctx = Context.create()) {
                Instrument testContexsInstrument = ctx.getEngine().getInstruments().get("testContexsInstrument");
                TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
                events = test.events;
                ctx.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
                assertTrue(Integer.toString(events.size()), events.size() > 1);
                // Have creation of polyglot context and a language
                assertEquals(5, events.size());
                assertContextEventType(events, ContextEvent.ContextEventType.CREATED, 0);
                assertNotNull(events.get(0).context);
                assertNull(events.get(0).language);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 1);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, 2);
                assertEquals(InstrumentationTestLanguage.ID, events.get(2).language.getId());
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, 3);
                assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZED, 4);
            }
            assertEquals(8, events.size());
            Engine engine = Engine.create();
            // Contexts created and closed:
            try (Context ctx = Context.newBuilder().engine(engine).build()) {
                ctx.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
                ctx.eval(Source.create(InstrumentationTestLanguage.ID, "CONTEXT(STATEMENT())"));
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
                    assertEquals(5, events.size());
                    assertContextEventType(events, ContextEvent.ContextEventType.CREATED, 0);
                    assertNull(events.get(0).language);
                    assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATING, 1);
                    assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.CREATED, 2);
                    assertEquals(InstrumentationTestLanguage.ID, events.get(2).language.getId());
                    assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZING, 3);
                    assertLanguageContextEventType(events, ContextEvent.LanguageContextEventType.INITIALIZED, 4);
                    testBlock.blockOnStatements.set(false);
                }
            };
            Context ctx = Context.newBuilder().engine(engine).build();
            ctx.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(CONTEXT(EXPRESSION()), CONTEXT(STATEMENT))"));
        } finally {
            TestContextsInstrument.includeActiveContexts = false;
        }
    }

    @Test
    public void testContextsNotCloseable() {
        try (Engine engine = Engine.create()) {
            Instrument testContexsInstrument = engine.getInstruments().get("testContexsInstrument");
            TestContextsInstrument test = testContexsInstrument.lookup(TestContextsInstrument.class);
            final List<ContextEvent> events = test.events;
            try (Context ctx = Context.newBuilder().engine(engine).build()) {
                ctx.eval(Source.create(InstrumentationTestLanguage.ID, "CONTEXT(STATEMENT())"));
                for (ContextEvent event : events) {
                    // supported as long as not entered
                    event.context.close();
                    break;
                }
            }
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
            events.add(new ContextEvent(context.isEntered(), context, null, ContextEvent.ContextEventType.CREATED));
        }

        @Override
        public void onLanguageContextCreate(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.CREATING));
        }

        @Override
        public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.CREATED));
        }

        @Override
        public void onLanguageContextCreateFailed(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.CREATEFAILED));
        }

        @Override
        public void onLanguageContextInitialize(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.INITIALIZING));
        }

        @Override
        public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.INITIALIZED));
        }

        @Override
        public void onLanguageContextInitializeFailed(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.INITIALIZEFAILED));
        }

        @Override
        public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.FINALIZED));
        }

        @Override
        public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
            events.add(new ContextEvent(context.isEntered(), context, language, ContextEvent.LanguageContextEventType.DISPOSED));
        }

        @Override
        public void onContextClosed(TruffleContext context) {
            events.add(new ContextEvent(context.isEntered(), context, null, ContextEvent.ContextEventType.CLOSED));
        }

    }

    private static class ContextEvent {
        enum ContextEventType {
            CREATED,
            CLOSED
        }

        enum LanguageContextEventType {
            CREATING,
            CREATED,
            CREATEFAILED,
            INITIALIZING,
            INITIALIZED,
            INITIALIZEFAILED,
            FINALIZED,
            DISPOSED
        }

        final boolean entered;
        final TruffleContext context;
        final LanguageInfo language;
        final ContextEventType contextEventType;
        final LanguageContextEventType languageContextEventType;

        ContextEvent(boolean entered, TruffleContext context, LanguageInfo language, ContextEventType contextEventType) {
            this(entered, context, language, contextEventType, null);
        }

        ContextEvent(boolean entered, TruffleContext context, LanguageInfo language, LanguageContextEventType languageContextEventType) {
            this(entered, context, language, null, languageContextEventType);
        }

        ContextEvent(boolean entered, TruffleContext context, LanguageInfo language, ContextEventType contextEventType, LanguageContextEventType languageContextEventType) {
            assert contextEventType == null || languageContextEventType == null;
            this.entered = entered;
            this.context = context;
            this.language = language;
            this.contextEventType = contextEventType;
            this.languageContextEventType = languageContextEventType;
        }
    }

    static class LanguageContextFailureLanguage extends ProxyLanguage {
        private final boolean failCreate;
        private final boolean failInitialize;

        LanguageContextFailureLanguage(boolean failCreate, boolean failInitialize) {
            this.failCreate = failCreate;
            this.failInitialize = failInitialize;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(languageInstance) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return null;
                }
            }.getCallTarget();
        }

        @Override
        protected LanguageContext createContext(Env env) {
            if (failCreate) {
                throw new CancellationException(CANCELLING_LANGUAGE_CONTEXT_CREATION);
            }
            return super.createContext(env);
        }

        @Override
        protected void initializeContext(LanguageContext context) throws Exception {
            if (failInitialize) {
                throw new CancellationException(CANCELLING_LANGUAGE_CONTEXT_INITIALIZATION);
            }
            super.initializeContext(context);
        }
    }

    @Test
    public void testOtherThreadBlockedDuringLanguageContextInitialization() throws InterruptedException, ExecutionException {
        setupEnv(Context.newBuilder(), new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
        /*
         * Test that when the main thread is not done initializing a language context, other thread
         * cannot execute the language and must wait for the initialization to finish.
         */
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch contextInitLatch = new CountDownLatch(1);
            CountDownLatch contextInitFinishLatch = new CountDownLatch(1);
            instrumentEnv.getInstrumenter().attachContextsListener(new ContextsListener() {
                @Override
                public void onContextCreated(TruffleContext ctx) {

                }

                @Override
                public void onLanguageContextCreated(TruffleContext ctx, LanguageInfo lang) {

                }

                @Override
                public void onLanguageContextInitialized(TruffleContext ctx, LanguageInfo lang) {
                    if (InstrumentationTestLanguage.ID.equals(lang.getId())) {
                        contextInitLatch.countDown();
                        try {
                            /*
                             * The await here blocks finishing PolyglotLanguageContext
                             * initialization. The countDown of the contextInitFinishLatch is done
                             * in a separate thread after it evals a simple statement. Since the
                             * eval must wait for the PolyglotLanguageContext initialization to
                             * finish, the await cannot finish successfully, hence the assert.
                             */
                            Assert.assertFalse(contextInitFinishLatch.await(1, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }
                }

                @Override
                public void onLanguageContextFinalized(TruffleContext ctx, LanguageInfo lang) {

                }

                @Override
                public void onLanguageContextDisposed(TruffleContext ctx, LanguageInfo lang) {

                }

                @Override
                public void onContextClosed(TruffleContext ctx) {

                }
            }, false);
            Future<?> future = executorService.submit(() -> {
                try {
                    contextInitLatch.await();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                context.eval(InstrumentationTestLanguage.ID, "STATEMENT");
                contextInitFinishLatch.countDown();
            });
            context.initialize(InstrumentationTestLanguage.ID);
            future.get();
        } finally {
            executorService.shutdownNow();
            Assert.assertTrue(executorService.awaitTermination(1, TimeUnit.MINUTES));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ExecutableObject implements TruffleObject {
        private final CallTarget target;

        ExecutableObject(CallTarget target) {
            this.target = target;
        }

        @ExportMessage
        static boolean isExecutable(ExecutableObject obj) {
            return obj.target != null;
        }

        @ExportMessage
        static Object execute(ExecutableObject obj, Object[] args) {
            return obj.target.call(args);
        }

        @ExportMessage
        static boolean isNull(ExecutableObject obj) {
            return obj.target == null;
        }
    }

    @TruffleLanguage.Registration
    static class UseBeforeContextInitFinishedLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(UseBeforeContextInitFinishedLanguage.class);

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            ExecutableContext execCtx = TestAPIAccessor.engineAccess().getCurrentContext(UseBeforeContextInitFinishedLanguage.class);
            String memberKey = "thread" + Thread.currentThread().threadId();
            appendElement(execCtx, memberKey, "executed");
            return memberKey;
        }

        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
        private void appendElement(ExecutableContext ctx, String memberKey, String element) {
            synchronized (ctx) {
                try {
                    Object scope = getScope(ctx);
                    InteropLibrary uncached = InteropLibrary.getUncached();
                    String prev = "";
                    if (uncached.isMemberReadable(scope, memberKey)) {
                        prev = uncached.readMember(scope, memberKey) + ",";
                    }
                    String newValue = prev + element;
                    uncached.writeMember(scope, memberKey, newValue);
                } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
                    throw new AssertionError(e);
                }
            }
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected void initializeContext(ExecutableContext ctx) throws Exception {
            com.oracle.truffle.api.source.Source useBeforeInitExecutableSrc = com.oracle.truffle.api.source.Source.newBuilder(UseBeforeContextInitFinishedLanguage.ID, "doesnotmatter",
                            "dummy").build();
            CallTarget useBeforeInitExecutableTarget = ctx.env.parsePublic(useBeforeInitExecutableSrc);
            com.oracle.truffle.api.source.Source populateOtherLangExecutableSrc = com.oracle.truffle.api.source.Source.newBuilder(UseOtherLangObjectOnDifferentThreadLanguage.ID,
                            "populateOtherLangExecutable", "dummy").build();
            CallTarget populateOtherLangExecutableTarget = ctx.env.parsePublic(populateOtherLangExecutableSrc);
            populateOtherLangExecutableTarget.call(new ExecutableObject(useBeforeInitExecutableTarget));
        }

        @Override
        protected void initializeThread(ExecutableContext ctx, Thread thread) {
            appendElement(ctx, "thread" + thread.threadId(), "initializeThread");
        }
    }

    @TruffleLanguage.Registration
    static class UseOtherLangObjectOnDifferentThreadLanguage extends TruffleLanguage<UseOtherLangObjectOnDifferentThreadLanguage.UseOtherLangObjectLanguageContext> {
        static final String ID = TestUtils.getDefaultLanguageId(UseOtherLangObjectOnDifferentThreadLanguage.class);

        static class UseOtherLangObjectLanguageContext {
            private Object otherLangExecutable;

            private static final ContextReference<UseOtherLangObjectLanguageContext> REFERENCE = ContextReference.create(UseOtherLangObjectOnDifferentThreadLanguage.class);

            void populateOtherLangExecutable(Object o) {
                assertNull(otherLangExecutable);
                synchronized (this) {
                    otherLangExecutable = o;
                    notifyAll();
                }
            }

            @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
            Object waitForAndExecuteOtherLangExecutable(Node node) {
                TruffleSafepoint.setBlockedThreadInterruptible(node, langContext -> {
                    synchronized (langContext) {
                        while (langContext.otherLangExecutable == null) {
                            langContext.wait();
                        }
                    }

                }, this);
                try {
                    return InteropLibrary.getUncached().execute(otherLangExecutable);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw new AssertionError(e);
                }
            }
        }

        @Override
        protected UseOtherLangObjectLanguageContext createContext(Env env) {
            return new UseOtherLangObjectLanguageContext();
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        protected CallTarget parse(ParsingRequest request) throws Exception {
            String srcString = request.getSource().getCharacters().toString();
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    UseOtherLangObjectLanguageContext langContext = UseOtherLangObjectLanguageContext.REFERENCE.get(this);
                    return populateOrExecuteOtherLangObject(langContext, srcString, frame.getArguments());
                }

                @CompilerDirectives.TruffleBoundary
                private Object populateOrExecuteOtherLangObject(UseOtherLangObjectLanguageContext langContext, String branchName, Object[] args) {
                    switch (branchName) {
                        case "populateOtherLangExecutable":
                            langContext.populateOtherLangExecutable(args[0]);
                            break;
                        case "waitForAndExecuteOtherLangExecutable":
                            return langContext.waitForAndExecuteOtherLangExecutable(this);
                        default:
                            throw CompilerDirectives.shouldNotReachHere("Unimplemented branch");
                    }
                    return NullObject.SINGLETON;
                }
            }.getCallTarget();
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testUseOtherLangObjectOnDifferentThread() throws InterruptedException, ExecutionException {
        /*
         * This test executes code of UseBeforeContextInitFinishedLanguage on another thread before
         * TruffleLanguage#initializeContext callback for that language finishes on the main thread.
         * The test then verifies that the TruffleLanguage#initializeThread callback was called
         * before the code was executed on the other thread.
         */
        setupEnv(Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL), new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
        CountDownLatch langContextInitFinishLatch = new CountDownLatch(1);
        CountDownLatch langContextInitLatch = new CountDownLatch(1);
        instrumentEnv.getInstrumenter().attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext ctx) {

            }

            @Override
            public void onLanguageContextCreated(TruffleContext ctx, LanguageInfo lang) {

            }

            @Override
            public void onLanguageContextInitialized(TruffleContext ctx, LanguageInfo lang) {
                if (UseBeforeContextInitFinishedLanguage.ID.equals(lang.getId())) {
                    langContextInitLatch.countDown();
                    try {
                        langContextInitFinishLatch.await();
                    } catch (InterruptedException ie) {
                        throw new AssertionError(ie);
                    }
                }
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext ctx, LanguageInfo lang) {

            }

            @Override
            public void onLanguageContextDisposed(TruffleContext ctx, LanguageInfo lang) {

            }

            @Override
            public void onContextClosed(TruffleContext ctx) {

            }
        }, false);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executorService.submit(() -> {
                String toRet;
                context.enter();
                try {
                    langContextInitLatch.countDown();
                    toRet = context.eval(UseOtherLangObjectOnDifferentThreadLanguage.ID, "waitForAndExecuteOtherLangExecutable").asString();
                } finally {
                    context.leave();
                }
                langContextInitFinishLatch.countDown();
                return toRet;
            });
            try {
                langContextInitLatch.await();
            } catch (InterruptedException ie) {
                throw new AssertionError(ie);
            }
            context.initialize(UseBeforeContextInitFinishedLanguage.ID);
            Assert.assertEquals("initializeThread,executed", context.getBindings(UseBeforeContextInitFinishedLanguage.ID).getMember(future.get()).asString());
        } finally {
            executorService.shutdownNow();
            Assert.assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
        }
    }
}
