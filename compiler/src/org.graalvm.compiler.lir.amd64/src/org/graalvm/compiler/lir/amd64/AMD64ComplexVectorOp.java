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

import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.TZCNT;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;

import java.util.EnumSet;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
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
        return size == AVXSize.XMM || size == AVXSize.YMM || size == AVXSize.ZMM;
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

    public static boolean supports(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, CPUFeature cpuFeature) {
        return runtimeCheckedCPUFeatures != null && runtimeCheckedCPUFeatures.contains(cpuFeature) || ((AMD64) target.arch).getFeatures().contains(cpuFeature);
    }

    public static boolean supportsAVX512VLBW(TargetDescription target, EnumSet<CPUFeature> runtimeCheckedCPUFeatures) {
        return supports(target, runtimeCheckedCPUFeatures, CPUFeature.AVX512VL) && supports(target, runtimeCheckedCPUFeatures, CPUFeature.AVX512BW);
    }

    protected boolean supports(CPUFeature cpuFeature) {
        return supports(targetDescription, runtimeCheckedCPUFeatures, cpuFeature);
    }

    protected boolean supportsAVX2AndYMM() {
        return AVXSize.YMM.fitsWithin(vectorSize) && supports(CPUFeature.AVX2);
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
}
