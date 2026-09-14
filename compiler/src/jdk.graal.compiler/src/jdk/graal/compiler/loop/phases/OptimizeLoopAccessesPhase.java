/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.loop.phases;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopFragmentWhole;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.AbstractWriteNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.LazyValue;

/// Looks for a [FloatingReadNode] inside a loop that
/// [reads from][jdk.graal.compiler.nodes.memory.MemoryAccess#getLastLocationAccess] a memory state
/// represented by a phi on the loop. If that memory phi is the only thing keeping
/// the read from floating above the loop, the phase checks whether the memory nodes merged on its
/// back edges are all writes to the same address and location. In that case, it moves the read out
/// of the loop and replaces its usages with a value phi of the initial read and the written values.
///
/// It transforms this:
///
/// ```
/// m0 = MemoryNode()
/// loop {
///    m1 = MemoryPhi(m0, m2)
///    r0 = Read(addr0, loc0, m1)
///    ... r0 ...
///    m2 = Write(addr0, loc0, v0)
/// }
/// ```
///
/// into:
///
/// ```
/// m0 = MemoryNode()
/// r0 = Read(addr0, loc0, m0)
/// loop {
///    m1 = MemoryPhi(m0, m2)
///    v1 = ValuePhi(r0, v0)
///    ... v1 ...
///    m2 = Write(addr0, loc0, v0)
/// }
/// ```
///
/// For a concrete Java example, consider a non-null object `state` with an `int value` field:
///
/// ```java
/// int sum = 0;
/// for (int i = 0; i < iterations; i++) {
///     sum += state.value;
///     state.value = i;
/// }
/// ```
///
/// Each iteration reads either the value present before the loop or the value written by the
/// previous iteration. Conceptually, the optimized graph carries that value explicitly:
///
/// ```java
/// int current = state.value;
/// int sum = 0;
/// for (int i = 0; i < iterations; i++) {
///     sum += current;
///     state.value = i;
///     current = i;
/// }
/// ```
///
/// The compiler represents `current` with a value phi seeded by the read before the loop and
/// updated with the value written on each back edge.
///
/// This trades reads for value phis, which might increase register pressure.
public class OptimizeLoopAccessesPhase extends BasePhase<CoreProviders> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops();
    }

    /// Optimizes eligible floating reads around each loop memory phi in `graph`.
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        LazyValue<LoopsData> loopsData = new LazyValue<>(() -> context.getLoopsDataProvider().getLoopsData(graph));
        for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.TYPE)) {
            assert loopBegin.forwardEndCount() == 1 : loopBegin.forwardEnds().snapshot();
            for (MemoryPhiNode phi : loopBegin.memoryPhis().snapshot()) {
                tryOptimizeAroundMemoryPhi(phi, loopBegin, loopsData);
            }
        }
    }

    /// Tries to replace reads anchored to `phi` with a value phi when every back edge either writes
    /// the same address or carries the memory phi unchanged.
    private static void tryOptimizeAroundMemoryPhi(MemoryPhiNode phi, LoopBeginNode loopBegin, LazyValue<LoopsData> loopsData) {
        DebugContext debug = phi.getDebug();
        debug.log(DebugContext.DETAILED_LEVEL, "tryOptimizeAroundMemoryPhi(%s)", phi);
        List<ValueNode> writtenBackValues = new ArrayList<>();
        AddressNode address = null;
        LocationIdentity locationIdentity = null;
        Stamp accessStamp = null;
        for (ValueNode backValue : phi.backValues()) {
            if (backValue instanceof AbstractWriteNode write) {
                if (address == null) {
                    assert locationIdentity == null && accessStamp == null : locationIdentity + " " + accessStamp;
                    address = write.getAddress();
                    locationIdentity = write.getLocationIdentity();
                    accessStamp = write.getAccessStamp(NodeView.DEFAULT);
                    assert address != null;
                } else {
                    assert locationIdentity != null && accessStamp != null : locationIdentity + " " + accessStamp;
                    if (!locationIdentity.equals(write.getLocationIdentity())) {
                        return;
                    }
                    if (address != write.getAddress()) {
                        return;
                    }
                    if (!accessStamp.isCompatible(write.getAccessStamp(NodeView.DEFAULT))) {
                        return;
                    }
                }
                writtenBackValues.add(write.value());
            } else if (backValue == phi) {
                writtenBackValues.add(phi);
            } else {
                return;
            }
        }
        if (locationIdentity == null || address == null || accessStamp == null) {
            return;
        }
        debug.log(DebugContext.VERBOSE_LEVEL, "tryOptimizeAroundMemoryPhi(%s): address=%s, locationIdentity=%s", phi, address, locationIdentity);
        // Check each read because a memory phi can anchor unrelated locations.
        reads: for (FloatingReadNode read : phi.usages().filter(FloatingReadNode.class).snapshot()) {
            if (read.getLastLocationAccess() != phi) {
                continue;
            }
            if (read.getAddress() != address || !locationIdentity.equals(read.getLocationIdentity()) || !accessStamp.isCompatible(read.stamp(NodeView.DEFAULT))) {
                continue;
            }
            Loop loop = loopsData.get().loop(loopBegin);
            LoopFragmentWhole loopContent = loop.whole();
            if (!loopContent.contains(read)) {
                // The read might be after the loop rather than in its body.
                continue;
            }
            for (Node input : read.inputs()) {
                if (input == read.getLastLocationAccess()) {
                    continue;
                }
                if (loopContent.contains(input)) {
                    debug.log(DebugContext.VERBOSE_LEVEL, "tryOptimizeAroundMemoryPhi(%s): %s can not float because of %s", phi, read, input);
                    continue reads;
                }
            }
            debug.log(DebugContext.BASIC_LEVEL, "tryOptimizeAroundMemoryPhi(%s): optimizing %s", phi, read);
            // Rewire the memory graph and represent loop-carried values with a value phi.
            MemoryKill memoryBeforeLoop = (MemoryKill) phi.valueAt(loopBegin.forwardEnd());
            read.setLastLocationAccess(memoryBeforeLoop);
            ValuePhiNode valuePhi = phi.graph().addWithoutUnique(new ValuePhiNode(read.stamp(NodeView.DEFAULT).unrestricted(), loopBegin));
            read.replaceAtUsages(valuePhi);
            valuePhi.addInput(read);
            for (ValueNode value : writtenBackValues) {
                if (value == phi || value == read) {
                    valuePhi.addInput(valuePhi);
                } else {
                    valuePhi.addInput(value);
                }
            }
            valuePhi.inferStamp();
            read.graph().getOptimizationLog().report(OptimizeLoopAccessesPhase.class, "LoopReadWithPhiReplacement", read);
        }
    }
}
