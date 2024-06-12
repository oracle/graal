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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PRE_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.EQ;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.NE;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off

@SyncPort(from = "https://github.com/openjdk/jdk/blob/43a2f17342af8f5bf1f5823df9fa0bf0bdfdfce2/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L4714-L4733",
          sha1 = "57f40186d75104a5e607d6fc047bbd50ef246590")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/aaaa86b57172d45d1126c50efc270c6e49aba7a5/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L4032-L4068",
          sha1 = "33649be9177daf5f0b4817d807458a5ff8c00365")
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
