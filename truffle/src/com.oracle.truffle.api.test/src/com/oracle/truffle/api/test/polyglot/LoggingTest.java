/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;

public class LoggingTest {

    @After
    public void tearDown() {
        AbstractLoggingLanguage.action = null;
    }

    @Test
    public void testDefaultLogging() {
        final TestHandler handler = new TestHandler();
        final Level defaultLevel = Level.INFO;
        try (Context ctx = newContextBuilder().logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            List<Map.Entry<Level, String>> expected = createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap());
            Assert.assertEquals(expected, handler.getLog());
            handler.clear();
            ctx.eval(LoggingLanguageSecond.ID, "");
            expected = createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap());
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testSingleLanguageAllLogging() {
        final Level defaultLevel = Level.INFO;
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            // All levels from log1 language and logs >= defaultLevel from log2 language
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            // All levels from log2 language and logs >= defaultLevel from log1 language
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, Level.FINEST, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testAllLanguagesAllLogging() {
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(null, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, Level.FINEST, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testBothLanguagesAllLogging() {
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(
                        createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString(), LoggingLanguageSecond.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, Level.FINEST, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testFinestOnListLogger() {
        final Level defaultLevel = Level.INFO;
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.b", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.b", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testFinestOnIntermediateLogger() {
        final Level defaultLevel = Level.INFO;
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testFinestOnIntermediateNonExistentLogger() {
        final Level defaultLevel = Level.INFO;
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "b.a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("b.a.a", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testDifferentLogLevelOnChildAndParent() {
        final Level defaultLevel = Level.INFO;
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(
                        LoggingLanguageFirst.ID, "a", Level.FINE.toString(),
                        LoggingLanguageFirst.ID, "a.a", Level.FINER.toString(),
                        LoggingLanguageFirst.ID, "a.a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            final List<Map.Entry<Level, String>> expected = new ArrayList<>();
            final Map<String, Level> levels = new HashMap<>();
            levels.put("a", Level.FINE);
            levels.put("a.a", Level.FINER);
            levels.put("a.a.a", Level.FINEST);
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, levels));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testMultipleContextsExclusive() {
        final Level defaultLevel = Level.INFO;
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = newContextBuilder().logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testMultipleContextsNested() {
        final Level defaultLevel = Level.INFO;
        final TestHandler handler1 = new TestHandler();
        final TestHandler handler2 = new TestHandler();
        final TestHandler handler3 = new TestHandler();
        try (Context ctx1 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler1).build()) {
            try (Context ctx2 = newContextBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, "a.a", Level.FINEST.toString())).logHandler(handler2).build()) {
                try (Context ctx3 = newContextBuilder().logHandler(handler3).build()) {
                    ctx1.eval(LoggingLanguageFirst.ID, "");
                    ctx1.eval(LoggingLanguageSecond.ID, "");
                    ctx2.eval(LoggingLanguageFirst.ID, "");
                    ctx2.eval(LoggingLanguageSecond.ID, "");
                    ctx3.eval(LoggingLanguageFirst.ID, "");
                    ctx3.eval(LoggingLanguageSecond.ID, "");
                    List<Map.Entry<Level, String>> expected = new ArrayList<>();
                    expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
                    expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
                    Assert.assertEquals(expected, handler1.getLog());
                    expected = new ArrayList<>();
                    expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                    expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
                    Assert.assertEquals(expected, handler2.getLog());
                    expected = new ArrayList<>();
                    expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                    expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
                    Assert.assertEquals(expected, handler3.getLog());
                }
            }
        }
    }

    @Test
    public void testMultipleContextsNested2() {
        final Level defaultLevel = Level.INFO;
        final TestHandler handler1 = new TestHandler();
        final TestHandler handler2 = new TestHandler();
        try (Context ctx1 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a", Level.FINER.toString())).logHandler(handler1).build()) {
            try (Context ctx2 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINE.toString())).logHandler(handler2).build()) {
                ctx1.eval(LoggingLanguageFirst.ID, "");
                ctx2.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a", Level.FINER)));
                Assert.assertEquals(expected, handler1.getLog());
                expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINE)));
                Assert.assertEquals(expected, handler2.getLog());
            }
        }
    }

