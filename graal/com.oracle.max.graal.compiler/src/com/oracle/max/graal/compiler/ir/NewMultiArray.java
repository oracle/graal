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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NewMultiArray} instruction represents an allocation of a multi-dimensional object
 * array.
 */
public final class NewMultiArray extends NewArray {

    @Input    private final NodeInputList<Value> dimensions;

    @Override
    public Value dimension(int index) {
        return dimensions.get(index);
    }

    public void setDimension(int index, Value x) {
        dimensions.set(index, x);
    }

    /**
     * The rank of the array allocated by this instruction, i.e. how many array dimensions.
     */
    @Override
    public int dimensionCount() {
        return dimensions.size();
    }

    public final RiType elementType;
    public final int cpi;
    public final RiConstantPool constantPool;

    /**
     * Constructs a new NewMultiArray instruction.
     * @param elementType the element type of the array
     * @param dimensions the instructions which produce the dimensions for this array
     * @param cpi the constant pool index for resolution
     * @param riConstantPool the constant pool for resolution
     * @param graph
     */
    public NewMultiArray(RiType elementType, Value[] dimensions, int cpi, RiConstantPool riConstantPool, Graph graph) {
        super(null, graph);
        this.constantPool = riConstantPool;
        this.elementType = elementType;
        this.cpi = cpi;

        this.dimensions = new NodeInputList<Value>(this, dimensions.length);
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
        return elementType;
    }

    @Override
    public CiKind elementKind() {
        return elementType.kind();
    }

    @Override
    public RiType exactType() {
        return elementType.arrayOf();
    }

    @Override
    public RiType declaredType() {
        return exactType();
    }

    @Override
    public void print(LogStream out) {
        out.print("new multi array [");
        for (int i = 0; i < dimensionCount(); i++) {
          if (i > 0) {
              out.print(", ");
          }
          out.print(dimension(i));
        }
        out.print("] ").print(CiUtil.toJavaName(elementType));
    }
}
