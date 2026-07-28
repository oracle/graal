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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.AboveEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.BelowEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotEqual;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
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
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/18bcbf7941f7567449983b3f317401efb3e34d39/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L1634-L1680",
          sha1 = "2c80c5744293a2f52c282898101c701790fd23df")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/18bcbf7941f7567449983b3f317401efb3e34d39/src/hotspot/cpu/x86/macroAssembler_x86_sha.cpp#L1010-L1493",
          sha1 = "a13f01c5f15f95cbdb6acb082866aa3f14bc94b4")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/18bcbf7941f7567449983b3f317401efb3e34d39/src/hotspot/cpu/x86/macroAssembler_x86_sha.cpp#L1496-L1672",
          sha1 = "09d8ac6f9fe48a599d5315f49802654cc0005c28")
// @formatter:on
public final class AMD64SHA512Op extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64SHA512Op> TYPE = LIRInstructionClass.create(AMD64SHA512Op.class);

    @Use({OperandFlag.REG}) private Value bufValue;
    @Use({OperandFlag.REG}) private Value stateValue;

    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsValue;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value limitValue;

    @Def({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    private final boolean multiBlock;

    public AMD64SHA512Op(AllocatableValue bufValue, AllocatableValue stateValue) {
        this(bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AMD64SHA512Op(AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue, AllocatableValue limitValue, AllocatableValue resultValue, boolean multiBlock) {
        super(TYPE);

        GraalError.guarantee(asRegister(bufValue).equals(rdi), "expect bufValue at rdi, but was %s", bufValue);
        GraalError.guarantee(asRegister(stateValue).equals(rsi), "expect stateValue at rsi, but was %s", stateValue);
        GraalError.guarantee(!multiBlock || asRegister(ofsValue).equals(rdx), "expect ofsValue at rdx, but was %s", ofsValue);
        GraalError.guarantee(!multiBlock || asRegister(limitValue).equals(rcx), "expect limitValue at rcx, but was %s", limitValue);
        GraalError.guarantee(!multiBlock || asRegister(resultValue).equals(rax), "expect resultValue at rax, but was %s", resultValue);
        GraalError.guarantee(!multiBlock || resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;
        this.resultValue = resultValue;

        this.multiBlock = multiBlock;

        // rbp, rbx, r12-r15 will be restored. vzeroupper clears upper bits of xmm0-xmm15.
        this.temps = multiBlock ? new Value[]{
                        rcx.asValue(),
                        rdx.asValue(),
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
        }
                        : new Value[]{
                                        rax.asValue(),
                                        rcx.asValue(),
                                        rdx.asValue(),
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
    }

    static ArrayDataPointerConstant k512W = pointerConstant(16, new long[]{
            // @formatter:off
            0x428a2f98d728ae22L, 0x7137449123ef65cdL,
            0xb5c0fbcfec4d3b2fL, 0xe9b5dba58189dbbcL,
            0x3956c25bf348b538L, 0x59f111f1b605d019L,
            0x923f82a4af194f9bL, 0xab1c5ed5da6d8118L,
            0xd807aa98a3030242L, 0x12835b0145706fbeL,
            0x243185be4ee4b28cL, 0x550c7dc3d5ffb4e2L,
            0x72be5d74f27b896fL, 0x80deb1fe3b1696b1L,
            0x9bdc06a725c71235L, 0xc19bf174cf692694L,
            0xe49b69c19ef14ad2L, 0xefbe4786384f25e3L,
            0x0fc19dc68b8cd5b5L, 0x240ca1cc77ac9c65L,
            0x2de92c6f592b0275L, 0x4a7484aa6ea6e483L,
            0x5cb0a9dcbd41fbd4L, 0x76f988da831153b5L,
            0x983e5152ee66dfabL, 0xa831c66d2db43210L,
            0xb00327c898fb213fL, 0xbf597fc7beef0ee4L,
            0xc6e00bf33da88fc2L, 0xd5a79147930aa725L,
            0x06ca6351e003826fL, 0x142929670a0e6e70L,
            0x27b70a8546d22ffcL, 0x2e1b21385c26c926L,
            0x4d2c6dfc5ac42aedL, 0x53380d139d95b3dfL,
            0x650a73548baf63deL, 0x766a0abb3c77b2a8L,
            0x81c2c92e47edaee6L, 0x92722c851482353bL,
            0xa2bfe8a14cf10364L, 0xa81a664bbc423001L,
            0xc24b8b70d0f89791L, 0xc76c51a30654be30L,
            0xd192e819d6ef5218L, 0xd69906245565a910L,
            0xf40e35855771202aL, 0x106aa07032bbd1b8L,
            0x19a4c116b8d2d0c8L, 0x1e376c085141ab53L,
            0x2748774cdf8eeb99L, 0x34b0bcb5e19b48a8L,
            0x391c0cb3c5c95a63L, 0x4ed8aa4ae3418acbL,
            0x5b9cca4f7763e373L, 0x682e6ff3d6b2b8a3L,
            0x748f82ee5defb2fcL, 0x78a5636f43172f60L,
            0x84c87814a1f0ab72L, 0x8cc702081a6439ecL,
            0x90befffa23631e28L, 0xa4506cebde82bde9L,
            0xbef9a3f7b2c67915L, 0xc67178f2e372532bL,
            0xca273eceea26619cL, 0xd186b8c721c0c207L,
            0xeada7dd6cde0eb1eL, 0xf57d4f7fee6ed178L,
            0x06f067aa72176fbaL, 0x0a637dc5a2c898a6L,
            0x113f9804bef90daeL, 0x1b710b35131c471bL,
            0x28db77f523047d84L, 0x32caab7b40c72493L,
            0x3c9ebe0a15c9bebcL, 0x431d67c49c100d4cL,
            0x4cc5d4becb3e42b6L, 0x597f299cfc657e2aL,
            0x5fcb6fab3ad6faecL, 0x6c44198c4a475817L,
            // @formatter:on
    });

    static ArrayDataPointerConstant pshuffleByteFlipMaskSha512 = pointerConstant(16, new long[]{
            // @formatter:off
            0x0001020304050607L,
            0x08090a0b0c0d0e0fL,
            0x1011121314151617L,
            0x18191a1b1c1d1e1fL,
            // @formatter:on
    });

    static ArrayDataPointerConstant ymmMask = pointerConstant(16, new long[]{
            // @formatter:off
            0x0000000000000000L,
            0x0000000000000000L,
            0xFFFFFFFFFFFFFFFFL,
            0xFFFFFFFFFFFFFFFFL,
            // @formatter:on
    });

    // Local variables as defined in assembly file.
    private static final int XFER_SIZE = 4 * 8; // resq 4 => reserve 4 quadwords. Hence 4 * 8
    private static final int SRND_SIZE = 8; // resq 1
    private static final int INP_SIZE = 8;
    private static final int INP_END_SIZE = 8;
    private static final int RSP_SAVE_SIZE = 8; // defined as resq 1

    private static final int GPR_SAVE_SIZE = 6 * 8; // resq 6

    private static final int OFFSET_XFER = 0;
    private static final int OFFSET_SRND = OFFSET_XFER + XFER_SIZE; // 32
    private static final int OFFSET_INP = OFFSET_SRND + SRND_SIZE; // 40
    private static final int OFFSET_INP_END = OFFSET_INP + INP_SIZE; // 48
    private static final int OFFSET_RSP = OFFSET_INP_END + INP_END_SIZE; // 56
    private static final int OFFSET_GPR = OFFSET_RSP + RSP_SAVE_SIZE; // 64
    private static final int STACK_SIZE = OFFSET_GPR + GPR_SAVE_SIZE; // 112

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (masm.supports(CPUFeature.SHA512)) {
            emitSHA512NIX1(crb, masm);
        } else {
            emitAVX2Bmi2Code(crb, masm);
        }
        masm.vzeroupper();
    }

    // Implemented using Intel IpSec implementation (intel-ipsec-mb on github)
    private void emitSHA512NIX1(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelDoneHash = new Label();
        Label labelBlockLoop = new Label();

        Register argHash = rsi;
        Register argMsg = rdi;
        Register ofs = rdx;
        Register limit = rcx;

        masm.vpbroadcasti128(xmm15, recordExternalAddress(crb, pshuffleByteFlipMaskSha512));

        // load current hash value and transform
        masm.vmovdqu(xmm0, new AMD64Address(argHash));
        masm.vmovdqu(xmm1, new AMD64Address(argHash, 32));
        // ymm0 = D C B A, ymm1 = H G F E
        masm.vperm2i128(xmm2, xmm0, xmm1, 0x20);
        masm.vperm2i128(xmm3, xmm0, xmm1, 0x31);
        // ymm2 = F E B A, ymm3 = H G D C
        masm.vpermq(xmm13, xmm2, 0x1b, AVXSize.YMM);
        masm.vpermq(xmm14, xmm3, 0x1b, AVXSize.YMM);
        // ymm13 = A B E F, ymm14 = C D G H

        masm.leaq(rax, recordExternalAddress(crb, k512W));
        masm.align(16);
        masm.bind(labelBlockLoop);
        masm.vmovdqu(xmm11, xmm13); // ABEF
        masm.vmovdqu(xmm12, xmm14); // CDGH

        // R0 - R3
        masm.vmovdqu(xmm0, new AMD64Address(argMsg, 0 * 32));
        masm.vpshufb(xmm3, xmm0, xmm15, AVXSize.YMM); // ymm0 / ymm3 = W[0..3]
        masm.vpaddq(xmm0, xmm3, new AMD64Address(rax, 0 * 32), AVXSize.YMM);
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);

        // R4 - R7
        masm.vmovdqu(xmm0, new AMD64Address(argMsg, 1 * 32));
        masm.vpshufb(xmm4, xmm0, xmm15, AVXSize.YMM); // ymm0 / ymm4 = W[4..7]
        masm.vpaddq(xmm0, xmm4, new AMD64Address(rax, 1 * 32), AVXSize.YMM);
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);
        masm.sha512msg1(xmm3, xmm4); // ymm3 = W[0..3] + S0(W[1..4])

        // R8 - R11
        masm.vmovdqu(xmm0, new AMD64Address(argMsg, 2 * 32));
        masm.vpshufb(xmm5, xmm0, xmm15, AVXSize.YMM); // ymm0 / ymm5 = W[8..11]
        masm.vpaddq(xmm0, xmm5, new AMD64Address(rax, 2 * 32), AVXSize.YMM);
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);
        masm.sha512msg1(xmm4, xmm5); // ymm4 = W[4..7] + S0(W[5..8])

        // R12 - R15
        masm.vmovdqu(xmm0, new AMD64Address(argMsg, 3 * 32));
        masm.vpshufb(xmm6, xmm0, xmm15, AVXSize.YMM); // ymm0 / ymm6 = W[12..15]
        masm.vpaddq(xmm0, xmm6, new AMD64Address(rax, 3 * 32), AVXSize.YMM);
        masm.vpermq(xmm8, xmm6, 0x1b, AVXSize.YMM); // ymm8 = W[12] W[13] W[14] W[15]
        masm.vpermq(xmm9, xmm5, 0x39, AVXSize.YMM); // ymm9 = W[8] W[11] W[10] W[9]
        masm.vpblendd(xmm8, xmm8, xmm9, 0x3f, AVXSize.YMM); // ymm8 = W[12] W[11] W[10] W[9]
        masm.vpaddq(xmm3, xmm3, xmm8, AVXSize.YMM);
        masm.sha512msg2(xmm3, xmm6); // W[16..19] = xmm3 + W[9..12] + S1(W[14..17])
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);
        masm.sha512msg1(xmm5, xmm6); // ymm5 = W[8..11] + S0(W[9..12])

        // R16 - R19, R32 - R35, R48 - R51
        for (int i = 4, j = 3; j > 0; j--) {
            masm.vpaddq(xmm0, xmm3, new AMD64Address(rax, i * 32), AVXSize.YMM);
            masm.vpermq(xmm8, xmm3, 0x1b, AVXSize.YMM); // ymm8 = W[16] W[17] W[18] W[19]
            masm.vpermq(xmm9, xmm6, 0x39, AVXSize.YMM); // ymm9 = W[12] W[15] W[14] W[13]
            masm.vpblendd(xmm7, xmm8, xmm9, 0x3f, AVXSize.YMM); // xmm7 = W[16] W[15] W[14] W[13]
            masm.vpaddq(xmm4, xmm4, xmm7, AVXSize.YMM); // ymm4 = W[4..7] + S0(W[5..8]) + W[13..16]
            masm.sha512msg2(xmm4, xmm3); // ymm4 += S1(W[14..17])
            masm.sha512rnds2(xmm12, xmm11, xmm0);
            masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
            masm.sha512rnds2(xmm11, xmm12, xmm0);
            masm.sha512msg1(xmm6, xmm3); // ymm6 = W[12..15] + S0(W[13..16])
            i += 1;
            // R20 - R23, R36 - R39, R52 - R55
            masm.vpaddq(xmm0, xmm4, new AMD64Address(rax, i * 32), AVXSize.YMM);
            masm.vpermq(xmm8, xmm4, 0x1b, AVXSize.YMM); // ymm8 = W[20] W[21] W[22] W[23]
            masm.vpermq(xmm9, xmm3, 0x39, AVXSize.YMM); // ymm9 = W[16] W[19] W[18] W[17]
            masm.vpblendd(xmm7, xmm8, xmm9, 0x3f, AVXSize.YMM); // ymm7 = W[20] W[19] W[18] W[17]
            masm.vpaddq(xmm5, xmm5, xmm7, AVXSize.YMM); // ymm5 = W[8..11] + S0(W[9..12]) + W[17..20]
            masm.sha512msg2(xmm5, xmm4); // ymm5 += S1(W[18..21])
            masm.sha512rnds2(xmm12, xmm11, xmm0);
            masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
            masm.sha512rnds2(xmm11, xmm12, xmm0);
            masm.sha512msg1(xmm3, xmm4); // ymm3 = W[16..19] + S0(W[17..20])
            i += 1;
            // R24 - R27, R40 - R43, R56 - R59
            masm.vpaddq(xmm0, xmm5, new AMD64Address(rax, i * 32), AVXSize.YMM);
            masm.vpermq(xmm8, xmm5, 0x1b, AVXSize.YMM); // ymm8 = W[24] W[25] W[26] W[27]
            masm.vpermq(xmm9, xmm4, 0x39, AVXSize.YMM); // ymm9 = W[20] W[23] W[22] W[21]
            masm.vpblendd(xmm7, xmm8, xmm9, 0x3f, AVXSize.YMM); // ymm7 = W[24] W[23] W[22] W[21]
            masm.vpaddq(xmm6, xmm6, xmm7, AVXSize.YMM); // ymm6 = W[12..15] + S0(W[13..16]) + W[21..24]
            masm.sha512msg2(xmm6, xmm5); // ymm6 += S1(W[22..25])
            masm.sha512rnds2(xmm12, xmm11, xmm0);
            masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
            masm.sha512rnds2(xmm11, xmm12, xmm0);
            masm.sha512msg1(xmm4, xmm5); // ymm4 = W[20..23] + S0(W[21..24])
            i += 1;
            // R28 - R31, R44 - R47, R60 - R63
            masm.vpaddq(xmm0, xmm6, new AMD64Address(rax, i * 32), AVXSize.YMM);
            masm.vpermq(xmm8, xmm6, 0x1b, AVXSize.YMM); // ymm8 = W[28] W[29] W[30] W[31]
            masm.vpermq(xmm9, xmm5, 0x39, AVXSize.YMM); // ymm9 = W[24] W[27] W[26] W[25]
            masm.vpblendd(xmm7, xmm8, xmm9, 0x3f, AVXSize.YMM); // ymm7 = W[28] W[27] W[26] W[25]
            masm.vpaddq(xmm3, xmm3, xmm7, AVXSize.YMM); // ymm3 = W[16..19] + S0(W[17..20]) + W[25..28]
            masm.sha512msg2(xmm3, xmm6); // ymm3 += S1(W[26..29])
            masm.sha512rnds2(xmm12, xmm11, xmm0);
            masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
            masm.sha512rnds2(xmm11, xmm12, xmm0);
            masm.sha512msg1(xmm5, xmm6); // ymm5 = W[24..27] + S0(W[25..28])
            i += 1;
        }
        // R64 - R67
        masm.vpaddq(xmm0, xmm3, new AMD64Address(rax, 16 * 32), AVXSize.YMM);
        masm.vpermq(xmm8, xmm3, 0x1b, AVXSize.YMM); // ymm8 = W[64] W[65] W[66] W[67]
        masm.vpermq(xmm9, xmm6, 0x39, AVXSize.YMM); // ymm9 = W[60] W[63] W[62] W[61]
        masm.vpblendd(xmm7, xmm8, xmm9, 0x3f, AVXSize.YMM); // ymm7 = W[64] W[63] W[62] W[61]
        masm.vpaddq(xmm4, xmm4, xmm7, AVXSize.YMM); // ymm4 = W[52..55] + S0(W[53..56]) + W[61..64]
        masm.sha512msg2(xmm4, xmm3); // ymm4 += S1(W[62..65])
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);
        masm.sha512msg1(xmm6, xmm3); // ymm6 = W[60..63] + S0(W[61..64])

        // R68 - R71
        masm.vpaddq(xmm0, xmm4, new AMD64Address(rax, 17 * 32), AVXSize.YMM);
        masm.vpermq(xmm8, xmm4, 0x1b, AVXSize.YMM); // ymm8 = W[68] W[69] W[70] W[71]
        masm.vpermq(xmm9, xmm3, 0x39, AVXSize.YMM); // ymm9 = W[64] W[67] W[66] W[65]
        masm.vpblendd(xmm7, xmm8, xmm9, 0x3f, AVXSize.YMM); // ymm7 = W[68] W[67] W[66] W[65]
        masm.vpaddq(xmm5, xmm5, xmm7, AVXSize.YMM); // ymm5 = W[56..59] + S0(W[57..60]) + W[65..68]
        masm.sha512msg2(xmm5, xmm4); // ymm5 += S1(W[66..69])
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);

        // R72 - R75
        masm.vpaddq(xmm0, xmm5, new AMD64Address(rax, 18 * 32), AVXSize.YMM);
        masm.vpermq(xmm8, xmm5, 0x1b, AVXSize.YMM); // ymm8 = W[72] W[73] W[74] W[75]
        masm.vpermq(xmm9, xmm4, 0x39, AVXSize.YMM); // ymm9 = W[68] W[71] W[70] W[69]
        masm.vpblendd(xmm7, xmm8, xmm9, 0x3f, AVXSize.YMM); // ymm7 = W[72] W[71] W[70] W[69]
        masm.vpaddq(xmm6, xmm6, xmm7, AVXSize.YMM); // ymm6 = W[60..63] + S0(W[61..64]) + W[69..72]
        masm.sha512msg2(xmm6, xmm5); // ymm6 += S1(W[70..73])
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);

        // R76 - R79
        masm.vpaddq(xmm0, xmm6, new AMD64Address(rax, 19 * 32), AVXSize.YMM);
        masm.sha512rnds2(xmm12, xmm11, xmm0);
        masm.vperm2i128(xmm0, xmm0, xmm0, 0x01);
        masm.sha512rnds2(xmm11, xmm12, xmm0);

        // update hash value
        masm.vpaddq(xmm14, xmm14, xmm12, AVXSize.YMM);
        masm.vpaddq(xmm13, xmm13, xmm11, AVXSize.YMM);

        if (multiBlock) {
            masm.addq(argMsg, 4 * 32);
            masm.addq(ofs, 128);
            masm.cmpqAndJcc(ofs, limit, BelowEqual, labelBlockLoop, false);
            masm.movq(rax, ofs); // return ofs
        }

        // store the hash value back in memory
        // xmm13 = ABEF
        // xmm14 = CDGH
        masm.vperm2i128(xmm1, xmm13, xmm14, 0x31);
        masm.vperm2i128(xmm2, xmm13, xmm14, 0x20);
        masm.vpermq(xmm1, xmm1, 0xb1, AVXSize.YMM); // ymm1 = D C B A
        masm.vpermq(xmm2, xmm2, 0xb1, AVXSize.YMM); // ymm2 = H G F E
        masm.vmovdqu(new AMD64Address(argHash, 0 * 32), xmm1);
        masm.vmovdqu(new AMD64Address(argHash, 1 * 32), xmm2);

        masm.bind(labelDoneHash);
    }

    private void emitAVX2Bmi2Code(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelLoop0 = new Label();
        Label labelLoop1 = new Label();
        Label labelLoop2 = new Label();
        Label labelDoneHash = new Label();
        Label labelComputeBlockSize = new Label();
        Label labelComputeSize = new Label();
        Label labelComputeBlockSizeEnd = new Label();
        Label labelComputeSizeEnd = new Label();

        Register byteFlipMask = xmm9;
        Register ymmMaskLo = xmm10;

        Register regINP = rdi; // 1st arg
        Register regCTX = rsi; // 2nd arg
        Register regNUMBLKS = rdx; // 3rd arg
        Register offset = rdx;
        Register regInputLimit = rcx;

        Register regTBL = rbp;

        Register a = rax;
        Register b = rbx;
        Register c = rcx;
        Register d = r8;
        Register e = rdx;
        Register f = r9;
        Register g = r10;
        Register h = r11;

        masm.push(rdx);   // this is ofs, need at the end for multiblock calculation
        masm.push(rcx);   // this is the limit

        // Allocate Stack Space
        masm.movq(rax, rsp);
        masm.subq(rsp, STACK_SIZE);
        masm.andq(rsp, -32);
        masm.movq(new AMD64Address(rsp, OFFSET_RSP), rax);

        // Save GPRs
        masm.movq(new AMD64Address(rsp, OFFSET_GPR), rbp);
        masm.movq(new AMD64Address(rsp, (OFFSET_GPR + 8)), rbx);
        masm.movq(new AMD64Address(rsp, (OFFSET_GPR + 16)), r12);
        masm.movq(new AMD64Address(rsp, (OFFSET_GPR + 24)), r13);
        masm.movq(new AMD64Address(rsp, (OFFSET_GPR + 32)), r14);
        masm.movq(new AMD64Address(rsp, (OFFSET_GPR + 40)), r15);

        masm.vpblendd(xmm0, xmm0, xmm1, 0xF0, AVXSize.XMM);
        masm.vpblendd(xmm0, xmm0, xmm1, 0xF0, AVXSize.YMM);

        if (multiBlock) {
            masm.xorq(rax, rax);
            masm.bind(labelComputeBlockSize);
            // Assuming that offset is less than limit.
            masm.cmpqAndJcc(offset, regInputLimit, AboveEqual, labelComputeBlockSizeEnd, true);
            masm.addq(offset, 128);
            masm.addq(rax, 128);
            masm.jmpb(labelComputeBlockSize);

            masm.bind(labelComputeBlockSizeEnd);
            masm.movq(regNUMBLKS, rax);

            masm.cmpqAndJcc(regNUMBLKS, 0, Equal, labelDoneHash, false);
        } else {
            masm.xorq(regNUMBLKS, regNUMBLKS); // If single block.
            masm.addq(regNUMBLKS, 128);
        }

        masm.addq(regNUMBLKS, regINP); // pointer to end of data
        masm.movq(new AMD64Address(rsp, OFFSET_INP_END), regNUMBLKS);

        // load initial digest
        masm.movq(a, new AMD64Address(regCTX, 8 * 0));
        masm.movq(b, new AMD64Address(regCTX, 8 * 1));
        masm.movq(c, new AMD64Address(regCTX, 8 * 2));
        masm.movq(d, new AMD64Address(regCTX, 8 * 3));
        masm.movq(e, new AMD64Address(regCTX, 8 * 4));
        masm.movq(f, new AMD64Address(regCTX, 8 * 5));
        // load g - r10 after it is used as scratch
        masm.movq(h, new AMD64Address(regCTX, 8 * 7));

        // PSHUFFLE_BYTE_FLIP_MASK wrt rip
        masm.vmovdqu(byteFlipMask, recordExternalAddress(crb, pshuffleByteFlipMaskSha512));
        masm.vmovdqu(ymmMaskLo, recordExternalAddress(crb, ymmMask));

        masm.movq(g, new AMD64Address(regCTX, 8 * 6));

        masm.bind(labelLoop0);
        masm.leaq(regTBL, recordExternalAddress(crb, k512W));

        // byte swap first 16 dwords
        masm.vmovdqu(xmm4, new AMD64Address(regINP, 32 * 0));
        masm.vpshufb(xmm4, xmm4, byteFlipMask, AVXSize.YMM);
        masm.vmovdqu(xmm5, new AMD64Address(regINP, 32 * 1));
        masm.vpshufb(xmm5, xmm5, byteFlipMask, AVXSize.YMM);
        masm.vmovdqu(xmm6, new AMD64Address(regINP, 32 * 2));
        masm.vpshufb(xmm6, xmm6, byteFlipMask, AVXSize.YMM);
        masm.vmovdqu(xmm7, new AMD64Address(regINP, 32 * 3));
        masm.vpshufb(xmm7, xmm7, byteFlipMask, AVXSize.YMM);

        masm.movq(new AMD64Address(rsp, OFFSET_INP), regINP);

        masm.movslq(new AMD64Address(rsp, OFFSET_SRND), 4);
        masm.align(16);

        // Schedule 64 input dwords, by calling sha512_AVX2_one_round_and_schedule
        masm.bind(labelLoop1);
        masm.vpaddq(xmm0, xmm4, new AMD64Address(regTBL, 0 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, OFFSET_XFER), xmm0);
        // four rounds and schedule
        sha512AVX2OneRoundAndSchedule(masm, xmm4, xmm5, xmm6, xmm7, a, b, c, d, e, f, g, h, 0);
        sha512AVX2OneRoundAndSchedule(masm, xmm4, xmm5, xmm6, xmm7, h, a, b, c, d, e, f, g, 1);
        sha512AVX2OneRoundAndSchedule(masm, xmm4, xmm5, xmm6, xmm7, g, h, a, b, c, d, e, f, 2);
        sha512AVX2OneRoundAndSchedule(masm, xmm4, xmm5, xmm6, xmm7, f, g, h, a, b, c, d, e, 3);

        masm.vpaddq(xmm0, xmm5, new AMD64Address(regTBL, 1 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, OFFSET_XFER), xmm0);
        // four rounds and schedule
        sha512AVX2OneRoundAndSchedule(masm, xmm5, xmm6, xmm7, xmm4, e, f, g, h, a, b, c, d, 0);
        sha512AVX2OneRoundAndSchedule(masm, xmm5, xmm6, xmm7, xmm4, d, e, f, g, h, a, b, c, 1);
        sha512AVX2OneRoundAndSchedule(masm, xmm5, xmm6, xmm7, xmm4, c, d, e, f, g, h, a, b, 2);
        sha512AVX2OneRoundAndSchedule(masm, xmm5, xmm6, xmm7, xmm4, b, c, d, e, f, g, h, a, 3);

        masm.vpaddq(xmm0, xmm6, new AMD64Address(regTBL, 2 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, OFFSET_XFER), xmm0);
        // four rounds and schedule
        sha512AVX2OneRoundAndSchedule(masm, xmm6, xmm7, xmm4, xmm5, a, b, c, d, e, f, g, h, 0);
        sha512AVX2OneRoundAndSchedule(masm, xmm6, xmm7, xmm4, xmm5, h, a, b, c, d, e, f, g, 1);
        sha512AVX2OneRoundAndSchedule(masm, xmm6, xmm7, xmm4, xmm5, g, h, a, b, c, d, e, f, 2);
        sha512AVX2OneRoundAndSchedule(masm, xmm6, xmm7, xmm4, xmm5, f, g, h, a, b, c, d, e, 3);

        masm.vpaddq(xmm0, xmm7, new AMD64Address(regTBL, 3 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, OFFSET_XFER), xmm0);
        masm.addq(regTBL, 4 * 32);
        // four rounds and schedule
        sha512AVX2OneRoundAndSchedule(masm, xmm7, xmm4, xmm5, xmm6, e, f, g, h, a, b, c, d, 0);
        sha512AVX2OneRoundAndSchedule(masm, xmm7, xmm4, xmm5, xmm6, d, e, f, g, h, a, b, c, 1);
        sha512AVX2OneRoundAndSchedule(masm, xmm7, xmm4, xmm5, xmm6, c, d, e, f, g, h, a, b, 2);
        sha512AVX2OneRoundAndSchedule(masm, xmm7, xmm4, xmm5, xmm6, b, c, d, e, f, g, h, a, 3);

        masm.decq(new AMD64Address(rsp, OFFSET_SRND));
        masm.jcc(NotEqual, labelLoop1);

        masm.movslq(new AMD64Address(rsp, OFFSET_SRND), 2);

        masm.bind(labelLoop2);
        masm.vpaddq(xmm0, xmm4, new AMD64Address(regTBL, 0 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, OFFSET_XFER), xmm0);
        // four rounds and compute.
        sha512AVX2OneRoundCompute(masm, a, a, b, c, d, e, f, g, h, 0);
        sha512AVX2OneRoundCompute(masm, h, h, a, b, c, d, e, f, g, 1);
        sha512AVX2OneRoundCompute(masm, g, g, h, a, b, c, d, e, f, 2);
        sha512AVX2OneRoundCompute(masm, f, f, g, h, a, b, c, d, e, 3);

        masm.vpaddq(xmm0, xmm5, new AMD64Address(regTBL, 1 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, OFFSET_XFER), xmm0);
        masm.addq(regTBL, 2 * 32);
        // four rounds and compute.
        sha512AVX2OneRoundCompute(masm, e, e, f, g, h, a, b, c, d, 0);
        sha512AVX2OneRoundCompute(masm, d, d, e, f, g, h, a, b, c, 1);
        sha512AVX2OneRoundCompute(masm, c, c, d, e, f, g, h, a, b, 2);
        sha512AVX2OneRoundCompute(masm, b, b, c, d, e, f, g, h, a, 3);

        masm.vmovdqu(xmm4, xmm6);
        masm.vmovdqu(xmm5, xmm7);

        masm.decq(new AMD64Address(rsp, OFFSET_SRND));
        masm.jcc(NotEqual, labelLoop2);

        addmq(masm, 8 * 0, regCTX, a);
        addmq(masm, 8 * 1, regCTX, b);
        addmq(masm, 8 * 2, regCTX, c);
        addmq(masm, 8 * 3, regCTX, d);
        addmq(masm, 8 * 4, regCTX, e);
        addmq(masm, 8 * 5, regCTX, f);
        addmq(masm, 8 * 6, regCTX, g);
        addmq(masm, 8 * 7, regCTX, h);

        masm.movq(regINP, new AMD64Address(rsp, OFFSET_INP));
        masm.addq(regINP, 128);
        masm.cmpqAndJcc(regINP, new AMD64Address(rsp, OFFSET_INP_END), NotEqual, labelLoop0, false);

        masm.bind(labelDoneHash);

        // Restore GPRs
        masm.movq(rbp, new AMD64Address(rsp, (OFFSET_GPR + 0)));
        masm.movq(rbx, new AMD64Address(rsp, (OFFSET_GPR + 8)));
        masm.movq(r12, new AMD64Address(rsp, (OFFSET_GPR + 16)));
        masm.movq(r13, new AMD64Address(rsp, (OFFSET_GPR + 24)));
        masm.movq(r14, new AMD64Address(rsp, (OFFSET_GPR + 32)));
        masm.movq(r15, new AMD64Address(rsp, (OFFSET_GPR + 40)));

        // Restore Stack Pointer
        masm.movq(rsp, new AMD64Address(rsp, OFFSET_RSP));

        masm.pop(rcx);
        masm.pop(rdx);

        if (multiBlock) {
            Register limitEnd = rcx;
            Register ofsEnd = rdx;
            masm.movq(rax, ofsEnd);
            masm.bind(labelComputeSize);
            masm.cmpqAndJcc(rax, limitEnd, AboveEqual, labelComputeSizeEnd, true);
            masm.addq(rax, 128);
            masm.jmpb(labelComputeSize);
            masm.bind(labelComputeSizeEnd);
            masm.movl(asRegister(resultValue), rax);
        }
    }

    private static void addmq(AMD64MacroAssembler masm, int disp, Register base, Register value) {
        masm.addq(value, new AMD64Address(base, disp));
        masm.movq(new AMD64Address(base, disp), value);
    }

    private static void sha512AVX2OneRoundCompute(AMD64MacroAssembler masm,
                    Register oldH,
                    Register a,
                    Register b,
                    Register c,
                    Register d,
                    Register e,
                    Register f,
                    Register g,
                    Register h,
                    int iteration) {

        Register y0 = r13;
        Register y1 = r14;
        Register y2 = r15;
        Register y3 = rdi;
        Register t1 = r12;

        if (iteration % 4 > 0) {
            // h = k + w + h + S0 + S1 + CH = t1 + S0;
            masm.addq(oldH, y2);
        }
        // y2 = f; CH
        masm.movq(y2, f);
        // y0 = e >> 41; S1A
        masm.rorxq(y0, e, 41);
        // y1 = e >> 18; S1B
        masm.rorxq(y1, e, 18);
        // y2 = f^g; CH
        masm.xorq(y2, g);

        // y0 = (e >> 41) ^ (e >> 18); S1
        masm.xorq(y0, y1);
        // y1 = (e >> 14); S1
        masm.rorxq(y1, e, 14);
        // y2 = (f^g)&e; CH
        masm.andq(y2, e);

        if (iteration % 4 > 0) {
            // h = t1 + S0 + MAJ
            masm.addq(oldH, y3);
        }
        // y0 = (e >> 41) ^ (e >> 18) ^ (e >> 14); S1
        masm.xorq(y0, y1);
        // t1 = a >> 34; S0B
        masm.rorxq(t1, a, 34);
        // y2 = CH = ((f^g)&e) ^g; CH
        masm.xorq(y2, g);
        // y1 = a >> 39; S0A
        masm.rorxq(y1, a, 39);
        // y3 = a; MAJA
        masm.movq(y3, a);

        // y1 = (a >> 39) ^ (a >> 34); S0
        masm.xorq(y1, t1);
        // t1 = (a >> 28); S0
        masm.rorxq(t1, a, 28);
        // h = k + w + h; --
        masm.addq(h, new AMD64Address(rsp, (8 * iteration)));
        // y3 = a | c; MAJA
        masm.orq(y3, c);

        // y1 = (a >> 39) ^ (a >> 34) ^ (a >> 28); S0
        masm.xorq(y1, t1);
        // t1 = a; MAJB
        masm.movq(t1, a);
        // y3 = (a | c)&b; MAJA
        masm.andq(y3, b);
        // t1 = a&c; MAJB
        masm.andq(t1, c);
        // y2 = S1 + CH; --
        masm.addq(y2, y0);

        // d = k + w + h + d; --
        masm.addq(d, h);
        // y3 = MAJ = (a | c)&b) | (a&c); MAJ
        masm.orq(y3, t1);
        // h = k + w + h + S0; --
        masm.addq(h, y1);

        // d = k + w + h + d + S1 + CH = d + t1; --
        masm.addq(d, y2);

        if (iteration % 4 == 3) {
            // h = k + w + h + S0 + S1 + CH = t1 + S0; --
            masm.addq(h, y2);
            // h = t1 + S0 + MAJ; --
            masm.addq(h, y3);
        }
    }

    private static void sha512AVX2OneRoundAndSchedule(AMD64MacroAssembler masm,
                    Register vector4,
                    Register vector5,
                    Register vector6,
                    Register vector7,
                    Register a,
                    Register b,
                    Register c,
                    Register d,
                    Register e,
                    Register f,
                    Register g,
                    Register h,
                    int iteration) {
        Register y0 = r13;
        Register y1 = r14;
        Register y2 = r15;
        Register y3 = rdi;
        Register t1 = r12;

        if (iteration % 4 == 0) {
            // Extract w[t - 7]
            // xmm0 = W[-7]
            masm.vperm2f128(xmm0, vector7, vector6, 3);
            masm.vpalignr(xmm0, xmm0, vector6, 8, AVXSize.YMM);

            // Calculate w[t - 16] + w[t - 7]
            // xmm0 = W[-7] + W[-16]
            masm.vpaddq(xmm0, xmm0, vector4, AVXSize.YMM);
            // Extract w[t - 15]
            // xmm1 = W[-15]
            masm.vperm2f128(xmm1, vector5, vector4, 3);
            masm.vpalignr(xmm1, xmm1, vector4, 8, AVXSize.YMM);

            // Calculate sigma0
            // Calculate w[t - 15] ror 1
            masm.vpsrlq(xmm2, xmm1, 1, AVXSize.YMM);
            masm.vpsllq(xmm3, xmm1, (64 - 1), AVXSize.YMM);
            // xmm3 = W[-15] ror 1
            masm.vpor(xmm3, xmm3, xmm2, AVXSize.YMM);
            // Calculate w[t - 15] shr 7
            // xmm8 = W[-15] >> 7
            masm.vpsrlq(xmm8, xmm1, 7, AVXSize.YMM);

        } else if (iteration % 4 == 1) {
            // Calculate w[t - 15] ror 8
            masm.vpsrlq(xmm2, xmm1, 8, AVXSize.YMM);
            masm.vpsllq(xmm1, xmm1, (64 - 8), AVXSize.YMM);
            // xmm1 = W[-15] ror 8
            masm.vpor(xmm1, xmm1, xmm2, AVXSize.YMM);

            // XOR the three components
            // xmm3 = W[-15] ror 1 ^ W[-15] >> 7
            masm.vpxor(xmm3, xmm3, xmm8, AVXSize.YMM);
            // xmm1 = s0
            masm.vpxor(xmm1, xmm3, xmm1, AVXSize.YMM);

            // Add three components, w[t - 16], w[t - 7] and sigma0
            // xmm0 = W[-16] + W[-7] + s0
            masm.vpaddq(xmm0, xmm0, xmm1, AVXSize.YMM);

            // Move to appropriate lanes for calculating w[16] and w[17]
            // xmm4 = W[-16] + W[-7] + s0{ BABA }
            masm.vperm2f128(vector4, xmm0, xmm0, 0);

            // Move to appropriate lanes for calculating w[18] and w[19]
            // xmm0 = W[-16] + W[-7] + s0{ DC00 }
            masm.vpand(xmm0, xmm0, xmm10, AVXSize.YMM);
            // Calculate w[16] and w[17] in both 128 bit lanes
            // Calculate sigma1 for w[16] and w[17] on both 128 bit lanes
            // xmm2 = W[-2] {BABA}
            masm.vperm2f128(xmm2, vector7, vector7, 17);
            // xmm8 = W[-2] >> 6 {BABA}
            masm.vpsrlq(xmm8, xmm2, 6, AVXSize.YMM);
        } else if (iteration % 4 == 2) {
            // xmm3 = W[-2] >> 19 {BABA}
            masm.vpsrlq(xmm3, xmm2, 19, AVXSize.YMM);
            // xmm1 = W[-2] << 19 {BABA}
            masm.vpsllq(xmm1, xmm2, (64 - 19), AVXSize.YMM);
            // xmm3 = W[-2] ror 19 {BABA}
            masm.vpor(xmm3, xmm3, xmm1, AVXSize.YMM);
            // xmm8 = W[-2] ror 19 ^ W[-2] >> 6 {BABA}
            masm.vpxor(xmm8, xmm8, xmm3, AVXSize.YMM);
            // xmm3 = W[-2] >> 61 {BABA}
            masm.vpsrlq(xmm3, xmm2, 61, AVXSize.YMM);
            // xmm1 = W[-2] << 61 {BABA}
            masm.vpsllq(xmm1, xmm2, (64 - 61), AVXSize.YMM);
            // xmm3 = W[-2] ror 61 {BABA}
            masm.vpor(xmm3, xmm3, xmm1, AVXSize.YMM);
            // xmm8 = s1 = (W[-2] ror 19) ^ (W[-2] ror 61) ^ (W[-2] >> 6) { BABA }
            masm.vpxor(xmm8, xmm8, xmm3, AVXSize.YMM);

            // Add sigma1 to the other components to get w[16] and w[17]
            // xmm4 = { W[1], W[0], W[1], W[0] }
            masm.vpaddq(vector4, vector4, xmm8, AVXSize.YMM);

            // Calculate sigma1 for w[18] and w[19] for upper 128 bit lane
            // xmm8 = W[-2] >> 6 {DC--}
            masm.vpsrlq(xmm8, vector4, 6, AVXSize.YMM);
        } else if (iteration % 4 == 3) {
            // xmm3 = W[-2] >> 19 {DC--}
            masm.vpsrlq(xmm3, vector4, 19, AVXSize.YMM);
            // xmm1 = W[-2] << 19 {DC--}
            masm.vpsllq(xmm1, vector4, (64 - 19), AVXSize.YMM);
            // xmm3 = W[-2] ror 19 {DC--}
            masm.vpor(xmm3, xmm3, xmm1, AVXSize.YMM);
            // xmm8 = W[-2] ror 19 ^ W[-2] >> 6 {DC--}
            masm.vpxor(xmm8, xmm8, xmm3, AVXSize.YMM);
            // xmm3 = W[-2] >> 61 {DC--}
            masm.vpsrlq(xmm3, vector4, 61, AVXSize.YMM);
            // xmm1 = W[-2] << 61 {DC--}
            masm.vpsllq(xmm1, vector4, (64 - 61), AVXSize.YMM);
            // xmm3 = W[-2] ror 61 {DC--}
            masm.vpor(xmm3, xmm3, xmm1, AVXSize.YMM);
            // xmm8 = s1 = (W[-2] ror 19) ^ (W[-2] ror 61) ^ (W[-2] >> 6) { DC-- }
            masm.vpxor(xmm8, xmm8, xmm3, AVXSize.YMM);

            // Add the sigma0 + w[t - 7] + w[t - 16] for w[18] and w[19] to newly calculated sigma1
            // to get w[18] and w[19]
            // xmm2 = { W[3], W[2], --, -- }
            masm.vpaddq(xmm2, xmm0, xmm8, AVXSize.YMM);

            // Form w[19, w[18], w17], w[16]
            // xmm4 = { W[3], W[2], W[1], W[0] }
            masm.vpblendd(vector4, vector4, xmm2, 0xF0, AVXSize.YMM);
        }
        // y3 = a; MAJA
        masm.movq(y3, a);
        // y0 = e >> 41; S1A
        masm.rorxq(y0, e, 41);
        // y1 = e >> 18; S1B
        masm.rorxq(y1, e, 18);
        // h = k + w + h; --
        masm.addq(h, new AMD64Address(rsp, (iteration * 8)));
        // y3 = a | c; MAJA
        masm.orq(y3, c);
        // y2 = f; CH
        masm.movq(y2, f);
        // y2 = f^g; CH
        masm.xorq(y2, g);
        // t1 = a >> 34; S0B
        masm.rorxq(t1, a, 34);
        // y0 = (e >> 41) ^ (e >> 18); S1
        masm.xorq(y0, y1);
        // y1 = (e >> 14); S1
        masm.rorxq(y1, e, 14);
        // y2 = (f^g) & e; CH
        masm.andq(y2, e);
        // d = k + w + h + d; --
        masm.addq(d, h);
        // y3 = (a | c)&b; MAJA
        masm.andq(y3, b);
        // y0 = (e >> 41) ^ (e >> 18) ^ (e >> 14); S1
        masm.xorq(y0, y1);
        // y1 = a >> 39; S0A
        masm.rorxq(y1, a, 39);
        // y1 = (a >> 39) ^ (a >> 34); S0
        masm.xorq(y1, t1);
        // t1 = (a >> 28); S0
        masm.rorxq(t1, a, 28);
        // y2 = CH = ((f^g)&e) ^ g; CH
        masm.xorq(y2, g);
        // y1 = (a >> 39) ^ (a >> 34) ^ (a >> 28); S0
        masm.xorq(y1, t1);
        // t1 = a; MAJB
        masm.movq(t1, a);
        // t1 = a&c; MAJB
        masm.andq(t1, c);
        // y2 = S1 + CH; --
        masm.addq(y2, y0);
        // y3 = MAJ = (a | c)&b) | (a&c); MAJ
        masm.orq(y3, t1);
        // h = k + w + h + S0; --
        masm.addq(h, y1);
        // d = k + w + h + d + S1 + CH = d + t1; --
        masm.addq(d, y2);
        // h = k + w + h + S0 + S1 + CH = t1 + S0; --
        masm.addq(h, y2);
        // h = t1 + S0 + MAJ; --
        masm.addq(h, y3);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
