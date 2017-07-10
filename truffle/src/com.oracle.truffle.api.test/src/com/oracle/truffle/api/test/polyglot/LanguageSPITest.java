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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleException;
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
    public void testContextCloseInsideFromSameThread() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        LanguageSPITestLanguage.runinside = new Callable<CallTarget>() {
            public CallTarget call() throws Exception {
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
        LanguageSPITestLanguage.runinside = new Callable<CallTarget>() {
            public CallTarget call() throws Exception {
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
        LanguageSPITestLanguage.runinside = new Callable<CallTarget>() {
            public CallTarget call() throws Exception {
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
        LanguageSPITestLanguage.runinside = new Callable<CallTarget>() {
            public CallTarget call() throws Exception {
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
            LanguageSPITestLanguage.runinside = new Callable<CallTarget>() {
                public CallTarget call() throws Exception {
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

}
