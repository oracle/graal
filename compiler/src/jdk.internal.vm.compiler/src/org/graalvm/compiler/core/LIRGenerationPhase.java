/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core;

import java.util.List;

import org.graalvm.compiler.core.common.cfg.BasicBlock;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.ssa.SSAUtil;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.TargetDescription;

public class LIRGenerationPhase extends LIRPhase<LIRGenerationPhase.LIRGenerationContext> {

    public static final class LIRGenerationContext {
        private final NodeLIRBuilder nodeLirBuilder;
        private final LIRGeneratorTool lirGen;
        private final StructuredGraph graph;
        private final ScheduleResult schedule;

        public LIRGenerationContext(LIRGeneratorTool lirGen, NodeLIRBuilderTool nodeLirBuilder, StructuredGraph graph, ScheduleResult schedule) {
            this.nodeLirBuilder = (NodeLIRBuilder) nodeLirBuilder;
            this.lirGen = lirGen;
            this.graph = graph;
            this.schedule = schedule;
        }
    }

    private static final CounterKey instructionCounter = DebugContext.counter("GeneratedLIRInstructions");
    private static final CounterKey nodeCount = DebugContext.counter("FinalNodeCount");

    @Override
    protected final void run(TargetDescription target, LIRGenerationResult lirGenRes, LIRGenerationPhase.LIRGenerationContext context) {
        NodeLIRBuilder nodeLirBuilder = context.nodeLirBuilder;
        StructuredGraph graph = context.graph;
        ScheduleResult schedule = context.schedule;
        BasicBlock<?>[] blocks = lirGenRes.getLIR().getControlFlowGraph().getBlocks();
        for (BasicBlock<?> b : blocks) {
            matchBlock(nodeLirBuilder, (HIRBlock) b, schedule);
        }
        for (BasicBlock<?> b : blocks) {
            emitBlock(nodeLirBuilder, lirGenRes, (HIRBlock) b, graph, schedule.getBlockToNodesMap());
        }
        ((LIRGenerator) context.lirGen).beforeRegisterAllocation();
        assert SSAUtil.verifySSAForm(lirGenRes.getLIR());
        nodeCount.add(graph.getDebug(), graph.getNodeCount());
    }

    private static void emitBlock(NodeLIRBuilderTool nodeLirGen, LIRGenerationResult lirGenRes, HIRBlock b, StructuredGraph graph, BlockMap<List<Node>> blockMap) {
        assert !isProcessed(lirGenRes, b) : "Block already processed " + b;
        assert verifyPredecessors(lirGenRes, b);
        nodeLirGen.doBlock(b, graph, blockMap);
        LIR lir = lirGenRes.getLIR();
        DebugContext debug = lir.getDebug();
        instructionCounter.add(debug, lir.getLIRforBlock(b).size());
    }

    private static void matchBlock(NodeLIRBuilder nodeLirGen, HIRBlock b, ScheduleResult schedule) {
        nodeLirGen.matchBlock(b, schedule);
    }

    private static boolean verifyPredecessors(LIRGenerationResult lirGenRes, HIRBlock block) {
        for (int i = 0; i < block.getPredecessorCount(); i++) {
            HIRBlock pred = block.getPredecessorAt(i);
            if (!block.isLoopHeader() || !pred.isLoopEnd()) {
                assert isProcessed(lirGenRes, pred) : "Predecessor not yet processed " + pred;
            }
        }
        return true;
    }

    private static boolean isProcessed(LIRGenerationResult lirGenRes, HIRBlock b) {
        return lirGenRes.getLIR().getLIRforBlock(b) != null;
    }

}
