/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
