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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexGeneralPurposeRMVOp.BZHI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVB2M;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTD_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMT2B;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMADDUBSW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMADDWD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPTESTMB;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z0;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm19;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm20;
import static jdk.vm.ci.amd64.AMD64.xmm21;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d91e3ef51121f605d6060d61571a2266adb74ca0/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L2326-L3002",
          sha1 = "980048eb20961d899fe5e033eb15121903fcc0a7")
// @formatter:on
@Opcode("AMD64_BASE64_DECODE_BLOCK")
public final class AMD64Base64DecodeOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64Base64DecodeOp> TYPE = LIRInstructionClass.create(AMD64Base64DecodeOp.class);

    private static final ArrayDataPointerConstant DECODE_VBMI_LOOKUP_LO = pointerConstant(16, new long[]{
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x3f8080803e808080L,
                    0x3b3a393837363534L,
                    0x8080808080803d3cL,
    });

    private static final ArrayDataPointerConstant DECODE_VBMI_LOOKUP_HI = pointerConstant(16, new long[]{
                    0x0605040302010080L,
                    0x0e0d0c0b0a090807L,
                    0x161514131211100fL,
                    0x8080808080191817L,
                    0x201f1e1d1c1b1a80L,
                    0x2827262524232221L,
                    0x302f2e2d2c2b2a29L,
                    0x8080808080333231L,
    });

    private static final ArrayDataPointerConstant DECODE_VBMI_LOOKUP_LO_URL = pointerConstant(16, new long[]{
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x8080808080808080L,
                    0x80803e8080808080L,
                    0x3b3a393837363534L,
                    0x8080808080803d3cL,
    });

    private static final ArrayDataPointerConstant DECODE_VBMI_LOOKUP_HI_URL = pointerConstant(16, new long[]{
                    0x0605040302010080L,
                    0x0e0d0c0b0a090807L,
                    0x161514131211100fL,
                    0x3f80808080191817L,
                    0x201f1e1d1c1b1a80L,
                    0x2827262524232221L,
                    0x302f2e2d2c2b2a29L,
                    0x8080808080333231L,
    });

    private static final ArrayDataPointerConstant DECODE_VBMI_PACK_VEC = pointerConstant(16, new long[]{
                    0x090a040506000102L,
                    0x161011120c0d0e08L,
                    0x1c1d1e18191a1415L,
                    0x292a242526202122L,
                    0x363031322c2d2e28L,
                    0x3c3d3e38393a3435L,
                    0x0000000000000000L,
                    0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant DECODE_VBMI_JOIN_0_1 = pointerConstant(16, new long[]{
                    0x090a040506000102L,
                    0x161011120c0d0e08L,
                    0x1c1d1e18191a1415L,
                    0x292a242526202122L,
                    0x363031322c2d2e28L,
                    0x3c3d3e38393a3435L,
                    0x494a444546404142L,
                    0x565051524c4d4e48L,
    });

    private static final ArrayDataPointerConstant DECODE_VBMI_JOIN_1_2 = pointerConstant(16, new long[]{
                    0x1c1d1e18191a1415L,
                    0x292a242526202122L,
                    0x363031322c2d2e28L,
                    0x3c3d3e38393a3435L,
                    0x494a444546404142L,
                    0x565051524c4d4e48L,
                    0x5c5d5e58595a5455L,
                    0x696a646566606162L,
    });

    private static final ArrayDataPointerConstant DECODE_VBMI_JOIN_2_3 = pointerConstant(16, new long[]{
                    0x363031322c2d2e28L,
                    0x3c3d3e38393a3435L,
                    0x494a444546404142L,
                    0x565051524c4d4e48L,
                    0x5c5d5e58595a5455L,
                    0x696a646566606162L,
                    0x767071726c6d6e68L,
                    0x7c7d7e78797a7475L,
    });

    private static final ArrayDataPointerConstant DECODE_AVX2_TABLES = pointerConstant(16, new long[]{
                    0x5f5f5f5f2f2f2f2fL,
                    0xfcfcfcfcffffffffL,
                    0x0000000100000000L,
                    0x0000000400000002L,
                    0x0000000600000005L,
                    0xffffffffffffffffL,
                    0x090a040506000102L,
                    0xffffffff0c0d0e08L,
                    0x090a040506000102L,
                    0xffffffff0c0d0e08L,
                    0x0001100001400140L
    });

    private static final ArrayDataPointerConstant DECODE_AVX2_LUT_TABLES = pointerConstant(16, new long[]{
                    0x1111111111111115L,
                    0x1a1b1b1b1a131111L,
                    0x1111111111111115L,
                    0x1a1b1b1b1a131111L,
                    0xb9b9bfbf04131000L,
                    0x0000000000000000L,
                    0xb9b9bfbf04131000L,
                    0x0000000000000000L,
                    0x1111111111111115L,
                    0x1b1b1a1b1b131111L,
                    0x1111111111111115L,
                    0x1b1b1a1b1b131111L,
                    0xb9b9bfbf0411e000L,
                    0x0000000000000000L,
                    0xb9b9bfbf0411e000L,
                    0x0000000000000000L,
                    0x0804080402011010L,
                    0x1010101010101010L,
                    0x0804080402011010L,
                    0x1010101010101010L
    });

    private static final ArrayDataPointerConstant DECODE_TABLE = pointerConstant(16, new long[]{
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0x3fffffff3effffffL,
                    0x3b3a393837363534L,
                    0xffffffffffff3d3cL,
                    0x06050403020100ffL,
                    0x0e0d0c0b0a090807L,
                    0x161514131211100fL,
                    0xffffffffff191817L,
                    0x201f1e1d1c1b1affL,
                    0x2827262524232221L,
                    0x302f2e2d2c2b2a29L,
                    0xffffffffff333231L,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    // URL table
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffff3effffffffffL,
                    0x3b3a393837363534L,
                    0xffffffffffff3d3cL,
                    0x06050403020100ffL,
                    0x0e0d0c0b0a090807L,
                    0x161514131211100fL,
                    0x3fffffffff191817L,
                    0x201f1e1d1c1b1affL,
                    0x2827262524232221L,
                    0x302f2e2d2c2b2a29L,
                    0xffffffffff333231L,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL,
                    0xffffffffffffffffL
    });

    @Def({OperandFlag.REG}) private Value resultValue;

    @Use({OperandFlag.REG}) private Value srcValue;
    @Use({OperandFlag.REG}) private Value spValue;
    @Use({OperandFlag.REG}) private Value slValue;
    @Use({OperandFlag.REG}) private Value dstValue;
    @Use({OperandFlag.REG}) private Value dpValue;
    @Use({OperandFlag.REG}) private Value urlValue;
    @Use({OperandFlag.REG}) private Value mimeValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AMD64Base64DecodeOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, AllocatableValue resultValue, AllocatableValue srcValue,
                    AllocatableValue spValue, AllocatableValue slValue, AllocatableValue dstValue, AllocatableValue dpValue, AllocatableValue urlValue, AllocatableValue mimeValue) {
        super(TYPE);
        this.resultValue = resultValue;
        this.srcValue = srcValue;
        this.spValue = spValue;
        this.slValue = slValue;
        this.dstValue = dstValue;
        this.dpValue = dpValue;
        this.urlValue = urlValue;
        this.mimeValue = mimeValue;
        GraalError.guarantee(resultValue instanceof RegisterValue resultValueReg && rax.equals(resultValueReg.getRegister()), "resultValue should be fixed to rax, but is %s", resultValue);
        GraalError.guarantee(srcValue instanceof RegisterValue srcValueReg && rdi.equals(srcValueReg.getRegister()), "srcValue should be fixed to rdi, but is %s", srcValue);
        GraalError.guarantee(spValue instanceof RegisterValue spValueReg && rsi.equals(spValueReg.getRegister()), "spValue should be fixed to rsi, but is %s", spValue);
        GraalError.guarantee(slValue instanceof RegisterValue slValueReg && rdx.equals(slValueReg.getRegister()), "slValue should be fixed to rdx, but is %s", slValue);
        GraalError.guarantee(dstValue instanceof RegisterValue dstValueReg && rcx.equals(dstValueReg.getRegister()), "dstValue should be fixed to rcx, but is %s", dstValue);
        GraalError.guarantee(dpValue instanceof RegisterValue dpValueReg && r8.equals(dpValueReg.getRegister()), "dpValue should be fixed to r8, but is %s", dpValue);
        GraalError.guarantee(urlValue instanceof RegisterValue urlValueReg && r9.equals(urlValueReg.getRegister()), "urlValue should be fixed to r9, but is %s", urlValue);
        GraalError.guarantee(mimeValue instanceof RegisterValue mimeValueReg && rbx.equals(mimeValueReg.getRegister()), "mimeValue should be fixed to rbx, but is %s", mimeValue);

        if (supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX512_VBMI, CPUFeature.AVX512BW)) {
            this.temps = AMD64LIRHelper.registersToValues(new Register[]{
                            rcx,
                            rdx,
                            rbx,
                            rsi,
                            rdi,
                            r8,
                            r9,
                            r10,
                            r11,
                            // vzeroupper clears upper bits of xmm0-xmm15.
                            xmm0,
                            xmm1,
                            xmm2,
                            xmm3,
                            xmm4,
                            xmm5,
                            xmm6,
                            xmm7,
                            xmm8,
                            xmm9,
                            xmm10,
                            xmm11,
                            xmm12,
                            xmm13,
                            xmm14,
                            xmm15,
                            xmm19,
                            xmm20,
                            xmm21,
                            k1,
                            k2,
                            k3,
            });
        } else if (supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX2)) {
            this.temps = AMD64LIRHelper.registersToValues(new Register[]{
                            rcx,
                            rdx,
                            rbx,
                            rsi,
                            rdi,
                            r8,
                            r9,
                            r10,
                            r11,
                            // vzeroupper clears upper bits of xmm0-xmm15.
                            xmm0,
                            xmm1,
                            xmm2,
                            xmm3,
                            xmm4,
                            xmm5,
                            xmm6,
                            xmm7,
                            xmm8,
                            xmm9,
                            xmm10,
                            xmm11,
                            xmm12,
                            xmm13,
                            xmm14,
                            xmm15,
            });
        } else {
            this.temps = AMD64LIRHelper.registersToValues(new Register[]{
                            rcx,
                            rdx,
                            rbx,
                            rsi,
                            rdi,
                            r8,
                            r9,
                            r10,
                            r11,
            });
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        masm.push(r12);
        masm.push(r13);
        masm.push(r14);
        masm.push(r15);

        Register source = asRegister(srcValue);
        Register startOffset = asRegister(spValue);
        Register endOffset = asRegister(slValue);
        Register dest = asRegister(dstValue);
        Register dp = asRegister(dpValue);
        Register isURL = asRegister(urlValue);
        Register isMIME = rbx;

        // AVX-512 register aliases:
        // lookupLo / lookupHi - translation tables for the low / high nibbles
        // errorVec - OR of translated and original bytes, used to detect invalid input
        // pack16Op / pack32Op - constants used to repack four 6-bit values into three bytes
        // input0-3 - source chunks loaded from the input
        // join01 / join12 / join23 - byte-permute tables used to combine adjacent 64-byte results
        // translated0-3 - translated 6-bit values
        // merged0-3 - packed 24-bit output bytes
        // pack24Bits - byte-permute table for a single 64-byte chunk
        // outputSize / outputMask / inputMask - final-chunk decode bookkeeping
        // inputInitialValidB64 / tmp / mask / invalidB64 - final-chunk scratch registers
        Register length = r14;

        Label labelProcess256 = new Label();
        Label labelProcess64 = new Label();
        Label labelProcess64Loop = new Label();
        Label labelExit = new Label();
        Label labelProcessData = new Label();
        Label labelLoadURL = new Label();
        Label labelContinue = new Label();
        Label labelFinalBit = new Label();
        Label labelPadding = new Label();
        Label labelDonePadding = new Label();
        Label labelBruteForce = new Label();
        Label labelForceLoop = new Label();
        Label labelBottomLoop = new Label();
        Label labelExitNoVZero = new Label();
        Label labelLastChunk = new Label();

        masm.movl(length, endOffset);
        masm.subl(length, startOffset);
        masm.push(dest);

        if (masm.supports(CPUFeature.AVX512_VBMI) && masm.supports(CPUFeature.AVX512BW)) {
            Register lookupLo = xmm5;
            Register lookupHi = xmm6;
            Register errorVec = xmm7;
            Register pack16Op = xmm9;
            Register pack32Op = xmm8;
            Register input0 = xmm3;
            Register input1 = xmm20;
            Register input2 = xmm21;
            Register input3 = xmm19;
            Register join01 = xmm12;
            Register join12 = xmm11;
            Register join23 = xmm10;
            Register translated0 = xmm2;
            Register translated1 = xmm1;
            Register translated2 = xmm0;
            Register translated3 = xmm4;

            Register merged0 = translated0;
            Register merged1 = translated1;
            Register merged2 = translated2;
            Register merged3 = translated3;

            Register pack24Bits = xmm4;
            Register outputSize = r13;
            Register outputMask = r15;
            Register inputMask = k1;

            Register inputInitialValidB64 = xmm0;
            Register tmp = xmm10;
            Register mask = xmm0;
            Register invalidB64 = xmm1;
            // 32-bytes is break-even for AVX-512
            masm.cmplAndJcc(length, 31, ConditionFlag.LessEqual, labelLastChunk, false);

            masm.cmplAndJcc(isMIME, 0, ConditionFlag.NotEqual, labelLastChunk, false);

            // Load lookup tables based on isURL
            masm.cmplAndJcc(isURL, 0, ConditionFlag.NotEqual, labelLoadURL, false);

            masm.evmovdqu64(lookupLo, recordExternalAddress(crb, DECODE_VBMI_LOOKUP_LO));
            masm.evmovdqu64(lookupHi, recordExternalAddress(crb, DECODE_VBMI_LOOKUP_HI));

            masm.bind(labelContinue);

            masm.movl(r15, 0x01400140);
            EVPBROADCASTD_GPR.emit(masm, ZMM, pack16Op, r15);

            masm.movl(r15, 0x00011000);
            EVPBROADCASTD_GPR.emit(masm, ZMM, pack32Op, r15);

            masm.cmplAndJcc(length, 0xff, ConditionFlag.LessEqual, labelProcess64, false);

            // load masks required for decoding data
            masm.bind(labelProcessData);
            masm.evmovdqu64(join01, recordExternalAddress(crb, DECODE_VBMI_JOIN_0_1));
            masm.evmovdqu64(join12, recordExternalAddress(crb, DECODE_VBMI_JOIN_1_2));
            masm.evmovdqu64(join23, recordExternalAddress(crb, DECODE_VBMI_JOIN_2_3));

            masm.align(32);
            masm.bind(labelProcess256);
            // Grab input data
            masm.evmovdqu64(input0, new AMD64Address(source, startOffset, Stride.S1, 0x00));
            masm.evmovdqu64(input1, new AMD64Address(source, startOffset, Stride.S1, 0x40));
            masm.evmovdqu64(input2, new AMD64Address(source, startOffset, Stride.S1, 0x80));
            masm.evmovdqu64(input3, new AMD64Address(source, startOffset, Stride.S1, 0xc0));

            // Copy the low part of the lookup table into the destination of the permutation
            EVMOVDQU64.emit(masm, ZMM, translated0, lookupLo);
            EVMOVDQU64.emit(masm, ZMM, translated1, lookupLo);
            EVMOVDQU64.emit(masm, ZMM, translated2, lookupLo);
            EVMOVDQU64.emit(masm, ZMM, translated3, lookupLo);

            // Translate the base64 input into "decoded" bytes
            EVPERMT2B.emit(masm, ZMM, translated0, input0, lookupHi);
            EVPERMT2B.emit(masm, ZMM, translated1, input1, lookupHi);
            EVPERMT2B.emit(masm, ZMM, translated2, input2, lookupHi);
            EVPERMT2B.emit(masm, ZMM, translated3, input3, lookupHi);

            // OR all of the translations together to check for errors (high-order bit of byte set)
            EVPTERNLOGD.emit(masm, ZMM, input0, input1, input2, 0xfe);

            // Check if there was an error - if so, try 64-byte chunks
            EVPTERNLOGD.emit(masm, ZMM, input3, translated0, translated1, 0xfe);
            EVPTERNLOGD.emit(masm, ZMM, input0, translated2, translated3, 0xfe);
            EVPORD.emit(masm, ZMM, errorVec, input3, input0);

            // The merging and shuffling happens here
            // We multiply each byte pair [00dddddd | 00cccccc | 00bbbbbb | 00aaaaaa]
            // Multiply [00cccccc] by 2^6 added to [00dddddd] to get [0000cccc | ccdddddd]
            // The pack16_op is a vector of 0x01400140, so multiply D by 1 and C by 0x40
            EVPMOVB2M.emit(masm, ZMM, k3, errorVec);
            masm.kortestq(k3, k3);
            masm.jcc(ConditionFlag.NotZero, labelProcess64);

            // Now do the same with packed 16-bit values.
            // We start with [0000cccc | ccdddddd | 0000aaaa | aabbbbbb]
            // pack32_op is 0x00011000 (2^12, 1), so this multiplies [0000aaaa | aabbbbbb] by 2^12
            // and adds [0000cccc | ccdddddd] to yield [00000000 | aaaaaabb | bbbbcccc | ccdddddd]
            EVPMADDUBSW.emit(masm, ZMM, merged0, translated0, pack16Op);
            EVPMADDUBSW.emit(masm, ZMM, merged1, translated1, pack16Op);
            EVPMADDUBSW.emit(masm, ZMM, merged2, translated2, pack16Op);
            EVPMADDUBSW.emit(masm, ZMM, merged3, translated3, pack16Op);

            // The join vectors specify which byte from which vector goes into the outputs
            // One of every 4 bytes in the extended vector is zero, so we pack them into their
            // final positions in the register for storing (256 bytes in, 192 bytes out)
            EVPMADDWD.emit(masm, ZMM, merged0, merged0, pack32Op);
            EVPMADDWD.emit(masm, ZMM, merged1, merged1, pack32Op);
            EVPMADDWD.emit(masm, ZMM, merged2, merged2, pack32Op);
            EVPMADDWD.emit(masm, ZMM, merged3, merged3, pack32Op);

            // Store result
            EVPERMT2B.emit(masm, ZMM, merged0, join01, merged1);
            EVPERMT2B.emit(masm, ZMM, merged1, join12, merged2);
            EVPERMT2B.emit(masm, ZMM, merged2, join23, merged3);

            masm.evmovdqu64(new AMD64Address(dest, dp, Stride.S1, 0x00), merged0);
            masm.evmovdqu64(new AMD64Address(dest, dp, Stride.S1, 0x40), merged1);
            masm.evmovdqu64(new AMD64Address(dest, dp, Stride.S1, 0x80), merged2);

            // At this point, we've decoded 64 * 4 * n bytes.
            // The remaining length will be <= 64 * 4 - 1.
            // UNLESS there was an error decoding the first 256-byte chunk. In this
            // case, the length will be arbitrarily long.
            //
            // Note that this will be the path for MIME-encoded strings.

            masm.addq(source, 0x100);
            masm.addq(dest, 0xc0);
            masm.subl(length, 0x100);
            masm.cmplAndJcc(length, 64 * 4, ConditionFlag.GreaterEqual, labelProcess256, false);

            masm.bind(labelProcess64);

            masm.evmovdqu64(pack24Bits, recordExternalAddress(crb, DECODE_VBMI_PACK_VEC));

            masm.cmplAndJcc(length, 63, ConditionFlag.LessEqual, labelFinalBit, false);

            masm.movq(rax, 0x0000ffffffffffffL);
            masm.kmovq(k2, rax);

            masm.align(32);
            masm.bind(labelProcess64Loop);

            // Handle first 64-byte block

            masm.evmovdqu64(input0, new AMD64Address(source, startOffset, Stride.S1));
            EVMOVDQU64.emit(masm, ZMM, translated0, lookupLo);
            EVPERMT2B.emit(masm, ZMM, translated0, input0, lookupHi);

            EVPORD.emit(masm, ZMM, errorVec, translated0, input0);

            // Check for error and bomb out before updating dest
            EVPMOVB2M.emit(masm, ZMM, k3, errorVec);
            masm.kortestq(k3, k3);
            masm.jcc(ConditionFlag.NotZero, labelExit);

            // Pack output register, selecting correct byte ordering
            EVPMADDUBSW.emit(masm, ZMM, merged0, translated0, pack16Op);
            EVPMADDWD.emit(masm, ZMM, merged0, merged0, pack32Op);
            EVPERMB.emit(masm, ZMM, merged0, pack24Bits, merged0);

            EVMOVDQU8.emit(masm, ZMM, new AMD64Address(dest, dp, Stride.S1), merged0, k2, Z0, B0);

            masm.subl(length, 64);
            masm.addq(source, 64);
            masm.addq(dest, 48);

            masm.cmplAndJcc(length, 64, ConditionFlag.GreaterEqual, labelProcess64Loop, false);

            masm.cmplAndJcc(length, 0, ConditionFlag.LessEqual, labelExit, false);

            masm.bind(labelFinalBit);
            // Now have 1 to 63 bytes left to decode
            // Leave an incomplete quantum to the Java code, which handles valid unpadded tails
            // and rejects malformed input after accounting for the prefix decoded by this stub.
            masm.cmplAndJcc(length, 4, ConditionFlag.Less, labelExit, false);

            // I was going to let Java take care of the final fragment
            // however it will repeatedly call this routine for every 4 bytes
            // of input data, so handle the rest here.
            masm.movq(rax, -1);
            BZHI.emit(masm, QWORD, rax, rax, length); // Input mask in rax

            masm.movl(outputSize, length);
            masm.shrl(outputSize, 2); // Find (len / 4) * 3 (output length)
            masm.leaq(outputSize, new AMD64Address(outputSize, outputSize, Stride.S2));
            // output_size in r13

            // Strip pad characters, if any, and adjust length and mask
            masm.addq(length, startOffset);
            masm.cmpb(new AMD64Address(source, length, Stride.S1, -1), 0x3d);
            masm.jcc(ConditionFlag.Equal, labelPadding);

            masm.bind(labelDonePadding);
            masm.subq(length, startOffset);

            // Output size is (64 - output_size), output mask is (all 1s >> output_size).
            masm.kmovq(inputMask, rax);
            masm.movq(outputMask, -1);
            BZHI.emit(masm, QWORD, outputMask, outputMask, outputSize);

            // Load initial input with all valid base64 characters. Will be used
            // in merging source bytes to avoid masking when determining if an error occurred.
            masm.movl(rax, 0x61616161);
            EVPBROADCASTD_GPR.emit(masm, ZMM, inputInitialValidB64, rax);

            // A register containing all invalid base64 decoded values
            masm.movl(rax, 0x80808080);
            EVPBROADCASTD_GPR.emit(masm, ZMM, invalidB64, rax);

            // input_mask is in k1
            // output_size is in r13
            // output_mask is in r15
            // zmm0 - free
            // zmm1 - 0x00011000
            // zmm2 - 0x01400140
            // zmm3 - errorvec
            // zmm4 - pack vector
            // zmm5 - lookup_lo
            // zmm6 - lookup_hi
            // zmm7 - errorvec
            // zmm8 - 0x61616161
            // zmm9 - 0x80808080

            // Load only the bytes from source, merging into our "fully-valid" register
            EVMOVDQU8.emit(masm, ZMM, inputInitialValidB64, new AMD64Address(source, startOffset, Stride.S1), inputMask, Z0, B0);

            // Decode all bytes within our merged input
            EVMOVDQU64.emit(masm, ZMM, tmp, lookupLo);
            EVPERMT2B.emit(masm, ZMM, tmp, inputInitialValidB64, lookupHi);
            EVPORQ.emit(masm, ZMM, mask, tmp, inputInitialValidB64);

            // Check for error. Compare (decoded | initial) to all invalid.
            // If any bytes have their high-order bit set, then we have an error.
            EVPTESTMB.emit(masm, ZMM, k2, mask, invalidB64);
            masm.kortestq(k2, k2);

            // If we have an error, use the brute force loop to decode what we can (4-byte chunks).
            masm.jcc(ConditionFlag.NotZero, labelBruteForce);

            // Shuffle output bytes
            EVPMADDUBSW.emit(masm, ZMM, tmp, tmp, pack16Op);
            EVPMADDWD.emit(masm, ZMM, tmp, tmp, pack32Op);

            EVPERMB.emit(masm, ZMM, tmp, pack24Bits, tmp);
            masm.kmovq(k1, outputMask);
            EVMOVDQU8.emit(masm, ZMM, new AMD64Address(dest, dp, Stride.S1), tmp, k1, Z0, B0);

            masm.addq(dest, outputSize);

            masm.bind(labelExit);
            masm.vzeroupper();
            masm.jmp(labelExitNoVZero);

            masm.bind(labelLoadURL);
            masm.evmovdqu64(lookupLo, recordExternalAddress(crb, DECODE_VBMI_LOOKUP_LO_URL));
            masm.evmovdqu64(lookupHi, recordExternalAddress(crb, DECODE_VBMI_LOOKUP_HI_URL));
            masm.jmp(labelContinue);

            masm.bind(labelPadding);
            masm.decq(outputSize);
            masm.shrq(rax, 1);

            masm.cmpb(new AMD64Address(source, length, Stride.S1, -2), 0x3d);
            masm.jcc(ConditionFlag.NotEqual, labelDonePadding);

            masm.decq(outputSize);
            masm.shrq(rax, 1);
            masm.jmp(labelDonePadding);

            masm.align(32);
            masm.bind(labelBruteForce);
        }

        if (masm.supports(CPUFeature.AVX2)) {
            Label labelTailProc = new Label();
            Label labelTopLoop = new Label();
            Label labelEnterLoop = new Label();

            masm.cmplAndJcc(isMIME, 0, ConditionFlag.NotEqual, labelLastChunk, false);

            // Check for buffer too small (for algorithm)
            masm.sublAndJcc(length, 0x2c, ConditionFlag.Less, labelTailProc, false);

            masm.shll(isURL, 2);

            // Algorithm adapted from https://arxiv.org/abs/1704.00605, "Faster Base64
            // Encoding and Decoding using AVX2 Instructions". URL modifications added.

            // Set up constants
            masm.leaq(r13, recordExternalAddress(crb, DECODE_AVX2_TABLES));
            masm.vpbroadcastd(xmm4, new AMD64Address(r13, isURL, Stride.S1), YMM); // 2F or 5F
            masm.vpbroadcastd(xmm10, new AMD64Address(r13, isURL, Stride.S1, 0x08), YMM);
            masm.vmovdqu(xmm12, new AMD64Address(r13, 0x10)); // permute
            masm.vmovdqu(xmm13, new AMD64Address(r13, 0x30)); // shuffle
            masm.vpbroadcastd(xmm7, new AMD64Address(r13, 0x50), YMM); // merge
            masm.vpbroadcastd(xmm6, new AMD64Address(r13, 0x54), YMM); // merge mult

            masm.leaq(r13, recordExternalAddress(crb, DECODE_AVX2_LUT_TABLES));
            masm.shll(isURL, 4);
            masm.vmovdqu(xmm11, new AMD64Address(r13, isURL, Stride.S1, 0x00)); // lut_lo
            masm.vmovdqu(xmm8, new AMD64Address(r13, isURL, Stride.S1, 0x20)); // lut_roll
            masm.shrl(isURL, 6); // restore isURL
            masm.vmovdqu(xmm9, new AMD64Address(r13, 0x80)); // lut_hi
            masm.jmp(labelEnterLoop);

            masm.align(32);
            masm.bind(labelTopLoop);

            // Add in the offset value (roll) to get 6-bit out values
            masm.vpaddb(xmm0, xmm0, xmm2, YMM);

            // Merge and permute the output bits into appropriate output byte lanes
            masm.vpmaddubsw(xmm0, xmm0, xmm7, YMM);
            masm.vpmaddwd(xmm0, xmm0, xmm6, YMM);
            masm.vpshufb(xmm0, xmm0, xmm13, YMM);
            masm.vpermd(xmm0, xmm12, xmm0, YMM);

            // Store the output bytes
            masm.vmovdqu(new AMD64Address(dest, dp, Stride.S1, 0), xmm0);
            masm.addq(source, 0x20);
            masm.addq(dest, 0x18);
            masm.sublAndJcc(length, 0x20, ConditionFlag.Less, labelTailProc, false);

            masm.bind(labelEnterLoop);

            // Load in encoded string (32 bytes)
            masm.vmovdqu(xmm2, new AMD64Address(source, startOffset, Stride.S1, 0x0));

            // Extract the high nibble for indexing into the lut tables. High 4 bits are don't care.
            masm.vpsrld(xmm1, xmm2, 0x4, YMM);
            masm.vpand(xmm1, xmm4, xmm1, YMM);
            // Extract the low nibble. 5F/2F will isolate the low-order 4 bits. High 4 bits are
            // don't care.
            masm.vpand(xmm3, xmm2, xmm4, YMM);
            // Check for special-case (0x2F or 0x5F (URL))
            masm.vpcmpeqb(xmm0, xmm4, xmm2);
            // Get the bitset based on the low nibble. vpshufb uses low-order 4 bits only.
            masm.vpshufb(xmm3, xmm11, xmm3, YMM);
            // Get the bit value of the high nibble
            masm.vpshufb(xmm5, xmm9, xmm1, YMM);
            // Make sure 2F / 5F shows as valid
            masm.vpandn(xmm3, xmm0, xmm3);
            // Make adjustment for roll index. For non-URL, this is a no-op,
            // for URL, this adjusts by -4. This is to properly index the
            // roll value for 2F / 5F.
            masm.vpand(xmm0, xmm0, xmm10, YMM);
            // If the and of the two is non-zero, we have an invalid input character
            masm.vptest(xmm3, xmm5, YMM);
            // Extract the "roll" value - value to add to the input to get 6-bit out value
            masm.vpaddb(xmm0, xmm0, xmm1, YMM); // Handle 2F / 5F
            masm.vpshufb(xmm0, xmm8, xmm0, YMM);
            masm.jcc(ConditionFlag.Equal, labelTopLoop); // Fall through on error

            masm.bind(labelTailProc);

            masm.addl(length, 0x2c);
            masm.vzeroupper();
        }

        // Use non-AVX code to decode 4-byte chunks into 3 bytes of output

        // Register state (Linux):
        // r12-15 - saved on stack
        // rdi - src
        // rsi - sp
        // rdx - sl
        // rcx - dst
        // r8 - dp
        // r9 - isURL

        // Register state (Windows):
        // r12-15 - saved on stack
        // rcx - src
        // rdx - sp
        // r8 - sl
        // r9 - dst
        // r12 - dp
        // r10 - isURL

        // Registers (common):
        // length (r14) - bytes in src
        Register decodeTable = r11;
        Register byte1 = r13;
        Register byte2 = r15;
        Register byte3 = rdx;
        Register byte4 = r9;

        masm.bind(labelLastChunk);

        masm.shrl(length, 2); // Multiple of 4 bytes only - length is # 4-byte chunks
        masm.cmplAndJcc(length, 0, ConditionFlag.LessEqual, labelExitNoVZero, false);

        masm.shll(isURL, 8); // index into decode table based on isURL
        masm.leaq(decodeTable, recordExternalAddress(crb, DECODE_TABLE));
        masm.addq(decodeTable, isURL);

        masm.jmp(labelBottomLoop);

        masm.align(32);
        masm.bind(labelForceLoop);
        masm.shll(byte1, 18);
        masm.shll(byte2, 12);
        masm.shll(byte3, 6);
        masm.orl(byte1, byte2);
        masm.orl(byte1, byte3);
        masm.orl(byte1, byte4);

        masm.addq(source, 4);

        masm.movb(new AMD64Address(dest, dp, Stride.S1, 2), byte1);
        masm.shrl(byte1, 8);
        masm.movb(new AMD64Address(dest, dp, Stride.S1, 1), byte1);
        masm.shrl(byte1, 8);
        masm.movb(new AMD64Address(dest, dp, Stride.S1, 0), byte1);

        masm.addq(dest, 3);
        masm.declAndJcc(length, ConditionFlag.Zero, labelExitNoVZero, false);

        masm.bind(labelBottomLoop);
        masm.movzbl(byte1, new AMD64Address(source, startOffset, Stride.S1, 0x00));
        masm.movzbl(byte2, new AMD64Address(source, startOffset, Stride.S1, 0x01));
        masm.movsbl(byte1, new AMD64Address(decodeTable, byte1, Stride.S1, 0x00));
        masm.movsbl(byte2, new AMD64Address(decodeTable, byte2, Stride.S1, 0x00));
        masm.movzbl(byte3, new AMD64Address(source, startOffset, Stride.S1, 0x02));
        masm.movzbl(byte4, new AMD64Address(source, startOffset, Stride.S1, 0x03));
        masm.movsbl(byte3, new AMD64Address(decodeTable, byte3, Stride.S1, 0x00));
        masm.movsbl(byte4, new AMD64Address(decodeTable, byte4, Stride.S1, 0x00));

        masm.movq(rax, byte1);
        masm.orl(rax, byte2);
        masm.orl(rax, byte3);
        masm.orl(rax, byte4);
        masm.jcc(ConditionFlag.Positive, labelForceLoop);

        masm.bind(labelExitNoVZero);

        masm.pop(rax); // Get original dest value
        masm.subq(dest, rax); // Number of bytes converted
        masm.movq(rax, dest);

        masm.pop(r15);
        masm.pop(r14);
        masm.pop(r13);
        masm.pop(r12);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
