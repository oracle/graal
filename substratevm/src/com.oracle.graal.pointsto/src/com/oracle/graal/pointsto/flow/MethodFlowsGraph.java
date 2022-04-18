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

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.nodes.EncodedGraph.EncodedNodeReference;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;

import jdk.vm.ci.code.BytecodePosition;

public class MethodFlowsGraph {

    protected final int id;

    private final PointsToAnalysisMethod method;
    private AnalysisContext context;
    private boolean isClone;

    public TypeFlow<?>[] linearizedGraph;

    // parameters, sources and field loads are the possible data entry
    // points in the method
    private FormalParamTypeFlow[] parameters;
    private InitialParamTypeFlow[] initialParameterFlows;
    private List<TypeFlow<?>> miscEntryFlows;
    private EconomicMap<EncodedNodeReference, TypeFlow<?>> nodeFlows;
    /*
     * We keep a bci->flow mapping for instanceof and invoke flows since they are queried by the
     * analysis results builder.
     */
    private EconomicSet<Object> nonUniqueBcis;
    private EconomicMap<Object, InstanceOfTypeFlow> instanceOfFlows;
    private EconomicMap<Object, InvokeTypeFlow> invokeFlows;

    private FormalReturnTypeFlow returnFlow;

    private boolean isLinearized = false;
    private boolean sealed;

    /**
     * Constructor for the 'original' method flows graph. This is used as a source for creating
     * clones. It has a <code>null</code> context and <code>null</code> original method flows.
     */
    public MethodFlowsGraph(PointsToAnalysisMethod analysisMethod) {

        id = TypeFlow.nextId.incrementAndGet();

        method = analysisMethod;
        context = null;
        isClone = false;

        // parameters
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        int parameterCount = method.getSignature().getParameterCount(!isStatic);
        parameters = new FormalParamTypeFlow[parameterCount];

        // lookup the parameters type so that they are added to the universe even if the method is
        // never linked and parsed
        int offset = isStatic ? 0 : 1;
        for (int i = offset; i < parameters.length; i++) {
            method.getSignature().getParameterType(i - offset, method.getDeclaringClass());
        }

        // initial parameter flows
        initialParameterFlows = new InitialParamTypeFlow[parameterCount];

        // lookup the return type so that it is added to the universe even if the method is
        // never linked and parsed
        method.getSignature().getReturnType(method.getDeclaringClass());
    }

    public MethodFlowsGraph(PointsToAnalysisMethod method, AnalysisContext context) {
        this.id = TypeFlow.nextId.incrementAndGet();
        this.context = context;
        this.method = method;
        this.isClone = true;
    }

    public void cloneOriginalFlows(PointsToAnalysis bb) {

        assert this.isClone && context != null;

        /*
         * The original method flows represent the source for cloning.
         */
        MethodFlowsGraph originalMethodFlowsGraph = method.getTypeFlow().originalMethodFlows;
        assert originalMethodFlowsGraph != null && originalMethodFlowsGraph.isLinearized() : " Method " + this + " is not linearized";

        linearizedGraph = new TypeFlow<?>[originalMethodFlowsGraph.linearizedGraph.length];

        // parameters
        parameters = new FormalParamTypeFlow[originalMethodFlowsGraph.parameters.length];
        for (int i = 0; i < originalMethodFlowsGraph.parameters.length; i++) {
            // copy the flow
            if (originalMethodFlowsGraph.getParameter(i) != null) {
                parameters[i] = lookupCloneOf(bb, originalMethodFlowsGraph.getParameter(i));
            }
        }

        initialParameterFlows = new InitialParamTypeFlow[originalMethodFlowsGraph.initialParameterFlows.length];

        nodeFlows = lookupClonesOf(bb, originalMethodFlowsGraph.nodeFlows);
        returnFlow = originalMethodFlowsGraph.getReturnFlow() != null ? lookupCloneOf(bb, originalMethodFlowsGraph.getReturnFlow()) : null;
        instanceOfFlows = lookupClonesOf(bb, originalMethodFlowsGraph.instanceOfFlows);
        miscEntryFlows = lookupClonesOf(bb, originalMethodFlowsGraph.miscEntryFlows);
        invokeFlows = lookupClonesOf(bb, originalMethodFlowsGraph.invokeFlows);

        /* At this point all the clones should have been created. */
        sealed = true;
    }

