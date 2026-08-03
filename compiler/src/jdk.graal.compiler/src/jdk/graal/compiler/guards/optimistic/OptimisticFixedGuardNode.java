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

import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractFixedGuardNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;

@NodeInfo(nameTemplate = "OptimisticFixedGuard(!={p#negated}) {p#reason/s}", allowedUsageTypes = Guard, cycles = CYCLES_1, size = SIZE_1)
public class OptimisticFixedGuardNode extends AbstractFixedGuardNode implements IterableNodeType {

    public static final NodeClass<OptimisticFixedGuardNode> TYPE = NodeClass.create(OptimisticFixedGuardNode.class);

    public OptimisticFixedGuardNode(LogicNode condition, DeoptimizationReason reason, DeoptimizationAction action, SpeculationLog.Speculation speculation, boolean negated,
                    NodeSourcePosition noDeoptSuccessorPosition) {
        super(TYPE, condition, reason, action, speculation, negated, noDeoptSuccessorPosition);
    }

    public OptimisticFixedGuardNode(NodeClass<? extends OptimisticFixedGuardNode> type, LogicNode condition, DeoptimizationReason reason, DeoptimizationAction action,
                    SpeculationLog.Speculation speculation, boolean negated,
                    NodeSourcePosition noDeoptSuccessorPosition) {
        super(type, condition, reason, action, speculation, negated, noDeoptSuccessorPosition);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        super.simplify(tool);

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
                graph().removeFixed(this);
            }
        }
    }
}
