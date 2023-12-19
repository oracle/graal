/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v10;
import static jdk.vm.ci.aarch64.AArch64.v11;
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
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.v8;
import static jdk.vm.ci.aarch64.AArch64.v9;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.LE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/ce8399fd6071766114f5f201b6e44a7abdba9f5a/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L3700-L3812",
          sha1 = "f226b109da456148c11f83d1bcd78d14aac862cf")
// @formatter:on
public final class AArch64SHA256Op extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64SHA256Op> TYPE = LIRInstructionClass.create(AArch64SHA256Op.class);

    @Alive({REG}) private Value bufValue;
    @Alive({REG}) private Value stateValue;
    @Alive({REG, ILLEGAL}) private Value ofsValue;
    @Alive({REG, ILLEGAL}) private Value limitValue;

    @Def({REG, ILLEGAL}) private Value resultValue;

    @Temp({REG, ILLEGAL}) private Value bufTempValue;
    @Temp({REG, ILLEGAL}) private Value ofsTempValue;

    @Temp({REG}) private Value[] temps;

    private final boolean multiBlock;

    public AArch64SHA256Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue) {
        this(tool, bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AArch64SHA256Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue,
                    AllocatableValue limitValue, AllocatableValue resultValue, boolean multiBlock) {
        super(TYPE);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;
        this.resultValue = resultValue;

        this.multiBlock = multiBlock;

        if (multiBlock) {
            this.bufTempValue = tool.newVariable(bufValue.getValueKind());
            this.ofsTempValue = tool.newVariable(ofsValue.getValueKind());
        } else {
            this.bufTempValue = Value.ILLEGAL;
            this.ofsTempValue = Value.ILLEGAL;
        }

        this.temps = new Value[]{
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
                        // v5 not used by the intrinsic
                        v6.asValue(),
                        v7.asValue(),
                        v8.asValue(),
                        v9.asValue(),
                        v10.asValue(),
                        v11.asValue(),
                        // v12-v15 not used by the intrinsic
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

    static ArrayDataPointerConstant roundConsts = new ArrayDataPointerConstant(new int[]{
            // @formatter:off
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
            0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
            0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
            0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
            // @formatter:on
    }, 16);

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(bufValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid bufValue kind: %s", bufValue);
        GraalError.guarantee(stateValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);

        Register buf;
        Register ofs;
        Register state = asRegister(stateValue);
        Register limit;

        if (multiBlock) {
            GraalError.guarantee(ofsValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid ofsValue kind: %s", ofsValue);
            GraalError.guarantee(limitValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid limitValue kind: %s", limitValue);

            buf = asRegister(bufTempValue);
            ofs = asRegister(ofsTempValue);
            limit = asRegister(limitValue);

            masm.mov(64, buf, asRegister(bufValue));
            masm.mov(32, ofs, asRegister(ofsValue));
        } else {
            buf = asRegister(bufValue);
            ofs = Register.None;
            limit = Register.None;
        }

        // We have marked v8-v11 as @Temp. The register allocator will take care of the spilling.
        // masm.fstp(64, v8, v9, [sp, #-32]!)
        // masm.fstp(64, v10, v11, [sp, #16])

        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg.getRegister();

            // load 16 keys to v16..v31
            crb.recordDataReferenceInCode(roundConsts);
            masm.adrpAdd(rscratch1);
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Word, v16, v17, v18, v19,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Word, rscratch1, 64));
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Word, v20, v21, v22, v23,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Word, rscratch1, 64));
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Word, v24, v25, v26, v27,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Word, rscratch1, 64));
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Word, v28, v29, v30, v31,
                            AArch64Address.createStructureNoOffsetAddress(rscratch1));
        }

        masm.fldp(128, v0, v1, AArch64Address.createPairBaseRegisterOnlyAddress(128, state));

        Label labelSHA256Loop = new Label();
        masm.bind(labelSHA256Loop);

        masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Word, v8, v9, v10, v11,
                        multiBlock ? AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Word, buf, 64)
                                        : AArch64Address.createStructureNoOffsetAddress(buf));
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v8, v8);
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v9, v9);
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v10, v10);
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v11, v11);

        masm.neon.addVVV(FullReg, ElementSize.Word, v6, v8, v16);
        masm.neon.orrVVV(FullReg, v2, v0, v0);
        masm.neon.orrVVV(FullReg, v3, v1, v1);

        Register d0 = v8;
        Register d1 = v9;
        Register d2 = v10;
        Register d3 = v11;

        for (int round = 0; round < 16; round++) {
            Register tmp1 = ((round & 1) == 1) ? v6 : v7;
            Register tmp2 = ((round & 1) == 1) ? v7 : v6;
            // tmp3 and tmp4 from the original stub are not used

            if (round < 12) {
                masm.neon.sha256su0(d0, d1);
            }
            masm.neon.orrVVV(FullReg, v4, v2, v2);
            if (round < 15) {
                masm.neon.addVVV(FullReg, ElementSize.Word, tmp1, d1, AArch64AESEncryptOp.asFloatRegister(v0, round + 17));
            }
            masm.neon.sha256h(v2, v3, tmp2);
            masm.neon.sha256h2(v3, v4, tmp2);
            if (round < 12) {
                masm.neon.sha256su1(d0, d2, d3);
            }

            tmp1 = d0;
            d0 = d1;
            d1 = d2;
            d2 = d3;
            d3 = tmp1;
        }

        masm.neon.addVVV(FullReg, ElementSize.Word, v0, v0, v2);
        masm.neon.addVVV(FullReg, ElementSize.Word, v1, v1, v3);

        if (multiBlock) {
            masm.add(32, ofs, ofs, 64);
            masm.cmp(32, ofs, limit);
            masm.branchConditionally(LE, labelSHA256Loop);

            GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
            masm.mov(32, asRegister(resultValue), ofs); // return ofs
        }

        // We have marked v8-v11 as @Temp. The register allocator will take care of the spilling.
        // masm.fldp(64, v10, v11, [sp,#16])
        // masm.fldp(64, v8, v9, [sp],#32)

        masm.fstp(128, v0, v1, AArch64Address.createPairBaseRegisterOnlyAddress(128, state));
    }

}
