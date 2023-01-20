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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.PrefetchMode;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.CharsetName;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp",
          lineStart = 5255,
          lineEnd   = 5366,
          commit    = "2afb4c3327b6830a009ee1ab8a1eb7803ef53007",
          sha1      = "1cedae5438e352f0572eb8ffbe1dd379feeb8ba0")
// @formatter:on
@Opcode("AArch64_ENCODE_ARRAY")
public final class AArch64EncodeArrayOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64EncodeArrayOp> TYPE = LIRInstructionClass.create(AArch64EncodeArrayOp.class);

    @Def({REG}) private Value resultValue;
    @Alive({REG}) private Value originSrcValue;
    @Alive({REG}) private Value originDstValue;
    @Alive({REG}) private Value lenValue;

    @Temp({REG}) private AllocatableValue srcValue;
    @Temp({REG}) private AllocatableValue dstValue;

    @Temp({REG}) private Value vectorTempValue0;
    @Temp({REG}) private Value vectorTempValue1;
    @Temp({REG}) private Value vectorTempValue2;
    @Temp({REG}) private Value vectorTempValue3;
    @Temp({REG}) private Value vectorTempValue4;
    @Temp({REG}) private Value vectorTempValue5;

    private final CharsetName charset;

    public AArch64EncodeArrayOp(LIRGeneratorTool tool, Value result, Value src, Value dst, Value length, CharsetName charset) {
        super(TYPE);

        this.resultValue = result;
        this.originSrcValue = src;
        this.originDstValue = dst;
        this.lenValue = length;

        this.srcValue = tool.newVariable(src.getValueKind());
        this.dstValue = tool.newVariable(dst.getValueKind());

        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        this.vectorTempValue0 = AArch64.v0.asValue(vectorKind);
        this.vectorTempValue1 = AArch64.v1.asValue(vectorKind);
        this.vectorTempValue2 = AArch64.v2.asValue(vectorKind);
        this.vectorTempValue3 = AArch64.v3.asValue(vectorKind);
        this.vectorTempValue4 = AArch64.v4.asValue(vectorKind);
        this.vectorTempValue5 = AArch64.v5.asValue(vectorKind);

        this.charset = charset;
        assert charset == CharsetName.ASCII || charset == CharsetName.ISO_8859_1;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        AArch64Move.move(AArch64Kind.QWORD, crb, masm, srcValue, originSrcValue);
        AArch64Move.move(AArch64Kind.QWORD, crb, masm, dstValue, originDstValue);

        boolean ascii = charset == CharsetName.ASCII;

        Register src = asRegister(srcValue);
        Register dst = asRegister(dstValue);
        Register len = asRegister(lenValue);
        Register res = asRegister(resultValue);

        Register vtmp0 = asRegister(vectorTempValue0);
        Register vtmp1 = asRegister(vectorTempValue1);
        Register vtmp2 = asRegister(vectorTempValue2);
        Register vtmp3 = asRegister(vectorTempValue3);

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register cnt = res;
            Register max = sc1.getRegister();
            Register chk = sc2.getRegister();

            masm.prfm(AArch64Address.createBaseRegisterOnlyAddress(64, src), PrefetchMode.PLDL1STRM);
            masm.mov(32, cnt, len);

            Label labelLoop32 = new Label();
            Label labelDone32 = new Label();
            Label labelFail32 = new Label();

            masm.bind(labelLoop32);
            masm.compare(32, cnt, 32);
            masm.branchConditionally(ConditionFlag.LT, labelDone32);
            masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.HalfWord, vtmp0, vtmp1, vtmp2, vtmp3,
                            AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.HalfWord, src, 64));
            // Extract lower bytes.
            Register vlo0 = asRegister(vectorTempValue4);
            Register vlo1 = asRegister(vectorTempValue5);
            masm.neon.uzp1VVV(ASIMDSize.FullReg, ElementSize.Byte, vlo0, vtmp0, vtmp1);
            masm.neon.uzp1VVV(ASIMDSize.FullReg, ElementSize.Byte, vlo1, vtmp2, vtmp3);
            // Merge bits...
            masm.neon.orrVVV(ASIMDSize.FullReg, vtmp0, vtmp0, vtmp1);
            masm.neon.orrVVV(ASIMDSize.FullReg, vtmp2, vtmp2, vtmp3);
            // Extract merged upper bytes.
            Register vhix = vtmp0;
            masm.neon.uzp2VVV(ASIMDSize.FullReg, ElementSize.Byte, vhix, vtmp0, vtmp2);
            // ISO-check on hi-parts (all zero).
            if (ascii) {
                // + ASCII-check on lo-parts (no sign).
                Register vlox = vtmp1; // Merge lower bytes.
                masm.neon.orrVVV(ASIMDSize.FullReg, vlox, vlo0, vlo1);
                masm.neon.umovGX(ElementSize.DoubleWord, chk, vhix, 1);
                masm.neon.cmltZeroVV(ASIMDSize.FullReg, ElementSize.Byte, vlox, vlox);
                masm.fmov(64, max, vhix);
                masm.neon.umaxvSV(ASIMDSize.FullReg, ElementSize.Byte, vlox, vlox);
                masm.orr(64, chk, chk, max);
                masm.neon.umovGX(ElementSize.Byte, max, vlo0, 0);
                masm.orr(64, chk, chk, max);
            } else {
                masm.neon.umovGX(ElementSize.DoubleWord, chk, vhix, 1);
                masm.fmov(64, max, vhix);
                masm.orr(64, chk, chk, max);
            }

            masm.cbnz(64, chk, labelFail32);
            masm.sub(32, cnt, cnt, 32);
            masm.neon.st1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, vlo0, vlo1,
                            AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.ST1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, dst, 32));
            masm.jmp(labelLoop32);

            masm.bind(labelFail32);
            masm.sub(64, src, src, 64);
            masm.bind(labelDone32);

            Label labelLoop8 = new Label();
            Label labelSkip8 = new Label();

            masm.bind(labelLoop8);
            masm.compare(32, cnt, 8);
            masm.branchConditionally(ConditionFlag.LT, labelSkip8);

            Register vhi = vtmp0;
            Register vlo = vtmp1;
            masm.neon.ld1MultipleV(ASIMDSize.FullReg, ElementSize.HalfWord, vtmp3, AArch64Address.createBaseRegisterOnlyAddress(64, src));
            masm.neon.uzp1VVV(ASIMDSize.FullReg, ElementSize.Byte, vlo, vtmp3, vtmp3);
            masm.neon.uzp2VVV(ASIMDSize.FullReg, ElementSize.Byte, vhi, vtmp3, vtmp3);
            // ISO-check on hi-parts (all zero).
            if (ascii) {
                // + ASCII-check on lo-parts (no sign).
                masm.neon.cmltZeroVV(ASIMDSize.FullReg, ElementSize.Byte, vtmp2, vlo);
                masm.fmov(64, chk, vhi);
                masm.neon.umaxvSV(ASIMDSize.FullReg, ElementSize.Byte, vtmp2, vtmp2);
                masm.neon.umovGX(ElementSize.Byte, max, vtmp2, 0);
                masm.orr(64, chk, chk, max);
            } else {
                masm.fmov(64, chk, vhi);
            }

            masm.cbnz(64, chk, labelSkip8);
            masm.fstr(64, vlo, AArch64Address.createImmediateAddress(64, AddressingMode.IMMEDIATE_POST_INDEXED, dst, 8));
            masm.sub(32, cnt, cnt, 8);
            masm.add(64, src, src, 16);
            masm.jmp(labelLoop8);

            masm.bind(labelSkip8);

            Label labelLoop = new Label();
            Label labelDone = new Label();

            masm.cbz(32, cnt, labelDone);
            masm.bind(labelLoop);
            Register chr = sc1.getRegister();
            masm.ldr(16, chr, AArch64Address.createImmediateAddress(16, AddressingMode.IMMEDIATE_POST_INDEXED, src, 2));
            masm.tst(32, chr, ascii ? 0xFF80 : 0xFF00);
            masm.branchConditionally(ConditionFlag.NE, labelDone);
            masm.str(8, chr, AArch64Address.createImmediateAddress(8, AddressingMode.IMMEDIATE_POST_INDEXED, dst, 1));
            masm.subs(32, cnt, cnt, 1);
            masm.branchConditionally(ConditionFlag.GT, labelLoop);

            masm.bind(labelDone);
            // Return index where we stopped.
            masm.sub(32, res, len, cnt);
        }
    }
}
