/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.LanguageSPITestLanguage.LanguageContext;

public class LanguageSPITest {

    static LanguageContext langContext;

    @Test
    public void testContextClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = Context.create(LanguageSPITestLanguage.ID);
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
                PolyglotException polyglotException = (PolyglotException) e.getCause();
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
        Context context = Context.newBuilder().allowHostAccess(true).build();
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
        Context context = Context.newBuilder().allowHostAccess(true).build();
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
        Context context = Context.newBuilder().allowHostAccess(false).build();
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
        Context context = Context.newBuilder().allowHostAccess(false).build();
        assertTrue(!eval(context, env -> env.isHostLookupAllowed()).asBoolean());
        context.close();

        context = Context.newBuilder().allowHostAccess(true).build();
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
                boolean assertions = false;
                assert (assertions = true) == true;
                if (!assertions) {
                    fail("Tests must be run with assertions on");
                }
                LanguageSPITestLanguage.runinside = null; // No more recursive runs inside
                Throwable[] error = new Throwable[1];
                Thread thread = new Thread(() -> {
                    try {
                        Source source = Source.newBuilder("").language(LanguageSPITestLanguage.ID).name("s").build();
                        boolean parsingFailed = false;
                        try {
                            // execute Truffle code in a fresh thread fails
                            env.parse(source).call();
                        } catch (AssertionError e) {
                            // No current context available.
                            parsingFailed = true;
                        }
                        if (!parsingFailed) {
                            fail("no assertion error \"No current context available.\"");
                        }

                        TruffleContext truffleContext = env.getContext();
                        // attach the Thread
                        Object prev = truffleContext.enter();
                        try {
                            // execute Truffle code
                            env.parse(source).call();
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
                return innerLangContext;
            }
        }).asHostObject();
        context.close();
        // inner context automatically closed
        assertEquals(1, returnedInnerContext.disposeCalled);
    }

    @Test
    public void testParseOtherLanguage() {
        Context context = Context.newBuilder().build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                assertCorrectTarget(t.parse(Source.newBuilder("").language(ContextAPITestLanguage.ID).name("").build()));
                assertCorrectTarget(t.parse(Source.newBuilder("").mimeType(ContextAPITestLanguage.MIME).name("").build()));
                // this is here for compatibility because mime types and language ids were allowed
                // in between.
                assertCorrectTarget(t.parse(Source.newBuilder("").mimeType(ContextAPITestLanguage.ID).name("").build()));
                return null;
            }

            private void assertCorrectTarget(CallTarget target) {
                Assert.assertEquals(ContextAPITestLanguage.ID, ((RootCallTarget) target).getRootNode().getLanguageInfo().getId());
            }

        });
        context.close();
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
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected Object findMetaObject(LanguageContext context, Object value) {
                throw new RuntimeException();
            }

            @Override
            protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
            }
        });
        Context c = Context.create();
        Value v = c.eval(ProxyLanguage.ID, "");
        testFails(() -> v.getMetaObject());
        testFails(() -> v.getMetaObject());
        c.close();
    }

    @Test
    public void testErrorInLookup() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected Object lookupSymbol(LanguageContext context, String symbolName) {
                throw new RuntimeException();
            }
        });
        Context c = Context.create();
        testFails(() -> c.lookup(ProxyLanguage.ID, "foobar"));
        testFails(() -> c.lookup(ProxyLanguage.ID, "foobar"));
        c.close();
    }

    @Test
    public void testErrorInSymbolLookup() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected Object findExportedSymbol(LanguageContext context, String globalName, boolean onlyExplicit) {
                throw new RuntimeException();
            }
        });
        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        testFails(() -> c.importSymbol("foobar"));
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
                descriptors.add(OptionDescriptor.newBuilder(option, ProxyLanguage.ID + ".option").build());
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
                env.exportSymbol("symbol", JavaInterop.asTruffleObject(env));
                return super.createContext(env);
            }

        });
        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        assertTrue(c.importSymbol("symbol").isHostObject());
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

}
