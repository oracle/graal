/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

    public static final NodeClass<MembarNode> TYPE = NodeClass.create(MembarNode.class);
    protected final int barriers;
    protected final LocationIdentity location;

    public MembarNode(int barriers) {
        this(barriers, LocationIdentity.any());
    }

    public MembarNode(int barriers, LocationIdentity location) {
        super(TYPE, StampFactory.forVoid());
        this.barriers = barriers;
        this.location = location;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return location;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        map.put("barriersString", MemoryBarriers.barriersString(barriers));
        return super.getDebugProperties(map);
    }

    public int getBarriers() {
        return barriers;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().emitMembar(barriers);
    }

    @NodeIntrinsic
    public static native void memoryBarrier(@ConstantNodeParameter int barriers);

    @NodeIntrinsic
    public static native void memoryBarrier(@ConstantNodeParameter int barriers, @ConstantNodeParameter LocationIdentity location);
}
