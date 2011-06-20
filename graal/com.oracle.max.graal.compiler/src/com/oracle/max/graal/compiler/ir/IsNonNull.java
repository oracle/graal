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
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NullCheck} class represents an explicit null check instruction.
 */
public final class IsNonNull extends BooleanNode {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_OBJECT = 0;

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
     * The instruction that produces the object tested against null.
     */
     public Value object() {
        return (Value) inputs().get(super.inputCount() + INPUT_OBJECT);
    }

    public Value setObject(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_OBJECT, n);
    }

    /**
     * Constructs a new NullCheck instruction.
     * @param object the instruction producing the object to check against null
     * @param graph
     */
    public IsNonNull(Value object, Graph graph) {
        super(CiKind.Object, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        assert object == null || object.kind == CiKind.Object;
        setObject(object);
    }

    @Override
    public void accept(ValueVisitor v) {
        // Nothing to do.
    }

    @Override
    public int valueNumber() {
        return Util.hash1(Bytecodes.IFNONNULL, object());
    }

    @Override
    public boolean valueEqual(Node i) {
        if (i instanceof IsNonNull) {
            IsNonNull o = (IsNonNull) i;
            return object() == o.object();
        }
        return false;
    }

    @Override
    public RiType declaredType() {
        // null check does not alter the type of the object
        return object().declaredType();
    }

    @Override
    public RiType exactType() {
        // null check does not alter the type of the object
        return object().exactType();
    }

    @Override
    public void print(LogStream out) {
        out.print("null_check(").print(object()).print(')');
    }

    @Override
    public Node copy(Graph into) {
        return new IsNonNull(null, into);
    }
}
