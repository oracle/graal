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
package com.oracle.graal.compiler.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;

public class LockEliminationTest extends GraalCompilerTest {

    static class A {

        int value;

        public synchronized int getValue() {
            return value;
        }
    }

    static int field1;
    static int field2;

    public static void testSynchronizedSnippet(A x, A y) {
        synchronized (x) {
            field1 = x.value;
        }
        synchronized (x) {
            field2 = y.value;
        }
    }

    @Test
    public void testLock() {
        test("testSynchronizedSnippet", new A(), new A());

        StructuredGraph graph = getGraph("testSynchronizedSnippet");
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), null));
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(MonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    public static void testSynchronizedMethodSnippet(A x) {
        int value1 = x.getValue();
        int value2 = x.getValue();
        field1 = value1;
        field2 = value2;
    }

    @Test
    public void testSynchronizedMethod() {
        test("testSynchronizedMethodSnippet", new A());

        StructuredGraph graph = getGraph("testSynchronizedMethodSnippet");
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), null));
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(MonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    private StructuredGraph getGraph(String snippet) {
        Method method = getMethod(snippet);
        StructuredGraph graph = parse(method);
        Assumptions assumptions = new Assumptions(true);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new CanonicalizerPhase(true).apply(graph, context);
        new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        new LoweringPhase(new CanonicalizerPhase(true), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        new ValueAnchorCleanupPhase().apply(graph);
        new LockEliminationPhase().apply(graph);
        return graph;
    }

}
