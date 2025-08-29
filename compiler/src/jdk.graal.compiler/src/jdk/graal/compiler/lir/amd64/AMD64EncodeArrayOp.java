/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPACKUSWB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPOR;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.CharsetName;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/8e4485699235caff0074c4d25ee78539e57da63a/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L6030-L6202",
          sha1 = "f0d710b1f37beded6afe58263684e90862dde1da")
// @formatter:on
@Opcode("AMD64_ENCODE_ARRAY")
public final class AMD64EncodeArrayOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64EncodeArrayOp> TYPE = LIRInstructionClass.create(AMD64EncodeArrayOp.class);

    @Def({OperandFlag.REG}) private Value resultValue;
    @Alive({OperandFlag.REG}) private Value originSrcValue;
    @Alive({OperandFlag.REG}) private Value originDstValue;
    @Alive({OperandFlag.REG, OperandFlag.CONST}) private Value originLengthValue;

    @Temp({OperandFlag.REG}) private Value srcValue;
    @Temp({OperandFlag.REG}) private Value dstValue;
    @Temp({OperandFlag.REG}) private Value lenValue;

    @Temp({OperandFlag.REG}) private Value vectorTempValue1;
    @Temp({OperandFlag.REG}) private Value vectorTempValue2;
    @Temp({OperandFlag.REG}) private Value vectorTempValue3;
    @Temp({OperandFlag.REG}) private Value vectorTempValue4;

    @Temp({OperandFlag.REG}) private Value tempValue5;

    private final LIRGeneratorTool.CharsetName charset;

    public AMD64EncodeArrayOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value result, Value src, Value dst, Value length, LIRGeneratorTool.CharsetName charset) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, YMM);

        this.resultValue = result;
        this.originSrcValue = src;
        this.originDstValue = dst;
        this.originLengthValue = length;

        this.srcValue = tool.newVariable(src.getValueKind());
        this.dstValue = tool.newVariable(dst.getValueKind());
        this.lenValue = tool.newVariable(length.getValueKind());

        LIRKind lirKind = LIRKind.value(getVectorKind(JavaKind.Byte));
        this.vectorTempValue1 = tool.newVariable(lirKind);
        this.vectorTempValue2 = tool.newVariable(lirKind);
        this.vectorTempValue3 = tool.newVariable(lirKind);
        this.vectorTempValue4 = tool.newVariable(lirKind);

        this.tempValue5 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));

        this.charset = charset;
        assert charset == CharsetName.ASCII || charset == CharsetName.ISO_8859_1 : charset;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelDone = new Label();
        Label labelCopy1Char = new Label();
        Label labelCopy1CharExit = new Label();

        AMD64Move.move(crb, masm, srcValue, originSrcValue);
        AMD64Move.move(crb, masm, dstValue, originDstValue);
        AMD64Move.move(crb, masm, lenValue, originLengthValue);

        Register src = asRegister(srcValue);
        Register dst = asRegister(dstValue);
        Register len = asRegister(lenValue);
        Register result = asRegister(resultValue);

        Register vectorTemp1 = asRegister(vectorTempValue1);
        Register vectorTemp2 = asRegister(vectorTempValue2);
        Register vectorTemp3 = asRegister(vectorTempValue3);
        Register vectorTemp4 = asRegister(vectorTempValue4);
        Register temp5 = asRegister(tempValue5);

        boolean ascii = charset == LIRGeneratorTool.CharsetName.ASCII;
        int mask = ascii ? 0xff80ff80 : 0xff00ff00;
        int shortMask = ascii ? 0xff80 : 0xff00;

        masm.xorl(result, result);
        masm.testlAndJcc(len, len, ConditionFlag.Zero, labelDone, false);

        masm.movl(result, len);

        masm.leaq(src, new AMD64Address(src, len, Stride.S2));
        masm.leaq(dst, new AMD64Address(dst, len, Stride.S1));
        masm.negq(len);

        if (supportsAVX2AndYMM() || masm.supports(CPUFeature.SSE4_2)) {
            Label labelCopy8Chars = new Label();
            Label labelCopy8CharsExit = new Label();
            Label labelChars16Check = new Label();
            Label labelCopy16Chars = new Label();
            Label labelCopy16CharsExit = new Label();

            if (supportsAVX2AndYMM()) {
                Label labelChars32Check = new Label();
                Label labelCopy32Chars = new Label();
                Label labelCopy32CharsExit = new Label();

                masm.movl(temp5, mask); // create mask to test for Unicode chars in vector
                masm.movdl(vectorTemp1, temp5);
                masm.emit(VPBROADCASTD, vectorTemp1, vectorTemp1, YMM);
                masm.jmp(labelChars32Check);

                masm.bind(labelCopy32Chars);
                masm.vmovdqu(vectorTemp3, new AMD64Address(src, len, Stride.S2, -64));
                masm.vmovdqu(vectorTemp4, new AMD64Address(src, len, Stride.S2, -32));
                masm.emit(VPOR, vectorTemp2, vectorTemp3, vectorTemp4, YMM);
                masm.vptest(vectorTemp2, vectorTemp1, AVXKind.AVXSize.YMM);
                masm.jcc(ConditionFlag.NotZero, labelCopy32CharsExit, true);
                masm.emit(VPACKUSWB, vectorTemp3, vectorTemp3, vectorTemp4, YMM);
                masm.emit(VPERMQ, vectorTemp4, vectorTemp3, 0xD8, YMM);
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S1, -32), vectorTemp4);

                masm.bind(labelChars32Check);
                masm.addqAndJcc(len, 32, ConditionFlag.LessEqual, labelCopy32Chars, false);

                masm.bind(labelCopy32CharsExit);
                masm.subqAndJcc(len, 16, ConditionFlag.Greater, labelCopy16CharsExit, true);
            } else {
                // generate SSE4_2 compatible code
                masm.movl(temp5, mask); // create mask to test for Unicode chars in vector
                masm.movdl(vectorTemp1, temp5);
                masm.pshufd(vectorTemp1, vectorTemp1, 0);
                masm.jmpb(labelChars16Check);
            }

            masm.bind(labelCopy16Chars);

            if (supportsAVX2AndYMM()) {
                masm.vmovdqu(vectorTemp2, new AMD64Address(src, len, Stride.S2, -32));
                masm.vptest(vectorTemp2, vectorTemp1, AVXKind.AVXSize.YMM);
                masm.jcc(ConditionFlag.NotZero, labelCopy16CharsExit);
                masm.emit(VPACKUSWB, vectorTemp2, vectorTemp2, vectorTemp1, YMM);
                masm.emit(VPERMQ, vectorTemp3, vectorTemp2, 0xD8, YMM);
            } else {
                if (masm.supports(CPUFeature.AVX)) {
                    masm.movdqu(vectorTemp3, new AMD64Address(src, len, Stride.S2, -32));
                    masm.movdqu(vectorTemp4, new AMD64Address(src, len, Stride.S2, -16));
                    masm.emit(VPOR, vectorTemp2, vectorTemp3, vectorTemp4, XMM);
                } else {
                    masm.movdqu(vectorTemp3, new AMD64Address(src, len, Stride.S2, -32));
                    masm.por(vectorTemp2, vectorTemp3);
                    masm.movdqu(vectorTemp4, new AMD64Address(src, len, Stride.S2, -16));
                    masm.por(vectorTemp2, vectorTemp4);
                }

                masm.ptest(vectorTemp2, vectorTemp1);
                masm.jccb(ConditionFlag.NotZero, labelCopy16CharsExit);
                masm.packuswb(vectorTemp3, vectorTemp4);
            }

            masm.movdqu(new AMD64Address(dst, len, Stride.S1, -16), vectorTemp3);

            masm.bind(labelChars16Check);
            masm.addqAndJcc(len, 16, ConditionFlag.LessEqual, labelCopy16Chars, false);

            masm.bind(labelCopy16CharsExit);
            masm.subqAndJcc(len, 8, ConditionFlag.Greater, labelCopy8CharsExit, true);

            masm.bind(labelCopy8Chars);
            masm.movdqu(vectorTemp3, new AMD64Address(src, len, Stride.S2, -16));
            masm.ptest(vectorTemp3, vectorTemp1);
            masm.jccb(ConditionFlag.NotZero, labelCopy8CharsExit);

            masm.packuswb(vectorTemp3, vectorTemp1);
            masm.movq(new AMD64Address(dst, len, Stride.S1, -8), vectorTemp3);

            masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, labelCopy8Chars, true);

            masm.bind(labelCopy8CharsExit);
            masm.subqAndJcc(len, 8, ConditionFlag.Zero, labelDone, true);
        }

        masm.bind(labelCopy1Char);
        masm.movzwl(temp5, new AMD64Address(src, len, Stride.S2, 0));
        masm.testlAndJcc(temp5, shortMask, ConditionFlag.NotZero, labelCopy1CharExit, true);

        masm.movb(new AMD64Address(dst, len, Stride.S1, 0), temp5);
        masm.addqAndJcc(len, 1, ConditionFlag.Less, labelCopy1Char, true);

        masm.bind(labelCopy1CharExit);
        masm.addq(result, len);

        masm.bind(labelDone);
    }
}
