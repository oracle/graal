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
import com.oracle.max.graal.compiler.ir.NewInstance.*;
import com.oracle.max.graal.compiler.phases.EscapeAnalysisPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public final class MemoryWrite extends MemoryAccess {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_VALUE = 0;
    private static final int SUCCESSOR_COUNT = 0;

    public Value value() {
        return (Value) inputs().get(super.inputCount() + INPUT_VALUE);
    }

    public void setValue(Value v) {
        inputs().set(super.inputCount() + INPUT_VALUE, v);
    }

    public MemoryWrite(CiKind kind, Value value, int displacement, Graph graph) {
        super(kind, displacement, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setValue(value);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMemoryWrite(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("mem write to ").print(location()).print(" with value").print(value());
    }

    @Override
    public Node copy(Graph into) {
        return new MemoryWrite(super.kind, null, displacement(), into);
    }
}
