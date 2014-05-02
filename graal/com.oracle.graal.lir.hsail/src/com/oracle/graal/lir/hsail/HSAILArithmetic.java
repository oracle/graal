/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.hsail;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

/**
 * Defines arithmetic instruction nodes.
 */
public enum HSAILArithmetic {
    ABS,
    CALL,
    CEIL,
    FDIV,
    FLOOR,
    FREM,
    DADD,
    DDIV,
    DMUL,
    DNEG,
    DREM,
    DSUB,
    FADD,
    FMUL,
    FNEG,
    FSUB,
    IADD,
    IAND,
    ICARRY,
    IDIV,
    IMAX,
    IMIN,
    IMUL,
    INEG,
    INOT,
    IOR,
    IREM,
    ISHL,
    ISHR,
    ISUB,
    IUADD,
    IUCARRY,
    IUDIV,
    IUMAX,
    IUMIN,
    IUMUL,
    IUREM,
    IUSHR,
    IUSUB,
    IXOR,
    LADD,
    LAND,
    LCARRY,
    LDIV,
    LMAX,
    LMIN,
    LMUL,
    LNEG,
    LNOT,
    LOR,
    LREM,
    LSHL,
    LSHR,
    LSUB,
    LUADD,
    LUCARRY,
    LUDIV,
    LUMAX,
    LUMIN,
    LUMUL,
    LUREM,
    LUSHR,
    LUSUB,
    LXOR,
    OADD,
    RINT,
    SQRT,
    UNDEF;

    public static class ConvertOp extends HSAILLIRInstruction {
        private final String from;
        private final String to;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;

        public ConvertOp(AllocatableValue result, AllocatableValue x, String to, String from) {
            this.from = from;
            this.to = to;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitConvert(result, x, to, from);
        }
    }

