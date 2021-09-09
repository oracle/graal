/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.FileSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;

public class ContextPreInitializationTest {

    static final String FIRST = "ContextPreInitializationFirst";
    static final String SECOND = "ContextPreInitializationSecond";
    static final String INTERNAL = "ContextPreInitializationInternal";
    static final String SHARED = "ContextPreInitializationShared";
    private static final AtomicInteger NEXT_ORDER_INDEX = new AtomicInteger();
    private static final String SYS_OPTION1_KEY = "polyglot." + FIRST + ".Option1";
    private static final String SYS_OPTION2_KEY = "polyglot." + FIRST + ".Option2";
    private static final List<CountingContext> emittedContexts = new ArrayList<>();
    private static final Set<String> patchableLanguages = new HashSet<>();

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
            try (TruffleContext truffleContext = langCtx.environment().newContextBuilder().build()) {
                contexts = new ArrayList<>(emittedContexts);
                assertEquals(2, contexts.size());
                langCtx = contexts.get(1);
                assertTrue(langCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option1));
                assertFalse(langCtx.optionValues.get(ContextPreInitializationTestSharedLanguage.Option2));
                assertSame("Patched pre-initialized language should be shared with the second context since the options are compatible.", firstLang, langCtx.language);
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
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
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
        BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
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
        final ByteArrayOutputStream preInitErr = new ByteArrayOutputStream();
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
        final ByteArrayOutputStream preInitErr = new ByteArrayOutputStream();
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
            m.setAccessible(true);
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
        final ByteArrayOutputStream preInitErr = new ByteArrayOutputStream();
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
    public void testFileSystemSwitch() throws Exception {
        setPatchable(FIRST);
        Path tmpDir = Files.createTempDirectory("testFileSystemSwitch");
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
            try (Context ctx = Context.newBuilder().allowIO(true).build()) {
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
            try (Context ctx = Context.newBuilder().allowIO(true).currentWorkingDirectory(newCwd).build()) {
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

    @Test
    @SuppressWarnings("try")
    public void testAuxImageStore() throws Exception {
        setPatchable(FIRST);
        Path testFolder = Files.createTempDirectory("testSources").toRealPath();
        try {
            Path home = Files.createDirectories(testFolder.resolve("build").resolve(FIRST));
            Path resource = Files.write(home.resolve("testImageStore.test"), Collections.singleton("test"));
            System.setProperty(String.format("org.graalvm.language.%s.home", FIRST), home.toString());
            AtomicReference<com.oracle.truffle.api.source.Source> buildTimeSourceRef = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                buildTimeSourceRef.set(createSource(env, resource, true));
            });
            doContextPreinitialize(FIRST);
            List<CountingContext> contexts = new ArrayList<>(emittedContexts);
            assertEquals(1, contexts.size());
            CountingContext firstLangCtx = findContext(FIRST, contexts);
            assertEquals(1, firstLangCtx.initializeContextCount);
            assertNotNull(buildTimeSourceRef.get());

            Engine engine = Engine.create();
            AbstractPolyglotImpl impl = findImpl();
            Object engineImpl = impl.getAPIAccess().getReceiver(engine);
            AtomicReference<com.oracle.truffle.api.source.Source> storeTimeSourceRef = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_INITIALIZE_CONTEXT, (env) -> {
                storeTimeSourceRef.set(createSource(env, resource, true));
            });
            setPreInitializeOption(FIRST);
            try {
                TestAPIAccessor.engineAccess().preinitializeContext(engineImpl);
            } finally {
                clearPreInitializeOption();
            }
            contexts = new ArrayList<>(emittedContexts);
            assertEquals(2, contexts.size());
            AtomicReference<com.oracle.truffle.api.source.Source> runtimeSourceRef = new AtomicReference<>();
            BaseLanguage.registerAction(ContextPreInitializationTestFirstLanguage.class, ActionKind.ON_PATCH_CONTEXT, (env) -> {
                runtimeSourceRef.set(createSource(env, resource, true));
            });
            try (Context ctx = Context.create()) {
                assertEquals(1, firstLangCtx.patchContextCount);
                assertSame(buildTimeSourceRef.get(), storeTimeSourceRef.get());
                assertSame(buildTimeSourceRef.get(), runtimeSourceRef.get());
            }
        } finally {
            delete(testFolder);
        }
    }

