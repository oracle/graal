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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.PrefetchMode;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.CharsetName;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/8e4485699235caff0074c4d25ee78539e57da63a/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L6422-L6539",
          sha1 = "7ec995886d9acb20550c9ed4a7fdbe9051043589")
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

        this.vectorTempValue = allocateVectorRegisters(tool, charset);
        this.charset = charset;
    }

    public static Value[] allocateVectorRegisters(LIRGeneratorTool tool, CharsetName charset) {
        switch (charset) {
            case ASCII -> {
                return allocateConsecutiveVectorRegisters(tool, 7);
            }
            case ISO_8859_1 -> {
                return allocateConsecutiveVectorRegisters(tool, 6);
            }
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(charset); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register src = asRegister(srcValue);
        Register dst = asRegister(dstValue);
        Register len = asRegister(lenValue);
        Register res = asRegister(resultValue);

        AArch64Move.move(AArch64Kind.QWORD, crb, masm, srcValue, originSrcValue);
        AArch64Move.move(AArch64Kind.QWORD, crb, masm, dstValue, originDstValue);

        emitEncodeArrayOp(masm, res, src, dst, len, vectorTempValue, charset);
    }

    public static void emitEncodeArrayOp(AArch64MacroAssembler masm, Register res, Register src, Register dst, Register len, Value[] vectorRegisters, CharsetName charset) {
        GraalError.guarantee(charset == CharsetName.ASCII || charset == CharsetName.ISO_8859_1, "unsupported charset: %s", charset);
        boolean ascii = charset == CharsetName.ASCII;

        Register vtmp0 = asRegister(vectorRegisters[0]);
        Register vtmp1 = asRegister(vectorRegisters[1]);
        Register vtmp2 = asRegister(vectorRegisters[2]);
        Register vtmp3 = asRegister(vectorRegisters[3]);
        Register vlo0 = asRegister(vectorRegisters[4]);
        Register vlo1 = asRegister(vectorRegisters[5]);
        Register vmask = ascii ? asRegister(vectorRegisters[6]) : null;

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
            // Narrow result to bytes for zero check
            masm.neon.xtnVV(ElementSize.HalfWord.narrow(), vtmp0, vtmp0);
        } else {
            // Extract merged upper bytes for ISO check (all zero).
            masm.neon.uzp2VVV(FullReg, ElementSize.Byte, vtmp0, vtmp0, vtmp0);
        }
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
            Register tmp = sc1.getRegister();
            masm.neon.umovGX(ElementSize.DoubleWord, tmp, vtmp0, 0);
            masm.cbnz(64, tmp, labelFail32);
        }

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
            // Narrow result to bytes for zero check
            masm.neon.xtnVV(ElementSize.HalfWord.narrow(), vtmp0, vtmp0);
        } else {
            // Extract upper bytes for ISO check (all zero).
            masm.neon.uzp2VVV(FullReg, ElementSize.Byte, vtmp0, vtmp0, vtmp0);
        }
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
            Register tmp = sc1.getRegister();
            masm.neon.umovGX(ElementSize.DoubleWord, tmp, vtmp0, 0);
            masm.cbnz(64, tmp, labelSkip8);
        }
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
