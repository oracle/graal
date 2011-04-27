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

import static com.sun.c1x.ir.Value.Flag.*;

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code AccessIndexed} class is the base class of instructions that read or write
 * elements of an array.
 *
 * @author Ben L. Titzer
 */
public abstract class AccessIndexed extends AccessArray {

    private Value index;
    private Value length;
    private final CiKind elementType;

    /**
     * Create an new AccessIndexed instruction.
     * @param kind the result kind of the access
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length (used in bounds check elimination?)
     * @param elementType the type of the elements of the array
     * @param stateBefore the state before executing this instruction
     */
    AccessIndexed(CiKind kind, Value array, Value index, Value length, CiKind elementType, FrameState stateBefore) {
        super(kind, array, stateBefore);
        this.index = index;
        this.length = length;
        this.elementType = elementType;
    }

    /**
     * Gets the instruction producing the index into the array.
     * @return the index
     */
    public Value index() {
        return index;
    }

    /**
     * Gets the instruction that produces the length of the array.
     * @return the length
     */
    public Value length() {
        return length;
    }

    /**
     * Gets the element type of the array.
     * @return the element type
     */
    public CiKind elementKind() {
        return elementType;
    }

    /**
     * Checks whether this instruction needs a bounds check.
     * @return {@code true} if a bounds check is needed
     */
    public boolean needsBoundsCheck() {
        return !checkFlag(NoBoundsCheck);
    }

    public void eliminateBoundsCheck() {
        clearRuntimeCheck(NoBoundsCheck);
    }

    /**
     * Checks whether this instruction can cause a trap.
     * @return {@code true} if this instruction can cause a trap
     */
    @Override
    public boolean canTrap() {
        return needsNullCheck() || needsBoundsCheck();
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        index = closure.apply(index);
        if (length != null) {
            length = closure.apply(length);
        }
    }
}
