/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/be2b92bd8b43841cc2b9c22ed4fde29be30d47bb/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L1434-L1469",
          sha1 = "12a9844b6c686f0185bb738d9c0758a66b54ba7a")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/b3f34039fedd3c49404783ec880e1885dceb296b/src/hotspot/cpu/x86/macroAssembler_x86_sha.cpp#L32-L233",
          sha1 = "983fb75958945f5fb6b89327bd807f98b4e8c99c")
// @formatter:on
public final class AMD64SHA1Op extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64SHA1Op> TYPE = LIRInstructionClass.create(AMD64SHA1Op.class);

    @Alive({OperandFlag.REG}) private Value bufValue;
    @Alive({OperandFlag.REG}) private Value stateValue;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsValue;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value limitValue;

    @Def({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value resultValue;

    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value bufTempValue;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsTempValue;
    @Temp({OperandFlag.REG}) private Value[] temps;
    private final boolean multiBlock;

    public AMD64SHA1Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue) {
        this(tool, bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AMD64SHA1Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue,
                    AllocatableValue limitValue, AllocatableValue resultValue, boolean multiBlock) {
        super(TYPE);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;
        this.resultValue = resultValue;

        this.multiBlock = multiBlock;

        this.temps = new Value[]{
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
        };

        if (multiBlock) {
            this.bufTempValue = tool.newVariable(bufValue.getValueKind());
            this.ofsTempValue = tool.newVariable(ofsValue.getValueKind());
        } else {
            this.bufTempValue = Value.ILLEGAL;
            this.ofsTempValue = Value.ILLEGAL;
        }
    }

    static ArrayDataPointerConstant upperWordMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x00000000, 0x00000000, 0xFFFFFFFF
            // @formatter:on
    });

    static ArrayDataPointerConstant shuffleByteFlipMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x0c0d0e0f, 0x08090a0b, 0x04050607, 0x00010203
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(bufValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid bufValue kind: %s", bufValue);
        GraalError.guarantee(stateValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);

        Register buf;
        Register state = asRegister(stateValue);
        Register ofs;
        Register limit;

        if (multiBlock) {
            GraalError.guarantee(ofsValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid ofsValue kind: %s", ofsValue);
            GraalError.guarantee(limitValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid limitValue kind: %s", limitValue);
            GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

            buf = asRegister(bufTempValue);
            ofs = asRegister(ofsTempValue);
            limit = asRegister(limitValue);

            masm.movq(buf, asRegister(bufValue));
            masm.movl(ofs, asRegister(ofsValue));
        } else {
            buf = asRegister(bufValue);
            ofs = Register.None;
            limit = Register.None;
        }

        Register abcd = xmm0;
        Register e0 = xmm1;
        Register e1 = xmm2;
        Register msg0 = xmm3;
        Register msg1 = xmm4;
        Register msg2 = xmm5;
        Register msg3 = xmm6;
        Register shufMask = xmm7;

        Register e0Backup = xmm8;
        Register abcdBackup = xmm9;

        Label labelDoneHash = new Label();
        Label labelLoop0 = new Label();

        masm.movdqu(abcd, new AMD64Address(state, 0));
        masm.pinsrd(e0, new AMD64Address(state, 16), 3);
        masm.movdqu(shufMask, recordExternalAddress(crb, upperWordMask));
        masm.pand(e0, shufMask);
        masm.pshufd(abcd, abcd, 0x1B);
        masm.movdqu(shufMask, recordExternalAddress(crb, shuffleByteFlipMask));

        masm.bind(labelLoop0);
        // Save hash values for addition after rounds
        // Save e0, abcd in registers instead of stack
        masm.movdqu(e0Backup, e0);
        masm.movdqu(abcdBackup, abcd);

        // Rounds 0 - 3
        masm.movdqu(msg0, new AMD64Address(buf, 0));
        masm.pshufb(msg0, shufMask);
        masm.paddd(e0, msg0);
        masm.movdqa(e1, abcd);
        masm.sha1rnds4(abcd, e0, 0);

        // Rounds 4 - 7
        masm.movdqu(msg1, new AMD64Address(buf, 16));
        masm.pshufb(msg1, shufMask);
        masm.sha1nexte(e1, msg1);
        masm.movdqa(e0, abcd);
        masm.sha1rnds4(abcd, e1, 0);
        masm.sha1msg1(msg0, msg1);

        // Rounds 8 - 11
        masm.movdqu(msg2, new AMD64Address(buf, 32));
        masm.pshufb(msg2, shufMask);
        masm.sha1nexte(e0, msg2);
        masm.movdqa(e1, abcd);
        masm.sha1rnds4(abcd, e0, 0);
        masm.sha1msg1(msg1, msg2);
        masm.pxor(msg0, msg2);

        // Rounds 12 - 15
        masm.movdqu(msg3, new AMD64Address(buf, 48));
        masm.pshufb(msg3, shufMask);
        masm.sha1nexte(e1, msg3);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg0, msg3);
        masm.sha1rnds4(abcd, e1, 0);
        masm.sha1msg1(msg2, msg3);
        masm.pxor(msg1, msg3);

        // Rounds 16 - 19
        masm.sha1nexte(e0, msg0);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg1, msg0);
        masm.sha1rnds4(abcd, e0, 0);
        masm.sha1msg1(msg3, msg0);
        masm.pxor(msg2, msg0);

        // Rounds 20 - 23
        masm.sha1nexte(e1, msg1);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg2, msg1);
        masm.sha1rnds4(abcd, e1, 1);
        masm.sha1msg1(msg0, msg1);
        masm.pxor(msg3, msg1);

        // Rounds 24 - 27
        masm.sha1nexte(e0, msg2);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg3, msg2);
        masm.sha1rnds4(abcd, e0, 1);
        masm.sha1msg1(msg1, msg2);
        masm.pxor(msg0, msg2);

        // Rounds 28 - 31
        masm.sha1nexte(e1, msg3);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg0, msg3);
        masm.sha1rnds4(abcd, e1, 1);
        masm.sha1msg1(msg2, msg3);
        masm.pxor(msg1, msg3);

        // Rounds 32 - 35
        masm.sha1nexte(e0, msg0);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg1, msg0);
        masm.sha1rnds4(abcd, e0, 1);
        masm.sha1msg1(msg3, msg0);
        masm.pxor(msg2, msg0);

        // Rounds 36 - 39
        masm.sha1nexte(e1, msg1);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg2, msg1);
        masm.sha1rnds4(abcd, e1, 1);
        masm.sha1msg1(msg0, msg1);
        masm.pxor(msg3, msg1);

        // Rounds 40 - 43
        masm.sha1nexte(e0, msg2);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg3, msg2);
        masm.sha1rnds4(abcd, e0, 2);
        masm.sha1msg1(msg1, msg2);
        masm.pxor(msg0, msg2);

        // Rounds 44 - 47
        masm.sha1nexte(e1, msg3);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg0, msg3);
        masm.sha1rnds4(abcd, e1, 2);
        masm.sha1msg1(msg2, msg3);
        masm.pxor(msg1, msg3);

        // Rounds 48 - 51
        masm.sha1nexte(e0, msg0);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg1, msg0);
        masm.sha1rnds4(abcd, e0, 2);
        masm.sha1msg1(msg3, msg0);
        masm.pxor(msg2, msg0);

        // Rounds 52 - 55
        masm.sha1nexte(e1, msg1);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg2, msg1);
        masm.sha1rnds4(abcd, e1, 2);
        masm.sha1msg1(msg0, msg1);
        masm.pxor(msg3, msg1);

        // Rounds 56 - 59
        masm.sha1nexte(e0, msg2);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg3, msg2);
        masm.sha1rnds4(abcd, e0, 2);
        masm.sha1msg1(msg1, msg2);
        masm.pxor(msg0, msg2);

        // Rounds 60 - 63
        masm.sha1nexte(e1, msg3);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg0, msg3);
        masm.sha1rnds4(abcd, e1, 3);
        masm.sha1msg1(msg2, msg3);
        masm.pxor(msg1, msg3);

        // Rounds 64 - 67
        masm.sha1nexte(e0, msg0);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg1, msg0);
        masm.sha1rnds4(abcd, e0, 3);
        masm.sha1msg1(msg3, msg0);
        masm.pxor(msg2, msg0);

        // Rounds 68 - 71
        masm.sha1nexte(e1, msg1);
        masm.movdqa(e0, abcd);
        masm.sha1msg2(msg2, msg1);
        masm.sha1rnds4(abcd, e1, 3);
        masm.pxor(msg3, msg1);

        // Rounds 72 - 75
        masm.sha1nexte(e0, msg2);
        masm.movdqa(e1, abcd);
        masm.sha1msg2(msg3, msg2);
        masm.sha1rnds4(abcd, e0, 3);

        // Rounds 76 - 79
        masm.sha1nexte(e1, msg3);
        masm.movdqa(e0, abcd);
        masm.sha1rnds4(abcd, e1, 3);

        // add current hash values with previously saved
        masm.movdqu(msg0, e0Backup);
        masm.sha1nexte(e0, msg0);
        masm.movdqu(msg0, abcdBackup);
        masm.paddd(abcd, msg0);

        if (multiBlock) {
            // increment data pointer and loop if more to process
            masm.addq(buf, 64);
            masm.addl(ofs, 64);
            masm.cmplAndJcc(ofs, limit, ConditionFlag.BelowEqual, labelLoop0, false);

            GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
            masm.movl(asRegister(resultValue), ofs); // return ofs
        }
        // write hash values back in the correct order
        masm.pshufd(abcd, abcd, 0x1b);
        masm.movdqu(new AMD64Address(state, 0), abcd);
        masm.pextrd(new AMD64Address(state, 16), e0, 3);

        masm.bind(labelDoneHash);
    }
}
