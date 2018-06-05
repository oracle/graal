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

import static jdk.vm.ci.common.JVMCIError.guarantee;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

public abstract class InvokeTypeFlow extends TypeFlow<MethodCallTargetNode> {

    protected final BytecodeLocation location;

    /**
     * Actual parameters passed to the callee.
     */
    protected final TypeFlow<?>[] actualParameters;

    /**
     * Result type flow returned by the callee.
     */
    protected ActualReturnTypeFlow actualReturn;

    protected final Invoke invoke;

    protected final InvokeTypeFlow originalInvoke;

    protected InvokeTypeFlow(Invoke invoke, MethodCallTargetNode target, TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(target, null);

        this.originalInvoke = null;
        this.location = location;
        this.invoke = invoke;
        this.actualParameters = actualParameters;
        this.actualReturn = actualReturn;

        getTargetMethod().registerAsInvoked(this);
    }

    protected InvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, InvokeTypeFlow original) {
        super(original, methodFlows);

        this.originalInvoke = original;
        this.location = original.location;
        this.invoke = original.invoke;

        actualReturn = original.getActualReturn() != null ? (ActualReturnTypeFlow) methodFlows.lookupCloneOf(bb, original.getActualReturn()) : null;

        actualParameters = new TypeFlow<?>[original.actualParameters.length];
        for (int i = 0; i < original.actualParameters.length; i++) {
            if (original.getActualParameter(i) != null) {
                actualParameters[i] = methodFlows.lookupCloneOf(bb, original.getActualParameter(i));
            }
        }
    }

    public BytecodeLocation getLocation() {
        return location;
    }

    public DebugContext getDebug() {
        return invoke.asNode().getDebug();
    }

    public AnalysisMethod getTargetMethod() {
        return (AnalysisMethod) getSource().targetMethod();
    }

    public int actualParametersCount() {
        return actualParameters.length;
    }

    public Invoke invoke() {
        return invoke;
    }

    public TypeFlow<?>[] getActualParameters() {
        return actualParameters;
    }

    public TypeFlow<?> getReceiver() {
        return actualParameters[0];
    }

    public TypeFlow<?> getActualParameter(int index) {
        return actualParameters[index];
    }

    public TypeFlow<?> getActualReturn() {
        return actualReturn;
    }

    public void setActualReturn(ActualReturnTypeFlow actualReturn) {
        this.actualReturn = actualReturn;
    }

    public TypeFlow<?> getResult() {
        return actualReturn;
    }

    @Override
    public boolean addState(BigBang bb, TypeState add) {
        /* Only a clone should be updated */
        assert this.isClone();
        return super.addState(bb, add);
    }

    protected void updateReceiver(BigBang bb, MethodFlowsGraph calleeFlows, AnalysisObject receiverObject) {
        TypeState receiverTypeState = TypeState.forExactType(bb, receiverObject, false);
        updateReceiver(bb, calleeFlows, receiverTypeState);
    }

    protected void updateReceiver(BigBang bb, MethodFlowsGraph calleeFlows, TypeState receiverTypeState) {
        FormalReceiverTypeFlow formalReceiverFlow = calleeFlows.getFormalReceiver();
        if (formalReceiverFlow != null) {
            formalReceiverFlow.addReceiverState(bb, receiverTypeState);
        }

        if (PointstoOptions.DivertParameterReturningMethod.getValue(bb.getOptions())) {
            ParameterNode paramNode = calleeFlows.getMethod().getTypeFlow().getReturnedParameter();
            if (actualReturn != null && paramNode != null && paramNode.index() == 0) {
                actualReturn.addState(bb, receiverTypeState);
            }
        }

    }

    protected void linkCallee(BigBang bb, boolean isStatic, MethodFlowsGraph calleeFlows) {

        // iterate over the actual parameters in caller context
        for (int i = 0; i < actualParameters.length; i++) {
            TypeFlow<?> actualParam = actualParameters[i];

            // get the formal parameter from the specific clone
            TypeFlow<?> formalParam = calleeFlows.getParameter(i);
            /*
             * The link between the receiver object and 'this' parameter of instance methods is a
             * non-state-transfer link. The link only exists for a proper iteration of type flow
             * graphs, but the state update of 'this' parameters is achieved through direct state
             * update in VirtualInvokeTypeFlow.update and SpecialInvokeTypeFlow.update by calling
             * FormalReceiverTypeFlow.addReceiverState. This happens because the formal receiver ,
             * i.e., 'this' parameter, state must ONLY reflect those objects of the actual receiver
             * that generated the context for the method clone which it belongs to. A direct link
             * would instead transfer all the objects of compatible type from the actual receiver to
             * the formal receiver.
             */
            if (actualParam != null && formalParam != null /* && (i != 0 || isStatic) */) {
                // create the use link:
                // (formalParam, callerContext) -> (actualParam, calleeContext)
                // Note: the callerContext is an implicit property of the current InvokeTypeFlow
                // clone
                actualParam.addUse(bb, formalParam);
            }
        }

        if (actualReturn != null) {
            if (PointstoOptions.DivertParameterReturningMethod.getValue(bb.getOptions())) {
                ParameterNode paramNode = calleeFlows.getMethod().getTypeFlow().getReturnedParameter();
                if (paramNode != null) {
                    if (isStatic || paramNode.index() != 0) {
                        TypeFlow<?> actualParam = actualParameters[paramNode.index()];
                        actualParam.addUse(bb, actualReturn);
                    }
                    // else {
                    // receiver object state is transfered in updateReceiver()
                    // }
                } else {
                    /*
                     * The callee may have a return type, hence the actualReturn is non-null, but it
                     * might throw an exception instead of returning, hence the formal return is
                     * null.
                     */
                    if (calleeFlows.getResult() != null) {
                        calleeFlows.getResult().addUse(bb, actualReturn);
                    }
                }
            } else {
                /*
                 * The callee may have a return type, hence the actualReturn is non-null, but it
                 * might throw an exception instead of returning, hence the formal return is null.
                 */
                if (calleeFlows.getResult() != null) {
                    calleeFlows.getResult().addUse(bb, actualReturn);
                }
            }
        }

        assert this.isClone() && originalInvoke != null;
        calleeFlows.getMethod().registerAsImplementationInvoked(originalInvoke);

    }

    /**
     * Returns the callees that were linked at this invoke.
     *
     * If this is an invoke clone it returns the callees registered with the clone. If this is the
     * original invoke it returns the current registered callees of all clones.
     */
    public abstract Collection<AnalysisMethod> getCallees();

    /**
     * Checks if this invoke can be statically bound.
     * </p>
     * First we check if the call target can be trivially statically bound (i.e., it is final or
     * private or static, but not abstract, or the declaring class is final).
     * </p>
     * If it cannot be trivially statically bound then we look at the number of callees. Iff the
     * invoke links to one and only one callee then it can be statically bound. In the corner case
     * where the invoke doesn't link to any callee this method concludes that it cannot be
     * statically bound. If the invoke doesn't link to any callee then it is unreachable, i.e., it
     * has no implementation, and should be removed by the analysis client.
     */
    public boolean canBeStaticallyBound() {
        /*
         * Check whether this method can be trivially statically bound, i.e., without the help of
         * the analysis, but asking the host VM. That means it is final or private or static, but
         * not abstract, or the declaring class is final.
         */
        boolean triviallyStaticallyBound = invoke.callTarget().targetMethod().canBeStaticallyBound();
        if (triviallyStaticallyBound) {
            /*
             * The check bellow is "size <= 1" and not "size == 1" because a method can be reported
             * as trivially statically bound by the host VM but unreachable in the analysis.
             */
            assert getCallees().size() <= 1 : "Statically bound result mismatch between analysis and host VM.";
            return true;
        }
        return getCallees().size() == 1;
    }

    /**
     * Returns the context sensitive method flows for the callees resolved for the invoke type flow.
     * That means that for each callee only those method flows corresponding to contexts reached
     * from this invoke are returned.
     */
    public abstract Collection<MethodFlowsGraph> getCalleesFlows(BigBang bb);

}

