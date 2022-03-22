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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPACKUSWB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPOR;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.CharsetName;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/x86/macroAssembler_x86.cpp",
          lineStart = 5498,
          lineEnd   = 5656,
          commit    = "2920ce54874c404126d9fd6bfbebee5f3da27dae",
          sha1      = "28e9e817bee0afd9e5b698c5bff3ed519e09e410")
// @formatter:on
@Opcode("AMD64_ENCODE_ARRAY")
public final class AMD64EncodeArrayOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64EncodeArrayOp> TYPE = LIRInstructionClass.create(AMD64EncodeArrayOp.class);

    @Def({REG}) private Value resultValue;
    @Alive({REG}) private Value originSrcValue;
    @Alive({REG}) private Value originDstValue;
    @Alive({REG, CONST}) private Value originLengthValue;

    @Temp({REG}) private Value srcValue;
    @Temp({REG}) private Value dstValue;
    @Temp({REG}) private Value lenValue;

    @Temp({REG}) private Value vectorTempValue1;
    @Temp({REG}) private Value vectorTempValue2;
    @Temp({REG}) private Value vectorTempValue3;
    @Temp({REG}) private Value vectorTempValue4;

    @Temp({REG}) private Value tempValue5;

    private final CharsetName charset;

    public AMD64EncodeArrayOp(LIRGeneratorTool tool, Value result, Value src, Value dst, Value length, CharsetName charset) {
        super(TYPE);

        this.resultValue = result;
        this.originSrcValue = src;
        this.originDstValue = dst;
        this.originLengthValue = length;

        this.srcValue = tool.newVariable(src.getValueKind());
        this.dstValue = tool.newVariable(dst.getValueKind());
        this.lenValue = tool.newVariable(length.getValueKind());

        LIRKind lirKind = LIRKind.value(((AMD64) tool.target().arch).getFeatures().contains(AMD64.CPUFeature.AVX2) ? AMD64Kind.V256_BYTE : AMD64Kind.V128_BYTE);
        this.vectorTempValue1 = tool.newVariable(lirKind);
        this.vectorTempValue2 = tool.newVariable(lirKind);
        this.vectorTempValue3 = tool.newVariable(lirKind);
        this.vectorTempValue4 = tool.newVariable(lirKind);

        this.tempValue5 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));

        this.charset = charset;
        assert charset == CharsetName.ASCII || charset == CharsetName.ISO_8859_1;
    }

    private static boolean supportsSSE42(TargetDescription target) {
        return supports(target, CPUFeature.SSE4_2);
    }

    private static boolean supportsAVX(TargetDescription target) {
        return supports(target, CPUFeature.AVX);
    }

    private static boolean supportsAVX2(TargetDescription target) {
        return supports(target, CPUFeature.AVX2);
    }

    public static boolean supports(TargetDescription target, AMD64.CPUFeature feature) {
        AMD64 arch = (AMD64) target.arch;
        return arch.getFeatures().contains(feature);
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

        boolean ascii = charset == CharsetName.ASCII;
        int mask = ascii ? 0xff80ff80 : 0xff00ff00;
        int shortMask = ascii ? 0xff80 : 0xff00;

        masm.xorl(result, result);
        masm.testlAndJcc(len, len, ConditionFlag.Zero, labelDone, false);

        masm.movl(result, len);

        masm.leaq(src, new AMD64Address(src, len, Scale.Times2));
        masm.leaq(dst, new AMD64Address(dst, len, Scale.Times1));
        masm.negq(len);

        if (supportsAVX2(crb.target) || supportsSSE42(crb.target)) {
            Label labelCopy8Chars = new Label();
            Label labelCopy8CharsExit = new Label();
            Label labelChars16Check = new Label();
            Label labelCopy16Chars = new Label();
            Label labelCopy16CharsExit = new Label();

            if (supportsAVX2(crb.target)) {
                Label labelChars32Check = new Label();
                Label labelCopy32Chars = new Label();
                Label labelCopy32CharsExit = new Label();

                masm.movl(temp5, mask); // create mask to test for Unicode chars in vector
                masm.movdl(vectorTemp1, temp5);
                masm.emit(VPBROADCASTD, vectorTemp1, vectorTemp1, AVXSize.YMM);
                masm.jmp(labelChars32Check);

                masm.bind(labelCopy32Chars);
                masm.vmovdqu(vectorTemp3, new AMD64Address(src, len, Scale.Times2, -64));
                masm.vmovdqu(vectorTemp4, new AMD64Address(src, len, Scale.Times2, -32));
                masm.emit(VPOR, vectorTemp2, vectorTemp3, vectorTemp4, AVXSize.YMM);
                masm.vptest(vectorTemp2, vectorTemp1);
                masm.jcc(ConditionFlag.NotZero, labelCopy32CharsExit, true);
                masm.emit(VPACKUSWB, vectorTemp3, vectorTemp3, vectorTemp4, AVXSize.YMM);
                masm.emit(VPERMQ, vectorTemp4, vectorTemp3, 0xD8, AVXSize.YMM);
                masm.vmovdqu(new AMD64Address(dst, len, Scale.Times1, -32), vectorTemp4);

                masm.bind(labelChars32Check);
                masm.addqAndJcc(len, 32, ConditionFlag.LessEqual, labelCopy32Chars, false);

                masm.bind(labelCopy32CharsExit);
                masm.subqAndJcc(len, 16, ConditionFlag.Greater, labelCopy16CharsExit, true);
            } else if (supportsSSE42(crb.target)) {
                masm.movl(temp5, mask); // create mask to test for Unicode chars in vector
                masm.movdl(vectorTemp1, temp5);
                masm.pshufd(vectorTemp1, vectorTemp1, 0);
                masm.jmpb(labelChars16Check);
            }

            masm.bind(labelCopy16Chars);

            if (supportsAVX2(crb.target)) {
                masm.vmovdqu(vectorTemp2, new AMD64Address(src, len, Scale.Times2, -32));
                masm.vptest(vectorTemp2, vectorTemp1);
                masm.jcc(ConditionFlag.NotZero, labelCopy16CharsExit);
                masm.emit(VPACKUSWB, vectorTemp2, vectorTemp2, vectorTemp1, AVXSize.YMM);
                masm.emit(VPERMQ, vectorTemp3, vectorTemp2, 0xD8, AVXSize.YMM);
            } else {
                if (supportsAVX(crb.target)) {
                    masm.movdqu(vectorTemp3, new AMD64Address(src, len, Scale.Times2, -32));
                    masm.movdqu(vectorTemp4, new AMD64Address(src, len, Scale.Times2, -16));
                    masm.emit(VPOR, vectorTemp2, vectorTemp3, vectorTemp4, AVXSize.XMM);
                } else {
                    masm.movdqu(vectorTemp3, new AMD64Address(src, len, Scale.Times2, -32));
                    masm.por(vectorTemp2, vectorTemp3);
                    masm.movdqu(vectorTemp4, new AMD64Address(src, len, Scale.Times2, -16));
                    masm.por(vectorTemp2, vectorTemp4);
                }

                masm.ptest(vectorTemp2, vectorTemp1);
                masm.jccb(ConditionFlag.NotZero, labelCopy16CharsExit);
                masm.packuswb(vectorTemp3, vectorTemp4);
            }

            masm.movdqu(new AMD64Address(dst, len, Scale.Times1, -16), vectorTemp3);

            masm.bind(labelChars16Check);
            masm.addqAndJcc(len, 16, ConditionFlag.LessEqual, labelCopy16Chars, false);

            masm.bind(labelCopy16CharsExit);
            masm.subqAndJcc(len, 8, ConditionFlag.Greater, labelCopy8CharsExit, true);

            masm.bind(labelCopy8Chars);
            masm.movdqu(vectorTemp3, new AMD64Address(src, len, Scale.Times2, -16));
            masm.ptest(vectorTemp3, vectorTemp1);
            masm.jccb(ConditionFlag.NotZero, labelCopy8CharsExit);

            masm.packuswb(vectorTemp3, vectorTemp1);
            masm.movq(new AMD64Address(dst, len, Scale.Times1, -8), vectorTemp3);

            masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, labelCopy8Chars, true);

            masm.bind(labelCopy8CharsExit);
            masm.subqAndJcc(len, 8, ConditionFlag.Zero, labelDone, true);
        }

        masm.bind(labelCopy1Char);
        masm.movzwl(temp5, new AMD64Address(src, len, Scale.Times2, 0));
        masm.testlAndJcc(temp5, shortMask, ConditionFlag.NotZero, labelCopy1CharExit, true);

        masm.movb(new AMD64Address(dst, len, AMD64Address.Scale.Times1, 0), temp5);
        masm.addqAndJcc(len, 1, ConditionFlag.Less, labelCopy1Char, true);

        masm.bind(labelCopy1CharExit);
        masm.addq(result, len);

        masm.bind(labelDone);
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }
}
