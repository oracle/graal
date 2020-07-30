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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.option.OptionProcessorTest.OptionTestLang1;
import com.oracle.truffle.api.test.polyglot.ContextAPITestLanguage.LanguageContext;
import com.oracle.truffle.tck.tests.ValueAssert;
import com.oracle.truffle.tck.tests.ValueAssert.Trait;

public class ContextAPITest extends AbstractPolyglotTest {
    private static HostAccess CONFIG;

    static LanguageContext langContext;

    @BeforeClass
    public static void initHostAccess() throws Exception {
        CONFIG = HostAccess.newBuilder().allowAccess(Runnable.class.getMethod("run")).allowAccessAnnotatedBy(HostAccess.Export.class).build();
    }

    public ContextAPITest() {
        // API test should not enter automatically by default for more control
        enterContext = false;
    }

    @Test
    public void testEvalErrors() {
        setupEnv();

        assertFails(() -> context.eval(null, null), NullPointerException.class);
        assertFails(() -> context.eval(ProxyLanguage.ID, null), NullPointerException.class);
        assertFails(() -> context.eval(null, ""), NullPointerException.class);
        assertFails(() -> context.eval("<<unknown-language>>", null), NullPointerException.class);
        assertFails(() -> context.eval("<<unknown-language>>", ""), IllegalArgumentException.class);

        Source src = Source.create("<<unknown-language>>", "");
        assertFails(() -> context.eval(src), IllegalArgumentException.class);
        assertFails(() -> context.eval(null), NullPointerException.class);
    }

    @Test
    public void testParseErrors() {
        setupEnv();

        assertFails(() -> context.parse(null, null), NullPointerException.class);
        assertFails(() -> context.parse(ProxyLanguage.ID, null), NullPointerException.class);
        assertFails(() -> context.parse(null, ""), NullPointerException.class);
        assertFails(() -> context.parse("<<unknown-language>>", null), NullPointerException.class);
        assertFails(() -> context.parse("<<unknown-language>>", ""), IllegalArgumentException.class);

        Source src = Source.create("<<unknown-language>>", "");
        assertFails(() -> context.parse(src), IllegalArgumentException.class);
        assertFails(() -> context.parse(null), NullPointerException.class);
    }

    @SuppressWarnings("serial")
    static class SyntaxError extends RuntimeException implements TruffleException {

        private final SourceSection location;

        SyntaxError(SourceSection location) {
            this.location = location;
        }

        public boolean isSyntaxError() {
            return true;
        }

        public Node getLocation() {
            return null;
        }

        public SourceSection getSourceLocation() {
            return location;
        }
    }

    @Test
    public void testParseBasic() {
        AtomicInteger parseCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        setupParseLang(Context.create(), parseCalls, executeCalls);

        assertEquals(0, parseCalls.get());
        assertEquals(0, executeCalls.get());

        context.parse(ProxyLanguage.ID, "42");
        assertEquals(1, parseCalls.get());
        assertEquals(0, executeCalls.get());

        context.parse(ProxyLanguage.ID, "42");
        assertEquals(1, parseCalls.get());
        assertEquals(0, executeCalls.get());

        context.eval(ProxyLanguage.ID, "42");
        assertEquals(1, parseCalls.get());
        assertEquals(1, executeCalls.get());

        Source uncachedSource = Source.newBuilder(ProxyLanguage.ID, "42", "uncached").cached(false).buildLiteral();
        context.parse(uncachedSource);
        assertEquals(2, parseCalls.get());
        assertEquals(1, executeCalls.get());

        context.parse(uncachedSource);
        assertEquals(3, parseCalls.get());
        assertEquals(1, executeCalls.get());

        context.eval(uncachedSource);
        assertEquals(4, parseCalls.get());
        assertEquals(2, executeCalls.get());

        assertFails(() -> context.parse(ProxyLanguage.ID, "error-1"), PolyglotException.class,
                        (e) -> {
                            assertTrue(e.isSyntaxError());
                            assertEquals("error", e.getSourceLocation().getCharacters());
                        });
        assertEquals(5, parseCalls.get());
        assertEquals(2, executeCalls.get());

        assertFails(() -> context.parse(ProxyLanguage.ID, "error-1"), PolyglotException.class,
                        (e) -> {
                            assertTrue(e.isSyntaxError());
                            assertEquals("error", e.getSourceLocation().getCharacters());
                        });
        assertEquals(6, parseCalls.get());
        assertEquals(2, executeCalls.get());
    }

