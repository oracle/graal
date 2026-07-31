/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateTarget;

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
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class AArch64PACNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<AArch64PACNode> TYPE = NodeClass.create(AArch64PACNode.class);
    @Input ValueNode address;
    @Input ValueNode modifier;

    public AArch64PACNode(ValueNode address, ValueNode modifier) {
        super(TYPE, SubstrateTarget.getWordStamp());
        this.address = address;
        this.modifier = modifier;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool lirGeneratorTool = gen.getLIRGeneratorTool();
        Value addressValue = gen.operand(address);
        Value modifierValue = gen.operand(modifier);
        Variable result = lirGeneratorTool.newVariable(LIRKind.combine(addressValue));
        lirGeneratorTool.append(new AArch64PACOp(lirGeneratorTool.asAllocatable(addressValue),
                        lirGeneratorTool.asAllocatable(modifierValue), result));
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native CodePointer signAddress(CodePointer address, Pointer modifier);
}

/**
 * Uses PACIA to sign an instruction address with key A and an explicit modifier.
 */
@Opcode("PAC")
class AArch64PACOp extends AArch64LIRInstruction {
    @Use(REG) AllocatableValue input;
    @Alive(REG) AllocatableValue modifier;
    @Def(REG) AllocatableValue result;

    public static final LIRInstructionClass<AArch64PACOp> TYPE = LIRInstructionClass.create(AArch64PACOp.class);

    AArch64PACOp(AllocatableValue input, AllocatableValue modifier, AllocatableValue result) {
        super(TYPE);
        this.input = input;
        this.modifier = modifier;
        this.result = result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        assert result.getPlatformKind().equals(AArch64Kind.QWORD);
        assert input.getPlatformKind().equals(AArch64Kind.QWORD);
        assert modifier.getPlatformKind().equals(AArch64Kind.QWORD);

        Register dst = asRegister(result);
        masm.mov(64, dst, asRegister(input));
        masm.pacia(dst, asRegister(modifier));
    }
}
