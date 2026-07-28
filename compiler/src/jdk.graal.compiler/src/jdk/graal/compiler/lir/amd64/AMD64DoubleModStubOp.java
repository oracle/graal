/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2025, Intel Corporation. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Above;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.AboveEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Below;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.BelowEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NoParity;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding.VEX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMRoundingOp.EVEXRoundingMode.RZ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQA64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTQ_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VROUNDSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VDIVPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMRoundingOp.EVDIVSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMRoundingOp.EVFNMADD213SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMRoundingOp.VDIVSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMRoundingOp.VFNMADD213SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMRoundingOp.VFNMADD231SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPAND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPGTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMXCSROp.VLDMXCSR;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512DQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.FMA;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

@Opcode("AMD64_DOUBLE_MOD_STUB")
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/x86/stubGenerator_x86_64_fmod.cpp#L75-L281",
          sha1 = "20ab78cbc42ba2ed5af815adcf67310c5e885e60")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/x86/stubGenerator_x86_64_fmod.cpp#L283-L501",
          sha1 = "f75743de37797a57030b0900647b8fa5216e6050")
// @formatter:on
public final class AMD64DoubleModStubOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64DoubleModStubOp> TYPE = LIRInstructionClass.create(AMD64DoubleModStubOp.class);
    private static final ArrayDataPointerConstant ABS_MASK = pointerConstant(16, new long[]{
                    0x7FFFFFFFFFFFFFFFL, 0x7FFFFFFFFFFFFFFFL
    });
    private static final ArrayDataPointerConstant ONE_P_260 = pointerConstant(16, new long[]{
                    0x5030000000000000L, 0
    });
    private static final ArrayDataPointerConstant MAX_VALUE = pointerConstant(16, new long[]{
                    0x7FEFFFFFFFFFFFFFL, 0
    });
    private static final ArrayDataPointerConstant INF = pointerConstant(16, new long[]{
                    0x7FF0000000000000L, 0
    });
    private static final ArrayDataPointerConstant E307 = pointerConstant(16, new long[]{
                    0x7FE0000000000000L, 0
    });
    private static final ArrayDataPointerConstant MXCSR_STD = pointerConstant(4, new int[]{
                    0x1F80
    });
    private static final ArrayDataPointerConstant MXCSR_RZ = pointerConstant(4, new int[]{
                    0x7F80
    });

    @Def protected Value resultValue;
    @Use protected Value xValue;
    @Use protected Value yValue;
    @Temp protected Value[] temps;

    public AMD64DoubleModStubOp(Value resultValue, Value xValue, Value yValue) {
        super(TYPE);

        guaranteeFixedRegister(resultValue, xmm0, "resultValue");
        guaranteeFixedRegister(xValue, xmm0, "xValue");
        guaranteeFixedRegister(yValue, xmm1, "yValue");

        this.resultValue = resultValue;
        this.xValue = xValue;
        this.yValue = yValue;
        temps = registersToValues(new Register[]{AMD64.rax, AMD64.rcx, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8});
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (masm.supports(AVX512F, AVX512BW, AVX512DQ, AVX512VL)) {
            emitDRemAVX512FastPath(crb, masm);
        } else {
            emitDRemFMAFastPath(crb, masm);
        }
    }

    private static void emitDRemFMAFastPath(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(masm.supports(AVX, FMA), "AMD64 fmod FMA stub requires AVX and FMA, current features: %s", masm.getFeatures());

        Register resultReg = xmm0;
        Register xReg = xmm0;
        Register yReg = xmm1;
        Register absX = xmm4;
        Register absY = xmm3;
        Register signX = xmm2;
        Register corr = xmm5;
        Register zero = xmm1;
        Register scaledDivisor = xmm1;
        Register scaledDivisor2 = xmm0;
        Register originalY = xmm1;
        Label done = new Label();
        Label smallDivisor = new Label();
        Label largeDivisor = new Label();
        Label smallLoop = new Label();
        Label restoreSign = new Label();
        Label largeLoop = new Label();
        Label special = new Label();
        Label notYZero = new Label();
        Label invalid = new Label();
        Label scaleByE307 = new Label();
        Label reduceByScaledDivisor = new Label();
        Label scaledReductionDone = new Label();
        Label scaledDivisor2Loop = new Label();
        Label scaledDivisorLoop = new Label();
        Label fmodCont = new Label();

        masm.movq(signX, xReg);
        masm.movdqu(absY, recordExternalAddress(crb, ABS_MASK));
        VPAND.emit(masm, XMM, absX, signX, absY);
        VPAND.emit(masm, XMM, absY, yReg, absY);
        masm.movq(AMD64.rcx, 0x8000000000000000L);
        VMOVQ.emit(masm, XMM, corr, AMD64.rcx);
        VPAND.emit(masm, XMM, signX, signX, corr);

        masm.ucomisd(absY, absX);
        masm.jcc(BelowEqual, smallDivisor);
        VADDSD.emit(masm, XMM, resultReg, signX, xReg);
        masm.jmp(done);

        masm.bind(smallDivisor);
        VMULSD.emit(masm, XMM, resultReg, absY, recordExternalAddress(crb, ONE_P_260));
        masm.ucomisd(resultReg, absX);
        masm.jcc(BelowEqual, largeDivisor);

        VDIVPD.emit(masm, XMM, resultReg, absX, absY);
        masm.movapd(zero, resultReg);
        VFNMADD213SD.emit(masm, XMM, zero, absY, absX);
        masm.movq(corr, zero);
        VPXOR.emit(masm, XMM, zero, zero, zero);
        VPCMPGTQ.emit(masm, XMM, corr, zero, corr);
        VPADDQ.emit(masm, XMM, resultReg, corr, resultReg);
        VROUNDSD.emit(masm, XMM, resultReg, resultReg, resultReg, 3);
        VFNMADD213SD.emit(masm, XMM, resultReg, absY, absX);
        masm.align(16);

        masm.bind(smallLoop);
        masm.ucomisd(resultReg, absY);
        masm.jcc(Below, restoreSign);
        VDIVSD.emit(masm, XMM, absX, resultReg, absY);
        masm.movapd(corr, absX);
        VFNMADD213SD.emit(masm, XMM, corr, absY, resultReg);
        masm.movq(corr, corr);
        VPCMPGTQ.emit(masm, XMM, corr, zero, corr);
        VPADDQ.emit(masm, XMM, absX, corr, absX);
        VROUNDSD.emit(masm, XMM, absX, absX, absX, 3);
        VFNMADD231SD.emit(masm, XMM, resultReg, absY, absX);
        masm.jmp(smallLoop);

        masm.bind(largeDivisor);
        VLDMXCSR.emit(masm, XMM, recordExternalAddress(crb, MXCSR_RZ));

        VDIVPD.emit(masm, XMM, resultReg, absX, absY);
        VROUNDSD.emit(masm, XMM, resultReg, resultReg, resultReg, 3);

        VEXTRACTPS.emit(masm, XMM, AMD64.rax, resultReg, 1);

        masm.cmplAndJcc(AMD64.rax, 0x7feffffe, Above, special, false);

        VFNMADD213SD.emit(masm, XMM, resultReg, absY, absX);
        masm.jmp(largeLoop);

        masm.bind(special);
        VPXOR.emit(masm, XMM, corr, corr, corr);
        masm.ucomisd(absY, corr);
        masm.jcc(NotEqual, notYZero);
        masm.jcc(NoParity, invalid);

        masm.bind(notYZero);
        masm.movsd(corr, recordExternalAddress(crb, MAX_VALUE));
        masm.ucomisd(corr, absX);
        masm.jcc(Below, invalid);

        masm.movsd(resultReg, recordExternalAddress(crb, INF));
        masm.ucomisd(resultReg, absY);
        masm.jcc(AboveEqual, scaleByE307);
        VADDSD.emit(masm, XMM, resultReg, originalY, originalY);
        VLDMXCSR.emit(masm, XMM, recordExternalAddress(crb, MXCSR_STD));
        masm.jmp(done);

        masm.bind(invalid);
        VFNMADD213SD.emit(masm, XMM, resultReg, absY, absX);
        VLDMXCSR.emit(masm, XMM, recordExternalAddress(crb, MXCSR_STD));
        masm.jmp(done);

        masm.bind(scaleByE307);
        VMULSD.emit(masm, XMM, scaledDivisor, absY, recordExternalAddress(crb, E307));

        VDIVSD.emit(masm, XMM, resultReg, absX, scaledDivisor);
        VROUNDSD.emit(masm, XMM, resultReg, resultReg, resultReg, 3);

        VEXTRACTPS.emit(masm, XMM, AMD64.rax, resultReg, 1);

        masm.cmplAndJcc(AMD64.rax, 0x7fefffff, Below, reduceByScaledDivisor, false);
        VMULSD.emit(masm, XMM, scaledDivisor2, scaledDivisor, recordExternalAddress(crb, E307));
        masm.ucomisd(absX, scaledDivisor2);
        masm.jcc(Below, scaledReductionDone);
        masm.bind(scaledDivisor2Loop);
        VDIVSD.emit(masm, XMM, corr, absX, scaledDivisor2);
        VROUNDSD.emit(masm, XMM, corr, corr, corr, 3);
        VFNMADD231SD.emit(masm, XMM, absX, scaledDivisor2, corr);
        masm.ucomisd(absX, scaledDivisor2);
        masm.jcc(AboveEqual, scaledDivisor2Loop);
        masm.jmp(scaledReductionDone);
        masm.bind(reduceByScaledDivisor);
        VFNMADD231SD.emit(masm, XMM, absX, scaledDivisor, resultReg);

        masm.bind(scaledReductionDone);
        masm.ucomisd(absX, scaledDivisor);
        masm.jcc(AboveEqual, scaledDivisorLoop);
        masm.movapd(resultReg, absX);
        masm.jmp(largeLoop);
        masm.bind(scaledDivisorLoop);
        VDIVSD.emit(masm, XMM, resultReg, absX, scaledDivisor);
        VROUNDSD.emit(masm, XMM, resultReg, resultReg, resultReg, 3);
        VFNMADD213SD.emit(masm, XMM, resultReg, scaledDivisor, absX);

        masm.ucomisd(resultReg, scaledDivisor);
        masm.movapd(absX, resultReg);
        masm.jcc(AboveEqual, scaledDivisorLoop);
        masm.jmp(largeLoop);

        masm.align(16);
        masm.bind(fmodCont);
        VDIVSD.emit(masm, XMM, scaledDivisor, resultReg, absY);
        VROUNDSD.emit(masm, XMM, scaledDivisor, scaledDivisor, scaledDivisor, 3);
        VFNMADD231SD.emit(masm, XMM, resultReg, absY, scaledDivisor);

        masm.bind(largeLoop);
        masm.ucomisd(resultReg, absY);
        masm.jcc(AboveEqual, fmodCont);

        VLDMXCSR.emit(masm, XMM, recordExternalAddress(crb, MXCSR_STD));
        masm.bind(restoreSign);
        VPXOR.emit(masm, XMM, resultReg, signX, resultReg);
        masm.bind(done);
    }

    private static void emitDRemAVX512FastPath(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register resultReg = xmm0;
        Register xReg = xmm0;
        Register yReg = xmm1;
        Register originalX = xmm2;
        Register signX = xmm3;
        Register absY = xmm4;
        Register divisor = xmm5;
        Register absX = xmm6;
        Register work = xmm7;
        Register scaledDivisor = xmm1;
        Label done = new Label();
        Label quotientIsZero = new Label();
        Label quotientFinite = new Label();
        Label notYZero = new Label();
        Label invalid = new Label();
        Label scaleByE307 = new Label();
        Label fmodCheck = new Label();
        Label fmodLoop = new Label();
        Label fmodLoopBody = new Label();
        Label reduceByScaledDivisor = new Label();
        Label scaledDivisor2Loop = new Label();
        Label scaledDivisor2LoopBody = new Label();
        Label scaledReductionDone = new Label();
        Label scaledDivisorLoop = new Label();
        Label scaledDivisorLoopBody = new Label();

        VMOVDQA64.encoding(VEX).emit(masm, XMM, originalX, xReg);
        masm.movq(resultReg, resultReg);
        masm.movq(AMD64.rax, 0x7FFFFFFFFFFFFFFFL);
        EVPBROADCASTQ_GPR.emit(masm, XMM, signX, AMD64.rax);
        VPAND.emit(masm, XMM, absX, xReg, signX);
        VPAND.emit(masm, XMM, absY, yReg, signX);
        VPXOR.emit(masm, XMM, signX, absX, xReg);
        masm.movq(divisor, absY);
        EVDIVSD.emit(masm, XMM, resultReg, absX, divisor, RZ);
        masm.movq(resultReg, resultReg);
        VXORPD.emit(masm, XMM, work, work, work);
        VROUNDSD.emit(masm, XMM, resultReg, work, resultReg, 0xb);
        VEXTRACTPS.emit(masm, XMM, AMD64.rax, resultReg, 1);
        masm.testlAndJcc(AMD64.rax, AMD64.rax, Equal, quotientIsZero, false);
        masm.cmplAndJcc(AMD64.rax, 0x7feffffe, BelowEqual, quotientFinite, false);
        VPXOR.emit(masm, XMM, originalX, originalX, originalX);
        masm.ucomisd(absY, originalX);
        masm.jcc(NotEqual, notYZero);
        masm.jcc(NoParity, invalid);
        masm.bind(notYZero);
        masm.movsd(originalX, recordExternalAddress(crb, MAX_VALUE));
        masm.ucomisd(originalX, absX);
        masm.jcc(Below, invalid);
        masm.movsd(resultReg, recordExternalAddress(crb, INF));
        masm.ucomisd(resultReg, absY);
        masm.jcc(AboveEqual, scaleByE307);
        VADDSD.emit(masm, XMM, resultReg, yReg, yReg);
        masm.jmp(done);

        masm.align(32);
        masm.bind(quotientIsZero);
        VADDSD.emit(masm, XMM, resultReg, signX, originalX);
        masm.jmp(done);

        masm.align(8);
        masm.bind(quotientFinite);
        EVFNMADD213SD.emit(masm, XMM, resultReg, absY, absX, RZ);
        masm.bind(fmodCheck);
        masm.ucomisd(resultReg, absY);
        masm.jcc(AboveEqual, fmodLoop);
        VPXOR.emit(masm, XMM, resultReg, signX, resultReg);
        masm.jmp(done);

        masm.bind(fmodLoop);
        masm.movq(absX, resultReg);
        VPXOR.emit(masm, XMM, scaledDivisor, scaledDivisor, scaledDivisor);
        masm.align(32);
        masm.bind(fmodLoopBody);
        EVDIVSD.emit(masm, XMM, originalX, absX, divisor, RZ);
        masm.movq(originalX, originalX);
        VROUNDSD.emit(masm, XMM, originalX, scaledDivisor, originalX, 0xb);
        EVFNMADD213SD.emit(masm, XMM, originalX, absY, resultReg, RZ);
        masm.ucomisd(originalX, absY);
        masm.movq(absX, originalX);
        masm.movapd(resultReg, originalX);
        masm.jcc(AboveEqual, fmodLoopBody);
        VPXOR.emit(masm, XMM, resultReg, signX, originalX);
        masm.jmp(done);

        masm.bind(invalid);
        VFNMADD213SD.emit(masm, XMM, resultReg, absY, absX);
        masm.jmp(done);

        masm.bind(scaleByE307);
        VMULSD.emit(masm, XMM, scaledDivisor, absY, recordExternalAddress(crb, E307));
        masm.movq(originalX, scaledDivisor);
        EVDIVSD.emit(masm, XMM, resultReg, absX, originalX, RZ);
        masm.movq(resultReg, resultReg);
        VROUNDSD.emit(masm, XMM, work, work, resultReg, 0xb);
        VEXTRACTPS.emit(masm, XMM, AMD64.rax, work, 1);
        masm.cmplAndJcc(AMD64.rax, 0x7fefffff, Below, reduceByScaledDivisor, false);
        VMULSD.emit(masm, XMM, resultReg, scaledDivisor, recordExternalAddress(crb, E307));
        masm.ucomisd(absX, resultReg);
        masm.jcc(AboveEqual, scaledDivisor2Loop);
        masm.movapd(work, absX);
        masm.jmp(scaledReductionDone);

        masm.bind(reduceByScaledDivisor);
        EVFNMADD213SD.emit(masm, XMM, work, scaledDivisor, absX, RZ);
        masm.jmp(scaledReductionDone);

        masm.bind(scaledDivisor2Loop);
        VXORPD.emit(masm, XMM, xmm8, xmm8, xmm8);
        masm.align(32);
        masm.bind(scaledDivisor2LoopBody);
        EVDIVSD.emit(masm, XMM, work, absX, resultReg, RZ);
        masm.movq(work, work);
        VROUNDSD.emit(masm, XMM, work, xmm8, work, 0xb);
        EVFNMADD213SD.emit(masm, XMM, work, resultReg, absX, RZ);
        masm.ucomisd(work, resultReg);
        masm.movapd(absX, work);
        masm.jcc(AboveEqual, scaledDivisor2LoopBody);
        masm.bind(scaledReductionDone);
        masm.ucomisd(work, scaledDivisor);
        masm.jcc(AboveEqual, scaledDivisorLoop);
        masm.movapd(resultReg, work);
        masm.jmp(fmodCheck);

        masm.bind(scaledDivisorLoop);
        VXORPD.emit(masm, XMM, absX, absX, absX);
        masm.align(32);
        masm.bind(scaledDivisorLoopBody);
        EVDIVSD.emit(masm, XMM, resultReg, work, originalX, RZ);
        masm.movq(resultReg, resultReg);
        VROUNDSD.emit(masm, XMM, resultReg, absX, resultReg, 0xb);
        EVFNMADD213SD.emit(masm, XMM, resultReg, scaledDivisor, work, RZ);
        masm.ucomisd(resultReg, scaledDivisor);
        masm.movapd(work, resultReg);
        masm.jcc(AboveEqual, scaledDivisorLoopBody);
        masm.jmp(fmodCheck);
        masm.bind(done);
    }

}
