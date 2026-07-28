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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPANDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSLLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRAW;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.F00;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.PERMS_12_TO_16_TABLE;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM24_27;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM28_31;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM2_0_10_8;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.load4regs;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.store4regs;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
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
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
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
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d8d51f3327b133af4f21cd6dd248324e4fc2a482/src/hotspot/cpu/x86/stubGenerator_x86_64_kyber.cpp#L809-L900",
          sha1 = "d7ce935e2743b96feb42446e8f6f745947541801")
// @formatter:on
public final class AMD64Kyber12To16Op extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64Kyber12To16Op> TYPE = LIRInstructionClass.create(AMD64Kyber12To16Op.class);

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value condensedValue;
    @Use({REG}) private Value condensedOffsValue;
    @Use({REG}) private Value parsedValue;
    @Use({REG}) private Value parsedLengthValue;
    @Temp({REG}) private Value[] temps;

    public AMD64Kyber12To16Op(AllocatableValue resultValue, AllocatableValue condensedValue, AllocatableValue condensedOffsValue, AllocatableValue parsedValue,
                    AllocatableValue parsedLengthValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, rax, "resultValue");
        guaranteeFixedRegister(condensedValue, rdi, "condensedValue");
        guaranteeFixedRegister(condensedOffsValue, rsi, "condensedOffsValue");
        guaranteeFixedRegister(parsedValue, rdx, "parsedValue");
        guaranteeFixedRegister(parsedLengthValue, rcx, "parsedLengthValue");
        this.resultValue = resultValue;
        this.condensedValue = condensedValue;
        this.condensedOffsValue = condensedOffsValue;
        this.parsedValue = parsedValue;
        this.parsedLengthValue = parsedLengthValue;
        this.temps = new Value[]{
                        rdi.asValue(),
                        rdx.asValue(),
                        rcx.asValue(),
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
        GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid %s kind: %s", "resultValue", resultValue);
        Register result = asRegister(resultValue);
        GraalError.guarantee(condensedValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "condensedValue", condensedValue);
        Register condensed = asRegister(condensedValue);
        GraalError.guarantee(condensedOffsValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid %s kind: %s", "condensedOffsValue", condensedOffsValue);
        Register condensedOffs = asRegister(condensedOffsValue);
        GraalError.guarantee(parsedValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "parsedValue", parsedValue);
        Register parsed = asRegister(parsedValue);
        GraalError.guarantee(parsedLengthValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid %s kind: %s", "parsedLengthValue", parsedLengthValue);
        Register parsedLength = asRegister(parsedLengthValue);

        Label loop = new Label();

        masm.addq(condensed, condensedOffs);
        masm.leaq(r11, recordExternalAddress(crb, PERMS_12_TO_16_TABLE));
        load4regs(masm, XMM24_27, r11, 0);
        load4regs(masm, XMM28_31, r11, 256);
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, xmm23, recordExternalAddress(crb, F00));

        masm.bind(loop);
        EVMOVDQU8.emit(masm, AVXSize.YMM, xmm0, new AMD64Address(condensed, 0));
        EVMOVDQU8.emit(masm, AVXSize.YMM, xmm1, new AMD64Address(condensed, 32));
        EVMOVDQU8.emit(masm, AVXSize.YMM, xmm2, new AMD64Address(condensed, 64));
        EVMOVDQU8.emit(masm, AVXSize.YMM, xmm8, new AMD64Address(condensed, 96));
        EVMOVDQU8.emit(masm, AVXSize.YMM, xmm9, new AMD64Address(condensed, 128));
        EVMOVDQU8.emit(masm, AVXSize.YMM, xmm10, new AMD64Address(condensed, 160));
        EVPMOVZXBW.emit(masm, AVXSize.ZMM, xmm0, xmm0);
        EVPMOVZXBW.emit(masm, AVXSize.ZMM, xmm1, xmm1);
        EVPMOVZXBW.emit(masm, AVXSize.ZMM, xmm2, xmm2);
        EVPMOVZXBW.emit(masm, AVXSize.ZMM, xmm8, xmm8);
        EVPMOVZXBW.emit(masm, AVXSize.ZMM, xmm9, xmm9);
        EVPMOVZXBW.emit(masm, AVXSize.ZMM, xmm10, xmm10);

        masm.evmovdqu16(xmm3, xmm24);
        masm.evmovdqu16(xmm4, xmm25);
        masm.evmovdqu16(xmm5, xmm26);
        masm.evmovdqu16(xmm11, xmm24);
        masm.evmovdqu16(xmm12, xmm25);
        masm.evmovdqu16(xmm13, xmm26);
        masm.evpermi2w(xmm3, xmm0, xmm1);
        masm.evpermi2w(xmm4, xmm0, xmm1);
        masm.evpermi2w(xmm5, xmm0, xmm1);
        masm.evpermi2w(xmm11, xmm8, xmm9);
        masm.evpermi2w(xmm12, xmm8, xmm9);
        masm.evpermi2w(xmm13, xmm8, xmm9);
        masm.evpermt2w(xmm3, xmm27, xmm2);
        masm.evpermt2w(xmm4, xmm28, xmm2);
        masm.evpermt2w(xmm5, xmm29, xmm2);
        masm.evpermt2w(xmm11, xmm27, xmm10);
        masm.evpermt2w(xmm12, xmm28, xmm10);
        masm.evpermt2w(xmm13, xmm29, xmm10);

        EVPSRAW.emit(masm, AVXSize.ZMM, xmm2, xmm4, 4);
        EVPSLLW.emit(masm, AVXSize.ZMM, xmm0, xmm4, 8);
        EVPSLLW.emit(masm, AVXSize.ZMM, xmm1, xmm5, 4);
        EVPSLLW.emit(masm, AVXSize.ZMM, xmm8, xmm12, 8);
        EVPSRAW.emit(masm, AVXSize.ZMM, xmm10, xmm12, 4);
        EVPSLLW.emit(masm, AVXSize.ZMM, xmm9, xmm13, 4);
        EVPANDQ.emit(masm, AVXSize.ZMM, xmm0, xmm0, xmm23);
        EVPANDQ.emit(masm, AVXSize.ZMM, xmm8, xmm8, xmm23);
        EVPADDW.emit(masm, AVXSize.ZMM, xmm1, xmm1, xmm2);
        EVPADDW.emit(masm, AVXSize.ZMM, xmm0, xmm0, xmm3);
        masm.evmovdqu16(xmm2, xmm30);
        EVPADDW.emit(masm, AVXSize.ZMM, xmm9, xmm9, xmm10);
        EVPADDW.emit(masm, AVXSize.ZMM, xmm8, xmm8, xmm11);
        masm.evmovdqu16(xmm10, xmm30);
        masm.evpermi2w(xmm2, xmm0, xmm1);
        masm.evpermt2w(xmm0, xmm31, xmm1);
        masm.evpermi2w(xmm10, xmm8, xmm9);
        masm.evpermt2w(xmm8, xmm31, xmm9);

        store4regs(masm, parsed, 0, XMM2_0_10_8);

        masm.addq(condensed, 192);
        masm.addq(parsed, 256);
        masm.subl(parsedLength, 128);
        masm.jcc(ConditionFlag.Greater, loop);
        masm.xorl(result, result);
    }
}
