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
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.load4Xmms;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.montMul64;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.store4Xmms;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.r11;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/stubGenerator_x86_64_dilithium.cpp#L636-L700",
          sha1 = "b64ae7d8006be61433fc4440904d0851e1b67ba4")
// @formatter:on
public final class AMD64DilithiumNttMultOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64DilithiumNttMultOp> TYPE = LIRInstructionClass.create(AMD64DilithiumNttMultOp.class);

    @Use({OperandFlag.REG}) private Value resultPointerValue;
    @Use({OperandFlag.REG}) private Value coeffs1Value;
    @Use({OperandFlag.REG}) private Value coeffs2Value;

    @Def({OperandFlag.REG}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AMD64DilithiumNttMultOp(AllocatableValue resultValue, AllocatableValue resultPointerValue, AllocatableValue coeffs1Value, AllocatableValue coeffs2Value) {
        super(TYPE);

        GraalError.guarantee(asRegister(resultValue).equals(rax), "expect resultValue at rax, but was %s", resultValue);
        GraalError.guarantee(asRegister(resultPointerValue).equals(rdi), "expect resultPointerValue at rdi, but was %s", resultPointerValue);
        GraalError.guarantee(asRegister(coeffs1Value).equals(rsi), "expect coeffs1Value at rsi, but was %s", coeffs1Value);
        GraalError.guarantee(asRegister(coeffs2Value).equals(rdx), "expect coeffs2Value at rdx, but was %s", coeffs2Value);

        this.resultValue = resultValue;
        this.resultPointerValue = resultPointerValue;
        this.coeffs1Value = coeffs1Value;
        this.coeffs2Value = coeffs2Value;

        this.temps = new Value[]{
                        rdi.asValue(), // resultPointer is clobbered by pointer adjustment
                        rsi.asValue(), // coeffs1 is clobbered by pointer adjustment
                        rdx.asValue(), // coeffs2 is clobbered by pointer adjustment
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
        GraalError.guarantee(resultPointerValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid resultPointerValue kind: %s", resultPointerValue);
        GraalError.guarantee(coeffs1Value.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid coeffs1Value kind: %s", coeffs1Value);
        GraalError.guarantee(coeffs2Value.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid coeffs2Value kind: %s", coeffs2Value);

        Register resultPointer = asRegister(resultPointerValue);
        Register coeffs1 = asRegister(coeffs1Value);
        Register coeffs2 = asRegister(coeffs2Value);
        Register len = r11;

        Register result = asRegister(resultValue);

        Label loop = new Label();

        EVPBROADCASTD.emit(masm, ZMM, xmm30, recordExternalAddress(crb, AMD64DilithiumSupport.MONT_Q_INV_MOD_R));
        EVPBROADCASTD.emit(masm, ZMM, xmm31, recordExternalAddress(crb, AMD64DilithiumSupport.DILITHIUM_Q));
        EVPBROADCASTD.emit(masm, ZMM, xmm29, recordExternalAddress(crb, AMD64DilithiumSupport.MONT_R_SQUARE_MOD_Q));

        EVMOVDQU32.emit(masm, ZMM, xmm28, recordExternalAddress(crb, AMD64DilithiumSupport.MONT_MUL_PERMS));

        masm.movl(len, 4);

        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);

        // c load 32 (8x4S) next inputs from poly2
        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, coeffs2, 0);
        // b load 32 (8x4S) next inputs from poly1
        load4Xmms(masm, AMD64DilithiumSupport.XMM_0_3, coeffs1, 0);
        // compute a = b montmul c
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_29_29,
                        AMD64DilithiumSupport.XMM_16_27);
        // compute a = rsquare montmul a
        montMul64(masm, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_4_7,
                        AMD64DilithiumSupport.XMM_16_27, true);
        // save a 32 (8x4S) results
        store4Xmms(masm, resultPointer, 0, AMD64DilithiumSupport.XMM_0_3);

        masm.subl(len, 1);
        masm.addq(coeffs1, 4 * AMD64DilithiumSupport.XMM_BYTES);
        masm.addq(coeffs2, 4 * AMD64DilithiumSupport.XMM_BYTES);
        masm.addq(resultPointer, 4 * AMD64DilithiumSupport.XMM_BYTES);
        masm.cmpl(len, 0);
        masm.jcc(ConditionFlag.NotEqual, loop);

        masm.xorl(result, result);
    }
}
