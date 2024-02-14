/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;
import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.parseTestLanguage;
import static com.oracle.truffle.api.test.common.TestUtils.getDefaultLanguageId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
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
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import com.oracle.truffle.tck.tests.ValueAssert;
import com.oracle.truffle.tck.tests.ValueAssert.Trait;

public class ContextAPITest extends AbstractPolyglotTest {
    private static HostAccess CONFIG;

    @BeforeClass
    public static void initHostAccess() throws Exception {
        CONFIG = HostAccess.newBuilder().//
                        allowAccess(Runnable.class.getMethod("run")).//
                        allowAccessAnnotatedBy(HostAccess.Export.class).//
                        allowAccessInheritance(true).build();
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
        assertFails(() -> parseTestLanguage(context, ParseLang.class, (CharSequence) null), NullPointerException.class);
        assertFails(() -> context.parse(null, ""), NullPointerException.class);
        assertFails(() -> context.parse("<<unknown-language>>", null), NullPointerException.class);
        assertFails(() -> context.parse("<<unknown-language>>", ""), IllegalArgumentException.class);

        Source src = Source.create("<<unknown-language>>", "");
        assertFails(() -> context.parse(src), IllegalArgumentException.class);
        assertFails(() -> context.parse(null), NullPointerException.class);
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static class SyntaxError extends AbstractTruffleException {

        private final SourceSection location;

        SyntaxError(SourceSection location) {
            this.location = location;
        }

        @ExportMessage
        ExceptionType getExceptionType() {
            return ExceptionType.PARSE_ERROR;
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return location != null;
        }

        @ExportMessage(name = "getSourceLocation")
        SourceSection getSourceSection() throws UnsupportedMessageException {
            if (location == null) {
                throw UnsupportedMessageException.create();
            }
            return location;
        }
    }

    @Test
    public void testParseBasic() {
        AtomicInteger parseCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        setupEnv(Context.newBuilder().allowHostAccess(HostAccess.ALL).build());

        assertEquals(0, parseCalls.get());
        assertEquals(0, executeCalls.get());

        parseTestLanguage(context, ParseLang.class, "42", parseCalls, executeCalls);
        assertEquals(1, parseCalls.get());
        assertEquals(0, executeCalls.get());

        parseTestLanguage(context, ParseLang.class, "42", parseCalls, executeCalls);
        assertEquals(1, parseCalls.get());
        assertEquals(0, executeCalls.get());

        evalTestLanguage(context, ParseLang.class, "42", parseCalls, executeCalls);
        assertEquals(1, parseCalls.get());
        assertEquals(1, executeCalls.get());

        Source uncachedSource = Source.newBuilder(ParseLang.ID, "42", "uncached").cached(false).buildLiteral();
        context.parse(uncachedSource);
        assertEquals(2, parseCalls.get());
        assertEquals(1, executeCalls.get());

        context.parse(uncachedSource);
        assertEquals(3, parseCalls.get());
        assertEquals(1, executeCalls.get());

        context.eval(uncachedSource);
        assertEquals(4, parseCalls.get());
        assertEquals(2, executeCalls.get());

        assertFails(() -> parseTestLanguage(context, ParseLang.class, "error-1", parseCalls, executeCalls), PolyglotException.class,
                        (e) -> {
                            assertTrue(e.isSyntaxError());
                            assertEquals("error", e.getSourceLocation().getCharacters());
                        });
        assertEquals(5, parseCalls.get());
        assertEquals(2, executeCalls.get());

        assertFails(() -> parseTestLanguage(context, ParseLang.class, "error-1", parseCalls, executeCalls), PolyglotException.class,
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
        setupEnv(Context.newBuilder().out(out).allowHostAccess(HostAccess.ALL).build());

        assertEquals(0, parseCalls.get());
        assertEquals(0, executeCalls.get());

        Source source = Source.newBuilder(ParseLang.ID, "42", "interactive").interactive(true).buildLiteral();
        Value v = parseTestLanguage(context, ParseLang.class, source, parseCalls, executeCalls);

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
        StringBuilder callArguments = new StringBuilder();
        setupEnv(Context.newBuilder().allowHostAccess(HostAccess.ALL).build());

        assertEquals(0, parseCalls.get());
        assertEquals(0, executeCalls.get());

        Source source = Source.create(ParseLang.ID, "42");
        Value parseResult = parseTestLanguage(context, ParseLang.class, source, parseCalls, executeCalls, callArguments);
        assertEquals(1, parseCalls.get());
        assertEquals(0, executeCalls.get());

        assertTrue(parseResult.canExecute());
        assertEquals(parseResult, context.parse(source));
        assertNotSame(parseResult, context.parse(source));
        assertNotEquals(parseResult, context.parse(ParseLang.ID, "43"));
        assertEquals("Parsed[Source=" + source.toString() + "]", parseResult.toString());
        ValueAssert.assertValue(parseResult);

        Value result = parseResult.execute();
        assertEquals(2, parseCalls.get());
        assertEquals(1, executeCalls.get());
        assertEquals("[]", callArguments.toString());
        assertEquals(source.getCharacters(), result.asString());
        callArguments.delete(0, callArguments.length());

        result = parseResult.execute();
        assertEquals(2, parseCalls.get());
        assertEquals(2, executeCalls.get());
        assertEquals("[]", callArguments.toString());
        assertEquals(source.getCharacters(), result.asString());
        callArguments.delete(0, callArguments.length());

        // The parsed call target accepts arguments
        parseResult.execute(42);
        assertEquals("[42]", callArguments.toString());
        callArguments.delete(0, callArguments.length());
        parseResult.execute(42, 43);
        assertEquals("[42, 43]", callArguments.toString());
        callArguments.delete(0, callArguments.length());

    }

    @TruffleLanguage.Registration
    static class ParseLang extends AbstractExecutableTestLanguage {
        static final String ID = getDefaultLanguageId(ParseLang.class);

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
            Object parseCalls = contextArguments[0];
            InteropLibrary.getUncached().invokeMember(parseCalls, "incrementAndGet");

            if (request.getSource().getCharacters().toString().startsWith("error-")) {
                throw new SyntaxError(request.getSource().createSection(0, 5));
            }
        }

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object executeCalls = contextArguments[1];
            interop.invokeMember(executeCalls, "incrementAndGet");
            if (contextArguments.length > 2) {
                Object callArguments = contextArguments[2];
                interop.invokeMember(callArguments, "append", Arrays.toString(frameArguments));
            }
            return node.getSourceSection().getSource().getCharacters();
        }

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
        Context.Builder contextBuilder = Context.newBuilder().option("engine.WarnOptionDeprecation", "false");
        contextBuilder.option("optiontestinstr1.StringOption1", "Hello");
        contextBuilder.build().close();
    }

