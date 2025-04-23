/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.ValueProxy;

/**
 * Wraps a newly allocated object, acting as a barrier for any initializing writes by preventing
 * reads from the newly allocated object from floating above the initialization.
 * <p>
 * Operations on {@linkplain org.graalvm.word.LocationIdentity#INIT_LOCATION init} memory, such as
 * initializing an allocated object's header and fields, are not considered side effecting, because
 * the work of an allocation should never interact with the memory graph before the object is
 * initialized and visible to other code. However, this lack of explicit memory ordering could cause
 * {@linkplain jdk.graal.compiler.nodes.memory.FloatingReadNode floating reads} on a newly allocated
 * object be scheduled before its initializing writes.
 * <p>
 * In order to maintain object safety while still allowing reads to float, we require
 * non-initializing uses of the newly allocated object to have a data dependence on a fixed
 * {@link PublishWritesNode} instead of on the original raw allocation. The
 * {@link PublishWritesNode} must be placed after all initializing writes to the object it wraps.
 * <p>
 * This data dependence will ensure correct ordering of reads and writes during
 * {@linkplain jdk.graal.compiler.phases.schedule.SchedulePhase scheduling}. For allocation
 * snippets, this can be achieved by returning {@link PublishWritesNode#publishWrites(Object)}.
 * <p>
 * Additionally, to prevent uninitialized memory from being visible to other threads, a
 * {@link jdk.vm.ci.code.MemoryBarriers#STORE_STORE STORE_STORE} barrier must be issued after the
 * object is initialized. This is accomplished with a {@link MembarNode} placed after (one or more)
 * {@link PublishWritesNode}.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0, allowedUsageTypes = {InputType.Anchor, InputType.Value})
public class PublishWritesNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable, ValueProxy, GuardingNode, AnchoringNode {
    public static final NodeClass<PublishWritesNode> TYPE = NodeClass.create(PublishWritesNode.class);

    @Input ValueNode allocation;

    public ValueNode allocation() {
        return allocation;
    }

    public PublishWritesNode(ValueNode newObject) {
        super(TYPE, newObject.stamp(NodeView.DEFAULT));
        this.allocation = newObject;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp.join(allocation.stamp(NodeView.DEFAULT)));
    }

    @Override
    public boolean verifyNode() {
        // Check that the published allocation node is not used by init reads directly.
        for (AddressNode address : allocation.usages().filter(AddressNode.class)) {
            var readUsages = address.usages().filter(n -> {
                // n is a non-writing access (a.k.a. a read) to INIT_LOCATION
                return n instanceof MemoryAccess access && access.getLocationIdentity().isInit() && !MemoryKill.isMemoryKill(n);
            });
            assertTrue(readUsages.isEmpty(), "%s has unpublished reads", allocation);
        }
        return true;
    }

    @NodeIntrinsic
    public static native Object publishWrites(Object object);

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generator.operand(allocation));
    }

    @Override
    public ValueNode getOriginalNode() {
        return allocation;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!tool.allUsagesAvailable()) {
            return this;
        }
        /*
         * Sometimes an AbstractNewObjectNode that was already wrapped by a PublishWritesNode is
         * virtualized, and a second PublishWritesNode is created when the virtual object is
         * lowered. If the two are adjacent, merge them into a single node.
         */
        if (allocation instanceof PublishWritesNode && predecessor() == allocation) {
            return allocation;
        }
        return this;
    }

    @Override
    public GuardingNode getGuard() {
        return this;
    }
}
