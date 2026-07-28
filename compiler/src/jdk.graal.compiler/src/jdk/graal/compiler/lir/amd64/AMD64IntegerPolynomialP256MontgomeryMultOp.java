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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQA64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPALIGNR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPANDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMADD52HUQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMADD52LUQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPAND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPANDN;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMADD52HUQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMADD52LUQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRAQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRLQ;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z1;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_IFMA;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX_IFMA;
import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.k4;
import static jdk.vm.ci.amd64.AMD64.k5;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
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
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/0b8bc780f976dab23ec798018fef0b674e885b0c/src/hotspot/cpu/x86/stubGenerator_x86_64_poly_mont.cpp#L26-L639",
          sha1 = "29bf46272e2dbc367e69a3faaceba8bfe89ea196")
// @formatter:on
public final class AMD64IntegerPolynomialP256MontgomeryMultOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64IntegerPolynomialP256MontgomeryMultOp> TYPE = LIRInstructionClass.create(AMD64IntegerPolynomialP256MontgomeryMultOp.class);

    private static final long MASK_52 = 0x000fffffffffffffL;

    private static final ArrayDataPointerConstant MODULUS_P256 = pointerConstant(64, new long[]{
                    0x000fffffffffffffL, 0x00000fffffffffffL,
                    0x0000000000000000L, 0x0000001000000000L,
                    0x0000ffffffff0000L, 0x0000000000000000L,
                    0x0000000000000000L, 0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant MODULUS_P256_HIGH = pointerConstant(32, new long[]{
                    0x00000fffffffffffL, 0x0000000000000000L,
                    0x0000001000000000L, 0x0000ffffffff0000L,
    });

    private static final ArrayDataPointerConstant P256_MASK52 = pointerConstant(64, new long[]{
                    MASK_52, MASK_52, MASK_52, MASK_52,
                    0xffffffffffffffffL, 0xffffffffffffffffL,
                    0xffffffffffffffffL, 0xffffffffffffffffL,
    });

    private static final ArrayDataPointerConstant SHIFT_1R = pointerConstant(64, new long[]{
                    0x0000000000000001L, 0x0000000000000002L,
                    0x0000000000000003L, 0x0000000000000004L,
                    0x0000000000000005L, 0x0000000000000006L,
                    0x0000000000000007L, 0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant SHIFT_1L = pointerConstant(64, new long[]{
                    0x0000000000000007L, 0x0000000000000000L,
                    0x0000000000000001L, 0x0000000000000002L,
                    0x0000000000000003L, 0x0000000000000004L,
                    0x0000000000000005L, 0x0000000000000006L,
    });

    private static final ArrayDataPointerConstant MASK_LIMB5 = pointerConstant(32, new long[]{
                    0xffffffffffffffffL, 0xffffffffffffffffL,
                    0xffffffffffffffffL, 0x0000000000000000L,
    });

    @Use private Value aValue;
    @Use private Value bValue;
    @Use private Value rValue;

    @Temp private Value[] temps;

    public AMD64IntegerPolynomialP256MontgomeryMultOp(LIRGeneratorTool tool,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    AllocatableValue a,
                    AllocatableValue b,
                    AllocatableValue r) {
        super(TYPE);
        this.aValue = a;
        this.bValue = b;
        this.rValue = r;
        GraalError.guarantee(a instanceof RegisterValue aReg && rdi.equals(aReg.getRegister()), "a should be fixed to rdi, but is %s", a);
        GraalError.guarantee(b instanceof RegisterValue bReg && rsi.equals(bReg.getRegister()), "b should be fixed to rsi, but is %s", b);
        GraalError.guarantee(r instanceof RegisterValue rReg && rdx.equals(rReg.getRegister()), "r should be fixed to rdx, but is %s", r);

        if (supports(tool.target(), runtimeCheckedCPUFeatures, AVX512_IFMA, AVX512VL, AVX512BW, AVX512F)) {
            this.temps = registersToValues(new Register[]{
                            r9,
                            xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7,
                            xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
                            k1, k2, k3, k4, k5,
            });
        } else {
            this.temps = registersToValues(new Register[]{
                            rax, rdx, rcx, r8, r9, r10, r11,
                            xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7,
                            xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
            });
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(aValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid aValue kind: %s", aValue);
        GraalError.guarantee(bValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid bValue kind: %s", bValue);
        GraalError.guarantee(rValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid rValue kind: %s", rValue);

        Register aLimbs = asRegister(aValue);
        Register bLimbs = asRegister(bValue);
        Register rLimbs = asRegister(rValue);

        if (masm.supports(AVX512_IFMA, AVX512VL, AVX512BW, AVX512F)) {
            montgomeryMultiplyAVX512(crb, masm, aLimbs, bLimbs, rLimbs, r9);
        } else {
            GraalError.guarantee(masm.supports(AVX_IFMA), "Require AVX_IFMA support");
            masm.push(r12);
            masm.push(r13);
            masm.push(r14);
            masm.push(rbp);
            masm.movq(rbp, rsp);
            masm.andq(rsp, -32);
            masm.subq(rsp, 32);

            masm.movq(r8, rLimbs);
            montgomeryMultiplyAVX2(crb, masm, aLimbs, bLimbs, r8, rax, rdx, r9, r10, r11, r12, r13, r14, rcx);

            masm.movq(rsp, rbp);
            masm.pop(rbp);
            masm.pop(r14);
            masm.pop(r13);
            masm.pop(r12);
        }
    }

    private static void montgomeryMultiplyAVX512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register aLimbs, Register bLimbs, Register rLimbs, Register t0) {
        Register a = xmm0;
        Register b = xmm1;
        Register t = xmm2;
        Register acc1 = xmm10;
        Register acc2 = xmm11;
        Register n = xmm12;
        Register carry = xmm13;
        Register modulus = xmm5;
        Register shift1L = xmm6;
        Register shift1R = xmm7;
        Register mask52 = xmm8;
        Register allLimbs = k1;
        Register limb0 = k2;
        Register[] masks = {limb0, k3, k4, k5};

        for (int i = 0; i < masks.length; i++) {
            masm.movq(t0, 1L << i);
            masm.kmovq(masks[i], t0);
        }

        masm.movq(t0, 0x1f);
        masm.kmovq(allLimbs, t0);
        // JVMCI-installed data sections guarantee at most 32-byte alignment, so ZMM constants need
        // unaligned loads even though the corresponding HotSpot stub uses aligned loads.
        EVMOVDQU64.emit(masm, AVXSize.ZMM, shift1L, recordExternalAddress(crb, SHIFT_1L), allLimbs, Z1, B0);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, shift1R, recordExternalAddress(crb, SHIFT_1R), allLimbs, Z1, B0);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, mask52, recordExternalAddress(crb, P256_MASK52), allLimbs, Z1, B0);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, modulus, recordExternalAddress(crb, MODULUS_P256), allLimbs, Z1, B0);

        EVMOVDQU64.emit(masm, AVXSize.YMM, a, new AMD64Address(aLimbs, 8));
        AMD64Assembler.VexRVMOp.EVPERMQ.emit(masm, AVXSize.ZMM, a, shift1L, a, allLimbs, Z1, B0);
        VMOVQ.emit(masm, AVXSize.XMM, t, new AMD64Address(aLimbs, 0));
        EVPORQ.emit(masm, AVXSize.ZMM, a, a, t);

        EVPXORQ.emit(masm, AVXSize.ZMM, acc1, acc1, acc1);
        for (int i = 0; i < 5; i++) {
            EVPXORQ.emit(masm, AVXSize.ZMM, acc2, acc2, acc2);
            EVPBROADCASTQ.emit(masm, AVXSize.ZMM, b, new AMD64Address(bLimbs, i * 8));

            EVPMADD52LUQ.emit(masm, AVXSize.ZMM, acc1, a, b);
            EVPMADD52HUQ.emit(masm, AVXSize.ZMM, acc2, a, b);
            EVPBROADCASTQ.emit(masm, AVXSize.ZMM, n, acc1);
            EVPMADD52LUQ.emit(masm, AVXSize.ZMM, acc1, modulus, n);
            EVPMADD52HUQ.emit(masm, AVXSize.ZMM, acc2, modulus, n);

            EVPSRLQ.emit(masm, AVXSize.ZMM, carry, acc1, 52, limb0, Z0, B0);
            EVPADDQ.emit(masm, AVXSize.ZMM, acc2, carry, acc2, limb0);
            AMD64Assembler.VexRVMOp.EVPERMQ.emit(masm, AVXSize.ZMM, acc1, shift1R, acc1, allLimbs, Z1, B0);
            EVPADDQ.emit(masm, AVXSize.ZMM, acc1, acc1, acc2);
        }

        Register acc1L = a;
        Register acc2L = b;
        EVPSUBQ.emit(masm, AVXSize.ZMM, acc2, acc1, modulus);

        EVPSRAQ.emit(masm, AVXSize.YMM, carry, acc2, 52, limb0, Z1, B0);
        EVPANDQ.emit(masm, AVXSize.YMM, acc2L, acc2, mask52, limb0, Z1, B0);
        AMD64Assembler.VexRVMOp.EVPERMQ.emit(masm, AVXSize.ZMM, acc2, shift1R, acc2, allLimbs, Z1, B0);
        VPADDQ.emit(masm, AVXSize.YMM, acc2, acc2, carry);

        EVPSRAQ.emit(masm, AVXSize.YMM, carry, acc1, 52, limb0, Z1, B0);
        EVPANDQ.emit(masm, AVXSize.YMM, acc1L, acc1, mask52, limb0, Z1, B0);
        AMD64Assembler.VexRVMOp.EVPERMQ.emit(masm, AVXSize.ZMM, acc1, shift1R, acc1, allLimbs, Z1, B0);
        VPADDQ.emit(masm, AVXSize.YMM, acc1, acc1, carry);

        for (int i = 1; i < 4; i++) {
            EVPSRAQ.emit(masm, AVXSize.YMM, carry, acc2, 52, masks[i - 1], Z1, B0);
            if (i == 1 || i == 3) {
                VPALIGNR.emit(masm, AVXSize.YMM, carry, carry, carry, 8);
            } else {
                VPERMQ.emit(masm, AVXSize.YMM, carry, carry, 0b10010011);
            }
            VPADDQ.emit(masm, AVXSize.YMM, acc2, acc2, carry);

            EVPSRAQ.emit(masm, AVXSize.YMM, carry, acc1, 52, masks[i - 1], Z1, B0);
            if (i == 1 || i == 3) {
                VPALIGNR.emit(masm, AVXSize.YMM, carry, carry, carry, 8);
            } else {
                VPERMQ.emit(masm, AVXSize.YMM, carry, carry, 0b10010011);
            }
            VPADDQ.emit(masm, AVXSize.YMM, acc1, acc1, carry);
        }

        EVPSRAQ.emit(masm, AVXSize.YMM, carry, acc2, 64);
        VPERMQ.emit(masm, AVXSize.YMM, carry, carry, 0xff);
        EVPANDQ.emit(masm, AVXSize.YMM, acc1, acc1, mask52);
        EVPANDQ.emit(masm, AVXSize.YMM, acc2, acc2, mask52);

        VPANDN.emit(masm, AVXSize.YMM, acc2L, carry, acc2L);
        EVPTERNLOGQ.emit(masm, AVXSize.YMM, acc2L, carry, acc1L, 0xf8);
        VPANDN.emit(masm, AVXSize.YMM, acc2, carry, acc2);
        EVPTERNLOGQ.emit(masm, AVXSize.YMM, acc2, carry, acc1, 0xf8);

        VMOVQ.emit(masm, AVXSize.XMM, new AMD64Address(rLimbs, 0), acc2L);
        EVMOVDQU64.emit(masm, AVXSize.YMM, new AMD64Address(rLimbs, 8), acc2);
        masm.vzeroall();
    }

    private static void montgomeryMultiplyAVX2(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register aLimbs, Register bLimbs, Register rLimbs,
                    Register tmpRax, Register tmpRdx, Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register tmp6, Register tmp7) {
        Register a = tmp1;
        Register vecA = xmm0;
        Register vecB = xmm1;
        Register acc1 = tmp2;
        Register vecAcc1 = xmm3;
        Register acc2 = tmp3;
        Register vecAcc2 = xmm4;
        Register vecN = xmm5;
        Register modulus = tmp4;
        Register vecModulus = xmm7;
        Register mask52 = tmp5;
        Register vecMask52 = xmm8;
        Register vecMaskLimb5 = xmm9;
        Register zero = xmm10;

        masm.movq(mask52, MASK_52);
        VMOVQ.emit(masm, AVXSize.XMM, vecMask52, mask52);
        VPBROADCASTQ.emit(masm, AVXSize.YMM, vecMask52, vecMask52);
        VMOVDQA64.emit(masm, AVXSize.YMM, vecMaskLimb5, recordExternalAddress(crb, MASK_LIMB5));
        masm.vpxor(zero, zero, zero, AVXSize.YMM);

        masm.movq(modulus, mask52);
        VMOVDQU64.emit(masm, AVXSize.YMM, vecModulus, recordExternalAddress(crb, MODULUS_P256_HIGH));

        masm.movq(a, new AMD64Address(aLimbs, 0));
        VMOVDQU64.emit(masm, AVXSize.YMM, vecA, new AMD64Address(aLimbs, 8));

        masm.vpxor(vecAcc1, vecAcc1, vecAcc1, AVXSize.YMM);
        for (int i = 0; i < 5; i++) {
            masm.vpxor(vecAcc2, vecAcc2, vecAcc2, AVXSize.YMM);
            masm.movq(tmpRax, new AMD64Address(bLimbs, i * 8));
            VPBROADCASTQ.emit(masm, AVXSize.YMM, vecB, new AMD64Address(bLimbs, i * 8));

            masm.mulq(a);
            if (i == 0) {
                masm.movq(acc1, tmpRax);
                masm.movq(acc2, tmpRdx);
            } else {
                masm.xorq(acc2, acc2);
                masm.addq(acc1, tmpRax);
                masm.adcq(acc2, tmpRdx);
            }
            VPMADD52LUQ.emit(masm, AVXSize.YMM, vecAcc1, vecA, vecB);
            VPMADD52HUQ.emit(masm, AVXSize.YMM, vecAcc2, vecA, vecB);

            if (i != 0) {
                masm.movq(tmpRax, acc1);
            }
            masm.andq(tmpRax, mask52);
            VMOVQ.emit(masm, AVXSize.XMM, vecN, acc1);
            VPBROADCASTQ.emit(masm, AVXSize.YMM, vecN, vecN);

            masm.mulq(modulus);
            VPMADD52LUQ.emit(masm, AVXSize.YMM, vecAcc1, vecModulus, vecN);
            masm.addq(acc1, tmpRax);
            masm.adcq(acc2, tmpRdx);
            VPMADD52HUQ.emit(masm, AVXSize.YMM, vecAcc2, vecModulus, vecN);

            masm.shrq(acc1, 52);
            masm.shlq(acc2, 12);
            masm.addq(acc2, acc1);

            masm.movdq(acc1, vecAcc1);
            VPERMQ.emit(masm, AVXSize.YMM, vecAcc1, vecAcc1, 0b11111001);
            VPAND.emit(masm, AVXSize.YMM, vecAcc1, vecAcc1, vecMaskLimb5);

            masm.addq(acc1, acc2);
            VPADDQ.emit(masm, AVXSize.YMM, vecAcc1, vecAcc1, vecAcc2);
        }

        masm.movq(acc2, acc1);
        masm.subq(acc2, modulus);
        VPSUBQ.emit(masm, AVXSize.YMM, vecAcc2, vecAcc1, vecModulus);
        VMOVDQA64.emit(masm, AVXSize.YMM, new AMD64Address(rsp, 0), vecAcc2);

        Register[] limb = {acc2, tmp1, tmp4, tmpRdx, tmp6};
        Register carry = tmpRax;
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                masm.movq(limb[i], new AMD64Address(rsp, -8 + i * 8));
                masm.addq(limb[i], carry);
            }
            masm.movq(carry, limb[i]);
            if (i == 4) {
                break;
            }
            masm.sarq(carry, 52);
        }
        masm.sarq(carry, 63);
        masm.notq(carry);
        Register select = carry;
        carry = tmp7;

        Register digit = acc1;
        VMOVDQA64.emit(masm, AVXSize.YMM, new AMD64Address(rsp, 0), vecAcc1);

        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                masm.movq(digit, new AMD64Address(rsp, -8 + i * 8));
                masm.addq(digit, carry);
            }
            masm.movq(carry, digit);
            masm.sarq(carry, 52);

            masm.xorq(limb[i], digit);
            masm.andq(limb[i], select);
            masm.xorq(digit, limb[i]);

            masm.andq(digit, mask52);
            masm.movq(new AMD64Address(rLimbs, i * 8), digit);
        }

        masm.vzeroall();
        masm.vpxor(vecAcc1, vecAcc1, vecAcc1, AVXSize.YMM);
        VMOVDQA64.emit(masm, AVXSize.YMM, new AMD64Address(rsp, 0), vecAcc1);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
