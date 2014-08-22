/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Instances of this node class will look for a preceding if node and put the given probability into
 * the if node's taken probability. Then the branch probability node will be removed. This node is
 * intended primarily for snippets, so that they can define their fast and slow paths.
 */
@NodeInfo
public class BranchProbabilityNode extends FloatingNode implements Simplifiable, Lowerable {

    public static final double LIKELY_PROBABILITY = 0.6;
    public static final double NOT_LIKELY_PROBABILITY = 1 - LIKELY_PROBABILITY;

    public static final double FREQUENT_PROBABILITY = 0.9;
    public static final double NOT_FREQUENT_PROBABILITY = 1 - FREQUENT_PROBABILITY;

    public static final double FAST_PATH_PROBABILITY = 0.99;
    public static final double SLOW_PATH_PROBABILITY = 1 - FAST_PATH_PROBABILITY;

    public static final double VERY_FAST_PATH_PROBABILITY = 0.999;
    public static final double VERY_SLOW_PATH_PROBABILITY = 1 - VERY_FAST_PATH_PROBABILITY;

    @Input ValueNode probability;
    @Input ValueNode condition;

    public static BranchProbabilityNode create(ValueNode probability, ValueNode condition) {
        return new BranchProbabilityNodeGen(probability, condition);
    }

    BranchProbabilityNode(ValueNode probability, ValueNode condition) {
        super(condition.stamp());
        this.probability = probability;
        this.condition = condition;
    }

    public ValueNode getProbability() {
        return probability;
    }

    public ValueNode getCondition() {
        return condition;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (probability.isConstant()) {
            double probabilityValue = probability.asConstant().asDouble();
            if (probabilityValue < 0.0) {
                throw new GraalInternalError("A negative probability of " + probabilityValue + " is not allowed!");
            } else if (probabilityValue > 1.0) {
                throw new GraalInternalError("A probability of more than 1.0 (" + probabilityValue + ") is not allowed!");
            }
            boolean couldSet = false;
            for (IntegerEqualsNode node : this.usages().filter(IntegerEqualsNode.class)) {
                if (node.condition() == Condition.EQ) {
                    ValueNode other = node.getX();
                    if (node.getX() == this) {
                        other = node.getY();
                    }
                    if (other.isConstant()) {
                        double probabilityToSet = probabilityValue;
                        if (other.asConstant().asInt() == 0) {
                            probabilityToSet = 1.0 - probabilityToSet;
                        }
                        for (IfNode ifNodeUsages : node.usages().filter(IfNode.class)) {
                            couldSet = true;
                            ifNodeUsages.setTrueSuccessorProbability(probabilityToSet);
                        }
                    }
                }
            }
            if (couldSet) {
                replaceAndDelete(condition);
                tool.addToWorkList(condition.usages());
            } else {
                if (!isSubstitutionGraph()) {
                    throw new GraalInternalError("Wrong usage of branch probability injection!");
                }
            }
        }
    }

    private boolean isSubstitutionGraph() {
        return usages().count() == 1 && usages().first() instanceof ReturnNode;
    }

    /**
     * This intrinsic should only be used for the condition of an if statement. The parameter
     * condition should also only denote a simple condition and not a combined condition involving
     * &amp;&amp; or || operators. It injects the probability of the condition into the if
     * statement.
     *
     * @param probability the probability that the given condition is true as a double value between
     *            0.0 and 1.0.
     * @param condition the simple condition without any &amp;&amp; or || operators
     * @return the condition
     */
    @NodeIntrinsic
    public static boolean probability(double probability, boolean condition) {
        assert probability >= 0.0 && probability <= 1.0;
        return condition;
    }

    @Override
    public void lower(LoweringTool tool) {
        throw new GraalInternalError("Branch probability could not be injected, because the probability value did not reduce to a constant value.");
    }
}
