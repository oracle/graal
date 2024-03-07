/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.SpectrePHTMitigations.NonDeoptGuardTargets;
import static jdk.graal.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTBarriers;
import static jdk.graal.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTIndexMasking;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.core.common.SpectrePHTMitigations;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.phases.Phase;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * This phase sets the {@linkplain AbstractBeginNode#setHasSpeculationFence() speculation fence}
 * flag on {@linkplain AbstractBeginNode begin nodes} in order to mitigate speculative execution
 * attacks.
 */
public class InsertGuardFencesPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        SpectrePHTMitigations mitigations = SpectrePHTBarriers.getValue(graph.getOptions());
        if (mitigations == SpectrePHTMitigations.None || mitigations == SpectrePHTMitigations.AllTargets) {
            return;
        }
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeFrequency(true).build();
        for (AbstractBeginNode beginNode : graph.getNodes(AbstractBeginNode.TYPE)) {
            if (hasPotentialUnsafeAccess(cfg, beginNode)) {
                graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Adding speculation fence at %s because of unguarded fixed access", beginNode);
                beginNode.setHasSpeculationFence();
                continue;
            }
            if (hasGuardUsages(beginNode)) {
                if (mitigations == NonDeoptGuardTargets) {
                    if (isDeoptGuard(beginNode)) {
                        graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Skipping deoptimizing guard speculation fence at %s", beginNode);
                        continue;
                    }
                }
                if (SpectrePHTIndexMasking.getValue(graph.getOptions())) {
                    if (isBoundsCheckGuard(beginNode)) {
                        graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Skipping bounds-check speculation fence at %s", beginNode);
                        continue;
                    }
                }
                if (graph.getDebug().isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                    graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Adding speculation fence for %s at %s", guardUsages(beginNode), beginNode);
                } else {
                    graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Adding speculation fence at %s", beginNode);
                }
                beginNode.setHasSpeculationFence();
            } else {
                graph.getDebug().log(DebugContext.DETAILED_LEVEL, "No guards on %s", beginNode);
            }
        }
    }

    /**
     * Determine if, after guard lowering during mid tier where regular reads are still floating, a
     * fixed access node (read/write) without a guard is inside this block, if so this means the
     * block has an unguarded memory access thus we need to emit a fence.
     */
    private static boolean hasPotentialUnsafeAccess(ControlFlowGraph cfg, AbstractBeginNode beginNode) {
        for (FixedNode n : cfg.blockFor(beginNode).getNodes()) {
            if (n instanceof FixedAccessNode && ((FixedAccessNode) n).getGuard() == null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeoptGuard(AbstractBeginNode beginNode) {
        if (!(beginNode.predecessor() instanceof IfNode)) {
            return false;
        }
        IfNode ifNode = (IfNode) beginNode.predecessor();
        AbstractBeginNode otherBegin;
        if (ifNode.trueSuccessor() == beginNode) {
            otherBegin = ifNode.falseSuccessor();
        } else {
            assert ifNode.falseSuccessor() == beginNode : Assertions.errorMessage(ifNode, ifNode.falseSuccessor(), beginNode);
            otherBegin = ifNode.trueSuccessor();
        }
        if (!(otherBegin.next() instanceof DeoptimizeNode)) {
            return false;
        }
        DeoptimizeNode deopt = (DeoptimizeNode) otherBegin.next();
        return deopt.getAction().doesInvalidateCompilation();
    }

    public static final IntegerStamp POSITIVE_ARRAY_INDEX_STAMP = IntegerStamp.create(32, 0, Integer.MAX_VALUE - 1);

    private static boolean isBoundsCheckGuard(AbstractBeginNode beginNode) {
        if (!(beginNode.predecessor() instanceof IfNode)) {
            return false;
        }
        IfNode ifNode = (IfNode) beginNode.predecessor();
        AbstractBeginNode otherBegin;
        if (ifNode.trueSuccessor() == beginNode) {
            otherBegin = ifNode.falseSuccessor();
        } else {
            assert ifNode.falseSuccessor() == beginNode : Assertions.errorMessage(ifNode, ifNode.falseSuccessor(), beginNode);
            otherBegin = ifNode.trueSuccessor();
        }
        if (otherBegin.next() instanceof DeoptimizeNode) {
            DeoptimizeNode deopt = (DeoptimizeNode) otherBegin.next();
            if (deopt.getReason() == DeoptimizationReason.BoundsCheckException && !hasMultipleGuardUsages(beginNode)) {
                return true;
            }
        } else if (otherBegin instanceof LoopExitNode && beginNode.usages().filter(MultiGuardNode.class).isNotEmpty() && !hasMultipleGuardUsages(beginNode)) {
            return true;
        }

        for (Node usage : beginNode.usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.getInputType() == InputType.Guard && pos.get(usage) == beginNode) {
                    if (usage instanceof PiNode) {
                        if (!((PiNode) usage).piStamp().equals(POSITIVE_ARRAY_INDEX_STAMP)) {
                            return false;
                        }
                    } else if (usage instanceof MemoryAccess) {
                        if (!NamedLocationIdentity.isArrayLocation(((MemoryAccess) usage).getLocationIdentity())) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                    break;
                }
            }
        }

        return true;
    }

    private static boolean hasGuardUsages(Node n) {
        for (Node usage : n.usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.getInputType() == InputType.Guard && pos.get(usage) == n) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMultipleGuardUsages(Node n) {
        boolean foundOne = false;
        for (Node usage : n.usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.getInputType() == InputType.Guard && pos.get(usage) == n) {
                    if (foundOne) {
                        return true;
                    }
                    foundOne = true;
                }
            }
        }
        return false;
    }

    private static List<Node> guardUsages(Node n) {
        List<Node> ret = new ArrayList<>();
        for (Node usage : n.usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.getInputType() == InputType.Guard && pos.get(usage) == n) {
                    ret.add(usage);
                }
            }
        }
        return ret;
    }
}
