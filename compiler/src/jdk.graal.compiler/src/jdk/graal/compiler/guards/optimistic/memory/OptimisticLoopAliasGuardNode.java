/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.guards.optimistic.memory;

import static jdk.graal.compiler.nodeinfo.InputType.Guard;

import jdk.graal.compiler.guards.optimistic.OptimisticFixedGuardNode;
import jdk.graal.compiler.guards.optimistic.OptimisticGuardedNode;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * A node for associating {@linkplain OptimisticAliasingAnalysisPhase optimistic aliasing
 * information} with a loop begin node. That phase has two modes. First, a speculative mode that
 * inserts individual aliasing checks with deoptimizations for each pair of memory accesses in the
 * loop. Second, a non-speculative mode that computes an overall aliasing condition for the entire
 * loop. This node is used in this second mode to represent the aliasing information for the whole
 * loop in one place.
 * <p/>
 *
 * This node is intended to be used in a graph shape like the following:
 *
 * <pre>
 *        (some logic node)
 *                | condition
 *        OptimisticFixedGuard
 *                | guard
 *      OptimisticLoopAliasGuard
 *                | interIterationAliasingGuard
 *            LoopBegin
 * </pre>
 *
 * This shape is created by {@link OptimisticAliasingAnalysisPhase}. If the loop is consumed by loop
 * vectorization or some other optimization, this node will become unused and fold away. If the loop
 * survives, {@link OptimisticGuardsPhase} will {@link #revert()} this node, which will also remove
 * it. In both cases this node's input guard will also be removed by {@link OptimisticGuardsPhase}.
 * <p/>
 *
 * Note that this "guard" node has no condition itself, it just forwards another guard and its
 * condition in a way that makes {@link OptimisticGuardsPhase} do the right thing automatically.
 */
@NodeInfo(allowedUsageTypes = Guard, size = NodeSize.SIZE_0, cycles = NodeCycles.CYCLES_0)
public class OptimisticLoopAliasGuardNode extends FloatingNode implements OptimisticGuardedNode, GuardingNode {

    public static final NodeClass<OptimisticLoopAliasGuardNode> TYPE = NodeClass.create(OptimisticLoopAliasGuardNode.class);

    @OptionalInput(Guard) OptimisticFixedGuardNode guard;

    public OptimisticLoopAliasGuardNode(OptimisticFixedGuardNode guard) {
        super(TYPE, StampFactory.forVoid());
        this.guard = guard;
    }

    @Override
    public void revert() {
        for (Node usage : usages().snapshot()) {
            if (usage instanceof OptimisticGuardedNode) {
                ((OptimisticGuardedNode) usage).revert();
            }
        }
        if (isAlive()) {
            replaceAtUsages(null);
            GraphUtil.killWithUnusedFloatingInputs(this);
        }
    }

    @Override
    public OptimisticFixedGuardNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        OptimisticFixedGuardNode newGuard = (OptimisticFixedGuardNode) guard;
        updateUsages(this.guard, newGuard);
        this.guard = newGuard;
    }

    /**
     * Return a node that evaluates to {@code true} if there is aliasing in the loop. The returned
     * node may be a logic constant. It is not necessarily part of the graph.
     */
    public LogicNode isAliasing() {
        if (guard != null) {
            LogicNode condition = guard.condition();
            if (!guard.isNegated()) {
                condition = LogicNegationNode.create(condition);
            }
            return condition;
        } else {
            // The guard was optimized out because aliasing is impossible.
            return LogicConstantNode.contradiction();
        }
    }
}
