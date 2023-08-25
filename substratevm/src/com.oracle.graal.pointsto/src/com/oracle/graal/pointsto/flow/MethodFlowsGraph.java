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

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.EncodedGraph.EncodedNodeReference;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

public class MethodFlowsGraph implements MethodFlowsGraphInfo {
    /**
     * The type of method flows graph.
     */
    public enum GraphKind {
        /**
         * A stub MethodFlowsGraph is a graph which only has typeflows for parameter and return
         * values, but does not have an internal flow. Stubs are used as a placeholder and may be
         * dynamically replaced with a full flows graph throughout the analysis phase.
         */
        STUB,
        /**
         * A full MethodFlowsGraph has the full internal flow. Whether the graph flows for all
         * object parameters and return values, regardless of whether they are linked to the
         * internal flows, is dependent on
         * {@code HostVM.MultiMethodAnalysisPolicy#insertPlaceholderParamAndReturnFlows}.
         */
        FULL,
    }

    protected final int id;
    protected final PointsToAnalysisMethod method;
    protected TypeFlow<?>[] linearizedGraph;

    // parameters, sources and field loads are the possible data entry
    // points in the method
    protected FormalParamTypeFlow[] parameters;
    protected List<TypeFlow<?>> miscEntryFlows;
    protected EconomicMap<EncodedNodeReference, TypeFlow<?>> nodeFlows;
    /*
     * We keep a bci->flow mapping for instanceof and invoke flows since they are queried by the
     * analysis results builder.
     */
    protected EconomicSet<Object> nonUniqueBcis;
    protected EconomicMap<Object, InstanceOfTypeFlow> instanceOfFlows;
    protected EconomicMap<Object, InvokeTypeFlow> invokeFlows;

    protected FormalReturnTypeFlow returnFlow;

    protected volatile boolean isLinearized;

    private GraphKind graphKind;

    /**
     * Constructor for the 'original' method flows graph. This is used as a source for creating
     * clones. It has a <code>null</code> context and <code>null</code> original method flows.
     */
    public MethodFlowsGraph(PointsToAnalysisMethod method, GraphKind graphKind) {

        id = TypeFlow.nextId.incrementAndGet();

        this.method = method;
        this.graphKind = graphKind;

        // parameters
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        int parameterCount = method.getSignature().getParameterCount(!isStatic);
        parameters = new FormalParamTypeFlow[parameterCount];
    }

    public <T extends TypeFlow<?>> T lookupCloneOf(@SuppressWarnings("unused") PointsToAnalysis bb, T original) {
        return original;
    }

    public static boolean nonCloneableFlow(TypeFlow<?> flow) {
        /*
         * References to field flows and to array elements flows are not part of the method itself;
         * field and indexed load and store flows will instead be cloned, and used to access the
         * field flow.
         */
        return flow instanceof FieldTypeFlow || flow instanceof ArrayElementsTypeFlow;
    }

    public static boolean crossMethodUse(TypeFlow<?> flow, TypeFlow<?> use) {
        /*
         * Formal returns and unwinds are method exit points. Formal parameters are entry points
         * into callees.
         */
        return flow instanceof FormalReturnTypeFlow || use instanceof FormalParamTypeFlow;
    }

    public static boolean nonMethodFlow(TypeFlow<?> flow) {
        /*
         * All-instantiated flow doesn't belong to any method, but it can be reachable from a use.
         */
        return flow instanceof AllInstantiatedTypeFlow || flow instanceof AllSynchronizedTypeFlow;
    }

    /**
     * Return the linearized graph, i.e., the graph represented as an array where each flow has a
     * unique slot, blocking until the array is available.
     */
    public TypeFlow<?>[] getLinearizedGraph() {
        ensureLinearized();
        return linearizedGraph;
    }

    protected void ensureLinearized() {
        if (!isLinearized) {
            linearizeGraph(false);
        }
    }

