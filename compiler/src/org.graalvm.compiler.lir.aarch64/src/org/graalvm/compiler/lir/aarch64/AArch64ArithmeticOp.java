/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.ARITHMETIC;
import static org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.LOGICAL;
import static org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.NONE;
import static org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.SHIFT;

import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;

public enum AArch64ArithmeticOp {
    // TODO At least add and sub *can* be used with SP, so this should be supported
    NEG,
    NOT,
    ADD(ARITHMETIC),
    ADDS(ARITHMETIC),
    SUB(ARITHMETIC),
    SUBS(ARITHMETIC),
    MUL,
    MULVS,
    MNEG,
    DIV,
    SMULH,
    UMULH,
    SMULL,
    SMNEGL,
    MADD,
    MSUB,
    FMADD,
    FMSUB,
    SMADDL,
    SMSUBL,
    REM,
    UDIV,
    UREM,
    AND(LOGICAL),
    ANDS(LOGICAL),
    OR(LOGICAL),
    XOR(LOGICAL),
    BIC,
    ORN,
    EON,
    LSL(SHIFT),
    LSR(SHIFT),
    ASR(SHIFT),
    ROR(SHIFT),
    ABS,
    FADD,
    FSUB,
    FMUL,
    FDIV,
    FNEG,
    FABS,
    FRINTM,
    FRINTN,
    FRINTP,
    FRINTZ,
    FMAX,
    FMIN,
    FSQRT;

    /**
     * Specifies what constants can be used directly without having to be loaded into a register
     * with the given instruction.
     */
    public enum ARMv8ConstantCategory {
        NONE,
        LOGICAL,
        ARITHMETIC,
        SHIFT
    }

    public final ARMv8ConstantCategory category;

    AArch64ArithmeticOp(ARMv8ConstantCategory category) {
        this.category = category;
    }

    AArch64ArithmeticOp() {
        this(NONE);
    }

    public static class UnaryOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<UnaryOp> TYPE = LIRInstructionClass.create(UnaryOp.class);

        @Opcode private final AArch64ArithmeticOp opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;

