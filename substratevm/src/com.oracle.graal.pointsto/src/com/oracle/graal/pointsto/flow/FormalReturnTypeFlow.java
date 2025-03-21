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
import jdk.vm.ci.meta.JavaKind;

public class FormalReturnTypeFlow extends TypeFlow<BytecodePosition> {
    public FormalReturnTypeFlow(BytecodePosition source, AnalysisType declaredType) {
        super(source, filterUncheckedInterface(declaredType));
    }

    public FormalReturnTypeFlow(FormalReturnTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
    }

    /**
     * Filters the incoming type state using the declared type.
     */
    @Override
    protected TypeState processInputState(PointsToAnalysis bb, TypeState newState) {
        if (declaredType.getJavaKind() == JavaKind.Void) {
            /*
             * Void ReturnTypeFlow has a use edge from the latest predicate, which can propagate
             * random values. We only use this edge to signal that the method can return, we don't
             * care about the actual value. We sanitize it to AnyPrimitive to prevent from
             * primitive/object collisions in addState.
             */
            return newState.isEmpty() ? TypeState.forEmpty() : TypeState.anyPrimitiveState();
        }
        /*
         * Always filter the formal return state with the declared type, even if the type flow
         * constraints are not relaxed. This avoids imprecision caused by MethodHandle API methods
         * which elude static type checks.
         */
        return declaredTypeFilter(bb, newState, false);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new FormalReturnTypeFlow(this, methodFlows);
    }

    @Override
    public String format(boolean withState, boolean withSource) {
        return "Formal return from " + method().format("%H.%n(%p)") +
                        (withSource ? " at " + formatSource() : "") +
                        (withState ? " with state <" + getStateDescription() + ">" : "");
    }
}
