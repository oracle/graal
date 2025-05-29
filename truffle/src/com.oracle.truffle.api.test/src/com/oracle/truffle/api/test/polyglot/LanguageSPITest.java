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
import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.execute;
import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static com.oracle.truffle.tck.tests.ValueAssert.assertValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

import org.graalvm.home.Version;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ExceptionType;
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
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.api.test.polyglot.LanguageSPITest.ServiceTestLanguage.LanguageSPITestLanguageService1;
import com.oracle.truffle.api.test.polyglot.LanguageSPITest.ServiceTestLanguage.LanguageSPITestLanguageService2;
import com.oracle.truffle.api.test.polyglot.LanguageSPITest.ServiceTestLanguage.LanguageSPITestLanguageService3;
import com.oracle.truffle.api.test.polyglot.LanguageSPITestLanguage.LanguageContext;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
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

    Context assertContext;
    int evalNo;

    private void remoteAssert(Engine engine, Class<? extends AbstractExecutableTestLanguage> assertLanguage, boolean hostAccessNeeded, int assertsBlockNo) {
        if (assertContext == null || assertContext.getEngine() != engine) {
            if (assertContext != null) {
                assertContext.close();
            }
            Context.Builder builder = Context.newBuilder().engine(engine);
            if (hostAccessNeeded) {
                builder.allowHostAccess(HostAccess.ALL);
            }
            assertContext = builder.build();
        }
        evalTestLanguage(assertContext, assertLanguage, "assert no. " + (++evalNo), assertsBlockNo);
    }

    @After
    public void closeAssertContext() {
        if (assertContext != null) {
            assertContext.close();
        }
    }

    @TruffleLanguage.Registration
    static class ContextCloseTestLanguage extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            int assertsBlockNo = (Integer) contextArguments[0];
            switch (assertsBlockNo) {
                case 1:
                    assertNotNull(langContext);
                    assertEquals(0, langContext.disposeCalled);
                    break;
                case 2:
                    assertEquals(1, langContext.disposeCalled);
                    break;
            }
            return null;
        }
    }

    @Test
    public void testContextClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).allowPolyglotAccess(PolyglotAccess.ALL).build();
        assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        remoteAssert(engine, ContextCloseTestLanguage.class, false, 1);
        context.close();
        remoteAssert(engine, ContextCloseTestLanguage.class, false, 2);
        engine.close();
    }

    @Test
    public void testPolyglotClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = Context.newBuilder().engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);

        remoteAssert(engine, ContextCloseTestLanguage.class, false, 1);
        context.close();
        remoteAssert(engine, ContextCloseTestLanguage.class, false, 2);
        engine.close();
    }

    @Test
    public void testImplicitClose() {
        Engine engine = Engine.create();
        langContext = null;
        Context c = Context.newBuilder().engine(engine).build();
        c.initialize(LanguageSPITestLanguage.ID);
        LanguageContext context1 = langContext;

        Context c2 = Context.newBuilder().engine(engine).build();
        c2.initialize(LanguageSPITestLanguage.ID);
        LanguageContext context2 = langContext;

        c.close();
        engine.close();
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            assertEquals(1, context1.disposeCalled);
            assertEquals(1, context2.disposeCalled);
        } else {
            // We cannot obtain any information from a closed isolate.
            AbstractPolyglotTest.assertFails(c2::enter, IllegalStateException.class, (e) -> {
                assertEquals("The Context is already closed.", e.getMessage());
            });
        }
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
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            assertEquals(1, langContext.disposeCalled);
        } else {
            // We cannot obtain any information from a closed isolate.
            AbstractPolyglotTest.assertFails(context::enter, IllegalStateException.class, (e) -> {
                assertEquals("The Context is already closed.", e.getMessage());
            });
        }
    }

    @Test
    public void testCreateFromOtherThreadAndCloseFromMain() throws InterruptedException {
        Engine engine = Engine.create();
        langContext = null;
        Context[] context = new Context[1];
        Thread t = new Thread(new Runnable() {
            public void run() {
                context[0] = Context.newBuilder().engine(engine).build();
                context[0].initialize(LanguageSPITestLanguage.ID);
            }
        });
        t.start();
        t.join(10000);
        engine.close();
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            assertEquals(1, langContext.disposeCalled);
        } else {
            // We cannot obtain any information from a closed isolate.
            AbstractPolyglotTest.assertFails(context[0]::enter, IllegalStateException.class, (e) -> {
                assertEquals("The Context is already closed.", e.getMessage());
            });
        }
    }

    @TruffleLanguage.Registration
    static class ExecuteFirstContextArgumentTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(ExecuteFirstContextArgumentTestLanguage.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return interop.execute(contextArguments[0]);
        }
    }

    @Test
    public void testContextCloseInsideFromSameThread() {
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID, ExecuteFirstContextArgumentTestLanguage.ID).allowHostAccess(HostAccess.ALL).build();
        context.initialize(LanguageSPITestLanguage.ID);
        evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "", (Runnable) context::close);
        context.close();
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            assertEquals(1, langContext.disposeCalled);
        } else {
            // We cannot obtain any information from a closed isolate.
            AbstractPolyglotTest.assertFails(context::enter, IllegalStateException.class, (e) -> {
                assertEquals("The Context is already closed.", e.getMessage());
            });
        }
    }

    @Test
    public void testContextCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID, ExecuteFirstContextArgumentTestLanguage.ID).allowHostAccess(HostAccess.ALL).engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);
        try {
            evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "", (Runnable) () -> context.close(true));
            fail();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        remoteAssert(engine, ContextCloseTestLanguage.class, true, 2);
        engine.close();
    }

    @Test
    public void testContextCloseInsideFromSameThreadCancelExecutionNestedEval() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID, ExecuteFirstContextArgumentTestLanguage.ID).allowHostAccess(HostAccess.ALL).engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);
        try {
            evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "outer level", (Runnable) () -> {
                try {
                    evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "inner level", (Runnable) () -> context.close(true));
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            });
            fail();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        remoteAssert(engine, ContextCloseTestLanguage.class, true, 2);
        engine.close();
    }

    @Test
    public void testEngineCloseInsideFromSameThread() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID, ExecuteFirstContextArgumentTestLanguage.ID).allowHostAccess(HostAccess.ALL).engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);
        evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "", (Runnable) engine::close);
        engine.close();
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            assertEquals(1, langContext.disposeCalled);
        } else {
            // We cannot obtain any information from a closed isolate.
            AbstractPolyglotTest.assertFails(context::enter, IllegalStateException.class, (e) -> {
                assertEquals("The Context is already closed.", e.getMessage());
            });
        }
    }

    @Test
    public void testEngineCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID, ExecuteFirstContextArgumentTestLanguage.ID).allowHostAccess(HostAccess.ALL).engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);
        try {
            evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "", (Runnable) () -> engine.close(true));
            fail();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        engine.close();
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            assertEquals(1, langContext.disposeCalled);
        } else {
            // We cannot obtain any information from a closed isolate.
            AbstractPolyglotTest.assertFails(context::enter, PolyglotException.class, (e) -> {
                assertFalse(e.isInternalError());
                assertTrue(e.isCancelled());
            });
        }
    }

    @Test
    public void testEngineCloseInsideFromSameThreadCancelExecutionNestedEval() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID, ExecuteFirstContextArgumentTestLanguage.ID).allowHostAccess(HostAccess.ALL).engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);
        try {
            evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "outer level", (Runnable) () -> {
                try {
                    evalTestLanguage(context, ExecuteFirstContextArgumentTestLanguage.class, "inner level", (Runnable) () -> engine.close(true));
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            });
            fail();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        engine.close();
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            assertEquals(1, langContext.disposeCalled);
        } else {
            // We cannot obtain any information from a closed isolate.
            AbstractPolyglotTest.assertFails(context::enter, PolyglotException.class, (e) -> {
                assertFalse(e.isInternalError());
                assertTrue(e.isCancelled());
            });
        }
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static class Interrupted extends AbstractTruffleException {

        @ExportMessage
        ExceptionType getExceptionType() {
            return ExceptionType.INTERRUPT;
        }
    }

    @SuppressWarnings({"serial"})
    @ExportLibrary(InteropLibrary.class)
    static final class ParseException extends AbstractTruffleException {
        private final Source source;
        private final int start;
        private final int length;

        ParseException(final Source source, final int start, final int length) {
            Objects.requireNonNull(source, "Source must be non null");
            this.source = source;
            this.start = start;
            this.length = length;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.PARSE_ERROR;
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return source != null;
        }

        @ExportMessage(name = "getSourceLocation")
        @TruffleBoundary
        SourceSection getSourceSection() throws UnsupportedMessageException {
            if (source == null) {
                throw UnsupportedMessageException.create();
            }
            return source.createSection(start, length);
        }
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static class RemoteLatch implements TruffleObject {
        CountDownLatch latch = new CountDownLatch(1);

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        final Object execute(Object[] arguments) {
            latch.countDown();
            return true;
        }
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static class RemoteCounter implements TruffleObject {
        AtomicInteger counter = new AtomicInteger();

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        final Object execute(Object[] arguments) {
            counter.incrementAndGet();
            return true;
        }
    }

    @TruffleLanguage.Registration
    static class CancelExecutionWhileSleepingTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CancelExecutionWhileSleepingTestLanguage.class);

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object beforeSleepLatch = contextArguments[0];
            Object interruptLatch = contextArguments[1];
            Object interruptCounter = contextArguments[2];
            try {
                interop.execute(beforeSleepLatch);
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interop.execute(interruptCounter);
                interop.execute(interruptLatch);
                throw new Interrupted();
            }
            return null;
        }
    }

    @Test
    public void testCancelExecutionWhileSleeping() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(1);
        try {
            Engine engine = Engine.create();
            Context context = Context.newBuilder(LanguageSPITestLanguage.ID, CancelExecutionWhileSleepingTestLanguage.ID).engine(engine).build();
            context.initialize(LanguageSPITestLanguage.ID);

            RemoteLatch beforeSleep = new RemoteLatch();
            RemoteLatch interrupt = new RemoteLatch();
            RemoteCounter gotInterrupt = new RemoteCounter();
            Future<Value> future = service.submit(() -> evalTestLanguage(context, CancelExecutionWhileSleepingTestLanguage.class, "", beforeSleep, interrupt, gotInterrupt));
            beforeSleep.latch.await(10000, TimeUnit.MILLISECONDS);
            context.close(true);

            interrupt.latch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(1, gotInterrupt.counter.get());

            try {
                future.get();
                fail();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof PolyglotException)) {
                    throw new AssertionError(cause);
                }
                /*
                 * If interrupt is caused by polyglot context cancel, then the polyglot exception
                 * should have isCancelled() == true even though an exception of type interrupt was
                 * thrown. Moreover isInterrupted() == false as the cancellation fully overrides the
                 * interrupt in this case.
                 */
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

    @TruffleLanguage.Registration
    static class LookupHostTestLangauge extends AbstractExecutableTestLanguage {
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return env.lookupHostSymbol("java.util.HashMap");
        }
    }

    @Test
    public void testLookupHost() {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build();
        Value value = evalTestLanguage(context, LookupHostTestLangauge.class, "");
        assertTrue(value.isHostObject());
        Object map = value.asHostObject();
        assertSame(map, HashMap.class);
        context.close();
    }

    @TruffleLanguage.Registration
    static class LookupHostArrayTestLanguage extends AbstractExecutableTestLanguage {
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return env.lookupHostSymbol("java.lang.String[]");
        }
    }

    @Test
    public void testLookupHostArray() {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build();
        Value value = evalTestLanguage(context, LookupHostArrayTestLanguage.class, "");
        assertTrue(value.isHostObject());
        Object map = value.asHostObject();
        assertSame(map, String[].class);
        context.close();
    }

    @Test
    public void testLookupHostDisabled() {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> false).build();
        try {
            evalTestLanguage(context, LookupHostTestLangauge.class, "");
            fail();
        } catch (PolyglotException e) {
            assertTrue(!e.isInternalError());
        }
        context.close();
    }

    @TruffleLanguage.Registration
    static class IsHostAccessAllowedTestLanguage extends AbstractExecutableTestLanguage {
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return env.isHostLookupAllowed();
        }
    }

    @Test
    public void testIsHostAccessAllowed() {
        Context context = Context.create();
        assertFalse(evalTestLanguage(context, IsHostAccessAllowedTestLanguage.class, "").asBoolean());
        context.close();

        context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build();
        assertTrue(evalTestLanguage(context, IsHostAccessAllowedTestLanguage.class, "").asBoolean());
        context.close();
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextTestLanguage extends TruffleLanguage<InnerContextTestLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(InnerContextTestLanguage.class);

        static class Context {
            private final Env env;
            private final Map<String, Object> config;

            private int disposeCalled;

            Context(Env env, Map<String, Object> config) {
                this.env = env;
                this.config = config;
            }
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env, env.getConfig());
        }

        @Override
        protected void disposeContext(Context context) {
            context.disposeCalled++;
        }

        private static final ContextReference<Context> CONTEXT_REF = ContextReference.create(InnerContextTestLanguage.class);

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return executeBoundary();
                }
            }.getCallTarget();
        }

        @TruffleBoundary
        protected Object executeBoundary() {
            Context outerLangContext = CONTEXT_REF.get(null);
            Object config = new Object();
            TruffleContext innerContext = outerLangContext.env.newInnerContextBuilder().initializeCreatorContext(true).config("config", config).build();
            Object p = innerContext.enter(null);
            Context innerLangContext = CONTEXT_REF.get(null);
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
                        innerContext.leave(null, "foo");
                    } catch (AssertionError e) {
                        leaveFailed = true;
                    }
                    if (!leaveFailed) {
                        fail("no assertion error for leaving with the wrong object");
                    }
                }
            } finally {
                innerContext.leave(null, p);
            }
            assertSame(outerLangContext, CONTEXT_REF.get(null));
            innerContext.close();

            try {
                innerContext.enter(null);
                fail("cannot be entered after closing");
            } catch (IllegalStateException e) {
            }

            innerContext.close();

            assertEquals(1, innerLangContext.disposeCalled);

            return NullObject.SINGLETON;
        }
    }

    @Test
    public void testInnerContext() {
        Context context = Context.create();
        context.eval(InnerContextTestLanguage.ID, "");

        // ensure we are not yet closed
        context.eval(InnerContextTestLanguage.ID, "");
        context.close();
    }

    @TruffleLanguage.Registration
    static class TruffleContextTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(TruffleContextTestLanguage.class);
        static boolean firstExecution = true;

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            if (!firstExecution) {
                return null;
            } else {
                // no recursive runs inside
                firstExecution = false;
            }
            Throwable[] error = new Throwable[1];
            Thread thread = new Thread(() -> {
                try {
                    Source source = Source.newBuilder(ID, "", "s").build();
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
                    Object prev = truffleContext.enter(null);
                    try {
                        // execute Truffle code
                        env.parsePublic(source).call();
                    } finally {
                        // detach the Thread
                        truffleContext.leave(null, prev);
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
                truffleContext.leave(null, null);
            } catch (AssertionError e) {
                leaveFailed = true;
            }
            if (!leaveFailed) {
                fail("no assertion error for leaving without enter");
            }

            return null;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testTruffleContext() {
        Context context = Context.create();
        evalTestLanguage(context, TruffleContextTestLanguage.class, "");
        context.close();
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextAccessPrivilegesLanguage extends AbstractExecutableTestLanguage {

        static final int CREATE_PROCESS = 0;
        static final int NATIVE_ACCESS = 1;
        static final int IO = 2;
        static final int HOST_CLASS_LOADING = 3;
        static final int HOST_LOOKUP = 4;
        static final int CREATE_THREAD = 5;
        static final int POLYGLOT_ACCESS = 6;
        static final int ENVIRONMENT_ACCESS = 7;

        static final int NUMBER_PRIVILEGES = 8;

        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextAccessPrivilegesLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean expectedPrivilege = (boolean) contextArguments[0];
            int privilege = (int) contextArguments[1];
            boolean inheritPrivileges = (boolean) contextArguments[2];
            Boolean allowPrivilege = (Boolean) contextArguments[3];

            var builder = env.newInnerContextBuilder().initializeCreatorContext(true).inheritAllAccess(inheritPrivileges);
            if (allowPrivilege != null) {
                setInnerPrivilege(builder, privilege, allowPrivilege);
            }

            Boolean actualOuterPrivilege = getPrivilege(env, privilege);

            TruffleContext c = builder.build();
            Object prev = c.enter(null);
            try {
                ExecutableContext innerContext = CONTEXT_REFERENCE.get(null);
                Boolean actualInnerPrivilege = getPrivilege(innerContext.env, privilege);

                if (actualInnerPrivilege != null) {
                    assertEquals("Privilege " + privilege, expectedPrivilege, actualInnerPrivilege);

                    // check invariants
                    if (inheritPrivileges) {
                        if (allowPrivilege == null) {
                            assertEquals(actualOuterPrivilege, actualInnerPrivilege);
                        } else {
                            assertEquals(allowPrivilege && actualOuterPrivilege, actualInnerPrivilege);
                        }
                    } else {
                        if (allowPrivilege == null || !allowPrivilege) {
                            assertEquals(false, actualInnerPrivilege);
                        }
                    }
                }

            } finally {
                c.leave(null, prev);
                c.close();
            }

            return null;
        }

        private static void setInnerPrivilege(TruffleContext.Builder builder, int privilege, boolean allowPrivilege) {
            switch (privilege) {
                case CREATE_PROCESS:
                    builder.allowCreateProcess(allowPrivilege);
                    break;
                case NATIVE_ACCESS:
                    builder.allowNativeAccess(allowPrivilege);
                    break;
                case IO:
                    builder.allowIO(allowPrivilege);
                    break;
                case HOST_CLASS_LOADING:
                    if (allowPrivilege) {
                        // needed for host class loading
                        builder.allowIO(true);
                        builder.allowHostClassLookup(true);
                    }
                    builder.allowHostClassLoading(allowPrivilege);
                    break;
                case HOST_LOOKUP:
                    builder.allowHostClassLookup(allowPrivilege);
                    break;
                case CREATE_THREAD:
                    builder.allowCreateThread(allowPrivilege);
                    break;
                case POLYGLOT_ACCESS:
                    builder.allowPolyglotAccess(allowPrivilege);
                    break;
                case ENVIRONMENT_ACCESS:
                    builder.environment("testInnerEnvKey", "true");
                    builder.allowInheritEnvironmentAccess(allowPrivilege);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }

        }

        private static Boolean getPrivilege(Env env, int privilege) {
            switch (privilege) {
                case CREATE_PROCESS:
                    return env.isCreateProcessAllowed();
                case NATIVE_ACCESS:
                    return env.isNativeAccessAllowed();
                case IO:
                    return env.isFileIOAllowed();
                case HOST_CLASS_LOADING:
                    TruffleFile file = env.getTruffleFileInternal("", null);
                    try {
                        env.addToHostClassPath(file);
                        return true;
                    } catch (AbstractTruffleException e) {
                        if ("Cannot add classpath entry  in native mode.".equals(e.getMessage())) {
                            return null;
                        } else {
                            return false;
                        }
                    }
                case HOST_LOOKUP:
                    return env.isHostLookupAllowed();
                case CREATE_THREAD:
                    return env.isCreateThreadAllowed();
                case POLYGLOT_ACCESS:
                    return env.isPolyglotBindingsAccessAllowed() || env.isPolyglotEvalAllowed(null);
                case ENVIRONMENT_ACCESS:
                    // environment access can only be observed with properties
                    String value = env.getEnvironment().get(OUTER_CONTEXT_TEST_KEY);
                    if (value != null) {
                        return value.equals("enabled");
                    }
                    return false;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }

        }

    }

    static final String OUTER_CONTEXT_TEST_KEY = "OuterContextTestKey";

    @Test
    public void testInnerContextAccessPrivileges() {
        // test all privileges
        for (int privilege = 0; privilege < InnerContextAccessPrivilegesLanguage.NUMBER_PRIVILEGES; privilege++) {

            // test no outer context privilege
            assertInnerContextPrivilege(false, privilege, false, false, null);
            assertInnerContextPrivilege(false, privilege, false, false, true);
            assertInnerContextPrivilege(false, privilege, false, false, false);

            // test outer context privilege but no inheritance
            assertInnerContextPrivilege(false, privilege, true, false, null);
            assertInnerContextPrivilege(true, privilege, true, false, true);
            assertInnerContextPrivilege(false, privilege, true, false, false);

            // test outer context privilege and inheritance
            assertInnerContextPrivilege(true, privilege, true, true, null);
            assertInnerContextPrivilege(true, privilege, true, true, true);
            assertInnerContextPrivilege(false, privilege, true, true, false);

            // test no outer context privilege and inheritance
            assertInnerContextPrivilege(false, privilege, false, true, null);
            assertInnerContextPrivilege(false, privilege, false, true, true);
            assertInnerContextPrivilege(false, privilege, false, true, false);
        }
    }

    private static void assertInnerContextPrivilege(boolean expectedInnerPrivilege, int privilege, boolean outerPrivilege, boolean innerInheritPrivileges, Boolean innerPrivilege) {
        try (Context context = setOuterPrivilege(Context.newBuilder(), privilege, outerPrivilege).build()) {
            execute(context, InnerContextAccessPrivilegesLanguage.class, expectedInnerPrivilege, privilege, innerInheritPrivileges, innerPrivilege);
        }
    }

    private static Context.Builder setOuterPrivilege(Context.Builder builder, int privilege, boolean allowPrivilege) {
        switch (privilege) {
            case InnerContextAccessPrivilegesLanguage.CREATE_PROCESS:
                builder.allowCreateProcess(allowPrivilege);
                break;
            case InnerContextAccessPrivilegesLanguage.NATIVE_ACCESS:
                builder.allowNativeAccess(allowPrivilege);
                break;
            case InnerContextAccessPrivilegesLanguage.IO:
                builder.allowIO(allowPrivilege ? IOAccess.ALL : IOAccess.NONE);
                break;
            case InnerContextAccessPrivilegesLanguage.HOST_CLASS_LOADING:
                // required for host class loading
                builder.allowIO(IOAccess.ALL);
                builder.allowHostClassLookup((s) -> true);
                builder.allowHostClassLoading(allowPrivilege);
                break;
            case InnerContextAccessPrivilegesLanguage.HOST_LOOKUP:
                if (allowPrivilege) {
                    builder.allowHostClassLookup((s) -> true);
                } else {
                    builder.allowHostClassLookup(null);
                }
                break;
            case InnerContextAccessPrivilegesLanguage.CREATE_THREAD:
                builder.allowCreateThread(allowPrivilege);
                break;
            case InnerContextAccessPrivilegesLanguage.POLYGLOT_ACCESS:
                builder.allowPolyglotAccess(allowPrivilege ? PolyglotAccess.ALL : PolyglotAccess.NONE);
                break;
            case InnerContextAccessPrivilegesLanguage.ENVIRONMENT_ACCESS:
                if (allowPrivilege) {
                    builder.environment(OUTER_CONTEXT_TEST_KEY, "enabled");
                    builder.allowEnvironmentAccess(EnvironmentAccess.INHERIT);
                } else {
                    builder.environment(OUTER_CONTEXT_TEST_KEY, "disabled");
                    builder.allowEnvironmentAccess(EnvironmentAccess.NONE);
                }
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        return builder;
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextSharingLanguage extends AbstractExecutableTestLanguage {

        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextSharingLanguage.class);
        static final LanguageReference<InnerContextSharingLanguage> LANGUAGE_REFERENCE = LanguageReference.create(InnerContextSharingLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            ExecutableContext outerContext = CONTEXT_REFERENCE.get(null);
            InnerContextSharingLanguage outerLanguage = LANGUAGE_REFERENCE.get(null);
            Boolean forceSharing = (Boolean) contextArguments[0];
            boolean expectOuterShared = (boolean) contextArguments[1];
            boolean expectInnerShared = (boolean) contextArguments[2];

            ExecutableContext innerContext = null;
            InnerContextSharingLanguage innerLanguage = null;

            for (int i = 0; i < 3; i++) {
                TruffleContext c = env.newInnerContextBuilder().initializeCreatorContext(true).forceSharing(forceSharing).build();
                Object prev = c.enter(null);
                try {
                    if (i != 0) {
                        if (expectInnerShared) {
                            Assert.assertSame(innerLanguage, LANGUAGE_REFERENCE.get(null));
                        } else {
                            Assert.assertNotSame(innerLanguage, LANGUAGE_REFERENCE.get(null));
                        }
                    }
                    innerContext = CONTEXT_REFERENCE.get(null);
                    innerLanguage = LANGUAGE_REFERENCE.get(null);

                    if (expectOuterShared) {
                        Assert.assertSame(outerLanguage, innerLanguage);
                    } else {
                        Assert.assertNotSame(outerLanguage, innerLanguage);
                    }

                    Assert.assertNotSame(outerContext, innerContext);

                } finally {
                    c.leave(null, prev);
                    c.close();
                }
            }
            return null;
        }
    }

    @Test
    public void testInnerContextSharing() {
        Boolean forceSharing;
        boolean expectOuterShared;
        boolean expectInnerShared;

        forceSharing = null;
        expectOuterShared = false;
        expectInnerShared = false;
        try (Context context = Context.newBuilder().build()) {
            execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
        }

        forceSharing = null;
        expectOuterShared = true;
        expectInnerShared = true;
        try (Engine engine = Engine.create()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
            }
            try (Context context = Context.newBuilder().engine(engine).build()) {
                execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
            }
        }

        forceSharing = Boolean.TRUE;
        expectOuterShared = false;
        expectInnerShared = true;
        try (Context context = Context.newBuilder().build()) {
            execute(context, InnerContextSharingLanguage.class, forceSharing,
                            expectOuterShared, expectInnerShared);
        }

        forceSharing = Boolean.TRUE;
        expectOuterShared = true;
        expectInnerShared = true;
        try (Engine engine = Engine.create()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
            }
            try (Context context = Context.newBuilder().engine(engine).build()) {
                execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
            }
        }

        forceSharing = Boolean.FALSE;
        expectOuterShared = false;
        expectInnerShared = false;
        try (Context context = Context.newBuilder().build()) {
            execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
        }

        forceSharing = Boolean.FALSE;
        expectOuterShared = false;
        expectInnerShared = false;
        try (Engine engine = Engine.create()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
            }
            try (Context context = Context.newBuilder().engine(engine).build()) {
                execute(context, InnerContextSharingLanguage.class, forceSharing, expectOuterShared, expectInnerShared);
            }
        }
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextOptionsLanguage extends AbstractExecutableTestLanguage {

        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextOptionsLanguage.class);
        static final LanguageReference<InnerContextOptionsLanguage> LANGUAGE_REFERENCE = LanguageReference.create(InnerContextOptionsLanguage.class);

        @Option(help = "", category = OptionCategory.USER, stability = OptionStability.STABLE) public static final OptionKey<String> InheritedKey = new OptionKey<>("");
        @Option(help = "", category = OptionCategory.USER, stability = OptionStability.STABLE) public static final OptionKey<String> SharingKey = new OptionKey<>("");

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(SharingKey).equals(newOptions.get(SharingKey));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new InnerContextOptionsLanguageOptionDescriptors();
        }

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean expectSupportOptions = (boolean) contextArguments[0];
            boolean expectShared = (boolean) contextArguments[1];

            ExecutableContext innerContext = null;
            assertEquals("inheritedValue", env.getOptions().get(InheritedKey));

            if (expectSupportOptions) {
                assertTrue(env.isInnerContextOptionsAllowed());
                var builder = env.newInnerContextBuilder();

                assertFails(() -> builder.option(null, "value"), NullPointerException.class);
                assertFails(() -> builder.option(getOptionKey("SharingKey"), null), NullPointerException.class);
                assertFails(() -> builder.options(null), NullPointerException.class);
                var invalidMap = new HashMap<String, String>();
                invalidMap.put("map", null);
                assertFails(() -> builder.options(invalidMap), NullPointerException.class);

                builder.options(new HashMap<>());

                TruffleContext c = builder.initializeCreatorContext(true).option(getOptionKey("SharingKey"), "value").build();
                Object prev = c.enter(null);

                if (expectShared) {
                    assertSame(this, LANGUAGE_REFERENCE.get(null));
                } else {
                    assertNotSame(this, LANGUAGE_REFERENCE.get(null));
                }

                try {
                    innerContext = CONTEXT_REFERENCE.get(null);
                    assertEquals("value", innerContext.env.getOptions().get(SharingKey));
                    assertEquals("inheritedValue", innerContext.env.getOptions().get(InheritedKey));

                } finally {
                    c.leave(null, prev);
                    c.close();
                }
            } else {
                assertFalse(env.isInnerContextOptionsAllowed());
                var builder = env.newInnerContextBuilder().initializeCreatorContext(true).option(getOptionKey("SharingKey"), "value");
                assertFails(() -> builder.build(), IllegalArgumentException.class, (e) -> {
                    assertEquals("Language options were specified for the inner context but the outer context does not have the required context options privilege for this operation. " +
                                    "Use TruffleLanguage.Env.isInnerContextOptionsAllowed() to check whether the inner context has this privilege. " +
                                    "Use Context.Builder.allowInnerContextOptions(true) to grant inner context option privilege for inner contexts.", e.getMessage());
                });
            }

            return null;
        }

        static String getOptionKey(String name) {
            return TestUtils.getDefaultLanguageId(InnerContextOptionsLanguage.class) + "." + name;
        }

    }

    @Test
    public void testInnerContextOptions() {
        // allow inner context options when all access is granted
        try (Context context = Context.newBuilder().//
                        option(InnerContextOptionsLanguage.getOptionKey("InheritedKey"), "inheritedValue").//
                        allowInnerContextOptions(true).build()) {
            execute(context, InnerContextOptionsLanguage.class, true, false);
        }

        // disallow inner context options when all is denied
        try (Context context = Context.newBuilder().//
                        option(InnerContextOptionsLanguage.getOptionKey("InheritedKey"), "inheritedValue").//
                        allowInnerContextOptions(false).build()) {
            execute(context, InnerContextOptionsLanguage.class, false, false);
        }

        try (Engine engine = Engine.create()) {
            // test options may disable sharing for inner context options
            try (Context context = Context.newBuilder().//
                            option(InnerContextOptionsLanguage.getOptionKey("InheritedKey"), "inheritedValue").//
                            allowInnerContextOptions(true).engine(engine).build()) {
                execute(context, InnerContextOptionsLanguage.class, true, false);
            }
            // test with matching options we share code with the inner context
            try (Context context = Context.newBuilder().//
                            option(InnerContextOptionsLanguage.getOptionKey("InheritedKey"), "inheritedValue").//
                            option(InnerContextOptionsLanguage.getOptionKey("SharingKey"), "value").//
                            allowInnerContextOptions(true).engine(engine).build()) {
                execute(context, InnerContextOptionsLanguage.class, true, true);
            }
        }
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextArgumentsLanguage extends AbstractExecutableTestLanguage {

        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextArgumentsLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            assertArrayEquals(env.getApplicationArguments(), new String[]{"outerArgs"});

            var builder = env.newInnerContextBuilder().initializeCreatorContext(true);

            assertFails(() -> builder.arguments(null, new String[0]), NullPointerException.class);
            assertFails(() -> builder.arguments(TestUtils.getDefaultLanguageId(InnerContextArgumentsLanguage.class), null), NullPointerException.class);
            assertFails(() -> builder.arguments(TestUtils.getDefaultLanguageId(InnerContextArgumentsLanguage.class), new String[]{null}), NullPointerException.class);

            TruffleContext c;
            Object prev;

            c = builder.build();
            prev = c.enter(null);
            try {
                // application arguments are not inherited to inner contexts by default
                assertArrayEquals(new String[0], CONTEXT_REFERENCE.get(null).env.getApplicationArguments());
            } finally {
                c.leave(null, prev);
                c.close();
            }

            c = builder.arguments(TestUtils.getDefaultLanguageId(InnerContextArgumentsLanguage.class), new String[]{"innerArgs"}).build();
            prev = c.enter(null);
            try {
                // if set for inner contexts application arguments are availble
                assertArrayEquals(new String[]{"innerArgs"}, CONTEXT_REFERENCE.get(null).env.getApplicationArguments());
            } finally {
                c.leave(null, prev);
                c.close();
            }

            return null;
        }

    }

    @Test
    public void testInnerContextArguments() {
        try (Context context = Context.newBuilder().//
                        arguments(TestUtils.getDefaultLanguageId(InnerContextArgumentsLanguage.class), new String[]{"outerArgs"}).build()) {
            execute(context, InnerContextArgumentsLanguage.class);
        }
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextTimeZoneLanguage extends AbstractExecutableTestLanguage {

        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextTimeZoneLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            ZoneId outerZone = env.getTimeZone();
            assertEquals(ZoneOffset.UTC, outerZone);

            TruffleContext c;
            Object prev;

            c = env.newInnerContextBuilder().initializeCreatorContext(true).timeZone(null).build();
            prev = c.enter(null);
            try {
                // time zone is inherited unless set explicitly
                assertEquals(ZoneOffset.UTC, CONTEXT_REFERENCE.get(null).env.getTimeZone());
            } finally {
                c.leave(null, prev);
                c.close();
            }

            c = env.newInnerContextBuilder().initializeCreatorContext(true).timeZone(ZoneOffset.of("+1")).build();
            prev = c.enter(null);
            try {
                // if explicitly set time zone is passed on
                assertEquals(ZoneOffset.of("+1"), CONTEXT_REFERENCE.get(null).env.getTimeZone());
            } finally {
                c.leave(null, prev);
                c.close();
            }

            return null;
        }

    }

    @Test
    public void testInnerContextTimeZone() {
        try (Context context = Context.newBuilder().//
                        timeZone(ZoneOffset.UTC).build()) {
            execute(context, InnerContextTimeZoneLanguage.class);
        }
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextInnerStreamsLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(InnerContextInnerStreamsLanguage.class);
        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextInnerStreamsLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext c;
            Object prev;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            ByteArrayInputStream in = new ByteArrayInputStream("innerIn".getBytes());

            c = env.newInnerContextBuilder().initializeCreatorContext(true).out(out).err(err).in(in).build();
            prev = c.enter(null);
            try {
                Env innerEnv = CONTEXT_REFERENCE.get(null).env;
                innerEnv.out().write("innerOut".getBytes());
                assertEquals("innerOut", new String(out.toByteArray()));

                innerEnv.err().write("innerErr".getBytes());
                assertEquals("innerErr", new String(err.toByteArray()));

                assertEquals("innerIn", new String(innerEnv.in().readAllBytes()));
            } finally {
                c.leave(null, prev);
                c.close();
            }

            return null;
        }
    }

    @Test
    public void testInnerContextInnerStreams() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream("outerIn".getBytes());
        try (Context context = Context.newBuilder().logHandler(log).out(out).err(err).in(in).build()) {
            execute(context, InnerContextInnerStreamsLanguage.class);
        }
        assertEquals("", new String(out.toByteArray()));
        assertEquals("", new String(err.toByteArray()));
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextOuterStreamsLanguage extends AbstractExecutableTestLanguage {

        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextOuterStreamsLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext c;
            Object prev;

            c = env.newInnerContextBuilder().initializeCreatorContext(true).out(null).err(null).in(null).build();
            prev = c.enter(null);
            try {
                Env innerEnv = CONTEXT_REFERENCE.get(null).env;
                innerEnv.out().write("innerOut".getBytes());
                innerEnv.err().write("innerErr".getBytes());
                assertEquals("outerIn", new String(innerEnv.in().readAllBytes()));
            } finally {
                c.leave(null, prev);
                c.close();
            }
            return null;
        }
    }

    @Test
    public void testInnerContextOuterStreams() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream("outerIn".getBytes());
        try (Context context = Context.newBuilder().logHandler(log).out(out).err(err).in(in).build()) {
            execute(context, InnerContextOuterStreamsLanguage.class);
        }
        assertEquals("innerOut", new String(out.toByteArray()));
        assertEquals("innerErr", new String(err.toByteArray()));
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextPermittedLanguagesLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(InnerContextPermittedLanguagesLanguage.class);
        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextPermittedLanguagesLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object prev;

            // all languages accessible
            TruffleContext c0 = env.newInnerContextBuilder().build();
            prev = c0.enter(null);
            try {
                c0.initializePublic(null, InnerContextInnerStreamsLanguage.ID); // other language
                                                                                // works
                c0.initializePublic(null, ID); // current language works
            } finally {
                c0.leave(null, prev);
                c0.close();
            }

            // creator context always accessible even if not specified as permitted language
            TruffleContext c1 = env.newInnerContextBuilder(InnerContextInnerStreamsLanguage.ID).initializeCreatorContext(true).build();
            prev = c1.enter(null);
            try {
                c1.initializePublic(null, InnerContextInnerStreamsLanguage.ID); // other language
                                                                                // works
                c1.initializePublic(null, ID); // current language works
            } finally {
                c1.leave(null, prev);
                c1.close();
            }

            // creator context always accessible if initialized
            TruffleContext c2 = env.newInnerContextBuilder(InnerContextInnerStreamsLanguage.ID).build();
            prev = c2.enter(null);
            try {
                c2.initializePublic(null, InnerContextInnerStreamsLanguage.ID); // other language
                                                                                // works
                assertFails(() -> c2.initializePublic(null, ID), IllegalArgumentException.class);
            } finally {
                c2.leave(null, prev);
                c2.close();
            }

            TruffleContext c3 = env.newInnerContextBuilder(ID).initializeCreatorContext(true).build();
            prev = c3.enter(null);
            try {
                c3.initializePublic(null, ID); // other language works
                assertFails(() -> c3.initializePublic(null, InnerContextInnerStreamsLanguage.ID), IllegalArgumentException.class);

                assertNull(CONTEXT_REFERENCE.get(null).env.getPublicLanguages().get(InnerContextInnerStreamsLanguage.ID));
                assertNull(CONTEXT_REFERENCE.get(null).env.getInternalLanguages().get(InnerContextInnerStreamsLanguage.ID));
            } finally {
                c3.leave(null, prev);
                c3.close();
            }

            return null;
        }
    }

    @Test
    public void testGR63778() {
        try (Context context = Context.create()) {
            execute(context, TestGR63778Language.class);
        }
    }

    @TruffleLanguage.Registration
    static class TestGR63778Language extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TruffleContext truffleContext = env.newInnerContextBuilder().build();) {
                Source source = Source.newBuilder(TestUtils.getDefaultLanguageId(TestGR63778Internal.class), "", "source").build();
                assertFails(() -> truffleContext.evalPublic(null, source),
                                IllegalArgumentException.class,
                                (ia) -> {
                                    String message = ia.getMessage();
                                    assertTrue(message.contains(" language with id '" + TestUtils.getDefaultLanguageId(TestGR63778Internal.class) + "' is not available."));
                                    assertTrue(message.contains("A language with this id is installed, but only available internally."));
                                });
                return null;
            }
        }
    }

    @TruffleLanguage.Registration(internal = true)
    static class TestGR63778Internal extends AbstractExecutableTestLanguage {

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
    }

    @Test
    public void testInnerContextPermittedLanguages() {
        try (Context context = Context.create()) {
            execute(context, InnerContextPermittedLanguagesLanguage.class);
        }
    }

    @TruffleLanguage.Registration(contextPolicy = ContextPolicy.SHARED)
    static class InnerContextInheritPermittedLanguagesLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(InnerContextInheritPermittedLanguagesLanguage.class);
        static final ContextReference<ExecutableContext> CONTEXT_REFERENCE = ContextReference.create(InnerContextInheritPermittedLanguagesLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object prev;

            // all languages accessible accessible language config is inherited
            TruffleContext c0 = env.newInnerContextBuilder().build();
            prev = c0.enter(null);
            try {
                c0.initializePublic(null, InnerContextPermittedLanguagesLanguage.ID);
                c0.initializePublic(null, InnerContextInheritPermittedLanguagesLanguage.ID);
                assertFails(() -> c0.initializePublic(null, InnerContextInnerStreamsLanguage.ID), IllegalArgumentException.class);
            } finally {
                c0.leave(null, prev);
                c0.close();
            }

            // reduce languages further
            TruffleContext c1 = env.newInnerContextBuilder(InnerContextInheritPermittedLanguagesLanguage.ID).build();
            prev = c1.enter(null);
            try {
                c1.initializePublic(null, InnerContextInheritPermittedLanguagesLanguage.ID);
                assertFails(() -> c1.initializePublic(null, InnerContextPermittedLanguagesLanguage.ID), IllegalArgumentException.class);
                assertFails(() -> c1.initializePublic(null, InnerContextInnerStreamsLanguage.ID), IllegalArgumentException.class);
            } finally {
                c1.leave(null, prev);
                c1.close();
            }

            // not exisiting language
            var builder0 = env.newInnerContextBuilder("$$$invalidLanguage$$$");
            assertFails(() -> builder0.build(), IllegalArgumentException.class, (e) -> {
                assertTrue(e.getMessage(),
                                e.getMessage().startsWith("The language $$$invalidLanguage$$$ permitted for the created inner context is not installed or was not permitted by the parent context."));
            });

            // existing but unaccessible language
            var builder1 = env.newInnerContextBuilder(InnerContextInnerStreamsLanguage.ID);
            assertFails(() -> builder1.build(), IllegalArgumentException.class, (e) -> {
                assertTrue(e.getMessage(), e.getMessage().startsWith(
                                "The language " + InnerContextInnerStreamsLanguage.ID + " permitted for the created inner context is not installed or was not permitted by the parent context."));
            });

            return null;
        }
    }

    @Test
    public void testInnerContextInheritPermittedLanguages() {
        try (Context context = Context.create(InnerContextInheritPermittedLanguagesLanguage.ID, InnerContextPermittedLanguagesLanguage.ID)) {
            execute(context, InnerContextInheritPermittedLanguagesLanguage.class);
        }
    }

    @TruffleLanguage.Registration
    static class EnterInNewThreadTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Throwable[] error = new Throwable[1];
            Thread thread = new Thread(() -> {
                try {
                    try {
                        Object prev = env.getContext().enter(null);
                        assertNull("already entered in new thread", prev);
                    } finally {
                        env.getContext().leave(null, null);
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
            return null;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testEnterInNewThread() {
        Context context = Context.create();
        evalTestLanguage(context, EnterInNewThreadTestLanguage.class, "");
        context.close();
    }

    @TruffleLanguage.Registration
    static class CloseInnerContextWithParentTestLanguage extends TruffleLanguage<CloseInnerContextWithParentTestLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(CloseInnerContextWithParentTestLanguage.class);

        static class Context {
            private final Env env;
            private int disposeCalled;
            private TruffleContext innerCreatorTruffleContext;
            private static int disposeCalledStatic;

            Context(Env env) {
                this.env = env;
            }
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return executeBoundary();
                }

                @TruffleBoundary
                private Object executeBoundary() {
                    Context outerLangContext = CONTEXT_REF.get(null);
                    outerLangContext.innerCreatorTruffleContext = outerLangContext.env.newInnerContextBuilder().initializeCreatorContext(true).build();
                    Object p = outerLangContext.innerCreatorTruffleContext.enter(null);
                    Context innerLangContext = CONTEXT_REF.get(null);
                    outerLangContext.innerCreatorTruffleContext.leave(null, p);
                    return innerLangContext.disposeCalled;
                }
            }.getCallTarget();
        }

        @Override
        protected void disposeContext(Context context) {
            Context.disposeCalledStatic++;
        }

        private static final ContextReference<Context> CONTEXT_REF = ContextReference.create(CloseInnerContextWithParentTestLanguage.class);
    }

    @TruffleLanguage.Registration
    static class CloseInnerContextWithParentAssertTestLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            int assertsBlockNo = (Integer) contextArguments[0];
            switch (assertsBlockNo) {
                case 1:
                    // no context disposed
                    assertEquals(0, CloseInnerContextWithParentTestLanguage.Context.disposeCalledStatic);
                    break;
                case 2:
                    // bot inner and outer contexts disposed
                    assertEquals(2, CloseInnerContextWithParentTestLanguage.Context.disposeCalledStatic);
                    break;
            }
            return null;
        }
    }

    @Test
    public void testCloseInnerContextWithParent() {
        try (Engine engine = Engine.create()) {
            try (Context context = Context.newBuilder().engine(engine).allowHostAccess(HostAccess.ALL).build()) {
                assertEquals(0, context.eval(CloseInnerContextWithParentTestLanguage.ID, "").asInt());
                remoteAssert(engine, CloseInnerContextWithParentAssertTestLanguage.class, true, 1);
            }
            // inner context automatically closed
            remoteAssert(engine, CloseInnerContextWithParentAssertTestLanguage.class, true, 2);
        }
    }

    @TruffleLanguage.Registration
    static class LazyInitTestLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            int assertsBlockNo = (Integer) contextArguments[0];
            switch (assertsBlockNo) {
                case 1:
                    assertEquals(0, LanguageSPITestLanguage.instanceCount.get());
                    break;
                case 2:
                    assertEquals(1, LanguageSPITestLanguage.instanceCount.get());
                    break;
            }
            return null;
        }
    }

    @Test
    public void testLazyInit() {
        LanguageSPITestLanguage.instanceCount.set(0);
        try (Engine engine = Engine.create()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                remoteAssert(engine, LazyInitTestLanguage.class, false, 1);
                context.initialize(LanguageSPITestLanguage.ID);
                remoteAssert(engine, LazyInitTestLanguage.class, false, 2);
            }
        }
    }

    @Test
    public void testLazyInitSharedEngine() {
        LanguageSPITestLanguage.instanceCount.set(0);
        try (Engine engine = Engine.create()) {
            try (Context context1 = Context.newBuilder().engine(engine).build();
                            Context context2 = Context.newBuilder().engine(engine).build()) {
                remoteAssert(engine, LazyInitTestLanguage.class, false, 1);
                context1.initialize(LanguageSPITestLanguage.ID);
                context2.initialize(LanguageSPITestLanguage.ID);
                remoteAssert(engine, LazyInitTestLanguage.class, false, 2);
            }
        }
    }

    @TruffleLanguage.Registration(id = OneContextLanguage.ID, name = OneContextLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class OneContextLanguage extends MultiContextLanguage {
        static final String ID = "OneContextLanguage";

        public OneContextLanguage() {
            wrapper = false;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return null;
        }

        private static final LanguageReference<OneContextLanguage> REFERENCE = LanguageReference.create(OneContextLanguage.class);
        private static final ContextReference<LanguageContext> CONTEXT_REF = ContextReference.create(OneContextLanguage.class);

        public static OneContextLanguage get(Node node) {
            return REFERENCE.get(node);
        }

        public static LanguageContext getContext() {
            return CONTEXT_REF.get(null);
        }

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return false;
        }

    }

    @TruffleLanguage.Registration(id = MultiContextLanguage.ID, name = MultiContextLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.SHARED)
    public static class MultiContextLanguage extends ProxyLanguage {

        static final String ID = "MultiContextLanguage";

        List<Env> createContextCalled = new ArrayList<>();
        List<Source> parseCalled = new ArrayList<>();
        List<Integer> initializeMultipleContextsCalled = new ArrayList<>(); // executionIndex
        boolean contextCachingEnabled = false;
        int executionIndex;

        @Option(help = "", category = OptionCategory.INTERNAL, stability = OptionStability.STABLE) //
        static final OptionKey<Integer> DummyOption = new OptionKey<>(0);

        public MultiContextLanguage() {
            wrapper = false;
        }

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

        private static final LanguageReference<MultiContextLanguage> REFERENCE = LanguageReference.create(MultiContextLanguage.class);
        private static final ContextReference<LanguageContext> CONTEXT_REF = ContextReference.create(MultiContextLanguage.class);

        public static MultiContextLanguage get(Node node) {
            return REFERENCE.get(node);
        }

        public static LanguageContext getContext() {
            return CONTEXT_REF.get(null);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            executionIndex++;
            parseCalled.add(request.getSource());
            return RootNode.createConstantNode(42).getCallTarget();
        }

        @Override
        protected void initializeMultipleContexts() {
            executionIndex++;
            initializeMultipleContextsCalled.add(executionIndex);
        }

        static MultiContextLanguage getInstance(Class<? extends MultiContextLanguage> lang, Context context) {
            context.enter();
            try {
                if (lang == MultiContextLanguage.class) {
                    return REFERENCE.get(null);
                } else {
                    return OneContextLanguage.REFERENCE.get(null);
                }
            } finally {
                context.leave();
            }
        }

    }

    @Test
    public void testMultiContextBoundEngine() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with bound engine
        Context context = Context.create();

        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context);

        assertEquals(1, lang.createContextCalled.size());
        assertTrue(lang.initializeMultipleContextsCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        context.eval(source1); // cached parse
        assertEquals(1, lang.parseCalled.size());
        assertTrue(lang.initializeMultipleContextsCalled.isEmpty());

        context.eval(source2); // cached parse
        assertEquals(2, lang.parseCalled.size());
        assertEquals(source2.getCharacters(), lang.parseCalled.get(1).getCharacters());
        assertTrue(lang.initializeMultipleContextsCalled.isEmpty());
        context.close();
    }

    @Test
    public void testMultiContextBoundEngineInnerContextWithCaching() throws Exception {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        Source truffleSource1 = getTruffleSource(source1);
        Source truffleSource2 = getTruffleSource(source2);

        // test behavior with bound engine
        Context context = Context.create();
        context.enter();

        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage outerLang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context);

        assertEquals(1, outerLang.createContextCalled.size());
        assertTrue(outerLang.initializeMultipleContextsCalled.isEmpty());
        assertTrue(outerLang.parseCalled.isEmpty());
        Env env = outerLang.createContextCalled.get(0);

        context.eval(source1);
        assertEquals(1, outerLang.parseCalled.size());
        assertEquals(source1.getCharacters(), outerLang.parseCalled.get(0).getCharacters());

        TruffleContext innerContext = env.newInnerContextBuilder().initializeCreatorContext(true).build();
        Object prev = innerContext.enter(null);
        MultiContextLanguage innerLang = MultiContextLanguage.REFERENCE.get(null);
        assertNotSame(innerLang, outerLang);

        Env innerEnv = MultiContextLanguage.getContext().env;
        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, outerLang.parseCalled.size());
        assertEquals(0, outerLang.initializeMultipleContextsCalled.size());
        assertEquals(1, outerLang.createContextCalled.size());

        assertEquals(1, innerLang.parseCalled.size());
        assertEquals(0, innerLang.initializeMultipleContextsCalled.size());
        assertEquals(1, innerLang.createContextCalled.size());

        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, outerLang.parseCalled.size());
        assertEquals(1, innerLang.parseCalled.size());

        innerEnv.parsePublic(truffleSource2);
        assertEquals(1, outerLang.parseCalled.size());
        assertEquals(2, innerLang.parseCalled.size());

        innerContext.leave(null, prev);
        innerContext.close();

        context.eval(source2);
        assertEquals(2, outerLang.parseCalled.size());

        context.leave();
        context.close();
        assertEquals(0, outerLang.initializeMultipleContextsCalled.size());
        assertEquals(1, outerLang.createContextCalled.size());
    }

    @Test
    public void testMultiContextBoundEngineInnerContextNoCaching() throws Exception {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        assertTrue(lang.initializeMultipleContextsCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());
        Env env = lang.createContextCalled.get(0);

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        TruffleContext innerContext = env.newInnerContextBuilder().initializeCreatorContext(true).build();
        Object prev = innerContext.enter(null);

        MultiContextLanguage innerLang = OneContextLanguage.get(null);
        assertNotSame(innerLang, lang);

        Env innerEnv = OneContextLanguage.getContext().env;
        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, innerLang.parseCalled.size());
        assertEquals(0, innerLang.initializeMultipleContextsCalled.size());
        assertEquals(1, innerLang.createContextCalled.size());

        innerEnv.parsePublic(truffleSource1);
        assertEquals(1, innerLang.parseCalled.size());

        innerEnv.parsePublic(truffleSource2);
        assertEquals(2, innerLang.parseCalled.size());

        innerContext.leave(null, prev);
        innerContext.close();

        assertEquals(1, lang.parseCalled.size());
        context.eval(source2);
        assertEquals(2, lang.parseCalled.size());

        context.leave();
        context.close();

        assertEquals(1, lang.createContextCalled.size());
        assertEquals(0, lang.initializeMultipleContextsCalled.size());
    }

    private static Source getTruffleSource(org.graalvm.polyglot.Source source) throws NoSuchFieldException, IllegalAccessException {
        java.lang.reflect.Field impl = source.getClass().getDeclaredField("receiver");
        impl.setAccessible(true);
        return (Source) impl.get(source);
    }

    @Test
    public void testInitializeMultiContextNotCalledForExclusive() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();
        Context context2 = Context.newBuilder().engine(engine).build();
        context1.initialize(OneContextLanguage.ID);
        context2.initialize(OneContextLanguage.ID);
        MultiContextLanguage lang1 = OneContextLanguage.getInstance(OneContextLanguage.class, context1);
        MultiContextLanguage lang2 = OneContextLanguage.getInstance(OneContextLanguage.class, context2);
        assertEquals(0, lang1.initializeMultipleContextsCalled.size());
        assertEquals(0, lang2.initializeMultipleContextsCalled.size());
        context1.close();
        context2.close();
    }

    @Test
    public void testInitializeCalledWithEngineOptions() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Engine engine = Engine.newBuilder().option(MultiContextLanguage.ID + ".DummyOption", "42").build();
        Context context = Context.newBuilder().engine(engine).build();
        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context);
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang.initializeMultipleContextsCalled.get(0));
        assertEquals(1, lang.createContextCalled.size());
        context.close();
        engine.close();
    }

    @Test
    public void testMultiContextExplicitEngineNoCaching() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();

        context1.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang1 = MultiContextLanguage.getInstance(MultiContextLanguage.class, context1);

        assertTrue(lang1.parseCalled.isEmpty());
        assertEquals(1, lang1.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang1.initializeMultipleContextsCalled.get(0));
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

        assertEquals(1, lang1.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang1.initializeMultipleContextsCalled.get(0));
        assertEquals(1, lang1.createContextCalled.size());
        assertEquals(1, lang2.createContextCalled.size());
    }

    @Test
    public void testMultiContextExplicitEngineWithCaching() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();

        context1.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(MultiContextLanguage.class, context1);

        assertTrue(lang.parseCalled.isEmpty());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang.initializeMultipleContextsCalled.get(0));
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
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
    }

    @TruffleLanguage.Registration
    static class ErrorInCreateContextTestLanguage extends TruffleLanguage<Env> {
        static final String ID = TestUtils.getDefaultLanguageId(ErrorInCreateContextTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            throw new RuntimeException();
        }
    }

    @Test
    public void testErrorInCreateContext() {
        testFails((c) -> c.initialize(ErrorInCreateContextTestLanguage.ID));
    }

    @TruffleLanguage.Registration
    static class ErrorInInitializeContextTestLanguage extends TruffleLanguage<Env> {
        static final String ID = TestUtils.getDefaultLanguageId(ErrorInInitializeContextTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected void initializeContext(Env context) throws Exception {
            throw new RuntimeException();
        }
    }

    @Test
    public void testErrorInInitializeContext() {
        testFails((c) -> c.initialize(ErrorInInitializeContextTestLanguage.ID));
    }

    @TruffleLanguage.Registration
    static class ErrorInInitializeThreadTestLanguage extends TruffleLanguage<Env> {
        static final String ID = TestUtils.getDefaultLanguageId(ErrorInInitializeThreadTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected void initializeThread(Env context, Thread thread) {
            throw new RuntimeException();
        }
    }

    @Test
    public void testErrorInInitializeThread() {
        testFails((c) -> c.initialize(ErrorInInitializeThreadTestLanguage.ID));
    }

    @TruffleLanguage.Registration
    static class ErrorInDisposeTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(ErrorInDisposeTestLanguage.class);
        static final AtomicBoolean fail = new AtomicBoolean();

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            fail.set((Boolean) contextArguments[0]);
            return null;
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            if (fail.get()) {
                throw new RuntimeException();
            }
        }
    }

    @Test
    public void testErrorInDisposeLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context c = Context.create();
        c.initialize(ErrorInDisposeTestLanguage.ID);
        evalTestLanguage(c, ErrorInDisposeTestLanguage.class, "set fail to true", true);
        testFails(() -> {
            c.close();
        });
        testFails(() -> {
            c.close();
        });

        // clean up the context
        evalTestLanguage(c, ErrorInDisposeTestLanguage.class, "set fail to false", false);
        c.close();
    }

    @TruffleLanguage.Registration
    static class ErrorInParseTestLanguage extends TruffleLanguage<Env> {
        static final String ID = TestUtils.getDefaultLanguageId(ErrorInParseTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            throw new RuntimeException();
        }
    }

    @Test
    public void testErrorInParse() {
        Context c = Context.create();
        c.initialize(ErrorInParseTestLanguage.ID);
        testFails(() -> c.eval(ErrorInParseTestLanguage.ID, "t0"));
        testFails(() -> c.eval(ErrorInParseTestLanguage.ID, "t1"));
        c.close();
    }

    @Test
    @SuppressWarnings("all")
    public void testLazyOptionInit() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context c = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build();
        c.initialize(ProxyLanguage.ID);
        c.enter();
        Env env = com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext.get(null).getEnv();
        env.exportSymbol("symbol", env.asGuestValue(1));
        assertTrue(c.getPolyglotBindings().hasMember("symbol"));
        env.exportSymbol("symbol", null);
        assertFalse(c.getPolyglotBindings().hasMember("symbol"));
        c.close();
    }

    @Test
    public void testCreateContextDuringDispose() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
                    context.env.newInnerContextBuilder().initializeCreatorContext(true).build();
                }
            }

            @Override
            protected void disposeContext(LanguageContext context) {
                if (contextOnDispose.get()) {
                    context.env.newInnerContextBuilder().initializeCreatorContext(true).build();
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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
                    context.env.newTruffleThreadBuilder(() -> {
                    }).build().start();
                }
            }

            @Override
            protected void disposeContext(LanguageContext context) {
                if (contextOnDispose.get()) {
                    context.env.newTruffleThreadBuilder(() -> {
                    }).build().start();
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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        for (int i = 0; i < scopesArray.length - 1; i++) {
            ((TestScope) scopesArray[i]).parentScope = (TestScope) scopesArray[i + 1];
        }
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                findScopeInvokes++;
                return scopesArray[0];
            }
        });
    }

    @Test
    public void testBindingsWithInvalidScopes() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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

        @CompilationFinal(dimensions = 1) private final String[] keys;

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
    static final class TestScope implements TruffleObject {

        final Map<String, Object> values = new LinkedHashMap<>();
        TestScope parentScope;
        boolean modifiable;
        boolean insertable;
        boolean removable;

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new TestKeysArray(values.keySet().toArray(new String[0]));
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String key) throws UnknownIdentifierException {
            if (values.containsKey(key)) {
                return values.get(key);
            } else if (parentScope != null && parentScope.isMemberReadable(key)) {
                return parentScope.readMember(key);
            } else {
                throw UnknownIdentifierException.create(key);
            }
        }

        @ExportMessage
        @TruffleBoundary
        void writeMember(String key, Object value) throws UnsupportedMessageException {
            if (isMemberModifiable(key)) {
                if (modifiable && values.containsKey(key)) {
                    values.put(key, value);
                } else if (parentScope != null) {
                    parentScope.writeMember(key, value);
                } else {
                    throw UnsupportedMessageException.create();
                }
            } else if (isMemberInsertable(key)) {
                if (insertable && !values.containsKey(key)) {
                    values.put(key, value);
                } else if (parentScope != null) {
                    parentScope.writeMember(key, value);
                } else {
                    throw UnsupportedMessageException.create();
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        @ExportMessage
        @TruffleBoundary
        void removeMember(String key) throws UnsupportedMessageException {
            boolean contains = values.containsKey(key);
            if (removable && contains) {
                values.remove(key);
            } else if (!contains && parentScope != null) {
                parentScope.removeMember(key);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return values.containsKey(member) || parentScope != null && parentScope.isMemberReadable(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberModifiable(String member) {
            return modifiable && values.containsKey(member) || parentScope != null && parentScope.isMemberModifiable(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberInsertable(String member) {
            return !isMemberReadable(member) && (insertable || parentScope != null && parentScope.isMemberInsertable(member));
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberRemovable(String member) {
            boolean contains = values.containsKey(member);
            return removable && contains || !contains && parentScope != null && parentScope.isMemberRemovable(member);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "local";
        }

        @ExportMessage
        boolean hasScopeParent() {
            return parentScope != null;
        }

        @ExportMessage
        Object getScopeParent() throws UnsupportedMessageException {
            if (parentScope != null) {
                return parentScope;
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }

    @Test
    public void testBindingsWithDefaultScope() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

        TestScope scope = new TestScope();
        setupTopScopes(scope);
        testBindingsWithSimpleScope(scope);
    }

    private void testBindingsWithSimpleScope(TestScope scope) {
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
        TruffleTestAssumptions.assumeWeakEncapsulation();

        // innermost to outermost
        TestScope[] scopes = new TestScope[5];
        for (int i = 0; i < 5; i++) {
            scopes[i] = new TestScope();
        }
        setupTopScopes((Object[]) scopes);
        testBindingsWithMultipleScopes(scopes);
    }

    private void testBindingsWithMultipleScopes(TestScope[] scopes) {

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
        // InteropLibrary has asserts for parent scope members being present in the scope.
        try {
            assertValue(bindings, ValueAssert.Trait.MEMBERS);
            fail("The scopes are not hierarchical.");
        } catch (PolyglotException e) {
            // Expected as the merged scope does not contain parent scopes.
        }
        // Correct the scope hierarchy:
        scopes[0].values.put("bar", "val");
        scopes[0].values.put("foo", "val");
        scopes[3].values.clear();
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        c.close();
    }

    @Test
    public void testPolyglotBindings() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return LanguageContext.get(this).env.getPolyglotBindings();
                    }
                }.getCallTarget();
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
        TruffleTestAssumptions.assumeWeakEncapsulation();

        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return LanguageContext.get(this).env.getPolyglotBindings();
                    }
                }.getCallTarget();
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

    private static boolean lookupLanguage(Class<?> serviceClass) {
        Env env = com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext.get(null).env;
        LanguageInfo languageInfo = env.getInternalLanguages().get(SERVICE_LANGUAGE);
        return env.lookup(languageInfo, serviceClass) != null;
    }

    @Test
    public void testLookup() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
    public void testHostLookup() throws Exception {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build()) {
            // Get the host language info:
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get(ProxyInstrument.ID).lookup(ProxyInstrument.Initialize.class).getEnv();
            @SuppressWarnings("unchecked")
            Class<? extends TruffleLanguage<?>> hostLanguage = (Class<? extends TruffleLanguage<?>>) Class.forName("com.oracle.truffle.host.HostLanguage");
            LanguageInfo hostInfo = instrumentEnv.getLanguageInfo(hostLanguage);

            context.initialize(LanguageSPITestLanguage.ID);
            langContext.env.initializeLanguage(hostInfo);
            langContext.env.lookup(hostInfo, Runnable.class);
        }
    }

    @Test
    public void testLanguageInfoLookup() throws Exception {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            context.initialize(ProxyLanguage.ID);
            context.enter();
            Env env = ProxyLanguage.LanguageContext.get(null).getEnv();
            LanguageInfo languageInfo = env.getLanguageInfo(ProxyLanguage.class);
            assertEquals(ProxyLanguage.ID, languageInfo.getId());

            assertFails(() -> env.getLanguageInfo(InvalidLanguageClass.class), IllegalArgumentException.class);

            Class<? extends TruffleLanguage<?>> hostLanguage = InteropLibrary.getUncached().getLanguage(env.asBoxedGuestValue(1));
            assertEquals("host", env.getLanguageInfo(hostLanguage).getId());
        }
    }

    static class InvalidLanguageClass extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

    }

    @Test
    public void testConcurrentLookupWhileInitializing() throws InterruptedException {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
                    com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext.get(null).env.registerService(new LanguageSPITestLanguageService3() {
                    });
                    fail("Illegal state exception should be thrown when calling Env.registerService outside createContext");
                } catch (IllegalStateException e) {
                    // expected
                }
                return RootNode.createConstantNode(true).getCallTarget();
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

        public InheritedVersionLanguage() {
            wrapper = false;
        }
    }

    @Test
    public void testInheritedVersionLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context context = Context.create();
        context.initialize(INHERITED_VERSION);
        final Engine engine = context.getEngine();
        assertEquals(engine.getVersion(), engine.getLanguages().get(INHERITED_VERSION).getVersion());
        context.close();
    }

    static final String WEBSITE_TEST = "SPIWebsiteLanguage";

    @TruffleLanguage.Registration(id = WEBSITE_TEST, name = "", website = "https://graalvm.org/test/${graalvm-website-version}")
    public static class WebsiteLanguage extends ProxyLanguage {

        public WebsiteLanguage() {
            wrapper = false;
        }
    }

    @Test
    public void testWebsiteLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context context = Context.create();
        context.initialize(WEBSITE_TEST);
        final Engine engine = context.getEngine();
        assertEquals(Version.getCurrent().format("https://graalvm.org/test/%[R%d.%d]%[Sdev]"), engine.getLanguages().get(WEBSITE_TEST).getWebsite());
        context.close();
    }

    @SuppressWarnings("serial")
    static class TestError extends RuntimeException {
    }

    @Test
    public void testLanguageErrorDuringInitialization() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
        }
        context.close();
    }

    @Test
    public void testInitializeInternalLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        try (Context context = Context.create()) {
            testInitializeInternalImpl(context, (env) -> env.getInternalLanguages().get(InitializeTestInternalLanguage.ID), (se) -> se == null && InitializeTestInternalLanguage.initialized);
        }
    }

    @Test
    public void testInitializeNonInternalLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.newBuilder().allowEval(ProxyLanguage.ID, InitializeTestLanguage.ID).build()).build()) {
            testInitializeInternalImpl(context, (env) -> env.getPublicLanguages().get(InitializeTestLanguage.ID), (se) -> se == null && InitializeTestLanguage.initialized);
        }
    }

    @Test
    public void testInitializeNonAccessibleLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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
                return new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        Env env = LanguageContext.get(this).getEnv();
                        boundary(env);
                        return true;
                    }

                    @TruffleBoundary
                    private void boundary(Env env) {
                        LanguageInfo languageToInitialize = languageResolver.apply(env);
                        assertNotNull(languageToInitialize);
                        try {
                            assertTrue(env.initializeLanguage(languageToInitialize));
                        } catch (SecurityException se) {
                            exception.set(se);
                        }
                    }
                }.getCallTarget();
            }
        });
        assertTrue(context.eval(ProxyLanguage.ID, "").asBoolean());
        assertTrue(verifier.apply(exception.get()));
    }

    @Test
    public void testInitializeFromServiceInternalLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        try (Context context = Context.create()) {
            testInitializeFromServiceImpl(context, (env) -> env.getInternalLanguages().get(InitializeTestInternalLanguage.ID), () -> InitializeTestInternalLanguage.initialized);
        }
    }

    @Test
    public void testInitializeFromServiceNonInternalLanguage() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.newBuilder().allowEval(ProxyLanguage.ID, InitializeTestLanguage.ID).build()).build()) {
            testInitializeFromServiceImpl(context, (env) -> env.getPublicLanguages().get(InitializeTestLanguage.ID), () -> InitializeTestLanguage.initialized);
        }
    }

    private static void testInitializeFromServiceImpl(Context context, Function<Env, LanguageInfo> languageResolver, Supplier<Boolean> verifier) {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        Env env = LanguageContext.get(this).getEnv();
                        boundary(env);
                        return true;
                    }

                    @TruffleBoundary
                    private void boundary(Env env) {
                        LanguageInfo languageProvidingService = languageResolver.apply(env);
                        assertNotNull(languageProvidingService);
                        InitializeTestBaseLanguage.Service service = env.lookup(languageProvidingService, InitializeTestBaseLanguage.Service.class);
                        assertNotNull(service);
                        service.doNotInitialize();
                        assertFalse(verifier.get());
                        service.doesInitialize();
                        assertTrue(verifier.get());
                    }
                }.getCallTarget();
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
            return RootNode.createConstantNode(true).getCallTarget();
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
            return RootNode.createConstantNode(42).getCallTarget();
        }

        @Override
        protected Env createContext(Env env) {
            return env;
        }

    }

    @Test
    public void testThrowInClassInitializer() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

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

    @Test
    public void testEquals() {
        try (Engine engine = Engine.create()) {
            Language language1 = engine.getLanguages().get(EqualsLanguage1.ID);
            Language language2 = engine.getLanguages().get(EqualsLanguage2.ID);
            assertNotEquals(language1, language2);
            assertEquals(language1, engine.getLanguages().get(EqualsLanguage1.ID));
        }
    }

    @TruffleLanguage.Registration(id = EqualsLanguage1.ID)
    public static final class EqualsLanguage1 extends TruffleLanguage<Void> {

        static final String ID = "EqualsLanguage1";

        @Override
        protected Void createContext(Env env) {
            return null;
        }
    }

    @TruffleLanguage.Registration(id = EqualsLanguage2.ID)
    public static final class EqualsLanguage2 extends TruffleLanguage<Void> {

        static final String ID = "EqualsLanguage2";

        @Override
        protected Void createContext(Env env) {
            return null;
        }
    }
}
