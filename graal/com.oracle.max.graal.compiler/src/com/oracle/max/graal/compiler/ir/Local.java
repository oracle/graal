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
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Local} instruction is a placeholder for an incoming argument
 * to a function call.
 */
public final class Local extends FloatingNode {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_START = 0;

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
     * The start node of the graph that this local belongs to. This is used for correctly scheduling the locals.
     */
     private StartNode start() {
        return (StartNode) inputs().get(super.inputCount() + INPUT_START);
    }

     private void setStart(StartNode n) {
         inputs().set(super.inputCount() + INPUT_START, n);
     }

    private final int index;
    private RiType declaredType;

    public Local(CiKind kind, int javaIndex, StartNode start, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.index = javaIndex;
        setStart(start);
    }

    /**
     * Gets the index of this local.
     * @return the index
     */
    public int index() {
        return index;
    }

    /**
     * Sets the declared type of this local, e.g. derived from the signature of the method.
     * @param declaredType the declared type of the local variable
     */
    public void setDeclaredType(RiType declaredType) {
        this.declaredType = declaredType;
    }

    /**
     * Computes the declared type of the result of this instruction, if possible.
     * @return the declared type of the result of this instruction, if it is known; {@code null} otherwise
     */
    @Override
    public RiType declaredType() {
        return declaredType;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLocal(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("local[index ").print(index()).print(']');
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("index", index());
        return properties;
    }

    @Override
    public Node copy(Graph into) {
        Local x = new Local(kind, index, null, into);
        x.setDeclaredType(declaredType());
        return x;
    }
}
