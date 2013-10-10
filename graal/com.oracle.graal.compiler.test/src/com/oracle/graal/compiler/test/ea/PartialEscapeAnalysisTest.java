/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.test.ea;

import java.util.concurrent.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.virtual.phases.ea.*;

/**
 * The PartialEscapeAnalysisPhase is expected to remove all allocations and return the correct
 * values.
 */
public class PartialEscapeAnalysisTest extends GraalCompilerTest {

    public static class TestObject {

        public int x;
        public int y;

        public TestObject(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class TestObject2 {

        public Object x;
        public Object y;

        public TestObject2(Object x, Object y) {
            this.x = x;
            this.y = y;
        }
    }

    @Test
    public void test1() {
        testMaterialize("test1Snippet", 0.25, 1);
    }

    @SuppressWarnings("all")
    public static Object test1Snippet(int a, int b, Object x, Object y) {
        TestObject2 obj = new TestObject2(x, y);
        if (a < 0) {
            if (b < 0) {
                return obj;
            } else {
                return obj.y;
            }
        } else {
            return obj.x;
        }
    }

    @Test
    public void test2() {
        testMaterialize("test2Snippet", 1.5, 3, LoadIndexedNode.class);
    }

    public static Object test2Snippet(int a, Object x, Object y, Object z) {
        TestObject2 obj = new TestObject2(x, y);
        obj.x = new TestObject2(obj, z);
        if (a < 0) {
            ((TestObject2) obj.x).y = null;
            obj.y = null;
            return obj;
        } else {
            ((TestObject2) obj.x).y = Integer.class;
            ((TestObject2) obj.x).x = null;
            return obj.x;
        }
    }

    @Test
    public void test3() {
        testMaterialize("test3Snippet", 0.5, 1, StoreFieldNode.class, LoadFieldNode.class);
    }

    public static Object test3Snippet(int a) {
        if (a < 0) {
            TestObject obj = new TestObject(1, 2);
            obj.x = 123;
            obj.y = 234;
            obj.x = 123111;
            obj.y = new Integer(123).intValue();
            return obj;
        } else {
            return null;
        }
    }

    @Test
    public void testCache() {
        testMaterialize("testCacheSnippet", 0.75, 1);
    }

    public static class CacheKey {

        private final int idx;
        private final Object ref;

        public CacheKey(int idx, Object ref) {
            this.idx = idx;
            this.ref = ref;
        }

        @Override
        public int hashCode() {
            return 31 * idx + ref.hashCode();
        }

        public synchronized boolean equals(CacheKey other) {
            return idx == other.idx && ref == other.ref;
        }
    }

    public static CacheKey cacheKey = null;
    public static Object value = null;

    private static native Object createValue(CacheKey key);

    public static Object testCacheSnippet(int idx, Object ref) {
        CacheKey key = new CacheKey(idx, ref);
        if (!key.equals(cacheKey)) {
            cacheKey = key;
            value = createValue(key);
        }
        return value;
    }

    @SafeVarargs
    final void testMaterialize(final String snippet, double expectedProbability, int expectedCount, Class<? extends Node>... invalidNodeClasses) {
        StructuredGraph result = processMethod(snippet);
        try {
            Assert.assertTrue("partial escape analysis should have removed all NewInstanceNode allocations", result.getNodes().filter(NewInstanceNode.class).isEmpty());
            Assert.assertTrue("partial escape analysis should have removed all NewArrayNode allocations", result.getNodes().filter(NewArrayNode.class).isEmpty());

            NodesToDoubles nodeProbabilities = new ComputeProbabilityClosure(result).apply();
            double probabilitySum = 0;
            int materializeCount = 0;
            for (CommitAllocationNode materialize : result.getNodes().filter(CommitAllocationNode.class)) {
                probabilitySum += nodeProbabilities.get(materialize) * materialize.getVirtualObjects().size();
                materializeCount += materialize.getVirtualObjects().size();
            }
            Assert.assertEquals("unexpected number of MaterializeObjectNodes", expectedCount, materializeCount);
            Assert.assertEquals("unexpected probability of MaterializeObjectNodes", expectedProbability, probabilitySum, 0.01);
            for (Node node : result.getNodes()) {
                for (Class<? extends Node> clazz : invalidNodeClasses) {
                    Assert.assertFalse("instance of invalid class: " + clazz.getSimpleName(), clazz.isInstance(node) && node.usages().isNotEmpty());
                }
            }
        } catch (AssertionError e) {
            TypeSystemTest.outputGraph(result, snippet + ": " + e.getMessage());
            throw e;
        }
    }

    private StructuredGraph processMethod(final String snippet) {
        return Debug.scope("PartialEscapeAnalysisTest " + snippet, new DebugDumpScope(snippet), new Callable<StructuredGraph>() {

            @Override
            public StructuredGraph call() {
                StructuredGraph graph = parse(snippet);

                Assumptions assumptions = new Assumptions(false);
                HighTierContext context = new HighTierContext(getMetaAccess(), getCodeCache(), assumptions, replacements, null, getDefaultPhasePlan(), OptimisticOptimizations.ALL);
                new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
                new DeadCodeEliminationPhase().apply(graph);
                new CanonicalizerPhase(true).apply(graph, context);
                new PartialEscapePhase(false, new CanonicalizerPhase(true)).apply(graph, context);

                for (MergeNode merge : graph.getNodes(MergeNode.class)) {
                    merge.setStateAfter(null);
                }
                new DeadCodeEliminationPhase().apply(graph);
                new CanonicalizerPhase(true).apply(graph, context);
                return graph;
            }
        });
    }
}
