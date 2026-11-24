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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.debug.SideEffectNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;

public class NodesInLoopBranchTest extends GraalCompilerTest {

    private static int loopSnippet(int n) {
        for (int i = 0; i < n; i++) {
            if ((i & 1) == 0) {
                GraalDirectives.sideEffect();
            } else {
                GraalDirectives.sideEffect();
                GraalDirectives.sideEffect();
                GraalDirectives.sideEffect();
            }
        }
        return n;
    }

    @Test
    public void testNodesInLoopBranch() {
        DebugContext debug = getDebugContext();
        StructuredGraph graph = parseEager("loopSnippet", StructuredGraph.AllowAssumptions.NO);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());

        Loop loop = loops.loops().get(0);
        IfNode branchIf = getLoopBodyIf(loop);

        NodeBitMap trueBranchNodes = graph.createNodeBitMap();
        loop.nodesInLoopBranch(trueBranchNodes, branchIf.trueSuccessor());

        NodeBitMap falseBranchNodes = graph.createNodeBitMap();
        loop.nodesInLoopBranch(falseBranchNodes, branchIf.falseSuccessor());

        try (DebugContext.Scope _ = debug.scope("NodesInLoopBranchTest", new DebugDumpScope("loopSnippet"))) {
            Assert.assertEquals(1, trueBranchNodes.filter(SideEffectNode.class).count());
            Assert.assertEquals(3, falseBranchNodes.filter(SideEffectNode.class).count());
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private static IfNode getLoopBodyIf(Loop loop) {
        IfNode branchIf = null;
        for (IfNode ifNode : loop.whole().nodes().filter(IfNode.class)) {
            if (!loop.isCfgLoopExit(ifNode.trueSuccessor()) && !loop.isCfgLoopExit(ifNode.falseSuccessor())) {
                Assert.assertNull(branchIf);
                branchIf = ifNode;
            }
        }
        Assert.assertNotNull(branchIf);
        return branchIf;
    }

    private static void nestedLoopSnippet(int n, boolean flag) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (flag) {
                    GraalDirectives.sideEffect();
                } else {
                    GraalDirectives.sideEffect();
                    GraalDirectives.sideEffect();
                    GraalDirectives.sideEffect();
                }
            }
        }
    }

    @Test
    public void testBranchInNestedLoop() {
        StructuredGraph graph = parseEager("nestedLoopSnippet", StructuredGraph.AllowAssumptions.NO);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(2, loops.loops().size());
        Loop innerLoop = loops.innerFirst().getFirst();
        IfNode branchIf = null;
        for (IfNode ifNode : graph.getNodes(IfNode.TYPE)) {
            if (ifNode.condition().inputs().contains(graph.getParameter(1))) {
                Assert.assertNull(branchIf);
                branchIf = ifNode;
            }
        }
        Assert.assertNotNull(branchIf);
        NodeBitMap trueBranchNodes = graph.createNodeBitMap();
        innerLoop.nodesInLoopBranch(trueBranchNodes, branchIf.trueSuccessor());
        NodeBitMap falseBranchNodes = graph.createNodeBitMap();
        innerLoop.nodesInLoopBranch(falseBranchNodes, branchIf.falseSuccessor());
        // Parsing a boolean condition can negate the branch, so check both possible orientations.
        int trueCount = trueBranchNodes.filter(SideEffectNode.class).count();
        int falseCount = falseBranchNodes.filter(SideEffectNode.class).count();
        Assert.assertEquals(1, Math.min(trueCount, falseCount));
        Assert.assertEquals(3, Math.max(trueCount, falseCount));
    }
}
