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

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_VBMI2;
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
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L3556-L3679",
          sha1 = "a54d2d9054409f9f1b1aaee44753740e0172b09b")
// @formatter:on
public final class AMD64BigIntegerLeftShiftOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64BigIntegerLeftShiftOp> TYPE = LIRInstructionClass.create(AMD64BigIntegerLeftShiftOp.class);

    private final int useAVX3Threshold;

    @Use({OperandFlag.REG}) private Value newArrValue;
    @Use({OperandFlag.REG}) private Value oldArrValue;
    @Use({OperandFlag.REG}) private Value newIdxValue;
    @Use({OperandFlag.REG}) private Value shiftCountValue;
    @Use({OperandFlag.REG}) private Value totalNumIterValue;

    @Temp({OperandFlag.REG}) private Value[] tmpValues;

    public AMD64BigIntegerLeftShiftOp(Value newArrValue, Value oldArrValue, Value newIdxValue, Value shiftCountValue, Value totalNumIterValue, int useAVX3Threshold) {
        super(TYPE);
        GraalError.guarantee(asRegister(newArrValue).equals(rdi), "expect newArr at rdi, but was %s", newArrValue);
        GraalError.guarantee(asRegister(oldArrValue).equals(rsi), "expect oldArr at rsi, but was %s", oldArrValue);
        GraalError.guarantee(asRegister(newIdxValue).equals(rdx), "expect newIdx at rdx, but was %s", newIdxValue);
        GraalError.guarantee(asRegister(shiftCountValue).equals(rcx), "expect shiftCount at rcx, but was %s", shiftCountValue);
        GraalError.guarantee(asRegister(totalNumIterValue).equals(r8), "expect totalNumIter at r8, but was %s", totalNumIterValue);

        this.useAVX3Threshold = useAVX3Threshold;
        this.newArrValue = newArrValue;
        this.oldArrValue = oldArrValue;
        this.newIdxValue = newIdxValue;
        this.shiftCountValue = shiftCountValue;
        this.totalNumIterValue = totalNumIterValue;
        this.tmpValues = new Value[]{
                        rdi.asValue(),
                        rax.asValue(),
                        rbx.asValue(),
                        r9.asValue(),
                        r10.asValue(),
                        r11.asValue(),
                        // vzeroupper clears upper bits of xmm0-xmm15.
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

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(newArrValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid newArrValue kind: %s", newArrValue);
        GraalError.guarantee(oldArrValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid oldArrValue kind: %s", oldArrValue);
        GraalError.guarantee(newIdxValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid newIdxValue kind: %s", newIdxValue);
        GraalError.guarantee(shiftCountValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid shiftCountValue kind: %s", shiftCountValue);
        GraalError.guarantee(totalNumIterValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid totalNumIterValue kind: %s", totalNumIterValue);
        GraalError.guarantee(masm.supports(AVX512F, AVX512_VBMI2), "BigInteger left shift worker requires AVX512F and AVX512_VBMI2 support");

        Register newArr = asRegister(newArrValue);
        Register oldArr = asRegister(oldArrValue);
        Register newIdx = asRegister(newIdxValue);
        // It was intentional to have shiftCount in rcx since it is used implicitly for shift.
        Register shiftCount = asRegister(shiftCountValue);
        Register totalNumIter = asRegister(totalNumIterValue);

        // Rename temps used throughout the code
        Register idx = r11;
        Register numIterTmp = rax;

        /*
         * HotSpot uses r14 for tmp5 and saves/restores it in the stub prologue. Graal models
         * tmp5 as a fixed LIR temp instead, so the allocator handles the callee-save discipline.
         */
        Register tmp3 = r9;
        Register tmp4 = r10;
        Register tmp5 = rbx;

        Register x0 = xmm0;
        Register x1 = xmm1;
        Register x2 = xmm2;

        Label shift512Loop = new Label();
        Label shiftTwo = new Label();
        Label shiftTwoLoop = new Label();
        Label shiftOne = new Label();
        Label exit = new Label();

        // Start idx from zero.
        masm.xorl(idx, idx);
        /*
         * Compute interior pointer for new array. We do this so that we can use same index for
         * both old and new arrays.
         */
        masm.leaq(newArr, new AMD64Address(newArr, newIdx, Stride.S4));
        masm.movl(numIterTmp, totalNumIter);

        // If vectorization is enabled, check if the number of iterations is at least 64
        // If not, then go to ShiftTwo shifting two numbers at a time
        masm.cmplAndJcc(totalNumIter, useAVX3Threshold / 64, ConditionFlag.Less, shiftTwo, false);
        if (useAVX3Threshold < 16 * 64) {
            masm.cmplAndJcc(totalNumIter, 16, ConditionFlag.Less, shiftTwo, false);
        }

        VexRROp.EVPBROADCASTD_GPR.emit(masm, AVXSize.ZMM, x0, shiftCount);
        masm.subl(numIterTmp, 16);

        masm.bind(shift512Loop);
        VexMoveOp.EVMOVDQU32.emit(masm, AVXSize.ZMM, x1, new AMD64Address(oldArr, idx, Stride.S4));
        VexMoveOp.EVMOVDQU32.emit(masm, AVXSize.ZMM, x2, new AMD64Address(oldArr, idx, Stride.S4, 4));
        masm.evpshldvd(x1, x2, x0);
        VexMoveOp.EVMOVDQU32.emit(masm, AVXSize.ZMM, new AMD64Address(newArr, idx, Stride.S4), x1);
        masm.addl(idx, 16);
        masm.subl(numIterTmp, 16);
        masm.jcc(ConditionFlag.GreaterEqual, shift512Loop);
        masm.addl(numIterTmp, 16);

        masm.bind(shiftTwo);
        masm.cmplAndJcc(totalNumIter, 1, ConditionFlag.Less, exit, false);
        masm.movl(tmp3, new AMD64Address(oldArr, idx, Stride.S4));
        masm.subl(numIterTmp, 2);
        masm.jcc(ConditionFlag.Less, shiftOne);

        masm.bind(shiftTwoLoop);
        masm.movl(tmp4, new AMD64Address(oldArr, idx, Stride.S4, 4));
        masm.movl(tmp5, new AMD64Address(oldArr, idx, Stride.S4, 8));
        masm.shldl(tmp3, tmp4);
        masm.shldl(tmp4, tmp5);
        masm.movl(new AMD64Address(newArr, idx, Stride.S4), tmp3);
        masm.movl(new AMD64Address(newArr, idx, Stride.S4, 4), tmp4);
        masm.movl(tmp3, tmp5);
        masm.addl(idx, 2);
        masm.subl(numIterTmp, 2);
        masm.jcc(ConditionFlag.GreaterEqual, shiftTwoLoop);

        // Do the last iteration
        masm.bind(shiftOne);
        masm.addl(numIterTmp, 2);
        masm.cmplAndJcc(numIterTmp, 1, ConditionFlag.Less, exit, false);
        masm.movl(tmp4, new AMD64Address(oldArr, idx, Stride.S4, 4));
        masm.shldl(tmp3, tmp4);
        masm.movl(new AMD64Address(newArr, idx, Stride.S4), tmp3);

        masm.bind(exit);
        masm.vzeroupper();
    }
}
