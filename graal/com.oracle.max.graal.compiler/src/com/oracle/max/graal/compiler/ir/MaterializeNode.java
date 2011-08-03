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

import com.oracle.max.asm.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public final class MaterializeNode extends FloatingNode {

    @NodeInput
    private Value value;

    public Value value() {
        return value;
    }

    public void setValue(Value x) {
        updateUsages(value, x);
        value = x;
    }

    public MaterializeNode(Value value, Graph graph) {
        super(CiKind.Int, graph);
        setValue(value);
    }

    @Override
    public void accept(ValueVisitor v) {
    }

    @Override
    public boolean valueEqual(Node i) {
        return (i instanceof MaterializeNode);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGenerator.LIRGeneratorOp.class) {
            return (T) LIR_GENERATOR_OP;
        }
        return super.lookup(clazz);
    }

    public static final LIRGenerator.LIRGeneratorOp LIR_GENERATOR_OP = new LIRGenerator.LIRGeneratorOp() {

        @Override
        public void generate(Node n, LIRGenerator generator) {
            LIRBlock trueSuccessor = new LIRBlock(new Label(), null);
            generator.emitBooleanBranch(((MaterializeNode) n).value(), trueSuccessor, null, null);
            CiValue result = generator.createResultVariable((Value) n);
            LIRList lir = generator.lir();
            lir.move(CiConstant.FALSE, result);
            Label label = new Label();
            lir.branch(Condition.TRUE, label);
            lir.branchDestination(trueSuccessor.label);
            lir.move(CiConstant.TRUE, result);
            lir.branchDestination(label);
        }
    };

    @Override
    public void print(LogStream out) {
        out.print("materialize(").print(value().toString()).print(')');
    }

    @Override
    public Node copy(Graph into) {
        return new MaterializeNode(null, into);
    }
}
