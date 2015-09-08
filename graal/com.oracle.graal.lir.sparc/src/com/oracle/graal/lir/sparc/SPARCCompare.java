/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.lir.LIRValueUtil.*;
import static jdk.internal.jvmci.code.ValueUtil.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCControlFlow.CompareBranchOp;

public enum SPARCCompare {
    ICMP,
    LCMP,
    ACMP,
    FCMP,
    DCMP;

    public static final class CompareOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<CompareOp> TYPE = LIRInstructionClass.create(CompareOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Opcode private final SPARCCompare opcode;
        @Use({REG}) protected Value x;
        @Use({REG, CONST}) protected Value y;

        public CompareOp(SPARCCompare opcode, Value x, Value y) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emit(crb, masm, opcode, x, y);
        }

        @Override
        public void verify() {
            super.verify();
            assert CompareBranchOp.SUPPORTED_KINDS.contains(x.getPlatformKind()) : x.getPlatformKind();
            assert x.getPlatformKind().equals(y.getPlatformKind()) : x + " " + y;
            // @formatter:off
            assert
                    (name().startsWith("I") && x.getPlatformKind() == Kind.Int && ((Kind) y.getPlatformKind()).getStackKind() == Kind.Int) ||
                    (name().startsWith("L") && x.getPlatformKind() == Kind.Long && y.getPlatformKind() == Kind.Long) ||
                    (name().startsWith("A") && x.getPlatformKind() == Kind.Object && y.getPlatformKind() == Kind.Object) ||
                    (name().startsWith("F") && x.getPlatformKind() == Kind.Float && y.getPlatformKind() == Kind.Float) ||
                    (name().startsWith("D") && x.getPlatformKind() == Kind.Double && y.getPlatformKind() == Kind.Double)
                    : "Name; " + name() + " x: " + x + " y: " + y;
            // @formatter:on
        }
    }

    public static void emit(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCCompare opcode, Value x, Value y) {
        if (isRegister(y)) {
            switch (opcode) {
                case ICMP:
                    masm.cmp(asRegister(x, Kind.Int), asRegister(y, Kind.Int));
                    break;
                case LCMP:
                    masm.cmp(asRegister(x, Kind.Long), asRegister(y, Kind.Long));
                    break;
                case ACMP:
                    masm.cmp(asRegister(x), asRegister(y));
                    break;
                case FCMP:
                    masm.fcmp(Fcc0, Fcmps, asRegister(x, Kind.Float), asRegister(y, Kind.Float));
                    break;
                case DCMP:
                    masm.fcmp(Fcc0, Fcmpd, asRegister(x, Kind.Double), asRegister(y, Kind.Double));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        } else {
            assert isJavaConstant(y);
            switch (opcode) {
                case LCMP:
                    assert isSimm13(crb.asLongConst(y));
                    masm.cmp(asRegister(x, Kind.Long), (int) crb.asLongConst(y));
                    break;
                case ICMP:
                    assert isSimm13(crb.asIntConst(y));
                    masm.cmp(asRegister(x, Kind.Int), crb.asIntConst(y));
                    break;
                case ACMP:
                    if (asJavaConstant(y).isNull()) {
                        masm.cmp(asRegister(x), 0);
                        break;
                    } else {
                        throw JVMCIError.shouldNotReachHere("Only null object constants are allowed in comparisons");
                    }
                case FCMP:
                    masm.fcmp(Fcc0, Fcmps, asRegister(x, Kind.Float), asRegister(y, Kind.Float));
                    break;
                case DCMP:
                    masm.fcmp(Fcc0, Fcmpd, asRegister(x, Kind.Double), asRegister(y, Kind.Double));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }
}
