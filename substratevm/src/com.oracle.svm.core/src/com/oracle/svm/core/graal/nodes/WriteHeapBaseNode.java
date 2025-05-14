/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.ReservedRegisters;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1, allowedUsageTypes = InputType.Memory)
public class WriteHeapBaseNode extends FixedWithNextNode implements LIRLowerable, SingleMemoryKill {
    public static final NodeClass<WriteHeapBaseNode> TYPE = NodeClass.create(WriteHeapBaseNode.class);

    @Input protected ValueNode value;

    protected WriteHeapBaseNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        tool.emitWriteRegister(ReservedRegisters.singleton().getHeapBaseRegister(), gen.operand(value), tool.getLIRKind(FrameAccess.getWordStamp()));
    }

    @NodeIntrinsic
    public static native void writeCurrentVMHeapBase(PointerBase value);

    /**
     * Kill memory to keep memory accesses that use this register as a base from floating across
     * changes of this register.
     */
    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }
}
