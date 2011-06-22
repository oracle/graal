/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public abstract class MemoryAccess extends StateSplit {
    private static final int INPUT_COUNT = 2;
    private static final int INPUT_NODE = 0;
    private static final int INPUT_GUARD = 1;

    private static final int SUCCESSOR_COUNT = 0;

    private int displacement;
    private CiKind valueKind;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    /**
     * The instruction that produces the object tested against null.
     */
     public Value location() {
        return (Value) inputs().get(super.inputCount() + INPUT_NODE);
    }

    public Value setLocation(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_NODE, n);
    }

    /**
     * The instruction that produces the object tested against null.
     */
     public GuardNode guard() {
        return (GuardNode) inputs().get(super.inputCount() + INPUT_GUARD);
    }

    public void setGuard(GuardNode n) {
        inputs().set(super.inputCount() + INPUT_GUARD, n);
    }

    public int displacement() {
        return displacement;
    }

    public CiKind valueKind() {
        return valueKind;
    }

    public MemoryAccess(CiKind kind, Value location, int displacement, int inputCount, int successorCount, Graph graph) {
        super(kind.stackKind(), INPUT_COUNT + inputCount, SUCCESSOR_COUNT + successorCount, graph);
        this.displacement = displacement;
        this.valueKind = kind;
        setLocation(location);
    }

    @Override
    public void print(LogStream out) {
        out.print("mem read from ").print(location());
    }
}
