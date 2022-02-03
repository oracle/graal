/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.ea;

import java.lang.ref.SoftReference;
import java.lang.reflect.Array;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.TypeSystemTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * The PartialEscapeAnalysisPhase is expected to remove all allocations and return the correct
 * values.
 */
public class PartialEscapeAnalysisTest extends EATestBase {

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
        testPartialEscapeAnalysis("test1Snippet", 0.25, 1);
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
        testPartialEscapeAnalysis("test2Snippet", 1.5, 3, LoadIndexedNode.class);
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
        testPartialEscapeAnalysis("test3Snippet", 0.5, 1, StoreFieldNode.class, LoadFieldNode.class);
    }

    @SuppressWarnings("deprecation")
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
    public void testDynArray() {
        testPartialEscapeAnalysis("testDynArraySnippet", 0, 0);
    }

    public static Object testDynArraySnippet(int a) {
        Object[] args = (Object[]) Array.newInstance(Object.class, 2);
        args[0] = a;
        args[1] = 42;
        return (int) args[0] + (int) args[1];
    }

    @Test
    public void testArrayCopy() {
        testPartialEscapeAnalysis("testArrayCopySnippet", 0, 0);
    }

    public static Object[] array = new Object[]{1, 2, 3, 4, 5, "asdf", "asdf"};
    public static char[] charArray = new char[]{1, 2, 3, 4, 5, 'a', 'f'};

    public static Object testArrayCopySnippet(int a) {
        Object[] tmp = new Object[]{a != 1 ? array[a] : null};
        Object[] tmp2 = new Object[5];
        System.arraycopy(tmp, 0, tmp2, 4, 1);
        return tmp2[4];
    }

    @Test
    public void testPrimitiveArraycopy() {
        testPartialEscapeAnalysis("testPrimitiveArraycopySnippet", 0, 0);
    }

    public static Object testPrimitiveArraycopySnippet(int a) {
        char[] tmp = new char[]{a != 1 ? charArray[a] : 0};
        char[] tmp2 = new char[5];
        System.arraycopy(tmp, 0, tmp2, 4, 1);
        return tmp2[4];
    }

    @Test
    @Ignore
    public void testCache() {
        testPartialEscapeAnalysis("testCacheSnippet", 0.75, 1);
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

    public static int testReference1Snippet(Object a) {
        SoftReference<Object> softReference = new SoftReference<>(a);
        if (softReference.get().hashCode() == 0) {
            return 1;
        } else {
            return 2;
        }
    }

    @Test
    public void testReference1() {
        prepareGraph("testReference1Snippet", false);
        assertDeepEquals(1, graph.getNodes().filter(NewInstanceNode.class).count());
    }

    public static int testCanonicalizeSnippet(int v) {
        CacheKey key = new CacheKey(v, null);

        CacheKey key2;
        if (key.idx == v) {
            key2 = new CacheKey(v, null);
        } else {
            key2 = null;
        }
        return key2.idx;
    }

    @Test
    public void testCanonicalize() {
        prepareGraph("testCanonicalizeSnippet", false);
        assertTrue(graph.getNodes().filter(ReturnNode.class).count() == 1);
        assertTrue(graph.getNodes().filter(ReturnNode.class).first().result() == graph.getParameter(0));
    }

    public static int testBoxLoopSnippet(int n) {
        Integer sum = 0;
        for (Integer i = 0; i < n; i++) {
            if (sum == null) {
                sum = null;
            } else {
                sum += i;
            }
        }
        return sum;
    }

    @Test
    public void testBoxLoop() {
        testPartialEscapeAnalysis("testBoxLoopSnippet", 0, 0, BoxNode.class, UnboxNode.class);
    }

    static volatile int staticField;
    static boolean executedDeoptimizeDirective;

    static class A {
        String field;
    }

    public static Object deoptWithVirtualObjectsSnippet() {
        A a = new A();
        a.field = "field";

        staticField = 5;
        if (staticField == 5) {
            GraalDirectives.deoptimize();
            executedDeoptimizeDirective = true;
        }

        return a.field;
    }

    /**
     * Tests deoptimizing with virtual objects in debug info.
     */
    @Test
    public void testDeoptWithVirtualObjects() {
        assertFalse(executedDeoptimizeDirective);
        test("deoptWithVirtualObjectsSnippet");
        assertTrue(executedDeoptimizeDirective);
    }

    @SafeVarargs
    protected final void testPartialEscapeAnalysis(String snippet, double expectedProbability, int expectedCount, Class<? extends Node>... invalidNodeClasses) {
        prepareGraph(snippet, false);
        graph.clearAllStateAfterForTestingOnly();
        new DeadCodeEliminationPhase().apply(graph);
        createCanonicalizerPhase().apply(graph, context);
        try {
            Assert.assertTrue("partial escape analysis should have removed all NewInstanceNode allocations", graph.getNodes().filter(NewInstanceNode.class).isEmpty());
            Assert.assertTrue("partial escape analysis should have removed all NewArrayNode allocations", graph.getNodes().filter(NewArrayNode.class).isEmpty());

            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);
            double frequencySum = 0;
            int materializeCount = 0;
            for (CommitAllocationNode materialize : graph.getNodes().filter(CommitAllocationNode.class)) {
                frequencySum += cfg.blockFor(materialize).getRelativeFrequency() * materialize.getVirtualObjects().size();
                materializeCount += materialize.getVirtualObjects().size();
            }
            Assert.assertEquals("unexpected number of MaterializeObjectNodes", expectedCount, materializeCount);
            Assert.assertEquals("unexpected frequency of MaterializeObjectNodes", expectedProbability, frequencySum, 0.01);
            for (Node node : graph.getNodes()) {
                for (Class<? extends Node> clazz : invalidNodeClasses) {
                    Assert.assertFalse("instance of invalid class: " + clazz.getSimpleName(), clazz.isInstance(node) && node.usages().isNotEmpty());
                }
            }
        } catch (AssertionError e) {
            TypeSystemTest.outputGraph(graph, snippet + ": " + e.getMessage());
            throw e;
        }
    }
}
