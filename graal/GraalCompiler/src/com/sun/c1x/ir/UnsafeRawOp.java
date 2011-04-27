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
 * The {@code UnsafeRawOp} class is the base class of all unsafe raw operations.
 *
 * @author Ben L. Titzer
 */
public abstract class UnsafeRawOp extends UnsafeOp {

    Value base;
    Value index;
    int log2Scale;

    /**
     * Creates a new UnsafeRawOp instruction.
     * @param opKind the kind of the operation
     * @param addr the instruction generating the base address (a long)
     * @param isStore {@code true} if this operation is a store
     */
    public UnsafeRawOp(CiKind opKind, Value addr, boolean isStore) {
        super(opKind, isStore);
        assert addr == null || addr.kind == CiKind.Long;
        base = addr;
    }

    /**
     * Creates a new UnsafeRawOp instruction.
     * @param opKind the kind of the operation
     * @param addr the instruction generating the base address (a long)
     * @param index the instruction generating the index
     * @param log2scale the log base 2 of the scaling factor
     * @param isStore {@code true} if this operation is a store
     */
    public UnsafeRawOp(CiKind opKind, Value addr, Value index, int log2scale, boolean isStore) {
        this(opKind, addr, isStore);
        this.base = addr;
        this.index = index;
        this.log2Scale = log2scale;
    }

    /**
     * Gets the instruction generating the base address for this operation.
     * @return the instruction generating the base
     */
    public Value base() {
        return base;
    }

    /**
     * Gets the instruction generating the index for this operation.
     * @return the instruction generating the index
     */
    public Value index() {
        return index;
    }

    /**
     * Checks whether this instruction has an index.
     * @return {@code true} if this instruction has an index
     */
    public boolean hasIndex() {
        return index != null;
    }

    /**
     * Gets the log base 2 of the scaling factor for the index of this instruction.
     * @return the log base 2 of the scaling factor
     */
    public int log2Scale() {
        return log2Scale;
    }

    /**
     * Sets the instruction that generates the base address for this instruction.
     * @param base the instruction generating the base address
     */
    public void setBase(Value base) {
        this.base = base;
    }

    /**
     * Sets the instruction generating the base address for this instruction.
     * @param index the instruction generating the index
     */
    public void setIndex(Value index) {
        this.index = index;
    }

    /**
     * Sets the scaling factor for the index of this instruction.
     * @param log2scale the log base 2 of the scaling factor for this instruction
     */
    public void setLog2Scale(int log2scale) {
        this.log2Scale = log2scale;
    }

    /**
     * Iterates over the input values to this instruction.
     * @param closure the closure to apply
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        base = closure.apply(base);
        if (index != null) {
            index = closure.apply(index);
        }
    }
}
