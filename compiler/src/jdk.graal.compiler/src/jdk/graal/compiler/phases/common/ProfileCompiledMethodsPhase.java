/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.serviceprovider.GraalServices.getSavedProperty;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConvertNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.DynamicCounterNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.util.EconomicHashSet;

/**
 * This phase add counters for the dynamically executed number of nodes. Incrementing the counter
 * for each node would be too costly, so this phase takes the compromise that it trusts split
 * probabilities, but not loop frequencies. This means that it will insert counters at the start of
 * a method and at each loop header.
 *
 * A schedule is created so that floating nodes can also be taken into account. The weight of a node
 * is determined heuristically in the {@link ProfileCompiledMethodsPhase#getNodeWeight(Node)}
 * method.
 *
 * Additionally, there's a second counter that's only increased for code sections without invokes.
 */
public class ProfileCompiledMethodsPhase extends Phase {

    private static final String GROUP_NAME = "~profiled weight";
    private static final String GROUP_NAME_WITHOUT = "~profiled weight (invoke-free sections)";
    private static final String GROUP_NAME_INVOKES = "~profiled invokes";

    private static final boolean WITH_SECTION_HEADER = Boolean.parseBoolean(getSavedProperty("ProfileCompiledMethodsPhase.WITH_SECTION_HEADER", "false"));
    private static final boolean WITH_INVOKE_FREE_SECTIONS = Boolean.parseBoolean(getSavedProperty("ProfileCompiledMethodsPhase.WITH_FREE_SECTIONS", "false"));
    private static final boolean WITH_INVOKES = Boolean.parseBoolean(getSavedProperty("ProfileCompiledMethodsPhase.WITH_INVOKES", "true"));

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.getDefaultStrategy(graph.getOptions()), true);
        ControlFlowGraph cfg = graph.getLastSchedule().getCFG();
        for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
            double loopProbability = cfg.blockFor(loop.getHeader().getBeginNode()).getRelativeFrequency();
            if (loopProbability > (1D / Integer.MAX_VALUE)) {
                addSectionCounters(loop.getHeader().getBeginNode(), loop.getBlocks(), loop.getChildren(), graph.getLastSchedule(), cfg);
            }
        }
        // don't put the counter increase directly after the start (problems with OSR)
        FixedWithNextNode current = graph.start();
        while (current.next() instanceof FixedWithNextNode) {
            current = (FixedWithNextNode) current.next();
        }
        addSectionCounters(current, Arrays.asList(cfg.getBlocks()), cfg.getLoops(), graph.getLastSchedule(), cfg);

        if (WITH_INVOKES) {
            for (Node node : graph.getNodes()) {
                if (node instanceof Invoke) {
                    Invoke invoke = (Invoke) node;
                    DynamicCounterNode.addCounterBefore(GROUP_NAME_INVOKES, invoke.callTarget().targetName(), 1, true, invoke.asFixedNode());

                }
            }
        }
    }

    private static void addSectionCounters(FixedWithNextNode start, Collection<HIRBlock> sectionBlocks, Collection<CFGLoop<HIRBlock>> childLoops, ScheduleResult schedule, ControlFlowGraph cfg) {
        Set<HIRBlock> blocks = new EconomicHashSet<>(sectionBlocks);
        for (CFGLoop<HIRBlock> loop : childLoops) {
            blocks.removeAll(loop.getBlocks());
        }
        long increment = DynamicCounterNode.clampIncrement((long) (getSectionWeight(schedule, blocks) / cfg.blockFor(start).getRelativeFrequency()));
        DynamicCounterNode.addCounterBefore(GROUP_NAME, sectionHead(start), increment, true, start.next());
        if (WITH_INVOKE_FREE_SECTIONS && !hasInvoke(blocks)) {
            DynamicCounterNode.addCounterBefore(GROUP_NAME_WITHOUT, sectionHead(start), increment, true, start.next());
        }
    }

    private static String sectionHead(Node node) {
        if (WITH_SECTION_HEADER) {
            return node.toString();
        } else {
            return "";
        }
    }

    private static double getSectionWeight(ScheduleResult schedule, Collection<HIRBlock> blocks) {
        double count = 0;
        for (HIRBlock block : blocks) {
            double blockProbability = block.getRelativeFrequency();
            for (Node node : schedule.getBlockToNodesMap().get(block)) {
                count += blockProbability * getNodeWeight(node);
            }
        }
        return count;
    }

    private static double getNodeWeight(Node node) {
        if (node instanceof AbstractMergeNode) {
            return ((AbstractMergeNode) node).phiPredecessorCount();
        } else if (node instanceof AbstractBeginNode || node instanceof AbstractEndNode || node instanceof MonitorIdNode || node instanceof ConstantNode || node instanceof ParameterNode ||
                        node instanceof CallTargetNode || node instanceof ValueProxy || node instanceof VirtualObjectNode || node instanceof ReinterpretNode) {
            return 0;
        } else if (node instanceof AccessMonitorNode) {
            return 10;
        } else if (node instanceof FloatingReadNode) {
            return 2;
        } else if (node instanceof LogicNode || node instanceof ConvertNode || node instanceof NotNode) {
            return 1;
        } else if (node instanceof IntegerDivRemNode || node instanceof FloatDivNode || node instanceof RemNode) {
            return 10;
        } else if (node instanceof MulNode) {
            return 3;
        } else if (node instanceof Invoke) {
            return 5;
        } else if (node instanceof IfNode || node instanceof SafepointNode || node instanceof BinaryNode) {
            return 1;
        } else if (node instanceof SwitchNode) {
            return node.successors().count();
        } else if (node instanceof ReturnNode || node instanceof UnwindNode || node instanceof DeoptimizeNode) {
            return node.successors().count();
        } else if (node instanceof AbstractNewObjectNode) {
            return 10;
        } else if (node instanceof VirtualState) {
            return 0;
        }
        return 2;
    }

    private static boolean hasInvoke(Collection<HIRBlock> blocks) {
        for (HIRBlock block : blocks) {
            for (FixedNode fixed : block.getNodes()) {
                if (fixed instanceof Invoke) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean checkContract() {
        return false;
    }

}
