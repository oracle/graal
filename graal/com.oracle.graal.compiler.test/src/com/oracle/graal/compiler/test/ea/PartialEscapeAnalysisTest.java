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

import junit.framework.Assert;

import org.junit.Test;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.virtual.nodes.*;
import com.oracle.graal.virtual.phases.ea.experimental.*;

/**
 * In these test cases the probability of all invokes is set to a high value, such that an InliningPhase should inline
 * them all. After that, the PartialEscapeAnalysisPhase is expected to remove all allocations and return the correct
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
    public static Object test1Snippet(int a, int b) {
        TestObject obj = new TestObject(1, 2);
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

    public static Object test2Snippet(int a) {
        TestObject2 obj = new TestObject2(1, 2);
        obj.x = new TestObject2(obj, 3);
        if (a < 0) {
            ((TestObject2) obj.x).y = null;
            obj.y = null;
            return obj;
        } else {
            ((TestObject2) obj.x).y = null;
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

    @SafeVarargs
    final void testMaterialize(final String snippet, double expectedProbability, int expectedCount, Class<? extends Node>... invalidNodeClasses) {
        StructuredGraph result = processMethod(snippet);
        Assert.assertTrue("partial escape analysis should have removed all NewInstanceNode allocations", result.getNodes(NewInstanceNode.class).isEmpty());
        Assert.assertTrue("partial escape analysis should have removed all NewObjectArrayNode allocations", result.getNodes(NewObjectArrayNode.class).isEmpty());
        Assert.assertTrue("partial escape analysis should have removed all NewPrimitiveArrayNode allocations", result.getNodes(NewPrimitiveArrayNode.class).isEmpty());
        double probabilitySum = 0;
        int materializeCount = 0;
        for (MaterializeObjectNode materialize : result.getNodes(MaterializeObjectNode.class)) {
            probabilitySum += materialize.probability();
            materializeCount++;
        }
        Assert.assertEquals("unexpected number of MaterializeObjectNodes", expectedCount, materializeCount);
        Assert.assertEquals("unexpected probability of MaterializeObjectNodes", expectedProbability, probabilitySum, 0.01);
        for (Node node : result.getNodes()) {
            for (Class<? extends Node> clazz : invalidNodeClasses) {
                Assert.assertFalse("instance of invalid class: " + clazz.getSimpleName(), clazz.isInstance(node) && node.usages().isNotEmpty());
            }
        }
    }

    private StructuredGraph processMethod(final String snippet) {
        return Debug.scope(getClass().getSimpleName(), new Callable<StructuredGraph>() {
            @Override
            public StructuredGraph call() throws Exception {
                StructuredGraph graph = parse(snippet);
                new ComputeProbabilityPhase().apply(graph);
                for (Invoke n : graph.getInvokes()) {
                    n.node().setProbability(100000);
                }
                new InliningPhase(null, runtime(), null, null, null, getDefaultPhasePlan(), OptimisticOptimizations.ALL).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);
                new CanonicalizerPhase(null, runtime(), null).apply(graph);
//                TypeSystemTest.outputGraph(graph, "before EscapeAnalysis " + snippet);
                new SplitPartialEscapeAnalysisPhase(null).apply(graph);
//                TypeSystemTest.outputGraph(graph, "after EscapeAnalysis " + snippet);
                new CullFrameStatesPhase().apply(graph);
                new DeadCodeEliminationPhase().apply(graph);
                new CanonicalizerPhase(null, runtime(), null).apply(graph);
//                TypeSystemTest.outputGraph(graph, "after CullFrameStates " + snippet);
                return graph;
            }
        });
    }
}
