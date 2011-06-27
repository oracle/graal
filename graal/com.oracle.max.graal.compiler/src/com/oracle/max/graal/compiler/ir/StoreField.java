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
import com.oracle.max.graal.compiler.phases.LoweringPhase.LoweringOp;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code StoreField} instruction represents a write to a static or instance field.
 */
public final class StoreField extends AccessField {

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
     * The value that is written to the field.
     */
     public Value value() {
        return (Value) inputs().get(super.inputCount() + INPUT_VALUE);
    }

    public Value setValue(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_VALUE, n);
    }

    /**
     * Creates a new LoadField instance.
     * @param object the receiver object
     * @param field the compiler interface field
     * @param value the instruction representing the value to store to the field
     * @param stateAfter the state after the field access
     * @param graph
     */
    public StoreField(Value object, RiField field, Value value, Graph graph) {
        super(CiKind.Void, object, field, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setValue(value);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitStoreField(this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(java.lang.Class<T> clazz) {
        if (clazz == LoweringOp.class) {
            return (T) LoweringPhase.DELEGATE_TO_RUNTIME;
        }
        return super.lookup(clazz);
    };

    @Override
    public void print(LogStream out) {
        out.print(object()).
        print(".").
        print(field().name()).
        print(" := ").
        print(value()).
        print(" [type: ").print(CiUtil.format("%h.%n:%t", field(), false)).
        print(']');
    }

    @Override
    public Node copy(Graph into) {
        return new StoreField(null, field, null, into);
    }
}
