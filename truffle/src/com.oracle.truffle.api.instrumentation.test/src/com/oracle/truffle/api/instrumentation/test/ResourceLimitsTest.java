/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimitEvent;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.test.ReflectionUtils;

public class ResourceLimitsTest {

    @Test
    public void testBoundContextTimeLimit() {
        ResourceLimits limits = cpuTimeLimit(ResourceLimits.newBuilder(), //
                        Duration.ofMillis(10), Duration.ofMillis(1)).//
                                        build();

        try (Context context = Context.newBuilder().resourceLimits(limits).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            try {
                evalStatements(context);
                fail();
            } catch (PolyglotException e) {
                assertTimeout(context, e);
            }
        }
    }

    @Test
    public void testSharedContextTimeLimitSynchronous() {
        ResourceLimits limits = cpuTimeLimit(ResourceLimits.newBuilder(), //
                        Duration.ofMillis(3), Duration.ofMillis(1)).//
                                        build();

        Engine engine = Engine.create();

        for (int i = 0; i < 10; i++) {
            try (Context c = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                try {
                    evalStatements(c);
                    fail();
                } catch (PolyglotException e) {
                    assertTimeout(c, e);
                }
            }
        }
    }

    @Test
    public void testSharedContextTimeLimitParallel() throws InterruptedException, ExecutionException {
        ResourceLimits limits = cpuTimeLimit(ResourceLimits.newBuilder(), //
                        Duration.ofMillis(5), Duration.ofMillis(1)).//
                                        onLimit((e) -> {
                                        }).build();

        Engine engine = Engine.create();
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(executorService.submit(() -> {
                try (Context c = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                    try {
                        evalStatements(c);
                        fail();
                    } catch (PolyglotException e) {
                        assertTimeout(c, e);
                    }
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        executorService.shutdownNow();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    private static ResourceLimits.Builder cpuTimeLimit(ResourceLimits.Builder builder, Duration timeLimit, Duration accuracy) {
        try {
            Method m = builder.getClass().getDeclaredMethod("cpuTimeLimit", Duration.class, Duration.class);
            ReflectionUtils.setAccessible(m, true);
            m.invoke(builder, timeLimit, accuracy);
        } catch (InvocationTargetException e) {
            throw (RuntimeException) e.getCause();
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException e) {
            throw new AssertionError(e);
        }
        return builder;
    }

    @Test
    public void testSharedContextTimeLimitResetParallel() throws InterruptedException, ExecutionException {
        ResourceLimits limits = cpuTimeLimit(ResourceLimits.newBuilder(), //
                        Duration.ofMillis(30), Duration.ofMillis(10)).//
                                        build();

        Engine engine = Engine.create();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(executorService.submit(() -> {
                try (Context c = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                    for (int j = 0; j < 2; j++) {
                        c.enter();
                        Thread.sleep(5);
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
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    private static void evalStatements(Context c) {
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
        if (!e.isCancelled()) {
            // not expected exception
            throw e;
        }
        assertTrue(e.isResourceExhausted());
        assertTrue(e.toString(), e.isCancelled());
        assertTrue(e.getMessage(), e.getMessage().startsWith("Time resource limit"));
        try {
            c.eval(InstrumentationTestLanguage.ID, "EXPRESSION");
            fail();
        } catch (PolyglotException ex) {
            assertTrue(e.isCancelled());
            assertTrue(e.getMessage(), e.getMessage().startsWith("Time resource limit"));
        }
    }

    @Test
    public void testStatementLimit() {
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(50, null).//
                        build();

        try (Context context = Context.newBuilder().resourceLimits(limits).build()) {
            context.eval(statements(50));
            try {
                context.eval(statements(1));
                fail();
            } catch (PolyglotException e) {
                assertStatementCountLimit(context, e, 50);
            }
        }
    }

    @Test
    public void testStatementLimitFilter() {
        Source internalSource = statements(10, "internalSource");
        Source publicSource = statements(10, "publicSource");
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(50, (s) -> internalSource.equals(s)).//
                        build();

        try (Context context = Context.newBuilder().resourceLimits(limits).build()) {
            for (int i = 0; i < 5; i++) {
                context.eval(publicSource);
                context.eval(internalSource);
            }
            try {
                for (int i = 0; i < 100; i++) {
                    context.eval(internalSource);
                }
                context.eval(InstrumentationTestLanguage.ID, "LOOP(1, STATEMENT)");
                fail();
            } catch (PolyglotException e) {
                assertStatementCountLimit(context, e, 50);
            }
        }
    }

    @Test
    public void testStatementInvalidLimitFilter() {
        ResourceLimits limits0 = ResourceLimits.newBuilder().//
                        statementLimit(50, (s) -> true).//
                        build();
        ResourceLimits limits1 = ResourceLimits.newBuilder().//
                        statementLimit(50, (s) -> true).//
                        build();
        Context.newBuilder().resourceLimits(limits0).build().close();
        Context.newBuilder().resourceLimits(limits1).build().close();

        Engine engine = Engine.create();
        Context.newBuilder().engine(engine).resourceLimits(limits0).build().close();
        Context.Builder builder = Context.newBuilder().engine(engine).resourceLimits(limits1);
        try {
            builder.build();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Using multiple source predicates per engine is not supported. " +
                            "The same statement limit source predicate must be used for all polyglot contexts that are assigned to the same engine. " +
                            "Resolve this by using the same predicate instance when constructing the limits object with ResourceLimits.Builder.statementLimit(long, Predicate).",
                            e.getMessage());
        }
    }

    @Test
    public void testStatementLimitFilterError() {
        RuntimeException expectedException = new RuntimeException("error");
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(50, (s) -> {
                            throw expectedException;
                        }).//
                        build();
        try (Context context = Context.newBuilder().resourceLimits(limits).build()) {
            Source source = statements(1);
            try {
                context.eval(source);
                fail();
            } catch (PolyglotException ex) {
                assertTrue(ex.isHostException());
                assertSame(expectedException, ex.asHostException());
            }
        }
    }

    @Test
    public void testStatementLimitOnEvent() {
        List<ResourceLimitEvent> events = new ArrayList<>();
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(1, null).//
                        onLimit((e) -> events.add(e)).//
                        build();
        try (Context context = Context.newBuilder().resourceLimits(limits).build()) {
            Source source = statements(1);

            context.eval(source);
            try {
                context.eval(source);
                fail();
            } catch (PolyglotException ex) {
                assertStatementCountLimit(context, ex, 1);
                assertEquals(1, events.size());
                assertSame(context, events.iterator().next().getContext());
            }
        }
    }

    @Test
    public void testStatementLimitDifferentPerContext() {
        ResourceLimits limits1 = ResourceLimits.newBuilder().//
                        statementLimit(1, null).//
                        build();
        ResourceLimits limits2 = ResourceLimits.newBuilder().//
                        statementLimit(2, null).//
                        build();
        Source source = statements(1);
        Engine engine = Engine.create();

        for (int i = 0; i < 10; i++) {
            // test no limit
            try (Context context = Context.newBuilder().engine(engine).build()) {
                context.eval(source);
                context.eval(source);
                context.eval(source);
            }

            // test with limit
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits1).build()) {
                context.eval(source);
                try {
                    context.eval(source);
                    fail();
                } catch (PolyglotException ex) {
                    assertStatementCountLimit(context, ex, 1);
                }
            }

            // test with different limit
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits2).build()) {
                context.eval(source);
                context.eval(source);
                try {
                    context.eval(source);
                    fail();
                } catch (PolyglotException ex) {
                    assertStatementCountLimit(context, ex, 2);
                }
            }
        }
    }

    @Test
    public void testStatementLimitDifferentPerContextParallel() throws InterruptedException {
        ResourceLimits limits1 = ResourceLimits.newBuilder().//
                        statementLimit(1, null).//
                        build();
        ResourceLimits limits2 = ResourceLimits.newBuilder().//
                        statementLimit(2, null).//
                        build();
        Source source = statements(1);
        Engine engine = Engine.create();

        ExecutorService executorService = Executors.newFixedThreadPool(20);
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
                try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits1).build()) {
                    context.eval(source);
                    try {
                        context.eval(source);
                        fail();
                    } catch (PolyglotException ex) {
                        assertStatementCountLimit(context, ex, 1);
                    }
                }

                // test with different limit
                try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits2).build()) {
                    context.eval(source);
                    context.eval(source);
                    try {
                        context.eval(source);
                        fail();
                    } catch (PolyglotException ex) {
                        assertStatementCountLimit(context, ex, 2);
                    }
                }
            }));
        }

        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    private static Source statements(int count) {
        return statements(count, ResourceLimitsTest.class.getSimpleName());
    }

    private static Source statements(int count, String name) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, "LOOP(" + (count == Integer.MAX_VALUE ? "infinity" : count) + ", STATEMENT)", name).buildLiteral();
    }

    @Test
    public void testSharedContextStatementLimitSynchronous() {
        List<ResourceLimitEvent> events = new ArrayList<>();
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(50, null).//
                        onLimit((e) -> events.add(e)).//
                        build();
        Engine engine = Engine.create();

        for (int i = 0; i < 10; i++) {
            try (Context c = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                c.eval(statements(50));
                try {
                    c.eval(statements(1));
                    fail();
                } catch (PolyglotException e) {
                    assertStatementCountLimit(c, e, 50);
                    assertEquals(1, events.size());
                    assertSame(c, events.iterator().next().getContext());
                    assertNotNull(events.iterator().next().toString());
                }
            }
            events.clear();
        }
    }

    @Test
    public void testSharedContextStatementLimitParallel() throws InterruptedException, ExecutionException {
        Engine engine = Engine.create();
        Map<Context, ResourceLimitEvent> events = new HashMap<>();
        final int limit = 50;
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(limit, null).//
                        onLimit((e) -> {
                            synchronized (events) {
                                events.put(e.getContext(), e);
                            }
                        }).//
                        build();
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        final int tasks = 1000;
        for (int i = 0; i < tasks; i++) {
            futures.add(executorService.submit(() -> {
                try (Context c = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                    c.eval(statements(limit));
                    try {
                        c.eval(statements(1));
                        fail();
                    } catch (PolyglotException e) {
                        assertStatementCountLimit(c, e, limit);
                        synchronized (events) {
                            assertNotNull(events.get(c));
                            assertSame(c, events.get(c).getContext());
                            assertNotNull(events.get(c).toString());
                        }
                    }
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
        synchronized (events) {
            assertEquals(tasks, events.size());
        }
    }

    @Test
    public void testParallelContextStatementLimit() throws InterruptedException, ExecutionException {
        Map<Context, ResourceLimitEvent> events = new HashMap<>();
        final int limit = 10000;
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(limit, null).//
                        onLimit((e) -> {
                            synchronized (events) {
                                assertTrue(events.isEmpty());
                                events.put(e.getContext(), e);
                            }
                        }).//
                        build();
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        try (Context c = Context.newBuilder().resourceLimits(limits).build()) {
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
                synchronized (events) {
                    assertNotNull(events.get(c));
                    assertSame(c, events.get(c).getContext());
                    assertNotNull(events.get(c).toString());
                }
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    @Test
    public void testParallelMultiContextStatementLimit() throws InterruptedException, ExecutionException {
        Engine engine = Engine.create();
        Map<Context, ResourceLimitEvent> events = new ConcurrentHashMap<>();
        final int executions = 100;
        final int contexts = 100;
        final int threads = 20;
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(executions, null).//
                        onLimit((e) -> {
                            events.put(e.getContext(), e);
                        }).//
                        build();
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        List<Future<?>> testFutures = new ArrayList<>();

        for (int contextIndex = 0; contextIndex < contexts; contextIndex++) {
            Context c = Context.newBuilder().engine(engine).resourceLimits(limits).build();
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
                    assertNotNull(events.get(c));
                    assertSame(c, events.get(c).getContext());
                    assertNotNull(events.get(c).toString());
                }
                c.close();
            }));
        }
        for (Future<?> future : testFutures) {
            future.get();
        }

        assertEquals(contexts, events.size());
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    private static void forceMultiThreading(ExecutorService executorService, Context c) throws InterruptedException, ExecutionException {
        c.enter();
        executorService.submit(new Runnable() {
            public void run() {
                c.enter();
                c.leave();
            }
        }).get();
        c.leave();
    }

    @Test
    public void testParallelMultiContextStatementResetLimit() throws InterruptedException, ExecutionException {
        Engine engine = Engine.create();
        Map<Context, ResourceLimitEvent> events = new ConcurrentHashMap<>();
        final int executions = 100;
        final int contexts = 100;
        final int threads = 20;
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(executions, null).//
                        onLimit((e) -> {
                            events.put(e.getContext(), e);
                        }).//
                        build();
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        List<Future<?>> testFutures = new ArrayList<>();
        for (int contextIndex = 0; contextIndex < contexts; contextIndex++) {
            Context c = Context.newBuilder().engine(engine).resourceLimits(limits).build();
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
                    assertNotNull(events.get(c));
                    assertSame(c, events.get(c).getContext());
                    assertNotNull(events.get(c).toString());
                }
                c.close();
            }));
        }
        for (

        Future<?> future : testFutures) {
            future.get();
        }

        assertEquals(contexts, events.size());
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    @Test
    public void testTimeLimitErrors() {
        ResourceLimits.Builder builder = ResourceLimits.newBuilder();
        assertFails(() -> cpuTimeLimit(builder, Duration.ofMillis(-1), Duration.ofMillis(1)), IllegalArgumentException.class);
        assertFails(() -> cpuTimeLimit(builder, Duration.ofMillis(0), Duration.ofMillis(1)), IllegalArgumentException.class);
        assertFails(() -> cpuTimeLimit(builder, Duration.ofMillis(1), Duration.ofMillis(-1)), IllegalArgumentException.class);
        assertFails(() -> cpuTimeLimit(builder, Duration.ofMillis(1), Duration.ofMillis(0)), IllegalArgumentException.class);
        assertFails(() -> cpuTimeLimit(builder, null, Duration.ofMillis(0)), IllegalArgumentException.class);
        assertFails(() -> cpuTimeLimit(builder, Duration.ofMillis(0), null), IllegalArgumentException.class);
        cpuTimeLimit(builder, null, null); // allowed to reset
    }

    @Test
    public void testStatementLimitErrors() {
        assertFails(() -> ResourceLimits.newBuilder().statementLimit(-1, null), IllegalArgumentException.class);
        Context context = Context.create();
        // no op without limits
        context.resetLimits();

        Context.newBuilder().resourceLimits(null); // allowed
    }

    private static void assertStatementCountLimit(Context c, PolyglotException e, int limit) {
        assertTrue(e.isCancelled());
        String expectedMessage = "Statement count limit of " + limit + " exceeded. Statements executed " + (limit + 1) + ".";
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

}