    @TruffleLanguage.Registration
    public static class TestOptionLanguage extends AbstractExecutableTestLanguage {
        static final String ID = getDefaultLanguageId(TestOptionLanguage.class);

        @Option(help = "Stable Option Help", category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<String> StableOption = new OptionKey<>("stable");

        @Option(help = "StringOption help%nwith newline", category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
        public static final OptionKey<String> StringOption = new OptionKey<>("defaultValue");

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            int assertToExecute = (Integer) contextArguments[0];
            switch (assertToExecute) {
                case 0:
                    assertEquals("Hello", env.getOptions().get(StableOption));
                    break;
                case 1:
                    assertEquals("Allow", env.getOptions().get(StringOption));
                    break;
                case 2:
                    assertEquals("All access", env.getOptions().get(StringOption));
                    break;
                default:
                    fail();
                    break;
            }
            return null;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new TestOptionLanguageOptionDescriptors();
        }
    }

    @Test
    public void testStableOption() {
        try (Context c = Context.newBuilder().option(TestOptionLanguage.ID + ".StableOption", "Hello").build()) {
            evalTestLanguage(c, TestOptionLanguage.class, "", 0);
        }
    }

    @Test
    public void testExperimentalOption() {
        try (Context c = Context.newBuilder().allowExperimentalOptions(true).option(TestOptionLanguage.ID + ".StringOption", "Allow").build()) {
            evalTestLanguage(c, TestOptionLanguage.class, "", 1);
        }

        try (Context c = Context.newBuilder().allowAllAccess(true).option(TestOptionLanguage.ID + ".StringOption", "All access").build()) {
            evalTestLanguage(c, TestOptionLanguage.class, "", 2);
        }
    }

