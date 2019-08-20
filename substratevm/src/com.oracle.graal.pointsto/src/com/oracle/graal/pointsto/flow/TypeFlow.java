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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;

@SuppressWarnings("rawtypes")
public abstract class TypeFlow<T> {
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> USE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "uses");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> INPUTS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "inputs");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> OBSERVERS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "observers");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> OBSERVEES_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "observees");
    protected static final AtomicInteger nextId = new AtomicInteger();

    protected final int id;

    protected final T source;
    protected final AnalysisType declaredType;

    private volatile TypeState state;

    /** The set of all {@link TypeFlow}s that need to be update when this flow changes. */
    @SuppressWarnings("unused") private volatile Object uses;

    /** The set of all flows that have this flow as an use. */
    @SuppressWarnings("unused") private volatile Object inputs;

    /** The set of all observers, i.e., objects that are notified when this flow changes. */
    @SuppressWarnings("unused") private volatile Object observers;

    /** The set of all observees, i.e., objects that notify this flow when they change. */
    @SuppressWarnings("unused") private volatile Object observees;

    private int slot;
    private final boolean isClone; // true -> clone, false -> original
    protected final MethodFlowsGraph graphRef;
    protected final AnalysisContext context;

    /** True if this flow is passed as a parameter to a call. */
    protected boolean usedAsAParameter;

    /**
     * True if this flow is the receiver of a virtual call. If true, usedAsAParameter is also true.
     */
    protected boolean usedAsAReceiver;

    public volatile boolean inQueue;

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<TypeFlow, TypeState> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, TypeState.class, "state");

    private TypeFlow(T source, AnalysisType declaredType, TypeState typeState, int slot, boolean isClone, MethodFlowsGraph graphRef) {
        this.id = nextId.incrementAndGet();
        this.source = source;
        this.declaredType = declaredType;
        this.slot = slot;
        this.isClone = isClone;
        this.graphRef = graphRef;
        this.context = graphRef != null ? graphRef.context() : null;
        this.state = typeState;
        this.usedAsAParameter = false;
        this.usedAsAReceiver = false;
    }

    public TypeFlow() {
        this(null, null, TypeState.forEmpty(), -1, false, null);
    }

    public TypeFlow(TypeState typeState) {
        this(null, null, typeState, -1, false, null);
    }

    public TypeFlow(T source, AnalysisType declaredType) {
        this(source, declaredType, TypeState.forEmpty(), -1, false, null);
    }

    public TypeFlow(T source, AnalysisType declaredType, TypeState state) {
        this(source, declaredType, state, -1, false, null);
    }

    /**
     * Shallow copy constructor. Does not copy the flows or the state.
     *
     * @param original the original flow
     * @param graphRef the holder method clone
     */
    public TypeFlow(TypeFlow<T> original, MethodFlowsGraph graphRef) {
        this(original.getSource(), original.getDeclaredType(), TypeState.forEmpty(), original.getSlot(), true, graphRef);
        this.usedAsAParameter = original.usedAsAParameter;
        this.usedAsAReceiver = original.usedAsAReceiver;
        PointsToStats.registerTypeFlowRetainReason(this, original);
    }

    /**
     * By default a type flow is not cloneable.
     *
     * @param bb
     * @param methodFlows
     */
    public TypeFlow<T> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return this;
    }

    /**
     * Initialization code for some clone corner case type flows.
     *
     * @param bb
     */
    public void initClone(BigBang bb) {
    }

    public void setUsedAsAParameter(boolean usedAsAParameter) {
        this.usedAsAParameter = usedAsAParameter;
    }

    public boolean isUsedAsAParameter() {
        return usedAsAParameter;
    }

    public void setUsedAsAReceiver(boolean usedAsAReceiver) {
        this.usedAsAReceiver = usedAsAReceiver;
    }

    public boolean isUsedAsAReceiver() {
        return usedAsAReceiver;
    }

    /** Some flow have a reciver (e.g., loads, store and invokes). */
    public TypeFlow<?> receiver() {
        return null;
    }

    public int id() {
        return id;
    }

    public AnalysisContext context() {
        return context;
    }

    public MethodFlowsGraph graphRef() {
        return graphRef;
    }

    public AnalysisMethod method() {
        return graphRef != null ? graphRef.getMethod() : null;
    }

    public T getSource() {
        return source;
    }

    public boolean isClone() {
        return isClone;
    }

    public AnalysisType getDeclaredType() {
        return declaredType;
    }

    public TypeState getState() {
        return state;
    }

    public boolean isAllInstantiated() {
        return this instanceof AllInstantiatedTypeFlow;
    }

    public boolean isCloseToAllInstantiated(BigBang bb) {
        return this.getState().closeToAllInstantiated(bb);
    }

    public void setState(BigBang bb, TypeState state) {
        assert !PointstoOptions.ExtendedAsserts.getValue(bb.getOptions()) || this instanceof InstanceOfTypeFlow || state.verifyDeclaredType(declaredType) : "declaredType: " +
                        declaredType.toJavaName(true) + " state: " + state;
        this.state = state;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public int getSlot() {
        return this.slot;
    }

    public boolean addState(BigBang bb, TypeState add) {
        return addState(bb, add, true);
    }

    public boolean addState(BigBang bb, TypeState add, boolean postFlow) {

        PointsToStats.registerTypeFlowUpdate(bb, this, add);

        TypeState before;
        TypeState after;
        TypeState filteredAdd;
        do {
            before = state;
            filteredAdd = filter(bb, add);
            after = TypeState.forUnion(bb, before, filteredAdd);
            if (after.equals(before)) {
                return false;
            }
        } while (!STATE_UPDATER.compareAndSet(this, before, after));

        PointsToStats.registerTypeFlowSuccessfulUpdate(bb, this, add);

        /*
         * Checkcast and instanceof type flows no longer reflect a type state that contains only the
         * types assignable to the declared type; they keep track of all the types discovered during
         * analysis and are always followed by a filter type flow that implements the filter
         * operation based on the declared type.
         */
        assert !PointstoOptions.ExtendedAsserts.getValue(bb.getOptions()) || this instanceof InstanceOfTypeFlow || after.verifyDeclaredType(declaredType) : "declaredType: " +
                        declaredType.toJavaName(true) + " after: " + after + " before: " + before + " this: " + this;

        if (postFlow) {
            bb.postFlow(this);
        }
        return true;
    }

    // manage uses

    /** Adds a use, if not already present, without propagating state. */
    public boolean addOriginalUse(BigBang bb, TypeFlow<?> use) {
        return addUse(bb, use, false, false);
    }

    public boolean addUse(BigBang bb, TypeFlow<?> use) {
        return addUse(bb, use, true, false);
    }

    private boolean addUse(BigBang bb, TypeFlow<?> use, boolean propagateTypeState, boolean registerInput) {
        if (doAddUse(bb, use, registerInput)) {
            if (propagateTypeState) {
                use.addState(bb, getState());
            }
            return true;
        }
        return false;
    }

    protected boolean doAddUse(BigBang bb, TypeFlow<?> use, boolean registerInput) {
        if (use.equals(this)) {
            return false;
        }
        if (bb.trackTypeFlowInputs() || registerInput) {
            use.addInput(this);
        }
        return ConcurrentLightHashSet.addElement(this, USE_UPDATER, use);
    }

    public Collection<TypeFlow<?>> getUses() {
        return ConcurrentLightHashSet.getElements(this, USE_UPDATER);
    }

    // manage observers

    /** Adds an observer, if not already present, without triggering update. */
    public boolean addOriginalObserver(BigBang bb, TypeFlow<?> observer) {
        return addObserver(bb, observer, false, false);
    }

    /** Register object that will be notified when the state of this flow changes. */
    public void addObserver(BigBang bb, TypeFlow<?> observer) {
        addObserver(bb, observer, true, false);
    }

    private boolean addObserver(BigBang bb, TypeFlow<?> observer, boolean triggerUpdate, boolean registerObservees) {
        if (doAddObserver(bb, observer, registerObservees)) {
            if (triggerUpdate) {
                /*
                 * Notify the observer after registering. This flow might have already reached a
                 * fixed point and might never notify its observers otherwise.
                 */
                observer.onObservedUpdate(bb);
            }
            return true;
        }
        return false;
    }

    protected boolean doAddObserver(BigBang bb, TypeFlow<?> observer, boolean registerObservees) {
        if (observer.equals(this)) {
            return false;
        }
        if (bb.trackTypeFlowInputs() || registerObservees) {
            observer.addObservee(this);
        }
        return ConcurrentLightHashSet.addElement(this, OBSERVERS_UPDATER, observer);
    }

    public boolean removeObserver(TypeFlow<?> observer) {
        observer.removeObservee(this);
        return ConcurrentLightHashSet.removeElement(this, OBSERVERS_UPDATER, observer);
    }

    public Collection<TypeFlow<?>> getObservers() {
        return ConcurrentLightHashSet.getElements(this, OBSERVERS_UPDATER);
    }

    /** Let the observers that the state has changed. */
    protected void notifyObservers(BigBang bb) {
        for (TypeFlow<?> observer : getObservers()) {
            observer.onObservedUpdate(bb);
        }
    }

    // manage observees

    public void addObservee(TypeFlow<?> observee) {
        ConcurrentLightHashSet.addElement(this, OBSERVEES_UPDATER, observee);
    }

    public Collection<TypeFlow<?>> getObservees() {
        return ConcurrentLightHashSet.getElements(this, OBSERVEES_UPDATER);
    }

    public boolean removeObservee(TypeFlow<?> observee) {
        return ConcurrentLightHashSet.removeElement(this, OBSERVEES_UPDATER, observee);
    }

    // manage inputs

    public void addInput(TypeFlow<?> input) {
        ConcurrentLightHashSet.addElement(this, INPUTS_UPDATER, input);
    }

    public Collection<TypeFlow<?>> getInputs() {
        return ConcurrentLightHashSet.getElements(this, INPUTS_UPDATER);
    }

    public boolean removeInput(TypeFlow<?> input) {
        return ConcurrentLightHashSet.removeElement(this, INPUTS_UPDATER, input);
    }

    public TypeState filter(@SuppressWarnings("unused") BigBang bb, TypeState newState) {
        return newState;
    }

    public void update(BigBang bb) {
        TypeState curState = getState();
        for (TypeFlow<?> use : getUses()) {
            use.addState(bb, curState);
        }

        notifyObservers(bb);
    }

    /** Notify the observer that the observed type flow state has changed. */
    public void onObservedUpdate(@SuppressWarnings("unused") BigBang bb) {

    }

    @Override
    public String toString() {
        return "TypeFlow<" + (source instanceof Node ? ((StructuredGraph) ((Node) source).graph()).method().format("%h.%n@") : "") + source + ": " + getState() + ">";
    }

    @Override
    public final boolean equals(Object other) {
        return other == this;
    }

    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }
}
