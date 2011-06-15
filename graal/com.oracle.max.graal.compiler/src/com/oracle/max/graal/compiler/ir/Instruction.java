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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * Denotes an instruction node in the IR, which is a {@link Value} that
 * can be added to a basic block (whereas other {@link Value} nodes such as {@link Phi} and
 * {@link Local} cannot be added to basic blocks).
 *
 * Subclasses of instruction represent arithmetic and object operations,
 * control flow operators, phi statements, method calls, the start of basic blocks, and
 * the end of basic blocks.
 *
 * Instruction nodes are chained together in a basic block through the embedded
 * {@link Instruction#next} field. An Instruction may also have a list of {@link ExceptionHandler}s.
 */
public abstract class Instruction extends FixedNode {

    private static final int INPUT_COUNT = 0;

    private static final int SUCCESSOR_COUNT = 1;
    public static final int SUCCESSOR_NEXT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * Links to next instruction in a basic block, to {@code null} if this instruction is the end of a basic block or to
     * itself if not in a block.
     */
    public FixedNode next() {
        return (FixedNode) successors().get(super.successorCount() + SUCCESSOR_NEXT);
    }

    public Node setNext(FixedNode next) {
        return successors().set(super.successorCount() + SUCCESSOR_NEXT, next);
    }

    public int nextIndex() {
        return super.successorCount() + SUCCESSOR_NEXT;
    }


    public static final int SYNCHRONIZATION_ENTRY_BCI = -1;

    /**
     * Constructs a new instruction with the specified value type.
     * @param kind the value type for this instruction
     * @param inputCount
     * @param successorCount
     */
    public Instruction(CiKind kind, int inputCount, int successorCount, Graph graph) {
        super(kind, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        GraalMetrics.HIRInstructions++;
    }

    /**
     * Gets the state after the instruction, if it is recorded.
     * @return the state after the instruction
     */
    public FrameState stateAfter() {
        return null;
    }
}
