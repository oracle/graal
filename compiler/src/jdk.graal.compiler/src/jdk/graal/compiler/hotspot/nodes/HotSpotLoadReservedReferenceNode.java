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
package jdk.graal.compiler.hotspot.nodes;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.memory.FloatableThreadLocalAccess;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Loads {@code JavaThread::_jvmci_reserved_oop0} for the current thread.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1)
public final class HotSpotLoadReservedReferenceNode extends FixedWithNextNode implements Lowerable, MemoryAccess, FloatableThreadLocalAccess {

    static final LocationIdentity JVMCI_RESERVED_REFERENCE = NamedLocationIdentity.mutable("JavaThread::<JVMCIReservedOop0>");

    public static final NodeClass<HotSpotLoadReservedReferenceNode> TYPE = NodeClass.create(HotSpotLoadReservedReferenceNode.class);

    private final WordTypes wordTypes;

    private final int jvmciReservedReference0Offset;

    public HotSpotLoadReservedReferenceNode(MetaAccessProvider metaAccess, WordTypes wordTypes, int jvmciReservedReference0Offset) {
        super(TYPE, StampFactory.object(TypeReference.createTrustedWithoutAssumptions(metaAccess.lookupJavaType(Object[].class))));
        this.wordTypes = wordTypes;
        this.jvmciReservedReference0Offset = jvmciReservedReference0Offset;
    }

    @Override
    public void lower(LoweringTool tool) {
        CurrentJavaThreadNode thread = graph().unique(new CurrentJavaThreadNode(wordTypes));
        AddressNode address = graph().unique(new OffsetAddressNode(thread, graph().unique(ConstantNode.forLong(jvmciReservedReference0Offset))));
        JavaReadNode read = graph().add(new JavaReadNode(JavaKind.Object, address, JVMCI_RESERVED_REFERENCE, BarrierType.NONE, MemoryOrderMode.PLAIN, false));
        /*
         * Setting a guarding node allows a JavaReadNode to float when lowered. Otherwise they will
         * conservatively be forced at a fixed location.
         */
        read.setGuard(graph().start());
        graph().replaceFixedWithFixed(this, read);
        tool.getLowerer().lower(read, tool);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return JVMCI_RESERVED_REFERENCE;
    }

    @Override
    public boolean canFloat() {
        // We always lower this to a floatable read.
        return true;
    }

}
