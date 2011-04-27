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

import com.sun.c1x.debug.*;
import com.sun.c1x.util.*;
import com.sun.cri.bytecode.*;

/**
 * The {@code IfOp} class represents a comparison that yields one of two values.
 * Note that these nodes are not built directly from the bytecode but are introduced
 * by conditional expression elimination.
 *
 * @author Ben L. Titzer
 */
public final class IfOp extends Op2 {

    Condition cond;
    Value trueVal;
    Value falseVal;

    /**
     * Constructs a new IfOp.
     * @param x the instruction producing the first value to be compared
     * @param cond the condition of the comparison
     * @param y the instruction producing the second value to be compared
     * @param tval the value produced if the condition is true
     * @param fval the value produced if the condition is false
     */
    public IfOp(Value x, Condition cond, Value y, Value tval, Value fval) {
        // TODO: return the appropriate bytecode IF_ICMPEQ, etc
        super(tval.kind.meet(fval.kind), Bytecodes.ILLEGAL, x, y);
        this.cond = cond;
        this.trueVal = tval;
        falseVal = fval;
    }

    /**
     * Gets the condition of this if operation.
     * @return the condition
     */
    public Condition condition() {
        return cond;
    }

    /**
     * Gets the instruction that produces the value if the comparison is true.
     * @return the instruction producing the value upon true
     */
    public Value trueValue() {
        return trueVal;
    }

    /**
     * Gets the instruction that produces the value if the comparison is false.
     * @return the instruction producing the value upon false
     */
    public Value falseValue() {
        return falseVal;
    }

    /**
     * Checks whether this comparison operator is commutative (i.e. it is either == or !=).
     * @return {@code true} if this comparison is commutative
     */
    public boolean isCommutative() {
        return cond == Condition.EQ || cond == Condition.NE;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        trueVal = closure.apply(trueVal);
        falseVal = closure.apply(falseVal);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitIfOp(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash4(cond.hashCode(), x, y, trueVal, falseVal);
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof IfOp) {
            IfOp o = (IfOp) i;
            return opcode == o.opcode && x == o.x && y == o.y && trueVal == o.trueVal && falseVal == o.falseVal;
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print(x()).
        print(' ').
        print(condition().operator).
        print(' ').
        print(y()).
        print(" ? ").
        print(trueValue()).
        print(" : ").
        print(falseValue());
    }
}
