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
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_1R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.LE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/8032d640c0d34fe507392a1d4faa4ff2005c771d/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L3811-L3984",
          sha1 = "9a27893e95da304e616ebd2105529e39d9634483")
// @formatter:on
public final class AArch64SHA512Op extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64SHA512Op> TYPE = LIRInstructionClass.create(AArch64SHA512Op.class);

    @Alive({REG}) private Value bufValue;
    @Alive({REG}) private Value stateValue;
    @Alive({REG, ILLEGAL}) private Value ofsValue;
    @Alive({REG, ILLEGAL}) private Value limitValue;

    @Def({REG, ILLEGAL}) private Value resultValue;

    @Temp({REG, ILLEGAL}) private Value bufTempValue;
    @Temp({REG, ILLEGAL}) private Value ofsTempValue;

    @Temp({REG}) private Value[] temps;

    private final boolean multiBlock;

    public AArch64SHA512Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue) {
        this(tool, bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AArch64SHA512Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue,
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

    static ArrayDataPointerConstant roundConsts = new ArrayDataPointerConstant(new long[]{
            // @formatter:off
            0x428A2F98D728AE22L, 0x7137449123EF65CDL, 0xB5C0FBCFEC4D3B2FL,
            0xE9B5DBA58189DBBCL, 0x3956C25BF348B538L, 0x59F111F1B605D019L,
            0x923F82A4AF194F9BL, 0xAB1C5ED5DA6D8118L, 0xD807AA98A3030242L,
            0x12835B0145706FBEL, 0x243185BE4EE4B28CL, 0x550C7DC3D5FFB4E2L,
            0x72BE5D74F27B896FL, 0x80DEB1FE3B1696B1L, 0x9BDC06A725C71235L,
            0xC19BF174CF692694L, 0xE49B69C19EF14AD2L, 0xEFBE4786384F25E3L,
            0x0FC19DC68B8CD5B5L, 0x240CA1CC77AC9C65L, 0x2DE92C6F592B0275L,
            0x4A7484AA6EA6E483L, 0x5CB0A9DCBD41FBD4L, 0x76F988DA831153B5L,
            0x983E5152EE66DFABL, 0xA831C66D2DB43210L, 0xB00327C898FB213FL,
            0xBF597FC7BEEF0EE4L, 0xC6E00BF33DA88FC2L, 0xD5A79147930AA725L,
            0x06CA6351E003826FL, 0x142929670A0E6E70L, 0x27B70A8546D22FFCL,
            0x2E1B21385C26C926L, 0x4D2C6DFC5AC42AEDL, 0x53380D139D95B3DFL,
            0x650A73548BAF63DEL, 0x766A0ABB3C77B2A8L, 0x81C2C92E47EDAEE6L,
            0x92722C851482353BL, 0xA2BFE8A14CF10364L, 0xA81A664BBC423001L,
            0xC24B8B70D0F89791L, 0xC76C51A30654BE30L, 0xD192E819D6EF5218L,
            0xD69906245565A910L, 0xF40E35855771202AL, 0x106AA07032BBD1B8L,
            0x19A4C116B8D2D0C8L, 0x1E376C085141AB53L, 0x2748774CDF8EEB99L,
            0x34B0BCB5E19B48A8L, 0x391C0CB3C5C95A63L, 0x4ED8AA4AE3418ACBL,
            0x5B9CCA4F7763E373L, 0x682E6FF3D6B2B8A3L, 0x748F82EE5DEFB2FCL,
            0x78A5636F43172F60L, 0x84C87814A1F0AB72L, 0x8CC702081A6439ECL,
            0x90BEFFFA23631E28L, 0xA4506CEBDE82BDE9L, 0xBEF9A3F7B2C67915L,
            0xC67178F2E372532BL, 0xCA273ECEEA26619CL, 0xD186B8C721C0C207L,
            0xEADA7DD6CDE0EB1EL, 0xF57D4F7FEE6ED178L, 0x06F067AA72176FBAL,
            0x0A637DC5A2C898A6L, 0x113F9804BEF90DAEL, 0x1B710B35131C471BL,
            0x28DB77F523047D84L, 0x32CAAB7B40C72493L, 0x3C9EBE0A15C9BEBCL,
            0x431D67C49C100D4CL, 0x4CC5D4BECB3E42B6L, 0x597F299CFC657E2AL,
            0x5FCB6FAB3AD6FAECL, 0x6C44198C4A475817L
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

        // We have marked v8-v15 as @Temp. The register allocator will take care of the spilling.

        Label labelSHA512Loop = new Label();

        try (AArch64MacroAssembler.ScratchRegister scratchReg1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg1.getRegister();
            Register rscratch2 = scratchReg2.getRegister();

            // load state
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.DoubleWord, v8, v9, v10, v11,
                            AArch64Address.createStructureNoOffsetAddress(state));

            // load first 4 round constants
            crb.recordDataReferenceInCode(roundConsts);
            masm.adrpAdd(rscratch1);

            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.DoubleWord, v20, v21, v22, v23,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.DoubleWord, rscratch1, 64));

            masm.bind(labelSHA512Loop);
            // load 128B of data into v12..v19
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.DoubleWord, v12, v13, v14, v15,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.DoubleWord, buf, 64));
            masm.neon.ld1MultipleVVVV(FullReg, ElementSize.DoubleWord, v16, v17, v18, v19,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.DoubleWord, buf, 64));
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v12, v12);
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v13, v13);
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v14, v14);
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v15, v15);
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v16, v16);
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v17, v17);
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v18, v18);
            masm.neon.rev64VV(FullReg, ElementSize.Byte, v19, v19);

            masm.mov(64, rscratch2, rscratch1);

            masm.neon.moveVV(FullReg, v0, v8);
            masm.neon.moveVV(FullReg, v1, v9);
            masm.neon.moveVV(FullReg, v2, v10);
            masm.neon.moveVV(FullReg, v3, v11);

            sha512Dround(masm, rscratch2, 0, v0, v1, v2, v3, v4, v20, v24, v12, v13, v19, v16, v17);
            sha512Dround(masm, rscratch2, 1, v3, v0, v4, v2, v1, v21, v25, v13, v14, v12, v17, v18);
            sha512Dround(masm, rscratch2, 2, v2, v3, v1, v4, v0, v22, v26, v14, v15, v13, v18, v19);
            sha512Dround(masm, rscratch2, 3, v4, v2, v0, v1, v3, v23, v27, v15, v16, v14, v19, v12);
            sha512Dround(masm, rscratch2, 4, v1, v4, v3, v0, v2, v24, v28, v16, v17, v15, v12, v13);
            sha512Dround(masm, rscratch2, 5, v0, v1, v2, v3, v4, v25, v29, v17, v18, v16, v13, v14);
            sha512Dround(masm, rscratch2, 6, v3, v0, v4, v2, v1, v26, v30, v18, v19, v17, v14, v15);
            sha512Dround(masm, rscratch2, 7, v2, v3, v1, v4, v0, v27, v31, v19, v12, v18, v15, v16);
            sha512Dround(masm, rscratch2, 8, v4, v2, v0, v1, v3, v28, v24, v12, v13, v19, v16, v17);
            sha512Dround(masm, rscratch2, 9, v1, v4, v3, v0, v2, v29, v25, v13, v14, v12, v17, v18);
            sha512Dround(masm, rscratch2, 10, v0, v1, v2, v3, v4, v30, v26, v14, v15, v13, v18, v19);
            sha512Dround(masm, rscratch2, 11, v3, v0, v4, v2, v1, v31, v27, v15, v16, v14, v19, v12);
            sha512Dround(masm, rscratch2, 12, v2, v3, v1, v4, v0, v24, v28, v16, v17, v15, v12, v13);
            sha512Dround(masm, rscratch2, 13, v4, v2, v0, v1, v3, v25, v29, v17, v18, v16, v13, v14);
            sha512Dround(masm, rscratch2, 14, v1, v4, v3, v0, v2, v26, v30, v18, v19, v17, v14, v15);
            sha512Dround(masm, rscratch2, 15, v0, v1, v2, v3, v4, v27, v31, v19, v12, v18, v15, v16);
            sha512Dround(masm, rscratch2, 16, v3, v0, v4, v2, v1, v28, v24, v12, v13, v19, v16, v17);
            sha512Dround(masm, rscratch2, 17, v2, v3, v1, v4, v0, v29, v25, v13, v14, v12, v17, v18);
            sha512Dround(masm, rscratch2, 18, v4, v2, v0, v1, v3, v30, v26, v14, v15, v13, v18, v19);
            sha512Dround(masm, rscratch2, 19, v1, v4, v3, v0, v2, v31, v27, v15, v16, v14, v19, v12);
            sha512Dround(masm, rscratch2, 20, v0, v1, v2, v3, v4, v24, v28, v16, v17, v15, v12, v13);
            sha512Dround(masm, rscratch2, 21, v3, v0, v4, v2, v1, v25, v29, v17, v18, v16, v13, v14);
            sha512Dround(masm, rscratch2, 22, v2, v3, v1, v4, v0, v26, v30, v18, v19, v17, v14, v15);
            sha512Dround(masm, rscratch2, 23, v4, v2, v0, v1, v3, v27, v31, v19, v12, v18, v15, v16);
            sha512Dround(masm, rscratch2, 24, v1, v4, v3, v0, v2, v28, v24, v12, v13, v19, v16, v17);
            sha512Dround(masm, rscratch2, 25, v0, v1, v2, v3, v4, v29, v25, v13, v14, v12, v17, v18);
            sha512Dround(masm, rscratch2, 26, v3, v0, v4, v2, v1, v30, v26, v14, v15, v13, v18, v19);
            sha512Dround(masm, rscratch2, 27, v2, v3, v1, v4, v0, v31, v27, v15, v16, v14, v19, v12);
            sha512Dround(masm, rscratch2, 28, v4, v2, v0, v1, v3, v24, v28, v16, v17, v15, v12, v13);
            sha512Dround(masm, rscratch2, 29, v1, v4, v3, v0, v2, v25, v29, v17, v18, v16, v13, v14);
            sha512Dround(masm, rscratch2, 30, v0, v1, v2, v3, v4, v26, v30, v18, v19, v17, v14, v15);
            sha512Dround(masm, rscratch2, 31, v3, v0, v4, v2, v1, v27, v31, v19, v12, v18, v15, v16);
            sha512Dround(masm, rscratch2, 32, v2, v3, v1, v4, v0, v28, v24, v12, v0, v0, v0, v0);
            sha512Dround(masm, rscratch2, 33, v4, v2, v0, v1, v3, v29, v25, v13, v0, v0, v0, v0);
            sha512Dround(masm, rscratch2, 34, v1, v4, v3, v0, v2, v30, v26, v14, v0, v0, v0, v0);
            sha512Dround(masm, rscratch2, 35, v0, v1, v2, v3, v4, v31, v27, v15, v0, v0, v0, v0);
            sha512Dround(masm, rscratch2, 36, v3, v0, v4, v2, v1, v24, v0, v16, v0, v0, v0, v0);
            sha512Dround(masm, rscratch2, 37, v2, v3, v1, v4, v0, v25, v0, v17, v0, v0, v0, v0);
            sha512Dround(masm, rscratch2, 38, v4, v2, v0, v1, v3, v26, v0, v18, v0, v0, v0, v0);
            sha512Dround(masm, rscratch2, 39, v1, v4, v3, v0, v2, v27, v0, v19, v0, v0, v0, v0);

            masm.neon.addVVV(FullReg, ElementSize.DoubleWord, v8, v8, v0);
            masm.neon.addVVV(FullReg, ElementSize.DoubleWord, v9, v9, v1);
            masm.neon.addVVV(FullReg, ElementSize.DoubleWord, v10, v10, v2);
            masm.neon.addVVV(FullReg, ElementSize.DoubleWord, v11, v11, v3);

            if (multiBlock) {
                masm.add(32, ofs, ofs, 128);
                masm.cmp(32, ofs, limit);
                masm.branchConditionally(LE, labelSHA512Loop);

                GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
                masm.mov(32, asRegister(resultValue), ofs); // return ofs
            }

            masm.neon.st1MultipleVVVV(FullReg, ElementSize.DoubleWord, v8, v9, v10, v11,
                            AArch64Address.createStructureNoOffsetAddress(state));
        }
    }

    // Double rounds for sha512.
    private static void sha512Dround(AArch64MacroAssembler masm, Register rscratch2,
                    int dr, Register vi0, Register vi1, Register vi2, Register vi3, Register vi4,
                    Register vrc0, Register vrc1, Register vin0, Register vin1, Register vin2, Register vin3, Register vin4) {
        if (dr < 36) {
            masm.neon.ld1MultipleV(FullReg, ElementSize.DoubleWord, vrc1,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_1R, FullReg, ElementSize.DoubleWord, rscratch2, 16));
        }
        masm.neon.addVVV(FullReg, ElementSize.DoubleWord, v5, vrc0, vin0);
        masm.neon.extVVV(FullReg, v6, vi2, vi3, 8);
        masm.neon.extVVV(FullReg, v5, v5, v5, 8);
        masm.neon.extVVV(FullReg, v7, vi1, vi2, 8);
        masm.neon.addVVV(FullReg, ElementSize.DoubleWord, vi3, vi3, v5);
        if (dr < 32) {
            masm.neon.extVVV(FullReg, v5, vin3, vin4, 8);
            masm.neon.sha512su0(vin0, vin1);
        }
        masm.neon.sha512h(vi3, v6, v7);
        if (dr < 32) {
            masm.neon.sha512su1(vin0, vin2, v5);
        }
        masm.neon.addVVV(FullReg, ElementSize.DoubleWord, vi4, vi1, vi3);
        masm.neon.sha512h2(vi3, vi1, vi0);
    }
}
