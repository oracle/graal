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
