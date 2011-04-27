/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.cri.ci.*;

/**
 * The {@code UnsafeObjectOp} class is the base of all unsafe object instructions.
 *
 * @author Ben L. Titzer
 */
public abstract class UnsafeObjectOp extends UnsafeOp {

    Value object;
    Value offset;
    final boolean isVolatile;

    /**
     * Creates a new UnsafeObjectOp instruction.
     * @param opKind the kind of the operation
     * @param object the instruction generating the object
     * @param offset the instruction generating the index
     * @param isStore {@code true} if this is a store operation
     * @param isVolatile {@code true} if the operation is volatile
     */
    public UnsafeObjectOp(CiKind opKind, Value object, Value offset, boolean isStore, boolean isVolatile) {
        super(opKind, isStore);
        this.object = object;
        this.offset = offset;
        this.isVolatile = isVolatile;
    }

    /**
     * Gets the instruction that generates the object.
     * @return the instruction that produces the object
     */
    public Value object() {
        return object;
    }

    /**
     * Gets the instruction that generates the offset.
     * @return the instruction generating the offset
     */
    public Value offset() {
        return offset;
    }

    /**
     * Checks whether this is a volatile operation.
     * @return {@code true} if this operation is volatile
     */
    public boolean isVolatile() {
        return isVolatile;
    }

    /**
     * Iterates over the input values of this instruction.
     * @param closure the closure to apply
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        object = closure.apply(object);
        offset = closure.apply(offset);
    }
}
