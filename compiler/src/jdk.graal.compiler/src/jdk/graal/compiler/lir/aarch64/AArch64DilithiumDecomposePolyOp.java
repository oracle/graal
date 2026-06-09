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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD4_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST4_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_PRE_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L6936-L7207",
          sha1 = "8e8474bad3e9580dd874a92e86c98bccd93100c8")
// @formatter:on
public final class AArch64DilithiumDecomposePolyOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64DilithiumDecomposePolyOp> TYPE = LIRInstructionClass.create(AArch64DilithiumDecomposePolyOp.class);

    private static final int DILITHIUM_Q = 8380417;
    private static final int DILITHIUM_Q_ADD = 5373807;

    private static final ArrayDataPointerConstant DILITHIUM_CONSTS = pointerConstant(16, new int[]{
                    0, 0, 0, 0,
                    DILITHIUM_Q, DILITHIUM_Q, DILITHIUM_Q, DILITHIUM_Q,
                    0, 0, 0, 0,
                    0, 0, 0, 0,
                    DILITHIUM_Q_ADD, DILITHIUM_Q_ADD, DILITHIUM_Q_ADD, DILITHIUM_Q_ADD,
    });

    private static final Register[] VS1 = {AArch64.v0, AArch64.v1, AArch64.v2, AArch64.v3};
    private static final Register[] VS2 = {AArch64.v4, AArch64.v5, AArch64.v6, AArch64.v7};
    private static final Register[] VS3 = {AArch64.v8, AArch64.v9, AArch64.v10, AArch64.v11};
    private static final Register[] VS4 = {AArch64.v12, AArch64.v13, AArch64.v14, AArch64.v15};
    private static final Register[] VS5 = {AArch64.v16, AArch64.v17, AArch64.v18, AArch64.v19};
    private static final Register[] VTMP = {AArch64.v20, AArch64.v21, AArch64.v22, AArch64.v23};

    @Def({REG}) private Value resultValue;

    @Use({REG}) private Value inputValue;
    @Use({REG}) private Value lowPartValue;
    @Use({REG}) private Value highPartValue;
    @Use({REG}) private Value twoGamma2Value;
    @Use({REG}) private Value multiplierValue;

    @Temp({REG}) private Value[] temps;

    public AArch64DilithiumDecomposePolyOp(AllocatableValue resultValue,
                    AllocatableValue inputValue,
                    AllocatableValue lowPartValue,
                    AllocatableValue highPartValue,
                    AllocatableValue twoGamma2Value,
                    AllocatableValue multiplierValue) {
        super(TYPE);
        GraalError.guarantee(asRegister(resultValue).equals(AArch64.r0), "expect resultValue at r0, but was %s", resultValue);
        GraalError.guarantee(asRegister(inputValue).equals(AArch64.r0), "expect inputValue at r0, but was %s", inputValue);
        GraalError.guarantee(asRegister(lowPartValue).equals(AArch64.r1), "expect lowPartValue at r1, but was %s", lowPartValue);
        GraalError.guarantee(asRegister(highPartValue).equals(AArch64.r2), "expect highPartValue at r2, but was %s", highPartValue);
        GraalError.guarantee(asRegister(twoGamma2Value).equals(AArch64.r3), "expect twoGamma2Value at r3, but was %s", twoGamma2Value);
        GraalError.guarantee(asRegister(multiplierValue).equals(AArch64.r4), "expect multiplierValue at r4, but was %s", multiplierValue);
        this.resultValue = resultValue;
        this.inputValue = inputValue;
        this.lowPartValue = lowPartValue;
        this.highPartValue = highPartValue;
        this.twoGamma2Value = twoGamma2Value;
        this.multiplierValue = multiplierValue;

        this.temps = new Value[]{
                        AArch64.r0.asValue(), // input is clobbered by post-indexed loads
                        AArch64.r1.asValue(), // lowPart is clobbered by post-indexed stores
                        AArch64.r2.asValue(), // highPart is clobbered by post-indexed stores
                        AArch64.r11.asValue(),
                        AArch64.r14.asValue(),
                        AArch64.v0.asValue(),
                        AArch64.v1.asValue(),
                        AArch64.v2.asValue(),
                        AArch64.v3.asValue(),
                        AArch64.v4.asValue(),
                        AArch64.v5.asValue(),
                        AArch64.v6.asValue(),
                        AArch64.v7.asValue(),
                        AArch64.v8.asValue(),
                        AArch64.v9.asValue(),
                        AArch64.v10.asValue(),
                        AArch64.v11.asValue(),
                        AArch64.v12.asValue(),
                        AArch64.v13.asValue(),
                        AArch64.v14.asValue(),
                        AArch64.v15.asValue(),
                        AArch64.v16.asValue(),
                        AArch64.v17.asValue(),
                        AArch64.v18.asValue(),
                        AArch64.v19.asValue(),
                        AArch64.v20.asValue(),
                        AArch64.v21.asValue(),
                        AArch64.v22.asValue(),
                        AArch64.v23.asValue(),
                        AArch64.v25.asValue(),
                        AArch64.v26.asValue(),
                        AArch64.v27.asValue(),
                        AArch64.v28.asValue(),
                        AArch64.v29.asValue(),
                        AArch64.v30.asValue(),
                        AArch64.v31.asValue(),
        };
    }

    private static void addVSeq4(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register[] right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.addVVV(FullReg, ElementSize.Word, dst[i], left[i], right[i]);
        }
    }

    private static void addVSeq4Reg(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.addVVV(FullReg, ElementSize.Word, dst[i], left[i], right);
        }
    }

    private static void subVSeq4(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register[] right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.subVVV(FullReg, ElementSize.Word, dst[i], left[i], right[i]);
        }
    }

    private static void subVSeq4Reg(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.subVVV(FullReg, ElementSize.Word, dst[i], left[i], right);
        }
    }

    private static void subRegVSeq4(AArch64MacroAssembler masm, Register[] dst, Register left, Register[] right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.subVVV(FullReg, ElementSize.Word, dst[i], left, right[i]);
        }
    }

    private static void mulVSeq4Reg(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.mulVVV(FullReg, ElementSize.Word, dst[i], left[i], right);
        }
    }

    private static void sshrVSeq4(AArch64MacroAssembler masm, Register[] dst, Register[] src, int shift) {
        for (int i = 0; i < 4; i++) {
            masm.neon.sshrVVI(FullReg, ElementSize.Word, dst[i], src[i], shift);
        }
    }

    private static void andVSeq4Reg(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.andVVV(FullReg, dst[i], left[i], right);
        }
    }

    private static void negVSeq4(AArch64MacroAssembler masm, Register[] dst, Register[] src) {
        for (int i = 0; i < 4; i++) {
            masm.neon.negVV(FullReg, ElementSize.Word, dst[i], src[i]);
        }
    }

    private static void orrVSeq4(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register[] right) {
        for (int i = 0; i < 4; i++) {
            masm.neon.orrVVV(FullReg, dst[i], left[i], right[i]);
        }
    }

    private static void notVSeq4(AArch64MacroAssembler masm, Register[] dst, Register[] src) {
        for (int i = 0; i < 4; i++) {
            masm.neon.notVV(FullReg, dst[i], src[i]);
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
        GraalError.guarantee(inputValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid inputValue kind: %s", inputValue);
        GraalError.guarantee(lowPartValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid lowPartValue kind: %s", lowPartValue);
        GraalError.guarantee(highPartValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid highPartValue kind: %s", highPartValue);
        GraalError.guarantee(twoGamma2Value.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid twoGamma2Value kind: %s", twoGamma2Value);
        GraalError.guarantee(multiplierValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid multiplierValue kind: %s", multiplierValue);

        Register input = asRegister(inputValue);
        Register lowPart = asRegister(lowPartValue);
        Register highPart = asRegister(highPartValue);
        Register twoGamma2 = asRegister(twoGamma2Value);
        Register multiplier = asRegister(multiplierValue);
        Register dilithiumConsts = AArch64.r14;
        Register tmp = AArch64.r11;

        try (AArch64MacroAssembler.ScratchRegister scratch1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister scratch2 = masm.getScratchRegister()) {
            Register len = scratch2.getRegister();
            GraalError.guarantee(!scratch1.getRegister().equals(len), "scratch registers must be distinct");

            loadExternalAddress(crb, masm, dilithiumConsts, DILITHIUM_CONSTS);

            // save callee-saved registers
            masm.fstp(64, AArch64.v8, AArch64.v9, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_PRE_INDEXED, AArch64.sp, -64));
            masm.fstp(64, AArch64.v10, AArch64.v11, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, AArch64.sp, 16));
            masm.fstp(64, AArch64.v12, AArch64.v13, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, AArch64.sp, 32));
            masm.fstp(64, AArch64.v14, AArch64.v15, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, AArch64.sp, 48));

            // populate constant registers
            masm.mov(64, tmp, zr);
            masm.add(64, tmp, tmp, 1);
            masm.neon.dupVG(FullReg, ElementSize.Word, AArch64.v25, tmp); // 1
            masm.fldr(128, AArch64.v30, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, dilithiumConsts, 16)); // q
            masm.fldr(128, AArch64.v31, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, dilithiumConsts, 64)); // addend for mod q reduce
            masm.neon.dupVG(FullReg, ElementSize.Word, AArch64.v28, twoGamma2); // 2 * gamma2
            masm.neon.dupVG(FullReg, ElementSize.Word, AArch64.v29, multiplier); // multiplier for mod 2 * gamma reduce
            masm.neon.subVVV(FullReg, ElementSize.Word, AArch64.v26, AArch64.v30, AArch64.v25); // q - 1
            masm.neon.sshrVVI(FullReg, ElementSize.Word, AArch64.v27, AArch64.v28, 1); // gamma2

            masm.mov(64, len, zr);
            masm.add(64, len, len, 1024);

            Label loop = new Label();
            masm.bind(loop);

            // load next 4x4S inputs interleaved: rplus --> vs1
            masm.neon.ld4MultipleVVVV(FullReg, ElementSize.Word, AArch64.v0, AArch64.v1, AArch64.v2, AArch64.v3,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, FullReg, ElementSize.Word, input, 64));

            // rplus = rplus - ((rplus + qadd) >> 23) * q
            addVSeq4Reg(masm, VTMP, VS1, AArch64.v31);
            sshrVSeq4(masm, VTMP, VTMP, 23);
            mulVSeq4Reg(masm, VTMP, VTMP, AArch64.v30);
            subVSeq4(masm, VS1, VS1, VTMP);

            // rplus = rplus + ((rplus >> 31) & dilithium_q);
            sshrVSeq4(masm, VTMP, VS1, 31);
            andVSeq4Reg(masm, VTMP, VTMP, AArch64.v30);
            addVSeq4(masm, VS1, VS1, VTMP);

            // quotient --> vs2
            // int quotient = (rplus * multiplier) >> 22;
            mulVSeq4Reg(masm, VTMP, VS1, AArch64.v29);
            sshrVSeq4(masm, VS2, VTMP, 22);

            // r0 --> vs3
            // int r0 = rplus - quotient * twoGamma2;
            mulVSeq4Reg(masm, VTMP, VS2, AArch64.v28);
            subVSeq4(masm, VS3, VS1, VTMP);

            // mask --> vs4
            // int mask = (twoGamma2 - r0) >> 22;
            subRegVSeq4(masm, VTMP, AArch64.v28, VS3);
            sshrVSeq4(masm, VS4, VTMP, 22);

            // r0 -= (mask & twoGamma2);
            andVSeq4Reg(masm, VTMP, VS4, AArch64.v28);
            subVSeq4(masm, VS3, VS3, VTMP);

            // quotient += (mask & 1);
            andVSeq4Reg(masm, VTMP, VS4, AArch64.v25);
            addVSeq4(masm, VS2, VS2, VTMP);

            // mask = (twoGamma2 / 2 - r0) >> 31;
            subRegVSeq4(masm, VTMP, AArch64.v27, VS3);
            sshrVSeq4(masm, VS4, VTMP, 31);

            // r0 -= (mask & twoGamma2);
            andVSeq4Reg(masm, VTMP, VS4, AArch64.v28);
            subVSeq4(masm, VS3, VS3, VTMP);

            // quotient += (mask & 1);
            andVSeq4Reg(masm, VTMP, VS4, AArch64.v25);
            addVSeq4(masm, VS2, VS2, VTMP);

            // r1 --> vs5
            // int r1 = rplus - r0 - (dilithium_q - 1);
            subVSeq4(masm, VTMP, VS1, VS3);
            subVSeq4Reg(masm, VS5, VTMP, AArch64.v26);

            // r1 --> vs1 (overwriting rplus)
            // r1 = (r1 | (-r1)) >> 31; // 0 if rplus - r0 == (dilithium_q - 1), -1 otherwise
            negVSeq4(masm, VTMP, VS5);
            orrVSeq4(masm, VTMP, VS5, VTMP);
            sshrVSeq4(masm, VS1, VTMP, 31);

            // r0 += ~r1;
            notVSeq4(masm, VTMP, VS1);
            addVSeq4(masm, VS3, VS3, VTMP);

            // r1 = r1 & quotient;
            for (int i = 0; i < 4; i++) {
                masm.neon.andVVV(FullReg, VS1[i], VS2[i], VS1[i]);
            }

            // store results inteleaved
            // lowPart[m] = r0;
            // highPart[m] = r1;
            masm.neon.st4MultipleVVVV(FullReg, ElementSize.Word, AArch64.v8, AArch64.v9, AArch64.v10, AArch64.v11,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4_MULTIPLE_4R, FullReg, ElementSize.Word, lowPart, 64));
            masm.neon.st4MultipleVVVV(FullReg, ElementSize.Word, AArch64.v0, AArch64.v1, AArch64.v2, AArch64.v3,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST4_MULTIPLE_4R, FullReg, ElementSize.Word, highPart, 64));

            masm.sub(64, len, len, 64);
            masm.compare(64, len, 64);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, loop);

            // restore callee-saved vector registers
            masm.fldp(64, AArch64.v14, AArch64.v15, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, AArch64.sp, 48));
            masm.fldp(64, AArch64.v12, AArch64.v13, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, AArch64.sp, 32));
            masm.fldp(64, AArch64.v10, AArch64.v11, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, AArch64.sp, 16));
            masm.fldp(64, AArch64.v8, AArch64.v9, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, AArch64.sp, 64));

            masm.mov(32, asRegister(resultValue), zr);
        }
    }
}
