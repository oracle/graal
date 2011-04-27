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
 * The {@code NegateOp} instruction negates its operand.
 *
 * @author Ben L. Titzer
 */
public final class NegateOp extends Instruction {

    Value x;

    /**
     * Creates new NegateOp instance.
     * @param x the instruction producing the value that is input to this instruction
     */
    public NegateOp(Value x) {
        super(x.kind);
        this.x = x;
    }

    /**
     * Gets the instruction producing input to this instruction.
     * @return the instruction that produces this instruction's input
     */
    public Value x() {
        return x;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        x = closure.apply(x);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNegateOp(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash1(Bytecodes.INEG, x);
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof NegateOp) {
            NegateOp o = (NegateOp) i;
            return x == o.x;
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print("- ").print(x());
    }
}
