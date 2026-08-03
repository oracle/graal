/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.guards.optimistic;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * A {@link GuardNode} that will be removed if only {@linkplain OptimisticGuardedNode
 * OptimisticGuardedNodes} depend on it.
 */
@NodeInfo(nameTemplate = "OptimisticGuard(!={p#negated}) {p#reason/s}", allowedUsageTypes = {InputType.Guard})
public final class OptimisticGuardNode extends GuardNode {
    public static final NodeClass<OptimisticGuardNode> TYPE = NodeClass.create(OptimisticGuardNode.class);

    public OptimisticGuardNode(LogicNode condition, AnchoringNode anchor, DeoptimizationReason reason, DeoptimizationAction action, SpeculationLog.Speculation speculation, boolean negated,
                    NodeSourcePosition noDeoptSuccessorPosition) {
        super(TYPE, condition, anchor, reason, action, negated, speculation, noDeoptSuccessorPosition);
    }

    @Override
    @SuppressWarnings("try")
    public FixedWithNextNode lowerGuard() {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            return graph().add(new OptimisticFixedGuardNode(getCondition(), getReason(), getAction(), getSpeculation(), isNegated(), getNoDeoptSuccessorPosition()));
        }
    }

    @Override
    @SuppressWarnings("try")
    public Node canonical(CanonicalizerTool tool) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            if (getCondition() instanceof LogicNegationNode) {
                LogicNegationNode negation = (LogicNegationNode) getCondition();
                return new OptimisticGuardNode(negation.getValue(), getAnchor(), getReason(), getAction(), getSpeculation(), !isNegated(), getNoDeoptSuccessorPosition());
            }
            if (getCondition() instanceof LogicConstantNode) {
                LogicConstantNode c = (LogicConstantNode) getCondition();
                if (c.getValue() == isNegated()) {
                    // this guard always fails, back out of all optimistic assumptions that depend
                    // on it
                    for (Node usage : usages().snapshot()) {
                        if (usage instanceof OptimisticGuardedNode) {
                            ((OptimisticGuardedNode) usage).revert();
                        }
                    }
                } else {
                    // this guard always succeeds, remove it
                    replaceAtUsages(null);
                    return null;
                }
            }
            return this;
        }
    }
}
