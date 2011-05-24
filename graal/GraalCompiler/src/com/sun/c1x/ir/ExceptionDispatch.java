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
 * This instruction takes an exception object and has two successors:
 * The catchSuccessor is called whenever the exception matches the given type, otherwise otherSuccessor is called.
 */
public final class ExceptionDispatch extends BlockEnd {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_EXCEPTION = 0;

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
     * The instruction producing the exception object.
     */
     public Value exception() {
        return (Value) inputs().get(super.inputCount() + INPUT_EXCEPTION);
    }

    public Value setException(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_EXCEPTION, n);
    }

    private final RiType catchType;

    /**
     * Constructs a new ExceptionDispatch instruction.
     */
    public ExceptionDispatch(Value exception, Instruction catchSuccessor, Instruction otherSuccessor, RiType catchType, Graph graph) {
        super(CiKind.Int, 2, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setException(exception);
        setBlockSuccessor(0, otherSuccessor);
        setBlockSuccessor(1, catchSuccessor);
        this.catchType = catchType;
    }

    public RiType catchType() {
        return catchType;
    }

    /**
     * Gets the block corresponding to the catch block.
     * @return the true successor
     */
    public BlockBegin catchSuccessor() {
        return blockSuccessor(1);
    }

    /**
     * Gets the block corresponding to the rest of the dispatch chain.
     * @return the false successor
     */
    public BlockBegin otherSuccessor() {
        return blockSuccessor(0);
    }

    /**
     * Gets the block corresponding to the specified outcome of the branch.
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public BlockBegin successor(boolean istrue) {
        return blockSuccessor(istrue ? 1 : 0);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitExceptionDispatch(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("exception_dispatch ").
        print(exception()).
        print(' ').
        print("instanceof").
        print(' ').
        print(catchType().name()).
        print(" then B").
        print(blockSuccessors().get(1).blockID).
        print(" else B").
        print(blockSuccessors().get(0).blockID);
    }

    @Override
    public String shortName() {
        return "Dispatch " + catchType().name();
    }


}
