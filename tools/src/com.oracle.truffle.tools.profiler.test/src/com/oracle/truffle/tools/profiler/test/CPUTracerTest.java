/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.tools.profiler.CPUTracer;

public class CPUTracerTest extends AbstractProfilerTest {

    private CPUTracer tracer;

    @Before
    public void setupTracer() {
        tracer = CPUTracer.find(context.getEngine());
        Assert.assertNotNull(tracer);
    }

    @Test
    public void testCollecting() {

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertEquals(0, tracer.getPayloads().size());
        Assert.assertTrue(tracer.isCollecting());

        eval(defaultSource);

        Assert.assertNotEquals(0, tracer.getPayloads().size());
        Assert.assertTrue(tracer.isCollecting());

        tracer.setCollecting(false);

        Assert.assertFalse(tracer.isCollecting());

        tracer.clearData();

        Assert.assertEquals(0, tracer.getPayloads().size());
    }

    @Test
    public void testCorrectRootCount() {
        final Map<String, Long> expectedCountMap = new HashMap<>();
        expectedCountMap.put("baz", 1L);
        expectedCountMap.put("bar", 11L);
        expectedCountMap.put("foo", 110L);
        expectedCountMap.put("", 1L);

        synchronized (tracer) {
            tracer.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        }
        executeAndCheckRootNameCounters(defaultSource, expectedCountMap);
    }

    @Test
    public void testCorrectCallCount() {
        final Map<String, Long> expectedCountMap = new HashMap<>();
        expectedCountMap.put("baz", 10L);
        expectedCountMap.put("bar", 110L);
        expectedCountMap.put("", 1L);
        // every call in the root is its own source section so this is just a placeholder
        expectedCountMap.put(":", 1L);

        synchronized (tracer) {
            tracer.setFilter(NO_INTERNAL_CALL_TAG_FILTER);
        }

        executeAndCheckRootNameCounters(defaultSource, expectedCountMap);
    }

    @Test
    public void testCorrectStatementCount() {
        final Map<String, Long> expectedCountMap = new HashMap<>();

        Map<String, Long> foo = new HashMap<>();
        foo.put("foo", 110L);
        expectedCountMap.put("baz", 1L);
        expectedCountMap.put("bar", 11L);
        expectedCountMap.put("foo", 110L);

        synchronized (tracer) {
            tracer.setFilter(NO_INTERNAL_STATEMENT_TAG_FILTER);
        }
        executeAndCheckStatementCounters(defaultSource, expectedCountMap);
    }

    @Test
    public void testCorrectRootCountRecursive() {
        final Map<String, Long> expectedCountMap = new HashMap<>();
        expectedCountMap.put("foo", 110L);
        expectedCountMap.put("bar", 1L);
        expectedCountMap.put("", 1L);

        synchronized (tracer) {
            tracer.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        }
        executeAndCheckRootNameCounters(defaultRecursiveSource,
                        expectedCountMap);
    }

    @Test
    public void testCorrectCallCountRecursive() {
        final Map<String, Long> expectedCountMap = new HashMap<>();
        expectedCountMap.put("foo", 110L);
        expectedCountMap.put("bar", 10L);
        expectedCountMap.put("", 1L);

        synchronized (tracer) {
            tracer.setFilter(NO_INTERNAL_CALL_TAG_FILTER);
        }
        executeAndCheckRootNameCounters(defaultRecursiveSource,
                        expectedCountMap);
    }

    @Test
    public void testCorrectStatementCountRecursive() {
        final Map<String, Long> expectedCountMap = new HashMap<>();

        expectedCountMap.put("foo", 110L);
        expectedCountMap.put("bar", 1L);

        synchronized (tracer) {
            tracer.setFilter(NO_INTERNAL_STATEMENT_TAG_FILTER);
        }
        executeAndCheckStatementCounters(defaultRecursiveSource,
                        expectedCountMap);
    }

    // Works only assuming unique root names in counters
    // This is, for example, not true for statement tracing
    private void executeAndCheckRootNameCounters(Source recursiveSource,
                    Map<String, Long> expectedCountMap) {
        final int longExecutionCount = 1000;

        tracer.setCollecting(true);
        eval(recursiveSource);
        Collection<CPUTracer.Payload> payloads = tracer.getPayloads();
        Assert.assertEquals(
                        "Total number of counters does not match after one elxecution",
                        expectedCountMap.size(), payloads.size());

        for (CPUTracer.Payload payload : payloads) {
            final long expectedCount = expectedCountMap.get(payload.getRootName());
            final long count = payload.getCount();
            Assert.assertEquals(payload.getRootName() + " count not correct",
                            expectedCount, count);
        }

        for (int i = 1; i < longExecutionCount; i++) {
            eval(recursiveSource);
        }

        payloads = tracer.getPayloads();
        Assert.assertEquals(
                        "Total number of counters does not match after one execution",
                        expectedCountMap.size(), payloads.size());

        for (CPUTracer.Payload payload : payloads) {
            final long expectedCount = longExecutionCount * expectedCountMap.get(payload.getRootName());
            final long count = payload.getCount();
            Assert.assertEquals(payload.getRootName() + " count not correct",
                            expectedCount, count);
        }
    }

    private void executeAndCheckStatementCounters(Source source,
                    Map<String, Long> expectedCountMap) {
        tracer.setCollecting(true);
        eval(source);
        Collection<CPUTracer.Payload> payloads = tracer.getPayloads();

        Assert.assertEquals("Total number of counters does not match",
                        expectedCountMap.size(), payloads.size());

        for (CPUTracer.Payload payload : payloads) {
            Long expected = expectedCountMap.get(payload.getRootName());
            Assert.assertEquals(expected.longValue(), payload.getCount());
        }

    }
}
