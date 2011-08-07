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
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.LoweringPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code StoreIndexed} instruction represents a write to an array element.
 */
public final class StoreIndexed extends AccessIndexed {

    @Input    private Value value;

    public Value value() {
        return value;
    }

    public void setValue(Value x) {
        updateUsages(value, x);
        value = x;
    }

    /**
     * Creates a new StoreIndexed instruction.
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length
     * @param elementKind the element type
     * @param value the value to store into the array
     * @param stateAfter the state after executing this instruction
     * @param graph
     */
    public StoreIndexed(Value array, Value index, Value length, CiKind elementKind, Value value, Graph graph) {
        super(CiKind.Void, array, index, length, elementKind, graph);
        setValue(value);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitStoreIndexed(this);
    }

    @Override
    public void print(LogStream out) {
        out.print(array()).print('[').print(index()).print("] := ").print(value()).print(" (").print(kind.typeChar).print(')');
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LoweringOp.class) {
            return (T) LoweringPhase.DELEGATE_TO_RUNTIME;
        }
        return super.lookup(clazz);
    }

    @Override
    public Node copy(Graph into) {
        StoreIndexed x = new StoreIndexed(null, null, null, elementKind(), null, into);
        super.copyInto(x);
        return x;
    }
}
