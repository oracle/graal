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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ContextAPITestLanguage.LanguageContext;
import com.oracle.truffle.api.test.polyglot.ValueAssert.Trait;

public class ContextAPITest {

    static LanguageContext langContext;

    @Test
    public void testContextCreateSingleLanguage() {
        Context context = Context.create(ContextAPITestLanguage.ID);
        try {
            context.eval(LanguageSPITestLanguage.ID, "");
            fail();
        } catch (IllegalArgumentException e) {
        }
        assertInternalNotAccessible(context);
        context.close();
    }

    private static void assertInternalNotAccessible(Context context) {
        try {
            context.eval(ContextAPITestInternalLanguage.ID, "");
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            context.initialize(ContextAPITestInternalLanguage.ID);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            context.getBindings(ContextAPITestInternalLanguage.ID);
            fail();
        } catch (IllegalArgumentException e) {
        }

        assertFalse(context.getEngine().getLanguages().containsKey(ContextAPITestInternalLanguage.ID));
    }

    @Test
    public void testContextCreateAllLanguages() {
        Context context = Context.create();
        context.eval(ContextAPITestLanguage.ID, "");
        context.eval(LanguageSPITestLanguage.ID, "");
        assertInternalNotAccessible(context);
        context.close();
    }

    @Test
    public void testImportExport() {
        Context context = Context.create();
        Value polyglotBindings = context.getPolyglotBindings();
        polyglotBindings.putMember("string", "bar");
        polyglotBindings.putMember("null", null);
        polyglotBindings.putMember("int", 42);
        Object object = new Object();
        polyglotBindings.putMember("object", object);

        assertEquals("bar", polyglotBindings.getMember("string").asString());
        assertTrue(polyglotBindings.getMember("null").isNull());
        assertEquals(42, polyglotBindings.getMember("int").asInt());
        assertSame(object, polyglotBindings.getMember("object").asHostObject());
        assertNull(polyglotBindings.getMember("notexisting"));
        context.close();
    }

    @Test
    public void testInstrumentOption() {
        // Instrument options can be set to context builders with implicit engine:
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.option("optiontestinstr1.StringOption1", "Hello");
        contextBuilder.build();

    }

