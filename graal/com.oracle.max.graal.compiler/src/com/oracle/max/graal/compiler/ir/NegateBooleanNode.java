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

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public final class NegateBooleanNode extends BooleanNode {
    private static final int INPUT_COUNT = 1;
    private static final int INPUT_NODE = 0;

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
     * The instruction that produces the array object.
     */
     public BooleanNode value() {
        return (BooleanNode) inputs().get(super.inputCount() + INPUT_NODE);
    }

    public BooleanNode setValue(BooleanNode n) {
        return (BooleanNode) inputs().set(super.inputCount() + INPUT_NODE, n);
    }

    public NegateBooleanNode(BooleanNode value, Graph graph) {
        super(CiKind.Int, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setValue(value);
    }

    @Override
    public void accept(ValueVisitor v) {
    }

    @Override
    public boolean valueEqual(Node i) {
        return i instanceof NegateBooleanNode;
    }

    @Override
    public void print(LogStream out) {
        out.print(value()).print("!");
    }

    @Override
    public Node copy(Graph into) {
        return new NegateBooleanNode(null, into);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static final CanonicalizerOp CANONICALIZER = new CanonicalizerOp() {
        @Override
        public Node canonical(Node node) {
            NegateBooleanNode negateNode = (NegateBooleanNode) node;
            Value value = negateNode.value();
            if (value instanceof NegateBooleanNode) {
                return ((NegateBooleanNode) value).value();
            } else if (value instanceof Constant) {
                return Constant.forBoolean(!value.asConstant().asBoolean(), node.graph());
            } else if (value instanceof Compare) {
                Compare compare = (Compare) value;
                compare.condition = compare.condition.negate();
                return compare;
            }
            return negateNode;
        }
    };
}
