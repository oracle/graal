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
import static org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Word;

import java.util.Arrays;

import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public abstract class AArch64ComplexVectorOp extends AArch64LIRInstruction {

    protected AArch64ComplexVectorOp(LIRInstructionClass<? extends AArch64LIRInstruction> c) {
        super(c);
    }

    protected static AllocatableValue[] allocateTempRegisters(LIRGeneratorTool tool, int n) {
        return allocateRegisters(tool, LIRKind.value(tool.target().arch.getWordKind()), n);
    }

    protected static AllocatableValue[] allocateVectorRegisters(LIRGeneratorTool tool, int n) {
        return allocateRegisters(tool, LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD)), n);
    }

    private static AllocatableValue[] allocateRegisters(LIRGeneratorTool tool, LIRKind kind, int n) {
        AllocatableValue[] values = new AllocatableValue[n];
        for (int i = 0; i < values.length; i++) {
            values[i] = tool.newVariable(kind);
        }
        return values;
    }

    private static final Register[] consecutiveSIMDRegisters = {
                    AArch64.v0,
                    AArch64.v1,
                    AArch64.v2,
                    AArch64.v3,
                    AArch64.v4,
                    AArch64.v5,
                    AArch64.v6,
                    AArch64.v7,
                    AArch64.v8,
                    AArch64.v9,
                    AArch64.v10,
                    AArch64.v11,
                    AArch64.v12,
                    AArch64.v13,
                    AArch64.v14,
                    AArch64.v15,
                    AArch64.v16,
                    AArch64.v17,
                    AArch64.v18,
                    AArch64.v19,
                    AArch64.v20,
                    AArch64.v21,
                    AArch64.v22,
                    AArch64.v23,
                    AArch64.v24,
                    AArch64.v25,
                    AArch64.v26,
                    AArch64.v27,
                    AArch64.v28,
                    AArch64.v29,
                    AArch64.v30,
                    AArch64.v31
    };

    protected static Value[] allocateConsecutiveVectorRegisters(LIRGeneratorTool tool, int n) {
        Value[] ret = new Value[n];
        for (int i = 0; i < n; i++) {
            ret[i] = consecutiveSIMDRegisters[i].asValue(LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD)));
        }
        return ret;
    }

    protected static void loadDataSectionAddress(CompilationResultBuilder crb, AArch64MacroAssembler asm, Register dst, DataSection.Data data) {
        crb.recordDataSectionReference(data);
        asm.adrpAdd(dst);
    }

    /**
     * Check if vector register {@code src} is all zeros and set condition flags accordingly.
     * Register {@code dst} is clobbered by this operation.
     */
    protected static void vectorCheckZero(AArch64MacroAssembler asm, Register dst, Register src) {
        vectorCheckZero(asm, AArch64ASIMDAssembler.ElementSize.Byte, dst, src, false);
    }

    /**
     * Check if vector register {@code src} is all zeros and set condition flags accordingly. If
     * {@code allOnes} is true, assume that non-zero elements are always filled with ones, e.g.
     * {@code 0xff} for byte elements, allowing a slightly faster check.
     */
    protected static void vectorCheckZero(AArch64MacroAssembler asm, AArch64ASIMDAssembler.ElementSize eSize, Register dst, Register src, boolean allOnes) {
        assert src.getRegisterCategory().equals(SIMD);
        assert dst.getRegisterCategory().equals(SIMD);
        if (eSize == AArch64ASIMDAssembler.ElementSize.Byte || !allOnes) {
            asm.neon.umaxvSV(FullReg, Word, dst, src);
        } else {
            asm.neon.xtnVV(eSize.narrow(), dst, src);
        }
        asm.fcmpZero(64, dst);
    }

    /**
     * Initialize the vector mask needed for
     * {@link #calcIndexOfFirstMatch(AArch64MacroAssembler, Register, Register, Register, Register, boolean, Runnable)}.
     *
     * @param vecMask Result vector.
     * @param tmp Temp register, will be overwritten.
     */
    protected static void initCalcIndexOfFirstMatchMask(AArch64MacroAssembler asm, Register vecMask, Register tmp) {
        asm.mov(tmp, 0xc030_0c03);
        asm.neon.dupVG(FullReg, Word, vecMask, tmp);
    }

    private static final Runnable NO_ACTION = () -> {
    };

    protected static void calcIndexOfFirstMatch(AArch64MacroAssembler asm, Register dst, Register vec1, Register vec2, Register vecMask, boolean inverse) {
        calcIndexOfFirstMatch(asm, dst, vec1, vec2, vecMask, inverse, NO_ACTION);
    }

    /**
     * Calculate the index of the first matching byte of two vectors generated e.g. by
     * {@link AArch64ASIMDAssembler#cmeqVVV(AArch64ASIMDAssembler.ASIMDSize, AArch64ASIMDAssembler.ElementSize, Register, Register, Register)
     * cmeqVVV} instructions.
     *
     * @param dst Generic destination register. To get the correct result, right-shift it by 1.
     * @param vecMask mask vector generated by
     *            {@link #initCalcIndexOfFirstMatchMask(AArch64MacroAssembler, Register, Register)}.
     * @param inverse if true, find the first non-matching byte instead.
     * @param interleavedInstructions optional argument to emit interleaved instructions.
     */
    protected static void calcIndexOfFirstMatch(AArch64MacroAssembler asm, Register dst, Register vec1, Register vec2, Register vecMask, boolean inverse, Runnable interleavedInstructions) {
        /*
         * Using the magic constant set 2 bits in the matching byte(s) that represent its position
         * in the chunk
         */
        if (inverse) {
            asm.neon.bicVVV(FullReg, vec1, vecMask, vec1);
            asm.neon.bicVVV(FullReg, vec2, vecMask, vec2);
        } else {
            asm.neon.andVVV(FullReg, vec1, vec1, vecMask);
            asm.neon.andVVV(FullReg, vec2, vec2, vecMask);
        }
        /* Convert 32-byte to 8-byte representation, 2 bits per byte. */
        /* Reduce from 256 -> 128 bits. */
        asm.neon.addpVVV(FullReg, AArch64ASIMDAssembler.ElementSize.Byte, vec1, vec1, vec2);
        /* Reduce from 128 -> 64 bits. Note vec2's value doesn't matter; only care about vec1. */
        asm.neon.addpVVV(FullReg, AArch64ASIMDAssembler.ElementSize.Byte, vec1, vec1, vec2);
        asm.neon.moveFromIndex(AArch64ASIMDAssembler.ElementSize.DoubleWord, AArch64ASIMDAssembler.ElementSize.DoubleWord, dst, vec1, 0);
        /* moveFromIndex takes 4 cycles, optionally run other things in the meantime */
        interleavedInstructions.run();
        asm.rbit(64, dst, dst);
        asm.clz(64, dst, dst);
    }

    /**
     * Creates the following mask in the data section: {@code [ 0x00 <n times> 0xff <n times> ]}.
     *
     * With this mask, bytes loaded by a vector load aligned to the end of the array can be set to
     * zero with a {@code andVVV} instruction, e.g.:
     *
     * Given an array of 20 bytes, and vectors of 16 bytes, we can load bytes 0-15 with a
     * {@code fldr} instruction aligned to the beginning of the array, and bytes 4-19 with another
     * {@code fldr} instruction aligned to the end of the array, using the address
     * (arrayBasePointer, arrayLength, -16). To avoid processing bytes 4-15 twice, we can zero them
     * in the second vector with this mask and the tail count {@code 20 % 16 = 4}:
     *
     * {@code andVVV vector2, vector2, (maskBasePointer, tailCount)}
     *
     * {@code (maskBasePointer, tailCount)} yields a mask where all lower bytes are {@code 0x00},
     * and exactly the last {@code tailCount} bytes are {@code 0xff}, in this case:
     *
     * {@code [0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0xff 0xff 0xff 0xff] }
     */
    protected DataSection.Data createTailANDMask(CompilationResultBuilder crb, int n) {
        byte[] mask = new byte[n << 1];
        Arrays.fill(mask, n, mask.length, (byte) ~0);
        return writeToDataSection(crb, mask);
    }

    /**
     * Creates the following mask: {@code [0x00 0x01 0x02 ... 0x0f 0xff <n times>]}.
     *
     * This mask can be used with TBL to not only remove duplicate bytes in a vector tail load (see
     * {@link #createTailANDMask(CompilationResultBuilder, int)}, but also move the remaining bytes
     * to the beginning of the vector, as if the vector was right-shifted by {@code 16 - tailCount}
     * bytes.
     */
    protected static DataSection.Data createTailShuffleMask(CompilationResultBuilder crb, int n) {
        byte[] mask = new byte[n + 16];
        for (int i = 0; i < n; i++) {
            mask[i] = (byte) i;
        }
        Arrays.fill(mask, n, mask.length, (byte) ~0);
        return writeToDataSection(crb, mask);
    }

    protected static DataSection.Data writeToDataSection(CompilationResultBuilder crb, byte[] array) {
        int align = crb.dataBuilder.ensureValidDataAlignment((array.length & 15) == 0 ? array.length : (array.length & ~15) + 16);
        ArrayDataPointerConstant arrayConstant = new ArrayDataPointerConstant(array, align);
        return crb.dataBuilder.createSerializableData(arrayConstant, align);
    }
}
