/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.core.aarch64;

import jdk.vm.ci.code.MemoryBarriers;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

/**
 * AArch64-specific subclass of ReadNode that implements the read with a memory barrier appropriate
 * for a Java volatile.
 */

@NodeInfo
public class AArch64VolatileReadNode extends ReadNode {
    public static final NodeClass<AArch64VolatileReadNode> TYPE = NodeClass.create(AArch64VolatileReadNode.class);

    public AArch64VolatileReadNode(AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck,
                    FrameState stateBefore) {
        super(TYPE, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        AArch64LIRGenerator lirgen = (AArch64LIRGenerator) gen.getLIRGeneratorTool();
        AArch64ArithmeticLIRGenerator arithgen = (AArch64ArithmeticLIRGenerator) lirgen.getArithmetic();
        LIRKind readKind = lirgen.getLIRKind(getAccessStamp());
        gen.setResult(this, arithgen.emitLoad(readKind, gen.operand(getAddress()), gen.state(this), true));
    }

    /**
     * replace a ReadNode for a volatile field with an AArch64-specific variant which can be
     * generated as ldar.
     *
     * @param readNode
     */
    public static void replace(ReadNode readNode, MembarNode pre, MembarNode post) {
        assert pre.getBarriers() == MemoryBarriers.JMM_PRE_VOLATILE_READ;
        assert post.getBarriers() == MemoryBarriers.JMM_POST_VOLATILE_READ;
        assert post.getAccess() == readNode;
        assert post.getLeading() == pre;

        Stamp stamp = readNode.getAccessStamp();
        AddressNode address = readNode.getAddress();
        LocationIdentity location = readNode.getLocationIdentity();
        GuardingNode guard = readNode.getGuard();
        BarrierType barrierType = readNode.getBarrierType();
        boolean nullCheck = readNode.getNullCheck();
        FrameState stateBefore = readNode.stateBefore();
        AArch64VolatileReadNode clone = new AArch64VolatileReadNode(address, location, stamp, guard, barrierType, nullCheck, stateBefore);
        StructuredGraph graph = readNode.graph();
        graph.add(clone);
        // splice out the pre and post barriers
        post.setAccess(null);
        post.setLeading(null);
        graph.removeFixed(pre);
        graph.removeFixed(post);
        // swap the clone for the read
        graph.replaceFixedWithFixed(readNode, clone);
    }
}
