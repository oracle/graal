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
package com.oracle.max.graal.nodes.loop;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.base.PhiNode.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;


/**
 * LinearInductionVariable that is computed in the loops thanks to Phi(init, this + stride).
 * This will keep at least one register busy in the whole loop body
 */
public class BasicInductionVariableNode extends LinearInductionVariableNode implements Canonicalizable{
    public static final BIVLoweringOp LOWERING = new BIVLoweringOp();
    @Input private LoopCounterNode loopCounter;

    public BasicInductionVariableNode(CiKind kind, ValueNode init, ValueNode stride, LoopCounterNode counter, Graph graph) {
        super(kind, init, stride, graph);
        setLoopCounter(counter);
    }

    public LoopCounterNode loopCounter() {
        return loopCounter;
    }

    public void setLoopCounter(LoopCounterNode loopCounter) {
        updateUsages(this.loopCounter, loopCounter);
        this.loopCounter = loopCounter;
    }

    public ValueNode init() {
        return a();
    }

    public void setInit(ValueNode init) {
        setA(init);
    }

    public ValueNode stride() {
        return b();
    }

    public void setStride(ValueNode stride) {
        setB(stride);
    }

    @Override
    public LoopBeginNode loopBegin() {
        return loopCounter().loopBegin();
    }

    @Override
    public void peelOneIteration() {
        this.setInit(IntegerArithmeticNode.add(init(), stride()));
    }

    /**
     * Will lessen the register pressure but augment the code complexity with a multiplication.
     * @return the new DerivedInductionVariable
     */
    public DerivedInductionVariableNode toDerivedInductionVariable() {
        DerivedInductionVariableNode newDIV = new DerivedInductionVariableNode(kind, init(), stride(), loopCounter(), graph());
        this.replaceAndDelete(newDIV);
        return newDIV;
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (this.init().isConstant() && this.init().asConstant().asLong() == 0
                        && this.stride().isConstant() && this.stride().asConstant().asLong() == 1) {
            return this.loopCounter();
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LoweringOp.class) {
            return (T) LOWERING;
        }
        return super.lookup(clazz);
    }

    public static class BIVLoweringOp implements LoweringOp {
        @Override
        public void lower(Node n, CiLoweringTool tool) {
            BasicInductionVariableNode biv = (BasicInductionVariableNode) n;
            PhiNode phi = this.ivToPhi(biv.loopBegin(), biv.init(), biv.stride(), biv.kind);
            biv.replaceAtNonIVUsages(phi);
        }

        public PhiNode ivToPhi(LoopBeginNode loopBegin, ValueNode init, ValueNode stride, CiKind kind) {
            PhiNode phi = new PhiNode(kind, loopBegin, PhiType.Value, loopBegin.graph());
            IntegerArithmeticNode after = IntegerArithmeticNode.add(phi, stride);
            phi.addInput(init);
            phi.addInput(after);
            return phi;
        }
    }
}
