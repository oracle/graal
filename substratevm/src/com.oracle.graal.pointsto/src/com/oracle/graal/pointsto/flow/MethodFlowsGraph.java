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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.LoadIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.vm.ci.code.BytecodePosition;

public class MethodFlowsGraph {

    protected final int id;

    private final AnalysisMethod method;
    private AnalysisContext context;
    private boolean isClone;

    public TypeFlow<?>[] linearizedGraph;

    // parameters, sources and field loads are the possible data entry
    // points in the method
    private FormalParamTypeFlow[] parameters;
    private InitialParamTypeFlow[] initialParameterFlows;
    private List<SourceTypeFlow> sources;
    private List<LoadFieldTypeFlow> fieldLoads;
    private List<LoadIndexedTypeFlow> indexedLoads;
    private List<TypeFlow<?>> miscEntryFlows;

    private List<NewInstanceTypeFlow> allocations;
    private List<DynamicNewInstanceTypeFlow> dynamicAllocations;
    private List<CloneTypeFlow> clones;
    private List<MonitorEnterTypeFlow> monitorEntries;

    /*
     * We keep a bci->flow mapping for instanceof and invoke flows since they are queried by the
     * analysis results builder.
     */
    private Set<Object> nonUniqueBcis;
    private Map<Object, InstanceOfTypeFlow> instanceOfFlows;
    private Map<Object, InvokeTypeFlow> invokeFlows;

    private FormalReturnTypeFlow result;

    private boolean isLinearized = false;
    private boolean sealed;

    /**
     * Constructor for the 'original' method flows graph. This is used as a source for creating
     * clones. It has a <code>null</code> context and <code>null</code> original method flows.
     */
    public MethodFlowsGraph(AnalysisMethod analysisMethod) {

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

        // allocations
        allocations = new ArrayList<>();

        dynamicAllocations = new ArrayList<>();

        monitorEntries = new ArrayList<>();

        // clones
        clones = new ArrayList<>();

        // sources
        sources = new ArrayList<>();

        // field loads
        fieldLoads = new ArrayList<>();

        // indexed loads
        indexedLoads = new ArrayList<>();

        // misc entry inputs
        miscEntryFlows = new ArrayList<>();

        // instanceof
        instanceOfFlows = new HashMap<>();

        // invoke
        invokeFlows = new HashMap<>(4, 0.75f);
        nonUniqueBcis = new HashSet<>();
    }

    public MethodFlowsGraph(AnalysisMethod method, AnalysisContext context) {
        this.id = TypeFlow.nextId.incrementAndGet();
        this.context = context;
        this.method = method;
        this.isClone = true;
    }

