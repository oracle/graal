/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.Map;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.MemoryBarriers;

/**
 * Creates a memory barrier.
 */
@NodeInfo(nameTemplate = "Membar#{p#location/s}", allowedUsageTypes = Memory, cycles = CYCLES_2, size = SIZE_2)
public final class MembarNode extends FixedWithNextNode implements LIRLowerable, SingleMemoryKill {

    public enum FenceKind {
        NONE(0),
        STORE_LOAD(MemoryBarriers.STORE_LOAD),
        LOAD_ACQUIRE(MemoryBarriers.LOAD_LOAD | MemoryBarriers.LOAD_STORE),
        STORE_RELEASE(MemoryBarriers.LOAD_STORE | MemoryBarriers.STORE_STORE),
        ALLOCATION_INIT(MemoryBarriers.STORE_STORE),
        CONSTRUCTOR_FREEZE(MemoryBarriers.STORE_STORE),
        FULL(MemoryBarriers.LOAD_LOAD | MemoryBarriers.STORE_LOAD | MemoryBarriers.LOAD_STORE | MemoryBarriers.STORE_STORE);

        private final int barriers;

        FenceKind(int barriers) {
            this.barriers = barriers;
        }
    }

    public static final NodeClass<MembarNode> TYPE = NodeClass.create(MembarNode.class);
    protected final FenceKind fence;
    protected final LocationIdentity location;

    public MembarNode(FenceKind fence) {
        this(fence, LocationIdentity.any());
    }

    public MembarNode(FenceKind fence, LocationIdentity location) {
        super(TYPE, StampFactory.forVoid());
        this.fence = fence;
        this.location = location;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return location;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        map.put("barriersString", MemoryBarriers.barriersString(fence.barriers));
        return super.getDebugProperties(map);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().emitMembar(fence.barriers);
    }

    @NodeIntrinsic
    public static native void memoryBarrier(@ConstantNodeParameter FenceKind fence);

    @NodeIntrinsic
    public static native void memoryBarrier(@ConstantNodeParameter FenceKind fence, @ConstantNodeParameter LocationIdentity location);
}
