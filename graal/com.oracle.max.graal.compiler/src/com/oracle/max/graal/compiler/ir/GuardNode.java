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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public final class GuardNode extends FloatingNode {
    private static final int INPUT_COUNT = 2;
    private static final int INPUT_ANCHOR = 0;
    private static final int INPUT_NODE = 1;

    private static final int SUCCESSOR_COUNT = 0;

    public FixedNode anchor() {
        return (FixedNode) inputs().get(super.inputCount() + INPUT_ANCHOR);
    }

    public void setAnchor(FixedNode anchor) {
        inputs().set(super.inputCount() + INPUT_ANCHOR, anchor);
    }

    /**
     * The instruction that produces the tested boolean value.
     */
    public BooleanNode node() {
        return (BooleanNode) inputs().get(super.inputCount() + INPUT_NODE);
    }

    public void setNode(BooleanNode n) {
        inputs().set(super.inputCount() + INPUT_NODE, n);
    }

    public GuardNode(BooleanNode node, Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setNode(node);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitGuardNode(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("guard node ").print(node());
    }

    @Override
    public Node copy(Graph into) {
        return new GuardNode(null, into);
    }

    @Override
    public boolean valueEqual(Node i) {
        return i instanceof GuardNode;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static CanonicalizerOp CANONICALIZER = new CanonicalizerOp() {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            GuardNode guard = (GuardNode) node;
            if (guard.node() instanceof Constant) {
                Constant c = (Constant) guard.node();
                if (c.asConstant().asBoolean()) {
                    if (GraalOptions.TraceCanonicalizer) {
                        TTY.println("Removing redundant floating guard " + guard);
                    }
                    return Node.Null;
                }
            }
            return guard;
        }
    };
}
