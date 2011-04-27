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
import com.sun.cri.bytecode.*;
import com.sun.cri.bytecode.Bytecodes.UnsignedComparisons;
import com.sun.cri.ci.*;

/**
 * Unsigned comparisons.
 *
 * @author Mick Jordan
 * @see UnsignedComparisons
 */
public final class UnsignedCompareOp extends Op2 {

    /**
     * One of the constants defined in {@link UnsignedComparisons} denoting the type of this comparison.
     */
    public final int op;

    /**
     * Creates a new compare operation.
     *
     * @param opcode the bytecode opcode
     * @param op the comparison type
     * @param x the first input
     * @param y the second input
     */
    public UnsignedCompareOp(int opcode, int op, Value x, Value y) {
        super(CiKind.Int, opcode, x, y);
        assert opcode == Bytecodes.UWCMP || opcode == Bytecodes.UCMP;
        this.op = op;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitUnsignedCompareOp(this);
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
