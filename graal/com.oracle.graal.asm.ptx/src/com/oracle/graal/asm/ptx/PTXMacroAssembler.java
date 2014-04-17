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
package com.oracle.graal.asm.ptx;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;

public class PTXMacroAssembler extends PTXAssembler {

    public PTXMacroAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    public static class LoadAddr extends LoadStoreFormat {

        public LoadAddr(PTXStateSpace space, Variable dst, Variable src1, Value src2) {
            super(space, dst, src1, src2);
        }

        public void emit(PTXMacroAssembler masm) {
            String ldAddrStr = "ld." + space.getStateName();
            if (Unsafe.ADDRESS_SIZE == 8) {
                ldAddrStr = ldAddrStr + ".u64";
            } else {
                ldAddrStr = ldAddrStr + ".u32";
            }
            masm.emitString(ldAddrStr + " " + emitRegister(dest, false) + ", " + emitAddress(source1, source2) + ";");
        }
    }

    public static class Ld extends LoadStoreFormat {

        public Ld(PTXStateSpace space, Variable dst, Variable src1, Value src2) {
            super(space, dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("ld." + super.emit(true));
        }
    }

    public static class St extends LoadStoreFormat {

        public St(PTXStateSpace space, Variable dst, Variable src1, Value src2) {
            super(space, dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("st." + super.emit(false));
        }
    }

    public static class LoadParam extends Ld {
        // Type of the operation is dependent on src1's type.
        public LoadParam(PTXStateSpace space, Variable dst, Variable src1, Value src2) {
            super(space, dst, src1, src2);
            setKind(src1.getKind());
        }
    }

    public static class Add extends StandardFormat {

        public Add(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("add." + super.emit());
        }
    }

    public static class And extends LogicInstructionFormat {

        public And(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("and." + super.emit());
        }
    }

    public static class Div extends StandardFormat {

        public Div(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("div." + super.emit());
        }
    }

    public static class Mul extends StandardFormat {

        public Mul(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("mul.lo." + super.emit());
        }
    }

    public static class Or extends LogicInstructionFormat {

        public Or(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("or." + super.emit());
        }
    }

    public static class Rem extends StandardFormat {

        public Rem(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("rem." + super.emit());
        }
    }

    public static class Shl extends LogicInstructionFormat {

        public Shl(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("shl." + super.emit());
        }
    }

    public static class Shr extends StandardFormat {

        public Shr(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("shr." + super.emit());
        }
    }

    public static class Sub extends StandardFormat {

        public Sub(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("sub." + super.emit());
        }
    }

    public static class Ushr extends StandardFormat {

        public Ushr(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setKind(Kind.Illegal);  // get around not having an Unsigned Kind
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("shr." + super.emit());
        }
    }

    public static class Xor extends LogicInstructionFormat {

        public Xor(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("xor." + super.emit());
        }
    }

    public static class Cvt extends ConversionFormat {

        public Cvt(Variable dst, Variable src, Kind dstKind, Kind srcKind) {
            super(dst, src, dstKind, srcKind);
        }

        public void emit(PTXMacroAssembler asm) {
            if (dest.getKind() == Kind.Float || dest.getKind() == Kind.Double) {
                // round-to-zero - might not be right
                asm.emitString("cvt.rz." + super.emit());
            } else {
                asm.emitString("cvt." + super.emit());
            }
        }
    }

    public static class Mov extends SingleOperandFormat {

        private int predicateRegisterNumber = -1;

        public Mov(Variable dst, Value src) {
            super(dst, src);
        }

        public Mov(Variable dst, Value src, int predicate) {
            super(dst, src);
            this.predicateRegisterNumber = predicate;
        }

        /*
         * public Mov(Variable dst, AbstractAddress src) { throw
         * GraalInternalError.unimplemented("AbstractAddress Mov"); }
         */

        public void emit(PTXMacroAssembler asm) {
            if (predicateRegisterNumber >= 0) {
                asm.emitString("@%p" + String.valueOf(predicateRegisterNumber) + " mov." + super.emit());
            } else {
                asm.emitString("mov." + super.emit());
            }
        }
    }

    public static class Neg extends SingleOperandFormat {

        public Neg(Variable dst, Variable src) {
            super(dst, src);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("neg." + super.emit());
        }
    }

    public static class Not extends BinarySingleOperandFormat {

        public Not(Variable dst, Variable src) {
            super(dst, src);
        }

        public void emit(PTXMacroAssembler asm) {
            asm.emitString("not." + super.emit());
        }
    }

    public static class Param extends SingleOperandFormat {
        boolean isReturnParameter;

        public Param(Variable d, boolean isRet) {
            super(d, null);
            isReturnParameter = isRet;
        }

        public String emitParameter(Variable v) {
            return (" param" + v.index);
        }

        public void emit(PTXMacroAssembler asm, boolean isLastParam) {
            asm.emitString(".param ." + paramForKind(dest.getKind()) + emitParameter(dest) + (isLastParam ? "" : ","));
        }

        public String paramForKind(Kind k) {
            if (isReturnParameter) {
                if (Unsafe.ADDRESS_SIZE == 8) {
                    return "u64";
                } else {
                    return "u32";
                }
            } else {
                switch (k.getTypeChar()) {
                    case 'z':
                    case 'f':
                        return "s32";
                    case 'b':
                        return "b8";
                    case 's':
                        return "s16";
                    case 'c':
                        return "u16";
                    case 'i':
                        return "s32";
                    case 'j':
                        return "s64";
                    case 'd':
                        return "f64";
                    case 'a':
                        return "u64";
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
            }
        }

    }

}
