/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.loop.*;

/**
 * This phase looks for {@link InductionVariableNode}s and converts them to Phis and arithmetic nodes.
 */
public class RemoveInductionVariablesPhase extends Phase {

    private NodeMap<ValueNode> loweredIV;

    @Override
    protected void run(StructuredGraph graph) {
        loweredIV = graph.createNodeMap();

        for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.class)) {
            Collection<InductionVariableNode> inductionVariables = loopBegin.inductionVariables();
            Map<InductionVariableNode, InductionVariableNode> nextIterOf = null;
            for (InductionVariableNode iv1 : inductionVariables) {
                for (InductionVariableNode iv2 : inductionVariables) {
                    if (iv1 != iv2 && iv1.isNextIteration(iv2)) {
                        if (nextIterOf == null) {
                            nextIterOf = new IdentityHashMap<InductionVariableNode, InductionVariableNode>();
                        }
                        nextIterOf.put(iv2, iv1);
                    }
                }
            }

            for (InductionVariableNode iv : inductionVariables) {
                if (nextIterOf == null || !nextIterOf.containsKey(iv)) {
                    loweredIV.set(iv, iv.lowerInductionVariable());
                }
            }

            if (nextIterOf != null) {
                for (Entry<InductionVariableNode, InductionVariableNode> entry : nextIterOf.entrySet()) {
                    InductionVariableNode it = entry.getValue();
                    InductionVariableNode nextIt = entry.getKey();
                    // can't fuse if nextIt is used in the loopBegin's framestate because this would pop the backedge value out of the loop in scheduler
                    if (it != null && !nextIt.usages().contains(loopBegin.stateAfter())) {
                        ValueNode itValue = loweredIV.get(it);
                        if (itValue instanceof PhiNode) {
                            PhiNode phi = (PhiNode) itValue;
                            loweredIV.set(nextIt, phi.valueAt(loopBegin.loopEnd()));
                            continue;
                        }
                    }
                    loweredIV.set(nextIt, nextIt.lowerInductionVariable());
                }
            }
        }
        for (Entry<Node, ValueNode> entry : loweredIV.entries()) {
            InductionVariableNode iv = (InductionVariableNode) entry.getKey();
            ValueNode lower = entry.getValue();
            for (Node usage : iv.usages().snapshot()) {
                if (!(usage instanceof InductionVariableNode)) {
                    usage.replaceFirstInput(iv, lower);
                } else {
                    usage.replaceFirstInput(iv, null);
                }
            }
            iv.safeDelete();
        }
    }

}
