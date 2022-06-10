/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

public class FormalParamTypeFlow extends TypeFlow<BytecodePosition> {
    /**
     * The position of the parameter in the method signature. The holding method can be accessed
     * through the source position.
     */
    protected final int position;

    public FormalParamTypeFlow(BytecodePosition sourcePosition, AnalysisType declaredType, int position) {
        super(sourcePosition, declaredType);
        this.position = position;
    }

    protected FormalParamTypeFlow(FormalParamTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
        this.position = original.position;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new FormalParamTypeFlow(this, methodFlows);
    }

    @Override
    public TypeState filter(PointsToAnalysis bb, TypeState newState) {
        /*
         * If the type flow constraints are relaxed filter the incoming value using the parameter's
         * declared type.
         */
        return declaredTypeFilter(bb, newState);
    }

    public int position() {
        return position;
    }

    @Override
    public String format(boolean withState, boolean withSource) {
        return "Parameter " + position + " of " + method().format("%H.%n(%p)") +
                        (withSource ? " at " + formatSource() : "") +
                        (withState ? " with state <" + getState() + ">" : "");
    }

}