abstract class DirectInvokeTypeFlow extends InvokeTypeFlow {

    protected MethodTypeFlow callee;

    /**
     * Context of the caller.
     */
    protected AnalysisContext callerContext;

    protected DirectInvokeTypeFlow(Invoke invoke, MethodCallTargetNode target, TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invoke, target, actualParameters, actualReturn, location);
        callerContext = null;
    }

    protected DirectInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, DirectInvokeTypeFlow original) {
        super(bb, methodFlows, original);
        this.callerContext = methodFlows.context();
    }

    @Override
    public Collection<AnalysisMethod> getCallees() {
        if (callee != null && callee.getMethod().isImplementationInvoked()) {
            return Collections.singletonList(callee.getMethod());
        } else {
            return Collections.emptyList();
        }
    }

}

final class StaticInvokeTypeFlow extends DirectInvokeTypeFlow {

    private AnalysisContext calleeContext;

    protected StaticInvokeTypeFlow(Invoke invoke, MethodCallTargetNode target, TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invoke, target, actualParameters, actualReturn, location);
        calleeContext = null;
        assert target.invokeKind() == InvokeKind.Static;
    }

    protected StaticInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, StaticInvokeTypeFlow original) {
        super(bb, methodFlows, original);
    }

    @Override
    public TypeFlow<MethodCallTargetNode> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new StaticInvokeTypeFlow(bb, methodFlows, this);
    }

    @Override
    public void update(BigBang bb) {
        assert this.isClone();

        /* The static invokes should be updated only once and the callee should be null. */
        guarantee(callee == null, "static invoke updated multiple times!");

        /*
         * Initialize the callee lazily so that if the invoke flow is not reached in this context,
         * i.e. for this clone, there is no callee linked/
         */
        MethodCallTargetNode target = (MethodCallTargetNode) invoke.callTarget();
        callee = ((AnalysisMethod) target.targetMethod()).getTypeFlow();
        // set the callee in the original invoke too
        ((DirectInvokeTypeFlow) originalInvoke).callee = callee;

        calleeContext = bb.contextPolicy().staticCalleeContext(bb, location, callerContext, callee);
        MethodFlowsGraph calleeFlows = callee.addContext(bb, calleeContext, this);
        linkCallee(bb, true, calleeFlows);
    }

    @Override
    public Collection<MethodFlowsGraph> getCalleesFlows(BigBang bb) {
        if (callee == null || calleeContext == null) {
            /* This static invoke was not updated. */
            return Collections.emptyList();
        } else {
            assert calleeContext != null;
            MethodFlowsGraph methodFlows = callee.getFlows(calleeContext);
            return Collections.singletonList(methodFlows);
        }
    }

    @Override
    public String toString() {
        return "StaticInvoke<" + getSource().targetMethod().format("%h.%n") + ">" + ":" + getState();
    }

}

