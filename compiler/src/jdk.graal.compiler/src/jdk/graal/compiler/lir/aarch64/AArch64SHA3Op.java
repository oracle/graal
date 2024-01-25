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
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_3R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.HalfReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
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
@SyncPort(from = "https://github.com/openjdk/jdk/blob/ce8399fd6071766114f5f201b6e44a7abdba9f5a/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L3989-L4211",
          sha1 = "c17848fadbacb526e5da3c4e7c2a300c8160e092")
// @formatter:on
public final class AArch64SHA3Op extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64SHA3Op> TYPE = LIRInstructionClass.create(AArch64SHA3Op.class);

    @Alive({REG}) private Value bufValue;
    @Alive({REG}) private Value stateValue;
    @Alive({REG}) private Value blockSizeValue;
    @Alive({REG, ILLEGAL}) private Value ofsValue;
    @Alive({REG, ILLEGAL}) private Value limitValue;

    @Temp({REG}) private Value blockSizeTempValue;

    @Def({REG, ILLEGAL}) private Value resultValue;

    @Temp({REG, ILLEGAL}) private Value bufTempValue;
    @Temp({REG, ILLEGAL}) private Value ofsTempValue;

    @Temp({REG}) private Value[] temps;

    private final boolean multiBlock;

    public AArch64SHA3Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue blockSizeValue) {
        this(tool, bufValue, stateValue, blockSizeValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AArch64SHA3Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue blockSizeValue, AllocatableValue ofsValue,
                    AllocatableValue limitValue, AllocatableValue resultValue, boolean multiBlock) {
        super(TYPE);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.blockSizeValue = blockSizeValue;

        this.ofsValue = ofsValue;
        this.limitValue = limitValue;
        this.resultValue = resultValue;

        this.bufTempValue = tool.newVariable(bufValue.getValueKind());
        this.blockSizeTempValue = tool.newVariable(blockSizeValue.getValueKind());

        this.multiBlock = multiBlock;

        if (multiBlock) {
            this.ofsTempValue = tool.newVariable(ofsValue.getValueKind());
        } else {
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
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL,
            0x8000000080008000L, 0x000000000000808BL, 0x0000000080000001L,
            0x8000000080008081L, 0x8000000000008009L, 0x000000000000008AL,
            0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
            0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L,
            0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
            0x000000000000800AL, 0x800000008000000AL, 0x8000000080008081L,
            0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
            // @formatter:on
    }, 16);

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(bufValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid bufValue kind: %s", bufValue);
        GraalError.guarantee(stateValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);
        GraalError.guarantee(blockSizeValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid blockSizeValue kind: %s", blockSizeValue);

        Register buf = asRegister(bufTempValue);
        Register state = asRegister(stateValue);
        Register blockSize = asRegister(blockSizeValue);

        Register blockSizeTail = asRegister(blockSizeTempValue);

        Register ofs;
        Register limit;

        masm.mov(64, buf, asRegister(bufValue));

        if (multiBlock) {
            GraalError.guarantee(ofsValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid ofsValue kind: %s", ofsValue);
            GraalError.guarantee(limitValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid limitValue kind: %s", limitValue);

            ofs = asRegister(ofsTempValue);
            limit = asRegister(limitValue);

            masm.mov(64, ofs, asRegister(ofsValue));
        } else {
            ofs = Register.None;
            limit = Register.None;
        }

        Label labelSHA3Loop = new Label();
        Label labelRounds24Loop = new Label();
        Label labelSHA3512OrSha3384 = new Label();
        Label labelSHAke128 = new Label();

        // We have marked v8-v15 as @Temp. The register allocator will take care of the spilling.

        try (ScratchRegister scratchReg1 = masm.getScratchRegister();
                        ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg1.getRegister();
            Register rscratch2 = scratchReg2.getRegister();

            // load state
            masm.add(64, rscratch1, state, 32);

            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v0, v1, v2, v3,
                            AArch64Address.createStructureNoOffsetAddress(state));
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v4, v5, v6, v7,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, rscratch1, 32));
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v8, v9, v10, v11,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, rscratch1, 32));
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v12, v13, v14, v15,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, rscratch1, 32));
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v16, v17, v18, v19,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, rscratch1, 32));
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v20, v21, v22, v23,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, rscratch1, 32));
            masm.neon.ld1MultipleV(HalfReg, ElementSize.DoubleWord, v24,
                            AArch64Address.createStructureNoOffsetAddress(rscratch1));

            masm.bind(labelSHA3Loop);

            // 24 keccak rounds
            masm.mov(rscratch2, 24);

            // load round_constants base
            crb.recordDataReferenceInCode(roundConsts);
            masm.adrpAdd(rscratch1);

            // load input
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.Byte, v25, v26, v27, v28,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.Byte, buf, 32));

            masm.neon.ld1MultipleVVV(HalfReg, ElementSize.Byte, v29, v30, v31,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_3R, HalfReg, ElementSize.Byte, buf, 24));
            masm.neon.eorVVV(HalfReg, v0, v0, v25);
            masm.neon.eorVVV(HalfReg, v1, v1, v26);
            masm.neon.eorVVV(HalfReg, v2, v2, v27);
            masm.neon.eorVVV(HalfReg, v3, v3, v28);
            masm.neon.eorVVV(HalfReg, v4, v4, v29);
            masm.neon.eorVVV(HalfReg, v5, v5, v30);
            masm.neon.eorVVV(HalfReg, v6, v6, v31);

            // block_size == 72, SHA3-512; block_size == 104, SHA3-384
            masm.tbz(blockSize, 7, labelSHA3512OrSha3384);

            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.Byte, v25, v26, v27, v28,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.Byte, buf, 32));
            masm.neon.ld1MultipleVVV(HalfReg, ElementSize.Byte, v29, v30, v31,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_3R, HalfReg, ElementSize.Byte, buf, 24));

            masm.neon.eorVVV(HalfReg, v7, v7, v25);
            masm.neon.eorVVV(HalfReg, v8, v8, v26);
            masm.neon.eorVVV(HalfReg, v9, v9, v27);
            masm.neon.eorVVV(HalfReg, v10, v10, v28);
            masm.neon.eorVVV(HalfReg, v11, v11, v29);
            masm.neon.eorVVV(HalfReg, v12, v12, v30);
            masm.neon.eorVVV(HalfReg, v13, v13, v31);

            masm.neon.ld1MultipleVVV(HalfReg, ElementSize.Byte, v25, v26, v27,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_3R, HalfReg, ElementSize.Byte, buf, 24));

            masm.neon.eorVVV(HalfReg, v14, v14, v25);
            masm.neon.eorVVV(HalfReg, v15, v15, v26);
            masm.neon.eorVVV(HalfReg, v16, v16, v27);

            // block_size == 136, bit4 == 0 and bit5 == 0, SHA3-256 or SHAKE256
            masm.and(32, blockSizeTail, blockSize, 48);
            masm.cbz(32, blockSizeTail, labelRounds24Loop);

            masm.tbnz(blockSize, 5, labelSHAke128);
            // block_size == 144, bit5 == 0, SHA3-244
            masm.fldr(64, v28, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, buf, 8));

            masm.neon.eorVVV(HalfReg, v17, v17, v28);
            masm.jmp(labelRounds24Loop);

            masm.bind(labelSHAke128);
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.Byte, v28, v29, v30, v31,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.Byte, buf, 32));

            masm.neon.eorVVV(HalfReg, v17, v17, v28);
            masm.neon.eorVVV(HalfReg, v18, v18, v29);
            masm.neon.eorVVV(HalfReg, v19, v19, v30);
            masm.neon.eorVVV(HalfReg, v20, v20, v31);
            masm.jmp(labelRounds24Loop); // block_size == 168, SHAKE128

            masm.bind(labelSHA3512OrSha3384);
            masm.neon.ld1MultipleVV(HalfReg, ElementSize.Byte, v25, v26,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_2R, HalfReg, ElementSize.Byte, buf, 16));
            masm.neon.eorVVV(HalfReg, v7, v7, v25);
            masm.neon.eorVVV(HalfReg, v8, v8, v26);
            masm.tbz(blockSize, 5, labelRounds24Loop); // SHA3-512

            // SHA3-384
            masm.neon.ld1MultipleVVVV(HalfReg, ElementSize.Byte, v27, v28, v29, v30,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, HalfReg, ElementSize.Byte, buf, 32));
            masm.neon.eorVVV(HalfReg, v9, v9, v27);
            masm.neon.eorVVV(HalfReg, v10, v10, v28);
            masm.neon.eorVVV(HalfReg, v11, v11, v29);
            masm.neon.eorVVV(HalfReg, v12, v12, v30);

            masm.bind(labelRounds24Loop);
            masm.sub(32, rscratch2, rscratch2, 1);

            masm.neon.eor3VVVV(v29, v4, v9, v14);
            masm.neon.eor3VVVV(v26, v1, v6, v11);
            masm.neon.eor3VVVV(v28, v3, v8, v13);
            masm.neon.eor3VVVV(v25, v0, v5, v10);
            masm.neon.eor3VVVV(v27, v2, v7, v12);
            masm.neon.eor3VVVV(v29, v29, v19, v24);
            masm.neon.eor3VVVV(v26, v26, v16, v21);
            masm.neon.eor3VVVV(v28, v28, v18, v23);
            masm.neon.eor3VVVV(v25, v25, v15, v20);
            masm.neon.eor3VVVV(v27, v27, v17, v22);

            masm.neon.rax1VVV(v30, v29, v26);
            masm.neon.rax1VVV(v26, v26, v28);
            masm.neon.rax1VVV(v28, v28, v25);
            masm.neon.rax1VVV(v25, v25, v27);
            masm.neon.rax1VVV(v27, v27, v29);

            masm.neon.eorVVV(FullReg, v0, v0, v30);
            masm.neon.xarVVVI(v29, v1, v25, (64 - 1));
            masm.neon.xarVVVI(v1, v6, v25, (64 - 44));
            masm.neon.xarVVVI(v6, v9, v28, (64 - 20));
            masm.neon.xarVVVI(v9, v22, v26, (64 - 61));
            masm.neon.xarVVVI(v22, v14, v28, (64 - 39));
            masm.neon.xarVVVI(v14, v20, v30, (64 - 18));
            masm.neon.xarVVVI(v31, v2, v26, (64 - 62));
            masm.neon.xarVVVI(v2, v12, v26, (64 - 43));
            masm.neon.xarVVVI(v12, v13, v27, (64 - 25));
            masm.neon.xarVVVI(v13, v19, v28, (64 - 8));
            masm.neon.xarVVVI(v19, v23, v27, (64 - 56));
            masm.neon.xarVVVI(v23, v15, v30, (64 - 41));
            masm.neon.xarVVVI(v15, v4, v28, (64 - 27));
            masm.neon.xarVVVI(v28, v24, v28, (64 - 14));
            masm.neon.xarVVVI(v24, v21, v25, (64 - 2));
            masm.neon.xarVVVI(v8, v8, v27, (64 - 55));
            masm.neon.xarVVVI(v4, v16, v25, (64 - 45));
            masm.neon.xarVVVI(v16, v5, v30, (64 - 36));
            masm.neon.xarVVVI(v5, v3, v27, (64 - 28));
            masm.neon.xarVVVI(v27, v18, v27, (64 - 21));
            masm.neon.xarVVVI(v3, v17, v26, (64 - 15));
            masm.neon.xarVVVI(v25, v11, v25, (64 - 10));
            masm.neon.xarVVVI(v26, v7, v26, (64 - 6));
            masm.neon.xarVVVI(v30, v10, v30, (64 - 3));

            masm.neon.bcaxVVVV(v20, v31, v22, v8);
            masm.neon.bcaxVVVV(v21, v8, v23, v22);
            masm.neon.bcaxVVVV(v22, v22, v24, v23);
            masm.neon.bcaxVVVV(v23, v23, v31, v24);
            masm.neon.bcaxVVVV(v24, v24, v8, v31);

            masm.neon.ld1rV(FullReg, ElementSize.DoubleWord, v31,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1R, FullReg, ElementSize.DoubleWord, rscratch1, 8));

            masm.neon.bcaxVVVV(v17, v25, v19, v3);
            masm.neon.bcaxVVVV(v18, v3, v15, v19);
            masm.neon.bcaxVVVV(v19, v19, v16, v15);
            masm.neon.bcaxVVVV(v15, v15, v25, v16);
            masm.neon.bcaxVVVV(v16, v16, v3, v25);

            masm.neon.bcaxVVVV(v10, v29, v12, v26);
            masm.neon.bcaxVVVV(v11, v26, v13, v12);
            masm.neon.bcaxVVVV(v12, v12, v14, v13);
            masm.neon.bcaxVVVV(v13, v13, v29, v14);
            masm.neon.bcaxVVVV(v14, v14, v26, v29);

            masm.neon.bcaxVVVV(v7, v30, v9, v4);
            masm.neon.bcaxVVVV(v8, v4, v5, v9);
            masm.neon.bcaxVVVV(v9, v9, v6, v5);
            masm.neon.bcaxVVVV(v5, v5, v30, v6);
            masm.neon.bcaxVVVV(v6, v6, v4, v30);

            masm.neon.bcaxVVVV(v3, v27, v0, v28);
            masm.neon.bcaxVVVV(v4, v28, v1, v0);
            masm.neon.bcaxVVVV(v0, v0, v2, v1);
            masm.neon.bcaxVVVV(v1, v1, v27, v2);
            masm.neon.bcaxVVVV(v2, v2, v28, v27);

            masm.neon.eorVVV(FullReg, v0, v0, v31);

            masm.cbnz(32, rscratch2, labelRounds24Loop);

            if (multiBlock) {
                masm.add(32, ofs, ofs, blockSize);
                masm.cmp(32, ofs, limit);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.LE, labelSHA3Loop);

                GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
                masm.mov(32, asRegister(resultValue), ofs); // return ofs
            }

            masm.neon.st1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v0, v1, v2, v3,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, state, 32));
            masm.neon.st1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v4, v5, v6, v7,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, state, 32));
            masm.neon.st1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v8, v9, v10, v11,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, state, 32));
            masm.neon.st1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v12, v13, v14, v15,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, state, 32));
            masm.neon.st1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v16, v17, v18, v19,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, state, 32));
            masm.neon.st1MultipleVVVV(HalfReg, ElementSize.DoubleWord, v20, v21, v22, v23,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST1_MULTIPLE_4R, HalfReg, ElementSize.DoubleWord, state, 32));
            masm.neon.st1MultipleV(HalfReg, ElementSize.DoubleWord, v24,
                            AArch64Address.createStructureNoOffsetAddress(state));

            // We have marked v8-v15 as @Temp. The register allocator will take care of the
            // spilling.
        }
    }

}
