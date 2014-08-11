/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

public abstract class LoopPolicies {

    private LoopPolicies() {
        // does not need to be instantiated
    }

    // TODO (gd) change when inversion is available
    public static boolean shouldPeel(LoopEx loop, ToDoubleFunction<FixedNode> probabilities) {
        if (loop.detectCounted()) {
            return false;
        }
        LoopBeginNode loopBegin = loop.loopBegin();
        double entryProbability = probabilities.applyAsDouble(loopBegin.forwardEnd());
        return entryProbability > MinimumPeelProbability.getValue() && loop.size() + loopBegin.graph().getNodeCount() < MaximumDesiredSize.getValue();
    }

    public static boolean shouldFullUnroll(LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long maxTrips = counted.constantMaxTripCount();
        int maxNodes = (counted.isExactTripCount() && counted.isConstantExactTripCount()) ? ExactFullUnrollMaxNodes.getValue() : FullUnrollMaxNodes.getValue();
        maxNodes = Math.min(maxNodes, MaximumDesiredSize.getValue() - loop.loopBegin().graph().getNodeCount());
        int size = Math.max(1, loop.size() - 1 - loop.loopBegin().phis().count());
        return size * maxTrips <= maxNodes;
    }

    public static boolean shouldTryUnswitch(LoopEx loop) {
        return loop.loopBegin().unswitches() <= LoopMaxUnswitch.getValue();
    }

    public static boolean shouldUnswitch(LoopEx loop, List<ControlSplitNode> controlSplits) {
        int loopTotal = loop.size();
        int inBranchTotal = 0;
        double maxProbability = 0;
        for (ControlSplitNode controlSplit : controlSplits) {
            Block postDomBlock = loop.loopsData().controlFlowGraph().blockFor(controlSplit).getPostdominator();
            BeginNode postDom = postDomBlock != null ? postDomBlock.getBeginNode() : null;
            for (Node successor : controlSplit.successors()) {
                BeginNode branch = (BeginNode) successor;
                // this may count twice because of fall-through in switches
                inBranchTotal += loop.nodesInLoopFrom(branch, postDom).count();
                double probability = controlSplit.probability(branch);
                if (probability > maxProbability) {
                    maxProbability = probability;
                }
            }
        }
        int netDiff = loopTotal - (inBranchTotal);
        double uncertainty = 1 - maxProbability;
        int maxDiff = LoopUnswitchMaxIncrease.getValue() + (int) (LoopUnswitchUncertaintyBoost.getValue() * loop.loopBegin().loopFrequency() * uncertainty);
        Debug.log("shouldUnswitch(%s, %s) : delta=%d, max=%d, %.2f%% inside of branches", loop, controlSplits, netDiff, maxDiff, (double) (inBranchTotal) / loopTotal * 100);
        return netDiff <= maxDiff;
    }

}
