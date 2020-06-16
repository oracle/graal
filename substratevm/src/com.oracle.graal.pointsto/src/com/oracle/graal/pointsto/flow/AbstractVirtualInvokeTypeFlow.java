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

import static com.oracle.graal.pointsto.util.ConcurrentLightHashSet.addElement;
import static com.oracle.graal.pointsto.util.ConcurrentLightHashSet.getElements;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

public abstract class AbstractVirtualInvokeTypeFlow extends InvokeTypeFlow {
    private static final AtomicReferenceFieldUpdater<AbstractVirtualInvokeTypeFlow, Object> CALLEES_UPDATER = AtomicReferenceFieldUpdater
                    .newUpdater(AbstractVirtualInvokeTypeFlow.class, Object.class, "callees");

    private static final AtomicReferenceFieldUpdater<AbstractVirtualInvokeTypeFlow, Object> INVOKE_LOCATIONS_UPDATER = AtomicReferenceFieldUpdater
                    .newUpdater(AbstractVirtualInvokeTypeFlow.class, Object.class, "invokeLocations");

    @SuppressWarnings("unused") protected volatile Object callees;

    private boolean isContextInsensitive;

    /**
     * The context insensitive invoke needs to keep track of all the locations it is swapped in. For
     * all the other invokes this is null, their location is the source node location.
     */
    @SuppressWarnings("unused") protected volatile Object invokeLocations;

    protected AbstractVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
    }

    protected AbstractVirtualInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, AbstractVirtualInvokeTypeFlow original) {
        super(bb, methodFlows, original);
    }

    public void markAsContextInsensitive() {
        isContextInsensitive = true;
    }

    public boolean isContextInsensitive() {
        return isContextInsensitive;
    }

    public boolean addInvokeLocation(BytecodePosition invokeLocation) {
        if (invokeLocation != null) {
            return addElement(this, INVOKE_LOCATIONS_UPDATER, invokeLocation);
        }
        return false;
    }

    /** The context insensitive virual invoke returns all the locations where it is swapped in. */
    public Collection<BytecodePosition> getInvokeLocations() {
        if (isContextInsensitive) {
            return getElements(this, INVOKE_LOCATIONS_UPDATER);
        } else {
            return Collections.singleton(getSource());
        }
    }

    @Override
    public final boolean isDirectInvoke() {
        return false;
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

    @Override
    public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
        assert this.isClone();
        /* When the receiver flow saturates start observing the flow of the receiver type. */
        replaceObservedWith(bb, receiverType);
    }

    protected boolean addCallee(AnalysisMethod callee) {
        boolean add = addElement(this, CALLEES_UPDATER, callee);
        if (this.isClone()) {
            // if this is a clone, register the callee with the original invoke
            ((AbstractVirtualInvokeTypeFlow) originalInvoke).addCallee(callee);
        }
        return add;
    }

    @Override
    public Collection<AnalysisMethod> getCallees() {
        return getElements(this, CALLEES_UPDATER);
    }

    @Override
    public String toString() {
        return "VirtualInvoke<" + targetMethod.format("%h.%n") + ">" + ":" + getState();
    }
}
