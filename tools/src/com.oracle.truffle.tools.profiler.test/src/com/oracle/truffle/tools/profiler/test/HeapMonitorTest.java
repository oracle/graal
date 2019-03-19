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

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.profiler.HeapMonitor;
import com.oracle.truffle.tools.profiler.HeapSummary;

public class HeapMonitorTest extends AbstractProfilerTest {
    private HeapMonitor monitor;

    @Before
    public void setupMonitor() {
        monitor = HeapMonitor.find(context.getEngine());
        assertNotNull(monitor);
    }

    @Test
    public void testNoAllocations() {
        assertFalse(monitor.isCollecting());
        assertFalse(monitor.hasData());

        monitor.setCollecting(true);

        assertTrue(monitor.isCollecting());
        assertFalse(monitor.hasData());

        for (int i = 0; i < 10; i++) {
            eval(defaultSource);
            Assert.assertEquals("Data in snapshot without allocations!", 0, monitor.takeSummary().getTotalInstances());
        }
    }

    final Source oneAllocationSource = makeSource("ROOT(" + //
                    "DEFINE(foo,ROOT(STATEMENT))," + //
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," + //
                    "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," + //
                    "ALLOCATION,CALL(baz),CALL(bar)" + ")");

    @Test
    public void testAllocations() {
        assertFalse(monitor.isCollecting());
        assertFalse(monitor.hasData());

        monitor.setCollecting(true);

        assertTrue(monitor.isCollecting());
        assertFalse(monitor.hasData());

        for (int i = 0; i < 10; i++) {
            eval(oneAllocationSource);
            HeapSummary summary = monitor.takeSummary();
            assertEquals(i + 1, summary.getTotalInstances());
            assertEquals(i + 1, summary.getTotalBytes());

            final Map<LanguageInfo, Map<String, HeapSummary>> snapshot = monitor.takeMetaObjectSummary();
            assertEquals("Incorrect snapshot size", 1, snapshot.size());
            Map<String, HeapSummary> summaries = snapshot.values().iterator().next();
            assertEquals("Incorrect snapshot size", 1, summaries.size());
            summary = summaries.values().iterator().next();

            assertEquals(i + 1, summary.getTotalInstances());
            assertEquals(i + 1, summary.getTotalBytes());
            assertEquals(InstrumentationTestLanguage.ID, snapshot.keySet().iterator().next().getId());
            assertEquals("Integer", summaries.keySet().iterator().next());
        }
    }

    @Test
    public void testActivatedDuringExec() throws InterruptedException {
        assertFalse(monitor.isCollecting());
        assertFalse(monitor.hasData());

        ExecutorService executeInParallel = Executors.newFixedThreadPool(11);
        AtomicBoolean cancelled = new AtomicBoolean();

        AtomicInteger allocations = new AtomicInteger();

        int tasks = 10;
        for (int i = 0; i < tasks; i++) {
            executeInParallel.submit(new Runnable() {
                public void run() {
                    while (!cancelled.get()) {
                        eval(oneAllocationSource);
                        allocations.incrementAndGet();
                        if (Thread.interrupted()) {
                            break;
                        }
                    }
                }
            });
        }

        try {
            requireAllocations(allocations, 1000);
            monitor.setCollecting(true);
            requireAllocations(allocations, 1000);
            assertTrue(monitor.isCollecting());
            assertTrue(monitor.hasData());
            for (int i = 0; i < 10; i++) {
                final Map<LanguageInfo, Map<String, HeapSummary>> snapshot = monitor.takeMetaObjectSummary();
                assertEquals("Incorrect snapshot size", 1, snapshot.size());
                Map<String, HeapSummary> summaries = snapshot.values().iterator().next();
                assertEquals("Incorrect snapshot size", 1, summaries.size());
                final HeapSummary summary = summaries.values().iterator().next();
                assertTrue(summary.getTotalInstances() > 1);
                assertEquals(InstrumentationTestLanguage.ID, snapshot.keySet().iterator().next().getId());
                assertEquals("Integer", summaries.keySet().iterator().next());
            }
        } catch (InterruptedException e) {
            Assert.fail("Interruption.");
        } finally {
            cancelled.set(true);
        }
        executeInParallel.shutdown();
        executeInParallel.awaitTermination(1000, TimeUnit.MILLISECONDS);

        // test that we actually free the objects
        for (int i = 0; i < 1000; i++) {
            System.gc();
            if (monitor.takeSummary().getAliveInstances() == 0) {
                break;
            }
            Thread.sleep(1);
        }
        assertEquals(0, monitor.takeSummary().getAliveInstances());
        assertEquals(0, monitor.takeSummary().getAliveBytes());

    }

    private static void requireAllocations(AtomicInteger allocations, int number) throws InterruptedException {
        allocations.set(0);
        while (allocations.get() < number) {
            Thread.sleep(5);
        }
    }
}