    @Test
    public void testInstrumentOptionAsContext() {
        // Instrument options are refused by context builders with an existing engine:
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.engine(Engine.create());
        contextBuilder.option("optiontestinstr1.StringOption1", "Hello");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertEquals("Option optiontestinstr1.StringOption1 is an engine option. Engine level options can only be configured for contexts without a shared engine set. " +
                            "To resolve this, configure the option when creating the Engine or create a context without a shared engine.", ex.getMessage());
        }
    }

    @Test
    public void testInvalidEngineOptionAsContext() {
        // Instrument options are refused by context builders with an existing engine:
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.engine(Engine.create());
        contextBuilder.option("optiontestinstr1.StringOption1+Typo", "100");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertTrue(ex.getMessage().startsWith("Could not find option with name optiontestinstr1.StringOption1+Typo."));
        }
    }

    public void testEnterLeave() {
        Context context = Context.create();
        testEnterLeave(context, 0);
        context.close();
    }

    private static void testEnterLeave(Context context, int depth) {
        try {
            context.leave();
            fail();
        } catch (IllegalStateException e) {
        }

        context.getPolyglotBindings().getMember("");
        context.enter();

        try {
            context.close();
            fail();
        } catch (IllegalStateException e) {
        }

        context.getPolyglotBindings().getMember("");
        context.enter();

        try {
            context.close();
            fail();
        } catch (IllegalStateException e) {
        }

        if (depth < 3) {
            Context innerContext = Context.create();
            testEnterLeave(innerContext, depth + 1);
            innerContext.close();
        }

        context.leave();

        try {
            context.close();
            fail();
        } catch (IllegalStateException e) {
        }

        context.leave();

        try {
            context.leave();
            fail();
        } catch (IllegalStateException e) {
        }

    }

    @Test
    public void testMultithreadeddEnterLeave() throws InterruptedException, ExecutionException {
        Context context = Context.create();
        ExecutorService service = Executors.newFixedThreadPool(20);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            futures.add(service.submit(() -> testEnterLeave(context, 0)));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        service.shutdown();
        service.awaitTermination(1000, TimeUnit.MILLISECONDS);

        context.close();
    }

    @Test
    public void testEnteredExecute() {
        Context context = Context.create(ContextAPITestLanguage.ID);

        // test outside
        testExecute(context);

        // test inside
        context.enter();
        testExecute(context);
        context.leave();

        // test enter twice
        context.enter();
        context.enter();
        testExecute(context);
        context.leave();
        testExecute(context);
        context.leave();
        testExecute(context);

        // test entered with inner context
        context.enter();

        Context context2 = Context.create(ContextAPITestLanguage.ID);
        testExecute(context2);
        context2.enter();
        testExecute(context2);
        context2.leave();
        context2.enter();
        context2.enter();
        testExecute(context2);
        context2.leave();
        testExecute(context2);
        context2.leave();
        context2.close();

        context.leave();

        // finally close the context
        context.close();
    }

    private static void testExecute(Context context) {
        ContextAPITestLanguage.runinside = (env) -> new ProxyInteropObject() {
            @Override
            public boolean isExecutable() {
                return true;
            }

            @Override
            public Object execute(Object[] args) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
                return 42;
            }
        };
        Value executable = context.eval(ContextAPITestLanguage.ID, "");
        assertEquals(42, executable.execute().asInt());
        assertEquals(42, executable.execute(42).asInt());
        executable.executeVoid();
        executable.executeVoid(42);
    }

    private static void testPolyglotBindings(Context context) {
        assertSame("is stable", context.getPolyglotBindings(), context.getPolyglotBindings());

        Value bindings = context.getPolyglotBindings();
        testWritableBindings(bindings);

        ValueAssert.assertValue(context, bindings, Trait.MEMBERS);
    }

    public static class MyClass {

        public Object field = "bar";

        public int bazz() {
            return 42;
        }

    }

    private static void testWritableBindings(Value bindings) {
        bindings.putMember("int", 42);
        assertEquals(42, bindings.getMember("int").asInt());

        bindings.putMember("int", (byte) 43);
        assertEquals(43, bindings.getMember("int").asInt());

        bindings.putMember("string", "foo");
        assertEquals("foo", bindings.getMember("string").asString());

        bindings.putMember("obj", new MyClass());
        assertEquals("bar", bindings.getMember("obj").getMember("field").asString());

        bindings.putMember("obj", new MyClass());
        assertEquals(42, bindings.getMember("obj").getMember("bazz").execute().asInt());
    }

    private static void testBindings(Context context) {
        Map<String, Object> values = new HashMap<>();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Iterable<Scope> findTopScopes(LanguageContext env) {
                return Arrays.asList(Scope.newBuilder("top", new ProxyInteropObject() {
                    @Override
                    public Object read(String key) throws UnsupportedMessageException, UnknownIdentifierException {
                        return values.get(key);
                    }

                    @Override
                    public Object write(String key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
                        values.put(key, value);
                        return value;
                    }

                    @Override
                    public boolean hasKeys() {
                        return true;
                    }
                }).build());
            }
        });
        Value bindings = context.getBindings(ProxyLanguage.ID);

        testWritableBindings(bindings);

        ValueAssert.assertValue(context, bindings, Trait.MEMBERS);
    }

    @Test
    public void testEnteredContext() {
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);

        Context context = Context.create();

        assertFails(() -> Context.getCurrent(), IllegalStateException.class);

        context.enter();

        testGetContext(context);

        context.leave();

        assertFails(() -> Context.getCurrent(), IllegalStateException.class);

        context.close();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
    }

    @Test
    public void testEnteredContextInJava() {
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        Context context = Context.create();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        Value v = context.asValue(new Runnable() {
            public void run() {
                testGetContext(context);

                Value.asValue(new Runnable() {
                    public void run() {
                        testGetContext(context);
                    }
                }).execute();
            }
        });
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        v.execute();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        context.close();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
    }

    @Test
    public void testChangeContextInJava() {
        Context context = Context.create();
        Value v = context.asValue(new Runnable() {
            public void run() {
                Context innerContext = Context.create();
                testGetContext(context);
                innerContext.enter();
                testGetContext(innerContext);
                innerContext.leave();

                testGetContext(context);
                innerContext.close();
            }
        });
        v.execute();
        context.close();
    }

    private static void testGetContext(Context creatorContext) {
        assertNotSame("needs to be wrapped", creatorContext, Context.getCurrent());
        assertSame("needs to be stable", Context.getCurrent(), Context.getCurrent());

        // assert that creator context and current context refer
        // to the same context.
        assertNull(creatorContext.getPolyglotBindings().getMember("foo"));
        creatorContext.getPolyglotBindings().putMember("foo", "bar");
        assertEquals("bar", creatorContext.getPolyglotBindings().getMember("foo").asString());
        assertEquals("bar", Context.getCurrent().getPolyglotBindings().getMember("foo").asString());
        creatorContext.getPolyglotBindings().removeMember("foo");

        Context context = Context.getCurrent();
        testExecute(context);
        testPolyglotBindings(context);
        testBindings(context);

        assertFails(() -> context.leave(), IllegalStateException.class);
        assertFails(() -> context.close(), IllegalStateException.class);
        assertFails(() -> context.enter(), IllegalStateException.class);
        assertFails(() -> context.close(true), IllegalStateException.class);
        assertFails(() -> context.close(false), IllegalStateException.class);
        assertFails(() -> context.getEngine().close(), IllegalStateException.class);
        assertFails(() -> context.getEngine().close(true), IllegalStateException.class);
        assertFails(() -> context.getEngine().close(false), IllegalStateException.class);
    }

    private static void assertFails(Runnable r, Class<?> exceptionType) {
        try {
            r.run();
        } catch (Exception e) {
            assertTrue(e.getClass().getName(), exceptionType.isInstance(e));
        }
    }

    @Test
    public void testTransferControlToOtherThreadWhileEntered() {
        Context context = Context.create();

        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        try {
                            TruffleObject o = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), (TruffleObject) ProxyLanguage.getCurrentContext().env.getPolyglotBindings(), "test");
                            return ForeignAccess.sendExecute(Message.EXECUTE.createNode(), o);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        });

        context.initialize(ProxyLanguage.ID);
        context.enter();
        AtomicInteger depth = new AtomicInteger(0);
        context.getPolyglotBindings().putMember("test", new Runnable() {
            public void run() {
                depth.incrementAndGet();
                context.leave();
                try {
                    AtomicReference<Throwable> innerThrow = new AtomicReference<>();
                    Thread thread = new Thread(() -> {
                        try {
                            context.enter();
                            if (depth.get() < 3) {
                                context.eval(ProxyLanguage.ID, "");
                            }
                            context.leave();
                        } catch (Throwable t) {
                            innerThrow.set(t);
                            throw t;
                        }
                    });
                    thread.start();
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                    }
                    if (innerThrow.get() != null) {
                        throw new RuntimeException(innerThrow.get());
                    }
                } finally {
                    context.enter();
                }
            }
        });
        context.eval(ProxyLanguage.ID, "");
        context.leave();
        context.close();
        assertEquals(3, depth.get());
    }

}
