/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ThreadsEventsTest {

    @Test
    public void testSingleThread() {
        final List<ThreadEvent> events;
        try (Context context = Context.create()) {
            Instrument testThreadsInstrument = context.getEngine().getInstruments().get("testThreadsInstrument");
            TestThreadsInstrument test = testThreadsInstrument.lookup(TestThreadsInstrument.class);
            events = test.events;

            context.eval(Source.create(InstrumentationTestLanguage.ID, "STATEMENT()"));
            assertEquals(1, events.size());
            assertTrue(events.get(0).isNew);
            assertEquals(Thread.currentThread(), events.get(0).thread);
            assertNotNull(events.get(0).context);
        }
        assertEquals(2, events.size());
        assertFalse(events.get(1).isNew);
        assertEquals(Thread.currentThread(), events.get(1).thread);
        assertEquals(events.get(0).context, events.get(1).context);
        events.clear();
    }

    @Test
    public void testSpawnThread() {
        final List<ThreadEvent> events;
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            Instrument testThreadsInstrument = context.getEngine().getInstruments().get("testThreadsInstrument");
            TestThreadsInstrument test = testThreadsInstrument.lookup(TestThreadsInstrument.class);
            events = test.events;

            context.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo, STATEMENT), SPAWN(foo), JOIN())"));
            assertEquals(3, events.size());
            // current thread
            assertTrue(events.get(0).isNew);
            assertEquals(Thread.currentThread(), events.get(0).thread);
            assertNotNull(events.get(0).context);
            // spawned thread
            assertTrue(events.get(1).isNew);
            assertNotEquals(Thread.currentThread(), events.get(1).thread);
            assertEquals(events.get(0).context, events.get(1).context);
            assertFalse(events.get(2).isNew);
            assertEquals(events.get(1).thread, events.get(2).thread);
            assertEquals(events.get(1).context, events.get(2).context);
        }
        assertEquals(4, events.size());
        // current thread
        assertFalse(events.get(3).isNew);
        assertEquals(Thread.currentThread(), events.get(3).thread);
        assertEquals(events.get(0).context, events.get(3).context);
        events.clear();
    }

    @Test
    public void testSpawnManyThread() {
        final int numThreads = 100;
        final List<ThreadEvent> events;
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            Instrument testThreadsInstrument = context.getEngine().getInstruments().get("testThreadsInstrument");
            TestThreadsInstrument test = testThreadsInstrument.lookup(TestThreadsInstrument.class);
            events = test.events;

            context.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo, STATEMENT), LOOP(" + numThreads + ", SPAWN(foo)), JOIN())"));
        }
        assertEquals(2 + 2 * numThreads, events.size());
        List<ThreadEvent> startEvents = new ArrayList<>(1 + numThreads);
        for (ThreadEvent event : events) {
            if (event.isNew) {
                startEvents.add(event);
            }
        }
        assertEquals(1 + numThreads, startEvents.size());
        TruffleContext tcontext = events.get(0).context;
        for (ThreadEvent event : events) {
            assertEquals(tcontext, event.context);
        }
        events.clear();
    }

    @Test
    public void testContextThreads() throws Exception {
        Engine engine = Engine.create();
        Instrument testThreadsInstrument = engine.getInstruments().get("testThreadsInstrument");
        TestThreadsInstrument test = testThreadsInstrument.lookup(TestThreadsInstrument.class);
        final List<ThreadEvent> events = test.events;
        Source source = Source.create(InstrumentationTestLanguage.ID, "STATEMENT()");

        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try (Context context = Context.newBuilder().engine(engine).build()) {
                        context.eval(source);
                    }
                }
            };
        }
        for (int i = 0; i < numThreads; i++) {
            threads[i].start();
        }
        for (int i = 0; i < numThreads; i++) {
            threads[i].join();
        }

        assertEquals(2 * numThreads, events.size());
        List<ThreadEvent> startEvents = new ArrayList<>(numThreads);
        for (ThreadEvent event : events) {
            if (event.isNew) {
                startEvents.add(event);
            }
        }
        assertEquals(numThreads, startEvents.size());
        // Verify that we got all threads in the events:
        Set<Thread> allThreads = new HashSet<>(Arrays.asList(threads));
        for (int i = 0; i < numThreads; i++) {
            assertTrue(allThreads.remove(startEvents.get(i).thread));
        }
        assertTrue(allThreads.toString(), allThreads.isEmpty());
        // Verify that we got 'numThreads' number of contexts:
        Set<TruffleContext> distinctContexts = new HashSet<>();
        for (ThreadEvent event : events) {
            distinctContexts.add(event.context);
        }
        assertEquals(numThreads, distinctContexts.size());
    }

    @Test
    public void testGetStartedThreads() throws Exception {
        try {
            TestThreadsInstrument.includeStartedThreads = true;
            final List<ThreadEvent> events;
            try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
                context.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(DEFINE(f1, EXPRESSION), SPAWN(f1), JOIN())"));
                Instrument testBlockOnStatementsInstrument = context.getEngine().getInstruments().get("testBlockOnStatementsInstrument");
                TestBlockOnStatementsInstrument testBlock = testBlockOnStatementsInstrument.lookup(TestBlockOnStatementsInstrument.class);
                CountDownLatch latch = new CountDownLatch(1);
                testBlock.runOnBlock = new Runnable() {
                    @Override
                    public void run() {
                        latch.countDown();
                    }
                };
                context.eval(Source.create(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo, STATEMENT), SPAWN(foo))"));
                latch.await();

                Instrument testThreadsInstrument = context.getEngine().getInstruments().get("testThreadsInstrument");
                TestThreadsInstrument test = testThreadsInstrument.lookup(TestThreadsInstrument.class);
                events = test.events;

                assertEquals(events.toString(), 2, events.size());
                assertTrue(events.get(0).isNew);
                assertNotNull(events.get(0).context);
                assertTrue(events.get(1).isNew);
                assertEquals(events.get(0).context, events.get(1).context);
                assertNotEquals(events.get(0).thread, events.get(1).thread);

                testBlock.blockOnStatements.set(false);
                synchronized (testBlock.blockOnStatements) {
                    testBlock.blockOnStatements.notifyAll();
                }
                context.eval(Source.create(InstrumentationTestLanguage.ID, "JOIN()"));
                assertEquals(3, events.size());
                assertFalse(events.get(2).isNew);
                assertTrue(events.get(0).thread == events.get(2).thread || events.get(1).thread == events.get(2).thread);
            }
            assertEquals(4, events.size());
        } finally {
            TestThreadsInstrument.includeStartedThreads = false;
        }
    }

    @Registration(id = "testThreadsInstrument", services = TestThreadsInstrument.class)
    public static class TestThreadsInstrument extends TruffleInstrument implements ThreadsListener {

        static boolean includeStartedThreads = false;
        final List<ThreadEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachThreadsListener(this, includeStartedThreads);
            env.registerService(this);
        }

        @Override
        public void onThreadInitialized(TruffleContext context, Thread thread) {
            events.add(new ThreadEvent(true, thread, context));
        }

        @Override
        public void onThreadDisposed(TruffleContext context, Thread thread) {
            events.add(new ThreadEvent(false, thread, context));
        }

    }

    @Registration(id = "testBlockOnStatementsInstrument", services = TestBlockOnStatementsInstrument.class)
    public static class TestBlockOnStatementsInstrument extends TruffleInstrument implements ExecutionEventListener {

        final AtomicBoolean blockOnStatements = new AtomicBoolean(true);
        Runnable runOnBlock;

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), this);
            env.registerService(this);
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            if (runOnBlock != null) {
                runOnBlock.run();
            }
            doBlock();
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }

        @TruffleBoundary
        private void doBlock() {
            synchronized (blockOnStatements) {
                if (blockOnStatements.get()) {
                    try {
                        blockOnStatements.wait();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    private static class ThreadEvent {

        final boolean isNew;
        final Thread thread;
        final TruffleContext context;

        ThreadEvent(boolean isNew, Thread thread, TruffleContext context) {
            this.isNew = isNew;
            this.thread = thread;
            this.context = context;
        }

        @Override
        public String toString() {
            return (isNew ? "New " : "Passed ") + thread.toString() + " context = " + context;
        }

    }
}
