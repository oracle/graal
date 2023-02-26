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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Round float to integer. Line by line assembly translation rounding algorithm. Please refer to
 * {@link Math#round} algorithm for details.
 */
// @formatter:off
@StubPort(path      = "src/hotspot/cpu/x86/macroAssembler_x86.cpp",
          lineStart = 9423,
          lineEnd   = 9519,
          commit    = "2afb4c3327b6830a009ee1ab8a1eb7803ef53007",
          sha1      = "7bb09de1deee91732af6a55f527c53eb33dec489")
@StubPort(path      = "src/hotspot/cpu/x86/stubGenerator_x86_64.cpp",
          lineStart = 607,
          lineEnd   = 774,
          commit    = "5d48da4574f6aacb0db445dd5750566330aa383d",
          sha1      = "9f29fa373a8469daa04ae89fde27febeb70e7f3b")
// @formatter:on
@Opcode("AMD64_ROUND_FLOAT_TO_INTEGER")
public class AMD64RoundFloatToIntegerOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64RoundFloatToIntegerOp> TYPE = LIRInstructionClass.create(AMD64RoundFloatToIntegerOp.class);

    @Def({REG, HINT}) protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue input;

    @Temp({REG}) protected AllocatableValue tmp;
    @Temp({REG}) protected AllocatableValue rcxTmp;

    public AMD64RoundFloatToIntegerOp(LIRGeneratorTool tool, AllocatableValue result, AllocatableValue input) {
        super(TYPE);

        this.result = result;
        this.input = input;

        this.tmp = tool.newVariable(result.getValueKind());
        this.rcxTmp = AMD64.rcx.asValue(); // used in sarq instruction
    }

    // Constants taken from jdk.internal.math.FloatConsts
    private static final int FLOAT_SIGNIFICAND_WIDTH = 24;
    private static final int FLOAT_EXP_BIAS = 127;
    private static final int FLOAT_EXP_BIT_MASK = 0x7F800000;
    private static final int FLOAT_SIGNIF_BIT_MASK = 0x007FFFFF;
    private static final int FLOAT_SIGN_BIT_MASK = 0x80000000;

    // Constants taken from jdk.internal.math.DoubleConsts
    private static final int DOUBLE_SIGNIFICAND_WIDTH = 53;
    private static final int DOUBLE_EXP_BIAS = 1023;
    private static final long DOUBLE_EXP_BIT_MASK = 0x7FF0000000000000L;
    private static final long DOUBLE_SIGNIF_BIT_MASK = 0x000FFFFFFFFFFFFFL;
    private static final long DOUBLE_SIGN_BIT_MASK = 0x8000000000000000L;

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelSpecialCase = new Label();
        Label labelBlock1 = new Label();
        Label labelExit = new Label();

        Register rtmp = asRegister(tmp);
        Register dst = asRegister(result);
        Register src = asRegister(input);

        if (input.getPlatformKind() == AMD64Kind.SINGLE) {
            masm.movl(rtmp, FLOAT_EXP_BIT_MASK);
            masm.movdl(dst, src);
            masm.andl(dst, rtmp);
            masm.sarl(dst, FLOAT_SIGNIFICAND_WIDTH - 1);
            masm.movl(AMD64.rcx, FLOAT_SIGNIFICAND_WIDTH - 2 + FLOAT_EXP_BIAS);
            masm.subl(AMD64.rcx, dst);
            masm.testlAndJcc(AMD64.rcx, -32, ConditionFlag.NotEqual, labelSpecialCase, true);
            masm.movdl(dst, src);
            masm.andl(dst, FLOAT_SIGNIF_BIT_MASK);
            masm.orl(dst, FLOAT_SIGNIF_BIT_MASK + 1);
            masm.movdl(rtmp, src);
            masm.testlAndJcc(rtmp, rtmp, ConditionFlag.GreaterEqual, labelBlock1, true);
            masm.negl(dst);
            masm.bind(labelBlock1);
            masm.sarl(dst);
            masm.addl(dst, 0x1);
            masm.sarl(dst, 0x1);
            masm.jmpb(labelExit);

            masm.bind(labelSpecialCase);
            masm.cvttss2sil(dst, src);
            masm.cmplAndJcc(dst, FLOAT_SIGN_BIT_MASK, ConditionFlag.NotEqual, labelExit, true);

            masm.movdl(rtmp, src);
            masm.andl(rtmp, 0x7FFFFFFF);

            // NaN -> 0
            masm.xorl(dst, dst);
            masm.cmplAndJcc(rtmp, FLOAT_EXP_BIT_MASK, ConditionFlag.Greater, labelExit, true);

            // signed ? Long.MIN_VALUE : Long.MAX_VALUE
            masm.movdl(rtmp, src);
            masm.testl(rtmp, rtmp);
            masm.movl(dst, Integer.MIN_VALUE);
            masm.movl(rtmp, Integer.MAX_VALUE);
            masm.cmovl(ConditionFlag.Positive, dst, rtmp);
        } else {
            assert input.getPlatformKind() == AMD64Kind.DOUBLE;
            masm.movq(rtmp, DOUBLE_EXP_BIT_MASK);
            masm.movdq(dst, src);
            masm.andq(dst, rtmp);
            masm.sarq(dst, DOUBLE_SIGNIFICAND_WIDTH - 1);
            masm.movl(AMD64.rcx, DOUBLE_SIGNIFICAND_WIDTH - 2 + DOUBLE_EXP_BIAS);
            masm.subq(AMD64.rcx, dst);
            masm.testAndJcc(OperandSize.QWORD, AMD64.rcx, -64, ConditionFlag.NotEqual, labelSpecialCase, true);
            masm.movdq(dst, src);
            masm.movq(rtmp, DOUBLE_SIGNIF_BIT_MASK);
            masm.andq(dst, rtmp);
            masm.movq(rtmp, DOUBLE_SIGNIF_BIT_MASK + 1);
            masm.orq(dst, rtmp);
            masm.movdq(rtmp, src);
            masm.testqAndJcc(rtmp, rtmp, ConditionFlag.GreaterEqual, labelBlock1, true);
            masm.negq(dst);
            masm.bind(labelBlock1);
            masm.sarq(dst);
            masm.incrementq(dst, 0x1);
            masm.sarq(dst, 0x1);
            masm.jmp(labelExit);

            masm.bind(labelSpecialCase);
            masm.cvttsd2siq(dst, src);
            masm.movq(rtmp, DOUBLE_SIGN_BIT_MASK);
            masm.cmpqAndJcc(dst, rtmp, ConditionFlag.NotEqual, labelExit, true);

            masm.movdq(rtmp, src);
            masm.movl(dst, rtmp);
            masm.negl(dst);
            masm.orl(dst, rtmp);
            masm.shrq(rtmp, 0x20);
            masm.andl(rtmp, 0x7FFFFFFF);
            masm.shrl(dst, 0x1f);
            masm.orl(rtmp, dst);

            // NaN -> 0
            masm.xorl(dst, dst);
            masm.cmplAndJcc(rtmp, (int) (DOUBLE_EXP_BIT_MASK >> 32), ConditionFlag.Greater, labelExit, true);

            // signed ? Long.MIN_VALUE : Long.MAX_VALUE
            masm.movdq(rtmp, src);
            masm.testq(rtmp, rtmp);
            masm.movq(dst, Long.MIN_VALUE);
            masm.movq(rtmp, Long.MAX_VALUE);
            masm.cmovq(ConditionFlag.Positive, dst, rtmp);
        }

        masm.bind(labelExit);
    }
}
