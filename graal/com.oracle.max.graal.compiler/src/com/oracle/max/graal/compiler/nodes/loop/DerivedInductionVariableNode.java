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
package com.oracle.max.graal.compiler.nodes.loop;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.calc.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


/**
 * LinearInductionVariable that is computed in the loops with offset + scale * base.
 * This is computed in the loop only when necessary, puts less pressure on registers.
 */
public class DerivedInductionVariableNode extends LinearInductionVariableNode {
    @Input private InductionVariableNode base;

    public DerivedInductionVariableNode(CiKind kind, ValueNode offset, ValueNode scale, InductionVariableNode base, Graph graph) {
        super(kind, offset, scale, graph);
        setBase(base);
    }

    public InductionVariableNode base() {
        return base;
    }

    public void setBase(InductionVariableNode base) {
        updateUsages(this.base, base);
        this.base = base;
    }

    public ValueNode offset() {
        return a();
    }

    public void setOffset(ValueNode offset) {
        setA(offset);
    }

    public ValueNode scale() {
        return b();
    }

    public void setScale(ValueNode scale) {
        setB(scale);
    }

    @Override
    public LoopBeginNode loopBegin() {
        return base().loopBegin();
    }

    @Override
    public void peelOneIteration() {
        // nop
    }

    /**
     * This will apply strength reduction to this induction variable but will augment register pressure in the loop.
     * @return the new BasicInductionVariable
     */
    public BasicInductionVariableNode toBasicInductionVariable() {
        InductionVariableNode base = base();
        if (base instanceof DerivedInductionVariableNode) {
            base = ((DerivedInductionVariableNode) base).toBasicInductionVariable();
        }
        ValueNode init;
        ValueNode stride;
        LoopCounterNode counter;
        if (base instanceof BasicInductionVariableNode) {
            BasicInductionVariableNode basic = (BasicInductionVariableNode) base;
            // let the canonicalizer do its job with this
            init = IntegerArithmeticNode.add(offset(), IntegerArithmeticNode.mul(scale(), basic.init()));
            stride = IntegerArithmeticNode.mul(scale(), basic.stride());
            counter = basic.loopCounter();
        } else {
            assert base instanceof LoopCounterNode;
            init = offset();
            stride = scale();
            counter = (LoopCounterNode) base;
        }
        BasicInductionVariableNode newBIV = new BasicInductionVariableNode(kind, init, stride, counter, graph());
        this.replaceAndDelete(newBIV);
        return newBIV;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LoweringOp.class) {
            return (T) LOWERING;
        }
        return super.lookup(clazz);
    }

    private static final LoweringOp LOWERING = new LoweringOp() {
        @Override
        public void lower(Node n, CiLoweringTool tool) {
            DerivedInductionVariableNode div = (DerivedInductionVariableNode) n;
            IntegerArithmeticNode computed = IntegerArithmeticNode.add(div.offset(), IntegerArithmeticNode.mul(div.scale(), div.base()));
            div.replaceAtNonIVUsages(computed);
        }
    };
}
