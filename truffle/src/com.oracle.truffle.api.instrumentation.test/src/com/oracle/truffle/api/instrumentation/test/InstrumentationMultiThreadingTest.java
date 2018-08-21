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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

public class InstrumentationMultiThreadingTest {

    @Test
    public void testAsyncAttachement() throws InterruptedException, ExecutionException {
        // unfortunately we don't support multiple evals yet
        int nEvals = 1;
        int nInstruments = 10;

        for (int j = 0; j < 5; j++) {
            ExecutorService executorService = Executors.newFixedThreadPool(nEvals + nInstruments);
            List<Future<?>> futures = new ArrayList<>();
            final AtomicBoolean terminated = new AtomicBoolean(false);
            final AtomicReference<Context> engineRef = new AtomicReference<>();
            for (int i = 0; i < nEvals; i++) {
                futures.add(executorService.submit(new Runnable() {
                    public void run() {
                        final Context engine = Context.create();
                        engineRef.set(engine);
                        while (!terminated.get()) {
                            try {
                                engine.eval(Source.create(InstrumentationTestLanguage.ID,
                                                "ROOT(BLOCK(STATEMENT(EXPRESSION, EXPRESSION), STATEMENT(EXPRESSION)))"));
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }));
            }

            for (int i = 0; i < nInstruments; i++) {
                final int index = i;
                futures.add(executorService.submit(new Runnable() {
                    public void run() {
                        while (!terminated.get()) {
                            Context engine = engineRef.get();
                            if (engine != null) {
                                engine.getEngine().getInstruments().get("testAsyncAttachement1").lookup(EnableableInstrument.class).setEnabled(index % 2 == 0);
                                engine.getEngine().getInstruments().get("testAsyncAttachement2").lookup(EnableableInstrument.class).setEnabled(index % 2 == 1);
                            }
                        }
                    }
                }));
            }
            terminated.set(true);
            for (Future<?> future : futures) {
                future.get();
            }
            engineRef.get().getEngine().getInstruments().get("testAsyncAttachement1").lookup(EnableableInstrument.class).setEnabled(false);
            engineRef.get().getEngine().getInstruments().get("testAsyncAttachement2").lookup(EnableableInstrument.class).setEnabled(false);
        }

        Assert.assertEquals(0, createDisposeCount.get());

    }

    @Registration(id = "testAsyncAttachement1", services = EnableableInstrument.class)
    public static class TestAsyncAttachement1 extends EnableableInstrument {

        private EventBinding<?>[] bindings;

        @Override
        public synchronized void setEnabled(boolean enabled) {
            if (enabled && bindings == null) {
                bindings = createDummyBindings(getEnv().getInstrumenter());
                createDisposeCount.incrementAndGet();
            } else if (!enabled && bindings != null) {
                createDisposeCount.decrementAndGet();
                for (EventBinding<?> b : bindings) {
                    b.dispose();
                }
                bindings = null;
            }
        }

    }

    static final AtomicInteger createDisposeCount = new AtomicInteger(0);

    @Registration(id = "testAsyncAttachement2", services = EnableableInstrument.class)
    public static class TestAsyncAttachement2 extends EnableableInstrument {

        private EventBinding<?>[] bindings;

        @Override
        public synchronized void setEnabled(boolean enabled) {
            if (enabled && bindings == null) {
                bindings = createDummyBindings(getEnv().getInstrumenter());
                createDisposeCount.incrementAndGet();
            } else if (!enabled && bindings != null) {
                createDisposeCount.decrementAndGet();
                for (EventBinding<?> b : bindings) {
                    b.dispose();
                }
                bindings = null;
            }
        }

    }

    private static EventBinding<?>[] createDummyBindings(Instrumenter instrumenter) {
        EventBinding<?>[] bindings = new EventBinding<?>[5];
        int bi = 0;
        ExecutionEventListener dummyListener = new ExecutionEventListener() {
            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            }

            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }

            public void onEnter(EventContext context, VirtualFrame frame) {
            }
        };
        bindings[bi++] = instrumenter.attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), dummyListener);
        bindings[bi++] = instrumenter.attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), dummyListener);
        bindings[bi++] = instrumenter.attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {

            public void onLoad(LoadSourceEvent event) {

            }
        }, true);
        bindings[bi++] = instrumenter.attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new LoadSourceSectionListener() {

            public void onLoad(LoadSourceSectionEvent event) {

            }
        }, true);

        bindings[bi++] = instrumenter.attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new LoadSourceSectionListener() {
            public void onLoad(LoadSourceSectionEvent event) {

            }
        }, true);
        return bindings;
    }

}
