/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.ThreadsActivationListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@SuppressWarnings("this-escape")
@RunWith(Theories.class)
public class ContextLocalTest extends AbstractPolyglotTest {

    @DataPoints public static final boolean[] useVirtualThreads = new boolean[]{false, true};

    private static final int PARALLELISM = 32;
    static final String VALID_EXCLUSIVE_LANGUAGE = "ContextLocalTest_ValidExclusiveLanguage";
    static final String VALID_SHARED_LANGUAGE = "ContextLocalTest_ValidSharedLanguage";
    static final String VALID_INSTRUMENT = "ContextLocalTest_ValidInstrument";
    static final String INVALID_CONTEXT_LOCAL = "ContextLocalTest_InvalidLanguageContextLocal";
    static final String INVALID_CONTEXT_THREAD_LOCAL = "ContextLocalTest_InvalidLanguageContextThreadLocal";
    static final String CONTEXT_LOCAL_ACCESS_RACE_INSTRUMENT = "ContextLocalTest_ContextLocalAccessRaceInstrument";

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void customSubclassesDisallowed() {
        assertFails(() -> new ContextLocal<String>(null) {

            @Override
            public String get() {
                return null;
            }

            @Override
            public String get(TruffleContext c) {
                return null;
            }
        }, IllegalStateException.class);

        assertFails(() -> new ContextThreadLocal<String>(null) {

            @Override
            public String get() {
                return null;
            }

            @Override
            public String get(TruffleContext t) {
                return null;
            }

            @Override
            public String get(TruffleContext c, Thread t) {
                return null;
            }

            @Override
            public String get(Thread t) {
                return null;
            }

        }, IllegalStateException.class);

    }

