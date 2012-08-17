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
package com.oracle.graal.compiler.loop;

import com.oracle.graal.compiler.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;


public abstract class LoopPolicies {
    private LoopPolicies() {
        // does not need to be instantiated
    }

    // TODO (gd) change when inversion is available
    public static boolean shouldPeel(LoopEx loop) {
        LoopBeginNode loopBegin = loop.loopBegin();
        double entryProbability = loopBegin.forwardEnd().probability();
        return entryProbability > GraalOptions.MinimumPeelProbability && loop.size() + loopBegin.graph().getNodeCount() < GraalOptions.MaximumDesiredSize;
    }

    public static boolean shouldFullUnroll(LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long exactTrips = counted.constantMaxTripCount();
        int maxNodes = (counted.isExactTripCount() && counted.isConstantExactTripCount()) ? GraalOptions.ExactFullUnrollMaxNodes : GraalOptions.FullUnrollMaxNodes;
        maxNodes = Math.min(maxNodes, GraalOptions.MaximumDesiredSize - loop.loopBegin().graph().getNodeCount());
        int size = Math.max(1, loop.size() - 1 - loop.loopBegin().phis().count());
        return size * exactTrips <= maxNodes;
    }

    public static boolean shouldTryUnswitch(@SuppressWarnings("unused") LoopEx loop) {
        // TODO (gd) maybe there should be a max number of unswitching per loop
        return true;
    }

    public static boolean shouldUnswitch(LoopEx loop, IfNode ifNode) {
        Block postDomBlock = loop.loopsData().controlFlowGraph().blockFor(ifNode).getPostdominator();
        BeginNode postDom = postDomBlock != null ? postDomBlock.getBeginNode() : null;
        int inTrueBranch = loop.nodesInLoopFrom(ifNode.trueSuccessor(), postDom).cardinality();
        int inFalseBranch = loop.nodesInLoopFrom(ifNode.falseSuccessor(), postDom).cardinality();
        int loopTotal = loop.size();
        int netDiff = loopTotal - (inTrueBranch + inFalseBranch);
        double uncertainty = (0.5 - Math.abs(ifNode.probability(IfNode.TRUE_EDGE) - 0.5)) * 2;
        int maxDiff = GraalOptions.LoopUnswitchMaxIncrease + (int) (GraalOptions.LoopUnswitchUncertaintyBoost * loop.loopBegin().loopFrequency() * uncertainty);
        Debug.log("shouldUnswitch(%s, %s) : delta=%d, max=%d, %.2f%% inside of if", loop, ifNode, netDiff, maxDiff, (double) (inTrueBranch + inFalseBranch) / loopTotal * 100);
        return netDiff <= maxDiff;
    }


}