    @Test
    public void testParseInteractive() {
        AtomicInteger parseCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        setupParseLang(Context.newBuilder().out(out).build(), parseCalls, executeCalls);

        assertEquals(0, parseCalls.get());
        assertEquals(0, executeCalls.get());

        Source source = Source.newBuilder(ProxyLanguage.ID, "42", "interactive").interactive(true).buildLiteral();
        Value v = context.parse(source);

        assertEquals(1, parseCalls.get());
        assertEquals(0, executeCalls.get());
        assertEquals(0, out.size());

        String lineFeed = System.getProperty("line.separator");
        v.execute();
        assertEquals(1, parseCalls.get());
        assertEquals(1, executeCalls.get());
        assertEquals("42" + lineFeed, new String(out.toByteArray(), StandardCharsets.UTF_8));

        v.execute();
        assertEquals(1, parseCalls.get());
        assertEquals(2, executeCalls.get());
        assertEquals("42" + lineFeed + "42" + lineFeed, new String(out.toByteArray()));
    }

    @Test
    public void testParseAndEval() {
        AtomicInteger parseCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        setupParseLang(Context.create(), parseCalls, executeCalls);

        assertEquals(0, parseCalls.get());
        assertEquals(0, executeCalls.get());

        Source source = Source.create(ProxyLanguage.ID, "42");
        Value parseResult = context.parse(source);
        assertEquals(1, parseCalls.get());
        assertEquals(0, executeCalls.get());

        assertTrue(parseResult.canExecute());
        assertEquals(parseResult, context.parse(source));
        assertNotSame(parseResult, context.parse(source));
        assertNotEquals(parseResult, context.parse(ProxyLanguage.ID, "43"));
        assertEquals("Parsed[Source=" + source.toString() + "]", parseResult.toString());
        ValueAssert.assertValue(parseResult);

        Value result = parseResult.execute();
        assertEquals(2, parseCalls.get());
        assertEquals(1, executeCalls.get());
        assertEquals(source.getCharacters(), result.asString());

        result = parseResult.execute();
        assertEquals(2, parseCalls.get());
        assertEquals(2, executeCalls.get());
        assertEquals(source.getCharacters(), result.asString());

        assertFails(() -> parseResult.execute(42), IllegalArgumentException.class);
        assertFails(() -> parseResult.execute(42, 42), IllegalArgumentException.class);

    }

