/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.nodes;

import static jdk.compiler.graal.hotspot.nodes.HotSpotLoadReservedReferenceNode.JVMCI_RESERVED_REFERENCE;

import jdk.compiler.graal.core.common.memory.BarrierType;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.AbstractStateSplit;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.extended.JavaWriteNode;
import jdk.compiler.graal.nodes.memory.SingleMemoryKill;
import jdk.compiler.graal.nodes.memory.address.AddressNode;
import jdk.compiler.graal.nodes.memory.address.OffsetAddressNode;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * Stores {@code JavaThread::_jvmci_reserved_oop0} of the current thread.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1)
public final class HotSpotStoreReservedReferenceNode extends AbstractStateSplit implements Lowerable, SingleMemoryKill {

    public static final NodeClass<HotSpotStoreReservedReferenceNode> TYPE = NodeClass.create(HotSpotStoreReservedReferenceNode.class);

    private final WordTypes wordTypes;
    @Input protected ValueNode value;

    private final int jvmciReservedReference0Offset;

    public HotSpotStoreReservedReferenceNode(WordTypes wordTypes, ValueNode value, int jvmciReservedReference0Offset) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
        this.wordTypes = wordTypes;
        this.jvmciReservedReference0Offset = jvmciReservedReference0Offset;
    }

    @Override
    public void lower(LoweringTool tool) {
        CurrentJavaThreadNode thread = graph().unique(new CurrentJavaThreadNode(wordTypes));
        AddressNode address = graph().unique(new OffsetAddressNode(thread, graph().unique(ConstantNode.forLong(jvmciReservedReference0Offset))));
        JavaWriteNode write = graph().add(new JavaWriteNode(JavaKind.Object, address, JVMCI_RESERVED_REFERENCE, value, BarrierType.NONE, false));
        write.setStateAfter(stateAfter());
        graph().replaceFixedWithFixed(this, write);
        tool.getLowerer().lower(write, tool);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return JVMCI_RESERVED_REFERENCE;
    }

}
