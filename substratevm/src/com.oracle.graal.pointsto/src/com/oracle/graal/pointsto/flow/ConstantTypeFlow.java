/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.graal.pointsto.flow;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Models a flow that introduces a constant in the type flow graph. Depending on the analysis policy
 * this could be just the type of the constant, without the object identity.
 */
public class ConstantTypeFlow extends TypeFlow<BytecodePosition> {

    /** The constant state is propagated when the flow is linked in. */
    private final TypeState constantState;

    /** Constant flow has an immutable type state. */
    public ConstantTypeFlow(BytecodePosition position, AnalysisType type, TypeState constantState) {
        super(position, type, TypeState.forEmpty());
        this.constantState = constantState;
        assert source != null;
        assert declaredType == null || declaredType.isInstantiated() : "Type " + declaredType + " not instantiated " + position;
    }

    public ConstantTypeFlow(ConstantTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
        this.constantState = original.constantState;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new ConstantTypeFlow(this, methodFlows);
    }

    @Override
    public void initFlow(PointsToAnalysis bb) {
        /*
         * Inject state into graphs lazily, only after the type flow graph is pruned. When context
         * sensitivity is enabled the default graph is kept clean and used as a template for clones.
         */
        addState(bb, constantState);
    }

    @Override
    public String toString() {
        return "ConstantFlow<" + getState() + ">";
    }
}
