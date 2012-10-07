/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import junit.framework.Assert;

import org.junit.Test;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.virtual.phases.ea.*;

/**
 * In these test cases the probability of all invokes is set to a high value, such that an InliningPhase should inline them all.
 * After that, the EscapeAnalysisPhase is expected to remove all allocations and return the correct values.
 */
public class EscapeAnalysisTest extends GraalCompilerTest {

    @Test
    public void test1() {
        test("test1Snippet", Constant.forInt(101));
    }

    @SuppressWarnings("all")
    public static int test1Snippet(int a) {
        Integer x = new Integer(101);
        return x.intValue();
    }

    @Test
    public void test2() {
        test("test2Snippet", Constant.forInt(0));
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a) {
        Integer[] x = new Integer[0];
        return x.length;
    }

    @Test
    public void test3() {
        test("test3Snippet", Constant.forObject(null));
    }

    @SuppressWarnings("all")
    public static Object test3Snippet(int a) {
        Integer[] x = new Integer[1];
        return x[0];
    }

    @Test
    public void testMonitor() {
        test("testMonitorSnippet", Constant.forInt(0));
    }

    private static native void notInlineable();

    @SuppressWarnings("all")
    public static int testMonitorSnippet(int a) {
        Integer x = new Integer(0);
        Integer[] y = new Integer[0];
        Integer[] z = new Integer[1];
        synchronized (x) {
            synchronized (y) {
                synchronized (z) {
                    notInlineable();
                }
            }
        }
        return x.intValue();
    }

    @Test
    public void testMonitor2() {
        test("testMonitor2Snippet", Constant.forInt(0));
    }

    /**
     * This test case differs from the last one in that it requires inlining within a synchronized region.
     */
    @SuppressWarnings("all")
    public static int testMonitor2Snippet(int a) {
        Integer x = new Integer(0);
        Integer[] y = new Integer[0];
        Integer[] z = new Integer[1];
        synchronized (x) {
            synchronized (y) {
                synchronized (z) {
                    notInlineable();
                    return x.intValue();
                }
            }
        }
    }

    @Test
    public void testMerge() {
        test("testMerge1Snippet", Constant.forInt(0));
    }

    public static class TestObject {
        int x;
        int y;
        public TestObject(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static int testMerge1Snippet(int a) {
        TestObject obj = new TestObject(1, 0);
        if (a < 0) {
            obj.x = obj.x + 1;
        } else {
            obj.x = obj.x + 2;
            obj.y = 0;
        }
        if (obj.x > 1000) {
            return 1;
        }
        return obj.y;
    }

    @Test
    public void testSimpleLoop() {
        test("testSimpleLoopSnippet", Constant.forInt(1));
    }

    public int testSimpleLoopSnippet(int a) {
        TestObject obj = new TestObject(1, 2);
        for (int i = 0; i < a; i++) {
            notInlineable();
        }
        return obj.x;
    }

    private void test(final String snippet, final Constant expectedResult) {
        Debug.scope("EscapeAnalysisTest", new DebugDumpScope(snippet), new Runnable() {
            public void run() {
                StructuredGraph graph = parse(snippet);
                for (Invoke n : graph.getInvokes()) {
                    n.node().setProbability(100000);
                }

                new InliningPhase(null, runtime(), null, null, null, getDefaultPhasePlan(), OptimisticOptimizations.ALL).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);
                Debug.dump(graph, "Graph");
                new PartialEscapeAnalysisPhase(null, runtime(), null).apply(graph);
                new CullFrameStatesPhase().apply(graph);
                new CanonicalizerPhase(null, runtime(), null).apply(graph);
                Debug.dump(graph, "Graph");
                int retCount = 0;
                for (ReturnNode ret : graph.getNodes(ReturnNode.class)) {
                    Assert.assertTrue(ret.result().isConstant());
                    Assert.assertEquals(ret.result().asConstant(), expectedResult);
                    retCount++;
                }
                Assert.assertEquals(1, retCount);
                int newInstanceCount = 0;
                for (@SuppressWarnings("unused") NewInstanceNode n : graph.getNodes(NewInstanceNode.class)) {
                    newInstanceCount++;
                }
                for (@SuppressWarnings("unused") NewObjectArrayNode n : graph.getNodes(NewObjectArrayNode.class)) {
                    newInstanceCount++;
                }
                Assert.assertEquals(0, newInstanceCount);
            }
        });
    }
}
