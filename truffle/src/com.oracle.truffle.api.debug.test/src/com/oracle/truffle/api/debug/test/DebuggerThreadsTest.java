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
package com.oracle.truffle.api.debug.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.debug.DebugContext;
import com.oracle.truffle.api.debug.DebugThreadsListener;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;

public class DebuggerThreadsTest extends AbstractDebugTest {

    @Test
    public void testSingleThread() throws Throwable {
        final Source source = testSource("STATEMENT()");
        TestThreadsListener threadsListener = new TestThreadsListener();
        List<ThreadEvent> events = threadsListener.events;
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            session.setThreadsListener(threadsListener, false);
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                assertEquals(1, events.size());
                assertTrue(events.get(0).isNew);
                assertEquals(Thread.currentThread(), events.get(0).thread);
                assertNotNull(events.get(0).context);
            });
            expectDone();
            closeEngine();
            // We're closing the engine not on the execution thread - we get two more thread events.
            assertEquals(4, events.size());
            assertFalse(events.get(3).isNew);
            assertEquals(events.get(0).context, events.get(3).context);
        }
        events.clear();
    }

    @Test
    public void testSpawnThreads() {
        int numThreads = 10;
        final Source source = testSource("ROOT(DEFINE(foo, STATEMENT), LOOP(" + numThreads + ", SPAWN(foo)), JOIN())");
        TestThreadsListener threadsListener = new TestThreadsListener();
        List<ThreadEvent> events = threadsListener.events;
        try (DebuggerSession session = startSession()) {
            session.setThreadsListener(threadsListener, false);
            startEval(source);
            expectDone();
        }
        assertEquals(1 + 2 * numThreads, events.size());
        List<ThreadEvent> startEvents = new ArrayList<>(1 + numThreads);
        for (ThreadEvent event : events) {
            if (event.isNew) {
                startEvents.add(event);
            }
        }
        assertEquals(1 + numThreads, startEvents.size());
        DebugContext dcontext = events.get(0).context;
        for (ThreadEvent event : events) {
            assertEquals(dcontext, event.context);
        }
        events.clear();
    }

    @Test
    public void testContextThreads() throws Exception {
        Engine engine = Engine.create();
        final Source source = testSource("STATEMENT()");
        TestThreadsListener threadsListener = new TestThreadsListener();
        List<ThreadEvent> events = threadsListener.events;

        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        Debugger debugger = engine.getInstruments().get("debugger").lookup(Debugger.class);
        try (DebuggerSession session = debugger.startSession(null)) {
            session.setThreadsListener(threadsListener, false);
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
        Set<DebugContext> distinctContexts = new HashSet<>();
        for (ThreadEvent event : events) {
            distinctContexts.add(event.context);
        }
        assertEquals(numThreads, distinctContexts.size());
    }

    @Test
    public void testGetStartedThreads() throws Exception {
        Engine engine = Engine.create();
        TestThreadsListener threadsListener = new TestThreadsListener();
        List<ThreadEvent> events = threadsListener.events;

        Debugger debugger = engine.getInstruments().get("debugger").lookup(Debugger.class);
        CountDownLatch latch = new CountDownLatch(1);
        DebuggerSession[] sessionPtr = new DebuggerSession[1];
        try (DebuggerSession session = debugger.startSession(new SuspendedCallback() {
            @Override
            public void onSuspend(SuspendedEvent event) {
                latch.countDown();
                sessionPtr[0].setThreadsListener(threadsListener, true);
                assertEquals(events.toString(), 2, events.size());
                assertTrue(events.get(0).isNew);
                assertNotNull(events.get(0).context);
                assertTrue(events.get(1).isNew);
                assertEquals(events.get(0).context, events.get(1).context);
                assertNotEquals(events.get(0).thread, events.get(1).thread);
            }
        })) {
            session.suspendNextExecution();
            sessionPtr[0] = session;
            try (Context context = Context.newBuilder().engine(engine).allowCreateThread(true).build()) {
                Source source = testSource("ROOT(DEFINE(f1, EXPRESSION), SPAWN(f1), JOIN())");
                context.eval(source);
                source = testSource("ROOT(DEFINE(foo, STATEMENT), SPAWN(foo))");
                context.eval(source);
                latch.await();
                context.eval(testSource("JOIN()"));
                assertEquals(events.toString(), 3, events.size());
                assertFalse(events.get(2).isNew);
                assertTrue(events.get(0).thread == events.get(2).thread || events.get(1).thread == events.get(2).thread);
            }
            assertEquals(4, events.size());
        }
    }

    private static class TestThreadsListener implements DebugThreadsListener {

        final List<ThreadEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void threadInitialized(DebugContext context, Thread thread) {
            events.add(new ThreadEvent(true, thread, context));
        }

        @Override
        public void threadDisposed(DebugContext context, Thread thread) {
            events.add(new ThreadEvent(false, thread, context));
        }

    }

    private static class ThreadEvent {

        final boolean isNew;
        final Thread thread;
        final DebugContext context;

        ThreadEvent(boolean isNew, Thread thread, DebugContext context) {
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
