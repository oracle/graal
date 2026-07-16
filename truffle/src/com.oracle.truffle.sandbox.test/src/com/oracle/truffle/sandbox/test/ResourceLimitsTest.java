/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sandbox.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@SuppressWarnings("hiding")
@RunWith(Theories.class)
public class ResourceLimitsTest extends AbstractPolyglotTest {

    @Rule public TestName testNameRule = new TestName();

    @DataPoints public static final boolean[] useVirtualThreads = new boolean[]{false, true};

    @Before
    public void before() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
    }

    @After
    public void tearDown() {
        // No context should be entered at the end of the test.
        assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    static Context.Builder maxCPUTime(Context.Builder builder, String limit, String check) {
        builder.allowExperimentalOptions(true);
        builder.option("sandbox.MaxCPUTime", limit);
        if (check != null) {
            builder.option("sandbox.MaxCPUTimeCheckInterval", check);
        }
        return builder;
    }

    static Context.Builder maxStackFrames(Context.Builder builder, int limit) {
        builder.allowExperimentalOptions(true);
        builder.option("sandbox.MaxStackFrames", String.valueOf(limit));
        return builder;
    }

    static Source statements(int count) {
        return statements(count, ResourceLimitsTest.class.getSimpleName(), false);
    }

    static Source statements(int count, String name, boolean internal) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, "LOOP(" + (count == Integer.MAX_VALUE ? "infinity" : count) + ", STATEMENT)", name).internal(internal).buildLiteral();
    }

    private static void assertStatementCountLimit(Context c, PolyglotException e, int limit) {
        if (!e.isCancelled()) {
            throw e;
        }
        String expectedMessage = "Maximum statement limit of " + limit + " exceeded. Statements executed " + (limit + 1) + ".";
        assertTrue(e.isResourceExhausted());
        assertEquals(expectedMessage, e.getMessage());
        assertEquals("STATEMENT", e.getSourceLocation().getCharacters());
        try {
            c.eval(InstrumentationTestLanguage.ID, "EXPRESSION");
            fail();
        } catch (PolyglotException ex) {
            assertTrue(e.isCancelled());
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    static Context.Builder maxStatements(Context.Builder builder, String limit, boolean includeInternal) {
        builder.allowExperimentalOptions(true);
        builder.option("sandbox.MaxStatements", limit);
        builder.option("sandbox.MaxStatementsIncludeInternal", String.valueOf(includeInternal));
        return builder;
    }

    static void forceMultiThreading(ExecutorService executorService, Context c) throws InterruptedException, ExecutionException {
        c.enter();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                c.enter();
                c.leave();
            }
        }).get();
        c.leave();
    }

    static ByteArrayOutputStream createLogHandler(Context.Builder builder) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        builder.logHandler(byteArrayOutputStream);
        return byteArrayOutputStream;
    }

    static Context.Builder disableLogging(Context.Builder builder) {
        builder.option("log.level", "OFF");
        return builder;
    }

    static Context.Builder traceLimits(Context.Builder builder) {
        builder.allowExperimentalOptions(true);
        builder.option("sandbox.TraceLimits", "true");
        return builder;
    }

    @Test
    public void testStatementLimit() {
        testStatementLimit(false, false);
    }

    @Test
    public void testStatementLimitExplicitEnter() {
        testStatementLimit(true, false);
    }

    @Test
    public void testStatementLimitWithTracing() {
        testStatementLimit(false, true);
    }

    private static void testStatementLimit(boolean explicitEnter, boolean useTracing) {
        Context.Builder builder = Context.newBuilder();
        if (useTracing) {
            createLogHandler(builder);
            traceLimits(builder);
        }
        maxStatements(builder, "50", false);

        try (Context context = builder.build()) {
            if (explicitEnter) {
                context.enter();
            }
            context.eval(statements(50));
            try {
                context.eval(statements(1));
                fail();
            } catch (PolyglotException e) {
                assertStatementCountLimit(context, e, 50);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testStatementLimitFilter() {
        Source internalSource = statements(10, "internalSource", true);
        Source publicSource = statements(10, "publicSource", false);
        Context.Builder builder = Context.newBuilder();
        maxStatements(builder, "50", false);

        try (Context context = builder.build()) {
            for (int i = 0; i < 5; i++) {
                context.eval(publicSource);
                context.eval(internalSource);
            }
            try {
                for (int i = 0; i < 10; i++) {
                    context.eval(internalSource);
                }
                context.eval(InstrumentationTestLanguage.ID, "LOOP(infinity, STATEMENT)");
                fail();
            } catch (PolyglotException e) {
                assertStatementCountLimit(context, e, 50);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testOutputLimit() {
        testOutputLimit(StreamKind.OUT, false);
    }

    @Test
    public void testOutputLimitWithTracing() {
        testOutputLimit(StreamKind.OUT, true);
    }

    @Test
    public void testErrorLimit() {
        testOutputLimit(StreamKind.ERR, false);
    }

    @Test
    public void testErrorLimitWithTracing() {
        testOutputLimit(StreamKind.ERR, true);
    }

    private static void testOutputLimit(StreamKind kind, boolean useTracing) {
        Context.Builder builder = Context.newBuilder();
        builder.out(OutputStream.nullOutputStream());
        builder.err(OutputStream.nullOutputStream());
        if (useTracing) {
            createLogHandler(builder);
            traceLimits(builder);
        }
        maxStreamSize(builder, kind, 100);
        try (Context context = builder.build()) {
            context.eval(prints(kind, 25, "test"));
            try {
                context.eval(prints(kind, 1, "f"));
                fail();
            } catch (PolyglotException e) {
                assertStreamSizeLimit(context, kind, e, 100);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testOutputLimitSharedEngine() {
        testOutputLimitSharedEngine(StreamKind.OUT);
    }

    @Test
    public void testErrorLimitSharedEngine() {
        testOutputLimitSharedEngine(StreamKind.ERR);
    }

    private static void testOutputLimitSharedEngine(StreamKind kind) {
        Engine.Builder engineBuilder = Engine.newBuilder();
        engineBuilder.out(OutputStream.nullOutputStream());
        engineBuilder.err(OutputStream.nullOutputStream());
        try (Engine engine = engineBuilder.build()) {
            Context.Builder context1Builder = Context.newBuilder().engine(engine);
            maxStreamSize(context1Builder, kind, 40);
            Context.Builder context2Builder = Context.newBuilder().engine(engine);
            maxStreamSize(context2Builder, kind, 80);
            try (Context context1 = context1Builder.build(); Context context2 = context2Builder.build()) {
                context1.eval(prints(kind, 5, "test"));
                context2.eval(prints(kind, 15, "test"));
                context1.eval(prints(kind, 5, "test"));
                try {
                    context1.eval(prints(kind, 1, "f"));
                    fail();
                } catch (PolyglotException e) {
                    assertStreamSizeLimit(context1, kind, e, 40);
                }
                context2.eval(prints(kind, 5, "test"));
                try {
                    context2.eval(prints(kind, 1, "f"));
                    fail();
                } catch (PolyglotException e) {
                    assertStreamSizeLimit(context2, kind, e, 80);
                }

            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testOutputLimitMultiThreadedVirtualThreads() throws InterruptedException {
        testOutputLimitMultiThreaded(true, StreamKind.OUT);
    }

    @Test
    public void testOutputLimitMultiThreadedPlatformThreads() throws InterruptedException {
        testOutputLimitMultiThreaded(false, StreamKind.OUT);
    }

    @Test
    public void testErrorLimitMultiThreadedVirtualThreads() throws InterruptedException {
        testOutputLimitMultiThreaded(true, StreamKind.ERR);
    }

    @Test
    public void testErrorLimitMultiThreadedPlatformThreads() throws InterruptedException {
        testOutputLimitMultiThreaded(false, StreamKind.ERR);
    }

    private static void testOutputLimitMultiThreaded(boolean vthreads, StreamKind kind) throws InterruptedException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        Context.Builder builder = Context.newBuilder();
        builder.out(OutputStream.nullOutputStream());
        builder.err(OutputStream.nullOutputStream());
        String text = "test";
        int size = 1_000 * text.length();
        AtomicInteger availableAtomic = new AtomicInteger(size);
        maxStreamSize(builder, kind, size);
        Phaser phaser = new Phaser(5);
        try (Context context = builder.build()) {
            Runnable work = () -> {
                try {
                    for (int available = availableAtomic.addAndGet(-text.length()); available >= 0; available = availableAtomic.addAndGet(-text.length())) {
                        context.eval(prints(kind, 1, text));
                        phaser.arriveAndAwaitAdvance();
                    }
                } finally {
                    phaser.arriveAndDeregister();
                }
                try {
                    context.eval(prints(kind, 1, "f"));
                    fail();
                } catch (PolyglotException e) {
                    // We cannot use assertStreamSizeLimit because it expects the written bytes to
                    // be of size + 1. For multiple threads bytes written may be <size + 1, size +
                    // number_of_threads>
                    assertTrue(e.isCancelled());
                    assertTrue(e.isResourceExhausted());
                    String expectedMessage = String.format("Maximum %s size of %d exceeded.", kind.errorMessageName(), size);
                    assertTrue(e.getMessage().contains(expectedMessage));
                }
            };
            ExecutorService executor = threadPool(4, vthreads);
            try {
                for (int i = 0; i < 4; i++) {
                    executor.submit(work);
                }
                work.run();
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testOutputLimitReset() {
        testOutputLimitReset(StreamKind.OUT);
    }

    @Test
    public void testErrorLimitReset() {
        testOutputLimitReset(StreamKind.ERR);
    }

    private static void testOutputLimitReset(StreamKind kind) {
        Context.Builder builder = Context.newBuilder();
        builder.out(OutputStream.nullOutputStream());
        builder.err(OutputStream.nullOutputStream());
        maxStreamSize(builder, kind, 100);
        try (Context context = builder.build()) {
            context.eval(prints(kind, 25, "test"));
            context.resetLimits();
            context.eval(prints(kind, 10, "test"));
            context.resetLimits();
            context.eval(prints(kind, 25, "test"));
            try {
                context.eval(prints(kind, 1, "f"));
                fail();
            } catch (PolyglotException e) {
                assertStreamSizeLimit(context, kind, e, 100);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    private static Context.Builder maxStreamSize(Context.Builder builder, StreamKind kind, long limit) {
        return builder.option(kind.optionName(), String.format("%dB", limit));
    }

    private static Source prints(StreamKind kind, int count, String text) {
        return Source.newBuilder(InstrumentationTestLanguage.ID,
                        String.format("LOOP(%d, STATEMENT(PRINT(%s,CONSTANT(\"%s\"))))", count, kind, text),
                        ResourceLimitsTest.class.getSimpleName()).buildLiteral();
    }

    private static void assertStreamSizeLimit(Context c, StreamKind kind, PolyglotException e, long limit) {
        assertTrue(e.isCancelled());
        String expectedMessage = String.format("Maximum %s size of %d exceeded. Bytes written %d.", kind.errorMessageName(), limit, limit + 1);
        assertTrue(e.isResourceExhausted());
        assertEquals(expectedMessage, e.getMessage());
        try {
            c.eval(InstrumentationTestLanguage.ID, String.format("PRINT(%s,CONSTANT(\"%s\"))", kind, "t"));
            fail();
        } catch (PolyglotException ex) {
            assertTrue(e.isCancelled());
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private enum StreamKind {
        OUT("sandbox.MaxOutputStreamSize", "output stream"),
        ERR("sandbox.MaxErrorStreamSize", "error stream");

        private final String optionName;
        private final String errorMessageName;

        StreamKind(String optionName, String errorMessageName) {
            this.optionName = optionName;
            this.errorMessageName = errorMessageName;
        }

        String optionName() {
            return optionName;
        }

        String errorMessageName() {
            return errorMessageName;
        }
    }

    @Test
    public void testLimitTracingOutput() {
        int numberOfStatements = 100;
        Context.Builder builder = Context.newBuilder();
        builder.out(OutputStream.nullOutputStream());
        builder.err(OutputStream.nullOutputStream());
        ByteArrayOutputStream byteArrayOutputStream = createLogHandler(builder);
        traceLimits(builder);
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID,
                        "BLOCK(" +
                                        "LOOP(" + numberOfStatements / 2 + ", STATEMENT(PRINT(OUT, CONSTANT(\"output\"))))," +
                                        "LOOP(" + numberOfStatements / 2 + ", STATEMENT(PRINT(ERR, CONSTANT(\"error\")))))",
                        ResourceLimitsTest.class.getSimpleName()).buildLiteral();
        try (Context context = builder.build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            context.eval(source);
        }
        StringReader traceReader = new StringReader(byteArrayOutputStream.toString());
        BufferedReader bufferedReader = new BufferedReader(traceReader);
        String line;
        Set<ResourceKind> resources = new HashSet<>();
        Collections.addAll(resources, ResourceKind.values());
        Set<ResourceKind> observedResources = new HashSet<>();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                ResourceKind resource = ResourceKind.find(line);
                if (resource != null) {
                    String value = resource.parseValue(line);
                    switch (resource) {
                        case HEAP -> assertMemory(value, 0, 1023);
                        case CPU -> {
                            if (value.endsWith("ms")) {
                                String time = value.substring(0, value.length() - 2);
                                long milliseconds = Long.valueOf(time);
                                // It should not take 100 seconds to evaluate the above program.
                                if (milliseconds < 0 || milliseconds > 100000) {
                                    fail("Incorrect time unit recorded: " + milliseconds);
                                }
                            } else {
                                fail("Invalid time unit:" + value);
                            }
                        }
                        case STATEMENTS -> {
                            long statements = Long.valueOf(value);
                            if (statements != numberOfStatements) {
                                fail("Incorrect number of statements recorded: " + value);
                            }
                        }
                        case THREADS -> {
                            long threads = Long.valueOf(value);
                            if (threads != 1) {
                                fail("Incorrect number of threads recorded: " + value);
                            }
                        }
                        case STACKFRAMES -> {
                            long stackframes = Long.valueOf(value);
                            if (stackframes != 1) {
                                fail("Incorrect number of stackframes recorded: " + value);
                            }
                        }
                        case ASTDEPTH -> {
                            long astDepth = Long.valueOf(value);
                            if (astDepth != 7) {
                                fail("Incorrect ast depth recorded: " + value);
                            }
                        }
                        case STDOUT -> assertMemory(value, 300, 300);
                        case STDERR -> assertMemory(value, 250, 250);
                        default -> fail("Invalid resource recorded:" + value);
                    }
                    observedResources.add(resource);
                }
            }
            if (!resources.equals(observedResources)) {
                Set<ResourceKind> missingResources = new HashSet<>();
                for (ResourceKind resource : resources) {
                    if (!observedResources.contains(resource)) {
                        missingResources.add(resource);
                    }
                }
                String missingResourceNames = missingResources.toString();
                fail("The following resources are missing from the trace: " + missingResourceNames);
            }
        } catch (IOException ex) {
            fail("An IO Exception occurred while reading the log");
        }
    }

    enum ResourceKind {
        HEAP("Maximum Heap Memory:(.*)"),
        CPU("CPU Time:(.*)"),
        STATEMENTS("Number of statements executed:(.*)"),
        STACKFRAMES("Maximum active stack frames:(.*)"),
        THREADS("Maximum number of threads:(.*)"),
        ASTDEPTH("Maximum AST Depth:(.*)"),
        STDOUT("Size written to standard output:(.*)"),
        STDERR("Size written to standard error output:(.*)");

        private final Pattern pattern;

        ResourceKind(String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        String parseValue(String line) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1).trim();
            } else {
                throw new IllegalArgumentException("Not matched line.");
            }
        }

        static ResourceKind find(String line) {
            for (ResourceKind resource : values()) {
                if (resource.pattern.matcher(line).matches()) {
                    return resource;
                }
            }
            return null;
        }
    }

    private static void assertMemory(String value, long min, long max) {
        int length = value.length();
        int endOfValue = length;
        if (value.endsWith("KB") || value.endsWith("MB")) {
            endOfValue = length - 2;
        } else if (value.endsWith("B")) {
            endOfValue = length - 1;
        } else {
            fail("Invalid memory unit: " + value);
        }
        String allocated = value.substring(0, endOfValue);
        long allocatedUnits = Long.valueOf(allocated);
        if (allocatedUnits < min || allocatedUnits > max) {
            fail("Incorrect amount of memory recorded: " + allocated);
        }
    }

    @Test
    public void testStatementInvalidLimitFilter() {
        Context.Builder builder1 = Context.newBuilder();
        maxStatements(builder1, "50", true);

        Context.Builder builder2 = Context.newBuilder();
        maxStatements(builder2, "51", false);

        builder1.build().close();
        builder2.build().close();

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            Context.Builder builder = builder2.engine(engine);
            try {
                builder.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid max statements filter configuration detected. " +
                                "All contexts of the same engine need to use the same option value for 'sandbox.MaxStatementsIncludeInternal'. " +
                                "To resolve this use the same option value for 'sandbox.MaxStatementsIncludeInternal'.",
                                e.getMessage());
            }
        }
    }

    @Test
    public void testStatementLimitDifferentPerContext() {
        Context.Builder builder1 = Context.newBuilder();
        maxStatements(builder1, "1", false);

        Context.Builder builder2 = Context.newBuilder();
        maxStatements(builder2, "2", false);

        Source source = statements(1);
        try (Engine engine = Engine.create()) {

            for (int i = 0; i < 10; i++) {
                // test no limit
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    context.eval(source);
                    context.eval(source);
                    context.eval(source);
                }

                // test with limit
                try (Context context = builder1.engine(engine).build()) {
                    context.eval(source);
                    try {
                        context.eval(InstrumentationTestLanguage.ID, "LOOP(1, STATEMENT)");
                        fail();
                    } catch (PolyglotException ex) {
                        assertStatementCountLimit(context, ex, 1);
                    }
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }

                // test with different limit
                try (Context context = builder2.engine(engine).build()) {
                    context.eval(source);
                    context.eval(source);
                    try {
                        context.eval(InstrumentationTestLanguage.ID, "LOOP(1, STATEMENT)");
                        fail();
                    } catch (PolyglotException ex) {
                        assertStatementCountLimit(context, ex, 2);
                    }
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            }
        }
    }

    @Test
    public void testStatementLimitDifferentPerContextParallelVirtualThreads() throws InterruptedException, ExecutionException {
        testStatementLimitDifferentPerContextParallel(true);
    }

    @Test
    public void testStatementLimitDifferentPerContextParallelPlatformThreads() throws InterruptedException, ExecutionException {
        testStatementLimitDifferentPerContextParallel(false);
    }

    private static void testStatementLimitDifferentPerContextParallel(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        Context.Builder builder1 = Context.newBuilder();
        maxStatements(builder1, "1", false);

        Context.Builder builder2 = Context.newBuilder();
        maxStatements(builder2, "2", false);

        Source source = statements(1);
        try (Engine engine = Engine.create()) {

            ExecutorService executorService = threadPool(20, vthreads);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                futures.add(executorService.submit(() -> {

                    // test no limit
                    try (Context context = Context.newBuilder().engine(engine).build()) {
                        context.eval(source);
                        context.eval(source);
                        context.eval(source);
                    }

                    // test with limit
                    try (Context context = builder1.engine(engine).build()) {
                        context.eval(source);
                        try {
                            context.eval(InstrumentationTestLanguage.ID, "LOOP(1, STATEMENT)");
                            fail();
                        } catch (PolyglotException ex) {
                            assertStatementCountLimit(context, ex, 1);
                        }
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }

                    // test with different limit
                    try (Context context = builder2.engine(engine).build()) {
                        context.eval(source);
                        context.eval(source);
                        try {
                            context.eval(InstrumentationTestLanguage.ID, "LOOP(1, STATEMENT)");
                            fail();
                        } catch (PolyglotException ex) {
                            assertStatementCountLimit(context, ex, 2);
                        }
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }

            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testStatementLimitNoLimitCombination() {
        try (Engine engine = Engine.create()) {
            try (Context longLivedContext = Context.newBuilder().engine(engine).build()) {
                Context.Builder builder = Context.newBuilder().engine(engine);
                maxStatements(builder, "50", true);

                try (Context context = builder.build()) {
                    context.eval(statements(50));
                    try {
                        context.eval(statements(1));
                        fail();
                    } catch (PolyglotException e) {
                        assertStatementCountLimit(context, e, 50);
                    }
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }

                Context.Builder builder2 = Context.newBuilder().engine(engine);
                // Any other limit than maxStatements that won't be exceeded
                maxStackFrames(builder2, 1000);
                maxThreads(builder2, 1000);
                try (Context context = builder2.build()) {
                    context.eval(statements(100));
                }

                try (Context context = Context.newBuilder().engine(engine).build()) {
                    context.eval(statements(99));
                }

                // Verify that later enabled limits don't influence previously created context
                longLivedContext.eval(statements(98));
            }
        }
    }

    @Test
    public void testSharedContextStatementLimitSynchronous() {
        Context.Builder builder = Context.newBuilder();
        maxStatements(builder, "50", false);

        try (Engine engine = Engine.create()) {

            for (int i = 0; i < 10; i++) {
                try (Context c = builder.engine(engine).build()) {
                    c.eval(statements(50));
                    try {
                        c.eval(statements(1));
                        fail();
                    } catch (PolyglotException e) {
                        assertStatementCountLimit(c, e, 50);
                    }
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            }
        }
    }

    @Test
    public void testSharedContextStatementLimitParallelVirtualThreads() throws ExecutionException, InterruptedException {
        testSharedContextStatementLimitParallel(true);
    }

    @Test
    public void testSharedContextStatementLimitParallelPlatformThreads() throws ExecutionException, InterruptedException {
        testSharedContextStatementLimitParallel(false);
    }

    private static void testSharedContextStatementLimitParallel(boolean vthreads) throws ExecutionException, InterruptedException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        try (Engine engine = Engine.create()) {
            final int limit = 50;
            Context.Builder builder = Context.newBuilder();
            maxStatements(builder, "50", false);

            ExecutorService executorService = threadPool(20, vthreads);
            List<Future<?>> futures = new ArrayList<>();
            final int tasks = 1000;
            for (int i = 0; i < tasks; i++) {
                futures.add(executorService.submit(() -> {
                    try (Context c = builder.engine(engine).build()) {
                        c.eval(statements(limit));
                        try {
                            c.eval(statements(1));
                            fail();
                        } catch (PolyglotException e) {
                            assertStatementCountLimit(c, e, limit);
                        }
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testParallelContextStatementLimitVirtualThreads() throws InterruptedException, ExecutionException {
        testParallelContextStatementLimit(true);
    }

    @Test
    public void testParallelContextStatementLimitPlatformThreads() throws InterruptedException, ExecutionException {
        testParallelContextStatementLimit(false);
    }

    private static void testParallelContextStatementLimit(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        final int limit = 10000;
        Context.Builder builder = Context.newBuilder();
        maxStatements(builder, "10000", false);

        ExecutorService executorService = threadPool(20, vthreads);
        List<Future<?>> futures = new ArrayList<>();
        try (Context c = builder.build()) {
            forceMultiThreading(executorService, c);
            for (int i = 0; i < limit; i++) {
                futures.add(executorService.submit(() -> {
                    c.eval(statements(1));
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            try {
                c.eval(statements(1));
                fail();
            } catch (PolyglotException e) {
                assertStatementCountLimit(c, e, limit);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    @Test
    public void testParallelContextStatementLimit2VirtualThreads() throws InterruptedException, ExecutionException {
        testParallelContextStatementLimit2(true);
    }

    @Test
    public void testParallelContextStatementLimit2PlatformThreads() throws InterruptedException, ExecutionException {
        testParallelContextStatementLimit2(false);
    }

    private static void testParallelContextStatementLimit2(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        final int limit = 10000000;
        Context.Builder builder = Context.newBuilder();
        maxStatements(builder, String.valueOf(limit), false);
        // Tracing for issue GR-39450: possible deopt loop in OSR compiled block. The tracing is
        // enabled in the gate by setting TRACE_COMPILATION environment property to true.
        if (Boolean.parseBoolean(System.getenv("TRACE_COMPILATION"))) {
            builder.option("engine.TraceCompilation", "true");
            builder.option("log.file", "testParallelContextStatementLimit2_compilation.log");
        }
        ExecutorService executorService = threadPool(20, vthreads);
        List<Future<?>> futures = new ArrayList<>();
        try (Context c = builder.build()) {
            for (int i = 0; i < 20; i++) {
                futures.add(executorService.submit(() -> {
                    try {
                        c.eval(statements(Integer.MAX_VALUE));
                        fail();
                    } catch (PolyglotException e) {
                        if (!e.isCancelled() || !e.isResourceExhausted()) {
                            throw e;
                        }
                    }

                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    @Test
    public void testParallelMultiContextStatementLimitVirtualThreads() throws InterruptedException, ExecutionException {
        testParallelMultiContextStatementLimit(true);
    }

    @Test
    public void testParallelMultiContextStatementLimitPlatformThreads() throws InterruptedException, ExecutionException {
        testParallelMultiContextStatementLimit(false);
    }

    private static void testParallelMultiContextStatementLimit(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        try (Engine engine = Engine.create()) {
            final int executions = 100;
            final int contexts = 100;
            final int threads = 20;

            Context.Builder builder = Context.newBuilder();
            maxStatements(builder, String.valueOf(executions), false);

            ExecutorService executorService = threadPool(threads, vthreads);
            List<Future<?>> testFutures = new ArrayList<>();

            for (int contextIndex = 0; contextIndex < contexts; contextIndex++) {
                Context c = builder.engine(engine).build();
                forceMultiThreading(executorService, c);

                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < executions; i++) {
                    futures.add(executorService.submit(() -> {
                        c.eval(statements(1));
                    }));
                }
                testFutures.add(executorService.submit(() -> {
                    for (Future<?> future : futures) {
                        try {
                            future.get();
                        } catch (InterruptedException | ExecutionException e1) {
                            if (e1 instanceof ExecutionException) {
                                if (e1.getCause() instanceof PolyglotException) {
                                    PolyglotException e = (PolyglotException) e1.getCause();
                                    if (e.isCancelled()) {
                                        throw new AssertionError("Context was cancelled too early.", e);
                                    }
                                }
                            }
                            throw new RuntimeException(e1);
                        }
                    }
                    try {
                        c.eval(statements(1));
                        fail();
                    } catch (PolyglotException e) {
                        assertStatementCountLimit(c, e, executions);
                    }
                    try {
                        c.close();
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }
            for (Future<?> future : testFutures) {
                future.get();
            }

            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testParallelMultiContextStatementResetLimitVirtualThreads() throws InterruptedException, ExecutionException {
        testParallelMultiContextStatementResetLimit(true);
    }

    @Test
    public void testParallelMultiContextStatementResetLimitPlatformThreads() throws InterruptedException, ExecutionException {
        testParallelMultiContextStatementResetLimit(false);
    }

    private static void testParallelMultiContextStatementResetLimit(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        try (Engine engine = Engine.create()) {
            final int executions = 100;
            final int contexts = 100;
            final int threads = 20;
            Context.Builder builder = Context.newBuilder();
            maxStatements(builder, String.valueOf(executions), false);
            ExecutorService executorService = threadPool(threads, vthreads);
            List<Future<?>> testFutures = new ArrayList<>();
            for (int contextIndex = 0; contextIndex < contexts; contextIndex++) {
                Context c = builder.engine(engine).build();
                forceMultiThreading(executorService, c);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < executions; i++) {
                    futures.add(executorService.submit(() -> {
                        c.eval(statements(1));
                    }));
                }
                Future<?> prev = executorService.submit(() -> {
                    for (Future<?> future : futures) {
                        try {
                            future.get();
                        } catch (InterruptedException | ExecutionException e1) {
                            throw new RuntimeException(e1);
                        }
                    }
                    c.resetLimits();
                });

                testFutures.add(executorService.submit(() -> {
                    try {
                        prev.get();
                    } catch (InterruptedException | ExecutionException e1) {
                        throw new RuntimeException(e1);
                    }
                    c.eval(statements(executions));
                    try {
                        c.eval(statements(1));
                        fail();
                    } catch (PolyglotException e) {
                        assertStatementCountLimit(c, e, executions);
                    }
                    try {
                        c.close();
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }
            for (Future<?> future : testFutures) {
                future.get();
            }

            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    static Context.Builder maxThreads(Context.Builder builder, int limit) {
        builder.allowExperimentalOptions(true);
        builder.option("sandbox.MaxThreads", String.valueOf(limit));
        return builder;
    }

    static Context.Builder maxASTDepth(Context.Builder builder, int limit) {
        builder.allowExperimentalOptions(true);
        builder.option("sandbox.MaxASTDepth", String.valueOf(limit));
        return builder;
    }

    static void testBoundContextTimeLimit(boolean traceLimits) {
        Context.Builder builder = Context.newBuilder();
        if (traceLimits) {
            disableLogging(builder);
            traceLimits(builder);
        }
        maxCPUTime(builder, "10ms", "1ms");
        try (Context context = builder.build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            try {
                evalStatements(context);
                fail();
            } catch (PolyglotException e) {
                assertTimeout(context, e);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testBoundContextTimeLimit() {
        testBoundContextTimeLimit(false);
    }

    @Test
    public void testBoundContextTimeLimitWithTracing() {
        testBoundContextTimeLimit(true);
    }

    @Test
    public void testBoundContextTimeLimitExit() {
        try (Engine engine = Engine.create()) {
            Context.Builder builder = Context.newBuilder().engine(engine);
            maxCPUTime(builder, "1000ms", "10ms");
            try (Context context = builder.build()) {
                context.initialize(InstrumentationTestLanguage.ID);
                context.eval(InstrumentationTestLanguage.ID, "EXIT(1)");
            }
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testSharedContextTimeLimitSynchronous() {
        Context.Builder builder = Context.newBuilder();
        maxCPUTime(builder, "3ms", "1ms");

        try (Engine engine = Engine.create()) {

            for (int i = 0; i < 10; i++) {
                try (Context c = builder.engine(engine).build()) {
                    try {
                        evalStatements(c);
                        fail();
                    } catch (PolyglotException e) {
                        assertTimeout(c, e);
                    }
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            }
        }
    }

    @Test
    public void testSharedContextTimeLimitParallelVirtualThreads() throws InterruptedException, ExecutionException {
        testSharedContextTimeLimitParallel(true);
    }

    @Test
    public void testSharedContextTimeLimitParallelPlatformThreads() throws InterruptedException, ExecutionException {
        testSharedContextTimeLimitParallel(false);
    }

    private static void testSharedContextTimeLimitParallel(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        Assume.assumeFalse(vthreads); // GR-53547
        Context.Builder builder = Context.newBuilder();
        maxCPUTime(builder, "5ms", "1ms");

        try (Engine engine = Engine.create()) {
            ExecutorService executorService = threadPool(5, vthreads);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(executorService.submit(() -> {
                    try (Context c = builder.engine(engine).build()) {
                        try {
                            evalStatements(c);
                            fail();
                        } catch (PolyglotException e) {
                            assertTimeout(c, e);
                        }
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }
            waitAllowInterrupted(futures);
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testSharedContextTimeLimitResetParallelVirtualThreads() throws InterruptedException, ExecutionException {
        testSharedContextTimeLimitResetParallel(true);
    }

    @Test
    public void testSharedContextTimeLimitResetParallelPlatformThreads() throws InterruptedException, ExecutionException {
        testSharedContextTimeLimitResetParallel(false);
    }

    private static void testSharedContextTimeLimitResetParallel(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        Assume.assumeFalse(vthreads); // GR-53547
        Context.Builder builder = Context.newBuilder();
        /*
         * The limit is set rather high because even the thread that does just Thread#sleep might
         * take unexpectedly high CPU time in extreme cases. So to make this test robust we use a
         * high buffer for error (800ms).
         */
        maxCPUTime(builder, "1000ms", "10ms");

        try (Engine engine = Engine.create()) {
            ExecutorService executorService = threadPool(10, vthreads);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(executorService.submit(() -> {
                    try (Context c = builder.engine(engine).build()) {
                        for (int j = 0; j < 10; j++) {
                            c.enter();
                            Thread.sleep(200);
                            c.leave();
                            c.resetLimits();
                        }
                        try {
                            evalStatements(c);
                            fail();
                        } catch (PolyglotException e) {
                            assertTimeout(c, e);
                        }
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }
            waitAllowInterrupted(futures);
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancelBeforeResourceExhaustedInfiniteCountOfFiniteLoopsVirtualThreads() throws InterruptedException, ExecutionException {
        testCancelBeforeResourceExhaustedInfiniteCountOfFiniteLoops(true);
    }

    @Test
    public void testCancelBeforeResourceExhaustedInfiniteCountOfFiniteLoopsPlatformThreads() throws InterruptedException, ExecutionException {
        testCancelBeforeResourceExhaustedInfiniteCountOfFiniteLoops(false);
    }

    private static void testCancelBeforeResourceExhaustedInfiniteCountOfFiniteLoops(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        Assume.assumeFalse(vthreads); // GR-53547
        try (Engine engn = Engine.create()) {
            Context.Builder builder = Context.newBuilder().engine(engn);
            maxCPUTime(builder, "1000ms", "10ms");
            ExecutorService executorService = threadPool(10, vthreads);
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch cancelLatch = new CountDownLatch(10);
            List<Context> contextList = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(executorService.submit(() -> {
                    try (Context c = builder.build()) {
                        contextList.add(c);
                        try {
                            for (int j = 0; j < 100; j++) {
                                c.eval(statements(1000));
                            }
                            cancelLatch.countDown();
                            while (true) {
                                c.eval(statements(1000));
                            }
                        } catch (PolyglotException e) {
                            if (!e.isCancelled()) {
                                throw e;
                            }
                        }
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    } finally {
                        /*
                         * In case an exception occurs before countDown in the try body, the
                         * following prevents a deadlock and then the exception is thrown from
                         * future.get(). In case the countDown is executed in the try body, the
                         * following is harmless because in that case it is executed when the
                         * cancelLatch.await() in the main thread already returned, or an exception
                         * is thrown from the infinite loop before cancelling, in which case this
                         * test will also fail.
                         */
                        cancelLatch.countDown();
                    }
                }));
            }
            cancelLatch.await();
            for (Context c : contextList) {
                c.close(true);
            }
            for (Future<?> future : futures) {
                future.get();
            }
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    static void evalStatements(Context c) {
        /*
         * We do multiple smaller eval statements in order to test the behavior when the resource
         * consumption limit was reached between statements.
         */
        c.eval(statements(10));
        c.eval(statements(500));
        while (true) {
            c.eval(statements(1000));
        }
    }

    private static void assertTimeout(Context c, PolyglotException e) {
        assertResourceExceeded(c, e, "Maximum CPU time limit of");
    }

    static void assertResourceExceeded(Context c, PolyglotException e, String messagePrefix) {
        if (!e.isCancelled()) {
            // not expected exception
            throw e;
        }
        if (!e.isResourceExhausted()) {
            ByteArrayOutputStream stackTraceStream;
            try (PrintWriter printWriter = new PrintWriter(stackTraceStream = new ByteArrayOutputStream())) {
                e.printStackTrace(printWriter);
                try {
                    Field implField = e.getClass().getDeclaredField("impl");
                    implField.setAccessible(true);
                    Object impl = implField.get(e);
                    Field exceptionField = impl.getClass().getDeclaredField("exception");
                    exceptionField.setAccessible(true);
                    Throwable originalException = (Throwable) exceptionField.get(impl);
                    printWriter.write("ORIGINAL EXCEPTION:\n");
                    originalException.printStackTrace(printWriter);
                } catch (NoSuchFieldException | IllegalAccessException re) {
                    throw new RuntimeException(re);
                }
                printWriter.flush();
                assertTrue(stackTraceStream.toString(), e.isResourceExhausted());
            }
        }
        assertTrue(e.toString(), e.isCancelled());
        assertTrue(e.getMessage(), e.getMessage().startsWith(messagePrefix));
        try {
            c.eval(InstrumentationTestLanguage.ID, "EXPRESSION");
            fail();
        } catch (PolyglotException ex) {
            assertTrue(e.isCancelled());
            assertTrue(e.getMessage(), e.getMessage().startsWith(messagePrefix));
        }
    }

    private static void waitAllowInterrupted(List<Future<?>> futures) throws ExecutionException {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
            }
        }
    }

    static Context.Builder newContextBuilder() {
        return Context.newBuilder().allowExperimentalOptions(true);
    }

    static Engine.Builder newEngineBuilder() {
        return Engine.newBuilder().allowExperimentalOptions(true);
    }

    @Test
    public void testMaxCPUTimeErrors() {
        assertTimeUnitOption("sandbox.MaxCPUTime", true);
        assertTimeUnitOption("sandbox.MaxCPUTimeCheckInterval", false);
    }

    static void assertTimeUnitOption(String optionName, boolean isMaxCPUTime) {
        // valid values
        newContextBuilder().option(optionName, "0ms").build().close();
        try {
            newContextBuilder().option(optionName, "1ms").build().close();
        } catch (PolyglotException pe) {
            if (!isMaxCPUTime || TruffleTestAssumptions.isWeakEncapsulation() || !pe.isResourceExhausted() || !pe.getMessage().startsWith("Maximum CPU time limit of 1ms exceeded.")) {
                /*
                 * Isolated context enters the preinitialized context to replay events, and so it
                 * consumes some CPU time which may lead to exceeding the very low CPU time limit.
                 */
                throw pe;
            }
        }
        newContextBuilder().option(optionName, "1s").build().close();
        newContextBuilder().option(optionName, "1m").build().close();
        newContextBuilder().option(optionName, "1h").build().close();
        /*
         * Limit checkers are not started if no context is created, so the very low 0ms and 1ms CPU
         * time limits cannot cause failure here.
         */
        newEngineBuilder().option(optionName, "0ms").build().close();
        newEngineBuilder().option(optionName, "1ms").build().close();
        newEngineBuilder().option(optionName, "1s").build().close();
        newEngineBuilder().option(optionName, "1m").build().close();
        newEngineBuilder().option(optionName, "1h").build().close();

        /*
         * Check is on the context level, so even if isMaxCPUTime=false, this does not fail.
         */
        newEngineBuilder().option(optionName, "1d").build().close();
        newEngineBuilder().option(optionName, Long.MAX_VALUE + "ms").build().close();

        try {
            newContextBuilder().option(optionName, "1d").build().close();
            if (!isMaxCPUTime) {
                fail();
            }
        } catch (PolyglotException e) {
            assertEquals("Invalid value of the option '" + optionName + "'. The value must be in the range [1ms, 1h].",
                            e.getMessage());
        }
        try {
            newContextBuilder().option(optionName, Long.MAX_VALUE + "ms").build().close();
            if (!isMaxCPUTime) {
                fail();
            }
        } catch (PolyglotException e) {
            assertEquals("Invalid value of the option '" + optionName + "'. The value must be in the range [1ms, 1h].",
                            e.getMessage());
        }

        // missing unit
        assertFails(() -> newContextBuilder().option(optionName, String.valueOf(42)).build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, String.valueOf(42)).build(), IllegalArgumentException.class);
        // negative integer
        assertFails(() -> newContextBuilder().option(optionName, String.valueOf(-42)).build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, String.valueOf(-42)).build(), IllegalArgumentException.class);
        // negative integer with unit
        assertFails(() -> newContextBuilder().option(optionName, "-42ms").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "-42ms").build(), IllegalArgumentException.class);
        // wrong unit
        assertFails(() -> newContextBuilder().option(optionName, "42a").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "42a").build(), IllegalArgumentException.class);
        // invalid precision number
        assertFails(() -> newContextBuilder().option(optionName, "42.1ms").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "42.1ms").build(), IllegalArgumentException.class);
        // long overflow
        String tooLargeDuration = "9223372036854776s";
        String expectedMessage = "Invalid duration '" + tooLargeDuration +
                        "' specified. The duration exceeds Long.MAX_VALUE milliseconds, which is already roughly 292 million years.";
        assertFails(() -> newContextBuilder().option(optionName, tooLargeDuration).build(), IllegalArgumentException.class, expectedMessage);
        assertFails(() -> newEngineBuilder().option(optionName, tooLargeDuration).build(), IllegalArgumentException.class, expectedMessage);
        String tooLargeMillis = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE) + "ms";
        expectedMessage = "Invalid duration '" + tooLargeMillis +
                        "' specified. The duration exceeds Long.MAX_VALUE milliseconds, which is already roughly 292 million years.";
        assertFails(() -> newContextBuilder().option(optionName, tooLargeMillis).build(), IllegalArgumentException.class, expectedMessage);
        assertFails(() -> newEngineBuilder().option(optionName, tooLargeMillis).build(), IllegalArgumentException.class, expectedMessage);
    }

    @Test
    public void testMaxStatementsErrors() {
        String optionName = "sandbox.MaxStatements";
        // valid values
        newContextBuilder().option(optionName, "0").build().close();
        newContextBuilder().option(optionName, "42").build().close();
        newContextBuilder().option(optionName, "-1").build().close();
        newContextBuilder().option(optionName, String.valueOf(Long.MAX_VALUE)).build().close();
        newEngineBuilder().option(optionName, "0").build().close();
        newEngineBuilder().option(optionName, "42").build().close();
        newEngineBuilder().option(optionName, "-1").build().close();
        newEngineBuilder().option(optionName, String.valueOf(Long.MAX_VALUE)).build().close();

        // invalid precision number
        assertFails(() -> newContextBuilder().option(optionName, "42.1").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "42.1").build(), IllegalArgumentException.class);
        // long overflow
        assertFails(() -> newContextBuilder().option(optionName, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(1)).toString()).build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(1)).toString()).build(), IllegalArgumentException.class);
    }

    @Test
    public void testMaxStatementsIncludeInternalErrors() {
        String optionName = "sandbox.MaxStatementsIncludeInternal";

        // valid values
        newContextBuilder().option(optionName, "true").build().close();
        newContextBuilder().option(optionName, "false").build().close();

        // no weird formats of true
        assertFails(() -> newContextBuilder().option(optionName, "True").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "True").build(), IllegalArgumentException.class);
        assertFails(() -> newContextBuilder().option(optionName, "TRUE").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "TRUE").build(), IllegalArgumentException.class);
        // no numbers
        assertFails(() -> newContextBuilder().option(optionName, "42").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "42").build(), IllegalArgumentException.class);
        // no strings
        assertFails(() -> newContextBuilder().option(optionName, "foo").build(), IllegalArgumentException.class);
        assertFails(() -> newEngineBuilder().option(optionName, "bar").build(), IllegalArgumentException.class);
    }

    @Test
    public void testFrameLimit() {
        Source recursion = Source.create(InstrumentationTestLanguage.ID,
                        "ROOT(DEFINE(foo1, ROOT(CALL(foo2), STATEMENT))," +
                                        "DEFINE(foo2, ROOT(CALL(foo1), STATEMENT))," +
                                        "CALL_WITH(foo1, 7))");

        testFrameLimit(recursion, 5, false);
        testFrameLimit(recursion, 10, true);
        testFrameLimit(recursion, 20, false);
    }

    private static void testFrameLimit(Source source, int limit, boolean trace) {
        Context.Builder builder = Context.newBuilder();
        maxStackFrames(builder, limit);
        if (trace) {
            traceLimits(builder);
            disableLogging(builder);
        }
        try (Context context = builder.build()) {
            try {
                context.eval(source);
                fail();
            } catch (PolyglotException e) {
                assertTrue(e.isCancelled());
                String expectedMessage = "Maximum frame limit of " + limit + " exceeded. Frames on stack: " + (limit + 1) + ".";
                assertEquals(expectedMessage, e.getMessage());
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testParallelFrameLimitVirtualthreads() throws InterruptedException, ExecutionException {
        testParallelFrameLimit(true);
    }

    @Test
    public void testParallelFrameLimitPlatformThreads() throws InterruptedException, ExecutionException {
        testParallelFrameLimit(false);
    }

    private static void testParallelFrameLimit(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        final int limit = 2;
        Context.Builder builder = Context.newBuilder();
        maxStackFrames(builder, limit);
        ExecutorService executorService = threadPool(10, vthreads);

        Source source2 = Source.create(InstrumentationTestLanguage.ID,
                        "ROOT(DEFINE(foo1, ROOT(STATEMENT))," +
                                        "CALL_WITH(foo1, 7))");
        Source source3 = Source.create(InstrumentationTestLanguage.ID,
                        "ROOT(DEFINE(foo2, ROOT(STATEMENT))," +
                                        "DEFINE(foo3, ROOT(CALL(foo2), STATEMENT))," +
                                        "DEFINE(foo4, ROOT(CALL(foo3), STATEMENT))," +
                                        "CALL_WITH(foo4, 7))");

        List<Future<?>> futures = new ArrayList<>();
        Context c = builder.build();
        forceMultiThreading(executorService, c);
        for (int i = 0; i < 5; i++) {
            futures.add(executorService.submit(() -> {
                c.eval(source2);
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        try {
            c.eval(source3);
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isCancelled());
        }
        try {
            c.close();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    @Test
    public void testActiveThreadsLimitExceededVirtualThreads() throws InterruptedException, ExecutionException {
        testActiveThreadsLimitExceeded(true);
    }

    @Test
    public void testActiveThreadsLimitExceededPlatformThreads() throws InterruptedException, ExecutionException {
        testActiveThreadsLimitExceeded(false);
    }

    private static void testActiveThreadsLimitExceeded(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        ExecutorService executorService = threadPool(30, vthreads);

        testActiveThreadsLimitExceeded(executorService, 1, false);
        if (!vthreads) { // TraceLimits implies measuring CPUTime GR-53547
            testActiveThreadsLimitExceeded(executorService, 5, true);
        }
        testActiveThreadsLimitExceeded(executorService, 10, false);
        if (!vthreads) { // TraceLimits implies measuring CPUTime GR-53547
            testActiveThreadsLimitExceeded(executorService, 20, true);
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void testActiveThreadsLimitExceeded(ExecutorService executorService, int limit, boolean traceLimits) throws InterruptedException, ExecutionException {
        Context.Builder builder = Context.newBuilder();
        maxThreads(builder, limit);

        if (traceLimits) {
            disableLogging(builder);
            traceLimits(builder);
        }
        List<Future<?>> futures = new ArrayList<>();
        Context c = builder.build();
        CountDownLatch leaveSignal = new CountDownLatch(1);
        CountDownLatch testSignal = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    c.enter();
                    testSignal.countDown();
                    while (true) {
                        try {
                            leaveSignal.await();
                            break;
                        } catch (InterruptedException e) {
                        }
                    }
                    c.leave();
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw t;
                }
            }));
        }
        try {
            testSignal.await();
            c.enter();
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isCancelled());
            String expectedMessage = "Maximum number of active threads " + limit + " exceeded. Active threads: " + (limit + 1) + ".";
            assertEquals(expectedMessage, e.getMessage());
        }
        leaveSignal.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        try {
            c.close();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    /**
     * Reenter the same context multiple times on the same thread - should not count towards the
     * thread limit.
     */
    @Test
    public void testActiveThreadsLimitContextReenter() {
        int limit = 3;
        Context.Builder builder = Context.newBuilder();
        maxThreads(builder, limit);
        Context c = builder.build();
        for (int i = 0; i < limit + 1; i++) {
            try {
                c.enter();
            } catch (PolyglotException e) {
                fail();
            }
        }
        for (int i = 0; i < limit + 1; i++) {
            c.leave();
        }
        try {
            c.close();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testActiveThreadsLimitNotExceedableVirtualThreads() throws InterruptedException, ExecutionException {
        testActiveThreadsLimitNotExceedable(true);
    }

    @Test
    public void testActiveThreadsLimitNotExceedablePlatformThreads() throws InterruptedException, ExecutionException {
        testActiveThreadsLimitNotExceedable(false);
    }

    /**
     * Use less threads than the limit -> limit can't be exceeded.
     */
    private static void testActiveThreadsLimitNotExceedable(boolean vthreads) throws InterruptedException, ExecutionException {
        Assume.assumeFalse(vthreads && !canCreateVirtualThreads());
        int limit = 40;
        ExecutorService executorService = threadPool(limit - 10, vthreads);

        Context.Builder builder = Context.newBuilder();
        maxThreads(builder, limit);

        List<Future<?>> futures = new ArrayList<>();
        Context c = builder.build();
        CountDownLatch leaveSignal = new CountDownLatch(1);
        for (int i = 0; i < limit + 10; i++) {
            futures.add(executorService.submit(() -> {
                c.enter();
                while (true) {
                    try {
                        leaveSignal.await();
                        break;
                    } catch (InterruptedException e) {
                    }
                }
                c.leave();
            }));
        }
        leaveSignal.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        try {
            c.close();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void testAstDepthLimit() {
        Source source = Source.create(InstrumentationTestLanguage.ID,
                        "ROOT(DEFINE(foo1, ROOT(STATEMENT))," +
                                        "DEFINE(foo2, ROOT(CALL(foo1), STATEMENT))," +
                                        "CALL(foo1)," +
                                        "CALL_WITH(foo1, 43))");
        testAstDepthLimitExceeded(2, source);
        testAstDepthLimitExceeded(2, source);
        testAstDepthLimitOk(3, source);
        testAstDepthLimitOk(4, source);
    }

    @Test
    public void testValidAstDepthLimitCombination() {
        Context.Builder builder1 = Context.newBuilder();
        maxASTDepth(builder1, 50);

        Context.Builder builder2 = Context.newBuilder();
        maxASTDepth(builder2, 50);

        builder1.build().close();
        builder2.build().close();

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            builder2.engine(engine).build().close();
        }
    }

    @Test
    public void testInvalidAstDepthLimitCombination() {
        Context.Builder builder1 = Context.newBuilder();
        maxASTDepth(builder1, 50);

        Context.Builder builder2 = Context.newBuilder();
        maxASTDepth(builder2, 51);

        Context.Builder builder3 = Context.newBuilder();

        builder1.build().close();
        builder2.build().close();
        builder3.build().close();

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            Context.Builder builder = builder2.engine(engine);
            try {
                builder.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid AST depth limit configuration detected. " +
                                "All contexts of the same engine need to use the same option value for 'sandbox.MaxASTDepth'. " +
                                "To resolve this use the same option value for 'sandbox.MaxASTDepth'." +
                                "Even combining context with and without AST depth limit is not allowed.",
                                e.getMessage());
            }
        }

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            Context.Builder builder = builder3.engine(engine);
            try {
                builder.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid AST depth limit configuration detected. " +
                                "All contexts of the same engine need to use the same option value for 'sandbox.MaxASTDepth'. " +
                                "To resolve this use the same option value for 'sandbox.MaxASTDepth'." +
                                "Even combining context with and without AST depth limit is not allowed.",
                                e.getMessage());
            }
        }

        try (Engine engine = Engine.create()) {
            Context context = builder3.engine(engine).build();
            try {
                /*
                 * The no-limit context still has to live at this point for the invalid
                 * configuration to be detected. A situation where it does not live an it loaded an
                 * offending AST can only be detected at run-time which is tested by
                 * testLoadInvalidAstByPreviousNolimitContext.
                 */
                builder1.engine(engine).build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid AST depth limit configuration detected. " +
                                "All contexts of the same engine need to use the same option value for 'sandbox.MaxASTDepth'. " +
                                "To resolve this use the same option value for 'sandbox.MaxASTDepth'." +
                                "Even combining context with and without AST depth limit is not allowed.",
                                e.getMessage());
            } finally {
                context.close();
            }
        }
    }

    @Test
    public void testExecuteInvalidAstLoadedByPreviousNolimitContext() {
        Source source = Source.create(InstrumentationTestLanguage.ID,
                        "ROOT(DEFINE(foo1, ROOT(STATEMENT))," +
                                        "DEFINE(foo2, ROOT(CALL(foo1), STATEMENT))," +
                                        "CALL(foo1)," +
                                        "CALL_WITH(foo1, 43))");
        try (Engine engine = Engine.create()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                context.eval(source);
            }
            testAstDepthLimitExceeded(Context.newBuilder().engine(engine), 2, source);
        }
    }

    private static void testAstDepthLimitExceeded(int limit, Source source) {
        Context.Builder builder = Context.newBuilder();
        testAstDepthLimitExceeded(builder, limit, source);
    }

    private static void testAstDepthLimitExceeded(Context.Builder builder, int limit, Source source) {
        maxASTDepth(builder, limit);
        try (Context context = builder.build()) {
            try {
                context.eval(source);
                fail();
            } catch (PolyglotException e) {
                assertTrue(e.isCancelled());
                String expectedMessage = "AST depth limit of " + limit + " nodes exceeded. AST depth: 3.";
                assertEquals(expectedMessage, e.getMessage());
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    private static void testAstDepthLimitOk(int limit, Source source) {
        Context.Builder builder = Context.newBuilder();
        maxASTDepth(builder, limit);
        try (Context context = builder.build()) {
            try {
                context.eval(source);
            } catch (PolyglotException e) {
                fail();
            }
        }
    }

    @Test
    public void testEvalOnClosedContext() {
        /*
         * Used to test race condition, cancel of the context called between the eval statements,
         * where the context is not entered. Trying to enter the context after the cancel should
         * throw the resource-exhausted exception, not an IllegalStateException saying the context
         * is already closed.
         */
        Context.Builder builder = Context.newBuilder();
        maxCPUTime(builder, "50ms", "10ms");
        try (Engine engine = Engine.create()) {
            try (Context c = builder.engine(engine).build()) {
                while (true) {
                    c.eval(statements(1000));
                }
            } catch (PolyglotException e) {
                assertTrue(e.toString(), e.isResourceExhausted());
                assertTrue(e.toString(), e.isCancelled());
                assertTrue(e.getMessage(), e.getMessage().startsWith("Maximum CPU time limit of"));
            }
        }
    }
}
