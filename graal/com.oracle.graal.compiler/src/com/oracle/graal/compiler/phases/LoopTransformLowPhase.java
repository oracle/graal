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
package com.oracle.graal.compiler.phases;

import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.loop.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

public class LoopTransformLowPhase extends Phase {
    private static final DebugMetric UNSWITCHED = Debug.metric("Unswitched");

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.hasLoops()) {
            if (GraalOptions.ReassociateInvariants) {
                final LoopsData dataReassociate = new LoopsData(graph);
                Debug.scope("ReassociateInvariants", new Runnable() {
                    @Override
                    public void run() {
                        for (LoopEx loop : dataReassociate.loops()) {
                            loop.reassociateInvariants();
                        }
                    }
                });
            }
            if (GraalOptions.LoopUnswitch) {
                NodeBitMap unswitchedDebug = graph.createNodeBitMap();
                boolean unswitched;
                do {
                    unswitched = false;
                    final LoopsData dataUnswitch = new LoopsData(graph);
                    for (LoopEx loop : dataUnswitch.loops()) {
                        if (LoopPolicies.shouldTryUnswitch(loop)) {
                            IfNode ifNode = LoopTransformations.findUnswitchableIf(loop);
                            if (ifNode != null && !unswitchedDebug.isMarked(ifNode) && LoopPolicies.shouldUnswitch(loop, ifNode)) {
                                unswitchedDebug.mark(ifNode);
                                Debug.log("Unswitching %s at %s [%f - %f]", loop, ifNode, ifNode.probability(0), ifNode.probability(1));
                                //LoopTransformations.unswitch(loop, ifNode);
                                UNSWITCHED.increment();
                                //Debug.dump(graph, "After unswitch %s", loop);
                                unswitched = true;
                                break;
                            }
                        }
                    }
                } while(unswitched);
            }
        }
    }
}
