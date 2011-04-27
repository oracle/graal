/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.cri.ci.*;

/**
 * Implements the {@link Bytecodes#LSB} and {@link Bytecodes#MSB} instructions.
 *
 * @author Laurent Daynes
 */
public class SignificantBitOp extends Instruction {
    Value value;

    /**
     * This will be {@link Bytecodes#LSB} or {@link Bytecodes#MSB}.
     */
    public final int op;

    /**
     * Create a a new SignificantBitOp instance.
     *
     * @param value the instruction producing the value that is input to this instruction
     * @param opcodeop either {@link Bytecodes#LSB} or {@link Bytecodes#MSB}
     */
    public SignificantBitOp(Value value, int opcodeop) {
        super(CiKind.Int);
        assert opcodeop == Bytecodes.LSB || opcodeop == Bytecodes.MSB;
        this.value = value;
        this.op = opcodeop;
    }

    /**
     * Gets the instruction producing input to this instruction.
     * @return the instruction that produces this instruction's input
     */
    public Value value() {
        return value;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        value = closure.apply(value);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitSignificantBit(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash1(op, value);
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof SignificantBitOp) {
            SignificantBitOp o = (SignificantBitOp) i;
            // FIXME: this is a conservative estimate. If x is a single-bit value
            // (i.e., a power of 2), then the values are equal regardless of the value of the most field.
            return value == o.value && op == o.op;
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print(Bytecodes.nameOf(op) + " [").print(this).print("] ");
    }
}
