/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Check that multiple bounds checks are correctly grouped together.
 */
public class ConditionalEliminationTest14 extends ConditionalEliminationTestBase {

    public static void test1Snippet(Object[] args) {
        Object a5 = args[5];
        Object a7 = args[7];
        Object a6 = args[6];

        /*
         * The order of the conditions matters: The scheduler processes the floating reads for the
         * array loads in the order of the conditions here, and we want the index 7 access to be
         * processed before the index 6 access.
         */
        if (a5 != null && a7 != null && a6 != null) {
            sink1 = 1;
        }
        sink0 = 0;
    }

    @Test
    public void test1() {
        StructuredGraph graph = parseEager("test1Snippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        CoreProviders context = getProviders();

        /* Convert the LoadIndexNode to ReadNode with floating guards. */
        new HighTierLoweringPhase(canonicalizer).apply(graph, context);
        /* Convert the ReadNode to FloatingReadNode. */
        new FloatingReadPhase(canonicalizer).apply(graph, context);
        /* Apply the phase that we want to test. */
        new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);

        Assert.assertEquals("All guards must be floating", 0, graph.getNodes(FixedGuardNode.TYPE).count());
        Assert.assertEquals("All array accesses must have been lowered", 0, graph.getNodes().filter(LoadIndexedNode.class).count());
        Assert.assertEquals("All reads must be floating", 0, graph.getNodes().filter(ReadNode.class).count());
        Assert.assertEquals("Must have floating reads (3 array accesses, 1 array length)", 4, graph.getNodes().filter(FloatingReadNode.class).count());

        NodeIterable<GuardNode> boundsChecks = graph.getNodes(GuardNode.TYPE).filter(n -> ((GuardNode) n).getReason() == DeoptimizationReason.BoundsCheckException);
        Assert.assertEquals("Must have only 1 bounds check remaining", 1, boundsChecks.count());
        LogicNode condition = boundsChecks.first().getCondition();
        Assert.assertTrue("Bounds check must check for array length 8", condition instanceof IntegerBelowNode && ((IntegerBelowNode) condition).getY().valueEquals(ConstantNode.forInt(8)));
    }
}
