/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.ZoneId;
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
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.option.OptionProcessorTest.OptionTestLang1;
import com.oracle.truffle.api.test.polyglot.ContextAPITestLanguage.LanguageContext;
import com.oracle.truffle.api.test.polyglot.ValueAssert.Trait;
import org.graalvm.polyglot.PolyglotAccess;

public class ContextAPITest {
    private static HostAccess CONFIG;

    static LanguageContext langContext;

    @BeforeClass
    public static void initHostAccess() throws Exception {
        CONFIG = HostAccess.newBuilder().allowAccess(Runnable.class.getMethod("run")).allowAccessAnnotatedBy(HostAccess.Export.class).build();
    }

    @Test
    public void testEqualsAndHashcode() {
        Context context = Context.create();
        context.enter();

        Context currentContext = Context.getCurrent();
        assertEquals(context, currentContext);
        assertEquals(context.hashCode(), currentContext.hashCode());

        context.leave();
        context.close();
    }

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
        contextBuilder.build().close();
    }

    @Test
    public void testStableOption() {
        try (Context context = Context.newBuilder().option("optiontestlang1.StableOption", "Hello").build()) {
            context.initialize("optiontestlang1");
            context.enter();
            try {
                assertEquals("Hello", OptionTestLang1.getCurrentContext().getOptions().get(OptionTestLang1.StableOption));
            } finally {
                context.leave();
            }
        }
    }

    @Test
    public void testExperimentalOption() {
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("optiontestlang1.StringOption2", "Allow").build()) {
            context.initialize("optiontestlang1");
            context.enter();
            try {
                assertEquals("Allow", OptionTestLang1.getCurrentContext().getOptions().get(OptionTestLang1.StringOption2));
            } finally {
                context.leave();
            }
        }

        try (Context context = Context.newBuilder().allowAllAccess(true).option("optiontestlang1.StringOption2", "All access").build()) {
            context.initialize("optiontestlang1");
            context.enter();
            try {
                assertEquals("All access", OptionTestLang1.getCurrentContext().getOptions().get(OptionTestLang1.StringOption2));
            } finally {
                context.leave();
            }
        }
    }

    @Test
    public void testExperimentalOptionException() {
        ValueAssert.assertFails(() -> Context.newBuilder().option("optiontestlang1.StringOption2", "Hello").build(), IllegalArgumentException.class, e -> {
            assertEquals("Option 'optiontestlang1.StringOption2' is experimental and must be enabled with allowExperimentalOptions(). Do not use experimental options in production environments.",
                            e.getMessage());
        });
    }

    @Test
    public void testImageBuildTimeOptionAtRuntime() {
        ValueAssert.assertFails(() -> Context.newBuilder().option("image-build-time.DisablePrivileges", "createProcess").build(), IllegalArgumentException.class, e -> {
            assertEquals("Image build-time option 'image-build-time.DisablePrivileges' cannot be set at runtime", e.getMessage());
        });
    }

    @Test
    public void testInstrumentOptionAsContext() {
        // Instrument options are refused by context builders with an existing engine:
        Context.Builder contextBuilder = Context.newBuilder();
        Engine engine = Engine.create();
        contextBuilder.engine(engine);
        contextBuilder.option("optiontestinstr1.StringOption1", "Hello");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertEquals("Option optiontestinstr1.StringOption1 is an engine option. Engine level options can only be configured for contexts without a shared engine set. " +
                            "To resolve this, configure the option when creating the Engine or create a context without a shared engine.", ex.getMessage());
        }
        engine.close();
    }

    @Test
    public void testInvalidEngineOptionAsContext() {
        // Instrument options are refused by context builders with an existing engine:
        Context.Builder contextBuilder = Context.newBuilder();
        Engine engine = Engine.create();
        contextBuilder.engine(engine);
        contextBuilder.option("optiontestinstr1.StringOption1+Typo", "100");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertTrue(ex.getMessage().startsWith("Could not find option with name optiontestinstr1.StringOption1+Typo."));
        }
        engine.close();
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
        ContextAPITestLanguage.runinside = (env) -> new ProxyLegacyInteropObject() {
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

        ValueAssert.assertValue(bindings, Trait.MEMBERS);
    }

    public static class MyClass {

        @HostAccess.Export public Object field = "bar";

        @HostAccess.Export
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

    @ExportLibrary(InteropLibrary.class)
    static final class TopScope implements TruffleObject {

        Map<String, Object> values = new HashMap<>();

        @ExportMessage
        @TruffleBoundary
        Object readMember(String key) {
            return values.get(key);
        }

        @ExportMessage
        @TruffleBoundary
        Object writeMember(String key, Object value) {
            values.put(key, value);
            return value;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return values.containsKey(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberModifiable(String member) {
            return values.containsKey(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberInsertable(String member) {
            return !values.containsKey(member);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }
    }

    private static void testBindings(Context context) {
        TopScope values = new TopScope();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Iterable<Scope> findTopScopes(LanguageContext env) {
                return Arrays.asList(Scope.newBuilder("top", values).build());
            }
        });
        Value bindings = context.getBindings(ProxyLanguage.ID);

        testWritableBindings(bindings);

        ValueAssert.assertValue(bindings, Trait.MEMBERS);
    }

    @Test
    public void testEnteredContext() {
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);

        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();

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
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
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
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        Value v = context.asValue(new Runnable() {
            public void run() {
                Context innerContext = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
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
        Context context = Context.newBuilder().allowHostAccess(CONFIG).allowPolyglotAccess(PolyglotAccess.ALL).build();

        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        try {
                            return boundary();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @TruffleBoundary
                    private Object boundary() throws UnsupportedMessageException, UnsupportedTypeException, ArityException, UnknownIdentifierException {
                        Object o = InteropLibrary.getFactory().getUncached().readMember(ProxyLanguage.getCurrentContext().env.getPolyglotBindings(), "test");
                        return InteropLibrary.getFactory().getUncached().execute(o);
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

    @Test
    public void testContextBuilderAllAccess() {
        Context.Builder builder = Context.newBuilder();
        builder.allowAllAccess(true);
        try (Context context = builder.build()) {
            context.initialize(ProxyLanguage.ID);
            context.enter();
            TruffleLanguage.Env env = ProxyLanguage.getCurrentContext().getEnv();
            assertTrue("all access implies host access allowed", env.isHostLookupAllowed());
            assertTrue("all access implies native access allowed", env.isNativeAccessAllowed());
            assertTrue("all access implies create thread allowed", env.isCreateThreadAllowed());
            context.leave();
        }
        builder.allowAllAccess(false);
        try (Context context = builder.build()) {
            context.initialize(ProxyLanguage.ID);
            context.enter();
            TruffleLanguage.Env env = ProxyLanguage.getCurrentContext().getEnv();
            assertFalse("host access is disallowed by default", env.isHostLookupAllowed());
            assertFalse("native access is disallowed by default", env.isNativeAccessAllowed());
            assertFalse("thread creation is disallowed by default", env.isCreateThreadAllowed());
            context.leave();
        }
    }

    @Test
    public void testTimeZone() {
        ZoneId zone = ZoneId.of("UTC+1");
        Context context = Context.newBuilder().timeZone(zone).build();
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertEquals(zone, ProxyLanguage.getCurrentContext().getEnv().getTimeZone());
        context.leave();
        context.close();
    }

    @Test
    public void testDefaultTimeZone() {
        Context context = Context.create();
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertEquals(ZoneId.systemDefault(), ProxyLanguage.getCurrentContext().getEnv().getTimeZone());
        context.leave();
        context.close();
    }

}
