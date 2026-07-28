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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTD_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPANDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRAD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.load4Xmms;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.store4Xmms;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r8;
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
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/stubGenerator_x86_64_dilithium.cpp#L763-L1018",
          sha1 = "bc0aea164e8c5b04a46df8b895e023c94e355695")
// @formatter:on
public final class AMD64DilithiumDecomposePolyOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64DilithiumDecomposePolyOp> TYPE = LIRInstructionClass.create(AMD64DilithiumDecomposePolyOp.class);

    @Use({OperandFlag.REG}) private Value inputValue;
    @Use({OperandFlag.REG}) private Value lowPartValue;
    @Use({OperandFlag.REG}) private Value highPartValue;
    @Use({OperandFlag.REG}) private Value twoGamma2Value;
    @Use({OperandFlag.REG}) private Value multiplierValue;

    @Def({OperandFlag.REG}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AMD64DilithiumDecomposePolyOp(AllocatableValue resultValue, AllocatableValue inputValue, AllocatableValue lowPartValue, AllocatableValue highPartValue,
                    AllocatableValue twoGamma2Value, AllocatableValue multiplierValue) {
        super(TYPE);

        GraalError.guarantee(asRegister(resultValue).equals(rax), "expect resultValue at rax, but was %s", resultValue);
        GraalError.guarantee(asRegister(inputValue).equals(rdi), "expect inputValue at rdi, but was %s", inputValue);
        GraalError.guarantee(asRegister(lowPartValue).equals(rsi), "expect lowPartValue at rsi, but was %s", lowPartValue);
        GraalError.guarantee(asRegister(highPartValue).equals(rdx), "expect highPartValue at rdx, but was %s", highPartValue);
        GraalError.guarantee(asRegister(twoGamma2Value).equals(rcx), "expect twoGamma2Value at rcx, but was %s", twoGamma2Value);
        GraalError.guarantee(asRegister(multiplierValue).equals(r8), "expect multiplierValue at r8, but was %s", multiplierValue);

        this.resultValue = resultValue;
        this.inputValue = inputValue;
        this.lowPartValue = lowPartValue;
        this.highPartValue = highPartValue;
        this.twoGamma2Value = twoGamma2Value;
        this.multiplierValue = multiplierValue;

        this.temps = new Value[]{
                        rdi.asValue(), // input is clobbered by pointer adjustment
                        rsi.asValue(), // lowPart is clobbered by pointer adjustment
                        rdx.asValue(), // highPart is clobbered by pointer adjustment
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
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(inputValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid inputValue kind: %s", inputValue);
        GraalError.guarantee(lowPartValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid lowPartValue kind: %s", lowPartValue);
        GraalError.guarantee(highPartValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid highPartValue kind: %s", highPartValue);
        GraalError.guarantee(twoGamma2Value.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid twoGamma2Value kind: %s", twoGamma2Value);
        GraalError.guarantee(multiplierValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid multiplierValue kind: %s", multiplierValue);

        Register input = asRegister(inputValue);
        Register lowPart = asRegister(lowPartValue);
        Register highPart = asRegister(highPartValue);
        Register rTwoGamma2 = asRegister(twoGamma2Value);
        Register rMultiplier = asRegister(multiplierValue);

        Register len = r11;
        Register result = asRegister(resultValue);
        Register zero = xmm24;
        Register one = xmm25;
        Register qMinus1 = xmm26;
        Register gamma2 = xmm27;
        Register twoGamma2 = xmm28;
        Register barrettMultiplier = xmm29;
        Register barrettAddend = xmm30;
        Register dilithiumQ = xmm31;

        Label loop = new Label();

        masm.emit(EVPXORD, zero, zero, zero, ZMM);
        EVPTERNLOGD.emit(masm, ZMM, xmm0, xmm0, xmm0, 0xff);
        masm.emit(EVPSUBD, one, zero, xmm0, ZMM);

        EVPBROADCASTD.emit(masm, ZMM, dilithiumQ, recordExternalAddress(crb, AMD64DilithiumSupport.DILITHIUM_Q));
        EVPBROADCASTD.emit(masm, ZMM, barrettAddend, recordExternalAddress(crb, AMD64DilithiumSupport.BARRETT_ADDEND));

        EVPBROADCASTD_GPR.emit(masm, ZMM, twoGamma2, rTwoGamma2);
        EVPBROADCASTD_GPR.emit(masm, ZMM, barrettMultiplier, rMultiplier);

        masm.emit(EVPSUBD, qMinus1, dilithiumQ, one, ZMM);
        EVPSRAD.emit(masm, ZMM, gamma2, twoGamma2, 1);

        masm.movl(len, 1024);

        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);

        load4Xmms(masm, AMD64DilithiumSupport.XMM_0_3, input, 0);
        masm.addq(input, 4 * AMD64DilithiumSupport.XMM_BYTES);

        // rplus in xmm0
        // rplus = rplus - ((rplus + 5373807) >> 23) * dilithium_q;
        masm.emit(EVPADDD, xmm4, xmm0, barrettAddend, ZMM);
        masm.emit(EVPADDD, xmm5, xmm1, barrettAddend, ZMM);
        masm.emit(EVPADDD, xmm6, xmm2, barrettAddend, ZMM);
        masm.emit(EVPADDD, xmm7, xmm3, barrettAddend, ZMM);

        EVPSRAD.emit(masm, ZMM, xmm4, xmm4, 23);
        EVPSRAD.emit(masm, ZMM, xmm5, xmm5, 23);
        EVPSRAD.emit(masm, ZMM, xmm6, xmm6, 23);
        EVPSRAD.emit(masm, ZMM, xmm7, xmm7, 23);

        masm.emit(EVPMULLD, xmm4, xmm4, dilithiumQ, ZMM);
        masm.emit(EVPMULLD, xmm5, xmm5, dilithiumQ, ZMM);
        masm.emit(EVPMULLD, xmm6, xmm6, dilithiumQ, ZMM);
        masm.emit(EVPMULLD, xmm7, xmm7, dilithiumQ, ZMM);

        masm.emit(EVPSUBD, xmm0, xmm0, xmm4, ZMM);
        masm.emit(EVPSUBD, xmm1, xmm1, xmm5, ZMM);
        masm.emit(EVPSUBD, xmm2, xmm2, xmm6, ZMM);
        masm.emit(EVPSUBD, xmm3, xmm3, xmm7, ZMM);

        // rplus in xmm0
        // rplus = rplus + ((rplus >> 31) & dilithium_q);
        EVPSRAD.emit(masm, ZMM, xmm4, xmm0, 31);
        EVPSRAD.emit(masm, ZMM, xmm5, xmm1, 31);
        EVPSRAD.emit(masm, ZMM, xmm6, xmm2, 31);
        EVPSRAD.emit(masm, ZMM, xmm7, xmm3, 31);

        masm.emit(EVPANDD, xmm4, xmm4, dilithiumQ, ZMM);
        masm.emit(EVPANDD, xmm5, xmm5, dilithiumQ, ZMM);
        masm.emit(EVPANDD, xmm6, xmm6, dilithiumQ, ZMM);
        masm.emit(EVPANDD, xmm7, xmm7, dilithiumQ, ZMM);

        masm.emit(EVPADDD, xmm0, xmm0, xmm4, ZMM);
        masm.emit(EVPADDD, xmm1, xmm1, xmm5, ZMM);
        masm.emit(EVPADDD, xmm2, xmm2, xmm6, ZMM);
        masm.emit(EVPADDD, xmm3, xmm3, xmm7, ZMM);

        // rplus in xmm0
        // int quotient = (rplus * barrettMultiplier) >> 22;
        masm.emit(EVPMULLD, xmm4, xmm0, barrettMultiplier, ZMM);
        masm.emit(EVPMULLD, xmm5, xmm1, barrettMultiplier, ZMM);
        masm.emit(EVPMULLD, xmm6, xmm2, barrettMultiplier, ZMM);
        masm.emit(EVPMULLD, xmm7, xmm3, barrettMultiplier, ZMM);

        EVPSRAD.emit(masm, ZMM, xmm4, xmm4, 22);
        EVPSRAD.emit(masm, ZMM, xmm5, xmm5, 22);
        EVPSRAD.emit(masm, ZMM, xmm6, xmm6, 22);
        EVPSRAD.emit(masm, ZMM, xmm7, xmm7, 22);

        // quotient in xmm4
        // int r0 = rplus - quotient * twoGamma2;
        masm.emit(EVPMULLD, xmm8, xmm4, twoGamma2, ZMM);
        masm.emit(EVPMULLD, xmm9, xmm5, twoGamma2, ZMM);
        masm.emit(EVPMULLD, xmm10, xmm6, twoGamma2, ZMM);
        masm.emit(EVPMULLD, xmm11, xmm7, twoGamma2, ZMM);

        masm.emit(EVPSUBD, xmm8, xmm0, xmm8, ZMM);
        masm.emit(EVPSUBD, xmm9, xmm1, xmm9, ZMM);
        masm.emit(EVPSUBD, xmm10, xmm2, xmm10, ZMM);
        masm.emit(EVPSUBD, xmm11, xmm3, xmm11, ZMM);

        // r0 in xmm8
        // int mask = (twoGamma2 - r0) >> 22;
        masm.emit(EVPSUBD, xmm12, twoGamma2, xmm8, ZMM);
        masm.emit(EVPSUBD, xmm13, twoGamma2, xmm9, ZMM);
        masm.emit(EVPSUBD, xmm14, twoGamma2, xmm10, ZMM);
        masm.emit(EVPSUBD, xmm15, twoGamma2, xmm11, ZMM);

        EVPSRAD.emit(masm, ZMM, xmm12, xmm12, 22);
        EVPSRAD.emit(masm, ZMM, xmm13, xmm13, 22);
        EVPSRAD.emit(masm, ZMM, xmm14, xmm14, 22);
        EVPSRAD.emit(masm, ZMM, xmm15, xmm15, 22);

        // mask in xmm12
        // r0 -= (mask & twoGamma2);
        masm.emit(EVPANDD, xmm16, xmm12, twoGamma2, ZMM);
        masm.emit(EVPANDD, xmm17, xmm13, twoGamma2, ZMM);
        masm.emit(EVPANDD, xmm18, xmm14, twoGamma2, ZMM);
        masm.emit(EVPANDD, xmm19, xmm15, twoGamma2, ZMM);

        masm.emit(EVPSUBD, xmm8, xmm8, xmm16, ZMM);
        masm.emit(EVPSUBD, xmm9, xmm9, xmm17, ZMM);
        masm.emit(EVPSUBD, xmm10, xmm10, xmm18, ZMM);
        masm.emit(EVPSUBD, xmm11, xmm11, xmm19, ZMM);

        // r0 in xmm8
        // quotient += (mask & 1);
        masm.emit(EVPANDD, xmm16, xmm12, one, ZMM);
        masm.emit(EVPANDD, xmm17, xmm13, one, ZMM);
        masm.emit(EVPANDD, xmm18, xmm14, one, ZMM);
        masm.emit(EVPANDD, xmm19, xmm15, one, ZMM);

        masm.emit(EVPADDD, xmm4, xmm4, xmm16, ZMM);
        masm.emit(EVPADDD, xmm5, xmm5, xmm17, ZMM);
        masm.emit(EVPADDD, xmm6, xmm6, xmm18, ZMM);
        masm.emit(EVPADDD, xmm7, xmm7, xmm19, ZMM);

        // mask = (twoGamma2 / 2 - r0) >> 31;
        masm.emit(EVPSUBD, xmm12, gamma2, xmm8, ZMM);
        masm.emit(EVPSUBD, xmm13, gamma2, xmm9, ZMM);
        masm.emit(EVPSUBD, xmm14, gamma2, xmm10, ZMM);
        masm.emit(EVPSUBD, xmm15, gamma2, xmm11, ZMM);

        EVPSRAD.emit(masm, ZMM, xmm12, xmm12, 31);
        EVPSRAD.emit(masm, ZMM, xmm13, xmm13, 31);
        EVPSRAD.emit(masm, ZMM, xmm14, xmm14, 31);
        EVPSRAD.emit(masm, ZMM, xmm15, xmm15, 31);

        // r0 -= (mask & twoGamma2);
        masm.emit(EVPANDD, xmm16, xmm12, twoGamma2, ZMM);
        masm.emit(EVPANDD, xmm17, xmm13, twoGamma2, ZMM);
        masm.emit(EVPANDD, xmm18, xmm14, twoGamma2, ZMM);
        masm.emit(EVPANDD, xmm19, xmm15, twoGamma2, ZMM);

        masm.emit(EVPSUBD, xmm8, xmm8, xmm16, ZMM);
        masm.emit(EVPSUBD, xmm9, xmm9, xmm17, ZMM);
        masm.emit(EVPSUBD, xmm10, xmm10, xmm18, ZMM);
        masm.emit(EVPSUBD, xmm11, xmm11, xmm19, ZMM);

        // r0 in xmm8
        // quotient += (mask & 1);
        masm.emit(EVPANDD, xmm16, xmm12, one, ZMM);
        masm.emit(EVPANDD, xmm17, xmm13, one, ZMM);
        masm.emit(EVPANDD, xmm18, xmm14, one, ZMM);
        masm.emit(EVPANDD, xmm19, xmm15, one, ZMM);

        masm.emit(EVPADDD, xmm4, xmm4, xmm16, ZMM);
        masm.emit(EVPADDD, xmm5, xmm5, xmm17, ZMM);
        masm.emit(EVPADDD, xmm6, xmm6, xmm18, ZMM);
        masm.emit(EVPADDD, xmm7, xmm7, xmm19, ZMM);

        // quotient in xmm4
        // int r1 = rplus - r0 - (dilithium_q - 1);
        masm.emit(EVPSUBD, xmm16, xmm0, xmm8, ZMM);
        masm.emit(EVPSUBD, xmm17, xmm1, xmm9, ZMM);
        masm.emit(EVPSUBD, xmm18, xmm2, xmm10, ZMM);
        masm.emit(EVPSUBD, xmm19, xmm3, xmm11, ZMM);

        masm.emit(EVPSUBD, xmm16, xmm16, qMinus1, ZMM);
        masm.emit(EVPSUBD, xmm17, xmm17, qMinus1, ZMM);
        masm.emit(EVPSUBD, xmm18, xmm18, qMinus1, ZMM);
        masm.emit(EVPSUBD, xmm19, xmm19, qMinus1, ZMM);

        // r1 in xmm16
        // r1 = (r1 | (-r1)) >> 31; // 0 if rplus - r0 == (dilithium_q - 1), -1 otherwise
        masm.emit(EVPSUBD, xmm20, zero, xmm16, ZMM);
        masm.emit(EVPSUBD, xmm21, zero, xmm17, ZMM);
        masm.emit(EVPSUBD, xmm22, zero, xmm18, ZMM);
        masm.emit(EVPSUBD, xmm23, zero, xmm19, ZMM);

        masm.emit(EVPORQ, xmm16, xmm16, xmm20, ZMM);
        masm.emit(EVPORQ, xmm17, xmm17, xmm21, ZMM);
        masm.emit(EVPORQ, xmm18, xmm18, xmm22, ZMM);
        masm.emit(EVPORQ, xmm19, xmm19, xmm23, ZMM);

        masm.emit(EVPSUBD, xmm12, zero, one, ZMM);

        // r1 in xmm0
        // r0 += ~r1;
        EVPSRAD.emit(masm, ZMM, xmm0, xmm16, 31);
        EVPSRAD.emit(masm, ZMM, xmm1, xmm17, 31);
        EVPSRAD.emit(masm, ZMM, xmm2, xmm18, 31);
        EVPSRAD.emit(masm, ZMM, xmm3, xmm19, 31);

        masm.emit(EVPXORQ, xmm20, xmm0, xmm12, ZMM);
        masm.emit(EVPXORQ, xmm21, xmm1, xmm12, ZMM);
        masm.emit(EVPXORQ, xmm22, xmm2, xmm12, ZMM);
        masm.emit(EVPXORQ, xmm23, xmm3, xmm12, ZMM);

        masm.emit(EVPADDD, xmm8, xmm8, xmm20, ZMM);
        masm.emit(EVPADDD, xmm9, xmm9, xmm21, ZMM);
        masm.emit(EVPADDD, xmm10, xmm10, xmm22, ZMM);
        masm.emit(EVPADDD, xmm11, xmm11, xmm23, ZMM);

        // r0 in xmm8
        // r1 = r1 & quotient;
        masm.emit(EVPANDD, xmm0, xmm4, xmm0, ZMM);
        masm.emit(EVPANDD, xmm1, xmm5, xmm1, ZMM);
        masm.emit(EVPANDD, xmm2, xmm6, xmm2, ZMM);
        masm.emit(EVPANDD, xmm3, xmm7, xmm3, ZMM);

        // r1 in xmm0
        // lowPart[m] = r0;
        // highPart[m] = r1;
        store4Xmms(masm, highPart, 0, AMD64DilithiumSupport.XMM_0_3);
        store4Xmms(masm, lowPart, 0, AMD64DilithiumSupport.XMM_8_11);

        masm.addq(highPart, 4 * AMD64DilithiumSupport.XMM_BYTES);
        masm.addq(lowPart, 4 * AMD64DilithiumSupport.XMM_BYTES);
        masm.subl(len, 4 * AMD64DilithiumSupport.XMM_BYTES);
        masm.jcc(ConditionFlag.NotEqual, loop);

        masm.xorl(result, result);
    }
}