    private <K, V extends TypeFlow<?>> EconomicMap<K, V> lookupClonesOf(PointsToAnalysis bb, EconomicMap<K, V> original) {
        if (original == null) {
            return null;
        }
        EconomicMap<K, V> result = EconomicMap.create(original.size());
        var cursor = original.getEntries();
        while (cursor.advance()) {
            result.put(cursor.getKey(), lookupCloneOf(bb, cursor.getValue()));
        }
        return result;
    }

    private <V extends TypeFlow<?>> List<V> lookupClonesOf(PointsToAnalysis bb, List<V> original) {
        if (original == null) {
            return null;
        }
        List<V> result = new ArrayList<>(original.size());
        for (V value : original) {
            result.add(lookupCloneOf(bb, value));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T extends TypeFlow<?>> T lookupCloneOf(PointsToAnalysis bb, T original) {
        assert original != null : "Looking for the clone of a 'null' flow in " + this;
        assert !original.isClone() : "Looking for the clone of the already cloned flow " + original + " in " + this;
        assert !(original instanceof FieldTypeFlow) : "Trying to clone a field type flow";
        assert !(original instanceof ArrayElementsTypeFlow) : "Trying to clone an mixed elements type flow";

        if (original instanceof AllInstantiatedTypeFlow || original instanceof AllSynchronizedTypeFlow) {
            /* All instantiated is not cloneable. */
            return original;
        }
        if (original instanceof ProxyTypeFlow) {
            /* The ProxyTypeFlow is just a place holder in the original graph for its input. */
            return (T) ((ProxyTypeFlow) original).getInput();
        }

        int slot = original.getSlot();

        assert slot >= 0 && slot < linearizedGraph.length : "Slot index out of bounds " + slot + " : " + original + " [" + original.getSource() + "]";

        TypeFlow<?> clone = linearizedGraph[slot];
        if (clone == null) {

            if (sealed) {
                shouldNotReachHere("Trying to create a clone after the method flows have been sealed.");
            }

            // copy only makes a shallow copy of the original flows;
            // it does not copy it's uses or inputs (for those flows that have inputs)
            clone = original.copy(bb, this);
            assert slot == clone.getSlot();

            assert linearizedGraph[slot] == null : "Clone already exists: " + slot + " : " + original;
            linearizedGraph[slot] = clone;
        }

        return (T) clone;
    }

    public void linkClones(final PointsToAnalysis bb) {

        MethodFlowsGraph originalMethodFlowsGraph = method.getTypeFlow().originalMethodFlows;

        /* Link the initial parameter flows to the parameters. */
        for (int i = 0; i < originalMethodFlowsGraph.initialParameterFlows.length; i++) {
            InitialParamTypeFlow initialParameterFlow = originalMethodFlowsGraph.getInitialParameterFlow(i);
            if (initialParameterFlow != null && parameters[i] != null) {
                initialParameterFlow.addUse(bb, parameters[i]);
                initialParameterFlows[i] = initialParameterFlow;
            }
        }

        for (TypeFlow<?> original : originalMethodFlowsGraph.linearizedGraph) {
            TypeFlow<?> clone = lookupCloneOf(bb, original);

            /*
             * Run initialization code for corner case type flows. This can be used to add link from
             * 'outside' into the graph.
             */
            clone.initClone(bb);

            /* Link all 'internal' observers. */
            for (TypeFlow<?> originalObserver : original.getObservers()) {
                // only clone the original observers
                assert !(originalObserver instanceof AllInstantiatedTypeFlow);
                assert !(originalObserver.isClone());

                if (nonCloneableFlow(originalObserver)) {
                    clone.addObserver(bb, originalObserver);
                } else if (crossMethodUse(original, originalObserver)) {
                    // cross method uses (parameters, return and unwind) are linked by
                    // InvokeTypeFlow.linkCallee
                } else {
                    TypeFlow<?> clonedObserver = lookupCloneOf(bb, originalObserver);
                    clone.addObserver(bb, clonedObserver);
                }
            }

            /* Link all 'internal' uses. */
            for (TypeFlow<?> originalUse : original.getUses()) {
                // only clone the original uses
                assert !(originalUse instanceof AllInstantiatedTypeFlow);
                assert !(originalUse.isClone());

                if (nonCloneableFlow(originalUse)) {
                    clone.addUse(bb, originalUse);
                } else if (crossMethodUse(original, originalUse)) {
                    // cross method uses (parameters, return and unwind) are linked by
                    // InvokeTypeFlow.linkCallee
                } else {
                    TypeFlow<?> clonedUse = lookupCloneOf(bb, originalUse);
                    clone.addUse(bb, clonedUse);
                }
            }

            if (clone instanceof StaticInvokeTypeFlow) {
                /* Trigger the update for static invokes, there is no receiver to trigger it. */
                StaticInvokeTypeFlow invokeFlow = (StaticInvokeTypeFlow) clone;
                bb.postFlow(invokeFlow);
            }
        }
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

    public void linearizeGraph() {
        linearizedGraph = doLinearizeGraph();
        isLinearized = true;
    }

    private TypeFlow<?>[] doLinearizeGraph() {

        Deque<TypeFlow<?>> worklist = new ArrayDeque<>();

        for (TypeFlow<?> param : parameters) {
            if (param != null) {
                worklist.add(param);
            }
        }
        if (nodeFlows != null) {
            for (var value : nodeFlows.getValues()) {
                worklist.add(value);
            }
        }
        if (miscEntryFlows != null) {
            worklist.addAll(miscEntryFlows);
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
        if (returnFlow != null) {
            worklist.add(returnFlow);
        }

        // temporary list in which the linearized graph is built
        List<TypeFlow<?>> resultFlows = new ArrayList<>();

        while (worklist.size() > 0) {
            TypeFlow<?> flow = worklist.pop();
            if (flow.getSlot() != -1) {
                // flow already discovered and processed
                continue;
            }
            if (flow instanceof AllInstantiatedTypeFlow) {
                continue;
            }

            int slot = resultFlows.size();
            flow.setSlot(slot);
            resultFlows.add(flow);

            for (TypeFlow<?> use : flow.getUses()) {
                assert use != null;
                if (use.isClone() || crossMethodUse(flow, use) || nonCloneableFlow(use)) {
                    /*
                     * use is already cloned, or crosses method boundary, or is not a cloneable flow
                     */
                    continue;
                }
                worklist.add(use);
            }
        }

        return resultFlows.toArray(new TypeFlow<?>[resultFlows.size()]);
    }

    public int id() {
        return id;
    }

    public AnalysisContext context() {
        return context;
    }

    public PointsToAnalysisMethod getMethod() {
        return method;
    }

    public FormalReceiverTypeFlow getFormalReceiver() {
        return (FormalReceiverTypeFlow) getParameter(0);
    }

    public void setParameter(int index, FormalParamTypeFlow parameter) {
        assert index >= 0 && index < this.parameters.length;
        parameters[index] = parameter;
    }

    public FormalParamTypeFlow getParameter(int idx) {
        assert idx >= 0 && idx < this.parameters.length;
        return parameters[idx];
    }

    public TypeFlow<?>[] getParameters() {
        return parameters;
    }

    public void setInitialParameterFlow(InitialParamTypeFlow initialParameterFlow, int i) {
        assert i >= 0 && i < this.initialParameterFlows.length;
        initialParameterFlows[i] = initialParameterFlow;
    }

    public InitialParamTypeFlow getInitialParameterFlow(int i) {
        assert i >= 0 && i < this.initialParameterFlows.length;
        return initialParameterFlows[i];
    }

    public TypeFlow<?>[] getInitialParameterFlows() {
        return initialParameterFlows;
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
        assert !(entryFlow instanceof AllInstantiatedTypeFlow);
        if (miscEntryFlows == null) {
            miscEntryFlows = new ArrayList<>();
        }
        miscEntryFlows.add(entryFlow);
    }

    public void setReturnFlow(FormalReturnTypeFlow returnFlow) {
        this.returnFlow = returnFlow;
    }

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
                        invoke = callerInvoke.getTargetMethod().getContextInsensitiveVirtualInvoke();
                    }
                    for (MethodFlowsGraph calleeFlowGraph : invoke.getCalleesFlows(bb)) {
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
            for (MethodFlowsGraph calleeFlowGraph : callerInvoke.getCalleesFlows(bb)) {
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
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        MethodFlowsGraph that = (MethodFlowsGraph) obj;
        return this.method.equals(that.method) && this.isClone == that.isClone && this.context.equals(that.context);
    }

    @Override
    public int hashCode() {
        /* The context is not null only for clone's method flows. */
        return 42 ^ method.hashCode() ^ (this.isClone ? context.hashCode() : 1);
    }

    @Override
    public String toString() {
        return "MethodFlowsGraph<" + method.format("%h.%n(%p)") + " " + (isClone ? context : "original") + ">";
    }
}
