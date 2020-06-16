/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

public abstract class AbstractSpecialInvokeTypeFlow extends DirectInvokeTypeFlow {

    protected AbstractSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
    }

    protected AbstractSpecialInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, AbstractSpecialInvokeTypeFlow original) {
        super(bb, methodFlows, original);
    }

    @Override
    public boolean addState(BigBang bb, TypeState add, boolean postFlow) {
        throw AnalysisError.shouldNotReachHere("The SpecialInvokeTypeFlow should not be updated directly.");
    }

    @Override
    public void update(BigBang bb) {
        throw AnalysisError.shouldNotReachHere("The SpecialInvokeTypeFlow should not be updated directly.");
    }

    /**
     * Initialize the callee lazily so that if the invoke flow is not reached in this context, i.e.
     * for this clone, there is no callee linked.
     */
    protected void initCallee() {
        if (callee == null) {
            callee = targetMethod.getTypeFlow();
            // set the callee in the original invoke too
            ((DirectInvokeTypeFlow) originalInvoke).callee = callee;
        }
    }

    @Override
    public abstract void onObservedUpdate(BigBang bb);

    @Override
    public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
        assert this.isClone();
        /* When the receiver flow saturates start observing the flow of the receiver type. */
        replaceObservedWith(bb, receiverType);
    }

    @Override
    public abstract Collection<MethodFlowsGraph> getCalleesFlows(BigBang bb);

    @Override
    public String toString() {
        return "SpecialInvoke<" + targetMethod.format("%h.%n") + ">" + ":" + getState();
    }

}
