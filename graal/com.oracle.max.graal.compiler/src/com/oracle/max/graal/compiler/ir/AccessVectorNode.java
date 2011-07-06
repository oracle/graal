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

import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public abstract class AccessVectorNode extends AbstractVectorNode {
    private static final int INPUT_COUNT = 2;
    private static final int INPUT_OBJECT = 0;
    private static final int INPUT_LOCATION = 1;
    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    public void setObject(Value object) {
        inputs().set(super.inputCount() + INPUT_OBJECT, object);
    }

    public Value object() {
        return (Value) inputs().get(super.inputCount() + INPUT_OBJECT);
    }

    public void setLocation(LocationNode object) {
        inputs().set(super.inputCount() + INPUT_LOCATION, object);
    }

    public LocationNode location() {
        return (LocationNode) inputs().get(super.inputCount() + INPUT_LOCATION);
    }

    public AccessVectorNode(CiKind kind, int inputCount, int successorCount, AbstractVectorNode vector, Value object, LocationNode location, Graph graph) {
        super(kind, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, vector, graph);
        setObject(object);
        setLocation(location);
    }
}
