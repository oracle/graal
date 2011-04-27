/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code ArithmeticOp} class represents arithmetic operations such as addition, subtraction, etc.
 *
 * @author Ben L. Titzer
 */
public final class ArithmeticOp extends Op2 {

    private FrameState stateBefore;

    /**
     * Creates a new arithmetic operation.
     * @param opcode the bytecode opcode
     * @param kind the result kind of the operation
     * @param x the first input instruction
     * @param y the second input instruction
     * @param isStrictFP indicates this operation has strict rounding semantics
     * @param stateBefore the state for instructions that may trap
     */
    public ArithmeticOp(int opcode, CiKind kind, Value x, Value y, boolean isStrictFP, FrameState stateBefore) {
        super(kind, opcode, x, y);
        initFlag(Flag.IsStrictFP, isStrictFP);
        if (stateBefore != null) {
            // state before is only used in the case of a division or remainder,
            // and isn't needed if the zero check is redundant
            if (y.isConstant()) {
                long divisor = y.asConstant().asLong();
                if (divisor != 0) {
                    C1XMetrics.ZeroChecksRedundant++;
                    setFlag(Flag.NoZeroCheck);
                } else {
                    this.stateBefore = stateBefore;
                }
                if (divisor != -1) {
                    C1XMetrics.DivideSpecialChecksRedundant++;
                    setFlag(Flag.NoDivSpecialCase);
                }
            } else {
                this.stateBefore = stateBefore;
            }
        }
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    /**
     * Checks whether this instruction has strict fp semantics.
     * @return {@code true} if this instruction has strict fp semantics
     */
    public boolean isStrictFP() {
        return checkFlag(Flag.IsStrictFP);
    }

    /**
     * Checks whether this instruction can cause a trap. For arithmetic operations,
     * only division and remainder operations can cause traps.
     * @return {@code true} if this instruction can cause a trap
     */
    @Override
    public boolean canTrap() {
        return stateBefore != null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitArithmeticOp(this);
    }

    public boolean isCommutative() {
        return Bytecodes.isCommutative(opcode);
    }

    public boolean needsZeroCheck() {
        return !checkFlag(Flag.NoZeroCheck);
    }

    public void eliminateZeroCheck() {
        clearRuntimeCheck(Flag.NoZeroCheck);
    }

    @Override
    public void print(LogStream out) {
        out.print(x()).
             print(' ').
             print(Bytecodes.operator(opcode)).
             print(' ').
             print(y());
    }
}
