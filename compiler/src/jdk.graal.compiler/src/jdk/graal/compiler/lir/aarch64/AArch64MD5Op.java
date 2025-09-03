/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r19;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r21;
import static jdk.vm.ci.aarch64.AArch64.r22;
import static jdk.vm.ci.aarch64.AArch64.r23;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/0ad919c1e54895b000b58f6a1b54d79f76970845/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L3520-L3766",
          sha1 = "1a840118aca65c256bea1bfae208c7dbf21fa516")
// @formatter:on
public final class AArch64MD5Op extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64MD5Op> TYPE = LIRInstructionClass.create(AArch64MD5Op.class);

    @Alive({REG}) private Value bufValue;
    @Alive({REG}) private Value stateValue;
    @Alive({REG, ILLEGAL}) private Value ofsValue;
    @Alive({REG, ILLEGAL}) private Value limitValue;

    @Def({REG, ILLEGAL}) private Value resultValue;

    @Temp({REG, ILLEGAL}) private Value bufTempValue;
    @Temp({REG, ILLEGAL}) private Value ofsTempValue;

    @Temp({REG}) private Value[] temps;

    private final boolean multiBlock;

    public AArch64MD5Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue) {
        this(tool, bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AArch64MD5Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue,
                    AllocatableValue limitValue, AllocatableValue resultValue, boolean multiBlock) {
        super(TYPE);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;
        this.resultValue = resultValue;

        this.multiBlock = multiBlock;

        if (multiBlock) {
            this.bufTempValue = tool.newVariable(bufValue.getValueKind());
            this.ofsTempValue = tool.newVariable(ofsValue.getValueKind());
        } else {
            this.bufTempValue = Value.ILLEGAL;
            this.ofsTempValue = Value.ILLEGAL;
        }

        this.temps = new Value[]{
                        r4.asValue(),
                        r5.asValue(),
                        r6.asValue(),
                        r7.asValue(),
                        // r8/r9 are scratch registers on HotSpot, r9/r10 on SubstrateVM
                        r11.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        r15.asValue(),
                        r16.asValue(),
                        r17.asValue(),
                        // r18 is the "platform register" on mac and windows
                        r19.asValue(),
                        r20.asValue(),
                        r21.asValue(),
                        r22.asValue(),
                        r23.asValue(),
        };
    }

    private static void regCacheExtractU32(AArch64MacroAssembler masm, Register[] regCache, Register dest, int i) {
        masm.ubfx(64, dest, regCache[i / 2], 32 * (i % 2), 32);
    }

    // Utility routines for md5.
    // Clobbers r23 and r11.
    private static void md5FF(AArch64MacroAssembler masm, Register[] regCache, Register reg1, Register reg2, Register reg3, Register reg4,
                    int k, int s, int t, Register rscratch1, Register rscratch2, Register rscratch3, Register rscratch4) {
        masm.eor(32, rscratch3, reg3, reg4);
        masm.mov(rscratch2, t);
        masm.and(32, rscratch3, rscratch3, reg2);
        masm.add(32, rscratch4, reg1, rscratch2);
        regCacheExtractU32(masm, regCache, rscratch1, k);
        masm.ubfx(64, rscratch1, regCache[k / 2], 32 * (k % 2), 32);
        masm.eor(32, rscratch3, rscratch3, reg4);
        masm.add(32, rscratch4, rscratch4, rscratch1);
        masm.add(32, rscratch3, rscratch3, rscratch4);
        masm.ror(32, rscratch2, rscratch3, 32 - s);
        masm.add(32, reg1, rscratch2, reg2);
    }

    private static void md5GG(AArch64MacroAssembler masm, Register[] regCache, Register reg1, Register reg2, Register reg3, Register reg4,
                    int k, int s, int t, Register rscratch1, Register rscratch2, Register rscratch3, Register rscratch4) {
        regCacheExtractU32(masm, regCache, rscratch1, k);
        masm.mov(rscratch2, t);
        masm.add(32, rscratch4, reg1, rscratch2);
        masm.add(32, rscratch4, rscratch4, rscratch1);
        masm.bic(32, rscratch2, reg3, reg4);
        masm.and(32, rscratch3, reg2, reg4);
        masm.add(32, rscratch2, rscratch2, rscratch4);
        masm.add(32, rscratch2, rscratch2, rscratch3);
        masm.ror(32, rscratch2, rscratch2, 32 - s);
        masm.add(32, reg1, rscratch2, reg2);
    }

    private static void md5HH(AArch64MacroAssembler masm, Register[] regCache, Register reg1, Register reg2, Register reg3, Register reg4,
                    int k, int s, int t, Register rscratch1, Register rscratch2, Register rscratch3, Register rscratch4) {
        masm.eor(32, rscratch3, reg3, reg4);
        masm.mov(rscratch2, t);
        masm.add(32, rscratch4, reg1, rscratch2);
        regCacheExtractU32(masm, regCache, rscratch1, k);
        masm.eor(32, rscratch3, rscratch3, reg2);
        masm.add(32, rscratch4, rscratch4, rscratch1);
        masm.add(32, rscratch3, rscratch3, rscratch4);
        masm.ror(32, rscratch2, rscratch3, 32 - s);
        masm.add(32, reg1, rscratch2, reg2);
    }

    private static void md5II(AArch64MacroAssembler masm, Register[] regCache, Register reg1, Register reg2, Register reg3, Register reg4,
                    int k, int s, int t, Register rscratch1, Register rscratch2, Register rscratch3, Register rscratch4) {
        masm.mov(rscratch3, t);
        masm.orn(32, rscratch2, reg2, reg4);
        masm.add(32, rscratch4, reg1, rscratch3);
        regCacheExtractU32(masm, regCache, rscratch1, k);
        masm.eor(32, rscratch3, rscratch2, reg3);
        masm.add(32, rscratch4, rscratch4, rscratch1);
        masm.add(32, rscratch3, rscratch3, rscratch4);
        masm.ror(32, rscratch2, rscratch3, 32 - s);
        masm.add(32, reg1, rscratch2, reg2);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(bufValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid bufValue kind: %s", bufValue);
        GraalError.guarantee(stateValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);

        Register buf;
        Register ofs;
        Register state = asRegister(stateValue);
        Register limit;

        if (multiBlock) {
            GraalError.guarantee(ofsValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid ofsValue kind: %s", ofsValue);
            GraalError.guarantee(limitValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid limitValue kind: %s", limitValue);

            buf = asRegister(bufTempValue);
            ofs = asRegister(ofsTempValue);
            limit = asRegister(limitValue);

            masm.mov(64, buf, asRegister(bufValue));
            masm.mov(32, ofs, asRegister(ofsValue));
        } else {
            buf = asRegister(bufValue);
            ofs = Register.None;
            limit = Register.None;
        }

        Register a = r4;
        Register b = r5;
        Register c = r6;
        Register d = r7;
        Register rscratch3 = r23;
        Register rscratch4 = r11;

        Register[] stateRegs = new Register[]{r12, r13};
        Register[] regCache = new Register[]{r14, r15, r16, r17, r19, r20, r21, r22};

        try (ScratchRegister scratchReg1 = masm.getScratchRegister();
                        ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg1.getRegister();
            Register rscratch2 = scratchReg2.getRegister();

            masm.ldp(64, stateRegs[0], stateRegs[1], AArch64Address.createPairBaseRegisterOnlyAddress(64, state));
            masm.ubfx(64, a, stateRegs[0], 0, 32);
            masm.ubfx(64, b, stateRegs[0], 32, 32);
            masm.ubfx(64, c, stateRegs[1], 0, 32);
            masm.ubfx(64, d, stateRegs[1], 32, 32);

            Label labelMD5Loop = new Label();
            masm.bind(labelMD5Loop);

            for (int i = 0; i < 8; i += 2) {
                masm.ldp(64, regCache[i], regCache[i + 1], AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, buf, 8 * i));
            }

            // Round 1
            md5FF(masm, regCache, a, b, c, d, 0, 7, 0xd76aa478, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, d, a, b, c, 1, 12, 0xe8c7b756, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, c, d, a, b, 2, 17, 0x242070db, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, b, c, d, a, 3, 22, 0xc1bdceee, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, a, b, c, d, 4, 7, 0xf57c0faf, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, d, a, b, c, 5, 12, 0x4787c62a, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, c, d, a, b, 6, 17, 0xa8304613, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, b, c, d, a, 7, 22, 0xfd469501, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, a, b, c, d, 8, 7, 0x698098d8, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, d, a, b, c, 9, 12, 0x8b44f7af, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, c, d, a, b, 10, 17, 0xffff5bb1, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, b, c, d, a, 11, 22, 0x895cd7be, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, a, b, c, d, 12, 7, 0x6b901122, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, d, a, b, c, 13, 12, 0xfd987193, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, c, d, a, b, 14, 17, 0xa679438e, rscratch1, rscratch2, rscratch3, rscratch4);
            md5FF(masm, regCache, b, c, d, a, 15, 22, 0x49b40821, rscratch1, rscratch2, rscratch3, rscratch4);

            // Round 2
            md5GG(masm, regCache, a, b, c, d, 1, 5, 0xf61e2562, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, d, a, b, c, 6, 9, 0xc040b340, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, c, d, a, b, 11, 14, 0x265e5a51, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, b, c, d, a, 0, 20, 0xe9b6c7aa, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, a, b, c, d, 5, 5, 0xd62f105d, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, d, a, b, c, 10, 9, 0x02441453, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, c, d, a, b, 15, 14, 0xd8a1e681, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, b, c, d, a, 4, 20, 0xe7d3fbc8, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, a, b, c, d, 9, 5, 0x21e1cde6, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, d, a, b, c, 14, 9, 0xc33707d6, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, c, d, a, b, 3, 14, 0xf4d50d87, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, b, c, d, a, 8, 20, 0x455a14ed, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, a, b, c, d, 13, 5, 0xa9e3e905, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, d, a, b, c, 2, 9, 0xfcefa3f8, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, c, d, a, b, 7, 14, 0x676f02d9, rscratch1, rscratch2, rscratch3, rscratch4);
            md5GG(masm, regCache, b, c, d, a, 12, 20, 0x8d2a4c8a, rscratch1, rscratch2, rscratch3, rscratch4);

            // Round 3
            md5HH(masm, regCache, a, b, c, d, 5, 4, 0xfffa3942, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, d, a, b, c, 8, 11, 0x8771f681, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, c, d, a, b, 11, 16, 0x6d9d6122, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, b, c, d, a, 14, 23, 0xfde5380c, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, a, b, c, d, 1, 4, 0xa4beea44, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, d, a, b, c, 4, 11, 0x4bdecfa9, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, c, d, a, b, 7, 16, 0xf6bb4b60, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, b, c, d, a, 10, 23, 0xbebfbc70, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, a, b, c, d, 13, 4, 0x289b7ec6, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, d, a, b, c, 0, 11, 0xeaa127fa, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, c, d, a, b, 3, 16, 0xd4ef3085, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, b, c, d, a, 6, 23, 0x04881d05, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, a, b, c, d, 9, 4, 0xd9d4d039, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, d, a, b, c, 12, 11, 0xe6db99e5, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, c, d, a, b, 15, 16, 0x1fa27cf8, rscratch1, rscratch2, rscratch3, rscratch4);
            md5HH(masm, regCache, b, c, d, a, 2, 23, 0xc4ac5665, rscratch1, rscratch2, rscratch3, rscratch4);

            // Round 4
            md5II(masm, regCache, a, b, c, d, 0, 6, 0xf4292244, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, d, a, b, c, 7, 10, 0x432aff97, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, c, d, a, b, 14, 15, 0xab9423a7, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, b, c, d, a, 5, 21, 0xfc93a039, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, a, b, c, d, 12, 6, 0x655b59c3, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, d, a, b, c, 3, 10, 0x8f0ccc92, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, c, d, a, b, 10, 15, 0xffeff47d, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, b, c, d, a, 1, 21, 0x85845dd1, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, a, b, c, d, 8, 6, 0x6fa87e4f, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, d, a, b, c, 15, 10, 0xfe2ce6e0, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, c, d, a, b, 6, 15, 0xa3014314, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, b, c, d, a, 13, 21, 0x4e0811a1, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, a, b, c, d, 4, 6, 0xf7537e82, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, d, a, b, c, 11, 10, 0xbd3af235, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, c, d, a, b, 2, 15, 0x2ad7d2bb, rscratch1, rscratch2, rscratch3, rscratch4);
            md5II(masm, regCache, b, c, d, a, 9, 21, 0xeb86d391, rscratch1, rscratch2, rscratch3, rscratch4);

            masm.add(32, a, stateRegs[0], a);
            masm.ubfx(64, rscratch2, stateRegs[0], 32, 32);
            masm.add(32, b, rscratch2, b);
            masm.add(32, c, stateRegs[1], c);
            masm.ubfx(64, rscratch4, stateRegs[1], 32, 32);
            masm.add(32, d, rscratch4, d);

            masm.orr(64, stateRegs[0], a, b, ShiftType.LSL, 32);
            masm.orr(64, stateRegs[1], c, d, ShiftType.LSL, 32);

            if (multiBlock) {
                masm.add(64, buf, buf, 64);
                masm.add(32, ofs, ofs, 64);
                masm.cmp(32, ofs, limit);
                masm.branchConditionally(ConditionFlag.LE, labelMD5Loop);
                masm.mov(32, asRegister(resultValue), ofs); // return ofs
            }

            // write hash values back in the correct order
            masm.stp(64, stateRegs[0], stateRegs[1], AArch64Address.createPairBaseRegisterOnlyAddress(64, state));
        }
    }
}
