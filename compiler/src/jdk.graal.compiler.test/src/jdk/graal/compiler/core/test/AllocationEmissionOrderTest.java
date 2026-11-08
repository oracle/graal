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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;

public class AllocationEmissionOrderTest extends GraalCompilerTest {

    static class Root {
        Middle child;
    }

    static class Middle {
        Leaf child;
    }

    static class Leaf {
    }

    static class Cycle {
        Cycle next;
    }

    public static Object reverseDependenciesSnippet() {
        Root root = new Root();
        Middle middle = new Middle();
        Leaf leaf = new Leaf();
        root.child = middle;
        middle.child = leaf;
        return root;
    }

    public static Object cyclicDependenciesSnippet() {
        Cycle first = new Cycle();
        Cycle second = new Cycle();
        first.next = second;
        second.next = first;
        return first;
    }

    private static List<String> expectedAllocationOrder(StructuredGraph graph) {
        if (graph.method().getName().equals("cyclicDependenciesSnippet")) {
            return List.of(Cycle.class.getName(), Cycle.class.getName());
        }
        return List.of(Leaf.class.getName(), Middle.class.getName(), Root.class.getName());
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        CommitAllocationNode commit = graph.getNodes().filter(CommitAllocationNode.class).first();
        Assert.assertNotNull("Expected escape analysis to materialize the object chain", commit);
        Assert.assertEquals(expectedAllocationOrder(graph).size(), commit.getVirtualObjects().size());
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        List<String> allocationOrder = new ArrayList<>();
        for (FixedNode node = graph.start(); node instanceof FixedWithNextNode fixed; node = fixed.next()) {
            if (node instanceof NewInstanceNode allocation) {
                allocationOrder.add(allocation.instanceClass().toJavaName());
            }
        }
        if (allocationOrder.isEmpty() && !(getLowerer() instanceof DefaultJavaLoweringProvider)) {
            // Bulk allocation can branch before the scalar slow-path allocations.
            return;
        }
        Assert.assertEquals("Acyclic materializations should allocate dependencies before their users", expectedAllocationOrder(graph), allocationOrder);
    }

    @Test
    public void testReverseDependencies() throws Throwable {
        Root root = (Root) getCode(getResolvedJavaMethod("reverseDependenciesSnippet")).executeVarargs();
        Assert.assertNotNull(root.child.child);
    }

    @Test
    public void testCyclicDependencies() throws Throwable {
        Cycle cycle = (Cycle) getCode(getResolvedJavaMethod("cyclicDependenciesSnippet")).executeVarargs();
        Assert.assertNotSame(cycle, cycle.next);
        Assert.assertSame(cycle, cycle.next.next);
    }
}
