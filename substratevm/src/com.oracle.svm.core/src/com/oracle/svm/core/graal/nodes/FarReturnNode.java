/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGenerator;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;

import jdk.vm.ci.meta.AllocatableValue;

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class FarReturnNode extends ControlSinkNode implements LIRLowerable {
    public static final NodeClass<FarReturnNode> TYPE = NodeClass.create(FarReturnNode.class);

    @Input protected ValueNode result;
    @Input protected ValueNode sp;
    @Input protected ValueNode ip;
    private final boolean fromMethodWithCalleeSavedRegisters;

    public FarReturnNode(ValueNode result, ValueNode sp, ValueNode ip, boolean fromMethodWithCalleeSavedRegisters) {
        super(TYPE, StampFactory.forVoid());
        this.result = result;
        this.sp = sp;
        this.ip = ip;
        this.fromMethodWithCalleeSavedRegisters = fromMethodWithCalleeSavedRegisters;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGenerator lirGenTool = (LIRGenerator) gen.getLIRGeneratorTool();
        AllocatableValue resultOperand = lirGenTool.resultOperandFor(result.getStackKind(), LIRKind.fromJavaKind(lirGenTool.target().arch, result.getStackKind()));
        lirGenTool.emitMove(resultOperand, gen.operand(result));

        ((SubstrateLIRGenerator) lirGenTool).emitFarReturn(resultOperand, gen.operand(sp), gen.operand(ip), fromMethodWithCalleeSavedRegisters);
    }
}