    private static void runInParallel(boolean vthreads, Runnable callable) throws InterruptedException, ExecutionException {
        ExecutorService executor = threadPool(PARALLELISM, vthreads);
        List<Future<?>> futures = new ArrayList<>();
        /*
         * For virtual threads, we want a number of iterations well above the maxPoolSize of
         * VirtualThread.DEFAULT_SCHEDULER, which is max(availableProcessors(), 256).
         */
        int iterations = vthreads ? 5000 : 50;
        for (int i = 0; i < iterations; i++) {
            futures.add(executor.submit(() -> {
                callable.run();
                return null;
            }));
        }
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        }
    }

    @Theory
    public void testExclusiveLanguageContextThreadLocal(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());

        try (Context c0 = Context.create(); Context c1 = Context.create()) {
            c0.initialize(VALID_EXCLUSIVE_LANGUAGE);

            c0.enter();
            Env env0;
            TruffleContext t0;
            ValidExclusiveLanguage language0;
            try {
                env0 = ValidExclusiveLanguage.CONTEXT_REF.get(null);
                t0 = env0.getContext();
                language0 = ValidExclusiveLanguage.REFERENCE.get(null);
            } finally {
                c0.leave();
            }

            runInParallel(vthreads, () -> {
                c0.enter();
                try {
                    assertSame(env0, language0.contextThreadLocal0.get().env);
                    assertSame(env0, language0.contextThreadLocal1.get().env);
                    reschedule();
                    assertSame(Thread.currentThread(), language0.contextThreadLocal0.get().thread);
                    assertSame(Thread.currentThread(), language0.contextThreadLocal1.get().thread);
                } finally {
                    c0.leave();
                }
            });

            c1.initialize(VALID_EXCLUSIVE_LANGUAGE);
            runInParallel(vthreads, () -> {
                c1.enter();
                try {
                    Env env1 = ValidExclusiveLanguage.CONTEXT_REF.get(null);
                    TruffleContext t1 = env1.getContext();
                    ValidExclusiveLanguage language1 = ValidExclusiveLanguage.REFERENCE.get(null);
                    assertSame(env1, language1.contextThreadLocal0.get().env);
                    assertSame(Thread.currentThread(), language1.contextThreadLocal0.get().thread);
                    assertSame(env1, language1.contextThreadLocal1.get().env);
                    assertSame(Thread.currentThread(), language1.contextThreadLocal1.get().thread);

                    assertFails(() -> language1.contextThreadLocal0.get(t0, Thread.currentThread()),
                                    AssertionError.class,
                                    (e) -> e.getMessage().startsWith("Detected invalid sharing of context locals"));
                    assertFails(() -> language1.contextThreadLocal1.get(t0, Thread.currentThread()),
                                    AssertionError.class,
                                    (e) -> e.getMessage().startsWith("Detected invalid sharing of context locals"));

                    assertSame(env1, language1.contextThreadLocal0.get(t1, Thread.currentThread()).env);
                    assertSame(Thread.currentThread(), language1.contextThreadLocal0.get(t1,
                                    Thread.currentThread()).thread);
                    assertSame(env1, language1.contextThreadLocal1.get(t1, Thread.currentThread()).env);
                    assertSame(Thread.currentThread(), language1.contextThreadLocal1.get(t1,
                                    Thread.currentThread()).thread);
                    assertNotSame(env0, env1);

                } finally {
                    c1.leave();
                }
            });

        }

    }

    @Test
    public void testExclusiveLanguageContextLocal() {
        try (Context c0 = Context.create();
                        Context c1 = Context.create()) {
            c0.initialize(VALID_EXCLUSIVE_LANGUAGE);

            c0.enter();
            Env env0;
            TruffleContext t0;
            ValidExclusiveLanguage language0;
            try {
                env0 = ValidExclusiveLanguage.CONTEXT_REF.get(null);
                t0 = env0.getContext();
                language0 = ValidExclusiveLanguage.REFERENCE.get(null);
                assertSame(env0, language0.contextLocal0.get());
                assertSame(env0, language0.contextLocal1.get());
            } finally {
                c0.leave();
            }

            c1.initialize(VALID_EXCLUSIVE_LANGUAGE);
            c1.enter();
            try {
                Env env1 = ValidExclusiveLanguage.CONTEXT_REF.get(null);
                TruffleContext t1 = env1.getContext();
                ValidExclusiveLanguage language1 = ValidExclusiveLanguage.REFERENCE.get(null);
                assertSame(env1, language1.contextLocal0.get());
                assertSame(env1, language1.contextLocal1.get());

                assertFails(() -> language1.contextLocal0.get(t0), AssertionError.class, (e) -> e.getMessage().startsWith("Detected invalid sharing of context locals"));
                assertFails(() -> language1.contextLocal1.get(t0), AssertionError.class, (e) -> e.getMessage().startsWith("Detected invalid sharing of context locals"));
                assertFails(() -> language0.contextLocal0.get(), AssertionError.class, (e) -> e.getMessage().startsWith("Detected invalid sharing of context locals"));

                assertSame(env1, language1.contextLocal0.get(t1));
                assertSame(env1, language1.contextLocal1.get(t1));
                assertNotSame(env0, env1);
            } finally {
                c1.leave();
            }
        }
    }

    @Test
    public void testSharedLanguageContextLocal() {
        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder().engine(engine).build();
                            Context c1 = Context.newBuilder().engine(engine).build()) {
                c0.initialize(VALID_SHARED_LANGUAGE);

                c0.enter();
                Env env0;
                TruffleContext t0;
                ValidSharedLanguage language0;
                try {
                    env0 = ValidSharedLanguage.CONTEXT_REF.get(null);
                    t0 = env0.getContext();
                    language0 = ValidSharedLanguage.REFERENCE.get(null);
                    assertSame(env0, language0.local0.get());
                    assertSame(env0, language0.local1.get());
                } finally {
                    c0.leave();
                }
                c1.initialize(VALID_SHARED_LANGUAGE);

                c1.enter();
                try {
                    Env env1 = ValidSharedLanguage.CONTEXT_REF.get(null);
                    TruffleContext t1 = env1.getContext();
                    ValidSharedLanguage language1 = ValidSharedLanguage.REFERENCE.get(null);
                    assertSame(env1, language1.local0.get());
                    assertSame(env1, language1.local1.get());

                    assertSame(env0, language1.local0.get(t0));
                    assertSame(env0, language1.local1.get(t0));

                    assertSame(env0, language0.local0.get(t0));
                    assertSame(env1, language1.local0.get(t1));
                    assertSame(env1, language1.local1.get(t1));
                    assertNotSame(env0, env1);
                } finally {
                    c1.leave();
                }
            }
        }
    }

    @Theory
    public void testSharedLanguageContextThreadLocal(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());

        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder().engine(engine).build();
                            Context c1 = Context.newBuilder().engine(engine).build()) {
                c0.initialize(VALID_SHARED_LANGUAGE);

                c0.enter();
                Env env0;
                TruffleContext t0;
                ValidSharedLanguage language0;
                try {
                    env0 = ValidSharedLanguage.CONTEXT_REF.get(null);
                    t0 = env0.getContext();
                    language0 = ValidSharedLanguage.REFERENCE.get(null);
                } finally {
                    c0.leave();
                }

                runInParallel(vthreads, () -> {
                    c0.enter();
                    try {
                        assertSame(env0, language0.contextThreadLocal0.get().env);
                        assertSame(env0, language0.contextThreadLocal1.get().env);
                        assertSame(Thread.currentThread(), language0.contextThreadLocal0.get().thread);
                        assertSame(Thread.currentThread(), language0.contextThreadLocal1.get().thread);
                    } finally {
                        c0.leave();
                    }
                });

                c1.initialize(VALID_SHARED_LANGUAGE);
                runInParallel(vthreads, () -> {
                    c1.enter();
                    try {
                        Env env1 = ValidSharedLanguage.CONTEXT_REF.get(null);
                        TruffleContext t1 = env1.getContext();
                        ValidSharedLanguage language1 = ValidSharedLanguage.REFERENCE.get(null);
                        assertSame(env1, language1.contextThreadLocal0.get().env);
                        assertSame(env1, language1.contextThreadLocal1.get().env);

                        // thread local not initialized on this thread.
                        assertNull(language1.contextThreadLocal0.get(t0, Thread.currentThread()));
                        assertNull(language1.contextThreadLocal1.get(t0, Thread.currentThread()));

                        assertSame(env1, language1.contextThreadLocal0.get(Thread.currentThread()).env);
                        assertSame(env1, language1.contextThreadLocal1.get(Thread.currentThread()).env);

                        assertNull(language1.contextThreadLocal0.get(t0, Thread.currentThread()));
                        assertSame(env1, language1.contextThreadLocal1.get(t1, Thread.currentThread()).env);
                        assertSame(env1, language1.contextThreadLocal1.get(t1, Thread.currentThread()).env);
                        assertNotSame(env0, env1);
                    } finally {
                        c1.leave();
                    }
                });

            }
        }
    }

    @SuppressWarnings("cast")
    @Test
    public void testContextLocalValidInstrument() {
        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder().engine(engine).build();
                            Context c1 = Context.newBuilder().engine(engine).build()) {
                @SuppressWarnings("unchecked")
                ValidInstrument instrument = engine.getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);

                // no context entered
                assertFails(() -> instrument.local0.get(), IllegalStateException.class);

                c0.enter();
                TruffleContext tc0;
                try {
                    tc0 = instrument.local0.get();
                } finally {
                    c0.leave();
                }

                c1.enter();
                TruffleContext tc1;
                try {
                    tc1 = instrument.local0.get();
                } finally {
                    c1.leave();
                }

                assertNotSame(tc0, tc1);

                assertSame(tc0, instrument.local0.get(tc0));
                assertSame(tc1, instrument.local0.get(tc1));

                c0.initialize(VALID_SHARED_LANGUAGE);
                c0.enter();
                try {
                    assertEquals(tc0, ValidSharedLanguage.REFERENCE.get(null).local0.get().getContext());
                } finally {
                    c0.leave();
                }

                c1.initialize(VALID_SHARED_LANGUAGE);
                c1.enter();
                try {
                    assertEquals(tc1, ValidSharedLanguage.REFERENCE.get(null).local1.get().getContext());
                } finally {
                    c1.leave();
                }
            }
        }

    }

    @SuppressWarnings("cast")
    @Theory
    public void testContextThreadLocalValidInstrument(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());

        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder().engine(engine).build();
                            Context c1 = Context.newBuilder().engine(engine).build()) {

                ValidInstrument instrument = engine.getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);
                runInParallel(vthreads, () -> {

                    c0.enter();
                    InstrumentThreadLocalValue tc0;
                    try {
                        tc0 = instrument.threadLocal0.get();
                    } finally {
                        c0.leave();
                    }

                    c1.enter();
                    InstrumentThreadLocalValue tc1;
                    try {
                        tc1 = instrument.threadLocal0.get();
                    } finally {
                        c1.leave();
                    }

                    assertNotSame(tc0, tc1);

                    assertEquals(tc0, instrument.threadLocal0.get(tc0.context, Thread.currentThread()));
                    assertEquals(tc1, instrument.threadLocal0.get(tc1.context, Thread.currentThread()));

                    c0.initialize(VALID_SHARED_LANGUAGE);
                    c0.enter();
                    try {
                        assertEquals(tc0.context, ValidSharedLanguage.REFERENCE.get(null).contextThreadLocal0.get().env.getContext());
                    } finally {
                        c0.leave();
                    }

                    c1.initialize(VALID_SHARED_LANGUAGE);
                    c1.enter();
                    try {
                        assertEquals(tc1.context, ValidSharedLanguage.REFERENCE.get(null).contextThreadLocal1.get().env.getContext());
                    } finally {
                        c1.leave();
                    }

                    // no context entered
                    assertFails(() -> instrument.threadLocal0.get(), IllegalStateException.class);
                    assertFails(() -> instrument.threadLocal0.get(Thread.currentThread()), IllegalStateException.class);
                    assertFails(() -> instrument.threadLocal0.get(Thread.currentThread()), IllegalStateException.class);
                });
            }
        }

    }

    private static Object contextLocalDynamicValue = "non-null";
    private static Object threadLocalDynamicValue = "non-null";

    @Test
    public void testInvalidContextLocalLanguage() {
        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder(INVALID_CONTEXT_LOCAL, VALID_SHARED_LANGUAGE).engine(engine).allowAllAccess(true).build();
                            Context c1 = Context.newBuilder(INVALID_CONTEXT_LOCAL, VALID_SHARED_LANGUAGE).engine(engine).allowAllAccess(true).build()) {
                c0.initialize(INVALID_CONTEXT_LOCAL);
                c1.enter();
                try {
                    c1.initialize(VALID_SHARED_LANGUAGE);

                    Env env1 = ValidSharedLanguage.CONTEXT_REF.get(null);
                    LanguageInfo invalid = env1.getInternalLanguages().get(INVALID_CONTEXT_LOCAL);
                    assertFails(() -> env1.initializeLanguage(invalid), IllegalStateException.class,
                                    (e) -> assertTrue(e.getCause().getMessage(), e.getCause().getMessage().contains("did not create the same number of context locals")));
                } finally {
                    c1.leave();
                }
            }
        }
    }

    @Test
    public void testInvalidContextThreadLocalLanguage() {
        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder(INVALID_CONTEXT_THREAD_LOCAL, VALID_SHARED_LANGUAGE).engine(engine).allowAllAccess(true).build();
                            Context c1 = Context.newBuilder(INVALID_CONTEXT_THREAD_LOCAL, VALID_SHARED_LANGUAGE).engine(engine).allowAllAccess(true).build()) {
                c0.initialize(INVALID_CONTEXT_THREAD_LOCAL);
                c1.enter();
                try {
                    c1.initialize(VALID_SHARED_LANGUAGE);

                    Env env1 = ValidSharedLanguage.CONTEXT_REF.get(null);
                    LanguageInfo invalid = env1.getInternalLanguages().get(INVALID_CONTEXT_THREAD_LOCAL);
                    assertFails(() -> env1.initializeLanguage(invalid), IllegalStateException.class,
                                    (e) -> assertTrue(e.getCause().getMessage(), e.getCause().getMessage().contains("did not create the same number of context thread locals")));
                } finally {
                    c1.leave();
                }
            }
        }
    }

    @Test
    public void testCreateLanguageContextLocalLooLate() {
        try (Context c0 = Context.create();) {
            c0.initialize(VALID_SHARED_LANGUAGE);
            c0.enter();

            try {
                ValidSharedLanguage lang = ValidSharedLanguage.REFERENCE.get(null);
                assertFails(() -> lang.createContextLocal0("testString"), IllegalStateException.class,
                                (e) -> assertEquals(e.getMessage(), "The set of context locals is frozen. " +
                                                "Context locals can only be created during construction of the TruffleLanguage subclass."));
            } finally {
                c0.leave();
            }
        }
    }

    @Test
    public void testCreateLanguageContextThreadLocalLooLate() {
        try (Context c0 = Context.create();) {
            c0.initialize(VALID_SHARED_LANGUAGE);
            c0.enter();

            try {
                ValidSharedLanguage lang = ValidSharedLanguage.REFERENCE.get(null);
                assertFails(() -> lang.createContextThreadLocal0("testString"), IllegalStateException.class,
                                (e) -> assertEquals(e.getMessage(), "The set of context thread locals is frozen. " +
                                                "Context thread locals can only be created during construction of the TruffleLanguage subclass."));
            } finally {
                c0.leave();
            }

        }
    }

    @Test
    public void testCreateInstrumentContextLocalLooLate() {
        try (Context c0 = Context.create();) {
            ValidInstrument instrument = c0.getEngine().getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);

            assertFails(() -> instrument.createContextLocal0(), IllegalStateException.class, (e) -> assertEquals(e.getMessage(), "The set of context locals is frozen. " +
                            "Context locals can only be created during construction of the TruffleInstrument subclass."));
        }
    }

    @Test
    public void testCreateInstrumentContextThreadLocalLooLate() {
        try (Context c0 = Context.create();) {
            ValidInstrument instrument = c0.getEngine().getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);

            assertFails(() -> instrument.createContextLocal0(), IllegalStateException.class, (e) -> assertEquals(e.getMessage(), "The set of context locals is frozen. " +
                            "Context locals can only be created during construction of the TruffleInstrument subclass."));
        }
    }

    @Test
    public void testNullContextLocalValue() {
        contextLocalDynamicValue = null;
        try (Context c0 = Context.create()) {

            Instrument instrument = c0.getEngine().getInstruments().get(VALID_INSTRUMENT);
            assertFails(() -> instrument.lookup(ValidInstrument.class), PolyglotException.class,
                            (e) -> assertTrue(e.getMessage(), e.getMessage().contains("ContextLocalFactory.create is not allowed to return null")));

            assertFails(() -> c0.initialize(VALID_SHARED_LANGUAGE), PolyglotException.class,
                            (e) -> assertTrue(e.getMessage(), e.getMessage().contains("ContextLocalFactory.create is not allowed to return null")));

        } finally {
            contextLocalDynamicValue = "non-null"; // this is a valid value again
        }
    }

    @Test
    public void testNullContextThreadLocalValue() {
        threadLocalDynamicValue = null;
        try (Context c0 = Context.create()) {

            Instrument instrument = c0.getEngine().getInstruments().get(VALID_INSTRUMENT);
            c0.enter(); // required to initialize thread locals
            try {
                assertFails(() -> instrument.lookup(ValidInstrument.class), PolyglotException.class,
                                (e) -> assertTrue(e.getMessage(), e.getMessage().contains("ContextThreadLocalFactory.create is not allowed to return null")));

                assertFails(() -> c0.initialize(VALID_SHARED_LANGUAGE), PolyglotException.class,
                                (e) -> assertTrue(e.getMessage(), e.getMessage().contains("ContextThreadLocalFactory.create is not allowed to return null")));
            } finally {
                c0.leave();
            }
        } finally {
            threadLocalDynamicValue = "non-null"; // this is a valid value again
        }
    }

    @Test
    public void testUnstableContextLocalValue() {
        contextLocalDynamicValue = "foobar";
        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder().engine(engine).allowAllAccess(true).build()) {
                c0.getEngine().getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);
                c0.initialize(VALID_SHARED_LANGUAGE);
            }
            // change to a different type
            contextLocalDynamicValue = 42;

            assertFails(() -> Context.newBuilder().engine(engine).allowAllAccess(true).build(), PolyglotException.class,
                            (e) -> assertTrue(e.getMessage(), e.getMessage().contains(
                                            "The return context value type must be stable and exact. " +
                                                            "Expected class java.lang.String but got class java.lang.Integer ")));

            contextLocalDynamicValue = "42";

            try (Context c1 = Context.newBuilder().engine(engine).allowAllAccess(true).build()) {
                ValidInstrument instrument = c1.getEngine().getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);
                c1.enter();
                try {
                    Assert.assertEquals(contextLocalDynamicValue, instrument.localDynamic.get());
                    contextLocalDynamicValue = 42;

                    assertFails(() -> c1.initialize(VALID_SHARED_LANGUAGE), PolyglotException.class,
                                    (e) -> assertTrue(e.getMessage(), e.getMessage().contains(
                                                    "The return context value type must be stable and exact. " +
                                                                    "Expected class java.lang.String but got class java.lang.Integer ")));
                } finally {
                    c1.leave();
                }
            }
        } finally {
            contextLocalDynamicValue = "non-null"; // this is a valid value again
        }
    }

    @Test
    public void testUnstableContextThreadLocalValue() {
        threadLocalDynamicValue = "foobar";
        try (Engine engine = Engine.create()) {
            try (Context c0 = Context.newBuilder().engine(engine).allowAllAccess(true).build()) {
                c0.getEngine().getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);
                c0.initialize(VALID_SHARED_LANGUAGE);
            }
            // change to a different type
            threadLocalDynamicValue = 42;

            // context creation does not yet create thread local values
            Context c0 = Context.newBuilder().engine(engine).allowAllAccess(true).build();

            // first enter triggers error
            assertFails(() -> c0.enter(), PolyglotException.class,
                            (e) -> assertTrue(e.getMessage(), e.getMessage().contains(
                                            "The return context value type must be stable and exact. " +
                                                            "Expected class java.lang.String but got class java.lang.Integer ")));

            threadLocalDynamicValue = "42";

            try (Context c1 = Context.newBuilder().engine(engine).allowAllAccess(true).build()) {
                ValidInstrument instrument = c1.getEngine().getInstruments().get(VALID_INSTRUMENT).lookup(ValidInstrument.class);
                c1.enter();
                try {
                    Assert.assertEquals(contextLocalDynamicValue, instrument.localDynamic.get());
                    threadLocalDynamicValue = 42;

                    assertFails(() -> c1.initialize(VALID_SHARED_LANGUAGE), PolyglotException.class,
                                    (e) -> assertTrue(e.getMessage(), e.getMessage().contains(
                                                    "The return context value type must be stable and exact. " +
                                                                    "Expected class java.lang.String but got class java.lang.Integer ")));
                } finally {
                    c1.leave();
                }
            }
        } finally {
            threadLocalDynamicValue = ""; // this is a valid value again
        }
    }

    @Test
    public void testInnerContextLocals() {
        try (Context ctx = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            Assert.assertEquals(0, ctx.eval(VALID_SHARED_LANGUAGE, "").asInt());
        }
    }

    private static CountDownLatch contextLocalCreationStartedLatch;
    private static CountDownLatch contextLocalCreationLatch;

    @Test
    public void testContextLocalAccessRace() throws ExecutionException, InterruptedException {
        /*
         * In this test, context close is called on one of the two contexts before the instrument
         * created in a third thread initializes context locals for the context at the end of
         * PolyglotInstrument#ensureCreated. The context local defined by the instrument is accessed
         * in an onContextClosed event installed by the instrument, and so if the event was called,
         * we would get an assertion error. However, since the events in the context listener
         * installed by the instrument are not invoked before context locals for all contexts are
         * properly initialized, we get no error.
         */
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        CountDownLatch contextCreatedAndEnteredLatch = new CountDownLatch(2);
        CountDownLatch contextCloseLatch = new CountDownLatch(1);
        CountDownLatch contextClosedLatch = new CountDownLatch(1);
        contextLocalCreationStartedLatch = new CountDownLatch(1);
        contextLocalCreationLatch = new CountDownLatch(1);
        try (Engine engine = Engine.create()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executorService.submit(() -> {
                    try (Context ctx = Context.newBuilder().engine(engine).build()) {
                        /*
                         * Context close enters the context in the process and if it is the first
                         * enter on that thread, it initializes context locals, which would block
                         * the closing process and cause deadlock because we deliberately block the
                         * context local creation using the contextLocalCreationLatch. But if we
                         * enter and leave before creation of the instrument, the context local
                         * creation relies on the instrument calling it on all contexts, thread
                         * enter done as the part of context close does not initialize context
                         * locals because the thread was already entered.
                         */
                        ctx.enter();
                        ctx.leave();
                        contextCreatedAndEnteredLatch.countDown();
                        try {
                            contextCloseLatch.await();
                        } catch (InterruptedException ie) {
                            throw new AssertionError(ie);
                        }
                    } finally {
                        contextClosedLatch.countDown();
                    }
                }));
            }
            contextCreatedAndEnteredLatch.await();
            futures.add(executorService.submit(() -> {
                engine.getInstruments().get(CONTEXT_LOCAL_ACCESS_RACE_INSTRUMENT).lookup(ContextLocalAccessRaceInstrument.class);
            }));
            contextLocalCreationStartedLatch.await();
            contextCloseLatch.countDown();
            contextClosedLatch.await();
            contextLocalCreationLatch.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            contextLocalCreationLatch = null;
            contextLocalCreationStartedLatch = null;
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(1, TimeUnit.MINUTES));
        }
    }

    @TruffleLanguage.Registration(id = VALID_EXCLUSIVE_LANGUAGE, name = VALID_EXCLUSIVE_LANGUAGE)
    public static class ValidExclusiveLanguage extends TruffleLanguage<TruffleLanguage.Env> {

        final ContextLocal<Env> contextLocal0 = locals.createContextLocal((e) -> e);
        final ContextLocal<Env> contextLocal1 = locals.createContextLocal((e) -> e);

        final ContextThreadLocal<LanguageThreadLocalValue> contextThreadLocal0 = locals.createContextThreadLocal(LanguageThreadLocalValue::new);
        final ContextThreadLocal<LanguageThreadLocalValue> contextThreadLocal1 = locals.createContextThreadLocal(LanguageThreadLocalValue::new);

        @Override
        protected TruffleLanguage.Env createContext(TruffleLanguage.Env env) {
            return env;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            RootNode rootNode = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return 0;
                }
            };
            return rootNode.getCallTarget();
        }

        static final ContextReference<Env> CONTEXT_REF = ContextReference.create(ValidExclusiveLanguage.class);
        static final LanguageReference<ValidExclusiveLanguage> REFERENCE = LanguageReference.create(ValidExclusiveLanguage.class);

    }

    static class LanguageThreadLocalValue {

        final Env env;
        final Thread thread;

        LanguageThreadLocalValue(Env env, Thread t) {
            this.env = env;
            this.thread = t;
        }

    }

    static class InstrumentThreadLocalValue {

        final TruffleContext context;
        final Thread thread;

        InstrumentThreadLocalValue(TruffleContext context, Thread t) {
            this.context = context;
            this.thread = t;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof InstrumentThreadLocalValue)) {
                return false;
            }
            InstrumentThreadLocalValue key = (InstrumentThreadLocalValue) obj;
            return this.context == key.context && this.thread == key.thread;
        }

        @Override
        public int hashCode() {
            return Objects.hash(context, thread);
        }

    }

    @TruffleLanguage.Registration(id = VALID_SHARED_LANGUAGE, name = VALID_SHARED_LANGUAGE, contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    public static class ValidSharedLanguage extends TruffleLanguage<TruffleLanguage.Env> {

        final ContextLocal<Env> local0 = locals.createContextLocal((e) -> e);
        final ContextLocal<Env> local1 = locals.createContextLocal((e) -> e);
        final ContextLocal<Object> localDynamic = locals.createContextLocal((e) -> contextLocalDynamicValue);

        final ContextThreadLocal<LanguageThreadLocalValue> contextThreadLocal0 = locals.createContextThreadLocal(LanguageThreadLocalValue::new);
        final ContextThreadLocal<LanguageThreadLocalValue> contextThreadLocal1 = locals.createContextThreadLocal(LanguageThreadLocalValue::new);
        final ContextThreadLocal<Object> threadLocalDynamic = locals.createContextThreadLocal((c, t) -> threadLocalDynamicValue);

        @Override
        protected TruffleLanguage.Env createContext(TruffleLanguage.Env env) {
            return env;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        public ContextLocal<String> createContextLocal0(String value) {
            return locals.createContextLocal((e) -> value);
        }

        public ContextThreadLocal<String> createContextThreadLocal0(String value) {
            return locals.createContextThreadLocal((e, t) -> value);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return (new VSLRootNode(this)).getCallTarget();
        }

        static final ContextReference<Env> CONTEXT_REF = ContextReference.create(ValidSharedLanguage.class);
        static final LanguageReference<ValidSharedLanguage> REFERENCE = LanguageReference.create(ValidSharedLanguage.class);

    }

    public static class VSLRootNode extends RootNode {

        @Node.Child private VSLNode node = new VSLNode();

        public VSLRootNode(ValidSharedLanguage language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame virtualFrame) {
            return node.execute(virtualFrame);
        }

    }

    public static class VSLNode extends Node {

        private final Source source = Source.newBuilder(VALID_EXCLUSIVE_LANGUAGE, "", "").build();

        private final IndirectCallNode callNode = IndirectCallNode.create();

        @SuppressWarnings("unused")
        public Object execute(VirtualFrame virtualFrame) {
            Env outerLanguageOuterEnv = ValidSharedLanguage.CONTEXT_REF.get(this);
            TruffleContext innerTruffleContext = createInnerContext(outerLanguageOuterEnv);
            Object outerTruffleContext = innerTruffleContext.enter(this);
            try {
                Env outerLanguageInnerEnv = ValidSharedLanguage.CONTEXT_REF.get(null);
                createInstrument(outerLanguageInnerEnv);
                CallTarget callTarget = parse(outerLanguageInnerEnv);
                return callNode.call(callTarget);
            } finally {
                innerTruffleContext.leave(this, outerTruffleContext);
                innerTruffleContext.close();
            }
        }

        @CompilerDirectives.TruffleBoundary
        private CallTarget parse(Env env) {
            return env.parsePublic(source);
        }

        @CompilerDirectives.TruffleBoundary
        private static TruffleContext createInnerContext(Env env) {
            return env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).build();
        }

        @CompilerDirectives.TruffleBoundary
        private static void createInstrument(Env env) {
            InstrumentInfo instrumentInfo = env.getInstruments().get(VALID_INSTRUMENT);
            env.lookup(instrumentInfo, ValidInstrument.class);
        }

    }

    @TruffleInstrument.Registration(id = VALID_INSTRUMENT, name = VALID_INSTRUMENT, services = ValidInstrument.class)
    public static class ValidInstrument extends TruffleInstrument {

        final ContextLocal<TruffleContext> local0 = locals.createContextLocal(this::createInstrumentContextLocal);
        final ContextLocal<Object> localDynamic = locals.createContextLocal((e) -> contextLocalDynamicValue);

        final ContextThreadLocal<InstrumentThreadLocalValue> threadLocal0 = locals.createContextThreadLocal(this::newInstrumentThreadLocal);
        final ContextThreadLocal<Object> threadLocalDynamic = locals.createContextThreadLocal((c, t) -> threadLocalDynamicValue);

        private Env environment;

        private final ConcurrentHashMap<InstrumentThreadLocalValue, String> seenValues = new ConcurrentHashMap<>();

        @Override
        protected void onCreate(Env env) {
            this.environment = env;
            env.registerService(this);
            env.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
                @Override
                public void onEnterThread(TruffleContext c) {
                    if (threadLocalDynamicValue != null && contextLocalDynamicValue != null) {
                        local0.get(c);
                        threadLocal0.get(c);
                    }
                }

                @Override
                public void onLeaveThread(TruffleContext c) {
                    if (threadLocalDynamicValue != null && contextLocalDynamicValue != null) {
                        local0.get(c);
                        threadLocal0.get(c);
                    }
                }
            });
        }

        InstrumentThreadLocalValue newInstrumentThreadLocal(TruffleContext context, Thread t) {
            // must be guaranteed that onCreate is called before any context thread local inits
            assertNotNull(environment);

            InstrumentThreadLocalValue local = new InstrumentThreadLocalValue(context, t);

            if (seenValues.containsKey(local)) {
                throw new AssertionError("duplicate thread local factory invocation");
            }
            seenValues.put(local, "");
            return local;
        }

        TruffleContext createInstrumentContextLocal(TruffleContext context) {
            // must be guaranteed that onCreate is called before any context local inits
            assertNotNull(environment);
            return context;
        }

        public ContextLocal<String> createContextLocal0() {
            return locals.createContextLocal((e) -> {
                throw new AssertionError();
            });
        }

        public ContextThreadLocal<String> createContextThreadLocal0() {
            return locals.createContextThreadLocal((e, t) -> {
                throw new AssertionError();
            });
        }
    }

    @TruffleLanguage.Registration(id = INVALID_CONTEXT_LOCAL, name = INVALID_CONTEXT_LOCAL)
    public static class InvalidLanguageContextLocal extends TruffleLanguage<TruffleLanguage.Env> {

        static int effect = 0;

        final ContextLocal<Env> local0 = locals.createContextLocal((e) -> e);
        final ContextLocal<Env> local1 = (effect++) % 2 == 0 ? locals.createContextLocal((e) -> e) : null;

        @Override
        protected TruffleLanguage.Env createContext(TruffleLanguage.Env env) {
            return env;
        }

    }

    @TruffleLanguage.Registration(id = INVALID_CONTEXT_THREAD_LOCAL, name = INVALID_CONTEXT_THREAD_LOCAL)
    public static class InvalidLanguageContextThreadLocal extends TruffleLanguage<TruffleLanguage.Env> {

        static int effect = 0;

        final ContextThreadLocal<Env> local0 = locals.createContextThreadLocal((e, t) -> e);
        final ContextThreadLocal<Env> local1 = (effect++) % 2 == 0 ? locals.createContextThreadLocal((e, t) -> e) : null;

        @Override
        protected TruffleLanguage.Env createContext(TruffleLanguage.Env env) {
            return env;
        }

    }

    @TruffleInstrument.Registration(id = "example", name = "Example Instrument")
    public static class ExampleInstrument extends TruffleInstrument {

        final ContextThreadLocal<ExampleLocal> local = locals.createContextThreadLocal(ExampleLocal::new);

        @Override
        protected void onCreate(Env env) {
            ExecutionEventListener listener = new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame frame) {
                    ExampleLocal value = local.get();
                    // use thread local value;
                    assert value.thread.get() == Thread.currentThread();
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }
            };

            env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.ANY,
                            listener);
        }

        static class ExampleLocal {

            final TruffleContext context;
            final WeakReference<Thread> thread;

            ExampleLocal(TruffleContext context, Thread thread) {
                this.context = context;
                this.thread = new WeakReference<>(thread);
            }

        }

    }

    @TruffleInstrument.Registration(id = CONTEXT_LOCAL_ACCESS_RACE_INSTRUMENT, services = ContextLocalAccessRaceInstrument.class)
    public static class ContextLocalAccessRaceInstrument extends TruffleInstrument {

        final ContextLocal<CLARIContextLocal> local = locals.createContextLocal(CLARIContextLocal::new);

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachContextsListener(new ContextsListener() {
                @Override
                public void onContextCreated(TruffleContext context) {

                }

                @Override
                public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {

                }

                @Override
                public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {

                }

                @Override
                public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {

                }

                @Override
                public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {

                }

                @Override
                public void onContextClosed(TruffleContext context) {
                    local.get(context);
                }
            }, false);
            env.registerService(this);
        }

        static class CLARIContextLocal {
            @SuppressWarnings("unused")
            CLARIContextLocal(TruffleContext truffleContext) {
                contextLocalCreationStartedLatch.countDown();
                try {
                    contextLocalCreationLatch.await();
                } catch (InterruptedException ie) {
                    throw new AssertionError(ie);
                }
            }
        }

    }

}
