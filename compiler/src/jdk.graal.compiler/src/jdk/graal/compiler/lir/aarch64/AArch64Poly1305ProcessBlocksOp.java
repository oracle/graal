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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.GE;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.LT;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSR;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L10675-L10788",
          sha1 = "6f8f7da26eb15f71d9e95bd97c106ec8ce27be3d")
// @formatter:on
public final class AArch64Poly1305ProcessBlocksOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64Poly1305ProcessBlocksOp> TYPE = LIRInstructionClass.create(AArch64Poly1305ProcessBlocksOp.class);

    @Use({OperandFlag.REG}) private Value inputValue;
    @Use({OperandFlag.REG}) private Value lengthValue;
    @Use({OperandFlag.REG}) private Value accumulatorValue;
    @Use({OperandFlag.REG}) private Value rValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AArch64Poly1305ProcessBlocksOp(AllocatableValue input,
                    AllocatableValue length,
                    AllocatableValue accumulator,
                    AllocatableValue r) {
        super(TYPE);
        GraalError.guarantee(input instanceof RegisterValue inputReg && r0.equals(inputReg.getRegister()), "input should be fixed to r0, but is %s", input);
        GraalError.guarantee(length instanceof RegisterValue lengthReg && r1.equals(lengthReg.getRegister()), "length should be fixed to r1, but is %s", length);
        GraalError.guarantee(accumulator instanceof RegisterValue accumulatorReg && r2.equals(accumulatorReg.getRegister()), "accumulator should be fixed to r2, but is %s", accumulator);
        GraalError.guarantee(r instanceof RegisterValue rReg && r3.equals(rReg.getRegister()), "r should be fixed to r3, but is %s", r);
        this.inputValue = input;
        this.lengthValue = length;
        this.accumulatorValue = accumulator;
        this.rValue = r;
        this.temps = new Value[]{
                        r0.asValue(), // input is clobbered
                        r1.asValue(), // input is clobbered
                        r4.asValue(),
                        r5.asValue(),
                        r6.asValue(),
                        r7.asValue(),
                        r11.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        r15.asValue(),
                        r16.asValue(),
                        r17.asValue(),
                        r20.asValue(),
        };
    }

    private static void pack26(AArch64MacroAssembler masm, Register dest0, Register dest1, Register dest2, Register src) {
        try (ScratchRegister scratchReg1 = masm.getScratchRegister();
                        ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register scratch1 = scratchReg1.getRegister();
            Register scratch2 = scratchReg2.getRegister();

            masm.ldp(64, dest0, scratch1, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, src, 0));
            masm.add(64, dest0, dest0, scratch1, LSL, 26);
            masm.ldp(64, scratch1, scratch2, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, src, 16));
            masm.add(64, dest0, dest0, scratch1, LSL, 52);

            masm.add(64, dest1, zr, scratch1, LSR, 12);
            masm.add(64, dest1, dest1, scratch2, LSL, 14);
            masm.ldr(64, scratch1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, src, 32));
            masm.add(64, dest1, dest1, scratch1, LSL, 40);

            if (!Register.None.equals(dest2)) {
                masm.add(64, dest2, zr, scratch1, LSR, 24);
            }
        }
    }

    private static void wideMul(AArch64MacroAssembler masm, Register prodLo, Register prodHi, Register n, Register m) {
        masm.mul(64, prodLo, n, m);
        masm.umulh(64, prodHi, n, m);
    }

    private static void wideMadd(AArch64MacroAssembler masm, Register sumLo, Register sumHi, Register n, Register m) {
        try (ScratchRegister scratchReg1 = masm.getScratchRegister();
                        ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register scratch1 = scratchReg1.getRegister();
            Register scratch2 = scratchReg2.getRegister();
            wideMul(masm, scratch1, scratch2, n, m);
            masm.adds(64, sumLo, sumLo, scratch1);
            masm.adc(64, sumHi, sumHi, scratch2);
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // Arguments
        Register input = asRegister(inputValue);
        Register length = asRegister(lengthValue);
        Register accumulator = asRegister(accumulatorValue);
        Register r = asRegister(rValue);

        // R_n is the 128-bit randomly-generated key, packed into two
        // registers. The caller passes this key to us as long[5], with
        // BITS_PER_LIMB = 26.
        Register rnd0 = r4;
        Register rnd1 = r5;

        // RR_n is (R_n >> 2) * 5
        Register rr0 = r6;
        Register rr1 = r7;

        // U_n is the current checksum
        // Use r20 instead of r10 here, since SVM treats r10 as a scratch register
        Register u0 = r20;
        Register u1 = r11;
        Register u2 = r12;
        Register s0 = r13;
        Register s1 = r14;
        Register s2 = r15;
        Register u0hi = r16;
        Register u1hi = r17;

        Label labelDone = new Label();
        Label labelLoop = new Label();

        // R_n is the 128-bit randomly-generated key, packed into two
        // registers. The caller passes this key to us as long[5], with
        // BITS_PER_LIMB = 26.
        pack26(masm, rnd0, rnd1, Register.None, r);

        masm.lsr(64, rr0, rnd0, 2);
        masm.add(64, rr0, rr0, rr0, LSL, 2);
        masm.lsr(64, rr1, rnd1, 2);
        masm.add(64, rr1, rr1, rr1, LSL, 2);

        pack26(masm, u0, u1, u2, accumulator);

        masm.compare(64, length, 16);
        masm.branchConditionally(LT, labelDone);
        masm.bind(labelLoop);

        // S_n is to be the sum of U_n and the next block of data
        masm.ldp(64, s0, s1, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, input, 16));
        masm.adds(64, s0, u0, s0);
        masm.adcs(64, s1, u1, s1);
        masm.adc(64, s2, u2, zr);
        masm.add(64, s2, s2, 1);

        // NB: this logic depends on some of the special properties of
        // Poly1305 keys. In particular, because we know that the top
        // four bits of R_0 and R_1 are zero, we can add together
        // partial products without any risk of needing to propagate a
        // carry out.
        wideMul(masm, u0, u0hi, s0, rnd0);
        wideMadd(masm, u0, u0hi, s1, rr1);
        wideMadd(masm, u0, u0hi, s2, rr0);
        wideMul(masm, u1, u1hi, s0, rnd1);
        wideMadd(masm, u1, u1hi, s1, rnd0);
        wideMadd(masm, u1, u1hi, s2, rr1);
        masm.and(64, u2, rnd0, 3);
        masm.mul(64, u2, s2, u2);

        // Recycle registers S_0, S_1, S_2

        // Partial reduction mod 2**130 - 5
        masm.adds(64, u1, u0hi, u1);
        masm.adc(64, u2, u1hi, u2);

        // Sum now in U_2:U_1:U_0.
        // Dead: U_0HI, U_1HI.

        // U_2:U_1:U_0 += (U_2 >> 2) * 5 in two steps

        try (ScratchRegister scratchReg1 = masm.getScratchRegister();
                        ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg1.getRegister();
            Register rscratch2 = scratchReg2.getRegister();

            // First, U_2:U_1:U_0 += (U_2 >> 2)
            masm.lsr(64, rscratch1, u2, 2);
            masm.and(64, u2, u2, 3);
            masm.adds(64, u0, u0, rscratch1);
            masm.adcs(64, u1, u1, zr);
            masm.adc(64, u2, u2, zr);

            // Second, U_2:U_1:U_0 += (U_2 >> 2) << 2
            masm.adds(64, u0, u0, rscratch1, LSL, 2);
            masm.adcs(64, u1, u1, zr);
            masm.adc(64, u2, u2, zr);

            masm.sub(64, length, length, 16);
            masm.compare(64, length, 16);
            masm.branchConditionally(GE, labelLoop);

            // Further reduce modulo 2^130 - 5
            masm.lsr(64, rscratch1, u2, 2);
            masm.add(64, rscratch1, rscratch1, rscratch1, LSL, 2);
            masm.adds(64, u0, u0, rscratch1);
            masm.adcs(64, u1, u1, zr);
            masm.and(64, u2, u2, 3);
            masm.adc(64, u2, u2, zr);

            // Unpack the sum into five 26-bit limbs and write to memory.
            masm.ubfx(64, rscratch1, u0, 0, 26);
            masm.ubfx(64, rscratch2, u0, 26, 26);
            masm.stp(64, rscratch1, rscratch2, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, accumulator, 0));
            masm.ubfx(64, rscratch1, u0, 52, 12);
            masm.bfi(64, rscratch1, u1, 12, 14);
            masm.ubfx(64, rscratch2, u1, 14, 26);
            masm.stp(64, rscratch1, rscratch2, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, accumulator, 16));
            masm.ubfx(64, rscratch1, u1, 40, 24);
            masm.bfi(64, rscratch1, u2, 24, 3);
            masm.str(64, rscratch1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, accumulator, 32));

            masm.bind(labelDone);
        }
    }
}
