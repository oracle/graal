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
package com.oracle.graal.compiler.tests;

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import java.util.*;

import org.junit.*;

import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.graal.compiler.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0.
 * Then boxing elimination is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class BoxingEliminationTest extends GraphTest {
    private static final Short s = 2;
    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    @SuppressWarnings("all")
    public static short referenceSnippet(short a) {
        return 1;
    }

    public static Short boxedShort() {
        return 1;
    }

    public static Object boxedObject() {
        return (short) 1;
    }

    public static Short constantBoxedShort() {
        return s;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    @SuppressWarnings("all")
    public static short test1Snippet(short a) {
        return boxedShort();
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    @SuppressWarnings("all")
    public static short test2Snippet(short a) {
        return (Short) boxedObject();
    }
    @Test
    public void test3() {
        test("test3Snippet");
    }

    @SuppressWarnings("all")
    public static short test3Snippet(short a) {
        short b = boxedShort();
        if (b < 0) {
            b = boxedShort();
        }
        return b;
    }

    @Test
    public void test4() {
        test("test4Snippet");
    }

    @SuppressWarnings("all")
    public static short test4Snippet(short a) {
        return constantBoxedShort();
    }

    private void test(final String snippet) {
        Debug.scope("BoxingEliminationTest", new DebugDumpScope(snippet), new Runnable() {
            @Override
            public void run() {
                StructuredGraph graph = parse(snippet);
                BoxingMethodPool pool = new BoxingMethodPool(runtime());
                IdentifyBoxingPhase identifyBoxingPhase = new IdentifyBoxingPhase(pool);
                PhasePlan phasePlan = getDefaultPhasePlan();
                phasePlan.addPhase(PhasePosition.AFTER_PARSING, identifyBoxingPhase);
                identifyBoxingPhase.apply(graph);
                LocalNode local = graph.getNodes(LocalNode.class).iterator().next();
                ConstantNode constant = ConstantNode.forShort((short) 0, graph);
                for (Node n : local.usages().filter(isNotA(FrameState.class)).snapshot()) {
                    n.replaceFirstInput(local, constant);
                }
                Collection<Invoke> hints = new ArrayList<>();
                for (Invoke invoke : graph.getInvokes()) {
                    hints.add(invoke);
                }

                new InliningPhase(null, runtime(), hints, null, phasePlan, ProfilingInfoConfiguration.ALL).apply(graph);
                new CanonicalizerPhase(null, runtime(), null).apply(graph);
                Debug.dump(graph, "Graph");
                new BoxingEliminationPhase().apply(graph);
                Debug.dump(graph, "Graph");
                new ExpandBoxingNodesPhase(pool).apply(graph);
                new CanonicalizerPhase(null, runtime(), null).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);
                StructuredGraph referenceGraph = parse(REFERENCE_SNIPPET);
                assertEquals(referenceGraph, graph);
            }
        });
    }
}
