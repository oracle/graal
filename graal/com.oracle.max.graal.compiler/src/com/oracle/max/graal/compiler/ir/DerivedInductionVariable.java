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

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


/**
 * LinearInductionVariable that is computed in the loops with offset + scale * base.
 * This is computed in the loop only when necessary, puts less pressure on registers.
 */
public class DerivedInductionVariable extends LinearInductionVariable {
    @Input private InductionVariable base;

    public DerivedInductionVariable(CiKind kind, ValueNode offset, ValueNode scale, InductionVariable base, Graph graph) {
        super(kind, offset, scale, graph);
        setBase(base);
    }

    public InductionVariable base() {
        return base;
    }

    public void setBase(InductionVariable base) {
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
    public LoopBegin loopBegin() {
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
    public BasicInductionVariable toBasicInductionVariable() {
        InductionVariable base = base();
        if (base instanceof DerivedInductionVariable) {
            base = ((DerivedInductionVariable) base).toBasicInductionVariable();
        }
        ValueNode init;
        ValueNode stride;
        LoopCounter counter;
        if (base instanceof BasicInductionVariable) {
            BasicInductionVariable basic = (BasicInductionVariable) base;
            // let the canonicalizer do its job with this
            init = IntegerArithmeticNode.add(offset(), IntegerArithmeticNode.mul(scale(), basic.init()));
            stride = IntegerArithmeticNode.mul(scale(), basic.stride());
            counter = basic.loopCounter();
        } else {
            assert base instanceof LoopCounter;
            init = offset();
            stride = scale();
            counter = (LoopCounter) base;
        }
        BasicInductionVariable newBIV = new BasicInductionVariable(kind, init, stride, counter, graph());
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
            DerivedInductionVariable div = (DerivedInductionVariable) n;
            IntegerArithmeticNode computed = IntegerArithmeticNode.add(div.offset(), IntegerArithmeticNode.mul(div.scale(), div.base()));
            div.replaceAtNonIVUsages(computed);
        }
    };
}
