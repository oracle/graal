/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;

/**
 * This phase add counters for the dynamically executed number of nodes. Incrementing the counter
 * for each node would be too costly, so this phase takes the compromise that it trusts split
 * probabilities, but not loop frequencies. This means that it will insert counters at the start of
 * a method and at each loop header.
 * 
 * A schedule is created so that floating nodes can also be taken into account. The weight of a node
 * is determined heuristically in the
 * {@link ProfileCompiledMethodsPhase#getNodeWeight(ScheduledNode)} method.
 * 
 * Additionally, there's a second counter that's only increased for code sections without invokes.
 */
public class ProfileCompiledMethodsPhase extends Phase {

    private static final String GROUP_NAME = "~profiled weight";
    private static final String GROUP_NAME_WITHOUT = "~profiled weight (invoke-free sections)";

    private static final boolean WITH_SECTION_HEADER = false;

    @Override
    protected void run(StructuredGraph graph) {
        ComputeProbabilityClosure closure = new ComputeProbabilityClosure(graph);
        NodesToDoubles probabilities = closure.apply();
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph, false);

        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        for (Loop loop : cfg.getLoops()) {
            double loopProbability = probabilities.get(loop.loopBegin());
            if (loopProbability > (1D / Integer.MAX_VALUE)) {
                addSectionCounters(loop.loopBegin(), loop.blocks, loop.children, schedule, probabilities);
            }
        }
        addSectionCounters(graph.start(), Arrays.asList(cfg.getBlocks()), Arrays.asList(cfg.getLoops()), schedule, probabilities);
    }

    private static void addSectionCounters(FixedWithNextNode start, Collection<Block> sectionBlocks, Collection<Loop> childLoops, SchedulePhase schedule, NodesToDoubles probabilities) {
        HashSet<Block> blocks = new HashSet<>(sectionBlocks);
        for (Loop loop : childLoops) {
            blocks.removeAll(loop.blocks);
        }
        double weight = getSectionWeight(schedule, probabilities, blocks) / probabilities.get(start);
        DynamicCounterNode.addCounterBefore(GROUP_NAME, sectionHead(start), (long) weight, true, start.next());
        if (!hasInvoke(blocks)) {
            DynamicCounterNode.addCounterBefore(GROUP_NAME_WITHOUT, sectionHead(start), (long) weight, true, start.next());
        }
    }

    private static String sectionHead(Node node) {
        if (WITH_SECTION_HEADER) {
            return node.toString();
        } else {
            return "";
        }
    }

    private static double getSectionWeight(SchedulePhase schedule, NodesToDoubles probabilities, Collection<Block> blocks) {
        double count = 0;
        for (Block block : blocks) {
            double blockProbability = probabilities.get(block.getBeginNode());
            for (ScheduledNode node : schedule.getBlockToNodesMap().get(block)) {
                count += blockProbability * getNodeWeight(node);
            }
        }
        return count;
    }

    private static double getNodeWeight(ScheduledNode node) {
        if (node instanceof MergeNode) {
            return ((MergeNode) node).phiPredecessorCount();
        } else if (node instanceof AbstractBeginNode || node instanceof AbstractEndNode || node instanceof MonitorIdNode || node instanceof ConstantNode || node instanceof ParameterNode ||
                        node instanceof CallTargetNode || node instanceof ValueProxy || node instanceof VirtualObjectNode || node instanceof ReinterpretNode) {
            return 0;
        } else if (node instanceof AccessMonitorNode) {
            return 10;
        } else if (node instanceof Access) {
            return 2;
        } else if (node instanceof LogicNode || node instanceof ConvertNode || node instanceof BinaryNode || node instanceof NotNode) {
            return 1;
        } else if (node instanceof IntegerDivNode || node instanceof FloatDivNode || node instanceof IntegerRemNode || node instanceof FloatRemNode) {
            return 10;
        } else if (node instanceof IntegerMulNode || node instanceof FloatMulNode) {
            return 3;
        } else if (node instanceof Invoke) {
            return 5;
        } else if (node instanceof IfNode || node instanceof SafepointNode) {
            return 1;
        } else if (node instanceof SwitchNode) {
            return node.successors().count();
        } else if (node instanceof ReturnNode || node instanceof UnwindNode || node instanceof DeoptimizeNode) {
            return node.successors().count();
        } else if (node instanceof AbstractNewObjectNode) {
            return 10;
        }
        return 2;
    }

    private static boolean hasInvoke(Collection<Block> blocks) {
        boolean hasInvoke = false;
        for (Block block : blocks) {
            for (FixedNode fixed : block.getNodes()) {
                if (fixed instanceof Invoke) {
                    hasInvoke = true;
                }
            }
        }
        return hasInvoke;
    }
}
