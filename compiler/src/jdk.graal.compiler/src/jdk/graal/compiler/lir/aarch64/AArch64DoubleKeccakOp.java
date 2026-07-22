/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD4;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST4;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.DoubleWord;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v10;
import static jdk.vm.ci.aarch64.AArch64.v11;
import static jdk.vm.ci.aarch64.AArch64.v12;
import static jdk.vm.ci.aarch64.AArch64.v13;
import static jdk.vm.ci.aarch64.AArch64.v14;
import static jdk.vm.ci.aarch64.AArch64.v15;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.v8;
import static jdk.vm.ci.aarch64.AArch64.v9;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L4441-L4526",
          sha1 = "95ac4f9f10685ccead594d8757540a7bbf8d3368")
// @formatter:on
public final class AArch64DoubleKeccakOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64DoubleKeccakOp> TYPE = LIRInstructionClass.create(AArch64DoubleKeccakOp.class);

    @Use({OperandFlag.REG}) private Value state0Value;
    @Use({OperandFlag.REG}) private Value state1Value;
    @Def({OperandFlag.REG}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AArch64DoubleKeccakOp(AllocatableValue state0Value, AllocatableValue state1Value, AllocatableValue resultValue) {
        super(TYPE);

        guaranteeFixedRegister(state0Value, r0, "state0Value");
        guaranteeFixedRegister(state1Value, r1, "state1Value");
        guaranteeFixedRegister(resultValue, r0, "resultValue");

        this.state0Value = state0Value;
        this.state1Value = state1Value;
        this.resultValue = resultValue;

        this.temps = new Value[]{
                        r1.asValue(),
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
                        v5.asValue(),
                        v6.asValue(),
                        v7.asValue(),
                        v8.asValue(),
                        v9.asValue(),
                        v10.asValue(),
                        v11.asValue(),
                        v12.asValue(),
                        v13.asValue(),
                        v14.asValue(),
                        v15.asValue(),
                        v16.asValue(),
                        v17.asValue(),
                        v18.asValue(),
                        v19.asValue(),
                        v20.asValue(),
                        v21.asValue(),
                        v22.asValue(),
                        v23.asValue(),
                        v24.asValue(),
                        v25.asValue(),
                        v26.asValue(),
                        v27.asValue(),
                        v28.asValue(),
                        v29.asValue(),
                        v30.asValue(),
                        v31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register state0 = asRegister(state0Value);
        Register state1 = asRegister(state1Value);
        Register result = asRegister(resultValue);

        Label rounds24Loop = new Label();

        try (ScratchRegister scratchReg1 = masm.getScratchRegister();
                        ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg1.getRegister();
            Register rscratch2 = scratchReg2.getRegister();

            // load states
            masm.add(64, rscratch1, state0, 32);
            masm.neon.ld4SingleVVVV(DoubleWord, v0, v1, v2, v3, 0, AArch64Address.createStructureNoOffsetAddress(state0));
            masm.neon.ld4SingleVVVV(DoubleWord, v4, v5, v6, v7, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v8, v9, v10, v11, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v12, v13, v14, v15, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v16, v17, v18, v19, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v20, v21, v22, v23, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld1SingleV(DoubleWord, v24, 0, AArch64Address.createStructureNoOffsetAddress(rscratch1));
            masm.add(64, rscratch1, state1, 32);
            masm.neon.ld4SingleVVVV(DoubleWord, v0, v1, v2, v3, 1, AArch64Address.createStructureNoOffsetAddress(state1));
            masm.neon.ld4SingleVVVV(DoubleWord, v4, v5, v6, v7, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v8, v9, v10, v11, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v12, v13, v14, v15, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v16, v17, v18, v19, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld4SingleVVVV(DoubleWord, v20, v21, v22, v23, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4, FullReg, DoubleWord, rscratch1, 32));
            masm.neon.ld1SingleV(DoubleWord, v24, 1, AArch64Address.createStructureNoOffsetAddress(rscratch1));

            // 24 keccak rounds
            masm.mov(rscratch2, 24);

            // load round_constants base
            crb.recordDataReferenceInCode(AArch64SHA3Op.roundConsts);
            masm.adrpAdd(rscratch1);

            masm.bind(rounds24Loop);
            masm.sub(32, rscratch2, rscratch2, 1);

            AArch64SHA3Op.keccakRound(masm, rscratch1);

            masm.cbnz(32, rscratch2, rounds24Loop);

            masm.neon.st4SingleVVVV(DoubleWord, v0, v1, v2, v3, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state0, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v4, v5, v6, v7, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state0, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v8, v9, v10, v11, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state0, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v12, v13, v14, v15, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state0, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v16, v17, v18, v19, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state0, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v20, v21, v22, v23, 0,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state0, 32));
            masm.neon.st1SingleV(DoubleWord, v24, 0, AArch64Address.createStructureNoOffsetAddress(state0));
            masm.neon.st4SingleVVVV(DoubleWord, v0, v1, v2, v3, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state1, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v4, v5, v6, v7, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state1, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v8, v9, v10, v11, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state1, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v12, v13, v14, v15, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state1, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v16, v17, v18, v19, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state1, 32));
            masm.neon.st4SingleVVVV(DoubleWord, v20, v21, v22, v23, 1,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4, FullReg, DoubleWord, state1, 32));
            masm.neon.st1SingleV(DoubleWord, v24, 1, AArch64Address.createStructureNoOffsetAddress(state1));

            masm.mov(32, result, zr);
        }
    }
}
