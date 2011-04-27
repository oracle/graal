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
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NewMultiArray} instruction represents an allocation of a multi-dimensional object
 * array.
 *
 * @author Ben L. Titzer
 */
public final class NewMultiArray extends NewArray {
    public final RiType elementKind;
    final Value[] dimensions;
    public final int cpi;
    public final RiConstantPool constantPool;

    /**
     * Constructs a new NewMultiArray instruction.
     * @param elementKind the element type of the array
     * @param dimensions the instructions which produce the dimensions for this array
     * @param stateBefore the state before this instruction
     * @param cpi the constant pool index for resolution
     * @param riConstantPool the constant pool for resolution
     */
    public NewMultiArray(RiType elementKind, Value[] dimensions, FrameState stateBefore, int cpi, RiConstantPool riConstantPool) {
        super(null, stateBefore);
        this.constantPool = riConstantPool;
        this.elementKind = elementKind;
        this.dimensions = dimensions;
        this.cpi = cpi;
    }

    /**
     * Gets the list of instructions which produce input for this instruction.
     * @return the list of instructions which produce input
     */
    public Value[] dimensions() {
        return dimensions;
    }

    /**
     * Gets the rank of the array allocated by this instruction, i.e. how many array dimensions.
     * @return the rank of the array allocated
     */
    public int rank() {
        return dimensions.length;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = closure.apply(dimensions[i]);
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
        final Value[] dimensions = dimensions();
        for (int i = 0; i < dimensions.length; i++) {
          if (i > 0) {
              out.print(", ");
          }
          out.print(dimensions[i]);
        }
        out.print("] ").print(CiUtil.toJavaName(elementKind));
    }
}
