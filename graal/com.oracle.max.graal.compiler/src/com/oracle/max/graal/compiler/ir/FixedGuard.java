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
import com.oracle.max.graal.compiler.ir.Deoptimize.DeoptAction;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public final class FixedGuard extends FixedNodeWithNext {
    private static final int INPUT_COUNT = 1;
    private static final int INPUT_NODE = 0;

    private static final int SUCCESSOR_COUNT = 0;

    /**
     * The instruction that produces the object tested against null.
     */
     public BooleanNode node() {
        return (BooleanNode) inputs().get(super.inputCount() + INPUT_NODE);
    }

    public void setNode(BooleanNode n) {
        inputs().set(super.inputCount() + INPUT_NODE, n);
    }

    public FixedGuard(Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitFixedGuard(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("clip node ").print(node());
    }

    @Override
    public Node copy(Graph into) {
        return new FixedGuard(into);
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
        public Node canonical(Node node) {
            FixedGuard fixedGuard = (FixedGuard) node;
            if (fixedGuard.node() instanceof Constant) {
                Constant c = (Constant) fixedGuard.node();
                if (c.asConstant().asBoolean()) {
                    if (GraalOptions.TraceCanonicalizer) {
                        TTY.println("Removing redundant fixed guard " + fixedGuard);
                    }
                    return fixedGuard.next();
                } else {
                    if (GraalOptions.TraceCanonicalizer) {
                        TTY.println("Replacing fixed guard " + fixedGuard + " with deoptimization node");
                    }
                    return new Deoptimize(DeoptAction.InvalidateRecompile, fixedGuard.graph());
                }
            }
            return fixedGuard;
        }
    };
}
