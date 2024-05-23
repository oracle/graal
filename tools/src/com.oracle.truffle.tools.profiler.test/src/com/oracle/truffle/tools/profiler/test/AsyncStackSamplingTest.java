/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.StackTraceEntry;

@RunWith(Parameterized.class)
public class AsyncStackSamplingTest extends AbstractPolyglotTest {

    private Semaphore enteredSample = new Semaphore(0);
    private Semaphore leaveSample = new Semaphore(0);
    private CPUSampler sampler;

    @Parameter(value = 0) public boolean includeInternal;
    @Parameter(value = 1) public boolean captureStack;

    @Parameters(name = "includeInternal={0},captureStack={1}")
    public static List<Object[]> data() {
        return List.of(new Object[][]{
                        {false, false},
                        {false, true},
                        {true, false},
                        {true, true},
        });
    }

    private static void assertEntry(Iterator<StackTraceEntry> iterator, String expectedName, int expectedStartLine, int expectedCharIndex) {
        StackTraceEntry entry = iterator.next();
        assertEquals("root name", expectedName, entry.getRootName());
        assertEquals("startLine", expectedStartLine, entry.getSourceSection().getStartLine());
        assertEquals("charIndex", expectedCharIndex, entry.getSourceSection().getCharIndex());
        assertNotNull(entry.toStackTraceElement());
        assertNotNull(entry.toString());
        assertNotEquals(0, entry.hashCode());
        assertEquals(entry, entry);
    }

    public AsyncStackSamplingTest() {
        needsInstrumentEnv = true;
        ignoreCancelOnClose = true;
    }

    @Before
    public void setup() {
        enterContext = false;
        enteredSample = new Semaphore(0);
        leaveSample = new Semaphore(0);
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
                                TruffleSafepoint.setBlockedThreadInterruptible(c.getInstrumentedNode(), Semaphore::acquire, leaveSample);
                            }

                            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                            }

                            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                            }
                        });

        sampler = CPUSampler.find(context.getEngine());
        sampler.setGatherAsyncStackTrace(true);
        Assert.assertTrue(sampler.isGatherAsyncStackTrace());

        // Filter out any unavailable source section to deliberately exclude ASYNC_RESUME RootNode.
        if (includeInternal) {
            sampler.setFilter(SourceSectionFilter.newBuilder().sourceSectionAvailableOnly(true).tagIs(RootTag.class, StatementTag.class).build());
        } else {
            sampler.setFilter(SourceSectionFilter.newBuilder().sourceSectionAvailableOnly(true).sourceIs(s -> !s.isInternal()).tagIs(RootTag.class, StatementTag.class).build());
        }
        if (captureStack) {
            instrumentEnv.setAsynchronousStackDepth(100);
        } else {
            instrumentEnv.setAsynchronousStackDepth(0);
        }
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testAsyncStackSample1() throws InterruptedException {
        testSampling(new String[]{"""
                        ROOT(
                        DEFINE(aaa,ROOT(BLOCK(EXPRESSION(CALL(sample))))),
                        DEFINE(bbb,ROOT(BLOCK(EXPRESSION(ASYNC_CALL(aaa))))),
                        DEFINE(ccc,ROOT(BLOCK(STATEMENT(CALL(bbb))))),
                        DEFINE(ddd,ROOT(BLOCK(STATEMENT(CALL(ccc))))),
                        CALL(ddd)
                        )
                        """,
                        "ASYNC_RESUME(aaa)",
        }, (samples, i) -> {
            assertEquals(1, samples.size());
            List<StackTraceEntry> stackTraceEntry = samples.values().iterator().next();
            Iterator<StackTraceEntry> iterator = stackTraceEntry.iterator();
            assertEntry(iterator, "sample", 1, 14);
            assertEntry(iterator, "aaa", 2, 17);
            if (includeInternal) {
                assertEntry(iterator, "bbb->aaa", 3, 90); // resumption frame
            }
            assertEntry(iterator, "bbb", 3, 68);
            if (captureStack) {
                assertEntry(iterator, "ccc", 4, 122);
                assertEntry(iterator, "ddd", 5, 169);
                assertEntry(iterator, "", 1, 0);
            }
            assertFalse(iterator.hasNext());
        }, 1, Long.MAX_VALUE);
    }

    @Test
    public void testAsyncStackSample2() throws InterruptedException {
        testSampling(new String[]{"""
                        ROOT(
                        DEFINE(aaa,ROOT(BLOCK(EXPRESSION(CALL(sample))))),
                        DEFINE(bbb,ROOT(BLOCK(EXPRESSION(ASYNC_CALL(aaa))))),
                        DEFINE(ccc,ROOT(BLOCK(STATEMENT(CALL(bbb))))),
                        DEFINE(ddd,ROOT(BLOCK(STATEMENT(ASYNC_CALL(ccc))))),
                        DEFINE(eee,ROOT(BLOCK(STATEMENT(CALL(ddd))))),
                        CALL(eee)
                        )
                        """,
                        "ASYNC_RESUME(aaa)",
                        "ASYNC_RESUME(ccc)",
        }, (samples, i) -> {
            assertEquals(1, samples.size());
            List<StackTraceEntry> stackTraceEntry = samples.values().iterator().next();
            Iterator<StackTraceEntry> iterator = stackTraceEntry.iterator();
            assertEntry(iterator, "sample", 1, 14);
            assertEntry(iterator, "aaa", 2, 17);
            if (includeInternal) {
                assertEntry(iterator, "bbb->aaa", 3, 90); // resumption frame
            }
            assertEntry(iterator, "bbb", 3, 68);
            if (captureStack) {
                assertEntry(iterator, "ccc", 4, 122);
            }
            if (includeInternal && captureStack) {
                assertEntry(iterator, "ddd->ccc", 5, 190); // resumption frame
            }
            assertEntry(iterator, "ddd", 5, 169);
            if (captureStack) {
                assertEntry(iterator, "eee", 6, 222);
                assertEntry(iterator, "", 1, 0);
            }
            assertFalse(iterator.hasNext());
        }, 1, Long.MAX_VALUE);
    }

    /**
     * @param sources sources are executed in sequence on another thread and sampled.
     * @param verifier verify the stack samples
     * @param expectedSamples number of expected samples
     */
    private void testSampling(String[] sources, BiConsumer<Map<Thread, List<StackTraceEntry>>, Integer> verifier, int expectedSamples, long timeout)
                    throws InterruptedException {
        List<Thread> threads = new ArrayList<>(sources.length);
        int numThreads = 1;
        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    for (String source : sources) {
                        context.eval(InstrumentationTestLanguage.ID, source);
                    }
                } catch (PolyglotException e) {
                    if (!e.isInternalError()) {
                        return; // all good
                    }
                    throw e;
                }
            });
            t.setName("t" + i);
            threads.add(t);
        }

        try {
            threads.forEach((t) -> t.start());
            enteredSample.acquire(numThreads);

            for (int i = 0; i < expectedSamples; i++) {
                verifier.accept(sampler.takeSample(timeout, TimeUnit.MILLISECONDS), i);
                leaveSample.release(numThreads);
            }

        } finally {
            context.close(true);
            for (Thread thread : threads) {
                leaveSample.release(numThreads * expectedSamples);
                thread.interrupt();
                thread.join(10000);
            }
        }
        assertTrue(sampler.takeSample().isEmpty());
    }

}
