/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.core.aarch64;

import jdk.vm.ci.code.MemoryBarriers;
import org.graalvm.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRGenerator;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

/**
 * AArch64-specific subclass of WriteNode that implements the write with a memory barrier
 * appropriate for a Java volatile.
 */

@NodeInfo
public class AArch64VolatileWriteNode extends WriteNode {
    public static final NodeClass<AArch64VolatileWriteNode> TYPE = NodeClass.create(AArch64VolatileWriteNode.class);

    public AArch64VolatileWriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(TYPE, address, location, value, barrierType);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        AArch64LIRGenerator lirgen = (AArch64LIRGenerator) gen.getLIRGeneratorTool();
        AArch64ArithmeticLIRGenerator arithgen = (AArch64ArithmeticLIRGenerator) lirgen.getArithmetic();
        LIRKind writeKind = lirgen.getLIRKind(value().stamp(NodeView.DEFAULT));
        arithgen.emitStore(writeKind, gen.operand(getAddress()), gen.operand(value()), gen.state(this), true);
    }

    /**
     * replace a ReadNode with an AArch64-specific variant which knows how to merge a downstream
     * zero or sign extend into the read operation.
     *
     * @param writeNode
     */
    public static void replace(WriteNode writeNode, MembarNode pre, MembarNode post) {
        assert pre.getBarriers() == MemoryBarriers.JMM_PRE_VOLATILE_WRITE;
        assert post.getBarriers() == MemoryBarriers.JMM_POST_VOLATILE_WRITE;
        assert post.getAccess() == writeNode;
        assert post.getLeading() == pre;

        AddressNode address = writeNode.getAddress();
        LocationIdentity location = writeNode.getLocationIdentity();
        ValueNode value = writeNode.value();
        BarrierType barrierType = writeNode.getBarrierType();
        AArch64VolatileWriteNode clone = new AArch64VolatileWriteNode(address, location, value, barrierType);
        StructuredGraph graph = writeNode.graph();
        graph.add(clone);
        // splice out the pre and post nodes
        post.setAccess(null);
        post.setLeading(null);
        graph.removeFixed(pre);
        graph.removeFixed(post);
        // swap the clone for the read
        graph.replaceFixedWithFixed(writeNode, clone);
    }
}
