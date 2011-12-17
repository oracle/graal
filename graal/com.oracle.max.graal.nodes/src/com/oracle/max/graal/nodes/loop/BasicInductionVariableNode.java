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
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

/**
 * LinearInductionVariable that is computed in the loops thanks to Phi(init, this + stride).
 * This will keep at least one register busy in the whole loop body
 */
public class BasicInductionVariableNode extends LinearInductionVariableNode implements Canonicalizable {

    @Input private LoopCounterNode loopCounter;

    public BasicInductionVariableNode(CiKind kind, ValueNode init, ValueNode stride, LoopCounterNode loopCounter) {
        super(kind, stride, init);
        this.loopCounter = loopCounter;
    }

    public LoopCounterNode loopCounter() {
        return loopCounter;
    }

    public ValueNode init() {
        return b();
    }

    public void setInit(ValueNode init) {
        setB(init);
    }

    @Override
    public ValueNode stride() {
        return a();
    }

    public void setStride(ValueNode stride) {
        setA(stride);
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
        DerivedInductionVariableNode newDIV = graph().add(new DerivedInductionVariableNode(kind(), init(), stride(), loopCounter()));
        this.replaceAndDelete(newDIV);
        return newDIV;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (this.init().isConstant() && this.init().asConstant().asLong() == 0 && this.stride().isConstant() && this.stride().asConstant().asLong() == 1) {
            return this.loopCounter();
        }
        return this;
    }

    @Override
    public ValueNode lowerInductionVariable() {
        return ivToPhi(loopBegin(), init(), stride(), kind());
    }

    @Override
    public boolean isNextIteration(InductionVariableNode other) {
        if (other instanceof LoopCounterNode && this.loopCounter() == other) {
            if (this.init().isConstant() && this.init().asConstant().asLong() == -1 && this.stride().isConstant() && this.stride().asConstant().asLong() == 1) {
                return true;
            }
        } else if (other instanceof LinearInductionVariableNode) {
            if ((other instanceof BasicInductionVariableNode && ((BasicInductionVariableNode) other).loopCounter() == loopCounter())
                            || (other instanceof DerivedInductionVariableNode && ((DerivedInductionVariableNode) other).base() == loopCounter())) {
                LinearInductionVariableNode liv = (LinearInductionVariableNode) other;
                if (liv.a() == stride() && IntegerAddNode.isIntegerAddition(liv.b(), init(), stride())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static PhiNode ivToPhi(LoopBeginNode loopBegin, ValueNode init, ValueNode stride, CiKind kind) {
        PhiNode phi = loopBegin.graph().add(new PhiNode(kind, loopBegin, PhiType.Value));
        IntegerArithmeticNode after = IntegerArithmeticNode.add(phi, stride);
        phi.addInput(init);
        phi.addInput(after);
        return phi;
    }

    @Override
    public StrideDirection strideDirection() {
        ValueNode stride = stride();
        if (stride.isConstant()) {
            long val = stride.asConstant().asLong();
            if (val > 0) {
                return StrideDirection.Up;
            }
            if (val < 0) {
                return StrideDirection.Down;
            }
        }
        return null;
    }

    @Override
    public ValueNode minValue(FixedNode point) {
        StrideDirection strideDirection = strideDirection();
        if (strideDirection == StrideDirection.Up) {
            return init();
        } else if (strideDirection == StrideDirection.Down) {
            return searchExtremum(point, StrideDirection.Down);
        }
        return null;
    }

    @Override
    public ValueNode maxValue(FixedNode point) {
        StrideDirection strideDirection = strideDirection();
        if (strideDirection == StrideDirection.Down) {
            return init();
        } else if (strideDirection == StrideDirection.Up) {
            return searchExtremum(point, StrideDirection.Up);
        }
        return null;
    }
}
