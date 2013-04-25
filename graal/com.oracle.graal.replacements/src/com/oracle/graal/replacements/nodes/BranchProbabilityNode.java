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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

/**
 * Instances of this node class will look for a preceding if node and put the given probability into
 * the if node's taken probability. Then the branch probability node will be removed. This node is
 * intended primarily for snippets, so that they can define their fast and slow paths.
 */
public class BranchProbabilityNode extends FixedWithNextNode implements Simplifiable, Lowerable {

    public static final double LIKELY_PROBABILITY = 0.6;
    public static final double NOT_LIKELY_PROBABILITY = 1 - LIKELY_PROBABILITY;

    public static final double FREQUENT_PROBABILITY = 0.9;
    public static final double NOT_FREQUENT_PROBABILITY = 1 - FREQUENT_PROBABILITY;

    public static final double FAST_PATH_PROBABILITY = 0.99;
    public static final double SLOW_PATH_PROBABILITY = 1 - FAST_PATH_PROBABILITY;

    public static final double NOT_DEOPT_PATH_PROBABILITY = 0.999;
    public static final double DEOPT_PATH_PROBABILITY = 1 - NOT_DEOPT_PATH_PROBABILITY;

    @Input private ValueNode probability;

    public BranchProbabilityNode(ValueNode probability) {
        super(StampFactory.forVoid());
        this.probability = probability;
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
            FixedNode current = this;
            while (!(current instanceof BeginNode)) {
                current = (FixedNode) current.predecessor();
            }
            BeginNode begin = (BeginNode) current;
            if (!(begin.predecessor() instanceof IfNode)) {
                throw new GraalInternalError("Injected branch probability cannot follow a merge, only if nodes");
            }
            IfNode ifNode = (IfNode) begin.predecessor();
            if (ifNode.trueSuccessor() == begin) {
                ifNode.setTrueSuccessorProbability(probabilityValue);
            } else {
                ifNode.setTrueSuccessorProbability(1 - probabilityValue);
            }

            FixedNode next = next();
            setNext(null);
            ((FixedWithNextNode) predecessor()).setNext(next);
            GraphUtil.killCFG(this);
        }
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void probability(double probability) {
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        throw new GraalInternalError("Branch probability could not be injected, because the probability value did not reduce to a constant value.");
    }

}
