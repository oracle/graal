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
import com.sun.c1x.debug.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NewMultiArray} instruction represents an allocation of a multi-dimensional object
 * array.
 */
public final class NewMultiArray extends NewArray {

    private final int dimensionCount;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + dimensionCount;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The list of instructions which produce input for this instruction.
     */
    public Value dimension(int index) {
        assert index >= 0 && index < dimensionCount;
        return (Value) inputs().get(super.inputCount() + index);
    }

    public Value setDimension(int index, Value n) {
        assert index >= 0 && index < dimensionCount;
        return (Value) inputs().set(super.inputCount() + index, n);
    }

    /**
     * The rank of the array allocated by this instruction, i.e. how many array dimensions.
     */
    public int dimensionCount() {
        return dimensionCount;
    }

    public final RiType elementKind;
    public final int cpi;
    public final RiConstantPool constantPool;

    /**
     * Constructs a new NewMultiArray instruction.
     * @param elementKind the element type of the array
     * @param dimensions the instructions which produce the dimensions for this array
     * @param stateBefore the state before this instruction
     * @param cpi the constant pool index for resolution
     * @param riConstantPool the constant pool for resolution
     * @param graph
     */
    public NewMultiArray(RiType elementKind, Value[] dimensions, int cpi, RiConstantPool riConstantPool, Graph graph) {
        super(null, dimensions.length, SUCCESSOR_COUNT, graph);
        this.constantPool = riConstantPool;
        this.elementKind = elementKind;
        this.cpi = cpi;

        this.dimensionCount = dimensions.length;
        for (int i = 0; i < dimensions.length; i++) {
            setDimension(i, dimensions[i]);
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNewMultiArray(this);
    }

    /**
     * Gets the element type of the array.
     * @return the element type of the array
     */
    public RiType elementType() {
        return elementKind;
    }

    @Override
    public void print(LogStream out) {
        out.print("new multi array [");
        for (int i = 0; i < dimensionCount; i++) {
          if (i > 0) {
              out.print(", ");
          }
          out.print(dimension(i));
        }
        out.print("] ").print(CiUtil.toJavaName(elementKind));
    }

    @Override
    public Node copy(Graph into) {
        NewMultiArray x = new NewMultiArray(elementKind, new Value[dimensionCount], cpi, constantPool, into);
        x.setNonNull(isNonNull());
        return x;
    }
}
