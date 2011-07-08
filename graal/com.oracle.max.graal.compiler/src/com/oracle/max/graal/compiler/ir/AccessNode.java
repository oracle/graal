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

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public abstract class AccessNode extends AbstractMemoryCheckpointNode {
    private static final int INPUT_COUNT = 3;
    private static final int INPUT_NODE = 0;
    private static final int INPUT_LOCATION = 1;
    private static final int INPUT_GUARD = 2;

    private static final int SUCCESSOR_COUNT = 0;

    private LocationNode location;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    public Value object() {
        return (Value) inputs().get(super.inputCount() + INPUT_NODE);
    }

    public Value setObject(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_NODE, n);
    }

    public GuardNode guard() {
        return (GuardNode) inputs().get(super.inputCount() + INPUT_GUARD);
    }

    public void setGuard(GuardNode n) {
        inputs().set(super.inputCount() + INPUT_GUARD, n);
    }

    public LocationNode location() {
        return (LocationNode) inputs().get(super.inputCount() + INPUT_LOCATION);
    }

    public void setLocation(LocationNode n) {
        inputs().set(super.inputCount() + INPUT_LOCATION, n);
    }

    public AccessNode(CiKind kind, Value object, LocationNode location, int inputCount, int successorCount, Graph graph) {
        super(kind, INPUT_COUNT + inputCount, SUCCESSOR_COUNT + successorCount, graph);
        setLocation(location);
        setObject(object);
    }

    public void addDependency(Node x) {
        variableInputs().add(x);
    }

    public List<Node> dependencies() {
        return variableInputs();
    }

    @Override
    public void print(LogStream out) {
        out.print("mem read from ").print(object());
    }
}
