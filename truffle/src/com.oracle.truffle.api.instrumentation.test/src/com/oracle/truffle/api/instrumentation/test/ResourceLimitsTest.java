/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class ResourceLimitsTest {

    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testStatementLimit() {
        testStatementLimit(false);
    }

    @Test
    public void testStatementLimitExplicitEnter() {
        testStatementLimit(true);
    }

    private static void testStatementLimit(boolean explicitEnter) {
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(50, null).//
                        build();

        try (Context context = Context.newBuilder().resourceLimits(limits).build()) {
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
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
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

        try (Engine engine = Engine.create()) {
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
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
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
        try (Engine engine = Engine.create()) {
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
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
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
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            }
        }
    }

    @Test
    public void testStatementLimitDifferentPerContextParallel() throws ExecutionException, InterruptedException {
        ResourceLimits limits1 = ResourceLimits.newBuilder().//
                        statementLimit(1, null).//
                        build();
        ResourceLimits limits2 = ResourceLimits.newBuilder().//
                        statementLimit(2, null).//
                        build();
        Source source = statements(1);

        try (Engine engine = Engine.create()) {
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
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
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

        try (Engine engine = Engine.create()) {
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
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
                events.clear();
            }
        }
    }

    @Test
    public void testSharedContextStatementLimitParallel() throws InterruptedException, ExecutionException {
        try (Engine engine = Engine.create()) {
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
            synchronized (events) {
                assertEquals(tasks, events.size());
            }
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
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(100, TimeUnit.SECONDS);
    }

    @Test
    public void testParallelContextStatementLimit2() throws InterruptedException, ExecutionException {
        Map<Context, ResourceLimitEvent> events = new HashMap<>();
        final int limit = 10000000;
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(limit, null).//
                        onLimit((e) -> {
                            synchronized (events) {
                                if (events.isEmpty()) {
                                    events.put(e.getContext(), e);
                                } else {
                                    assertTrue(events.containsKey(e.getContext()));
                                }
                            }
                        }).//
                        build();
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        try (Context c = Context.newBuilder().resourceLimits(limits).build()) {
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
            synchronized (events) {
                assertNotNull(events.get(c));
                assertSame(c, events.get(c).getContext());
                assertNotNull(events.get(c).toString());
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
    public void testParallelMultiContextStatementLimit() throws InterruptedException, ExecutionException {
        try (Engine engine = Engine.create()) {
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

            assertEquals(contexts, events.size());
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
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
        try (Engine engine = Engine.create()) {
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
                    try {
                        c.close();
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
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

    @Test
    public void testStatementLimitNoLimitCombination() {
        try (Engine engine = Engine.create()) {
            ResourceLimits limits = ResourceLimits.newBuilder().//
                            statementLimit(50, s -> !s.isInternal()).//
                            build();

            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
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

            ResourceLimits limitsNoLimits = ResourceLimits.newBuilder().build();

            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limitsNoLimits).build()) {
                context.eval(statements(100));
            }

            try (Context context = Context.newBuilder().engine(engine).build()) {
                context.eval(statements(100));
            }
        }
    }

}
