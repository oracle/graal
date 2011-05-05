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

import com.oracle.graal.graph.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code AccessIndexed} class is the base class of instructions that read or write
 * elements of an array.
 *
 * @author Ben L. Titzer
 */
public abstract class AccessIndexed extends AccessArray {

    private static final int INPUT_COUNT = 2;
    private static final int INPUT_INDEX = 0;
    private static final int INPUT_LENGTH = 1;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The instruction producing the index into the array.
     */
     public Value index() {
        return (Value) inputs().get(super.inputCount() + INPUT_INDEX);
    }

    public Value setIndex(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_INDEX, n);
    }

    /**
     * The instruction that produces the length of the array.
     */
    public Value length() {
        return (Value) inputs().get(super.inputCount() + INPUT_LENGTH);
    }

    public Value setLength(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_LENGTH, n);
    }

    private final CiKind elementType;

    /**
     * Create an new AccessIndexed instruction.
     * @param kind the result kind of the access
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length (used in bounds check elimination?)
     * @param elementType the type of the elements of the array
     * @param stateBefore the state before executing this instruction
     * @param inputCount
     * @param successorCount
     * @param graph
     */
    AccessIndexed(CiKind kind, Value array, Value index, Value length, CiKind elementType, FrameState stateBefore, int inputCount, int successorCount, Graph graph) {
        super(kind, array, stateBefore, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        setIndex(index);
        setLength(length);
        this.elementType = elementType;
    }

    /**
     * Gets the element type of the array.
     * @return the element type
     */
    public CiKind elementKind() {
        return elementType;
    }

}
