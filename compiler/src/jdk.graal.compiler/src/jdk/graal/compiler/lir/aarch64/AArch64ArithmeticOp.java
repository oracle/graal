/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.ADDSUBTRACT;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.LOGICAL;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.NONE;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.ARMv8ConstantCategory.SHIFT;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;

public enum AArch64ArithmeticOp {
    // TODO At least add and sub *can* be used with SP, so this should be supported
    NEG,
    NEGS,
    NOT,
    ADD(ADDSUBTRACT),
    ADDS(ADDSUBTRACT),
    SUB(ADDSUBTRACT),
    SUBS(ADDSUBTRACT),
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
    SMAX,
    SMIN,
    UMAX,
    UMIN,
    AND(LOGICAL),
    ANDS(LOGICAL),
    OR(LOGICAL),
    XOR(LOGICAL),
    TST(LOGICAL),
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
        ADDSUBTRACT,
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
            assert checkParameters(result, x);
        }

        private static boolean checkParameters(AllocatableValue result, AllocatableValue input) {
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int srcSize = input.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            assert dstSize == 32 || dstSize == 64 : dstSize;
            // input must have at least as many meaningful bits as the dst
            assert dstSize <= srcSize : Assertions.errorMessageContext("result", result, "src", input);
            return true;
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
                case NEGS:
                    masm.subs(size, dst, zr, src);
                    break;
                case FNEG:
                    masm.fneg(size, dst, src);
                    break;
                case NOT:
                    masm.not(size, dst, src);
                    break;
                case ABS:
                    masm.compare(size, src, 0);
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
                    throw GraalError.shouldNotReachHere("op=" + opcode.name()); // ExcludeFromJacocoGeneratedReport
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
            assert checkParameters(op, result, a, b);
        }

        private static boolean checkParameters(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, JavaConstant b) {
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int srcSize = a.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            long immediate = b.asLong();
            switch (op) {
                case ADD:
                case SUB:
                case ADDS:
                case SUBS:
                    assert AArch64MacroAssembler.isAddSubtractImmediate(immediate, true);
                    break;
                case AND:
                case ANDS:
                case OR:
                case XOR:
                case TST:
                    assert AArch64MacroAssembler.isLogicalImmediate(dstSize, immediate);
                    break;
            }
            assert dstSize == 32 || dstSize == 64 : dstSize;
            if (op == AND || op == ANDS) {
                /*
                 * Either the input must have at least as many meaningful bits as the dst or the
                 * immediate is smaller than the src.
                 */
                assert dstSize <= srcSize || (NumUtil.getNbitNumberLong(srcSize) & immediate) == immediate : Assertions.errorMessageContext("dstSize", dstSize, "srcSize", srcSize, "immediate",
                                immediate);
            } else {
                // input must have at least as many meaningful bits as the dst
                assert dstSize <= srcSize : Assertions.errorMessageContext("dstSize", dstSize, "srcSize", srcSize);
            }
            return true;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert op.category != NONE : op.category;
            Register dst = asRegister(result);
            Register src = asRegister(a);
            int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            long immediate = b.asLong();
            switch (op) {
                case ADD:
                    // Don't use asInt() here, since we can't use asInt on a long variable, even
                    // if the constant easily fits as an int.
                    masm.add(size, dst, src, NumUtil.safeToInt(immediate));
                    break;
                case SUB:
                    // Don't use asInt() here, since we can't use asInt on a long variable, even
                    // if the constant easily fits as an int.
                    masm.sub(size, dst, src, NumUtil.safeToInt(immediate));
                    break;
                case ADDS:
                    masm.adds(size, dst, src, NumUtil.safeToInt(immediate));
                    break;
                case SUBS:
                    masm.subs(size, dst, src, NumUtil.safeToInt(immediate));
                    break;
                case AND:
                    // XXX Should this be handled somewhere else?
                    long mask = NumUtil.getNbitNumberLong(size);
                    if ((immediate & mask) == mask) {
                        masm.mov(size, dst, src);
                    } else {
                        masm.and(size, dst, src, immediate);
                    }
                    break;
                case ANDS:
                    masm.ands(size, dst, src, immediate);
                    break;
                case OR:
                    masm.orr(size, dst, src, immediate);
                    break;
                case XOR:
                    masm.eor(size, dst, src, immediate);
                    break;
                case TST:
                    assert dst.equals(zr);
                    masm.tst(size, src, immediate);
                    break;
                case LSL:
                    masm.lsl(size, dst, src, immediate);
                    break;
                case LSR:
                    masm.lsr(size, dst, src, immediate);
                    break;
                case ASR:
                    masm.asr(size, dst, src, immediate);
                    break;
                case ROR:
                    masm.ror(size, dst, src, immediate);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name()); // ExcludeFromJacocoGeneratedReport
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
            assert checkParameters(op, result, a, b);
        }

        private static boolean checkParameters(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src1Size = a.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src2Size = b.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case SMNEGL:
                case SMULL:
                    assert dstSize == 64 && src1Size == 32 && src2Size == 32 : Assertions.errorMessageContext("a", a, "b", b, "dstSize", dstSize, "src2Size", src2Size);
                    break;
                case FADD:
                case FSUB:
                case FMUL:
                case FDIV:
                case FMAX:
                case FMIN:
                    assert dstSize == 32 || dstSize == 64 : Assertions.errorMessageContext("a", a, "b", b, "dstSize", dstSize);
                    // inputs must be same size as output
                    assert dstSize == src1Size && dstSize == src2Size : Assertions.errorMessageContext("a", a, "b", b, "dstSize", dstSize, "src2Size", src2Size);
                    break;
                case LSL:
                case LSR:
                case ASR:
                case ROR:
                    assert dstSize == 32 || dstSize == 64 : Assertions.errorMessageContext("a", a, "b", b, "dstSize", dstSize);
                    // src1 must have at least as many meaningful bits as the dst
                    // src2's size doesn't matter, as it will be clamped
                    assert dstSize <= src1Size : Assertions.errorMessageContext("dstSize", dstSize, "src1Size", src1Size);
                    break;
                default:
                    assert dstSize == 32 || dstSize == 64 : dstSize;
                    // inputs must have at least as many meaningful bits as the dst
                    assert dstSize <= src1Size && dstSize <= src2Size : Assertions.errorMessageContext("dstSize", dstSize, "src2Size", src2Size);
                    break;
            }
            return true;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            Register src1 = asRegister(a);
            Register src2 = asRegister(b);
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
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
                    masm.smull(dst, src1, src2);
                    break;
                case SMNEGL:
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
                case TST:
                    assert dst.equals(zr);
                    masm.tst(dstSize, src1, src2);
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
                case REM:
                    emitRem(masm, dstSize, dst, src1, src2);
                    break;
                case UREM:
                    emitURem(masm, dstSize, dst, src1, src2);
                    break;
                case MULVS:
                    emitMulvs(masm, dstSize, dst, src1, src2);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("op=" + op.name()); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    private static void emitRem(AArch64MacroAssembler masm, int size, Register dst, Register n, Register d) {
        // There is no irem or similar instruction. Instead we use the relation:
        // n % d = n - Floor(n / d) * d if nd >= 0
        // n % d = n - Ceil(n / d) * d else
        // Which is equivalent to n - TruncatingDivision(n, d) * d
        try (AArch64MacroAssembler.ScratchRegister scratch = masm.getScratchRegister()) {
            Register scratchReg = scratch.getRegister();
            masm.sdiv(size, scratchReg, n, d);
            masm.msub(size, dst, scratchReg, d, n);
        }
    }

    private static void emitURem(AArch64MacroAssembler masm, int size, Register dst, Register n, Register d) {
        // There is no irem or similar instruction. Instead we use the relation:
        // n % d = n - Floor(n / d) * d
        // Which is equivalent to n - TruncatingDivision(n, d) * d
        try (AArch64MacroAssembler.ScratchRegister scratch = masm.getScratchRegister()) {
            Register scratchReg = scratch.getRegister();
            masm.udiv(size, scratchReg, n, d);
            masm.msub(size, dst, scratchReg, d, n);
        }
    }

    /**
     * Sets overflow flag according to result of x * y.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stack-pointer.
     * @param x general purpose register. May not be null or stackpointer.
     * @param y general purpose register. May not be null or stackpointer.
     */
    private static void emitMulvs(AArch64MacroAssembler masm, int size, Register dst, Register x, Register y) {
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            switch (size) {
                case 64: {
                    // Be careful with registers: it's possible that x, y, and dst are the same
                    // register.
                    Register temp1 = sc1.getRegister();
                    Register temp2 = sc2.getRegister();
                    masm.mul(64, temp1, x, y);     // Result bits 0..63
                    masm.smulh(64, temp2, x, y);  // Result bits 64..127
                    // Top is pure sign ext
                    masm.subs(64, zr, temp2, temp1, AArch64Assembler.ShiftType.ASR, 63);
                    // Copy all 64 bits of the result into dst
                    masm.mov(64, dst, temp1);
                    masm.mov(temp1, 0x80000000);
                    // Develop 0 (EQ), or 0x80000000 (NE)
                    masm.csel(32, temp1, temp1, zr, ConditionFlag.NE);
                    masm.compare(32, temp1, 1);
                    // 0x80000000 - 1 => VS
                    break;
                }
                case 32: {
                    Register temp1 = sc1.getRegister();
                    masm.smaddl(temp1, x, y, zr);
                    // Copy the low 32 bits of the result into dst
                    masm.mov(32, dst, temp1);
                    masm.subs(64, zr, temp1, temp1, AArch64Assembler.ExtendType.SXTW, 0);
                    // NE => overflow
                    masm.mov(temp1, 0x80000000);
                    // Develop 0 (EQ), or 0x80000000 (NE)
                    masm.csel(32, temp1, temp1, zr, ConditionFlag.NE);
                    masm.compare(32, temp1, 1);
                    // 0x80000000 - 1 => VS
                    break;
                }
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
            assert checkParameters(result, src1, src2);
        }

        private static boolean checkParameters(AllocatableValue result, AllocatableValue src1, AllocatableValue src2) {
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src1Size = src1.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src2Size = src2.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            assert dstSize == 32 || dstSize == 64 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size);
            assert dstSize == src1Size && dstSize == src2Size : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size);
            return true;
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
                    throw GraalError.shouldNotReachHere("op=" + op.name()); // ExcludeFromJacocoGeneratedReport
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
            assert checkParameters(op, result, src1, src2, extendType, shiftAmt);
        }

        private static boolean checkParameters(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue src1, AllocatableValue src2, AArch64Assembler.ExtendType extendType, int shiftAmt) {
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src1Size = src1.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src2Size = src2.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            assert op == ADD || op == SUB : op;
            assert shiftAmt >= 0 && shiftAmt <= 4 : Assertions.errorMessageContext("shiftAmt", shiftAmt);
            assert dstSize == 32 || dstSize == 64 : Assertions.errorMessageContext("dstSize", dstSize);
            assert dstSize <= src1Size : Assertions.errorMessageContext("dstSize", dstSize, "src1Size", src1Size);
            switch (extendType) {
                case UXTB:
                case SXTB:
                    assert src2Size >= 8 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size);
                    break;
                case UXTH:
                case SXTH:
                    assert src2Size >= 16 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size);
                    break;
                case UXTW:
                case SXTW:
                    assert src2Size >= 32 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size);
                    break;
                case UXTX:
                case SXTX:
                    assert dstSize == 64 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "dstSize", dstSize);
                    assert src2Size == 64 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "dstSize", dstSize);
                    break;
            }
            return true;
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
                    throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
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
            assert checkParameters(op, result, src1, src2, src3);
        }

        private static boolean checkParameters(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue src1, AllocatableValue src2, AllocatableValue src3) {
            int dstSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src1Size = src1.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src2Size = src2.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int src3Size = src3.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (op) {
                case SMADDL:
                case SMSUBL:
                    assert dstSize == 64 && src3Size == 64 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size",
                                    src3Size);
                    assert src1Size == 32 && src2Size == 32 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size",
                                    src3Size);
                    break;
                case MADD:
                case MSUB:
                    assert dstSize == 64 || dstSize == 32 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size", src3Size);
                    assert dstSize <= src1Size && dstSize <= src2Size && dstSize <= src3Size : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size,
                                    "src3", src3, "src3Size", src3Size);
                    break;
                case FMADD:
                    assert dstSize == 64 || dstSize == 32 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size", src3Size);
                    assert dstSize == src1Size && dstSize == src2Size && dstSize == src3Size : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size,
                                    "src3", src3, "src3Size", src3Size);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
            }
            return true;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert src1.getPlatformKind() == src2.getPlatformKind() : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2);
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
                    assert dstSize == 64 && src3Size == 64 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size",
                                    src3Size);
                    assert src1Size == 32 && src2Size == 32 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size",
                                    src3Size);
                    masm.smaddl(asRegister(result), asRegister(src1), asRegister(src2), asRegister(src3));
                    break;
                case SMSUBL:
                    assert dstSize == 64 && src3Size == 64 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size",
                                    src3Size);
                    assert src1Size == 32 && src2Size == 32 : Assertions.errorMessageContext("result", result, "src1", src1, "src2", src2, "src2Size", src2Size, "src3", src3, "src3Size",
                                    src3Size);
                    masm.smsubl(asRegister(result), asRegister(src1), asRegister(src2), asRegister(src3));
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
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
                    throw GraalError.shouldNotReachHere("op=" + opcode.name()); // ExcludeFromJacocoGeneratedReport
            }
        }

    }

    public static AArch64LIRInstruction generateASIMDBinaryInstruction(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
        switch (op) {
            case ASR:
            case LSR:
                return new ASIMDBinaryTwoStepOp(op, result, a, b);
        }
        return new ASIMDBinaryOp(op, result, a, b);
    }

    /**
     * For ASIMD, some arithmetic operations require generating two instructions and eagerly use the
     * result register. In this case, the input registers must be marked as ALIVE to guarantee they
     * are not reused for the result reg.
     */
    public static class ASIMDBinaryTwoStepOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDBinaryTwoStepOp> TYPE = LIRInstructionClass.create(ASIMDBinaryTwoStepOp.class);

        @Opcode private final AArch64ArithmeticOp op;
        @Def({REG}) protected AllocatableValue result;
        /*
         * Note currently it is only necessary to keep the first register alive. However, this may
         * change if more instructions are added here.
         */
        @Alive({REG}) protected AllocatableValue a;
        @Use({REG}) protected AllocatableValue b;

        ASIMDBinaryTwoStepOp(AArch64ArithmeticOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
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
                    throw GraalError.shouldNotReachHere("op=" + op.name()); // ExcludeFromJacocoGeneratedReport

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
                case TST:
                    masm.neon.cmtstVVV(size, eSize, dst, src1, src2);
                    break;
                case MNEG:
                    /* First perform multiply. */
                    masm.neon.mulVVV(size, eSize, dst, src1, src2);
                    /* Next negate value. */
                    masm.neon.negVV(size, eSize, dst, dst);
                    break;
                case SMAX:
                    masm.neon.smaxVVV(size, eSize, dst, src1, src2);
                    break;
                case SMIN:
                    masm.neon.sminVVV(size, eSize, dst, src1, src2);
                    break;
                case UMAX:
                    masm.neon.umaxVVV(size, eSize, dst, src1, src2);
                    break;
                case UMIN:
                    masm.neon.uminVVV(size, eSize, dst, src1, src2);
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
                    throw GraalError.shouldNotReachHere("op=" + op.name()); // ExcludeFromJacocoGeneratedReport
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
                    throw GraalError.shouldNotReachHere("op=" + op.name()); // ExcludeFromJacocoGeneratedReport
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
                    throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
            }
        }
    }
}