    @Test
    public void testExperimentalOptionException() {
        Assume.assumeFalse(Boolean.getBoolean("polyglot.engine.AllowExperimentalOptions"));
        AbstractPolyglotTest.assertFails(() -> Context.newBuilder().option(TestOptionLanguage.ID + ".StringOption", "Hello").build(), IllegalArgumentException.class, e -> {
            assertEquals("Option '" + TestOptionLanguage.ID +
                            ".StringOption' is experimental and must be enabled with allowExperimentalOptions(boolean) in Context.Builder or Engine.Builder. Do not use experimental options in production environments.",
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
        Engine engine = Engine.newBuilder().option("engine.WarnOptionDeprecation", "false").build();
        contextBuilder.engine(engine);
        contextBuilder.option("optiontestinstr1.StringOption1", "Hello");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertEquals("Option optiontestinstr1.StringOption1 is an engine level instrument option. Engine level instrument options can only be configured for contexts without an explicit engine set. " +
                            "To resolve this, configure the option when creating the Engine or create a context without a shared engine.", ex.getMessage());
        }
        engine.close();
    }

    @Test
    public void testInvalidEngineOptionAsContext() {
        // Instrument options are refused by context builders with an existing engine:
        Context.Builder contextBuilder = Context.newBuilder();
        Engine engine = Engine.newBuilder().option("engine.WarnOptionDeprecation", "false").build();
        contextBuilder.engine(engine);
        contextBuilder.option("optiontestinstr1.StringOption1+Typo", "100");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Could not find option with name optiontestinstr1.StringOption1+Typo."));
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
        assertTrue("Executor threads did not finish in time.", service.awaitTermination(10, TimeUnit.SECONDS));
        for (Reference<Thread> threadRef : threads) {
            Thread t = threadRef.get();
            if (t != null) {
                t.join(10000);
            }
        }
        Reference<ExecutorService> ref = new WeakReference<>(service);
        service = null;
        GCUtils.assertGc("Nobody holds on the executor anymore", ref);
        GCUtils.assertGc("Nobody holds on the thread anymore", threads);
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

    private static void testExecute(Context context) {
        Value executable = evalTestLanguage(context, ContextAPITestLanguage.class, "", "return test function");
        assertEquals(42, executable.execute().asInt());
        assertEquals(42, executable.execute(42).asInt());
        executable.executeVoid();
        executable.executeVoid(42);
    }

    private static void testPolyglotBindings(Context context) {
        assertEquals("is stable", context.getPolyglotBindings(), context.getPolyglotBindings());

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

    private static void testBindings(Context context) {
        Value bindings = context.getBindings(ContextAPITestLanguage.ID);

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

    static class TestGetContextRunnable implements Runnable {
        private final Context c;
        private final boolean executeNested;

        TestGetContextRunnable(Context c, boolean executeNested) {
            this.c = c;
            this.executeNested = executeNested;
        }

        @Override
        public void run() {
            testGetContext(c);
            if (executeNested) {
                Value.asValue(new TestGetContextRunnable(c, false)).execute();
            }
        }
    }

    @Test
    public void testEnteredContextInJava() {
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        Value v = c.asValue(new TestGetContextRunnable(c, true));
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

    @TruffleLanguage.Registration
    static class TestTransferControlLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            try {
                Object o = interop.readMember(env.getPolyglotBindings(), "test");
                return interop.execute(o);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testTransferControlToOtherThreadWhileEntered() {
        setupEnv(Context.newBuilder().allowHostAccess(CONFIG).allowPolyglotAccess(PolyglotAccess.ALL).build());
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
                                evalTestLanguage(context, TestTransferControlLanguage.class, "");
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
        evalTestLanguage(context, TestTransferControlLanguage.class, "");
        context.leave();
        context.close();
        assertEquals(3, depth.get());
    }

    @TruffleLanguage.Registration
    static class ContextBuilderAllAccessLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            Boolean testValid = (Boolean) contextArguments[0];

            assertEquals(testValid, env.isHostLookupAllowed());
            assertEquals(testValid, env.isNativeAccessAllowed());
            assertEquals(testValid, env.isCreateThreadAllowed());
            return null;
        }
    }

    @Test
    public void testContextBuilderAllAccess() {
        Context.Builder builder = Context.newBuilder();
        builder.allowAllAccess(true);
        try (Context c = builder.build()) {
            evalTestLanguage(c, ContextBuilderAllAccessLanguage.class, "", Boolean.TRUE);
        }
        builder.allowAllAccess(false);
        try (Context c = builder.build()) {
            evalTestLanguage(c, ContextBuilderAllAccessLanguage.class, "", Boolean.FALSE);
        }
    }

    @TruffleLanguage.Registration
    static class TestTimeZoneLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            String zoneId = (String) contextArguments[0];
            assertEquals(ZoneId.of(zoneId), env.getTimeZone());
            return null;
        }
    }

    @Test
    public void testTimeZone() {
        String zoneId = "UTC+1";
        ZoneId zone = ZoneId.of(zoneId);
        try (Context c = Context.newBuilder().timeZone(zone).build()) {
            evalTestLanguage(c, TestTimeZoneLanguage.class, "", zoneId);
        }
    }

    @Test
    public void testDefaultTimeZone() {
        try (Context c = Context.create()) {
            evalTestLanguage(c, TestTimeZoneLanguage.class, "", ZoneId.systemDefault().getId());
        }
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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
                return new RootNode(this.languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        executeSlowPath();
                        return true;
                    }

                    @TruffleBoundary
                    private void executeSlowPath() {
                        assertEquals(expectedContextClassLoader, Thread.currentThread().getContextClassLoader());
                    }
                }.getCallTarget();
            }
        });
        context.eval(Source.newBuilder(ProxyLanguage.ID, "", "test").cached(false).buildLiteral());
    }

