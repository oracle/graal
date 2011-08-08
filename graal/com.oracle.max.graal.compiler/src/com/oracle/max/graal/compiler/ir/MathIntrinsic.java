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

import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public class MathIntrinsic extends FloatingNode {

    public enum Operation {
        ABS, SQRT,
    }

    @Input private Value x;

    private final Operation operation;

    public Value x() {
        return x;
    }

    public void setX(Value x) {
        updateUsages(this.x, x);
        this.x = x;
    }

    public Operation operation() {
        return operation;
    }

    public MathIntrinsic(Value x, Operation op, Graph graph) {
        super(x.kind, graph);
        setX(x);
        this.operation = op;
    }

    // for copying
    private MathIntrinsic(CiKind kind, Operation op, Graph graph) {
        super(kind, graph);
        this.operation = op;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMathIntrinsic(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash1(operation.hashCode(), x);
    }

    @Override
    public boolean valueEqual(Node i) {
        if (i instanceof MathIntrinsic) {
            MathIntrinsic mi = (MathIntrinsic) i;
            return (operation() == mi.operation && x() == mi.x());
        }
        return false;
    }

    @Override
    public Node copy(Graph into) {
        return new MathIntrinsic(kind, operation, into);
    }

}
