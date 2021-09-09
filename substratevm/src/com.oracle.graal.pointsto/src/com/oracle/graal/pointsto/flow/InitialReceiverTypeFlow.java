/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

/**
 * Represents the type flow for 'this' parameter for entry instance methods.
 */
public class InitialReceiverTypeFlow extends InitialParamTypeFlow {

    public InitialReceiverTypeFlow(AnalysisMethod source, AnalysisType declaredType) {
        super(source, declaredType, 0);
    }

    @Override
    public TypeFlow<AnalysisMethod> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return this;
    }

    @Override
    public TypeState filter(PointsToAnalysis bb, TypeState newState) {
        return newState.forNonNull(bb);
    }

    /**
     * The state of the formal receiver type flow cannot be updated directly, thus
     * {@link FormalReceiverTypeFlow#addReceiverState(PointsToAnalysis, TypeState)} needs to be
     * used. See {@link FormalReceiverTypeFlow#addState(PointsToAnalysis, TypeState)} for a complete
     * explanation.
     */
    @Override
    public boolean addUse(PointsToAnalysis bb, TypeFlow<?> use) {
        boolean useAdded = super.addUse(bb, use);
        if (useAdded) {
            ((FormalReceiverTypeFlow) use).addReceiverState(bb, getState());
        }
        return useAdded;
    }

    @Override
    public void update(PointsToAnalysis bb) {
        TypeState curState = getState();
        for (TypeFlow<?> use : getUses()) {
            assert use instanceof FormalReceiverTypeFlow;
            ((FormalReceiverTypeFlow) use).addReceiverState(bb, curState);
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("InitialReceiverFlow").append("<").append(getState()).append(">");
        return str.toString();
    }

}
