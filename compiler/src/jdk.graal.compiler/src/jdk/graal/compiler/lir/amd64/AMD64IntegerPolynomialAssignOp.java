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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTQ_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPANDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPAND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_IFMA;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/x86/stubGenerator_x86_64_poly_mont.cpp#L641-L734",
          sha1 = "b58e0f03339c73b0ad6719795cc137099bc8e79e")
// @formatter:on
public final class AMD64IntegerPolynomialAssignOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64IntegerPolynomialAssignOp> TYPE = LIRInstructionClass.create(AMD64IntegerPolynomialAssignOp.class);

    @Use private Value setValue;
    @Use private Value aValue;
    @Use private Value bValue;
    @Use private Value lengthValue;

    @Temp private Value[] temps;

    public AMD64IntegerPolynomialAssignOp(AllocatableValue set,
                    AllocatableValue a,
                    AllocatableValue b,
                    AllocatableValue length) {
        super(TYPE);
        this.setValue = set;
        this.aValue = a;
        this.bValue = b;
        this.lengthValue = length;
        GraalError.guarantee(set instanceof RegisterValue setReg && rdi.equals(setReg.getRegister()), "set should be fixed to rdi, but is %s", set);
        GraalError.guarantee(a instanceof RegisterValue aReg && rsi.equals(aReg.getRegister()), "a should be fixed to rsi, but is %s", a);
        GraalError.guarantee(b instanceof RegisterValue bReg && rdx.equals(bReg.getRegister()), "b should be fixed to rdx, but is %s", b);
        GraalError.guarantee(length instanceof RegisterValue lengthReg && rcx.equals(lengthReg.getRegister()), "length should be fixed to rcx, but is %s", length);
        this.temps = registersToValues(new Register[]{rdi, rsi, rdx, rcx, r9, xmm0, xmm1, xmm2});
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register set = asRegister(setValue);
        Register aLimbs = asRegister(aValue);
        Register bLimbs = asRegister(bValue);
        Register length = asRegister(lengthValue);
        Register a = xmm0;
        Register b = xmm1;
        Register select = xmm2;
        Register tmp = r9;

        Label labelLength5 = new Label();
        Label labelLength10 = new Label();
        Label labelLength14 = new Label();
        Label labelLength16 = new Label();
        Label labelLength19 = new Label();
        Label labelDefaultLoop = new Label();
        Label labelDone = new Label();

        masm.negq(set);
        if (masm.supports(AVX512_IFMA, AVX512VL, AVX512BW, AVX512F)) {
            EVPBROADCASTQ_GPR.emit(masm, AVXSize.ZMM, select, set);
        } else {
            VMOVQ.emit(masm, AVXSize.XMM, select, set);
            VPBROADCASTQ.emit(masm, AVXSize.YMM, select, select);
        }

        // NOTE! Crypto code cannot branch on user input. However; allowed to branch on number of limbs;
        // Number of limbs is a constant in each IntegerPolynomial (i.e. this side-channel branch leaks
        // number of limbs which is not a secret)
        masm.cmplAndJcc(length, 5, ConditionFlag.Equal, labelLength5, false);
        masm.cmplAndJcc(length, 10, ConditionFlag.Equal, labelLength10, false);
        masm.cmplAndJcc(length, 14, ConditionFlag.Equal, labelLength14, false);
        masm.cmplAndJcc(length, 16, ConditionFlag.Equal, labelLength16, false);
        masm.cmplAndJcc(length, 19, ConditionFlag.Equal, labelLength19, false);

        // Default copy loop (UNLIKELY)
        masm.cmplAndJcc(length, 0, ConditionFlag.LessEqual, labelDone, false);
        masm.bind(labelDefaultLoop);
        assignScalar(masm, aLimbs, bLimbs, 0, set, tmp);
        masm.subl(length, 1);
        masm.leaq(aLimbs, new AMD64Address(aLimbs, 8));
        masm.leaq(bLimbs, new AMD64Address(bLimbs, 8));
        masm.cmplAndJcc(length, 0, ConditionFlag.Greater, labelDefaultLoop, false);
        masm.jmp(labelDone);

        masm.bind(labelLength5); // 1 + 4
        assignScalar(masm, aLimbs, bLimbs, 0, set, tmp);
        assignAVX(masm, aLimbs, bLimbs, 8, select, a, b, AVXSize.YMM);
        masm.jmp(labelDone);

        masm.bind(labelLength10); // 2 + 8
        assignAVX(masm, aLimbs, bLimbs, 0, select, a, b, AVXSize.XMM);
        assignAVX(masm, aLimbs, bLimbs, 16, select, a, b, AVXSize.ZMM);
        masm.jmp(labelDone);

        masm.bind(labelLength14); // 2 + 4 + 8
        assignAVX(masm, aLimbs, bLimbs, 0, select, a, b, AVXSize.XMM);
        assignAVX(masm, aLimbs, bLimbs, 16, select, a, b, AVXSize.YMM);
        assignAVX(masm, aLimbs, bLimbs, 48, select, a, b, AVXSize.ZMM);
        masm.jmp(labelDone);

        masm.bind(labelLength16); // 8 + 8
        assignAVX(masm, aLimbs, bLimbs, 0, select, a, b, AVXSize.ZMM);
        assignAVX(masm, aLimbs, bLimbs, 64, select, a, b, AVXSize.ZMM);
        masm.jmp(labelDone);

        masm.bind(labelLength19); // 1 + 2 + 8 + 8
        assignScalar(masm, aLimbs, bLimbs, 0, set, tmp);
        assignAVX(masm, aLimbs, bLimbs, 8, select, a, b, AVXSize.XMM);
        assignAVX(masm, aLimbs, bLimbs, 24, select, a, b, AVXSize.ZMM);
        assignAVX(masm, aLimbs, bLimbs, 88, select, a, b, AVXSize.ZMM);

        masm.bind(labelDone);
    }

    private static void assignAVX(AMD64MacroAssembler masm, Register aBase, Register bBase, int offset, Register select, Register tmp, Register aTmp, AVXSize size) {
        if (size == AVXSize.ZMM && !masm.supports(AVX512F)) {
            assignAVX(masm, aBase, bBase, offset, select, tmp, aTmp, AVXSize.YMM);
            assignAVX(masm, aBase, bBase, offset + 32, select, tmp, aTmp, AVXSize.YMM);
            return;
        }

        AMD64Address aAddr = new AMD64Address(aBase, offset);
        AMD64Address bAddr = new AMD64Address(bBase, offset);

        // Original java:
        // long dummyLimbs = maskValue & (a[i] ^ b[i]);
        // a[i] = dummyLimbs ^ a[i];
        if (size == AVXSize.ZMM) {
            EVMOVDQU64.emit(masm, size, tmp, aAddr);
            EVMOVDQU64.emit(masm, size, aTmp, tmp);
            EVPXORD.emit(masm, size, tmp, tmp, bAddr);
            EVPANDD.emit(masm, size, tmp, tmp, select);
            EVPXORD.emit(masm, size, tmp, tmp, aTmp);
            EVMOVDQU64.emit(masm, size, aAddr, tmp);
        } else {
            VMOVDQU64.emit(masm, size, tmp, aAddr);
            VMOVDQU64.emit(masm, size, aTmp, tmp);
            VPXOR.emit(masm, size, tmp, tmp, bAddr);
            VPAND.emit(masm, size, tmp, tmp, select);
            VPXOR.emit(masm, size, tmp, tmp, aTmp);
            VMOVDQU64.emit(masm, size, aAddr, tmp);
        }
    }

    private static void assignScalar(AMD64MacroAssembler masm, Register aBase, Register bBase, int offset, Register select, Register tmp) {
        // Original java:
        // long dummyLimbs = maskValue & (a[i] ^ b[i]);
        // a[i] = dummyLimbs ^ a[i];

        AMD64Address aAddr = new AMD64Address(aBase, offset);
        AMD64Address bAddr = new AMD64Address(bBase, offset);

        masm.movq(tmp, aAddr);
        masm.xorq(tmp, bAddr);
        masm.andq(tmp, select);
        masm.xorq(aAddr, tmp);
    }

}