    public void cloneOriginalFlows(BigBang bb) {

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

        // initial parameter flows
        initialParameterFlows = new InitialParamTypeFlow[originalMethodFlowsGraph.initialParameterFlows.length];

        // allocations
        allocations = originalMethodFlowsGraph.allocations.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // dynamic allocations
        dynamicAllocations = originalMethodFlowsGraph.dynamicAllocations.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // monitor entries
        monitorEntries = originalMethodFlowsGraph.monitorEntries.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // clones
        clones = originalMethodFlowsGraph.clones.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // sources
        sources = originalMethodFlowsGraph.sources.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // result
        result = originalMethodFlowsGraph.getResult() != null ? lookupCloneOf(bb, originalMethodFlowsGraph.getResult()) : null;

        // field loads
        fieldLoads = originalMethodFlowsGraph.fieldLoads.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // indexed loads
        indexedLoads = originalMethodFlowsGraph.indexedLoads.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // instanceof
        instanceOfFlows = originalMethodFlowsGraph.instanceOfFlows.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> lookupCloneOf(bb, e.getValue())));

        // misc entry flows (merge, proxy, etc.)
        miscEntryFlows = originalMethodFlowsGraph.miscEntryFlows.stream().map(f -> lookupCloneOf(bb, f)).collect(Collectors.toList());

        // instanceof
        invokeFlows = originalMethodFlowsGraph.invokeFlows.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> lookupCloneOf(bb, e.getValue())));

        /* At this point all the clones should have been created. */
        sealed = true;
    }

    @SuppressWarnings("unchecked")
    public <T extends TypeFlow<?>> T lookupCloneOf(BigBang bb, T original) {
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

    public void linkClones(final BigBang bb) {

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

        worklist.addAll(allocations);
        worklist.addAll(dynamicAllocations);
        worklist.addAll(monitorEntries);
        worklist.addAll(clones);
        worklist.addAll(sources);
        worklist.addAll(miscEntryFlows);
        worklist.addAll(fieldLoads);
        worklist.addAll(indexedLoads);

        worklist.addAll(instanceOfFlows.values());
        worklist.addAll(invokeFlows.values());

        if (result != null) {
            worklist.add(result);
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

    public AnalysisMethod getMethod() {
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

    public void addAllocation(NewInstanceTypeFlow allocation) {
        allocations.add(allocation);
    }

    public List<NewInstanceTypeFlow> getAllocations() {
        return allocations;
    }

    public void addDynamicAllocation(DynamicNewInstanceTypeFlow allocation) {
        dynamicAllocations.add(allocation);
    }

    public List<DynamicNewInstanceTypeFlow> getDynamicAllocations() {
        return dynamicAllocations;
    }

    public void addMonitorEntry(MonitorEnterTypeFlow monitorEntry) {
        monitorEntries.add(monitorEntry);
    }

    public List<MonitorEnterTypeFlow> getMonitorEntries() {
        return monitorEntries;
    }

    public void addClone(CloneTypeFlow clone) {
        clones.add(clone);
    }

    public List<CloneTypeFlow> getClones() {
        return clones;
    }

    public void addSource(SourceTypeFlow source) {
        sources.add(source);
    }

    public void addMiscEntryFlow(TypeFlow<?> entryFlow) {
        assert !(entryFlow instanceof AllInstantiatedTypeFlow);
        miscEntryFlows.add(entryFlow);
    }

    public void addFieldLoad(LoadFieldTypeFlow fieldLoad) {
        fieldLoads.add(fieldLoad);
    }

    public void addIndexedLoad(LoadIndexedTypeFlow indexedLoad) {
        indexedLoads.add(indexedLoad);
    }

    public void setResult(FormalReturnTypeFlow result) {
        this.result = result;
    }

    public FormalReturnTypeFlow getResult() {
        return this.result;
    }

    public Set<Entry<Object, InvokeTypeFlow>> getInvokes() {
        return invokeFlows.entrySet();
    }

    public Collection<InvokeTypeFlow> getInvokeFlows() {
        return invokeFlows.values();
    }

    public Set<Entry<Object, InstanceOfTypeFlow>> getInstanceOfFlows() {
        return instanceOfFlows.entrySet();
    }

    void addInstanceOf(Object key, InstanceOfTypeFlow instanceOf) {
        doAddFlow(key, instanceOf, instanceOfFlows);
    }

    void addInvoke(Object key, InvokeTypeFlow invokeTypeFlow) {
        doAddFlow(key, invokeTypeFlow, invokeFlows);
    }

    private <T extends TypeFlow<BytecodePosition>> void doAddFlow(Object key, T flow, Map<Object, T> map) {
        assert map == instanceOfFlows || map == invokeFlows : "Keys of these maps must not be overlapping";
        Object uniqueKey = key;
        if (nonUniqueBcis.contains(key) || removeNonUnique(key, instanceOfFlows) || removeNonUnique(key, invokeFlows)) {
            uniqueKey = new Object();
        }
        map.put(uniqueKey, flow);
    }

    private <T extends TypeFlow<BytecodePosition>> boolean removeNonUnique(Object key, Map<Object, T> map) {
        T oldFlow = map.remove(key);
        if (oldFlow != null) {
            /*
             * This can happen when Graal inlines jsr/ret routines and the inlined nodes share the
             * same bci. Or for some invokes where the bytecode parser needs to insert a type check
             * before the invoke. Remove the old bci->flow pairing and replace it with a
             * uniqueKey->flow pairing.
             */
            map.put(new Object(), oldFlow);
            nonUniqueBcis.add(key);
            return true;
        } else {
            return false;
        }
    }

    public InvokeTypeFlow getInvoke(Object key) {
        return invokeFlows.get(key);
    }

    public boolean isLinearized() {
        return isLinearized;
    }

    /**
     * Get the list of all context sensitive callers.
     *
     * @return a list containing all the callers for the given context sensitive method
     */
    public List<MethodFlowsGraph> callers(BigBang bb) {
        /*
         * This list is seldom needed thus it is created lazily instead of storing a mapping from a
         * caller context to a caller graph for each method graph.
         *
         * TODO cache the result
         */
        List<MethodFlowsGraph> callers = new ArrayList<>();
        for (AnalysisMethod caller : method.getCallers()) {
            for (MethodFlowsGraph callerFlowGraph : caller.getTypeFlow().getFlows()) {
                for (InvokeTypeFlow callerInvoke : callerFlowGraph.getInvokeFlows()) {
                    InvokeTypeFlow invoke = callerInvoke;
                    if (InvokeTypeFlow.isContextInsensitiveVirtualInvoke(callerInvoke)) {
                        /* The invoke has been replaced by the context insensitive one. */
                        invoke = callerInvoke.getTargetMethod().getContextInsensitiveInvoke();
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
    public InvokeTypeFlow invokeFlow(MethodFlowsGraph callerFlowGraph, BigBang bb) {
        for (InvokeTypeFlow callerInvoke : callerFlowGraph.getInvokeFlows()) {
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
