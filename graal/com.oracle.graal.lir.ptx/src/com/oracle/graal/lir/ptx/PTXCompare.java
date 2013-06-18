/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.ptx;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

public enum PTXCompare {
    ICMP, LCMP, ACMP, FCMP, DCMP;

    public static class CompareOp extends PTXLIRInstruction {

        @Opcode private final PTXCompare opcode;
        @Use({REG, STACK, CONST}) protected Value x;
        @Use({REG, STACK, CONST}) protected Value y;
        private final Condition condition;

        public CompareOp(PTXCompare opcode, Condition condition, Value x, Value y) {
            this.opcode = opcode;
            this.condition = condition;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            emit(tasm, masm, opcode, condition, x, y);
        }

        @Override
        protected void verify() {
            super.verify();
            assert (name().startsWith("I") && x.getKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int) || (name().startsWith("L") && x.getKind() == Kind.Long && y.getKind() == Kind.Long) ||
                            (name().startsWith("A") && x.getKind() == Kind.Object && y.getKind() == Kind.Object) ||
                            (name().startsWith("F") && x.getKind() == Kind.Float && y.getKind() == Kind.Float) || (name().startsWith("D") && x.getKind() == Kind.Double && y.getKind() == Kind.Double);
        }
    }

    public static void emit(TargetMethodAssembler tasm, PTXAssembler masm, PTXCompare opcode, Condition condition, Value x, Value y) {
        if (isConstant(x)) {
            switch (opcode) {
                case ICMP:
                    emitCompareConstReg(masm, condition, tasm.asIntConst(x), asIntReg(y));
                    break;
                case FCMP:
                    emitCompareConstReg(masm, condition, tasm.asFloatConst(x), asFloatReg(y));
                    break;
                case DCMP:
                    emitCompareConstReg(masm, condition, tasm.asDoubleConst(x), asDoubleReg(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(y)) {
            Register a = asIntReg(x);
            int b = tasm.asIntConst(y);
            switch (opcode) {
                case ICMP:
                    emitCompareRegConst(masm, condition, a, b);
                    break;
                case ACMP:
                    if (((Constant) y).isNull()) {
                        switch (condition) {
                            case EQ:
                                masm.setp_eq_s32(a, b);
                                break;
                            case NE:
                                masm.setp_ne_s32(a, b);
                                break;
                            default:
                                throw GraalInternalError.shouldNotReachHere();
                        }
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Only null object constants are allowed in comparisons");
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                case ICMP:
                    emitCompareRegReg(masm, condition, asIntReg(x), asIntReg(y));
                    break;
                case LCMP:
                    emitCompareRegReg(masm, condition, asLongReg(x), asLongReg(y));
                    break;
                case FCMP:
                    emitCompareRegReg(masm, condition, asFloatReg(x), asFloatReg(y));
                    break;
                case DCMP:
                    emitCompareRegReg(masm, condition, asDoubleReg(x), asDoubleReg(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: "  + opcode);
            }
        }
    }

    private static void emitCompareConstReg(PTXAssembler masm, Condition condition, float a, Register b) {
        switch (condition) {
        case EQ:
            masm.setp_eq_f32(a, b);
            break;
        case NE:
            masm.setp_ne_f32(a, b);
            break;
        case LT:
            masm.setp_lt_f32(a, b);
            break;
        case LE:
            masm.setp_le_f32(a, b);
            break;
        case GT:
            masm.setp_gt_f32(a, b);
            break;
        case GE:
            masm.setp_ge_f32(a, b);
            break;
        default:
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void emitCompareConstReg(PTXAssembler masm, Condition condition, double a, Register b) {
        switch (condition) {
        case EQ:
            masm.setp_eq_f64(a, b);
            break;
        case NE:
            masm.setp_ne_f64(a, b);
            break;
        case LT:
            masm.setp_lt_f64(a, b);
            break;
        case LE:
            masm.setp_le_f64(a, b);
            break;
        case GT:
            masm.setp_gt_f64(a, b);
            break;
        case GE:
            masm.setp_ge_f64(a, b);
            break;
        default:
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void emitCompareConstReg(PTXAssembler masm, Condition condition, int a, Register b) {
        switch (condition) {
            case EQ:
                masm.setp_eq_s32(a, b);
                break;
            case NE:
                masm.setp_ne_s32(a, b);
                break;
            case LT:
                masm.setp_lt_s32(a, b);
                break;
            case LE:
                masm.setp_le_s32(a, b);
                break;
            case GT:
                masm.setp_gt_s32(a, b);
                break;
            case GE:
                masm.setp_ge_s32(a, b);
                break;
            case AT:
                masm.setp_gt_u32(a, b);
                break;
            case AE:
                masm.setp_ge_u32(a, b);
                break;
            case BT:
                masm.setp_lt_u32(a, b);
                break;
            case BE:
                masm.setp_le_u32(a, b);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void emitCompareRegConst(PTXAssembler masm, Condition condition, Register a, int b) {
        switch (condition) {
            case EQ:
                masm.setp_eq_s32(a, b);
                break;
            case NE:
                masm.setp_ne_s32(a, b);
                break;
            case LT:
                masm.setp_lt_s32(a, b);
                break;
            case LE:
                masm.setp_le_s32(a, b);
                break;
            case GT:
                masm.setp_gt_s32(a, b);
                break;
            case GE:
                masm.setp_ge_s32(a, b);
                break;
            case AT:
                masm.setp_gt_u32(a, b);
                break;
            case AE:
                masm.setp_ge_u32(a, b);
                break;
            case BT:
                masm.setp_lt_u32(a, b);
                break;
            case BE:
                masm.setp_le_u32(a, b);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void emitCompareRegReg(PTXAssembler masm, Condition condition, Register a, Register b) {
        switch (condition) {
            case EQ:
                masm.setp_eq_s32(a, b);
                break;
            case NE:
                masm.setp_ne_s32(a, b);
                break;
            case LT:
                masm.setp_lt_s32(a, b);
                break;
            case LE:
                masm.setp_le_s32(a, b);
                break;
            case GT:
                masm.setp_gt_s32(a, b);
                break;
            case GE:
                masm.setp_ge_s32(a, b);
                break;
            case AT:
                masm.setp_gt_u32(a, b);
                break;
            case AE:
                masm.setp_ge_u32(a, b);
                break;
            case BT:
                masm.setp_lt_u32(a, b);
                break;
            case BE:
                masm.setp_le_u32(a, b);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
