/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.debug.DebugOptions.DumpOnError;

import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This test verifies that the backend detects graphs for which the scheduling is broken, i.e. input
 * values are scheduled in non-dominating blocks causing illegal flow of values in the control flow
 * graph.
 */
public class UnschedulableGraphTest extends GraalCompilerTest {

    public static int snippet01(int a, int b, int c) {
        if (GraalDirectives.sideEffect(a) == b) {
            GraalDirectives.sideEffect(b);
            GraalDirectives.controlFlowAnchor();
        } else {
            GraalDirectives.sideEffect(c);
            GraalDirectives.controlFlowAnchor();
        }
        GraalDirectives.sideEffect();
        GraalDirectives.controlFlowAnchor();
        return GraalDirectives.opaque(-a) + GraalDirectives.opaque(-b);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        ScheduleResult res = graph.getLastSchedule();
        BlockMap<List<Node>> blockToNode = res.getBlockToNodesMap();
        NodeMap<HIRBlock> nodeToBlock = res.getNodeToBlockMap();
        Assert.assertEquals(4, res.getCFG().getBlocks().length);
        HIRBlock split = res.getCFG().getStartBlock();
        Assert.assertEquals(2, split.getSuccessorCount());
        HIRBlock trueSucc = split.getSuccessorAt(0);
        HIRBlock falseSucc = split.getSuccessorAt(1);
        HIRBlock merge = trueSucc.getFirstSuccessor();
        Assert.assertEquals(merge, falseSucc.getFirstSuccessor());
        for (OpaqueNode op : graph.getNodes().filter(OpaqueNode.class)) {
            Assert.assertEquals(merge, res.getNodeToBlockMap().get(op));
        }
        int k = 0;
        // destroy dominance relation for NegateNode nodes, they no longer dominate the addition
        for (NegateNode op : graph.getNodes().filter(NegateNode.class)) {
            final HIRBlock nonDominatingBlock = k++ % 2 == 0 ? trueSucc : falseSucc;
            blockToNode.get(merge).remove(op);
            blockToNode.get(nonDominatingBlock).add(0, op);
            nodeToBlock.set(op, nonDominatingBlock);
        }
        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "After changing constant schedule");
    }

    private DebugContext getDebugContext(ResolvedJavaMethod method) {
        OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
        return getDebugContext(options, null, method);
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        /*
         * Disable optimistic optimizations to make the test more resilient towards wrong/strange
         * profiling information and the removal of never executed code as this can cause the
         * assertions in this test to fail since the control flow graph is in an uncommon shape.
         */
        return OptimisticOptimizations.NONE;
    }

    @Test
    @SuppressWarnings("try")
    public void test01() {
        ResolvedJavaMethod method = getResolvedJavaMethod("snippet01");
        try (AutoCloseable c = new TTY.Filter();
                        DebugContext debug = getDebugContext(method);
                        DebugCloseable s = debug.disableIntercept()) {
            test(debug.getOptions(), "snippet01", 0, 1, 2);
            Assert.fail("Compilation should not reach this point, must throw an exception before");
        } catch (Throwable t) {
            if (t.getMessage().contains("liveIn set of first block must be empty")) {
                return;
            }
            throw new AssertionError(t);
        }
    }
}