        public UnaryOp(AArch64ArithmeticOp opcode, AllocatableValue result, AllocatableValue x) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            Register src = asRegister(x);
            int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (opcode) {
                case NEG:
                    masm.sub(size, dst, zr, src);
                    break;
                case FNEG:
                    masm.fneg(size, dst, src);
                    break;
                case NOT:
                    masm.not(size, dst, src);
                    break;
                case ABS:
                    masm.cmp(size, src, 0);
                    masm.csneg(size, dst, src, src, ConditionFlag.GE);
                    break;
                case FABS:
                    masm.fabs(size, dst, src);
                    break;
                case FRINTM:
                    masm.frintm(size, dst, src);
                    break;
                case FRINTN:
                    masm.frintn(size, dst, src);
                    break;
                case FRINTP:
                    masm.frintp(size, dst, src);
                    break;
                case FRINTZ:
                    masm.frintz(size, dst, src);
                    break;
                case FSQRT:
                    masm.fsqrt(size, dst, src);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + opcode.name());
            }
        }
    }

    public static class BinaryConstOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<BinaryConstOp> TYPE = LIRInstructionClass.create(BinaryConstOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue a;
        private final JavaConstant b;

        public BinaryConstOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, JavaConstant b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert op.category != NONE;
            Register dst = asRegister(result);
            Register src = asRegister(a);
            int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case ADD:
                    // Don't use asInt() here, since we can't use asInt on a long variable, even
                    // if the constant easily fits as an int.
                    assert AArch64MacroAssembler.isArithmeticImmediate(b.asLong());
                    masm.add(size, dst, src, (int) b.asLong());
                    break;
                case SUB:
                    // Don't use asInt() here, since we can't use asInt on a long variable, even
                    // if the constant easily fits as an int.
                    assert AArch64MacroAssembler.isArithmeticImmediate(b.asLong());
                    masm.sub(size, dst, src, (int) b.asLong());
                    break;
                case ADDS:
                    assert AArch64MacroAssembler.isArithmeticImmediate(b.asLong());
                    masm.adds(size, dst, src, (int) b.asLong());
                    break;
                case SUBS:
                    assert AArch64MacroAssembler.isArithmeticImmediate(b.asLong());
                    masm.subs(size, dst, src, (int) b.asLong());
                    break;
                case AND:
                    // XXX Should this be handled somewhere else?
                    if (size == 32 && b.asLong() == 0xFFFF_FFFFL) {
                        masm.mov(size, dst, src);
                    } else {
                        masm.and(size, dst, src, b.asLong());
                    }
                    break;
                case ANDS:
                    masm.ands(size, dst, src, b.asLong());
                    break;
                case OR:
                    masm.orr(size, dst, src, b.asLong());
                    break;
                case XOR:
                    masm.eor(size, dst, src, b.asLong());
                    break;
                case LSL:
                    masm.lsl(size, dst, src, b.asLong());
                    break;
                case LSR:
                    masm.lsr(size, dst, src, b.asLong());
                    break;
                case ASR:
                    masm.asr(size, dst, src, b.asLong());
                    break;
                case ROR:
                    masm.ror(size, dst, src, (int) b.asLong());
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name());
            }
        }
    }

    public static class BinaryOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<BinaryOp> TYPE = LIRInstructionClass.create(BinaryOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue a;
        @Use({REG}) protected AllocatableValue b;

        public BinaryOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            Register src1 = asRegister(a);
            Register src2 = asRegister(b);
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src1Size = a.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src2Size = b.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case ADD:
                    masm.add(dstSize, dst, src1, src2);
                    break;
                case ADDS:
                    masm.adds(dstSize, dst, src1, src2);
                    break;
                case SUB:
                    masm.sub(dstSize, dst, src1, src2);
                    break;
                case SUBS:
                    masm.subs(dstSize, dst, src1, src2);
                    break;
                case MUL:
                    masm.mul(dstSize, dst, src1, src2);
                    break;
                case UMULH:
                    masm.umulh(dstSize, dst, src1, src2);
                    break;
                case SMULH:
                    masm.smulh(dstSize, dst, src1, src2);
                    break;
                case MNEG:
                    masm.mneg(dstSize, dst, src1, src2);
                    break;
                case SMULL:
                    assert dstSize == 64;
                    assert src1Size == 32 && src2Size == 32;
                    masm.smull(dst, src1, src2);
                    break;
                case SMNEGL:
                    assert dstSize == 64;
                    assert src1Size == 32 && src2Size == 32;
                    masm.smnegl(dst, src1, src2);
                    break;
                case DIV:
                    masm.sdiv(dstSize, dst, src1, src2);
                    break;
                case UDIV:
                    masm.udiv(dstSize, dst, src1, src2);
                    break;
                case AND:
                    masm.and(dstSize, dst, src1, src2);
                    break;
                case ANDS:
                    masm.ands(dstSize, dst, src1, src2);
                    break;
                case OR:
                    masm.orr(dstSize, dst, src1, src2);
                    break;
                case XOR:
                    masm.eor(dstSize, dst, src1, src2);
                    break;
                case BIC:
                    masm.bic(dstSize, dst, src1, src2);
                    break;
                case ORN:
                    masm.orn(dstSize, dst, src1, src2);
                    break;
                case EON:
                    masm.eon(dstSize, dst, src1, src2);
                    break;
                case LSL:
                    masm.lsl(dstSize, dst, src1, src2);
                    break;
                case LSR:
                    masm.lsr(dstSize, dst, src1, src2);
                    break;
                case ASR:
                    masm.asr(dstSize, dst, src1, src2);
                    break;
                case ROR:
                    masm.ror(dstSize, dst, src1, src2);
                    break;
                case FADD:
                    masm.fadd(dstSize, dst, src1, src2);
                    break;
                case FSUB:
                    masm.fsub(dstSize, dst, src1, src2);
                    break;
                case FMUL:
                    masm.fmul(dstSize, dst, src1, src2);
                    break;
                case FDIV:
                    masm.fdiv(dstSize, dst, src1, src2);
                    break;
                case FMAX:
                    masm.fmax(dstSize, dst, src1, src2);
                    break;
                case FMIN:
                    masm.fmin(dstSize, dst, src1, src2);
                    break;
                case MULVS:
                    masm.mulvs(dstSize, dst, src1, src2);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name());
            }
        }
    }

    /**
     * Class used for instructions that have to reuse one of their arguments. This only applies to
     * the remainder instructions at the moment, since we have to compute n % d using rem = n -
     * TruncatingDivision(n, d) * d
     *
     * TODO (das) Replace the remainder nodes in the LIR.
     */
    public static class BinaryCompositeOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<BinaryCompositeOp> TYPE = LIRInstructionClass.create(BinaryCompositeOp.class);
        @Opcode private final AArch64ArithmeticOp op;
        @Def({REG}) protected AllocatableValue result;
        @Alive({REG}) protected AllocatableValue a;
        @Alive({REG}) protected AllocatableValue b;

        public BinaryCompositeOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            Register src1 = asRegister(a);
            Register src2 = asRegister(b);
            int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case REM:
                    masm.rem(size, dst, src1, src2);
                    break;
                case UREM:
                    masm.urem(size, dst, src1, src2);
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    public static class BinaryShiftOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<BinaryShiftOp> TYPE = LIRInstructionClass.create(BinaryShiftOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def(REG) protected AllocatableValue result;
        @Use(REG) protected AllocatableValue src1;
        @Use(REG) protected AllocatableValue src2;
        private final AArch64MacroAssembler.ShiftType shiftType;
        private final int shiftAmt;

        /**
         * <code>result = src1 <op> src2 <shiftType> <shiftAmt></code>.
         */
        public BinaryShiftOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue src1, AllocatableValue src2,
                        AArch64MacroAssembler.ShiftType shiftType, int shiftAmt) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.src1 = src1;
            this.src2 = src2;
            this.shiftType = shiftType;
            this.shiftAmt = shiftAmt;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case ADD:
                    masm.add(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                case SUB:
                    masm.sub(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                case AND:
                    masm.and(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                case OR:
                    masm.orr(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                case XOR:
                    masm.eor(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                case BIC:
                    masm.bic(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                case ORN:
                    masm.orn(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                case EON:
                    masm.eon(size, asRegister(result), asRegister(src1), asRegister(src2), shiftType, shiftAmt);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name());
            }
        }
    }

    public static class ExtendedAddSubShiftOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ExtendedAddSubShiftOp> TYPE = LIRInstructionClass.create(ExtendedAddSubShiftOp.class);
        @Opcode private final AArch64ArithmeticOp op;
        @Def(REG) protected AllocatableValue result;
        @Use(REG) protected AllocatableValue src1;
        @Use(REG) protected AllocatableValue src2;
        private final AArch64Assembler.ExtendType extendType;
        private final int shiftAmt;

        /**
         * Computes <code>result = src1 + extendType(src2) << shiftAmt</code>.
         *
         * @param extendType defines how src2 is extended to the same size as src1.
         * @param shiftAmt must be in range 0 to 4.
         */
        public ExtendedAddSubShiftOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue src1, AllocatableValue src2, AArch64Assembler.ExtendType extendType, int shiftAmt) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.src1 = src1;
            this.src2 = src2;
            this.extendType = extendType;
            this.shiftAmt = shiftAmt;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case ADD:
                    masm.add(size, asRegister(result), asRegister(src1), asRegister(src2), extendType, shiftAmt);
                    break;
                case SUB:
                    masm.sub(size, asRegister(result), asRegister(src1), asRegister(src2), extendType, shiftAmt);
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    public static class MultiplyAddSubOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<MultiplyAddSubOp> TYPE = LIRInstructionClass.create(MultiplyAddSubOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def(REG) protected AllocatableValue result;
        @Use(REG) protected AllocatableValue src1;
        @Use(REG) protected AllocatableValue src2;
        @Use(REG) protected AllocatableValue src3;

        /**
         * Computes <code>result = src3 +/- src1 * src2</code>.
         */
        public MultiplyAddSubOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue src1, AllocatableValue src2, AllocatableValue src3) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.src1 = src1;
            this.src2 = src2;
            this.src3 = src3;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert src1.getPlatformKind() == src2.getPlatformKind();
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src1Size = src1.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src2Size = src2.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src3Size = src3.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case MADD:
                    masm.madd(dstSize, asRegister(result), asRegister(src1), asRegister(src2), asRegister(src3));
                    break;
                case MSUB:
                    masm.msub(dstSize, asRegister(result), asRegister(src1), asRegister(src2), asRegister(src3));
                    break;
                case FMADD:
                    masm.fmadd(dstSize, asRegister(result), asRegister(src1), asRegister(src2), asRegister(src3));
                    break;
                case SMADDL:
                    assert dstSize == 64 && src3Size == 64;
                    assert src1Size == 32 && src2Size == 32;
                    masm.smaddl(asRegister(result), asRegister(src1), asRegister(src2), asRegister(src3));
                    break;
                case SMSUBL:
                    assert dstSize == 64 && src3Size == 64;
                    assert src1Size == 32 && src2Size == 32;
                    masm.smsubl(asRegister(result), asRegister(src1), asRegister(src2), asRegister(src3));
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    public static class ASIMDUnaryOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDUnaryOp> TYPE = LIRInstructionClass.create(ASIMDUnaryOp.class);

        @Opcode private final AArch64ArithmeticOp opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;

        public ASIMDUnaryOp(AArch64ArithmeticOp opcode, AllocatableValue result, AllocatableValue x) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register src = asRegister(x);

            switch (opcode) {
                case NOT:
                    masm.neon.notVV(size, dst, src);
                    break;
                case NEG:
                    masm.neon.negVV(size, eSize, dst, src);
                    break;
                case FNEG:
                    masm.neon.fnegVV(size, eSize, dst, src);
                    break;
                case ABS:
                    masm.neon.absVV(size, eSize, dst, src);
                    break;
                case FABS:
                    masm.neon.fabsVV(size, eSize, dst, src);
                    break;
                case FSQRT:
                    masm.neon.fsqrtVV(size, eSize, dst, src);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + opcode.name());
            }
        }

    }

    public static AArch64LIRInstruction generateASIMDBinaryInstruction(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
        switch (op) {
            case ASR:
            case LSR:
                return new ASIMDTwoStepBinaryOp(op, result, a, b);
        }
        return new ASIMDBinaryOp(op, result, a, b);
    }

    /**
     * For ASIMD, some arithmetic operations require generating two instructions and eagerly use the
     * result register. In this case, the input registers must be marked as ALIVE to guarantee they
     * are not reused for the result reg.
     */
    public static class ASIMDTwoStepBinaryOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDTwoStepBinaryOp> TYPE = LIRInstructionClass.create(ASIMDTwoStepBinaryOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def({REG}) protected AllocatableValue result;
        /*
         * Note currently it is only necessary to keep the first register alive. However, this may
         * change if more instructions are added here.
         */
        @Alive({REG}) protected AllocatableValue a;
        @Use({REG}) protected AllocatableValue b;

        ASIMDTwoStepBinaryOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register src1 = asRegister(a);
            Register src2 = asRegister(b);
            switch (op) {
                case LSR:
                    /*
                     * On AArch64 right shifts are actually left shifts by a negative value.
                     */
                    masm.neon.negVV(size, eSize, dst, src2);
                    masm.neon.ushlVVV(size, eSize, dst, src1, dst);
                    break;
                case ASR:
                    /*
                     * On AArch64 right shifts are actually left shifts by a negative value.
                     */
                    masm.neon.negVV(size, eSize, dst, src2);
                    masm.neon.sshlVVV(size, eSize, dst, src1, dst);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name());

            }
        }
    }

    public static class ASIMDBinaryOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDBinaryOp> TYPE = LIRInstructionClass.create(ASIMDBinaryOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue a;
        @Use({REG}) protected AllocatableValue b;

        ASIMDBinaryOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register src1 = asRegister(a);
            Register src2 = asRegister(b);
            switch (op) {
                case AND:
                    masm.neon.andVVV(size, dst, src1, src2);
                    break;
                case OR:
                    masm.neon.orrVVV(size, dst, src1, src2);
                    break;
                case BIC:
                    masm.neon.bicVVV(size, dst, src1, src2);
                    break;
                case ORN:
                    masm.neon.ornVVV(size, dst, src1, src2);
                    break;
                case XOR:
                    masm.neon.eorVVV(size, dst, src1, src2);
                    break;
                case ADD:
                    masm.neon.addVVV(size, eSize, dst, src1, src2);
                    break;
                case SUB:
                    masm.neon.subVVV(size, eSize, dst, src1, src2);
                    break;
                case LSL:
                    masm.neon.ushlVVV(size, eSize, dst, src1, src2);
                    break;
                case MUL:
                    masm.neon.mulVVV(size, eSize, dst, src1, src2);
                    break;
                case MNEG:
                    /* First perform multiply. */
                    masm.neon.mulVVV(size, eSize, dst, src1, src2);
                    /* Next negate value. */
                    masm.neon.negVV(size, eSize, dst, dst);
                    break;
                case FADD:
                    masm.neon.faddVVV(size, eSize, dst, src1, src2);
                    break;
                case FSUB:
                    masm.neon.fsubVVV(size, eSize, dst, src1, src2);
                    break;
                case FMUL:
                    masm.neon.fmulVVV(size, eSize, dst, src1, src2);
                    break;
                case FDIV:
                    masm.neon.fdivVVV(size, eSize, dst, src1, src2);
                    break;
                case FMAX:
                    masm.neon.fmaxVVV(size, eSize, dst, src1, src2);
                    break;
                case FMIN:
                    masm.neon.fminVVV(size, eSize, dst, src1, src2);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name());
            }
        }
    }

    public static class ASIMDBinaryConstOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDBinaryConstOp> TYPE = LIRInstructionClass.create(ASIMDBinaryConstOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue a;
        private final JavaConstant b;

        public ASIMDBinaryConstOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, JavaConstant b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register src = asRegister(a);

            long immValue = b.asLong();
            int clampedShift = AArch64MacroAssembler.clampShiftAmt(eSize == ElementSize.DoubleWord ? 64 : 32, immValue);
            switch (op) {
                case OR:
                    masm.neon.moveVV(size, dst, src);
                    masm.neon.orrVI(size, eSize, dst, immValue);
                    break;
                case BIC:
                    masm.neon.moveVV(size, dst, src);
                    masm.neon.bicVI(size, eSize, dst, immValue);
                    break;
                case LSL:
                    masm.neon.shlVVI(size, eSize, dst, src, clampedShift);
                    break;
                case LSR:
                    masm.neon.ushrVVI(size, eSize, dst, src, clampedShift);
                    break;
                case ASR:
                    masm.neon.sshrVVI(size, eSize, dst, src, clampedShift);
                    break;

                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name());
            }
        }

    }

    public static class ASIMDMultiplyAddSubOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDMultiplyAddSubOp> TYPE = LIRInstructionClass.create(ASIMDMultiplyAddSubOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def(REG) protected AllocatableValue result;
        /*
         * a & b cannot be assigned the same reg as the result reg, as c is moved into the result
         * reg before a & b are used.
         */
        @Alive(REG) protected AllocatableValue a;
        @Alive(REG) protected AllocatableValue b;
        @Use(REG) protected AllocatableValue c;

        /**
         * Computes <code>result = c +/- a * b</code>.
         */
        public ASIMDMultiplyAddSubOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b, AllocatableValue c) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register src1 = asRegister(a);
            Register src2 = asRegister(b);

            /*
             * for ASIMD fused instructions, the addition/subtraction is performed directly on the
             * dst register.
             */
            masm.neon.moveVV(size, dst, asRegister(c));
            switch (op) {
                case MADD:
                    masm.neon.mlaVVV(size, eSize, dst, src1, src2);
                    break;
                case MSUB:
                    masm.neon.mlsVVV(size, eSize, dst, src1, src2);
                    break;
                case FMADD:
                    masm.neon.fmlaVVV(size, eSize, dst, src1, src2);
                    break;
                case FMSUB:
                    masm.neon.fmlsVVV(size, eSize, dst, src1, src2);
                    break;
                case SMADDL:
                    masm.neon.smlalVVV(eSize, dst, src1, src2);
                    break;
                case SMSUBL:
                    masm.neon.smlslVVV(eSize, dst, src1, src2);
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }
}
