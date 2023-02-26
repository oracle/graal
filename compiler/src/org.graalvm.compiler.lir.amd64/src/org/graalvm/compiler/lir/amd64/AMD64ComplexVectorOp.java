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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.rcx;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.TZCNT;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Less;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.YMM;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Base class for AMD64 LIR instruction using AVX CPU features.
 */
public abstract class AMD64ComplexVectorOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ComplexVectorOp> TYPE = LIRInstructionClass.create(AMD64ComplexVectorOp.class);

    protected final AVXSize vectorSize;
    protected final EnumSet<CPUFeature> runtimeCheckedCPUFeatures;
    protected final TargetDescription targetDescription;

    public AMD64ComplexVectorOp(LIRInstructionClass<? extends AMD64ComplexVectorOp> c, LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, AVXSize maxUsedVectorSize) {
        super(c);

        this.targetDescription = tool.target();
        this.runtimeCheckedCPUFeatures = runtimeCheckedCPUFeatures;
        AVXSize maxSupportedVectorSize = (AVXSize) tool.getMaxVectorSize(runtimeCheckedCPUFeatures);
        assert isXMMOrGreater(maxUsedVectorSize) && isXMMOrGreater(maxSupportedVectorSize);

        if (maxUsedVectorSize.fitsWithin(maxSupportedVectorSize)) {
            this.vectorSize = maxUsedVectorSize;
        } else {
            this.vectorSize = maxSupportedVectorSize;
        }
    }

    private static boolean isXMMOrGreater(AVXSize size) {
        return size == AVXSize.XMM || size == YMM || size == AVXSize.ZMM;
    }

    protected AMD64Kind getVectorKind(JavaKind valueKind) {
        switch (vectorSize) {
            case XMM:
                switch (valueKind) {
                    case Byte:
                        return AMD64Kind.V128_BYTE;
                    case Char:
                        return AMD64Kind.V128_WORD;
                    case Int:
                        return AMD64Kind.V128_DWORD;
                    case Long:
                        return AMD64Kind.V128_QWORD;
                    case Float:
                        return AMD64Kind.V128_SINGLE;
                    case Double:
                        return AMD64Kind.V128_DOUBLE;
                    default:
                        throw GraalError.shouldNotReachHere("Unsupported base value kind.");
                }
            case YMM:
                switch (valueKind) {
                    case Byte:
                        return AMD64Kind.V256_BYTE;
                    case Char:
                        return AMD64Kind.V256_WORD;
                    case Int:
                        return AMD64Kind.V256_DWORD;
                    case Long:
                        return AMD64Kind.V256_QWORD;
                    case Float:
                        return AMD64Kind.V256_SINGLE;
                    case Double:
                        return AMD64Kind.V256_DOUBLE;
                    default:
                        throw GraalError.shouldNotReachHere("Unsupported base value kind.");
                }
            case ZMM:
                switch (valueKind) {
                    case Byte:
                        return AMD64Kind.V512_BYTE;
                    case Char:
                        return AMD64Kind.V512_WORD;
                    case Int:
                        return AMD64Kind.V512_DWORD;
                    case Long:
                        return AMD64Kind.V512_QWORD;
                    case Float:
                        return AMD64Kind.V512_SINGLE;
                    case Double:
                        return AMD64Kind.V512_DOUBLE;
                    default:
                        throw GraalError.shouldNotReachHere("Unsupported base value kind.");
                }
            default:
                throw GraalError.shouldNotReachHere("Unsupported vector size.");
        }
    }

    protected AMD64Kind getVectorKind(Stride stride) {
        switch (vectorSize) {
            case XMM:
                switch (stride) {
                    case S1:
                        return AMD64Kind.V128_BYTE;
                    case S2:
                        return AMD64Kind.V128_WORD;
                    case S4:
                        return AMD64Kind.V128_DWORD;
                    case S8:
                        return AMD64Kind.V128_QWORD;
                    default:
                        throw GraalError.shouldNotReachHere("Unsupported base value kind.");
                }
            case YMM:
                switch (stride) {
                    case S1:
                        return AMD64Kind.V256_BYTE;
                    case S2:
                        return AMD64Kind.V256_WORD;
                    case S4:
                        return AMD64Kind.V256_DWORD;
                    case S8:
                        return AMD64Kind.V256_QWORD;
                    default:
                        throw GraalError.shouldNotReachHere("Unsupported base value kind.");
                }
            case ZMM:
                switch (stride) {
                    case S1:
                        return AMD64Kind.V512_BYTE;
                    case S2:
                        return AMD64Kind.V512_WORD;
                    case S4:
                        return AMD64Kind.V512_DWORD;
                    case S8:
                        return AMD64Kind.V512_QWORD;
                    default:
                        throw GraalError.shouldNotReachHere("Unsupported base value kind.");
                }
            default:
                throw GraalError.shouldNotReachHere("Unsupported vector size.");
        }
    }

    protected Value[] allocateTempRegisters(LIRGeneratorTool tool, AMD64Kind kind, int n) {
        Value[] temp = new Value[n];
        for (int i = 0; i < temp.length; i++) {
            temp[i] = tool.newVariable(LIRKind.value(kind));
        }
        return temp;
    }

    protected Value[] allocateVectorRegisters(LIRGeneratorTool tool, JavaKind valueKind, int n) {
        return allocateVectorRegisters(tool, LIRKind.value(getVectorKind(valueKind)), n);
    }

    protected Value[] allocateVectorRegisters(LIRGeneratorTool tool, Stride stride, int n) {
        return allocateVectorRegisters(tool, LIRKind.value(getVectorKind(stride)), n);
    }

    protected Value[] allocateVectorRegisters(LIRGeneratorTool tool, LIRKind kind, int n) {
        Value[] vectors = new Value[n];
        for (int i = 0; i < vectors.length; i++) {
            vectors[i] = tool.newVariable(kind);
        }
        return vectors;
    }

    public static boolean supports(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, CPUFeature requiredFeature, CPUFeature... additionalRequiredFeatures) {
        return supports(target, runtimeCheckedCPUFeatures, EnumSet.of(requiredFeature, additionalRequiredFeatures));
    }

    public static boolean supports(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, EnumSet<CPUFeature> requiredFeatures) {
        return runtimeCheckedCPUFeatures != null && runtimeCheckedCPUFeatures.containsAll(requiredFeatures) || ((AMD64) target.arch).getFeatures().containsAll(requiredFeatures);
    }

    public static boolean supportsAVX512VLBW(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures) {
        return supports(target, runtimeCheckedCPUFeatures, CPUFeature.AVX512VL) && supports(target, runtimeCheckedCPUFeatures, CPUFeature.AVX512BW);
    }

    protected boolean supports(CPUFeature cpuFeature) {
        return supports(targetDescription, runtimeCheckedCPUFeatures, cpuFeature);
    }

    protected boolean supportsAVX2AndYMM() {
        return YMM.fitsWithin(vectorSize) && supports(CPUFeature.AVX2);
    }

    protected boolean supportsAVX512VLBWAndZMM() {
        return AVXSize.ZMM.fitsWithin(vectorSize) && supportsAVX512VLBW(targetDescription, runtimeCheckedCPUFeatures);
    }

    protected boolean supportsBMI2() {
        return supports(CPUFeature.BMI2);
    }

    protected boolean supportsTZCNT() {
        return supports(AMD64.CPUFeature.BMI1) && ((AMD64) targetDescription.arch).getFlags().contains(AMD64.Flag.UseCountTrailingZerosInstruction);
    }

    protected void bsfq(AMD64MacroAssembler masm, Register dst, Register src) {
        if (supportsTZCNT()) {
            TZCNT.emit(masm, QWORD, dst, src);
        } else {
            masm.bsfq(dst, src);
        }
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }

    static int elementsPerVector(AVXSize size, Stride stride) {
        return size.getBytes() >> stride.log2;
    }

    /**
     * Load the tail of an XMM or YMM vector loop into a XMM/YMM register, via vector loads aligned
     * to the end of the array. The preceding loop must have had at least one iteration, or this
     * will read out of bounds. After this operation, {@code vecArray} contains all tail byes (0-15
     * bytes on XMM, 0-31 bytes on YMM) as if it was loaded directly from address {@code (arr)}.
     * <p>
     * Kills all parameter registers except {@code arr}.
     *
     * @param xmmTailShuffleMask result of {@link #createXMMTailShuffleMask(int)}.
     */
    protected void loadTailIntoYMMOrdered(CompilationResultBuilder crb, AMD64MacroAssembler asm, Stride stride, DataSection.Data xmmTailShuffleMask,
                    Register arr, Register lengthTail, Register vecArray, Register tmp, Register vecTmp1, Register vecTmp2) {
        if (supportsAVX2AndYMM()) {
            Label lessThan16 = new Label();
            Label done = new Label();
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(xmmTailShuffleMask));
            // load last 16 bytes
            asm.movdqu(XMM, vecArray, new AMD64Address(arr, lengthTail, stride, -XMM.getBytes()));
            // if we're using YMM vectors and the tail is greater than 16 bytes, load the tail's
            // first 16 bytes and prepend them to the shifted vector
            asm.cmpqAndJcc(lengthTail, elementsPerVector(XMM, stride), Less, lessThan16, true);
            asm.movdqu(XMM, vecTmp1, new AMD64Address(arr));
            asm.negq(lengthTail);
            // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
            // memory parameters
            asm.movdqu(XMM, vecTmp2, new AMD64Address(tmp, lengthTail, stride, XMM.getBytes() * 2));
            // shuffle the tail vector such that its content effectively gets left-shifted by
            // 16 - lengthTail bytes
            asm.pshufb(XMM, vecArray, vecTmp2);
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, YMM, vecArray, vecArray, vecTmp1, 0x02);
            asm.jmpb(done);
            asm.bind(lessThan16);
            asm.negq(lengthTail);
            // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
            // memory parameters
            asm.movdqu(XMM, vecTmp2, new AMD64Address(tmp, lengthTail, stride, XMM.getBytes()));
            // shuffle the tail vector such that its content effectively gets right-shifted by
            // 16 - lengthTail bytes
            asm.pshufb(XMM, vecArray, vecTmp2);
            asm.bind(done);
        } else {
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(xmmTailShuffleMask));
            // load last 16 bytes
            asm.movdqu(XMM, vecArray, new AMD64Address(arr, lengthTail, stride, -XMM.getBytes()));
            asm.negq(lengthTail);
            // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
            // memory parameters
            asm.movdqu(XMM, vecTmp2, new AMD64Address(tmp, lengthTail, stride, XMM.getBytes()));
            // shuffle the tail vector such that its content effectively gets right-shifted by
            // 16 - lengthTail bytes
            asm.pshufb(XMM, vecArray, vecTmp2);
        }
    }

    /**
     * Load an array of 16-31 bytes into a YMM register.
     */
    protected void loadLessThan32IntoYMMOrdered(CompilationResultBuilder crb, AMD64MacroAssembler asm, Stride stride, DataSection.Data xmmTailShuffleMask,
                    Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        GraalError.guarantee(supportsAVX2AndYMM(), "AVX2 and YMM support required");
        // array is between 16 and 31 bytes long, load it into a YMM register via two XMM loads
        asm.movdqu(XMM, vecTmp1, new AMD64Address(arr));
        asm.movdqu(XMM, vecArray, new AMD64Address(arr, lengthTail, stride, -XMM.getBytes()));
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(xmmTailShuffleMask));
        asm.negq(lengthTail);
        // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
        // memory parameters
        asm.movdqu(XMM, vecTmp2, new AMD64Address(tmp, lengthTail, stride, XMM.getBytes() * 2));
        // shuffle the tail vector such that its content effectively gets right-shifted by
        // 16 - lengthTail bytes
        asm.pshufb(XMM, vecArray, vecTmp2);
        AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, YMM, vecArray, vecArray, vecTmp1, 0x02);
    }

    /**
     * Load an array of 16-31 bytes into a YMM register, with a gap in the middle, e.g. the
     * resulting vector of an array of length 20 is:
     *
     * <pre>
     * {@code
     * [<array bytes 0-15> <0x00, 12 times> <array bytes 16-19>]
     * }
     * </pre>
     */
    protected void loadLessThan32IntoYMMUnordered(CompilationResultBuilder crb, AMD64MacroAssembler asm, Stride stride, DataSection.Data maskTail,
                    Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        GraalError.guarantee(supportsAVX2AndYMM(), "AVX2 and YMM support required");
        asm.movdqu(XMM, vecArray, new AMD64Address(arr));
        asm.movdqu(XMM, vecTmp1, new AMD64Address(arr, lengthTail, stride, -XMM.getBytes()));
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
        asm.pandU(YMM, vecTmp1, new AMD64Address(tmp, lengthTail, stride), vecTmp2);
        AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, YMM, vecArray, vecArray, vecTmp1, 0x02);
    }

    /**
     * Load an array of 8-15 bytes into a XMM register.
     */
    protected static void loadLessThan16IntoXMMOrdered(CompilationResultBuilder crb, AMD64MacroAssembler asm,
                    Stride stride, Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        // array is between 8 and 15 bytes long, load it into a YMM register via two QWORD loads
        asm.movdq(vecArray, new AMD64Address(arr));
        asm.movdq(vecTmp1, new AMD64Address(arr, lengthTail, stride, -8));
        asm.leaq(tmp, getMaskOnce(crb, createXMMTailShuffleMask(8), XMM.getBytes() * 2));
        asm.negq(lengthTail);
        asm.movdqu(XMM, vecTmp2, new AMD64Address(tmp, lengthTail, stride, XMM.getBytes()));
        asm.pshufb(XMM, vecTmp1, vecTmp2);
        asm.movlhps(vecArray, vecTmp1);
    }

    /**
     * Load an array of 8-15 bytes into a XMM register, with a gap in the middle.
     */
    protected void loadLessThan16IntoXMMUnordered(CompilationResultBuilder crb, AMD64MacroAssembler asm, Stride stride, DataSection.Data maskTail,
                    Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        // array is between 8 and 15 bytes long, load it into a YMM register via two QWORD loads
        asm.movdq(vecArray, new AMD64Address(arr));
        asm.movdq(vecTmp1, new AMD64Address(arr, lengthTail, stride, -8));
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
        asm.pandU(vectorSize, vecTmp1, new AMD64Address(tmp, lengthTail, stride, supportsAVX2AndYMM() ? XMM.getBytes() : 0), vecTmp2);
        asm.movlhps(vecArray, vecTmp1);
    }

    /**
     * Load an array of 4-7 bytes into a XMM register.
     */
    protected static void loadLessThan8IntoXMMOrdered(AMD64MacroAssembler asm, Stride stride, Register arr, Register lengthTail, Register vecArray, Register tmp, Register tmp2) {
        GraalError.guarantee(stride.log2 < 2, "stride of more than 2 bytes not supported");
        // array is between 4 and 7 bytes long, load it into a YMM register via two DWORD loads
        asm.movl(tmp, new AMD64Address(arr));
        asm.movl(tmp2, new AMD64Address(arr, lengthTail, stride, -4));
        // compute tail length
        asm.andq(lengthTail, 3 >> stride.log2);
        // convert byte count to bit count
        asm.shlq(lengthTail, 3 + stride.log2);
        GraalError.guarantee(lengthTail.equals(rcx), "lengthTail must be RCX, as it is used as an implicit argument to shlq");
        // shift second vector to the left by tailCount bits
        asm.shlq(tmp2);
        // shift to the right and left by 32 to zero the lower 4 bytes
        asm.shrq(tmp2, 32);
        asm.shlq(tmp2, 32);
        asm.orq(tmp, tmp2);
        asm.movdq(vecArray, tmp);
    }

    /**
     * Load an array of 4-7 bytes into a XMM register, with a gap in the middle.
     */
    protected void loadLessThan8IntoXMMUnordered(CompilationResultBuilder crb, AMD64MacroAssembler asm, Stride stride, DataSection.Data maskTail,
                    Register arr, Register lengthTail, Register vecArray, Register tmp, Register tmp2) {
        // array is between 4 and 7 bytes long, load it into a YMM register via two DWORD loads
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
        asm.movl(tmp2, new AMD64Address(arr, lengthTail, stride, -4));
        asm.andq(tmp2, new AMD64Address(tmp, lengthTail, stride, (supportsAVX2AndYMM() ? XMM.getBytes() : 0) + 8));
        asm.movl(tmp, new AMD64Address(arr));
        asm.shlq(tmp2, 32);
        asm.orq(tmp, tmp2);
        asm.movdq(vecArray, tmp);
    }

    protected void loadMask(CompilationResultBuilder crb, AMD64MacroAssembler asm, Stride stride, Register vecMask, int value) {
        asm.movdqu(vectorSize, vecMask, getMaskOnce(crb, createMaskBytes(value, stride)));
    }

    protected static AMD64Address getMaskOnce(CompilationResultBuilder crb, byte[] mask) {
        return getMaskOnce(crb, mask, mask.length);
    }

    protected static AMD64Address getMaskOnce(CompilationResultBuilder crb, byte[] mask, int alignLength) {
        int align = crb.dataBuilder.ensureValidDataAlignment(alignLength);
        return (AMD64Address) crb.recordDataReferenceInCode(mask, align);
    }

    protected DataSection.Data createMask(CompilationResultBuilder crb, Stride stride, int value) {
        return writeToDataSection(crb, createMaskBytes(value, stride));
    }

    /**
     * Creates the following mask in the data section: {@code [ 0x00 <n times> 0xff <n times> ]},
     * where {@code n} is the AVX vector size in bytes.
     *
     * With this mask, bytes loaded by a vector load aligned to the end of the array can be set to
     * zero with a PAND instruction, e.g.:
     *
     * Given an array of 20 bytes, and XMM vectors of 16 bytes, we can load bytes 0-15 with a MOVDQU
     * instruction aligned to the beginning of the array, and bytes 4-19 with another MOVDQU
     * instruction aligned to the end of the array, using the address (arrayBasePointer,
     * arrayLength, -16). To avoid processing bytes 4-15 twice, we can zero them in the second
     * vector with this mask and the tail count {@code 20 % 16 = 4}:
     *
     * {@code PAND vector2, (maskBasePointer, tailCount)}
     *
     * {@code (maskBasePointer, tailCount)} yields a mask where all lower bytes are {@code 0x00},
     * and exactly the last {@code tailCount} bytes are {@code 0xff}, in this case:
     *
     * {@code [0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0xff 0xff 0xff 0xff] }
     */
    protected DataSection.Data createTailMask(CompilationResultBuilder crb, Stride stride) {
        byte[] mask = new byte[vectorSize.getBytes() * 2];
        for (int i = elementsPerVector(vectorSize, stride); i < elementsPerVector(vectorSize, stride) * 2; i++) {
            writeValue(mask, stride, i, ~0);
        }
        return writeToDataSection(crb, mask);
    }

    /**
     * Creates the following mask: {@code [0x00 0x01 0x02 ... 0x0f 0xff <n times>]}, where {@code n}
     * is the AVX vector size in bytes.
     *
     * This mask can be used with PSHUFB to not only remove duplicate bytes in a vector tail load
     * (see {@link #createTailMask(CompilationResultBuilder, Stride)}, but also move the remaining
     * bytes to the beginning of the vector, as if the vector was right-shifted by
     * {@code 16 - tailCount} bytes.
     *
     * This only works on XMM vectors; to achieve the same on a YMM vector additional instructions
     * are needed.
     */
    protected static byte[] createXMMTailShuffleMask(int length) {
        byte[] mask = new byte[XMM.getBytes() + length];
        for (int i = 0; i < length; i++) {
            mask[i] = (byte) i;
        }
        Arrays.fill(mask, length, XMM.getBytes() + length, (byte) ~0);
        return mask;
    }

    protected byte[] createMaskBytes(int value, Stride stride) {
        byte[] mask = new byte[vectorSize.getBytes()];
        for (int i = 0; i < elementsPerVector(vectorSize, stride); i++) {
            writeValue(mask, stride, i, value);
        }
        return mask;
    }

    protected static DataSection.Data writeToDataSection(CompilationResultBuilder crb, byte[] array) {
        int align = crb.dataBuilder.ensureValidDataAlignment(array.length);
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
