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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

public class MethodTypeFlow extends TypeFlow<AnalysisMethod> {

    protected final PointsToAnalysisMethod method;
    private volatile MethodFlowsGraph flowsGraph;
    private InvokeTypeFlow parsingReason;
    private int returnedParameterIndex;

    public MethodTypeFlow(PointsToAnalysisMethod method) {
        super(method, null);
        this.method = method;
    }

    public PointsToAnalysisMethod getMethod() {
        return method;
    }

    /** Returns the flows graph for this method, blocking until parsing is finished if necessary. */
    public MethodFlowsGraph getOrCreateMethodFlowsGraph(PointsToAnalysis bb, InvokeTypeFlow reason) {
        ensureFlowsGraphCreated(bb, reason);
        return flowsGraph;
    }

    /** Accessor for the flowsGraph that assumes that the graph was already created. */
    public MethodFlowsGraph getMethodFlowsGraph() {
        assert flowsGraph != null;
        return flowsGraph;
    }

    /** The flows graph is created lazily only when the method is implementation invoked. */
    public boolean flowsGraphCreated() {
        return flowsGraph != null;
    }

    /** Trigger parsing and create the flows graph, blocking until ready. */
    protected void ensureFlowsGraphCreated(PointsToAnalysis bb, InvokeTypeFlow reason) {
        if (flowsGraph == null) {
            createFlowsGraph(bb, reason);
        }
    }

    /* All threads that try to parse the current method synchronize and only the first parses. */
    private synchronized void createFlowsGraph(PointsToAnalysis bb, InvokeTypeFlow reason) {
        if (flowsGraph == null) {
            AnalysisError.guarantee(reason == null || reason.getSource() == null ||
                            !reason.getSource().getMethod().equals(method), "Parsing reason cannot be in the target method itself " + method.format("%H.%n"));

            parsingReason = reason;
            try {
                MethodTypeFlowBuilder builder = bb.createMethodTypeFlowBuilder(bb, method);
                builder.apply(PointsToAnalysisMethod.unwrapInvokeReason(parsingReason));

                returnedParameterIndex = computeReturnedParameterIndex(builder.graph);
                bb.numParsedGraphs.incrementAndGet();

                /* Set the flows graph after fully built. */
                flowsGraph = builder.flowsGraph;

                initFlowsGraph(bb);
            } catch (Throwable t) {
                /* Wrap all other errors as parsing errors. */
                throw AnalysisError.parsingError(method, t);
            }
        }
    }

    private static int computeReturnedParameterIndex(StructuredGraph graph) {
        if (graph == null) {
            // Some methods, e.g., native ones, don't have a graph.
            return -1;
        }

        ValueNode singleReturnedValue = null;
        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            if (singleReturnedValue == null) {
                singleReturnedValue = returnNode.result();
            } else if (returnNode.result() != singleReturnedValue) {
                return -1;
            }
        }
        if (singleReturnedValue instanceof ParameterNode) {
            return ((ParameterNode) singleReturnedValue).index();
        } else {
            return -1;
        }
    }

    protected void initFlowsGraph(PointsToAnalysis bb) {
        flowsGraph.init(bb);
    }

    public Collection<MethodFlowsGraph> getFlows() {
        return flowsGraph == null ? Collections.emptyList() : List.of(flowsGraph);
    }

    public EconomicMap<Object, InvokeTypeFlow> getInvokes() {
        return flowsGraph == null ? EconomicMap.emptyMap() : flowsGraph.getInvokes();
    }

    public TypeFlow<?> getParameter(int idx) {
        return flowsGraph == null ? null : flowsGraph.getParameter(idx);
    }

    public Iterable<TypeFlow<?>> getParameters() {
        return flowsGraph == null ? Collections.emptyList() : Arrays.asList(flowsGraph.getParameters());
    }

    /** Check if the type flow is saturated, i.e., any of its clones is saturated. */
    public boolean isSaturated(@SuppressWarnings("unused") PointsToAnalysis bb, TypeFlow<?> originalTypeFlow) {
        return originalTypeFlow.isSaturated();
    }

    /**
     * Return the type state of the original flow.
     */
    public TypeState foldTypeFlow(@SuppressWarnings("unused") PointsToAnalysis bb, TypeFlow<?> originalTypeFlow) {
        return originalTypeFlow == null ? null : originalTypeFlow.getState();
    }

    /**
     * Returns the index of the parameter that is the only return value of this method, or -1 if the
     * method does not always return a parameter.
     */
    public int getReturnedParameterIndex() {
        return returnedParameterIndex;
    }

    public BytecodePosition getParsingReason() {
        return parsingReason != null ? parsingReason.getSource() : null;
    }

    @Override
    public void update(PointsToAnalysis bb) {
        /*
         * Method type flow update (which is effectively method parsing) is done by
         * MethodTypeFlow.ensureFlowsGraphCreated().
         */
        shouldNotReachHere();
    }

    @Override
    public String toString() {
        return "MethodTypeFlow<" + method + ">";
    }
}