    public static class Op1Reg extends HSAILLIRInstruction {
        @Opcode private final HSAILArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, CONST}) protected Value x;

        public Op1Reg(HSAILArithmetic opcode, Value result, Value x) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            emit(crb, masm, opcode, result, x, null);
        }
    }

    public static class Op2Reg extends HSAILLIRInstruction {
        @Opcode private final HSAILArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public Op2Reg(HSAILArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static class ShiftOp extends HSAILLIRInstruction {
        @Opcode private final HSAILArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;

        public ShiftOp(HSAILArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            emit(crb, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, x);
            assert y.getKind().getStackKind() == Kind.Int;
        }
    }

    public static class DivOp extends HSAILLIRInstruction {
        @Opcode private final HSAILArithmetic opcode;
        @Def protected Value result;
        @Use protected Value x;
        @Alive protected Value y;
        @State protected LIRFrameState state;

        public DivOp(HSAILArithmetic opcode, Value result, Value x, Value y, LIRFrameState state) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            emit(crb, masm, opcode, result, y, state);
        }

        @Override
        protected void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    @SuppressWarnings("unused")
    protected static void emit(CompilationResultBuilder crb, HSAILAssembler masm, HSAILArithmetic opcode, Value result) {
        switch (opcode) {
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    /**
     * Emits the HSAIL code for an arithmetic operation taking one input parameter.
     *
     * @param crb the CompilationResultBuilder
     * @param masm the HSAIL assembler
     * @param opcode the opcode of the arithmetic operation
     * @param dst the destination
     * @param src the source parameter
     * @param info structure that stores the LIRFrameState. Used for exception handling.
     */

    public static void emit(CompilationResultBuilder crb, HSAILAssembler masm, HSAILArithmetic opcode, Value dst, Value src, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isRegister(src)) {
            switch (opcode) {
                case ABS:
                    masm.emit("abs", dst, src);
                    break;
                case CEIL:
                    masm.emit("ceil", dst, src);
                    break;
                case FLOOR:
                    masm.emit("floor", dst, src);
                    break;
                case RINT:
                    masm.emit("rint", dst, src);
                    break;
                case SQRT:
                    masm.emit("sqrt", dst, src);
                    break;
                case UNDEF:
                    masm.undefined("undefined node");
                    break;
                case CALL:
                    masm.undefined("undefined node CALL");
                    break;
                case INOT:
                case LNOT:
                    masm.emitForceBitwise("not", dst, src);
                    break;
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG:
                    masm.emit("neg", dst, src);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    public static void emit(CompilationResultBuilder crb, HSAILAssembler masm, HSAILArithmetic opcode, Value dst, Value src1, Value src2, LIRFrameState info) {
        /**
         * First check if one of src1 or src2 is an AddressValue. If it is, convert the address to a
         * register using an lda instruction. We can just reuse the eventual dst register for this.
         */
        if (src1 instanceof HSAILAddressValue) {
            assert (!(src2 instanceof HSAILAddressValue));
            masm.emitLda(dst, ((HSAILAddressValue) src1).toAddress());
            emit(crb, masm, opcode, dst, dst, src2, info);
            return;
        } else if (src2 instanceof HSAILAddressValue) {
            assert (!(src1 instanceof HSAILAddressValue));
            masm.emitLda(dst, ((HSAILAddressValue) src2).toAddress());
            emit(crb, masm, opcode, dst, src1, dst, info);
            return;
        }
        int exceptionOffset = -1;
        switch (opcode) {
            case IADD:
            case LADD:
            case DADD:
            case FADD:
            case OADD:
                masm.emit("add", dst, src1, src2);
                break;
            case ISUB:
            case LSUB:
            case DSUB:
            case FSUB:
                masm.emit("sub", dst, src1, src2);
                break;
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
            case LUMUL:
                masm.emit("mul", dst, src1, src2);
                break;
            case IDIV:
            case LDIV:
            case FDIV:
            case DDIV:
                masm.emit("div", dst, src1, src2);
                break;
            case IMAX:
            case LMAX:
                masm.emit("max", dst, src1, src2);
                break;
            case IMIN:
            case LMIN:
                masm.emit("min", dst, src1, src2);
                break;
            case ISHL:
            case LSHL:
                masm.emit("shl", dst, src1, src2);
                break;
            case ISHR:
            case LSHR:
                masm.emit("shr", dst, src1, src2);
                break;
            case IUSHR:
            case LUSHR:
                masm.emitForceUnsigned("shr", dst, src1, src2);
                break;
            case IAND:
            case LAND:
                masm.emitForceBitwise("and", dst, src1, src2);
                break;
            case IXOR:
            case LXOR:
                masm.emitForceBitwise("xor", dst, src1, src2);
                break;
            case IOR:
            case LOR:
                masm.emitForceBitwise("or", dst, src1, src2);
                break;
            case IREM:
            case LREM:
                masm.emit("rem", dst, src1, src2);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        if (info != null) {
            assert exceptionOffset != -1;
            crb.recordImplicitException(exceptionOffset, info);
        }
    }

    private static void verifyKind(HSAILArithmetic opcode, Value result, Value x, Value y) {
        assert (opcode.name().startsWith("I") && result.getKind() == Kind.Int && x.getKind().getStackKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int) ||
                        (opcode.name().startsWith("L") && result.getKind() == Kind.Long && x.getKind() == Kind.Long && y.getKind() == Kind.Long) ||
                        (opcode.name().startsWith("LU") && result.getKind() == Kind.Long && x.getKind() == Kind.Long && y.getKind() == Kind.Int) ||
                        (opcode.name().startsWith("F") && result.getKind() == Kind.Float && x.getKind() == Kind.Float && y.getKind() == Kind.Float) ||
                        (opcode.name().startsWith("D") && result.getKind() == Kind.Double && x.getKind() == Kind.Double && y.getKind() == Kind.Double) ||
                        (opcode.name().startsWith("O") && result.getKind() == Kind.Object && x.getKind() == Kind.Object && (y.getKind() == Kind.Int || y.getKind() == Kind.Long));
    }
}
