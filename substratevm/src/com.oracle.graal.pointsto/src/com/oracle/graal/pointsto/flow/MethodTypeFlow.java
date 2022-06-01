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
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.BytecodeParser.BytecodeParserError;
import org.graalvm.compiler.nodes.EncodedGraph.EncodedNodeReference;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

public class MethodTypeFlow extends TypeFlow<AnalysisMethod> {

    protected final PointsToAnalysisMethod method;
    protected MethodFlowsGraph flowsGraph;
    private volatile boolean flowsGraphCreated;
    private InvokeTypeFlow parsingReason;

    private int returnedParameterIndex;

    public MethodTypeFlow(PointsToAnalysisMethod method) {
        super(method, null);
        this.method = method;
        this.flowsGraph = new MethodFlowsGraph(method);
    }

    public PointsToAnalysisMethod getMethod() {
        return method;
    }

    public StackTraceElement[] getParsingContext() {
        List<StackTraceElement> parsingContext = new ArrayList<>();
        InvokeTypeFlow invokeFlow = parsingReason;

        /* Defend against cycles in the parsing context. GR-35744 should fix this properly. */
        int maxSize = 100;

        while (invokeFlow != null && parsingContext.size() < maxSize) {
            parsingContext.add(invokeFlow.getSource().getMethod().asStackTraceElement(invokeFlow.getSource().getBCI()));
            invokeFlow = ((PointsToAnalysisMethod) invokeFlow.getSource().getMethod()).getTypeFlow().parsingReason;
        }
        return parsingContext.toArray(new StackTraceElement[0]);
    }

    /**
     * Returns the flows graph corresponding to this method, blocking until parsing is finished if
     * necessary.
     */
    public MethodFlowsGraph getOrCreateMethodFlowsGraph(PointsToAnalysis bb, InvokeTypeFlow reason) {
        ensureFlowsGraphCreated(bb, reason);
        return flowsGraph;
    }

    public MethodFlowsGraph getMethodFlowsGraph() {
        assert flowsGraph != null;
        return flowsGraph;
    }

    public Collection<MethodFlowsGraph> getFlows() {
        return flowsGraphCreated ? List.of(flowsGraph) : Collections.emptyList();
    }

    public void setResult(FormalReturnTypeFlow result) {
        flowsGraph.setReturnFlow(result);
    }

    public void setParameter(int index, FormalParamTypeFlow parameter) {
        flowsGraph.setParameter(index, parameter);
    }

    protected void addInstanceOf(Object key, InstanceOfTypeFlow instanceOf) {
        flowsGraph.addInstanceOf(key, instanceOf);
    }

    public void addNodeFlow(PointsToAnalysis bb, Node node, TypeFlow<?> input) {
        if (bb.strengthenGraalGraphs()) {
            flowsGraph.addNodeFlow(new EncodedNodeReference(node), input);
        } else {
            flowsGraph.addMiscEntryFlow(input);
        }
    }

    public void addMiscEntry(TypeFlow<?> input) {
        flowsGraph.addMiscEntryFlow(input);
    }

    protected void addInvoke(Object key, InvokeTypeFlow invokeTypeFlow) {
        flowsGraph.addInvoke(key, invokeTypeFlow);
    }

    /**
     * Return the type state of the original flow.
     */
    public TypeState foldTypeFlow(@SuppressWarnings("unused") PointsToAnalysis bb, TypeFlow<?> originalTypeFlow) {
        if (originalTypeFlow == null) {
            return null;
        }
        return originalTypeFlow.getState();
    }

    /** Check if the type flow is saturated, i.e., any of its clones is saturated. */
    public boolean isSaturated(@SuppressWarnings("unused") PointsToAnalysis bb, TypeFlow<?> originalTypeFlow) {
        return originalTypeFlow.isSaturated();
    }

    public TypeState getParameterTypeState(PointsToAnalysis bb, int parameter) {
        return foldTypeFlow(bb, flowsGraph.getParameter(parameter));
    }

    protected FormalReturnTypeFlow getResultFlow() {
        return flowsGraph.getReturnFlow();
    }

    public Iterable<InvokeTypeFlow> getInvokes() {
        return flowsGraph.getInvokes().getValues();
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
     * Returns the index of the parameter that is the only return value of this method, or -1 if the
     * method does not always return a parameter.
     */
    public int getReturnedParameterIndex() {
        return returnedParameterIndex;
    }

    protected void ensureFlowsGraphCreated(PointsToAnalysis bb, InvokeTypeFlow reason) {
        if (!flowsGraphCreated) {
            createFlowsGraph(bb, reason);
        }
    }

    /* All threads that try to parse the current method synchronize and only the first parses. */
    private synchronized void createFlowsGraph(PointsToAnalysis bb, InvokeTypeFlow reason) {
        if (!flowsGraphCreated) {
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

            initFlowsGraph(bb);

            bb.numParsedGraphs.incrementAndGet();

            returnedParameterIndex = computeReturnedParameterIndex(graph);

            flowsGraphCreated = true;
        }
    }

    protected void initFlowsGraph(PointsToAnalysis bb) {
        flowsGraph.init(bb);
    }

    @Override
    public void update(PointsToAnalysis bb) {
        /*
         * Method type flow update (which is effectively method parsing) is done by
         * MethodTypeFlow.ensureTypeFlowCreated().
         */
        shouldNotReachHere();
    }

    @Override
    public String toString() {
        return "MethodTypeFlow<" + method + ">";
    }
}
