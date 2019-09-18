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
package com.oracle.truffle.tools.profiler.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSampler.Mode;
import com.oracle.truffle.tools.profiler.StackTraceEntry;

public class ManualSamplingTest extends AbstractPolyglotTest {

    protected static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().sourceIs(s -> !s.isInternal()).tagIs(RootTag.class, StatementTag.class).build();
    private static final Class<?>[] ROOT_TAGS = new Class<?>[]{RootTag.class, RootBodyTag.class};

    @Test
    @SuppressWarnings("unchecked")
    public void testFindActiveEngines() {
        List<Engine> engines = (List<Engine>) ReflectionUtils.invokeStatic(Engine.class, "findActiveEngines");
        for (Engine engine : engines) {
            if (engine == context.getEngine()) {
                // found
                return;
            }
        }
        fail("engine not found");
    }

    private final Semaphore awaitEnterSample = new Semaphore(0);
    private final Semaphore awaitLeaveSample = new Semaphore(0);
    private final Semaphore enteredSample = new Semaphore(0);
    private final Semaphore leaveSample = new Semaphore(0);
    private CPUSampler sampler;

    @Before
    public void setup() {
        setupEnv(Context.newBuilder().build(), new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
        context.eval(InstrumentationTestLanguage.ID, "DEFINE(sample, ROOT)");
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().rootNameIs((s) -> s.equals("sample")).tagIs(RootTag.class).build(),
                        new ExecutionEventListener() {
                            public void onEnter(EventContext c, VirtualFrame frame) {
                                enteredSample.release(1);
                                try {
                                    awaitEnterSample.acquire(1);
                                } catch (InterruptedException e) {
                                    throw new AssertionError(e);
                                }
                            }

                            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                                leaveSample.release(1);
                                try {
                                    awaitLeaveSample.acquire(1);
                                } catch (InterruptedException e) {
                                    throw new AssertionError(e);
                                }
                            }

                            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                            }
                        });

        sampler = CPUSampler.find(context.getEngine());
        sampler.setFilter(DEFAULT_FILTER);
        sampler.setMode(Mode.ROOTS);
    }

    @Test
    public void testReconstructingRoots() {

    }

    @Test
    public void testLazySingleThreadRoots1() throws InterruptedException {
        testSampling(new String[]{"ROOT(CALL(sample))"
        }, (samples) -> {
            assertEquals(1, samples.size());
            Iterator<StackTraceEntry> iterator = samples.values().iterator().next().iterator();
            assertEntry(iterator, "", 0, ROOT_TAGS);
            assertFalse(iterator.hasNext());
        }, true);
    }

    @Test
    public void testLazySingleThreadStatements() throws InterruptedException {
        sampler.setMode(Mode.STATEMENTS);
        testSampling(new String[]{"ROOT(STATEMENT(CALL(sample)))"
        }, (samples) -> {
            assertEquals(1, samples.size());
            Iterator<StackTraceEntry> iterator = samples.values().iterator().next().iterator();
            assertEntry(iterator, "", 5, StatementTag.class);
            assertEntry(iterator, "", 0, ROOT_TAGS);
            assertFalse(iterator.hasNext());
        }, true);
    }

    @Test
    public void testSingleThreadRoots1() throws InterruptedException {
        testSampling(new String[]{"ROOT(CALL(sample))"
        }, (samples) -> {
            assertEquals(1, samples.size());
            Iterator<StackTraceEntry> iterator = samples.values().iterator().next().iterator();
            assertEntry(iterator, "", 0, ROOT_TAGS);
            assertFalse(iterator.hasNext());
        }, false);
    }

    @Test
    public void testSingleThreadRoots2() throws InterruptedException {
        testSampling(new String[]{"ROOT(" +
                        "DEFINE(bar,ROOT(BLOCK(EXPRESSION(CALL(sample)))))," +
                        "DEFINE(baz,ROOT(BLOCK(STATEMENT(CALL(bar)))))," +
                        "CALL(baz))"
        }, (samples) -> {
            assertEquals(1, samples.size());
            Iterator<StackTraceEntry> iterator = samples.values().iterator().next().iterator();
            assertEntry(iterator, "bar", 16, ROOT_TAGS);
            assertEntry(iterator, "baz", 66, ROOT_TAGS);
            assertEntry(iterator, "", 0, ROOT_TAGS);
            assertFalse(iterator.hasNext());
        }, false);
    }

    @Test
    public void testSingleThreadStatements() throws InterruptedException {
        sampler.setMode(Mode.STATEMENTS);
        testSampling(new String[]{"ROOT(" +
                        "DEFINE(bar,ROOT(STATEMENT(CALL(sample))))," +
                        "DEFINE(baz,ROOT(STATEMENT(CALL(bar))))," +
                        "CALL(baz))"
        }, (samples) -> {
            assertEquals(1, samples.size());
            Iterator<StackTraceEntry> iterator = samples.values().iterator().next().iterator();
            assertEntry(iterator, "bar", 21, StatementTag.class);
            assertEntry(iterator, "bar", 16, ROOT_TAGS);
            assertEntry(iterator, "baz", 63, StatementTag.class);
            assertEntry(iterator, "baz", 58, ROOT_TAGS);
            assertEntry(iterator, "", 0, ROOT_TAGS);
            assertFalse(iterator.hasNext());
        }, false);
    }

    @Test
    public void testManyThreads() throws InterruptedException {
        testSampling(new String[]{
                        "ROOT(" +
                                        "DEFINE(t0_bar,ROOT(BLOCK(EXPRESSION(CALL(sample)))))," +
                                        "DEFINE(t0_baz,ROOT(BLOCK(STATEMENT(CALL(t0_bar)))))," +
                                        "CALL(t0_baz))",
                        "ROOT(" +
                                        "DEFINE(t1_bar,ROOT(BLOCK(EXPRESSION(CALL(sample)))))," +
                                        "DEFINE(t1_baz,ROOT(BLOCK(STATEMENT(CALL(t1_bar)))))," +
                                        "CALL(t1_baz))",
                        "ROOT(" +
                                        "DEFINE(t2_bar,ROOT(BLOCK(EXPRESSION(CALL(sample)))))," +
                                        "DEFINE(t2_baz,ROOT(BLOCK(STATEMENT(CALL(t2_bar)))))," +
                                        "CALL(t2_baz))"

        }, (samples) -> {
            assertEquals(3, samples.size());
            for (Entry<Thread, List<StackTraceEntry>> entry : samples.entrySet()) {
                String threadName = entry.getKey().getName();
                Iterator<StackTraceEntry> iterator = entry.getValue().iterator();
                assertEntry(iterator, threadName + "_bar", 19, ROOT_TAGS);
                assertEntry(iterator, threadName + "_baz", 72, ROOT_TAGS);
                assertEntry(iterator, "", 0, ROOT_TAGS);
                assertFalse(iterator.hasNext());
            }
        }, false);
    }

    private static void assertEntry(Iterator<StackTraceEntry> iterator, String expectedName, int expectedCharIndex, Class<?>... expectedTags) {
        StackTraceEntry entry = iterator.next();
        assertEquals(expectedName, entry.getRootName());
        assertTrue(entry.isInlined() ^ entry.isCompiled() ^ entry.isInterpreted());
        assertEquals(expectedTags.length, entry.getTags().size());
        for (Class<?> tag : expectedTags) {
            assertTrue(entry.getTags().toString(), entry.getTags().contains(tag));
        }
        assertEquals(expectedCharIndex, entry.getSourceSection().getCharIndex());
        assertNotNull(entry.toStackTraceElement());
        assertNotNull(entry.toString());
        assertNotNull(entry.hashCode());
        assertTrue(entry.equals(entry));
    }

    /**
     * @param sources every source is executed on its on thread and sampled.
     * @param verifier verify the stack samples
     * @param lazyAttach the instrument should be attached while executing, requiring the shadow
     *            stack to be reconstructed.
     */
    private void testSampling(String[] sources, Consumer<Map<Thread, List<StackTraceEntry>>> verifier, boolean lazyAttach) throws InterruptedException {
        List<Thread> threads = new ArrayList<>(sources.length);
        int numThreads = sources.length;
        for (int i = 0; i < numThreads; i++) {
            String source = sources[i];
            Thread t = new Thread(() -> {
                context.eval(InstrumentationTestLanguage.ID, source);
                /*
                 * run some code after to allow cleanup of the initial stack this is needed as we
                 * currently don't support listening to leaving the context on a thread in
                 * instrumentation.
                 */
                context.eval(InstrumentationTestLanguage.ID, "ROOT");
            });
            t.setName("t" + i);
            threads.add(t);
        }

        try {
            if (lazyAttach) {
                threads.forEach((t) -> t.start());
                enteredSample.acquire(numThreads);
                assertEquals(0, sampler.takeSample().size()); // initializes the sampler
                awaitEnterSample.release(numThreads);
                leaveSample.acquire(numThreads);
                try {
                    verifier.accept(sampler.takeSample());
                } finally {
                    awaitLeaveSample.release(numThreads);
                }
            } else {
                assertEquals(0, sampler.takeSample().size()); // initializes the sampler
                threads.forEach((t) -> t.start());
                enteredSample.acquire(numThreads);
                try {
                    verifier.accept(sampler.takeSample());
                } finally {
                    awaitEnterSample.release(numThreads);
                    leaveSample.acquire(numThreads);
                    awaitLeaveSample.release(numThreads);

                }
            }
        } finally {
            for (Thread thread : threads) {
                thread.join(10000);
            }
        }
        assertTrue(sampler.takeSample().isEmpty());
    }

}
