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

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.results.StaticAnalysisResultsBuilder;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.typestate.TypeStateUtils;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import com.oracle.svm.util.ClassUtil;

import jdk.vm.ci.code.BytecodePosition;

@SuppressWarnings("rawtypes")
public abstract class TypeFlow<T> {
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> USE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "uses");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> INPUTS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "inputs");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> OBSERVERS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "observers");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> OBSERVEES_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "observees");
    protected static final AtomicInteger nextId = new AtomicInteger();

    protected final int id;

    protected final T source;
    /*
     * The declared type of the node corresponding to this flow. The declared type is inferred from
     * stamps during bytecode parsing, and, when missing, it is approximated by Object.
     */
    protected final AnalysisType declaredType;

    protected volatile TypeState state;

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

    /** True if this flow is passed as a parameter to a call. */
    protected boolean usedAsAParameter;

    /**
     * True if this flow is the receiver of a virtual call. If true, usedAsAParameter is also true.
     */
    protected boolean usedAsAReceiver;

    public volatile boolean inQueue;

    /**
     * A TypeFlow is saturated when its type count is beyond a predetermined limit set via
     * {@link PointstoOptions#TypeFlowSaturationCutoff}. If true, this flow is marked as saturated,
     * i.e., it will not process state updates from its inputs anymore. Type flows should check the
     * saturated state of an use before calling {@link #addState(PointsToAnalysis, TypeState)} and
     * if the flag is set they should unlink the use. This will result in a lazy removal of this
     * flow from the type flow graph.
     * <p/>
     * A type flow can also be marked as saturated when one of its inputs has reached the saturated
     * state and has propagated the "saturated" marker downstream. Thus, since in such a situation
     * the input stops propagating type states, a flow's type state may be incomplete. It is up to
     * individual type flows to subscribe themselves directly to the type flows of their declared
     * types if they need further updates.
     * <p/>
     * When static analysis results are built in
     * {@link StaticAnalysisResultsBuilder#makeOrApplyResults} the type state is considered only if
     * the type flow was not marked as saturated.
     * <p/>
     * The initial value is false, i.e., the flow is initially not saturated.
     */
    private volatile boolean isSaturated;

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<TypeFlow, TypeState> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, TypeState.class, "state");

    private TypeFlow(T source, AnalysisType declaredType, TypeState typeState, int slot, boolean isClone, MethodFlowsGraph graphRef) {
        this.id = nextId.incrementAndGet();
        this.source = source;
        this.declaredType = declaredType;
        this.slot = slot;
        this.isClone = isClone;
        this.graphRef = graphRef;
        this.state = typeState;
        this.usedAsAParameter = false;
        this.usedAsAReceiver = false;

        assert !(source instanceof Node) : "must not reference Graal node from TypeFlow: " + source;
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

    public TypeFlow(T source, AnalysisType declaredType, boolean canBeNull) {
        this(source, declaredType, canBeNull ? TypeState.forNull() : TypeState.forEmpty(), -1, false, null);
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
    public TypeFlow<T> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return this;
    }

    /**
     * Initialization code for some clone corner case type flows.
     *
     * @param bb
     */
    public void initFlow(PointsToAnalysis bb) {
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

    /** Some flows have a receiver (e.g., loads, store and invokes). */
    public TypeFlow<?> receiver() {
        return null;
    }

    public int id() {
        return id;
    }

    public MethodFlowsGraph graphRef() {
        if (graphRef != null) {
            return graphRef;
        }
        if (source instanceof BytecodePosition && !isClone) {
            BytecodePosition position = (BytecodePosition) source;
            return ((PointsToAnalysisMethod) position.getMethod()).getTypeFlow().getMethodFlowsGraph();
        }
        return null;
    }

    public AnalysisMethod method() {
        if (graphRef != null) {
            return graphRef.getMethod();
        }
        if (source instanceof BytecodePosition) {
            BytecodePosition position = (BytecodePosition) source;
            return (AnalysisMethod) position.getMethod();
        }
        return null;
    }

    public T getSource() {
        return source;
    }

    public boolean isClone() {
        return isClone;
    }

    public boolean isContextInsensitive() {
        return false;
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

    public void setState(PointsToAnalysis bb, TypeState state) {
        assert !bb.extendedAsserts() || this instanceof InstanceOfTypeFlow ||
                        state.verifyDeclaredType(bb, declaredType) : "declaredType: " + declaredType.toJavaName(true) + " state: " + state;
        this.state = state;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public int getSlot() {
        return this.slot;
    }

    /**
     * Return true if this flow is saturated. When an observer becomes saturated it doesn't
     * immediately remove itslef from all its inputs. The inputs lazily remove it on next update.
     */
    public boolean isSaturated() {
        return isSaturated;
    }

    /**
     * Can this type flow saturate? By default all type flows can saturate, with the exception of a
     * few ones that need to track all their types, e.g., AllInstantiated, AllSynchronized, etc.
     */
    public boolean canSaturate() {
        return true;
    }

    /**
     * Mark this flow as saturated. Each flow starts with isSaturated as false and once it is set to
     * true it cannnot be changed.
     */
    public void setSaturated() {
        isSaturated = true;
    }

    public boolean addState(PointsToAnalysis bb, TypeState add) {
        return addState(bb, add, true);
    }

    /* Add state and notify inputs of the result. */
    public boolean addState(PointsToAnalysis bb, TypeState add, boolean postFlow) {
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

        assert !bb.extendedAsserts() || checkTypeState(bb, before, after);

        if (checkSaturated(bb, after)) {
            onSaturated(bb);
        } else if (postFlow) {
            bb.postFlow(this);
        }

        return true;
    }

    private boolean checkTypeState(PointsToAnalysis bb, TypeState before, TypeState after) {
        assert bb.extendedAsserts();

        if (bb.analysisPolicy().relaxTypeFlowConstraints()) {
            return true;
        }

        if (this instanceof InstanceOfTypeFlow || this instanceof FilterTypeFlow) {
            /*
             * The type state of an InstanceOfTypeFlow doesn't contain only types assignable from
             * its declared type. The InstanceOfTypeFlow keeps track of all the types discovered
             * during analysis and there is always a corresponding filter type flow that implements
             * the filter operation based on the declared type.
             *
             * Similarly, since a FilterTypeFlow implements complex logic, i.e., the filter can be
             * either inclusive or exclusive and it can filter exact types or complete type
             * hierarchies, the types in its type state are not necessary assignable from its
             * declared type.
             */
            return true;
        }
        assert after.verifyDeclaredType(bb, declaredType) : String.format("The type state of %s contains types that are not assignable from its declared type %s. " +
                        "%nState before: %s. %nState after: %s", format(false, true), declaredType.toJavaName(true), formatState(bb, before), formatState(bb, after));
        return true;
    }

    private static String formatState(PointsToAnalysis bb, TypeState typeState) {
        if (TypeStateUtils.closeToAllInstantiated(bb, typeState)) {
            return "close to AllInstantiated";
        }
        return typeState.toString();
    }

    // manage uses

    public boolean addUse(PointsToAnalysis bb, TypeFlow<?> use) {
        return addUse(bb, use, true, false);
    }

    public boolean addUse(PointsToAnalysis bb, TypeFlow<?> use, boolean propagateTypeState) {
        return addUse(bb, use, propagateTypeState, false);
    }

    private boolean addUse(PointsToAnalysis bb, TypeFlow<?> use, boolean propagateTypeState, boolean registerInput) {
        if (isSaturated() && propagateTypeState) {
            /* Let the use know that this flow is already saturated. */
            notifyUseOfSaturation(bb, use);
            return false;
        }
        if (doAddUse(bb, use, registerInput)) {
            if (propagateTypeState) {
                if (isSaturated()) {
                    /*
                     * If the flow became saturated while the use was *in-flight*, i.e., after the
                     * check at method entry, but before the use was actually registered, then the
                     * use would have missed the saturated signal. Let the use know that this flow
                     * became saturated.
                     */
                    notifyUseOfSaturation(bb, use);
                    /* And unlink the use. */
                    removeUse(use);
                    return false;
                } else {
                    use.addState(bb, getState());
                }
            }
            return true;
        }
        return false;
    }

    protected void notifyUseOfSaturation(PointsToAnalysis bb, TypeFlow<?> use) {
        use.onInputSaturated(bb, this);
    }

    protected boolean doAddUse(PointsToAnalysis bb, TypeFlow<?> use, boolean registerInput) {
        if (use.isSaturated()) {
            /* The use is already saturated so it will not be linked. */
            return false;
        }
        if (use.equals(this)) {
            return false;
        }
        if (bb.trackTypeFlowInputs() || registerInput) {
            use.addInput(this);
        }
        return ConcurrentLightHashSet.addElement(this, USE_UPDATER, use);
    }

    public boolean removeUse(TypeFlow<?> use) {
        return ConcurrentLightHashSet.removeElement(this, USE_UPDATER, use);
    }

    public Collection<TypeFlow<?>> getUses() {
        return ConcurrentLightHashSet.getElements(this, USE_UPDATER);
    }

    // manage observers

    /** Register object that will be notified when the state of this flow changes. */
    public void addObserver(PointsToAnalysis bb, TypeFlow<?> observer) {
        addObserver(bb, observer, true, false);
    }

    public boolean addObserver(PointsToAnalysis bb, TypeFlow<?> observer, boolean triggerUpdate) {
        return addObserver(bb, observer, triggerUpdate, false);
    }

    private boolean addObserver(PointsToAnalysis bb, TypeFlow<?> observer, boolean triggerUpdate, boolean registerObservees) {
        if (isSaturated() && triggerUpdate) {
            /* Let the observer know that this flow is already saturated. */
            notifyObserverOfSaturation(bb, observer);
            return false;
        }
        if (doAddObserver(bb, observer, registerObservees)) {
            if (triggerUpdate) {
                if (isSaturated()) {
                    /* This flow is already saturated, notify the observer. */
                    notifyObserverOfSaturation(bb, observer);
                    removeObserver(observer);
                    return false;
                } else if (!this.state.isEmpty()) {
                    /* Only trigger an observer update if this flow has a non-empty state. */
                    /*
                     * Notify the observer after registering. This flow might have already reached a
                     * fixed point and might never notify its observers otherwise.
                     */
                    bb.postTask(ignore -> observer.onObservedUpdate(bb));
                }
            }
            return true;
        }
        return false;
    }

    protected void notifyObserverOfSaturation(PointsToAnalysis bb, TypeFlow<?> observer) {
        observer.onObservedSaturated(bb, this);
    }

    private boolean doAddObserver(PointsToAnalysis bb, TypeFlow<?> observer, boolean registerObservees) {
        /*
         * An observer is linked even if it is already saturated itself, hence no
         * 'observer.isSaturated()' check is performed here. For observers the saturation state is
         * that of the values flowing through and not that of the objects they observe.
         * 
         * Some observers may need to continue to observe the state of their receiver object until
         * the receiver object saturates itself, e.g., instance field stores, other observers may
         * deregister themselves from observing the receiver object when they saturate, e.g.,
         * instance field loads.
         */
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

    public TypeState filter(@SuppressWarnings("unused") PointsToAnalysis bb, TypeState newState) {
        return newState;
    }

    /**
     * Filter type states using a flow's declared type. This is used when the type flow constraints
     * are relaxed to make sure that only compatible types are flowing through certain flows, e.g.,
     * stored to fields or passed to parameters. When the type flow constraints are not relaxed
     * incompatible types flowing through such flows will result in an analysis error.
     */
    public TypeState declaredTypeFilter(PointsToAnalysis bb, TypeState newState) {
        if (!bb.analysisPolicy().relaxTypeFlowConstraints()) {
            /* Type flow constraints are enforced, so no default filtering is done. */
            return newState;
        }
        if (declaredType == null) {
            /* The declared type of the operation is not known, no filtering can be done. */
            return newState;
        }
        if (declaredType.equals(bb.getObjectType())) {
            /* If the declared type is Object type there is no need to filter. */
            return newState;
        }
        /* By default, filter all type flows with the declared type. */
        return TypeState.forIntersection(bb, newState, declaredType.getAssignableTypes(true));
    }

    public void update(PointsToAnalysis bb) {
        TypeState curState = getState();
        for (TypeFlow<?> use : getUses()) {
            if (use.isSaturated()) {
                removeUse(use);
            } else {
                use.addState(bb, curState);
            }
        }

        for (TypeFlow<?> observer : getObservers()) {
            observer.onObservedUpdate(bb);
        }
    }

    /** Notify the observer that the observed type flow state has changed. */
    public void onObservedUpdate(@SuppressWarnings("unused") PointsToAnalysis bb) {

    }

    /** Check if the type state is saturated, i.e., its type count is beoynd the limit. */
    boolean checkSaturated(PointsToAnalysis bb, TypeState typeState) {
        if (!bb.analysisPolicy().removeSaturatedTypeFlows()) {
            /* If the type flow saturation optimization is disabled just return false. */
            return false;
        }
        if (!canSaturate()) {
            /* This type flow needs to track all its individual types. */
            return false;
        }
        return typeState.typesCount() > bb.analysisPolicy().typeFlowSaturationCutoff();
    }

    /** Called when this type flow becomes saturated. */
    protected void onSaturated(PointsToAnalysis bb) {
        assert bb.analysisPolicy().removeSaturatedTypeFlows() : "The type flow saturation optimization is disabled.";
        assert canSaturate() : "This type flow cannot saturate.";
        /*
         * Array type flow aliasing needs to be enabled for the type flow saturation optimization to
         * work correctly. When the receiver object of an array load/store operation is saturated,
         * i.e., it will stop sending updates, the load/store needs to subscribe for updates
         * directly to the type flow of the receiver object declared type. However, the declared
         * type cannot always be statically infered from the graphs, thus a conservative
         * approximation such as the Object type can be used. Therefore, all array type flows need
         * to be modeled using a unique elements type flow abstraction.
         */
        assert bb.analysisPolicy().aliasArrayTypeFlows() : "Array type flows must be aliased.";

        if (isSaturated()) {
            /* This flow is already marked as saturated. */
            return;
        }

        /* Mark the flow as saturated, this will lead to lazy removal from *all* its inputs. */
        setSaturated();
        /* Run flow-specific saturation tasks, e.g., stop observing receivers. */
        onSaturated();
        /* Notify uses and observers that this input is saturated and unlink them. */
        notifySaturated(bb);
    }

    protected void onSaturated() {
        // hook for flow-specific saturation tasks
    }

    /*** Notify the uses and observers that this flow is saturated and unlink them. */
    private void notifySaturated(PointsToAnalysis bb) {
        for (TypeFlow<?> use : getUses()) {
            use.onInputSaturated(bb, this);
            removeUse(use);
        }
        for (TypeFlow<?> observer : getObservers()) {
            observer.onObservedSaturated(bb, this);
            removeObserver(observer);
        }
    }

    /** This flow will swap itself out at all uses and observers. */
    protected void swapOut(PointsToAnalysis bb, TypeFlow<?> newFlow) {
        for (TypeFlow<?> use : getUses()) {
            swapAtUse(bb, newFlow, use);
        }
        for (TypeFlow<?> observer : getObservers()) {
            swapAtObserver(bb, newFlow, observer);
        }
    }

    protected void swapAtUse(PointsToAnalysis bb, TypeFlow<?> newFlow, TypeFlow<?> use) {
        removeUse(use);
        newFlow.addUse(bb, use);
    }

    protected void swapAtObserver(PointsToAnalysis bb, TypeFlow<?> newFlow, TypeFlow<?> observer) {
        removeObserver(observer);
        /* Notify the observer that its observed flow has changed. */
        observer.replacedObservedWith(bb, newFlow);
    }

    /**
     * Notified by an input that it is saturated and it will stop sending updates.
     */
    protected void onInputSaturated(PointsToAnalysis bb, @SuppressWarnings("unused") TypeFlow<?> input) {
        assert bb.analysisPolicy().removeSaturatedTypeFlows() : "The type flow saturation optimization is disabled.";
        if (!canSaturate()) {
            /* This type flow needs to track all its individual types. */
            return;
        }
        /*
         * By default when a type flow is notified that one of its inputs is saturated it will just
         * pass this information to its uses and observers and unlink them. Subclases should
         * override this method and provide custom behavior.
         */
        onSaturated(bb);
    }

    /**
     * Notified by an observed flow that it is saturated.
     */
    public void onObservedSaturated(@SuppressWarnings("unused") PointsToAnalysis bb, @SuppressWarnings("unused") TypeFlow<?> observed) {
    }

    /**
     * When an "observed -> observer" link is updated the observer needs to be notified as well so
     * it can update its internal reference. This link update can happen when the observed becomes
     * saturated and it will stop sending updates. It is then usually replaced with a conservative
     * approximation, e.g., the flow of the receiver type for a special invoke operation or of the
     * field declaring class for a field access operation. By default the observers don't use the
     * null state of the observed, therefore the non-null type flow is used.
     * 
     * The overloaded {@link #replacedObservedWith(PointsToAnalysis, TypeFlow)} can be used for
     * replacing the observed with a custom type flow.
     * 
     */
    public void replaceObservedWith(PointsToAnalysis bb, AnalysisType newObservedType) {
        replacedObservedWith(bb, newObservedType.getTypeFlow(bb, false));
    }

    public void replacedObservedWith(PointsToAnalysis bb, TypeFlow<?> newObservedFlow) {
        /*
         * It is important that the observed reference is set before the observer is actually
         * registered. The observer registration will trigger an update and the observer may need
         * the reference to the new observed.
         */
        setObserved(newObservedFlow);
        newObservedFlow.addObserver(bb, this);
    }

    /**
     * Set the type flow that this flow is observing.
     */
    protected void setObserved(@SuppressWarnings("unused") TypeFlow<?> newObservedFlow) {
        /*
         * By default this operation is a NOOP. Subtypes that keep a reference to an observed type
         * flow should override this method and update their internal reference.
         */
    }

    public String formatSource() {
        if (source instanceof BytecodePosition) {
            BytecodePosition position = (BytecodePosition) source;
            return position.getMethod().asStackTraceElement(position.getBCI()).toString();
        }
        if (source == null && method() != null) {
            return method().asStackTraceElement(-1).toString();
        }
        return "<unknown-position>";
    }

    public String format(boolean withState, boolean withSource) {
        return ClassUtil.getUnqualifiedName(getClass()) + (withSource ? " at " + formatSource() : "") + (withState ? " with state <" + getState() + '>' : "");
    }

    @Override
    public String toString() {
        return format(true, true);
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
