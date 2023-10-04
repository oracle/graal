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

import jdk.compiler.graal.core.common.memory.BarrierType;
import jdk.compiler.graal.core.common.memory.MemoryOrderMode;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.core.common.type.TypeReference;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.NamedLocationIdentity;
import jdk.compiler.graal.nodes.extended.JavaReadNode;
import jdk.compiler.graal.nodes.memory.FloatableThreadLocalAccess;
import jdk.compiler.graal.nodes.memory.MemoryAccess;
import jdk.compiler.graal.nodes.memory.address.AddressNode;
import jdk.compiler.graal.nodes.memory.address.OffsetAddressNode;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.word.WordTypes;
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
