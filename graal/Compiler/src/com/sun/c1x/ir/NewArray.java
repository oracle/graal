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

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code NewArray} class is the base of all instructions that allocate arrays.
 *
 * @author Ben L. Titzer
 */
public abstract class NewArray extends StateSplit {

    Value length;

    /**
     * Constructs a new NewArray instruction.
     * @param length the instruction that produces the length for this allocation
     * @param stateBefore the state before the allocation
     */
    NewArray(Value length, FrameState stateBefore) {
        super(CiKind.Object, stateBefore);
        this.length = length;
        setFlag(Flag.NonNull);
        setFlag(Flag.ResultIsUnique);
    }

    /**
     * Gets the instruction that produces the length of this array.
     * @return the instruction that produces the length
     */
    public Value length() {
        return length;
    }

    /**
     * Checks whether this instruction can trap.
     * @return <code>true</code>, conservatively assuming that this instruction can throw such
     * exceptions as {@code OutOfMemoryError}
     */
    @Override
    public boolean canTrap() {
        return true;
    }

    /**
     * Applies the specified closure to all input values of this instruction.
     * @param closure the closure to apply
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        length = closure.apply(length);
    }
}
