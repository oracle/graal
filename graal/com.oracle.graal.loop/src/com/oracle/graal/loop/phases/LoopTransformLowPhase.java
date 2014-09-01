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
package com.oracle.graal.loop.phases;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;

public class LoopTransformLowPhase extends Phase {

    private static final DebugMetric UNSWITCHED = Debug.metric("Unswitched");
    private static final DebugMetric UNSWITCH_CANDIDATES = Debug.metric("UnswitchCandidates");

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.hasLoops()) {
            if (ReassociateInvariants.getValue()) {
                final LoopsData dataReassociate = new LoopsData(graph);
                try (Scope s = Debug.scope("ReassociateInvariants")) {
                    for (LoopEx loop : dataReassociate.loops()) {
                        loop.reassociateInvariants();
                    }
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
                dataReassociate.deleteUnusedNodes();
            }
            if (LoopUnswitch.getValue()) {
                boolean unswitched;
                do {
                    unswitched = false;
                    final LoopsData dataUnswitch = new LoopsData(graph);
                    for (LoopEx loop : dataUnswitch.loops()) {
                        if (LoopPolicies.shouldTryUnswitch(loop)) {
                            List<ControlSplitNode> controlSplits = LoopTransformations.findUnswitchable(loop);
                            if (controlSplits != null) {
                                UNSWITCH_CANDIDATES.increment();
                                if (LoopPolicies.shouldUnswitch(loop, controlSplits)) {
                                    if (Debug.isLogEnabled()) {
                                        logUnswitch(loop, controlSplits);
                                    }
                                    LoopTransformations.unswitch(loop, controlSplits);
                                    UNSWITCHED.increment();
                                    unswitched = true;
                                    break;
                                }
                            }
                        }
                    }
                } while (unswitched);
            }
        }
    }

    private static void logUnswitch(LoopEx loop, List<ControlSplitNode> controlSplits) {
        StringBuilder sb = new StringBuilder("Unswitching ");
        sb.append(loop).append(" at ");
        for (ControlSplitNode controlSplit : controlSplits) {
            sb.append(controlSplit).append(" [");
            NodePosIterator it = controlSplit.successors().iterator();
            while (it.hasNext()) {
                sb.append(controlSplit.probability((BeginNode) it.next()));
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("]");
        }
        Debug.log("%s", sb);
    }
}
