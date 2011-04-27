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
import com.sun.cri.ci.*;

/**
 * The {@code Convert} class represents a conversion between primitive types.
 *
 * @author Ben L. Titzer
 */
public final class Convert extends Instruction {

    /**
     * The opcode for this conversion operation.
     */
    public final int opcode;

    Value value;

    /**
     * Constructs a new Convert instance.
     * @param opcode the bytecode representing the operation
     * @param value the instruction producing the input value
     * @param kind the result type of this instruction
     */
    public Convert(int opcode, Value value, CiKind kind) {
        super(kind);
        this.opcode = opcode;
        this.value = value;
    }

    /**
     * Gets the instruction which produces the input value to this instruction.
     * @return the input value instruction
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
        v.visitConvert(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash1(opcode, value);
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof Convert) {
            Convert o = (Convert) i;
            return opcode == o.opcode && value == o.value;
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print(Bytecodes.nameOf(opcode)).print('(').print(value()).print(')');
    }
}
