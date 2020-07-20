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

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.java.BytecodeParser.BytecodeParserError;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.LoadIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

public class MethodTypeFlow extends TypeFlow<AnalysisMethod> {

    protected MethodFlowsGraph originalMethodFlows;
    protected final ConcurrentMap<AnalysisContext, MethodFlowsGraph> clonedMethodFlows;
    private int localCallingContextDepth;

    private final AnalysisMethod method;

    private volatile boolean methodParsed;
    private InvokeTypeFlow parsingReason;

    private ParameterNode returnedParameter;

    public MethodTypeFlow(OptionValues options, AnalysisMethod method) {
        super(method, null);
        this.method = method;
        this.localCallingContextDepth = PointstoOptions.MaxCallingContextDepth.getValue(options);
        this.originalMethodFlows = new MethodFlowsGraph(method);
        this.clonedMethodFlows = new ConcurrentHashMap<>(4, 0.75f, 1);
    }

    public AnalysisMethod getMethod() {
        return method;
    }

    public InvokeTypeFlow getParsingReason() {
        return parsingReason;
    }

    public StackTraceElement[] getParsingContext() {
        List<StackTraceElement> parsingContext = new ArrayList<>();
        InvokeTypeFlow invokeFlow = parsingReason;

        while (invokeFlow != null) {
            parsingContext.add(invokeFlow.getSource().getMethod().asStackTraceElement(invokeFlow.getSource().getBCI()));
            invokeFlow = ((AnalysisMethod) invokeFlow.getSource().getMethod()).getTypeFlow().parsingReason;
        }
        return parsingContext.toArray(new StackTraceElement[parsingContext.size()]);
    }

    /**
     * Add the context, if not already added, and return the method flows clone from that context.
     */
    public MethodFlowsGraph addContext(BigBang bb, AnalysisContext calleeContext, InvokeTypeFlow reason) {

        // make sure that the method is parsed before attempting to clone it;
        // the parsing should always happen on the same thread
        this.ensureParsed(bb, reason);

        AnalysisContext newContext = bb.contextPolicy().peel(calleeContext, localCallingContextDepth);

        MethodFlowsGraph methodFlows = clonedMethodFlows.get(newContext);
        if (methodFlows == null) {
            MethodFlowsGraph newFlows = new MethodFlowsGraph(method, newContext);
            newFlows.cloneOriginalFlows(bb);
            MethodFlowsGraph oldFlows = clonedMethodFlows.putIfAbsent(newContext, newFlows);
            methodFlows = oldFlows != null ? oldFlows : newFlows;
            if (oldFlows == null) {
                // link uses after adding the clone to the map since linking uses might trigger
                // updates to the current method in the current context
                methodFlows.linkClones(bb);
            }
        }

        return methodFlows;
    }

    public AnalysisContext[] getContexts() {
        Set<AnalysisContext> contexts = clonedMethodFlows.keySet();
        return contexts.toArray(new AnalysisContext[contexts.size()]);
    }

    public int getLocalCallingContextDepth() {
        return localCallingContextDepth;
    }

    public Map<AnalysisContext, MethodFlowsGraph> getMethodContextFlows() {
        return clonedMethodFlows;
    }

    public Collection<MethodFlowsGraph> getFlows() {
        // TODO enforce the use of this method only after the analysis phase is finished
        return clonedMethodFlows.values();
    }

    public MethodFlowsGraph getFlows(AnalysisContext calleeContext) {
        // TODO enforce the use of this method only after the analysis phase is finished
        return clonedMethodFlows.get(calleeContext);
    }

    public void setResult(FormalReturnTypeFlow result) {
        originalMethodFlows.setResult(result);
    }

    public void setParameter(int index, FormalParamTypeFlow parameter) {
        originalMethodFlows.setParameter(index, parameter);
    }

    public void setInitialReceiverFlow(BigBang bb, AnalysisType declaringType) {
        TypeFlow<?> declaringTypeFlow = declaringType.getTypeFlow(bb, false);
        InitialReceiverTypeFlow initialReceiverFlow = new InitialReceiverTypeFlow(method, declaringType);
        declaringTypeFlow.addUse(bb, initialReceiverFlow);
        originalMethodFlows.setInitialParameterFlow(initialReceiverFlow, 0);
    }

    public void setInitialParameterFlow(BigBang bb, AnalysisType declaredType, int i) {
        TypeFlow<?> declaredTypeFlow = declaredType.getTypeFlow(bb, true);
        InitialParamTypeFlow initialParameterFlow = new InitialParamTypeFlow(method, declaredType, i);
        declaredTypeFlow.addUse(bb, initialParameterFlow);
        originalMethodFlows.setInitialParameterFlow(initialParameterFlow, i);
    }

    public void addAllocation(NewInstanceTypeFlow sourceFlow) {
        originalMethodFlows.addAllocation(sourceFlow);
    }

    protected void addDynamicAllocation(DynamicNewInstanceTypeFlow sourceFlow) {
        originalMethodFlows.addDynamicAllocation(sourceFlow);
    }

    protected void addClone(CloneTypeFlow sourceFlow) {
        originalMethodFlows.addClone(sourceFlow);
    }

    public void addSource(SourceTypeFlow sourceFlow) {
        originalMethodFlows.addSource(sourceFlow);
    }

