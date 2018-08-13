/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

public class LoggingTest {

    @After
    public void tearDown() {
        AbstractLoggingLanguage.action = null;
    }

    @Test
    public void testDefaultLogging() {
        final TestHandler handler = new TestHandler();
        final Level defaultLevel = Level.INFO;
        try (Context ctx = Context.newBuilder().logHandler(handler).build()) {
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            // All levels from log1 language and logs >= defaultLevel from log2 language
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(null, null, Level.FINEST.toString())).logHandler(handler).build()) {
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
        try (Context ctx = Context.newBuilder().options(
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.b", Level.FINEST.toString())).logHandler(handler).build()) {
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "b.a.a", Level.FINEST.toString())).logHandler(handler).build()) {
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = Context.newBuilder().logHandler(handler).build()) {
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
        try (Context ctx1 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler1).build()) {
            try (Context ctx2 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, "a.a", Level.FINEST.toString())).logHandler(handler2).build()) {
                try (Context ctx3 = Context.newBuilder().logHandler(handler3).build()) {
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
        try (Context ctx1 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a", Level.FINER.toString())).logHandler(handler1).build()) {
            try (Context ctx2 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINE.toString())).logHandler(handler2).build()) {
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
        try (Context ctx1 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a", Level.FINE.toString())).logHandler(handler1).build()) {
            Context ctx2 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINER.toString())).logHandler(handler2).build();
            try (Context ctx3 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a.a", Level.FINER.toString())).logHandler(handler3).build()) {
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
        try (Context ctx1 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a", Level.FINE.toString())).logHandler(handler1).build()) {
            Context ctx2 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINER.toString())).logHandler(handler2).build();
            try (Context ctx3 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINER.toString())).logHandler(handler3).build()) {
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
        try (Context ctx1 = Context.newBuilder().logHandler(handler1).build()) {
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
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, Collection<TruffleLogger>>() {
            @Override
            public boolean test(final LoggingContext context, final Collection<TruffleLogger> loggers) {
                for (TruffleLogger logger : loggers) {
                    logger.log(Level.WARNING, "Parameters", Arrays.copyOf(expected, expected.length));
                }
                return false;
            }
        };
        final TestHandler handler1 = new TestHandler();
        try (Context ctx1 = Context.newBuilder().logHandler(handler1).build()) {
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
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, Collection<TruffleLogger>>() {
            @Override
            public boolean test(final LoggingContext context, final Collection<TruffleLogger> loggers) {
                for (TruffleLogger logger : loggers) {
                    logger.log(Level.WARNING, "Parameters", new LoggingLanguageObject("passed"));
                }
                return false;
            }
        };
        final TestHandler handler1 = new TestHandler();
        try (Context ctx1 = Context.newBuilder().logHandler(handler1).build()) {
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
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, Collection<TruffleLogger>>() {
            @Override
            public boolean test(final LoggingContext context, final Collection<TruffleLogger> loggers) {
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
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
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
    public void testPolyglotLogHandler() throws IOException {
        AbstractLoggingLanguage.action = new BiPredicate<LoggingContext, Collection<TruffleLogger>>() {
            @Override
            public boolean test(final LoggingContext context, final Collection<TruffleLogger> loggers) {
                TruffleLogger.getLogger(LoggingLanguageFirst.ID).warning(LoggingLanguageFirst.ID);
                TruffleLogger.getLogger(LoggingLanguageFirst.ID, "a").warning(LoggingLanguageFirst.ID + "::a");
                return false;
            }
        };
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context ctx = Context.newBuilder().out(out).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
        }
        out.close();
        final String output = new String(out.toByteArray());
        final Pattern p = Pattern.compile("\\[(.*)\\]\\sWARNING:\\s(.*)");
        for (String line : output.split("\n")) {
            final Matcher m = p.matcher(line);
            Assert.assertTrue(m.matches());
            final String loggerName = m.group(1);
            final String message = m.group(2);
            Assert.assertEquals(message, loggerName);
        }
    }

    @Test
    public void testGcedContext() {
        TestHandler handler = new TestHandler();
        Context gcedContext = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build();
        gcedContext.eval(LoggingLanguageFirst.ID, "");
        List<Map.Entry<Level, String>> expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
        Assert.assertEquals(expected, handler.getLog());
        Reference<Context> gcedContextRef = new WeakReference<>(gcedContext);
        gcedContext = null;
        assertGc("Cannot free context.", gcedContextRef);
        handler = new TestHandler();
        Context newContext = Context.newBuilder().logHandler(handler).build();
        newContext.eval(LoggingLanguageFirst.ID, "");
        expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.INFO, Collections.emptyMap()));
        Assert.assertEquals(expected, handler.getLog());
    }

    @Test
    public void testGcedContext2() {
        TestHandler gcedContextHandler = new TestHandler();
        Context gcedContext = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(gcedContextHandler).build();
        TestHandler contextHandler = new TestHandler();
        Context context = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINE.toString())).logHandler(contextHandler).build();
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
        assertGc("Cannot free context.", gcedContextRef);
        contextHandler.clear();
        context.eval(LoggingLanguageFirst.ID, "");
        expected = new ArrayList<>();
        expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINE, Collections.emptyMap()));
        Assert.assertEquals(expected, contextHandler.getLog());
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

    private static void assertGc(final String message, final Reference<?> ref) {
        int blockSize = 100_000;
        final List<byte[]> blocks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            if (ref.get() == null) {
                return;
            }
            try {
                System.gc();
            } catch (OutOfMemoryError oom) {
            }
            try {
                System.runFinalization();
            } catch (OutOfMemoryError oom) {
            }
            try {
                blocks.add(new byte[blockSize]);
                blockSize = (int) (blockSize * 1.3);
            } catch (OutOfMemoryError oom) {
                blockSize >>>= 1;
            }
            if (i % 10 == 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        Assert.fail(message);
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

    private static final class LoggingLanguageObject implements TruffleObject {
        final String stringValue;

        LoggingLanguageObject(final String stringValue) {
            this.stringValue = stringValue;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return null;
        }
    }

    public abstract static class AbstractLoggingLanguage extends TruffleLanguage<LoggingContext> {
        static final String[] LOGGER_NAMES = {"a", "a.a", "a.b", "a.a.a", "b", "b.a", "b.a.a.a"};
        static final Level[] LOGGER_LEVELS = {Level.FINEST, Level.FINER, Level.FINE, Level.INFO, Level.SEVERE, Level.WARNING};
        static BiPredicate<LoggingContext, Collection<TruffleLogger>> action;
        private final Collection<TruffleLogger> allLoggers;

        AbstractLoggingLanguage(final String id) {
            final Collection<TruffleLogger> loggers = new ArrayList<>(LOGGER_NAMES.length);
            for (String loggerName : LOGGER_NAMES) {
                loggers.add(TruffleLogger.getLogger(id, loggerName));
            }
            allLoggers = loggers;
        }

        @Override
        protected LoggingContext createContext(Env env) {
            return new LoggingContext(env);
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return object instanceof LoggingLanguageObject;
        }

        @Override
        protected String toString(LoggingContext context, Object value) {
            return ((LoggingLanguageObject) value).stringValue;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final RootNode root = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    boolean doDefaultLogging = true;
                    if (action != null) {
                        doDefaultLogging = action.test(getContextReference().get(), allLoggers);
                    }
                    if (doDefaultLogging) {
                        doLog();
                    }
                    return getContextReference().get().getEnv().asGuestValue(null);
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
}
