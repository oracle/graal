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
package com.oracle.max.graal.compiler.tests;

import java.util.*;

import org.junit.*;

import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0.
 * Then boxing elimination is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class BoxingEliminationTest extends GraphTest {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    public static int referenceSnippet(int a) {
        return 1;
    }

    public static Integer boxedInteger() {
        return 1;
    }

    public static Object boxedObject() {
        return 1;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    public static int test1Snippet(int a) {
        return boxedInteger();
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    public static int test2Snippet(int a) {
        return (Integer) boxedObject();
    }
    @Test
    public void test3() {
        test("test3Snippet");
    }

    public static int test3Snippet(int a) {
        int b = boxedInteger();
        if (b < 0) {
            b = boxedInteger();
        }
        return b;
    }

    private void test(String snippet) {
        StructuredGraph graph = parse(snippet);
        BoxingMethodPool pool = new BoxingMethodPool(runtime());
        IdentifyBoxingPhase identifyBoxingPhase = new IdentifyBoxingPhase(pool);
        PhasePlan phasePlan = new PhasePlan();
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, identifyBoxingPhase);
        identifyBoxingPhase.apply(graph);
        LocalNode local = graph.getNodes(LocalNode.class).iterator().next();
        ConstantNode constant = ConstantNode.forInt(0, graph);
        for (Node n : local.usages().snapshot()) {
            if (n instanceof FrameState) {
                // Do not replace.
            } else {
                n.replaceFirstInput(local, constant);
            }
        }
        Collection<Invoke> hints = new ArrayList<Invoke>();
        for (Invoke invoke : graph.getInvokes()) {
            hints.add(invoke);
        }
        new InliningPhase(null, runtime(), hints, null, phasePlan).apply(graph);
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        print(graph);
        new BoxingEliminationPhase(runtime()).apply(graph);
        print(graph);
        new ExpandBoxingNodesPhase(pool).apply(graph);
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        new DeadCodeEliminationPhase().apply(graph);
        StructuredGraph referenceGraph = parse(REFERENCE_SNIPPET);
        assertEquals(referenceGraph, graph);
    }
}
