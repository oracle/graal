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
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ_XMM_MEM;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTQ_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTI32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VINSERTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPANDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPUNPCKHQDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPUNPCKLQDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPAND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPUNPCKHQDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPUNPCKLQDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftImmOp.EVPSLLDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSLLQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRLQ;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z1;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.rbp;
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

import jdk.graal.compiler.asm.Label;
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
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/stubGenerator_x86_64_poly1305.cpp#L203-L1697",
          sha1 = "4173314ea643beea5c377408be73eebd4291b7fc")
// @formatter:on
public final class AMD64Poly1305ProcessBlocksOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64Poly1305ProcessBlocksOp> TYPE = LIRInstructionClass.create(AMD64Poly1305ProcessBlocksOp.class);

    private static final long MASK_44 = 0x00000fffffffffffL;
    private static final long MASK_42 = 0x000003ffffffffffL;
    private static final long PAD_MSG = 0x0000010000000000L;

    private static final ArrayDataPointerConstant POLY1305_PAD_MSG = pointerConstant(16, new long[]{
                    PAD_MSG, PAD_MSG, PAD_MSG, PAD_MSG,
                    PAD_MSG, PAD_MSG, PAD_MSG, PAD_MSG,
    });

    private static final ArrayDataPointerConstant POLY1305_MASK42 = pointerConstant(16, new long[]{
                    MASK_42, MASK_42, MASK_42, MASK_42,
                    MASK_42, MASK_42, MASK_42, MASK_42,
    });

    private static final ArrayDataPointerConstant POLY1305_MASK44 = pointerConstant(16, new long[]{
                    MASK_44, MASK_44, MASK_44, MASK_44,
                    MASK_44, MASK_44, MASK_44, MASK_44,
    });

    @Use private Value inputValue;
    @Use private Value lengthValue;
    @Use private Value accumulatorValue;
    @Use private Value rValue;

    @Temp private Value[] temps;

    public AMD64Poly1305ProcessBlocksOp(LIRGeneratorTool tool,
                    java.util.EnumSet<AMD64.CPUFeature> runtimeCheckedCPUFeatures,
                    AllocatableValue input,
                    AllocatableValue length,
                    AllocatableValue accumulator,
                    AllocatableValue r) {
        super(TYPE);
        this.inputValue = input;
        this.lengthValue = length;
        this.accumulatorValue = accumulator;
        this.rValue = r;
        GraalError.guarantee(input instanceof RegisterValue inputReg && rdi.equals(inputReg.getRegister()), "input should be fixed to rdi, but is %s", input);
        GraalError.guarantee(length instanceof RegisterValue lengthReg && rbx.equals(lengthReg.getRegister()), "length should be fixed to rbx, but is %s", length);
        GraalError.guarantee(accumulator instanceof RegisterValue accumulatorReg && rcx.equals(accumulatorReg.getRegister()), "accumulator should be fixed to rcx, but is %s", accumulator);
        GraalError.guarantee(r instanceof RegisterValue rReg && r8.equals(rReg.getRegister()), "r should be fixed to r8, but is %s", r);

        if (AMD64ComplexVectorOp.supports(tool.target(), runtimeCheckedCPUFeatures, AMD64.CPUFeature.AVX512_IFMA,
                        AMD64.CPUFeature.AVX512VL, AMD64.CPUFeature.AVX512BW, AMD64.CPUFeature.AVX512F)) {
            this.temps = new Value[]{
                            rax.asValue(),
                            rdx.asValue(),
                            rbx.asValue(),
                            rsi.asValue(),
                            rdi.asValue(),
                            r8.asValue(),
                            r9.asValue(),
                            r10.asValue(),
                            r11.asValue(),
                            xmm0.asValue(),
                            xmm1.asValue(),
                            xmm2.asValue(),
                            xmm3.asValue(),
                            xmm4.asValue(),
                            xmm5.asValue(),
                            xmm6.asValue(),
                            xmm7.asValue(),
                            xmm8.asValue(),
                            xmm9.asValue(),
                            xmm10.asValue(),
                            xmm11.asValue(),
                            xmm12.asValue(),
                            xmm13.asValue(),
                            xmm14.asValue(),
                            xmm15.asValue(),
                            xmm16.asValue(),
                            xmm17.asValue(),
                            xmm18.asValue(),
                            xmm19.asValue(),
                            xmm20.asValue(),
                            xmm21.asValue(),
                            xmm22.asValue(),
                            xmm23.asValue(),
                            xmm24.asValue(),
                            xmm25.asValue(),
                            xmm26.asValue(),
                            xmm27.asValue(),
                            xmm28.asValue(),
                            xmm29.asValue(),
                            xmm30.asValue(),
                            xmm31.asValue(),
                            k1.asValue(),
            };
        } else if (AMD64ComplexVectorOp.supports(tool.target(), runtimeCheckedCPUFeatures, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX_IFMA)) {
            this.temps = new Value[]{
                            rax.asValue(),
                            rdx.asValue(),
                            rbx.asValue(),
                            rsi.asValue(),
                            rdi.asValue(),
                            r8.asValue(),
                            r9.asValue(),
                            r10.asValue(),
                            r11.asValue(),
                            xmm0.asValue(),
                            xmm1.asValue(),
                            xmm2.asValue(),
                            xmm3.asValue(),
                            xmm4.asValue(),
                            xmm5.asValue(),
                            xmm6.asValue(),
                            xmm7.asValue(),
                            xmm8.asValue(),
                            xmm9.asValue(),
                            xmm10.asValue(),
                            xmm11.asValue(),
                            xmm12.asValue(),
                            xmm13.asValue(),
                            xmm14.asValue(),
                            xmm15.asValue(),
            };
        } else {
            this.temps = new Value[]{
                            rax.asValue(),
                            rdx.asValue(),
                            rbx.asValue(),
                            rsi.asValue(),
                            rdi.asValue(),
                            r8.asValue(),
                            r9.asValue(),
                            r10.asValue(),
                            r11.asValue(),
            };
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(inputValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid inputValue kind: %s", inputValue);
        GraalError.guarantee(lengthValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lengthValue kind: %s", lengthValue);
        GraalError.guarantee(accumulatorValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid accumulatorValue kind: %s", accumulatorValue);
        GraalError.guarantee(rValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid rValue kind: %s", rValue);

        Register input = asRegister(inputValue);
        Register length = asRegister(lengthValue);
        Register accumulator = asRegister(accumulatorValue);
        Register r = asRegister(rValue);

        Register a0 = rsi;
        Register a1 = r9;
        Register a2 = r10;
        Register r0 = r11;
        Register r1 = r12;
        Register c1 = r8;
        Register t0 = r13;
        Register t1 = r14;
        Register t2 = r15;
        Register mulql = rax;
        Register mulqh = rdx;

        Label process16Loop = new Label();
        Label process16LoopDone = new Label();
        Label skipVector = new Label();

        masm.push(r12);
        masm.push(r13);
        masm.push(r14);
        masm.push(r15);

        poly1305Limbs(masm, r, r0, r1, Register.None, t0, t1);

        masm.movq(c1, r1);
        masm.shrq(c1, 2);
        masm.addq(c1, r1);

        poly1305Limbs(masm, accumulator, a0, a1, a2, t0, t1);

        if (masm.supports(AMD64.CPUFeature.AVX512_IFMA) &&
                        masm.supports(AMD64.CPUFeature.AVX512VL) &&
                        masm.supports(AMD64.CPUFeature.AVX512BW) &&
                        masm.supports(AMD64.CPUFeature.AVX512F)) {
            masm.cmplAndJcc(length, 16 * 16, AMD64Assembler.ConditionFlag.Less, skipVector, false);
            poly1305ProcessBlocksAVX512(masm, crb, input, length, a0, a1, a2, r0, r1, c1, t0, t1, t2, mulql, mulqh);
            masm.bind(skipVector);
        } else if (masm.supports(AMD64.CPUFeature.AVX2) && masm.supports(AMD64.CPUFeature.AVX_IFMA)) {
            masm.cmplAndJcc(length, 16 * 16, AMD64Assembler.ConditionFlag.Less, skipVector, false);
            poly1305ProcessBlocksAVX2(masm, crb, input, length, a0, a1, a2, r0, r1, c1, t0, t1, t2, mulql, mulqh);
            masm.bind(skipVector);
        }

        masm.bind(process16Loop);
        masm.cmplAndJcc(length, 16, AMD64Assembler.ConditionFlag.Less, process16LoopDone, false);

        masm.addq(a0, new AMD64Address(input, 0));
        masm.adcq(a1, new AMD64Address(input, 8));
        masm.adcq(a2, 1);
        poly1305MultiplyScalar(masm, a0, a1, a2, r0, r1, c1, false, t0, t1, t2, mulql, mulqh);

        masm.subl(length, 16);
        masm.leaq(input, new AMD64Address(input, 16));
        masm.jmp(process16Loop);

        masm.bind(process16LoopDone);
        poly1305LimbsOut(masm, a0, a1, a2, accumulator, t0, t1);

        masm.pop(r15);
        masm.pop(r14);
        masm.pop(r13);
        masm.pop(r12);
    }

    // @formatter:off
    // This function consumes as many whole 16*16-byte blocks as available in input
    // After execution, input and length will point at remaining (unprocessed) data
    // and [a2 a1 a0] will contain the current accumulator value
    //
    // Math Note:
    //    Main loop in this function multiplies each message block by r^16; And some glue before and after..
    //    Proof (for brevity, split into 4 'rows' instead of 16):
    //
    //     hash = ((((m1*r + m2)*r + m3)*r ...  mn)*r
    //          = m1*r^n + m2*r^(n-1) + ... +mn_1*r^2 + mn*r  // Horner's rule
    //
    //          = m1*r^n     + m4*r^(n-4) + m8*r^(n-8) ...    // split into 4 groups for brevity, same applies to 16 blocks
    //          + m2*r^(n-1) + m5*r^(n-5) + m9*r^(n-9) ...
    //          + m3*r^(n-2) + m6*r^(n-6) + m10*r^(n-10) ...
    //          + m4*r^(n-3) + m7*r^(n-7) + m11*r^(n-11) ...
    //
    //          = r^4 * (m1*r^(n-4) + m4*r^(n-8) + m8 *r^(n-16) ... + mn_3)   // factor out r^4..r; same applies to 16 but r^16..r factors
    //          + r^3 * (m2*r^(n-4) + m5*r^(n-8) + m9 *r^(n-16) ... + mn_2)
    //          + r^2 * (m3*r^(n-4) + m6*r^(n-8) + m10*r^(n-16) ... + mn_1)
    //          + r^1 * (m4*r^(n-4) + m7*r^(n-8) + m11*r^(n-16) ... + mn_0)   // Note last column: message group has no multiplier
    //
    //          = (((m1*r^4 + m4)*r^4 + m8 )*r^4 ... + mn_3) * r^4   // reverse Horner's rule, for each group
    //          + (((m2*r^4 + m5)*r^4 + m9 )*r^4 ... + mn_2) * r^3   // each column is multiplied by r^4, except last
    //          + (((m3*r^4 + m6)*r^4 + m10)*r^4 ... + mn_1) * r^2
    //          + (((m4*r^4 + m7)*r^4 + m11)*r^4 ... + mn_0) * r^1
    //
    // Also see M. Goll and S. Gueron, "Vectorization of Poly1305 Message Authentication Code"
    //
    // Pseudocode:
    //  * used for poly1305_multiply_scalar
    //  x used for poly1305_multiply8_avx512
    //  lower-case variables are scalar numbers in 3x44-bit limbs (in gprs)
    //  upper-case variables are 8&16-element vector numbers in 3x44-bit limbs (in zmm registers)
    //
    //    CL = a       // [0 0 0 0 0 0 0 a]
    //    AL = poly1305_limbs_avx512(input)
    //    AH = poly1305_limbs_avx512(input+8)
    //    AL = AL + C
    //    input+=16, length-=16
    //
    //    a = r
    //    a = a*r
    //  r^2 = a
    //    a = a*r
    //  r^3 = a
    //    r = a*r
    //  r^4 = a
    //
    //    T  = r^4 || r^3 || r^2 || r
    //    B  = limbs(T)           // [r^4  0  r^3  0  r^2  0  r^1  0 ]
    //    CL = B >> 1             // [ 0  r^4  0  r^3  0  r^2  0  r^1]
    //    R  = r^4 || r^4 || ..   // [r^4 r^4 r^4 r^4 r^4 r^4 r^4 r^4]
    //    B  = BxR                // [r^8  0  r^7  0  r^6  0  r^5  0 ]
    //    B  = B | CL             // [r^8 r^4 r^7 r^3 r^6 r^2 r^5 r^1]
    //    CL = B
    //    R  = r^8 || r^8 || ..   // [r^8 r^8 r^8 r^8 r^8 r^8 r^8 r^8]
    //    B  = B x R              // [r^16 r^12 r^15 r^11 r^14 r^10 r^13 r^9]
    //    CH = B
    //    R = r^16 || r^16 || ..  // [r^16 r^16 r^16 r^16 r^16 r^16 r^16 r^16]
    //
    // for (;length>=16; input+=16, length-=16)
    //     BL = poly1305_limbs_avx512(input)
    //     BH = poly1305_limbs_avx512(input+8)
    //     AL = AL x R
    //     AH = AH x R
    //     AL = AL + BL
    //     AH = AH + BH
    //
    //  AL = AL x CL
    //  AH = AH x CH
    //  A = AL + AH // 16->8 blocks
    //  T = A >> 4  // 8 ->4 blocks
    //  A = A + T
    //  T = A >> 2  // 4 ->2 blocks
    //  A = A + T
    //  T = A >> 1  // 2 ->1 blocks
    //  A = A + T
    //  a = A
    //
    // Register Map:
    // GPRs:
    //   input        = rdi
    //   length       = rbx
    //   accumulator  = rcx
    //   r   = r8
    //   a0  = rsi
    //   a1  = r9
    //   a2  = r10
    //   r0  = r11
    //   r1  = r12
    //   c1  = r8;
    //   t0  = r13
    //   t1  = r14
    //   t2  = r15
    //   stack(rsp, rbp)
    //   mulq(rax, rdx) in poly1305_multiply_scalar
    //
    // ZMMs:
    //   data0/data1 = xmm0-1
    //   tmp         = xmm2
    //   vecT0-vecT5 = xmm3-8
    //   vecA0-vecA5 = xmm9-14
    //   vecB0-vecB5 = xmm15-20
    //   vecC0-vecC5 = xmm21-26
    //   vecR0-vecR2, vecR1P, vecR2P = xmm27-31
    // @formatter:on
    private static void poly1305ProcessBlocksAVX512(AMD64MacroAssembler masm,
                    CompilationResultBuilder crb,
                    Register input,
                    Register length,
                    Register a0,
                    Register a1,
                    Register a2,
                    Register r0,
                    Register r1,
                    Register c1,
                    Register t0,
                    Register t1,
                    Register t2,
                    Register mulql,
                    Register mulqh) {
        AMD64Assembler.AMD64SIMDInstructionEncoding oldEncoding = masm.setTemporaryAvxEncoding(EVEX);
        Label labelLProcess256Loop = new Label();
        Label labelLProcess256LoopDone = new Label();

        Register data0 = xmm0;
        Register data1 = xmm1;
        Register tmp = xmm2;

        Register vecT0 = xmm3;
        Register vecT1 = xmm4;
        Register vecT2 = xmm5;
        Register vecT3 = xmm6;
        Register vecT4 = xmm7;
        Register vecT5 = xmm8;

        Register vecA0 = xmm9;
        Register vecA1 = xmm10;
        Register vecA2 = xmm11;
        Register vecA3 = xmm12;
        Register vecA4 = xmm13;
        Register vecA5 = xmm14;

        Register vecB0 = xmm15;
        Register vecB1 = xmm16;
        Register vecB2 = xmm17;
        Register vecB3 = xmm18;
        Register vecB4 = xmm19;
        Register vecB5 = xmm20;

        Register vecC0 = xmm21;
        Register vecC1 = xmm22;
        Register vecC2 = xmm23;
        Register vecC3 = xmm24;
        Register vecC4 = xmm25;
        Register vecC5 = xmm26;

        Register vecR0 = xmm27;
        Register vecR1 = xmm28;
        Register vecR2 = xmm29;
        Register vecR1P = xmm30;
        Register vecR2P = xmm31;

        // Spread accumulator into 44-bit limbs in quadwords vecC0, vecC1, vecC2
        masm.movq(t0, a0);
        // First limb (Acc[43:0])
        masm.andq(t0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.movdq(vecC0, t0);

        masm.movq(t0, a1);
        masm.shrdq(a0, t0, 44);
        // Second limb (Acc[77:52])
        masm.andq(a0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.movdq(vecC1, a0);

        masm.shrdq(a1, a2, 24);
        // Third limb (Acc[129:88])
        masm.andq(a1, recordExternalAddress(crb, POLY1305_MASK42));
        masm.movdq(vecC2, a1);

        // To add accumulator, we must unroll first loop iteration

        // Load first block of data (128 bytes) and pad
        // vecA0 to have bits 0-43 of all 8 blocks in 8 qwords
        // vecA1 to have bits 87-44 of all 8 blocks in 8 qwords
        // vecA2 to have bits 127-88 of all 8 blocks in 8 qwords
        masm.evmovdqu64(data0, new AMD64Address(input, 0));
        masm.evmovdqu64(data1, new AMD64Address(input, 64));
        poly1305LimbsAVX512(crb, masm, data0, data1, vecA0, vecA1, vecA2, true, tmp);

        // Add accumulator to the fist message block
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA0, vecA0, vecC0);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA1, vecA1, vecC1);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA2, vecA2, vecC2);

        // Load next blocks of data (128 bytes) and pad
        // vecA3 to have bits 0-43 of all 8 blocks in 8 qwords
        // vecA4 to have bits 87-44 of all 8 blocks in 8 qwords
        // vecA5 to have bits 127-88 of all 8 blocks in 8 qwords
        masm.evmovdqu64(data0, new AMD64Address(input, 64 * 2));
        masm.evmovdqu64(data1, new AMD64Address(input, 64 * 3));
        poly1305LimbsAVX512(crb, masm, data0, data1, vecA3, vecA4, vecA5, true, tmp);

        masm.subl(length, 16 * 16);
        masm.leaq(input, new AMD64Address(input, 16 * 16));

        // Compute the powers of R^1..R^4 and form 44-bit limbs of each
        // vecT0 to have bits 0-127 in 4 quadword pairs
        // vecT1 to have bits 128-129 in alternating 8 qwords
        EVPXORQ.emit(masm, AVXSize.ZMM, vecT1, vecT1, vecT1);
        masm.movdq(vecT2, r0);
        VPINSRQ.emit(masm, AVXSize.XMM, vecT2, vecT2, r1, 1);
        EVINSERTI32X4.emit(masm, AVXSize.ZMM, vecT0, vecT0, vecT2, 3);

        // Calculate R^2
        masm.movq(a0, r0);
        masm.movq(a1, r1);
        // "Clever": a2 not set because poly1305_multiply_scalar has a flag to indicate 128-bit
        // accumulator
        poly1305MultiplyScalar(masm, a0, a1, a2, r0, r1, c1, true, t0, t1, t2, mulql, mulqh);

        masm.movdq(vecT2, a0);
        VPINSRQ.emit(masm, AVXSize.XMM, vecT2, vecT2, a1, 1);
        EVINSERTI32X4.emit(masm, AVXSize.ZMM, vecT0, vecT0, vecT2, 2);
        masm.movdq(vecT2, a2);
        EVINSERTI32X4.emit(masm, AVXSize.ZMM, vecT1, vecT1, vecT2, 2);

        // Calculate R^3
        poly1305MultiplyScalar(masm, a0, a1, a2, r0, r1, c1, false, t0, t1, t2, mulql, mulqh);

        masm.movdq(vecT2, a0);
        VPINSRQ.emit(masm, AVXSize.XMM, vecT2, vecT2, a1, 1);
        EVINSERTI32X4.emit(masm, AVXSize.ZMM, vecT0, vecT0, vecT2, 1);
        masm.movdq(vecT2, a2);
        EVINSERTI32X4.emit(masm, AVXSize.ZMM, vecT1, vecT1, vecT2, 1);

        // Calculate R^4
        poly1305MultiplyScalar(masm, a0, a1, a2, r0, r1, c1, false, t0, t1, t2, mulql, mulqh);

        masm.movdq(vecT2, a0);
        VPINSRQ.emit(masm, AVXSize.XMM, vecT2, vecT2, a1, 1);
        EVINSERTI32X4.emit(masm, AVXSize.ZMM, vecT0, vecT0, vecT2, 0);
        masm.movdq(vecT2, a2);
        EVINSERTI32X4.emit(masm, AVXSize.ZMM, vecT1, vecT1, vecT2, 0);

        // Interleave the powers of R^1..R^4 to form 44-bit limbs (half-empty)
        // vecB0 to have bits 0-43 of all 4 blocks in alternating 8 qwords
        // vecB1 to have bits 87-44 of all 4 blocks in alternating 8 qwords
        // vecB2 to have bits 127-88 of all 4 blocks in alternating 8 qwords
        EVPXORQ.emit(masm, AVXSize.ZMM, vecT2, vecT2, vecT2);
        poly1305LimbsAVX512(crb, masm, vecT0, vecT2, vecB0, vecB1, vecB2, false, tmp);

        // vecT1 contains the 2 highest bits of the powers of R
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecT1, vecT1, 40);
        EVPORQ.emit(masm, AVXSize.ZMM, vecB2, vecB2, vecT1);

        // Broadcast 44-bit limbs of R^4 into vecR0, vecR1, vecR2
        masm.movq(t0, a0);
        // First limb (R^4[43:0])
        masm.andq(t0, recordExternalAddress(crb, POLY1305_MASK44));
        EVPBROADCASTQ_GPR.emit(masm, AVXSize.ZMM, vecR0, t0);

        masm.movq(t0, a1);
        masm.shrdq(a0, t0, 44);
        // Second limb (R^4[87:44])
        masm.andq(a0, recordExternalAddress(crb, POLY1305_MASK44));
        EVPBROADCASTQ_GPR.emit(masm, AVXSize.ZMM, vecR1, a0);

        masm.shrdq(a1, a2, 24);
        // Third limb (R^4[129:88])
        masm.andq(a1, recordExternalAddress(crb, POLY1305_MASK42));
        EVPBROADCASTQ_GPR.emit(masm, AVXSize.ZMM, vecR2, a1);

        // Generate 4*5*R^4 into {vecR2P, vecR1P}
        // Used as multiplier in poly1305_multiply8_avx512 so can
        // ignore bottom limb and carry propagation
        // 4*R^4
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2, 2);
        // 5*R^4
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, vecR1);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, vecR2);
        // 4*5*R^4
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, 2);

        // Move R^4..R^1 one element over
        EVPSLLDQ.emit(masm, AVXSize.ZMM, vecC0, vecB0, 8);
        EVPSLLDQ.emit(masm, AVXSize.ZMM, vecC1, vecB1, 8);
        EVPSLLDQ.emit(masm, AVXSize.ZMM, vecC2, vecB2, 8);

        // Calculate R^8-R^5
        poly1305Multiply8AVX512(crb, masm, vecB0, vecB1, vecB2,
                        vecR0, vecR1, vecR2, vecR1P, vecR2P,
                        vecT0, vecT1, vecT2, vecT3, vecT4, vecT5, tmp);

        // Interleave powers of R: R^8 R^4 R^7 R^3 R^6 R^2 R^5 R
        EVPORQ.emit(masm, AVXSize.ZMM, vecB0, vecB0, vecC0);
        EVPORQ.emit(masm, AVXSize.ZMM, vecB1, vecB1, vecC1);
        EVPORQ.emit(masm, AVXSize.ZMM, vecB2, vecB2, vecC2);

        // Store R^8-R for later use
        EVMOVDQU64.emit(masm, AVXSize.ZMM, vecC0, vecB0);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, vecC1, vecB1);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, vecC2, vecB2);

        // Broadcast R^8
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, vecR0, vecB0);
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, vecR1, vecB1);
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, vecR2, vecB2);

        // Generate 4*5*R^8
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2, 2);
        // 5*R^8
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, vecR1);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, vecR2);
        // 4*5*R^8
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, 2);

        // Calculate R^16-R^9
        poly1305Multiply8AVX512(crb, masm, vecB0, vecB1, vecB2,
                        vecR0, vecR1, vecR2, vecR1P, vecR2P,
                        vecT0, vecT1, vecT2, vecT3, vecT4, vecT5, tmp);

        // Store R^16-R^9 for later use
        EVMOVDQU64.emit(masm, AVXSize.ZMM, vecC3, vecB0);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, vecC4, vecB1);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, vecC5, vecB2);

        // Broadcast R^16
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, vecR0, vecB0);
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, vecR1, vecB1);
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, vecR2, vecB2);

        // Generate 4*5*R^16
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2, 2);
        // 5*R^16
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, vecR1);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, vecR2);
        // 4*5*R^16
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, 2);

        // VECTOR LOOP: process 16 * 16-byte message block at a time
        masm.bind(labelLProcess256Loop);
        masm.cmplAndJcc(length, 16 * 16, AMD64Assembler.ConditionFlag.Less, labelLProcess256LoopDone, false);

        // Load and interleave next block of data (128 bytes)
        masm.evmovdqu64(data0, new AMD64Address(input, 0));
        masm.evmovdqu64(data1, new AMD64Address(input, 64));
        poly1305LimbsAVX512(crb, masm, data0, data1, vecB0, vecB1, vecB2, true, tmp);

        // Load and interleave next block of data (128 bytes)
        masm.evmovdqu64(data0, new AMD64Address(input, 64 * 2));
        masm.evmovdqu64(data1, new AMD64Address(input, 64 * 3));
        poly1305LimbsAVX512(crb, masm, data0, data1, vecB3, vecB4, vecB5, true, tmp);

        poly1305Multiply8AVX512(crb, masm, vecA0, vecA1, vecA2,
                        vecR0, vecR1, vecR2, vecR1P, vecR2P,
                        vecT0, vecT1, vecT2, vecT3, vecT4, vecT5, tmp);
        poly1305Multiply8AVX512(crb, masm, vecA3, vecA4, vecA5,
                        vecR0, vecR1, vecR2, vecR1P, vecR2P,
                        vecT0, vecT1, vecT2, vecT3, vecT4, vecT5, tmp);

        // Add low 42-bit bits from new blocks to accumulator
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA0, vecA0, vecB0);
        // Add medium 42-bit bits from new blocks to accumulator
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA1, vecA1, vecB1);
        // Add highest bits from new blocks to accumulator
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA2, vecA2, vecB2);
        // Add low 42-bit bits from new blocks to accumulator
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA3, vecA3, vecB3);
        // Add medium 42-bit bits from new blocks to accumulator
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA4, vecA4, vecB4);
        // Add highest bits from new blocks to accumulator
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA5, vecA5, vecB5);

        masm.subl(length, 16 * 16);
        masm.leaq(input, new AMD64Address(input, 16 * 16));
        masm.jmp(labelLProcess256Loop);

        masm.bind(labelLProcess256LoopDone);

        // @formatter:off
        // Tail processing: Need to multiply ACC by R^16..R^1 and add it all up into a single scalar value
        // Generate 4*5*[R^16..R^9] (ignore lowest limb)
        // Use data0 ~ vecR1P, data1 ~ vecR2P for higher powers
        // @formatter:on
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecC4, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecC5, 2);
        // 5*R^8
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, vecC4);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, vecC5);
        // 4*5*R^8
        EVPSLLQ.emit(masm, AVXSize.ZMM, data0, vecR1P, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, data1, vecR2P, 2);

        // Generate 4*5*[R^8..R^1] (ignore lowest limb)
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecC1, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecC2, 2);
        // 5*R^8
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, vecC1);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, vecC2);
        // 4*5*R^8
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR1P, vecR1P, 2);
        EVPSLLQ.emit(masm, AVXSize.ZMM, vecR2P, vecR2P, 2);

        poly1305Multiply8AVX512(crb, masm, vecA0, vecA1, vecA2,
                        vecC3, vecC4, vecC5, data0, data1,
                        vecT0, vecT1, vecT2, vecT3, vecT4, vecT5, tmp);
        poly1305Multiply8AVX512(crb, masm, vecA3, vecA4, vecA5,
                        vecC0, vecC1, vecC2, vecR1P, vecR2P,
                        vecT0, vecT1, vecT2, vecT3, vecT4, vecT5, tmp);

        // @formatter:off
        // Add all blocks (horizontally)
        // 16->8 blocks
        // @formatter:on
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA0, vecA0, vecA3);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA1, vecA1, vecA4);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA2, vecA2, vecA5);

        // 8 -> 4 blocks
        EVEXTRACTI64X4.emit(masm, AVXSize.ZMM, vecT0, vecA0, 1);
        EVEXTRACTI64X4.emit(masm, AVXSize.ZMM, vecT1, vecA1, 1);
        EVEXTRACTI64X4.emit(masm, AVXSize.ZMM, vecT2, vecA2, 1);
        masm.vpaddq(vecA0, vecA0, vecT0, AVXSize.YMM);
        masm.vpaddq(vecA1, vecA1, vecT1, AVXSize.YMM);
        masm.vpaddq(vecA2, vecA2, vecT2, AVXSize.YMM);

        // 4 -> 2 blocks
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, vecT0, vecA0, 1);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, vecT1, vecA1, 1);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, vecT2, vecA2, 1);
        masm.vpaddq(vecA0, vecA0, vecT0, AVXSize.XMM);
        masm.vpaddq(vecA1, vecA1, vecT1, AVXSize.XMM);
        masm.vpaddq(vecA2, vecA2, vecT2, AVXSize.XMM);

        // 2 -> 1 blocks
        masm.vpsrldq(vecT0, vecA0, 8, AVXSize.XMM);
        masm.vpsrldq(vecT1, vecA1, 8, AVXSize.XMM);
        masm.vpsrldq(vecT2, vecA2, 8, AVXSize.XMM);

        // Finish folding and clear second qword
        masm.movq(t0, 0xfdL);
        masm.kmovq(k1, t0);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA0, vecA0, vecT0, k1, Z1, jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA1, vecA1, vecT1, k1, Z1, jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA2, vecA2, vecT2, k1, Z1, jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0);

        // Carry propagation
        EVPSRLQ.emit(masm, AVXSize.ZMM, data0, vecA0, 44);
        // Clear top 20 bits
        EVPANDQ.emit(masm, AVXSize.ZMM, vecA0, vecA0, recordExternalAddress(crb, POLY1305_MASK44));
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA1, vecA1, data0);
        EVPSRLQ.emit(masm, AVXSize.ZMM, data0, vecA1, 44);
        // Clear top 20 bits
        EVPANDQ.emit(masm, AVXSize.ZMM, vecA1, vecA1, recordExternalAddress(crb, POLY1305_MASK44));
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA2, vecA2, data0);
        EVPSRLQ.emit(masm, AVXSize.ZMM, data0, vecA2, 42);
        // Clear top 22 bits
        EVPANDQ.emit(masm, AVXSize.ZMM, vecA2, vecA2, recordExternalAddress(crb, POLY1305_MASK42));
        EVPSLLQ.emit(masm, AVXSize.ZMM, data1, data0, 2);
        EVPADDQ.emit(masm, AVXSize.ZMM, data0, data0, data1);
        EVPADDQ.emit(masm, AVXSize.ZMM, vecA0, vecA0, data0);

        // Put together accumulator
        masm.movdq(a0, vecA0);

        masm.movdq(t0, vecA1);
        masm.movq(t1, t0);
        masm.shlq(t1, 44);
        masm.shrq(t0, 20);

        masm.movdq(a2, vecA2);
        masm.movq(a1, a2);
        masm.shlq(a1, 24);
        masm.shrq(a2, 40);

        masm.addq(a0, t1);
        masm.adcq(a1, t0);
        masm.adcq(a2, 0);

        // @formatter:off
        // Cleanup
        // Zero out zmm0-zmm31.
        // @formatter:on
        masm.vzeroall();
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm16, xmm16, xmm16);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm17, xmm17, xmm17);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm18, xmm18, xmm18);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm19, xmm19, xmm19);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm20, xmm20, xmm20);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm21, xmm21, xmm21);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm22, xmm22, xmm22);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm23, xmm23, xmm23);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm24, xmm24, xmm24);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm25, xmm25, xmm25);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm26, xmm26, xmm26);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm27, xmm27, xmm27);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm28, xmm28, xmm28);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm29, xmm29, xmm29);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm30, xmm30, xmm30);
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm31, xmm31, xmm31);

        masm.resetAvxEncoding(oldEncoding);
    }

    // @formatter:off
    //  The AVX2 implementation below is directly based on the AVX2 Poly1305 hash computation as
    //  implemented in Intel(R) Multi-Buffer Crypto for IPsec Library.
    //  (url: https://github.com/intel/intel-ipsec-mb/blob/main/lib/avx2_t3/poly_fma_avx2.asm)
    //
    //  Additional references:
    //  [1] Goll M, Gueron S., "Vectorization of Poly1305 message authentication code",
    //      12th International Conference on Information Technology-New Generations,
    //      2015 Apr 13 (pp. 145-150). IEEE.
    //  [2] Bhattacharyya S, Sarkar P., "Improved SIMD implementation of Poly1305",
    //      IET Information Security. 2020 Sep;14(5):521-30.
    //  Note: a compact summary of the Goll-Gueron AVX2 algorithm developed in [1] is presented in [2].
    //  [3] Wikipedia, "Parallel evaluation of Horner's method",
    //      (url: https://en.wikipedia.org/wiki/Horner%27s_method)
    // ----------------------------------------------------------
    //
    //  Poly1305 AVX2 algorithm:
    //  Let the 32-byte one-time key be partitioned into two equal parts R and K.
    //  Let R be the 16-byte secret key used for polynomial evaluation.
    //  Let K be the 16-byte secret key.
    //  Let Z_P be prime field over which the polynomial is evaluated. Let P = 2^130 - 5 be the prime.
    //  Let M be the message which can be represented as a concatenation (||) of 'l' 16-byte blocks M[i].
    //  i.e., M = M[0] || M[1] || ... || M[i] || ... || M[l-2] || M[l-1]
    //  To create the coefficients C[i] for polynomial evaluation over Z_P, each 16-byte (i.e., 128-bit)
    //  message block M[i] is concatenated with bits '10' to make a 130-bit block.
    //  The last block (<= 16-byte length) is concatenated with 1 followed by 0s to make a 130-bit block.
    //  Therefore, we define
    //  C[i]   = M[i] || '10' for 0 <= i <= l-2 ;
    //  C[l-1] = M[i] || '10...0'
    //  such that, length(C[i]) = 130 bits, for i ∈ [0, l).
    //
    //  Let * indicate scalar multiplication (i.e., w = u * v);
    //  Let × indicate scalar multiplication followed by reduction modulo P (i.e., z = u × v = {(u * v) mod P})
    //
    //  POLY1305_MAC = (POLY1305_EVAL_POLYNOMIAL(C, R, P) + K) mod 2^128; where,
    //
    //  POLY1305_EVAL_POLYNOMIAL(C, R, P) = {C[0] * R^l + C[1] * R^(l-1) + ... + C[l-2] * R^2 + C[l-1] * R} mod P
    //    = R × {C[0] × R^(l-1) + C[1] × R^(l-2) + ... + C[l-2] × R + C[l-1]}
    //    = R × Polynomial(R; C[0], C[1], ... ,C[l-2], C[l-1])
    //  Where,
    //  Polynomial(R; C[0], C[1], ... ,C[l-2], C[l-1]) = Σ{C[i] × R^(l-i-1)} for i ∈ [0, l)
    // ----------------------------------------------------------
    //
    //  Parallel evaluation of POLY1305_EVAL_POLYNOMIAL(C, R, P):
    //  Let the number of message blocks l = 4*l' + ρ where ρ = l mod 4.
    //  Using k-way parallel Horner's evaluation [3], for k = 4, we define SUM below:
    //
    //  SUM = R^4 × Polynomial(R^4; C[0], C[4], C[8]  ... , C[4l'-4]) +
    //        R^3 × Polynomial(R^4; C[1], C[5], C[9]  ... , C[4l'-3]) +
    //        R^2 × Polynomial(R^4; C[2], C[6], C[10] ... , C[4l'-2]) +
    //        R^1 × Polynomial(R^4; C[3], C[7], C[11] ... , C[4l'-1]) +
    //
    //  Then,
    //  POLY1305_EVAL_POLYNOMIAL(C, R, P) = SUM if ρ = 0 (i.e., l is multiple of 4)
    //                        = R × Polynomial(R; SUM + C[l-ρ], C[l-ρ+1], ... , C[l-1]) if ρ > 0
    // ----------------------------------------------------------
    //
    //  Gall-Gueron[1] 4-way SIMD Algorithm[2] for POLY1305_EVAL_POLYNOMIAL(C, R, P):
    //
    //  Define mathematical vectors (not same as SIMD vector lanes) as below:
    //  R4321   = [R^4, R^3, R^2, R^1];
    //  R4444   = [R^4, R^4, R^4, R^4];
    //  COEF[i] = [C[4i], C[4i+1], C[4i+2], C[4i+3]] for i ∈ [0, l'). For example, COEF[0] and COEF[1] shown below.
    //  COEF[0] = [C0, C1, C2, C3]
    //  COEF[1] = [C4, C5, C6, C7]
    //  T       = [T0, T1, T2, T3] be a temporary vector
    //  ACC     = [acc, 0, 0, 0]; acc has hash from previous computations (if any), otherwise 0.
    //  ⊗ indicates component-wise vector multiplication followed by modulo reduction
    //  ⊕ indicates component-wise vector addition, + indicates scalar addition
    //
    //  POLY1305_EVAL_POLYNOMIAL(C, R, P) {
    //    T ← ACC; # load accumulator
    //    T ← T ⊕ COEF[0]; # add accumulator to the first 4 blocks
    //    Compute R4321, R4444;
    //    # SIMD loop
    //    l' = floor(l/4); # operate on 4 blocks at a time
    //    for (i = 1 to l'-1):
    //      T ← (R4444 ⊗ T) ⊕ COEF[i];
    //    T ← R4321 ⊗ T;
    //    SUM ← T0 + T1 + T2 + T3;
    //
    //    # Scalar tail processing
    //    if (ρ > 0):
    //      SUM ← R × Polynomial(R; SUM + C[l-ρ], C[l-ρ+1], ... , C[l-1]);
    //    return SUM;
    //  }
    //
    //  Notes:
    //  (1) Each 130-bit block is represented using three 44-bit limbs (most significant limb is only 42-bit).
    //      (The Goll-Gueron implementation[1] uses five 26-bit limbs instead).
    //  (2) Each component of the mathematical vectors is a 130-bit value. The above mathemetical vectors are not to be confused with SIMD vector lanes.
    //  (3) Each AVX2 YMM register can store four 44-bit limbs in quadwords. Since each 130-bit message block is represented using 3 limbs,
    //      to store all the limbs of 4 different 130-bit message blocks, we need 3 YMM registers in total.
    //  (4) In the AVX2 implementation, multiplication followed by modulo reduction and addition are performed for 4 blocks at a time.
    // @formatter:on
    private static void poly1305ProcessBlocksAVX2(AMD64MacroAssembler masm,
                    CompilationResultBuilder crb,
                    Register input,
                    Register length,
                    Register a0,
                    Register a1,
                    Register a2,
                    Register r0,
                    Register r1,
                    Register c1,
                    Register t0,
                    Register t1,
                    Register t2,
                    Register mulql,
                    Register mulqh) {
        Label labelLProcess256Loop = new Label();
        Label labelLProcess256LoopDone = new Label();

        Register ymmAcc0 = xmm0;
        Register ymmAcc1 = xmm1;
        Register ymmAcc2 = xmm2;

        Register yTmp1 = xmm3;
        Register yTmp2 = xmm4;
        Register yTmp3 = xmm5;
        Register yTmp4 = xmm6;
        Register yTmp5 = xmm7;
        Register yTmp6 = xmm8;
        Register yTmp7 = xmm9;
        Register yTmp8 = xmm10;
        Register yTmp9 = xmm11;
        Register yTmp10 = xmm12;
        Register yTmp11 = xmm13;
        Register yTmp12 = xmm14;
        Register yTmp13 = xmm15;

        Register ymmR0 = yTmp11;
        Register ymmR1 = yTmp12;
        Register ymmR2 = yTmp13;

        // XMM aliases of YMM registers (for convenience)
        Register xTmp1 = yTmp1;
        Register xTmp2 = yTmp2;
        Register xTmp3 = yTmp3;

        final int r4R1Save = 0;
        final int r4Save = 32 * 3;
        final int r4PSave = 32 * 6;

        // @formatter:off
        // Setup stack frame
        // Save rbp and rsp
        // @formatter:on
        // Align stack and reserve space
        masm.push(rbp);
        masm.movq(rbp, rsp);
        masm.andq(rsp, -32);
        masm.subq(rsp, 32 * 8);

        // Compute the following steps of POLY1305_EVAL_POLYNOMIAL algorithm
        // T <- ACC
        // T <- T + COEF[0];

        // Spread accumulator into 44-bit limbs in quadwords
        // Accumulator limbs to be stored in yTmp1, yTmp2, yTmp3
        // First limb (Acc[43:0])
        masm.movq(t0, a0);
        masm.andq(t0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.movdq(xTmp1, t0);
        // Second limb (Acc[87:44])
        masm.movq(t0, a1);
        masm.shrdq(a0, t0, 44);
        masm.andq(a0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.movdq(xTmp2, a0);
        // Third limb (Acc[129:88])
        masm.shrdq(a1, a2, 24);
        masm.andq(a1, recordExternalAddress(crb, POLY1305_MASK42));
        masm.movdq(xTmp3, a1);
        // --- end of spread accumulator

        // To add accumulator, we must unroll first loop iteration
        // Load first four 16-byte message blocks of data (64 bytes)
        masm.vmovdqu(yTmp4, new AMD64Address(input, 0));
        masm.vmovdqu(yTmp5, new AMD64Address(input, 32));

        // Interleave the input message data to form 44-bit limbs
        // ymmAcc0 to have bits 0-43 of all 4 blocks in 4 qwords
        // ymmAcc1 to have bits 87-44 of all 4 blocks in 4 qwords
        // ymmAcc2 to have bits 127-88 of all 4 blocks in 4 qwords
        // Interleave blocks of data
        VPUNPCKHQDQ.emit(masm, AVXSize.YMM, ymmAcc2, yTmp4, yTmp5);
        VPUNPCKLQDQ.emit(masm, AVXSize.YMM, ymmAcc0, yTmp4, yTmp5);

        // Middle 44-bit limbs of new blocks
        masm.vpsrlq(ymmAcc1, ymmAcc0, 44, AVXSize.YMM);
        masm.vpsllq(yTmp4, ymmAcc2, 20, AVXSize.YMM);
        masm.vpor(ymmAcc1, ymmAcc1, yTmp4, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, ymmAcc1, ymmAcc1, recordExternalAddress(crb, POLY1305_MASK44));

        // Lowest 44-bit limbs of new blocks
        VPAND.emit(masm, AVXSize.YMM, ymmAcc0, ymmAcc0, recordExternalAddress(crb, POLY1305_MASK44));

        // Highest 42-bit limbs of new blocks; pad the msg with 2^128
        masm.vpsrlq(ymmAcc2, ymmAcc2, 24, AVXSize.YMM);

        // Add 2^128 to all 4 final qwords for the message
        VPOR.emit(masm, AVXSize.YMM, ymmAcc2, ymmAcc2, recordExternalAddress(crb, POLY1305_PAD_MSG));
        // --- end of input interleaving and message padding

        // Add accumulator to the fist message block
        // Accumulator limbs in yTmp1, yTmp2, yTmp3
        masm.vpaddq(ymmAcc0, ymmAcc0, yTmp1, AVXSize.YMM);
        masm.vpaddq(ymmAcc1, ymmAcc1, yTmp2, AVXSize.YMM);
        masm.vpaddq(ymmAcc2, ymmAcc2, yTmp3, AVXSize.YMM);

        // Compute the following steps of POLY1305_EVAL_POLYNOMIAL algorithm
        // Compute R4321, R4444;
        // R4321 = [R^4, R^3, R^2, R^1];
        // R4444 = [R^4, R^4, R^4, R^4];

        // Compute the powers of R^1..R^4 and form 44-bit limbs of each
        // yTmp5 to have bits 0-127 for R^1 and R^2
        // yTmp6 to have bits 128-129 for R^1 and R^2
        masm.movdq(xTmp1, r0);
        VPINSRQ.emit(masm, AVXSize.XMM, xTmp1, xTmp1, r1, 1);
        VINSERTI128.emit(masm, AVXSize.YMM, yTmp5, yTmp5, xTmp1, 1);
        // clear registers
        masm.vpxor(yTmp10, yTmp10, yTmp10, AVXSize.YMM);
        masm.vpxor(yTmp6, yTmp6, yTmp6, AVXSize.YMM);

        // Calculate R^2
        // a <- R
        masm.movq(a0, r0);
        masm.movq(a1, r1);
        // a <- a * R = R^2
        poly1305MultiplyScalar(masm, a0, a1, a2, r0, r1, c1, true, t0, t1, t2, mulql, mulqh);
        // Store R^2 in yTmp5, yTmp6
        masm.movdq(xTmp1, a0);
        VPINSRQ.emit(masm, AVXSize.XMM, xTmp1, xTmp1, a1, 1);
        VINSERTI128.emit(masm, AVXSize.YMM, yTmp5, yTmp5, xTmp1, 0);
        masm.movdq(xTmp1, a2);
        VINSERTI128.emit(masm, AVXSize.YMM, yTmp6, yTmp6, xTmp1, 0);

        // Calculate R^3
        // a <- a * R = R^3
        poly1305MultiplyScalar(masm, a0, a1, a2, r0, r1, c1, false, t0, t1, t2, mulql, mulqh);
        // Store R^3 in yTmp7, yTmp2
        masm.movdq(xTmp1, a0);
        VPINSRQ.emit(masm, AVXSize.XMM, xTmp1, xTmp1, a1, 1);
        VINSERTI128.emit(masm, AVXSize.YMM, yTmp7, yTmp7, xTmp1, 1);
        masm.movdq(xTmp1, a2);
        VINSERTI128.emit(masm, AVXSize.YMM, yTmp2, yTmp2, xTmp1, 1);

        // Calculate R^4
        // a <- a * R = R^4
        poly1305MultiplyScalar(masm, a0, a1, a2, r0, r1, c1, false, t0, t1, t2, mulql, mulqh);
        // Store R^4 in yTmp7, yTmp2
        masm.movdq(xTmp1, a0);
        VPINSRQ.emit(masm, AVXSize.XMM, xTmp1, xTmp1, a1, 1);
        VINSERTI128.emit(masm, AVXSize.YMM, yTmp7, yTmp7, xTmp1, 0);
        masm.movdq(xTmp1, a2);
        VINSERTI128.emit(masm, AVXSize.YMM, yTmp2, yTmp2, xTmp1, 0);

        // Interleave the powers of R^1..R^4 to form 44-bit limbs (half-empty)
        VPUNPCKHQDQ.emit(masm, AVXSize.YMM, ymmR2, yTmp5, yTmp10);
        VPUNPCKLQDQ.emit(masm, AVXSize.YMM, ymmR0, yTmp5, yTmp10);
        VPUNPCKHQDQ.emit(masm, AVXSize.YMM, yTmp3, yTmp7, yTmp10);
        VPUNPCKLQDQ.emit(masm, AVXSize.YMM, yTmp4, yTmp7, yTmp10);

        masm.vpslldq(ymmR2, ymmR2, 8, AVXSize.YMM);
        masm.vpslldq(yTmp6, yTmp6, 8, AVXSize.YMM);
        masm.vpslldq(ymmR0, ymmR0, 8, AVXSize.YMM);
        masm.vpor(ymmR2, ymmR2, yTmp3, AVXSize.YMM);
        masm.vpor(ymmR0, ymmR0, yTmp4, AVXSize.YMM);
        masm.vpor(yTmp6, yTmp6, yTmp2, AVXSize.YMM);
        // Move 2 MSbits to top 24 bits, to be OR'ed later
        masm.vpsllq(yTmp6, yTmp6, 40, AVXSize.YMM);

        masm.vpsrlq(ymmR1, ymmR0, 44, AVXSize.YMM);
        masm.vpsllq(yTmp5, ymmR2, 20, AVXSize.YMM);
        masm.vpor(ymmR1, ymmR1, yTmp5, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, ymmR1, ymmR1, recordExternalAddress(crb, POLY1305_MASK44));

        VPAND.emit(masm, AVXSize.YMM, ymmR0, ymmR0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.vpsrlq(ymmR2, ymmR2, 24, AVXSize.YMM);

        masm.vpor(ymmR2, ymmR2, yTmp6, AVXSize.YMM);
        // ymmR0, ymmR1, ymmR2 have the limbs of R^1, R^2, R^3, R^4

        // Store R^4-R on stack for later use
        masm.vmovdqu(new AMD64Address(rsp, r4R1Save + 0), ymmR0);
        masm.vmovdqu(new AMD64Address(rsp, r4R1Save + 32), ymmR1);
        masm.vmovdqu(new AMD64Address(rsp, r4R1Save + 32 * 2), ymmR2);

        // Broadcast 44-bit limbs of R^4
        masm.movq(t0, a0);
        // First limb (R^4[43:0])
        masm.andq(t0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.movdq(ymmR0, t0);
        VPERMQ.emit(masm, AVXSize.YMM, ymmR0, ymmR0, 0x0);

        masm.movq(t0, a1);
        masm.shrdq(a0, t0, 44);
        // Second limb (R^4[87:44])
        masm.andq(a0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.movdq(ymmR1, a0);
        VPERMQ.emit(masm, AVXSize.YMM, ymmR1, ymmR1, 0x0);

        masm.shrdq(a1, a2, 24);
        // Third limb (R^4[129:88])
        masm.andq(a1, recordExternalAddress(crb, POLY1305_MASK42));
        masm.movdq(ymmR2, a1);
        VPERMQ.emit(masm, AVXSize.YMM, ymmR2, ymmR2, 0x0);
        // ymmR0, ymmR1, ymmR2 have the limbs of R^4, R^4, R^4, R^4

        // Generate 4*5*R^4
        // 4*R^4
        masm.vpsllq(yTmp1, ymmR1, 2, AVXSize.YMM);
        masm.vpsllq(yTmp2, ymmR2, 2, AVXSize.YMM);
        // 5*R^4
        masm.vpaddq(yTmp1, yTmp1, ymmR1, AVXSize.YMM);
        masm.vpaddq(yTmp2, yTmp2, ymmR2, AVXSize.YMM);
        // 4*5*R^4
        masm.vpsllq(yTmp1, yTmp1, 2, AVXSize.YMM);
        masm.vpsllq(yTmp2, yTmp2, 2, AVXSize.YMM);

        // Store broadcasted R^4 and 4*5*R^4 on stack for later use
        masm.vmovdqu(new AMD64Address(rsp, r4Save + 0), ymmR0);
        masm.vmovdqu(new AMD64Address(rsp, r4Save + 32), ymmR1);
        masm.vmovdqu(new AMD64Address(rsp, r4Save + 32 * 2), ymmR2);
        masm.vmovdqu(new AMD64Address(rsp, r4PSave), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4PSave + 32), yTmp2);

        // Get the number of multiples of 4 message blocks (64 bytes) for vectorization
        masm.movq(t0, length);
        // 0xffffffffffffffc0 after sign extension
        masm.andq(t0, 0xffffffc0);

        // VECTOR LOOP: process 4 * 16-byte message blocks at a time
        masm.bind(labelLProcess256Loop);
        masm.cmplAndJcc(t0, 16 * 4, AMD64Assembler.ConditionFlag.BelowEqual, labelLProcess256LoopDone, false);

        // @formatter:off
        // Compute the following steps of POLY1305_EVAL_POLYNOMIAL algorithm
        //   l' = floor(l/4)
        //   for (i = 1 to l'-1):
        //     T ← (R4444 ⊗ T) ⊕ COEF[i];

        // Perform multiply and reduce while loading the next block and adding it in interleaved manner
        // The logic to advance the SIMD loop counter (i.e. length -= 64) is inside the function below.
        // The function below also includes the logic to load the next 4 blocks of data for efficient port utilization.
        // @formatter:on
        poly1305MsgMulReduceVec4AVX2(crb, masm, ymmAcc0, ymmAcc1, ymmAcc2,
                        new AMD64Address(rsp, r4Save + 0), new AMD64Address(rsp, r4Save + 32), new AMD64Address(rsp, r4Save + 32 * 2),
                        new AMD64Address(rsp, r4PSave), new AMD64Address(rsp, r4PSave + 32),
                        yTmp1, yTmp2, yTmp3, yTmp4, yTmp5, yTmp6,
                        yTmp7, yTmp8, yTmp9, yTmp10, yTmp11, yTmp12,
                        input, t0);
        masm.jmp(labelLProcess256Loop);
        // end of vector loop
        masm.bind(labelLProcess256LoopDone);

        // Compute the following steps of POLY1305_EVAL_POLYNOMIAL algorithm
        // T <- R4321 * T;

        // Need to multiply by R^4, R^3, R^2, R
        // Read R^4-R;
        masm.vmovdqu(ymmR0, new AMD64Address(rsp, r4R1Save + 0));
        masm.vmovdqu(ymmR1, new AMD64Address(rsp, r4R1Save + 32));
        masm.vmovdqu(ymmR2, new AMD64Address(rsp, r4R1Save + 32 * 2));

        // Generate 4*5*[R^4..R^1] (ignore lowest limb)
        // yTmp1 to have bits 87-44 of all 1-4th powers of R' in 4 qwords
        // yTmp2 to have bits 129-88 of all 1-4th powers of R' in 4 qwords
        masm.vpsllq(yTmp10, ymmR1, 2, AVXSize.YMM);
        // R1' (R1*5)
        masm.vpaddq(yTmp1, ymmR1, yTmp10, AVXSize.YMM);
        masm.vpsllq(yTmp10, ymmR2, 2, AVXSize.YMM);
        // R2' (R2*5)
        masm.vpaddq(yTmp2, ymmR2, yTmp10, AVXSize.YMM);

        // 4*5*R
        masm.vpsllq(yTmp1, yTmp1, 2, AVXSize.YMM);
        masm.vpsllq(yTmp2, yTmp2, 2, AVXSize.YMM);

        poly1305MulReduceVec4AVX2(crb, masm, ymmAcc0, ymmAcc1, ymmAcc2,
                        ymmR0, ymmR1, ymmR2, yTmp1, yTmp2,
                        yTmp3, yTmp4, yTmp5, yTmp6,
                        yTmp7, yTmp8, yTmp9);
        // Compute the following steps of POLY1305_EVAL_POLYNOMIAL algorithm
        // SUM <- T0 + T1 + T2 + T3;

        // 4 -> 2 blocks
        VEXTRACTI128.emit(masm, AVXSize.YMM, yTmp1, ymmAcc0, 1);
        VEXTRACTI128.emit(masm, AVXSize.YMM, yTmp2, ymmAcc1, 1);
        VEXTRACTI128.emit(masm, AVXSize.YMM, yTmp3, ymmAcc2, 1);

        masm.vpaddq(ymmAcc0, ymmAcc0, yTmp1, AVXSize.XMM);
        masm.vpaddq(ymmAcc1, ymmAcc1, yTmp2, AVXSize.XMM);
        masm.vpaddq(ymmAcc2, ymmAcc2, yTmp3, AVXSize.XMM);
        // 2 -> 1 blocks
        masm.vpsrldq(yTmp1, ymmAcc0, 8, AVXSize.XMM);
        masm.vpsrldq(yTmp2, ymmAcc1, 8, AVXSize.XMM);
        masm.vpsrldq(yTmp3, ymmAcc2, 8, AVXSize.XMM);

        // Finish folding
        masm.vpaddq(ymmAcc0, ymmAcc0, yTmp1, AVXSize.XMM);
        masm.vpaddq(ymmAcc1, ymmAcc1, yTmp2, AVXSize.XMM);
        masm.vpaddq(ymmAcc2, ymmAcc2, yTmp3, AVXSize.XMM);

        VMOVQ_XMM_MEM.emit(masm, AVXSize.XMM, ymmAcc0, ymmAcc0);
        VMOVQ_XMM_MEM.emit(masm, AVXSize.XMM, ymmAcc1, ymmAcc1);
        VMOVQ_XMM_MEM.emit(masm, AVXSize.XMM, ymmAcc2, ymmAcc2);

        masm.leaq(input, new AMD64Address(input, 16 * 4));
        // remaining bytes < length 64
        masm.andq(length, 63);
        // carry propagation
        masm.vpsrlq(yTmp1, ymmAcc0, 44, AVXSize.XMM);
        // Clear top 20 bits
        VPAND.emit(masm, AVXSize.XMM, ymmAcc0, ymmAcc0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.vpaddq(ymmAcc1, ymmAcc1, yTmp1, AVXSize.XMM);
        masm.vpsrlq(yTmp1, ymmAcc1, 44, AVXSize.XMM);
        // Clear top 20 bits
        VPAND.emit(masm, AVXSize.XMM, ymmAcc1, ymmAcc1, recordExternalAddress(crb, POLY1305_MASK44));
        masm.vpaddq(ymmAcc2, ymmAcc2, yTmp1, AVXSize.XMM);
        masm.vpsrlq(yTmp1, ymmAcc2, 42, AVXSize.XMM);
        // Clear top 20 bits
        VPAND.emit(masm, AVXSize.XMM, ymmAcc2, ymmAcc2, recordExternalAddress(crb, POLY1305_MASK42));
        masm.vpsllq(yTmp2, yTmp1, 2, AVXSize.XMM);
        masm.vpaddq(yTmp1, yTmp1, yTmp2, AVXSize.XMM);
        masm.vpaddq(ymmAcc0, ymmAcc0, yTmp1, AVXSize.XMM);

        // Put together A
        masm.movdq(a0, ymmAcc0);
        masm.movdq(t0, ymmAcc1);
        masm.movq(t1, t0);
        masm.shlq(t1, 44);
        masm.orq(a0, t1);
        masm.shrq(t0, 20);
        masm.movdq(a2, ymmAcc2);
        masm.movq(a1, a2);
        masm.shlq(a1, 24);
        masm.orq(a1, t0);
        masm.shrq(a2, 40);

        // cleanup
        // clears all ymm registers (ymm0 through ymm15)
        masm.vzeroall();

        // SAFE DATA (clear powers of R)
        masm.vmovdqu(new AMD64Address(rsp, r4R1Save + 0), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4R1Save + 32), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4R1Save + 32 * 2), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4Save + 0), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4Save + 32), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4Save + 32 * 2), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4PSave), yTmp1);
        masm.vmovdqu(new AMD64Address(rsp, r4PSave + 32), yTmp1);

        // Save rbp and rsp; clear stack frame
        masm.movq(rsp, rbp);
        masm.pop(rbp);
    }

    // @formatter:off
    // Convert array of 128-bit numbers in quadwords (in D0:D1) into 128-bit numbers across 44-bit limbs (in L0:L1:L2)
    // Optionally pad all the numbers (i.e. add 2^128)
    //
    //         +-------------------------+-------------------------+
    //  D0:D1  | h0 h1 g0 g1 f0 f1 e0 e1 | d0 d1 c0 c1 b0 b1 a0 a1 |
    //         +-------------------------+-------------------------+
    //         +-------------------------+
    //  L2     | h2 d2 g2 c2 f2 b2 e2 a2 |
    //         +-------------------------+
    //         +-------------------------+
    //  L1     | h1 d1 g1 c1 f1 b1 e1 a1 |
    //         +-------------------------+
    //         +-------------------------+
    //  L0     | h0 d0 g0 c0 f0 b0 e0 a0 |
    //         +-------------------------+
    // @formatter:on
    private static void poly1305LimbsAVX512(CompilationResultBuilder crb,
                    AMD64MacroAssembler masm,
                    Register d0,
                    Register d1,
                    Register l0,
                    Register l1,
                    Register l2,
                    boolean padMSG,
                    Register tmp) {
        // Interleave blocks of data
        EVPUNPCKHQDQ.emit(masm, AVXSize.ZMM, tmp, d0, d1);
        EVPUNPCKLQDQ.emit(masm, AVXSize.ZMM, l0, d0, d1);

        // Highest 42-bit limbs of new blocks
        EVPSRLQ.emit(masm, AVXSize.ZMM, l2, tmp, 24);
        if (padMSG) {
            // Add 2^128 to all 8 final qwords of the message
            EVPORQ.emit(masm, AVXSize.ZMM, l2, l2, recordExternalAddress(crb, POLY1305_PAD_MSG));
        }

        // Middle 44-bit limbs of new blocks
        EVPSRLQ.emit(masm, AVXSize.ZMM, l1, l0, 44);
        EVPSLLQ.emit(masm, AVXSize.ZMM, tmp, tmp, 20);
        // (A OR B AND C)
        EVPTERNLOGQ.emit(masm, AVXSize.ZMM, l1, tmp, recordExternalAddress(crb, POLY1305_MASK44), 0xA8);

        // Lowest 44-bit limbs of new blocks
        EVPANDQ.emit(masm, AVXSize.ZMM, l0, l0, recordExternalAddress(crb, POLY1305_MASK44));
    }

    private static void poly1305Multiply8AVX512(CompilationResultBuilder crb,
                    AMD64MacroAssembler masm,
                    Register a0,
                    Register a1,
                    Register a2,
                    Register r0,
                    Register r1,
                    Register r2,
                    Register r1p,
                    Register r2p,
                    Register p0l,
                    Register p0h,
                    Register p1l,
                    Register p1h,
                    Register p2l,
                    Register p2h,
                    Register tmp) {
        // Reset partial sums
        EVPXORQ.emit(masm, AVXSize.ZMM, p0l, p0l, p0l);
        EVPXORQ.emit(masm, AVXSize.ZMM, p0h, p0h, p0h);
        EVPXORQ.emit(masm, AVXSize.ZMM, p1l, p1l, p1l);
        EVPXORQ.emit(masm, AVXSize.ZMM, p1h, p1h, p1h);
        EVPXORQ.emit(masm, AVXSize.ZMM, p2l, p2l, p2l);
        EVPXORQ.emit(masm, AVXSize.ZMM, p2h, p2h, p2h);

        // Calculate partial products
        // p0 = a2xr1'
        // p1 = a2xr2'
        // p2 = a2xr0
        masm.evpmadd52luq(p0l, a2, r1p, AVXSize.ZMM);
        masm.evpmadd52huq(p0h, a2, r1p, AVXSize.ZMM);
        masm.evpmadd52luq(p1l, a2, r2p, AVXSize.ZMM);
        masm.evpmadd52huq(p1h, a2, r2p, AVXSize.ZMM);
        masm.evpmadd52luq(p2l, a2, r0, AVXSize.ZMM);
        masm.evpmadd52huq(p2h, a2, r0, AVXSize.ZMM);

        // p0 += a0xr0
        // p1 += a0xr1
        // p2 += a0xr2
        masm.evpmadd52luq(p1l, a0, r1, AVXSize.ZMM);
        masm.evpmadd52huq(p1h, a0, r1, AVXSize.ZMM);
        masm.evpmadd52luq(p2l, a0, r2, AVXSize.ZMM);
        masm.evpmadd52huq(p2h, a0, r2, AVXSize.ZMM);
        masm.evpmadd52luq(p0l, a0, r0, AVXSize.ZMM);
        masm.evpmadd52huq(p0h, a0, r0, AVXSize.ZMM);

        // p0 += a1xr2'
        // p1 += a1xr0
        // p2 += a1xr1
        masm.evpmadd52luq(p0l, a1, r2p, AVXSize.ZMM);
        masm.evpmadd52huq(p0h, a1, r2p, AVXSize.ZMM);
        masm.evpmadd52luq(p1l, a1, r0, AVXSize.ZMM);
        masm.evpmadd52huq(p1h, a1, r0, AVXSize.ZMM);
        masm.evpmadd52luq(p2l, a1, r1, AVXSize.ZMM);
        masm.evpmadd52huq(p2h, a1, r1, AVXSize.ZMM);

        // @formatter:off
        // Carry propagation:
        // (Not quite aligned)                         | More mathematically correct:
        //         P2L   P1L   P0L                     |                 P2Lx2^88 + P1Lx2^44 + P0Lx2^0
        // + P2H   P1H   P0H                           |   + P2Hx2^140 + P1Hx2^96 + P0Hx2^52
        // ---------------------------                 |   -----------------------------------------------
        // = P2H    A2    A1    A0                     |   = P2Hx2^130 + A2x2^88 +   A1x2^44 +  A0x2^0
        //
        // @formatter:on
        EVPSRLQ.emit(masm, AVXSize.ZMM, tmp, p0l, 44);
        EVPANDQ.emit(masm, AVXSize.ZMM, a0, p0l, recordExternalAddress(crb, POLY1305_MASK44));

        EVPSLLQ.emit(masm, AVXSize.ZMM, p0h, p0h, 8);
        EVPADDQ.emit(masm, AVXSize.ZMM, p0h, p0h, tmp);
        EVPADDQ.emit(masm, AVXSize.ZMM, p1l, p1l, p0h);
        EVPANDQ.emit(masm, AVXSize.ZMM, a1, p1l, recordExternalAddress(crb, POLY1305_MASK44));

        EVPSRLQ.emit(masm, AVXSize.ZMM, tmp, p1l, 44);
        EVPSLLQ.emit(masm, AVXSize.ZMM, p1h, p1h, 8);
        EVPADDQ.emit(masm, AVXSize.ZMM, p1h, p1h, tmp);
        EVPADDQ.emit(masm, AVXSize.ZMM, p2l, p2l, p1h);
        EVPANDQ.emit(masm, AVXSize.ZMM, a2, p2l, recordExternalAddress(crb, POLY1305_MASK42));

        EVPSRLQ.emit(masm, AVXSize.ZMM, tmp, p2l, 42);
        EVPSLLQ.emit(masm, AVXSize.ZMM, p2h, p2h, 10);
        EVPADDQ.emit(masm, AVXSize.ZMM, p2h, p2h, tmp);

        // Reduction: p2->a0->a1
        // Multiply by 5 the highest bits (p2 is above 130 bits)
        EVPADDQ.emit(masm, AVXSize.ZMM, a0, a0, p2h);
        EVPSLLQ.emit(masm, AVXSize.ZMM, p2h, p2h, 2);
        EVPADDQ.emit(masm, AVXSize.ZMM, a0, a0, p2h);
        EVPSRLQ.emit(masm, AVXSize.ZMM, tmp, a0, 44);
        EVPANDQ.emit(masm, AVXSize.ZMM, a0, a0, recordExternalAddress(crb, POLY1305_MASK44));
        EVPADDQ.emit(masm, AVXSize.ZMM, a1, a1, tmp);
    }

    // @formatter:off
    // Compute component-wise product for 4 16-byte message  blocks,
    // i.e. For each block, compute [a2 a1 a0] = [a2 a1 a0] x [r2 r1 r0]
    //
    // Each block/number is represented by 3 44-bit limb digits, start with multiplication
    //
    //      a2       a1       a0
    // x    r2       r1       r0
    // ----------------------------------
    //     a2xr0    a1xr0    a0xr0
    // +   a1xr1    a0xr1  5xa2xr1'     (r1' = r1<<2)
    // +   a0xr2  5xa2xr2' 5xa1xr2'     (r2' = r2<<2)
    // ----------------------------------
    //        p2       p1       p0
    //
    // @formatter:on
    private static void poly1305MulReduceVec4AVX2(CompilationResultBuilder crb,
                    AMD64MacroAssembler masm,
                    Register a0,
                    Register a1,
                    Register a2,
                    Register r0,
                    Register r1,
                    Register r2,
                    Register r1p,
                    Register r2p,
                    Register p0l,
                    Register p0h,
                    Register p1l,
                    Register p1h,
                    Register p2l,
                    Register p2h,
                    Register ytmp1) {
        // Reset accumulator
        masm.vpxor(p0l, p0l, p0l, AVXSize.YMM);
        masm.vpxor(p0h, p0h, p0h, AVXSize.YMM);
        masm.vpxor(p1l, p1l, p1l, AVXSize.YMM);
        masm.vpxor(p1h, p1h, p1h, AVXSize.YMM);
        masm.vpxor(p2l, p2l, p2l, AVXSize.YMM);
        masm.vpxor(p2h, p2h, p2h, AVXSize.YMM);

        // Calculate partial products
        // p0 = a2xr1'
        // p1 = a2xr2'
        // p0 += a0xr0
        masm.vpmadd52luq(p0l, a2, r1p, AVXSize.YMM);
        masm.vpmadd52huq(p0h, a2, r1p, AVXSize.YMM);

        masm.vpmadd52luq(p1l, a2, r2p, AVXSize.YMM);
        masm.vpmadd52huq(p1h, a2, r2p, AVXSize.YMM);

        masm.vpmadd52luq(p0l, a0, r0, AVXSize.YMM);
        masm.vpmadd52huq(p0h, a0, r0, AVXSize.YMM);

        // p2 = a2xr0
        // p1 += a0xr1
        // p0 += a1xr2'
        // p2 += a0Xr2
        masm.vpmadd52luq(p2l, a2, r0, AVXSize.YMM);
        masm.vpmadd52huq(p2h, a2, r0, AVXSize.YMM);

        masm.vpmadd52luq(p1l, a0, r1, AVXSize.YMM);
        masm.vpmadd52huq(p1h, a0, r1, AVXSize.YMM);

        masm.vpmadd52luq(p0l, a1, r2p, AVXSize.YMM);
        masm.vpmadd52huq(p0h, a1, r2p, AVXSize.YMM);

        masm.vpmadd52luq(p2l, a0, r2, AVXSize.YMM);
        masm.vpmadd52huq(p2h, a0, r2, AVXSize.YMM);

        // Carry propgation (first pass)
        masm.vpsrlq(ytmp1, p0l, 44, AVXSize.YMM);
        masm.vpsllq(p0h, p0h, 8, AVXSize.YMM);
        masm.vpmadd52luq(p1l, a1, r0, AVXSize.YMM);
        masm.vpmadd52huq(p1h, a1, r0, AVXSize.YMM);
        // Carry propagation (first pass) - continue
        VPAND.emit(masm, AVXSize.YMM, a0, p0l, recordExternalAddress(crb, POLY1305_MASK44));
        masm.vpaddq(p0h, p0h, ytmp1, AVXSize.YMM);
        masm.vpmadd52luq(p2l, a1, r1, AVXSize.YMM);
        masm.vpmadd52huq(p2h, a1, r1, AVXSize.YMM);

        // Carry propagation (first pass) - continue 2
        masm.vpaddq(p1l, p1l, p0h, AVXSize.YMM);
        masm.vpsllq(p1h, p1h, 8, AVXSize.YMM);
        masm.vpsrlq(ytmp1, p1l, 44, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, a1, p1l, recordExternalAddress(crb, POLY1305_MASK44));

        masm.vpaddq(p2l, p2l, p1h, AVXSize.YMM);
        masm.vpaddq(p2l, p2l, ytmp1, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, a2, p2l, recordExternalAddress(crb, POLY1305_MASK42));
        masm.vpsrlq(ytmp1, p2l, 42, AVXSize.YMM);
        masm.vpsllq(p2h, p2h, 10, AVXSize.YMM);
        masm.vpaddq(p2h, p2h, ytmp1, AVXSize.YMM);

        // Carry propagation (second pass)
        // Multiply by 5 the highest bits (above 130 bits)
        masm.vpaddq(a0, a0, p2h, AVXSize.YMM);
        masm.vpsllq(p2h, p2h, 2, AVXSize.YMM);
        masm.vpaddq(a0, a0, p2h, AVXSize.YMM);

        masm.vpsrlq(ytmp1, a0, 44, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, a0, a0, recordExternalAddress(crb, POLY1305_MASK44));
        masm.vpaddq(a1, a1, ytmp1, AVXSize.YMM);
    }

    // @formatter:off
    // Compute component-wise product for 4 16-byte message  blocks and adds the next 4 blocks
    // i.e. For each block, compute [a2 a1 a0] = [a2 a1 a0] x [r2 r1 r0],
    // followed by [a2 a1 a0] += [n2 n1 n0], where n contains the next 4 blocks of the message.
    //
    // Each block/number is represented by 3 44-bit limb digits, start with multiplication
    //
    //      a2       a1       a0
    // x    r2       r1       r0
    // ----------------------------------
    //     a2xr0    a1xr0    a0xr0
    // +   a1xr1    a0xr1  5xa2xr1'     (r1' = r1<<2)
    // +   a0xr2  5xa2xr2' 5xa1xr2'     (r2' = r2<<2)
    // ----------------------------------
    //        p2       p1       p0
    //
    // @formatter:on
    private static void poly1305MsgMulReduceVec4AVX2(CompilationResultBuilder crb,
                    AMD64MacroAssembler masm,
                    Register a0,
                    Register a1,
                    Register a2,
                    AMD64Address r0,
                    AMD64Address r1,
                    AMD64Address r2,
                    AMD64Address r1p,
                    AMD64Address r2p,
                    Register p0l,
                    Register p0h,
                    Register p1l,
                    Register p1h,
                    Register p2l,
                    Register p2h,
                    Register ytmp1,
                    Register ytmp2,
                    Register ytmp3,
                    Register ytmp4,
                    Register ytmp5,
                    Register ytmp6,
                    Register input,
                    Register length) {
        // Reset accumulator
        masm.vpxor(p0l, p0l, p0l, AVXSize.YMM);
        masm.vpxor(p0h, p0h, p0h, AVXSize.YMM);
        masm.vpxor(p1l, p1l, p1l, AVXSize.YMM);
        masm.vpxor(p1h, p1h, p1h, AVXSize.YMM);
        masm.vpxor(p2l, p2l, p2l, AVXSize.YMM);
        masm.vpxor(p2h, p2h, p2h, AVXSize.YMM);

        // Calculate partial products
        // p0 = a2xr1'
        // p1 = a2xr2'
        // p2 = a2xr0
        masm.vpmadd52luq(p0l, a2, r1p, AVXSize.YMM);
        masm.vpmadd52huq(p0h, a2, r1p, AVXSize.YMM);
        // Interleave input loading with hash computation
        masm.leaq(input, new AMD64Address(input, 16 * 4));
        masm.subl(length, 16 * 4);
        masm.vpmadd52luq(p1l, a2, r2p, AVXSize.YMM);
        masm.vpmadd52huq(p1h, a2, r2p, AVXSize.YMM);
        // Load next block of data (64 bytes)
        masm.vmovdqu(ytmp1, new AMD64Address(input, 0));
        masm.vmovdqu(ytmp2, new AMD64Address(input, 32));
        // interleave new blocks of data
        VPUNPCKHQDQ.emit(masm, AVXSize.YMM, ytmp3, ytmp1, ytmp2);
        VPUNPCKLQDQ.emit(masm, AVXSize.YMM, ytmp1, ytmp1, ytmp2);
        masm.vpmadd52luq(p0l, a0, r0, AVXSize.YMM);
        masm.vpmadd52huq(p0h, a0, r0, AVXSize.YMM);
        // Highest 42-bit limbs of new blocks
        masm.vpsrlq(ytmp6, ytmp3, 24, AVXSize.YMM);
        VPOR.emit(masm, AVXSize.YMM, ytmp6, ytmp6, recordExternalAddress(crb, POLY1305_PAD_MSG));

        // Middle 44-bit limbs of new blocks
        masm.vpsrlq(ytmp2, ytmp1, 44, AVXSize.YMM);
        masm.vpsllq(ytmp4, ytmp3, 20, AVXSize.YMM);
        // p2 = a2xr0
        masm.vpmadd52luq(p2l, a2, r0, AVXSize.YMM);
        masm.vpmadd52huq(p2h, a2, r0, AVXSize.YMM);
        masm.vpor(ytmp2, ytmp2, ytmp4, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, ytmp2, ytmp2, recordExternalAddress(crb, POLY1305_MASK44));
        // Lowest 44-bit limbs of new blocks
        VPAND.emit(masm, AVXSize.YMM, ytmp1, ytmp1, recordExternalAddress(crb, POLY1305_MASK44));

        masm.vpmadd52luq(p1l, a0, r1, AVXSize.YMM);
        masm.vpmadd52huq(p1h, a0, r1, AVXSize.YMM);
        masm.vpmadd52luq(p0l, a1, r2p, AVXSize.YMM);
        masm.vpmadd52huq(p0h, a1, r2p, AVXSize.YMM);
        masm.vpmadd52luq(p2l, a0, r2, AVXSize.YMM);
        masm.vpmadd52huq(p2h, a0, r2, AVXSize.YMM);

        // Carry propgation (first pass)
        masm.vpsrlq(ytmp5, p0l, 44, AVXSize.YMM);
        masm.vpsllq(p0h, p0h, 8, AVXSize.YMM);
        masm.vpmadd52luq(p1l, a1, r0, AVXSize.YMM);
        masm.vpmadd52huq(p1h, a1, r0, AVXSize.YMM);
        // Carry propagation (first pass) - continue
        VPAND.emit(masm, AVXSize.YMM, a0, p0l, recordExternalAddress(crb, POLY1305_MASK44));
        masm.vpaddq(p0h, p0h, ytmp5, AVXSize.YMM);
        masm.vpmadd52luq(p2l, a1, r1, AVXSize.YMM);
        masm.vpmadd52huq(p2h, a1, r1, AVXSize.YMM);

        // Carry propagation (first pass) - continue 2
        masm.vpaddq(p1l, p1l, p0h, AVXSize.YMM);
        masm.vpsllq(p1h, p1h, 8, AVXSize.YMM);
        masm.vpsrlq(ytmp5, p1l, 44, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, a1, p1l, recordExternalAddress(crb, POLY1305_MASK44));

        masm.vpaddq(p2l, p2l, p1h, AVXSize.YMM);
        masm.vpaddq(p2l, p2l, ytmp5, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, a2, p2l, recordExternalAddress(crb, POLY1305_MASK42));
        masm.vpaddq(a2, a2, ytmp6, AVXSize.YMM);
        masm.vpsrlq(ytmp5, p2l, 42, AVXSize.YMM);
        masm.vpsllq(p2h, p2h, 10, AVXSize.YMM);
        masm.vpaddq(p2h, p2h, ytmp5, AVXSize.YMM);

        // Carry propagation (second pass)
        // Multiply by 5 the highest bits (above 130 bits)
        masm.vpaddq(a0, a0, p2h, AVXSize.YMM);
        masm.vpsllq(p2h, p2h, 2, AVXSize.YMM);
        masm.vpaddq(a0, a0, p2h, AVXSize.YMM);

        masm.vpsrlq(ytmp5, a0, 44, AVXSize.YMM);
        VPAND.emit(masm, AVXSize.YMM, a0, a0, recordExternalAddress(crb, POLY1305_MASK44));
        // Add low 42-bit bits from new blocks to accumulator
        masm.vpaddq(a0, a0, ytmp1, AVXSize.YMM);
        // Add medium 42-bit bits from new blocks to accumulator
        masm.vpaddq(a1, a1, ytmp2, AVXSize.YMM);
        masm.vpaddq(a1, a1, ytmp5, AVXSize.YMM);
    }

    private static void poly1305MultiplyScalar(AMD64MacroAssembler masm,
                    Register a0,
                    Register a1,
                    Register a2,
                    Register r0,
                    Register r1,
                    Register c1,
                    boolean only128,
                    Register t0,
                    Register t1,
                    Register t2,
                    Register mulql,
                    Register mulqh) {
        masm.movq(mulql, r1);
        masm.mulq(a0);
        masm.movq(t1, mulql);
        masm.movq(t2, mulqh);

        masm.movq(mulql, r0);
        masm.mulq(a0);
        masm.movq(a0, mulql);
        masm.movq(t0, mulqh);

        masm.movq(mulql, r0);
        masm.mulq(a1);
        masm.addq(t1, mulql);
        masm.adcq(t2, mulqh);

        masm.movq(mulql, c1);
        masm.mulq(a1);
        masm.addq(a0, mulql);
        masm.adcq(t0, mulqh);

        if (only128) {
            masm.movq(a1, t0);
            masm.addq(a1, t1);
            masm.adcq(t2, 0);
        } else {
            masm.movq(a1, a2);
            masm.imulq(a1, c1);
            masm.addq(t1, a1);
            masm.adcq(t2, 0);

            masm.movq(a1, t0);
            masm.imulq(a2, r0);
            masm.addq(a1, t1);
            masm.adcq(t2, a2);
        }

        masm.movq(t0, t2);
        masm.movl(a2, t2);
        masm.andq(t0, ~3);
        masm.shrq(t2, 2);
        masm.addq(t0, t2);
        masm.andl(a2, 3);

        masm.addq(a0, t0);
        masm.adcq(a1, 0);
        masm.adcl(a2, 0);
    }

    private static void poly1305Limbs(AMD64MacroAssembler masm,
                    Register limbs,
                    Register a0,
                    Register a1,
                    Register a2,
                    Register t0,
                    Register t1) {
        masm.movq(a0, new AMD64Address(limbs, 0));
        masm.movq(t0, new AMD64Address(limbs, 8));
        masm.shlq(t0, 26);
        masm.addq(a0, t0);
        masm.movq(t0, new AMD64Address(limbs, 16));
        masm.movq(t1, new AMD64Address(limbs, 24));
        masm.movq(a1, t0);
        masm.shlq(t0, 52);
        masm.shrq(a1, 12);
        masm.shlq(t1, 14);
        masm.addq(a0, t0);
        masm.adcq(a1, t1);
        masm.movq(t0, new AMD64Address(limbs, 32));
        if (!Register.None.equals(a2)) {
            masm.movq(a2, t0);
            masm.shrq(a2, 24);
        }
        masm.shlq(t0, 40);
        masm.addq(a1, t0);
        if (!Register.None.equals(a2)) {
            masm.adcq(a2, 0);

            masm.movq(t0, a2);
            masm.andq(t0, ~3);
            masm.andq(a2, 3);
            masm.movq(t1, t0);
            masm.shrq(t1, 2);
            masm.addq(t0, t1);

            masm.addq(a0, t0);
            masm.adcq(a1, 0);
            masm.adcq(a2, 0);
        }
    }

    private static void poly1305LimbsOut(AMD64MacroAssembler masm,
                    Register a0,
                    Register a1,
                    Register a2,
                    Register limbs,
                    Register t0,
                    Register t1) {
        masm.movq(t0, a2);
        masm.andq(t0, ~3);
        masm.andq(a2, 3);
        masm.movq(t1, t0);
        masm.shrq(t1, 2);
        masm.addq(t0, t1);

        masm.addq(a0, t0);
        masm.adcq(a1, 0);
        masm.adcq(a2, 0);

        masm.movl(t0, a0);
        masm.andl(t0, 0x3ffffff);
        masm.movq(new AMD64Address(limbs, 0), t0);

        masm.shrq(a0, 26);
        masm.movl(t0, a0);
        masm.andl(t0, 0x3ffffff);
        masm.movq(new AMD64Address(limbs, 8), t0);

        masm.shrq(a0, 26);
        masm.movl(t0, a1);
        masm.shll(t0, 12);
        masm.addl(t0, a0);
        masm.andl(t0, 0x3ffffff);
        masm.movq(new AMD64Address(limbs, 16), t0);

        masm.shrq(a1, 14);
        masm.shlq(a2, 50);
        masm.addq(a1, a2);

        masm.movl(t0, a1);
        masm.andl(t0, 0x3ffffff);
        masm.movq(new AMD64Address(limbs, 24), t0);

        masm.shrq(a1, 26);
        masm.movl(t0, a1);
        masm.movq(new AMD64Address(limbs, 32), t0);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