    @Test
    public void testGetCurrentContextNotEnteredRaceCondition() throws ExecutionException, InterruptedException {
        // TODO GR-47643 too slow with isolates
        TruffleTestAssumptions.assumeWeakEncapsulation();

        for (int i = 0; i < 10000; i++) {
            AtomicBoolean checkCompleted = new AtomicBoolean();
            ExecutorService executorService = Executors.newFixedThreadPool(1);
            try (Context ctx = Context.create()) {
                ctx.enter();
                try {
                    Future<?> future = executorService.submit(() -> {
                        Context.create().close();
                        checkCompleted.set(true);
                    });
                    while (!checkCompleted.get()) {
                        Context.getCurrent();
                    }
                    future.get();
                } finally {
                    ctx.leave();
                }
            } finally {
                executorService.shutdownNow();
                executorService.awaitTermination(100, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void testGetCurrentContextEnteredRaceCondition() throws ExecutionException, InterruptedException {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        for (int i = 0; i < 10000; i++) {
            AtomicBoolean checkCompleted = new AtomicBoolean();
            ExecutorService executorService = Executors.newFixedThreadPool(1);

            try (Context ctx = Context.create()) {
                ctx.initialize(ValidExclusiveLanguage.ID);
                ctx.enter();
                try {
                    ValidExclusiveLanguage lang = ValidExclusiveLanguage.REFERENCE.get(null);
                    Future<?> future = executorService.submit(() -> {
                        Context.create().close();
                        checkCompleted.set(true);
                    });
                    while (!checkCompleted.get()) {
                        lang.contextLocal.get();
                    }
                    future.get();
                } finally {
                    ctx.leave();
                }
            } finally {
                executorService.shutdownNow();
                executorService.awaitTermination(100, TimeUnit.SECONDS);
            }
        }
    }

    @TruffleLanguage.Registration
    public static class ValidExclusiveLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = getDefaultLanguageId(ValidExclusiveLanguage.class);

        final ContextLocal<Env> contextLocal = locals.createContextLocal((e) -> e);

        @Override
        protected TruffleLanguage.Env createContext(TruffleLanguage.Env env) {
            return env;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        static final LanguageReference<ValidExclusiveLanguage> REFERENCE = LanguageReference.create(ValidExclusiveLanguage.class);

    }

    @TruffleLanguage.Registration
    static class TestPermittedLanguagesLanguage extends TruffleLanguage<Object> {
        static final String ID = getDefaultLanguageId(TestPermittedLanguagesLanguage.class);

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return RootNode.createConstantNode(42).getCallTarget();
        }
    }

    @Test
    public void testPermittedLanguages() {
        try (Context c = Context.newBuilder().build()) {
            c.eval(ContextAPITestLanguage.ID, "");
            c.eval(TestPermittedLanguagesLanguage.ID, "");
            AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
        }

        try (Context c = Context.create(ContextAPITestLanguage.ID)) {
            c.eval(ContextAPITestLanguage.ID, "");
            AbstractPolyglotTest.assertFails(() -> c.eval(TestPermittedLanguagesLanguage.ID, ""), IllegalArgumentException.class);
            AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
        }

        try (Context c = Context.newBuilder(TestPermittedLanguagesLanguage.ID).build()) {
            c.eval(TestPermittedLanguagesLanguage.ID, "");
            AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestLanguage.ID, ""), IllegalArgumentException.class);
            AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
        }

        try (Engine e = Engine.create()) {
            try (Context c = Context.newBuilder(TestPermittedLanguagesLanguage.ID).engine(e).build()) {
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestLanguage.ID, ""), IllegalArgumentException.class);
                c.eval(TestPermittedLanguagesLanguage.ID, "");
            }
        }

