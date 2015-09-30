/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.query;

import java.util.HashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeFlood;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.AccessMonitorNode;
import com.oracle.graal.nodes.java.MonitorIdNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.nodes.virtual.AllocatedObjectNode;
import com.oracle.graal.nodes.virtual.CommitAllocationNode;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.query.nodes.InstrumentationNode;
import com.oracle.graal.phases.common.query.nodes.MonitorProxyNode;

public class HighTierReconcileICGPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (CommitAllocationNode commit : graph.getNodes().filter(CommitAllocationNode.class)) {
            InstrumentationAggregation aggregation = new InstrumentationAggregation(commit);

            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                aggregation.duplicateInstrumentationIfMatch(i -> i.target() == virtual, () -> getAllocatedObject(commit, virtual));
            }

            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                for (MonitorIdNode monitorId : commit.getLocks(objIndex)) {
                    aggregation.duplicateInstrumentationIfMatch(i -> i.target() instanceof MonitorProxyNode && ((MonitorProxyNode) i.target()).getMonitorId() == monitorId, () -> new MonitorProxyNode(
                                    getAllocatedObject(commit, virtual), monitorId));
                }
            }
        }

        for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
            ValueNode target = instrumentationNode.target();
            if (target instanceof VirtualObjectNode) {
                // at this point, we can remove InstrumentationNode whose target is still virtual.
                GraphUtil.unlinkFixedNode(instrumentationNode);
                instrumentationNode.safeDelete();
            } else if (target instanceof MonitorProxyNode) {
                MonitorProxyNode proxy = (MonitorProxyNode) target;
                if (proxy.target() == null || proxy.target().isDeleted()) {
                    GraphUtil.unlinkFixedNode(instrumentationNode);
                    instrumentationNode.safeDelete();
                } else if (proxy.target() instanceof AccessMonitorNode) {
                    // unproxify the target of the InstrumentationNode.
                    instrumentationNode.replaceFirstInput(proxy, proxy.target());
                }
            }
        }
    }

    class InstrumentationAggregation {

        protected CommitAllocationNode commit;
        protected FixedWithNextNode insertingLocation;

        public InstrumentationAggregation(CommitAllocationNode commit) {
            this.commit = commit;
            this.insertingLocation = commit;
        }

        public void insert(InstrumentationNode instrumentationNode) {
            commit.graph().addAfterFixed(insertingLocation, instrumentationNode);
            insertingLocation = instrumentationNode;
        }

        public void duplicateInstrumentationIfMatch(Predicate<InstrumentationNode> guard, Supplier<ValueNode> newTargetSupplier) {
            StructuredGraph graph = commit.graph();
            for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
                // insert ICG only if the CommitAllocationNode is accessible from the
                // instrumentation node
                if (!(isCFGAccessible(instrumentationNode, commit) && guard.test(instrumentationNode))) {
                    continue;
                }
                InstrumentationNode clone = (InstrumentationNode) instrumentationNode.copyWithInputs();
                ValueNode newTarget = newTargetSupplier.get();
                if (!newTarget.isAlive()) {
                    graph.addWithoutUnique(newTarget);
                }
                clone.replaceFirstInput(clone.target(), newTarget);
                updateVirtualInputs(clone);
                insert(clone);
            }
        }

        private void updateVirtualInputs(InstrumentationNode clone) {
            for (ValueNode input : clone.getWeakDependencies()) {
                if ((input instanceof VirtualObjectNode) && (commit.getVirtualObjects().contains(input))) {
                    clone.replaceFirstInput(input, getAllocatedObject(commit, (VirtualObjectNode) input));
                }
            }
        }

    }

    private final HashMap<InstrumentationNode, NodeFlood> cachedNodeFloods = new HashMap<>();

    private boolean isCFGAccessible(InstrumentationNode from, CommitAllocationNode to) {
        // we only insert InstrumentationNode during this phase, so it is fine to reuse cached
        // NodeFlood.
        NodeFlood flood = cachedNodeFloods.get(from);
        if (flood == null) {
            flood = from.graph().createNodeFlood();
            flood.add(from);
            for (Node current : flood) {
                if (current instanceof LoopEndNode) {
                    continue;
                } else if (current instanceof AbstractEndNode) {
                    flood.add(((AbstractEndNode) current).merge());
                } else {
                    flood.addAll(current.successors());
                }
            }
            cachedNodeFloods.put(from, flood);
        }
        return flood.isMarked(to);
    }

    private static AllocatedObjectNode getAllocatedObject(CommitAllocationNode commit, VirtualObjectNode virtual) {
        for (AllocatedObjectNode object : commit.graph().getNodes().filter(AllocatedObjectNode.class)) {
            if (object.getCommit() == commit && object.getVirtualObject() == virtual) {
                return object;
            }
        }
        AllocatedObjectNode object = commit.graph().addWithoutUnique(new AllocatedObjectNode(virtual));
        object.setCommit(commit);
        return object;
    }

}
