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

import java.util.Collection;
import java.util.Collections;

import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ParameterNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaKind;

public abstract class InvokeTypeFlow extends TypeFlow<BytecodePosition> {

    protected final BytecodeLocation location;

    /**
     * Actual parameters passed to the callee.
     */
    protected final TypeFlow<?>[] actualParameters;

    /**
     * Result type flow returned by the callee.
     */
    protected ActualReturnTypeFlow actualReturn;

    protected final InvokeTypeFlow originalInvoke;

    protected final AnalysisType receiverType;
    protected final AnalysisMethod targetMethod;

    /**
     * The {@link #source} is used for all sorts of call stack printing (for error messages and
     * diagnostics), so we must have a non-null {@link BytecodePosition}.
     */
    public static BytecodePosition findBytecodePosition(Invoke invoke) {
        assert invoke != null;
        BytecodePosition result = invoke.asFixedNode().getNodeSourcePosition();
        if (result == null) {
            result = new BytecodePosition(null, invoke.asFixedNode().graph().method(), invoke.bci());
        }
        return result;
    }

    protected InvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invokeLocation, null);
        this.originalInvoke = null;
        this.location = location;
        this.receiverType = receiverType;
        this.targetMethod = targetMethod;
        this.actualParameters = actualParameters;
        this.actualReturn = actualReturn;

        getTargetMethod().registerAsInvoked(this);
    }

    protected InvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, InvokeTypeFlow original) {
        super(original, methodFlows);

        this.originalInvoke = original;
        this.location = original.location;
        this.receiverType = original.receiverType;
        this.targetMethod = original.targetMethod;

        actualReturn = original.getActualReturn() != null ? (ActualReturnTypeFlow) methodFlows.lookupCloneOf(bb, original.getActualReturn()) : null;

        actualParameters = new TypeFlow<?>[original.actualParameters.length];
        for (int i = 0; i < original.actualParameters.length; i++) {
            if (original.getActualParameter(i) != null) {
                actualParameters[i] = methodFlows.lookupCloneOf(bb, original.getActualParameter(i));
            }
        }
    }

    public abstract boolean isDirectInvoke();

    public AnalysisType getReceiverType() {
        return receiverType;
    }

    public AnalysisMethod getTargetMethod() {
        return targetMethod;
    }

    public int actualParametersCount() {
        return actualParameters.length;
    }

    public TypeFlow<?>[] getActualParameters() {
        return actualParameters;
    }

    public TypeFlow<?> getReceiver() {
        return actualParameters[0];
    }

    @Override
    public void setObserved(TypeFlow<?> newReceiver) {
        actualParameters[0] = newReceiver;
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

    /**
     * When the type flow constraints are relaxed the receiver object state can contain types that
     * are not part of the receiver's type hierarchy. We filter those out.
     */
    protected TypeState filterReceiverState(BigBang bb, TypeState invokeState) {
        if (bb.analysisPolicy().relaxTypeFlowConstraints()) {
            return TypeState.forIntersection(bb, invokeState, receiverType.getTypeFlow(bb, true).getState());
        }
        return invokeState;
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

        assert isClone() || isContextInsensitiveVirtualInvoke(this);
        if (isContextInsensitiveVirtualInvoke(this)) {
            calleeFlows.getMethod().registerAsImplementationInvoked(this);
        } else {
            calleeFlows.getMethod().registerAsImplementationInvoked(originalInvoke);
        }
    }

    public static boolean isContextInsensitiveVirtualInvoke(InvokeTypeFlow invoke) {
        return invoke instanceof AbstractVirtualInvokeTypeFlow && ((AbstractVirtualInvokeTypeFlow) invoke).isContextInsensitive();
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
        boolean triviallyStaticallyBound = targetMethod.canBeStaticallyBound();
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

    /**
     * Create an unique, per method, context insensitive invoke. The context insensitive invoke uses
     * the receiver type of the method, i.e., its declaring class. Therefore this invoke will link
     * with all possible callees.
     */
    public static AbstractVirtualInvokeTypeFlow createContextInsensitiveInvoke(BigBang bb, AnalysisMethod method, BytecodePosition originalLocation) {
        /*
         * The context insensitive invoke has actual parameters and return flows that will be linked
         * to the original actual parameters and return flows at each call site where it will be
         * swapped in.
         */
        TypeFlow<?>[] actualParameters = new TypeFlow<?>[method.getSignature().getParameterCount(true)];

        AnalysisType receiverType = method.getDeclaringClass();
        /*
         * The receiver flow of the context insensitive invoke is the type flow of its declaring
         * class.
         */
        AllInstantiatedTypeFlow receiverFlow = receiverType.getTypeFlow(bb, false);

        actualParameters[0] = receiverFlow;
        for (int i = 1; i < actualParameters.length; i++) {
            actualParameters[i] = new ActualParameterTypeFlow((AnalysisType) method.getSignature().getParameterType(i - 1, null));
        }
        ActualReturnTypeFlow actualReturn = null;
        AnalysisType returnType = (AnalysisType) method.getSignature().getReturnType(null);
        if (returnType.getStorageKind() == JavaKind.Object) {
            actualReturn = new ActualReturnTypeFlow(returnType);
        }

        AbstractVirtualInvokeTypeFlow invoke = bb.analysisPolicy().createVirtualInvokeTypeFlow(originalLocation, receiverType, method,
                        actualParameters, actualReturn, BytecodeLocation.UNKNOWN_BYTECODE_LOCATION);
        invoke.markAsContextInsensitive();

        return invoke;
    }

    /**
     * Register the context insensitive invoke flow as an observer of its receiver type, i.e., the
     * declaring class of its target method. This also triggers an update of the context insensitive
     * invoke, linking all callees.
     */
    public static void initContextInsensitiveInvoke(BigBang bb, AnalysisMethod method, InvokeTypeFlow invoke) {
        AnalysisType receiverType = method.getDeclaringClass();
        AllInstantiatedTypeFlow receiverFlow = receiverType.getTypeFlow(bb, false);
        receiverFlow.addObserver(bb, invoke);
    }

}

abstract class DirectInvokeTypeFlow extends InvokeTypeFlow {

    public MethodTypeFlow callee;

    /**
     * Context of the caller.
     */
    protected AnalysisContext callerContext;

    protected DirectInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
        callerContext = null;
    }

    protected DirectInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, DirectInvokeTypeFlow original) {
        super(bb, methodFlows, original);
        this.callerContext = methodFlows.context();
    }

    @Override
    public final boolean isDirectInvoke() {
        return true;
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

    protected StaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
        calleeContext = null;
    }

    protected StaticInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, StaticInvokeTypeFlow original) {
        super(bb, methodFlows, original);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new StaticInvokeTypeFlow(bb, methodFlows, this);
    }

    @Override
    public void update(BigBang bb) {
        assert this.isClone();

        /* The static invokes should be updated only once and the callee should be null. */
        guarantee(callee == null, "static invoke updated multiple times!");

        // Unlinked methods can not be parsed
        if (!targetMethod.getWrapped().getDeclaringClass().isLinked()) {
            return;
        }

        /*
         * Initialize the callee lazily so that if the invoke flow is not reached in this context,
         * i.e. for this clone, there is no callee linked/
         */
        callee = targetMethod.getTypeFlow();
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
        return "StaticInvoke<" + targetMethod.format("%h.%n") + ">" + ":" + getState();
    }

}
