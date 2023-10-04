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
package jdk.compiler.graal.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.compiler.graal.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.compiler.graal.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static jdk.compiler.graal.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.compiler.graal.lir.LIRInstruction.OperandFlag.REG;

import jdk.compiler.graal.asm.Label;
import jdk.compiler.graal.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import jdk.compiler.graal.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.compiler.graal.asm.aarch64.AArch64Address;
import jdk.compiler.graal.asm.aarch64.AArch64Address.AddressingMode;
import jdk.compiler.graal.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.compiler.graal.asm.aarch64.AArch64Assembler.PrefetchMode;
import jdk.compiler.graal.asm.aarch64.AArch64MacroAssembler;
import jdk.compiler.graal.lir.asm.CompilationResultBuilder;
import jdk.compiler.graal.lir.LIRInstructionClass;
import jdk.compiler.graal.lir.Opcode;
import jdk.compiler.graal.lir.SyncPort;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool.CharsetName;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/0a3a925ad88921d387aa851157f54ac0054d347b/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L5649-L5761",
          sha1 = "95adbb3a56ad94eca2698b6fd6d8359a15069de7")
// @formatter:on
@Opcode("AArch64_ENCODE_ARRAY")
public final class AArch64EncodeArrayOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64EncodeArrayOp> TYPE = LIRInstructionClass.create(AArch64EncodeArrayOp.class);

    @Def({REG}) private Value resultValue;
    @Alive({REG}) private Value originSrcValue;
    @Alive({REG}) private Value originDstValue;
    @Alive({REG}) private Value lenValue;

    @Temp({REG}) private AllocatableValue srcValue;
    @Temp({REG}) private AllocatableValue dstValue;

    @Temp({REG}) private Value[] vectorTempValue;

    private final CharsetName charset;

    public AArch64EncodeArrayOp(LIRGeneratorTool tool, Value result, Value src, Value dst, Value length, CharsetName charset) {
        super(TYPE);

        this.resultValue = result;
        this.originSrcValue = src;
        this.originDstValue = dst;
        this.lenValue = length;

        this.srcValue = tool.newVariable(src.getValueKind());
        this.dstValue = tool.newVariable(dst.getValueKind());

        this.vectorTempValue = allocateConsecutiveVectorRegisters(tool, charset == CharsetName.ASCII ? 7 : 6);

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

        Register vtmp0 = asRegister(vectorTempValue[0]);
        Register vtmp1 = asRegister(vectorTempValue[1]);
        Register vtmp2 = asRegister(vectorTempValue[2]);
        Register vtmp3 = asRegister(vectorTempValue[3]);
        Register vlo0 = asRegister(vectorTempValue[4]);
        Register vlo1 = asRegister(vectorTempValue[5]);
        Register vmask = ascii ? asRegister(vectorTempValue[6]) : null;

        Register cnt = res;

        masm.prfm(createBaseRegisterOnlyAddress(64, src), PrefetchMode.PLDL1STRM);
        masm.mov(32, cnt, len);

        if (ascii) {
            masm.neon.moveVI(FullReg, ElementSize.HalfWord, vmask, (short) 0xff80);
        }

        Label labelLoop32 = new Label();
        Label labelDone32 = new Label();
        Label labelFail32 = new Label();

        masm.bind(labelLoop32);
        masm.compare(32, cnt, 32);
        masm.branchConditionally(ConditionFlag.LT, labelDone32);
        masm.neon.ld1MultipleVVVV(FullReg, ElementSize.HalfWord, vtmp0, vtmp1, vtmp2, vtmp3,
                        AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R, FullReg, ElementSize.HalfWord, src, 64));
        // Extract lower bytes.
        masm.neon.uzp1VVV(FullReg, ElementSize.Byte, vlo0, vtmp0, vtmp1);
        masm.neon.uzp1VVV(FullReg, ElementSize.Byte, vlo1, vtmp2, vtmp3);
        // Merge bits...
        masm.neon.orrVVV(FullReg, vtmp0, vtmp0, vtmp1);
        masm.neon.orrVVV(FullReg, vtmp2, vtmp2, vtmp3);
        masm.neon.orrVVV(FullReg, vtmp0, vtmp0, vtmp2);
        if (ascii) {
            // Check if all merged chars are <= 0x7f
            masm.neon.cmtstVVV(FullReg, ElementSize.HalfWord, vtmp0, vtmp0, vmask);
            // Narrow result to bytes for fcmpZero
            masm.neon.xtnVV(ElementSize.HalfWord.narrow(), vtmp0, vtmp0);
        } else {
            // Extract merged upper bytes for ISO check (all zero).
            masm.neon.uzp2VVV(FullReg, ElementSize.Byte, vtmp0, vtmp0, vtmp0);
        }

        masm.fcmpZero(64, vtmp0);
        masm.branchConditionally(ConditionFlag.NE, labelFail32);
        masm.sub(32, cnt, cnt, 32);
        masm.fstp(128, vlo0, vlo1, createImmediateAddress(FullReg.bits(), AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, dst, 32));
        masm.jmp(labelLoop32);

        masm.bind(labelFail32);
        masm.sub(64, src, src, 64);
        masm.bind(labelDone32);

        Label labelLoop8 = new Label();
        Label labelSkip8 = new Label();

        masm.bind(labelLoop8);
        masm.compare(32, cnt, 8);
        masm.branchConditionally(ConditionFlag.LT, labelSkip8);

        masm.fldr(128, vtmp0, createBaseRegisterOnlyAddress(128, src));
        // Extract lower bytes.
        masm.neon.uzp1VVV(FullReg, ElementSize.Byte, vlo0, vtmp0, vtmp0);
        if (ascii) {
            // Check if all merged chars are <= 0x7f
            masm.neon.cmtstVVV(FullReg, ElementSize.HalfWord, vtmp0, vtmp0, vmask);
            // Narrow result to bytes for fcmpZero
            masm.neon.xtnVV(ElementSize.HalfWord.narrow(), vtmp0, vtmp0);
        } else {
            // Extract upper bytes for ISO check (all zero).
            masm.neon.uzp2VVV(FullReg, ElementSize.Byte, vtmp0, vtmp0, vtmp0);
        }

        masm.fcmpZero(64, vtmp0);
        masm.branchConditionally(ConditionFlag.NE, labelSkip8);
        masm.fstr(64, vlo0, createImmediateAddress(64, AddressingMode.IMMEDIATE_POST_INDEXED, dst, 8));
        masm.sub(32, cnt, cnt, 8);
        masm.add(64, src, src, 16);
        masm.jmp(labelLoop8);

        masm.bind(labelSkip8);

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {

            Label labelLoop = new Label();
            Label labelDone = new Label();

            masm.cbz(32, cnt, labelDone);
            masm.bind(labelLoop);
            Register chr = sc1.getRegister();
            masm.ldr(16, chr, createImmediateAddress(16, AddressingMode.IMMEDIATE_POST_INDEXED, src, 2));
            masm.tst(32, chr, ascii ? 0xFF80 : 0xFF00);
            masm.branchConditionally(ConditionFlag.NE, labelDone);
            masm.str(8, chr, createImmediateAddress(8, AddressingMode.IMMEDIATE_POST_INDEXED, dst, 1));
            masm.subs(32, cnt, cnt, 1);
            masm.branchConditionally(ConditionFlag.GT, labelLoop);

            masm.bind(labelDone);
            // Return index where we stopped.
            masm.sub(32, res, len, cnt);
        }
    }
}
