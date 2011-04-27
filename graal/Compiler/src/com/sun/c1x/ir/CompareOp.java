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
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code CompareOp} instruction represents comparisons such as equals, not equal, etc.
 *
 * @author Ben L. Titzer
 */
public final class CompareOp extends Op2 {

    /**
     * Creates a new compare operation.
     * @param opcode the bytecode opcode
     * @param kind the result kind
     * @param x the first input
     * @param y the second input
     */
    public CompareOp(int opcode, CiKind kind, Value x, Value y) {
        super(kind, opcode, x, y);
        if (kind.isVoid()) {
            // A compare that does not produce a value exists soley for it's side effect (i.e. setting condition codes)
            setFlag(Flag.LiveSideEffect);
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitCompareOp(this);
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
