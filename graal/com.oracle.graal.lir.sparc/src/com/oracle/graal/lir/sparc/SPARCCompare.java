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

import static com.oracle.graal.asm.sparc.SPARCAssembler.isSimm13;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Fcc0;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fcmpd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fcmps;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.CONST;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.code.ValueUtil.isRegister;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
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
                    (name().startsWith("I") && x.getPlatformKind() == JavaKind.Int && ((JavaKind) y.getPlatformKind()).getStackKind() == JavaKind.Int) ||
                    (name().startsWith("L") && x.getPlatformKind() == JavaKind.Long && y.getPlatformKind() == JavaKind.Long) ||
                    (name().startsWith("A") && x.getPlatformKind() == JavaKind.Object && y.getPlatformKind() == JavaKind.Object) ||
                    (name().startsWith("F") && x.getPlatformKind() == JavaKind.Float && y.getPlatformKind() == JavaKind.Float) ||
                    (name().startsWith("D") && x.getPlatformKind() == JavaKind.Double && y.getPlatformKind() == JavaKind.Double)
                    : "Name; " + name() + " x: " + x + " y: " + y;
            // @formatter:on
        }
    }

    public static void emit(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCCompare opcode, Value x, Value y) {
        if (isRegister(y)) {
            switch (opcode) {
                case ICMP:
                    masm.cmp(asRegister(x, JavaKind.Int), asRegister(y, JavaKind.Int));
                    break;
                case LCMP:
                    masm.cmp(asRegister(x, JavaKind.Long), asRegister(y, JavaKind.Long));
                    break;
                case ACMP:
                    masm.cmp(asRegister(x), asRegister(y));
                    break;
                case FCMP:
                    masm.fcmp(Fcc0, Fcmps, asRegister(x, JavaKind.Float), asRegister(y, JavaKind.Float));
                    break;
                case DCMP:
                    masm.fcmp(Fcc0, Fcmpd, asRegister(x, JavaKind.Double), asRegister(y, JavaKind.Double));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        } else {
            JavaConstant c = asJavaConstant(y);
            int imm;
            if (c.isNull()) {
                imm = 0;
            } else {
                assert NumUtil.isInt(c.asLong());
                imm = (int) c.asLong();
            }

            switch (opcode) {
                case LCMP:
                    assert isSimm13(imm);
                    masm.cmp(asRegister(x, JavaKind.Long), imm);
                    break;
                case ICMP:
                    assert isSimm13(crb.asIntConst(y));
                    masm.cmp(asRegister(x, JavaKind.Int), imm);
                    break;
                case ACMP:
                    assert imm == 0 : "Only null object constants are allowed in comparisons";
                    masm.cmp(asRegister(x), 0);
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }
}
