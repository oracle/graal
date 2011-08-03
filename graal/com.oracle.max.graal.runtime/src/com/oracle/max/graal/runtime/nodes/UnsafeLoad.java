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
package com.oracle.max.graal.runtime.nodes;

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.LoweringPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * Load of a value from a location specified as an offset relative to an object.
 */
public class UnsafeLoad extends StateSplit {

    private static final int INPUT_COUNT = 2;
    private static final int INPUT_OBJECT = 0;
    private static final int INPUT_OFFSET = 1;

    private static final int SUCCESSOR_COUNT = 0;

    public UnsafeLoad(Value object, Value offset, CiKind kind, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setObject(object);
        setOffset(offset);
    }

    public Value object() {
        return (Value) inputs().get(super.inputCount() + INPUT_OBJECT);
    }

    public Value setObject(Value object) {
        return (Value) inputs().set(super.inputCount() + INPUT_OBJECT, object);
    }

    public Value offset() {
        return (Value) inputs().get(super.inputCount() + INPUT_OFFSET);
    }

    public Value setOffset(Value offset) {
        return (Value) inputs().set(super.inputCount() + INPUT_OFFSET, offset);
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
        UnsafeLoad x = new UnsafeLoad(null, null, kind, into);
        super.copyInto(x);
        return x;
    }

}
