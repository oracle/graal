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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding.EVEX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding.VEX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexAESOp.EVAESENC;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexAESOp.EVAESENCLAST;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMemoryOp.EVBROADCASTF64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.EVPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTI64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPCLMULQDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVSHUFI64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSHUFB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPEQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftImmOp.EVPSLLDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftImmOp.EVPSRLDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSLLQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRLQ;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.loadKey;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AES;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512DQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_VAES;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_VPCLMULQDQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLMUL;
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
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm16;
import static jdk.vm.ci.amd64.AMD64.xmm17;
import static jdk.vm.ci.amd64.AMD64.xmm18;
import static jdk.vm.ci.amd64.AMD64.xmm19;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm20;
import static jdk.vm.ci.amd64.AMD64.xmm21;
import static jdk.vm.ci.amd64.AMD64.xmm22;
import static jdk.vm.ci.amd64.AMD64.xmm23;
import static jdk.vm.ci.amd64.AMD64.xmm24;
import static jdk.vm.ci.amd64.AMD64.xmm25;
import static jdk.vm.ci.amd64.AMD64.xmm26;
import static jdk.vm.ci.amd64.AMD64.xmm27;
import static jdk.vm.ci.amd64.AMD64.xmm28;
import static jdk.vm.ci.amd64.AMD64.xmm29;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm30;
import static jdk.vm.ci.amd64.AMD64.xmm31;
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
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/x86/stubGenerator_x86_64_aes.cpp#L337-L4095",
          sha1 = "cce426e6025e6b8843b3eba8110f561967148640")
