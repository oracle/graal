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

import java.util.Collection;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.common.meta.MultiMethod.MultiMethodKey;

import jdk.vm.ci.code.BytecodePosition;

public abstract class InvokeTypeFlow extends TypeFlow<BytecodePosition> implements InvokeInfo {

    /**
     * Actual parameters passed to the callee.
     */
    protected final TypeFlow<?>[] actualParameters;

    /**
     * Result type flow returned by the callee.
     */
    protected volatile ActualReturnTypeFlow actualReturn;

    protected final InvokeTypeFlow originalInvoke;

    protected final AnalysisType receiverType;
    protected final PointsToAnalysisMethod targetMethod;
    protected boolean isContextInsensitive;

    /**
     * The multi-method key for the method which contains this invoke type flow.
     */
    protected final MultiMethodKey callerMultiMethodKey;

    /**
     * Flag to monitor whether all callees are original or not. This is used to optimize
     * {@link #getOriginalCallees}.
     */
    protected volatile boolean allOriginalCallees = true;

    @SuppressWarnings("this-escape")
    protected InvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey) {
        super(invokeLocation, null);
        this.originalInvoke = null;
        this.receiverType = receiverType;
        this.targetMethod = targetMethod;
        this.actualParameters = actualParameters;
        this.actualReturn = actualReturn;
        this.callerMultiMethodKey = callerMultiMethodKey;

        getTargetMethod().registerAsInvoked(this);
    }

    protected InvokeTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, InvokeTypeFlow original) {
        super(original, methodFlows);

        this.originalInvoke = original;
        this.receiverType = original.receiverType;
        this.targetMethod = original.targetMethod;
        this.callerMultiMethodKey = original.callerMultiMethodKey;

        actualReturn = original.getActualReturn() != null ? (ActualReturnTypeFlow) methodFlows.lookupCloneOf(bb, original.getActualReturn()) : null;

        actualParameters = new TypeFlow<?>[original.actualParameters.length];
        for (int i = 0; i < original.actualParameters.length; i++) {
            if (original.getActualParameter(i) != null) {
                actualParameters[i] = methodFlows.lookupCloneOf(bb, original.getActualParameter(i));
            }
        }
    }

    public boolean linksOnlyOriginalCallees() {
        return allOriginalCallees;
    }

    public void markAsContextInsensitive() {
        isContextInsensitive = true;
    }

    @Override
    public boolean isContextInsensitive() {
        return isContextInsensitive;
    }

    public AnalysisType getReceiverType() {
        return receiverType;
    }

    @Override
    public PointsToAnalysisMethod getTargetMethod() {
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

    public InvokeTypeFlow getOriginalInvoke() {
        return originalInvoke;
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

    public void setActualReturn(PointsToAnalysis bb, boolean isStatic, ActualReturnTypeFlow actualReturn) {
        assert this.actualReturn == null;
        this.actualReturn = actualReturn;
        bb.analysisPolicy().linkActualReturn(bb, isStatic, this);
    }

    public TypeFlow<?> getResult() {
        return actualReturn;
    }

    /**
     * When the type flow constraints are relaxed the receiver object state can contain types that
     * are not part of the receiver's type hierarchy. We filter those out.
     *
     * With saturation enabled, types not part of the hierarchy may always reach the receiver
     * because:
     * <ul>
     * <li>{@link FilterTypeFlow}s saturate to the type of the filter.</li>
     * <li>Instanceof checks can create {@link FilterTypeFlow}s which are not assignable to the
     * receiver type.</li>
     * <li>A receiver type can be attached to different inputs (and FilterTypeFlows) based on the
     * optimizations performed on the graph.</li>
     * </ul>
     *
     * Therefore, under no circumstances can this filtering be removed.
     */
    protected TypeState filterReceiverState(PointsToAnalysis bb, TypeState receiverState) {
        if (bb.analysisPolicy().relaxTypeFlowConstraints()) {
            return TypeState.forIntersection(bb, receiverState, receiverType.getAssignableTypes(true));
        } else {
            // when not filtering, all input types should be assignable
            assert verifyAllAssignable(bb, receiverState) : receiverState;
        }
        return receiverState;
    }

    private boolean verifyAllAssignable(BigBang bb, TypeState receiverState) {
        for (AnalysisType type : receiverState.types(bb)) {
            if (!receiverType.isAssignableFrom(type)) {
                return false;
            }
        }
        return true;
    }

    protected void updateReceiver(PointsToAnalysis bb, MethodFlowsGraphInfo calleeFlows, AnalysisObject receiverObject) {
        TypeState receiverTypeState = TypeState.forExactType(bb, receiverObject, false);
        updateReceiver(bb, calleeFlows, receiverTypeState);
    }

    protected void updateReceiver(PointsToAnalysis bb, MethodFlowsGraphInfo calleeFlows, TypeState receiverTypeState) {
        var analysisPolicy = bb.getHostVM().getMultiMethodAnalysisPolicy();
        var calleeKey = calleeFlows.getMethod().getMultiMethodKey();
        if (analysisPolicy.performParameterLinking(callerMultiMethodKey, calleeKey)) {
            FormalReceiverTypeFlow formalReceiverFlow = calleeFlows.getFormalReceiver();
            if (formalReceiverFlow != null) {
                formalReceiverFlow.addReceiverState(bb, receiverTypeState);
            }
        }

        if (analysisPolicy.performReturnLinking(callerMultiMethodKey, calleeKey) && !analysisPolicy.unknownReturnValue(bb, callerMultiMethodKey, calleeFlows.getMethod())) {
            if (bb.optimizeReturnedParameter()) {
                int paramIndex = calleeFlows.getMethod().getTypeFlow().getReturnedParameterIndex();
                if (actualReturn != null && paramIndex == 0) {
                    /*
                     * The callee returns `this`. Propagate the receiver state to the actual-return.
                     * See also InvokeTypeFlow#linkReturn() for more details.
                     */
                    actualReturn.addState(bb, receiverTypeState);
                }
            }
        }

    }

    protected void linkCallee(PointsToAnalysis bb, boolean isStatic, MethodFlowsGraphInfo calleeFlows) {

        if (bb.getHostVM().getMultiMethodAnalysisPolicy().performParameterLinking(callerMultiMethodKey, calleeFlows.getMethod().getMultiMethodKey())) {
            // iterate over the actual parameters in caller context
            for (int i = 0; i < actualParameters.length; i++) {
                TypeFlow<?> actualParam = actualParameters[i];

                // get the formal parameter from the specific clone
                TypeFlow<?> formalParam = calleeFlows.getParameter(i);
                /*
                 * The link between the receiver object and 'this' parameter of instance methods is
                 * a non-state-transfer link. The link only exists for a proper iteration of type
                 * flow graphs, but the state update of 'this' parameters is achieved through direct
                 * state update in VirtualInvokeTypeFlow.update and SpecialInvokeTypeFlow.update by
                 * calling FormalReceiverTypeFlow.addReceiverState.
                 * 
                 * In other words, while the receiver param (actualParameters[0] when !isStatic) is
                 * linked to the FormalReceiverTypeFlow of the callee, type information is not
                 * propagated along this edge. This is accomplished by overriding the addState
                 * method within FormalReceiverTypeFlow.
                 *
                 * This action is taken because the formal receiver (i.e., 'this' parameter) state
                 * must ONLY reflect those objects of the actual receiver that generated the context
                 * for the method clone which it belongs to. A direct link would instead transfer
                 * all the objects of compatible type from the actual receiver to the formal
                 * receiver.
                 */
                if (actualParam != null && formalParam != null) {
                    // create the use link:
                    // (formalParam, callerContext) -> (actualParam, calleeContext)
                    // Note: the callerContext is an implicit property of the current InvokeTypeFlow
                    // clone
                    if (actualParam.addUse(bb, formalParam)) {
                        if (i == 0 && !isStatic) {
                            maybeSaturateFormalReceiver(bb, actualParam, formalParam);
                        }
                    }
                }
            }
        }

        linkReturn(bb, isStatic, calleeFlows);

        /*
         * Stubs act as placeholders and are not registered as implementation invoked until a full
         * typeflow is created for the method.
         */
        if (!calleeFlows.isStub()) {
            bb.analysisPolicy().registerAsImplementationInvoked(this, calleeFlows.getMethod());
        }

        if (calleeFlows.getMethod().isDelayed()) {
            saturateForOpenTypeWorld(bb);
        }
    }

    /*
     * In the open type world we always propagate saturation of the actual receiver to the formal
     * receiver, even after the saturated invoke was replaced by the context insensitive variant.
     * So, if we are linking the receiver of a context insensitive invoke we immediately notify the
     * formal receiver of saturation.
     */
    private void maybeSaturateFormalReceiver(PointsToAnalysis bb, TypeFlow<?> actualParam, TypeFlow<?> formalParam) {
        if (!bb.getHostVM().isClosedTypeWorld() && this.isContextInsensitive()) {
            AnalysisError.guarantee(actualParam instanceof AllInstantiatedTypeFlow && formalParam instanceof FormalReceiverTypeFlow);
            /* The actualParam is not technically saturated, but we treat it as such. */
            actualParam.notifyUseOfSaturation(bb, formalParam);
        }
    }

    public void linkReturn(PointsToAnalysis bb, boolean isStatic, MethodFlowsGraphInfo calleeFlows) {
        /*
         * If actualReturn is null, then there is no linking necessary. Later, if a typeflow is
         * created for the return, then {@code setActualReturn} will perform all necessary linking.
         */
        if (actualReturn != null && bb.getHostVM().getMultiMethodAnalysisPolicy().performReturnLinking(callerMultiMethodKey, calleeFlows.getMethod().getMultiMethodKey())) {
            if (bb.getHostVM().getMultiMethodAnalysisPolicy().unknownReturnValue(bb, callerMultiMethodKey, calleeFlows.getMethod())) {
                /*
                 * When there is an unknown return value we must be conservative.
                 */
                actualReturn.declaredType.getTypeFlow(bb, true).addUse(bb, actualReturn);
            } else if (bb.optimizeReturnedParameter()) {
                int paramNodeIndex = calleeFlows.getMethod().getTypeFlow().getReturnedParameterIndex();
                if (paramNodeIndex != -1) {
                    if (isStatic || paramNodeIndex != 0) {
                        TypeFlow<?> actualParam = actualParameters[paramNodeIndex];
                        actualParam.addUse(bb, actualReturn);
                    } else {
                        /*
                         * The callee returns `this`. The formal-receiver state is updated in
                         * InvokeTypeFlow#updateReceiver() for each linked callee and every time the
                         * formal-receiver is updated then the same update state is propagated to
                         * the actual-return. One may think that we could simply add a direct use
                         * link from the formal-receiver in the callee to the actual-return in the
                         * caller to get the state propagation automatically. But that would be
                         * wrong because then the actual-return would get the state from *all* the
                         * other places that callee may be called from, and that would defeat the
                         * purpose of this optimization: we want just the receiver state from the
                         * caller of current invoke to reach the actual-return.
                         */
                    }
                } else {
                    /*
                     * The callee may have a return type, hence the actualReturn is non-null, but it
                     * might throw an exception instead of returning, hence the formal return is
                     * null.
                     */
                    if (calleeFlows.getReturnFlow() != null) {
                        calleeFlows.getReturnFlow().addUse(bb, actualReturn);
                    }
                }
            } else {
                /*
                 * The callee may have a return type, hence the actualReturn is non-null, but it
                 * might throw an exception instead of returning, hence the formal return is null.
                 */
                if (calleeFlows.getReturnFlow() != null) {
                    calleeFlows.getReturnFlow().addUse(bb, actualReturn);
                }
            }
        }
    }

    public static boolean isContextInsensitiveVirtualInvoke(InvokeTypeFlow invoke) {
        return invoke instanceof AbstractVirtualInvokeTypeFlow && invoke.isContextInsensitive();
    }

    /**
     * Returns the callees that were linked at this invoke.
     *
     * If this is an invoke clone it returns the callees registered with the clone. If this is the
     * original invoke it returns the current registered callees of all clones.
     */
    @Override
    public Collection<AnalysisMethod> getOriginalCallees() {
        if (allOriginalCallees) {
            return getAllCallees();
        }

        return getAllCallees().stream().filter(callee -> {
            boolean originalMethod = callee.isOriginalMethod();
            assert !originalMethod || callee.isImplementationInvoked() : callee;
            return originalMethod;
        }).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public abstract Collection<AnalysisMethod> getAllCallees();

    /**
     * Returns all callees which have been computed for this method which should be linked to the
     * return. It is possible that these callees have yet to have their typeflow created.
     */
    public abstract Collection<AnalysisMethod> getCalleesForReturnLinking();

    @Override
    public BytecodePosition getPosition() {
        return getSource();
    }

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
    @Override
    public boolean canBeStaticallyBound() {
        /*
         * Check whether this method can be trivially statically bound, i.e., without the help of
         * the analysis, but asking the host VM. That means it is final or private or static, but
         * not abstract, or the declaring class is final.
         */
        boolean triviallyStaticallyBound = targetMethod.canBeStaticallyBound();
        if (triviallyStaticallyBound) {
            /*
             * The check below is "size <= 1" and not "size == 1" because a method can be reported
             * as trivially statically bound by the host VM but unreachable in the analysis.
             */
            assert getOriginalCallees().size() <= 1 : "Statically bound result mismatch between analysis and host VM.";
            return true;
        }
        return getOriginalCallees().size() == 1;
    }

    /**
     * Returns the context sensitive method flows for the callees resolved for the invoke type flow
     * which are not still in stub form. That means that for each callee only those method flows
     * corresponding to contexts reached from this invoke are returned. Note that callee flows in
     * this list can have a MultiMethodKey different from {@link MultiMethod#ORIGINAL_METHOD}.
     */
    public abstract Collection<MethodFlowsGraph> getAllNonStubCalleesFlows(PointsToAnalysis bb);

    public MultiMethodKey getCallerMultiMethodKey() {
        return callerMultiMethodKey;
    }

    /**
     * Saturates the actual return of the invoke type flow to ensure that the type state represents
     * all the types that could exist in the open world.
     */
    public void saturateForOpenTypeWorld(PointsToAnalysis bb) {
        if (actualReturn != null) {
            actualReturn.enableFlow(bb);
            actualReturn.onSaturated(bb);
        }
    }
}
