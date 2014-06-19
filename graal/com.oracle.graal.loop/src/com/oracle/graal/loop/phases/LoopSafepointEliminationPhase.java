/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop.phases;

import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class LoopSafepointEliminationPhase extends BasePhase<MidTierContext> {

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        LoopsData loops = new LoopsData(graph);
        if (context.getOptimisticOptimizations().useLoopLimitChecks()) {
            loops.detectedCountedLoops();
            for (LoopEx loop : loops.countedLoops()) {
                if (loop.loop().getChildren().isEmpty() && loop.counted().getStamp().getBits() <= 32) {
                    boolean hasSafepoint = false;
                    for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                        hasSafepoint |= loopEnd.canSafepoint();
                    }
                    if (hasSafepoint) {
                        loop.counted().createOverFlowGuard();
                        for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                            loopEnd.disableSafepoint();
                        }
                    }
                }
            }
        }
        for (LoopEx loop : loops.countedLoops()) {
            for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                Block b = loops.controlFlowGraph().blockFor(loopEnd);
                blocks: while (b != loop.loop().getHeader()) {
                    assert b != null;
                    for (FixedNode node : b.getNodes()) {
                        if (node instanceof Invoke || node instanceof ForeignCallNode) {
                            loopEnd.disableSafepoint();
                            break blocks;
                        }
                    }
                    b = b.getDominator();
                }
            }
        }
        loops.deleteUnusedNodes();
    }
}
