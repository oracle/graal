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
package com.oracle.graal.snippets.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

public class AMD64MathIntrinsicOp extends AMD64LIRInstruction {
    public enum Opcode  {
        SQRT,
        SIN, COS, TAN,
        LOG, LOG10;
    }

    public AMD64MathIntrinsicOp(Opcode opcode, RiValue result, RiValue input) {
        super(opcode, new RiValue[] {result}, null, new RiValue[] {input}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        Opcode opcode = (Opcode) code;
        RiValue result = output(0);
        RiValue input = input(0);

        switch (opcode) {
            case SQRT:  masm.sqrtsd(asDoubleReg(result), asDoubleReg(input)); break;
            case LOG:   masm.flog(asDoubleReg(result), asDoubleReg(input), false); break;
            case LOG10: masm.flog(asDoubleReg(result), asDoubleReg(input), true); break;
            case SIN:   masm.fsin(asDoubleReg(result), asDoubleReg(input)); break;
            case COS:   masm.fcos(asDoubleReg(result), asDoubleReg(input)); break;
            case TAN:   masm.ftan(asDoubleReg(result), asDoubleReg(input)); break;
            default:    throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
        if (mode == OperandMode.Input && index == 0) {
            return EnumSet.of(OperandFlag.Register);
        } else if (mode == OperandMode.Output && index == 0) {
            return EnumSet.of(OperandFlag.Register);
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
