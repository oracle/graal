/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.test.ReflectionUtils;
import org.graalvm.collections.Pair;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ThreadsActivationListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import com.oracle.truffle.api.test.polyglot.InternalResourceTest.TemporaryResourceCacheRoot;

public class ContextPreInitializationTest {

    static final String FIRST = "ContextPreInitializationFirst";
    static final String SECOND = "ContextPreInitializationSecond";
    static final String INTERNAL = "ContextPreInitializationInternal";
    static final String SHARED = "ContextPreInitializationShared";
    static final String CONSTRAINED = "ContextPreInitializationConstrained";

    private static final AtomicInteger NEXT_ORDER_INDEX = new AtomicInteger();
    private static final String SYS_OPTION1_KEY = "polyglot." + FIRST + ".Option1";
    private static final String SYS_OPTION2_KEY = "polyglot." + FIRST + ".Option2";
    private static final List<CountingContext> emittedContexts = new ArrayList<>();
    private static final Set<String> patchableLanguages = new HashSet<>();

    private static String originalDynamicCompilationThresholds;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        // Workaround for issue GR-31197: Compiler tests are passing
        // engine.DynamicCompilationThresholds option from command line.
        originalDynamicCompilationThresholds = System.getProperty("polyglot.engine.DynamicCompilationThresholds");
        if (originalDynamicCompilationThresholds != null) {
            System.getProperties().remove("polyglot.engine.DynamicCompilationThresholds");
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (originalDynamicCompilationThresholds != null) {
            System.setProperty("polyglot.engine.DynamicCompilationThresholds", originalDynamicCompilationThresholds);
        }
    }

