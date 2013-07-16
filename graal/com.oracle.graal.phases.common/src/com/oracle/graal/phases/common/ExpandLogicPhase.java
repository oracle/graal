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

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.phases.*;

public class ExpandLogicPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (LogicBinaryNode logic : graph.getNodes(LogicBinaryNode.class)) {
            processBinary(logic);
        }
        assert graph.getNodes(LogicBinaryNode.class).isEmpty();
    }

    private static void processBinary(LogicBinaryNode binary) {
        while (binary.usages().isNotEmpty()) {
            Node usage = binary.usages().first();
            if (usage instanceof LogicBinaryNode) {
                processBinary((LogicBinaryNode) usage);
            } else if (usage instanceof IfNode) {
                if (binary instanceof LogicConjunctionNode) {
                    processIf(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (IfNode) usage, false, binary.getShortCircuitProbability());
                } else if (binary instanceof LogicDisjunctionNode) {
                    processIf(binary.getX(), !binary.isXNegated(), binary.getY(), !binary.isYNegated(), (IfNode) usage, true, binary.getShortCircuitProbability());
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
            } else if (usage instanceof ConditionalNode) {
                if (binary instanceof LogicConjunctionNode) {
                    processConditional(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (ConditionalNode) usage, false);
                } else if (binary instanceof LogicDisjunctionNode) {
                    processConditional(binary.getX(), !binary.isXNegated(), binary.getY(), !binary.isYNegated(), (ConditionalNode) usage, true);
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        binary.safeDelete();
    }

    private static void processIf(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, IfNode ifNode, boolean negateTargets, double shortCircuitProbability) {
        AbstractBeginNode trueTarget = negateTargets ? ifNode.falseSuccessor() : ifNode.trueSuccessor();
        AbstractBeginNode falseTarget = negateTargets ? ifNode.trueSuccessor() : ifNode.falseSuccessor();
        double firstIfProbability = shortCircuitProbability;
        double secondIfProbability = 1 - ifNode.probability(trueTarget);
        ifNode.clearSuccessors();
        Graph graph = ifNode.graph();
        MergeNode falseTargetMerge = graph.add(new MergeNode());
        falseTargetMerge.setNext(falseTarget);
        EndNode firstFalseEnd = graph.add(new EndNode());
        EndNode secondFalseEnd = graph.add(new EndNode());
        falseTargetMerge.addForwardEnd(firstFalseEnd);
        falseTargetMerge.addForwardEnd(secondFalseEnd);
        AbstractBeginNode firstFalseTarget = AbstractBeginNode.begin(firstFalseEnd);
        AbstractBeginNode secondFalseTarget = AbstractBeginNode.begin(secondFalseEnd);
        AbstractBeginNode secondIf = AbstractBeginNode.begin(graph.add(new IfNode(y, yNegated ? firstFalseTarget : trueTarget, yNegated ? trueTarget : firstFalseTarget, secondIfProbability)));
        IfNode firstIf = graph.add(new IfNode(x, xNegated ? secondFalseTarget : secondIf, xNegated ? secondIf : secondFalseTarget, firstIfProbability));
        ifNode.replaceAtPredecessor(firstIf);
        ifNode.safeDelete();
    }

    private static void processConditional(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, ConditionalNode conditional, boolean negateTargets) {
        ValueNode trueTarget = negateTargets ? conditional.falseValue() : conditional.trueValue();
        ValueNode falseTarget = negateTargets ? conditional.trueValue() : conditional.falseValue();
        Graph graph = conditional.graph();
        ConditionalNode secondConditional = graph.unique(new ConditionalNode(y, yNegated ? falseTarget : trueTarget, yNegated ? trueTarget : falseTarget));
        ConditionalNode firstConditional = graph.unique(new ConditionalNode(x, xNegated ? falseTarget : secondConditional, xNegated ? secondConditional : falseTarget));
        conditional.replaceAndDelete(firstConditional);
    }
}
