/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static com.oracle.truffle.tck.tests.ValueAssert.assertValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.LanguageSPITest.ServiceTestLanguage.LanguageSPITestLanguageService1;
import com.oracle.truffle.api.test.polyglot.LanguageSPITest.ServiceTestLanguage.LanguageSPITestLanguageService2;
import com.oracle.truffle.api.test.polyglot.LanguageSPITest.ServiceTestLanguage.LanguageSPITestLanguageService3;
import com.oracle.truffle.api.test.polyglot.LanguageSPITestLanguage.LanguageContext;
import com.oracle.truffle.tck.tests.ValueAssert;

public class LanguageSPITest {

    static LanguageContext langContext;

    @After
    public void cleanup() {
        langContext = null;
        ProxyLanguage.setDelegate(new ProxyLanguage());
        InitializeTestLanguage.initialized = false;
        InitializeTestInternalLanguage.initialized = false;
    }

    @Test
    public void testContextClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).allowPolyglotAccess(PolyglotAccess.ALL).build();
        assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        assertNotNull(langContext);
        assertEquals(0, langContext.disposeCalled);
        context.close();
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testPolyglotClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = Context.newBuilder().engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);

        assertNotNull(langContext);

        assertEquals(0, langContext.disposeCalled);
        context.close();
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testImplicitClose() {
        Engine engine = Engine.create();
        langContext = null;
        Context c = Context.newBuilder().engine(engine).build();
        c.initialize(LanguageSPITestLanguage.ID);
        LanguageContext context1 = langContext;

        Context.newBuilder().engine(engine).build().initialize(LanguageSPITestLanguage.ID);
        LanguageContext context2 = langContext;

        c.close();
        engine.close();
        assertEquals(1, context1.disposeCalled);
        assertEquals(1, context2.disposeCalled);
    }

    @Test
    public void testImplicitCloseFromOtherThread() throws InterruptedException {
        Engine engine = Engine.create();
        Context context = Context.newBuilder().engine(engine).build();
        langContext = null;
        context.initialize(LanguageSPITestLanguage.ID);

        Thread t = new Thread(new Runnable() {
            public void run() {
                engine.close();
            }
        });
        t.start();
        t.join(10000);
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testCreateFromOtherThreadAndCloseFromMain() throws InterruptedException {
        Engine engine = Engine.create();
        langContext = null;
        Thread t = new Thread(new Runnable() {
            public void run() {
                Context context = Context.newBuilder().engine(engine).build();
                context.initialize(LanguageSPITestLanguage.ID);
            }
        });
        t.start();
        t.join(10000);
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    private static Value eval(Context context, Function<Env, Object> f) {
        LanguageSPITestLanguage.runinside = f;
        try {
            return context.eval(LanguageSPITestLanguage.ID, "");
        } finally {
            LanguageSPITestLanguage.runinside = null;
        }
    }

    @Test
    public void testContextCloseInsideFromSameThread() {
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                context.close();
                return null;
            }
        });
        context.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testContextCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                context.close(true);
                return null;
            }
        });
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testEngineCloseInsideFromSameThread() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                engine.close();
                return null;
            }
        });
        assertEquals(1, langContext.disposeCalled);
        engine.close();
    }

    @Test
    public void testEngineCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                engine.close(true);
                return null;
            }
        });
        assertEquals(1, langContext.disposeCalled);
        engine.close();
    }

    @SuppressWarnings("serial")
    private static class Interrupted extends RuntimeException implements TruffleException {

        public boolean isCancelled() {
            return true;
        }

        public Node getLocation() {
            return null;
        }
    }

    @SuppressWarnings("serial")
    private static final class ParseException extends RuntimeException implements TruffleException {
        private final Source source;
        private final int start;
        private final int length;

        ParseException(final Source source, final int start, final int length) {
            Objects.requireNonNull(source, "Source must be non null");
            this.source = source;
            this.start = start;
            this.length = length;
        }

        @Override
        public boolean isSyntaxError() {
            return true;
        }

        @Override
        public Node getLocation() {
            return null;
        }

        @Override
        public SourceSection getSourceLocation() {
            return source.createSection(start, length);
        }
    }

    @Test
    public void testCancelExecutionWhileSleeping() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(1);
        try {
            Engine engine = Engine.create();
            Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();

            CountDownLatch beforeSleep = new CountDownLatch(1);
            CountDownLatch interrupt = new CountDownLatch(1);
            AtomicInteger gotInterrupt = new AtomicInteger(0);
            Function<Env, Object> f = new Function<Env, Object>() {
                public Object apply(Env t) {
                    try {
                        beforeSleep.countDown();
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        gotInterrupt.incrementAndGet();
                        interrupt.countDown();
                        throw new Interrupted();
                    }
                    return null;
                }
            };
            Future<Value> future = service.submit(() -> eval(context, f));
            beforeSleep.await(10000, TimeUnit.MILLISECONDS);
            context.close(true);

            interrupt.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(1, gotInterrupt.get());

            try {
                future.get();
                fail();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof PolyglotException)) {
                    throw new AssertionError(cause);
                }
                PolyglotException polyglotException = (PolyglotException) cause;
                assertTrue(polyglotException.isCancelled());
            }
            engine.close();
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void testImplicitEngineClose() {
        Context context = Context.create();
        context.close();

        try {
            context.getEngine().getOptions();
            fail();
        } catch (IllegalStateException e) {
            // expect closed
        }

        try {
            context.getEngine().getLanguages();
            fail();
        } catch (IllegalStateException e) {
            // expect closed
        }

        try {
            context.getEngine().getInstruments();
            fail();
        } catch (IllegalStateException e) {
            // expect closed
        }

        // does not fail
        context.getEngine().close();
    }

    @Test
    public void testLookupHost() {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build();
        Value value = eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.lookupHostSymbol("java.util.HashMap");
            }
        });
        assertTrue(value.isHostObject());
        Object map = value.asHostObject();
        assertSame(map, HashMap.class);
        context.close();
    }

    @Test
    public void testLookupHostArray() {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build();
        Value value = eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.lookupHostSymbol("java.lang.String[]");
            }
        });
        assertTrue(value.isHostObject());
        Object map = value.asHostObject();
        assertSame(map, String[].class);
        context.close();
    }

    @Test
    public void testLookupHostDisabled() {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> false).build();
        try {
            eval(context, new Function<Env, Object>() {
                public Object apply(Env t) {
                    return t.lookupHostSymbol("java.util.HashMap");
                }
            });
            fail();
        } catch (PolyglotException e) {
            assertTrue(!e.isInternalError());
        }
        context.close();
    }

    @Test
    public void testIsHostAccessAllowed() {
        Context context = Context.create();
        assertTrue(!eval(context, env -> env.isHostLookupAllowed()).asBoolean());
        context.close();

        context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build();
        assertTrue(eval(context, env -> env.isHostLookupAllowed()).asBoolean());
        context.close();
    }

    @Test
    public void testInnerContext() {
        Context context = Context.create();
        Function<Env, Object> f = new Function<Env, Object>() {
            public Object apply(Env env) {
                LanguageContext outerLangContext = LanguageSPITestLanguage.getContext();
                Object config = new Object();
                TruffleContext innerContext = env.newContextBuilder().config("config", config).build();
                Object p = innerContext.enter();
                LanguageContext innerLangContext = LanguageSPITestLanguage.getContext();
                try {

                    try {
                        innerContext.close();
                        fail("context could be closed when entered");
                    } catch (IllegalStateException e) {
                    }
                    assertEquals(0, innerLangContext.disposeCalled);

                    assertEquals(config, innerLangContext.config.get("config"));
                    assertNotSame(outerLangContext, innerLangContext);

                    boolean assertions = false;
                    assert (assertions = true) == true;
                    if (assertions) {
                        boolean leaveFailed = false;
                        try {
                            innerContext.leave("foo");
                        } catch (AssertionError e) {
                            leaveFailed = true;
                        }
                        if (!leaveFailed) {
                            fail("no assertion error for leaving with the wrong object");
                        }
                    }
                } finally {
                    innerContext.leave(p);
                }
                assertSame(outerLangContext, LanguageSPITestLanguage.getContext());
                innerContext.close();

                try {
                    innerContext.enter();
                    fail("cannot be entered after closing");
                } catch (IllegalStateException e) {
                }

                innerContext.close();

                assertEquals(1, innerLangContext.disposeCalled);
                return null;
            }
        };
        eval(context, f);

        // ensure we are not yet closed
        eval(context, f);
        context.close();
    }

    @Test
    public void testTruffleContext() {
        Context context = Context.create();
        Function<Env, Object> f = new Function<Env, Object>() {
            public Object apply(Env env) {
                LanguageSPITestLanguage.runinside = null; // No more recursive runs inside
                Throwable[] error = new Throwable[1];
                Thread thread = new Thread(() -> {
                    try {
                        Source source = Source.newBuilder(LanguageSPITestLanguage.ID, "", "s").build();
                        boolean parsingFailed = false;
                        try {
                            // execute Truffle code in a fresh thread fails
                            env.parsePublic(source).call();
                        } catch (IllegalStateException e) {
                            // No current context available.
                            parsingFailed = true;
                        }
                        if (!parsingFailed) {
                            fail("no IllegalStateException \"No current context available.\"");
                        }

                        TruffleContext truffleContext = env.getContext();
                        // attach the Thread
                        Object prev = truffleContext.enter();
                        try {
                            // execute Truffle code
                            env.parsePublic(source).call();
                        } finally {
                            // detach the Thread
                            truffleContext.leave(prev);
                        }
                    } catch (Throwable t) {
                        error[0] = t;
                    }
                });
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                if (error[0] != null) {
                    throw new AssertionError(error[0]);
                }
                boolean leaveFailed = false;
                try {
                    TruffleContext truffleContext = env.getContext();
                    truffleContext.leave(null);
                } catch (AssertionError e) {
                    leaveFailed = true;
                }
                if (!leaveFailed) {
                    fail("no assertion error for leaving without enter");
                }
                return null;
            }
        };
        eval(context, f);
        context.close();
    }

    @Test
    public void testCloseInnerContextWithParent() {
        Context context = Context.create();
        LanguageContext returnedInnerContext = eval(context, new Function<Env, Object>() {
            public Object apply(Env env) {
                TruffleContext innerContext = env.newContextBuilder().build();
                Object p = innerContext.enter();
                LanguageContext innerLangContext = LanguageSPITestLanguage.getContext();
                innerContext.leave(p);
                return env.asGuestValue(innerLangContext);
            }
        }).asHostObject();
        context.close();
        // inner context automatically closed
        assertEquals(1, returnedInnerContext.disposeCalled);
    }

    @Test
    public void testLazyInit() {
        LanguageSPITestLanguage.instanceCount.set(0);
        Context context = Context.create();
        assertEquals(0, LanguageSPITestLanguage.instanceCount.get());
        context.initialize(LanguageSPITestLanguage.ID);
        assertEquals(1, LanguageSPITestLanguage.instanceCount.get());
        context.close();
    }

    @Test
    public void testLazyInitSharedEngine() {
        LanguageSPITestLanguage.instanceCount.set(0);
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();
        Context context2 = Context.newBuilder().engine(engine).build();
        assertEquals(0, LanguageSPITestLanguage.instanceCount.get());
        context1.initialize(LanguageSPITestLanguage.ID);
        context2.initialize(LanguageSPITestLanguage.ID);
        assertEquals(1, LanguageSPITestLanguage.instanceCount.get());
        context1.close();
        context2.close();
    }

    @TruffleLanguage.Registration(id = OneContextLanguage.ID, name = OneContextLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class OneContextLanguage extends MultiContextLanguage {
        static final String ID = "OneContextLanguage";

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return null;
        }

        public static OneContextLanguage getCurrentLanguage() {
            return getCurrentLanguage(OneContextLanguage.class);
        }

        public static LanguageContext getCurrentContext() {
            return getCurrentContext(OneContextLanguage.class);
        }

    }

    @TruffleLanguage.Registration(id = MultiContextLanguage.ID, name = MultiContextLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.SHARED)
    public static class MultiContextLanguage extends ProxyLanguage {

        static final String ID = "MultiContextLanguage";

        List<Env> createContextCalled = new ArrayList<>();
        List<Source> parseCalled = new ArrayList<>();
        List<Integer> initializeMultiContextCalled = new ArrayList<>(); // executionIndex
        List<Integer> initializeMultipleContextsCalled = new ArrayList<>(); // executionIndex
        boolean contextCachingEnabled = false;
        int executionIndex;

        @Option(help = "", category = OptionCategory.INTERNAL, stability = OptionStability.STABLE) //
        static final OptionKey<Integer> DummyOption = new OptionKey<>(0);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new MultiContextLanguageOptionDescriptors();
        }

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(DummyOption).equals(newOptions.get(DummyOption));
        }

        @Override
        protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            wrapper = false;
            executionIndex++;
            createContextCalled.add(env);
            return super.createContext(env);
        }

        public static LanguageContext getCurrentContext() {
            return getCurrentContext(MultiContextLanguage.class);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            executionIndex++;
            parseCalled.add(request.getSource());
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }

        @Override
        protected void initializeMultipleContexts() {
            executionIndex++;
            initializeMultipleContextsCalled.add(executionIndex);
        }

        @Override
        protected boolean initializeMultiContext() {
            executionIndex++;
            initializeMultiContextCalled.add(executionIndex);
            return contextCachingEnabled;
        }

        static MultiContextLanguage getInstance() {
            return MultiContextLanguage.getCurrentLanguage(MultiContextLanguage.class);
        }

        static MultiContextLanguage getInstance(Class<? extends MultiContextLanguage> lang, Context context) {
            context.enter();
            try {
                return MultiContextLanguage.getCurrentLanguage(lang);
            } finally {
                context.leave();
            }
        }

    }

    @Test
    public void testMultiContextBoundEngine() {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with bound engine
        Context context = Context.create();

        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context);

        assertEquals(1, lang.createContextCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        context.eval(source1); // cached parse
        assertEquals(1, lang.parseCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());

        context.eval(source2); // cached parse
        assertEquals(2, lang.parseCalled.size());
        assertEquals(source2.getCharacters(), lang.parseCalled.get(1).getCharacters());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        context.close();
    }

    @Test
    public void testMultiContextBoundEngineInnerContextWithCaching() throws Exception {

        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        Source truffleSource1 = getTruffleSource(source1);
        Source truffleSource2 = getTruffleSource(source2);

        // test behavior with bound engine
        Context context = Context.create();
        context.enter();

        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context);

        assertEquals(1, lang.createContextCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());
        Env env = lang.createContextCalled.get(0);

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        TruffleContext innerContext = env.newContextBuilder().build();
        Object prev = innerContext.enter();
        Env innerEnv = MultiContextLanguage.getCurrentContext().env;
        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(3, (int) lang.initializeMultipleContextsCalled.get(0));
        assertEquals(4, (int) lang.initializeMultiContextCalled.get(0));
        assertEquals(2, lang.createContextCalled.size());

        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, lang.parseCalled.size());

        innerEnv.parsePublic(truffleSource2);
        assertEquals(2, lang.parseCalled.size());

        innerContext.leave(prev);
        innerContext.close();

        context.eval(source2);
        assertEquals(2, lang.parseCalled.size());

        context.leave();
        context.close();
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(2, lang.createContextCalled.size());
    }

    @Test
    public void testMultiContextBoundEngineInnerContextNoCaching() throws Exception {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(OneContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(OneContextLanguage.ID, "bar");

        Source truffleSource1 = getTruffleSource(source1);
        Source truffleSource2 = getTruffleSource(source2);

        // test behavior with bound engine
        Context context = Context.create();
        context.enter();

        context.initialize(OneContextLanguage.ID);
        MultiContextLanguage lang = OneContextLanguage.getInstance(OneContextLanguage.class, context);
        assertEquals(1, lang.createContextCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());
        Env env = lang.createContextCalled.get(0);

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        TruffleContext innerContext = env.newContextBuilder().build();
        Object prev = innerContext.enter();

        MultiContextLanguage innerLang = OneContextLanguage.getCurrentLanguage();
        assertNotSame(innerLang, lang);

        Env innerEnv = OneContextLanguage.getCurrentContext().env;
        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, innerLang.parseCalled.size());
        assertEquals(0, innerLang.initializeMultiContextCalled.size());
        assertEquals(1, innerLang.createContextCalled.size());

        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, innerLang.parseCalled.size());

        innerEnv.parsePublic(truffleSource2);
        assertEquals(2, innerLang.parseCalled.size());

        innerContext.leave(prev);
        innerContext.close();

        assertEquals(1, lang.parseCalled.size());
        context.eval(source2);
        assertEquals(2, lang.parseCalled.size());

        context.leave();
        context.close();

        assertEquals(1, lang.createContextCalled.size());
        assertEquals(0, lang.initializeMultiContextCalled.size());
    }

    private static Source getTruffleSource(org.graalvm.polyglot.Source source) throws NoSuchFieldException, IllegalAccessException {
        java.lang.reflect.Field impl = source.getClass().getDeclaredField("impl");
        impl.setAccessible(true);
        return (Source) impl.get(source);
    }

    @Test
    public void testInitializeMultiContextNotCalledForExclusive() {
        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();
        Context context2 = Context.newBuilder().engine(engine).build();
        context1.initialize(OneContextLanguage.ID);
        context2.initialize(OneContextLanguage.ID);
        MultiContextLanguage lang1 = OneContextLanguage.getInstance(OneContextLanguage.class, context1);
        MultiContextLanguage lang2 = OneContextLanguage.getInstance(OneContextLanguage.class, context2);
        assertEquals(0, lang1.initializeMultiContextCalled.size());
        assertEquals(0, lang2.initializeMultiContextCalled.size());
    }

    @Test
    public void testInitializeCalledWithEngineOptions() {
        Engine engine = Engine.newBuilder().option(MultiContextLanguage.ID + ".DummyOption", "42").build();
        Context context = Context.newBuilder().engine(engine).build();
        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context);
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang.initializeMultiContextCalled.get(0));
        assertEquals(1, lang.createContextCalled.size());
        context.close();
        engine.close();
    }

    @Test
    public void testMultiContextExplicitEngineNoCaching() {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();

        context1.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang1 = MultiContextLanguage.getInstance(MultiContextLanguage.class, context1);

        assertTrue(lang1.parseCalled.isEmpty());
        assertEquals(1, lang1.initializeMultiContextCalled.size());
        assertEquals(1, lang1.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang1.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang1.initializeMultiContextCalled.get(0));
        assertEquals(1, lang1.createContextCalled.size());

        context1.eval(source1);
        assertEquals(1, lang1.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());

        context1.eval(source1);
        assertEquals(1, lang1.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());

        context1.eval(source2);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());

        // pass a dummy option to avoid caching
        Context context2 = Context.newBuilder().engine(engine).option(MultiContextLanguage.ID + ".DummyOption", "42").build();
        context2.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang2 = MultiContextLanguage.getInstance(MultiContextLanguage.class, context2);

        assertEquals(2, lang1.parseCalled.size());
        assertEquals(0, lang2.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());
        assertEquals(1, lang2.createContextCalled.size());

        context2.eval(source1);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(1, lang2.parseCalled.size());

        context2.eval(source1);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(1, lang2.parseCalled.size());

        context2.eval(source2);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(2, lang2.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());
        assertEquals(1, lang2.createContextCalled.size());
        engine.close();

        assertEquals(1, lang1.initializeMultiContextCalled.size());
        assertEquals(1, lang1.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang1.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang1.initializeMultiContextCalled.get(0));
        assertEquals(1, lang1.createContextCalled.size());
        assertEquals(1, lang2.createContextCalled.size());
    }

    @Test
    public void testMultiContextExplicitEngineWithCaching() {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();

        context1.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context1);

        assertTrue(lang.parseCalled.isEmpty());
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang.initializeMultiContextCalled.get(0));
        assertEquals(1, lang.createContextCalled.size());

        context1.eval(source1);
        assertEquals(1, lang.parseCalled.size());

        context1.eval(source1);
        assertEquals(1, lang.parseCalled.size());

        context1.eval(source2);
        assertEquals(2, lang.parseCalled.size());
        assertEquals(1, lang.createContextCalled.size());

        Context context2 = Context.newBuilder().engine(engine).build();

        context2.initialize(MultiContextLanguage.ID);
        assertEquals(2, lang.parseCalled.size());
        assertEquals(2, lang.createContextCalled.size());

        context2.eval(source1);
        assertEquals(2, lang.parseCalled.size());

        context2.eval(source1);
        assertEquals(2, lang.parseCalled.size());

        context2.eval(source2);
        assertEquals(2, lang.parseCalled.size());

        engine.close();

        assertEquals(2, lang.createContextCalled.size());
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
    }

    @Test
    public void testErrorInCreateContext() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
                throw new RuntimeException();
            }
        });
        testFails((c) -> c.initialize(ProxyLanguage.ID));
    }

    @Test
    public void testErrorInInitializeContext() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeContext(LanguageContext context) throws Exception {
                throw new RuntimeException();
            }
        });
        testFails((c) -> c.initialize(ProxyLanguage.ID));
    }

    @Test
    public void testErrorInInitializeThread() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeThread(LanguageContext context, Thread thread) {
                throw new RuntimeException();
            }
        });
        testFails((c) -> c.initialize(ProxyLanguage.ID));
    }

    @Test
    public void testErrorInDisposeLanguage() {
        AtomicBoolean fail = new AtomicBoolean(true);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected void disposeContext(LanguageContext context) {
                if (fail.get()) {
                    throw new RuntimeException();
                }
            }
        });

        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        testFails(() -> {
            c.close();
        });
        testFails(() -> {
            c.close();
        });

        // clean up the context
        fail.set(false);
        c.close();
    }

    @Test
    public void testErrorInParse() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
                throw new RuntimeException();
            }
        });
        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        testFails(() -> c.eval(ProxyLanguage.ID, "t0"));
        testFails(() -> c.eval(ProxyLanguage.ID, "t1"));
        c.close();
    }

    @Test
    public void testErrorInFindMetaObject() {
        final TruffleObject testObject = new TruffleObject() {
        };
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected Object findMetaObject(LanguageContext context, Object value) {
                throw new RuntimeException();
            }

            @Override
            protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(testObject));
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object == testObject;
            }
        });
        Context c = Context.create();
        Value v = c.eval(ProxyLanguage.ID, "");
        testFails(() -> v.getMetaObject());
        testFails(() -> v.getMetaObject());
        c.close();
    }

    @Test
    @SuppressWarnings("all")
    public void testLazyOptionInit() {
        AtomicInteger getOptionDescriptors = new AtomicInteger(0);
        AtomicInteger iterator = new AtomicInteger(0);
        AtomicInteger get = new AtomicInteger(0);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            final OptionKey<String> option = new OptionKey<>("");
            final List<OptionDescriptor> descriptors;
            {
                descriptors = new ArrayList<>();
                descriptors.add(OptionDescriptor.newBuilder(option, ProxyLanguage.ID + ".option").stability(OptionStability.STABLE).build());
            }

            @Override
            protected OptionDescriptors getOptionDescriptors() {
                getOptionDescriptors.incrementAndGet();
                return new OptionDescriptors() {

                    public Iterator<OptionDescriptor> iterator() {
                        iterator.incrementAndGet();
                        return descriptors.iterator();
                    }

                    public OptionDescriptor get(String optionName) {
                        get.incrementAndGet();
                        for (OptionDescriptor optionDescriptor : descriptors) {
                            if (optionDescriptor.getName().equals(optionName)) {
                                return optionDescriptor;
                            }
                        }
                        return null;
                    }
                };
            }
        });
        Context c = Context.create();
        c.close();
        assertEquals(0, getOptionDescriptors.get());
        assertEquals(0, iterator.get());
        assertEquals(0, get.get());

        c = Context.newBuilder(ProxyLanguage.ID).option(ProxyLanguage.ID + ".option", "foobar").build();
        assertEquals(1, getOptionDescriptors.get());
        assertEquals(1, get.get());
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (assertionsEnabled) {
            // with assertions enabled we assert the options.
            assertEquals(1, iterator.get());
        } else {
            // for valid options we should not need the iterator
            assertEquals(0, iterator.get());
        }
        c.close();

        // test lazyness when using meta-data
        getOptionDescriptors.set(0);
        iterator.set(0);
        get.set(0);

        c = Context.create();
        OptionDescriptors descriptors = c.getEngine().getLanguages().get(ProxyLanguage.ID).getOptions();
        assertEquals(1, getOptionDescriptors.get());
        if (assertionsEnabled) {
            // with assertions enabled we assert the options.
            assertEquals(1, iterator.get());
        } else {
            // for valid options we should not need the iterator
            assertEquals(0, iterator.get());
        }
        assertEquals(0, get.get());

        for (OptionDescriptor descriptor : descriptors) {
        }
        assertEquals(1, getOptionDescriptors.get());
        if (assertionsEnabled) {
            // with assertions enabled we assert the options.
            assertEquals(2, iterator.get());
        } else {
            // for valid options we should not need the iterator
            assertEquals(1, iterator.get());
        }
        assertEquals(0, get.get());

        assertNotNull(descriptors.get(ProxyLanguage.ID + ".option"));

        assertEquals(1, getOptionDescriptors.get());
        assertEquals(1, get.get());

        c.close();
    }

    @Test
    public void testExportSymbolInCreate() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
                env.exportSymbol("symbol", env.asGuestValue(env));
                return super.createContext(env);
            }

        });
        Context c = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build();
        c.initialize(ProxyLanguage.ID);
        assertTrue(c.getPolyglotBindings().getMember("symbol").isHostObject());
        c.close();
    }

    @Test
    public void testRemoveSymbol() {
        Context c = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build();
        c.initialize(ProxyLanguage.ID);
        c.enter();
        Env env = ProxyLanguage.getCurrentContext().getEnv();
        env.exportSymbol("symbol", env.asGuestValue(1));
        assertTrue(c.getPolyglotBindings().hasMember("symbol"));
        env.exportSymbol("symbol", null);
        assertFalse(c.getPolyglotBindings().hasMember("symbol"));
        c.close();
    }

    @Test
    public void testCreateContextDuringDispose() {
        AtomicBoolean contextOnFinalize = new AtomicBoolean(true);
        AtomicBoolean contextOnDispose = new AtomicBoolean(false);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected LanguageContext createContext(Env env) {
                return new LanguageContext(env);
            }

            @Override
            protected void finalizeContext(LanguageContext context) {
                if (contextOnFinalize.get()) {
                    context.env.newContextBuilder().build();
                }
            }

            @Override
            protected void disposeContext(LanguageContext context) {
                if (contextOnDispose.get()) {
                    context.env.newContextBuilder().build();
                }
            }
        });

        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        // Fails on finalize
        testFails(() -> {
            c.close();
        });
        contextOnFinalize.set(false);
        contextOnDispose.set(true);
        // Fails on dispose
        testFails(() -> {
            c.close();
        });

        // clean up the context
        contextOnDispose.set(false);
        c.close();
    }

    @Test
    public void testCreateThreadDuringDispose() {
        AtomicBoolean contextOnFinalize = new AtomicBoolean(true);
        AtomicBoolean contextOnDispose = new AtomicBoolean(false);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected LanguageContext createContext(Env env) {
                return new LanguageContext(env);
            }

            @Override
            protected void finalizeContext(LanguageContext context) {
                if (contextOnFinalize.get()) {
                    context.env.createThread(() -> {
                    }).start();
                }
            }

            @Override
            protected void disposeContext(LanguageContext context) {
                if (contextOnDispose.get()) {
                    context.env.createThread(() -> {
                    }).start();
                }
            }
        });

        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        // Fails on finalize
        testFails(() -> {
            c.close();
        });
        contextOnFinalize.set(false);
        contextOnDispose.set(true);
        // Fails on dispose
        testFails(() -> {
            c.close();
        });

        // clean up the context
        contextOnDispose.set(false);
        c.close();
    }

    @Test
    public void testNullContext() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(TruffleLanguage.Env env) {
                return null;
            }
        });
        try (Engine engine = Engine.create()) {
            for (int i = 0; i < 2; i++) {
                try (Context context = Context.newBuilder(ProxyLanguage.ID).engine(engine).build()) {
                    context.initialize(ProxyLanguage.ID);
                }
            }
        }
    }

    @Test
    public void testExceptionGetSourceLocation() {
        try (Context context = Context.create(LanguageSPITestLanguage.ID)) {
            final String text = "0123456789";
            LanguageSPITestLanguage.runinside = (env) -> {
                Source src = Source.newBuilder(LanguageSPITestLanguage.ID, text, "test.txt").build();
                throw new ParseException(src, 1, 2);
            };
            try {
                context.eval(LanguageSPITestLanguage.ID, text);
                Assert.fail("PolyglotException expected.");
            } catch (PolyglotException pe) {
                Assert.assertTrue(pe.isSyntaxError());
                Assert.assertEquals("12", pe.getSourceLocation().getCharacters().toString());
            } finally {
                LanguageSPITestLanguage.runinside = null;
            }
        }
    }

    private static void testFails(Runnable consumer) {
        try {
            consumer.run();
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isInternalError());
            assertFalse(e.isHostException()); // should not expose internal errors
        }
    }

    private static void testFails(Consumer<Context> consumer) {
        Context context = Context.create();
        testFails(() -> consumer.accept(context));
        context.close();
    }

    private int findScopeInvokes = 0;

    private void setupTopScopes(Object... scopesArray) {
        findScopeInvokes = 0;
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Iterable<Scope> findTopScopes(LanguageContext context) {
                findScopeInvokes++;
                List<Scope> scopes = new ArrayList<>();
                for (int i = 0; i < scopesArray.length; i++) {
                    Object scope = scopesArray[i];
                    scopes.add(Scope.newBuilder(String.valueOf(i), scope).build());
                }
                return scopes;
            }
        });
    }

    @Test
    public void testBindingsWithInvalidScopes() {
        setupTopScopes(new TruffleObject() {
        });
        Context c = Context.create();
        assertEquals(0, findScopeInvokes);
        testFails(() -> c.getBindings(ProxyLanguage.ID));
        assertEquals(1, findScopeInvokes);
        c.close();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestKeysArray implements TruffleObject {

        private final String[] keys;

        TestKeysArray(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            try {
                return keys[(int) index];
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class TestScope implements TruffleObject {

        final Map<String, Object> values = new HashMap<>();
        boolean modifiable;
        boolean insertable;
        boolean removable;

        @ExportMessage
        public boolean hasMembers() {
            return true;
        }

        @ExportMessage
        public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new TestKeysArray(values.keySet().toArray(new String[0]));
        }

        @ExportMessage
        public Object readMember(String key) throws UnknownIdentifierException {
            if (values.containsKey(key)) {
                return values.get(key);
            } else {
                throw UnknownIdentifierException.create(key);
            }
        }

        @ExportMessage
        public void writeMember(String key, Object value) throws UnsupportedMessageException {
            if (modifiable && values.containsKey(key)) {
                values.put(key, value);
            } else if (insertable && !values.containsKey(key)) {
                values.put(key, value);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        @ExportMessage
        public void removeMember(String key) throws UnsupportedMessageException {
            if (removable && values.containsKey(key)) {
                values.remove(key);
                return;
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        final boolean isMemberReadable(String member) {
            return values.containsKey(member);
        }

        @ExportMessage
        final boolean isMemberModifiable(String member) {
            return modifiable && values.containsKey(member);
        }

        @ExportMessage
        final boolean isMemberInsertable(String member) {
            return insertable && !values.containsKey(member);
        }

        @ExportMessage
        final boolean isMemberRemovable(String member) {
            return removable && values.containsKey(member);
        }

    }

    @Test
    public void testBindingsWithDefaultScope() {
        Context c = Context.create();
        Value bindings = c.getBindings(ProxyLanguage.ID);
        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertTrue(bindings.getMemberKeys().isEmpty());
        assertNull(bindings.getMember(""));
        AbstractPolyglotTest.assertFails(() -> bindings.putMember("", ""), UnsupportedOperationException.class);
        assertFalse(bindings.removeMember(""));
        ValueAssert.assertValue(bindings);

        c.close();
    }

    @Test
    public void testBindingsWithSimpleScope() {
        TestScope scope = new TestScope();
        setupTopScopes(scope);
        Context c = Context.create();
        assertEquals(0, findScopeInvokes);
        Value bindings = c.getBindings(ProxyLanguage.ID);
        c.getBindings(ProxyLanguage.ID);
        assertEquals(1, findScopeInvokes);

        scope.values.put("foobar", "baz");

        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertTrue(bindings.hasMember("foobar"));
        assertEquals(new HashSet<>(Arrays.asList("foobar")), bindings.getMemberKeys());
        assertNull(bindings.getMember(""));
        assertEquals("baz", bindings.getMember("foobar").asString());
        AbstractPolyglotTest.assertFails(() -> bindings.putMember("", ""), UnsupportedOperationException.class);
        assertFalse(bindings.removeMember(""));
        AbstractPolyglotTest.assertFails(() -> bindings.removeMember("foobar"), UnsupportedOperationException.class);
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.insertable = true;
        bindings.putMember("baz", "val");
        assertEquals("val", scope.values.get("baz"));
        assertEquals("val", bindings.getMember("baz").asString());
        AbstractPolyglotTest.assertFails(() -> bindings.putMember("foobar", "42"), UnsupportedOperationException.class);
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.modifiable = true;
        bindings.putMember("foobar", "val");
        assertEquals("val", scope.values.get("foobar"));
        assertEquals("val", bindings.getMember("foobar").asString());
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.removable = true;
        assertFalse(bindings.removeMember(""));
        assertTrue(bindings.removeMember("foobar"));
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        assertEquals(1, findScopeInvokes);

        c.close();
    }

    @Test
    public void testBindingsWithMultipleScopes() {
        // innermost to outermost
        TestScope[] scopes = new TestScope[5];
        for (int i = 0; i < 5; i++) {
            scopes[i] = new TestScope();
        }
        setupTopScopes((Object[]) scopes);

        Context c = Context.create();

        assertEquals(0, findScopeInvokes);
        Value bindings = c.getBindings(ProxyLanguage.ID);
        assertEquals(1, findScopeInvokes);

        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertNull(bindings.getMember(""));

        AbstractPolyglotTest.assertFails(() -> bindings.putMember("foo", "bar"), UnsupportedOperationException.class);

        // test insertion into first insertable scope
        scopes[1].insertable = true;
        scopes[2].insertable = true;
        bindings.putMember("foo", "bar"); // should end up in scope 1
        assertEquals("bar", bindings.getMember("foo").asString());
        assertEquals("bar", scopes[1].values.get("foo"));
        assertNull(scopes[0].values.get("foo"));
        assertNull(scopes[2].values.get("foo"));
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        // test it does not insert early before already existing member
        scopes[0].insertable = true;
        AbstractPolyglotTest.assertFails(() -> bindings.putMember("foo", "baz"), UnsupportedOperationException.class);
        scopes[1].modifiable = true;
        // does not insert in 1 but modifies foo in 2
        bindings.putMember("foo", "baz");
        assertNull(scopes[0].values.get("foo"));
        assertEquals("baz", scopes[1].values.get("foo"));
        assertEquals("baz", bindings.getMember("foo").asString());

        // test check for existing keys for remove
        scopes[1].values.clear();
        scopes[1].values.put("foo", "bar");
        scopes[2].removable = true;
        scopes[2].values.put("foo", "baz");
        scopes[2].values.put("bar", "baz");
        scopes[3].values.put("bar", "val");
        assertEquals("bar", bindings.getMember("foo").asString());
        assertEquals("baz", bindings.getMember("bar").asString());
        assertFails(() -> bindings.removeMember("foo"), UnsupportedOperationException.class);
        assertTrue(bindings.removeMember("bar"));
        assertNotNull(scopes[2].values.get("foo"));
        assertNull(scopes[2].values.get("bar"));
        assertEquals("val", bindings.getMember("bar").asString());
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        c.close();
    }

    @Test
    public void testPolyglotBindings() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return lookupContextReference(ProxyLanguage.class).get().env.getPolyglotBindings();
                    }
                });
            }
        });

        Context c = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build();
        Value languageBindings = c.eval(ProxyLanguage.ID, "");
        Value polyglotBindings = c.getPolyglotBindings();

        polyglotBindings.putMember("foo", "bar");
        assertEquals("bar", polyglotBindings.getMember("foo").asString());
        assertEquals("bar", languageBindings.getMember("foo").asString());

        languageBindings.putMember("baz", "val");
        assertEquals("val", polyglotBindings.getMember("baz").asString());
        assertEquals("val", languageBindings.getMember("baz").asString());

        assertValue(polyglotBindings);
        assertValue(languageBindings);

        c.close();
    }

    @Test
    public void testPolyglotBindingsMultiThreaded() throws Throwable {
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return lookupContextReference(ProxyLanguage.class).get().env.getPolyglotBindings();
                    }
                });
            }
        });

        Context c = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build();
        ExecutorService service = Executors.newFixedThreadPool(20);

        Value languageBindings = c.eval(ProxyLanguage.ID, "");
        Value polyglotBindings = c.getPolyglotBindings();

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 2000; i++) {
            futures.add(service.submit(() -> {
                polyglotBindings.putMember("foo", "bar");
                assertEquals("bar", polyglotBindings.getMember("foo").asString());
                assertEquals("bar", languageBindings.getMember("foo").asString());

                languageBindings.putMember("baz", "42");
                assertEquals("42", polyglotBindings.getMember("baz").asString());
                assertEquals("42", languageBindings.getMember("baz").asString());
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(100000, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }

        service.shutdown();
        service.awaitTermination(100000, TimeUnit.MILLISECONDS);

        c.close();
    }

    @Test
    public void testFindSourceLocation() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new SourceHolder(request.getSource())));
            }

            @Override
            protected SourceSection findSourceLocation(ProxyLanguage.LanguageContext context, Object value) {
                if (value instanceof SourceHolder) {
                    final Source src = ((SourceHolder) value).source;
                    return src.createSection(0, src.getLength());
                }
                return super.findSourceLocation(context, value);
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object instanceof SourceHolder;
            }
        });

        try (Context context = Context.create(ProxyLanguage.ID)) {
            String text = "01234567";
            Value res = context.eval(ProxyLanguage.ID, text);
            assertNotNull(res);
            org.graalvm.polyglot.SourceSection sourceSection = res.getSourceLocation();
            assertNotNull(sourceSection);
            assertTrue(text.contentEquals(sourceSection.getCharacters()));
            res = context.asValue(new SourceHolder(Source.newBuilder(ProxyLanguage.ID, text, null).build()));
            sourceSection = res.getSourceLocation();
            assertNotNull(sourceSection);
            assertTrue(text.contentEquals(sourceSection.getCharacters()));
        }
    }

    @Test
    public void testToString() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new SourceHolder(request.getSource())));
            }

            @Override
            protected String toString(ProxyLanguage.LanguageContext context, Object value) {
                if (value instanceof SourceHolder) {
                    final Source src = ((SourceHolder) value).source;
                    return src.getCharacters().toString();
                }
                return super.toString(context, value);
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object instanceof SourceHolder;
            }
        });

        try (Context context = Context.create(ProxyLanguage.ID)) {
            String text = "01234567";
            Value res = context.eval(ProxyLanguage.ID, text);
            assertNotNull(res);
            String toString = res.toString();
            assertEquals(text, toString);
            res = context.asValue(new SourceHolder(Source.newBuilder(ProxyLanguage.ID, text, null).build()));
            toString = res.toString();
            assertEquals(text, toString);
        }
    }

    private static boolean lookupLanguage(Class<?> serviceClass) {
        Env env = ProxyLanguage.getCurrentContext().env;
        LanguageInfo languageInfo = env.getInternalLanguages().get(SERVICE_LANGUAGE);
        return env.lookup(languageInfo, serviceClass) != null;
    }

    @Test
    public void testLookup() {
        // Registered service
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            context.initialize(ProxyLanguage.ID);
            context.enter();
            try {
                assertTrue(lookupLanguage(LanguageSPITestLanguageService1.class));
                assertTrue(lookupLanguage(LanguageSPITestLanguageService2.class));
            } finally {
                context.leave();
            }
        }
        // Non registered service
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            context.initialize(ProxyLanguage.ID);
            context.enter();
            try {
                assertFalse(lookupLanguage(LanguageSPITestLanguageService3.class));
            } finally {
                context.leave();
            }
        }
    }

    @Test
    public void testConcurrentLookupWhileInitializing() throws InterruptedException {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });

        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            context.initialize(ProxyLanguage.ID);

            final CountDownLatch startCreate = new CountDownLatch(1);
            ServiceTestLanguage.startCreate = startCreate;

            Throwable[] error = new Throwable[1];
            Thread thread = new Thread(() -> {
                try {
                    context.enter();
                    startCreate.await(); // Wait until the context starts being created
                    try {
                        // Concurrently trying to access the service should wait for language
                        // context creation
                        assertTrue(lookupLanguage(LanguageSPITestLanguageService1.class));
                    } finally {
                        context.leave();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    error[0] = t;
                }
            });

            context.enter();
            try {
                thread.start();
                // Trigger initialization of SERVICE_LANGUAGE on the main thread
                assertTrue(lookupLanguage(LanguageSPITestLanguageService2.class));
            } finally {
                context.leave();
            }

            thread.join();
            assertNull(error[0]);
        }
    }

    static final String SERVICE_LANGUAGE = "ServiceTestLanguage";

    @Test
    public void testRegisterService() {
        ProxyLanguage registerServiceLanguage = new ProxyLanguage() {
            @Override
            protected ProxyLanguage.LanguageContext createContext(Env env) {
                env.registerService(new LanguageSPITestLanguageService1() {
                });
                return super.createContext(env);
            }

            @Override
            protected void initializeContext(ProxyLanguage.LanguageContext context) throws Exception {
                try {
                    context.env.registerService(new LanguageSPITestLanguageService2() {
                    });
                    fail("Illegal state exception should be thrown when calling Env.registerService outside createContext");
                } catch (IllegalStateException e) {
                    // expected
                }
                super.initializeContext(context);
            }

            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                try {
                    getCurrentContext().env.registerService(new LanguageSPITestLanguageService3() {
                    });
                    fail("Illegal state exception should be thrown when calling Env.registerService outside createContext");
                } catch (IllegalStateException e) {
                    // expected
                }
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
            }
        };
        ProxyLanguage.setDelegate(registerServiceLanguage);
        try (Context context = Context.create(ProxyLanguage.ID)) {
            context.eval(ProxyLanguage.ID, "");
        }
    }

    static final String INHERITED_VERSION = "SPIInheritedVersionLanguage";

    @TruffleLanguage.Registration(id = INHERITED_VERSION, name = "")
    public static class InheritedVersionLanguage extends ProxyLanguage {
    }

    @Test
    public void testInheritedVersionLanguage() {
        Context context = Context.create();
        context.initialize(INHERITED_VERSION);
        final Engine engine = context.getEngine();
        assertEquals(engine.getVersion(), engine.getLanguages().get(INHERITED_VERSION).getVersion());
        context.close();
    }

    private static class SourceHolder implements TruffleObject {
        final Source source;

        SourceHolder(final Source source) {
            Objects.requireNonNull(source, "The source must be non null.");
            this.source = source;
        }

    }

    static final Source TEST_SOURCE = Source.newBuilder("", "", "testLanguageErrorDuringInitialization").build();

    @SuppressWarnings("serial")
    static class TestError extends RuntimeException implements TruffleException {

        public SourceSection getSourceLocation() {
            return TEST_SOURCE.createSection(0, 0);
        }

        public Node getLocation() {
            return null;
        }

    }

    @Test
    public void testLanguageErrorDuringInitialization() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeContext(LanguageContext c) throws Exception {
                throw new TestError();
            }
        });

        Context context = Context.create();
        try {
            context.eval(ProxyLanguage.ID, "");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isGuestException());
            assertEquals("testLanguageErrorDuringInitialization", e.getSourceLocation().getSource().getName());
        }
        context.close();
    }

    @Test
    public void testInitializeInternalLanguage() {
        try (Context context = Context.create()) {
            testInitializeInternalImpl(context, (env) -> env.getInternalLanguages().get(InitializeTestInternalLanguage.ID), (se) -> se == null && InitializeTestInternalLanguage.initialized);
        }
    }

    @Test
    public void testInitializeNonInternalLanguage() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.newBuilder().allowEval(ProxyLanguage.ID, InitializeTestLanguage.ID).build()).build()) {
            testInitializeInternalImpl(context, (env) -> env.getPublicLanguages().get(InitializeTestLanguage.ID), (se) -> se == null && InitializeTestLanguage.initialized);
        }
    }

    @Test
    public void testInitializeNonAccessibleLanguage() {
        try (Context context = Context.create()) {
            Function<TruffleLanguage.Env, LanguageInfo> languageResolver = (env) -> {
                InstrumentInfo instrumentInfo = env.getInstruments().get(InitializeTestInstrument.ID);
                assertNotNull(instrumentInfo);
                InitializeTestInstrument.Service service = env.lookup(instrumentInfo, InitializeTestInstrument.Service.class);
                assertNotNull(service);
                LanguageInfo languageInfo = service.findLanguage(InitializeTestLanguage.ID);
                assertNotNull(languageInfo);
                return languageInfo;
            };
            testInitializeInternalImpl(context, languageResolver, (se) -> se != null);
        }
    }

    private static void testInitializeInternalImpl(Context context, Function<TruffleLanguage.Env, LanguageInfo> languageResolver, Function<SecurityException, Boolean> verifier) {
        AtomicReference<SecurityException> exception = new AtomicReference<>();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        Env env = lookupContextReference(ProxyLanguage.class).get().getEnv();
                        LanguageInfo languageToInitialize = languageResolver.apply(env);
                        assertNotNull(languageToInitialize);
                        try {
                            assertTrue(env.initializeLanguage(languageToInitialize));
                        } catch (SecurityException se) {
                            exception.set(se);
                        }
                        return true;
                    }
                });
            }
        });
        assertTrue(context.eval(ProxyLanguage.ID, "").asBoolean());
        assertTrue(verifier.apply(exception.get()));
    }

    @Test
    public void testInitializeFromServiceInternalLanguage() {
        try (Context context = Context.create()) {
            testInitializeFromServiceImpl(context, (env) -> env.getInternalLanguages().get(InitializeTestInternalLanguage.ID), () -> InitializeTestInternalLanguage.initialized);
        }
    }

    @Test
    public void testInitializeFromServiceNonInternalLanguage() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.newBuilder().allowEval(ProxyLanguage.ID, InitializeTestLanguage.ID).build()).build()) {
            testInitializeFromServiceImpl(context, (env) -> env.getPublicLanguages().get(InitializeTestLanguage.ID), () -> InitializeTestLanguage.initialized);
        }
    }

    private static void testInitializeFromServiceImpl(Context context, Function<Env, LanguageInfo> languageResolver, Supplier<Boolean> verifier) {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        Env env = lookupContextReference(ProxyLanguage.class).get().getEnv();
                        LanguageInfo languageProvidingService = languageResolver.apply(env);
                        assertNotNull(languageProvidingService);
                        InitializeTestBaseLanguage.Service service = env.lookup(languageProvidingService, InitializeTestBaseLanguage.Service.class);
                        assertNotNull(service);
                        service.doNotInitialize();
                        assertFalse(verifier.get());
                        service.doesInitialize();
                        assertTrue(verifier.get());
                        return true;
                    }
                });
            }
        });
        assertTrue(context.eval(ProxyLanguage.ID, "").asBoolean());
        assertTrue(verifier.get());
    }

    @TruffleLanguage.Registration(id = SERVICE_LANGUAGE, name = SERVICE_LANGUAGE, version = "1.0", contextPolicy = ContextPolicy.SHARED, services = {
                    LanguageSPITestLanguageService1.class, LanguageSPITestLanguageService2.class})
    public static class ServiceTestLanguage extends TruffleLanguage<Env> {

        static CountDownLatch startCreate;

        @Override
        protected Env createContext(Env env) {
            if (startCreate != null) {
                startCreate.countDown();
                startCreate = null;
            }

            env.registerService(new LanguageSPITestLanguageService1() {
            });
            env.registerService(new LanguageSPITestLanguageService2() {
            });
            return env;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        interface LanguageSPITestLanguageService1 {
        }

        interface LanguageSPITestLanguageService2 {
        }

        interface LanguageSPITestLanguageService3 {
        }

    }

    public abstract static class InitializeTestBaseLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        @Override
        protected Env createContext(Env env) {
            env.registerService(new Service() {

                @Override
                public void doNotInitialize() {
                }

                @Override
                public void doesInitialize() {
                    LanguageInfo language = getLanguageInfo(env);
                    assertNotNull(language);
                    env.initializeLanguage(language);
                }
            });
            return env;
        }

        @Override
        protected void initializeContext(Env context) throws Exception {
            setInitialized(true);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
        }

        abstract LanguageInfo getLanguageInfo(Env env);

        abstract void setInitialized(boolean value);

        interface Service {
            void doNotInitialize();

            void doesInitialize();
        }
    }

    @TruffleLanguage.Registration(id = InitializeTestLanguage.ID, name = InitializeTestLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.EXCLUSIVE, services = InitializeTestBaseLanguage.Service.class)
    public static final class InitializeTestLanguage extends InitializeTestBaseLanguage {
        static final String ID = "LanguageSPITestInitializeTestLanguage";

        static boolean initialized;

        @Override
        LanguageInfo getLanguageInfo(Env env) {
            return env.getPublicLanguages().get(ID);
        }

        @Override
        void setInitialized(boolean value) {
            initialized = value;
        }
    }

    @TruffleLanguage.Registration(id = InitializeTestInternalLanguage.ID, name = InitializeTestInternalLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.EXCLUSIVE, internal = true, services = InitializeTestBaseLanguage.Service.class)
    public static final class InitializeTestInternalLanguage extends InitializeTestBaseLanguage {
        static final String ID = "LanguageSPITestInitializeTestInternalLanguage";

        static boolean initialized;

        @Override
        LanguageInfo getLanguageInfo(Env env) {
            return env.getInternalLanguages().get(ID);
        }

        @Override
        void setInitialized(boolean value) {
            initialized = value;
        }
    }

    @TruffleInstrument.Registration(id = InitializeTestInstrument.ID, name = InitializeTestInstrument.ID, version = "1.0", services = InitializeTestInstrument.Service.class)
    public static final class InitializeTestInstrument extends TruffleInstrument {

        static final String ID = "LanguageSPITestInitializeTestInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService(new Service() {
                @Override
                public LanguageInfo findLanguage(String id) {
                    return env.getLanguages().get(id);
                }
            });
        }

        public interface Service {
            LanguageInfo findLanguage(String id);
        }
    }

    static volatile boolean enableThrow = false;

    @TruffleLanguage.Registration(id = ThrowInLanguageInitializer.ID, name = ThrowInLanguageInitializer.ID)
    public static final class ThrowInLanguageInitializer extends TruffleLanguage<TruffleLanguage.Env> {

        static final String ID = "LanguageSPITestThrowInLanguageInitializer";

        public ThrowInLanguageInitializer() {
            if (enableThrow) {
                throw new AssertionError("expectedMessage");
            }
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }

        @Override
        protected Env createContext(Env env) {
            return env;
        }

    }

    @Test
    public void testThrowInClassInitializer() {
        enableThrow = true;
        try {
            try (Engine engine = Engine.create()) {
                Language language = engine.getLanguages().get(ThrowInLanguageInitializer.ID);
                AbstractPolyglotTest.assertFails(() -> language.getOptions(), PolyglotException.class, this::assertExpectedInternalError);
                AbstractPolyglotTest.assertFails(() -> language.getOptions(), PolyglotException.class, this::assertExpectedInternalError);
            }

            try (Context context = Context.create()) {
                Language language = context.getEngine().getLanguages().get(ThrowInLanguageInitializer.ID);
                AbstractPolyglotTest.assertFails(() -> language.getOptions(), PolyglotException.class, this::assertExpectedInternalError);
                AbstractPolyglotTest.assertFails(() -> language.getOptions(), PolyglotException.class, this::assertExpectedInternalError);

                AbstractPolyglotTest.assertFails(() -> context.initialize(ThrowInLanguageInitializer.ID), PolyglotException.class, this::assertExpectedInternalError);
                AbstractPolyglotTest.assertFails(() -> context.eval(ThrowInLanguageInitializer.ID, ""), PolyglotException.class, this::assertExpectedInternalError);
            }

        } finally {
            enableThrow = false;
        }
    }

    void assertExpectedInternalError(PolyglotException e) {
        assertTrue(e.isInternalError());
        assertEquals("java.lang.AssertionError: expectedMessage", e.getMessage());
    }

}
