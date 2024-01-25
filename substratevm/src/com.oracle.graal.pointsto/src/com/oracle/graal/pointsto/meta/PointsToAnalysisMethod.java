/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualParameterTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.ConcurrentLightHashMap;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class PointsToAnalysisMethod extends AnalysisMethod {

    private MethodTypeFlow typeFlow;
    /** The parsing context in which given method was parsed, preserved after analysis. */
    private Object parsingReason;

    private Set<InvokeTypeFlow> invokedBy;
    private Set<InvokeTypeFlow> implementationInvokedBy;
    /**
     * Unique, per method, per multi-method key, context insensitive invoke. The context insensitive
     * invoke uses the receiver type of the method, i.e., its declaring-class. Therefore, this
     * invoke will link with all possible callees.
     */
    @SuppressWarnings("unused") private volatile Object contextInsensitiveVirtualInvoke;
    @SuppressWarnings("unused") private volatile Object contextInsensitiveSpecialInvoke;

    private static final AtomicReferenceFieldUpdater<PointsToAnalysisMethod, Object> VIRTUAL_INVOKE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(PointsToAnalysisMethod.class, Object.class,
                    "contextInsensitiveVirtualInvoke");

    private static final AtomicReferenceFieldUpdater<PointsToAnalysisMethod, Object> SPECIAL_INVOKE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(PointsToAnalysisMethod.class, Object.class,
                    "contextInsensitiveSpecialInvoke");

    public PointsToAnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped) {
        super(universe, wrapped, MultiMethod.ORIGINAL_METHOD, null);
        typeFlow = declaringClass.universe.analysisPolicy().createMethodTypeFlow(this);
    }

    private PointsToAnalysisMethod(AnalysisMethod original, MultiMethodKey multiMethodKey) {
        super(original, multiMethodKey);
        typeFlow = declaringClass.universe.analysisPolicy().createMethodTypeFlow(this);
    }

    @Override
    protected AnalysisMethod createMultiMethod(AnalysisMethod analysisMethod, MultiMethodKey newMultiMethodKey) {
        return new PointsToAnalysisMethod(analysisMethod, newMultiMethodKey);
    }

    @Override
    public void startTrackInvocations() {
        if (invokedBy == null) {
            invokedBy = ConcurrentHashMap.newKeySet();
        }
        if (implementationInvokedBy == null) {
            implementationInvokedBy = ConcurrentHashMap.newKeySet();
        }
    }

    public MethodTypeFlow getTypeFlow() {
        return typeFlow;
    }

    @Override
    public boolean registerAsInvoked(Object reason) {
        assert reason instanceof InvokeTypeFlow || reason instanceof String : reason;
        if (invokedBy != null && reason instanceof InvokeTypeFlow) {
            invokedBy.add((InvokeTypeFlow) reason);
        }
        return super.registerAsInvoked(unwrapInvokeReason(reason));
    }

    @Override
    public boolean registerAsImplementationInvoked(Object reason) {
        assert reason instanceof InvokeTypeFlow || reason instanceof String : reason;
        if (implementationInvokedBy != null && reason instanceof InvokeTypeFlow) {
            implementationInvokedBy.add((InvokeTypeFlow) reason);
        }
        return super.registerAsImplementationInvoked(unwrapInvokeReason(reason));
    }

    /**
     * In general the reason for a method invocation is an {@link InvokeTypeFlow}. Special and
     * virtual root methods have the corresponding context-insensitive invoke reason set. Static
     * root method doesn't have any reason set.
     */
    public static Object unwrapInvokeReason(Object reason) {
        if (reason == null) {
            return "static root method";
        }
        if (reason instanceof InvokeTypeFlow) {
            BytecodePosition source = ((InvokeTypeFlow) reason).getSource();
            return source != null ? source : "root method";
        }
        return reason;
    }

    @Override
    public List<BytecodePosition> getInvokeLocations() {
        List<BytecodePosition> locations = new ArrayList<>();
        for (InvokeTypeFlow invoke : implementationInvokedBy) {
            if (InvokeTypeFlow.isContextInsensitiveVirtualInvoke(invoke)) {
                locations.addAll(((AbstractVirtualInvokeTypeFlow) invoke).getInvokeLocations());
            } else if (invoke.getSource() != null) {
                locations.add(invoke.getSource());
            }
        }
        return locations;
    }

    @Override
    public Iterable<? extends InvokeInfo> getInvokes() {
        return getTypeFlow().getInvokes();
    }

    /**
     * Set parsing reason when the {@link #typeFlow} is initialized. We cannot initialize it in the
     * constructor because that may be too early, before the flows graph is actually initialized and
     * a parsing reason is available.
     */
    public void setParsingReason(Object parsingReason) {
        this.parsingReason = parsingReason;
    }

    @Override
    public Object getParsingReason() {
        return parsingReason;
    }

    public InvokeTypeFlow initAndGetContextInsensitiveInvoke(PointsToAnalysis bb, BytecodePosition originalLocation, boolean isSpecial, MultiMethodKey callerMultiMethodKey) {
        var invokeUpdater = isSpecial ? SPECIAL_INVOKE_UPDATER : VIRTUAL_INVOKE_UPDATER;
        InvokeTypeFlow flow = ConcurrentLightHashMap.computeIfAbsent(this, invokeUpdater, callerMultiMethodKey, key -> {
            InvokeTypeFlow result = createContextInsensitiveInvoke(bb, this, originalLocation, isSpecial, key);
            initContextInsensitiveInvoke(bb, this, result);
            return result;
        });
        return flow;
    }

    /**
     * Create a unique, per method, context insensitive virtual or special invoke. The context
     * insensitive invoke uses the receiver type of the method, i.e., its declaring class. Therefore
     * this invoke will link with all possible callees.
     */
    private static InvokeTypeFlow createContextInsensitiveInvoke(PointsToAnalysis bb, PointsToAnalysisMethod method, BytecodePosition originalLocation, boolean isSpecial,
                    MultiMethodKey callerMultiMethodKey) {
        assert !method.isStatic() : method;
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
        var receiverFlow = receiverType.getTypeFlow(bb, false);

        actualParameters[0] = receiverFlow;
        for (int i = 1; i < actualParameters.length; i++) {
            actualParameters[i] = new ActualParameterTypeFlow(method.getSignature().getParameterType(i - 1));
        }
        ActualReturnTypeFlow actualReturn = null;
        AnalysisType returnType = method.getSignature().getReturnType();
        if (bb.isSupportedJavaKind(returnType.getStorageKind())) {
            actualReturn = new ActualReturnTypeFlow(returnType);
        }

        InvokeTypeFlow invoke;
        if (isSpecial) {
            invoke = bb.analysisPolicy().createSpecialInvokeTypeFlow(originalLocation, receiverType, method, actualParameters,
                            actualReturn, callerMultiMethodKey);
        } else {
            invoke = bb.analysisPolicy().createVirtualInvokeTypeFlow(originalLocation, receiverType, method, actualParameters,
                            actualReturn, callerMultiMethodKey);
        }
        invoke.markAsContextInsensitive();

        return invoke;
    }

    /**
     * Register the context insensitive invoke flow as an observer of its receiver type, i.e., the
     * declaring class of its target method. This also triggers an update of the context insensitive
     * invoke, linking all callees.
     */
    private static void initContextInsensitiveInvoke(PointsToAnalysis bb, AnalysisMethod method, InvokeTypeFlow invoke) {
        AnalysisType receiverType = method.getDeclaringClass();
        var receiverFlow = receiverType.getTypeFlow(bb, false);
        receiverFlow.addObserver(bb, invoke);
    }

    public InvokeTypeFlow getContextInsensitiveVirtualInvoke(MultiMethodKey callerMultiMethodKey) {
        InvokeTypeFlow invoke = ConcurrentLightHashMap.get(this, VIRTUAL_INVOKE_UPDATER, callerMultiMethodKey);
        AnalysisError.guarantee(invoke != null);
        return invoke;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        contextInsensitiveVirtualInvoke = null;
        contextInsensitiveSpecialInvoke = null;
        typeFlow = null;
        invokedBy = null;
        implementationInvokedBy = null;
    }

    @Override
    public boolean isImplementationInvokable() {
        if (!getTypeFlow().flowsGraphCreated()) {
            // flows for direct roots can be created later
            return isDirectRootMethod();
        } else {
            /*
             * If only a stub is ever created for this method, then it will not be invoked.
             *
             * However, for deopt targets it is possible for a root to temporarily be a stub before
             * a full flow graph is created.
             */
            return !getTypeFlow().getMethodFlowsGraphInfo().isStub() || (isDirectRootMethod() && isDeoptTarget());
        }
    }

    @Override
    public void setReturnsAllInstantiatedTypes() {
        super.setReturnsAllInstantiatedTypes();
        assert !getTypeFlow().flowsGraphCreated() : "must call setReturnsAllInstantiatedTypes before typeflow is created";
    }
}
