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

import com.oracle.max.graal.compiler.ir.Phi.PhiType;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.compiler.phases.LoweringPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


/**
 * LinearInductionVariable that is computed in the loops thanks to Phi(init, this + stride).
 * This will keep at least one register busy in the whole loop body
 */
public class BasicInductionVariable extends LinearInductionVariable {
    public static final BIVLoweringOp LOWERING = new BIVLoweringOp();
    @Input private LoopCounter loopCounter;

    public BasicInductionVariable(CiKind kind, Value init, Value stride, LoopCounter counter, Graph graph) {
        super(kind, init, stride, graph);
        setLoopCounter(counter);
    }

    public LoopCounter loopCounter() {
        return loopCounter;
    }

    public void setLoopCounter(LoopCounter loopCounter) {
        updateUsages(this.loopCounter, loopCounter);
        this.loopCounter = loopCounter;
    }

    public Value init() {
        return a();
    }

    public void setInit(Value init) {
        setA(init);
    }

    public Value stride() {
        return b();
    }

    public void setStride(Value stride) {
        setB(stride);
    }

    @Override
    public LoopBegin loopBegin() {
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
    public DerivedInductionVariable toDerivedInductionVariable() {
        DerivedInductionVariable newDIV = new DerivedInductionVariable(kind, init(), stride(), loopCounter(), graph());
        this.replaceAndDelete(newDIV);
        return newDIV;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LoweringOp.class) {
            return (T) LOWERING;
        }
        if (clazz == CanonicalizerOp.class) {
            return (T) CANON;
        }
        return super.lookup(clazz);
    }

    public static class BIVLoweringOp implements LoweringOp {
        @Override
        public void lower(Node n, CiLoweringTool tool) {
            BasicInductionVariable biv = (BasicInductionVariable) n;
            Phi phi = this.ivToPhi(biv.loopBegin(), biv.init(), biv.stride(), biv.kind);
            biv.replaceAtNonIVUsages(phi);
        }

        public Phi ivToPhi(LoopBegin loopBegin, Value init, Value stride, CiKind kind) {
            Phi phi = new Phi(kind, loopBegin, PhiType.Value, loopBegin.graph());
            IntegerArithmeticNode after = IntegerArithmeticNode.add(phi, stride);
            phi.addInput(init);
            phi.addInput(after);
            return phi;
        }
    }

    private static CanonicalizerOp CANON = new CanonicalizerOp() {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            BasicInductionVariable biv = (BasicInductionVariable) node;
            if (biv.init().isConstant() && biv.init().asConstant().asLong() == 0
                            && biv.stride().isConstant() && biv.stride().asConstant().asLong() == 1) {
                return biv.loopCounter();
            }
            return biv;
        }
    };
}
