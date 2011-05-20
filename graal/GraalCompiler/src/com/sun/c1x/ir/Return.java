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

/**
 * The {@code Return} class definition.
 */
public final class Return extends BlockEnd {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_RESULT = 0;

    private static final int SUCCESSOR_COUNT = 1;
    private static final int SUCCESSOR_END = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The instruction that produces the result for the return.
     */
     public Value result() {
        return (Value) inputs().get(super.inputCount() + INPUT_RESULT);
    }

    public Value setResult(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_RESULT, n);
    }

    @Override
    public Instruction next() {
        return null;
    }

    /**
     * Constructs a new Return instruction.
     * @param result the instruction producing the result for this return; {@code null} if this
     * is a void return
     * @param isSafepoint {@code true} if this instruction is a safepoint instruction
     * @param graph
     */
    public Return(Value result, Graph graph) {
        super(result == null ? CiKind.Void : result.kind, null, 0, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setResult(result);
        successors().set(SUCCESSOR_END, graph.end());
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitReturn(this);
    }

    @Override
    public void print(LogStream out) {
        if (result() == null) {
            out.print("return");
        } else {
            out.print(kind.typeChar).print("return ").print(result());
        }
    }
}