    AbstractPolyglotImpl findImpl() throws ReflectiveOperationException {
        Method getImplMethod = Engine.class.getDeclaredMethod("getImpl");
        getImplMethod.setAccessible(true);
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
        IsSameFileResult res = testIsSameFileImpl(true, null);
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
        IsSameFileResult res = testIsSameFileImpl(false, null);
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
        IsSameFileResult res = testIsSameFileImpl(true, FileSystem.newDefaultFileSystem());
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

    private static IsSameFileResult testIsSameFileImpl(boolean allowIO, FileSystem fs) throws ReflectiveOperationException {
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
        Context.Builder builder = Context.newBuilder().allowIO(allowIO);
        if (fs != null) {
            builder.fileSystem(fs);
        }
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
        reset.setAccessible(true);
        reset.invoke(null);
    }

    private static void doContextPreinitialize(String... languages) throws ReflectiveOperationException {
        setPreInitializeOption(languages);
        try {
            final Class<?> holderClz = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, ContextPreInitializationTest.class.getClassLoader());
            final Method preInitMethod = holderClz.getDeclaredMethod("preInitializeEngine");
            preInitMethod.setAccessible(true);
            preInitMethod.invoke(null);
        } finally {
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
        m.setAccessible(true);
        return (Set<Path>) m.invoke(null);
    }

    private static Collection<? extends CountingContext> findContexts(
                    final String languageId,
                    Collection<? extends CountingContext> contexts) {
        final Set<CountingContext> result = new HashSet<>();
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

        static Map<Pair<Class<? extends BaseLanguage>, ActionKind>, Consumer<TruffleLanguage.Env>> actions = new HashMap<>();

        static void registerAction(Class<? extends BaseLanguage> languageClass, ActionKind kind, Consumer<TruffleLanguage.Env> action) {
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
            findAction(ActionKind.ON_INITIALIZE_CONTEXT).ifPresent((c) -> c.accept(context.environment()));
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
            findAction(ActionKind.ON_PATCH_CONTEXT).ifPresent((c) -> c.accept(context.environment()));
            return patchable;
        }

        @Override
        protected void finalizeContext(CountingContext context) {
            findAction(ActionKind.ON_FINALIZE_CONTEXT).ifPresent((c) -> c.accept(context.environment()));
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
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    executeImpl(getContextReference0().get(this));
                    return result;
                }
            });
        }

        protected abstract ContextReference<CountingContext> getContextReference0();

        protected void useLanguage(CountingContext context, String id) {
            com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder(id, "", "").internal(true).build();
            context.environment().parseInternal(source);
        }

        @CompilerDirectives.TruffleBoundary
        private void executeImpl(CountingContext context) {
            findAction(ActionKind.ON_EXECUTE).ifPresent((c) -> c.accept(context.environment()));
            assertEquals(0, context.disposeContextCount);
        }

        Optional<Consumer<Env>> findAction(ActionKind kind) {
            Class<?> clz = getClass();
            while (clz != null) {
                Pair<Class<?>, ActionKind> key = Pair.create(clz, kind);
                Consumer<Env> action = actions.get(key);
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

    @TruffleLanguage.Registration(id = FIRST, name = FIRST, version = "1.0", dependentLanguages = INTERNAL, contextPolicy = TruffleLanguage.ContextPolicy.SHARED, services = Service.class)
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
            threadState = createContextThreadLocal((context, thread) -> new AtomicInteger());
            state = createContextLocal((context) -> new AtomicInteger());
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

        TestHandler(String... importantLoggers) {
            this.importantLoggers = new HashSet<>();
            Collections.addAll(this.importantLoggers, importantLoggers);
        }

        @Override
        public void publish(LogRecord record) {
            if (importantLoggers.isEmpty() || importantLoggers.contains(record.getLoggerName())) {
                logs.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
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
            return firstOptions.equals(newOptions);
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

        final ContextLocal<TruffleContext> contextLocal = createContextLocal((c) -> c);

        @Override
        protected void onCreate(Env env) {
            if (getActions() != null) {
                environment = env;
                contextsListenerBinding = env.getInstrumenter().attachContextsListener(this, true);
                verifyContextLocalBinding = env.getInstrumenter().attachContextsListener(new ContextsListener() {

                    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));
                    }

                    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));

                    }

                    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));

                    }

                    public void onContextCreated(TruffleContext context) {
                        assertSame(context, contextLocal.get(context));
                    }

                    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
                        assertSame(context, contextLocal.get(context));
                    }

                    public void onContextClosed(TruffleContext context) {
                        assertSame(context, contextLocal.get(context));

                    }
                }, false);
                performAction(null, null);
            }
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

    @TruffleInstrument.Registration(id = ContextPreInitializationFirstInstrument.ID, name = ContextPreInitializationFirstInstrument.ID)
    public static final class ContextPreInitializationFirstInstrument extends BaseInstrument {

        static final String ID = "ContextPreInitializationFirstInstrument";

        static volatile Map<String, Consumer<Event>> actions;

        @Option(name = "", category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Activates instrument") //
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
}
