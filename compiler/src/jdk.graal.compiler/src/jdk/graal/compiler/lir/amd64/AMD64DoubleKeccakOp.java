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

import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.k4;
import static jdk.vm.ci.amd64.AMD64.k5;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
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
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/x86/stubGenerator_x86_64_sha3.cpp#L328-L499",
          sha1 = "0ce2ed248bdcbfa9c607949c7acecb6e897cd88e")
// @formatter:on
public final class AMD64DoubleKeccakOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64DoubleKeccakOp> TYPE = LIRInstructionClass.create(AMD64DoubleKeccakOp.class);

    @Use({OperandFlag.REG}) private Value state0Value;
    @Use({OperandFlag.REG}) private Value state1Value;
    @Def({OperandFlag.REG}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AMD64DoubleKeccakOp(AllocatableValue state0Value, AllocatableValue state1Value, AllocatableValue resultValue) {
        super(TYPE);
        GraalError.guarantee(asRegister(state0Value).equals(rdi), "expect state0Value at rdi, but was %s", state0Value);
        GraalError.guarantee(asRegister(state1Value).equals(rsi), "expect state1Value at rsi, but was %s", state1Value);
        GraalError.guarantee(asRegister(resultValue).equals(rax), "expect resultValue at rax, but was %s", resultValue);

        this.state0Value = state0Value;
        this.state1Value = state1Value;
        this.resultValue = resultValue;

        this.temps = new Value[]{
                        rdx.asValue(),
                        rcx.asValue(),
                        r10.asValue(),
                        r11.asValue(),
                        k1.asValue(),
                        k2.asValue(),
                        k3.asValue(),
                        k4.asValue(),
                        k5.asValue(),
                        xmm0.asValue(),
                        xmm1.asValue(),
                        xmm2.asValue(),
                        xmm3.asValue(),
                        xmm4.asValue(),
                        xmm5.asValue(),
                        xmm6.asValue(),
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
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register state0 = asRegister(state0Value);
        Register state1 = asRegister(state1Value);
        Register result = asRegister(resultValue);

        Register permsAndRots = rdx;
        Register roundConsts = rcx;
        Register constant2use = r10;
        Register roundsLeft = r11;

        Label rounds24Loop = new Label();

        masm.leaq(permsAndRots, recordExternalAddress(crb, AMD64SHA3Op.permsAndRotsAddr));
        masm.leaq(roundConsts, recordExternalAddress(crb, AMD64SHA3Op.roundConstsAddr));

        // set up the masks
        masm.movl(result, 0x1F);
        masm.kmovw(k5, result);
        masm.kshiftrw(k4, k5, 1);
        masm.kshiftrw(k3, k5, 2);
        masm.kshiftrw(k2, k5, 3);
        masm.kshiftrw(k1, k5, 4);

        // load the states
        for (int i = 0; i < 5; i++) {
            masm.evmovdqu64(asXMMRegister(i), k5, new AMD64Address(state0, i * 40));
        }
        for (int i = 0; i < 5; i++) {
            masm.evmovdqu64(asXMMRegister(10 + i), k5, new AMD64Address(state1, i * 40));
        }

        // load the permutation and rotation constants
        for (int i = 0; i < 15; i++) {
            masm.evmovdqu64(asXMMRegister(17 + i), new AMD64Address(permsAndRots, i * 64));
        }

        // there will be 24 keccak rounds
        // The same operations as the ones in generate_sha3_implCompress are
        // performed, but in parallel for two states: one in regs z0-z5, using z6
        // as the scratch register and the other in z10-z15, using z16 as the
        // scratch register.
        // The permutation and rotation constants, that are loaded into z17-z31,
        // are shared between the two computations.
        masm.movl(roundsLeft, 24);
        // load round_constants base
        masm.movq(constant2use, roundConsts);

        masm.align(preferredLoopAlignment(crb));
        masm.bind(rounds24Loop);
        masm.subl(roundsLeft, 1);

        masm.evmovdqu16(xmm5, xmm0);
        masm.evmovdqu16(xmm15, xmm10);
        masm.evpternlogq(xmm5, 150, xmm1, xmm2);
        masm.evpternlogq(xmm15, 150, xmm11, xmm12);
        masm.evpternlogq(xmm5, 150, xmm3, xmm4);
        masm.evpternlogq(xmm15, 150, xmm13, xmm14);
        masm.evprolq(xmm6, xmm5, 1);
        masm.evprolq(xmm16, xmm15, 1);
        masm.evpermt2q(xmm5, xmm30, xmm5);
        masm.evpermt2q(xmm15, xmm30, xmm15);
        masm.evpermt2q(xmm6, xmm31, xmm6);
        masm.evpermt2q(xmm16, xmm31, xmm16);
        masm.evpternlogq(xmm0, 150, xmm5, xmm6);
        masm.evpternlogq(xmm10, 150, xmm15, xmm16);
        masm.evpternlogq(xmm1, 150, xmm5, xmm6);
        masm.evpternlogq(xmm11, 150, xmm15, xmm16);
        masm.evpternlogq(xmm2, 150, xmm5, xmm6);
        masm.evpternlogq(xmm12, 150, xmm15, xmm16);
        masm.evpternlogq(xmm3, 150, xmm5, xmm6);
        masm.evpternlogq(xmm13, 150, xmm15, xmm16);
        masm.evpternlogq(xmm4, 150, xmm5, xmm6);
        masm.evpternlogq(xmm14, 150, xmm15, xmm16);
        masm.evpermt2q(xmm4, xmm17, xmm3);
        masm.evpermt2q(xmm14, xmm17, xmm13);
        masm.evpermt2q(xmm3, xmm18, xmm2);
        masm.evpermt2q(xmm13, xmm18, xmm12);
        masm.evpermt2q(xmm2, xmm17, xmm1);
        masm.evpermt2q(xmm12, xmm17, xmm11);
        masm.evpermt2q(xmm1, xmm19, xmm0);
        masm.evpermt2q(xmm11, xmm19, xmm10);
        masm.evpermt2q(xmm4, xmm20, xmm2);
        masm.evpermt2q(xmm14, xmm20, xmm12);
        masm.evprolvq(xmm1, xmm1, xmm27);
        masm.evprolvq(xmm11, xmm11, xmm27);
        masm.evprolvq(xmm3, xmm3, xmm28);
        masm.evprolvq(xmm13, xmm13, xmm28);
        masm.evprolvq(xmm4, xmm4, xmm29);
        masm.evprolvq(xmm14, xmm14, xmm29);
        masm.evmovdqu16(xmm2, xmm1);
        masm.evmovdqu16(xmm12, xmm11);
        masm.evmovdqu16(xmm5, xmm3);
        masm.evmovdqu16(xmm15, xmm13);
        masm.evpermt2q(xmm0, xmm21, xmm4);
        masm.evpermt2q(xmm10, xmm21, xmm14);
        masm.evpermt2q(xmm1, xmm22, xmm3);
        masm.evpermt2q(xmm11, xmm22, xmm13);
        masm.evpermt2q(xmm5, xmm22, xmm2);
        masm.evpermt2q(xmm15, xmm22, xmm12);
        masm.evmovdqu16(xmm3, xmm1);
        masm.evmovdqu16(xmm13, xmm11);
        masm.evmovdqu16(xmm2, xmm5);
        masm.evmovdqu16(xmm12, xmm15);
        masm.evpermt2q(xmm1, xmm23, xmm4);
        masm.evpermt2q(xmm11, xmm23, xmm14);
        masm.evpermt2q(xmm2, xmm24, xmm4);
        masm.evpermt2q(xmm12, xmm24, xmm14);
        masm.evpermt2q(xmm3, xmm25, xmm4);
        masm.evpermt2q(xmm13, xmm25, xmm14);
        masm.evpermt2q(xmm4, xmm26, xmm5);
        masm.evpermt2q(xmm14, xmm26, xmm15);

        masm.evpermt2q(xmm5, xmm31, xmm0);
        masm.evpermt2q(xmm15, xmm31, xmm10);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpermt2q(xmm16, xmm31, xmm15);
        masm.evpternlogq(xmm0, 180, xmm6, xmm5);
        masm.evpternlogq(xmm10, 180, xmm16, xmm15);

        masm.evpermt2q(xmm5, xmm31, xmm1);
        masm.evpermt2q(xmm15, xmm31, xmm11);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpermt2q(xmm16, xmm31, xmm15);
        masm.evpternlogq(xmm1, 180, xmm6, xmm5);
        masm.evpternlogq(xmm11, 180, xmm16, xmm15);

        masm.evpxorq(xmm0, k1, xmm0, new AMD64Address(constant2use, 0));
        masm.evpxorq(xmm10, k1, xmm10, new AMD64Address(constant2use, 0));
        masm.addq(constant2use, 8);

        masm.evpermt2q(xmm5, xmm31, xmm2);
        masm.evpermt2q(xmm15, xmm31, xmm12);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpermt2q(xmm16, xmm31, xmm15);
        masm.evpternlogq(xmm2, 180, xmm6, xmm5);
        masm.evpternlogq(xmm12, 180, xmm16, xmm15);

        masm.evpermt2q(xmm5, xmm31, xmm3);
        masm.evpermt2q(xmm15, xmm31, xmm13);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpermt2q(xmm16, xmm31, xmm15);
        masm.evpternlogq(xmm3, 180, xmm6, xmm5);
        masm.evpternlogq(xmm13, 180, xmm16, xmm15);
        masm.evpermt2q(xmm5, xmm31, xmm4);
        masm.evpermt2q(xmm15, xmm31, xmm14);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpermt2q(xmm16, xmm31, xmm15);
        masm.evpternlogq(xmm4, 180, xmm6, xmm5);
        masm.evpternlogq(xmm14, 180, xmm16, xmm15);
        masm.cmplAndJcc(roundsLeft, 0, ConditionFlag.NotEqual, rounds24Loop, false);

        // store the states
        for (int i = 0; i < 5; i++) {
            masm.evmovdqu64(new AMD64Address(state0, i * 40), k5, asXMMRegister(i));
        }
        for (int i = 0; i < 5; i++) {
            masm.evmovdqu64(new AMD64Address(state1, i * 40), k5, asXMMRegister(10 + i));
        }

        masm.xorq(result, result);
    }
}
