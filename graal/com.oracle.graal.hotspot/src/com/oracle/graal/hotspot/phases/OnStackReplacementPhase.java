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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.java.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;

public class OnStackReplacementPhase extends Phase {

    public static final Descriptor OSR_MIGRATION_END = new Descriptor("OSR_migration_end", true, void.class, long.class);

    public class OSREntryProxyNode extends FloatingNode implements LIRLowerable {

        @Input private ValueNode object;
        @Input(notDataflow = true) private final RuntimeCallNode anchor;

        public OSREntryProxyNode(ValueNode object, RuntimeCallNode anchor) {
            super(object.stamp());
            this.object = object;
            this.anchor = anchor;
        }

        public RuntimeCallNode getAnchor() {
            return anchor;
        }

        @Override
        public void generate(LIRGeneratorTool generator) {
            generator.setResult(this, generator.operand(object));
        }
    }

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
                // this can happen with JSR inlining
                throw new BailoutException("Multiple OnStackReplacementNodes generated");
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

            LoopTransformations.peel(osrLoop);
            for (Node usage : osr.usages().snapshot()) {
                ProxyNode proxy = (ProxyNode) usage;
                proxy.replaceAndDelete(proxy.value());
            }
            FixedNode next = osr.next();
            osr.setNext(null);
            ((FixedWithNextNode) osr.predecessor()).setNext(next);
            GraphUtil.killWithUnusedFloatingInputs(osr);
            Debug.dump(graph, "OnStackReplacement loop peeling result");
        } while (true);

        LocalNode buffer = graph.unique(new LocalNode(0, StampFactory.forKind(wordKind())));
        RuntimeCallNode migrationEnd = graph.add(new RuntimeCallNode(OSR_MIGRATION_END, buffer));
        FrameState osrState = osr.stateAfter();
        migrationEnd.setStateAfter(osrState);
        osr.setStateAfter(null);

        StartNode start = graph.start();
        FixedNode rest = start.next();
        start.setNext(migrationEnd);
        FixedNode next = osr.next();
        osr.setNext(null);
        migrationEnd.setNext(next);

        FrameState oldStartState = start.stateAfter();
        start.setStateAfter(null);
        GraphUtil.killWithUnusedFloatingInputs(oldStartState);

        // mirroring the calculations in c1_GraphBuilder.cpp (setup_osr_entry_block)
        int localsOffset = (graph.method().getMaxLocals() - 1) * 8;
        for (int i = 0; i < osrState.localsSize(); i++) {
            ValueNode value = osrState.localAt(i);
            if (value != null) {
                ProxyNode proxy = (ProxyNode) value;
                int size = FrameStateBuilder.stackSlots(value.kind());
                int offset = localsOffset - (i + size - 1) * 8;
                UnsafeLoadNode load = graph.add(new UnsafeLoadNode(buffer, offset, ConstantNode.forInt(0, graph), value.kind()));
                OSREntryProxyNode newProxy = graph.add(new OSREntryProxyNode(load, migrationEnd));
                proxy.replaceAndDelete(newProxy);
                graph.addBeforeFixed(migrationEnd, load);
            }
        }

        GraphUtil.killCFG(rest);

        Debug.dump(graph, "OnStackReplacement result");
        new DeadCodeEliminationPhase().apply(graph);
    }
}
