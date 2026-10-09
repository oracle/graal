/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.util.GraphOrder;

/**
 * Exercises peeling when allocation commits inside and outside a loop share a virtual descriptor.
 * The fixture constructs this topology explicitly, independently of escape-analysis heuristics.
 */
public class LoopPeelingAllocationTest extends GraalCompilerTest {

    public static volatile Object sink;

    public static void allocationSnippet(int iterations) {
        Object outside = new Object();
        for (int i = 0; i < iterations; i++) {
            Object inside = new Object();
            sink = outside;
            sink = inside;
        }
    }

    @Test
    public void separateDescriptors() {
        StructuredGraph graph = peel(false);
        assertAllocationMembership(graph);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        assertAllocationMembership(graph);
    }

    @Test
    public void sharedDescriptorMembershipAfterPeeling() {
        assertAllocationMembership(peel(true));
    }

    @Test
    public void sharedDescriptorCanonicalizationAfterPeeling() {
        StructuredGraph graph = peel(true);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        assertAllocationMembership(graph);
    }

    @Test
    public void sharedDescriptorRepeatedPeeling() {
        StructuredGraph graph = peel(true);
        for (int peelings = 2; peelings <= 3; peelings++) {
            LoopTransformations.peel(onlyLoop(graph));
            assertAllocationMembership(graph);
            Assert.assertEquals(peelings + 2, graph.getNodes().filter(CommitAllocationNode.class).count());
            Assert.assertEquals(peelings + 1, graph.getNodes().filter(VirtualInstanceNode.class).count());
            for (CommitAllocationNode commit : graph.getNodes().filter(CommitAllocationNode.class)) {
                Assert.assertEquals(1, commit.usages().filter(AllocatedObjectNode.class).count());
            }
            createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
            assertAllocationMembership(graph);
            Assert.assertTrue(graph.verify());
            Assert.assertTrue(GraphOrder.assertSchedulableGraph(graph));
        }
    }

    private StructuredGraph peel(boolean shareDescriptor) {
        StructuredGraph graph = parseEager("allocationSnippet", StructuredGraph.AllowAssumptions.NO);
        new DisableOverflownCountedLoopsPhase().apply(graph);
        Loop originalLoop = onlyLoop(graph);
        List<NewInstanceNode> allocations = graph.getNodes().filter(NewInstanceNode.class).snapshot();
        Assert.assertEquals(2, allocations.size());
        NewInstanceNode outside = null;
        NewInstanceNode inside = null;
        for (NewInstanceNode allocation : allocations) {
            if (originalLoop.inside().contains(allocation)) {
                inside = allocation;
            } else {
                outside = allocation;
            }
        }
        Assert.assertNotNull(outside);
        Assert.assertNotNull(inside);

        VirtualInstanceNode outsideDescriptor = graph.addWithoutUnique(new VirtualInstanceNode(getMetaAccess().lookupJavaType(Object.class), true));
        VirtualInstanceNode insideDescriptor = shareDescriptor ? outsideDescriptor : graph.addWithoutUnique(new VirtualInstanceNode(getMetaAccess().lookupJavaType(Object.class), true));
        CommitAllocationNode outsideCommit = materialize(graph, outside, outsideDescriptor);
        CommitAllocationNode insideCommit = materialize(graph, inside, insideDescriptor);

        assertAllocationMembership(graph);
        Assert.assertTrue(graph.verify());
        Assert.assertTrue(GraphOrder.assertSchedulableGraph(graph));

        Loop loop = onlyLoop(graph);
        Assert.assertFalse(loop.inside().contains(outsideCommit));
        Assert.assertTrue(loop.inside().contains(insideCommit));
        LoopTransformations.peel(loop);
        Assert.assertEquals(3, graph.getNodes().filter(CommitAllocationNode.class).count());
        return graph;
    }

    private Loop onlyLoop(StructuredGraph graph) {
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        return loops.loops().iterator().next();
    }

    private static CommitAllocationNode materialize(StructuredGraph graph, NewInstanceNode allocation, VirtualInstanceNode descriptor) {
        Assert.assertEquals(0, descriptor.entryCount());
        CommitAllocationNode commit = graph.add(new CommitAllocationNode());
        commit.getVirtualObjects().add(descriptor);
        commit.addLocks(List.of());
        commit.getEnsureVirtual().add(false);
        AllocatedObjectNode projection = graph.addWithoutUnique(new AllocatedObjectNode(descriptor));
        projection.setCommit(commit);
        allocation.replaceAtUsages(projection);
        graph.replaceFixedWithFixed(allocation, commit);
        return commit;
    }

    private static void assertAllocationMembership(StructuredGraph graph) {
        for (AllocatedObjectNode projection : graph.getNodes().filter(AllocatedObjectNode.class)) {
            CommitAllocationNode commit = projection.getCommit();
            Assert.assertNotNull(commit);
            Assert.assertTrue(projection + " selects " + projection.getVirtualObject() + " from " + commit + " containing " + commit.getVirtualObjects(),
                            commit.getVirtualObjects().contains(projection.getVirtualObject()));
        }
    }
}
