/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.Pointer;

import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class WriteReturnAddressNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<WriteReturnAddressNode> TYPE = NodeClass.create(WriteReturnAddressNode.class);

    @Input protected ValueNode value;

    protected WriteReturnAddressNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        TargetDescription target = gen.getLIRGeneratorTool().target();
        assert target.arch.getReturnAddressSize() > 0;

        LIRKind wordKind = LIRKind.fromJavaKind(target.arch, target.wordJavaKind);
        gen.getLIRGeneratorTool().emitMove(StackSlot.get(wordKind, -target.arch.getReturnAddressSize(), true), gen.operand(value));
    }

    @NodeIntrinsic()
    public static native void writeReturnAddress(Pointer value);
}
