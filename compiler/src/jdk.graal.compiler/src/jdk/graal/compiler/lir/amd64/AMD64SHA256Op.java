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
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.BelowEqual;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
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
@SyncPort(from = "https://github.com/openjdk/jdk/blob/be2b92bd8b43841cc2b9c22ed4fde29be30d47bb/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L1517-L1559",
          sha1 = "593a45db708f1b6a74086cf170612c1102fe56c2")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/8c9d091f19760deece8daf3e57add85482b9f2a7/src/hotspot/cpu/x86/macroAssembler_x86_sha.cpp#L235-L493",
          sha1 = "722bdd7519a7d7b9d9cec900af38137f1849ac4e")
// @formatter:on
public final class AMD64SHA256Op extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64SHA256Op> TYPE = LIRInstructionClass.create(AMD64SHA256Op.class);

    @Alive({OperandFlag.REG}) private Value bufValue;
    @Alive({OperandFlag.REG}) private Value stateValue;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsValue;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value limitValue;

    @Def({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value resultValue;

    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value bufTempValue;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsTempValue;
    @Temp({OperandFlag.REG}) private Value keyTempValue;
    @Temp({OperandFlag.REG}) private Value[] temps;

    private final boolean multiBlock;

    public AMD64SHA256Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue) {
        this(tool, bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AMD64SHA256Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue,
                    AllocatableValue limitValue, AllocatableValue resultValue, boolean multiBlock) {
        super(TYPE);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;
        this.resultValue = resultValue;

        this.multiBlock = multiBlock;

        this.keyTempValue = tool.newVariable(bufValue.getValueKind());

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
                        xmm10.asValue(),
        };

        if (multiBlock) {
            this.bufTempValue = tool.newVariable(bufValue.getValueKind());
            this.ofsTempValue = tool.newVariable(ofsValue.getValueKind());
        } else {
            this.bufTempValue = Value.ILLEGAL;
            this.ofsTempValue = Value.ILLEGAL;
        }
    }

    static ArrayDataPointerConstant k256 = pointerConstant(16, new int[]{
            // @formatter:off
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
            0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
            0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
            0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
            // @formatter:on
    });

    static ArrayDataPointerConstant pshuffleByteFlipMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f,
            0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f,
            // _SHUF_00BA
            0x03020100, 0x0b0a0908, 0xFFFFFFFF, 0xFFFFFFFF,
            0x03020100, 0x0b0a0908, 0xFFFFFFFF, 0xFFFFFFFF,
            // _SHUF_DC00
            0xFFFFFFFF, 0xFFFFFFFF, 0x03020100, 0x0b0a0908,
            0xFFFFFFFF, 0xFFFFFFFF, 0x03020100, 0x0b0a0908,
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

        Register msg = xmm0;
        Register state0 = xmm1;
        Register state1 = xmm2;
        Register msgtmp0 = xmm3;

        Register msgtmp1 = xmm4;
        Register msgtmp2 = xmm5;
        Register msgtmp3 = xmm6;
        Register msgtmp4 = xmm7;
        Register shufMask = xmm8;

        Register state0Backup = xmm9;
        Register state1Backup = xmm10;

        Label labelLoop0 = new Label();

        // keyTemp replaces the hardcoded rax in the original stub.
        Register keyTemp = asRegister(keyTempValue);

        masm.movdqu(state0, new AMD64Address(state, 0));
        masm.movdqu(state1, new AMD64Address(state, 16));

        masm.pshufd(state0, state0, 0xB1);
        masm.pshufd(state1, state1, 0x1B);
        masm.movdqa(msgtmp4, state0);
        masm.palignr(state0, state1, 8);
        masm.pblendw(state1, msgtmp4, 0xF0);

        masm.movdqu(shufMask, recordExternalAddress(crb, pshuffleByteFlipMask));
        masm.leaq(keyTemp, recordExternalAddress(crb, k256));

        masm.bind(labelLoop0);
        masm.movdqu(state0Backup, state0);
        masm.movdqu(state1Backup, state1);

        // Rounds 0-3
        masm.movdqu(msg, new AMD64Address(buf, 0));
        masm.pshufb(msg, shufMask);
        masm.movdqa(msgtmp0, msg);
        masm.paddd(msg, new AMD64Address(keyTemp, 0));
        masm.sha256rnds2(state1, state0);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);

        // Rounds 4-7
        masm.movdqu(msg, new AMD64Address(buf, 16));
        masm.pshufb(msg, shufMask);
        masm.movdqa(msgtmp1, msg);
        masm.paddd(msg, new AMD64Address(keyTemp, 16));
        masm.sha256rnds2(state1, state0);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp0, msgtmp1);

        // Rounds 8-11
        masm.movdqu(msg, new AMD64Address(buf, 32));
        masm.pshufb(msg, shufMask);
        masm.movdqa(msgtmp2, msg);
        masm.paddd(msg, new AMD64Address(keyTemp, 32));
        masm.sha256rnds2(state1, state0);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp1, msgtmp2);

        // Rounds 12-15
        masm.movdqu(msg, new AMD64Address(buf, 48));
        masm.pshufb(msg, shufMask);
        masm.movdqa(msgtmp3, msg);
        masm.paddd(msg, new AMD64Address(keyTemp, 48));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp3);
        masm.palignr(msgtmp4, msgtmp2, 4);
        masm.paddd(msgtmp0, msgtmp4);
        masm.sha256msg2(msgtmp0, msgtmp3);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp2, msgtmp3);

        // Rounds 16-19
        masm.movdqa(msg, msgtmp0);
        masm.paddd(msg, new AMD64Address(keyTemp, 64));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp0);
        masm.palignr(msgtmp4, msgtmp3, 4);
        masm.paddd(msgtmp1, msgtmp4);
        masm.sha256msg2(msgtmp1, msgtmp0);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp3, msgtmp0);

        // Rounds 20-23
        masm.movdqa(msg, msgtmp1);
        masm.paddd(msg, new AMD64Address(keyTemp, 80));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp1);
        masm.palignr(msgtmp4, msgtmp0, 4);
        masm.paddd(msgtmp2, msgtmp4);
        masm.sha256msg2(msgtmp2, msgtmp1);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp0, msgtmp1);

        // Rounds 24-27
        masm.movdqa(msg, msgtmp2);
        masm.paddd(msg, new AMD64Address(keyTemp, 96));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp2);
        masm.palignr(msgtmp4, msgtmp1, 4);
        masm.paddd(msgtmp3, msgtmp4);
        masm.sha256msg2(msgtmp3, msgtmp2);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp1, msgtmp2);

        // Rounds 28-31
        masm.movdqa(msg, msgtmp3);
        masm.paddd(msg, new AMD64Address(keyTemp, 112));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp3);
        masm.palignr(msgtmp4, msgtmp2, 4);
        masm.paddd(msgtmp0, msgtmp4);
        masm.sha256msg2(msgtmp0, msgtmp3);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp2, msgtmp3);

        // Rounds 32-35
        masm.movdqa(msg, msgtmp0);
        masm.paddd(msg, new AMD64Address(keyTemp, 128));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp0);
        masm.palignr(msgtmp4, msgtmp3, 4);
        masm.paddd(msgtmp1, msgtmp4);
        masm.sha256msg2(msgtmp1, msgtmp0);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp3, msgtmp0);

        // Rounds 36-39
        masm.movdqa(msg, msgtmp1);
        masm.paddd(msg, new AMD64Address(keyTemp, 144));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp1);
        masm.palignr(msgtmp4, msgtmp0, 4);
        masm.paddd(msgtmp2, msgtmp4);
        masm.sha256msg2(msgtmp2, msgtmp1);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp0, msgtmp1);

        // Rounds 40-43
        masm.movdqa(msg, msgtmp2);
        masm.paddd(msg, new AMD64Address(keyTemp, 160));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp2);
        masm.palignr(msgtmp4, msgtmp1, 4);
        masm.paddd(msgtmp3, msgtmp4);
        masm.sha256msg2(msgtmp3, msgtmp2);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp1, msgtmp2);

        // Rounds 44-47
        masm.movdqa(msg, msgtmp3);
        masm.paddd(msg, new AMD64Address(keyTemp, 176));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp3);
        masm.palignr(msgtmp4, msgtmp2, 4);
        masm.paddd(msgtmp0, msgtmp4);
        masm.sha256msg2(msgtmp0, msgtmp3);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp2, msgtmp3);

        // Rounds 48-51
        masm.movdqa(msg, msgtmp0);
        masm.paddd(msg, new AMD64Address(keyTemp, 192));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp0);
        masm.palignr(msgtmp4, msgtmp3, 4);
        masm.paddd(msgtmp1, msgtmp4);
        masm.sha256msg2(msgtmp1, msgtmp0);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.sha256msg1(msgtmp3, msgtmp0);

        // Rounds 52-55
        masm.movdqa(msg, msgtmp1);
        masm.paddd(msg, new AMD64Address(keyTemp, 208));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp1);
        masm.palignr(msgtmp4, msgtmp0, 4);
        masm.paddd(msgtmp2, msgtmp4);
        masm.sha256msg2(msgtmp2, msgtmp1);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);

        // Rounds 56-59
        masm.movdqa(msg, msgtmp2);
        masm.paddd(msg, new AMD64Address(keyTemp, 224));
        masm.sha256rnds2(state1, state0);
        masm.movdqa(msgtmp4, msgtmp2);
        masm.palignr(msgtmp4, msgtmp1, 4);
        masm.paddd(msgtmp3, msgtmp4);
        masm.sha256msg2(msgtmp3, msgtmp2);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);

        // Rounds 60-63
        masm.movdqa(msg, msgtmp3);
        masm.paddd(msg, new AMD64Address(keyTemp, 240));
        masm.sha256rnds2(state1, state0);
        masm.pshufd(msg, msg, 0x0E);
        masm.sha256rnds2(state0, state1);
        masm.movdqu(msg, state0Backup);
        masm.paddd(state0, msg);
        masm.movdqu(msg, state1Backup);
        masm.paddd(state1, msg);

        if (multiBlock) {
            // increment data pointer and loop if more to process
            masm.addq(buf, 64);
            masm.addl(ofs, 64);
            masm.cmplAndJcc(ofs, limit, BelowEqual, labelLoop0, false);

            GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
            masm.movl(asRegister(resultValue), ofs); // return ofs
        }

        masm.pshufd(state0, state0, 0x1B);
        masm.pshufd(state1, state1, 0xB1);
        masm.movdqa(msgtmp4, state0);
        masm.pblendw(state0, state1, 0xF0);
        masm.palignr(state1, msgtmp4, 8);

        masm.movdqu(new AMD64Address(state, 0), state0);
        masm.movdqu(new AMD64Address(state, 16), state1);
    }

}
