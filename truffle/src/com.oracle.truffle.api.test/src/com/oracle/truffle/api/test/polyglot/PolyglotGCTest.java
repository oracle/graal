/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import org.graalvm.collections.Pair;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PolyglotGCTest {

    private static final AtomicBoolean languageInnerContextDisposed = new AtomicBoolean();
    private static final boolean CONCURRENT_GC_LOAD = Boolean.getBoolean(PolyglotGCTest.class.getSimpleName() + ".ConcurrentGCLoad");
    private static final Class<?> ENTERPRISE_POLYGLOT_CLASS;
    static {
        Class<?> clz;
        try {
            clz = Class.forName("com.oracle.truffle.polyglot.enterprise.EnterprisePolyglotImpl");
        } catch (ClassNotFoundException cnf) {
            clz = null;
        }
        ENTERPRISE_POLYGLOT_CLASS = clz;
    }

    private static volatile Thread gcLoadThread;

    @BeforeClass
    public static void setUpClass() {
        if (SubprocessTestUtils.isSubprocess() && CONCURRENT_GC_LOAD) {
            gcLoadThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    GCUtils.generateGcPressure(0.7);
                }
            });
            gcLoadThread.setDaemon(true);
            gcLoadThread.start();
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (SubprocessTestUtils.isSubprocess() && CONCURRENT_GC_LOAD) {
            gcLoadThread.interrupt();
            try {
                gcLoadThread.join(5_000);
            } catch (InterruptedException ie) {
                // pass
            }
        }
    }

    private static volatile TestLogHandler testLogHandler;

    @Before
    public void setUp() {
        testLogHandler = new TestLogHandler();
        languageInnerContextDisposed.set(false);
    }

    Engine.Builder newEngineBuilder() {
        return Engine.newBuilder().logHandler(testLogHandler).allowExperimentalOptions(true).option(PolyglotGCInstrument.ID + ".Start", "true");
    }

    Context.Builder newContextBuilder() {
        return Context.newBuilder().logHandler(testLogHandler).allowExperimentalOptions(true).option(PolyglotGCInstrument.ID + ".Start", "true");
    }

    @Test
    public void testContextCollected() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            AbstractExecutableTestLanguage.execute(context, ContextGcLanguage.class);
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            assertContextGc("Context without running threads should be collected", contextRef);
        });
    }

    @Registration
    static final class ContextGcLanguage extends AbstractPolyglotGcLanguage {
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return true;
        }
    }

    @Test
    public void testBoundEngineCollected() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            AbstractExecutableTestLanguage.execute(context, ContextGcLanguage.class);
            Isolate<?> isolate = getEnginesIsolate(context.getEngine());
            Reference<Engine> engineRef = new WeakReference<>(context.getEngine());
            context = null;
            assertEngineGc("Engine without running threads should be collected", engineRef, isolate);
        });
    }

    @Test
    public void testEngineCollected() throws Exception {
        runInSubprocess(() -> {
            Engine engine = newEngineBuilder().build();
            Isolate<?> isolate = getEnginesIsolate(engine);
            Reference<Engine> engineRef = new WeakReference<>(engine);
            engine = null;
            assertEngineGc("Engine without running threads should be collected", engineRef, isolate);
        });
    }

    @Test
    public void testEngineNotCollectedForReachableContext() throws Exception {
        runInSubprocess(() -> {
            try (Context context = newContextBuilder().build()) {
                AbstractExecutableTestLanguage.execute(context, ContextGcLanguage.class);
                Reference<Engine> engineRef = new WeakReference<>(context.getEngine());
                GCUtils.assertNotGc("Engine for reachable context must not be collected", engineRef);
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testContextCollectedForReachableEngine() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            try (Engine engine = context.getEngine()) {
                AbstractExecutableTestLanguage.execute(context, ContextGcLanguage.class);
                Reference<Context> contextRef = new WeakReference<>(context);
                context = null;
                assertContextGc("Context with reachable engine should be collected", contextRef);
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testContextCollectedForReachableSharedEngine() throws Exception {
        runInSubprocess(() -> {
            Engine engine = newEngineBuilder().build();
            Context context = Context.newBuilder().logHandler(testLogHandler).engine(engine).build();
            AbstractExecutableTestLanguage.execute(context, ContextGcLanguage.class);
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            assertContextGc("Context with reachable engine should be collected", contextRef);
        });
    }

    @Test
    public void testExplicitEnter() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            context.enter();
            // We need to initialize PolyglotGCLanguage to later verify that it was correctly
            // disposed
            AbstractExecutableTestLanguage.execute(context, ContextGcLanguage.class);
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            GCUtils.assertNotGc("Entered context must not be collected", contextRef);
            Context.getCurrent();
            context = contextRef.get();
            context.leave();
            context = null;
            assertContextGc("Context must not be collected", contextRef);
        });
    }

    @Test
    public void testPolyglotBindings() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            /*
             * We need to initialize PolyglotGCLanguage to later verify that it was correctly
             * disposed.
             */
            AbstractExecutableTestLanguage.execute(context, ContextGcLanguage.class);
            Value polyglotBindings = context.getPolyglotBindings();
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            GCUtils.assertNotGc("Context with active polyglot bindings must not be collected", contextRef);
            Reference.reachabilityFence(polyglotBindings);
            polyglotBindings = null;
            assertContextGc("Should be collected", contextRef);
        });
    }

    @Test
    public void testContextWithReachableValueNotCollected() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            Value object = AbstractExecutableTestLanguage.execute(context, ValueGcLanguage.class);
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            GCUtils.assertNotGc("Context with existing Value must not be collected", contextRef);
            Reference.reachabilityFence(object);
        });
    }

    @Registration
    static final class ValueGcLanguage extends AbstractPolyglotGcLanguage {
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return new InteropObject();
        }
    }

    @Test
    public void testPolyglotList() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            Value value = AbstractExecutableTestLanguage.execute(context, ValueGcLanguage.class);
            List<?> list = value.as(List.class);
            Reference<Context> contextReference = new WeakReference<>(context);
            value = null;
            context = null;
            GCUtils.assertNotGc("Context with existing polyglot list should not be collected", contextReference);
            Reference.reachabilityFence(list);
        });
    }

    @Test
    public void testContextWithReachablePolyglotExceptionNotCollected() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            PolyglotException polyglotException = null;
            try {
                AbstractExecutableTestLanguage.execute(context, ExceptionGcLanguage.class);
                Assert.fail("Should not reach here");
            } catch (PolyglotException pe) {
                assertEquals("Test Exception", pe.getMessage());
                polyglotException = pe;
            }
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            GCUtils.assertNotGc("Context with existing PolyglotException must not be collected", contextRef);
            Reference.reachabilityFence(polyglotException);
        });
    }

    @Registration
    static final class ExceptionGcLanguage extends AbstractPolyglotGcLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            throw new TruffleExceptionImpl("Test Exception");
        }
    }

    @Test
    public void testContextWithPolyglotThreadNotCollected() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().allowCreateThread(true).build();
            Source src;
            try {
                src = Source.newBuilder(TestUtils.getDefaultLanguageId(PolyglotThreadGcLanguage.class), "", "test").cached(false).build();
            } catch (IOException ioe) {
                throw new AssertionError("Source from literal should not throw IOException", ioe);
            }
            long threadId = AbstractExecutableTestLanguage.evalTestLanguage(context, PolyglotThreadGcLanguage.class, src, "start-polyglot-thread").asLong();
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            GCUtils.assertNotGc("Context with active polyglot thread must not be collected", contextRef);
            context = contextRef.get();
            assertNotNull(context);
            AbstractExecutableTestLanguage.evalTestLanguage(context, PolyglotThreadGcLanguage.class, src, "stop-polyglot-thread", threadId);
            context = null;
            assertContextGc("Context with stopped polyglot thread should be collected", contextRef);
        });
    }

    @Test
    public void testContextWithUnstartedPolyglotThreadCollected() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().allowCreateThread(true).build();
            AbstractExecutableTestLanguage.execute(context, PolyglotThreadGcLanguage.class, "create-unstarted-polyglot-thread").asLong();
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            GCUtils.assertGc("Context with unstarted polyglot thread should be collected", contextRef);
        });
    }

    @Registration
    static final class PolyglotThreadGcLanguage extends AbstractPolyglotGcLanguage {

        private final ContextLocal<PolyglotThreads> threads = locals.createContextLocal((e) -> new PolyglotThreads());

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String command = (String) contextArguments[0];
            return switch (command) {
                case "create-unstarted-polyglot-thread" -> createPolyglotThread(env).getLeft();
                case "start-polyglot-thread" -> startPolyglotThread(env);
                case "stop-polyglot-thread" -> stopPolyglotThread((long) contextArguments[1]);
                default -> throw new IllegalArgumentException(command);
            };
        }

        private Pair<Long, Thread> createPolyglotThread(Env env) {
            Task task = new Task(env);
            Thread thread = env.newTruffleThreadBuilder(task).build();
            long id = threads.get().registerPolyglotThread(task, thread);
            return Pair.create(id, thread);
        }

        private long startPolyglotThread(Env env) {
            Pair<Long, Thread> pair = createPolyglotThread(env);
            pair.getRight().start();
            return pair.getLeft();
        }

        private boolean stopPolyglotThread(long id) {
            Pair<Task, Thread> p = threads.get().unregisterPolyglotThread(id);
            if (p != null) {
                Throwable exception = stopThread(p);
                if (exception != null) {
                    throw new RuntimeException(exception);
                }
                return true;
            }
            return false;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected void finalizeContext(ExecutableContext context) {
            super.finalizeContext(context);
            Throwable exception = stopThreads(threads.get().polyglotThreads());
            if (exception != null) {
                throw new RuntimeException(exception);
            }
        }

        private static final class PolyglotThreads {

            private final AtomicLong threadIdCounter = new AtomicLong();
            private final Map<Long, Pair<Task, Thread>> createdPolyglotThreads = new ConcurrentHashMap<>();

            long registerPolyglotThread(Task task, Thread thread) {
                long id = threadIdCounter.incrementAndGet();
                createdPolyglotThreads.put(id, Pair.create(task, thread));
                return id;
            }

            Pair<Task, Thread> unregisterPolyglotThread(long id) {
                return createdPolyglotThreads.remove(id);
            }

            Collection<Pair<Task, Thread>> polyglotThreads() {
                return createdPolyglotThreads.values();
            }
        }
    }

    @Test
    public void testContextWithSystemThreadCollected() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            AbstractExecutableTestLanguage.execute(context, SystemThreadGcLanguage.class);
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            assertContextGc("Context with active system thread should be collected", contextRef);
        });
    }

    @Registration
    static final class SystemThreadGcLanguage extends AbstractPolyglotGcLanguage {

        private final ContextLocal<SystemThreads> threads = locals.createContextLocal((e) -> new SystemThreads());

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Task task = new Task(env);
            Thread thread = env.createSystemThread(task);
            long id = threads.get().registerSystemThread(task, thread);
            thread.start();
            return id;
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            super.disposeContext(context);
            Throwable exception = stopThreads(threads.get().systemThreads());
            if (exception != null) {
                throw new RuntimeException(exception);
            }
        }

        private static final class SystemThreads {

            private final AtomicLong threadIdCounter = new AtomicLong();
            private final Map<Long, Pair<Task, Thread>> createdSystemThreads = new ConcurrentHashMap<>();

            long registerSystemThread(Task task, Thread thread) {
                long id = threadIdCounter.incrementAndGet();
                createdSystemThreads.put(id, Pair.create(task, thread));
                return id;
            }

            Collection<Pair<Task, Thread>> systemThreads() {
                return createdSystemThreads.values();
            }
        }
    }

    @Test
    public void testEngineWithSystemThreadCollected() throws Exception {
        runInSubprocess(() -> {
            Engine engine = newEngineBuilder().build();
            Isolate<?> isolate = getEnginesIsolate(engine);
            WeakReference<Engine> engineRef = new WeakReference<>(engine);
            engine = null;
            assertEngineGc("Engine with active system thread should be collected", engineRef, isolate);
        });
    }

    @Test
    public void testEngineWithReachableLanguageNotCollected() throws Exception {
        runInSubprocess(() -> {
            Engine engine = newEngineBuilder().build();
            Language language = engine.getLanguages().get(TestUtils.getDefaultLanguageId(ContextGcLanguage.class));
            assertNotNull(language);
            Reference<Engine> engineRef = new WeakReference<>(engine);
            engine = null;
            GCUtils.assertNotGc("Engine with existing language must not be collected", engineRef);
            Reference.reachabilityFence(language);
        });
    }

    @Test
    public void testEngineWithReachableInstrumentNotCollected() throws Exception {
        runInSubprocess(() -> {
            Engine engine = newEngineBuilder().build();
            Instrument instrument = engine.getInstruments().get(PolyglotGCInstrument.ID);
            assertNotNull(instrument);
            Reference<Engine> engineRef = new WeakReference<>(engine);
            engine = null;
            GCUtils.assertNotGc("Engine with existing instrument must not be collected", engineRef);
            Reference.reachabilityFence(instrument);
        });
    }

    @Test
    public void testEngineWithReachableExecutionListenerNotCollected() throws Exception {
        runInSubprocess(() -> {
            Engine engine = newEngineBuilder().build();
            ExecutionListener listener = ExecutionListener.newBuilder().roots(true).onEnter((e) -> {
            }).attach(engine);
            Reference<Engine> engineRef = new WeakReference<>(engine);
            engine = null;
            GCUtils.assertNotGc("Engine with existing execution listener must not be collected", engineRef);
            Reference.reachabilityFence(listener);
        });
    }

    /**
     * This test simulates languages that have SPI classes (language implementation, context) held
     * from an unmanaged memory. The reference is freed during execution of
     * TruffleLanguage#onDispose.
     */
    @Test
    public void testContextCollectedWhenSPIReachable() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            AbstractExecutableTestLanguage.execute(context, SPIReachableGcLanguage.class);
            Reference<Context> contextRef = new WeakReference<>(context);
            context = null;
            assertContextGc("Context with reachable SPI should be collected", contextRef);
        });
    }

    @Registration
    static final class SPIReachableGcLanguage extends AbstractPolyglotGcLanguage {

        /*
         * Used to simulate JNI global references to language SPI classes.
         */
        private static final List<Object> globalStrongReferences = new ArrayList<>();

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            globalStrongReferences.add(env);
            globalStrongReferences.add(env.getContext());
            return true;
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            super.disposeContext(context);
            globalStrongReferences.clear();
        }
    }

    @Test
    public void testEngineAPI() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            Reference<Engine> engineReference = new WeakReference<>(context.getEngine());
            context.enter();
            Engine apiEngine;
            try {
                apiEngine = Context.getCurrent().getEngine();
            } finally {
                context.leave();
            }
            context = null;
            GCUtils.assertNotGc("Engine with existing API context should not be collected", engineReference);
            Isolate<?> isolate = getEnginesIsolate(apiEngine);
            apiEngine = null;
            assertEngineGc("Should be collected", engineReference, isolate);
        });
    }

    @Test
    public void testReachableInnerContextEmbedder() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            AbstractExecutableTestLanguage.execute(context, ReachableInnerContextGcLanguage.class);
            Reference<Context> contextReference = new WeakReference<>(context);
            context = null;
            assertContextGc("Context with existing inner TruffleContext should be collected.", contextReference);
        });
    }

    @Registration
    static final class ReachableInnerContextGcLanguage extends AbstractPolyglotGcLanguage {

        private final ContextLocal<RuntimeData> runtimeData = locals.createContextLocal((e) -> new RuntimeData());

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext truffleContext = env.newInnerContextBuilder().build();
            truffleContext.initializePublic(null, TestUtils.getDefaultLanguageId(ReachableInnerContextGcLanguage.class));
            runtimeData.get().innerContext = truffleContext;
            return true;
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            super.disposeContext(context);
            runtimeData.get().innerContext = null;
        }

        private static final class RuntimeData {
            @SuppressWarnings("unused") volatile TruffleContext innerContext;
        }
    }

    @Test
    public void testUnreachableInnerContextEmbedder() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            AbstractExecutableTestLanguage.execute(context, UnreachableInnerContextGcLanguage.class);
            Reference<Context> contextReference = new WeakReference<>(context);
            context = null;
            assertContextGc("Context with unreachable inner TruffleContext should be collected.", contextReference);
        });
    }

    @Registration
    static final class UnreachableInnerContextGcLanguage extends AbstractPolyglotGcLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext truffleContext = env.newInnerContextBuilder().build();
            truffleContext.initializePublic(null, TestUtils.getDefaultLanguageId(UnreachableInnerContextGcLanguage.class));
            return true;
        }
    }

    @Test
    public void testInnerContextSPI() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().build();
            AbstractExecutableTestLanguage.execute(context, UnreachableInnerContextGcLanguage2.class);
            Reference.reachabilityFence(context);
        });
    }

    @Registration
    static final class UnreachableInnerContextGcLanguage2 extends AbstractPolyglotGcLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext truffleContext = env.newInnerContextBuilder().build();
            truffleContext.initializePublic(null, TestUtils.getDefaultLanguageId(UnreachableInnerContextGcLanguage.class));
            Reference<TruffleContext> innerContextReference = new WeakReference<>(truffleContext);
            truffleContext = null;
            assertInnerContextGc("Unused inner context should be collectd", innerContextReference);
            return true;
        }
    }

    @Test
    public void testInterruptedContext() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().allowCreateThread(true).build();
            AbstractExecutableTestLanguage.execute(context, WaitingThreadLanguage.class);
            try {
                context.interrupt(Duration.ZERO);
            } catch (TimeoutException te) {
                throw new AssertionError("Context did not finish", te);
            }
            Reference<Context> contextReference = new WeakReference<>(context);
            context = null;
            assertContextGc("Interrupted Context should be collected.", contextReference);
        });
    }

    @Registration
    static final class WaitingThreadLanguage extends AbstractPolyglotGcLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            WaitNode waitNode = new WaitNode(started);
            Thread t = env.newTruffleThreadBuilder(waitNode::execute).build();
            t.start();
            started.await();
            return true;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        static final class WaitNode extends Node {

            private final CountDownLatch started;

            WaitNode(CountDownLatch started) {
                this.started = started;
            }

            @TruffleBoundary
            public void execute() {
                started.countDown();
                while (true) {
                    // Wait for context interrupt or cancel
                    try {
                        TruffleSafepoint.poll(this);
                        Thread.sleep(1_000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }
    }

    @Test
    public void testCancelledContext() throws Exception {
        runInSubprocess(() -> {
            Context context = newContextBuilder().allowCreateThread(true).build();
            AbstractExecutableTestLanguage.execute(context, WaitingThreadLanguage.class);
            context.close(true);
            Reference<Context> contextReference = new WeakReference<>(context);
            context = null;
            assertContextGc("Cancelled Context should be collected.", contextReference);
        });
    }

    @Test
    public void testPreliminaryContextCollection() throws Exception {
        // GR-57223: Test is very slow on polyglot isolate
        TruffleTestAssumptions.assumeWeakEncapsulation();
        int numExecutionThreads = 20;
        int numRepeats = 500;
        ExecutorService threadPool = Executors.newFixedThreadPool(numExecutionThreads);
        try {
            for (int i = 0; i < numRepeats; i++) {
                testPreliminaryContextCollectionImpl(numExecutionThreads, threadPool);
            }
        } finally {
            threadPool.shutdown();
            assertTrue(threadPool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /*
     * Based on {@code SourceListenerTest#testMultiThreadedSourceBindings}.
     */
    private static void testPreliminaryContextCollectionImpl(int numExecutionThreads, ExecutorService threadPool) throws InterruptedException, ExecutionException {
        Runnable[] executionRunnables = new Runnable[numExecutionThreads];
        for (int i = 0; i < numExecutionThreads; i++) {
            executionRunnables[i] = new Runnable() {
                @Override
                public void run() {
                    Context context = Context.newBuilder().logHandler(testLogHandler).build();
                    String code = "";
                    Source source = Source.newBuilder(TestUtils.getDefaultLanguageId(ValueGcLanguage.class), code, "test").buildLiteral();
                    context.eval(source); // Without reachability fence context is collected before
                                          // the eval call
                }
            };
        }
        int numExec1 = numExecutionThreads / 4;
        for (int i = 0; i < numExec1; i++) {
            threadPool.submit(executionRunnables[i]).get();
        }
        List<Future<?>> futures = new ArrayList<>();
        for (int i = numExec1; i < numExecutionThreads; i++) {
            futures.add(threadPool.submit(executionRunnables[i]));
        }
        for (Future<?> f : futures) {
            f.get();
        }
    }

    @Test
    public void testPreliminaryContextCollection2() throws Exception {
        // GR-57223: Test is very slow on polyglot isolate
        TruffleTestAssumptions.assumeWeakEncapsulation();
        int numExecutionThreads = 20;
        int numRepeats = 500;
        ExecutorService threadPool = Executors.newFixedThreadPool(numExecutionThreads);
        try {
            for (int i = 0; i < numRepeats; i++) {
                testPreliminaryContextCollection2Impl(numExecutionThreads, threadPool);
            }
        } finally {
            threadPool.shutdown();
            assertTrue(threadPool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /*
     * Based on {@code SourceListenerTest#testMultiThreadedSourceBindings}.
     */
    private static void testPreliminaryContextCollection2Impl(int numExecutionThreads, ExecutorService threadPool) throws InterruptedException, ExecutionException {
        Runnable[] executionRunnables = new Runnable[numExecutionThreads];
        for (int i = 0; i < numExecutionThreads; i++) {
            executionRunnables[i] = new Runnable() {
                @Override
                public void run() {
                    Context threadContext = Context.newBuilder().logHandler(testLogHandler).build();
                    String code = "";
                    Source source = Source.newBuilder(TestUtils.getDefaultLanguageId(ValueGcLanguage.class), code, "test").buildLiteral();
                    Value fnc = threadContext.parse(source);
                    fnc.execute();  // Without reachability fence fnc is collected before the
                    // execute call
                }
            };
        }
        int numExec1 = numExecutionThreads / 4;
        for (int i = 0; i < numExec1; i++) {
            threadPool.submit(executionRunnables[i]).get();
        }
        List<Future<?>> futures = new ArrayList<>();
        for (int i = numExec1; i < numExecutionThreads; i++) {
            futures.add(threadPool.submit(executionRunnables[i]));
        }
        for (Future<?> f : futures) {
            f.get();
        }
    }

    @Test
    public void testGR59795() throws Exception {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        runInSubprocess(() -> {
            List<Context> contexts = new ArrayList<>(100);
            // Create top-level contexts
            for (int i = 0; i < 100; i++) {
                contexts.add(newContextBuilder().build());
            }

            // In each top-level context create a strongly reachable inner context
            for (Context context : contexts) {
                AbstractExecutableTestLanguage.execute(context, TestGR59795Language.class);
            }

            // Make the inner context unreachable and assert that are collected
            List<? extends Reference<TruffleContext>> innerContextRefs = TestGR59795Language.clearInnerContexts();
            List<Pair<String, Reference<?>>> msgRefPairs = new ArrayList<>(innerContextRefs.size());
            for (Reference<?> reference : innerContextRefs) {
                msgRefPairs.add(Pair.create("Inner context must be collected", reference));
            }
            GCUtils.assertGc(msgRefPairs);

            // Dispose top-level contexts in parallel
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(20);
            CompletionService<Boolean> todo = new ExecutorCompletionService<>(executor);
            Semaphore threadsStarted = new Semaphore(-1 * executor.getMaximumPoolSize());
            CountDownLatch runClose = new CountDownLatch(1);
            for (Context context : contexts) {
                todo.submit(() -> {
                    threadsStarted.release();
                    runClose.await();
                    context.close();
                    return true;
                });
            }
            threadsStarted.release();
            try {
                threadsStarted.acquire();
            } catch (InterruptedException ie) {
                throw new AssertionError(ie);
            }
            runClose.countDown();
            for (int i = 0; i < contexts.size(); i++) {
                try {
                    todo.take();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
            executor.shutdown();
        });
    }

    @Registration
    static final class TestGR59795Language extends AbstractPolyglotGcLanguage {

        private static final List<TruffleContext> innerContexts = new ArrayList<>();

        static List<? extends Reference<TruffleContext>> clearInnerContexts() {
            List<? extends Reference<TruffleContext>> res = TestGR59795Language.innerContexts.stream().map(WeakReference::new).toList();
            TestGR59795Language.innerContexts.clear();
            return res;
        }

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext innerContext = env.newInnerContextBuilder().build();
            innerContext.initializePublic(node, TestUtils.getDefaultLanguageId(TestGR59795Language.class));
            innerContexts.add(innerContext);
            return true;
        }
    }

    @SuppressWarnings("try")
    private static void assertContextGc(String message, Reference<Context> contextRef) {
        GCUtils.assertGc(message, contextRef);
        // Process reference queue by creating a new Context
        try (Context context = Context.create()) {
            assertTrue("TruffleLanguage#disposeContext must be called for language in garbage collected Context.", testLogHandler.isLanguageDisposed());
        }
    }

    @SuppressWarnings("try")
    private static void assertEngineGc(String message, Reference<Engine> engineRef, Isolate<?> isolate) {
        GCUtils.assertGc(message, engineRef);
        // Process reference queue by creating a new Engine
        try (Engine engine = Engine.create()) {
            assertTrue("TruffleInstrument#onDispose must be called for instrument in garbage collected Engine.", testLogHandler.isInstrumentDisposed());
        }
        if (TruffleTestAssumptions.isIsolateEncapsulation()) {
            // Verify that the isolate has been disposed.
            assertTrue("Isolated must be disposed in garbage collected Engine.", isolate.isDisposed());
        }
    }

    static Isolate<?> getEnginesIsolate(Engine engine) {
        if (TruffleTestAssumptions.isIsolateEncapsulation()) {
            assertNotNull(ENTERPRISE_POLYGLOT_CLASS.getSimpleName() + " must be on classpath/module-path", ENTERPRISE_POLYGLOT_CLASS);
            return (Isolate<?>) ReflectionUtils.invokeStatic(ENTERPRISE_POLYGLOT_CLASS, "getIsolate", new Class<?>[]{Object.class}, engine);
        }
        return null;
    }

    @SuppressWarnings("try")
    private static void assertInnerContextGc(String message, Reference<TruffleContext> contextRef) {
        GCUtils.assertGc(message, contextRef);
        boolean disposed;
        try (Context context = Context.create()) {
            disposed = languageInnerContextDisposed.get();
        }
        assertTrue("TruffleLanguage#disposeContext must be called for language in garbage collected TruffleContext.", disposed);
    }

    private static void runInSubprocess(Runnable r) throws IOException, InterruptedException {
        if (ImageInfo.inImageRuntimeCode()) {
            r.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotGCTest.class, r).run();
        }
    }

    private static Throwable stopThreads(Iterable<Pair<Task, Thread>> threads) {
        Throwable exception = null;
        for (Pair<Task, Thread> p : threads) {
            exception = mergeExceptions(exception, stopThread(p));
        }
        return exception;
    }

    private static Throwable stopThread(Pair<Task, Thread> p) {
        p.getLeft().done.countDown();
        Throwable exception = null;
        try {
            p.getRight().join();
        } catch (InterruptedException ie) {
            exception = ie;
        }
        return mergeExceptions(exception, p.getLeft().exception);
    }

    private abstract static class AbstractPolyglotGcLanguage extends AbstractExecutableTestLanguage {

        @Override
        protected void disposeContext(ExecutableContext context) {
            boolean isInnerContext = context.env.getContext().getParent() != null;
            if (isInnerContext) {
                languageInnerContextDisposed.set(true);
            } else {
                context.env.getLogger(AbstractPolyglotGcLanguage.class).info(TestLogHandler.LANGUAGE_DISPOSE_TOKEN);
            }
        }
    }

    @TruffleInstrument.Registration(id = PolyglotGCInstrument.ID, name = PolyglotGCInstrument.ID, version = PolyglotGCInstrument.VERSION)
    public static final class PolyglotGCInstrument extends TruffleInstrument {

        static final String ID = "PolyglotGCInstrument";
        static final String VERSION = "1.0.0";

        @Option(help = "Start system thread", category = OptionCategory.INTERNAL) public static final OptionKey<Boolean> Start = new OptionKey<>(false);

        private volatile Pair<Task, Thread> systemThread;

        @Override
        protected void onCreate(Env env) {
            if (env.getOptions().get(Start)) {
                Task task = new Task(env);
                Thread thread = env.createSystemThread(task);
                systemThread = Pair.create(task, thread);
                thread.start();
            }
        }

        @Override
        protected void onDispose(Env env) {
            Pair<Task, Thread> p = systemThread;
            if (p != null) {
                Throwable exception = stopThread(p);
                if (exception != null) {
                    throw new RuntimeException(exception);
                }
            }
            env.getLogger(PolyglotGCInstrument.class).info(TestLogHandler.INSTRUMENT_DISPOSED_TOKEN);
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new PolyglotGCInstrumentOptionDescriptors();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static final class InteropObject implements TruffleObject {

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(boolean includeInternals) {
            return this;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return 0;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return false;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(0);
        }
    }

    @SuppressWarnings("serial")
    private static final class TruffleExceptionImpl extends AbstractTruffleException {
        TruffleExceptionImpl(String message) {
            super(message);
        }
    }

    private static final class Task implements Runnable {

        final CountDownLatch done = new CountDownLatch(1);
        volatile Throwable exception;
        private final Object[] reachableObjects;

        Task(Object... reachableObjects) {
            this.reachableObjects = reachableObjects;
        }

        @Override
        public void run() {
            try {
                done.await();
            } catch (InterruptedException ie) {
                exception = ie;
            }
            Reference.reachabilityFence(reachableObjects);
        }
    }

    private static Throwable mergeExceptions(Throwable e1, Throwable e2) {
        if (e1 != null) {
            if (e2 != null) {
                e1.addSuppressed(e2);
            }
            return e1;
        } else {
            return e2;
        }
    }

    /**
     * To ensure that the test works with an isolated heap, we avoid using fields to track the
     * disposal of language and instrument instances. This is because the isolate might be disposed
     * before we attempt to access these fields. Instead, we use
     * {@link com.oracle.truffle.api.TruffleLogger} to notify the test about the disposal events for
     * language and instrument instances.
     */
    private static final class TestLogHandler extends Handler {

        static final String LANGUAGE_DISPOSE_TOKEN = "_language_disposed";
        static final String INSTRUMENT_DISPOSED_TOKEN = "_instrument_disposed";

        private volatile boolean languageDisposed;
        private volatile boolean instrumentDisposed;

        @Override
        public void publish(LogRecord record) {
            switch (record.getMessage()) {
                case LANGUAGE_DISPOSE_TOKEN -> languageDisposed = true;
                case INSTRUMENT_DISPOSED_TOKEN -> instrumentDisposed = true;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        boolean isLanguageDisposed() {
            return languageDisposed;
        }

        boolean isInstrumentDisposed() {
            return instrumentDisposed;
        }
    }
}
