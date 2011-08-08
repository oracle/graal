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
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code NegateOp} instruction negates its operand.
 */
public final class Negate extends FloatingNode implements Node.GlobalValueNumberable {
    private static final NegateCanonicalizerOp CANONICALIZER = new NegateCanonicalizerOp();

    @Input    private Value x;

    public Value x() {
        return x;
    }

    public void setX(Value x) {
        updateUsages(this.x, x);
        this.x = x;
    }

    /**
     * Creates new NegateOp instance.
     * @param x the instruction producing the value that is input to this instruction
     */
    public Negate(Value x, Graph graph) {
        super(x.kind, graph);
        setX(x);
    }

    // for copying
    private Negate(CiKind kind, Graph graph) {
        super(kind, graph);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNegate(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("- ").print(x());
    }

    @Override
    public Node copy(Graph into) {
        Negate x = new Negate(kind, into);
        return x;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static class NegateCanonicalizerOp implements CanonicalizerOp {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            Negate negate = (Negate) node;
            Value x = negate.x();
            Graph graph = negate.graph();
            if (x.isConstant()) {
                switch (x.kind) {
                    case Int: return Constant.forInt(-x.asConstant().asInt(), graph);
                    case Long: return Constant.forLong(-x.asConstant().asLong(), graph);
                    case Float: return Constant.forFloat(-x.asConstant().asFloat(), graph);
                    case Double: return Constant.forDouble(-x.asConstant().asDouble(), graph);
                }
            }
            if (x instanceof Negate) {
                return ((Negate) x).x();
            }
            return negate;
        }
    }
}
