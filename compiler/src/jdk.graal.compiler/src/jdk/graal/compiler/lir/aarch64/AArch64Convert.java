/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public class AArch64Convert {

    public static class SignExtendOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<SignExtendOp> TYPE = LIRInstructionClass.create(SignExtendOp.class);

        @Def protected AllocatableValue resultValue;
        @Use protected AllocatableValue inputValue;
        private final int fromBits;
        private final int toBits;

        public SignExtendOp(AllocatableValue resultValue, AllocatableValue inputValue, int fromBits, int toBits) {
            super(TYPE);
            this.resultValue = resultValue;
            this.inputValue = inputValue;
            this.fromBits = fromBits;
            this.toBits = toBits;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register result = asRegister(resultValue);
            Register input = asRegister(inputValue);
            masm.sxt(toBits <= 32 ? 32 : 64, fromBits, result, input);
        }
    }

    public static final class FloatConvertOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<FloatConvertOp> TYPE = LIRInstructionClass.create(
                        FloatConvertOp.class);

        private final FloatConvert op;
        @Def protected AllocatableValue resultValue;
        @Use protected AllocatableValue inputValue;

        public FloatConvertOp(FloatConvert op, AllocatableValue resultValue, AllocatableValue inputValue) {
            super(TYPE);
            this.op = op;
            this.resultValue = resultValue;
            this.inputValue = inputValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int fromSize = inputValue.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int toSize = resultValue.getPlatformKind().getSizeInBytes() * Byte.SIZE;

            Register result = asRegister(resultValue);
            Register input = asRegister(inputValue);
            switch (op) {
                case F2I:
                case D2I:
                case F2L:
                case D2L:
                    masm.fcvtzs(toSize, fromSize, result, input);
                    break;
                case F2UI:
                case D2UI:
                case F2UL:
                case D2UL:
                    masm.fcvtzu(toSize, fromSize, result, input);
                    break;
                case I2F:
                case I2D:
                case L2F:
                case L2D:
                    masm.scvtf(toSize, fromSize, result, input);
                    break;
                case UI2F:
                case UI2D:
                case UL2F:
                case UL2D:
                    masm.ucvtf(toSize, fromSize, result, input);
                    break;
                case D2F:
                case F2D:
                    masm.fcvt(toSize, fromSize, result, input);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    public static class ASIMDNarrowOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDNarrowOp> TYPE = LIRInstructionClass.create(ASIMDNarrowOp.class);

        @Def protected AllocatableValue resultValue;
        @Use protected AllocatableValue inputValue;

        public ASIMDNarrowOp(AllocatableValue resultValue, AllocatableValue inputValue) {
            super(TYPE);
            this.resultValue = resultValue;
            this.inputValue = inputValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ElementSize finalESize = ElementSize.fromKind(resultValue.getPlatformKind());
            ElementSize currentESize = ElementSize.fromKind(inputValue.getPlatformKind());

            Register result = asRegister(resultValue);
            Register input = asRegister(inputValue);
            if (currentESize == finalESize) {
                /* register move */
                masm.neon.moveVV(ASIMDSize.fromVectorKind(resultValue.getPlatformKind()), result, input);
            } else {
                do {
                    currentESize = currentESize.narrow();
                    masm.neon.xtnVV(currentESize, result, input);
                    /* After first iteration, the input reg should be the result reg */
                    input = result;
                } while (currentESize != finalESize);
            }
        }
    }

    public static class ASIMDSignExtendOp extends AArch64LIRInstruction {

        private static final LIRInstructionClass<ASIMDSignExtendOp> TYPE = LIRInstructionClass.create(ASIMDSignExtendOp.class);

        @Def protected AllocatableValue resultValue;
        @Use protected AllocatableValue inputValue;

        public ASIMDSignExtendOp(AllocatableValue resultValue, AllocatableValue inputValue) {
            super(TYPE);
            this.resultValue = resultValue;
            this.inputValue = inputValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ElementSize finalESize = ElementSize.fromKind(resultValue.getPlatformKind());
            ElementSize currentESize = ElementSize.fromKind(inputValue.getPlatformKind());

            Register result = asRegister(resultValue);
            Register input = asRegister(inputValue);
            if (currentESize == finalESize) {
                /* register move */
                masm.neon.moveVV(ASIMDSize.fromVectorKind(resultValue.getPlatformKind()), result, input);
            } else {
                do {
                    masm.neon.sxtlVV(currentESize, result, input);
                    currentESize = currentESize.expand();
                    /* After first iteration, the input reg should be the result reg */
                    input = result;
                } while (currentESize != finalESize);
            }
        }
    }

    public static class ASIMDZeroExtendOp extends AArch64LIRInstruction {

        private static final LIRInstructionClass<ASIMDZeroExtendOp> TYPE = LIRInstructionClass.create(ASIMDZeroExtendOp.class);

        @Def protected AllocatableValue resultValue;
        @Use protected AllocatableValue inputValue;

        public ASIMDZeroExtendOp(AllocatableValue resultValue, AllocatableValue inputValue) {
            super(TYPE);
            this.resultValue = resultValue;
            this.inputValue = inputValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ElementSize finalESize = ElementSize.fromKind(resultValue.getPlatformKind());
            ElementSize currentESize = ElementSize.fromKind(inputValue.getPlatformKind());

            Register result = asRegister(resultValue);
            Register input = asRegister(inputValue);
            if (currentESize == finalESize) {
                /* register move */
                masm.neon.moveVV(ASIMDSize.fromVectorKind(resultValue.getPlatformKind()), result, input);
            } else {
                do {
                    masm.neon.uxtlVV(currentESize, result, input);
                    currentESize = currentESize.expand();
                    /* After first iteration, the input reg should be the result reg */
                    input = result;
                } while (currentESize != finalESize);
            }
        }

    }

    public static class ASIMDFloatConvertOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDFloatConvertOp> TYPE = LIRInstructionClass.create(ASIMDFloatConvertOp.class);

        private final FloatConvert op;
        @Def protected AllocatableValue resultValue;
        @Use protected AllocatableValue inputValue;

        public ASIMDFloatConvertOp(FloatConvert op, AllocatableValue resultValue, AllocatableValue inputValue) {
            super(TYPE);
            this.op = op;
            this.resultValue = resultValue;
            this.inputValue = inputValue;
        }

        private boolean verifyConversionSizes(ElementSize dstESize, ElementSize srcESize) {
            switch (op) {
                case F2I:
                case I2F:
                case F2UI:
                case UI2F:
                    assert srcESize == ElementSize.Word && dstESize == ElementSize.Word : Assertions.errorMessage(srcESize, dstESize);
                    break;
                case F2D:
                case I2D:
                case UI2D:
                    assert srcESize == ElementSize.Word && dstESize == ElementSize.DoubleWord : Assertions.errorMessage(srcESize, dstESize);
                    break;
                case D2F:
                case D2I:
                case D2UI:
                    assert srcESize == ElementSize.DoubleWord && dstESize == ElementSize.Word : Assertions.errorMessage(srcESize, dstESize);
                    break;
                case D2L:
                case L2D:
                case D2UL:
                case UL2D:
                    assert srcESize == ElementSize.DoubleWord && dstESize == ElementSize.DoubleWord : Assertions.errorMessage(srcESize, dstESize);
                    break;
            }
            return true;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(resultValue.getPlatformKind());
            ElementSize dstESize = ElementSize.fromKind(resultValue.getPlatformKind());
            ElementSize srcESize = ElementSize.fromKind(inputValue.getPlatformKind());
            assert verifyConversionSizes(dstESize, srcESize);

            Register result = asRegister(resultValue);
            Register input = asRegister(inputValue);
            switch (op) {
                case F2I:
                case D2L:
                    masm.neon.fcvtzsVV(size, srcESize, result, input);
                    break;
                case F2UI:
                case D2UL:
                    masm.neon.fcvtzuVV(size, srcESize, result, input);
                    break;
                case I2F:
                case L2D:
                    masm.neon.scvtfVV(size, srcESize, result, input);
                    break;
                case UI2F:
                case UL2D:
                    masm.neon.ucvtfVV(size, srcESize, result, input);
                    break;
                case D2F:
                    masm.neon.fcvtnVV(srcESize, result, input);
                    break;
                case F2D:
                    masm.neon.fcvtlVV(srcESize, result, input);
                    break;
                case I2D:
                    /* First convert int to long, then to double */
                    masm.neon.sxtlVV(srcESize, result, input);
                    masm.neon.scvtfVV(size, ElementSize.fromKind(resultValue.getPlatformKind()), result, result);
                    break;
                case UI2D:
                    /* First unsigned extend int to long, then unsigned convert to double */
                    masm.neon.uxtlVV(srcESize, result, input);
                    masm.neon.ucvtfVV(size, ElementSize.fromKind(resultValue.getPlatformKind()), result, result);
                    break;
                case D2I:
                    /* First convert double to long, then saturating extract int */
                    masm.neon.fcvtzsVV(size, srcESize, result, input);
                    masm.neon.sqxtnVV(dstESize, result, result);
                    break;
                case D2UI:
                    /* First convert double to unsigned long, then saturating extract uint */
                    masm.neon.fcvtzuVV(size, srcESize, result, input);
                    masm.neon.uqxtnVV(dstESize, result, result);
                    break;
                /*
                 * It is not possible to handle these conversions correctly without losing fidelity.
                 */
                case F2L:
                case L2F:
                case F2UL:
                case UL2F:
                default:
                    throw GraalError.shouldNotReachHere("Unsupported conversion requested."); // ExcludeFromJacocoGeneratedReport
            }

        }
    }
}
