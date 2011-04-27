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
import com.sun.c1x.value.*;
import com.sun.cri.bytecode.*;

/**
 * Atomic update of a value in memory. Implements the {@link Bytecodes#PCMPSWP} family of instructions.
 *
 * Compares a suspected value with the actual value in a memory location.
 * Iff they are same, a new value is placed into the location and the expected value is returned.
 * Otherwise, the actual value is returned.
 *
 * @author Doug Simon
 */
public final class CompareAndSwap extends PointerOp {

    /**
     * The value to store.
     */
    Value expectedValue;

    Value newValue;

    /**
     * Creates an instruction for a pointer store. If {@code displacement != null}, the effective of the address of the store is
     * computed as the pointer plus a byte displacement plus a scaled index. Otherwise, the effective address is computed as the
     * pointer plus a byte offset.
     * @param pointer the value producing the pointer
     * @param offset the value producing the byte offset
     * @param expectedValue the value that must currently being in memory location for the swap to occur
     * @param newValue the new value to store if the precondition is satisfied
     * @param stateBefore the state before
     * @param isVolatile {@code true} if the access is volatile
     */
    public CompareAndSwap(int opcode, Value pointer, Value offset, Value expectedValue, Value newValue, FrameState stateBefore, boolean isVolatile) {
        super(expectedValue.kind, expectedValue.kind, opcode, pointer, null, offset, stateBefore, isVolatile);
        assert offset != null;
        this.expectedValue = expectedValue;
        this.newValue = newValue;
        setFlag(Flag.LiveStore);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitCompareAndSwap(this);
    }

    public Value expectedValue() {
        return expectedValue;
    }

    public Value newValue() {
        return newValue;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        expectedValue = closure.apply(expectedValue);
        newValue = closure.apply(newValue);
    }

    @Override
    public void print(LogStream out) {
        out.print(Bytecodes.nameOf(opcode)).print("(").print(pointer());
        out.print(" + ").print(offset());
        out.print(", ").print(expectedValue()).print(", ").print(newValue()).print(')');
    }
}
