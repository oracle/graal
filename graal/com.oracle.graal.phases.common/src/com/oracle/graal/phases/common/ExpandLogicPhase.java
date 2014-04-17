/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.phases.*;

public class ExpandLogicPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (ShortCircuitOrNode logic : graph.getNodes(ShortCircuitOrNode.class)) {
            processBinary(logic);
        }
        assert graph.getNodes(ShortCircuitOrNode.class).isEmpty();
    }

    private static void processBinary(ShortCircuitOrNode binary) {
        while (binary.usages().isNotEmpty()) {
            Node usage = binary.usages().first();
            if (usage instanceof ShortCircuitOrNode) {
                processBinary((ShortCircuitOrNode) usage);
            } else if (usage instanceof IfNode) {
                processIf(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (IfNode) usage, binary.getShortCircuitProbability());
            } else if (usage instanceof ConditionalNode) {
                processConditional(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (ConditionalNode) usage);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        binary.safeDelete();
    }

    private static void processIf(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, IfNode ifNode, double shortCircuitProbability) {
        BeginNode trueTarget = ifNode.trueSuccessor();
        BeginNode falseTarget = ifNode.falseSuccessor();
        double firstIfProbability = shortCircuitProbability;
        /*
         * P(Y | not(X)) = P(Y inter not(X)) / P(not(X)) = (P(X union Y) - P(X)) / (1 - P(X))
         * 
         * P(X) = shortCircuitProbability
         * 
         * P(X union Y) = ifNode.probability(trueTarget)
         */
        double secondIfProbability = (ifNode.probability(trueTarget) - shortCircuitProbability) / (1 - shortCircuitProbability);
        secondIfProbability = Math.min(1.0, Math.max(0.0, secondIfProbability));
        if (Double.isNaN(secondIfProbability)) {
            secondIfProbability = 0.5;
        }
        ifNode.clearSuccessors();
        Graph graph = ifNode.graph();
        MergeNode trueTargetMerge = graph.add(new MergeNode());
        trueTargetMerge.setNext(trueTarget);
        EndNode firstTrueEnd = graph.add(new EndNode());
        EndNode secondTrueEnd = graph.add(new EndNode());
        trueTargetMerge.addForwardEnd(firstTrueEnd);
        trueTargetMerge.addForwardEnd(secondTrueEnd);
        BeginNode firstTrueTarget = BeginNode.begin(firstTrueEnd);
        BeginNode secondTrueTarget = BeginNode.begin(secondTrueEnd);
        BeginNode secondIf = BeginNode.begin(graph.add(new IfNode(y, yNegated ? falseTarget : secondTrueTarget, yNegated ? secondTrueTarget : falseTarget, secondIfProbability)));
        IfNode firstIf = graph.add(new IfNode(x, xNegated ? secondIf : firstTrueTarget, xNegated ? firstTrueTarget : secondIf, firstIfProbability));
        ifNode.replaceAtPredecessor(firstIf);
        ifNode.safeDelete();
    }

    private static void processConditional(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, ConditionalNode conditional) {
        ValueNode trueTarget = conditional.trueValue();
        ValueNode falseTarget = conditional.falseValue();
        Graph graph = conditional.graph();
        ConditionalNode secondConditional = graph.unique(new ConditionalNode(y, yNegated ? falseTarget : trueTarget, yNegated ? trueTarget : falseTarget));
        ConditionalNode firstConditional = graph.unique(new ConditionalNode(x, xNegated ? secondConditional : trueTarget, xNegated ? trueTarget : secondConditional));
        conditional.replaceAndDelete(firstConditional);
    }
}
