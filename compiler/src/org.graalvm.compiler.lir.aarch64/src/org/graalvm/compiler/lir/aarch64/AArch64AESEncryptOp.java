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

import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp",
          lineStart = 2571,
          lineEnd   = 2601,
          commit    = "4a300818fe7a47932c5b762ccd3b948815a31974",
          sha1      = "350e5592f4df298c7ee648581bb1e8342edf9a05")
@StubPort(path      = "src/hotspot/cpu/aarch64/macroAssembler_aarch64_aes.cpp",
          lineStart = 112,
          lineEnd   = 283,
          commit    = "2fe0ce01485d7b84dc109d3d4f24bdd908c0e7cf",
          sha1      = "0809579798e28fe7d2439e9ac5d5f8e23f1fcd21")
// @formatter:on
public final class AArch64AESEncryptOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64AESEncryptOp> TYPE = LIRInstructionClass.create(AArch64AESEncryptOp.class);

    private final int lengthOffset;

    @Alive({REG}) private Value fromValue;
    @Alive({REG}) private Value toValue;
    @Alive({REG}) private Value keyValue;

    @Temp({REG}) private Value[] temps;

    public AArch64AESEncryptOp(Value fromValue, Value toValue, Value keyValue, int lengthOffset) {
        super(TYPE);
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.keyValue = keyValue;
        this.lengthOffset = lengthOffset;
        this.temps = new Value[]{
                        v0.asValue(),
                        v17.asValue(),
                        v18.asValue(),
                        v19.asValue(),
                        v20.asValue(),
                        v21.asValue(),
                        v22.asValue(),
                        v23.asValue(),
                        v24.asValue(),
                        v25.asValue(),
                        v26.asValue(),
                        v27.asValue(),
                        v28.asValue(),
                        v29.asValue(),
                        v30.asValue(),
                        v31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(fromValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid fromValue kind: %s", fromValue);
        GraalError.guarantee(toValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid toValue kind: %s", toValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);

        Register from = asRegister(fromValue); // source array address
        Register to = asRegister(toValue);     // destination array address
        Register key = asRegister(keyValue);   // key array address

        try (ScratchRegister sr = masm.getScratchRegister()) {
            Register keylen = sr.getRegister();
            masm.ldr(32, keylen, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, key, lengthOffset));

            aesencLoadkeys(masm, key, keylen);
            aesecbEncrypt(masm, from, to, keylen, v0, 1);
        }
    }

    static Register asFloatRegister(Register base, int offset) {
        return AArch64.simdRegisters.get(base.encoding + offset);
    }

    static void aesencLoadkeys(AArch64MacroAssembler masm, Register key, Register keylen) {
        Label loadkeys44 = new Label();
        Label loadkeys52 = new Label();

        masm.compare(32, keylen, 52);
        masm.branchConditionally(ConditionFlag.LO, loadkeys44);
        masm.branchConditionally(ConditionFlag.EQ, loadkeys52);

        AArch64Address ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
        masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, v17, v18, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v17, v17);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v18, v18);

        masm.bind(loadkeys52);
        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_2R, ASIMDSize.FullReg, ElementSize.Byte, key, 32);
        masm.neon.ld1MultipleVV(ASIMDSize.FullReg, ElementSize.Byte, v19, v20, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v19, v19);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v20, v20);

        masm.bind(loadkeys44);
        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, key, 64);
        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v21, v22, v23, v24, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v21, v21);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v22, v22);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v23, v23);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v24, v24);

        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R, ASIMDSize.FullReg, ElementSize.Byte, key, 64);
        masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v25, v26, v27, v28, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v25, v25);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v26, v26);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v27, v27);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v28, v28);

        ld1Addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_3R, ASIMDSize.FullReg, ElementSize.Byte, key, 48);
        masm.neon.ld1MultipleVVV(ASIMDSize.FullReg, ElementSize.Byte, v29, v30, v31, ld1Addr);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v29, v29);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v30, v30);
        masm.neon.rev32VV(ASIMDSize.FullReg, ElementSize.Byte, v31, v31);

        // Preserve the address of the start of the key
        masm.sub(64, key, key, keylen, AArch64Assembler.ShiftType.LSL, CodeUtil.log2(JavaKind.Int.getByteCount()));
    }

    /**
     * The abstract base class of an unrolled function generator. Subclasses override
     * {@link #generate(int)}, {@link #length()}, and {@link #next()} to generate unrolled and
     * interleaved functions.
     */
    abstract static class KernelGenerator {
        protected final int unrolls;

        KernelGenerator(int unrolls) {
            this.unrolls = unrolls;
        }

        public abstract void generate(int index);

        public abstract int length();

        public abstract KernelGenerator next();

        public void unroll() {
            KernelGenerator[] generators = new KernelGenerator[unrolls];
            generators[0] = this;
            for (int i = 1; i < unrolls; i++) {
                generators[i] = generators[i - 1].next();
            }

            for (int j = 0; j < length(); j++) {
                for (int i = 0; i < unrolls; i++) {
                    generators[i].generate(j);
                }
            }
        }
    }

    /** An unrolled and interleaved generator for AES encryption. */
    static final class AESKernelGenerator extends KernelGenerator {

        private final AArch64MacroAssembler masm;
        private final Register from;
        private final Register to;
        private final Register keylen;
        private final Register data;
        private final Register subkeys;
        private final boolean once;

        private final Label rounds44;
        private final Label rounds52;

        AESKernelGenerator(AArch64MacroAssembler masm,
                        int unrolls,
                        Register from,
                        Register to,
                        Register keylen,
                        Register data,
                        Register subkeys,
                        boolean once) {
            super(unrolls);
            this.masm = masm;
            this.from = from;
            this.to = to;
            this.keylen = keylen;
            this.data = data;
            this.subkeys = subkeys;
            this.once = once;
            this.rounds44 = new Label();
            this.rounds52 = new Label();
        }

        AESKernelGenerator(AArch64MacroAssembler masm,
                        int unrolls,
                        Register from,
                        Register to,
                        Register keylen,
                        Register data,
                        Register subkeys) {
            this(masm,
                            unrolls,
                            from,
                            to,
                            keylen,
                            data,
                            subkeys,
                            true);
        }

        private void aesRound(Register input, Register subkey) {
            masm.neon.aese(input, subkey);
            masm.neon.aesmc(input, input);
        }

        @Override
        public void generate(int index) {
            switch (index) {
                case 0:
                    if (!from.equals(Register.None)) {
                        // get 16 bytes of input
                        masm.fldr(128, data, AArch64Address.createBaseRegisterOnlyAddress(128, from));
                    }
                    break;
                case 1:
                    if (once) {
                        masm.compare(32, keylen, 52);
                        masm.branchConditionally(ConditionFlag.LO, rounds44);
                        masm.branchConditionally(ConditionFlag.EQ, rounds52);
                    }
                    break;
                case 2:
                    aesRound(data, asFloatRegister(subkeys, 0));
                    break;
                case 3:
                    aesRound(data, asFloatRegister(subkeys, 1));
                    break;
                case 4:
                    if (once) {
                        masm.bind(rounds52);
                    }
                    break;
                case 5:
                    aesRound(data, asFloatRegister(subkeys, 2));
                    break;
                case 6:
                    aesRound(data, asFloatRegister(subkeys, 3));
                    break;
                case 7:
                    if (once) {
                        masm.bind(rounds44);
                    }
                    break;
                case 8:
                    aesRound(data, asFloatRegister(subkeys, 4));
                    break;
                case 9:
                    aesRound(data, asFloatRegister(subkeys, 5));
                    break;
                case 10:
                    aesRound(data, asFloatRegister(subkeys, 6));
                    break;
                case 11:
                    aesRound(data, asFloatRegister(subkeys, 7));
                    break;
                case 12:
                    aesRound(data, asFloatRegister(subkeys, 8));
                    break;
                case 13:
                    aesRound(data, asFloatRegister(subkeys, 9));
                    break;
                case 14:
                    aesRound(data, asFloatRegister(subkeys, 10));
                    break;
                case 15:
                    aesRound(data, asFloatRegister(subkeys, 11));
                    break;
                case 16:
                    aesRound(data, asFloatRegister(subkeys, 12));
                    break;
                case 17:
                    masm.neon.aese(data, asFloatRegister(subkeys, 13));
                    break;
                case 18:
                    masm.neon.eorVVV(ASIMDSize.FullReg, data, data, asFloatRegister(subkeys, 14));
                    break;
                case 19:
                    if (!to.equals(Register.None)) {
                        masm.fstr(128, data, AArch64Address.createBaseRegisterOnlyAddress(128, to));
                    }
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }

        @Override
        public KernelGenerator next() {
            return new AESKernelGenerator(masm,
                            unrolls,
                            from,
                            to,
                            keylen,
                            asFloatRegister(data, 1),
                            subkeys,
                            false);
        }

        @Override
        public int length() {
            return 20;
        }
    }

    // Uses expanded key in v17..v31
    // Returns encrypted values in inputs.
    // If to != noreg, store value at to; likewise from
    // Preserves key, keylen
    // Increments from, to
    // Input data in v0, v1, ...
    // unrolls controls the number of times to unroll the generated function
    static void aesecbEncrypt(AArch64MacroAssembler masm, Register from, Register to, Register keylen, Register data, int unrolls) {
        new AESKernelGenerator(masm, unrolls, from, to, keylen, data, v17).unroll();
    }
}
