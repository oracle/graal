/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.graph;

import static com.oracle.graal.graph.util.CollectionsAccess.*;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

/**
 * Compute probabilities for fixed nodes on the fly and cache at {@link BeginNode}s and
 * {@link ControlSplitNode}s.
 */
public class FixedNodeProbabilityCache implements ToDoubleFunction<FixedNode> {

    private static final DebugMetric metricComputeNodeProbability = Debug.metric("ComputeNodeProbability");

    private final Map<FixedNode, Double> cache = newIdentityMap();

    public double applyAsDouble(FixedNode node) {
        metricComputeNodeProbability.increment();

        FixedNode current = node;
        while (true) {
            Node predecessor = current.predecessor();
            if (current instanceof BeginNode) {
                if (predecessor == null) {
                    break;
                } else if (predecessor.successors().count() != 1) {
                    assert predecessor instanceof ControlSplitNode : "a FixedNode with multiple successors needs to be a ControlSplitNode: " + current + " / " + predecessor;
                    break;
                }
            }
            current = (FixedNode) predecessor;
        }

        Double cachedValue = cache.get(current);
        if (cachedValue != null) {
            return cachedValue;
        }

        double probability;
        if (current.predecessor() == null) {
            if (current instanceof MergeNode) {
                probability = ((MergeNode) current).forwardEnds().stream().mapToDouble(this::applyAsDouble).sum();
                if (current instanceof LoopBeginNode) {
                    probability *= ((LoopBeginNode) current).loopFrequency();
                }
            } else if (current instanceof StartNode) {
                probability = 1D;
            } else {
                // this should only appear for dead code
                probability = 1D;
            }
        } else {
            ControlSplitNode split = (ControlSplitNode) current.predecessor();
            probability = split.probability((BeginNode) current) * applyAsDouble(split);
        }
        cache.put(current, probability);
        return probability;
    }
}
