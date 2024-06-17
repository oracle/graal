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
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.HalfReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
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
@SyncPort(from = "https://github.com/openjdk/jdk/blob/8032d640c0d34fe507392a1d4faa4ff2005c771d/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L3605-L3694",
          sha1 = "64b4f4aa44a5201f87d28ee048721dcd3c3231ed")
// @formatter:on
public final class AArch64SHA1Op extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64SHA1Op> TYPE = LIRInstructionClass.create(AArch64SHA1Op.class);

    @Alive({REG}) private Value bufValue;
    @Alive({REG}) private Value stateValue;
    @Alive({REG, ILLEGAL}) private Value ofsValue;
    @Alive({REG, ILLEGAL}) private Value limitValue;

    @Def({REG, ILLEGAL}) private Value resultValue;

    @Temp({REG, ILLEGAL}) private Value bufTempValue;
    @Temp({REG, ILLEGAL}) private Value ofsTempValue;

    @Temp({REG}) private Value[] temps;

    private final boolean multiBlock;

    public AArch64SHA1Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue) {
        this(tool, bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AArch64SHA1Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue,
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
                        // v8-v15 not used by the intrinsic
                        v16.asValue(),
                        v17.asValue(),
                        v18.asValue(),
                        v19.asValue(),
                        v20.asValue(),
                        v21.asValue(),
                        v22.asValue(),
        };
    }

    static ArrayDataPointerConstant keys = new ArrayDataPointerConstant(new int[]{
            // @formatter:off
            0x5a827999, 0x6ed9eba1, 0x8f1bbcdc, 0xca62c1d6
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
            GraalError.guarantee(ofsValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid ofsValue kind: %s", ofsValue);
            GraalError.guarantee(limitValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid limitValue kind: %s", limitValue);

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

        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg.getRegister();

            // load the keys into v0..v3
            crb.recordDataReferenceInCode(keys);
            masm.adrpAdd(rscratch1);
            masm.neon.ld4rVVVV(FullReg, ElementSize.Word, v0, v1, v2, v3, AArch64Address.createStructureNoOffsetAddress(rscratch1));
        }

        // load 5 words state into v6, v7
        masm.fldr(128, v6, AArch64Address.createImmediateAddress(128, IMMEDIATE_SIGNED_UNSCALED, state, 0));
        masm.fldr(32, v7, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, state, 16));

        Label labelSHA1Loop = new Label();
        masm.bind(labelSHA1Loop);
        // load 64 bytes of data into v16..v19
        masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Word, v16, v17, v18, v19,
                        multiBlock ? AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Word, buf, 64)
                                        : AArch64Address.createStructureNoOffsetAddress(buf));
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v16, v16);
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v17, v17);
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v18, v18);
        masm.neon.rev32VV(FullReg, ElementSize.Byte, v19, v19);

        // do the sha1
        masm.neon.addVVV(FullReg, ElementSize.Word, v4, v16, v0);
        masm.neon.orrVVV(FullReg, v20, v6, v6);

        Register d0 = v16;
        Register d1 = v17;
        Register d2 = v18;
        Register d3 = v19;

        for (int round = 0; round < 20; round++) {
            Register tmp1 = ((round & 1) == 1) ? v4 : v5;
            Register tmp2 = ((round & 1) == 1) ? v21 : v22;
            Register tmp3 = (round != 0) ? (((round & 1) == 1) ? v22 : v21) : v7;
            Register tmp4 = ((round & 1) == 1) ? v5 : v4;
            Register key = (round < 4) ? v0 : ((round < 9) ? v1 : ((round < 14) ? v2 : v3));

            if (round < 16) {
                masm.neon.sha1su0(d0, d1, d2);
            }
            if (round < 19) {
                masm.neon.addVVV(FullReg, ElementSize.Word, tmp1, d1, key);
            }
            masm.neon.sha1h(tmp2, v20);
            if (round < 5) {
                masm.neon.sha1c(v20, tmp3, tmp4);
            } else if (round < 10 || round >= 15) {
                masm.neon.sha1p(v20, tmp3, tmp4);
            } else {
                masm.neon.sha1m(v20, tmp3, tmp4);
            }
            if (round < 16) {
                masm.neon.sha1su1(d0, d3);
            }

            tmp1 = d0;
            d0 = d1;
            d1 = d2;
            d2 = d3;
            d3 = tmp1;
        }

        masm.neon.addVVV(HalfReg, ElementSize.Word, v7, v7, v21);
        masm.neon.addVVV(FullReg, ElementSize.Word, v6, v6, v20);

        if (multiBlock) {
            masm.add(32, ofs, ofs, 64);
            masm.cmp(32, ofs, limit);
            masm.branchConditionally(ConditionFlag.LE, labelSHA1Loop);

            GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
            masm.mov(32, asRegister(resultValue), ofs); // return ofs
        }

        masm.fstr(128, v6, AArch64Address.createImmediateAddress(128, IMMEDIATE_SIGNED_UNSCALED, state, 0));
        masm.fstr(32, v7, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, state, 16));
    }

}
