/*
 * Copyright (c) 2010, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The base class for pointer access operations.
 *
 * @author Doug Simon
 */
public abstract class PointerOp extends StateSplit {

    /**
     * The kind of value at the address accessed by the pointer operation.
     */
    public final CiKind dataKind;

    public final int opcode;
    protected Value pointer;
    protected Value displacement;
    protected Value offsetOrIndex;
    protected final boolean isVolatile;
    final boolean isPrefetch;

    /**
     * Creates an instruction for a pointer operation. If {@code displacement != null}, the effective of the address of the operation is
     * computed as the pointer plus a byte displacement plus a scaled index. Otherwise, the effective address is computed as the
     * pointer plus a byte offset.
     *
     * @param kind the kind of value produced by this operation
     * @param dataKind the kind of value at the address accessed by the pointer operation
     * @param opcode the opcode of the instruction
     * @param pointer the value producing the pointer
     * @param displacement the value producing the displacement. This may be {@code null}.
     * @param offsetOrIndex the value producing the scaled-index or the byte offset depending on whether {@code displacement} is {@code null}
     * @param stateBefore the state before
     * @param isVolatile {@code true} if the access is volatile
     */
    public PointerOp(CiKind kind, CiKind dataKind, int opcode, Value pointer, Value displacement, Value offsetOrIndex, FrameState stateBefore, boolean isVolatile) {
        super(kind.stackKind(), stateBefore);
        this.opcode = opcode;
        this.pointer = pointer;
        this.dataKind = dataKind;
        this.displacement = displacement;
        this.offsetOrIndex = offsetOrIndex;
        this.isVolatile = isVolatile;
        this.isPrefetch = false;
        if (pointer.isNonNull()) {
            eliminateNullCheck();
        }
    }

    public Value pointer() {
        return pointer;
    }

    public Value index() {
        return offsetOrIndex;
    }

    public Value offset() {
        return offsetOrIndex;
    }

    public Value displacement() {
        return displacement;
    }

    @Override
    public void runtimeCheckCleared() {
        clearState();
    }

    /**
     * Checks whether this field access may cause a trap or an exception, which
     * is if it either requires a null check or needs patching.
     * @return {@code true} if this field access can cause a trap
     */
    @Override
    public boolean canTrap() {
        return needsNullCheck();
    }

    /**
     * Iterates over the input values to this instruction.
     * @param closure the closure to apply to each value
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        pointer = closure.apply(pointer);
        offsetOrIndex = closure.apply(offsetOrIndex);
        if (displacement != null) {
            displacement = closure.apply(displacement);
        }
    }
}
