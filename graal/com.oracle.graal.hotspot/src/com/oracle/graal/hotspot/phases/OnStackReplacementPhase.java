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
package com.oracle.graal.hotspot.phases;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;

public class OnStackReplacementPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.getEntryBCI() == StructuredGraph.INVOCATION_ENTRY_BCI) {
            // This happens during inlining in a OSR method, because the same phase plan will be
            // used.
            return;
        }
        Debug.dump(graph, "OnStackReplacement initial");
        EntryMarkerNode osr;
        do {
            NodeIterable<EntryMarkerNode> osrNodes = graph.getNodes(EntryMarkerNode.class);
            osr = osrNodes.first();
            if (osr == null) {
                throw new BailoutException("No OnStackReplacementNode generated");
            }
            if (osrNodes.count() > 1) {
                throw new GraalInternalError("Multiple OnStackReplacementNodes generated");
            }
            if (osr.stateAfter().locksSize() != 0) {
                throw new BailoutException("OSR with locks not supported");
            }
            if (osr.stateAfter().stackSize() != 0) {
                throw new BailoutException("OSR with stack entries not supported: " + osr.stateAfter().toString(Verbosity.Debugger));
            }
            LoopEx osrLoop = null;
            LoopsData loops = new LoopsData(graph);
            for (LoopEx loop : loops.loops()) {
                if (loop.inside().contains(osr)) {
                    osrLoop = loop;
                    break;
                }
            }
            if (osrLoop == null) {
                break;
            }

            LoopTransformations.peel(osrLoop, true);
            for (Node usage : osr.usages().snapshot()) {
                ProxyNode proxy = (ProxyNode) usage;
                proxy.replaceAndDelete(proxy.value());
            }
            GraphUtil.removeFixedWithUnusedInputs(osr);
            Debug.dump(graph, "OnStackReplacement loop peeling result");
        } while (true);

        FrameState osrState = osr.stateAfter();
        osr.setStateAfter(null);
        OSRStartNode osrStart = graph.add(new OSRStartNode());
        StartNode start = graph.start();
        FixedNode next = osr.next();
        osr.setNext(null);
        osrStart.setNext(next);
        graph.setStart(osrStart);
        osrStart.setStateAfter(osrState);

        for (int i = 0; i < osrState.localsSize(); i++) {
            ValueNode value = osrState.localAt(i);
            if (value instanceof ProxyNode) {
                ProxyNode proxy = (ProxyNode) value;
                /*
                 * we need to drop the stamp since the types we see during OSR may be too precise
                 * (if a branch was not parsed for example).
                 */
                proxy.replaceAndDelete(graph.unique(new OSRLocalNode(i, proxy.stamp().unrestricted())));
            } else {
                assert value == null || value instanceof OSRLocalNode;
            }
        }

        GraphUtil.killCFG(start);

        Debug.dump(graph, "OnStackReplacement result");
        new DeadCodeEliminationPhase().apply(graph);
    }
}
