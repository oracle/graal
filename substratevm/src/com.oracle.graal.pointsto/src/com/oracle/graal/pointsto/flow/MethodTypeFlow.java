/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.builder.TypeFlowGraphBuilder;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisError.ParsingError;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;

public class MethodTypeFlow extends TypeFlow<AnalysisMethod> {

    protected final PointsToAnalysisMethod method;
    protected volatile MethodFlowsGraph flowsGraph;
    private InvokeTypeFlow parsingReason;
    private int returnedParameterIndex;
    private MethodFlowsGraph.GraphKind graphKind;

    /**
     * Used to detect races between calling {@link #getMethodFlowsGraph()} and
     * {@link #updateFlowsGraph}. Once the method flows graph has been retrieved, then it cannot be
     * updated again.
     */
    private Object sealedFlowsGraph;

    private boolean forceReparseOnCreation = false;

    public MethodTypeFlow(PointsToAnalysisMethod method) {
        super(method, null);
        this.method = method;
        this.graphKind = MethodFlowsGraph.GraphKind.FULL;
    }

    public PointsToAnalysisMethod getMethod() {
        return method;
    }

    /**
     * Signals that a STUB graphkind should be generated upon creation.
     */
    public synchronized void setAsStubFlow() {
        graphKind = MethodFlowsGraph.GraphKind.STUB;
        assert !method.isOriginalMethod() : "setting original method as stub";
        assert !flowsGraphCreated() : "cannot set as flow creation kind flows graph is created";
    }

    /**
     * Helper to see when the flows graph was sealed.
     */
    private void throwSealedError() {
        assert sealedFlowsGraph != null;
        StringBuilder sb = new StringBuilder();
        sb.append("Sealed problem:\n");
        if (sealedFlowsGraph instanceof StackTraceElement[]) {
            StackTraceElement[] trace = (StackTraceElement[]) sealedFlowsGraph;
            sb = new StringBuilder();
            sb.append("stack trace:\n");
            for (StackTraceElement elem : trace) {
                sb.append(elem.toString()).append("\n");
            }
            sb.append("end trace:\n");
        } else {
            sb.append("stack trace is unknown\n");
        }
        throw AnalysisError.shouldNotReachHere(sb.toString());
    }

    /**
     * Returns the flows graph info for this method, blocking until parsing is finished if
     * necessary.
     */
    public MethodFlowsGraphInfo getOrCreateMethodFlowsGraphInfo(PointsToAnalysis bb, InvokeTypeFlow reason) {
        ensureFlowsGraphCreated(bb, reason);
        return flowsGraph;
    }

    /**
     * Accessor for the flowsGraph that assumes that the graph was already created.
     */
    public MethodFlowsGraphInfo getMethodFlowsGraphInfo() {
        assert flowsGraph != null;
        return flowsGraph;
    }

    /**
     * Accessor for the flowsGraph that assumes that the graph was already created.
     */
    public MethodFlowsGraph getMethodFlowsGraph() {
        ensureFlowsGraphSealed();

        assert flowsGraph != null;
        return flowsGraph;
    }

    protected void ensureFlowsGraphSealed() {
        if (sealedFlowsGraph == null) {
            sealFlowsGraph();
        }
    }

    private synchronized void sealFlowsGraph() {
        if (sealedFlowsGraph == null) {
            sealedFlowsGraph = Assertions.assertionsEnabled() ? Thread.currentThread().getStackTrace() : Boolean.TRUE;
        }
    }

    /** The flows graph is created lazily only when the method is implementation invoked. */
    public boolean flowsGraphCreated() {
        return flowsGraph != null;
    }

    /** Trigger parsing and create the flows graph, blocking until ready. */
    public void ensureFlowsGraphCreated(PointsToAnalysis bb, InvokeTypeFlow reason) {
        if (flowsGraph == null) {
            createFlowsGraph(bb, reason);
        }
    }

