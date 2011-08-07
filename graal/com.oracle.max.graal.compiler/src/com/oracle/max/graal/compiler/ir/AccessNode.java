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


public abstract class AccessNode extends AbstractMemoryCheckpointNode {
    @Input private Value object;
    @Input private GuardNode guard;
    @Input private LocationNode location;
    @Input private final NodeInputList<Node> dependencies = new NodeInputList<Node>(this);

    public Value object() {
        return object;
    }

    public void setObject(Value x) {
        updateUsages(object, x);
        object = x;
    }

    public GuardNode guard() {
        return guard;
    }

    public void setGuard(GuardNode x) {
        updateUsages(guard, x);
        guard = x;
    }

    public LocationNode location() {
        return location;
    }

    public void setLocation(LocationNode x) {
        updateUsages(location, x);
        location = x;
    }

    public AccessNode(CiKind kind, Value object, LocationNode location, Graph graph) {
        super(kind, graph);
        setLocation(location);
        setObject(object);
    }

    public void addDependency(Node x) {
        dependencies.add(x);
    }

    public NodeInputList<Node> dependencies() {
        return dependencies;
    }

    @Override
    public void print(LogStream out) {
        out.print("mem read from ").print(object());
    }
}
