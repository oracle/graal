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


public final class LoopCounter extends FloatingNode {

    private static final int INPUT_COUNT = 3;
    private static final int INPUT_MERGE = 0;
    private static final int INPUT_INIT = 1;
    private static final int INPUT_STRIDE = 2;

    private static final int SUCCESSOR_COUNT = 0;

    public LoopCounter(CiKind kind, Value init, Value stride, LoopBegin loop, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setInit(init);
        setStride(stride);
        setLoopBegin(loop);
    }

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    public Value init() {
        return (Value) inputs().get(super.inputCount() + INPUT_INIT);
    }

    public Value setInit(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_INIT, n);
    }

    public Value stride() {
        return (Value) inputs().get(super.inputCount() + INPUT_STRIDE);
    }

    public Value setStride(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_STRIDE, n);
    }

    public LoopBegin loopBegin() {
        return (LoopBegin) inputs().get(super.inputCount() + INPUT_MERGE);
    }

    public Value setLoopBegin(LoopBegin n) {
        return (Value) inputs().set(super.inputCount() + INPUT_MERGE, n);
    }

    @Override
    public void accept(ValueVisitor v) {
        // TODO Auto-generated method stub

    }

    @Override
    public void print(LogStream out) {
        out.print("loopcounter [").print(init()).print(",+").print(stride()).print("]");

    }

    @Override
    public Node copy(Graph into) {
        return new LoopCounter(kind, null, null, null, into);
    }

}
