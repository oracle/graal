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

/**
 * The ValueAnchor instruction keeps non-CFG nodes above a certain point in the graph.
 */
public final class ValueAnchor extends FixedNodeWithNext {

    @NodeInput
    private Value object;

    public Value object() {
        return object;
    }

    public void setObject(Value x) {
        updateUsages(object, x);
        object = x;
    }

    public ValueAnchor(Value object, Graph graph) {
        super(CiKind.Illegal, graph);
        setObject(object);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitValueAnchor(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("value_anchor ").print(object());
    }

    @Override
    public Node copy(Graph into) {
        ValueAnchor x = new ValueAnchor(null, into);
        super.copyInto(x);
        return x;
    }
}
