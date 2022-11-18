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

import java.nio.ByteOrder;
import java.util.Arrays;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
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
    protected DataSection.Data createTailANDMask(CompilationResultBuilder crb, Stride stride, int n) {
        byte[] mask = new byte[n << 1];
        for (int i = n >> stride.log2; i < mask.length >> stride.log2; i++) {
            writeValue(mask, stride, i, ~0);
        }
        return writeToDataSection(crb, mask);
    }

    /**
     * Creates the following mask: {@code [0x00 0x01 0x02 ... 0x0f 0xff <n times>]}.
     *
     * This mask can be used with TBL to not only remove duplicate bytes in a vector tail load (see
     * {@link #createTailANDMask(CompilationResultBuilder, Stride, int)}, but also move the
     * remaining bytes to the beginning of the vector, as if the vector was right-shifted by
     * {@code 16 - tailCount} bytes.
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

    private static void writeValue(byte[] array, Stride stride, int index, int value) {
        int i = index << stride.log2;
        if (stride == Stride.S1) {
            array[i] = (byte) value;
            return;
        }
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            if (stride == Stride.S2) {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
            } else {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
                array[i + 2] = (byte) (value >> 16);
                array[i + 3] = (byte) (value >> 24);
            }
        } else {
            if (stride == Stride.S2) {
                array[i] = (byte) (value >> 8);
                array[i + 1] = (byte) value;
            } else {
                array[i] = (byte) (value >> 24);
                array[i + 1] = (byte) (value >> 16);
                array[i + 2] = (byte) (value >> 8);
                array[i + 3] = (byte) value;
            }
        }
    }
}