    @Before
    public void setUp() throws Exception {
        // Initialize IMPL
        Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, ContextPreInitializationTest.class.getClassLoader());
    }

    @After
    public void tearDown() throws Exception {
        ContextPreInitializationTestFirstLanguage.callDependentLanguage = false;
        ContextPreInitializationTestSecondLanguage.callDependentLanguageInCreate = false;
        ContextPreInitializationTestSecondLanguage.callDependentLanguageInPatch = false;
        ContextPreInitializationTestSecondLanguage.lookupService = false;
        ContextPreInitializationFirstInstrument.actions = null;
        BaseLanguage.actions.clear();
        resetSystemPropertiesOptions();
        resetLanguageHomes();
        patchableLanguages.clear();
        emittedContexts.clear();
        NEXT_ORDER_INDEX.set(0);

        final Class<?> holderClz = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, ContextPreInitializationTest.class.getClassLoader());
        final Method preInitMethod = holderClz.getDeclaredMethod("resetPreInitializedEngine");
        ReflectionUtils.setAccessible(preInitMethod, true);
        preInitMethod.invoke(null);
    }

    @Test
    public void testOutputNoLanguagePreInitialization() throws Exception {
        setPatchable();
        final String stdOutContent = "output";
        final String stdErrContent = "error";
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_EXECUTE, (env) -> {
            write(env.out(), stdOutContent);
            write(env.err(), stdErrContent);
        });
        doContextPreinitialize();
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(0, contexts.size());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (Context ctx = Context.newBuilder().out(out).err(err).build()) {
            final Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            assertEquals(stdOutContent, new String(out.toByteArray(), "UTF-8"));
            assertEquals(stdErrContent, new String(err.toByteArray(), "UTF-8"));
        }
    }

    @Test
    public void testOutputSingleLanguagePreInitialization() throws Exception {
        setPatchable(FIRST);
        final String firstStdOutContent = "first-output";
        final String firstStdErrContent = "first-error";
        final String secondStdOutContent = "second-output";
        final String secondStdErrContent = "second-error";
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_EXECUTE, (env) -> {
            write(env.out(), firstStdOutContent);
            write(env.err(), firstStdErrContent);
        });
        BaseLanguage.registerAction(ContextPreInitializationTestSecondLanguage.class, ActionKind.ON_EXECUTE, (env) -> {
            write(env.out(), secondStdOutContent);
            write(env.err(), secondStdErrContent);
        });
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (Context ctx = Context.newBuilder().out(out).err(err).build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            assertEquals(firstStdOutContent, new String(out.toByteArray(), "UTF-8"));
            assertEquals(firstStdErrContent, new String(err.toByteArray(), "UTF-8"));
            out.reset();
            err.reset();
            res = ctx.eval(Source.create(SECOND, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(2, contexts.size());
            assertEquals(secondStdOutContent, new String(out.toByteArray(), "UTF-8"));
            assertEquals(secondStdErrContent, new String(err.toByteArray(), "UTF-8"));
        }
    }

    @Test
    public void testArgumentsSingleLanguagePreInitialization() throws Exception {
        setPatchable();
        doContextPreinitialize();
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(0, contexts.size());
        try (Context ctx = Context.newBuilder().arguments(FIRST, new String[]{"a", "b"}).build()) {
            final Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            final CountingContext context = findContext(FIRST, contexts);
            assertNotNull(context);
            assertEquals(Arrays.asList("a", "b"), context.arguments);
        }
    }

    @Test
    public void testArgumentsSingleLanguagePreInitialization2() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        try (Context ctx = Context.newBuilder().arguments(FIRST, new String[]{"a", "b"}).arguments(SECOND, new String[]{"c", "d"}).build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            CountingContext context = findContext(FIRST, contexts);
            assertNotNull(context);
            assertEquals(Arrays.asList("a", "b"), context.arguments);
            res = ctx.eval(Source.create(SECOND, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(2, contexts.size());
            context = findContext(SECOND, contexts);
            assertNotNull(context);
            assertEquals(Arrays.asList("c", "d"), context.arguments);
        }
    }

    @Test
    public void testNoLanguagePreInitialization() throws Exception {
        setPatchable();
        doContextPreinitialize();
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(0, contexts.size());
        final Context ctx = Context.create();
        final Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext context = findContext(FIRST, contexts);
        assertNotNull(context);
        assertEquals(1, context.createContextCount);
        assertEquals(1, context.initializeContextCount);
        assertEquals(0, context.patchContextCount);
        assertEquals(0, context.disposeContextCount);
        assertEquals(1, context.initializeThreadCount);
        assertEquals(0, context.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, context.createContextCount);
        assertEquals(1, context.initializeContextCount);
        assertEquals(0, context.patchContextCount);
        assertEquals(1, context.disposeContextCount);
        assertEquals(1, context.initializeThreadCount);
        assertEquals(1, context.disposeThreadCount);
    }

    @Test
    public void testSingleLanguagePreInitialization() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(0, secondLangCtx.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
    }

    @Test
    public void testMoreLanguagesPreInitialization() throws Exception {
        setPatchable(FIRST, SECOND);
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
    }

    @Test
    public void testMoreLanguagesPreInitializationFailedPatch() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(3, contexts.size());
        Collection<? extends CountingContext> firstLangCtxs = findContexts(FIRST, contexts);
        firstLangCtxs.remove(firstLangCtx);
        assertFalse(firstLangCtxs.isEmpty());
        final CountingContext firstLangCtx2 = firstLangCtxs.iterator().next();
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount); // Close initializes thread
        assertEquals(2, firstLangCtx.disposeThreadCount);    // Close initializes thread
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);    // Close initializes thread
        assertEquals(2, secondLangCtx.disposeThreadCount);       // Close initializes thread
        assertEquals(1, firstLangCtx2.createContextCount);
        assertEquals(1, firstLangCtx2.initializeContextCount);
        assertEquals(0, firstLangCtx2.patchContextCount);
        assertEquals(0, firstLangCtx2.disposeContextCount);
        assertEquals(1, firstLangCtx2.initializeThreadCount);
        assertEquals(0, firstLangCtx2.disposeThreadCount);
        ctx.close();
        assertEquals(3, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx2.createContextCount);
        assertEquals(1, firstLangCtx2.initializeContextCount);
        assertEquals(0, firstLangCtx2.patchContextCount);
        assertEquals(1, firstLangCtx2.disposeContextCount);
        assertEquals(1, firstLangCtx2.initializeThreadCount);
        assertEquals(1, firstLangCtx2.disposeThreadCount);
    }

    @Test
    public void testSystemPropertiesOptionsSuccessfulPatch() throws Exception {
        System.setProperty(SYS_OPTION1_KEY, "true");
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertTrue(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option1));
        assertFalse(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option2));
        firstLangCtx.optionValues.clear();
        System.clearProperty(SYS_OPTION1_KEY);
        System.setProperty(SYS_OPTION2_KEY, "true");
        Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        assertFalse(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option1));
        assertTrue(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option2));
        ctx.close();
        ctx = Context.create();
        res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        assertFalse(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option1));
        assertTrue(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option2));
        ctx.close();
    }

    @Test
    public void testSystemPropertiesOptionsFailedPatch() throws Exception {
        System.setProperty(SYS_OPTION1_KEY, "true");
        setPatchable();
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertTrue(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option1));
        assertFalse(firstLangCtx.optionValues.get(ContextPreInitializationTestFirstLanguage.Option2));
        firstLangCtx.optionValues.clear();
        System.clearProperty(SYS_OPTION1_KEY);
        System.setProperty(SYS_OPTION2_KEY, "true");
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        contexts.remove(firstLangCtx);
        final CountingContext firstLangCtx2 = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx2);
        assertFalse(firstLangCtx2.optionValues.get(ContextPreInitializationTestFirstLanguage.Option1));
        assertTrue(firstLangCtx2.optionValues.get(ContextPreInitializationTestFirstLanguage.Option2));
        ctx.close();

    }

    @Test
    public void testContextOptionsNoLanguagePreInitialization() throws Exception {
        setPatchable();
        doContextPreinitialize();
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(0, contexts.size());
        final Context ctx = Context.newBuilder().option(FIRST + ".Option1", "true").build();
        final Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext context = findContext(FIRST, contexts);
        assertNotNull(context);
        assertTrue(context.optionValues.get(ContextPreInitializationTestFirstLanguage.Option1));
        assertFalse(context.optionValues.get(ContextPreInitializationTestFirstLanguage.Option2));
        ctx.close();
    }

    @Test
    public void testContextOptionsSingleLanguagePreInitialization() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext context = findContext(FIRST, contexts);
        assertNotNull(context);
        final Context ctx = Context.newBuilder().option(FIRST + ".Option1", "true").build();
        final Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertTrue(context.optionValues.get(ContextPreInitializationTestFirstLanguage.Option1));
        assertFalse(context.optionValues.get(ContextPreInitializationTestFirstLanguage.Option2));
        ctx.close();
    }

    @SuppressWarnings("try")
    @Test
    public void testContextOptionsCompatibleAfterSuccessfulPatch() throws Exception {
        setPatchable(SHARED);
        doContextPreinitialize(SHARED);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        final CountingContext firstLangCtx = findContext(SHARED, contexts);
        final BaseLanguage firstLang = firstLangCtx.language;
        assertNotNull(firstLangCtx);
        assertFalse(firstLangCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option1));
        assertFalse(firstLangCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option2));

        try (Context ctx = Context.newBuilder().option(SHARED + ".Option1", "true").build()) {
            Value res = ctx.eval(Source.create(SHARED, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            CountingContext langCtx = contexts.get(0);
            assertTrue(langCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option1));
            assertFalse(langCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option2));
            assertSame(firstLang, langCtx.language);
            assertSame(firstLangCtx, langCtx);

            ctx.enter();
            try (TruffleContext innerContext = langCtx.environment().newInnerContextBuilder().initializeCreatorContext(true).build()) {
                contexts = new ArrayList<>(emittedContexts);
                assertEquals(2, contexts.size());
                langCtx = contexts.get(1);
                assertTrue(langCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option1));
                assertFalse(langCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option2));
                assertNotSame("No sharing with inner contexts.", firstLang, langCtx.language);
            } finally {
                ctx.leave();
            }
        }
    }

    @Test
    public void testDependentLanguagePreInitializationSuccessfulPatch() throws Exception {
        setPatchable(SECOND, FIRST, INTERNAL);
        ContextPreInitializationTestSecondLanguage.callDependentLanguageInCreate = true;
        ContextPreInitializationTestFirstLanguage.callDependentLanguage = true;
        doContextPreinitialize(SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(3, contexts.size());
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        final CountingContext internalLangCtx = findContext(INTERNAL, contexts);
        assertNotNull(internalLangCtx);
        assertEquals(1, internalLangCtx.createContextCount);
        assertEquals(1, internalLangCtx.initializeContextCount);
        assertEquals(0, internalLangCtx.patchContextCount);
        assertEquals(0, internalLangCtx.disposeContextCount);
        assertEquals(1, internalLangCtx.initializeThreadCount);
        assertEquals(1, internalLangCtx.disposeThreadCount);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(3, contexts.size());
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, internalLangCtx.createContextCount);
        assertEquals(1, internalLangCtx.initializeContextCount);
        assertEquals(1, internalLangCtx.patchContextCount);
        assertEquals(0, internalLangCtx.disposeContextCount);
        assertEquals(2, internalLangCtx.initializeThreadCount);
        assertEquals(1, internalLangCtx.disposeThreadCount);
        assertTrue(internalLangCtx.patchContextOrder < firstLangCtx.patchContextOrder);
        assertTrue(firstLangCtx.patchContextOrder < secondLangCtx.patchContextOrder);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(3, contexts.size());
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
        assertEquals(1, internalLangCtx.createContextCount);
        assertEquals(1, internalLangCtx.initializeContextCount);
        assertEquals(1, internalLangCtx.patchContextCount);
        assertEquals(1, internalLangCtx.disposeContextCount);
        assertEquals(2, internalLangCtx.initializeThreadCount);
        assertEquals(2, internalLangCtx.disposeThreadCount);
    }

    @Test
    public void testDependentLanguagePreInitializationFailedPatch() throws Exception {
        setPatchable(SECOND);
        ContextPreInitializationTestSecondLanguage.callDependentLanguageInCreate = true;
        ContextPreInitializationTestFirstLanguage.callDependentLanguage = true;
        doContextPreinitialize(SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(3, contexts.size());
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        final CountingContext internalLangCtx = findContext(INTERNAL, contexts);
        assertNotNull(internalLangCtx);
        assertEquals(1, internalLangCtx.createContextCount);
        assertEquals(1, internalLangCtx.initializeContextCount);
        assertEquals(0, internalLangCtx.patchContextCount);
        assertEquals(0, internalLangCtx.disposeContextCount);
        assertEquals(1, internalLangCtx.initializeThreadCount);
        assertEquals(1, internalLangCtx.disposeThreadCount);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(6, contexts.size());
        Collection<? extends CountingContext> ctxs = findContexts(SECOND, contexts);
        ctxs.remove(secondLangCtx);
        assertEquals(1, ctxs.size());
        final CountingContext secondLangCtx2 = ctxs.iterator().next();
        ctxs = findContexts(FIRST, contexts);
        ctxs.remove(firstLangCtx);
        assertEquals(1, ctxs.size());
        final CountingContext firstLangCtx2 = ctxs.iterator().next();
        ctxs = findContexts(INTERNAL, contexts);
        ctxs.remove(internalLangCtx);
        assertEquals(1, ctxs.size());
        final CountingContext internalLangCtx2 = ctxs.iterator().next();
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
        assertEquals(1, internalLangCtx.createContextCount);
        assertEquals(1, internalLangCtx.initializeContextCount);
        assertEquals(1, internalLangCtx.patchContextCount);
        assertEquals(1, internalLangCtx.disposeContextCount);
        assertEquals(2, internalLangCtx.initializeThreadCount);
        assertEquals(2, internalLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx2.createContextCount);
        assertEquals(1, secondLangCtx2.initializeContextCount);
        assertEquals(0, secondLangCtx2.patchContextCount);
        assertEquals(0, secondLangCtx2.disposeContextCount);
        assertEquals(1, secondLangCtx2.initializeThreadCount);
        assertEquals(0, secondLangCtx2.disposeThreadCount);
        assertEquals(1, firstLangCtx2.createContextCount);
        assertEquals(1, firstLangCtx2.initializeContextCount);
        assertEquals(0, firstLangCtx2.patchContextCount);
        assertEquals(0, firstLangCtx2.disposeContextCount);
        assertEquals(1, firstLangCtx2.initializeThreadCount);
        assertEquals(0, firstLangCtx2.disposeThreadCount);
        assertEquals(1, internalLangCtx2.createContextCount);
        assertEquals(1, internalLangCtx2.initializeContextCount);
        assertEquals(0, internalLangCtx2.patchContextCount);
        assertEquals(0, internalLangCtx2.disposeContextCount);
        assertEquals(1, internalLangCtx2.initializeThreadCount);
        assertEquals(0, internalLangCtx2.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(6, contexts.size());
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
        assertEquals(1, internalLangCtx.createContextCount);
        assertEquals(1, internalLangCtx.initializeContextCount);
        assertEquals(1, internalLangCtx.patchContextCount);
        assertEquals(1, internalLangCtx.disposeContextCount);
        assertEquals(2, internalLangCtx.initializeThreadCount);
        assertEquals(2, internalLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx2.createContextCount);
        assertEquals(1, secondLangCtx2.initializeContextCount);
        assertEquals(0, secondLangCtx2.patchContextCount);
        assertEquals(1, secondLangCtx2.disposeContextCount);
        assertEquals(1, secondLangCtx2.initializeThreadCount);
        assertEquals(1, secondLangCtx2.disposeThreadCount);
        assertEquals(1, firstLangCtx2.createContextCount);
        assertEquals(1, firstLangCtx2.initializeContextCount);
        assertEquals(0, firstLangCtx2.patchContextCount);
        assertEquals(1, firstLangCtx2.disposeContextCount);
        assertEquals(1, firstLangCtx2.initializeThreadCount);
        assertEquals(1, firstLangCtx2.disposeThreadCount);
        assertEquals(1, internalLangCtx2.createContextCount);
        assertEquals(1, internalLangCtx2.initializeContextCount);
        assertEquals(0, internalLangCtx2.patchContextCount);
        assertEquals(1, internalLangCtx2.disposeContextCount);
        assertEquals(1, internalLangCtx2.initializeThreadCount);
        assertEquals(1, internalLangCtx2.disposeThreadCount);
    }

    @Test
    public void testSingleLanguageExceptionFromContextPatch() throws Exception {
        setPatchable(FIRST);
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (Consumer<Env>) (env) -> {
            throw new RuntimeException("patchContext() exception");
        });
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        try {
            Context.create();
            Assert.fail("Should not reach here.");
        } catch (PolyglotException pe) {
            // Expected exception
        }
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
    }

    @Test
    public void testMoreLanguagesExceptionFromContextPatch() throws Exception {
        setPatchable(FIRST, SECOND);
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (Consumer<Env>) (env) -> {
            throw new RuntimeException("patchContext() exception");
        });
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        try {
            Context.create();
            Assert.fail("Should not reach here.");
        } catch (PolyglotException pe) {
            // Expected exception
        }
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount); // Close initializes thread
        assertEquals(2, firstLangCtx.disposeThreadCount);
        // Close initializes thread
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);    // Close initializes thread
        assertEquals(2, secondLangCtx.disposeThreadCount);       // Close initializes thread
    }

    @Test
    public void testLanguageHome() throws Exception {
        setPatchable(FIRST);
        String expectedPath = Paths.get(String.format("/compile-graalvm/languages/%s", FIRST)).toString();
        System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), expectedPath);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        Assert.assertEquals(expectedPath, firstLangCtx.languageHome);

        expectedPath = Paths.get(String.format("/run-graalvm/languages/%s", FIRST)).toString();
        System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), expectedPath);
        try (Context ctx = Context.newBuilder().build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            Assert.assertEquals(expectedPath, firstLangCtx.languageHome);
        }
    }

    @Test
    public void testTemporaryEngine() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        Engine.create().close();
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
    }

    @Test
    public void testLogging() throws Exception {
        setPatchable(FIRST);
        // In context pre-initialization there is no sdk Context to set log handler,
        // logging is done to System.err
        final PrintStream origErr = System.err;
        final ByteArrayOutputStream preInitErr = new CheckCloseByteArrayOutputStream();
        String origLogFile = System.getProperty("polyglot.log.file");
        try (PrintStream printStream = new PrintStream(preInitErr)) {
            System.setErr(printStream);
            System.setProperty("polyglot.log.engine.level", "FINE");
            System.clearProperty("polyglot.log.file");
            doContextPreinitialize(FIRST);
        } finally {
            System.setErr(origErr);
            System.clearProperty("polyglot.log.engine.level");
            if (origLogFile != null) {
                System.setProperty("polyglot.log.file", origLogFile);
            }
        }
        final String preInitLog = preInitErr.toString("UTF-8");
        assertTrue(preInitLog.contains("Pre-initialized context for language: ContextPreInitializationFirst"));
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        final TestHandler testHandler = new TestHandler("engine.com.oracle.truffle.polyglot.PolyglotLanguageContext");
        final Context ctx = Context.newBuilder().option("log.engine.level", "FINE").logHandler(testHandler).build();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        assertEquals(1, testHandler.logs.size());
        assertEquals(FIRST, testHandler.logs.get(0).getParameters()[0]);
        assertEquals("Successfully patched context of language: {0}", testHandler.logs.get(0).getMessage());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
    }

    @Test
    public void testRedirectedLogging() throws Exception {
        setPatchable(FIRST);
        String origLogFile = System.getProperty("polyglot.log.file");
        Path preInitLogFile = Files.createTempFile("preinit", ".log");
        Path patchLogFile = Files.createTempFile("patch", ".log");
        try {
            System.setProperty("polyglot.log.engine.level", "FINE");
            System.setProperty("polyglot.log.file", preInitLogFile.toString());
            doContextPreinitialize(FIRST);
            assertFalse(getActiveFileHandlers().contains(preInitLogFile));
            List<CountingContext> contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            final CountingContext firstLangCtx = findContext(FIRST, contexts);
            assertNotNull(firstLangCtx);
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(0, firstLangCtx.patchContextCount);
            assertEquals(0, firstLangCtx.disposeContextCount);
            assertEquals(1, firstLangCtx.initializeThreadCount);
            assertEquals(1, firstLangCtx.disposeThreadCount);
            assertTrue(new String(Files.readAllBytes(preInitLogFile)).contains("Pre-initialized context for language: ContextPreInitializationFirst"));
            System.setProperty("polyglot.log.file", patchLogFile.toString());
            try (Context ctx = Context.newBuilder().option("log.engine.level", "FINE").build()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
                contexts = new ArrayList<>(emittedContexts);
                assertEquals(1, contexts.size());
                assertEquals(1, firstLangCtx.createContextCount);
                assertEquals(1, firstLangCtx.initializeContextCount);
                assertEquals(1, firstLangCtx.patchContextCount);
                assertEquals(0, firstLangCtx.disposeContextCount);
                assertEquals(2, firstLangCtx.initializeThreadCount);
                assertEquals(1, firstLangCtx.disposeThreadCount);
                assertTrue(new String(Files.readAllBytes(patchLogFile)).contains("Successfully patched context of language: ContextPreInitializationFirst"));
            }
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
            assertEquals(1, firstLangCtx.disposeContextCount);
            assertEquals(2, firstLangCtx.initializeThreadCount);
            assertEquals(2, firstLangCtx.disposeThreadCount);
        } finally {
            System.clearProperty("polyglot.log.engine.level");
            System.clearProperty("polyglot.log.file");
            if (origLogFile != null) {
                System.setProperty("polyglot.log.file", origLogFile);
            }
        }
    }

    @Test
    public void testEngineBoundLoggers() throws Exception {
        String loggerName = "engine";
        String loggerLevelOptionName = String.format("log.%s.level", loggerName);
        setPatchable(FIRST);
        // In context pre-initialization there is no sdk Context to set log handler,
        // logging is done to System.err
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
            Object engine = TestAPIAccessor.engineAccess().getCurrentPolyglotEngine();
            TruffleLogger log = getEngineLogger(engine);
            log.log(Level.INFO, "preInit:info");
            log.log(Level.FINEST, "preInit:finest");
        });
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
            Object engine = TestAPIAccessor.engineAccess().getCurrentPolyglotEngine();
            TruffleLogger log = getEngineLogger(engine);
            log.log(Level.INFO, "patch:info");
            log.log(Level.FINEST, "patch:finest");
        });
        final PrintStream origErr = System.err;
        final ByteArrayOutputStream preInitErr = new CheckCloseByteArrayOutputStream();
        String origLogFile = System.getProperty("polyglot.log.file");
        try (PrintStream printStream = new PrintStream(preInitErr)) {
            System.setErr(printStream);
            System.setProperty("polyglot." + loggerLevelOptionName, "FINE");
            System.clearProperty("polyglot.log.file");
            doContextPreinitialize(FIRST);
        } finally {
            System.setErr(origErr);
            System.clearProperty("polyglot." + loggerLevelOptionName);
            if (origLogFile != null) {
                System.setProperty("polyglot.log.file", origLogFile);
            }
        }
        final String preInitLog = preInitErr.toString("UTF-8");
        assertTrue(preInitLog.contains("preInit:info"));
        assertFalse(preInitLog.contains("preInit:finest"));
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final TestHandler testHandler = new TestHandler(loggerName);
        try (Context ctx = Context.newBuilder().option(loggerLevelOptionName, "FINEST").logHandler(testHandler).build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            Set<String> messages = testHandler.logs.stream().map(LogRecord::getMessage).collect(Collectors.toSet());
            assertTrue(messages.contains("patch:info"));
            assertTrue(messages.contains("patch:finest"));
        }
    }

    private static TruffleLogger getEngineLogger(Object engine) {
        try {
            Class<?> clz = Class.forName("com.oracle.truffle.polyglot.PolyglotEngineImpl");
            Method m = clz.getDeclaredMethod("getEngineLogger");
            ReflectionUtils.setAccessible(m, true);
            return (TruffleLogger) m.invoke(engine);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testContextBoundLogger() throws Exception {
        String logger1Name = String.format("%s.bound", FIRST);
        String logger1LevelOptionName = String.format("log.%s.level", logger1Name);
        String logger2Name = String.format("%s.bound2", FIRST);
        String logger2LevelOptionName = String.format("log.%s.level", logger2Name);
        AtomicReference<TruffleLogger> loggerRef = new AtomicReference<>();
        setPatchable(FIRST);
        // In context pre-initialization there is no sdk Context to set log handler,
        // logging is done to System.err
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
            env.getLogger("bound").log(Level.INFO, "bound:preInit:info");
            env.getLogger("bound").log(Level.FINEST, "bound:preInit:finest");
            TruffleLogger log = env.getLogger("bound2");
            loggerRef.set(log);
            log.log(Level.INFO, "bound2:preInit:info");
            log.log(Level.FINEST, "bound2:preInit:finest");
        });
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
            env.getLogger("bound").log(Level.INFO, "bound:patch:info");
            env.getLogger("bound").log(Level.FINEST, "bound:patch:finest");
            TruffleLogger log = loggerRef.get();
            Assert.assertNotNull(log);
            log.log(Level.INFO, "bound2:patch:info");
            log.log(Level.FINEST, "bound2:patch:finest");
        });
        final PrintStream origErr = System.err;
        final ByteArrayOutputStream preInitErr = new CheckCloseByteArrayOutputStream();
        String origLogFile = System.getProperty("polyglot.log.file");
        try (PrintStream printStream = new PrintStream(preInitErr)) {
            System.setErr(printStream);
            System.setProperty("polyglot." + logger1LevelOptionName, "FINE");
            System.setProperty("polyglot." + logger2LevelOptionName, "FINE");
            System.clearProperty("polyglot.log.file");
            doContextPreinitialize(FIRST);
        } finally {
            System.setErr(origErr);
            System.clearProperty("polyglot." + logger1LevelOptionName);
            System.clearProperty("polyglot." + logger2LevelOptionName);
            if (origLogFile != null) {
                System.setProperty("polyglot.log.file", origLogFile);
            }
        }
        final String preInitLog = preInitErr.toString("UTF-8");
        assertTrue(preInitLog.contains("bound:preInit:info"));
        assertTrue(preInitLog.contains("bound2:preInit:info"));
        assertFalse(preInitLog.contains("bound:preInit:finest"));
        assertFalse(preInitLog.contains("bound2:preInit:finest"));

        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final TestHandler testHandler = new TestHandler(logger1Name, logger2Name);
        try (Context ctx = Context.newBuilder().option(logger1LevelOptionName, "FINEST").option(logger2LevelOptionName, "FINEST").logHandler(testHandler).build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            assertEquals(4, testHandler.logs.size());
            assertEquals("bound:patch:info", testHandler.logs.get(0).getMessage());
            assertEquals("bound:patch:finest", testHandler.logs.get(1).getMessage());
            assertEquals("bound2:patch:info", testHandler.logs.get(2).getMessage());
            assertEquals("bound2:patch:finest", testHandler.logs.get(3).getMessage());
        }
    }

    @Test
    public void testJavaLoggerFailedPatch() throws Exception {
        TestHandler testHandler = new TestHandler(FIRST);
        failedPatch(Context.newBuilder().logHandler(testHandler));
        assertEquals(2, testHandler.logs.size());
        assertEquals("patchContext", testHandler.logs.get(0).getMessage());
        assertEquals("initializeContext", testHandler.logs.get(1).getMessage());
    }

    @Test
    public void testStreamLoggerFailedPatch() throws Exception {
        ByteArrayOutputStream logStream = new CheckCloseByteArrayOutputStream();
        failedPatch(Context.newBuilder().logHandler(logStream));
        String logContent = logStream.toString();
        assertTrue(logContent.contains("patchContext"));
        assertTrue(logContent.contains("initializeContext"));
    }

    private static void failedPatch(Context.Builder builder) throws Exception {
        doContextPreinitialize(FIRST);
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
            env.getLogger("").log(Level.INFO, "initializeContext");
        });
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
            env.getLogger("").log(Level.INFO, "patchContext");
        });
        assertEquals(1, emittedContexts.size());
        CountingContext firstLangContext = findContext(FIRST, emittedContexts);
        assertNotNull(firstLangContext);
        assertEquals(1, firstLangContext.createContextCount);
        assertEquals(1, firstLangContext.initializeContextCount);
        assertEquals(0, firstLangContext.patchContextCount);
        assertEquals(0, firstLangContext.disposeContextCount);
        assertEquals(1, firstLangContext.initializeThreadCount);
        assertEquals(1, firstLangContext.disposeThreadCount);
        try (Context ctx = builder.option(String.format("log.%s.level", FIRST), "INFO").build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            Collection<? extends CountingContext> firstLangContexts = findContexts(FIRST, emittedContexts);
            assertEquals(2, firstLangContexts.size());
        }
    }

    @Test
    public void testFileSystemSwitch() throws Exception {
        setPatchable(FIRST);
        Path tmpDir = Files.createTempDirectory("testFileSystemSwitch").toRealPath();
        Path buildHome = tmpDir.resolve("build");
        Path execHome = tmpDir.resolve("exec");
        Files.createDirectories(buildHome);
        Files.createDirectories(execHome);
        Path buildFile = write(buildHome.resolve("test"), "build");
        Path execFile = write(execHome.resolve("test"), "exec");
        Path noLangHomeFile = write(tmpDir.resolve("test"), "abs");

        try {
            List<TruffleFile> files = new ArrayList<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                TruffleFile f = env.getPublicTruffleFile(buildFile.toString());
                files.add(f);
                f = env.getPublicTruffleFile(noLangHomeFile.toString());
                files.add(f);
                f = env.getPublicTruffleFile("relative_file");
                files.add(f);
            });
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                try {
                    assertTrue(files.get(0).isAbsolute());
                    assertEquals(execFile.toString(), files.get(0).getPath());
                    assertEquals("exec", read(files.get(0)).trim());
                    assertTrue(files.get(1).isAbsolute());
                    assertEquals(noLangHomeFile.toString(), files.get(1).getPath());
                    assertEquals("abs", read(files.get(1)).trim());
                    assertFalse(files.get(2).isAbsolute());
                    assertEquals("relative_file", files.get(2).getPath());
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), buildHome.toString());
            doContextPreinitialize(FIRST);
            assertFalse(files.isEmpty());
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), execHome.toString());
            try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        } finally {
            delete(tmpDir);
        }
    }

    @Test
    public void testServiceLookupSuccessfulPatch() throws Exception {
        setPatchable(FIRST, SECOND);
        ContextPreInitializationTestSecondLanguage.lookupService = true;
        doContextPreinitialize(SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(0, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(0, firstLangCtx.initializeThreadCount);
        assertEquals(0, firstLangCtx.disposeThreadCount);
        final Context ctx = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).option(FIRST + ".ServiceKind", Service.Kind.IMAGE_EXECUTION_TIME.name()).build();
        Value res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(0, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(0, firstLangCtx.initializeThreadCount);
        assertEquals(0, firstLangCtx.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(1, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(0, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(0, firstLangCtx.initializeThreadCount);    // initializeThread is called only
                                                                // on TruffleLanguages with
                                                                // initialized contexts
        assertEquals(1, firstLangCtx.disposeThreadCount);       // disposeThread is called on all
                                                                // TruffleLanguages
    }

    @Test
    public void testServiceLookupFailedPatch() throws Exception {
        setPatchable(SECOND);
        ContextPreInitializationTestSecondLanguage.lookupService = true;
        doContextPreinitialize(SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(0, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(0, firstLangCtx.initializeThreadCount);
        assertEquals(0, firstLangCtx.disposeThreadCount);
        final Context ctx = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).option(FIRST + ".ServiceKind", Service.Kind.IMAGE_EXECUTION_TIME.name()).build();
        Value res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(4, contexts.size());
        Collection<? extends CountingContext> ctxs = findContexts(SECOND, contexts);
        ctxs.remove(secondLangCtx);
        assertEquals(1, ctxs.size());
        final CountingContext secondLangCtx2 = ctxs.iterator().next();
        ctxs = findContexts(FIRST, contexts);
        ctxs.remove(firstLangCtx);
        assertEquals(1, ctxs.size());
        final CountingContext firstLangCtx2 = ctxs.iterator().next();
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(0, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(0, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);

        assertEquals(1, secondLangCtx2.createContextCount);
        assertEquals(1, secondLangCtx2.initializeContextCount);
        assertEquals(0, secondLangCtx2.patchContextCount);
        assertEquals(0, secondLangCtx2.disposeContextCount);
        assertEquals(1, secondLangCtx2.initializeThreadCount);
        assertEquals(0, secondLangCtx2.disposeThreadCount);
        assertEquals(1, firstLangCtx2.createContextCount);
        assertEquals(0, firstLangCtx2.initializeContextCount);
        assertEquals(0, firstLangCtx2.patchContextCount);
        assertEquals(0, firstLangCtx2.disposeContextCount);
        assertEquals(0, firstLangCtx2.initializeThreadCount);
        assertEquals(0, firstLangCtx2.disposeThreadCount);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(4, contexts.size());
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(1, secondLangCtx.disposeContextCount);
        assertEquals(2, secondLangCtx.initializeThreadCount);
        assertEquals(2, secondLangCtx.disposeThreadCount);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(0, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(0, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx2.createContextCount);
        assertEquals(1, secondLangCtx2.initializeContextCount);
        assertEquals(0, secondLangCtx2.patchContextCount);
        assertEquals(1, secondLangCtx2.disposeContextCount);
        assertEquals(1, secondLangCtx2.initializeThreadCount);
        assertEquals(1, secondLangCtx2.disposeThreadCount);
        assertEquals(1, firstLangCtx2.createContextCount);
        assertEquals(0, firstLangCtx2.initializeContextCount);
        assertEquals(0, firstLangCtx2.patchContextCount);
        assertEquals(1, firstLangCtx2.disposeContextCount);
        assertEquals(0, firstLangCtx2.initializeThreadCount);
        assertEquals(1, firstLangCtx2.disposeThreadCount);
    }

    @Test
    public void testInstrumentsEvents() throws Exception {
        ContextPreInitializationFirstInstrument.actions = Collections.singletonMap("onLanguageContextInitialized", (e) -> {
            if (FIRST.equals(e.language.getId())) {
                final CountDownLatch signal = new CountDownLatch(1);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        InstrumentInfo instrument = e.env.getInstruments().get(ContextPreInitializationSecondInstrument.ID);
                        e.env.lookup(instrument, Service.class);
                        signal.countDown();
                    }
                }).start();
                try {
                    signal.await();
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
        });
        ContextPreInitializationTestSecondLanguage.callDependentLanguageInPatch = true;
        setPatchable(FIRST, SECOND);
        doContextPreinitialize(SECOND);
        try (Context ctx = Context.newBuilder().option(ContextPreInitializationFirstInstrument.ID, "true").build()) {
            Value res = ctx.eval(Source.create(SECOND, "test"));
            assertEquals("test", res.asString());
        }
    }

    @Test
    public void testSingeInstrumentInstanceAfterContextPatch() throws Exception {
        AtomicInteger instrumentCreateCount = new AtomicInteger();
        ContextPreInitializationFirstInstrument.actions = Collections.singletonMap("onCreate", (e) -> {
            instrumentCreateCount.incrementAndGet();
        });
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, instrumentCreateCount.get());
        try (Context ctx = Context.newBuilder().option(ContextPreInitializationFirstInstrument.ID, "true").build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            assertEquals(1, emittedContexts.size());
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
            assertEquals(0, firstLangCtx.disposeContextCount);
            assertEquals(2, firstLangCtx.initializeThreadCount);
            assertEquals(1, firstLangCtx.disposeThreadCount);
            assertEquals(1, instrumentCreateCount.get());
        }
    }

    @Test
    public void testInstrumentCreatedAfterFailedContextPatch() throws Exception {
        AtomicInteger instrumentCreateCount = new AtomicInteger();
        ContextPreInitializationFirstInstrument.actions = Collections.singletonMap("onCreate", (e) -> {
            instrumentCreateCount.incrementAndGet();
        });
        setPatchable();
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, instrumentCreateCount.get());
        try (Context ctx = Context.newBuilder().option(ContextPreInitializationFirstInstrument.ID, "true").build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(2, contexts.size());
            contexts.remove(firstLangCtx);
            CountingContext newFirstLangCtx = findContext(FIRST, contexts);
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
            assertEquals(1, firstLangCtx.disposeContextCount);
            assertEquals(2, firstLangCtx.initializeThreadCount);
            assertEquals(2, firstLangCtx.disposeThreadCount);
            assertEquals(1, newFirstLangCtx.createContextCount);
            assertEquals(1, newFirstLangCtx.initializeContextCount);
            assertEquals(0, newFirstLangCtx.patchContextCount);
            assertEquals(0, newFirstLangCtx.disposeContextCount);
            assertEquals(1, newFirstLangCtx.initializeThreadCount);
            assertEquals(0, newFirstLangCtx.disposeThreadCount);
            assertEquals(1, instrumentCreateCount.get());
        }
    }

    @Test
    public void testSetCurrentWorkingDirectory() throws Exception {
        setPatchable(FIRST);
        Path newCwd = Files.createTempDirectory("testSetCWD");
        Path absoluteFolder = Files.createTempDirectory("testSetCWDAbs");
        try {
            List<TruffleFile> filesFromPreInitialization = new ArrayList<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                TruffleFile f = env.getPublicTruffleFile("relative");
                filesFromPreInitialization.add(f);
                f = env.getPublicTruffleFile(absoluteFolder.toString());
                filesFromPreInitialization.add(f);
                f = env.getInternalTruffleFile("relative");
                filesFromPreInitialization.add(f);
                f = env.getInternalTruffleFile(absoluteFolder.toString());
                filesFromPreInitialization.add(f);
            });
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                Assert.assertEquals(newCwd.resolve("relative"), Paths.get(filesFromPreInitialization.get(0).getAbsoluteFile().getPath()));
                Assert.assertEquals(absoluteFolder, Paths.get(filesFromPreInitialization.get(1).getAbsoluteFile().getPath()));
                Assert.assertEquals(newCwd.resolve("relative"), Paths.get(filesFromPreInitialization.get(2).getAbsoluteFile().getPath()));
                Assert.assertEquals(absoluteFolder, Paths.get(filesFromPreInitialization.get(3).getAbsoluteFile().getPath()));
            });
            doContextPreinitialize(FIRST);
            assertFalse(filesFromPreInitialization.isEmpty());
            try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).currentWorkingDirectory(newCwd).build()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        } finally {
            delete(newCwd);
            delete(absoluteFolder);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testSourceInLanguageHome() throws Exception {
        setPatchable(FIRST);
        Path testFolder = Files.createTempDirectory("testSources").toRealPath();
        try {
            Path buildtimeHome = Files.createDirectories(testFolder.resolve("build").resolve(FIRST));
            Path buildtimeResource = Files.write(buildtimeHome.resolve("testSourceInLanguageHome.test"), Collections.singleton("test"));
            Path runtimeHome = Files.createDirectories(testFolder.resolve("exec").resolve(FIRST));
            Path runtimeResource = Files.copy(buildtimeResource, runtimeHome.resolve(buildtimeResource.getFileName()));
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), buildtimeHome.toString());
            AtomicReference<com.oracle.truffle.api.source.Source> buildtimeCachedSource = new AtomicReference<>();
            AtomicReference<com.oracle.truffle.api.source.Source> buildtimeUnCachedSource = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                buildtimeCachedSource.set(createSource(env, buildtimeResource, true));
                buildtimeUnCachedSource.set(createSource(env, buildtimeResource, false));
                assertFalse(buildtimeCachedSource.get() == buildtimeUnCachedSource.get());
            });
            doContextPreinitialize(FIRST);
            List<CountingContext> contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            CountingContext firstLangCtx = findContext(FIRST, contexts);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertNotNull(buildtimeCachedSource.get());
            assertNotNull(buildtimeUnCachedSource.get());
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), runtimeHome.toString());
            AtomicReference<com.oracle.truffle.api.source.Source> runtimeCachedSource = new AtomicReference<>();
            AtomicReference<com.oracle.truffle.api.source.Source> runtimeUnCachedSource = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                runtimeCachedSource.set(createSource(env, runtimeResource, true));
                runtimeUnCachedSource.set(createSource(env, runtimeResource, false));
                assertFalse(runtimeCachedSource.get() == runtimeUnCachedSource.get());
            });
            try (Context ctx = Context.create()) {
                assertEquals(1, firstLangCtx.patchContextCount);
                assertEquals(runtimeResource, Paths.get(buildtimeCachedSource.get().getPath()));
                assertEquals(runtimeResource, Paths.get(buildtimeUnCachedSource.get().getPath()));
                assertEquals(runtimeResource, Paths.get(runtimeUnCachedSource.get().getPath()));
                assertSame(buildtimeCachedSource.get(), runtimeCachedSource.get());
                assertFalse(buildtimeCachedSource.get() == buildtimeUnCachedSource.get());
                assertFalse(buildtimeCachedSource.get() == runtimeUnCachedSource.get());
                assertFalse(buildtimeUnCachedSource.get() == runtimeUnCachedSource.get());
            }
        } finally {
            delete(testFolder);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testSourceOutsideLanguageHome() throws Exception {
        setPatchable(FIRST);
        Path testFolder = Files.createTempDirectory("testSources").toRealPath();
        try {
            Path buildtimeHome = Files.createDirectories(testFolder.resolve("build").resolve(FIRST));
            Path runtimeHome = Files.createDirectories(testFolder.resolve("exec").resolve(FIRST));
            Path resource = Files.write(testFolder.resolve("testSourceOutsideLanguageHome.test"), Collections.singleton("test"));
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), buildtimeHome.toString());
            AtomicReference<com.oracle.truffle.api.source.Source> buildtimeCachedSource = new AtomicReference<>();
            AtomicReference<com.oracle.truffle.api.source.Source> buildtimeUnCachedSource = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                buildtimeCachedSource.set(createSource(env, resource, true));
                buildtimeUnCachedSource.set(createSource(env, resource, false));
                assertFalse(buildtimeCachedSource.get() == buildtimeUnCachedSource.get());
            });
            doContextPreinitialize(FIRST);
            List<CountingContext> contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            CountingContext firstLangCtx = findContext(FIRST, contexts);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertNotNull(buildtimeCachedSource.get());
            assertNotNull(buildtimeUnCachedSource.get());
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), runtimeHome.toString());
            AtomicReference<com.oracle.truffle.api.source.Source> runtimeCachedSource = new AtomicReference<>();
            AtomicReference<com.oracle.truffle.api.source.Source> runtimeUnCachedSource = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                runtimeCachedSource.set(createSource(env, resource, true));
                runtimeUnCachedSource.set(createSource(env, resource, false));
            });
            try (Context ctx = Context.newBuilder().allowAllAccess(true).build()) {
                assertEquals(1, firstLangCtx.patchContextCount);
                assertEquals(resource, Paths.get(buildtimeCachedSource.get().getPath()));
                assertEquals(resource, Paths.get(buildtimeUnCachedSource.get().getPath()));
                assertEquals(resource, Paths.get(runtimeUnCachedSource.get().getPath()));
                assertSame(buildtimeCachedSource.get(), runtimeCachedSource.get());
                assertFalse(buildtimeCachedSource.get() == buildtimeUnCachedSource.get());
                assertFalse(buildtimeCachedSource.get() == runtimeUnCachedSource.get());
                assertFalse(buildtimeUnCachedSource.get() == runtimeUnCachedSource.get());
            }
        } finally {
            delete(testFolder);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testSourceNotPatchedContext() throws Exception {
        setPatchable(FIRST);
        Path testFolder = Files.createTempDirectory("testSources").toRealPath();
        try {
            Path buildtimeHome = Files.createDirectories(testFolder.resolve("build").resolve(FIRST));
            Path buildtimeResource = Files.write(buildtimeHome.resolve("testSourceNotPatchedContext.test"), Collections.singleton("test"));
            Path runtimeHome = Files.createDirectories(testFolder.resolve("exec").resolve(FIRST));
            Path runtimeResource = Files.copy(buildtimeResource, runtimeHome.resolve(buildtimeResource.getFileName()));
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), buildtimeHome.toString());
            AtomicReference<com.oracle.truffle.api.source.Source> buildtimeSource = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                buildtimeSource.set(createSource(env, buildtimeResource, true));
            });
            doContextPreinitialize(FIRST);
            List<CountingContext> contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            CountingContext firstLangCtx = findContext(FIRST, contexts);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertNotNull(buildtimeSource.get());
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), runtimeHome.toString());
            Source.newBuilder(FIRST, runtimeResource.toFile()).build();
        } finally {
            delete(testFolder);
        }
    }

    /*
     * We trigger context preinitialization with no language ever used. No language context should
     * get preinitialized as no sharing layer was claimed. In theory we could precreate the context
     * instance without any language initialized, but that does not make much sense in practice.
     */
    @Test
    public void testCodeSharingEmpty() throws Exception {
        try (Engine engine = Engine.create()) {
            triggerPreinitialization(engine);
            assertEquals(0, emittedContexts.size());

            Context context = Context.newBuilder().engine(engine).build();
            context.initialize(SHARED);
            assertEquals(1, emittedContexts.size());
            assertEquals(0, findContext(SHARED, emittedContexts).patchContextCount);
            assertEquals(1, findContext(SHARED, emittedContexts).createContextCount);
        }
    }

    /*
     * We create a single contexts that triggers a single sharing layer. The implementation should
     * create a single preinitialized context.
     */
    @Test
    @SuppressWarnings("try")
    public void testCodeSharingSingleLayer() throws Exception {
        try (Engine engine = Engine.create()) {
            try (Context cacheContext = Context.newBuilder().engine(engine).build()) {
                cacheContext.initialize(SHARED);
            }

            assertEquals(1, emittedContexts.size());
            emittedContexts.clear();

            triggerPreinitialization(engine);
            assertEquals(1, emittedContexts.size());

            assertEquals(1, findContext(SHARED, emittedContexts).createContextCount);
            assertEquals(0, findContext(SHARED, emittedContexts).patchContextCount);
            try (Context preinitializedContext = Context.newBuilder().engine(engine).build()) {
                assertEquals(1, findContext(SHARED, emittedContexts).patchContextCount);
            }
        }
    }

    /*
     * We create two contexts that trigger separate sharing layers as their areOptionsComptible
     * return false. The implementation should create separate preinitialized contexts for each
     * layer that can be used independently. Part of the previously used context configuration will
     * be passed on to the createContext method during context preinitialization.
     */
    @Test
    public void testCodeSharingTwoLayers() throws Exception {
        try (Engine engine = Engine.create()) {

            final ZoneId systemDefault = ZoneId.systemDefault();
            ZoneId foundId = null;
            for (String zoneId : ZoneId.getAvailableZoneIds()) {
                if (!Objects.equals(ZoneId.of(zoneId), systemDefault)) {
                    foundId = ZoneId.of(zoneId);
                    break;
                }
            }
            final ZoneId testId = foundId;

            // always patchable or this test
            BaseLanguage.registerFunction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                return true;
            });

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").//
                            allowNativeAccess(true).//
                            allowPolyglotAccess(PolyglotAccess.ALL).//
                            timeZone(testId).//
                            allowCreateProcess(true).//
                            allowCreateThread(true).//
                            allowHostClassLookup((s) -> true).//
                            build()) {
                cacheContext.initialize(SHARED);
            }
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "false").build()) {
                cacheContext.initialize(SHARED);
            }

            assertEquals(2, emittedContexts.size());
            emittedContexts.clear();

            AtomicBoolean firstContextInitialized = new AtomicBoolean();
            AtomicBoolean secondContextInitialized = new AtomicBoolean();
            AtomicInteger initializeCount = new AtomicInteger();

            BaseLanguage.registerAction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                assertTrue(env.isPreInitialization());
                assertEquals(0, env.getApplicationArguments().length);
                initializeCount.incrementAndGet();
                if (emittedContexts.size() == 1) {
                    assertTrue(env.getOptions().get(ContextPreInitializationTestSharedLanguage.Option1));
                    assertTrue(env.isCreateProcessAllowed());
                    assertTrue(env.isCreateThreadAllowed());
                    assertFalse(env.isHostLookupAllowed());
                    assertTrue(env.isNativeAccessAllowed());
                    assertTrue(env.isPolyglotEvalAllowed());
                    assertTrue(env.isPolyglotBindingsAccessAllowed());
                    assertEquals(testId, env.getTimeZone());
                    firstContextInitialized.set(true);
                } else {
                    assertTrue(emittedContexts.size() == 2);
                    assertFalse(env.isCreateProcessAllowed());
                    assertFalse(env.isCreateThreadAllowed());
                    assertFalse(env.isHostLookupAllowed());
                    assertFalse(env.isNativeAccessAllowed());
                    assertFalse(env.isPolyglotEvalAllowed());
                    assertFalse(env.isPolyglotBindingsAccessAllowed());
                    assertFalse(env.getOptions().get(ContextPreInitializationTestSharedLanguage.Option1));
                    assertEquals(systemDefault, env.getTimeZone());
                    secondContextInitialized.set(true);
                }
            });

            triggerPreinitialization(engine);

            assertTrue(firstContextInitialized.get());
            assertTrue(secondContextInitialized.get());

            BaseLanguage.registerAction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                initializeCount.incrementAndGet();
            });

            assertEquals(2, emittedContexts.size());
            assertEquals(2, initializeCount.get());

            /*
             * The order of emitted contexts actually matters for this test.
             */
            List<CountingContext> contexts = new ArrayList<>(findContexts(SHARED, emittedContexts));
            assertEquals(2, contexts.size());
            for (CountingContext c : contexts) {
                assertEquals(1, c.createContextCount);
                assertEquals(0, c.patchContextCount);
            }

            // the first context uses the first preinitialized context
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(0, contexts.get(1).patchContextCount);
                cacheContext.initialize(SHARED);
            }

            // the second context uses a new context
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(0, contexts.get(1).patchContextCount);
                assertEquals(2, initializeCount.get());
                cacheContext.initialize(SHARED);
                assertEquals(3, initializeCount.get());
            }

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "false").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(1, contexts.get(1).patchContextCount);
                cacheContext.initialize(SHARED);
            }

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "false").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(1, contexts.get(1).patchContextCount);
                assertEquals(3, initializeCount.get());
                cacheContext.initialize(SHARED);
                assertEquals(4, initializeCount.get());
            }

        }
    }

    /*
     * We create two contexts where sharing is supported with the same layer so only a single
     * context will be preinitialized. We use different context configuration, so the preinitialize
     * config is commoned and defaults to their default configuration if it is different between
     * contexts.
     */
    @Test
    public void testCodeSharingCommonConfig() throws Exception {
        try (Engine engine = Engine.create()) {

            final ZoneId systemDefault = ZoneId.systemDefault();
            ZoneId foundId = null;
            for (String zoneId : ZoneId.getAvailableZoneIds()) {
                if (!Objects.equals(ZoneId.of(zoneId), systemDefault)) {
                    foundId = ZoneId.of(zoneId);
                    break;
                }
            }
            final ZoneId testId = foundId;

            // always patchable or this test
            BaseLanguage.registerFunction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                return true;
            });

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").//
                            option(SHARED + ".Option2", "false").//
                            allowNativeAccess(true).//
                            allowPolyglotAccess(PolyglotAccess.ALL).//
                            timeZone(testId).//
                            allowCreateProcess(true).//
                            allowCreateThread(true).//
                            allowValueSharing(false).//
                            useSystemExit(true).//
                            allowHostClassLookup((s) -> true).//
                            build()) {
                cacheContext.initialize(SHARED);
            }
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                cacheContext.initialize(SHARED);
            }

            assertEquals(2, emittedContexts.size());
            emittedContexts.clear();

            AtomicBoolean firstContextInitialized = new AtomicBoolean();
            AtomicInteger initializeCount = new AtomicInteger();

            BaseLanguage.registerAction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                initializeCount.incrementAndGet();

                assertTrue(env.isPreInitialization());
                assertEquals(0, env.getApplicationArguments().length);
                assertFalse(env.getOptions().hasBeenSet(ContextPreInitializationTestSharedLanguage.Option2));
                assertTrue(env.getOptions().hasBeenSet(ContextPreInitializationTestSharedLanguage.Option1));
                assertTrue(env.getOptions().get(ContextPreInitializationTestSharedLanguage.Option1));
                assertFalse(env.isCreateProcessAllowed());
                assertFalse(env.isCreateThreadAllowed());
                assertFalse(env.isHostLookupAllowed());
                assertFalse(env.isNativeAccessAllowed());
                // polyglot access currently defaults to true for preinit. See GR-14657.
                assertTrue(env.isPolyglotEvalAllowed());
                assertTrue(env.isPolyglotBindingsAccessAllowed());
                assertEquals(systemDefault, env.getTimeZone());
                firstContextInitialized.set(true);
            });

            triggerPreinitialization(engine);

            assertTrue(firstContextInitialized.get());

            BaseLanguage.registerAction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                initializeCount.incrementAndGet();
            });

            assertEquals(1, emittedContexts.size());
            assertEquals(1, initializeCount.get());

            /*
             * The order of emitted contexts actually matters for this test.
             */
            List<CountingContext> contexts = new ArrayList<>(findContexts(SHARED, emittedContexts));
            assertEquals(1, contexts.size());
            for (CountingContext c : contexts) {
                assertEquals(1, c.createContextCount);
                assertEquals(0, c.patchContextCount);
            }

            // the first context uses the first preinitialized context
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                cacheContext.initialize(SHARED);
                assertEquals(1, initializeCount.get());
            }

            // the second context uses a new context
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(1, initializeCount.get());
                cacheContext.initialize(SHARED);
                assertEquals(2, initializeCount.get());
            }

        }
    }

    /*
     * We create two preinitialized contexts for two layers. This time patching the context fails.
     * This test is intended to test recovery from patching failure.
     */
    @Test
    @SuppressWarnings("try")
    public void testCodeSharingTwoLayersIncompatible() throws Exception {
        try (Engine engine = Engine.create()) {
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                cacheContext.initialize(SHARED);
            }
            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "false").build()) {
                cacheContext.initialize(SHARED);
            }

            assertEquals(2, emittedContexts.size());
            emittedContexts.clear();

            AtomicInteger initializeCount = new AtomicInteger();
            BaseLanguage.registerAction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                initializeCount.incrementAndGet();
            });

            triggerPreinitialization(engine);

            assertEquals(2, emittedContexts.size());

            List<CountingContext> contexts = new ArrayList<>(findContexts(SHARED, emittedContexts));
            assertEquals(2, contexts.size());
            for (CountingContext c : findContexts(SHARED, emittedContexts)) {
                assertEquals(1, c.createContextCount);
                assertEquals(0, c.patchContextCount);
            }
            assertEquals(2, initializeCount.get());

            // try to create contexts that are incompatilbe

            BaseLanguage.registerFunction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                return false;
            });

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                assertEquals(2, initializeCount.get());
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(0, contexts.get(1).patchContextCount);

                cacheContext.initialize(SHARED);
                assertEquals(3, initializeCount.get());
            }

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "false").build()) {
                assertEquals(3, initializeCount.get());
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(1, contexts.get(0).patchContextCount);

                cacheContext.initialize(SHARED);
                assertEquals(4, initializeCount.get());
            }

            // both contexts were tried but failed to patch, so should not be tried again
            BaseLanguage.registerFunction(ContextPreInitializationTestSharedLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                return true;
            });

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "true").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(1, contexts.get(0).patchContextCount);
            }

            try (Context cacheContext = Context.newBuilder().engine(engine).option(SHARED + ".Option1", "false").build()) {
                assertEquals(1, contexts.get(0).patchContextCount);
                assertEquals(1, contexts.get(0).patchContextCount);
            }
        }
    }

    /*
     * We test context preinitialization with used instruments.
     */
    @Test
    @SuppressWarnings("try")
    public void testCodeSharingWithInstruments() throws Exception {
        try (Engine engine = Engine.newBuilder().option(ContextPreInitializationFirstInstrument.ID, "true").build()) {

            try (Context cacheContext = Context.newBuilder().engine(engine).build()) {
                cacheContext.initialize(SHARED);
            }
            assertEquals(1, emittedContexts.size());
            emittedContexts.clear();

            triggerPreinitialization(engine);
            assertEquals(1, emittedContexts.size());

            assertEquals(1, findContext(SHARED, emittedContexts).createContextCount);
            assertEquals(0, findContext(SHARED, emittedContexts).patchContextCount);
            try (Context preinitializedContext = Context.newBuilder().engine(engine).build()) {
                assertEquals(1, findContext(SHARED, emittedContexts).patchContextCount);
            }
        }
    }

    private void triggerPreinitialization(Engine engine) throws ReflectiveOperationException {
        TestAPIAccessor.engineAccess().preinitializeContext(findImpl().getAPIAccess().getEngineReceiver(engine));
    }

    AbstractPolyglotImpl findImpl() throws ReflectiveOperationException {
        Method getImplMethod = Engine.class.getDeclaredMethod("getImpl");
        ReflectionUtils.setAccessible(getImplMethod, true);
        return (AbstractPolyglotImpl) getImplMethod.invoke(null);
    }

    @Test
    public void testAccessPriviledgePatching() throws ReflectiveOperationException {
        setPatchable(FIRST, SECOND);

        doContextPreinitialize(FIRST, SECOND);

        /*
         * If language privileges would not be patched then FIRST would be accessible as public
         * language. Context preinitialization always happens without any restrictions on the
         * context set.
         */
        try (Context context = Context.create(SECOND)) {
            context.enter();
            Env env = ContextPreInitializationTestSecondLanguage.getCurrentContext();
            assertFalse(env.getPublicLanguages().containsKey(FIRST));
            assertTrue(env.getInternalLanguages().containsKey(FIRST));
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testFailToLookupInstrumentDuringContextPreInitialization() throws Exception {
        setPatchable(FIRST);
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
            InstrumentInfo instrumentInfo = env.getInstruments().get(ContextPreInitializationSecondInstrument.ID);
            assertNotNull("Cannot find instrument " + ContextPreInitializationSecondInstrument.ID, instrumentInfo);
            try {
                env.lookup(instrumentInfo, Service.class);
                Assert.fail("Expected IllegalStateException");
            } catch (IllegalStateException ise) {
                // Expected exception
            }
        });
        doContextPreinitialize(FIRST);
    }

    @Test
    public void testIsSameFileAllowedIO() throws Exception {
        IsSameFileResult res = testIsSameFileImpl(IOAccess.ALL);
        assertTrue(res.imageBuildInternalFile.isSameFile(res.imageBuildPublicFile));
        assertTrue(res.imageBuildInternalFile.isSameFile(res.imageExecInternalFile));
        assertTrue(res.imageBuildInternalFile.isSameFile(res.imageExecPublicFile));
        assertTrue(res.imageBuildPublicFile.isSameFile(res.imageBuildInternalFile));
        assertTrue(res.imageBuildPublicFile.isSameFile(res.imageExecInternalFile));
        assertTrue(res.imageBuildPublicFile.isSameFile(res.imageExecPublicFile));
        assertTrue(res.imageExecInternalFile.isSameFile(res.imageBuildInternalFile));
        assertTrue(res.imageExecInternalFile.isSameFile(res.imageBuildPublicFile));
        assertTrue(res.imageExecInternalFile.isSameFile(res.imageExecPublicFile));
        assertTrue(res.imageExecPublicFile.isSameFile(res.imageBuildInternalFile));
        assertTrue(res.imageExecPublicFile.isSameFile(res.imageBuildPublicFile));
        assertTrue(res.imageExecPublicFile.isSameFile(res.imageExecInternalFile));
    }

    @Test
    public void testIsSameFileDeniedIO() throws Exception {
        IsSameFileResult res = testIsSameFileImpl(IOAccess.NONE);
        assertFalse(res.imageBuildInternalFile.isSameFile(res.imageBuildPublicFile));
        assertTrue(res.imageBuildInternalFile.isSameFile(res.imageExecInternalFile));
        assertFalse(res.imageBuildInternalFile.isSameFile(res.imageExecPublicFile));
        assertFalse(res.imageBuildPublicFile.isSameFile(res.imageBuildInternalFile));
        assertFalse(res.imageBuildPublicFile.isSameFile(res.imageExecInternalFile));
        assertTrue(res.imageBuildPublicFile.isSameFile(res.imageExecPublicFile));
        assertTrue(res.imageExecInternalFile.isSameFile(res.imageBuildInternalFile));
        assertFalse(res.imageExecInternalFile.isSameFile(res.imageBuildPublicFile));
        assertFalse(res.imageExecInternalFile.isSameFile(res.imageExecPublicFile));
        assertFalse(res.imageExecPublicFile.isSameFile(res.imageBuildInternalFile));
        assertTrue(res.imageExecPublicFile.isSameFile(res.imageBuildPublicFile));
        assertFalse(res.imageExecPublicFile.isSameFile(res.imageExecInternalFile));
    }

    @Test
    public void testIsSameFileCustomFileSystem() throws Exception {
        IsSameFileResult res = testIsSameFileImpl(IOAccess.newBuilder().fileSystem(FileSystem.newDefaultFileSystem()).build());
        assertTrue(res.imageBuildInternalFile.isSameFile(res.imageBuildPublicFile));
        assertTrue(res.imageBuildInternalFile.isSameFile(res.imageExecInternalFile));
        assertTrue(res.imageBuildInternalFile.isSameFile(res.imageExecPublicFile));
        assertTrue(res.imageBuildPublicFile.isSameFile(res.imageBuildInternalFile));
        assertTrue(res.imageBuildPublicFile.isSameFile(res.imageExecInternalFile));
        assertTrue(res.imageBuildPublicFile.isSameFile(res.imageExecPublicFile));
        assertTrue(res.imageExecInternalFile.isSameFile(res.imageBuildInternalFile));
        assertTrue(res.imageExecInternalFile.isSameFile(res.imageBuildPublicFile));
        assertTrue(res.imageExecInternalFile.isSameFile(res.imageExecPublicFile));
        assertTrue(res.imageExecPublicFile.isSameFile(res.imageBuildInternalFile));
        assertTrue(res.imageExecPublicFile.isSameFile(res.imageBuildPublicFile));
        assertTrue(res.imageExecPublicFile.isSameFile(res.imageExecInternalFile));
    }

    @Test
    public void testEnvValidInFinalizeContext() throws Exception {
        BaseLanguage.registerAction(BaseLanguage.class, ActionKind.ON_FINALIZE_CONTEXT, (env) -> {
            env.parseInternal(com.oracle.truffle.api.source.Source.newBuilder(FIRST, "", "onfinalize").build());
        });
        setPatchable(FIRST);
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        try (Context ctx = Context.create()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(3, contexts.size());
            Collection<? extends CountingContext> firstLangCtxs = findContexts(FIRST, contexts);
            firstLangCtxs.remove(firstLangCtx);
            assertFalse(firstLangCtxs.isEmpty());
            final CountingContext firstLangCtx2 = firstLangCtxs.iterator().next();
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
            assertEquals(1, firstLangCtx.disposeContextCount);
            assertEquals(2, firstLangCtx.initializeThreadCount); // Close initializes thread
            assertEquals(2, firstLangCtx.disposeThreadCount);    // Close initializes thread
            assertEquals(1, secondLangCtx.createContextCount);
            assertEquals(1, secondLangCtx.initializeContextCount);
            assertEquals(1, secondLangCtx.patchContextCount);
            assertEquals(1, secondLangCtx.disposeContextCount);
            assertEquals(2, secondLangCtx.initializeThreadCount);    // Close initializes thread
            assertEquals(2, secondLangCtx.disposeThreadCount);       // Close initializes thread
            assertEquals(1, firstLangCtx2.createContextCount);
            assertEquals(1, firstLangCtx2.initializeContextCount);
            assertEquals(0, firstLangCtx2.patchContextCount);
            assertEquals(0, firstLangCtx2.disposeContextCount);
            assertEquals(1, firstLangCtx2.initializeThreadCount);
            assertEquals(0, firstLangCtx2.disposeThreadCount);
        }
    }

    @Test
    public void testContextThreadLocals() throws Exception {
        BaseLanguage.registerAction(BaseLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
            ContextThreadLocal<AtomicInteger> threadState = ContextPreInitializationTestFirstLanguage.getThreadStateReference();
            assertEquals(0, threadState.get().getAndIncrement());
        });
        BaseLanguage.registerAction(BaseLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
            ContextThreadLocal<AtomicInteger> threadState = ContextPreInitializationTestFirstLanguage.getThreadStateReference();
            assertEquals(0, threadState.get().get());
        });
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
    }

    @Test
    public void testContextLocals() throws Exception {
        BaseLanguage.registerAction(BaseLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
            ContextLocal<AtomicInteger> threadState = ContextPreInitializationTestFirstLanguage.getStateReference();
            assertEquals(0, threadState.get().getAndIncrement());
        });
        BaseLanguage.registerAction(BaseLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
            ContextLocal<AtomicInteger> threadState = ContextPreInitializationTestFirstLanguage.getStateReference();
            assertEquals(1, threadState.get().get());
        });
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
    }

    @Test
    public void testUsePreInitializedContextOptionEnabled() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        try (Context ctx = Context.newBuilder().allowExperimentalOptions(true).option("engine.UsePreInitializedContext", "true").build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
        }
    }

    @Test
    public void testUsePreInitializedContextOptionDisabled() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        try (Context ctx = Context.newBuilder().allowExperimentalOptions(true).option("engine.UsePreInitializedContext", "false").build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(2, contexts.size());
            contexts = new ArrayList<>(emittedContexts);
            contexts.remove(firstLangCtx);
            CountingContext firstLangCtx2 = findContext(FIRST, contexts);
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(0, firstLangCtx.patchContextCount);
            assertEquals(1, firstLangCtx2.createContextCount);
            assertEquals(1, firstLangCtx2.initializeContextCount);
            assertEquals(0, firstLangCtx2.patchContextCount);
        }
    }

    @Test
    public void testThreadLocalActions() throws Exception {
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_EXECUTE, (env) -> {
            env.submitThreadLocal(new Thread[]{Thread.currentThread()}, new ThreadLocalAction(false, false) {
                @Override
                protected void perform(Access access) {
                    // Empty action just to do TraceStackTraceInterval logging
                }
            });
        });
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        TestHandler handler = new TestHandler("engine");
        try (Context ctx = Context.newBuilder().allowExperimentalOptions(true).logHandler(handler).option("engine.TraceThreadLocalActions", "true").build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
        }
        Optional<String> message = handler.logs.stream().map((r) -> r.getMessage()).filter((m) -> m.contains("[tl]")).findAny();
        assertTrue(message.isPresent());
    }

    @Test
    @SuppressWarnings("try")
    public void testRestrictedPermittedLanguagesMatched() throws Exception {
        setPatchable(FIRST, SECOND, INTERNAL);
        ContextPreInitializationTestFirstLanguage.callDependentLanguage = true;
        ContextPreInitializationTestSecondLanguage.callDependentLanguageInCreate = true;
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(3, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        CountingContext internalLangCtx = findContext(INTERNAL, contexts);
        assertNotNull(internalLangCtx);
        try (Context ctx = Context.newBuilder(SECOND).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            ctx.eval(Source.create(SECOND, "test"));
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(3, contexts.size());
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testRestrictedPermittedLanguagesUnmatched() throws Exception {
        setPatchable(FIRST, SECOND, INTERNAL, SHARED);
        ContextPreInitializationTestFirstLanguage.callDependentLanguage = true;
        ContextPreInitializationTestSecondLanguage.callDependentLanguageInCreate = true;
        doContextPreinitialize(FIRST, SECOND, SHARED);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(4, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        CountingContext internalLangCtx = findContext(INTERNAL, contexts);
        assertNotNull(internalLangCtx);
        CountingContext sharedLangCtx = findContext(SHARED, contexts);
        assertNotNull(sharedLangCtx);
        try (Context ctx = Context.newBuilder(SECOND).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            ctx.eval(Source.create(SECOND, "test"));
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(7, contexts.size());
        }
    }

    @Test
    public void testInstrumentLogger() throws Exception {
        ContextPreInitializationFirstInstrument.actions = Collections.singletonMap("onCreate", (e) -> {
            TruffleLogger logger = e.env.getLogger((String) null);
            logger.log(Level.INFO, "CREATED");
        });
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        try (Context ctx = Context.newBuilder().option(ContextPreInitializationFirstInstrument.ID, "true").option("log.level", "OFF").build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
            assertEquals(0, firstLangCtx.disposeContextCount);
            assertEquals(2, firstLangCtx.initializeThreadCount);
            assertEquals(1, firstLangCtx.disposeThreadCount);
        }
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(1, firstLangCtx.patchContextCount);
        assertEquals(1, firstLangCtx.disposeContextCount);
        assertEquals(2, firstLangCtx.initializeThreadCount);
        assertEquals(2, firstLangCtx.disposeThreadCount);
    }

    @Test
    public void testSandboxPolicySuccess() throws Exception {
        Assume.assumeFalse("Restricted Truffle compiler options are specified on the command line.", executedWithXCompOptions());
        setPatchable(CONSTRAINED);
        doContextPreinitialize(CONSTRAINED);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext constrainedLangCtx = findContext(CONSTRAINED, contexts);
        assertNotNull(constrainedLangCtx);
        assertEquals(1, constrainedLangCtx.createContextCount);
        assertEquals(1, constrainedLangCtx.initializeContextCount);
        assertEquals(0, constrainedLangCtx.patchContextCount);
        assertEquals(0, constrainedLangCtx.disposeContextCount);
        assertEquals(1, constrainedLangCtx.initializeThreadCount);
        assertEquals(1, constrainedLangCtx.disposeThreadCount);
        try (Context ctx = Context.newBuilder(CONSTRAINED).sandbox(SandboxPolicy.CONSTRAINED).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).option(
                        ContextPreInitializationConstrainedInstrument.ID, "true").option(CONSTRAINED + ".Constrained", "true").build()) {
            Value res = ctx.eval(Source.create(CONSTRAINED, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            assertEquals(1, constrainedLangCtx.createContextCount);
            assertEquals(1, constrainedLangCtx.initializeContextCount);
            assertEquals(1, constrainedLangCtx.patchContextCount);
            assertEquals(0, constrainedLangCtx.disposeContextCount);
            assertEquals(2, constrainedLangCtx.initializeThreadCount);
            assertEquals(1, constrainedLangCtx.disposeThreadCount);
        }
    }

    @Test
    public void testSandboxPolicyLanguageFailure() throws Exception {
        Assume.assumeFalse("Restricted Truffle compiler options are specified on the command line.", executedWithXCompOptions());
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);

        AbstractPolyglotTest.assertFails(() -> {
            Context.newBuilder(FIRST).sandbox(SandboxPolicy.CONSTRAINED).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).build();
        }, IllegalArgumentException.class, (e) -> {
            assertTrue(e.getMessage().contains("The language ContextPreInitializationFirst can only be used up to the TRUSTED sandbox policy."));
        });
    }

    @Test
    public void testSandboxPolicyInstrumentFailure() throws Exception {
        Assume.assumeFalse("Restricted Truffle compiler options are specified on the command line.", executedWithXCompOptions());
        setPatchable(CONSTRAINED);
        doContextPreinitialize(CONSTRAINED);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext constrainedLangCtx = findContext(CONSTRAINED, contexts);
        assertNotNull(constrainedLangCtx);
        assertEquals(1, constrainedLangCtx.createContextCount);
        assertEquals(1, constrainedLangCtx.initializeContextCount);
        assertEquals(0, constrainedLangCtx.patchContextCount);
        assertEquals(0, constrainedLangCtx.disposeContextCount);
        assertEquals(1, constrainedLangCtx.initializeThreadCount);
        assertEquals(1, constrainedLangCtx.disposeThreadCount);
        AbstractPolyglotTest.assertFails(() -> {
            Context.newBuilder(CONSTRAINED).sandbox(SandboxPolicy.CONSTRAINED).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).option(
                            ContextPreInitializationFirstInstrument.ID, "true").build();
        }, IllegalArgumentException.class, (e) -> {
            assertTrue(e.getMessage().contains("The instrument ContextPreInitializationFirstInstrument can only be used up to the TRUSTED sandbox policy."));
        });
    }

    @Test
    public void testSandboxPolicyInstrumentOptionFailure() throws Exception {
        Assume.assumeFalse("Restricted Truffle compiler options are specified on the command line.", executedWithXCompOptions());
        setPatchable(CONSTRAINED);
        doContextPreinitialize(CONSTRAINED);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext constrainedLangCtx = findContext(CONSTRAINED, contexts);
        assertNotNull(constrainedLangCtx);
        assertEquals(1, constrainedLangCtx.createContextCount);
        assertEquals(1, constrainedLangCtx.initializeContextCount);
        assertEquals(0, constrainedLangCtx.patchContextCount);
        assertEquals(0, constrainedLangCtx.disposeContextCount);
        assertEquals(1, constrainedLangCtx.initializeThreadCount);
        assertEquals(1, constrainedLangCtx.disposeThreadCount);
        AbstractPolyglotTest.assertFails(() -> {
            Context.newBuilder(CONSTRAINED).sandbox(SandboxPolicy.CONSTRAINED).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).option(
                            ContextPreInitializationConstrainedInstrument.ID, "true").option(ContextPreInitializationConstrainedInstrument.ID + ".Trusted", "true").build();
        }, IllegalArgumentException.class, (e) -> {
            assertTrue(e.getMessage().contains("The option ContextPreInitializationConstrainedInstrument.Trusted can only be used up to the TRUSTED sandbox policy."));
        });
    }

    @Test
    public void testSandboxPolicyLanguageOptionFailure() throws Exception {
        Assume.assumeFalse("Restricted Truffle compiler options are specified on the command line.", executedWithXCompOptions());
        setPatchable(CONSTRAINED);
        doContextPreinitialize(CONSTRAINED);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext constrainedLangCtx = findContext(CONSTRAINED, contexts);
        assertNotNull(constrainedLangCtx);
        assertEquals(1, constrainedLangCtx.createContextCount);
        assertEquals(1, constrainedLangCtx.initializeContextCount);
        assertEquals(0, constrainedLangCtx.patchContextCount);
        assertEquals(0, constrainedLangCtx.disposeContextCount);
        assertEquals(1, constrainedLangCtx.initializeThreadCount);
        assertEquals(1, constrainedLangCtx.disposeThreadCount);
        AbstractPolyglotTest.assertFails(() -> {
            Context.newBuilder(CONSTRAINED).sandbox(SandboxPolicy.CONSTRAINED).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).option(CONSTRAINED + ".Trusted",
                            "true").build();
        }, IllegalArgumentException.class, (e) -> {
            assertTrue(e.getMessage().contains("The option ContextPreInitializationConstrained.Trusted can only be used up to the TRUSTED sandbox policy."));
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLanguageInternalResources() throws Exception {
        TruffleTestAssumptions.assumeNotAOT();
        setPatchable(FIRST);
        List<TruffleFile> files = new ArrayList<>();
        try (TemporaryResourceCacheRoot imageBuildTimeCacheRoot = new TemporaryResourceCacheRoot(false)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, newResourceBuildTimeVerifier(files));
            doContextPreinitialize(FIRST);
            assertFalse(files.isEmpty());
        }
        Path runtimeCacheRoot = Files.createTempDirectory(null).toRealPath();
        Engine.copyResources(runtimeCacheRoot, FIRST);
        try (TemporaryResourceCacheRoot imageExecutionCacheRoot = new TemporaryResourceCacheRoot(runtimeCacheRoot, true)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, newResourceExecutionTimeVerifier(files, runtimeCacheRoot.toString()));
            try (Context ctx = Context.create()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInternalResourcesInNonPreInitializedEngine() throws Exception {
        TruffleTestAssumptions.assumeNotAOT();
        setPatchable(FIRST);
        List<TruffleFile> files = new ArrayList<>();
        try (TemporaryResourceCacheRoot imageBuildTimeCacheRoot = new TemporaryResourceCacheRoot(false)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, newResourceBuildTimeVerifier(files));
            doContextPreinitialize(FIRST);
            assertFalse(files.isEmpty());
        }
        Path runtimeCacheRoot = Files.createTempDirectory(null).toRealPath();
        Engine.copyResources(runtimeCacheRoot, FIRST);
        System.setProperty("polyglot.engine.resourcePath", runtimeCacheRoot.toString());
        try (Engine engine = Engine.create()) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                throw CompilerDirectives.shouldNotReachHere("Pre-initialized context should not be used.");
            });
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, newResourceNonPreInitializedContextVerifier(runtimeCacheRoot.toString()));
            try (Context ctx = Context.newBuilder().engine(engine).build()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        } finally {
            System.getProperties().remove("polyglot.engine.resourcePath");
            TemporaryResourceCacheRoot.reset(false);
        }
    }

    private static Consumer<Env> newResourceBuildTimeVerifier(List<TruffleFile> files) {
        return (env) -> {
            try {
                TruffleFile root = env.getInternalResource(ContextPreInitializationResource.class);
                assertNotNull(root);
                assertTrue(root.isAbsolute());
                TruffleFile resource = root.resolve(ContextPreInitializationResource.FILE_NAME);
                assertNotNull(resource);
                assertTrue(resource.isAbsolute());
                assertEquals(ContextPreInitializationResource.FILE_CONTENT, new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                files.add(resource);
            } catch (IOException ioe) {
                throw new AssertionError(ioe);
            }
        };
    }

    private static Consumer<Env> newResourceNonPreInitializedContextVerifier(String expectedRootPrefix) {
        return (env) -> {
            try {
                ContextPreInitializationResource.unpackCount = 0;
                TruffleFile root = env.getInternalResource(ContextPreInitializationResource.class);
                assertEquals(0, ContextPreInitializationResource.unpackCount);
                assertNotNull(root);
                assertTrue(root.isAbsolute());
                TruffleFile resource = root.resolve(ContextPreInitializationResource.FILE_NAME);
                assertNotNull(resource);
                assertTrue(resource.isAbsolute());
                assertTrue(resource.getAbsoluteFile().toString().startsWith(expectedRootPrefix));
                assertEquals(ContextPreInitializationResource.FILE_CONTENT, new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ioe) {
                throw new AssertionError(ioe);
            }
        };
    }

    private static Consumer<Env> newResourceExecutionTimeVerifier(List<TruffleFile> files, String expectedRootPrefix) {
        return (env) -> {
            try {
                TruffleFile file1 = files.get(0);
                assertTrue(file1.isAbsolute());
                assertEquals(ContextPreInitializationResource.FILE_CONTENT, new String(file1.readAllBytes(), StandardCharsets.UTF_8));
                ContextPreInitializationResource.unpackCount = 0;
                TruffleFile root = env.getInternalResource(ContextPreInitializationResource.class);
                assertNotNull(root);
                assertTrue(root.isAbsolute());
                assertEquals(0, ContextPreInitializationResource.unpackCount);
                TruffleFile file2 = root.resolve(ContextPreInitializationResource.FILE_NAME);
                assertNotNull(file2);
                assertTrue(file2.isAbsolute());
                assertEquals(ContextPreInitializationResource.FILE_CONTENT, new String(file2.readAllBytes(), StandardCharsets.UTF_8));
                assertEquals(file1, file2);
                assertEquals(file1.getAbsoluteFile(), file2.getAbsoluteFile());
                assertTrue(file1.getAbsoluteFile().toString().startsWith(expectedRootPrefix));
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        };
    }

    @Test
    @SuppressWarnings("try")
    public void testSourcesForInternalResources() throws Exception {
        TruffleTestAssumptions.assumeNotAOT();
        setPatchable(FIRST);
        List<com.oracle.truffle.api.source.Source> sources = new ArrayList<>();
        try (TemporaryResourceCacheRoot imageBuildTimeCacheRoot = new TemporaryResourceCacheRoot(false)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                try {
                    TruffleFile root = env.getInternalResource(ContextPreInitializationResource.class);
                    TruffleFile sourceFile = root.resolve(ContextPreInitializationResource.FILE_NAME);
                    var source = com.oracle.truffle.api.source.Source.newBuilder(FIRST, sourceFile).build();
                    sources.add(source);
                } catch (IOException ioe) {
                    throw new AssertionError(ioe);
                }
            });
            doContextPreinitialize(FIRST);
            assertFalse(sources.isEmpty());
        }
        Path runtimeCacheRoot = Files.createTempDirectory(null).toRealPath();
        Engine.copyResources(runtimeCacheRoot, FIRST);
        try (TemporaryResourceCacheRoot imageExecutionCacheRoot = new TemporaryResourceCacheRoot(runtimeCacheRoot, true)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                try {
                    var source1 = sources.get(0);
                    ContextPreInitializationResource.unpackCount = 0;
                    TruffleFile root = env.getInternalResource(ContextPreInitializationResource.class);
                    TruffleFile sourceFile = root.resolve(ContextPreInitializationResource.FILE_NAME);
                    var source2 = com.oracle.truffle.api.source.Source.newBuilder(FIRST, sourceFile).build();
                    assertEquals(source1, source2);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });
            try (Context ctx = Context.create()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInstrumentInternalResources() throws Exception {
        TruffleTestAssumptions.assumeNotAOT();
        setPatchable(FIRST);
        AtomicReference<TruffleFile> rootRef = new AtomicReference<>();
        ContextPreInitializationFirstInstrument.actions = Collections.singletonMap("onContextCreated", (e) -> {
            try {
                TruffleFile root = e.env.getInternalResource(ContextPreInitializationResource.class);
                assertNotNull(root);
                assertTrue(root.isAbsolute());
                TruffleFile resource = root.resolve(ContextPreInitializationResource.FILE_NAME);
                assertNotNull(resource);
                assertTrue(resource.isAbsolute());
                assertEquals(ContextPreInitializationResource.FILE_CONTENT, new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                rootRef.set(root);
            } catch (IOException ioe) {
                throw new AssertionError(ioe);
            }
        });
        doContextPreinitialize(FIRST);
        assertNull(rootRef.get());
        Path runtimeCacheRoot = Files.createTempDirectory(null).toRealPath();
        Engine.copyResources(runtimeCacheRoot, ContextPreInitializationFirstInstrument.ID);
        try (TemporaryResourceCacheRoot imageExecutionCacheRoot = new TemporaryResourceCacheRoot(runtimeCacheRoot, true)) {
            try (Context ctx = Context.newBuilder().option(ContextPreInitializationFirstInstrument.ID, "true").allowIO(IOAccess.ALL).build()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
                TruffleFile root = rootRef.get();
                assertNotNull(root);
                assertTrue(root.getAbsoluteFile().toString().startsWith(runtimeCacheRoot.toString()));
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testOverriddenCacheRoot() throws Exception {
        TruffleTestAssumptions.assumeNotAOT();
        setPatchable(FIRST);
        List<TruffleFile> files = new ArrayList<>();
        try (TemporaryResourceCacheRoot imageBuildTimeCacheRoot = new TemporaryResourceCacheRoot(false)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, newResourceBuildTimeVerifier(files));
            doContextPreinitialize(FIRST);
            assertFalse(files.isEmpty());
        }
        Path overriddenCacheRoot = Files.createTempDirectory(null).toRealPath();
        Engine.copyResources(overriddenCacheRoot, FIRST);
        System.setProperty("polyglot.engine.resourcePath", overriddenCacheRoot.toRealPath().toString());
        TemporaryResourceCacheRoot.reset(true);
        try {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT,
                            newResourceExecutionTimeVerifier(files, overriddenCacheRoot.toString()));
            try (Context ctx = Context.create()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        } finally {
            TemporaryResourceCacheRoot.reset(true);
            System.getProperties().remove("polyglot.engine.resourcePath");
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testOverriddenComponentRoot() throws Exception {
        TruffleTestAssumptions.assumeNotAOT();
        setPatchable(FIRST);
        List<TruffleFile> files = new ArrayList<>();
        try (TemporaryResourceCacheRoot imageBuildTimeCacheRoot = new TemporaryResourceCacheRoot(false)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, newResourceBuildTimeVerifier(files));
            doContextPreinitialize(FIRST);
            assertFalse(files.isEmpty());
        }
        Path runtimeCacheRoot = Files.createTempDirectory(null).toRealPath();
        Path overriddenCacheRoot = Files.createTempDirectory(null).toRealPath();
        Engine.copyResources(overriddenCacheRoot, FIRST);
        System.setProperty(String.format("polyglot.engine.resourcePath.%s", FIRST), overriddenCacheRoot.resolve(FIRST).toRealPath().toString());
        try (TemporaryResourceCacheRoot imageExecutionCacheRoot = new TemporaryResourceCacheRoot(runtimeCacheRoot, true)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT,
                            newResourceExecutionTimeVerifier(files, overriddenCacheRoot.toString()));
            try (Context ctx = Context.create()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        } finally {
            System.getProperties().remove(String.format("polyglot.engine.resourcePath.%s", FIRST));
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testOverriddenResourceRoot() throws Exception {
        TruffleTestAssumptions.assumeNotAOT();
        setPatchable(FIRST);
        List<TruffleFile> files = new ArrayList<>();
        try (TemporaryResourceCacheRoot imageBuildTimeCacheRoot = new TemporaryResourceCacheRoot(false)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, newResourceBuildTimeVerifier(files));
            doContextPreinitialize(FIRST);
            assertFalse(files.isEmpty());
        }
        Path runtimeCacheRoot = Files.createTempDirectory(null).toRealPath();
        Path overriddenCacheRoot = Files.createTempDirectory(null).toRealPath();
        Engine.copyResources(overriddenCacheRoot, FIRST);
        System.setProperty(String.format("polyglot.engine.resourcePath.%s.%s", FIRST, ContextPreInitializationResource.ID),
                        overriddenCacheRoot.resolve(FIRST).resolve(ContextPreInitializationResource.ID).toRealPath().toString());
        try (TemporaryResourceCacheRoot imageExecutionCacheRoot = new TemporaryResourceCacheRoot(runtimeCacheRoot, true)) {
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT,
                            newResourceExecutionTimeVerifier(files, overriddenCacheRoot.toString()));
            try (Context ctx = Context.create()) {
                Value res = ctx.eval(Source.create(FIRST, "test"));
                assertEquals("test", res.asString());
            }
        } finally {
            System.getProperties().remove(String.format("polyglot.engine.resourcePath.%s.%s", FIRST, ContextPreInitializationResource.ID));
        }
    }

    @Test
    public void testPatchNotCalledOnNonAllowedLanguage() throws ReflectiveOperationException {
        setPatchable(FIRST, SECOND);
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        assertEquals(0, firstLangCtx.disposeContextCount);
        assertEquals(1, firstLangCtx.initializeThreadCount);
        assertEquals(1, firstLangCtx.disposeThreadCount);
        assertEquals(1, secondLangCtx.createContextCount);
        assertEquals(1, secondLangCtx.initializeContextCount);
        assertEquals(0, secondLangCtx.patchContextCount);
        assertEquals(0, secondLangCtx.disposeContextCount);
        assertEquals(1, secondLangCtx.initializeThreadCount);
        assertEquals(1, secondLangCtx.disposeThreadCount);
        try (Context ctx = Context.create(FIRST)) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(3, contexts.size());
            Collection<? extends CountingContext> firstLangCtxs = findContexts(FIRST, contexts);
            firstLangCtxs.remove(firstLangCtx);
            assertEquals(1, firstLangCtxs.size());
            CountingContext firstLangCtx2 = firstLangCtxs.iterator().next();
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
            assertEquals(1, firstLangCtx.disposeContextCount);
            assertEquals(2, firstLangCtx.initializeThreadCount); // Close initializes thread
            assertEquals(2, firstLangCtx.disposeThreadCount);    // Close initializes thread
            assertEquals(1, secondLangCtx.createContextCount);
            assertEquals(1, secondLangCtx.initializeContextCount);
            assertEquals(0, secondLangCtx.patchContextCount);
            assertEquals(1, secondLangCtx.disposeContextCount);
            assertEquals(2, secondLangCtx.initializeThreadCount);    // Close initializes thread
            assertEquals(2, secondLangCtx.disposeThreadCount);       // Close initializes thread
            assertEquals(1, firstLangCtx2.createContextCount);
            assertEquals(1, firstLangCtx2.initializeContextCount);
            assertEquals(0, firstLangCtx2.patchContextCount);
            assertEquals(0, firstLangCtx2.disposeContextCount);
            assertEquals(1, firstLangCtx2.initializeThreadCount);
            assertEquals(0, firstLangCtx2.disposeThreadCount);
        }
    }

    private static boolean executedWithXCompOptions() {
        Properties props = System.getProperties();
        return props.containsKey("polyglot.engine.CompileImmediately") || props.containsKey("polyglot.engine.BackgroundCompilation");
    }

    private static IsSameFileResult testIsSameFileImpl(IOAccess ioAccess) throws ReflectiveOperationException {
        String path = Paths.get(".").toAbsolutePath().toString();
        setPatchable(FIRST);
        IsSameFileResult result = new IsSameFileResult();
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
            result.imageBuildInternalFile = env.getInternalTruffleFile(path);
            result.imageBuildPublicFile = env.getPublicTruffleFile(path);
        });
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertEquals(1, firstLangCtx.createContextCount);
        assertEquals(1, firstLangCtx.initializeContextCount);
        assertEquals(0, firstLangCtx.patchContextCount);
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
            result.imageExecInternalFile = env.getInternalTruffleFile(path);
            result.imageExecPublicFile = env.getPublicTruffleFile(path);
        });
        Context.Builder builder = Context.newBuilder().allowIO(ioAccess);
        try (Context ctx = builder.build()) {
            Value res = ctx.eval(Source.create(FIRST, "test"));
            assertEquals("test", res.asString());
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            assertEquals(1, firstLangCtx.createContextCount);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertEquals(1, firstLangCtx.patchContextCount);
            return result;
        }
    }

    private static final class IsSameFileResult {
        TruffleFile imageBuildPublicFile;
        TruffleFile imageBuildInternalFile;
        TruffleFile imageExecPublicFile;
        TruffleFile imageExecInternalFile;
    }

    private static com.oracle.truffle.api.source.Source createSource(TruffleLanguage.Env env, Path resource, boolean cached) {
        try {
            TruffleFile file = env.getInternalTruffleFile(resource.toString());
            assertNotNull(file);
            return com.oracle.truffle.api.source.Source.newBuilder(FIRST, file).cached(cached).build();
        } catch (IOException ioe) {
            throw new AssertionError(ioe);
        }
    }

    private static void delete(Path file) throws IOException {
        if (Files.isDirectory(file)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
                for (Path child : stream) {
                    delete(child);
                }
            }
        }
        Files.delete(file);
    }

    private static Path write(Path path, CharSequence... lines) throws IOException {
        Files.write(path, Arrays.asList(lines), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        return path;
    }

    private static String read(TruffleFile file) throws IOException {
        return new String(file.readAllBytes());
    }

    private static void resetSystemPropertiesOptions() {
        System.clearProperty("polyglot.image-build-time.PreinitializeContexts");
        System.clearProperty(SYS_OPTION1_KEY);
        System.clearProperty(SYS_OPTION2_KEY);
    }

    private static void resetLanguageHomes() throws ReflectiveOperationException {
        Class<?> languageCache = Class.forName("com.oracle.truffle.polyglot.LanguageCache");
        Method reset = languageCache.getDeclaredMethod("resetNativeImageCacheLanguageHomes");
        ReflectionUtils.setAccessible(reset, true);
        reset.invoke(null);
    }

    private static void doContextPreinitialize(String... languages) throws ReflectiveOperationException {
        ContextPreInitializationResource.preInitialization = true;
        setPreInitializeOption(languages);
        try {
            final Class<?> holderClz = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, ContextPreInitializationTest.class.getClassLoader());
            final Method preInitMethod = holderClz.getDeclaredMethod("preInitializeEngine");
            ReflectionUtils.setAccessible(preInitMethod, true);
            preInitMethod.invoke(null);
        } finally {
            ContextPreInitializationResource.preInitialization = false;
            // PreinitializeContexts should only be set during pre-initialization, not at runtime
            clearPreInitializeOption();
        }
    }

    private static void setPreInitializeOption(String... languages) {
        final StringBuilder languagesOptionValue = new StringBuilder();
        for (String language : languages) {
            languagesOptionValue.append(language).append(',');
        }
        if (languagesOptionValue.length() > 0) {
            languagesOptionValue.replace(languagesOptionValue.length() - 1, languagesOptionValue.length(), "");
            System.setProperty("polyglot.image-build-time.PreinitializeContexts", languagesOptionValue.toString());
        }
    }

    private static void clearPreInitializeOption() {
        System.clearProperty("polyglot.image-build-time.PreinitializeContexts");
    }

    @SuppressWarnings("unchecked")
    private static Set<Path> getActiveFileHandlers() throws ReflectiveOperationException {
        Class<?> polyglotLoggersClass = Class.forName("com.oracle.truffle.polyglot.PolyglotLoggers");
        Method m = polyglotLoggersClass.getDeclaredMethod("getActiveFileHandlers");
        ReflectionUtils.setAccessible(m, true);
        return (Set<Path>) m.invoke(null);
    }

    private static Collection<? extends CountingContext> findContexts(
                    final String languageId,
                    Collection<? extends CountingContext> contexts) {
        final Set<CountingContext> result = new LinkedHashSet<>();
        for (CountingContext context : contexts) {
            if (context.getLanguageId().equals(languageId)) {
                result.add(context);
            }
        }
        return result;
    }

    private static CountingContext findContext(
                    final String languageId,
                    Collection<? extends CountingContext> contexts) {
        final Collection<? extends CountingContext> found = findContexts(languageId, contexts);
        return found.isEmpty() ? null : found.iterator().next();
    }

    static void setPatchable(String... languageIds) {
        patchableLanguages.clear();
        Collections.addAll(patchableLanguages, languageIds);
    }

    private static int nextId() {
        int id = NEXT_ORDER_INDEX.getAndIncrement();
        return id;
    }

    private static void write(final OutputStream out, final String content) {
        try {
            out.write(content.getBytes("UTF-8"));
            out.flush();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    static class CountingContext {
        private final String id;
        private TruffleLanguage.Env env;
        int createContextCount = 0;
        int createContextOrder = -1;
        int initializeContextCount = 0;
        int initializeContextOrder = -1;
        int patchContextCount = 0;
        int patchContextOrder = -1;
        int disposeContextCount = 0;
        int disposeContextOrder = -1;
        int initializeThreadCount = 0;
        int initializeThreadOrder = -1;
        int disposeThreadCount = 0;
        int disposeThreadOrder = -1;
        final Map<OptionKey<Boolean>, Boolean> optionValues;
        final List<String> arguments;
        String languageHome;
        boolean preInitialized;
        final BaseLanguage language;
        private final Map<Class<?>, Object> lookupItems = new HashMap<>();

        CountingContext(final String id, final TruffleLanguage.Env env, final BaseLanguage language) {
            this.id = id;
            this.env = env;
            this.language = language;
            this.optionValues = new HashMap<>();
            this.arguments = new ArrayList<>();
        }

        String getLanguageId() {
            return id;
        }

        void environment(TruffleLanguage.Env newEnv) {
            this.env = newEnv;
        }

        TruffleLanguage.Env environment() {
            return env;
        }

        <T> void register(Class<T> type, T item) {
            Objects.requireNonNull(type, "Type must be non null.");
            Objects.requireNonNull(item, "Item must be non null.");
            if (lookupItems.putIfAbsent(type, item) != null) {
                throw new IllegalArgumentException("Registration for " + type + " already exists");
            }
        }

        <T> T lookup(Class<T> type) {
            return type.cast(lookupItems.get(type));
        }
    }

    enum ActionKind {
        ON_INITIALIZE_CONTEXT,
        ON_PATCH_CONTEXT,
        ON_EXECUTE,
        ON_FINALIZE_CONTEXT
    }

    abstract static class BaseLanguage extends TruffleLanguage<CountingContext> {

        static Map<Pair<Class<? extends BaseLanguage>, ActionKind>, Function<TruffleLanguage.Env, Object>> actions = new HashMap<>();

        static void registerAction(Class<? extends BaseLanguage> languageClass, ActionKind kind, Consumer<TruffleLanguage.Env> action) {
            registerFunction(languageClass, kind, (e) -> {
                action.accept(e);
                return null;
            });
        }

        static void registerFunction(Class<? extends BaseLanguage> languageClass, ActionKind kind, Function<TruffleLanguage.Env, Object> action) {
            Pair<Class<? extends BaseLanguage>, ActionKind> key = Pair.create(languageClass, kind);
            actions.put(key, action);
        }

        @Override
        protected CountingContext createContext(TruffleLanguage.Env env) {
            final String languageId = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return null;
                }
            }.getLanguageInfo().getId();
            final CountingContext ctx = new CountingContext(languageId, env, this);
            ctx.createContextCount++;
            ctx.createContextOrder = nextId();
            ctx.languageHome = getLanguageHome();
            Collections.addAll(ctx.arguments, env.getApplicationArguments());
            ctx.preInitialized = env.isPreInitialization();
            emittedContexts.add(ctx);
            return ctx;
        }

        @Override
        protected void initializeContext(CountingContext context) throws Exception {
            context.initializeContextCount++;
            context.initializeContextOrder = nextId();
            findAction(ActionKind.ON_INITIALIZE_CONTEXT).map((c) -> c.apply(context.environment()));
        }

        @Override
        protected boolean patchContext(CountingContext context, TruffleLanguage.Env newEnv) {
            assertNotNull(getContextReference0().get(null));
            assertTrue(context.preInitialized);
            assertFalse(context.env.isPreInitialization());
            context.patchContextCount++;
            context.patchContextOrder = nextId();
            context.languageHome = getLanguageHome();
            boolean patchable = patchableLanguages.contains(context.getLanguageId());
            if (patchable) {
                context.environment(newEnv);
                context.arguments.clear();
                Collections.addAll(context.arguments, newEnv.getApplicationArguments());
            }
            Optional<Object> result = findAction(ActionKind.ON_PATCH_CONTEXT).map((c) -> c.apply(context.environment()));
            if (result.isPresent() && result.get() instanceof Boolean) {
                return (boolean) result.get();
            }
            return patchable;
        }

        @Override
        protected void finalizeContext(CountingContext context) {
            findAction(ActionKind.ON_FINALIZE_CONTEXT).map((c) -> c.apply(context.environment()));
        }

        @Override
        protected void disposeContext(CountingContext context) {
            context.disposeContextCount++;
            context.disposeContextOrder = nextId();
        }

        @Override
        protected void initializeThread(CountingContext context, Thread thread) {
            context.initializeThreadCount++;
            context.initializeThreadOrder = nextId();
        }

        @Override
        protected void disposeThread(CountingContext context, Thread thread) {
            context.disposeThreadCount++;
            context.disposeThreadOrder = nextId();
        }

        @Override
        protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            final CharSequence result = request.getSource().getCharacters();
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    executeImpl(getContextReference0().get(this));
                    return result;
                }
            }.getCallTarget();
        }

        protected abstract ContextReference<CountingContext> getContextReference0();

        protected void useLanguage(CountingContext context, String id) {
            com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder(id, "", "").internal(true).build();
            context.environment().parseInternal(source);
        }

        @CompilerDirectives.TruffleBoundary
        private void executeImpl(CountingContext context) {
            findAction(ActionKind.ON_EXECUTE).map((c) -> c.apply(context.environment()));
            assertEquals(0, context.disposeContextCount);
        }

        Optional<Function<Env, Object>> findAction(ActionKind kind) {
            Class<?> clz = getClass();
            while (clz != null) {
                Pair<Class<?>, ActionKind> key = Pair.create(clz, kind);
                Function<Env, Object> action = actions.get(key);
                if (action != null) {
                    return Optional.of(action);
                }
                if (clz == BaseLanguage.class) {
                    break;
                }
                clz = clz.getSuperclass();
            }
            return Optional.empty();
        }
    }

    public interface Service {
        enum Kind {
            IMAGE_BUILD_TIME,
            IMAGE_EXECUTION_TIME
        }

        Kind getKind();
    }

    @TruffleLanguage.Registration(id = FIRST, name = FIRST, version = "1.0", dependentLanguages = INTERNAL, //
                    contextPolicy = TruffleLanguage.ContextPolicy.SHARED, services = Service.class, //
                    internalResources = ContextPreInitializationResource.class)
    public static final class ContextPreInitializationTestFirstLanguage extends BaseLanguage {
        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Option 1") //
        public static final OptionKey<Boolean> Option1 = new OptionKey<>(false);
        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Option 2") //
        public static final OptionKey<Boolean> Option2 = new OptionKey<>(false);
        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Service Kind") //
        public static final OptionKey<String> ServiceKind = new OptionKey<>(Service.Kind.IMAGE_BUILD_TIME.name());

        private static boolean callDependentLanguage;

        private final ContextThreadLocal<AtomicInteger> threadState;
        private final ContextLocal<AtomicInteger> state;

        public ContextPreInitializationTestFirstLanguage() {
            threadState = locals.createContextThreadLocal((context, thread) -> new AtomicInteger());
            state = locals.createContextLocal((context) -> new AtomicInteger());
        }

        @Override
        protected ContextReference<CountingContext> getContextReference0() {
            return CONTEXT_REF;
        }

        private static final LanguageReference<ContextPreInitializationTestFirstLanguage> REFERENCE = LanguageReference.create(ContextPreInitializationTestFirstLanguage.class);
        private static final ContextReference<CountingContext> CONTEXT_REF = ContextReference.create(ContextPreInitializationTestFirstLanguage.class);

        static ContextThreadLocal<AtomicInteger> getThreadStateReference() {
            return REFERENCE.get(null).threadState;
        }

        static ContextLocal<AtomicInteger> getStateReference() {
            return REFERENCE.get(null).state;
        }

        @Override
        protected CountingContext createContext(Env env) {
            final CountingContext ctx = super.createContext(env);
            ctx.optionValues.put(Option1, env.getOptions().get(Option1));
            ctx.optionValues.put(Option2, env.getOptions().get(Option2));
            Service service = new ServiceImpl(Service.Kind.valueOf(env.getOptions().get(ServiceKind)));
            env.registerService(service);
            ctx.register(Service.class, service);
            return ctx;
        }

        @Override
        protected void initializeContext(CountingContext context) throws Exception {
            super.initializeContext(context);
            if (callDependentLanguage) {
                useLanguage(context, INTERNAL);
            }
        }

        @Override
        protected boolean patchContext(CountingContext context, Env newEnv) {
            context.optionValues.put(Option1, newEnv.getOptions().get(Option1));
            context.optionValues.put(Option2, newEnv.getOptions().get(Option2));
            ((ServiceImpl) context.lookup(Service.class)).setKind(Service.Kind.valueOf(newEnv.getOptions().get(ServiceKind)));
            final boolean result = super.patchContext(context, newEnv);
            return result;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ContextPreInitializationTestFirstLanguageOptionDescriptors();
        }

        private static final class ServiceImpl implements Service {

            private Kind kind;

            ServiceImpl(Kind kind) {
                this.kind = kind;
            }

            void setKind(Kind newKind) {
                this.kind = newKind;
            }

            @Override
            public Kind getKind() {
                return kind;
            }
        }
    }

    @TruffleLanguage.Registration(id = CONSTRAINED, name = CONSTRAINED, version = "1.0", sandbox = SandboxPolicy.CONSTRAINED)
    public static final class ContextPreInitializationTestConstrainedLanguage extends BaseLanguage {

        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Trusted option") //
        public static final OptionKey<Boolean> Trusted = new OptionKey<>(false);
        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Constrained option", sandbox = SandboxPolicy.CONSTRAINED) //
        public static final OptionKey<Boolean> Constrained = new OptionKey<>(false);

        @Override
        protected boolean patchContext(CountingContext context, Env newEnv) {
            return super.patchContext(context, newEnv);
        }

        @Override
        protected ContextReference<CountingContext> getContextReference0() {
            return CONTEXT_REF;
        }

        private static final ContextReference<CountingContext> CONTEXT_REF = ContextReference.create(ContextPreInitializationTestConstrainedLanguage.class);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ContextPreInitializationTestConstrainedLanguageOptionDescriptors();
        }
    }

    @TruffleLanguage.Registration(id = SECOND, name = SECOND, version = "1.0", dependentLanguages = FIRST)
    public static final class ContextPreInitializationTestSecondLanguage extends BaseLanguage {
        private static boolean callDependentLanguageInCreate;
        private static boolean callDependentLanguageInPatch;
        private static boolean lookupService;

        @Override
        protected void initializeContext(CountingContext context) throws Exception {
            super.initializeContext(context);
            if (lookupService) {
                expectService(context.environment(),
                                context.environment().isPreInitialization() ? Service.Kind.IMAGE_BUILD_TIME : Service.Kind.IMAGE_EXECUTION_TIME);
            }
            if (callDependentLanguageInCreate) {
                useLanguage(context, FIRST);
            }
        }

        public static Env getCurrentContext() {
            return CONTEXT_REF.get(null).env;
        }

        @Override
        protected ContextReference<CountingContext> getContextReference0() {
            return CONTEXT_REF;
        }

        private static final ContextReference<CountingContext> CONTEXT_REF = ContextReference.create(ContextPreInitializationTestSecondLanguage.class);

        @Override
        protected boolean patchContext(CountingContext context, Env newEnv) {
            if (lookupService) {
                expectService(newEnv, Service.Kind.IMAGE_EXECUTION_TIME);
            }
            boolean res = super.patchContext(context, newEnv);
            if (res && callDependentLanguageInPatch) {
                useLanguage(context, FIRST);
            }
            return res;
        }

        private static void expectService(Env env, Service.Kind expectedServiceKind) {
            LanguageInfo firstLanguage = env.getPublicLanguages().get(FIRST);
            Service service = env.lookup(firstLanguage, Service.class);
            assertNotNull("Service not found", service);
            assertEquals(expectedServiceKind, service.getKind());
        }
    }

    @TruffleLanguage.Registration(id = INTERNAL, name = INTERNAL, version = "1.0", internal = true)
    public static final class ContextPreInitializationTestInternalLanguage extends BaseLanguage {

        @Override
        protected ContextReference<CountingContext> getContextReference0() {
            return CONTEXT_REF;
        }

        private static final ContextReference<CountingContext> CONTEXT_REF = ContextReference.create(ContextPreInitializationTestInternalLanguage.class);
    }

    private static final class TestHandler extends Handler {

        private final Set<String> importantLoggers;
        final List<LogRecord> logs = new ArrayList<>();
        private volatile boolean closed;

        TestHandler(String... importantLoggers) {
            this.importantLoggers = Set.of(importantLoggers);
        }

        @Override
        public void publish(LogRecord record) {
            if (closed) {
                throw new IllegalStateException("Closed");
            }
            if (importantLoggers.isEmpty() || importantLoggers.contains(record.getLoggerName())) {
                logs.add(record);
            }
        }

        @Override
        public void flush() {
            if (closed) {
                throw new IllegalStateException("Closed");
            }
        }

        @Override
        public void close() throws SecurityException {
            closed = true;
        }
    }

    @TruffleLanguage.Registration(id = SHARED, name = SHARED, version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    public static final class ContextPreInitializationTestSharedLanguage extends BaseLanguage {
        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Option 1") //
        public static final OptionKey<Boolean> Option1 = new OptionKey<>(false);
        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Option 2") //
        public static final OptionKey<Boolean> Option2 = new OptionKey<>(false);

        @Override
        protected CountingContext createContext(Env env) {
            final CountingContext ctx = super.createContext(env);
            ctx.optionValues.put(Option1, env.getOptions().get(Option1));
            ctx.optionValues.put(Option2, env.getOptions().get(Option2));
            return ctx;
        }

        @Override
        protected void initializeContext(CountingContext context) throws Exception {
            super.initializeContext(context);
        }

        @Override
        protected boolean patchContext(CountingContext context, Env newEnv) {
            context.optionValues.put(Option1, newEnv.getOptions().get(Option1));
            context.optionValues.put(Option2, newEnv.getOptions().get(Option2));
            final boolean result = super.patchContext(context, newEnv);
            return result;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ContextPreInitializationTestSharedLanguageOptionDescriptors();
        }

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            for (OptionDescriptor descriptor : firstOptions.getDescriptors()) {
                if (!Objects.equals(firstOptions.get(descriptor.getKey()), newOptions.get(descriptor.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected ContextReference<CountingContext> getContextReference0() {
            return CONTEXT_REF;
        }

        private static final ContextReference<CountingContext> CONTEXT_REF = ContextReference.create(ContextPreInitializationTestSharedLanguage.class);
    }

    public abstract static class BaseInstrument extends TruffleInstrument implements ContextsListener {

        private Env environment;
        private EventBinding<BaseInstrument> contextsListenerBinding;
        private EventBinding<?> verifyContextLocalBinding;

        final ContextLocal<TruffleContext> contextLocal = locals.createContextLocal((c) -> c);
        final ContextThreadLocal<TruffleContext> contextThreadLocal = locals.createContextThreadLocal((c, t) -> c);
        private final Set<Pair<TruffleContext, Thread>> initializedThreads = Collections.newSetFromMap(new ConcurrentHashMap<>());

        @Override
        protected void onCreate(Env env) {
            if (getActions() != null) {
                environment = env;
                contextsListenerBinding = env.getInstrumenter().attachContextsListener(this, true);
                verifyContextLocalBinding = env.getInstrumenter().attachContextsListener(new ContextsListener() {

                    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));
                        assertSame(context, contextThreadLocal.get(context));
                    }

                    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));
                        assertSame(context, contextThreadLocal.get(context));
                    }

                    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));
                        assertSame(context, contextThreadLocal.get(context));
                    }

                    public void onContextCreated(TruffleContext context) {
                        assertSame(context, contextLocal.get(context));
                    }

                    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));
                        assertSame(context, contextThreadLocal.get(context));
                    }

                    public void onContextClosed(TruffleContext context) {
                        assertSame(context, contextLocal.get(context));
                        assertSame(context, contextThreadLocal.get(context));
                    }
                }, false);
                performAction(null, null);
            }
            env.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
                @Override
                public void onEnterThread(TruffleContext context) {
                    // tests that locals are initialized
                    contextLocal.get(context);
                    contextThreadLocal.get(context);
                }

                @Override
                public void onLeaveThread(TruffleContext context) {
                    // tests that locals are initialized
                    contextLocal.get(context);
                    contextThreadLocal.get(context);
                }
            });
            env.getInstrumenter().attachThreadsListener(new ThreadsListener() {
                @Override
                public void onThreadInitialized(TruffleContext context, Thread thread) {
                    assertTrue(initializedThreads.add(Pair.create(context, thread)));
                }

                @Override
                public void onThreadDisposed(TruffleContext context, Thread thread) {
                    // tests that onThreadInitialized was called
                    assertTrue(initializedThreads.remove(Pair.create(context, thread)));
                }

            }, true);
        }

        @Override
        protected void onDispose(Env env) {
            if (contextsListenerBinding != null) {
                verifyContextLocalBinding.dispose();
                contextsListenerBinding.dispose();
                contextsListenerBinding = null;
                environment = null;
            }
        }

        @Override
        public void onContextCreated(TruffleContext context) {
            performAction(context, null);
        }

        @Override
        public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            performAction(context, language);
        }

        @Override
        public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
            performAction(context, language);
        }

        @Override
        public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
            performAction(context, language);
        }

        @Override
        public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
            performAction(context, language);
        }

        @Override
        public void onContextClosed(TruffleContext context) {
            performAction(context, null);
        }

        private void performAction(TruffleContext context, LanguageInfo language) {
            StackTraceElement element = Thread.currentThread().getStackTrace()[2];
            Consumer<Event> action = getActions().get(element.getMethodName());
            if (action != null) {
                action.accept(new Event(environment, context, language));
            }
        }

        protected abstract Map<String, Consumer<Event>> getActions();

        static final class Event {
            final TruffleInstrument.Env env;
            final TruffleContext ctx;
            final LanguageInfo language;

            Event(TruffleInstrument.Env env, TruffleContext ctx, LanguageInfo info) {
                this.env = env;
                this.ctx = ctx;
                this.language = info;
            }
        }
    }

    @TruffleInstrument.Registration(id = ContextPreInitializationFirstInstrument.ID, name = ContextPreInitializationFirstInstrument.ID, //
                    internalResources = ContextPreInitializationResource.class)
    public static final class ContextPreInitializationFirstInstrument extends BaseInstrument {

        static final String ID = "ContextPreInitializationFirstInstrument";

        static volatile Map<String, Consumer<Event>> actions;

        @Option(name = "", category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Activates instrument", sandbox = SandboxPolicy.CONSTRAINED) //
        static final OptionKey<Boolean> Enabled = new OptionKey<>(false);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ContextPreInitializationFirstInstrumentOptionDescriptors();
        }

        @Override
        protected Map<String, Consumer<Event>> getActions() {
            return actions;
        }

    }

    @TruffleInstrument.Registration(id = ContextPreInitializationSecondInstrument.ID, name = ContextPreInitializationSecondInstrument.ID, services = Service.class)
    public static final class ContextPreInitializationSecondInstrument extends BaseInstrument {

        static final String ID = "ContextPreInitializationSecondInstrument";

        @Override
        protected Map<String, Consumer<Event>> getActions() {
            return Collections.emptyMap();
        }

        @Override
        protected void onCreate(Env env) {
            super.onCreate(env);
            env.registerService(new Service() {
                @Override
                public Service.Kind getKind() {
                    return Service.Kind.IMAGE_EXECUTION_TIME;
                }
            });
        }

    }

    @TruffleInstrument.Registration(id = ContextPreInitializationConstrainedInstrument.ID, name = ContextPreInitializationConstrainedInstrument.ID, sandbox = SandboxPolicy.CONSTRAINED)
    public static final class ContextPreInitializationConstrainedInstrument extends BaseInstrument {

        static final String ID = "ContextPreInitializationConstrainedInstrument";

        @Option(name = "", category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Activates instrument", sandbox = SandboxPolicy.CONSTRAINED) //
        static final OptionKey<Boolean> Enabled = new OptionKey<>(false);

        @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Trusted only option") //
        static final OptionKey<Boolean> Trusted = new OptionKey<>(false);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ContextPreInitializationConstrainedInstrumentOptionDescriptors();
        }

        @Override
        protected Map<String, Consumer<Event>> getActions() {
            return Collections.emptyMap();
        }
    }

    private static final class CheckCloseByteArrayOutputStream extends ByteArrayOutputStream {

        private boolean closed;

        @Override
        public synchronized void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public synchronized void write(int b) {
            checkClosed();
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            checkClosed();
            super.write(b, off, len);
        }

        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Closed");
            }
        }
    }

    @InternalResource.Id(ContextPreInitializationResource.ID)
    static final class ContextPreInitializationResource implements InternalResource {

        static final String ID = "context-pre-init-test-resource";

        static final String FILE_NAME = "library_source";
        static final String FILE_CONTENT = "library_content";

        static int unpackCount;

        static boolean preInitialization;

        @Override
        public void unpackFiles(Env env, Path targetDirectory) throws IOException {
            unpackCount++;
            Files.writeString(targetDirectory.resolve(FILE_NAME), FILE_CONTENT);
            assertEquals(preInitialization, env.inContextPreinitialization());
        }

        @Override
        public String versionHash(Env env) {
            return "1";
        }
    }
}