    /* All threads that try to parse the current method synchronize and only the first parses. */
    private synchronized void createFlowsGraph(PointsToAnalysis bb, InvokeTypeFlow reason) {
        if (flowsGraph == null) {
            AnalysisError.guarantee(reason == null || reason.getSource() == null ||
                            !reason.getSource().getMethod().equals(method), "Parsing reason cannot be in the target method itself: %s", method);

            parsingReason = reason;
            method.setParsingReason(PointsToAnalysisMethod.unwrapInvokeReason(reason));
            try {
                MethodTypeFlowBuilder builder = bb.createMethodTypeFlowBuilder(bb, method, null, graphKind);
                try {
                    builder.apply(forceReparseOnCreation, PointsToAnalysisMethod.unwrapInvokeReason(parsingReason));
                } catch (UnsupportedFeatureException ex) {
                    String message = String.format("%s%n%s", ex.getMessage(), ParsingError.message(method));
                    bb.getUnsupportedFeatures().addMessage("typeflow_" + method.getQualifiedName(), null, message, null, ex);
                }
                bb.numParsedGraphs.incrementAndGet();

                boolean computeIndex = !method.getReturnsAllInstantiatedTypes() && bb.getHostVM().getMultiMethodAnalysisPolicy().canComputeReturnedParameterIndex(method.getMultiMethodKey());
                returnedParameterIndex = computeIndex ? computeReturnedParameterIndex(builder.graph) : -1;

                /* Set the flows graph after fully built. */
                flowsGraph = builder.flowsGraph;
                assert flowsGraph != null;

                initFlowsGraph(bb, builder.postInitFlows);
            } catch (Throwable t) {
                /* Wrap all errors as parsing errors. */
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

    /**
     * Run type flow initialization. This will trigger state propagation from source flows, link
     * static load/store field flows, publish unsafe load/store flows, etc. The flows that need
     * initialization are collected by {@link TypeFlowGraphBuilder#build()}. Their initialization
     * needs to be triggered only after the graph is fully materialized such that lazily constructed
     * type flows (like InovkeTypeFlow.actualReturn) can observe the type state that other flows may
     * generate on initialization.
     */
    protected void initFlowsGraph(PointsToAnalysis bb, List<TypeFlow<?>> postInitFlows) {
        for (TypeFlow<?> flow : postInitFlows) {
            flow.initFlow(bb);
        }
    }

    public Collection<MethodFlowsGraph> getFlows() {
        ensureFlowsGraphSealed();
        return flowsGraph == null ? Collections.emptyList() : List.of(flowsGraph);
    }

    public List<InvokeTypeFlow> getInvokes() {
        ensureFlowsGraphSealed();
        return flowsGraph == null ? List.of() : flowsGraph.getInvokes();
    }

    public TypeFlow<?> getParameter(int idx) {
        return flowsGraph == null ? null : flowsGraph.getParameter(idx);
    }

    public TypeFlow<?> getReturn() {
        return flowsGraph == null ? null : flowsGraph.getReturnFlow();
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
        assert flowsGraphCreated() : returnedParameterIndex;
        return returnedParameterIndex;
    }

    @Override
    public void update(PointsToAnalysis bb) {
        /*
         * Method type flow update (which is effectively method parsing) is done by
         * MethodTypeFlow.ensureFlowsGraphCreated().
         */
        shouldNotReachHere();
    }

    /**
     * Updates the kind of flow graph of associated with the type flow. If the graph has not yet
     * been created, the type lazily created will be the new kind. Otherwise, a new graph is created
     * and the prior flow information is incorporated into the new graph.
     *
     * When updating the flows graph, the expectation is for the new flowGraph to be a superset of
     * the prior graph. This means that if the graphkind is currently FULL, then it cannot be set as
     * a STUB. Further, when forcing reparsing, the newly generated graph should be a superset of
     * the previous graph.
     *
     * @return whether a new graph was created
     */
    public synchronized boolean updateFlowsGraph(PointsToAnalysis bb, MethodFlowsGraph.GraphKind newGraphKind, InvokeTypeFlow newParsingReason, boolean forceReparse) {
        assert !method.isOriginalMethod() : method;
        if (sealedFlowsGraph != null) {
            throwSealedError();
        }

        forceReparseOnCreation = forceReparse || forceReparseOnCreation;

        assert !(newGraphKind == MethodFlowsGraph.GraphKind.STUB && graphKind == MethodFlowsGraph.GraphKind.FULL) : "creating less strict graph";
        MethodFlowsGraph.GraphKind originalGraphKind = graphKind;
        graphKind = newGraphKind;

        if (!forceReparse && originalGraphKind == newGraphKind) {
            /*
             * No action is needed since the current graphKind already satisfies the request.
             */
            return false;
        }
        if (newGraphKind == MethodFlowsGraph.GraphKind.STUB) {
            assert originalGraphKind == MethodFlowsGraph.GraphKind.STUB : originalGraphKind;
            /*
             * No action is needed since a stub creation is idempotent.
             */
            return false;
        }

        if (flowsGraph == null) {
            /*
             * If the flow has not yet been created, then it is not necessary to create a new type
             * flow. We only need to ensure the kind of graph is created.
             */
            return false;
        }
        if (newParsingReason != null) {
            parsingReason = newParsingReason;
            method.setParsingReason(PointsToAnalysisMethod.unwrapInvokeReason(newParsingReason));
        }

        try {
            assert returnedParameterIndex == -1 : returnedParameterIndex;

            // if the graph is a stub, then it has not yet be registered as implementation invoked
            boolean registerAsImplementationInvoked = originalGraphKind == MethodFlowsGraph.GraphKind.STUB;

            flowsGraph.removeInternalFlows(bb);

            MethodTypeFlowBuilder builder = bb.createMethodTypeFlowBuilder(bb, method, flowsGraph, newGraphKind);
            builder.apply(forceReparse, PointsToAnalysisMethod.unwrapInvokeReason(parsingReason));

            flowsGraph.updateInternalState(newGraphKind);

            initFlowsGraph(bb, builder.postInitFlows);

            if (registerAsImplementationInvoked) {
                if (parsingReason == null) {
                    method.registerAsImplementationInvoked(PointsToAnalysisMethod.unwrapInvokeReason(null));
                } else {
                    bb.analysisPolicy().registerAsImplementationInvoked(parsingReason, method);
                }
            }
        } catch (Throwable t) {
            /* Wrap all errors as parsing errors. */
            throw AnalysisError.parsingError(method, t);
        }
        return true;
    }

    @Override
    public String toString() {
        return "MethodTypeFlow<" + method + ">";
    }
}
