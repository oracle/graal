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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_1R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Byte;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureImmediatePostIndexAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ExtendType.UXTH;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L7538-L7748",
          sha1 = "0fde8fdf7e0cfeff3c204649a8b79ab6730e5cf4")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/5cc14e537ce7c6df41d44230ae5512703af1c0a0/src/hotspot/cpu/aarch64/stubRoutines_aarch64.cpp#L325-L327",
          sha1 = "b36d82322737d04cebe0d8efc4f4765454f48aa9")
// @formatter:on
@Opcode("AARCH64_ADLER32_UPDATE_BYTES")
public final class AArch64Adler32UpdateBytesOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64Adler32UpdateBytesOp> TYPE = LIRInstructionClass.create(AArch64Adler32UpdateBytesOp.class);

    private static final long BASE = 0xfff1L;
    private static final long NMAX = 0x15B0L;

    /*
     * HotSpot aligns _adler_table to 64 bytes. The Graal data section only needs the alignment of
     * the single 16-byte ld1 below.
     */
    private static final ArrayDataPointerConstant ADLER_TABLE = pointerConstant(16, new byte[]{
                    16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1
    });

    @Def private AllocatableValue resultValue;
    @Use private AllocatableValue adlerValue;
    @Use private AllocatableValue bufValue;
    @Use private AllocatableValue lenValue;

    @Temp private Value[] temps;

    public AArch64Adler32UpdateBytesOp(AllocatableValue resultValue, AllocatableValue adlerValue, AllocatableValue bufValue, AllocatableValue lenValue) {
        super(TYPE);
        this.resultValue = resultValue;
        this.adlerValue = adlerValue;
        this.bufValue = bufValue;
        this.lenValue = lenValue;
        guaranteeFixedRegister(resultValue, r0, "result");
        guaranteeFixedRegister(adlerValue, r0, "adler");
        guaranteeFixedRegister(bufValue, r1, "bufferAddress");
        guaranteeFixedRegister(lenValue, r2, "length");
        this.temps = new Value[]{
                        r1.asValue(),
                        r2.asValue(),
                        r3.asValue(),
                        r4.asValue(),
                        r5.asValue(),
                        r6.asValue(),
                        v0.asValue(), v1.asValue(), v2.asValue(), v3.asValue()
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register adler = asRegister(adlerValue);
        Register s1 = adler;
        Register s2 = r3;
        Register buff = asRegister(bufValue);
        Register len = asRegister(lenValue);
        Register nmax = r4;
        Register base = r5;
        Register count = r6;
        Register vbytes = v0;
        Register vs1acc = v1;
        Register vs2acc = v2;
        Register vtable = v3;

        Label labelSimpleBy1Loop = new Label();
        Label labelNmax = new Label();
        Label labelNmaxLoop = new Label();
        Label labelBy16 = new Label();
        Label labelBy16Loop = new Label();
        Label labelBy1Loop = new Label();
        Label labelDoMod = new Label();
        Label labelCombine = new Label();
        Label labelBy1 = new Label();

        try (AArch64MacroAssembler.ScratchRegister scratch1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister scratch2 = masm.getScratchRegister()) {
            Register temp0 = scratch1.getRegister();
            Register temp1 = scratch2.getRegister();

            // Max number of bytes we can process before having to take the mod
            // 0x15B0 is 5552 in decimal, the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
            masm.mov(base, BASE);
            masm.mov(nmax, NMAX);

            // Load accumulation coefficients for the upper 16 bits
            loadExternalAddress(crb, masm, temp0, ADLER_TABLE);
            masm.neon.ld1MultipleV(FullReg, Byte, vtable, createBaseRegisterOnlyAddress(128, temp0));

            // s1 is initialized to the lower 16 bits of adler
            // s2 is initialized to the upper 16 bits of adler
            masm.ubfx(64, s2, adler, 16, 16); // s2 = ((adler >> 16) & 0xffff)
            masm.ubfx(64, s1, adler, 0, 16);  // s1 = (adler & 0xffff)

            // The pipelined loop needs at least 16 elements for 1 iteration
            // It does check this, but it is more effective to skip to the cleanup loop
            masm.compare(64, len, 16);
            masm.branchConditionally(ConditionFlag.HS, labelNmax);
            masm.cbz(64, len, labelCombine);

            masm.bind(labelSimpleBy1Loop);
            masm.ldr(8, temp0, createImmediateAddress(8, IMMEDIATE_POST_INDEXED, buff, 1));
            masm.add(64, s1, s1, temp0);
            masm.add(64, s2, s2, s1);
            masm.subs(64, len, len, 1);
            masm.branchConditionally(ConditionFlag.HI, labelSimpleBy1Loop);

            // s1 = s1 % BASE
            masm.subs(64, temp0, s1, base);
            masm.csel(64, s1, temp0, s1, ConditionFlag.HS);

            // s2 = s2 % BASE
            masm.lsr(64, temp0, s2, 16);
            masm.lsl(64, temp1, temp0, 4);
            masm.sub(64, temp1, temp1, temp0);
            masm.add(64, s2, temp1, s2, UXTH, 0);

            masm.subs(64, temp0, s2, base);
            masm.csel(64, s2, temp0, s2, ConditionFlag.HS);

            masm.jmp(labelCombine);

            masm.bind(labelNmax);
            masm.subs(64, len, len, nmax);
            masm.sub(64, count, nmax, 16);
            masm.branchConditionally(ConditionFlag.LO, labelBy16);

            masm.bind(labelNmaxLoop);

            emitUpdateBytesAdler32Accum(masm, s1, s2, buff, temp0, temp1, vbytes, vs1acc, vs2acc, vtable);

            masm.subs(64, count, count, 16);
            masm.branchConditionally(ConditionFlag.HS, labelNmaxLoop);

            // s1 = s1 % BASE
            masm.lsr(64, temp0, s1, 16);
            masm.lsl(64, temp1, temp0, 4);
            masm.sub(64, temp1, temp1, temp0);
            masm.add(64, temp1, temp1, s1, UXTH, 0);

            masm.lsr(64, temp0, temp1, 16);
            masm.lsl(64, s1, temp0, 4);
            masm.sub(64, s1, s1, temp0);
            masm.add(64, s1, s1, temp1, UXTH, 0);

            masm.subs(64, temp0, s1, base);
            masm.csel(64, s1, temp0, s1, ConditionFlag.HS);

            // s2 = s2 % BASE
            masm.lsr(64, temp0, s2, 16);
            masm.lsl(64, temp1, temp0, 4);
            masm.sub(64, temp1, temp1, temp0);
            masm.add(64, temp1, temp1, s2, UXTH, 0);

            masm.lsr(64, temp0, temp1, 16);
            masm.lsl(64, s2, temp0, 4);
            masm.sub(64, s2, s2, temp0);
            masm.add(64, s2, s2, temp1, UXTH, 0);

            masm.subs(64, temp0, s2, base);
            masm.csel(64, s2, temp0, s2, ConditionFlag.HS);

            masm.subs(64, len, len, nmax);
            masm.sub(64, count, nmax, 16);
            masm.branchConditionally(ConditionFlag.HS, labelNmaxLoop);

            masm.bind(labelBy16);
            masm.adds(64, len, len, count);
            masm.branchConditionally(ConditionFlag.LO, labelBy1);

            masm.bind(labelBy16Loop);

            emitUpdateBytesAdler32Accum(masm, s1, s2, buff, temp0, temp1, vbytes, vs1acc, vs2acc, vtable);

            masm.subs(64, len, len, 16);
            masm.branchConditionally(ConditionFlag.HS, labelBy16Loop);

            masm.bind(labelBy1);
            masm.adds(64, len, len, 15);
            masm.branchConditionally(ConditionFlag.LO, labelDoMod);

            masm.bind(labelBy1Loop);
            masm.ldr(8, temp0, createImmediateAddress(8, IMMEDIATE_POST_INDEXED, buff, 1));
            masm.add(64, s1, temp0, s1);
            masm.add(64, s2, s2, s1);
            masm.subs(64, len, len, 1);
            masm.branchConditionally(ConditionFlag.HS, labelBy1Loop);

            masm.bind(labelDoMod);
            // s1 = s1 % BASE
            masm.lsr(64, temp0, s1, 16);
            masm.lsl(64, temp1, temp0, 4);
            masm.sub(64, temp1, temp1, temp0);
            masm.add(64, temp1, temp1, s1, UXTH, 0);

            masm.lsr(64, temp0, temp1, 16);
            masm.lsl(64, s1, temp0, 4);
            masm.sub(64, s1, s1, temp0);
            masm.add(64, s1, s1, temp1, UXTH, 0);

            masm.subs(64, temp0, s1, base);
            masm.csel(64, s1, temp0, s1, ConditionFlag.HS);

            // s2 = s2 % BASE
            masm.lsr(64, temp0, s2, 16);
            masm.lsl(64, temp1, temp0, 4);
            masm.sub(64, temp1, temp1, temp0);
            masm.add(64, temp1, temp1, s2, UXTH, 0);

            masm.lsr(64, temp0, temp1, 16);
            masm.lsl(64, s2, temp0, 4);
            masm.sub(64, s2, s2, temp0);
            masm.add(64, s2, s2, temp1, UXTH, 0);

            masm.subs(64, temp0, s2, base);
            masm.csel(64, s2, temp0, s2, ConditionFlag.HS);

            // Combine lower bits and higher bits
            masm.bind(labelCombine);
            masm.orr(64, s1, s1, s2, LSL, 16); // adler = s1 | (s2 << 16)
        }
    }

    private static void emitUpdateBytesAdler32Accum(AArch64MacroAssembler masm, Register s1, Register s2, Register buff,
                    Register temp0, Register temp1, Register vbytes, Register vs1acc, Register vs2acc, Register vtable) {
        // @formatter:off
        // Below is a vectorized implementation of updating s1 and s2 for 16 bytes.
        // We use b1, b2, ..., b16 to denote the 16 bytes loaded in each iteration.
        // In non-vectorized code, we update s1 and s2 as:
        //   s1 <- s1 + b1
        //   s2 <- s2 + s1
        //   s1 <- s1 + b2
        //   s2 <- s2 + b1
        //   ...
        //   s1 <- s1 + b16
        //   s2 <- s2 + s1
        // Putting above assignments together, we have:
        //   s1_new = s1 + b1 + b2 + ... + b16
        //   s2_new = s2 + (s1 + b1) + (s1 + b1 + b2) + ... + (s1 + b1 + b2 + ... + b16)
        //          = s2 + s1 * 16 + (b1 * 16 + b2 * 15 + ... + b16 * 1)
        //          = s2 + s1 * 16 + (b1, b2, ... b16) dot (16, 15, ... 1)
        // @formatter:on
        masm.neon.ld1MultipleV(FullReg, Byte, vbytes, createStructureImmediatePostIndexAddress(LD1_MULTIPLE_1R, FullReg, Byte, buff, 16));

        // s2 = s2 + s1 * 16
        masm.add(64, s2, s2, s1, LSL, 4);

        // vs1acc = b1 + b2 + b3 + ... + b16
        // vs2acc = (b1 * 16) + (b2 * 15) + (b3 * 14) + ... + (b16 * 1)
        masm.neon.umullVVV(Byte, vs2acc, vtable, vbytes);
        masm.neon.umlal2VVV(Byte, vs2acc, vtable, vbytes);
        masm.neon.uaddlvSV(FullReg, Byte, vs1acc, vbytes);
        masm.neon.uaddlvSV(FullReg, HalfWord, vs2acc, vs2acc);

        // s1 = s1 + vs1acc, s2 = s2 + vs2acc
        masm.fmov(64, temp0, vs1acc);
        masm.fmov(64, temp1, vs2acc);
        masm.add(64, s1, s1, temp0);
        masm.add(64, s2, s2, temp1);
    }
}
