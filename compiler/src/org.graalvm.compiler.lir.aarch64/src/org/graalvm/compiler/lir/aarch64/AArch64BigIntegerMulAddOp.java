/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PRE_INDEXED;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.EQ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.NE;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off

@StubPort(path      = "src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp",
          lineStart = 4531,
          lineEnd   = 4550,
          commit    = "fbc036e7454720b589d99a8cae30369a10471528",
          sha1      = "4000b30c24bfc830549474ba410a18b3d3892915")
@StubPort(path      = "src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp",
          lineStart = 3517,
          lineEnd   = 3553,
          commit    = "fbc036e7454720b589d99a8cae30369a10471528",
          sha1      = "a182dc046945490e566e6498c79677484c717a9c")
// @formatter:on
public final class AArch64BigIntegerMulAddOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64BigIntegerMulAddOp> TYPE = LIRInstructionClass.create(AArch64BigIntegerMulAddOp.class);

    @Def({REG}) private Value resultValue;

    @Alive({REG}) private Value outValue;
    @Alive({REG}) private Value inValue;
    @Alive({REG}) private Value offsetValue;
    @Alive({REG}) private Value lenValue;
    @Alive({REG}) private Value kValue;

    @Temp({REG}) private Value outValueTemp;
    @Temp({REG}) private Value inValueTemp;
    @Temp({REG}) private Value offsetValueTemp;
    @Temp({REG}) private Value lenValueTemp;

    public AArch64BigIntegerMulAddOp(LIRGeneratorTool tool,
                    Value outValue,
                    Value inValue,
                    Value offsetValue,
                    Value lenValue,
                    Value kValue,
                    Value resultValue) {
        super(TYPE);

        this.outValue = outValue;
        this.inValue = inValue;
        this.offsetValue = offsetValue;
        this.lenValue = lenValue;
        this.kValue = kValue;
        this.resultValue = resultValue;

        this.outValueTemp = tool.newVariable(outValue.getValueKind());
        this.inValueTemp = tool.newVariable(inValue.getValueKind());
        this.offsetValueTemp = tool.newVariable(offsetValue.getValueKind());
        this.lenValueTemp = tool.newVariable(lenValue.getValueKind());
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(outValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid outValue kind: %s", outValue);
        GraalError.guarantee(inValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid inValue kind: %s", inValue);
        GraalError.guarantee(offsetValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid offsetValue kind: %s", offsetValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(kValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid kValue kind: %s", kValue);

        Register out = asRegister(outValueTemp);
        Register in = asRegister(inValueTemp);
        Register offset = asRegister(offsetValueTemp);
        Register len = asRegister(lenValueTemp);
        Register k = asRegister(kValue);

        masm.mov(64, out, asRegister(outValue));
        masm.mov(64, in, asRegister(inValue));
        masm.mov(32, offset, asRegister(offsetValue));
        masm.mov(32, len, asRegister(lenValue));

        try (ScratchRegister sr1 = masm.getScratchRegister();
                        ScratchRegister sr2 = masm.getScratchRegister()) {
            Register rscratch1 = sr1.getRegister();
            Register rscratch2 = sr2.getRegister();

            Label labelLoop = new Label();
            Label labelEnd = new Label();
            // pre-loop
            masm.cmp(64, len, zr); // cmp, not cbz/cbnz: to use condition twice => less branches
            masm.csel(64, out, zr, out, EQ);
            masm.branchConditionally(EQ, labelEnd);
            masm.add(64, in, in, len, LSL, 2); // in[j+1] address
            masm.add(64, offset, out, offset, LSL, 2); // out[offset + 1] address
            masm.mov(64, out, zr); // used to keep carry now
            masm.bind(labelLoop);

            masm.ldr(32, rscratch1, AArch64Address.createImmediateAddress(32, IMMEDIATE_PRE_INDEXED, in, -4));
            masm.madd(64, rscratch1, rscratch1, k, out);

            masm.ldr(32, rscratch2, AArch64Address.createImmediateAddress(32, IMMEDIATE_PRE_INDEXED, offset, -4));
            masm.add(64, rscratch1, rscratch1, rscratch2);
            masm.str(32, rscratch1, AArch64Address.createBaseRegisterOnlyAddress(32, offset));
            masm.lsr(64, out, rscratch1, 32);
            masm.subs(64, len, len, 1);
            masm.branchConditionally(NE, labelLoop);
            masm.bind(labelEnd);

            masm.mov(32, asRegister(resultValue), out);
        }
    }

}
