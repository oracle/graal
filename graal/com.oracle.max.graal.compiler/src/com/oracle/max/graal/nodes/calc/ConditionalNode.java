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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.base.PhiNode.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code Conditional} class represents a comparison that yields one of two values. Note that these nodes are not
 * built directly from the bytecode but are introduced by conditional expression elimination.
 */
public class ConditionalNode extends BinaryNode implements Canonicalizable {

    @Input private BooleanNode condition;

    public BooleanNode condition() {
        return condition;
    }

    public void setCondition(BooleanNode n) {
        updateUsages(condition, n);
        condition = n;
    }

    /**
     * Constructs a new IfOp.
     *
     * @param x the instruction producing the first value to be compared
     * @param condition the condition of the comparison
     * @param y the instruction producing the second value to be compared
     * @param trueValue the value produced if the condition is true
     * @param falseValue the value produced if the condition is false
     */
    public ConditionalNode(BooleanNode condition, ValueNode trueValue, ValueNode falseValue, Graph graph) {
        // TODO: return the appropriate bytecode IF_ICMPEQ, etc
        super(trueValue.kind.meet(falseValue.kind), Bytecodes.ILLEGAL, trueValue, falseValue, graph);
        setCondition(condition);
    }

    // for copying
    private ConditionalNode(CiKind kind, Graph graph) {
        super(kind, Bytecodes.ILLEGAL, null, null, graph);
    }

    public ValueNode trueValue() {
        return x();
    }

    public ValueNode falseValue() {
        return y();
    }

    public void setTrueValue(ValueNode value) {
        setX(value);
    }

    public void setFalseValue(ValueNode value) {
        setY(value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGeneratorOp.class) {
            return (T) LIRGEN;
        }
        return super.lookup(clazz);
    }

    public static class ConditionalStructure {

        public final IfNode ifNode;
        public final PhiNode phi;
        public final MergeNode merge;

        public ConditionalStructure(IfNode ifNode, PhiNode phi, MergeNode merge) {
            this.ifNode = ifNode;
            this.phi = phi;
            this.merge = merge;
        }
    }

    public static ConditionalStructure createConditionalStructure(BooleanNode condition, ValueNode trueValue, ValueNode falseValue) {
        return createConditionalStructure(condition, trueValue, falseValue, 0.5);
    }

    public static ConditionalStructure createConditionalStructure(BooleanNode condition, ValueNode trueValue, ValueNode falseValue, double trueProbability) {
        Graph graph = condition.graph();
        CiKind kind = trueValue.kind.meet(falseValue.kind);
        IfNode ifNode = new IfNode(condition, trueProbability, graph);
        EndNode trueEnd = new EndNode(graph);
        EndNode falseEnd = new EndNode(graph);
        ifNode.setTrueSuccessor(trueEnd);
        ifNode.setFalseSuccessor(falseEnd);
        MergeNode merge = new MergeNode(graph);
        merge.addEnd(trueEnd);
        merge.addEnd(falseEnd);
        PhiNode phi = new PhiNode(kind, merge, PhiType.Value, graph);
        phi.addInput(trueValue);
        phi.addInput(falseValue);
        return new ConditionalStructure(ifNode, phi, merge);
    }

    private static final LIRGeneratorOp LIRGEN = new LIRGeneratorOp() {

        @Override
        public void generate(Node n, LIRGeneratorTool generator) {
            generator.visitConditional((ConditionalNode) n);
        }
    };

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (condition instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) condition;
            if (c.asConstant().asBoolean()) {
                return trueValue();
            } else {
                return falseValue();
            }
        }
        if (trueValue() == falseValue()) {
            return trueValue();
        }
        if (!(this instanceof MaterializeNode) && trueValue() instanceof ConstantNode && falseValue() instanceof ConstantNode && trueValue().kind == CiKind.Int && falseValue().kind == CiKind.Int) {
            int trueInt = trueValue().asConstant().asInt();
            int falseInt = falseValue().asConstant().asInt();
            if (trueInt == 0 && falseInt == 1) {
                reProcess.reProccess(condition); // because we negate it
                return new MaterializeNode(new NegateBooleanNode(condition, graph()), graph());
            } else if (trueInt == 1 && falseInt == 0) {
                return new MaterializeNode(condition, graph());
            }
        } else if (falseValue() instanceof ConstantNode && !(trueValue() instanceof ConstantNode)) {
            ValueNode temp = trueValue();
            setTrueValue(falseValue());
            setFalseValue(temp);
            condition = new NegateBooleanNode(condition, graph());
            setCondition(condition);
        }
        return this;
    }
}
