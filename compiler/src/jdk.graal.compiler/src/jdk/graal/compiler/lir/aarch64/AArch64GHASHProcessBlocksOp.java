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

import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp.asFloatRegister;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/be2b92bd8b43841cc2b9c22ed4fde29be30d47bb/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L6181-L6315",
          sha1 = "84b96e679b2ff5dc836da5c28fbbc779b5320a2b")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/12358e6c94bc96e618efc3ec5299a2cfe1b4669d/src/hotspot/cpu/aarch64/macroAssembler_aarch64_aes.cpp#L285-L691",
          sha1 = "1cd41d8f202ebe127aa31053ab3c6851f3900034")
// @formatter:on
public final class AArch64GHASHProcessBlocksOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64GHASHProcessBlocksOp> TYPE = LIRInstructionClass.create(AArch64GHASHProcessBlocksOp.class);

    private static final int REGISTER_STRIDE = 7;

    @Alive({REG}) private Value stateValue;
    @Alive({REG}) private Value htblValue;
    @Alive({REG}) private Value originalDataValue;
    @Alive({REG}) private Value originalBlocksValue;

    @Temp({REG}) private Value dataValue;
    @Temp({REG}) private Value blocksValue;

    @Temp protected Value[] temps;

    public AArch64GHASHProcessBlocksOp(LIRGeneratorTool tool,
                    AllocatableValue stateValue,
                    AllocatableValue htblValue,
                    AllocatableValue originalDataValue,
                    AllocatableValue originalBlocksValue) {
        super(TYPE);

        this.stateValue = stateValue;
        this.htblValue = htblValue;
        this.originalDataValue = originalDataValue;
        this.originalBlocksValue = originalBlocksValue;

        this.dataValue = tool.newVariable(originalDataValue.getValueKind());
        this.blocksValue = tool.newVariable(originalBlocksValue.getValueKind());

        this.temps = Arrays.stream(AArch64.simdRegisters.toArray()).map(Register::asValue).toArray(Value[]::new);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(stateValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);
        GraalError.guarantee(htblValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid htblValue kind: %s", htblValue);
        GraalError.guarantee(originalDataValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid originalDataValue kind: %s", originalDataValue);
        GraalError.guarantee(originalBlocksValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid originalBlocksValue kind: %s", originalBlocksValue);

        Label labelSmall = new Label();
        Label labelDone = new Label();

        Register state = asRegister(stateValue);
        Register subkeyH = asRegister(htblValue);
        Register originalData = asRegister(originalDataValue);
        Register originalBlocks = asRegister(originalBlocksValue);

        Register data = asRegister(dataValue);
        Register blocks = asRegister(blocksValue);

        masm.mov(64, data, originalData);
        masm.mov(32, blocks, originalBlocks);

        masm.compare(32, blocks, 8);
        masm.branchConditionally(ConditionFlag.LT, labelSmall);

        // No need to save/restore states as we already mark all SIMD registers as killed.
        // masm.sub(64, sp, sp, 4 * 16);
        // masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15,
        // AArch64Address.createBaseRegisterOnlyAddress(AArch64Address.ANY_SIZE, sp));
        // masm.sub(64, sp, sp, 4 * 16);
        // masm.neon.st1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11,
        // AArch64Address.createBaseRegisterOnlyAddress(AArch64Address.ANY_SIZE, sp));

        ghashProcessBlocksWide(masm, state, subkeyH, data, blocks, 4);

        // masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v8, v9, v10, v11,
        // AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R,
        // ASIMDSize.FullReg, ElementSize.Byte, sp, 64));
        // masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Byte, v12, v13, v14, v15,
        // AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R,
        // ASIMDSize.FullReg, ElementSize.Byte, sp, 64));

        masm.compare(32, blocks, 0);
        masm.branchConditionally(ConditionFlag.LE, labelDone);

        masm.bind(labelSmall);
        generateGhashProcessBlocks(masm, state, subkeyH, data, blocks);
        masm.bind(labelDone);
    }

    private static void generateGhashProcessBlocks(AArch64MacroAssembler masm,
                    Register state,
                    Register subkeyH,
                    Register data,
                    Register blocks) {
        // Bafflingly, GCM uses little-endian for the byte order, but
        // big-endian for the bit order. For example, the polynomial 1 is
        // represented as the 16-byte string 80 00 00 00 | 12 bytes of 00.
        //
        // So, we must either reverse the bytes in each word and do
        // everything big-endian or reverse the bits in each byte and do
        // it little-endian. On AArch64 it's more idiomatic to reverse
        // the bits in each byte (we have an instruction, RBIT, to do
        // that) and keep the data in little-endian bit order through the
        // calculation, bit-reversing the inputs and outputs.
        Register vzr = v30;
        masm.neon.eorVVV(ASIMDSize.FullReg, vzr, vzr, vzr); // zero register
        // The field polynomial
        try (AArch64MacroAssembler.ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            masm.mov(scratch, 0x00000087L);
            masm.neon.dupVG(ASIMDSize.FullReg, ElementSize.DoubleWord, v24, scratch);
        }

        masm.fldr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, state));
        masm.fldr(128, v1, AArch64Address.createBaseRegisterOnlyAddress(128, subkeyH));

        // Bit-reverse words in state and subkeyH
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v0, v0);
        masm.neon.rbitVV(ASIMDSize.FullReg, v0, v0);
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v1, v1);
        masm.neon.rbitVV(ASIMDSize.FullReg, v1, v1);

        // long-swap subkeyH into v1
        masm.neon.extVVV(ASIMDSize.FullReg, v4, v1, v1, 0x08);
        // xor subkeyH into subkeyL (Karatsuba: (A1+A0))
        masm.neon.eorVVV(ASIMDSize.FullReg, v4, v4, v1);

        Label labelGHASHLoop = new Label();
        masm.bind(labelGHASHLoop);

        // Load the data, bit reversing each byte
        masm.fldr(128, v2, AArch64Address.createImmediateAddress(128, IMMEDIATE_POST_INDEXED, data, 0x10));
        masm.neon.rbitVV(ASIMDSize.FullReg, v2, v2);
        // bit-swapped data ^ bit-swapped state
        masm.neon.eorVVV(ASIMDSize.FullReg, v2, v0, v2);

        // Multiply state in v2 by subkey in v1
        ghashMultiply(masm,
                        /* resultLo */v5,
                        /* resultHi */v7,
                        /* a */v1,
                        /* b */v2,
                        /* a1XORa0 */v4,
                        /* temps */v6,
                        v3,
                        /* reuse/clobber b */v2);
        // Reduce v7:v5 by the field polynomial
        ghashReduce(masm,
                        /* result */v0,
                        /* lo */v5,
                        /* hi */v7,
                        /* p */v24,
                        vzr,
                        /* temp */v3);

        masm.sub(32, blocks, blocks, 1);
        masm.cbnz(32, blocks, labelGHASHLoop);

        // The bit-reversed result is at this point in v0
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v0, v0);
        masm.neon.rbitVV(ASIMDSize.FullReg, v0, v0);
        masm.fstr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, state));
    }

    /**
     * Interleaved GHASH processing. Clobbers all vector registers.
     */
    private static void ghashProcessBlocksWide(AArch64MacroAssembler masm,
                    Register state,
                    Register subkeyH,
                    Register data,
                    Register blocks,
                    int unrolls) {
        Register a1XORa0 = v28;
        Register hPrime = v29;
        Register vzr = v30;
        Register p = v31;
        masm.neon.eorVVV(ASIMDSize.FullReg, vzr, vzr, vzr); // zero register

        // The field polynomial
        try (AArch64MacroAssembler.ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            masm.mov(scratch, 0x00000087L);
            masm.neon.dupVG(ASIMDSize.FullReg, ElementSize.DoubleWord, p, scratch);
        }

        masm.fldr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, state));
        masm.fldr(128, hPrime, AArch64Address.createBaseRegisterOnlyAddress(128, subkeyH));

        // Bit-reverse words in state and subkeyH
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v0, v0);
        masm.neon.rbitVV(ASIMDSize.FullReg, v0, v0);

        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, hPrime, hPrime);
        masm.neon.rbitVV(ASIMDSize.FullReg, hPrime, hPrime);

        // Powers of H -> hPrime

        Label labelAlreadyCalculated = new Label();
        Label labelDone = new Label();
        // The first time around we'll have to calculate H**2, H**3, etc.
        // Look at the largest power of H in the subkeyH array to see if
        // it's already been calculated.
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();
            Register rscratch2 = sc2.getRegister();
            masm.ldp(64, rscratch1, rscratch2, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, subkeyH, 16 * (unrolls - 1)));
            masm.orr(64, rscratch1, rscratch1, rscratch2);
            masm.cbnz(64, rscratch1, labelAlreadyCalculated);
        }

        // Start with H in v6 and hPrime
        masm.neon.orrVVV(ASIMDSize.FullReg, v6, hPrime, hPrime);
        for (int i = 1; i < unrolls; i++) {
            // long-swap subkeyH into a1XORa0
            masm.neon.extVVV(ASIMDSize.FullReg, a1XORa0, hPrime, hPrime, 0x08);
            // xor subkeyH into subkeyL (Karatsuba:(A1+A0))
            masm.neon.eorVVV(ASIMDSize.FullReg, a1XORa0, a1XORa0, hPrime);
            ghashModmul(masm,
                            /* result */v6,
                            /* result_lo */v5,
                            /* result_hi */v4,
                            /* b */v6,
                            hPrime,
                            vzr,
                            a1XORa0,
                            p,
                            /* temps */v1,
                            v3,
                            v2);
            masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v1, v6);
            masm.neon.rbitVV(ASIMDSize.FullReg, v1, v1);
            masm.fstr(128, v1, AArch64Address.createImmediateAddress(128, IMMEDIATE_SIGNED_UNSCALED, subkeyH, 16 * i));
        }
        masm.jmp(labelDone);
        masm.bind(labelAlreadyCalculated);

        // Load the largest power of H we need into v6.
        masm.fldr(128, v6, AArch64Address.createImmediateAddress(128, IMMEDIATE_SIGNED_UNSCALED, subkeyH, 16 * (unrolls - 1)));
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v6, v6);
        masm.neon.rbitVV(ASIMDSize.FullReg, v6, v6);

        masm.bind(labelDone);
        // Move H ** unrolls into hPrime
        masm.neon.orrVVV(ASIMDSize.FullReg, hPrime, v6, v6);

        // hPrime contains (H ** 1, H ** 2, ... H ** unrolls)
        // v0 contains the initial state. Clear the others.
        for (int i = 1; i < unrolls; i++) {
            int ofs = i * REGISTER_STRIDE;
            // zero each state register
            masm.neon.eorVVV(ASIMDSize.FullReg, asFloatRegister(v0, ofs), asFloatRegister(v0, ofs), asFloatRegister(v0, ofs));
        }

        // long-swap subkeyH into a1XORa0
        masm.neon.extVVV(ASIMDSize.FullReg, a1XORa0, hPrime, hPrime, 0x08);
        // xor subkeyH into subkeyL (Karatsuba: (A1+A0))
        masm.neon.eorVVV(ASIMDSize.FullReg, a1XORa0, a1XORa0, hPrime);

        // Load #unrolls blocks of data
        for (int ofs = 0; ofs < unrolls * REGISTER_STRIDE; ofs += REGISTER_STRIDE) {
            masm.fldr(128, asFloatRegister(v2, ofs), AArch64Address.createImmediateAddress(128, IMMEDIATE_POST_INDEXED, data, 0x10));
        }

        // Register assignments, replicated across 4 clones, v0 ... v23
        //
        // v0: input / output: current state, result of multiply/reduce
        // v1: temp
        // v2: input: one block of data (the ciphertext)
        // also used as a temp once the data has been consumed
        // v3: temp
        // v4: output: high part of product
        // v5: output: low part ...
        // v6: unused
        //
        // Not replicated:
        //
        // v28: High part of H xor low part of H'
        // v29: H' (hash subkey)
        // v30: zero
        // v31: Reduction polynomial of the Galois field

        // Inner loop.
        // Do the whole load/add/multiply/reduce over all our data except
        // the last few rows.
        Label labelGHASHLoop = new Label();
        masm.bind(labelGHASHLoop);

        // Prefetching doesn't help here. In fact, on Neoverse N1 it's worse.
        // prfm(Address(data, 128), PLDL1KEEP);

        // Xor data into current state
        for (int ofs = 0; ofs < unrolls * REGISTER_STRIDE; ofs += REGISTER_STRIDE) {
            // bit-swapped data ^ bit-swapped state
            masm.neon.rbitVV(ASIMDSize.FullReg, asFloatRegister(v2, ofs), asFloatRegister(v2, ofs));
            masm.neon.eorVVV(ASIMDSize.FullReg, asFloatRegister(v2, ofs), asFloatRegister(v0, ofs), asFloatRegister(v2, ofs));
        }

        // Generate fully-unrolled multiply-reduce in two stages.
        new GHASHMultiplyGenerator(masm,
                        unrolls,
                        /* result_lo */v5,
                        /* result_hi */v4,
                        /* data */v2,
                        hPrime,
                        a1XORa0,
                        p,
                        vzr,
                        /* temps */v1,
                        v3,
                        /* reuse b */v2).unroll();

        // NB: GHASHReduceGenerator also loads the next #unrolls blocks of
        // data into v0, v0+ofs, the current state.
        new GHASHReduceGenerator(masm,
                        unrolls,
                        /* result */v0,
                        /* lo */v5,
                        /* hi */v4,
                        p,
                        vzr,
                        data,
                        /* data */v2,
                        /* temp */v3,
                        true).unroll();

        masm.sub(32, blocks, blocks, unrolls);
        masm.compare(32, blocks, unrolls * 2);
        masm.branchConditionally(ConditionFlag.GE, labelGHASHLoop);

        // Merge the #unrolls states. Note that the data for the next
        // iteration has already been loaded into v4, v4+ofs, etc...

        // First, we multiply/reduce each clone by the appropriate power of H.
        for (int i = 0; i < unrolls; i++) {
            int ofs = i * REGISTER_STRIDE;
            masm.fldr(128, hPrime, AArch64Address.createImmediateAddress(128, IMMEDIATE_SIGNED_UNSCALED, subkeyH, 16 * (unrolls - i - 1)));

            masm.neon.rbitVV(ASIMDSize.FullReg, asFloatRegister(v2, ofs), asFloatRegister(v2, ofs));
            // bit-swapped data ^ bit-swapped state
            masm.neon.eorVVV(ASIMDSize.FullReg, asFloatRegister(v2, ofs), asFloatRegister(v0, ofs), asFloatRegister(v2, ofs));

            masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, hPrime, hPrime);
            masm.neon.rbitVV(ASIMDSize.FullReg, hPrime, hPrime);
            // long-swap subkeyH into a1XORa0
            masm.neon.extVVV(ASIMDSize.FullReg, a1XORa0, hPrime, hPrime, 0x08);
            // xor subkeyH into subkeyL (Karatsuba: (A1+A0))
            masm.neon.eorVVV(ASIMDSize.FullReg, a1XORa0, a1XORa0, hPrime);
            ghashModmul(masm,
                            /* result */asFloatRegister(v0, ofs),
                            /* resultLo */asFloatRegister(v5, ofs),
                            /* resultHi */asFloatRegister(v4, ofs),
                            /* b */asFloatRegister(v2, ofs),
                            hPrime,
                            vzr,
                            a1XORa0,
                            p,
                            /* temps */asFloatRegister(v1, ofs),
                            asFloatRegister(v3, ofs),
                            /* reuse b */asFloatRegister(v2, ofs));
        }

        // Then we sum the results.
        for (int i = 0; i < unrolls - 1; i++) {
            int ofs = i * REGISTER_STRIDE;
            masm.neon.eorVVV(ASIMDSize.FullReg, v0, v0, asFloatRegister(v0, ofs + REGISTER_STRIDE));
        }

        masm.sub(32, blocks, blocks, unrolls);

        // And finally bit-reverse the state back to big endian.
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Byte, v0, v0);
        masm.neon.rbitVV(ASIMDSize.FullReg, v0, v0);
        masm.fstr(128, v0, AArch64Address.createBaseRegisterOnlyAddress(128, state));
    }

    static final class GHASHMultiplyGenerator extends AArch64AESEncryptOp.KernelGenerator {

        private final AArch64MacroAssembler masm;
        private final Register resultLo;
        private final Register resultHi;
        private final Register b;
        private final Register a;
        private final Register vzr;
        private final Register a1XORa0;
        private final Register p;
        private final Register tmp1;
        private final Register tmp2;
        private final Register tmp3;

        GHASHMultiplyGenerator(AArch64MacroAssembler masm,
                        int unrolls,
                        Register resultLo,
                        Register resultHi,
                        Register b,
                        Register a,
                        Register a1XORa0,
                        Register p,
                        Register vzr,
                        Register tmp1,
                        Register tmp2,
                        Register tmp3) {
            super(unrolls);
            this.masm = masm;
            this.resultLo = resultLo;
            this.resultHi = resultHi;
            this.b = b;
            this.a = a;
            this.a1XORa0 = a1XORa0;
            this.p = p;
            this.vzr = vzr;
            this.tmp1 = tmp1;
            this.tmp2 = tmp2;
            this.tmp3 = tmp3;
        }

        @Override
        public void generate(int index) {
            // Karatsuba multiplication performs a 128*128 -> 256-bit
            // multiplication in three 128-bit multiplications and a few
            // additions.
            //
            // (C1:C0) = A1*B1, (D1:D0) = A0*B0, (E1:E0) = (A0+A1)(B0+B1)
            // (A1:A0)(B1:B0) = C1:(C0+C1+D1+E1):(D1+C0+D0+E0):D0
            //
            // Inputs:
            //
            // A0 in a.d[0] (subkey)
            // A1 in a.d[1]
            // (A1+A0) in a1_xor_a0.d[0]
            //
            // B0 in b.d[0] (state)
            // B1 in b.d[1]

            switch (index) {
                case 0:
                    masm.neon.extVVV(ASIMDSize.FullReg, tmp1, b, b, 0x08);
                    break;
                case 1:
                    masm.neon.pmull2VVV(ElementSize.DoubleWord, resultHi, b, a); // A1*B1
                    break;
                case 2:
                    masm.neon.eorVVV(ASIMDSize.FullReg, tmp1, tmp1, b); // (B1+B0)
                    break;
                case 3:
                    masm.neon.pmullVVV(ElementSize.DoubleWord, resultLo, b, a); // A0*B0
                    break;
                case 4:
                    masm.neon.pmullVVV(ElementSize.DoubleWord, tmp2, tmp1, a1XORa0); // (A1+A0)(B1+B0)
                    break;
                case 5:
                    masm.neon.extVVV(ASIMDSize.FullReg, tmp1, resultLo, resultHi, 0x08);
                    break;
                case 6:
                    masm.neon.eorVVV(ASIMDSize.FullReg, tmp3, resultHi, resultLo); // A1*B1+A0*B0
                    break;
                case 7:
                    masm.neon.eorVVV(ASIMDSize.FullReg, tmp2, tmp2, tmp1);
                    break;
                case 8:
                    masm.neon.eorVVV(ASIMDSize.FullReg, tmp2, tmp2, tmp3);
                    break;
                // Register pair <resultHi:resultLo> holds the result of carry-less multiplication
                case 9:
                    masm.neon.insXX(ElementSize.DoubleWord, resultHi, 0, tmp2, 1);
                    break;
                case 10:
                    masm.neon.insXX(ElementSize.DoubleWord, resultLo, 1, tmp2, 0);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(index); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        public AArch64AESEncryptOp.KernelGenerator next() {
            return new GHASHMultiplyGenerator(masm,
                            unrolls,
                            asFloatRegister(resultLo, REGISTER_STRIDE),
                            asFloatRegister(resultHi, REGISTER_STRIDE),
                            asFloatRegister(b, REGISTER_STRIDE),
                            a,
                            a1XORa0,
                            p,
                            vzr,
                            asFloatRegister(tmp1, REGISTER_STRIDE),
                            asFloatRegister(tmp2, REGISTER_STRIDE),
                            asFloatRegister(tmp3, REGISTER_STRIDE));
        }

        @Override
        public int length() {
            return 11;
        }
    }

    /**
     * Reduce the 128-bit product in hi:lo by the GCM field polynomial. The Register argument called
     * data is optional: if it is a valid register, we interleave LD1 instructions with the
     * reduction. This is to reduce latency next time around the loop.
     */
    static final class GHASHReduceGenerator extends AArch64AESEncryptOp.KernelGenerator {

        private final AArch64MacroAssembler masm;
        private final Register result;
        private final Register lo;
        private final Register hi;
        private final Register p;
        private final Register vzr;
        private final Register dataPtr;
        private final Register data;
        private final Register t1;
        private final boolean once;

        GHASHReduceGenerator(AArch64MacroAssembler masm,
                        int unrolls,
                        Register result,
                        Register lo,
                        Register hi,
                        Register p,
                        Register vzr,
                        Register dataPtr,
                        Register data,
                        Register t1,
                        boolean once) {
            super(unrolls);

            this.masm = masm;
            this.result = result;
            this.lo = lo;
            this.hi = hi;
            this.p = p;
            this.vzr = vzr;
            this.dataPtr = dataPtr;
            this.data = data;
            this.t1 = t1;
            this.once = once;
        }

        @Override
        public void generate(int index) {
            Register t0 = result;

            switch (index) {
                // The GCM field polynomial f is z^128 + p(z), where p =
                // z^7+z^2+z+1.
                //
                // z^128 === -p(z) (mod (z^128 + p(z)))
                //
                // so, given that the product we're reducing is
                // a == lo + hi * z^128
                // substituting,
                // === lo - hi * p(z) (mod (z^128 + p(z)))
                //
                // we reduce by multiplying hi by p(z) and subtracting the _result
                // from (i.e. XORing it with) lo. Because p has no nonzero high
                // bits we can do this with two 64-bit multiplications, lo*p and
                // hi*p.
                case 0:
                    masm.neon.pmull2VVV(ElementSize.DoubleWord, t0, hi, p);
                    break;
                case 1:
                    masm.neon.extVVV(ASIMDSize.FullReg, t1, t0, vzr, 8);
                    break;
                case 2:
                    masm.neon.eorVVV(ASIMDSize.FullReg, hi, hi, t1);
                    break;
                case 3:
                    masm.neon.extVVV(ASIMDSize.FullReg, t1, vzr, t0, 8);
                    break;
                case 4:
                    masm.neon.eorVVV(ASIMDSize.FullReg, lo, lo, t1);
                    break;
                case 5:
                    masm.neon.pmullVVV(ElementSize.DoubleWord, t0, hi, p);
                    break;
                case 6:
                    masm.neon.eorVVV(ASIMDSize.FullReg, result, lo, t0);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(index); // ExcludeFromJacocoGeneratedReport
            }

            // Sprinkle load instructions into the generated instructions
            if (!Register.None.equals(data) && once) {
                assert length() >= unrolls : "not enough room for interleaved loads";
                if (index < unrolls) {
                    masm.fldr(128, asFloatRegister(data, index * REGISTER_STRIDE),
                                    AArch64Address.createImmediateAddress(128, IMMEDIATE_POST_INDEXED, dataPtr, 0x10));
                }
            }
        }

        @Override
        public AArch64AESEncryptOp.KernelGenerator next() {
            return new GHASHReduceGenerator(masm,
                            unrolls,
                            asFloatRegister(result, REGISTER_STRIDE),
                            asFloatRegister(lo, REGISTER_STRIDE),
                            asFloatRegister(hi, REGISTER_STRIDE),
                            p,
                            vzr,
                            dataPtr,
                            data,
                            asFloatRegister(t1, REGISTER_STRIDE),
                            false);
        }

        @Override
        public int length() {
            return 7;
        }
    }

    /**
     * Perform a GHASH multiply/reduce on a single FloatRegister.
     */
    private static void ghashModmul(AArch64MacroAssembler masm,
                    Register result,
                    Register resultLo,
                    Register resultHi,
                    Register b,
                    Register a,
                    Register vzr,
                    Register a1XORa0,
                    Register p,
                    Register t1,
                    Register t2,
                    Register t3) {
        ghashMultiply(masm, resultLo, resultHi, a, b, a1XORa0, t1, t2, t3);
        ghashReduce(masm, result, resultLo, resultHi, p, vzr, t1);
    }

    private static void ghashReduce(AArch64MacroAssembler masm,
                    Register result,
                    Register lo,
                    Register hi,
                    Register p,
                    Register vzr,
                    Register t1) {
        Register t0 = result;

        // The GCM field polynomial f is z^128 + p(z), where p =
        // z^7+z^2+z+1.
        //
        // z^128 === -p(z) (mod (z^128 + p(z)))
        //
        // so, given that the product we're reducing is
        // a == lo + hi * z^128
        // substituting,
        // === lo - hi * p(z) (mod (z^128 + p(z)))
        //
        // we reduce by multiplying hi by p(z) and subtracting the result
        // from (i.e. XORing it with) lo. Because p has no nonzero high
        // bits we can do this with two 64-bit multiplications, lo*p and
        // hi*p.

        masm.neon.pmull2VVV(ElementSize.DoubleWord, t0, hi, p);
        masm.neon.extVVV(ASIMDSize.FullReg, t1, t0, vzr, 8);
        masm.neon.eorVVV(ASIMDSize.FullReg, hi, hi, t1);
        masm.neon.extVVV(ASIMDSize.FullReg, t1, vzr, t0, 8);
        masm.neon.eorVVV(ASIMDSize.FullReg, lo, lo, t1);
        masm.neon.pmullVVV(ElementSize.DoubleWord, t0, hi, p);
        masm.neon.eorVVV(ASIMDSize.FullReg, result, lo, t0);
    }

    /**
     *
     * ghashMultiply and ghashReduce are the non-unrolled versions of the GHASH function generators.
     */
    private static void ghashMultiply(AArch64MacroAssembler masm,
                    Register resultLo,
                    Register resultHi,
                    Register a,
                    Register b,
                    Register a1XORa0,
                    Register tmp1,
                    Register tmp2,
                    Register tmp3) {
        // Karatsuba multiplication performs a 128*128 -> 256-bit
        // multiplication in three 128-bit multiplications and a few
        // additions.
        //
        // (C1:C0) = A1*B1, (D1:D0) = A0*B0, (E1:E0) = (A0+A1)(B0+B1)
        // (A1:A0)(B1:B0) = C1:(C0+C1+D1+E1):(D1+C0+D0+E0):D0
        //
        // Inputs:
        //
        // A0 in a.d[0] (subkey)
        // A1 in a.d[1]
        // (A1+A0) in a1_xor_a0.d[0]
        //
        // B0 in b.d[0] (state)
        // B1 in b.d[1]
        masm.neon.extVVV(ASIMDSize.FullReg, tmp1, b, b, 0x08);
        masm.neon.pmull2VVV(ElementSize.DoubleWord, resultHi, b, a); // A1*B1
        masm.neon.eorVVV(ASIMDSize.FullReg, tmp1, tmp1, b); // (B1+B0)
        masm.neon.pmullVVV(ElementSize.DoubleWord, resultLo, b, a);  // A0*B0
        masm.neon.pmullVVV(ElementSize.DoubleWord, tmp2, tmp1, a1XORa0); // (A1+A0)(B1+B0)

        masm.neon.extVVV(ASIMDSize.FullReg, tmp1, resultLo, resultHi, 0x08);
        masm.neon.eorVVV(ASIMDSize.FullReg, tmp3, resultHi, resultLo); // A1*B1+A0*B0
        masm.neon.eorVVV(ASIMDSize.FullReg, tmp2, tmp2, tmp1);
        masm.neon.eorVVV(ASIMDSize.FullReg, tmp2, tmp2, tmp3);

        // Register pair <resultHi:resultLo> holds the result of carry-less multiplication
        masm.neon.insXX(ElementSize.DoubleWord, resultHi, 0, tmp2, 1);
        masm.neon.insXX(ElementSize.DoubleWord, resultLo, 1, tmp2, 0);
    }
}
