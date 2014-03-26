/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class UseTrappingNullChecksPhase extends BasePhase<LowTierContext> {

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        if (context.getTarget().implicitNullCheckLimit <= 0) {
            return;
        }
        assert graph.getGuardsStage().ordinal() >= GuardsStage.AFTER_FSA.ordinal();

        for (DeoptimizeNode deopt : graph.getNodes(DeoptimizeNode.class)) {
            tryUseTrappingNullCheck(deopt);
        }
    }

    private static void tryUseTrappingNullCheck(DeoptimizeNode deopt) {
        if (deopt.reason() != DeoptimizationReason.NullCheckException) {
            return;
        }
        if (deopt.getSpeculation() != null && !deopt.getSpeculation().equals(Constant.NULL_OBJECT)) {
            return;
        }
        Node predecessor = deopt.predecessor();
        Node branch = null;
        while (predecessor instanceof AbstractBeginNode) {
            branch = predecessor;
            predecessor = predecessor.predecessor();
        }
        if (predecessor instanceof IfNode) {
            IfNode ifNode = (IfNode) predecessor;
            if (branch != ifNode.trueSuccessor()) {
                return;
            }
            LogicNode condition = ifNode.condition();
            if (condition instanceof IsNullNode) {
                replaceWithTrappingNullCheck(deopt, ifNode, condition);
            }
        }
    }

    private static void replaceWithTrappingNullCheck(DeoptimizeNode deopt, IfNode ifNode, LogicNode condition) {
        IsNullNode isNullNode = (IsNullNode) condition;
        AbstractBeginNode nonTrappingContinuation = ifNode.falseSuccessor();
        AbstractBeginNode trappingContinuation = ifNode.trueSuccessor();
        NullCheckNode trappingNullCheck = deopt.graph().add(new NullCheckNode(isNullNode.object()));
        trappingNullCheck.setStateBefore(deopt.stateBefore());
        deopt.graph().replaceSplit(ifNode, trappingNullCheck, nonTrappingContinuation);

        GraphUtil.killCFG(trappingContinuation);
        if (isNullNode.usages().isEmpty()) {
            GraphUtil.killWithUnusedFloatingInputs(isNullNode);
        }
    }
}