    private void setupParseLang(Context context, AtomicInteger parseCalls, AtomicInteger executeCalls) {
        setupEnv(context, new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                parseCalls.incrementAndGet();

                if (request.getSource().getCharacters().toString().startsWith("error-")) {
                    throw new SyntaxError(request.getSource().createSection(0, 5));
                }

                return Truffle.getRuntime().createCallTarget(new RootNode(getCurrentLanguage()) {
                    private final com.oracle.truffle.api.source.Source source = request.getSource();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        executeCalls.incrementAndGet();
                        return source.getCharacters();
                    }
                });
            }
        });
    }

    @Test
    public void testEqualsAndHashcode() {
        setupEnv();
        context.enter();

        Context currentContext = Context.getCurrent();
        assertEquals(context, currentContext);
        assertEquals(context.hashCode(), currentContext.hashCode());

        context.leave();
    }

    @Test
    public void testCloseBeforeLeave() {
        for (int i = 0; i < 10; i++) {
            Context c = Context.create();
            for (int j = 0; j < i; j++) {
                c.enter();
            }
            c.close();
            // we have already left the context
            try {
                Context.getCurrent();
                fail();
            } catch (IllegalStateException e) {
            }
            for (int j = 0; j < i; j++) {
                // additional leave calls are allowed
                // this allows to simplify some error recovery code
                c.leave();
            }
        }
    }

    @Test
    public void testContextCreateSingleLanguage() {
        Context c = Context.create(ContextAPITestLanguage.ID);
        try {
            c.eval(LanguageSPITestLanguage.ID, "");
            fail();
        } catch (IllegalArgumentException e) {
        }
        assertInternalNotAccessible(c);
        c.close();
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
        setupEnv();
        context.eval(ContextAPITestLanguage.ID, "");
        context.eval(LanguageSPITestLanguage.ID, "");
        assertInternalNotAccessible(context);
    }

    @Test
    public void testImportExport() {
        setupEnv();
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
        try (Context c = Context.newBuilder().option("optiontestlang1.StableOption", "Hello").build()) {
            c.initialize("optiontestlang1");
            c.enter();
            try {
                assertEquals("Hello", OptionTestLang1.getCurrentContext().getOptions().get(OptionTestLang1.StableOption));
            } finally {
                c.leave();
            }
        }
    }

    @Test
    public void testExperimentalOption() {
        try (Context c = Context.newBuilder().allowExperimentalOptions(true).option("optiontestlang1.StringOption2", "Allow").build()) {
            c.initialize("optiontestlang1");
            c.enter();
            try {
                assertEquals("Allow", OptionTestLang1.getCurrentContext().getOptions().get(OptionTestLang1.StringOption2));
            } finally {
                c.leave();
            }
        }

        try (Context c = Context.newBuilder().allowAllAccess(true).option("optiontestlang1.StringOption2", "All access").build()) {
            c.initialize("optiontestlang1");
            c.enter();
            try {
                assertEquals("All access", OptionTestLang1.getCurrentContext().getOptions().get(OptionTestLang1.StringOption2));
            } finally {
                c.leave();
            }
        }
    }

    @Test
    public void testExperimentalOptionException() {
        Assume.assumeFalse(Boolean.getBoolean("polyglot.engine.AllowExperimentalOptions"));
        AbstractPolyglotTest.assertFails(() -> Context.newBuilder().option("optiontestlang1.StringOption2", "Hello").build(), IllegalArgumentException.class, e -> {
            assertEquals("Option 'optiontestlang1.StringOption2' is experimental and must be enabled with allowExperimentalOptions(). Do not use experimental options in production environments.",
                            e.getMessage());
        });
    }

    @Test
    public void testImageBuildTimeOptionAtRuntime() {
        AbstractPolyglotTest.assertFails(() -> Context.newBuilder().option("image-build-time.DisablePrivileges", "createProcess").build(), IllegalArgumentException.class, e -> {
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
        setupEnv();
        testEnterLeave(context, 0);
    }

    private static void testEnterLeave(Context context, int depth) {
        try {
            context.leave();
            fail();
        } catch (IllegalStateException e) {
        }

        context.getPolyglotBindings().getMember("");
        context.enter();

        context.getPolyglotBindings().getMember("");
        context.enter();

        if (depth < 3) {
            Context innerContext = Context.create();
            testEnterLeave(innerContext, depth + 1);
            innerContext.close();
        }

        context.leave();

        context.leave();

        try {
            context.leave();
            fail();
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void testMultithreadedEnterLeave() throws InterruptedException, ExecutionException {
        Context c = Context.create();
        Set<Reference<Thread>> threads = new HashSet<>();
        int[] counter = {1};
        ExecutorService service = Executors.newFixedThreadPool(20, (run) -> {
            class CollectibleThread extends Thread {
                CollectibleThread(Runnable target) {
                    super(target, "pool-" + counter[0]++);
                }
            }
            Thread t = new CollectibleThread(run);
            threads.add(new WeakReference<>(t));
            return t;
        });

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            futures.add(service.submit(() -> testEnterLeave(c, 0)));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        service.shutdown();
        service.awaitTermination(1000, TimeUnit.MILLISECONDS);
        Reference<ExecutorService> ref = new WeakReference<>(service);
        service = null;
        GCUtils.assertGc("Nobody holds on the executor anymore", ref);
        for (Reference<Thread> t : threads) {
            GCUtils.assertGc("Nobody holds on the thread anymore", t);
        }
        c.close();
    }

    @Test
    public void testEnteredExecute() {
        Context c1 = Context.create(ContextAPITestLanguage.ID);

        // test outside
        testExecute(c1);

        // test inside
        c1.enter();
        testExecute(c1);
        c1.leave();

        // test enter twice
        c1.enter();
        c1.enter();
        testExecute(c1);
        c1.leave();
        testExecute(c1);
        c1.leave();
        testExecute(c1);

        // test entered with inner context
        c1.enter();

        Context c2 = Context.create(ContextAPITestLanguage.ID);
        testExecute(c2);
        c2.enter();
        testExecute(c2);
        c2.leave();
        c2.enter();
        c2.enter();
        testExecute(c2);
        c2.leave();
        testExecute(c2);
        c2.leave();
        c2.close();

        c1.leave();

        // finally close the context
        c1.close();
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static final class ContextTestFunction implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            return 42;
        }

    }

    private static void testExecute(Context context) {
        ContextAPITestLanguage.runinside = (env) -> new ContextTestFunction();
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

        Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();

        assertFails(() -> Context.getCurrent(), IllegalStateException.class);

        c.enter();

        testGetContext(c);

        c.leave();

        assertFails(() -> Context.getCurrent(), IllegalStateException.class);

        c.close();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
    }

    @Test
    public void testEnteredContextInJava() {
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        Value v = c.asValue(new Runnable() {
            public void run() {
                testGetContext(c);

                Value.asValue(new Runnable() {
                    public void run() {
                        testGetContext(c);
                    }
                }).execute();
            }
        });
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        v.execute();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        c.close();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
    }

    @Test
    public void testChangeContextInJava() {
        setupEnv(Context.newBuilder().allowHostAccess(HostAccess.ALL).build());
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

    @Test
    public void testTransferControlToOtherThreadWhileEntered() {
        setupEnv(Context.newBuilder().allowHostAccess(CONFIG).allowPolyglotAccess(PolyglotAccess.ALL).build(),
                        new ProxyLanguage() {
                            @Override
                            protected CallTarget parse(ParsingRequest request) throws Exception {
                                return Truffle.getRuntime().createCallTarget(new RootNode(getCurrentLanguage()) {
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
                                        Object o = InteropLibrary.getUncached().readMember(ProxyLanguage.getCurrentContext().env.getPolyglotBindings(), "test");
                                        return InteropLibrary.getUncached().execute(o);
                                    }
                                });
                            }
                        });
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
        try (Context c = builder.build()) {
            c.initialize(ProxyLanguage.ID);
            c.enter();
            TruffleLanguage.Env env = ProxyLanguage.getCurrentContext().getEnv();
            assertTrue("all access implies host access allowed", env.isHostLookupAllowed());
            assertTrue("all access implies native access allowed", env.isNativeAccessAllowed());
            assertTrue("all access implies create thread allowed", env.isCreateThreadAllowed());
            c.leave();
        }
        builder.allowAllAccess(false);
        try (Context c = builder.build()) {
            c.initialize(ProxyLanguage.ID);
            c.enter();
            TruffleLanguage.Env env = ProxyLanguage.getCurrentContext().getEnv();
            assertFalse("host access is disallowed by default", env.isHostLookupAllowed());
            assertFalse("native access is disallowed by default", env.isNativeAccessAllowed());
            assertFalse("thread creation is disallowed by default", env.isCreateThreadAllowed());
            c.leave();
        }
    }

    @Test
    public void testTimeZone() {
        ZoneId zone = ZoneId.of("UTC+1");
        Context c = Context.newBuilder().timeZone(zone).build();
        c.initialize(ProxyLanguage.ID);
        c.enter();
        assertEquals(zone, ProxyLanguage.getCurrentContext().getEnv().getTimeZone());
        c.leave();
        c.close();
    }

    @Test
    public void testDefaultTimeZone() {
        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        c.enter();
        assertEquals(ZoneId.systemDefault(), ProxyLanguage.getCurrentContext().getEnv().getTimeZone());
        c.leave();
        c.close();
    }

    @Test
    public void testClose() {
        Context c = Context.newBuilder().allowAllAccess(true).build();
        c.enter();
        Value bindings = c.getBindings(ContextAPITestLanguage.ID);
        Value polyglotBindings = c.getPolyglotBindings();
        Map<String, Object> fields = new HashMap<>();
        fields.put("x", 1);
        fields.put("y", 2);
        Value object = c.asValue(ProxyObject.fromMap(fields));
        Value array = c.asValue(ProxyArray.fromArray(1, 2));
        Value fnc = c.asValue(new ProxyExecutable() {
            @Override
            public Object execute(Value... arguments) {
                return true;
            }
        });
        c.close();

        assertFails(() -> c.asValue(1), IllegalStateException.class);
        assertFails(() -> c.enter(), IllegalStateException.class);
        assertFails(() -> c.eval(ContextAPITestLanguage.ID, ""), IllegalStateException.class);
        assertFails(() -> c.initialize(ContextAPITestLanguage.ID), IllegalStateException.class);
        assertFails(() -> c.getBindings(ContextAPITestLanguage.ID), IllegalStateException.class);
        assertFails(() -> c.getPolyglotBindings(), IllegalStateException.class);

        assertFails(() -> bindings.hasMembers(), IllegalStateException.class);
        assertFails(() -> polyglotBindings.putMember("d", 1), IllegalStateException.class);
        assertFails(() -> object.as(Map.class), IllegalStateException.class);
        assertFails(() -> object.putMember("x", 0), IllegalStateException.class);
        assertFails(() -> array.as(List.class), IllegalStateException.class);
        assertFails(() -> array.setArrayElement(0, 3), IllegalStateException.class);
        assertFails(() -> fnc.execute(), IllegalStateException.class);
    }

    @Test
    public void testDefaultContextClassLoader() {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader cc = new URLClassLoader(new URL[0]);
            Thread.currentThread().setContextClassLoader(cc);
            try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
                testContextClassLoaderImpl(c, cc);
                cc = new URLClassLoader(new URL[0]);
                Thread.currentThread().setContextClassLoader(cc);
                testContextClassLoaderImpl(c, cc);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(orig);
        }
    }

    @Test
    public void testExplicitContextClassLoader() {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader hostClassLoader = new URLClassLoader(new URL[0]);
            try (Context c = Context.newBuilder().hostClassLoader(hostClassLoader).allowHostAccess(HostAccess.ALL).build()) {
                testContextClassLoaderImpl(c, hostClassLoader);
                ClassLoader contextClassLoader = new URLClassLoader(new URL[0]);
                Thread.currentThread().setContextClassLoader(contextClassLoader);
                testContextClassLoaderImpl(c, hostClassLoader);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(orig);
        }
    }

    @Test
    public void testExplicitContextClassLoaderMultipleContexts() {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader hostClassLoaderCtx1 = new URLClassLoader(new URL[0]);
            ClassLoader hostClassLoaderCtx2 = new URLClassLoader(new URL[0]);
            try (Context context1 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx1).allowHostAccess(HostAccess.ALL).build()) {
                try (Context context2 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx2).allowHostAccess(HostAccess.ALL).build()) {
                    try (Context context3 = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
                        testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                        testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                        testContextClassLoaderImpl(context3, orig);
                        ClassLoader contextClassLoader = new URLClassLoader(new URL[0]);
                        Thread.currentThread().setContextClassLoader(contextClassLoader);
                        testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                        testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                        testContextClassLoaderImpl(context3, contextClassLoader);
                    }
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(orig);
        }
    }

    @Test
    public void testExplicitContextClassLoaderNestedContexts() {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader hostClassLoaderCtx1 = new URLClassLoader(new URL[0]);
            ClassLoader hostClassLoaderCtx2 = new URLClassLoader(new URL[0]);
            try (Context context1 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx1).allowHostAccess(HostAccess.ALL).build()) {
                try (Context context2 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx2).allowHostAccess(HostAccess.ALL).build()) {
                    testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                    testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                    context1.enter();
                    testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                    testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                    try {
                        context2.enter();
                        testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                        testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                        try {
                            testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                            testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                        } finally {
                            context2.leave();
                        }
                        testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                        testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                    } finally {
                        context1.leave();
                    }
                    testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                    testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(orig);
        }
    }

    @Test
    public void testExplicitContextClassLoaderNestedContexts2() {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader hostClassLoaderCtx1 = new URLClassLoader(new URL[0]);
            ClassLoader hostClassLoaderCtx2 = new URLClassLoader(new URL[0]);
            ClassLoader hostClassLoaderCtx3 = new URLClassLoader(new URL[0]);
            try (Context context1 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx1).allowHostAccess(HostAccess.ALL).build()) {
                try (Context context2 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx2).allowHostAccess(HostAccess.ALL).build()) {
                    try (Context context3 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx3).allowHostAccess(HostAccess.ALL).build()) {
                        context1.enter();
                        try {
                            context2.enter();
                            try {
                                context3.enter();
                                try {
                                    testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                                    testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                                    testContextClassLoaderImpl(context3, hostClassLoaderCtx3);
                                } finally {
                                    context3.leave();
                                }
                                testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                                testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                                testContextClassLoaderImpl(context3, hostClassLoaderCtx3);
                            } finally {
                                context2.leave();
                            }
                            testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                            testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                            testContextClassLoaderImpl(context3, hostClassLoaderCtx3);
                        } finally {
                            context1.leave();
                        }
                        testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                        testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                        testContextClassLoaderImpl(context3, hostClassLoaderCtx3);
                    }
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(orig);
        }
    }

    @Test
    public void testExplicitContextClassLoaderNestedContexts3() {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader hostClassLoaderCtx1 = new URLClassLoader(new URL[0]);
            ClassLoader hostClassLoaderCtx2 = new URLClassLoader(new URL[0]);
            ClassLoader customClassLoader = new URLClassLoader(new URL[0]);
            try (Context context1 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx1).allowHostAccess(HostAccess.ALL).build()) {
                try (Context context2 = Context.newBuilder().hostClassLoader(hostClassLoaderCtx2).allowHostAccess(HostAccess.ALL).build()) {
                    context1.enter();
                    try {
                        testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                        testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                        ClassLoader prev = Thread.currentThread().getContextClassLoader();
                        Thread.currentThread().setContextClassLoader(customClassLoader);
                        try {
                            context2.enter();
                            try {
                                testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                                testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                            } finally {
                                context2.leave();
                            }
                            assertEquals(customClassLoader, Thread.currentThread().getContextClassLoader());
                        } finally {
                            Thread.currentThread().setContextClassLoader(prev);
                        }
                        testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                        testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                    } finally {
                        context1.leave();
                    }
                    testContextClassLoaderImpl(context1, hostClassLoaderCtx1);
                    testContextClassLoaderImpl(context2, hostClassLoaderCtx2);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(orig);
        }
    }

    private static void testContextClassLoaderImpl(Context context, ClassLoader expectedContextClassLoader) {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(this.languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        assertEquals(expectedContextClassLoader, Thread.currentThread().getContextClassLoader());
                        return true;
                    }
                });
            }
        });
        context.eval(Source.newBuilder(ProxyLanguage.ID, "", "test").cached(false).buildLiteral());
    }

}