    private synchronized void linearizeGraph(boolean isRedo) {
        /* Synchronize access to ensure that the slot is set only once. */
        if (isRedo || !isLinearized) {
            List<TypeFlow<?>> resultFlows = new ArrayList<>();
            for (TypeFlow<?> flow : flows()) {
                int slotNum = flow.getSlot();
                if (slotNum != -1) {
                    assert flow.isClone() || flow instanceof FormalParamTypeFlow || flow instanceof FormalReturnTypeFlow : "Unexpected flow " + flow;
                    AnalysisError.guarantee((isRedo || flow.isClone()) && flow.getSlot() == resultFlows.size(), "Flow already discovered: %s", flow);
                } else {
                    flow.setSlot(resultFlows.size());
                }
                resultFlows.add(flow);
            }
            linearizedGraph = resultFlows.toArray(new TypeFlow<?>[0]);
            isLinearized = true;
        }
    }

    /**
     * Creates an iterator containing all flows which are internal to this method. This does not
     * include the following types of flows:
     * <ul>
     * <li>A cloned flow</li>
     * <li>A flow which is from another method</li>
     * <li>A flow which does not belong to any method</li>
     * </ul>
     */
    public final Iterable<TypeFlow<?>> flows() {
        return this::flowsIterator;
    }

    private Iterator<TypeFlow<?>> flowsIterator() {
        return new Iterator<>() {
            final Deque<TypeFlow<?>> worklist = new ArrayDeque<>();
            final Set<TypeFlow<?>> seen = new HashSet<>();
            TypeFlow<?> next;

            {
                /*
                 * Note parameter and return flows must be processed first to ensure they have a
                 * stable id if the graph is updated.
                 */
                for (TypeFlow<?> param : parameters) {
                    if (param != null) {
                        worklist.add(param);
                    }
                }
                if (returnFlow != null) {
                    worklist.add(returnFlow);
                }
                if (nodeFlows != null) {
                    for (var value : nodeFlows.getValues()) {
                        worklist.add(value);
                    }
                }
                if (miscEntryFlows != null) {
                    for (var value : miscEntryFlows) {
                        /* Skip embedded AllInstantiatedTypeFlows. */
                        if (!nonMethodFlow(value)) {
                            worklist.add(value);
                        }
                    }
                }
                if (instanceOfFlows != null) {
                    for (var value : instanceOfFlows.getValues()) {
                        worklist.add(value);
                    }
                }
                if (invokeFlows != null) {
                    for (var value : invokeFlows.getValues()) {
                        worklist.add(value);
                    }
                }

                /* Initialize next. */
                next = findNext();
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public TypeFlow<?> next() {
                TypeFlow<?> current = next;
                next = findNext();
                return current;
            }

            /** Get the next flow and expand the work list. */
            private TypeFlow<?> findNext() {
                /* pollFirst returns null if the deque is empty. */
                TypeFlow<?> nextFlow = worklist.pollFirst();
                while (seen.contains(nextFlow)) {
                    nextFlow = worklist.pollFirst();
                }
                if (nextFlow != null) {
                    seen.add(nextFlow);
                    expand(nextFlow);
                }
                return nextFlow;
            }

            private void expand(TypeFlow<?> flow) {
                for (TypeFlow<?> use : flow.getUses()) {
                    if (use.isClone() || crossMethodUse(flow, use) || nonCloneableFlow(use) || nonMethodFlow(use)) {
                        continue;
                    }
                    worklist.add(use);
                }
            }
        };
    }

    public int id() {
        return id;
    }

    @Override
    public PointsToAnalysisMethod getMethod() {
        return method;
    }

    @Override
    public boolean isStub() {
        return graphKind == GraphKind.STUB;
    }

    public GraphKind getGraphKind() {
        return graphKind;
    }

    @Override
    public FormalReceiverTypeFlow getFormalReceiver() {
        return (FormalReceiverTypeFlow) getParameter(0);
    }

    public void setParameter(int index, FormalParamTypeFlow parameter) {
        assert index >= 0 && index < this.parameters.length;
        parameters[index] = parameter;
    }

    @Override
    public FormalParamTypeFlow getParameter(int idx) {
        assert idx >= 0 && idx < this.parameters.length;
        return parameters[idx];
    }

    public TypeFlow<?>[] getParameters() {
        return parameters;
    }

    public void addNodeFlow(PointsToAnalysis bb, Node node, TypeFlow<?> input) {
        if (bb.strengthenGraalGraphs()) {
            addNodeFlow(new EncodedNodeReference(node), input);
        } else {
            addMiscEntryFlow(input);
        }
    }

    public void addNodeFlow(EncodedNodeReference key, TypeFlow<?> flow) {
        assert flow != null && !(flow instanceof AllInstantiatedTypeFlow);
        if (nodeFlows == null) {
            nodeFlows = EconomicMap.create();
        }
        nodeFlows.put(key, flow);
    }

    public Collection<TypeFlow<?>> getMiscFlows() {
        return miscEntryFlows == null ? Collections.emptyList() : miscEntryFlows;
    }

    public EconomicMap<EncodedNodeReference, TypeFlow<?>> getNodeFlows() {
        return nodeFlows == null ? EconomicMap.emptyMap() : nodeFlows;
    }

    public void addMiscEntryFlow(TypeFlow<?> entryFlow) {
        if (miscEntryFlows == null) {
            miscEntryFlows = new ArrayList<>();
        }
        miscEntryFlows.add(entryFlow);
    }

    public void setReturnFlow(FormalReturnTypeFlow returnFlow) {
        this.returnFlow = returnFlow;
    }

    @Override
    public FormalReturnTypeFlow getReturnFlow() {
        return this.returnFlow;
    }

    public EconomicMap<Object, InvokeTypeFlow> getInvokes() {
        return invokeFlows == null ? EconomicMap.emptyMap() : invokeFlows;
    }

    public EconomicMap<Object, InstanceOfTypeFlow> getInstanceOfFlows() {
        return instanceOfFlows == null ? EconomicMap.emptyMap() : instanceOfFlows;
    }

    void addInstanceOf(Object key, InstanceOfTypeFlow instanceOf) {
        if (instanceOfFlows == null) {
            instanceOfFlows = EconomicMap.create();
        }
        doAddFlow(key, instanceOf, instanceOfFlows);
    }

    void addInvoke(Object key, InvokeTypeFlow invokeTypeFlow) {
        if (invokeFlows == null) {
            invokeFlows = EconomicMap.create();
        }
        doAddFlow(key, invokeTypeFlow, invokeFlows);
    }

    private <T extends TypeFlow<BytecodePosition>> void doAddFlow(Object key, T flow, EconomicMap<Object, T> map) {
        assert map == instanceOfFlows || map == invokeFlows : "Keys of these maps must not be overlapping";
        Object uniqueKey = key;
        if ((nonUniqueBcis != null && nonUniqueBcis.contains(key)) || removeNonUnique(key, instanceOfFlows) || removeNonUnique(key, invokeFlows)) {
            uniqueKey = new Object();
        }
        map.put(uniqueKey, flow);
    }

    private <T extends TypeFlow<BytecodePosition>> boolean removeNonUnique(Object key, EconomicMap<Object, T> map) {
        if (map == null) {
            return false;
        }
        T oldFlow = map.removeKey(key);
        if (oldFlow != null) {
            /*
             * This can happen when Graal inlines jsr/ret routines and the inlined nodes share the
             * same bci. Or for some invokes where the bytecode parser needs to insert a type check
             * before the invoke. Remove the old bci->flow pairing and replace it with a
             * uniqueKey->flow pairing.
             */
            map.put(new Object(), oldFlow);
            if (nonUniqueBcis == null) {
                nonUniqueBcis = EconomicSet.create();
            }
            nonUniqueBcis.add(key);
            return true;
        } else {
            return false;
        }
    }

    public boolean isLinearized() {
        return isLinearized;
    }

    /**
     * Get the list of all context sensitive callers.
     *
     * @return a list containing all the callers for the given context sensitive method
     */
    public List<MethodFlowsGraph> callers(PointsToAnalysis bb) {
        /*
         * This list is seldom needed thus it is created lazily instead of storing a mapping from a
         * caller context to a caller graph for each method graph.
         *
         * TODO cache the result
         */
        List<MethodFlowsGraph> callers = new ArrayList<>();
        for (AnalysisMethod caller : method.getCallers()) {
            for (MethodFlowsGraph callerFlowGraph : PointsToAnalysis.assertPointsToAnalysisMethod(caller).getTypeFlow().getFlows()) {
                for (InvokeTypeFlow callerInvoke : callerFlowGraph.getInvokes().getValues()) {
                    InvokeTypeFlow invoke = callerInvoke;
                    if (InvokeTypeFlow.isContextInsensitiveVirtualInvoke(callerInvoke)) {
                        /* The invoke has been replaced by the context insensitive one. */
                        invoke = callerInvoke.getTargetMethod().getContextInsensitiveVirtualInvoke(method.getMultiMethodKey());
                    }
                    for (MethodFlowsGraph calleeFlowGraph : invoke.getOriginalCalleesFlows(bb)) {
                        // 'this' method graph was found among the callees of an invoke flow in one
                        // of the clones of the caller methods, hence we regiter that clone as a
                        // caller for 'this' method clone
                        if (calleeFlowGraph.equals(this)) {
                            callers.add(callerFlowGraph);
                        }
                    }
                }
            }
        }
        return callers;
    }

    /**
     * Given a context sensitive caller, i.e., another MethodFlowsGraph, identify the InvokeTypeFlow
     * belonging to the caller that linked to this callee.
     *
     * @param callerFlowGraph the context sensitive caller.
     * @return the InvokeTypeFlow object belonging to the caller that linked to this callee.
     */
    public InvokeTypeFlow invokeFlow(MethodFlowsGraph callerFlowGraph, PointsToAnalysis bb) {
        for (InvokeTypeFlow callerInvoke : callerFlowGraph.getInvokes().getValues()) {
            for (MethodFlowsGraph calleeFlowGraph : callerInvoke.getOriginalCalleesFlows(bb)) {
                // 'this' method graph was found among the callees of an invoke flow in the caller
                // method clone, hence we register return it
                if (calleeFlowGraph.equals(this)) {
                    return callerInvoke;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "MethodFlowsGraph<" + method.format("%h.%n(%p)") + ">";
    }

    /**
     * Removes all internal flows of the graph and leave behind only the parameter and return flows.
     * This is expected to only be used by {@link MethodTypeFlow#updateFlowsGraph}.
     */
    final void removeInternalFlows(PointsToAnalysis bb) {

        // Invalidate internal flows which will be cleared
        flowsIterator().forEachRemaining(typeFlow -> {
            // param and return flows will not be cleared
            boolean skipInvalidate = typeFlow instanceof FormalParamTypeFlow || typeFlow instanceof FormalReturnTypeFlow;
            if (!skipInvalidate) {
                typeFlow.invalidate();
            }
        });

        // Clear out the parameter uses and observers
        for (var param : getParameters()) {
            if (param != null) {
                param.clearUses();
                param.clearObservers();
            }
        }

        // Clear out the return value inputs and observees
        if (returnFlow != null && bb.trackTypeFlowInputs()) {
            returnFlow.clearInputs();
            returnFlow.clearObservees();
        }

        // Clearing out all other flows
        miscEntryFlows = null;
        nodeFlows = null;

        nonUniqueBcis = null;
        instanceOfFlows = null;
        invokeFlows = null;
    }

    /**
     * Updates the graphkind and relinearizes the graph if necessary. This is expected to only be
     * used by {@link MethodTypeFlow#updateFlowsGraph}.
     */
    void updateInternalState(GraphKind newGraphKind) {

        graphKind = newGraphKind;

        if (isLinearized) {
            linearizeGraph(true);
        }
    }
}