    protected void addInstanceOf(Object key, InstanceOfTypeFlow instanceOf) {
        originalMethodFlows.addInstanceOf(key, instanceOf);
    }

    public void addMiscEntry(TypeFlow<?> input) {
        originalMethodFlows.addMiscEntryFlow(input);
    }

    protected void addMonitorEntryFlow(MonitorEnterTypeFlow monitorEntryFlow) {
        originalMethodFlows.addMonitorEntry(monitorEntryFlow);
    }

    protected void addFieldLoad(LoadFieldTypeFlow sourceFlow) {
        originalMethodFlows.addFieldLoad(sourceFlow);
    }

    protected void addIndexedLoad(LoadIndexedTypeFlow sourceFlow) {
        originalMethodFlows.addIndexedLoad(sourceFlow);
    }

    protected void addInvoke(Object key, InvokeTypeFlow invokeTypeFlow) {
        originalMethodFlows.addInvoke(key, invokeTypeFlow);
    }

    public MethodFlowsGraph getOriginalMethodFlows() {
        return originalMethodFlows;
    }

    /**
     * Get a type state containing the union of states over all the clones of the original flow.
     *
     * @param originalTypeFlow the original type flow
     * @return the resulting type state object
     */
    public TypeState foldTypeFlow(BigBang bb, TypeFlow<?> originalTypeFlow) {
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
            TypeState cloneStateCopy = TypeState.forContextInsensitiveTypeState(bb, cloneState);
            result = TypeState.forUnion(bb, result, cloneStateCopy);
        }
        return result;
    }

    /** Check if the type flow is saturated, i.e., any of its clones is saturated. */
    public boolean isSaturated(BigBang bb, TypeFlow<?> originalTypeFlow) {
        boolean saturated = false;
        for (MethodFlowsGraph methodFlows : clonedMethodFlows.values()) {
            TypeFlow<?> clonedTypeFlow = methodFlows.lookupCloneOf(bb, originalTypeFlow);
            saturated |= clonedTypeFlow.isSaturated();
        }
        return saturated;
    }

    // get original parameter
    public FormalParamTypeFlow getParameterFlow(int idx) {
        return originalMethodFlows.getParameter(idx);
    }

    public TypeState getParameterTypeState(BigBang bb, int parameter) {
        return foldTypeFlow(bb, originalMethodFlows.getParameter(parameter));
    }

    // original result
    protected FormalReturnTypeFlow getResultFlow() {
        return originalMethodFlows.getResult();
    }

    public Collection<InvokeTypeFlow> getInvokes() {
        return originalMethodFlows.getInvokeFlows();
    }

    private static ParameterNode computeReturnedParameter(StructuredGraph graph) {

        if (graph == null) {
            // Some methods, e.g., native ones, don't have a graph.
            return null;
        }

        ParameterNode retParam = null;

        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            if (param.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                boolean returnsParameter = true;
                NodeIterable<ReturnNode> retIterable = graph.getNodes(ReturnNode.TYPE);
                returnsParameter &= retIterable.count() > 0;
                for (ReturnNode ret : retIterable) {
                    returnsParameter &= ret.result() == param;
                }
                if (returnsParameter) {
                    retParam = param;
                }
            }
        }

        return retParam;
    }

    /**
     * If the method returns a parameter through all of the return nodes then that ParameterNode is
     * returned, otherwise null.
     */
    protected ParameterNode getReturnedParameter() {
        return returnedParameter;
    }

    public void ensureParsed(BigBang bb, InvokeTypeFlow reason) {
        if (!methodParsed) {
            doParse(bb, reason);
        }
    }

    /* All threads that try to parse the current method synchronize and only the first parses. */
    private synchronized void doParse(BigBang bb, InvokeTypeFlow reason) {
        if (!methodParsed) {
            parsingReason = reason;
            StructuredGraph graph = null;
            try {
                MethodTypeFlowBuilder builder = bb.createMethodTypeFlowBuilder(bb, this);
                builder.apply();
                graph = builder.graph;

            } catch (BytecodeParserError ex) {
                /* Rewrite some bytecode parsing errors as unsupported features. */
                if (ex.getCause() instanceof UnsupportedFeatureException) {
                    Throwable cause = ex;
                    if (ex.getCause().getCause() != null) {
                        cause = ex.getCause();
                    }
                    String message = cause.getMessage();
                    bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, message, ex.context(), cause.getCause());
                } else {
                    /* Wrap all other errors as parsing errors. */
                    throw AnalysisError.parsingError(method, ex);
                }
            } catch (Throwable t) {
                /* Wrap all other errors as parsing errors. */
                throw AnalysisError.parsingError(method, t);
            }

            originalMethodFlows.linearizeGraph();

            bb.numParsedGraphs.incrementAndGet();

            returnedParameter = computeReturnedParameter(graph);

            methodParsed = true;
        }
    }

    @Override
    public void update(BigBang bb) {
        // method type flow update (which is effectively method parsing) is done by
        // MethodTypeFlow.ensureParsed which should always be executed on the same thread as
        // MethodTypeFlow.addContext
        shouldNotReachHere();
    }

    @Override
    public String toString() {
        return "MethodTypeFlow<" + method + ">";
    }
}
