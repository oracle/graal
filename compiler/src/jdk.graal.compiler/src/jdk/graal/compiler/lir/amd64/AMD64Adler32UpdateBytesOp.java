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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.DIV;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.BYTE;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VBROADCASTF128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPHADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSLLD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.core.common.Stride.S1;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/58853f39377ea6168c4570327347294899d51f95/src/hotspot/cpu/x86/stubGenerator_x86_64_adler.cpp#L34-L357",
          sha1 = "c2dcec35dc071851bf37eb9d463aa4b86d793396")
// @formatter:on
@Opcode("AMD64_ADLER32_UPDATE_BYTES")
public final class AMD64Adler32UpdateBytesOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64Adler32UpdateBytesOp> TYPE = LIRInstructionClass.create(AMD64Adler32UpdateBytesOp.class);

    private static final int LIMIT = 5552;
    private static final int BASE = 65521;
    private static final int CHUNKSIZE = 16;
    private static final int CHUNKSIZE_M1 = CHUNKSIZE - 1;

    private static final ArrayDataPointerConstant ADLER32_ASCALE_TABLE = pointerConstant(64, new int[]{
                    0x00000000, 0x00000001, 0x00000002, 0x00000003,
                    0x00000004, 0x00000005, 0x00000006, 0x00000007,
                    0x00000008, 0x00000009, 0x0000000A, 0x0000000B,
                    0x0000000C, 0x0000000D, 0x0000000E, 0x0000000F
    });

    private static final ArrayDataPointerConstant ADLER32_SHUF0_TABLE = pointerConstant(32, new int[]{
                    0xFFFFFF00, 0xFFFFFF01, 0xFFFFFF02, 0xFFFFFF03,
                    0xFFFFFF04, 0xFFFFFF05, 0xFFFFFF06, 0xFFFFFF07
    });

    private static final ArrayDataPointerConstant ADLER32_SHUF1_TABLE = pointerConstant(32, new int[]{
                    0xFFFFFF08, 0xFFFFFF09, 0xFFFFFF0A, 0xFFFFFF0B,
                    0xFFFFFF0C, 0xFFFFFF0D, 0xFFFFFF0E, 0xFFFFFF0F
    });

    @Def private AllocatableValue result;
    @Use private AllocatableValue adler;
    @Use private AllocatableValue bufferAddress;
    @Use private AllocatableValue length;

    @Temp private Value[] temps;

    private final int useAVX3Threshold;

    public AMD64Adler32UpdateBytesOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, int useAVX3Threshold, AllocatableValue result, AllocatableValue adler,
                    AllocatableValue bufferAddress, AllocatableValue length) {
        super(TYPE);
        this.useAVX3Threshold = useAVX3Threshold;
        this.result = result;
        this.adler = adler;
        this.bufferAddress = bufferAddress;
        this.length = length;
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX, CPUFeature.AVX2), "Adler32 update bytes requires AVX2 support");
        GraalError.guarantee(result instanceof RegisterValue resultReg && rax.equals(resultReg.getRegister()), "result should be fixed to rax, but is %s", result);
        GraalError.guarantee(adler instanceof RegisterValue adlerReg && rdi.equals(adlerReg.getRegister()), "adler should be fixed to rdi, but is %s", adler);
        GraalError.guarantee(bufferAddress instanceof RegisterValue bufReg && rsi.equals(bufReg.getRegister()), "bufferAddress should be fixed to rsi, but is %s", bufferAddress);
        GraalError.guarantee(length instanceof RegisterValue lenReg && rdx.equals(lenReg.getRegister()), "length should be fixed to rdx, but is %s", length);
        this.temps = registersToValues(new Register[]{
                        rcx, rdi, rdx, r8, r9, r10, r11, r12, r13, r14,
                        xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8, xmm9, xmm10
        });
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        Register adlerReg = asRegister(adler);
        Register data = r9;
        Register size = r10;
        Register s = r11;
        Register aD = r12;
        Register bD = r8;
        Register end = r13;

        Register yshuf0 = xmm6;
        Register yshuf1 = xmm7;
        Register ya = xmm0;
        Register yb = xmm1;
        Register ydata0 = xmm2;
        Register ydata1 = xmm3;
        Register ysa = xmm4;
        Register ydata = ysa;
        Register xa = xmm0;
        Register xb = xmm1;
        Register xtmp0 = xmm2;
        Register xtmp1 = xmm3;
        Register xsa = xmm4;
        Register xtmp2 = xmm5;
        Register xtmp3 = xmm8;
        Register xtmp4 = xmm9;
        Register xtmp5 = xmm10;

        Label labelSloop1 = new Label();
        Label labelSloop1aAvx2 = new Label();
        Label labelSloop1aAvx3 = new Label();
        Label labelAvx3Reduce = new Label();
        Label labelSkipLoop1a = new Label();
        Label labelSkipLoop1aAvx3 = new Label();
        Label labelFinish = new Label();
        Label labelLt64 = new Label();
        Label labelDoFinal = new Label();
        Label labelFinalLoop = new Label();
        Label labelZeroSize = new Label();
        Label labelEnd = new Label();

        GraalError.guarantee(masm.supports(CPUFeature.AVX, CPUFeature.AVX2), "Adler32 update bytes requires AVX2 support");
        GraalError.guarantee(resultReg.equals(rax), "result should use rax, but is %s", resultReg);
        boolean supportsAvx512VL = masm.supports(CPUFeature.AVX512F, CPUFeature.AVX512VL);

        VMOVQ.emit(masm, XMM, xtmp3, r12);
        VMOVQ.emit(masm, XMM, xtmp4, r13);
        VMOVQ.emit(masm, XMM, xtmp5, r14);

        masm.vmovdqu(yshuf0, recordExternalAddress(crb, ADLER32_SHUF0_TABLE));
        masm.vmovdqu(yshuf1, recordExternalAddress(crb, ADLER32_SHUF1_TABLE));

        masm.movq(data, asRegister(bufferAddress));
        masm.movl(size, asRegister(length));

        masm.movl(bD, adlerReg);
        masm.shrl(bD, 16);
        masm.andl(adlerReg, 0xFFFF);
        masm.cmplAndJcc(size, 32, ConditionFlag.Below, labelLt64, false);
        masm.movdl(xa, adlerReg);

        masm.bind(labelSloop1);
        if (supportsAvx512VL) {
            EVPXORD.emit(masm, ZMM, yb, yb, yb);
        } else {
            masm.vpxor(yb, yb, yb, YMM);
        }
        masm.movl(s, LIMIT);
        masm.cmpl(s, size);
        masm.cmovl(ConditionFlag.Above, s, size); // s = min(size, LIMIT)
        masm.leaq(end, new AMD64Address(s, data, S1, -CHUNKSIZE_M1));
        masm.cmpqAndJcc(data, end, ConditionFlag.AboveEqual, labelSkipLoop1a, false);

        masm.align(32);
        if (supportsAvx512VL) {
            // AVX2 performs better for smaller inputs because of leaner post loop reduction sequence..
            masm.cmplAndJcc(s, Math.max(128, useAVX3Threshold), ConditionFlag.BelowEqual, labelSloop1aAvx2, false);
            masm.leaq(end, new AMD64Address(s, data, S1, -(2 * CHUNKSIZE - 1)));

            // @formatter:off
            // Some notes on vectorized main loop algorithm.
            // Additions are performed in slices of 16 bytes in the main loop.
            // input size : 64 bytes (a0 - a63).
            // Iteration0 : ya =  [a0 - a15]
            //              yb =  [a0 - a15]
            // Iteration1 : ya =  [a0 - a15] + [a16 - a31]
            //              yb =  2 x [a0 - a15] + [a16 - a31]
            // Iteration2 : ya =  [a0 - a15] + [a16 - a31] + [a32 - a47]
            //              yb =  3 x [a0 - a15] + 2 x [a16 - a31] + [a32 - a47]
            // Iteration4 : ya =  [a0 - a15] + [a16 - a31] + [a32 - a47] + [a48 - a63]
            //              yb =  4 x [a0 - a15] + 3 x [a16 - a31] + 2 x [a32 - a47] + [a48 - a63]
            // Before performing reduction we must scale the intermediate result appropriately.
            // Since addition was performed in chunks of 16 bytes, thus to match the scalar implementation
            // Oth lane element must be repeatedly added 16 times, 1st element 15 times and so on so forth.
            // Thus we first multiply yb by 16 followed by subtracting appropriately scaled ya value.
            // yb = 16 x yb  - [a0 - a15] x ya
            //    = 64 x [a0 - a15] + 48 x [a16 - a31] + 32 x [a32 - a47] + 16 x [a48 - a63]  -  [a0 - a15] x ya
            //    = 64 x a0 + 63 x a1 + 62 x a2 ...... + a63
            // @formatter:on
            masm.bind(labelSloop1aAvx3);
            EVPMOVZXBD.emit(masm, ZMM, ydata0, new AMD64Address(data, 0));
            EVPMOVZXBD.emit(masm, ZMM, ydata1, new AMD64Address(data, CHUNKSIZE));
            EVPADDD.emit(masm, ZMM, ya, ya, ydata0);
            EVPADDD.emit(masm, ZMM, yb, yb, ya);
            EVPADDD.emit(masm, ZMM, ya, ya, ydata1);
            EVPADDD.emit(masm, ZMM, yb, yb, ya);
            masm.addq(data, 2 * CHUNKSIZE);
            masm.cmpqAndJcc(data, end, ConditionFlag.Below, labelSloop1aAvx3, false);

            masm.addq(end, CHUNKSIZE);
            masm.cmpqAndJcc(data, end, ConditionFlag.AboveEqual, labelAvx3Reduce, false);

            EVPMOVZXBD.emit(masm, ZMM, ydata0, new AMD64Address(data, 0));
            EVPADDD.emit(masm, ZMM, ya, ya, ydata0);
            EVPADDD.emit(masm, ZMM, yb, yb, ya);
            masm.addq(data, CHUNKSIZE);

            masm.bind(labelAvx3Reduce);
            EVPSLLD.emit(masm, ZMM, yb, yb, 4); // b is scaled by 16(avx512))
            EVPMULLD.emit(masm, ZMM, ysa, ya, recordExternalAddress(crb, ADLER32_ASCALE_TABLE));

            // compute horizontal sums of ya, yb, ysa
            EVEXTRACTI64X4.emit(masm, ZMM, xtmp0, ya, 1);
            EVEXTRACTI64X4.emit(masm, ZMM, xtmp1, yb, 1);
            EVEXTRACTI64X4.emit(masm, ZMM, xtmp2, ysa, 1);
            masm.vpaddd(xtmp0, xtmp0, ya, YMM);
            masm.vpaddd(xtmp1, xtmp1, yb, YMM);
            masm.vpaddd(xtmp2, xtmp2, ysa, YMM);
            VEXTRACTI128.emit(masm, YMM, xa, xtmp0, 1);
            VEXTRACTI128.emit(masm, YMM, xb, xtmp1, 1);
            VEXTRACTI128.emit(masm, YMM, xsa, xtmp2, 1);
            masm.vpaddd(xa, xa, xtmp0, XMM);
            masm.vpaddd(xb, xb, xtmp1, XMM);
            masm.vpaddd(xsa, xsa, xtmp2, XMM);
            VPHADDD.emit(masm, XMM, xa, xa, xa);
            VPHADDD.emit(masm, XMM, xb, xb, xb);
            VPHADDD.emit(masm, XMM, xsa, xsa, xsa);
            VPHADDD.emit(masm, XMM, xa, xa, xa);
            VPHADDD.emit(masm, XMM, xb, xb, xb);
            VPHADDD.emit(masm, XMM, xsa, xsa, xsa);

            VPSUBD.emit(masm, XMM, xb, xb, xsa);

            masm.addq(end, CHUNKSIZE_M1);
            masm.testAndJcc(BYTE, s, CHUNKSIZE_M1, ConditionFlag.NotEqual, labelDoFinal, false);
            masm.jmp(labelSkipLoop1aAvx3);
        }

        masm.align(32);
        masm.bind(labelSloop1aAvx2);
        VBROADCASTF128.emit(masm, YMM, ydata, new AMD64Address(data, 0));
        masm.addq(data, CHUNKSIZE);
        masm.vpshufb(ydata0, ydata, yshuf0, YMM);
        masm.vpaddd(ya, ya, ydata0, YMM);
        masm.vpaddd(yb, yb, ya, YMM);
        masm.vpshufb(ydata1, ydata, yshuf1, YMM);
        masm.vpaddd(ya, ya, ydata1, YMM);
        masm.vpaddd(yb, yb, ya, YMM);
        masm.cmpqAndJcc(data, end, ConditionFlag.Below, labelSloop1aAvx2, false);

        masm.bind(labelSkipLoop1a);

        // reduce
        masm.vpslld(yb, yb, 3, YMM); // b is scaled by 8(avx)
        VPMULLD.emit(masm, YMM, ysa, ya, recordExternalAddress(crb, ADLER32_ASCALE_TABLE));

        // compute horizontal sums of ya, yb, ysa
        VEXTRACTI128.emit(masm, YMM, xtmp0, ya, 1);
        VEXTRACTI128.emit(masm, YMM, xtmp1, yb, 1);
        VEXTRACTI128.emit(masm, YMM, xtmp2, ysa, 1);
        masm.vpaddd(xa, xa, xtmp0, XMM);
        masm.vpaddd(xb, xb, xtmp1, XMM);
        masm.vpaddd(xsa, xsa, xtmp2, XMM);
        VPHADDD.emit(masm, XMM, xa, xa, xa);
        VPHADDD.emit(masm, XMM, xb, xb, xb);
        VPHADDD.emit(masm, XMM, xsa, xsa, xsa);
        VPHADDD.emit(masm, XMM, xa, xa, xa);
        VPHADDD.emit(masm, XMM, xb, xb, xb);
        VPHADDD.emit(masm, XMM, xsa, xsa, xsa);

        VPSUBD.emit(masm, XMM, xb, xb, xsa);

        masm.addq(end, CHUNKSIZE_M1);
        masm.testAndJcc(BYTE, s, CHUNKSIZE_M1, ConditionFlag.NotEqual, labelDoFinal, false);

        masm.bind(labelSkipLoop1aAvx3);
        // either we're done, or we just did LIMIT
        masm.subl(size, s);

        masm.movdl(rax, xa);
        masm.xorl(rdx, rdx);
        masm.movl(rcx, BASE);
        DIV.emit(masm, DWORD, rcx); // divide edx:eax by ecx, quot->eax, rem->edx
        masm.movl(aD, rdx);

        masm.movdl(rax, xb);
        masm.addl(rax, bD);
        masm.xorl(rdx, rdx);
        masm.movl(rcx, BASE);
        DIV.emit(masm, DWORD, rcx); // divide edx:eax by ecx, quot->eax, rem->edx
        masm.movl(bD, rdx);

        masm.testlAndJcc(size, size, ConditionFlag.Zero, labelFinish, false);

        // continue loop
        masm.movdl(xa, aD);
        masm.jmp(labelSloop1);

        masm.bind(labelFinish);
        masm.movl(rax, bD);
        masm.shll(rax, 16);
        masm.orl(rax, aD);
        masm.jmp(labelEnd);

        masm.bind(labelLt64);
        masm.movl(aD, adlerReg);
        masm.leaq(end, new AMD64Address(data, size, S1));
        masm.testlAndJcc(size, size, ConditionFlag.NotZero, labelFinalLoop, false);
        masm.jmp(labelZeroSize);

        masm.bind(labelDoFinal);
        masm.movdl(aD, xa);
        masm.movdl(rax, xb);
        masm.addl(bD, rax);

        masm.align(32);
        masm.bind(labelFinalLoop);
        masm.movzbl(rax, new AMD64Address(data, 0)); // movzx eax, byte[data]
        masm.addl(aD, rax);
        masm.addq(data, 1);
        masm.addl(bD, aD);
        masm.cmpqAndJcc(data, end, ConditionFlag.Below, labelFinalLoop, false);

        masm.bind(labelZeroSize);

        masm.movl(rax, aD);
        masm.xorl(rdx, rdx);
        masm.movl(rcx, BASE);
        DIV.emit(masm, DWORD, rcx); // div ecx -- divide edx:eax by ecx, quot->eax, rem->edx
        masm.movl(aD, rdx);

        masm.movl(rax, bD);
        masm.xorl(rdx, rdx);
        masm.movl(rcx, BASE);
        DIV.emit(masm, DWORD, rcx); // divide edx:eax by ecx, quot->eax, rem->edx
        masm.shll(rdx, 16);
        masm.orl(rdx, aD);
        masm.movl(resultReg, rdx);

        masm.bind(labelEnd);
        VMOVQ.emitReverse(masm, XMM, r14, xtmp5);
        VMOVQ.emitReverse(masm, XMM, r13, xtmp4);
        VMOVQ.emitReverse(masm, XMM, r12, xtmp3);
    }
}
