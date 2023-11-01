/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.FrameAccess;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class AArch64XPACNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<AArch64XPACNode> TYPE = NodeClass.create(AArch64XPACNode.class);
    @Input ValueNode address;

    public AArch64XPACNode(ValueNode address) {
        super(TYPE, FrameAccess.getWordStamp());
        this.address = address;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool lirGeneratorTool = gen.getLIRGeneratorTool();
        Value addressVal = gen.operand(address);
        Variable result = lirGeneratorTool.newVariable(LIRKind.combine(addressVal));
        lirGeneratorTool.append(new AArch64XPACOp(lirGeneratorTool.asAllocatable(addressVal),
                        result));
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native CodePointer stripAddress(CodePointer address);
}

/**
 * Uses xpaclri to strip address.
 */
@Opcode("XPAC")
class AArch64XPACOp extends AArch64LIRInstruction {
    @Use(REG) AllocatableValue input;
    @Def(REG) AllocatableValue result;
    @Temp(REG) AllocatableValue lrValue;

    public static final LIRInstructionClass<AArch64XPACOp> TYPE = LIRInstructionClass
                    .create(AArch64XPACOp.class);

    AArch64XPACOp(AllocatableValue input, AllocatableValue result) {
        super(TYPE);
        this.input = input;
        this.result = result;
        this.lrValue = AArch64.lr.asValue();
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        assert result.getPlatformKind().equals(AArch64Kind.QWORD);
        assert input.getPlatformKind().equals(AArch64Kind.QWORD);

        Register dst = asRegister(result);
        Register src = asRegister(input);
        Register lr = asRegister(lrValue);
        assert lr.equals(AArch64.lr);

        masm.mov(64, lr, src);
        masm.xpaclri();
        masm.mov(64, dst, lr);
    }
}
