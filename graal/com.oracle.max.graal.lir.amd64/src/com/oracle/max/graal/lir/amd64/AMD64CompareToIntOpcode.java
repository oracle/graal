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
package com.oracle.max.graal.lir.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.lir.*;
import com.oracle.max.graal.lir.asm.*;

/**
 * Implementation of the Java bytecodes that compare a long, float, or double value and produce the
 * integer constants -1, 0, 1 on less, equal, or greater, respectively.  For floating point compares,
 * unordered can be either greater {@link #CMP2INT_UG} or less {@link #CMP2INT_UL}.
 */
public enum AMD64CompareToIntOpcode {
    CMP2INT, CMP2INT_UG, CMP2INT_UL;

    public LIRInstruction create(CiValue result) {
        CiValue[] outputs = new CiValue[] {result};

        return new AMD64LIRInstruction(this, outputs, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(masm, output(0));
            }

            @Override
            protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
                if (mode == OperandMode.Output && index == 0) {
                    return EnumSet.of(OperandFlag.Register);
                }
                throw GraalInternalError.shouldNotReachHere();
            }
        };
    }

    private void emit(AMD64MacroAssembler masm, CiValue result) {
        CiRegister dest = asIntReg(result);
        Label high = new Label();
        Label low = new Label();
        Label done = new Label();

        // comparison is done by a separate LIR instruction before
        switch (this) {
            case CMP2INT:
                masm.jcc(ConditionFlag.greater, high);
                masm.jcc(ConditionFlag.less, low);
                break;
            case CMP2INT_UG:
                masm.jcc(ConditionFlag.parity, high);
                masm.jcc(ConditionFlag.above, high);
                masm.jcc(ConditionFlag.below, low);
                break;
            case CMP2INT_UL:
                masm.jcc(ConditionFlag.parity, low);
                masm.jcc(ConditionFlag.above, high);
                masm.jcc(ConditionFlag.below, low);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }

        // equal -> 0
        masm.xorptr(dest, dest);
        masm.jmp(done);

        // greater -> 1
        masm.bind(high);
        masm.xorptr(dest, dest);
        masm.incrementl(dest, 1);
        masm.jmp(done);

        // less -> -1
        masm.bind(low);
        masm.xorptr(dest, dest);
        masm.decrementl(dest, 1);

        masm.bind(done);
    }
}