        try (Engine e = Engine.create()) {
            try (Context c = Context.newBuilder(ContextAPITestLanguage.ID).engine(e).build()) {
                AbstractPolyglotTest.assertFails(() -> c.eval(TestPermittedLanguagesLanguage.ID, ""), IllegalArgumentException.class);
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
                c.eval(ContextAPITestLanguage.ID, "");
            }
        }

        try (Engine e = Engine.create()) {
            try (Context c = Context.newBuilder().engine(e).build()) {
                c.eval(ContextAPITestLanguage.ID, "");
                c.eval(TestPermittedLanguagesLanguage.ID, "");
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
            }
        }

        try (Engine e = Engine.create(TestPermittedLanguagesLanguage.ID)) {
            try (Context c = Context.newBuilder(TestPermittedLanguagesLanguage.ID).engine(e).build()) {
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestLanguage.ID, ""), IllegalArgumentException.class);
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
                c.eval(TestPermittedLanguagesLanguage.ID, "");
            }
        }

        // restricted languages are inherited from the engine if not further specified in the
        // context
        try (Engine e = Engine.create(TestPermittedLanguagesLanguage.ID)) {
            try (Context c = Context.newBuilder().engine(e).build()) {
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestLanguage.ID, ""), IllegalArgumentException.class);
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
                c.eval(TestPermittedLanguagesLanguage.ID, "");
            }
        }

        try (Engine e = Engine.newBuilder(TestPermittedLanguagesLanguage.ID, ContextAPITestLanguage.ID).build()) {
            try (Context c = Context.newBuilder().engine(e).build()) {
                c.eval(ContextAPITestLanguage.ID, "");
                c.eval(TestPermittedLanguagesLanguage.ID, "");
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
            }
        }

        try (Engine e = Engine.newBuilder(TestPermittedLanguagesLanguage.ID, ContextAPITestLanguage.ID).build()) {
            try (Context c = Context.newBuilder(TestPermittedLanguagesLanguage.ID).engine(e).build()) {
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestLanguage.ID, ""), IllegalArgumentException.class);
                AbstractPolyglotTest.assertFails(() -> c.eval(ContextAPITestInternalLanguage.ID, ""), IllegalArgumentException.class);
                c.eval(TestPermittedLanguagesLanguage.ID, "");
            }
        }

        try (Engine e = Engine.newBuilder(TestPermittedLanguagesLanguage.ID).build()) {
            Context.Builder b = Context.newBuilder(ContextAPITestLanguage.ID).engine(e);
            // fails language id is nost specified by the engine
            AbstractPolyglotTest.assertFails(() -> b.build(), IllegalArgumentException.class);
        }

        try (Engine e = Engine.newBuilder(TestPermittedLanguagesLanguage.ID).build()) {
            Context.Builder b = Context.newBuilder(ContextAPITestInternalLanguage.ID).engine(e);
            AbstractPolyglotTest.assertFails(() -> b.build(), IllegalArgumentException.class);
        }

        AbstractPolyglotTest.assertFails(() -> Context.create((String) null), NullPointerException.class);
        AbstractPolyglotTest.assertFails(() -> Engine.create((String) null), NullPointerException.class);

    }

}
