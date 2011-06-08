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

import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * This the base class of all array operations.
 */
public abstract class AccessArray extends StateSplit {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_ARRAY = 0;

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
     * The instruction that produces the array object.
     */
     public Value array() {
        return (Value) inputs().get(super.inputCount() + INPUT_ARRAY);
    }

    public Value setArray(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_ARRAY, n);
    }

    /**
     * Creates a new AccessArray instruction.
     * @param kind the type of the result of this instruction
     * @param array the instruction that produces the array object value
     * @param stateBefore the frame state before the instruction
     * @param inputCount
     * @param successorCount
     * @param graph
     */
    public AccessArray(CiKind kind, Value array, int inputCount, int successorCount, Graph graph) {
        super(kind, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        setArray(array);
    }

}
