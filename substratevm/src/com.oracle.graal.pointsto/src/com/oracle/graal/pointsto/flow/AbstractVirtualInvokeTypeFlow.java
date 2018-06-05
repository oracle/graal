/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;

public abstract class AbstractVirtualInvokeTypeFlow extends InvokeTypeFlow {

    protected final ConcurrentLightHashSet<AnalysisMethod> callees;

    protected AbstractVirtualInvokeTypeFlow(Invoke invoke, MethodCallTargetNode target,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invoke, target, actualParameters, actualReturn, location);
        assert target.invokeKind() == InvokeKind.Virtual || target.invokeKind() == InvokeKind.Interface;

        callees = new ConcurrentLightHashSet<>();
    }

    protected AbstractVirtualInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, AbstractVirtualInvokeTypeFlow original) {
        super(bb, methodFlows, original);
        callees = new ConcurrentLightHashSet<>();
    }

    @Override
    public boolean addState(BigBang bb, TypeState add, boolean postFlow) {
        throw AnalysisError.shouldNotReachHere("The VirtualInvokeTypeFlow should not be updated directly.");
    }

    @Override
    public void update(BigBang bb) {
        throw AnalysisError.shouldNotReachHere("The VirtualInvokeTypeFlow should not be updated directly.");
    }

    @Override
    public abstract void onObservedUpdate(BigBang bb);

    protected boolean addCallee(AnalysisMethod callee) {
        boolean add = callees.addElement(callee);
        if (this.isClone()) {
            // if this is a clone, register the callee with the original invoke
            ((AbstractVirtualInvokeTypeFlow) originalInvoke).addCallee(callee);
        }
        return add;
    }

    @Override
    public final Collection<AnalysisMethod> getCallees() {
        return callees.getElements();
    }

    @Override
    public String toString() {
        return "VirtualInvoke<" + getSource().targetMethod().format("%h.%n") + ">" + ":" + getState();
    }
}