// @formatter:on
@SuppressWarnings({"unused", "static-method"}) // Preserve HotSpot register aliases and helper structure for stub parity.
public final class AMD64GaloisCounterModeAESCryptOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64GaloisCounterModeAESCryptOp> TYPE = LIRInstructionClass.create(AMD64GaloisCounterModeAESCryptOp.class);

    private static final int AES_BLOCK_SIZE = 16;

    private final int lengthOffset;
    private final boolean useAVX512;

    @Use({OperandFlag.REG}) private Value inValue;
    @Use({OperandFlag.REG}) private Value lenValue;
    @Use({OperandFlag.REG}) private Value ctValue;
    @Use({OperandFlag.REG}) private Value outValue;
    @Use({OperandFlag.REG}) private Value keyValue;
    @Use({OperandFlag.REG}) private Value stateValue;
    @Use({OperandFlag.REG}) private Value subkeyHtblValue;
    @Use({OperandFlag.REG}) private Value counterValue;

    @Def({OperandFlag.REG}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    private static final ArrayDataPointerConstant KEY_SHUFFLE_MASK = pointerConstant(16, new long[]{
                    0x0405060700010203L, 0x0C0D0E0F08090A0BL,
    });

    private static final ArrayDataPointerConstant COUNTER_SHUFFLE_MASK = pointerConstant(16, new long[]{
                    0x08090A0B0C0D0E0FL, 0x0001020304050607L,
                    0x08090A0B0C0D0E0FL, 0x0001020304050607L,
                    0x08090A0B0C0D0E0FL, 0x0001020304050607L,
                    0x08090A0B0C0D0E0FL, 0x0001020304050607L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_LINC1 = pointerConstant(16, new long[]{
                    0x0000000000000001L, 0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_LINC1F = pointerConstant(16, new long[]{
                    0x0000000000000000L, 0x0100000000000000L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_LINC2 = pointerConstant(16, new long[]{
                    0x0000000000000002L, 0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_LINC2F = pointerConstant(16, new long[]{
                    0x0000000000000000L, 0x0200000000000000L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_LINC4 = pointerConstant(16, new long[]{
                    0x0000000000000004L, 0x0000000000000000L,
                    0x0000000000000004L, 0x0000000000000000L,
                    0x0000000000000004L, 0x0000000000000000L,
                    0x0000000000000004L, 0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant GHASH_LONG_SWAP_MASK = pointerConstant(16, new long[]{
                    0x0F0E0D0C0B0A0908L, 0x0706050403020100L,
    });

    private static final ArrayDataPointerConstant GHASH_POLYNOMIAL = pointerConstant(16, new long[]{
                    0x0000000000000001L, 0xC200000000000000L,
                    0x0000000000000001L, 0xC200000000000000L,
                    0x0000000000000001L, 0xC200000000000000L,
                    0x0000000000000001L, 0xC200000000000000L,
    });

    private static final ArrayDataPointerConstant GHASH_POLYNOMIAL_REDUCTION = pointerConstant(16, new long[]{
                    0x00000001C2000000L, 0xC200000000000000L,
                    0x00000001C2000000L, 0xC200000000000000L,
                    0x00000001C2000000L, 0xC200000000000000L,
                    0x00000001C2000000L, 0xC200000000000000L,
    });

    private static final ArrayDataPointerConstant GHASH_POLYNOMIAL_TWO_ONE = pointerConstant(16, new long[]{
                    0x0000000000000001L, 0x0000000100000000L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_ADDBE_4444 = pointerConstant(16, new long[]{
                    0x0000000000000000L, 0x0400000000000000L,
                    0x0000000000000000L, 0x0400000000000000L,
                    0x0000000000000000L, 0x0400000000000000L,
                    0x0000000000000000L, 0x0400000000000000L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_ADDBE_1234 = pointerConstant(16, new long[]{
                    0x0000000000000000L, 0x0100000000000000L,
                    0x0000000000000000L, 0x0200000000000000L,
                    0x0000000000000000L, 0x0300000000000000L,
                    0x0000000000000000L, 0x0400000000000000L,
    });

    private static final ArrayDataPointerConstant COUNTER_MASK_ADD_1234 = pointerConstant(16, new long[]{
                    0x0000000000000001L, 0x0000000000000000L,
                    0x0000000000000002L, 0x0000000000000000L,
                    0x0000000000000003L, 0x0000000000000000L,
                    0x0000000000000004L, 0x0000000000000000L,
    });

    public AMD64GaloisCounterModeAESCryptOp(LIRGeneratorTool tool,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    AllocatableValue inValue,
                    AllocatableValue lenValue,
                    AllocatableValue ctValue,
                    AllocatableValue outValue,
                    AllocatableValue keyValue,
                    AllocatableValue stateValue,
                    AllocatableValue subkeyHtblValue,
                    AllocatableValue counterValue,
                    AllocatableValue resultValue,
                    int lengthOffset) {
        super(TYPE);
        this.inValue = inValue;
        this.lenValue = lenValue;
        this.ctValue = ctValue;
        this.outValue = outValue;
        this.keyValue = keyValue;
        this.stateValue = stateValue;
        this.subkeyHtblValue = subkeyHtblValue;
        this.counterValue = counterValue;
        this.resultValue = resultValue;
        this.lengthOffset = lengthOffset;
        this.useAVX512 = supports(tool.target(), runtimeCheckedCPUFeatures, AES, CLMUL, AVX, AVX2, AVX512F, AVX512DQ, AVX512BW, AVX512VL, AVX512_VAES,
                        AVX512_VPCLMULQDQ);

        GraalError.guarantee(inValue instanceof RegisterValue inValueReg && rdi.equals(inValueReg.getRegister()), "inValue should be fixed to rdi, but is %s", inValue);
        GraalError.guarantee(lenValue instanceof RegisterValue lenValueReg && rsi.equals(lenValueReg.getRegister()), "lenValue should be fixed to rsi, but is %s", lenValue);
        GraalError.guarantee(ctValue instanceof RegisterValue ctValueReg && rdx.equals(ctValueReg.getRegister()), "ctValue should be fixed to rdx, but is %s", ctValue);
        GraalError.guarantee(outValue instanceof RegisterValue outValueReg && rcx.equals(outValueReg.getRegister()), "outValue should be fixed to rcx, but is %s", outValue);
        GraalError.guarantee(keyValue instanceof RegisterValue keyValueReg && r8.equals(keyValueReg.getRegister()), "keyValue should be fixed to r8, but is %s", keyValue);
        GraalError.guarantee(stateValue instanceof RegisterValue stateValueReg && r9.equals(stateValueReg.getRegister()), "stateValue should be fixed to r9, but is %s", stateValue);
        Register subkeyHtblRegister = useAVX512 ? r10 : r11;
        Register counterRegister = useAVX512 ? r11 : rax;
        GraalError.guarantee(subkeyHtblValue instanceof RegisterValue subkeyHtblValueReg && subkeyHtblRegister.equals(subkeyHtblValueReg.getRegister()),
                        "subkeyHtblValue should be fixed to %s, but is %s", subkeyHtblRegister, subkeyHtblValue);
        GraalError.guarantee(counterValue instanceof RegisterValue counterValueReg && counterRegister.equals(counterValueReg.getRegister()), "counterValue should be fixed to %s, but is %s",
                        counterRegister, counterValue);
        GraalError.guarantee(resultValue instanceof RegisterValue resultValueReg && rax.equals(resultValueReg.getRegister()), "resultValue should be fixed to rax, but is %s", resultValue);
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, AES, CLMUL, AVX, AVX2), "GCM AES requires AES, CLMUL, AVX, and AVX2 support");

        if (useAVX512) {
            temps = registersToValues(new Register[]{
                            // rsi stores lenValue as an incoming @Use and is also listed in @Temp
                            // because both AES-GCM paths mutate len in place.
                            rsi,
                            rbx,
                            r14,
                            r15,
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
                            xmm16,
                            xmm17,
                            xmm18,
                            xmm19,
                            xmm20,
                            xmm21,
                            xmm22,
                            xmm23,
                            xmm24,
                            xmm25,
                            xmm26,
                            xmm27,
                            xmm28,
                            xmm29,
                            xmm30,
                            xmm31,
            });
        } else {
            temps = registersToValues(new Register[]{
                            // rsi stores lenValue as an incoming @Use and is also listed in @Temp
                            // because both AES-GCM paths mutate len in place.
                            rsi,
                            r10,
                            r14,
                            r15,
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
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Assembler.AMD64SIMDInstructionEncoding oldEncoding = masm.setTemporaryAvxEncoding(useAVX512 ? EVEX : VEX);

        GraalError.guarantee(inValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid inValue kind: %s", inValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(ctValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid ctValue kind: %s", ctValue);
        GraalError.guarantee(outValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid outValue kind: %s", outValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);
        GraalError.guarantee(stateValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);
        GraalError.guarantee(subkeyHtblValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid subkeyHtblValue kind: %s", subkeyHtblValue);
        GraalError.guarantee(counterValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid counterValue kind: %s", counterValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        Register in = asRegister(inValue);
        Register len = asRegister(lenValue);
        Register ct = asRegister(ctValue);
        Register out = asRegister(outValue);
        Register key = asRegister(keyValue);
        Register state = asRegister(stateValue);
        Register subkeyHtbl = asRegister(subkeyHtblValue);
        Register counterValueRegister = asRegister(counterValue);

        masm.push(r12);
        if (!useAVX512) {
            masm.push(r13);
        }
        masm.push(r14);
        masm.push(r15);
        if (useAVX512) {
            Register avx512SubkeyHtbl = r12;
            Register counter = counterValueRegister;

            masm.push(rbx);
            // r10 matches HotSpot's subkeyHtbl register, so keep the pre-alignment stack pointer in rbx.
            masm.movq(rbx, rsp);
            masm.andq(rsp, -64);
            masm.subq(rsp, 200 * 8);
            masm.movq(avx512SubkeyHtbl, rsp);

            aesgcmAvx512(crb, masm, in, len, ct, out, key, state, subkeyHtbl, avx512SubkeyHtbl, counter);

            masm.vzeroupper();
            masm.movq(rsp, rbx);
            masm.pop(rbx);
        } else {
            Register counter = r12;

            masm.push(rbx);
            masm.movq(counter, counterValueRegister);
            masm.movq(r14, rsp);
            masm.andq(rsp, -64);
            masm.subq(rsp, 16 * 8);

            aesgcmAvx2(crb, masm, in, len, ct, out, key, state, subkeyHtbl, counter);

            masm.vzeroupper();
            masm.movq(rsp, r14);
            masm.pop(rbx);
        }

        masm.pop(r15);
        masm.pop(r14);
        if (!useAVX512) {
            masm.pop(r13);
        }
        masm.pop(r12);

        masm.resetAvxEncoding(oldEncoding);
    }

    private static void gfmulAvx2(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register gh, Register hk) {
        Register t1 = xmm1;
        Register t2 = xmm2;
        Register t3 = xmm3;

        // %%T1 = a1*b1
        masm.vpclmulqdq(t1, gh, hk, 0x11);
        // %%T2 = a0*b0
        masm.vpclmulqdq(t2, gh, hk, 0x00);
        // %%T3 = a1*b0
        masm.vpclmulqdq(t3, gh, hk, 0x01);
        // %%GH = a0*b1
        masm.vpclmulqdq(gh, gh, hk, 0x10);
        masm.vpxor(gh, gh, t3, XMM);

        // shift-R %%GH 2 DWs
        masm.vpsrldq(t3, gh, 8, XMM);
        // shift-L %%GH 2 DWs
        masm.vpslldq(gh, gh, 8, XMM);

        masm.vpxor(t1, t1, t3, XMM);
        masm.vpxor(gh, gh, t2, XMM);

        // first phase of the reduction
        masm.movdqu(t3, recordExternalAddress(crb, GHASH_POLYNOMIAL_REDUCTION));
        masm.vpclmulqdq(t2, t3, gh, 0x01);
        // shift-L %%T2 2 DWs
        masm.vpslldq(t2, t2, 8, XMM);

        // first phase of the reduction complete
        masm.vpxor(gh, gh, t2, XMM);
        // second phase of the reduction
        masm.vpclmulqdq(t2, t3, gh, 0x00);
        // shift-R %%T2 1 DW (Shift-R only 1-DW to obtain 2-DWs shift-R)
        masm.vpsrldq(t2, t2, 4, XMM);

        masm.vpclmulqdq(gh, t3, gh, 0x10);
        // shift-L %%GH 1 DW (Shift-L 1-DW to obtain result with no shifts)
        masm.vpslldq(gh, gh, 4, XMM);

        // second phase of the reduction complete
        masm.vpxor(gh, gh, t2, XMM);
        // the result is in %%GH
        masm.vpxor(gh, gh, t1, XMM);
    }

    // Generate 8 constants from the given subkeyH.
    private void generateHtbl8BlockAvx2(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register htbl) {
        Register hk = xmm6;

        masm.movdqu(hk, new AMD64Address(htbl, 0));
        masm.movdqu(xmm1, recordExternalAddress(crb, GHASH_LONG_SWAP_MASK));
        masm.vpshufb(hk, hk, xmm1, XMM);

        masm.movdqu(xmm11, recordExternalAddress(crb, GHASH_POLYNOMIAL));
        masm.movdqu(xmm12, recordExternalAddress(crb, GHASH_POLYNOMIAL_TWO_ONE));
        // Compute H ^ 2 from the input subkeyH
        masm.vpsrlq(xmm1, xmm6, 63, XMM);
        masm.vpsllq(xmm6, xmm6, 1, XMM);
        masm.vpslldq(xmm2, xmm1, 8, XMM);
        masm.vpsrldq(xmm1, xmm1, 8, XMM);

        masm.vpor(xmm6, xmm6, xmm2, XMM);

        masm.vpshufd(xmm2, xmm1, 0x24, XMM);
        VPCMPEQD.emit(masm, XMM, xmm2, xmm2, xmm12);
        masm.vpand(xmm2, xmm2, xmm11, XMM);
        masm.vpxor(xmm6, xmm6, xmm2, XMM);
        // H * 2
        masm.movdqu(new AMD64Address(htbl, 1 * 16), xmm6);
        masm.movdqu(xmm0, xmm6);
        for (int i = 2; i < 9; i++) {
            gfmulAvx2(crb, masm, xmm6, xmm0);
            masm.movdqu(new AMD64Address(htbl, i * 16), xmm6);
        }
    }

    private static void aesencStepAvx2(AMD64MacroAssembler masm, Register tKey) {
        masm.aesenc(xmm1, tKey);
        masm.aesenc(xmm2, tKey);
        masm.aesenc(xmm3, tKey);
        masm.aesenc(xmm4, tKey);
        masm.aesenc(xmm5, tKey);
        masm.aesenc(xmm6, tKey);
        masm.aesenc(xmm7, tKey);
        masm.aesenc(xmm8, tKey);
    }

    private static void ghashStepAvx2(AMD64MacroAssembler masm, Register ghdata, Register hkey) {
        masm.vpclmulqdq(xmm11, ghdata, hkey, 0x11);
        masm.vpxor(xmm12, xmm12, xmm11, XMM);
        masm.vpclmulqdq(xmm11, ghdata, hkey, 0x00);
        masm.vpxor(xmm15, xmm15, xmm11, XMM);
        masm.vpclmulqdq(xmm11, ghdata, hkey, 0x01);
        masm.vpxor(xmm14, xmm14, xmm11, XMM);
        masm.vpclmulqdq(xmm11, ghdata, hkey, 0x10);
        masm.vpxor(xmm14, xmm14, xmm11, XMM);
    }

    private void ghash8Encrypt8ParallelAvx2(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register key, Register subkeyHtbl, Register ctrBlockx, Register in,
                    Register out, Register ct, Register pos, boolean inOrder, Register rounds,
                    Register xmm1Reg, Register xmm2Reg, Register xmm3Reg, Register xmm4Reg,
                    Register xmm5Reg, Register xmm6Reg, Register xmm7Reg, Register xmm8Reg) {
        Register t1 = xmm0;
        Register t2 = xmm10;
        Register t3 = xmm11;
        Register t4 = xmm12;
        Register t5 = xmm13;
        Register t6 = xmm14;
        Register t7 = xmm15;
        Label labelSkipReload = new Label();
        Label labelLastAesRnd = new Label();
        Label labelAes192 = new Label();
        Label labelAes256 = new Label();

        masm.movdqu(t2, xmm1Reg);
        for (int i = 0; i <= 6; i++) {
            masm.movdqu(new AMD64Address(rsp, 16 * i), asXMMRegister(i + 2));
        }

        if (inOrder) {
            // Increment counter by 1
            masm.vpaddd(xmm1Reg, ctrBlockx, recordExternalAddress(crb, COUNTER_MASK_LINC1), XMM);
            masm.movdqu(t5, recordExternalAddress(crb, COUNTER_MASK_LINC2));
            masm.vpaddd(xmm2Reg, ctrBlockx, t5, XMM);
            for (int rnum = 1; rnum <= 6; rnum++) {
                masm.vpaddd(asXMMRegister(rnum + 2), asXMMRegister(rnum), t5, XMM);
            }
            masm.movdqu(ctrBlockx, xmm8Reg);

            masm.movdqu(t5, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK));
            for (int rnum = 1; rnum <= 8; rnum++) {
                // perform a 16Byte swap
                masm.vpshufb(asXMMRegister(rnum), asXMMRegister(rnum), t5, XMM);
            }
        } else {
            // Increment counter by 1
            masm.vpaddd(xmm1Reg, ctrBlockx, recordExternalAddress(crb, COUNTER_MASK_LINC1F), XMM);
            masm.movdqu(t5, recordExternalAddress(crb, COUNTER_MASK_LINC2F));
            masm.vpaddd(xmm2Reg, ctrBlockx, t5, XMM);
            for (int rnum = 1; rnum <= 6; rnum++) {
                masm.vpaddd(asXMMRegister(rnum + 2), asXMMRegister(rnum), t5, XMM);
            }
            masm.movdqu(ctrBlockx, xmm8Reg);
        }

        loadKey(masm, t1, key, 16 * 0, crb);
        for (int rnum = 1; rnum <= 8; rnum++) {
            masm.vpxor(asXMMRegister(rnum), asXMMRegister(rnum), t1, XMM);
        }

        loadKey(masm, t1, key, 16 * 1, crb);
        aesencStepAvx2(masm, t1);

        loadKey(masm, t1, key, 16 * 2, crb);
        aesencStepAvx2(masm, t1);

        masm.movdqu(t5, new AMD64Address(subkeyHtbl, 8 * 16));
        // t4 = a1*b1
        masm.vpclmulqdq(t4, t2, t5, 0x11);
        // t7 = a0*b0
        masm.vpclmulqdq(t7, t2, t5, 0x00);
        // t6 = a1*b0
        masm.vpclmulqdq(t6, t2, t5, 0x01);
        // t5 = a0*b1
        masm.vpclmulqdq(t5, t2, t5, 0x10);
        masm.vpxor(t6, t6, t5, XMM);

        for (int i = 3, j = 0; i <= 8; i++, j++) {
            loadKey(masm, t1, key, 16 * i, crb);
            aesencStepAvx2(masm, t1);
            masm.movdqu(t1, new AMD64Address(rsp, 16 * j));
            masm.movdqu(t5, new AMD64Address(subkeyHtbl, (7 - j) * 16));
            ghashStepAvx2(masm, t1, t5);
        }

        loadKey(masm, t1, key, 16 * 9, crb);
        aesencStepAvx2(masm, t1);

        masm.movdqu(t1, new AMD64Address(rsp, 16 * 6));
        masm.movdqu(t5, new AMD64Address(subkeyHtbl, 1 * 16));

        masm.vpclmulqdq(t3, t1, t5, 0x00);
        masm.vpxor(t7, t7, t3, XMM);

        masm.vpclmulqdq(t3, t1, t5, 0x01);
        masm.vpxor(t6, t6, t3, XMM);

        masm.vpclmulqdq(t3, t1, t5, 0x10);
        masm.vpxor(t6, t6, t3, XMM);

        masm.vpclmulqdq(t3, t1, t5, 0x11);
        masm.vpxor(t1, t4, t3, XMM);

        // shift-L t3 2 DWs
        masm.vpslldq(t3, t6, 8, XMM);
        // shift-R t2 2 DWs
        masm.vpsrldq(t6, t6, 8, XMM);
        masm.vpxor(t7, t7, t3, XMM);
        // accumulate the results in t1:t7
        masm.vpxor(t1, t1, t6, XMM);

        loadKey(masm, t5, key, 16 * 10, crb);
        masm.cmplAndJcc(rounds, 52, ConditionFlag.Less, labelLastAesRnd, false);

        masm.bind(labelAes192);
        aesencStepAvx2(masm, t5);
        loadKey(masm, t5, key, 16 * 11, crb);
        aesencStepAvx2(masm, t5);
        loadKey(masm, t5, key, 16 * 12, crb);
        masm.cmplAndJcc(rounds, 60, ConditionFlag.Less, labelLastAesRnd, false);

        masm.bind(labelAes256);
        aesencStepAvx2(masm, t5);
        loadKey(masm, t5, key, 16 * 13, crb);
        aesencStepAvx2(masm, t5);
        loadKey(masm, t5, key, 16 * 14, crb);
        masm.bind(labelLastAesRnd);
        for (int rnum = 1; rnum <= 8; rnum++) {
            masm.aesenclast(asXMMRegister(rnum), t5);
        }

        for (int i = 0; i <= 7; i++) {
            masm.movdqu(t2, new AMD64Address(in, pos, Stride.S1, 16 * i));
            masm.vpxor(asXMMRegister(i + 1), asXMMRegister(i + 1), t2, XMM);
        }

        // first phase of the reduction
        masm.movdqu(t3, recordExternalAddress(crb, GHASH_POLYNOMIAL_REDUCTION));

        masm.vpclmulqdq(t2, t3, t7, 0x01);
        // shift-L xmm2 2 DWs
        masm.vpslldq(t2, t2, 8, XMM);

        // first phase of the reduction complete
        masm.vpxor(t7, t7, t2, XMM);

        // Write to the Ciphertext buffer
        for (int i = 0; i <= 7; i++) {
            masm.movdqu(new AMD64Address(out, pos, Stride.S1, 16 * i), asXMMRegister(i + 1));
        }

        masm.cmpptr(ct, out);
        masm.jcc(ConditionFlag.Equal, labelSkipReload);
        for (int i = 0; i <= 7; i++) {
            masm.movdqu(asXMMRegister(i + 1), new AMD64Address(in, pos, Stride.S1, 16 * i));
        }

        masm.bind(labelSkipReload);
        // second phase of the reduction
        masm.vpclmulqdq(t2, t3, t7, 0x00);
        // shift-R t2 1 DW (Shift-R only 1-DW to obtain 2-DWs shift-R)
        masm.vpsrldq(t2, t2, 4, XMM);

        masm.vpclmulqdq(t4, t3, t7, 0x10);
        // shift-L t4 1 DW (Shift-L 1-DW to obtain result with no shifts)
        masm.vpslldq(t4, t4, 4, XMM);
        // second phase of the reduction complete
        masm.vpxor(t4, t4, t2, XMM);
        // the result is in t1
        masm.vpxor(t1, t1, t4, XMM);

        // perform a 16Byte swap
        masm.movdqu(t7, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK));
        for (int rnum = 1; rnum <= 8; rnum++) {
            masm.vpshufb(asXMMRegister(rnum), asXMMRegister(rnum), t7, XMM);
        }
        masm.vpxor(xmm1Reg, xmm1Reg, t1, XMM);
    }

    private static void ghashLast8Avx2(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register subkeyHtbl) {
        Register t1 = xmm0;
        Register t2 = xmm10;
        Register t3 = xmm11;
        Register t4 = xmm12;
        Register t5 = xmm13;
        Register t6 = xmm14;
        Register t7 = xmm15;

        // Karatsuba Method
        masm.movdqu(t5, new AMD64Address(subkeyHtbl, 8 * 16));

        masm.vpshufd(t2, xmm1, 78, XMM);
        masm.vpshufd(t3, t5, 78, XMM);
        masm.vpxor(t2, t2, xmm1, XMM);
        masm.vpxor(t3, t3, t5, XMM);

        masm.vpclmulqdq(t6, xmm1, t5, 0x11);
        masm.vpclmulqdq(t7, xmm1, t5, 0x00);

        masm.vpclmulqdq(xmm1, t2, t3, 0x00);

        for (int i = 7, rnum = 2; rnum <= 8; i--, rnum++) {
            masm.movdqu(t5, new AMD64Address(subkeyHtbl, i * 16));
            masm.vpshufd(t2, asXMMRegister(rnum), 78, XMM);
            masm.vpshufd(t3, t5, 78, XMM);
            masm.vpxor(t2, t2, asXMMRegister(rnum), XMM);
            masm.vpxor(t3, t3, t5, XMM);
            masm.vpclmulqdq(t4, asXMMRegister(rnum), t5, 0x11);
            masm.vpxor(t6, t6, t4, XMM);
            masm.vpclmulqdq(t4, asXMMRegister(rnum), t5, 0x00);
            masm.vpxor(t7, t7, t4, XMM);
            masm.vpclmulqdq(t2, t2, t3, 0x00);
            masm.vpxor(xmm1, xmm1, t2, XMM);
        }

        masm.vpxor(xmm1, xmm1, t6, XMM);
        masm.vpxor(t2, xmm1, t7, XMM);

        masm.vpslldq(t4, t2, 8, XMM);
        masm.vpsrldq(t2, t2, 8, XMM);

        masm.vpxor(t7, t7, t4, XMM);
        // <t6:t7> holds the result of the accumulated carry-less multiplications
        masm.vpxor(t6, t6, t2, XMM);

        // first phase of the reduction
        masm.movdqu(t3, recordExternalAddress(crb, GHASH_POLYNOMIAL_REDUCTION));

        masm.vpclmulqdq(t2, t3, t7, 0x01);
        // shift-L t2 2 DWs
        masm.vpslldq(t2, t2, 8, XMM);

        // first phase of the reduction complete
        masm.vpxor(t7, t7, t2, XMM);

        // second phase of the reduction
        masm.vpclmulqdq(t2, t3, t7, 0x00);
        // shift-R t2 1 DW (Shift-R only 1-DW to obtain 2-DWs shift-R)
        masm.vpsrldq(t2, t2, 4, XMM);

        masm.vpclmulqdq(t4, t3, t7, 0x10);
        // shift-L t4 1 DW (Shift-L 1-DW to obtain result with no shifts)
        masm.vpslldq(t4, t4, 4, XMM);
        // second phase of the reduction complete
        masm.vpxor(t4, t4, t2, XMM);
        // the result is in t6
        masm.vpxor(t6, t6, t4, XMM);
    }

    private static void initialBlocksAvx2(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register ctr, Register rounds, Register key, Register len, Register in,
                    Register out, Register ct, Register aadHashx, Register pos) {
        Register t1 = xmm12;
        Register t3 = xmm14;
        Register t4 = xmm15;
        Register t5 = xmm11;
        Register t6 = xmm10;
        Register tKey = xmm0;

        Label labelSkipReload = new Label();
        Label labelLastAesRnd = new Label();
        Label labelAes192 = new Label();
        Label labelAes256 = new Label();
        // Move AAD_HASH to temp reg t3
        masm.movdqu(t3, aadHashx);
        // Prepare 8 counter blocks and perform rounds of AES cipher on
        // them, load plain/cipher text and store cipher/plain text.
        masm.movdqu(xmm1, ctr);
        masm.movdqu(t5, recordExternalAddress(crb, COUNTER_MASK_LINC1));
        masm.movdqu(t6, recordExternalAddress(crb, COUNTER_MASK_LINC2));
        masm.vpaddd(xmm2, xmm1, t5, XMM);
        for (int rnum = 1; rnum <= 6; rnum++) {
            masm.vpaddd(asXMMRegister(rnum + 2), asXMMRegister(rnum), t6, XMM);
        }
        masm.movdqu(ctr, xmm8);

        masm.movdqu(t5, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK));
        for (int rnum = 1; rnum <= 8; rnum++) {
            // perform a 16Byte swap
            masm.vpshufb(asXMMRegister(rnum), asXMMRegister(rnum), t5, XMM);
        }

        loadKey(masm, tKey, key, 16 * 0, crb);
        for (int rnum = 1; rnum <= 8; rnum++) {
            masm.vpxor(asXMMRegister(rnum), asXMMRegister(rnum), tKey, XMM);
        }

        for (int i = 1; i <= 9; i++) {
            loadKey(masm, tKey, key, 16 * i, crb);
            aesencStepAvx2(masm, tKey);
        }

        loadKey(masm, tKey, key, 16 * 10, crb);
        masm.cmplAndJcc(rounds, 52, ConditionFlag.Less, labelLastAesRnd, false);

        masm.bind(labelAes192);
        aesencStepAvx2(masm, tKey);
        loadKey(masm, tKey, key, 16 * 11, crb);
        aesencStepAvx2(masm, tKey);
        loadKey(masm, tKey, key, 16 * 12, crb);
        masm.cmplAndJcc(rounds, 60, ConditionFlag.Less, labelLastAesRnd, false);

        masm.bind(labelAes256);
        aesencStepAvx2(masm, tKey);
        loadKey(masm, tKey, key, 16 * 13, crb);
        aesencStepAvx2(masm, tKey);
        loadKey(masm, tKey, key, 16 * 14, crb);

        masm.bind(labelLastAesRnd);
        for (int rnum = 1; rnum <= 8; rnum++) {
            masm.aesenclast(asXMMRegister(rnum), tKey);
        }

        // XOR and store data
        for (int i = 0; i <= 7; i++) {
            masm.movdqu(t1, new AMD64Address(in, pos, Stride.S1, 16 * i));
            masm.vpxor(asXMMRegister(i + 1), asXMMRegister(i + 1), t1, XMM);
            masm.movdqu(new AMD64Address(out, pos, Stride.S1, 16 * i), asXMMRegister(i + 1));
        }

        masm.cmpptr(ct, out);
        masm.jcc(ConditionFlag.Equal, labelSkipReload);
        for (int i = 0; i <= 7; i++) {
            masm.movdqu(asXMMRegister(i + 1), new AMD64Address(in, pos, Stride.S1, 16 * i));
        }

        masm.bind(labelSkipReload);
        // Update len with the number of blocks processed
        masm.subl(len, 128);
        masm.addl(pos, 128);

        masm.movdqu(t4, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK));
        for (int rnum = 1; rnum <= 8; rnum++) {
            masm.vpshufb(asXMMRegister(rnum), asXMMRegister(rnum), t4, XMM);
        }
        // Combine GHASHed value with the corresponding ciphertext
        masm.vpxor(xmm1, xmm1, t3, XMM);
    }

    private static void evmovdquq(AMD64MacroAssembler masm, AVXSize size, Register dst, AMD64Address src) {
        EVMOVDQU64.emit(masm, size, dst, src);
    }

    private static void evmovdquq(AMD64MacroAssembler masm, AVXSize size, AMD64Address dst, Register src) {
        EVMOVDQU64.emit(masm, size, dst, src);
    }

    private static void evmovdquq(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src) {
        EVMOVDQU64.emit(masm, size, dst, src);
    }

    private static boolean needsEvex(Register reg) {
        return reg.encoding >= 16;
    }

    private static void movdquXmm(AMD64MacroAssembler masm, Register dst, AMD64Address src) {
        if (needsEvex(dst)) {
            EVMOVDQU32.emit(masm, XMM, dst, src);
        } else {
            VMOVDQU32.encoding(VEX).emit(masm, XMM, dst, src);
        }
    }

    private static void movdquXmm(AMD64MacroAssembler masm, AMD64Address dst, Register src) {
        if (needsEvex(src)) {
            EVMOVDQU32.emit(masm, XMM, dst, src);
        } else {
            VMOVDQU32.encoding(VEX).emit(masm, XMM, dst, src);
        }
    }

    private static void movdquXmm(AMD64MacroAssembler masm, Register dst, Register src) {
        if (needsEvex(dst) || needsEvex(src)) {
            EVMOVDQU32.emit(masm, XMM, dst, src);
        } else {
            VMOVDQU32.encoding(VEX).emit(masm, XMM, dst, src);
        }
    }

    private static void movdlVex(AMD64MacroAssembler masm, Register dst, Register src) {
        AMD64Assembler.AMD64SIMDInstructionEncoding oldEncoding = masm.setTemporaryAvxEncoding(VEX);
        masm.movdl(dst, src);
        masm.resetAvxEncoding(oldEncoding);
    }

    private static void evpxorq(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src1, Register src2) {
        EVPXORQ.emit(masm, size, dst, src1, src2);
    }

    private static void evpxorq(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src1, AMD64Address src2) {
        EVPXORQ.emit(masm, size, dst, src1, src2);
    }

    private static void evpternlogq(AMD64MacroAssembler masm, AVXSize size, Register dst, int imm8, Register src1, Register src2) {
        EVPTERNLOGQ.emit(masm, size, dst, src1, src2, imm8);
    }

    private static void evpclmulqdq(AMD64MacroAssembler masm, Register dst, Register src1, Register src2, int imm8) {
        EVPCLMULQDQ.emit(masm, ZMM, dst, src1, src2, imm8);
    }

    private static void evpshufb(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src1, Register src2) {
        EVPSHUFB.emit(masm, size, dst, src1, src2);
    }

    private static void evpshufb(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src1, AMD64Address src2) {
        EVPSHUFB.emit(masm, size, dst, src1, src2);
    }

    private static void evpaddd(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src1, Register src2) {
        EVPADDD.emit(masm, size, dst, src1, src2);
    }

    private static void evpslldq(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src, int imm8) {
        EVPSLLDQ.emit(masm, size, dst, src, imm8);
    }

    private static void evpsrldq(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src, int imm8) {
        EVPSRLDQ.emit(masm, size, dst, src, imm8);
    }

    private static void evpsllq(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src, int imm8) {
        EVPSLLQ.emit(masm, size, dst, src, imm8);
    }

    private static void evpsrlq(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src, int imm8) {
        EVPSRLQ.emit(masm, size, dst, src, imm8);
    }

    private static void evshufi64x2(AMD64MacroAssembler masm, AVXSize size, Register dst, Register src1, Register src2, int imm8) {
        EVSHUFI64X2.emit(masm, size, dst, src1, src2, imm8);
    }

    private static void evLoadKey(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register dst, Register key, int offset, Register shufMask) {
        movdquXmm(masm, dst, new AMD64Address(key, offset));
        evpshufb(masm, XMM, dst, dst, shufMask);
        evshufi64x2(masm, ZMM, dst, dst, dst, 0x0);
    }

    private static void evLoadKey(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register dst, Register key, int offset) {
        movdquXmm(masm, dst, new AMD64Address(key, offset));
        evpshufb(masm, XMM, dst, dst, recordExternalAddress(crb, KEY_SHUFFLE_MASK));
        evshufi64x2(masm, ZMM, dst, dst, dst, 0x0);
    }

    private static void gfmulAvx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register gh, Register hk) {
        Register tmp1 = xmm0;
        Register tmp2 = xmm1;
        Register tmp3 = xmm2;

        evpclmulqdq(masm, tmp1, gh, hk, 0x11);
        evpclmulqdq(masm, tmp2, gh, hk, 0x00);
        evpclmulqdq(masm, tmp3, gh, hk, 0x01);
        evpclmulqdq(masm, gh, gh, hk, 0x10);
        evpxorq(masm, ZMM, gh, gh, tmp3);
        evpsrldq(masm, ZMM, tmp3, gh, 8);
        evpslldq(masm, ZMM, gh, gh, 8);
        evpxorq(masm, ZMM, tmp1, tmp1, tmp3);
        evpxorq(masm, ZMM, gh, gh, tmp2);

        evmovdquq(masm, ZMM, tmp3, recordExternalAddress(crb, GHASH_POLYNOMIAL_REDUCTION));
        evpclmulqdq(masm, tmp2, tmp3, gh, 0x01);
        evpslldq(masm, ZMM, tmp2, tmp2, 8);
        evpxorq(masm, ZMM, gh, gh, tmp2);
        evpclmulqdq(masm, tmp2, tmp3, gh, 0x00);
        evpsrldq(masm, ZMM, tmp2, tmp2, 4);
        evpclmulqdq(masm, gh, tmp3, gh, 0x10);
        evpslldq(masm, ZMM, gh, gh, 4);
        evpternlogq(masm, ZMM, gh, 0x96, tmp1, tmp2);
    }

    // Holds 64 Htbl entries, 32 HKey and 32 HkKey (derived from HKey)
    private void generateHtbl32BlocksAvx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register htbl, Register avx512Htbl) {
        Register hk = xmm6;
        Register zt1 = xmm0;
        Register zt2 = xmm1;
        Register zt3 = xmm2;
        Register zt5 = xmm4;
        Register zt7 = xmm7;
        Register zt8 = xmm8;
        Register zt10 = xmm10;
        Register zt11 = xmm11;
        Register zt12 = xmm12;

        movdquXmm(masm, hk, new AMD64Address(htbl, 0));
        movdquXmm(masm, zt10, recordExternalAddress(crb, GHASH_LONG_SWAP_MASK));
        masm.vpshufb(hk, hk, zt10, XMM);
        movdquXmm(masm, zt11, recordExternalAddress(crb, GHASH_POLYNOMIAL));
        movdquXmm(masm, zt12, recordExternalAddress(crb, GHASH_POLYNOMIAL_TWO_ONE));
        // Compute H ^ 2 from the input subkeyH
        movdquXmm(masm, zt3, hk);
        masm.vpsllq(hk, hk, 1, XMM);
        masm.vpsrlq(zt3, zt3, 63, XMM);
        movdquXmm(masm, zt2, zt3);
        masm.vpslldq(zt3, zt3, 8, XMM);
        masm.vpsrldq(zt2, zt2, 8, XMM);
        masm.vpor(hk, hk, zt3, XMM);
        masm.vpshufd(zt3, zt2, 0x24, XMM);
        VPCMPEQD.emit(masm, XMM, zt3, zt3, zt12);
        masm.vpand(zt3, zt3, zt11, XMM);
        masm.vpxor(hk, hk, zt3, XMM);
        movdquXmm(masm, new AMD64Address(avx512Htbl, 16 * 31), hk); // H ^ 2

        movdquXmm(masm, zt5, hk);
        EVINSERTI64X2.emit(masm, ZMM, zt7, zt7, hk, 3);

        // calculate HashKey ^ 2 << 1 mod poly
        gfmulAvx512(crb, masm, zt5, hk);
        movdquXmm(masm, new AMD64Address(avx512Htbl, 16 * 30), zt5);
        EVINSERTI64X2.emit(masm, ZMM, zt7, zt7, zt5, 2);

        // calculate HashKey ^ 3 << 1 mod poly
        gfmulAvx512(crb, masm, zt5, hk);
        movdquXmm(masm, new AMD64Address(avx512Htbl, 16 * 29), zt5);
        EVINSERTI64X2.emit(masm, ZMM, zt7, zt7, zt5, 1);

        // calculate HashKey ^ 4 << 1 mod poly
        gfmulAvx512(crb, masm, zt5, hk);
        movdquXmm(masm, new AMD64Address(avx512Htbl, 16 * 28), zt5);
        EVINSERTI64X2.emit(masm, ZMM, zt7, zt7, zt5, 0);
        // ZT5 amd ZT7 to be cleared(hash key)
        // calculate HashKeyK = HashKey x POLY
        evmovdquq(masm, ZMM, xmm11, recordExternalAddress(crb, GHASH_POLYNOMIAL));
        evpclmulqdq(masm, zt1, zt7, xmm11, 0x10);
        EVPSHUFD.emit(masm, ZMM, zt2, zt7, 78);
        evpxorq(masm, ZMM, zt1, zt1, zt2);
        evmovdquq(masm, ZMM, new AMD64Address(avx512Htbl, 16 * 60), zt1);
        // **ZT1 amd ZT2 to be cleared(hash key)

        // switch to 4x128 - bit computations now
        evshufi64x2(masm, ZMM, zt5, zt5, zt5, 0x00); // ;; broadcast HashKey ^ 4 across all ZT5
        evmovdquq(masm, ZMM, zt8, zt7); // ; save HashKey ^ 4 to HashKey ^ 1 in ZT8
        // **ZT8 to be cleared(hash key)

        // calculate HashKey ^ 5 << 1 mod poly, HashKey ^ 6 << 1 mod poly, ... HashKey ^ 8 << 1 mod poly
        gfmulAvx512(crb, masm, zt7, zt5);
        evmovdquq(masm, ZMM, new AMD64Address(avx512Htbl, 16 * 24), zt7); // ; HashKey ^ 8 to HashKey ^ 5 in ZT7 now

        // calculate HashKeyX = HashKey x POLY
        evpclmulqdq(masm, zt1, zt7, xmm11, 0x10);
        EVPSHUFD.emit(masm, ZMM, zt2, zt7, 78);
        evpxorq(masm, ZMM, zt1, zt1, zt2);
        evmovdquq(masm, ZMM, new AMD64Address(avx512Htbl, 16 * 56), zt1);

        evshufi64x2(masm, ZMM, zt5, zt7, zt7, 0x00); // ;; broadcast HashKey ^ 8 across all ZT5

        for (int i = 20, j = 52; i > 0;) {
            gfmulAvx512(crb, masm, zt8, zt5);
            evmovdquq(masm, ZMM, new AMD64Address(avx512Htbl, 16 * i), zt8);
            // calculate HashKeyK = HashKey x POLY
            evpclmulqdq(masm, zt1, zt8, xmm11, 0x10);
            EVPSHUFD.emit(masm, ZMM, zt2, zt8, 78);
            evpxorq(masm, ZMM, zt1, zt1, zt2);
            evmovdquq(masm, ZMM, new AMD64Address(avx512Htbl, 16 * j), zt1);

            i -= 4;
            j -= 4;
            // compute HashKey ^ (8 + n), HashKey ^ (7 + n), ... HashKey ^ (5 + n)
            gfmulAvx512(crb, masm, zt7, zt5);
            evmovdquq(masm, ZMM, new AMD64Address(avx512Htbl, 16 * i), zt7);

            // calculate HashKeyK = HashKey x POLY
            evpclmulqdq(masm, zt1, zt7, xmm11, 0x10);
            EVPSHUFD.emit(masm, ZMM, zt2, zt7, 78);
            evpxorq(masm, ZMM, zt1, zt1, zt2);
            evmovdquq(masm, ZMM, new AMD64Address(avx512Htbl, 16 * j), zt1);

            i -= 4;
            j -= 4;
        }
    }

    private static void vhpxori4x128(AMD64MacroAssembler masm, Register reg, Register tmp) {
        EVEXTRACTI64X4.emit(masm, ZMM, tmp, reg, 1);
        evpxorq(masm, YMM, reg, reg, tmp);
        EVEXTRACTI32X4.emit(masm, ZMM, tmp, reg, 1);
        evpxorq(masm, XMM, reg, reg, tmp);
    }

    private static void roundEncode(AMD64MacroAssembler masm, Register key, Register dst1, Register dst2, Register dst3, Register dst4) {
        EVAESENC.emit(masm, ZMM, dst1, dst1, key);
        EVAESENC.emit(masm, ZMM, dst2, dst2, key);
        EVAESENC.emit(masm, ZMM, dst3, dst3, key);
        EVAESENC.emit(masm, ZMM, dst4, dst4, key);
    }

    private static void lastRoundEncode(AMD64MacroAssembler masm, Register key, Register dst1, Register dst2, Register dst3, Register dst4) {
        EVAESENCLAST.emit(masm, ZMM, dst1, dst1, key);
        EVAESENCLAST.emit(masm, ZMM, dst2, dst2, key);
        EVAESENCLAST.emit(masm, ZMM, dst3, dst3, key);
        EVAESENCLAST.emit(masm, ZMM, dst4, dst4, key);
    }

    private static void storeData(AMD64MacroAssembler masm, Register dst, Register position, Register src1, Register src2, Register src3, Register src4) {
        evmovdquq(masm, ZMM, new AMD64Address(dst, position, Stride.S1, 0 * 64), src1);
        evmovdquq(masm, ZMM, new AMD64Address(dst, position, Stride.S1, 1 * 64), src2);
        evmovdquq(masm, ZMM, new AMD64Address(dst, position, Stride.S1, 2 * 64), src3);
        evmovdquq(masm, ZMM, new AMD64Address(dst, position, Stride.S1, 3 * 64), src4);
    }

    private static void loadData(AMD64MacroAssembler masm, Register src, Register position, Register dst1, Register dst2, Register dst3, Register dst4) {
        evmovdquq(masm, ZMM, dst1, new AMD64Address(src, position, Stride.S1, 0 * 64));
        evmovdquq(masm, ZMM, dst2, new AMD64Address(src, position, Stride.S1, 1 * 64));
        evmovdquq(masm, ZMM, dst3, new AMD64Address(src, position, Stride.S1, 2 * 64));
        evmovdquq(masm, ZMM, dst4, new AMD64Address(src, position, Stride.S1, 3 * 64));
    }

    private static void carrylessMultiply(AMD64MacroAssembler masm, Register dst00, Register dst01, Register dst10, Register dst11, Register ghdata, Register hkey2, Register hkey1) {
        evpclmulqdq(masm, dst00, ghdata, hkey2, 0x00);
        evpclmulqdq(masm, dst01, ghdata, hkey2, 0x10);
        evpclmulqdq(masm, dst10, ghdata, hkey1, 0x01);
        evpclmulqdq(masm, dst11, ghdata, hkey1, 0x11);
    }

    private static void shuffle(AMD64MacroAssembler masm, Register dst0, Register dst1, Register dst2, Register dst3, Register src0, Register src1, Register src2, Register src3, Register shufmask) {
        evpshufb(masm, ZMM, dst0, src0, shufmask);
        evpshufb(masm, ZMM, dst1, src1, shufmask);
        evpshufb(masm, ZMM, dst2, src2, shufmask);
        evpshufb(masm, ZMM, dst3, src3, shufmask);
    }

    private static void xorBeforeStore(AMD64MacroAssembler masm, Register dst0, Register dst1, Register dst2, Register dst3, Register src0, Register src1, Register src2, Register src3) {
        evpxorq(masm, ZMM, dst0, dst0, src0);
        evpxorq(masm, ZMM, dst1, dst1, src1);
        evpxorq(masm, ZMM, dst2, dst2, src2);
        evpxorq(masm, ZMM, dst3, dst3, src3);
    }

    private static void xorGHASH(AMD64MacroAssembler masm, Register dst0, Register dst1, Register dst2, Register dst3,
                    Register src02, Register src03, Register src12, Register src13, Register src22, Register src23, Register src32, Register src33) {
        evpternlogq(masm, ZMM, dst0, 0x96, src02, src03);
        evpternlogq(masm, ZMM, dst1, 0x96, src12, src13);
        evpternlogq(masm, ZMM, dst2, 0x96, src22, src23);
        evpternlogq(masm, ZMM, dst3, 0x96, src32, src33);
    }

    // schoolbook multiply of 16 blocks(8 x 16 bytes)
    // it is assumed that data read is already shuffledand
    private static void ghash16Avx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, boolean startGhash, boolean doReduction, boolean uloadShuffle, boolean hkBroadcast,
                    boolean doHxor, Register in, Register pos, Register subkeyHtbl, Register hash, Register shufm, int inOffset, int inDisp, int displacement, int hashkeyOffset) {
        Register ztmp0 = xmm0;
        Register ztmp1 = xmm3;
        Register ztmp2 = xmm4;
        Register ztmp3 = xmm5;
        Register ztmp4 = xmm6;
        Register ztmp5 = xmm7;
        Register ztmp6 = xmm10;
        Register ztmp7 = xmm11;
        Register ztmp8 = xmm12;
        Register ztmp9 = xmm13;
        Register ztmpa = xmm26;
        Register ztmpb = xmm23;
        Register gh = xmm24;
        Register gl = xmm25;
        int hkeyGap = 16 * 32;

        if (uloadShuffle) {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp));
            evpshufb(masm, ZMM, ztmp9, ztmp9, shufm);
        } else {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp));
        }

        if (startGhash) {
            evpxorq(masm, ZMM, ztmp9, ztmp9, hash);
        }
        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 0 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 0 * 64));
        } else {
            evmovdquq(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 0 * 64));
            evmovdquq(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 0 * 64));
        }

        carrylessMultiply(masm, ztmp0, ztmp1, ztmp2, ztmp3, ztmp9, ztmpa, ztmp8);

        // ghash blocks 4 - 7
        if (uloadShuffle) {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp + 64));
            evpshufb(masm, ZMM, ztmp9, ztmp9, shufm);
        } else {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp + 64));
        }

        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 1 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 1 * 64));
        } else {
            evmovdquq(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 1 * 64));
            evmovdquq(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 1 * 64));
        }

        carrylessMultiply(masm, ztmp4, ztmp5, ztmp6, ztmp7, ztmp9, ztmpa, ztmp8);

        // update sums
        if (startGhash) {
            evpxorq(masm, ZMM, gl, ztmp0, ztmp2); // T2 = THL + TLL
            evpxorq(masm, ZMM, gh, ztmp1, ztmp3); // T1 = THH + TLH
        } else { // mid, end, end_reduce
            evpternlogq(masm, ZMM, gl, 0x96, ztmp0, ztmp2); // T2 = THL + TLL
            evpternlogq(masm, ZMM, gh, 0x96, ztmp1, ztmp3); // T1 = THH + TLH
        }
        // ghash blocks 8 - 11
        if (uloadShuffle) {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp + 128));
            evpshufb(masm, ZMM, ztmp9, ztmp9, shufm);
        } else {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp + 128));
        }
        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 2 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 2 * 64));
        } else {
            evmovdquq(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 2 * 64));
            evmovdquq(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 2 * 64));
        }

        carrylessMultiply(masm, ztmp0, ztmp1, ztmp2, ztmp3, ztmp9, ztmpa, ztmp8);

        // update sums
        evpternlogq(masm, ZMM, gl, 0x96, ztmp6, ztmp4); // T2 = THL + TLL
        evpternlogq(masm, ZMM, gh, 0x96, ztmp7, ztmp5); // T1 = THH + TLH
        // ghash blocks 12 - 15
        if (uloadShuffle) {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp + 192));
            evpshufb(masm, ZMM, ztmp9, ztmp9, shufm);
        } else {
            evmovdquq(masm, ZMM, ztmp9, new AMD64Address(subkeyHtbl, inOffset * 16 + inDisp + 192));
        }

        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 3 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 3 * 64));
        } else {
            evmovdquq(masm, ZMM, ztmp8, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + 3 * 64));
            evmovdquq(masm, ZMM, ztmpa, new AMD64Address(subkeyHtbl, hashkeyOffset + displacement + hkeyGap + 3 * 64));
        }
        carrylessMultiply(masm, ztmp4, ztmp5, ztmp6, ztmp7, ztmp9, ztmpa, ztmp8);

        // update sums
        xorGHASH(masm, gl, gh, gl, gh, ztmp0, ztmp2, ztmp1, ztmp3, ztmp6, ztmp4, ztmp7, ztmp5);

        if (doReduction) {
            // new reduction
            evmovdquq(masm, ZMM, ztmpb, recordExternalAddress(crb, GHASH_POLYNOMIAL));
            evpclmulqdq(masm, hash, gl, ztmpb, 0x10);
            EVPSHUFD.emit(masm, ZMM, ztmp0, gl, 78);
            evpternlogq(masm, ZMM, hash, 0x96, gh, ztmp0);
            if (doHxor) {
                vhpxori4x128(masm, hash, ztmp0);
            }
        }
    }

    // Stitched GHASH of 16 blocks(with reduction) with encryption of 0 blocks
    private static void gcmEncDecLastAvx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register len, Register in, Register pos, Register hash, Register shufm,
                    Register subkeyHtbl, int ghashinOffset, int hashkeyOffset, boolean startGhash, boolean doReduction) {
        // there is 0 blocks to cipher so there are only 16 blocks for ghash and reduction
        ghash16Avx512(crb, masm, startGhash, doReduction, false, false, true, in, pos, subkeyHtbl, hash, shufm, ghashinOffset, 0, 0, hashkeyOffset);
    }

    // Main GCM macro stitching cipher with GHASH
    // encrypts 16 blocks at a time
    // ghash the 16 previously encrypted ciphertext blocks
    private static void ghash16EncryptParallel16Avx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register in, Register out, Register ct, Register pos,
                    Register avx512SubkeyHtbl, Register ctrCheck, Register nRounds, Register key, Register ctrBE, Register ghashIn, Register addbe4x4, Register addbe1234,
                    Register add1234, Register shfmsk, boolean hkBroadcast, boolean isHashStart, boolean doHashReduction, boolean doHashHxor, boolean noGhashIn,
                    int ghashinOffset, int aesoutOffset, int hashkeyOffset) {
        Register b0003 = xmm0;
        Register b0407 = xmm3;
        Register b0811 = xmm4;
        Register b1215 = xmm5;
        Register thh1 = xmm6;
        Register thl1 = xmm7;
        Register tlh1 = xmm10;
        Register tll1 = xmm11;
        Register thh2 = xmm12;
        Register thl2 = xmm13;
        Register tlh2 = xmm15;
        Register tll2 = xmm16;
        Register thh3 = xmm17;
        Register thl3 = xmm19;
        Register tlh3 = xmm20;
        Register tll3 = xmm21;
        Register data1 = xmm17;
        Register data2 = xmm19;
        Register data3 = xmm20;
        Register data4 = xmm21;
        Register aeskey1 = xmm30;
        Register aeskey2 = xmm31;
        Register ghkey1 = xmm1;
        Register ghkey2 = xmm18;
        Register ghdat1 = xmm8;
        Register ghdat2 = xmm22;
        Register zt = xmm23;
        Register toReduceL = xmm25;
        Register toReduceH = xmm24;
        int hkeyGap = 16 * 32;

        Label blocksOverflow = new Label();
        Label blocksOk = new Label();
        Label skipShuffle = new Label();
        Label cont = new Label();
        Label aes256 = new Label();
        Label aes192 = new Label();
        Label lastAesRnd = new Label();

        masm.cmpb(ctrCheck, 256 - 16);
        masm.jcc(ConditionFlag.AboveEqual, blocksOverflow);
        evpaddd(masm, ZMM, b0003, ctrBE, addbe1234);
        evpaddd(masm, ZMM, b0407, b0003, addbe4x4);
        evpaddd(masm, ZMM, b0811, b0407, addbe4x4);
        evpaddd(masm, ZMM, b1215, b0811, addbe4x4);
        masm.jmp(blocksOk);
        masm.bind(blocksOverflow);
        evpshufb(masm, ZMM, ctrBE, ctrBE, shfmsk);
        evmovdquq(masm, ZMM, b1215, recordExternalAddress(crb, COUNTER_MASK_LINC4));
        evpaddd(masm, ZMM, b0003, ctrBE, add1234);
        evpaddd(masm, ZMM, b0407, b0003, b1215);
        evpaddd(masm, ZMM, b0811, b0407, b1215);
        evpaddd(masm, ZMM, b1215, b0811, b1215);
        shuffle(masm, b0003, b0407, b0811, b1215, b0003, b0407, b0811, b1215, shfmsk);

        masm.bind(blocksOk);

        // pre - load constants
        evLoadKey(crb, masm, aeskey1, key, 0);
        if (!noGhashIn) {
            evpxorq(masm, ZMM, ghdat1, ghashIn, new AMD64Address(avx512SubkeyHtbl, 16 * ghashinOffset));
        } else {
            evmovdquq(masm, ZMM, ghdat1, new AMD64Address(avx512SubkeyHtbl, 16 * ghashinOffset));
        }

        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 0 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 0 * 64));
        } else {
            evmovdquq(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 0 * 64));
            evmovdquq(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 0 * 64));
        }

        // save counter for the next round
        // increment counter overflow check register
        evshufi64x2(masm, ZMM, ctrBE, b1215, b1215, 255);
        masm.addb(ctrCheck, 16);

        // pre - load constants
        evLoadKey(crb, masm, aeskey2, key, 1 * 16);
        evmovdquq(masm, ZMM, ghdat2, new AMD64Address(avx512SubkeyHtbl, 16 * (ghashinOffset + 4)));

        // stitch AES rounds with GHASH
        // AES round 0
        evpxorq(masm, ZMM, b0003, b0003, aeskey1);
        evpxorq(masm, ZMM, b0407, b0407, aeskey1);
        evpxorq(masm, ZMM, b0811, b0811, aeskey1);
        evpxorq(masm, ZMM, b1215, b1215, aeskey1);
        evLoadKey(crb, masm, aeskey1, key, 2 * 16);

        // GHASH 4 blocks(15 to 12)
        carrylessMultiply(masm, tll1, tlh1, thl1, thh1, ghdat1, ghkey2, ghkey1);

        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 1 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 1 * 64));
        } else {
            evmovdquq(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 1 * 64));
            evmovdquq(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 1 * 64));
        }

        evmovdquq(masm, ZMM, ghdat1, new AMD64Address(avx512SubkeyHtbl, 16 * (ghashinOffset + 8)));

        // AES round 1
        roundEncode(masm, aeskey2, b0003, b0407, b0811, b1215);

        evLoadKey(crb, masm, aeskey2, key, 3 * 16);

        // GHASH 4 blocks(11 to 8)
        carrylessMultiply(masm, tll2, tlh2, thl2, thh2, ghdat2, ghkey2, ghkey1);

        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 2 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 2 * 64));
        } else {
            evmovdquq(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 2 * 64));
            evmovdquq(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 2 * 64));
        }
        evmovdquq(masm, ZMM, ghdat2, new AMD64Address(avx512SubkeyHtbl, 16 * (ghashinOffset + 12)));

        // AES round 2
        roundEncode(masm, aeskey1, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey1, key, 4 * 16);

        // GHASH 4 blocks(7 to 4)
        carrylessMultiply(masm, tll3, tlh3, thl3, thh3, ghdat1, ghkey2, ghkey1);

        if (hkBroadcast) {
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 3 * 64));
            EVBROADCASTF64X2.emit(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 3 * 64));
        } else {
            evmovdquq(masm, ZMM, ghkey1, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + 3 * 64));
            evmovdquq(masm, ZMM, ghkey2, new AMD64Address(avx512SubkeyHtbl, hashkeyOffset + hkeyGap + 3 * 64));
        }

        // AES rounds 3
        roundEncode(masm, aeskey2, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey2, key, 5 * 16);

        // Gather(XOR) GHASH for 12 blocks
        xorGHASH(masm, tll1, tlh1, thl1, thh1, tll2, tll3, tlh2, tlh3, thl2, thl3, thh2, thh3);

        // AES rounds 4
        roundEncode(masm, aeskey1, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey1, key, 6 * 16);

        // load plain / cipher text(recycle GH3xx registers)
        loadData(masm, in, pos, data1, data2, data3, data4);

        // AES rounds 5
        roundEncode(masm, aeskey2, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey2, key, 7 * 16);

        // GHASH 4 blocks(3 to 0)
        carrylessMultiply(masm, tll2, tlh2, thl2, thh2, ghdat2, ghkey2, ghkey1);

        // AES round 6
        roundEncode(masm, aeskey1, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey1, key, 8 * 16);

        // gather GHASH in TO_REDUCE_H / L
        if (isHashStart) {
            evpxorq(masm, ZMM, toReduceL, tll2, thl2);
            evpxorq(masm, ZMM, toReduceH, thh2, tlh2);
            evpternlogq(masm, ZMM, toReduceL, 0x96, tll1, thl1);
            evpternlogq(masm, ZMM, toReduceH, 0x96, thh1, tlh1);
        } else {
            // not the first round so sums need to be updated
            xorGHASH(masm, toReduceL, toReduceH, toReduceL, toReduceH, tll2, thl2, thh2, tlh2, tll1, thl1, thh1, tlh1);
        }

        // AES round 7
        roundEncode(masm, aeskey2, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey2, key, 9 * 16);

        // new reduction
        if (doHashReduction) {
            evmovdquq(masm, ZMM, zt, recordExternalAddress(crb, GHASH_POLYNOMIAL_REDUCTION));
            evpclmulqdq(masm, thh1, toReduceL, zt, 0x10);
            EVPSHUFD.emit(masm, ZMM, toReduceL, toReduceL, 78);
            evpternlogq(masm, ZMM, thh1, 0x96, toReduceH, toReduceL);
        }

        // AES round 8
        roundEncode(masm, aeskey1, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey1, key, 10 * 16);

        // horizontalxor of 4 reduced hashes
        if (doHashHxor) {
            vhpxori4x128(masm, thh1, tll1);
        }

        // AES round 9
        roundEncode(masm, aeskey2, b0003, b0407, b0811, b1215);
        // AES rounds up to 11 (AES192) or 13 (AES256)
        // AES128 is done
        masm.cmplAndJcc(nRounds, 52, ConditionFlag.Less, lastAesRnd, false);
        masm.bind(aes192);
        evLoadKey(crb, masm, aeskey2, key, 11 * 16);
        roundEncode(masm, aeskey1, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey1, key, 12 * 16);
        roundEncode(masm, aeskey2, b0003, b0407, b0811, b1215);
        masm.cmplAndJcc(nRounds, 60, ConditionFlag.Less, lastAesRnd, false);
        masm.bind(aes256);
        evLoadKey(crb, masm, aeskey2, key, 13 * 16);
        roundEncode(masm, aeskey1, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, aeskey1, key, 14 * 16);
        roundEncode(masm, aeskey2, b0003, b0407, b0811, b1215);

        masm.bind(lastAesRnd);
        // the last AES round
        lastRoundEncode(masm, aeskey1, b0003, b0407, b0811, b1215);
        // AESKEY1and AESKEY2 contain AES round keys

        // XOR against plain / cipher text
        xorBeforeStore(masm, b0003, b0407, b0811, b1215, data1, data2, data3, data4);

        // store cipher / plain text
        storeData(masm, out, pos, b0003, b0407, b0811, b1215);
        // **B00_03, B04_07, B08_011, B12_B15 may contain sensitive data

        // shuffle cipher text blocks for GHASH computation
        masm.cmpptr(ct, out);
        masm.jcc(ConditionFlag.NotEqual, skipShuffle);
        shuffle(masm, b0003, b0407, b0811, b1215, b0003, b0407, b0811, b1215, shfmsk);
        masm.jmp(cont);
        masm.bind(skipShuffle);
        shuffle(masm, b0003, b0407, b0811, b1215, data1, data2, data3, data4, shfmsk);

        // **B00_03, B04_07, B08_011, B12_B15 overwritten with shuffled cipher text
        masm.bind(cont);
        // store shuffled cipher text for ghashing
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * aesoutOffset), b0003);
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * (aesoutOffset + 4)), b0407);
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * (aesoutOffset + 8)), b0811);
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * (aesoutOffset + 12)), b1215);
    }

    // Encrypt / decrypt the initial 16 blocks
    private static void initialBlocks16Avx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register in, Register out, Register ct, Register pos, Register key,
                    Register avx512SubkeyHtbl, Register ctrCheck, Register rounds, Register ctr, Register ghash, Register addbe4x4, Register addbe1234, Register add1234,
                    Register shufMask, int stackOffset) {
        Register b0003 = xmm7;
        Register b0407 = xmm10;
        Register b0811 = xmm11;
        Register b1215 = xmm12;
        Register t0 = xmm0;
        Register t1 = xmm3;
        Register t2 = xmm4;
        Register t3 = xmm5;
        Register t4 = xmm6;
        Register t5 = xmm30;

        Label next16Overflow = new Label();
        Label next16Ok = new Label();
        Label cont = new Label();
        Label skipShuffle = new Label();
        Label aes256 = new Label();
        Label aes192 = new Label();
        Label lastAesRnd = new Label();
        // prepare counter blocks
        masm.cmpb(ctrCheck, 256 - 16);
        masm.jcc(ConditionFlag.AboveEqual, next16Overflow);
        evpaddd(masm, ZMM, b0003, ctr, addbe1234);
        evpaddd(masm, ZMM, b0407, b0003, addbe4x4);
        evpaddd(masm, ZMM, b0811, b0407, addbe4x4);
        evpaddd(masm, ZMM, b1215, b0811, addbe4x4);
        masm.jmp(next16Ok);
        masm.bind(next16Overflow);
        evpshufb(masm, ZMM, ctr, ctr, shufMask);
        evmovdquq(masm, ZMM, b1215, recordExternalAddress(crb, COUNTER_MASK_LINC4));
        evpaddd(masm, ZMM, b0003, ctr, add1234);
        evpaddd(masm, ZMM, b0407, b0003, b1215);
        evpaddd(masm, ZMM, b0811, b0407, b1215);
        evpaddd(masm, ZMM, b1215, b0811, b1215);
        shuffle(masm, b0003, b0407, b0811, b1215, b0003, b0407, b0811, b1215, shufMask);
        masm.bind(next16Ok);
        evshufi64x2(masm, ZMM, ctr, b1215, b1215, 255);
        masm.addb(ctrCheck, 16);

        // load 16 blocks of data
        loadData(masm, in, pos, t0, t1, t2, t3);

        // move to AES encryption rounds
        movdquXmm(masm, t5, recordExternalAddress(crb, KEY_SHUFFLE_MASK));
        evLoadKey(crb, masm, t4, key, 0, t5);
        evpxorq(masm, ZMM, b0003, b0003, t4);
        evpxorq(masm, ZMM, b0407, b0407, t4);
        evpxorq(masm, ZMM, b0811, b0811, t4);
        evpxorq(masm, ZMM, b1215, b1215, t4);

        for (int i = 1; i < 10; i++) {
            evLoadKey(crb, masm, t4, key, i * 16, t5);
            roundEncode(masm, t4, b0003, b0407, b0811, b1215);
        }

        evLoadKey(crb, masm, t4, key, 10 * 16, t5);
        masm.cmplAndJcc(rounds, 52, ConditionFlag.Less, lastAesRnd, false);
        masm.bind(aes192);
        roundEncode(masm, t4, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, t4, key, 16 * 11, t5);
        roundEncode(masm, t4, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, t4, key, 16 * 12, t5);
        masm.cmplAndJcc(rounds, 60, ConditionFlag.Less, lastAesRnd, false);
        masm.bind(aes256);
        roundEncode(masm, t4, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, t4, key, 16 * 13, t5);
        roundEncode(masm, t4, b0003, b0407, b0811, b1215);
        evLoadKey(crb, masm, t4, key, 16 * 14, t5);

        masm.bind(lastAesRnd);
        lastRoundEncode(masm, t4, b0003, b0407, b0811, b1215);

        // xor against text
        xorBeforeStore(masm, b0003, b0407, b0811, b1215, t0, t1, t2, t3);

        // store
        storeData(masm, out, pos, b0003, b0407, b0811, b1215);

        masm.cmpptr(ct, out);
        masm.jcc(ConditionFlag.Equal, skipShuffle);
        // decryption - cipher text needs to go to GHASH phase
        shuffle(masm, b0003, b0407, b0811, b1215, t0, t1, t2, t3, shufMask);
        masm.jmp(cont);
        masm.bind(skipShuffle);
        shuffle(masm, b0003, b0407, b0811, b1215, b0003, b0407, b0811, b1215, shufMask);

        // B00_03, B04_07, B08_11, B12_15 overwritten with shuffled cipher text
        masm.bind(cont);
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * stackOffset), b0003);
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * (stackOffset + 4)), b0407);
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * (stackOffset + 8)), b0811);
        evmovdquq(masm, ZMM, new AMD64Address(avx512SubkeyHtbl, 16 * (stackOffset + 12)), b1215);
    }

    private void aesgcmAvx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register in, Register len, Register ct, Register out, Register key, Register state,
                    Register subkeyHtbl, Register avx512SubkeyHtbl, Register counter) {
        Label encDecDone = new Label();
        Label mesgBelow32Blks = new Label();
        Label noBigBlks = new Label();
        Label encryptBigBlksNoHxor = new Label();
        Label encryptBigNblks = new Label();
        Label encrypt16Blks = new Label();
        Label encryptNGhash32NBlks = new Label();
        Label ghashDone = new Label();
        Register ctrBlockx = xmm2;
        Register aadHashx = xmm14;
        Register ztmp0 = xmm0;
        Register ztmp1 = xmm3; // **sensitive
        Register ztmp2 = xmm4; // **sensitive(small data)
        Register ztmp3 = xmm5; // **sensitive(small data)
        Register ztmp4 = xmm6;
        Register ztmp5 = xmm7;
        Register ztmp6 = xmm10;
        Register ztmp7 = xmm11;
        Register ztmp8 = xmm12;
        Register ztmp9 = xmm13;
        Register ztmp10 = xmm15;
        Register ztmp11 = xmm16;
        Register ztmp12 = xmm17;
        Register ztmp13 = xmm19;
        Register ztmp14 = xmm20;
        Register ztmp15 = xmm21;
        Register ztmp16 = xmm30;
        Register ztmp17 = xmm31;
        Register ztmp18 = xmm1;
        Register ztmp19 = xmm18;
        Register ztmp20 = xmm8;
        Register ztmp21 = xmm22;
        Register ztmp22 = xmm23;
        Register ztmp23 = xmm26;
        Register gh = xmm24;
        Register gl = xmm25;
        Register shufMask = xmm29;
        Register addbe4x4 = xmm27;
        Register addbe1234 = xmm28;
        Register add1234 = xmm9;
        Register pos = rax;
        Register rounds = r15;
        Register ctrCheck = r14;

        int stackOffset = 64;
        int ghashinOffset = 64;
        int aesoutOffset = 64;
        int hashkeyOffset = 0;
        int hashKey32 = 0;
        int hashKey16 = 16 * 16;

        masm.movl(pos, 0);
        masm.cmplAndJcc(len, 256, ConditionFlag.LessEqual, encDecDone, false);

        /*
         * Structure of the Htbl is as follows:
         *   Where 0 - 31 we have 32 Hashkey's and 32-63 we have 32 HashKeyK (derived from HashKey)
         *   Rest 8 entries are for storing CTR values post AES rounds
         * ----------------------------------------------------------------------------------------
         *     Hashkey32 -> 16 * 0
         *     Hashkey31 -> 16 * 1
         *     Hashkey30 -> 16 * 2
         *     ........
         *     Hashkey1 -> 16 * 31
         *     ---------------------
         *     HaskeyK32 -> 16 * 32
         *     HashkeyK31 -> 16 * 33
         *     .........
         *     HashkeyK1 -> 16 * 63
         *     ---------------------
         *     1st set of AES Entries
         *     B00_03 -> 16 * 64
         *     B04_07 -> 16 * 68
         *     B08_11 -> 16 * 72
         *     B12_15 -> 16 * 80
         *     ---------------------
         *     2nd set of AES Entries
         *     B00_03 -> 16 * 84
         *     B04_07 -> 16 * 88
         *     B08_11 -> 16 * 92
         *     B12_15 -> 16 * 96
         *     ---------------------
         */
        generateHtbl32BlocksAvx512(crb, masm, subkeyHtbl, avx512SubkeyHtbl);

        // Move initial counter value and STATE value into variables
        movdquXmm(masm, ctrBlockx, new AMD64Address(counter, 0));
        movdquXmm(masm, aadHashx, new AMD64Address(state, 0));

        // Load lswap mask for ghash
        movdquXmm(masm, xmm24, recordExternalAddress(crb, GHASH_LONG_SWAP_MASK));
        // Shuffle input state using lswap mask
        evpshufb(masm, XMM, aadHashx, aadHashx, xmm24);

        // Compute #rounds for AES based on the length of the key array
        masm.movl(rounds, new AMD64Address(key, lengthOffset));

        evmovdquq(masm, ZMM, addbe4x4, recordExternalAddress(crb, COUNTER_MASK_ADDBE_4444));
        evmovdquq(masm, ZMM, addbe1234, recordExternalAddress(crb, COUNTER_MASK_ADDBE_1234));
        evmovdquq(masm, ZMM, shufMask, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK));
        evmovdquq(masm, ZMM, add1234, recordExternalAddress(crb, COUNTER_MASK_ADD_1234));

        // Shuffle counter, subtract 1 from the pre-incremented counter value and broadcast counter value to 512 bit register
        evpshufb(masm, XMM, ctrBlockx, ctrBlockx, shufMask);
        VPSUBD.emit(masm, XMM, ctrBlockx, ctrBlockx, add1234);
        evshufi64x2(masm, ZMM, ctrBlockx, ctrBlockx, ctrBlockx, 0);

        movdlVex(masm, ctrCheck, ctrBlockx);
        masm.andl(ctrCheck, 255);

        // Reshuffle counter
        evpshufb(masm, ZMM, ctrBlockx, ctrBlockx, shufMask);

        initialBlocks16Avx512(crb, masm, in, out, ct, pos, key, avx512SubkeyHtbl, ctrCheck, rounds, ctrBlockx, aadHashx, addbe4x4, addbe1234, add1234, shufMask,
                        stackOffset);
        masm.addl(pos, 16 * 16);
        masm.cmplAndJcc(len, 32 * 16, ConditionFlag.Below, mesgBelow32Blks, false);

        initialBlocks16Avx512(crb, masm, in, out, ct, pos, key, avx512SubkeyHtbl, ctrCheck, rounds, ctrBlockx, aadHashx, addbe4x4, addbe1234, add1234, shufMask,
                        stackOffset + 16);
        masm.addl(pos, 16 * 16);
        masm.subl(len, 32 * 16);

        masm.cmplAndJcc(len, 32 * 16, ConditionFlag.Below, noBigBlks, false);

        masm.bind(encryptBigBlksNoHxor);
        masm.cmplAndJcc(len, 2 * 32 * 16, ConditionFlag.Below, encryptBigNblks, false);
        ghash16EncryptParallel16Avx512(crb, masm, in, out, ct, pos, avx512SubkeyHtbl, ctrCheck, rounds, key, ctrBlockx, aadHashx, addbe4x4, addbe1234, add1234, shufMask,
                        true, true, false, false, false, ghashinOffset, aesoutOffset, hashKey32);
        masm.addl(pos, 16 * 16);

        ghash16EncryptParallel16Avx512(crb, masm, in, out, ct, pos, avx512SubkeyHtbl, ctrCheck, rounds, key, ctrBlockx, aadHashx, addbe4x4, addbe1234, add1234, shufMask,
                        true, false, true, false, true, ghashinOffset + 16, aesoutOffset + 16, hashKey16);
        evmovdquq(masm, ZMM, aadHashx, ztmp4);
        masm.addl(pos, 16 * 16);
        masm.subl(len, 32 * 16);
        masm.jmp(encryptBigBlksNoHxor);

        masm.bind(encryptBigNblks);
        ghash16EncryptParallel16Avx512(crb, masm, in, out, ct, pos, avx512SubkeyHtbl, ctrCheck, rounds, key, ctrBlockx, aadHashx, addbe4x4, addbe1234, add1234, shufMask,
                        false, true, false, false, false, ghashinOffset, aesoutOffset, hashKey32);
        masm.addl(pos, 16 * 16);
        ghash16EncryptParallel16Avx512(crb, masm, in, out, ct, pos, avx512SubkeyHtbl, ctrCheck, rounds, key, ctrBlockx, aadHashx, addbe4x4, addbe1234, add1234, shufMask,
                        false, false, true, true, true, ghashinOffset + 16, aesoutOffset + 16, hashKey16);

        movdquXmm(masm, aadHashx, ztmp4);
        masm.addl(pos, 16 * 16);
        masm.subl(len, 32 * 16);

        masm.bind(noBigBlks);
        masm.cmplAndJcc(len, 16 * 16, ConditionFlag.AboveEqual, encrypt16Blks, false);

        masm.bind(encryptNGhash32NBlks);
        ghash16Avx512(crb, masm, true, false, false, false, true, in, pos, avx512SubkeyHtbl, aadHashx, shufMask, stackOffset, 0, 0, hashKey32);
        gcmEncDecLastAvx512(crb, masm, len, in, pos, aadHashx, shufMask, avx512SubkeyHtbl, ghashinOffset + 16, hashKey16, false, true);
        masm.jmp(ghashDone);

        masm.bind(encrypt16Blks);
        ghash16EncryptParallel16Avx512(crb, masm, in, out, ct, pos, avx512SubkeyHtbl, ctrCheck, rounds, key, ctrBlockx, aadHashx, addbe4x4, addbe1234, add1234, shufMask,
                        false, true, false, false, false, ghashinOffset, aesoutOffset, hashKey32);

        ghash16Avx512(crb, masm, false, true, false, false, true, in, pos, avx512SubkeyHtbl, aadHashx, shufMask, stackOffset, 16 * 16, 0, hashKey16);
        masm.addl(pos, 16 * 16);

        masm.bind(mesgBelow32Blks);
        masm.subl(len, 16 * 16);
        gcmEncDecLastAvx512(crb, masm, len, in, pos, aadHashx, shufMask, avx512SubkeyHtbl, ghashinOffset, hashKey16, true, true);

        masm.bind(ghashDone);
        // Pre-increment counter for next operation, make sure that counter value is incremented on the LSB
        evpshufb(masm, XMM, ctrBlockx, ctrBlockx, shufMask);
        masm.vpaddd(ctrBlockx, ctrBlockx, add1234, XMM);
        evpshufb(masm, XMM, ctrBlockx, ctrBlockx, shufMask);
        movdquXmm(masm, new AMD64Address(counter, 0), ctrBlockx);
        // Load ghash lswap mask
        movdquXmm(masm, xmm24, recordExternalAddress(crb, GHASH_LONG_SWAP_MASK));
        // Shuffle ghash using lbswap_mask and store it
        evpshufb(masm, XMM, aadHashx, aadHashx, xmm24);
        movdquXmm(masm, new AMD64Address(state, 0), aadHashx);

        // Zero out sensitive data
        evpxorq(masm, ZMM, ztmp21, ztmp21, ztmp21);
        evpxorq(masm, ZMM, ztmp0, ztmp0, ztmp0);
        evpxorq(masm, ZMM, ztmp1, ztmp1, ztmp1);
        evpxorq(masm, ZMM, ztmp2, ztmp2, ztmp2);
        evpxorq(masm, ZMM, ztmp3, ztmp3, ztmp3);

        masm.bind(encDecDone);
    }

    // AES-GCM interleaved implementation
    private void aesgcmAvx2(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register in, Register len, Register ct, Register out, Register key,
                    Register state, Register subkeyHtbl, Register counter) {
        Register pos = rax;
        Register rounds = r10;
        Register ctrBlockx = xmm9;
        Register aadHashx = xmm8;
        Label labelEncryptDone = new Label();
        Label labelEncryptBy8New = new Label();
        Label labelEncryptBy8 = new Label();
        Label labelExit = new Label();

        // This routine should be called only for message sizes of 128 bytes or more.
        // Macro flow:
        // process 8 16 byte blocks in initial_num_blocks.
        // process 8 16 byte blocks at a time until all are done 'encrypt_by_8_new followed by ghash_last_8'
        masm.xorl(pos, pos);
        masm.cmplAndJcc(len, 128, ConditionFlag.Less, labelExit, false);

        // Generate 8 constants for htbl
        generateHtbl8BlockAvx2(crb, masm, subkeyHtbl);

        // Compute #rounds for AES based on the length of the key array
        masm.movl(rounds, new AMD64Address(key, lengthOffset));

        // Load and shuffle state and counter values
        masm.movdqu(ctrBlockx, new AMD64Address(counter, 0));
        masm.movdqu(aadHashx, new AMD64Address(state, 0));
        masm.vpshufb(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK), XMM);
        masm.vpshufb(aadHashx, aadHashx, recordExternalAddress(crb, GHASH_LONG_SWAP_MASK), XMM);

        initialBlocksAvx2(crb, masm, ctrBlockx, rounds, key, len, in, out, ct, aadHashx, pos);

        // We need at least 128 bytes to proceed further.
        masm.cmplAndJcc(len, 128, ConditionFlag.Less, labelEncryptDone, false);

        // in_order vs. out_order is an optimization to increment the counter without shuffling
        // it back into little endian. r15d keeps track of when we need to increment in order so
        // that the carry is handled correctly.
        masm.movdl(r15, ctrBlockx);
        masm.andl(r15, 255);
        masm.vpshufb(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK), XMM);

        masm.bind(labelEncryptBy8New);
        masm.cmplAndJcc(r15, 255 - 8, ConditionFlag.Greater, labelEncryptBy8, false);

        masm.addb(r15, 8);
        ghash8Encrypt8ParallelAvx2(crb, masm, key, subkeyHtbl, ctrBlockx, in, out, ct, pos, false, rounds,
                        xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8);
        masm.addl(pos, 128);
        masm.subl(len, 128);
        masm.cmplAndJcc(len, 128, ConditionFlag.GreaterEqual, labelEncryptBy8New, false);

        masm.vpshufb(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK), XMM);
        masm.jmp(labelEncryptDone);

        masm.bind(labelEncryptBy8);
        masm.vpshufb(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK), XMM);

        masm.addb(r15, 8);
        ghash8Encrypt8ParallelAvx2(crb, masm, key, subkeyHtbl, ctrBlockx, in, out, ct, pos, true, rounds,
                        xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8);

        masm.vpshufb(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK), XMM);
        masm.addl(pos, 128);
        masm.subl(len, 128);
        masm.cmplAndJcc(len, 128, ConditionFlag.GreaterEqual, labelEncryptBy8New, false);
        masm.vpshufb(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK), XMM);

        masm.bind(labelEncryptDone);
        ghashLast8Avx2(crb, masm, subkeyHtbl);

        masm.vpaddd(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_MASK_LINC1), XMM);
        masm.vpshufb(ctrBlockx, ctrBlockx, recordExternalAddress(crb, COUNTER_SHUFFLE_MASK), XMM);
        // current_counter = xmm9
        masm.movdqu(new AMD64Address(counter, 0), ctrBlockx);
        masm.vpshufb(xmm14, xmm14, recordExternalAddress(crb, GHASH_LONG_SWAP_MASK), XMM);
        // aad hash = xmm14
        masm.movdqu(new AMD64Address(state, 0), xmm14);
        // Xor out round keys
        masm.vpxor(xmm0, xmm0, xmm0, XMM);
        masm.vpxor(xmm13, xmm13, xmm13, XMM);

        masm.bind(labelExit);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
