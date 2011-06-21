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

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * Denotes the beginning of a basic block, and holds information
 * about the basic block, including the successor and
 * predecessor blocks, exception handlers, liveness information, etc.
 */
public class Merge extends StateSplit{

    private static final int INPUT_COUNT = 0;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    /**
     * Constructs a new Merge at the specified bytecode index.
     * @param bci the bytecode index of the start
     * @param blockID the ID of the block
     * @param graph
     */
    public Merge(Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
    }

    protected Merge(int inputCount, int successorCount, Graph graph) {
        super(CiKind.Illegal, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMerge(this);
    }

    public int endIndex(EndNode end) {
        assert inputs().variablePart().contains(end);
        return inputs().variablePart().indexOf(end);
    }

    public void addEnd(EndNode end) {
        inputs().variablePart().add(end);
    }

    public int endCount() {
        return inputs().variablePart().size();
    }

    public EndNode endAt(int index) {
        return (EndNode) inputs().variablePart().get(index);
    }

    public Iterable<Node> mergePredecessors() {
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new Iterator<Node>() {
                    int i = 0;
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                    @Override
                    public Node next() {
                        return Merge.this.endAt(i++);
                    }
                    @Override
                    public boolean hasNext() {
                        return i < Merge.this.endCount();
                    }
                };
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("merge #");
        builder.append(id());
        builder.append(" [");

        builder.append("]");

        builder.append(" -> ");
        boolean hasSucc = false;
        for (Node s : this.successors()) {
            if (hasSucc) {
                builder.append(", ");
            }
            builder.append("#");
            if (s != null) {
                builder.append(s.id());
            } else {
                builder.append("null");
            }
            hasSucc = true;
        }
        return builder.toString();
    }

    public void printWithoutPhis(LogStream out) {
        // print block id
        out.print("B").print(id()).print(" ");

        // print flags
        StringBuilder sb = new StringBuilder(8);
        if (sb.length() != 0) {
            out.print('(').print(sb.toString()).print(')');
        }

        // print block bci range
        out.print('[').print(-1).print(", ").print(-1).print(']');

        // print block successors
        //if (end != null && end.blockSuccessors().size() > 0) {
            out.print(" .");
            for (Node successor : this.successors()) {
                if (successor instanceof Value) {
                    out.print((Value) successor);
                } else {
                    out.print(successor.toString());
                }
            }
        //}

        // print predecessors
//        if (!blockPredecessors().isEmpty()) {
//            out.print(" pred:");
//            for (Instruction pred : blockPredecessors()) {
//                out.print(pred.block());
//            }
//        }
    }

    @Override
    public void print(LogStream out) {

        printWithoutPhis(out);

        // print phi functions
        boolean hasPhisInLocals = false;
        boolean hasPhisOnStack = false;

        //if (end() != null && end().stateAfter() != null) {
            FrameState state = stateAfter();

            int i = 0;
            while (!hasPhisOnStack && i < state.stackSize()) {
                Value value = state.stackAt(i);
                hasPhisOnStack = isPhiAtBlock(value);
                if (value != null && !(value instanceof Phi && ((Phi) value).isDead())) {
                    i += value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }

            for (i = 0; !hasPhisInLocals && i < state.localsSize();) {
                Value value = state.localAt(i);
                hasPhisInLocals = isPhiAtBlock(value);
                // also ignore illegal HiWords
                if (value != null && !(value instanceof Phi && ((Phi) value).isDead())) {
                    i += value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }
        //}

        // print values in locals
        if (hasPhisInLocals) {
            out.println();
            out.println("Locals:");

            int j = 0;
            while (j < state.localsSize()) {
                Value value = state.localAt(j);
                if (value != null) {
                    out.println(stateString(j, value));
                    // also ignore illegal HiWords
                    if (value instanceof Phi && ((Phi) value).isDead()) {
                        j +=  1;
                    } else {
                        j += value.kind.sizeInSlots();
                    }
                } else {
                    j++;
                }
            }
            out.println();
        }

        // print values on stack
        if (hasPhisOnStack) {
            out.println();
            out.println("Stack:");
            int j = 0;
            while (j < stateAfter().stackSize()) {
                Value value = stateAfter().stackAt(j);
                if (value != null) {
                    out.println(stateString(j, value));
                    j += value.kind.sizeInSlots();
                } else {
                    j++;
                }
            }
        }

    }

    /**
     * Determines if a given instruction is a phi whose {@linkplain Phi#merge() join block} is a given block.
     *
     * @param value the instruction to test
     * @param block the block that may be the join block of {@code value} if {@code value} is a phi
     * @return {@code true} if {@code value} is a phi and its join block is {@code block}
     */
    private boolean isPhiAtBlock(Value value) {
        return value instanceof Phi && ((Phi) value).merge() == this;
    }


    /**
     * Formats a given instruction as a value in a {@linkplain FrameState frame state}. If the instruction is a phi defined at a given
     * block, its {@linkplain Phi#valueCount() inputs} are appended to the returned string.
     *
     * @param index the index of the value in the frame state
     * @param value the frame state value
     * @param block if {@code value} is a phi, then its inputs are formatted if {@code block} is its
     *            {@linkplain Phi#merge() join point}
     * @return the instruction representation as a string
     */
    public String stateString(int index, Value value) {
        StringBuilder sb = new StringBuilder(30);
        sb.append(String.format("%2d  %s", index, Util.valueString(value)));
        if (value instanceof Phi) {
            Phi phi = (Phi) value;
            // print phi operands
            if (phi.merge() == this) {
                sb.append(" [");
                for (int j = 0; j < phi.valueCount(); j++) {
                    sb.append(' ');
                    Value operand = phi.valueAt(j);
                    if (operand != null) {
                        sb.append(Util.valueString(operand));
                    } else {
                        sb.append("NULL");
                    }
                }
                sb.append("] ");
            }
        }
        return sb.toString();
    }

    @Override
    public String shortName() {
        return "Merge #" + id();
    }

    @Override
    public Node copy(Graph into) {
        assert getClass() == Merge.class : "copy of " + getClass();
        return new Merge(into);
    }

    public void removeEnd(EndNode pred) {
        int predIndex = inputs().variablePart().indexOf(pred);
        assert predIndex != -1;
        inputs().variablePart().remove(predIndex);

        for (Node usage : usages()) {
            if (usage instanceof Phi) {
                Phi phi = (Phi) usage;
                if (!phi.isDead()) {
                    phi.removeInput(predIndex);
                }
            }
        }
    }

    public int phiPredecessorCount() {
        return endCount();
    }

    public int phiPredecessorIndex(Node pred) {
        EndNode end = (EndNode) pred;
        return endIndex(end);
    }

    public Collection<Phi> phis() {
        return Util.filter(this.usages(), Phi.class);
    }

    public List<Node> phiPredecessors() {
        return Collections.unmodifiableList(inputs().variablePart());
    }
}
