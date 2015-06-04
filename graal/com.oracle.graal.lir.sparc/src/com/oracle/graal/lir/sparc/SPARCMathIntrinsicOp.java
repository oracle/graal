/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.Value;

import static com.oracle.jvmci.code.ValueUtil.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.jvmci.common.*;

public final class SPARCMathIntrinsicOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCMathIntrinsicOp> TYPE = LIRInstructionClass.create(SPARCMathIntrinsicOp.class);

    public enum IntrinsicOpcode {
        SQRT,
        ABS
    }

    @Opcode private final IntrinsicOpcode opcode;
    @Def protected Value result;
    @Use protected Value input;

    public SPARCMathIntrinsicOp(IntrinsicOpcode opcode, Value result, Value input) {
        super(TYPE);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Kind inputKind = (Kind) input.getLIRKind().getPlatformKind();
        delayedControlTransfer.emitControlTransfer(crb, masm);
        switch (opcode) {
            case SQRT:
                switch (inputKind) {
                    case Float:
                        masm.fsqrts(asFloatReg(input), asFloatReg(result));
                        break;
                    case Double:
                        masm.fsqrtd(asDoubleReg(input), asDoubleReg(result));
                        break;
                    default:
                        JVMCIError.shouldNotReachHere();
                }
                break;
            case ABS:
                switch (inputKind) {
                    case Float:
                        masm.fabss(asFloatReg(input), asFloatReg(result));
                        break;
                    case Double:
                        masm.fabsd(asDoubleReg(input), asDoubleReg(result));
                        break;
                    default:
                        JVMCIError.shouldNotReachHere();
                }
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

}
