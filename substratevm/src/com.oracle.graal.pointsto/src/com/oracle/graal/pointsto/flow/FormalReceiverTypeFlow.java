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

import org.graalvm.compiler.nodes.ParameterNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

/**
 * Represents the type flow for 'this' parameter for instance methods.
 */
public class FormalReceiverTypeFlow extends FormalParamTypeFlow {

    public FormalReceiverTypeFlow(ParameterNode source, AnalysisType declaredType, AnalysisMethod method) {
        super(source, declaredType, method, 0);
    }

    public FormalReceiverTypeFlow(FormalReceiverTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
    }

    @Override
    public FormalReceiverTypeFlow copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new FormalReceiverTypeFlow(this, methodFlows);
    }

    @Override
    public TypeState filter(BigBang bb, TypeState newState) {
        /*
         * If the type flow constraints are relaxed filter the incoming value using the receiver's
         * declared type.
         */
        return declaredTypeFilter(bb, newState).forNonNull(bb);
    }

    @Override
    public boolean addState(BigBang bb, TypeState add) {
        /*
         * The formal receiver type flow, i.e., the type flow of the 'this' parameter is linked with
         * the actual receiver type flow through a non-state-transfer link, i.e., a link that exists
         * only for a proper iteration of type flow graphs. This happens because the formal receiver
         * , i.e., 'this' parameter, state must ONLY reflect those objects of the actual receiver
         * that generated the context for the method clone which it belongs to. A direct link would
         * instead transfer all the objects of compatible type from the actual receiver to the
         * formal receiver. The formal receiver state for the non-initial parameters is updated
         * through the FormalReceiverTypeFlow.addReceiverState method invoked directly from
         * VirtualInvokeTypeFlow.update, SpecialInvokeTypeFlow.update or from
         * InitialReceiverTypeFlow.update.
         */
        return false;
    }

    @Override
    protected void onInputSaturated(BigBang bb, TypeFlow<?> input) {
        /*
         * The saturation of the actual receiver doesn't result in the saturation of the formal
         * receiver; some callees, depending how low in the type hierarchies they are, may only see
         * a number of types smaller than the saturation cut-off limit.
         */
    }

    public boolean addReceiverState(BigBang bb, TypeState add) {
        // The type state of a receiver cannot be null.
        return super.addState(bb, add.forNonNull(bb));
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("FormalReceiverFlow").append("[").append(method.format("%H.%n")).append("]").append("<").append(getState()).append(">");
        return str.toString();
    }

}