final class SpecialInvokeTypeFlow extends DirectInvokeTypeFlow {

    /**
     * Contexts of the resolved method.
     */
    protected ConcurrentMap<MethodFlowsGraph, Object> calleesFlows;

    protected SpecialInvokeTypeFlow(Invoke invoke, MethodCallTargetNode target, TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn,
                    BytecodeLocation location) {
        super(invoke, target, actualParameters, actualReturn, location);
        assert target.invokeKind() == InvokeKind.Special;
    }

    protected SpecialInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, DirectInvokeTypeFlow original) {
        super(bb, methodFlows, original);
        calleesFlows = new ConcurrentHashMap<>(4, 0.75f, 1);
    }

    @Override
    public TypeFlow<MethodCallTargetNode> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new SpecialInvokeTypeFlow(bb, methodFlows, this);
    }

    @Override
    public boolean addState(BigBang bb, TypeState add, boolean postFlow) {
        throw AnalysisError.shouldNotReachHere("The SpecialInvokeTypeFlow should not be updated directly.");
    }

    @Override
    public void update(BigBang bb) {
        throw AnalysisError.shouldNotReachHere("The SpecialInvokeTypeFlow should not be updated directly.");
    }

    @Override
    public void onObservedUpdate(BigBang bb) {
        assert this.isClone();
        /* The receiver state has changed. Process the invoke. */

        /*
         * Initialize the callee lazily so that if the invoke flow is not reached in this context,
         * i.e. for this clone, there is no callee linked.
         */
        if (callee == null) {
            MethodCallTargetNode target = (MethodCallTargetNode) invoke.callTarget();
            callee = ((AnalysisMethod) target.targetMethod()).getTypeFlow();
            // set the callee in the original invoke too
            ((DirectInvokeTypeFlow) originalInvoke).callee = callee;
        }

        TypeState invokeState = getReceiver().getState();
        for (AnalysisObject receiverObject : invokeState.objects()) {
            AnalysisContext calleeContext = bb.contextPolicy().calleeContext(bb, receiverObject, callerContext, callee);
            MethodFlowsGraph calleeFlows = callee.addContext(bb, calleeContext, this);

            if (calleesFlows.putIfAbsent(calleeFlows, Boolean.TRUE) == null) {
                linkCallee(bb, false, calleeFlows);
            }

            updateReceiver(bb, calleeFlows, receiverObject);
        }
    }

    @Override
    public Collection<MethodFlowsGraph> getCalleesFlows(BigBang bb) {
        return new ArrayList<>(calleesFlows.keySet());
    }

    @Override
    public String toString() {
        return "SpecialInvoke<" + getSource().targetMethod().format("%h.%n") + ">" + ":" + getState();
    }

}
