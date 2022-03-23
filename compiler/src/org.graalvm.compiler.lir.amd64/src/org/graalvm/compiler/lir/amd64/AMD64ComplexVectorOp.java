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

import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

/**
 * Base class for AMD64 LIR instruction using AVX CPU features.
 */
public abstract class AMD64ComplexVectorOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ComplexVectorOp> TYPE = LIRInstructionClass.create(AMD64ComplexVectorOp.class);

    protected final AVXSize vectorSize;

    public AMD64ComplexVectorOp(LIRInstructionClass<? extends AMD64ComplexVectorOp> c, AVXSize maxUsedVectorSize, AVXSize maxSupportedVectorSize) {
        super(c);

        assert isXMMOrGreater(maxUsedVectorSize) && isXMMOrGreater(maxSupportedVectorSize);

        if (maxUsedVectorSize.fitsWithin(maxSupportedVectorSize)) {
            this.vectorSize = maxUsedVectorSize;
        } else {
            this.vectorSize = maxSupportedVectorSize;
        }
    }

    public static boolean useAVX512(LIRGeneratorTool tool) {
        return supports(tool.target(), CPUFeature.AVX512VL) && supports(tool.target(), CPUFeature.AVX512BW) && supports(tool.target(), CPUFeature.BMI2);
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

    public static boolean supports(TargetDescription target, CPUFeature cpuFeature) {
        return ((AMD64) target.arch).getFeatures().contains(cpuFeature);
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }
}
