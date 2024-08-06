/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import org.junit.Assert;
import org.junit.Test;

/**
 * Collection of tests for {@link ConditionalEliminationPhase} including those that triggered bugs
 * in this phase.
 */
public class ConditionalEliminationTest15 extends ConditionalEliminationTestBase {

    private void checkNodeCount(String methodName, Class<? extends Node> nodeClass, int count) {
        StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES);

        CanonicalizerPhase canonicalizer = this.createCanonicalizerPhase();
        CoreProviders context = getProviders();

        new HighTierLoweringPhase(this.createCanonicalizerPhase()).apply(graph, context);
        canonicalizer.apply(graph, context);

        // Merge arr.length reads.
        new ReadEliminationPhase(canonicalizer).apply(graph, context);
        new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);

        getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "After ConditionalEliminationPhase");

        Assert.assertEquals(count, graph.getNodes().filter(nodeClass).count());
    }

    public static int testRedundantIntegerLessThanNode(int index, int[] arr) {
        while (arr[index] != 42) {
            if (index >= 0) { // redundant
                return 1;
            }
        }
        return 2;
    }

    public static int testRedundantIntegerLessThanNode2(int index, int[] arr) {
        while (arr[index] != 42) {
            if (index < arr.length) { // redundant
                return 1;
            }
        }
        return 2;
    }

    @Test
    public void testRedundantSignedLessThanNode() {
        checkNodeCount("testRedundantIntegerLessThanNode", IntegerLessThanNode.class, 0);
        checkNodeCount("testRedundantIntegerLessThanNode2", IntegerLessThanNode.class, 0);
    }
}
