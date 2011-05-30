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
 * This class represents a value within the HIR graph, including local variables, phis, and
 * all other instructions.
 */
public abstract class Value extends Node {

    /**
     * The kind of this value. This is {@link CiKind#Void} for instructions that produce no value.
     * This kind is guaranteed to be a {@linkplain CiKind#stackKind() stack kind}.
     */
    public final CiKind kind;

    private boolean isNonNull;

    protected CiValue operand = CiValue.IllegalValue;

    /**
     * Creates a new value with the specified kind.
     * @param kind the type of this value
     * @param inputCount
     * @param successorCount
     * @param graph
     */
    public Value(CiKind kind, int inputCount, int successorCount, Graph graph) {
        super(inputCount, successorCount, graph);
        assert kind == kind.stackKind() : kind + " != " + kind.stackKind();
        this.kind = kind;
    }

    ///////////////
    // TODO: remove when Value class changes are completed

    @Override
    public Node copy(Graph into) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * Checks whether this instruction produces a value which is guaranteed to be non-null.
     * @return {@code true} if this instruction's value is not null
     */
    public boolean isNonNull() {
        return isNonNull;
    }

    public void setNonNull(boolean isNonNull) {
        this.isNonNull = isNonNull;
    }

    /**
     * Checks whether this value is a constant (i.e. it is of type {@link Constant}.
     * @return {@code true} if this value is a constant
     */
    public final boolean isConstant() {
        return this instanceof Constant;
    }

    /**
     * Checks whether this value represents the null constant.
     * @return {@code true} if this value represents the null constant
     */
    public final boolean isNullConstant() {
        return this instanceof Constant && ((Constant) this).value.isNull();
    }

    /**
     * Convert this value to a constant if it is a constant, otherwise return null.
     * @return the {@link CiConstant} represented by this value if it is a constant; {@code null}
     * otherwise
     */
    public final CiConstant asConstant() {
        if (this instanceof Constant) {
            return ((Constant) this).value;
        }
        return null;
    }

    /**
     * Gets the LIR operand associated with this instruction.
     * @return the LIR operand for this instruction
     */
    public final CiValue operand() {
        return operand;
    }

    /**
     * Sets the LIR operand associated with this instruction.
     * @param operand the operand to associate with this instruction
     */
    public final void setOperand(CiValue operand) {
        assert this.operand.isIllegal() : "operand cannot be set twice";
        assert operand != null && operand.isLegal() : "operand must be legal";
        assert operand.kind.stackKind() == this.kind;
        this.operand = operand;
    }

    /**
     * Clears the LIR operand associated with this instruction.
     */
    public final void clearOperand() {
        this.operand = CiValue.IllegalValue;
    }

    /**
     * Computes the exact type of the result of this instruction, if possible.
     * @return the exact type of the result of this instruction, if it is known; {@code null} otherwise
     */
    public RiType exactType() {
        return null; // default: unknown exact type
    }

    /**
     * Computes the declared type of the result of this instruction, if possible.
     * @return the declared type of the result of this instruction, if it is known; {@code null} otherwise
     */
    public RiType declaredType() {
        return null; // default: unknown declared type
    }

    /**
     * Apply the specified closure to all the input values of this instruction.
     * @param closure the closure to apply
     */
    public void inputValuesDo(ValueClosure closure) {
        for (int i = 0; i < inputs().size(); i++) {
            inputs().set(i, closure.apply((Value) inputs().get(i)));
        }
        for (int i = 0; i < successors().size(); i++) {
            successors().set(i, closure.apply((Value) successors().get(i)));
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("#");
        builder.append(id());
        builder.append(' ');
        if (id() < 10) {
            builder.append(' ');
        }
        builder.append(getClass().getSimpleName());
        builder.append(" [").append(flagsToString()).append("]");
        return builder.toString();
    }

    public String flagsToString() {
        StringBuilder sb = new StringBuilder();
        if (isNonNull()) {
            sb.append("NonNull");
        }
        return sb.toString();
    }

    /**
     * Compute the value number of this Instruction. Local and global value numbering
     * optimizations use a hash map, and the value number provides a hash code.
     * If the instruction cannot be value numbered, then this method should return
     * {@code 0}.
     * @return the hashcode of this instruction
     */
    public int valueNumber() {
        return 0;
    }

    /**
     * Checks that this instruction is equal to another instruction for the purposes
     * of value numbering.
     * @param i the other instruction
     * @return {@code true} if this instruction is equivalent to the specified
     * instruction w.r.t. value numbering
     */
    public boolean valueEqual(Node i) {
        return false;
    }

    /**
     * This method supports the visitor pattern by accepting a visitor and calling the
     * appropriate {@code visit()} method.
     *
     * @param v the visitor to accept
     */
    public abstract void accept(ValueVisitor v);

    public abstract void print(LogStream out);

}
