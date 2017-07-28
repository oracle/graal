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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
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

    @Test
    public void testAccessContextFromOtherThread() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder().engine(engine).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env env) {
                return LanguageSPITestLanguage.getContext();
            }
        };
        LanguageContext initLangContext = context.eval(LanguageSPITestLanguage.ID, "initialize").asHostObject();
        assertSame(initLangContext, langContext);

        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env env) {
                Object[] result = new Object[1];
                // Simulate a new thread created by the guest language
                Thread thread = new Thread(() -> {
                    result[0] = LanguageSPITestLanguage.getContext();
                });
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return result[0];
            }
        };
        LanguageContext langContextFromOtherThread = context.eval(LanguageSPITestLanguage.ID, "accessContextFromOtherThread").asHostObject();
        assertSame(langContextFromOtherThread, langContext);
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testContextCloseInsideFromSameThread() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                context.close();
                return null;
            }
        };
        context.eval(LanguageSPITestLanguage.ID, "");
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testContextCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                context.close(true);
                return null;
            }
        };
        context.eval(LanguageSPITestLanguage.ID, "");
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testEngineCloseInsideFromSameThread() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                engine.close();
                return null;
            }
        };
        context.eval(LanguageSPITestLanguage.ID, "");
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testEngineCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                engine.close(true);
                return null;
            }
        };
        context.eval(LanguageSPITestLanguage.ID, "");
        assertEquals(1, langContext.disposeCalled);
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
            LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
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
            Future<Value> future = service.submit(() -> context.eval(LanguageSPITestLanguage.ID, ""));
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
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.lookupHostSymbol("java.util.HashMap");
            }
        };
        Value value = context.eval(LanguageSPITestLanguage.ID, "");
        assertTrue(value.isHostObject());
        Object map = value.asHostObject();
        assertSame(map, HashMap.class);
    }

    @Test
    public void testLookupHostDisabled() {
        Context context = Context.newBuilder().allowHostAccess(false).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.lookupHostSymbol("java.util.HashMap");
            }
        };
        try {
            context.eval(LanguageSPITestLanguage.ID, "");
            fail();
        } catch (PolyglotException e) {
            assertTrue(!e.isInternalError());
        }
    }

    @Test
    public void testIsHostAccessAllowed() {
        Context context = Context.newBuilder().allowHostAccess(false).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.isHostLookupAllowed();
            }
        };
        assertTrue(!context.eval(LanguageSPITestLanguage.ID, "").asBoolean());

        context = Context.newBuilder().allowHostAccess(true).build();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.isHostLookupAllowed();
            }
        };
        assertTrue(context.eval(LanguageSPITestLanguage.ID, "").asBoolean());
    }

    @Test
    public void testInnerContext() {
        Context context = Context.create();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
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
                        try {
                            innerContext.leave("foo");
                            fail("no assertion error for leaving with the wrong object");
                        } catch (AssertionError e) {
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
        context.eval(LanguageSPITestLanguage.ID, "");

        // ensure we are not yet closed
        context.eval(LanguageSPITestLanguage.ID, "");
    }

    @Test
    public void testCloseInnerContextWithParent() {
        Context context = Context.create();
        LanguageSPITestLanguage.runinside = new Function<Env, Object>() {
            public Object apply(Env env) {
                TruffleContext innerContext = env.newContextBuilder().build();
                Object p = innerContext.enter();
                LanguageContext innerLangContext = LanguageSPITestLanguage.getContext();
                innerContext.leave(p);
                return innerLangContext;
            }
        };
        LanguageContext innerContext = context.eval(LanguageSPITestLanguage.ID, "").asHostObject();
        context.close();
        // inner context automatically closed
        assertEquals(1, innerContext.disposeCalled);
    }

}
