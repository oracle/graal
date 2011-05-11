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
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code Throw} instruction represents a throw of an exception.
 */
public final class Throw extends BlockEnd {

    private static final int INPUT_COUNT = 2;
    private static final int INPUT_EXCEPTION = 0;
    private static final int INPUT_STATE_BEFORE = 1;

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
     * The instruction which produces the exception to throw.
     */
     public Value exception() {
        return (Value) inputs().get(super.inputCount() + INPUT_EXCEPTION);
    }

    public Value setException(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_EXCEPTION, n);
    }

    /**
     * The state for this instruction.
     */
    public FrameState stateBefore() {
        return (FrameState) inputs().get(super.inputCount() + INPUT_STATE_BEFORE);
    }

    public FrameState setStateBefore(FrameState n) {
        return (FrameState) inputs().set(super.inputCount() + INPUT_STATE_BEFORE, n);
    }

    /**
     * Creates a new Throw instruction.
     * @param exception the instruction that generates the exception to throw
     * @param stateAfter the state before the exception is thrown but after the exception object has been popped
     * @param isSafepoint {@code true} if this instruction is a safepoint instruction
     * @param graph
     */
    public Throw(Value exception, boolean isSafepoint, Graph graph) {
        super(CiKind.Illegal, null, isSafepoint, 0, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setException(exception);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitThrow(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("throw ").print(exception());
    }
}
