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
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Above;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.AboveEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Below;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/0487aa61c67de695d008af4fe75c2a3072261a6f/src/hotspot/cpu/x86/macroAssembler_x86_sha.cpp#L496-L1035",
          sha1 = "f9283840deab5f199d600017cde5548f80ca0699")
// @formatter:on
public final class AMD64SHA256AVX2Op extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64SHA256AVX2Op> TYPE = LIRInstructionClass.create(AMD64SHA256AVX2Op.class);

    @Use({OperandFlag.REG}) private Value bufValue;
    @Use({OperandFlag.REG}) private Value stateValue;

    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsValue;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value limitValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    private final boolean multiBlock;

    public AMD64SHA256AVX2Op(AllocatableValue bufValue, AllocatableValue stateValue) {
        this(bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AMD64SHA256AVX2Op(AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue, AllocatableValue limitValue, boolean multiBlock) {
        super(TYPE);

        GraalError.guarantee(asRegister(bufValue).equals(rdi), "expect bufValue at rdi, but was %s", bufValue);
        GraalError.guarantee(asRegister(stateValue).equals(rsi), "expect stateValue at rsi, but was %s", stateValue);
        GraalError.guarantee(!multiBlock || asRegister(ofsValue).equals(rdx), "expect ofsValue at rdx, but was %s", ofsValue);
        GraalError.guarantee(!multiBlock || asRegister(limitValue).equals(rcx), "expect limitValue at rdx, but was %s", limitValue);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;

        this.multiBlock = multiBlock;

        // rbp, rbx, r12-r15 will be restored
        this.temps = new Value[]{
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
        };
    }

    static ArrayDataPointerConstant k256W = pointerConstant(16, new int[]{
            // @formatter:off
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
            0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
            0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
            0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
            0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
            // @formatter:on
    });

    static ArrayDataPointerConstant pshuffleByteFlipMask = pointerConstant(16, new long[]{
            // @formatter:off
            0x0405060700010203L,
            0x0c0d0e0f08090a0bL,
            0x0405060700010203L,
            0x0c0d0e0f08090a0bL,
            // @formatter:on
    });

    static ArrayDataPointerConstant shuf00BA = pointerConstant(16, new long[]{
            // @formatter:off
            0x0b0a090803020100L,
            0xFFFFFFFFFFFFFFFFL,
            0x0b0a090803020100L,
            0xFFFFFFFFFFFFFFFFL,
            // @formatter:on
    });

    static ArrayDataPointerConstant shufDC00 = pointerConstant(16, new long[]{
            // @formatter:off
            0xFFFFFFFFFFFFFFFFL,
            0x0b0a090803020100L,
            0xFFFFFFFFFFFFFFFFL,
            0x0b0a090803020100L,
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

    private static final int XFER_SIZE = 2 * 64 * 4; // 2 blocks, 64 rounds, 4 bytes/round
    private static final int INP_END_SIZE = 8;
    private static final int INP_SIZE = 8;
    private static final int CTX_SIZE = 8;
    private static final int RSP_SIZE = 8;

    private static final int OFFSET_XFER = 0;
    private static final int OFFSET_INP_END = OFFSET_XFER + XFER_SIZE;
    private static final int OFFSET_INP = OFFSET_INP_END + INP_END_SIZE;
    private static final int OFFSET_CTX = OFFSET_INP + INP_SIZE;
    private static final int OFFSET_RSP = OFFSET_CTX + CTX_SIZE;
    private static final int STACK_SIZE = OFFSET_RSP + RSP_SIZE;

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelLoop0 = new Label();
        Label labelLoop1 = new Label();
        Label labelLoop2 = new Label();
        Label labelLoop3 = new Label();
        Label labelLastBlockEnter = new Label();
        Label labelDoLastBlock = new Label();
        Label labelOnlyOneBlock = new Label();
        Label labelDoneHash = new Label();
        Label labelComputeSize = new Label();
        Label labelComputeSizeEnd = new Label();
        Label labelComputeSize1 = new Label();
        Label labelComputeSizeEnd1 = new Label();

        Register regSHUF00BA = xmm10;     // ymm10: shuffle xBxA -> 00BA
        Register regSHUFDC00 = xmm12;     // ymm12: shuffle xDxC -> DC00
        Register regByteFlipMask = xmm13; // ymm13

        Register regNumBlks = r8;         // 3rd arg
        Register regCTX = rdx;            // 2nd arg
        Register regINP = rcx;            // 1st arg

        Register c = rdi;
        Register d = rsi;
        Register e = r8;        // clobbers NUM_BLKS

        Register regTBL = rbp;
        // SRND is same register as CTX
        Register regSRND = regCTX;

        Register a = rax;
        Register b = rbx;
        Register f = r9;
        Register g = r10;
        Register h = r11;

        masm.push(rcx); // this is limit, need at the end
        masm.push(rdx); // this is ofs

        masm.push(rbx);
        masm.push(rbp);
        masm.push(r12);
        masm.push(r13);
        masm.push(r14);
        masm.push(r15);

        masm.movq(rax, rsp);
        masm.subq(rsp, STACK_SIZE);
        masm.andq(rsp, -32);
        masm.movq(new AMD64Address(rsp, OFFSET_RSP), rax);

        masm.movq(r9, rcx);
        masm.movq(r8, rdx);
        masm.movq(rdx, rsi);
        masm.movq(rcx, rdi);

        // setting original assembly ABI
        // message to encrypt in INP
        // rcx == message (buf) ;; linux: INP = buf = rdi
        masm.leaq(regINP, new AMD64Address(rcx, 0));
        // digest in CTX
        // rdx = digest (state) ;; linux: CTX = state = rsi
        masm.movq(regCTX, rdx);

        // NUM_BLK is the length of message, need to set it from ofs and limit
        if (multiBlock) {
            masm.xorq(rax, rax);

            masm.bind(labelComputeSize);
            // assume the original ofs <= limit ;; linux: cmp rcx, rdx
            masm.cmpqAndJcc(r8, r9, AboveEqual, labelComputeSizeEnd, true);
            // ;; linux: ofs = rdx
            masm.addq(r8, 64);
            masm.addq(rax, 64);
            masm.jmpb(labelComputeSize);

            masm.bind(labelComputeSizeEnd);
            // NUM_BLK (r8) ;; linux: NUM_BLK = rdx
            masm.movq(regNumBlks, rax);

            masm.cmpqAndJcc(regNumBlks, 0, Equal, labelDoneHash, false);
        } else {
            masm.xorq(regNumBlks, regNumBlks);
            masm.addq(regNumBlks, 64);
        } // if (!multi_block)

        // pointer to the last block
        masm.leaq(regNumBlks, new AMD64Address(regINP, regNumBlks, Stride.S1, -64));
        masm.movq(new AMD64Address(rsp, OFFSET_INP_END), regNumBlks);

        // cmp INP, NUM_BLKS
        masm.cmpqAndJcc(regINP, regNumBlks, Equal, labelOnlyOneBlock, false);

        // load initial digest
        masm.movl(a, new AMD64Address(regCTX, 4 * 0));
        masm.movl(b, new AMD64Address(regCTX, 4 * 1));
        masm.movl(c, new AMD64Address(regCTX, 4 * 2));
        masm.movl(d, new AMD64Address(regCTX, 4 * 3));
        masm.movl(e, new AMD64Address(regCTX, 4 * 4));
        masm.movl(f, new AMD64Address(regCTX, 4 * 5));
        // load g - r10 after it is used as scratch
        masm.movl(h, new AMD64Address(regCTX, 4 * 7));

        // [PSHUFFLE_BYTE_FLIP_MASK wrt rip]
        masm.vmovdqu(regByteFlipMask, recordExternalAddress(crb, pshuffleByteFlipMask));
        // [_SHUF_00BA wrt rip]
        masm.vmovdqu(regSHUF00BA, recordExternalAddress(crb, shuf00BA));
        // [_SHUF_DC00 wrt rip]
        masm.vmovdqu(regSHUFDC00, recordExternalAddress(crb, shufDC00));

        masm.movl(g, new AMD64Address(regCTX, 4 * 6));

        // store
        masm.movq(new AMD64Address(rsp, OFFSET_CTX), regCTX);
        masm.bind(labelLoop0);
        masm.leaq(regTBL, recordExternalAddress(crb, k256W));

        // assume buffers not aligned

        // Load first 16 dwords from two blocks
        masm.vmovdqu(xmm0, new AMD64Address(regINP, 0 * 32));
        masm.vmovdqu(xmm1, new AMD64Address(regINP, 1 * 32));
        masm.vmovdqu(xmm2, new AMD64Address(regINP, 2 * 32));
        masm.vmovdqu(xmm3, new AMD64Address(regINP, 3 * 32));

        // byte swap data
        masm.vpshufb(xmm0, xmm0, regByteFlipMask, AVXSize.YMM);
        masm.vpshufb(xmm1, xmm1, regByteFlipMask, AVXSize.YMM);
        masm.vpshufb(xmm2, xmm2, regByteFlipMask, AVXSize.YMM);
        masm.vpshufb(xmm3, xmm3, regByteFlipMask, AVXSize.YMM);

        // transpose data into high/low halves
        masm.vperm2i128(xmm4, xmm0, xmm2, 0x20);
        masm.vperm2i128(xmm5, xmm0, xmm2, 0x31);
        masm.vperm2i128(xmm6, xmm1, xmm3, 0x20);
        masm.vperm2i128(xmm7, xmm1, xmm3, 0x31);

        masm.bind(labelLastBlockEnter);
        masm.addq(regINP, 64);
        masm.movq(new AMD64Address(rsp, OFFSET_INP), regINP);

        // schedule 48 input dwords, by doing 3 rounds of 12 each
        masm.xorq(regSRND, regSRND);

        masm.align(16);
        masm.bind(labelLoop1);
        masm.vpaddd(xmm9, xmm4, new AMD64Address(regTBL, regSRND, Stride.S1, 0 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, regSRND, Stride.S1, OFFSET_XFER + 0 * 32), xmm9);
        sha256AVX2OneRoundAndSched(masm, xmm4, xmm5, xmm6, xmm7, rax, rbx, rdi, rsi, r8, r9, r10, r11, 0);
        sha256AVX2OneRoundAndSched(masm, xmm4, xmm5, xmm6, xmm7, r11, rax, rbx, rdi, rsi, r8, r9, r10, 1);
        sha256AVX2OneRoundAndSched(masm, xmm4, xmm5, xmm6, xmm7, r10, r11, rax, rbx, rdi, rsi, r8, r9, 2);
        sha256AVX2OneRoundAndSched(masm, xmm4, xmm5, xmm6, xmm7, r9, r10, r11, rax, rbx, rdi, rsi, r8, 3);

        masm.vpaddd(xmm9, xmm5, new AMD64Address(regTBL, regSRND, Stride.S1, 1 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, regSRND, Stride.S1, OFFSET_XFER + 1 * 32), xmm9);
        sha256AVX2OneRoundAndSched(masm, xmm5, xmm6, xmm7, xmm4, r8, r9, r10, r11, rax, rbx, rdi, rsi, 8 + 0);
        sha256AVX2OneRoundAndSched(masm, xmm5, xmm6, xmm7, xmm4, rsi, r8, r9, r10, r11, rax, rbx, rdi, 8 + 1);
        sha256AVX2OneRoundAndSched(masm, xmm5, xmm6, xmm7, xmm4, rdi, rsi, r8, r9, r10, r11, rax, rbx, 8 + 2);
        sha256AVX2OneRoundAndSched(masm, xmm5, xmm6, xmm7, xmm4, rbx, rdi, rsi, r8, r9, r10, r11, rax, 8 + 3);

        masm.vpaddd(xmm9, xmm6, new AMD64Address(regTBL, regSRND, Stride.S1, 2 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, regSRND, Stride.S1, OFFSET_XFER + 2 * 32), xmm9);
        sha256AVX2OneRoundAndSched(masm, xmm6, xmm7, xmm4, xmm5, rax, rbx, rdi, rsi, r8, r9, r10, r11, 16 + 0);
        sha256AVX2OneRoundAndSched(masm, xmm6, xmm7, xmm4, xmm5, r11, rax, rbx, rdi, rsi, r8, r9, r10, 16 + 1);
        sha256AVX2OneRoundAndSched(masm, xmm6, xmm7, xmm4, xmm5, r10, r11, rax, rbx, rdi, rsi, r8, r9, 16 + 2);
        sha256AVX2OneRoundAndSched(masm, xmm6, xmm7, xmm4, xmm5, r9, r10, r11, rax, rbx, rdi, rsi, r8, 16 + 3);

        masm.vpaddd(xmm9, xmm7, new AMD64Address(regTBL, regSRND, Stride.S1, 3 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, regSRND, Stride.S1, OFFSET_XFER + 3 * 32), xmm9);

        sha256AVX2OneRoundAndSched(masm, xmm7, xmm4, xmm5, xmm6, r8, r9, r10, r11, rax, rbx, rdi, rsi, 24 + 0);
        sha256AVX2OneRoundAndSched(masm, xmm7, xmm4, xmm5, xmm6, rsi, r8, r9, r10, r11, rax, rbx, rdi, 24 + 1);
        sha256AVX2OneRoundAndSched(masm, xmm7, xmm4, xmm5, xmm6, rdi, rsi, r8, r9, r10, r11, rax, rbx, 24 + 2);
        sha256AVX2OneRoundAndSched(masm, xmm7, xmm4, xmm5, xmm6, rbx, rdi, rsi, r8, r9, r10, r11, rax, 24 + 3);

        masm.addq(regSRND, 4 * 32);
        masm.cmpqAndJcc(regSRND, 3 * 4 * 32, Below, labelLoop1, false);

        masm.bind(labelLoop2);
        // Do last 16 rounds with no scheduling
        masm.vpaddd(xmm9, xmm4, new AMD64Address(regTBL, regSRND, Stride.S1, 0 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, regSRND, Stride.S1, OFFSET_XFER + 0 * 32), xmm9);
        sha256AVX2FourRoundsComputeFirst(masm, 0);

        masm.vpaddd(xmm9, xmm5, new AMD64Address(regTBL, regSRND, Stride.S1, 1 * 32), AVXSize.YMM);
        masm.vmovdqu(new AMD64Address(rsp, regSRND, Stride.S1, OFFSET_XFER + 1 * 32), xmm9);
        sha256AVX2FourRoundsComputeLast(masm, 0 + 8);

        masm.addq(regSRND, 2 * 32);

        masm.vmovdqu(xmm4, xmm6);
        masm.vmovdqu(xmm5, xmm7);

        masm.cmpqAndJcc(regSRND, 4 * 4 * 32, Below, labelLoop2, false);

        masm.movq(regCTX, new AMD64Address(rsp, OFFSET_CTX));
        masm.movq(regINP, new AMD64Address(rsp, OFFSET_INP));

        addm(masm, 4 * 0, regCTX, a);
        addm(masm, 4 * 1, regCTX, b);
        addm(masm, 4 * 2, regCTX, c);
        addm(masm, 4 * 3, regCTX, d);
        addm(masm, 4 * 4, regCTX, e);
        addm(masm, 4 * 5, regCTX, f);
        addm(masm, 4 * 6, regCTX, g);
        addm(masm, 4 * 7, regCTX, h);

        masm.cmpqAndJcc(regINP, new AMD64Address(rsp, OFFSET_INP_END), Above, labelDoneHash, false);

        // Do second block using previously scheduled results
        masm.xorq(regSRND, regSRND);
        masm.align(16);
        masm.bind(labelLoop3);
        sha256AVX2FourRoundsComputeFirst(masm, 4);
        sha256AVX2FourRoundsComputeLast(masm, 4 + 8);

        masm.addq(regSRND, 2 * 32);
        masm.cmpqAndJcc(regSRND, 4 * 4 * 32, Below, labelLoop3, false);

        masm.movq(regCTX, new AMD64Address(rsp, OFFSET_CTX));
        masm.movq(regINP, new AMD64Address(rsp, OFFSET_INP));
        masm.addq(regINP, 64);

        addm(masm, 4 * 0, regCTX, a);
        addm(masm, 4 * 1, regCTX, b);
        addm(masm, 4 * 2, regCTX, c);
        addm(masm, 4 * 3, regCTX, d);
        addm(masm, 4 * 4, regCTX, e);
        addm(masm, 4 * 5, regCTX, f);
        addm(masm, 4 * 6, regCTX, g);
        addm(masm, 4 * 7, regCTX, h);

        masm.cmpqAndJcc(regINP, new AMD64Address(rsp, OFFSET_INP_END), Below, labelLoop0, false);
        masm.jccb(Above, labelDoneHash);

        masm.bind(labelDoLastBlock);
        masm.leaq(regTBL, recordExternalAddress(crb, k256W));

        masm.movdqu(xmm4, new AMD64Address(regINP, 0 * 16));
        masm.movdqu(xmm5, new AMD64Address(regINP, 1 * 16));
        masm.movdqu(xmm6, new AMD64Address(regINP, 2 * 16));
        masm.movdqu(xmm7, new AMD64Address(regINP, 3 * 16));

        masm.vpshufb(xmm4, xmm4, xmm13, AVXSize.XMM);
        masm.vpshufb(xmm5, xmm5, xmm13, AVXSize.XMM);
        masm.vpshufb(xmm6, xmm6, xmm13, AVXSize.XMM);
        masm.vpshufb(xmm7, xmm7, xmm13, AVXSize.XMM);

        masm.jmp(labelLastBlockEnter);

        masm.bind(labelOnlyOneBlock);

        // load initial digest ;; table should be preloaded with following values
        masm.movl(a, new AMD64Address(regCTX, 4 * 0));   // 0x6a09e667
        masm.movl(b, new AMD64Address(regCTX, 4 * 1));   // 0xbb67ae85
        masm.movl(c, new AMD64Address(regCTX, 4 * 2));   // 0x3c6ef372
        masm.movl(d, new AMD64Address(regCTX, 4 * 3));   // 0xa54ff53a
        masm.movl(e, new AMD64Address(regCTX, 4 * 4));   // 0x510e527f
        masm.movl(f, new AMD64Address(regCTX, 4 * 5));   // 0x9b05688c
        // load g - r10 after use as scratch
        masm.movl(h, new AMD64Address(regCTX, 4 * 7));   // 0x5be0cd19

        // [PSHUFFLE_BYTE_FLIP_MASK wrt rip]
        masm.vmovdqu(regByteFlipMask, recordExternalAddress(crb, pshuffleByteFlipMask));
        // [_SHUF_00BA wrt rip]
        masm.vmovdqu(regSHUF00BA, recordExternalAddress(crb, shuf00BA));
        // [_SHUF_DC00 wrt rip]
        masm.vmovdqu(regSHUFDC00, recordExternalAddress(crb, shufDC00));

        masm.movl(g, new AMD64Address(regCTX, 4 * 6)); // 0x1f83d9ab

        masm.movq(new AMD64Address(rsp, OFFSET_CTX), regCTX);
        masm.jmpb(labelDoLastBlock);

        masm.bind(labelDoneHash);

        masm.movq(rsp, new AMD64Address(rsp, OFFSET_RSP));

        masm.pop(r15);
        masm.pop(r14);
        masm.pop(r13);
        masm.pop(r12);
        masm.pop(rbp);
        masm.pop(rbx);

        masm.pop(rdx);
        masm.pop(rcx);

        if (multiBlock) {
            Register limitEnd = rcx;
            Register ofsEnd = rdx;
            masm.movq(rax, ofsEnd);
            masm.bind(labelComputeSize1);
            // assume the original ofs <= limit
            masm.cmpqAndJcc(rax, limitEnd, AboveEqual, labelComputeSizeEnd1, true);
            masm.addq(rax, 64);
            masm.jmpb(labelComputeSize1);

            masm.bind(labelComputeSizeEnd1);
        }
    }

    private static void sha256AVX2OneRoundCompute(AMD64MacroAssembler masm,
                    Register regOldH,
                    Register regA,
                    Register regB,
                    Register regC,
                    Register regD,
                    Register regE,
                    Register regF,
                    Register regG,
                    Register regH,
                    int iter) {
        Register regY0 = r13;
        Register regY1 = r14;
        Register regY2 = r15;
        Register regY3 = rcx;
        Register regT1 = r12;
        if (iter % 4 > 0) {
            masm.addl(regOldH, regY2);     // reg_h = k + w + reg_h + S0 + S1 + CH = t1 + S0; --
        }
        masm.movl(regY2, regF);            // reg_y2 = reg_f ; CH
        masm.rorxl(regY0, regE, 25);       // reg_y0 = reg_e >> 25 ; S1A
        masm.rorxl(regY1, regE, 11);       // reg_y1 = reg_e >> 11 ; S1B
        masm.xorl(regY2, regG);            // reg_y2 = reg_f^reg_g ; CH

        masm.xorl(regY0, regY1);           // reg_y0 = (reg_e>>25) ^ (reg_h>>11) ; S1
        masm.rorxl(regY1, regE, 6);        // reg_y1 = (reg_e >> 6) ; S1
        masm.andl(regY2, regE);            // reg_y2 = (reg_f^reg_g)&reg_e ; CH

        if (iter % 4 > 0) {
            masm.addl(regOldH, regY3);     // reg_h = t1 + S0 + MAJ ; --
        }

        // reg_y0 = (reg_e>>25) ^ (reg_e>>11) ^ (reg_e>>6) ; S1
        masm.xorl(regY0, regY1);
        masm.rorxl(regT1, regA, 13);       // reg_T1 = reg_a >> 13 ; S0B
        masm.xorl(regY2, regG);            // reg_y2 = CH = ((reg_f^reg_g)&reg_e)^reg_g ; CH
        masm.rorxl(regY1, regA, 22);       // reg_y1 = reg_a >> 22 ; S0A
        masm.movl(regY3, regA);            // reg_y3 = reg_a ; MAJA

        masm.xorl(regY1, regT1);           // reg_y1 = (reg_a>>22) ^ (reg_a>>13) ; S0
        masm.rorxl(regT1, regA, 2);        // reg_T1 = (reg_a >> 2) ; S0
        // reg_h = k + w + reg_h; --
        masm.addl(regH, new AMD64Address(rsp, rdx, Stride.S1, 4 * iter));
        masm.orl(regY3, regC);             // reg_y3 = reg_a|reg_c ; MAJA

        masm.xorl(regY1, regT1);           // reg_y1 = (reg_a>>22) ^ (reg_a>>13) ^ (reg_a>>2) ; S0
        masm.movl(regT1, regA);            // reg_T1 = reg_a ; MAJB
        masm.andl(regY3, regB);            // reg_y3 = (reg_a|reg_c)&reg_b ; MAJA
        masm.andl(regT1, regC);            // reg_T1 = reg_a&reg_c ; MAJB
        masm.addl(regY2, regY0);           // reg_y2 = S1 + CH ; --

        masm.addl(regD, regH);             // reg_d = k + w + reg_h + reg_d ; --
        masm.orl(regY3, regT1);            // reg_y3 = MAJ = (reg_a|reg_c)&reg_b)|(reg_a&reg_c) ;
                                           // MAJ
        masm.addl(regH, regY1);            // reg_h = k + w + reg_h + S0 ; --

        masm.addl(regD, regY2);            // reg_d = k + w + reg_h + reg_d + S1 + CH = reg_d + t1 ;

        if (iter % 4 == 3) {
            masm.addl(regH, regY2);        // reg_h = k + w + reg_h + S0 + S1 + CH = t1 + S0; --
            masm.addl(regH, regY3);        // reg_h = t1 + S0 + MAJ ; --
        }
    }

    private static void sha256AVX2FourRoundsComputeFirst(AMD64MacroAssembler masm, int start) {
        sha256AVX2OneRoundCompute(masm, rax, rax, rbx, rdi, rsi, r8, r9, r10, r11, start + 0);
        sha256AVX2OneRoundCompute(masm, r11, r11, rax, rbx, rdi, rsi, r8, r9, r10, start + 1);
        sha256AVX2OneRoundCompute(masm, r10, r10, r11, rax, rbx, rdi, rsi, r8, r9, start + 2);
        sha256AVX2OneRoundCompute(masm, r9, r9, r10, r11, rax, rbx, rdi, rsi, r8, start + 3);
    }

    private static void sha256AVX2FourRoundsComputeLast(AMD64MacroAssembler masm, int start) {
        sha256AVX2OneRoundCompute(masm, r8, r8, r9, r10, r11, rax, rbx, rdi, rsi, start + 0);
        sha256AVX2OneRoundCompute(masm, rsi, rsi, r8, r9, r10, r11, rax, rbx, rdi, start + 1);
        sha256AVX2OneRoundCompute(masm, rdi, rdi, rsi, r8, r9, r10, r11, rax, rbx, start + 2);
        sha256AVX2OneRoundCompute(masm, rbx, rbx, rdi, rsi, r8, r9, r10, r11, rax, start + 3);
    }

    private static void sha256AVX2OneRoundAndSched(AMD64MacroAssembler masm,
                    Register vector4,
                    Register vector5,
                    Register vector6,
                    Register vector7,
                    Register regA,
                    Register regB,
                    Register regC,
                    Register regD,
                    Register regE,
                    Register regF,
                    Register regG,
                    Register regH,
                    int iter) {
        masm.movl(rcx, regA);           // rcx = reg_a ; MAJA
        masm.rorxl(r13, regE, 25);      // r13 = reg_e >> 25 ; S1A
        masm.rorxl(r14, regE, 11);      // r14 = reg_e >> 11 ; S1B
        masm.addl(regH, new AMD64Address(rsp, rdx, Stride.S1, 4 * iter));
        masm.orl(rcx, regC);            // rcx = reg_a|reg_c ; MAJA

        masm.movl(r15, regF);           // r15 = reg_f ; CH
        masm.rorxl(r12, regA, 13);      // r12 = reg_a >> 13 ; S0B
        masm.xorl(r13, r14);            // r13 = (reg_e>>25) ^ (reg_e>>11) ; S1
        masm.xorl(r15, regG);           // r15 = reg_f^reg_g ; CH

        masm.rorxl(r14, regE, 6);       // r14 = (reg_e >> 6) ; S1
        masm.andl(r15, regE);           // r15 = (reg_f^reg_g)&reg_e ; CH

        masm.xorl(r13, r14);            // r13 = (reg_e>>25) ^ (reg_e>>11) ^ (reg_e>>6) ; S1
        masm.rorxl(r14, regA, 22);      // r14 = reg_a >> 22 ; S0A
        masm.addl(regD, regH);          // reg_d = k + w + reg_h + reg_d ; --

        masm.andl(rcx, regB);           // rcx = (reg_a|reg_c)&reg_b ; MAJA
        masm.xorl(r14, r12);            // r14 = (reg_a>>22) ^ (reg_a>>13) ; S0

        masm.rorxl(r12, regA, 2);       // r12 = (reg_a >> 2) ; S0
        masm.xorl(r15, regG);           // r15 = CH = ((reg_f^reg_g)&reg_e)^reg_g ; CH

        masm.xorl(r14, r12);            // r14 = (reg_a>>22) ^ (reg_a>>13) ^ (reg_a>>2) ; S0
        masm.movl(r12, regA);           // r12 = reg_a ; MAJB
        masm.andl(r12, regC);           // r12 = reg_a&reg_c ; MAJB
        masm.addl(r15, r13);            // r15 = S1 + CH ; --

        masm.orl(rcx, r12);             // rcx = MAJ = (reg_a|reg_c)&reg_b)|(reg_a&reg_c) ; MAJ
        masm.addl(regH, r14);           // reg_h = k + w + reg_h + S0 ; --
        masm.addl(regD, r15);           // reg_d = k + w + reg_h + reg_d + S1 + CH = reg_d + t1 ; --

        masm.addl(regH, r15);           // reg_h = k + w + reg_h + S0 + S1 + CH = t1 + S0; --
        masm.addl(regH, rcx);           // reg_h = t1 + S0 + MAJ ; --

        if (iter % 4 == 0) {
            // ymm0 = W[-7]
            masm.vpalignr(xmm0, vector7, vector6, 4, AVXSize.YMM);
            // ymm0 = W[-7] + W[-16]; y1 = (e >> 6) ; S1
            masm.vpaddd(xmm0, xmm0, vector4, AVXSize.YMM);
            // ymm1 = W[-15]
            masm.vpalignr(xmm1, vector5, vector4, 4, AVXSize.YMM);
            masm.vpsrld(xmm2, xmm1, 7, AVXSize.YMM);
            masm.vpslld(xmm3, xmm1, 32 - 7, AVXSize.YMM);
            // ymm3 = W[-15] ror 7
            masm.vpor(xmm3, xmm3, xmm2, AVXSize.YMM);
            masm.vpsrld(xmm2, xmm1, 18, AVXSize.YMM);
        } else if (iter % 4 == 1) {
            // ymm8 = W[-15] >> 3
            masm.vpsrld(xmm8, xmm1, 3, AVXSize.YMM);
            masm.vpslld(xmm1, xmm1, 32 - 18, AVXSize.YMM);
            masm.vpxor(xmm3, xmm3, xmm1, AVXSize.YMM);
            // ymm3 = W[-15] ror 7 ^ W[-15] ror 18
            masm.vpxor(xmm3, xmm3, xmm2, AVXSize.YMM);
            // ymm1 = s0
            masm.vpxor(xmm1, xmm3, xmm8, AVXSize.YMM);
            // 11111010b ; ymm2 = W[-2] {BBAA}
            masm.vpshufd(xmm2, vector7, 0xFA, AVXSize.YMM);
            // ymm0 = W[-16] + W[-7] + s0
            masm.vpaddd(xmm0, xmm0, xmm1, AVXSize.YMM);
            // ymm8 = W[-2] >> 10 {BBAA}
            masm.vpsrld(xmm8, xmm2, 10, AVXSize.YMM);
        } else if (iter % 4 == 2) {
            masm.vpsrlq(xmm3, xmm2, 19, AVXSize.YMM);       // ymm3 = W[-2] ror 19 {xBxA}
            masm.vpsrlq(xmm2, xmm2, 17, AVXSize.YMM);       // ymm2 = W[-2] ror 17 {xBxA}
            masm.vpxor(xmm2, xmm2, xmm3, AVXSize.YMM);
            masm.vpxor(xmm8, xmm8, xmm2, AVXSize.YMM);      // ymm8 = s1 {xBxA}
            masm.vpshufb(xmm8, xmm8, xmm10, AVXSize.YMM);   // ymm8 = s1 {00BA}
            masm.vpaddd(xmm0, xmm0, xmm8, AVXSize.YMM);     // ymm0 = {..., ..., W[1], W[0]}
            masm.vpshufd(xmm2, xmm0, 0x50, AVXSize.YMM);    // 01010000b ; ymm2 = W[-2] {DDCC}
        } else if (iter % 4 == 3) {
            masm.vpsrld(xmm11, xmm2, 10, AVXSize.YMM);      // ymm11 = W[-2] >> 10 {DDCC}
            masm.vpsrlq(xmm3, xmm2, 19, AVXSize.YMM);       // ymm3 = W[-2] ror 19 {xDxC}
            masm.vpsrlq(xmm2, xmm2, 17, AVXSize.YMM);       // ymm2 = W[-2] ror 17 {xDxC}
            masm.vpxor(xmm2, xmm2, xmm3, AVXSize.YMM);
            masm.vpxor(xmm11, xmm11, xmm2, AVXSize.YMM);    // ymm11 = s1 {xDxC}
            masm.vpshufb(xmm11, xmm11, xmm12, AVXSize.YMM); // ymm11 = s1 {DC00}
            masm.vpaddd(vector4, xmm11, xmm0, AVXSize.YMM); // xmm_0 = {W[3], W[2], W[1], W[0]}
        }
    }

    private static void addm(AMD64MacroAssembler masm, int disp, Register r1, Register r2) {
        masm.addl(r2, new AMD64Address(r1, disp));
        masm.movl(new AMD64Address(r1, disp), r2);
    }
}
