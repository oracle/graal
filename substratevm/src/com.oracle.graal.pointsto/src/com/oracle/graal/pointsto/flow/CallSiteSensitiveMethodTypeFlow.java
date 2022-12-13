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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.bytecode.BytecodeAnalysisContext;
import com.oracle.graal.pointsto.flow.context.bytecode.BytecodeAnalysisContextPolicy;
import com.oracle.graal.pointsto.flow.context.bytecode.BytecodeSensitiveAnalysisPolicy;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;

public class CallSiteSensitiveMethodTypeFlow extends MethodTypeFlow {

    private final ConcurrentMap<AnalysisContext, MethodFlowsGraph> clonedMethodFlows;
    private final int localCallingContextDepth;

    public CallSiteSensitiveMethodTypeFlow(OptionValues options, PointsToAnalysisMethod method) {
        super(method);
        this.localCallingContextDepth = PointstoOptions.MaxCallingContextDepth.getValue(options);
        this.clonedMethodFlows = new ConcurrentHashMap<>(4, 0.75f, 1);
    }

    /**
     * Add the context, if not already added, and return the method flows clone from that context.
     */
    public MethodFlowsGraph addContext(PointsToAnalysis bb, AnalysisContext calleeContext, InvokeTypeFlow reason) {

        /* Ensure that the method is parsed before attempting to clone it. */
        this.ensureFlowsGraphCreated(bb, reason);
        this.getMethodFlowsGraph().ensureLinearized();

        BytecodeAnalysisContextPolicy contextPolicy = ((BytecodeSensitiveAnalysisPolicy) bb.analysisPolicy()).getContextPolicy();
        AnalysisContext newContext = contextPolicy.peel((BytecodeAnalysisContext) calleeContext, localCallingContextDepth);

        MethodFlowsGraphClone methodFlows = (MethodFlowsGraphClone) clonedMethodFlows.get(newContext);
        if (methodFlows == null) {
            MethodFlowsGraphClone newFlows = new MethodFlowsGraphClone(method, newContext);
            newFlows.cloneOriginalFlows(bb);
            MethodFlowsGraphClone oldFlows = (MethodFlowsGraphClone) clonedMethodFlows.putIfAbsent(newContext, newFlows);
            methodFlows = oldFlows != null ? oldFlows : newFlows;
            if (oldFlows == null) {
                /*
                 * Link uses only the clone is registered since linking uses may trigger updates to
                 * the current method in the current context.
                 */
                methodFlows.linkClones(bb);
            }
        }

        return methodFlows;
    }

    @Override
    protected void initFlowsGraph(PointsToAnalysis bb) {
        // nothing to do, cloning does all the initialization
    }

    public int getLocalCallingContextDepth() {
        return localCallingContextDepth;
    }

    @Override
    public Collection<MethodFlowsGraph> getFlows() {
        return clonedMethodFlows.values();
    }

    public MethodFlowsGraph getFlows(AnalysisContext calleeContext) {
        return clonedMethodFlows.get(calleeContext);
    }

    /**
     * Get a type state containing the union of states over all the clones of the original flow.
     *
     * @param originalTypeFlow the original type flow
     * @return the resulting type state object
     */
    @Override
    public TypeState foldTypeFlow(PointsToAnalysis bb, TypeFlow<?> originalTypeFlow) {
        if (originalTypeFlow == null) {
            return null;
        }

        TypeState result = TypeState.forEmpty();
        for (MethodFlowsGraph methodFlows : clonedMethodFlows.values()) {
            TypeFlow<?> clonedTypeFlow = methodFlows.lookupCloneOf(bb, originalTypeFlow);
            TypeState cloneState = clonedTypeFlow.getState();
            /*
             * Make a shallow copy of the clone state, i.e., only the types and not the concrete
             * objects, so that the union operation doesn't merge the concrete objects with abstract
             * objects.
             */
            TypeState cloneStateCopy = bb.analysisPolicy().forContextInsensitiveTypeState(bb, cloneState);
            result = TypeState.forUnion(bb, result, cloneStateCopy);
        }
        return result;
    }

    /** Check if the type flow is saturated, i.e., any of its clones is saturated. */
    @Override
    public boolean isSaturated(PointsToAnalysis bb, TypeFlow<?> originalTypeFlow) {
        boolean saturated = false;
        for (MethodFlowsGraph methodFlows : clonedMethodFlows.values()) {
            TypeFlow<?> clonedTypeFlow = methodFlows.lookupCloneOf(bb, originalTypeFlow);
            saturated |= clonedTypeFlow.isSaturated();
        }
        return saturated;
    }

    @Override
    public String toString() {
        return "CallSiteSensitiveMethodTypeFlow<" + method + ">";
    }
}