    @Test
    public void testMultipleContextsNested3() {
        final Level defaultLevel = Level.INFO;
        final TestHandler handler1 = new TestHandler();
        final TestHandler handler2 = new TestHandler();
        final TestHandler handler3 = new TestHandler();
        try (Context ctx1 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a", Level.FINE.toString())).logHandler(handler1).build()) {
            Context ctx2 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINER.toString())).logHandler(handler2).build();
            try (Context ctx3 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a.a", Level.FINER.toString())).logHandler(handler3).build()) {
                ctx2.close();
                ctx1.eval(LoggingLanguageFirst.ID, "");
                ctx3.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a", Level.FINE)));
                Assert.assertEquals(expected, handler1.getLog());
                expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a.a", Level.FINER)));
                Assert.assertEquals(expected, handler3.getLog());
            }
        }
    }

    @Test
    public void testMultipleContextsNested4() {
        final Level defaultLevel = Level.INFO;
        final TestHandler handler1 = new TestHandler();
        final TestHandler handler2 = new TestHandler();
        final TestHandler handler3 = new TestHandler();
        try (Context ctx1 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a", Level.FINE.toString())).logHandler(handler1).build()) {
            Context ctx2 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINER.toString())).logHandler(handler2).build();
            try (Context ctx3 = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINER.toString())).logHandler(handler3).build()) {
                ctx2.close();
                ctx1.eval(LoggingLanguageFirst.ID, "");
                ctx3.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a", Level.FINE)));
                Assert.assertEquals(expected, handler1.getLog());
                expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINER)));
                Assert.assertEquals(expected, handler3.getLog());
            }
        }
    }

    @Test
    public void testLogRecordImmutable() {
        final TestHandler handler1 = new TestHandler();
        try (Context ctx1 = newContextBuilder().logHandler(handler1).build()) {
            ctx1.eval(LoggingLanguageFirst.ID, "");
            boolean logged = false;
            for (LogRecord r : handler1.getRawLog()) {
                logged = true;
                assertImmutable(r);
            }
            Assert.assertTrue(logged);
        }
    }

    @Test
    public void testParametersPrimitive() {
        final Object[] expected = new Object[]{1, 1L, null, 1.1, 1.1d, "test", 't', null, true};
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, TruffleLogger[]>() {
            @Override
            public boolean test(final LoggingContext context, final TruffleLogger[] loggers) {
                for (TruffleLogger logger : loggers) {
                    logger.log(Level.WARNING, "Parameters", Arrays.copyOf(expected, expected.length));
                }
                return false;
            }
        };
        final TestHandler handler1 = new TestHandler();
        try (Context ctx1 = newContextBuilder().logHandler(handler1).build()) {
            ctx1.eval(LoggingLanguageFirst.ID, "");
            boolean logged = false;
            for (LogRecord r : handler1.getRawLog()) {
                logged = true;
                Assert.assertEquals("Parameters", r.getMessage());
                Assert.assertArrayEquals(expected, r.getParameters());
            }
            Assert.assertTrue(logged);
        }
    }

    @Test
    public void testParametersObjects() {
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, TruffleLogger[]>() {
            @Override
            public boolean test(final LoggingContext context, final TruffleLogger[] loggers) {
                for (TruffleLogger logger : loggers) {
                    logger.log(Level.WARNING, "Parameters", new LoggingLanguageObject("passed"));
                }
                return false;
            }
        };
        final TestHandler handler1 = new TestHandler();
        try (Context ctx1 = newContextBuilder().logHandler(handler1).build()) {
            ctx1.eval(LoggingLanguageFirst.ID, "");
            boolean logged = false;
            for (LogRecord r : handler1.getRawLog()) {
                logged = true;
                Assert.assertEquals("Parameters", r.getMessage());
                Assert.assertArrayEquals(new Object[]{"passed"}, r.getParameters());
            }
            Assert.assertTrue(logged);
        }
    }

    @Test
    public void testInnerContextLogging() {
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, TruffleLogger[]>() {
            @Override
            public boolean test(final LoggingContext context, final TruffleLogger[] loggers) {
                TruffleContext tc = context.getEnv().newContextBuilder().build();
                final Object prev = tc.enter();
                try {
                    for (TruffleLogger logger : loggers) {
                        logger.log(Level.FINEST, "INNER: " + logger.getName());
                    }
                } finally {
                    tc.leave(prev);
                    tc.close();
                }
                return true;
            }
        };
        TestHandler handler = new TestHandler();
        try (Context ctx = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            for (String loggerName : AbstractLoggingLanguage.LOGGER_NAMES) {
                expected.add(new AbstractMap.SimpleImmutableEntry<>(Level.FINEST, "INNER: " + LoggingLanguageFirst.ID + '.' + loggerName));
            }
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testPolyglotLogHandler() {
        CloseableByteArrayOutputStream err = new CloseableByteArrayOutputStream();
        testLogToStream(newContextBuilder().err(err), err, false);
    }

    @Test
    public void testGcedContext() {
        TestHandler handler = new TestHandler();
        Context gcedContext = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build();
        gcedContext.eval(LoggingLanguageFirst.ID, "");
        List<Map.Entry<Level, String>> expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
        Assert.assertEquals(expected, handler.getLog());
        Reference<Context> gcedContextRef = new WeakReference<>(gcedContext);
        gcedContext = null;
        GCUtils.assertGc("Cannot free context.", gcedContextRef);
        handler = new TestHandler();
        Context newContext = newContextBuilder().logHandler(handler).build();
        newContext.eval(LoggingLanguageFirst.ID, "");
        expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.INFO, Collections.emptyMap()));
        Assert.assertEquals(expected, handler.getLog());
    }

    @Test
    public void testGcedContext2() {
        TestHandler gcedContextHandler = new TestHandler();
        Context gcedContext = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(gcedContextHandler).build();
        TestHandler contextHandler = new TestHandler();
        Context context = newContextBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINE.toString())).logHandler(contextHandler).build();
        gcedContext.eval(LoggingLanguageFirst.ID, "");
        List<Map.Entry<Level, String>> expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
        Assert.assertEquals(expected, gcedContextHandler.getLog());
        context.eval(LoggingLanguageFirst.ID, "");
        expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINE, Collections.emptyMap()));
        Assert.assertEquals(expected, contextHandler.getLog());
        Reference<Context> gcedContextRef = new WeakReference<>(gcedContext);
        gcedContext = null;
        GCUtils.assertGc("Cannot free context.", gcedContextRef);
        contextHandler.clear();
        context.eval(LoggingLanguageFirst.ID, "");
        expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINE, Collections.emptyMap()));
        Assert.assertEquals(expected, contextHandler.getLog());
    }

    @Test
    public void testLogToStream() {
        CloseableByteArrayOutputStream stream = new CloseableByteArrayOutputStream();
        testLogToStream(newContextBuilder().logHandler(stream), stream, true);
        stream = new CloseableByteArrayOutputStream();
        try (Engine engine = newEngineBuilder().logHandler(stream).build()) {
            testLogToStream(newContextBuilder().engine(engine), stream, false);
            stream.clear();
            CloseableByteArrayOutputStream innerStream = new CloseableByteArrayOutputStream();
            testLogToStream(newContextBuilder().engine(engine).logHandler(innerStream), innerStream, true);
            Assert.assertFalse(stream.isClosed());
            Assert.assertEquals(0, stream.toByteArray().length);
            testLogToStream(newContextBuilder().engine(engine), stream, false);
        }
        Assert.assertTrue(stream.isClosed());
    }

    @Test
    public void testDecreaseLogLevelSingleContext() {
        Level defaultLevel = Level.INFO;
        Map<String, Level> setLevelsMap = new HashMap<>();
        setLevelsMap.put("a", Level.FINEST);
        setLevelsMap.put("a.a", Level.INFO);
        Context.Builder builder = newContextBuilder();
        for (Map.Entry<String, Level> levelsMapEntry : setLevelsMap.entrySet()) {
            builder.options(createLoggingOptions(LoggingLanguageFirst.ID, levelsMapEntry.getKey(), levelsMapEntry.getValue().toString()));
        }
        TestHandler handler = new TestHandler();
        try (Context ctx = builder.logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, setLevelsMap));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testDecreaseLogLevelMultipleContexts() {
        Level defaultLevel = Level.INFO;
        Map<String, Level> setLevelsMap = new HashMap<>();
        setLevelsMap.put("a", Level.FINEST);
        setLevelsMap.put("a.a", Level.INFO);
        Context.Builder builder = newContextBuilder();
        for (Map.Entry<String, Level> levelsMapEntry : setLevelsMap.entrySet()) {
            builder.options(createLoggingOptions(LoggingLanguageFirst.ID, levelsMapEntry.getKey(), levelsMapEntry.getValue().toString()));
        }
        TestHandler handler = new TestHandler();
        try (Context ctx = builder.logHandler(handler).build()) {
            TestHandler handler2 = new TestHandler();
            try (Context ctx2 = newContextBuilder().logHandler(handler2).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                ctx2.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, setLevelsMap));
                Assert.assertEquals(expected, handler.getLog());
                expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                Assert.assertEquals(expected, handler2.getLog());
            }
        }
    }

    @Test
    public void testDecreaseIncreaseLogLevelSingleContext() {
        Map<String, Level> setLevelsMap = new HashMap<>();
        setLevelsMap.put(null, Level.FINEST);   // level on language root level
        setLevelsMap.put("a", Level.INFO);
        setLevelsMap.put("a.a", Level.FINE);
        TestHandler handler = new TestHandler();
        Context.Builder builder = newContextBuilder();
        for (Map.Entry<String, Level> levelsMapEntry : setLevelsMap.entrySet()) {
            builder.options(createLoggingOptions(LoggingLanguageFirst.ID, levelsMapEntry.getKey(), levelsMapEntry.getValue().toString()));
        }
        try (Context ctx = builder.logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, setLevelsMap.remove(null), setLevelsMap));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testDecreaseIncreaseLogLevelMultipleContexts() {
        Level defaultLevel = Level.INFO;
        Map<String, Level> setLevelsMap = new HashMap<>();
        setLevelsMap.put(null, Level.FINEST);   // level on language root level
        setLevelsMap.put("a", Level.INFO);
        setLevelsMap.put("a.a", Level.FINE);
        Context.Builder builder = newContextBuilder();
        for (Map.Entry<String, Level> levelsMapEntry : setLevelsMap.entrySet()) {
            builder.options(createLoggingOptions(LoggingLanguageFirst.ID, levelsMapEntry.getKey(), levelsMapEntry.getValue().toString()));
        }
        TestHandler handler = new TestHandler();
        try (Context ctx = builder.logHandler(handler).build()) {
            TestHandler handler2 = new TestHandler();
            try (Context ctx2 = newContextBuilder().logHandler(handler2).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                ctx2.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, setLevelsMap.remove(null), setLevelsMap));
                Assert.assertEquals(expected, handler.getLog());
                expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                Assert.assertEquals(expected, handler2.getLog());
            }
        }
    }

    @Test
    public void testDisableLoggersSingleContext() {
        Map<String, Level> setLevelsMap = new HashMap<>();
        setLevelsMap.put(null, Level.FINEST);   // level on language root level
        setLevelsMap.put("a", Level.OFF);
        Context.Builder builder = newContextBuilder();
        for (Map.Entry<String, Level> levelsMapEntry : setLevelsMap.entrySet()) {
            builder.options(createLoggingOptions(LoggingLanguageFirst.ID, levelsMapEntry.getKey(), levelsMapEntry.getValue().toString()));
        }
        TestHandler handler = new TestHandler();
        try (Context ctx = builder.logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, setLevelsMap.remove(null), setLevelsMap));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testDisableLoggersMultipleContexts() {
        Level defaultLevel = Level.INFO;
        Map<String, Level> setLevelsMap = new HashMap<>();
        setLevelsMap.put(null, Level.FINEST);   // level on language root level
        setLevelsMap.put("a", Level.OFF);
        Context.Builder builder = newContextBuilder();
        for (Map.Entry<String, Level> levelsMapEntry : setLevelsMap.entrySet()) {
            builder.options(createLoggingOptions(LoggingLanguageFirst.ID, levelsMapEntry.getKey(), levelsMapEntry.getValue().toString()));
        }
        TestHandler handler = new TestHandler();
        try (Context ctx = builder.logHandler(handler).build()) {
            TestHandler handler2 = new TestHandler();
            try (Context ctx2 = newContextBuilder().logHandler(handler2).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                ctx2.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, setLevelsMap.remove(null), setLevelsMap));
                Assert.assertEquals(expected, handler.getLog());
                expected = new ArrayList<>();
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                Assert.assertEquals(expected, handler2.getLog());
            }
        }
    }

    @Test
    public void testNoContextLoggingBasic() {
        // Engine handler overriden by context handler, logging from language with context
        final Level defaultLevel = Level.INFO;
        TestHandler engineHandler = new TestHandler();
        try (Engine eng = newEngineBuilder().logHandler(engineHandler).build()) {
            TestHandler handler = new TestHandler();
            try (Context ctx = newContextBuilder().engine(eng).options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                ctx.eval(LoggingLanguageSecond.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                // All levels from log1 language and logs >= defaultLevel from log2 language
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
                Assert.assertEquals(expected, handler.getLog());
            }
        }
        Assert.assertTrue(engineHandler.getLog().isEmpty());

        // Engine handler as default, logging from language with context
        engineHandler = new TestHandler();
        try (Engine eng = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(engineHandler).build()) {
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                ctx.eval(LoggingLanguageSecond.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                // All levels from log1 language and logs >= defaultLevel from log2 language
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
                Assert.assertEquals(expected, engineHandler.getLog());
            }
        }
        Assert.assertTrue(engineHandler.getLog().isEmpty());

        // Engine handler as default, logging from instrument and language with context
        engineHandler = new TestHandler();
        ProxyInstrument delegate = new ProxyInstrument();
        delegate.setOnCreate(new InstrumentLogging(false));
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString(), ProxyInstrument.ID, null, Level.FINEST.toString())).logHandler(
                        engineHandler).build()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(true);
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(ProxyInstrument.ID, Level.FINEST, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
                Assert.assertEquals(expected, engineHandler.getLog());
            }
        }

        // Engine handler as default, logging from instrument without context, logging from language
        // with context
        engineHandler = new TestHandler();
        delegate = new ProxyInstrument();
        delegate.setOnCreate(new InstrumentLogging(true));
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString(), ProxyInstrument.ID, null, Level.FINEST.toString())).logHandler(
                        engineHandler).build()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(true);
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(ProxyInstrument.ID, Level.FINEST, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
                Assert.assertEquals(expected, engineHandler.getLog());
            }
        }
    }

    @Test
    public void testNoContextLoggingDefault() {
        final Level defaultLevel = Level.INFO;
        // Logging from instrument and language with context
        TestHandler engineHandler = new TestHandler();
        ProxyInstrument delegate = new ProxyInstrument();
        delegate.setOnCreate(new InstrumentLogging(false));
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = newEngineBuilder().logHandler(engineHandler).build()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(true);
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(ProxyInstrument.ID, defaultLevel, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                Assert.assertEquals(expected, engineHandler.getLog());
            }
        }

        // Logging from instrument without context, logging from language with context
        engineHandler = new TestHandler();
        delegate = new ProxyInstrument();
        delegate.setOnCreate(new InstrumentLogging(true));
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = newEngineBuilder().logHandler(engineHandler).build()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(true);
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(ProxyInstrument.ID, defaultLevel, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                Assert.assertEquals(expected, engineHandler.getLog());
            }
        }
    }

    @Test
    public void testNoContextLoggingEngineAndContextHandler() {
        // Logging from instrument and language with context
        TestHandler engineHandler = new TestHandler();
        ProxyInstrument delegate = new ProxyInstrument();
        delegate.setOnCreate(new InstrumentLogging(false));
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINE.toString(), ProxyInstrument.ID, null, Level.FINE.toString())).logHandler(
                        engineHandler).build()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(true);
            TestHandler contextHandler = new TestHandler();
            try (Context ctx = newContextBuilder().engine(eng).options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINER.toString())).logHandler(contextHandler).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(ProxyInstrument.ID, Level.FINE, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINER, Collections.emptyMap()));
                Assert.assertEquals(expected, contextHandler.getLog());
                Assert.assertTrue(engineHandler.getLog().isEmpty());
            }
        }

        // Logging from instrument without context, logging from language with context
        engineHandler = new TestHandler();
        delegate = new ProxyInstrument();
        delegate.setOnCreate(new InstrumentLogging(true));
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINE.toString(), ProxyInstrument.ID, null, Level.FINE.toString())).logHandler(
                        engineHandler).build()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(true);
            TestHandler contextHandler = new TestHandler();
            try (Context ctx = newContextBuilder().engine(eng).options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINER.toString())).logHandler(contextHandler).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                List<Map.Entry<Level, String>> expectedInEngine = new ArrayList<>();
                expectedInEngine.addAll(createExpectedLog(ProxyInstrument.ID, Level.FINE, Collections.emptyMap()));
                Assert.assertEquals(expectedInEngine, engineHandler.getLog());
                List<Map.Entry<Level, String>> expectedInContext = new ArrayList<>();
                expectedInContext.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINER, Collections.emptyMap()));
                Assert.assertEquals(expectedInContext, contextHandler.getLog());
            }
        }
    }

    @Test
    public void testNoContextLoggingMultipleEngines() {
        TestHandler engine1Handler = new TestHandler();
        TestHandler engine2Handler = new TestHandler();
        ProxyInstrument delegate = new ProxyInstrument();
        delegate.setOnCreate(new InstrumentLogging(true));
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng1 = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString(), ProxyInstrument.ID, null, Level.FINEST.toString())).logHandler(
                        engine1Handler).build()) {
            try (Engine eng2 = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINE.toString(), ProxyInstrument.ID, null, Level.FINE.toString())).logHandler(
                            engine2Handler).build()) {
                LoggingLanguageFirst.action = new LookupInstrumentAction(true);
                try (Context ctx = newContextBuilder().engine(eng1).build()) {
                    ctx.eval(LoggingLanguageFirst.ID, "");
                }
                LoggingLanguageFirst.action = new LookupInstrumentAction(true);
                try (Context ctx = newContextBuilder().engine(eng2).build()) {
                    ctx.eval(LoggingLanguageFirst.ID, "");
                }
                List<Map.Entry<Level, String>> expected = new ArrayList<>();
                expected.addAll(createExpectedLog(ProxyInstrument.ID, Level.FINEST, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
                Assert.assertEquals(expected, engine1Handler.getLog());
                expected.clear();
                expected.addAll(createExpectedLog(ProxyInstrument.ID, Level.FINE, Collections.emptyMap()));
                expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINE, Collections.emptyMap()));
                Assert.assertEquals(expected, engine2Handler.getLog());
            }
        }
    }

    @Test
    public void testNoContextLoggingDefaultTruffleLogger() {
        TestHandler engineHandler = new TestHandler();
        try (Engine eng = newEngineBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINE.toString(), ProxyInstrument.ID, null, Level.FINE.toString())).logHandler(
                        engineHandler).build()) {
            AtomicReference<TruffleLogger> loggerRef = new AtomicReference<>();
            LoggingLanguageFirst.action = new BiPredicate<LoggingContext, TruffleLogger[]>() {
                @Override
                public boolean test(LoggingContext ctx, TruffleLogger[] loggers) {
                    Assert.assertTrue(loggers.length > 0);
                    loggerRef.set(loggers[0]);
                    return true;
                }
            };
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
                try {
                    loggerRef.get().log(Level.INFO, "Should not be logged.");
                    Assert.assertFalse(assertionsEnabled());
                } catch (IllegalStateException e) {
                    // Expected
                }
                List<Map.Entry<Level, String>> expectedInEngine = new ArrayList<>();
                expectedInEngine.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINE, Collections.emptyMap()));
                Assert.assertEquals(expectedInEngine, engineHandler.getLog());
            }
        }
    }

    @Test
    public void testEngineLoggerIdentity() {
        ProxyInstrument delegate = new ProxyInstrument();
        AtomicReferenceArray<TruffleLogger> eng1Loggers = new AtomicReferenceArray<>(2);
        delegate.setOnCreate(new Consumer<TruffleInstrument.Env>() {
            @Override
            public void accept(TruffleInstrument.Env env) {
                TruffleLogger logger1 = env.getLogger("a.b.c");
                TruffleLogger logger2 = env.getLogger("a.b");
                Assert.assertNotSame(logger1, logger2);
                Assert.assertSame(logger1, env.getLogger("a.b.c"));
                Assert.assertSame(logger2, env.getLogger("a.b"));
                Assert.assertNotSame(logger1, TruffleLogger.getLogger(ProxyInstrument.ID, "a.b.c"));
                Assert.assertNotSame(logger2, TruffleLogger.getLogger(ProxyInstrument.ID, "a.b"));
                eng1Loggers.set(0, logger1);
                eng1Loggers.set(1, logger2);
            }
        });
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = Engine.create()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(false);
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
            }
        }
        delegate = new ProxyInstrument();
        delegate.setOnCreate(new Consumer<TruffleInstrument.Env>() {
            @Override
            public void accept(TruffleInstrument.Env env) {
                TruffleLogger logger1 = env.getLogger("a.b.c");
                TruffleLogger logger2 = env.getLogger("a.b");
                Assert.assertNotSame(logger1, logger2);
                Assert.assertNotSame(eng1Loggers.get(0), logger1);
                Assert.assertNotSame(eng1Loggers.get(1), logger2);
            }
        });
        ProxyInstrument.setDelegate(delegate);
        try (Engine eng = Engine.create()) {
            LoggingLanguageFirst.action = new LookupInstrumentAction(false);
            try (Context ctx = newContextBuilder().engine(eng).build()) {
                ctx.eval(LoggingLanguageFirst.ID, "");
            }
        }
    }

    @Test
    public void testErrorStream() {
        ByteArrayOutputStream errConsumer = new ByteArrayOutputStream();
        ProxyInstrument delegate = new ProxyInstrument();
        delegate.setOnCreate(new Consumer<TruffleInstrument.Env>() {
            @Override
            public void accept(TruffleInstrument.Env env) {
                env.getInstrumenter().attachErrConsumer(errConsumer);
                new InstrumentLogging(false).accept(env);
            }
        });
        ProxyInstrument.setDelegate(delegate);
        LoggingLanguageFirst.action = new LookupInstrumentAction(true);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (Context ctx = newContextBuilder().err(err).options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINE.toString(), ProxyInstrument.ID, null, Level.FINE.toString())).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
        }
        Assert.assertNotEquals(0, err.toByteArray().length);
        Assert.assertEquals(0, errConsumer.toByteArray().length);
    }

    @Test
    public void testInvalidId() {
        Context.Builder builder = newContextBuilder();
        TestHandler handler = new TestHandler();
        LoggingLanguageFirst.action = new BiPredicate<LoggingContext, TruffleLogger[]>() {
            @Override
            public boolean test(LoggingContext ctx, TruffleLogger[] defaultLoggers) {
                try {
                    TruffleLogger.getLogger("LoggingTest-Invalid-Id");
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException iae) {
                    // Expected exception
                }
                try {
                    TruffleLogger.getLogger("LoggingTest-Invalid-Id", LoggingTest.class);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException iae) {
                    // Expected exception
                }
                try {
                    TruffleLogger.getLogger("LoggingTest-Invalid-Id", "global");
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException iae) {
                    // Expected exception
                }
                return false;
            }
        };
        try (Context ctx = builder.logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
        }
    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }

    private static void testLogToStream(Context.Builder contextBuilder, CloseableByteArrayOutputStream stream, boolean expectStreamClosed) {
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, TruffleLogger[]>() {
            @Override
            @CompilerDirectives.TruffleBoundary
            public boolean test(final LoggingContext context, final TruffleLogger[] loggers) {
                TruffleLogger.getLogger(LoggingLanguageFirst.ID).warning(LoggingLanguageFirst.ID);
                TruffleLogger.getLogger(LoggingLanguageFirst.ID, "package.class").warning(LoggingLanguageFirst.ID + "::package.class");
                return false;
            }
        };
        try (Context ctx = contextBuilder.build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
        }
        Assert.assertEquals(expectStreamClosed, stream.isClosed());
        final String output = new String(stream.toByteArray());
        final Pattern p = Pattern.compile("\\[(.*)\\]\\sWARNING:\\s(.*)");
        for (String line : output.split("\n")) {
            final Matcher m = p.matcher(line.trim());
            Assert.assertTrue(m.matches());
            final String loggerName = m.group(1);
            final String message = m.group(2);
            Assert.assertEquals(message, loggerName);
        }
    }

    private static void assertImmutable(final LogRecord r) {
        try {
            r.setLevel(Level.FINEST);
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setLoggerName("test");
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setMessage("test");
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            setMillis(r, 10);
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setParameters(new Object[0]);
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setResourceBundle(null);
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setResourceBundleName(null);
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setSequenceNumber(10);
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setSourceClassName("Test");
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setSourceMethodName("test");
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setThreadID(10);
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            r.setThrown(new NullPointerException());
            Assert.fail("Should not reach here.");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    private static void setMillis(final LogRecord r, long value) {
        r.setMillis(value);
    }

    private static Map<String, String> createLoggingOptions(final String... kvs) {
        if ((kvs.length % 3) != 0) {
            throw new IllegalArgumentException("Lang, Key, Val length has to be divisible by 3.");
        }
        final Map<String, String> options = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 3) {
            final String key;
            if (kvs[i] == null) {
                assert kvs[i + 1] == null;
                key = "log.level";
            } else if (kvs[i + 1] == null) {
                key = String.format("log.%s.level", kvs[i]);
            } else {
                key = String.format("log.%s.%s.level", kvs[i], kvs[i + 1]);
            }
            options.put(key, kvs[i + 2]);
        }
        return options;
    }

    private static List<Map.Entry<Level, String>> createExpectedLog(final String languageId, final Level defaultLevel, final Map<String, Level> levels) {
        final LoggerNode root = levels.isEmpty() ? null : createLevelsTree(levels);
        final List<Map.Entry<Level, String>> res = new ArrayList<>();
        for (Level level : AbstractLoggingLanguage.LOGGER_LEVELS) {
            for (String loggerName : AbstractLoggingLanguage.LOGGER_NAMES) {
                final Level loggerLevel = root == null ? defaultLevel : root.computeLevel(loggerName, defaultLevel);
                if (loggerLevel.intValue() <= level.intValue()) {
                    res.add(new AbstractMap.SimpleImmutableEntry<>(
                                    level,
                                    String.format("%s.%s", languageId, loggerName)));
                }
            }
        }
        return res;
    }

    private static LoggerNode createLevelsTree(Map<String, Level> levels) {
        final LoggerNode root = new LoggerNode();
        for (Map.Entry<String, Level> level : levels.entrySet()) {
            final String loggerName = level.getKey();
            final Level loggerLevel = level.getValue();
            final LoggerNode node = root.findChild(loggerName);
            node.level = loggerLevel;
        }
        return root;
    }

    /**
     * Creates a Context builder with disabled compiler logging.
     */
    private static Context.Builder newContextBuilder() {
        return Context.newBuilder().options(createLoggingOptions("engine", null, Level.OFF.toString()));
    }

    /**
     * Creates an Engine builder with disabled compiler logging.
     */
    private static Engine.Builder newEngineBuilder() {
        return Engine.newBuilder().options(createLoggingOptions("engine", null, Level.OFF.toString()));
    }

    public static final class LoggingContext {
        private final TruffleLanguage.Env env;

        LoggingContext(final TruffleLanguage.Env env) {
            this.env = env;
        }

        TruffleLanguage.Env getEnv() {
            return env;
        }
    }

    private static final class LoggerNode {
        private final Map<String, LoggerNode> children;
        private Level level;

        LoggerNode() {
            this.children = new HashMap<>();
        }

        LoggerNode findChild(String loggerName) {
            if (loggerName.isEmpty()) {
                return this;
            }
            int index = loggerName.indexOf('.');
            String currentNameCompoment;
            String nameRest;
            if (index > 0) {
                currentNameCompoment = loggerName.substring(0, index);
                nameRest = loggerName.substring(index + 1);
            } else {
                currentNameCompoment = loggerName;
                nameRest = "";
            }
            LoggerNode child = children.get(currentNameCompoment);
            if (child == null) {
                child = new LoggerNode();
                children.put(currentNameCompoment, child);
            }
            return child.findChild(nameRest);
        }

        Level computeLevel(final String loggerName, final Level bestSoFar) {
            Level res = bestSoFar;
            if (this.level != null) {
                res = level;
            }
            if (loggerName.isEmpty()) {
                return res;
            }
            int index = loggerName.indexOf('.');
            String currentNameCompoment;
            String nameRest;
            if (index > 0) {
                currentNameCompoment = loggerName.substring(0, index);
                nameRest = loggerName.substring(index + 1);
            } else {
                currentNameCompoment = loggerName;
                nameRest = "";
            }
            LoggerNode child = children.get(currentNameCompoment);
            if (child == null) {
                return res;
            }
            return child.computeLevel(nameRest, res);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class LoggingLanguageObject implements TruffleObject {
        final String stringValue;

        LoggingLanguageObject(final String stringValue) {
            this.stringValue = stringValue;
        }

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return stringValue;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return LoggingLanguageFirst.class;
        }

    }

    public abstract static class AbstractLoggingLanguage extends TruffleLanguage<LoggingContext> {
        static final String[] LOGGER_NAMES = {"a", "a.a", "a.b", "a.a.a", "b", "b.a", "b.a.a.a"};
        static final Level[] LOGGER_LEVELS = {Level.FINEST, Level.FINER, Level.FINE, Level.INFO, Level.SEVERE, Level.WARNING};
        static BiPredicate<LoggingContext, TruffleLogger[]> action;
        private final TruffleLogger[] allLoggers;

        AbstractLoggingLanguage(final String id) {
            final ArrayList<TruffleLogger> loggers = new ArrayList<>(LOGGER_NAMES.length);
            for (String loggerName : LOGGER_NAMES) {
                loggers.add(TruffleLogger.getLogger(id, loggerName));
            }
            allLoggers = loggers.toArray(new TruffleLogger[0]);
        }

        @Override
        protected LoggingContext createContext(Env env) {
            return new LoggingContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            Class<? extends AbstractLoggingLanguage> language = getClass();
            final RootNode root = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    boolean doDefaultLogging = true;
                    if (action != null) {
                        doDefaultLogging = action.test(lookupContextReference(language).get(), allLoggers);
                    }
                    if (doDefaultLogging) {
                        doLog();
                    }
                    return lookupContextReference(language).get().getEnv().asGuestValue(null);
                }
            };
            return Truffle.getRuntime().createCallTarget(root);
        }

        private void doLog() {
            for (Level level : LOGGER_LEVELS) {
                for (TruffleLogger logger : allLoggers) {
                    logger.log(level, logger.getName());
                }
            }
        }
    }

    @TruffleLanguage.Registration(id = LoggingLanguageFirst.ID, name = LoggingLanguageFirst.ID, version = "1.0")
    public static final class LoggingLanguageFirst extends AbstractLoggingLanguage {
        static final String ID = "log1";

        public LoggingLanguageFirst() {
            super(ID);
        }
    }

    @TruffleLanguage.Registration(id = LoggingLanguageSecond.ID, name = LoggingLanguageSecond.ID, version = "1.0")
    public static final class LoggingLanguageSecond extends AbstractLoggingLanguage {
        static final String ID = "log2";

        public LoggingLanguageSecond() {
            super(ID);
        }
    }

    private static final class TestHandler extends Handler {
        private final List<LogRecord> logRecords;

        TestHandler() {
            this.logRecords = new ArrayList<>();
        }

        @Override
        public void publish(final LogRecord record) {
            final String message = record.getMessage();
            Assert.assertNotNull(message);
            final Level level = record.getLevel();
            Assert.assertNotNull(level);
            logRecords.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
            clear();
        }

        List<Map.Entry<Level, String>> getLog() {
            final List<Map.Entry<Level, String>> res = new ArrayList<>();
            for (LogRecord r : logRecords) {
                res.add(new AbstractMap.SimpleImmutableEntry<>(r.getLevel(), r.getMessage()));
            }
            return res;
        }

        List<LogRecord> getRawLog() {
            return logRecords;
        }

        void clear() {
            logRecords.clear();
        }
    }

    private static final class CloseableByteArrayOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }

        void clear() {
            this.count = 0;
        }
    }

    private static final class InstrumentLogging implements Consumer<TruffleInstrument.Env> {
        static final String[] LOGGER_NAMES = {"a", "a.a", "a.b", "a.a.a", "b", "b.a", "b.a.a.a"};
        static final Level[] LOGGER_LEVELS = {Level.FINEST, Level.FINER, Level.FINE, Level.INFO, Level.SEVERE, Level.WARNING};

        private final boolean detached;

        InstrumentLogging(boolean detached) {
            this.detached = detached;
        }

        @Override
        public void accept(TruffleInstrument.Env env) {
            if (detached) {
                Thread t = new Thread(() -> {
                    doLogging(env);
                });
                t.start();
                try {
                    t.join(10_000);
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            } else {
                doLogging(env);
            }
        }

        private static void doLogging(TruffleInstrument.Env env) {
            for (Level level : LOGGER_LEVELS) {
                for (String loggerName : LOGGER_NAMES) {
                    TruffleLogger logger = env.getLogger(loggerName);
                    logger.log(level, logger.getName());
                }
            }
        }
    }

    private static final class LookupInstrumentAction implements BiPredicate<LoggingContext, TruffleLogger[]> {

        private final boolean performDefaultLogging;

        LookupInstrumentAction(boolean performDefaultLogging) {
            this.performDefaultLogging = performDefaultLogging;
        }

        @Override
        public boolean test(LoggingContext ctx, TruffleLogger[] loggers) {
            TruffleLanguage.Env env = ctx.getEnv();
            InstrumentInfo instrumentInfo = env.getInstruments().get(ProxyInstrument.ID);
            env.lookup(instrumentInfo, ProxyInstrument.Initialize.class);
            return performDefaultLogging;
        }
    }
}
