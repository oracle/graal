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
import com.sun.c1x.value.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 *
 * @author Ben L. Titzer
 */
public final class ArrayLength extends AccessArray {

    /**
     * Constructs a new ArrayLength instruction.
     * @param array the instruction producing the array
     * @param newFrameState the state before executing this instruction
     */
    public ArrayLength(Value array, FrameState newFrameState) {
        super(CiKind.Int, array, newFrameState);
        if (array.isNonNull()) {
            eliminateNullCheck();
        }
    }

    /**
     * Clears the state associated with a null check.
     */
    @Override
    public void runtimeCheckCleared() {
        if (!needsNullCheck()) {
            clearState();
        }
    }

    /**
     * Checks whether this instruction can cause a trap.
     * @return {@code true} if this instruction can cause a trap
     */
    @Override
    public boolean canTrap() {
        return needsNullCheck();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitArrayLength(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash1(Bytecodes.ARRAYLENGTH, array);
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof ArrayLength) {
            ArrayLength o = (ArrayLength) i;
            return array == o.array;
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print(array).print(".length");
    }
}
