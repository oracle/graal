/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;

import jdk.internal.jvmci.meta.*;

public abstract class SPARCBlockEndOp extends SPARCLIRInstruction implements BlockEndOp {
    public static final LIRInstructionClass<SPARCBlockEndOp> TYPE = LIRInstructionClass.create(SPARCBlockEndOp.class);

    @Alive({REG, STACK, CONST}) private Value[] outgoingValues;
    private int size;

    protected SPARCBlockEndOp(LIRInstructionClass<? extends SPARCBlockEndOp> c) {
        this(c, null);
    }

    protected SPARCBlockEndOp(LIRInstructionClass<? extends SPARCBlockEndOp> c, SizeEstimate sizeEstimate) {
        super(c, sizeEstimate);
        this.outgoingValues = Value.NO_VALUES;
        this.size = 0;
    }

    public void setOutgoingValues(Value[] values) {
        assert outgoingValues.length == 0;
        assert values != null;
        outgoingValues = values;
        size = values.length;
    }

    public int getOutgoingSize() {
        return size;
    }

    public Value getOutgoingValue(int idx) {
        assert checkRange(idx);
        return outgoingValues[idx];
    }

    private boolean checkRange(int idx) {
        return idx < size;
    }

    public void clearOutgoingValues() {
        outgoingValues = Value.NO_VALUES;
        size = 0;
    }

    public int addOutgoingValues(Value[] v) {

        int t = size + v.length;
        if (t >= outgoingValues.length) {
            Value[] newArray = new Value[t];
            System.arraycopy(outgoingValues, 0, newArray, 0, size);
            outgoingValues = newArray;
        }
        System.arraycopy(v, 0, outgoingValues, size, v.length);
        size = t;
        return t;

    }
}
