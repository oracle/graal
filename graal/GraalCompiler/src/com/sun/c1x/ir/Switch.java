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

import java.util.*;

import com.oracle.graal.graph.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code Switch} class is the base of both lookup and table switches.
 */
public abstract class Switch extends BlockEnd {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_VALUE = 0;

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
     * The instruction that provides the input value to this switch.
     */
     public Value value() {
        return (Value) inputs().get(super.inputCount() + INPUT_VALUE);
    }

    public Value setValue(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_VALUE, n);
    }

    /**
     * Constructs a new Switch.
     * @param value the instruction that provides the value to be switched over
     * @param successors the list of successors of this switch
     * @param stateBefore the state before the switch
     * @param isSafepoint {@code true} if this switch is a safepoint
     * @param graph
     */
    public Switch(Value value, List<BlockBegin> successors, FrameState stateBefore, boolean isSafepoint, int inputCount, int successorCount, Graph graph) {
        super(CiKind.Illegal, stateBefore, isSafepoint, successors, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        setValue(value);
    }

    /**
     * Gets the number of cases that this switch covers (excluding the default case).
     * @return the number of cases
     */
    public int numberOfCases() {
        return blockSuccessorCount() - 1;
    }

}
