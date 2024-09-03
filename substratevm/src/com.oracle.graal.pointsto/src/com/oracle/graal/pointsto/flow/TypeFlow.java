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

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.PrimitiveConstantTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.graph.Node;
import jdk.vm.ci.code.BytecodePosition;

@SuppressWarnings("rawtypes")
public abstract class TypeFlow<T> {
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> USE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "uses");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> INPUTS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "inputs");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> OBSERVERS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "observers");
    private static final AtomicReferenceFieldUpdater<TypeFlow, Object> OBSERVEES_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, Object.class, "observees");
    protected static final AtomicInteger nextId = new AtomicInteger();

    protected final int id;

    protected T source;
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
     * When static analysis results are built in {@link StrengthenGraphs#applyResults} the type
     * state is considered only if the type flow was not marked as saturated.
     * <p/>
     * The initial value is false, i.e., the flow is initially not saturated.
     */
    private volatile boolean isSaturated;

    /**
     * A TypeFlow is invalidated when the flowsgraph it belongs to is updated due to
     * {@link MethodTypeFlow#updateFlowsGraph}. Once a flow is invalided it no longer needs to be
     * updated and its links can be removed. Note delaying the removal of invalid flows does not
     * affect correctness, so they can be removed lazily.
     */
    private boolean isValid = true;

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<TypeFlow, TypeState> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(TypeFlow.class, TypeState.class, "state");

    protected final boolean isPrimitiveFlow;

    private TypeFlow(T source, AnalysisType declaredType, TypeState typeState, int slot, boolean isClone, MethodFlowsGraph graphRef) {
        this.id = nextId.incrementAndGet();
        this.source = source;
        this.declaredType = declaredType;
        this.slot = slot;
        this.isClone = isClone;
        this.graphRef = graphRef;
        this.state = typeState;
        if (declaredType != null) {
            isPrimitiveFlow = declaredType.isPrimitive() || declaredType.isWordType();
        } else {
            /* If the declared type is not set, try to determine using the initial type state. */
            isPrimitiveFlow = typeState.isPrimitive();
        }
        validateSource();
        assert primitiveFlowCheck(state) : this;
    }

    private void validateSource() {
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
        this(original, graphRef, TypeState.forEmpty());
    }

    @SuppressWarnings("this-escape")
    public TypeFlow(TypeFlow<T> original, MethodFlowsGraph graphRef, TypeState cloneState) {
        this(original.getSource(), original.getDeclaredType(), cloneState, original.getSlot(), true, graphRef);
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
     * Initialization code for some type flow corner cases. {@link #needsInitialization()} also
     * needs to be overridden to enable type flow initialization.
     *
     * @param bb
     */
    public void initFlow(PointsToAnalysis bb) {
        throw AnalysisError.shouldNotReachHere("Type flow " + format(false, true) + " is not overriding initFlow().");
    }

    /**
     * Type flows that require initialization after the graph is created need to override this
     * method and return true.
     */
    public boolean needsInitialization() {
        return false;
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
        if (source instanceof BytecodePosition position && !isClone) {
            MethodTypeFlow methodFlow = ((PointsToAnalysisMethod) position.getMethod()).getTypeFlow();
            if (methodFlow.flowsGraphCreated()) {
                return methodFlow.getMethodFlowsGraph();
            }
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

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public int getSlot() {
        return this.slot;
    }

    /**
     * Return true is the flow is valid and should be updated.
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * Invalidating the typeflow will cause the flow to be lazily removed in the future.
     */
    public void invalidate() {
        isValid = false;
    }

    /**
     * Return true if this flow is saturated. When an observer becomes saturated it doesn't
     * immediately remove itself from all its inputs. The inputs lazily remove it on next update.
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
     * true it cannot be changed.
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

        assert !bb.trackPrimitiveValues() || primitiveFlowCheck(after) : this + "," + after;
        if (checkSaturated(bb, after)) {
            onSaturated(bb);
        } else if (postFlow) {
            bb.postFlow(this);
        }

        return true;
    }

    /**
     * Primitive flows should only have primitive or empty type states.
     */
    private boolean primitiveFlowCheck(TypeState newState) {
        return !isPrimitiveFlow || newState.isPrimitive() || newState.isEmpty();
    }

    // manage uses

    public boolean addUse(PointsToAnalysis bb, TypeFlow<?> use) {
        return addUse(bb, use, true);
    }

    /**
     * Verifies that primitive flows are only connected with other primitive flows.
     */
    private boolean checkDefUseCompatibility(TypeFlow<?> use) {
        if (this.declaredType == null || use.declaredType == null) {
            /* Some flows, e.g. MergeTypeFlow, do not have a declared type. */
            return true;
        }
        if (this.isPrimitiveFlow != use.isPrimitiveFlow) {
            if (this instanceof OffsetStoreTypeFlow.AbstractUnsafeStoreTypeFlow) {
                /*
                 * The links between unsafe store and its uses are the only place where the mix of
                 * primitive/object type states actually happens due to the fact that all unsafe
                 * accessed fields are conceptually merged into one. It does not matter though,
                 * because we only propagate object types states through them. Unsafe accessed
                 * primitive fields are set to AnyPrimitiveTypeState and so are the corresponding
                 * FieldFilterTypeFlows.
                 */
                return true;
            }
            return false;
        }
        return true;

    }

    public boolean addUse(PointsToAnalysis bb, TypeFlow<?> use, boolean propagateTypeState) {
        assert !bb.trackPrimitiveValues() || checkDefUseCompatibility(use) : "Incompatible flows: " + this + " connected with " + use;
        if (isSaturated() && propagateTypeState) {
            /* Register input. */
            registerInput(bb, use);
            /* Let the use know that this flow is already saturated. */
            notifyUseOfSaturation(bb, use);
            return false;
        }
        if (doAddUse(bb, use)) {
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

    protected boolean doAddUse(PointsToAnalysis bb, TypeFlow<?> use) {
        if (use.equals(this)) {
            return false;
        }
        if (!use.isValid()) {
            return false;
        }
        /* Input is always tracked. */
        registerInput(bb, use);
        if (use.isSaturated()) {
            /* The use is already saturated so it will not be linked. */
            return false;
        }
        return ConcurrentLightHashSet.addElement(this, USE_UPDATER, use);
    }

    private void registerInput(PointsToAnalysis bb, TypeFlow<?> use) {
        if (bb.trackTypeFlowInputs()) {
            use.addInput(this);
        }
    }

    public boolean removeUse(TypeFlow<?> use) {
        return ConcurrentLightHashSet.removeElement(this, USE_UPDATER, use);
    }

    public void clearUses() {
        ConcurrentLightHashSet.clear(this, USE_UPDATER);
    }

    public Collection<TypeFlow<?>> getUses() {
        return ConcurrentLightHashSet.getElements(this, USE_UPDATER);
    }

    // manage observers

    /** Register object that will be notified when the state of this flow changes. */
    public void addObserver(PointsToAnalysis bb, TypeFlow<?> observer) {
        addObserver(bb, observer, true);
    }

    public boolean addObserver(PointsToAnalysis bb, TypeFlow<?> observer, boolean triggerUpdate) {
        if (isSaturated() && triggerUpdate) {
            /* Register observee. */
            registerObservee(bb, observer);
            /* Let the observer know that this flow is already saturated. */
            notifyObserverOfSaturation(bb, observer);
            return false;
        }
        if (doAddObserver(bb, observer)) {
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

    private boolean doAddObserver(PointsToAnalysis bb, TypeFlow<?> observer) {
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
        if (!observer.isValid()) {
            return false;
        }

        registerObservee(bb, observer);
        return ConcurrentLightHashSet.addElement(this, OBSERVERS_UPDATER, observer);
    }

    private void registerObservee(PointsToAnalysis bb, TypeFlow<?> observer) {
        if (bb.trackTypeFlowInputs()) {
            observer.addObservee(this);
        }
    }

    public boolean removeObserver(TypeFlow<?> observer) {
        return ConcurrentLightHashSet.removeElement(this, OBSERVERS_UPDATER, observer);
    }

    public void clearObservers() {
        ConcurrentLightHashSet.clear(this, OBSERVERS_UPDATER);
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

    public void clearObservees() {
        ConcurrentLightHashSet.clear(this, OBSERVEES_UPDATER);
    }

    // manage inputs

    public void addInput(TypeFlow<?> input) {
        ConcurrentLightHashSet.addElement(this, INPUTS_UPDATER, input);
    }

    public Collection<TypeFlow<?>> getInputs() {
        return ConcurrentLightHashSet.getElements(this, INPUTS_UPDATER);
    }

    public void clearInputs() {
        ConcurrentLightHashSet.clear(this, INPUTS_UPDATER);
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
        return declaredTypeFilter(bb, newState, true);
    }

    public TypeState declaredTypeFilter(PointsToAnalysis bb, TypeState newState, boolean onlyWithRelaxedTypeFlowConstraints) {
        if (onlyWithRelaxedTypeFlowConstraints && !bb.analysisPolicy().relaxTypeFlowConstraints()) {
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
        if (isPrimitiveFlow) {
            assert newState.isPrimitive() || newState.isEmpty() : newState + "," + this;
            return newState;
        }
        /* By default, filter all type flows with the declared type. */
        return TypeState.forIntersection(bb, newState, declaredType.getAssignableTypes(true));
    }

    /**
     * In Java, interface types are not checked by the bytecode verifier. So even when, e.g., a
     * method parameter has the declared type Comparable, any Object can be passed in. We therefore
     * need to filter out interface types, as well as arrays of interface types, in many places
     * where we use the declared type.
     *
     * Places where interface types need to be filtered: method parameters, method return values,
     * and field loads (including unsafe memory loads).
     *
     * Places where interface types need not be filtered: array element loads (because all array
     * stores have an array store check).
     *
     * One exception are word types. We do not filter them here, because they are transformed to
     * primitive values later on anyway and the knowledge that a given type is a word type is useful
     * when distinguishing primitive and object flows.
     */
    public static AnalysisType filterUncheckedInterface(AnalysisType type) {
        if (type != null) {
            AnalysisType elementalType = type.getElementalType();
            if (elementalType.isInterface() && !elementalType.isWordType()) {
                return type.getUniverse().objectType().getArrayClass(type.getArrayDimension());
            }
        }
        return type;
    }

    public void update(PointsToAnalysis bb) {
        TypeState curState = getState();
        for (TypeFlow<?> use : getUses()) {
            if (!use.isValid() || use.isSaturated()) {
                removeUse(use);
            } else {
                use.addState(bb, curState);
            }
        }

        for (TypeFlow<?> observer : getObservers()) {
            if (observer.isValid()) {
                observer.onObservedUpdate(bb);
            } else {
                removeObserver(observer);
            }
        }
    }

    /** Notify the observer that the observed type flow state has changed. */
    public void onObservedUpdate(@SuppressWarnings("unused") PointsToAnalysis bb) {

    }

    /** Check if the type state is saturated, i.e., its type count is beyond the limit. */
    boolean checkSaturated(PointsToAnalysis bb, TypeState typeState) {
        if (!bb.analysisPolicy().removeSaturatedTypeFlows()) {
            /* If the type flow saturation optimization is disabled just return false. */
            return false;
        }
        if (!canSaturate()) {
            /* This type flow needs to track all its individual types. */
            return false;
        }
        if (typeState.isPrimitive()) {
            return !(typeState instanceof PrimitiveConstantTypeState);
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
            notifyUseOfSaturation(bb, use);
            removeUse(use);
        }
        for (TypeFlow<?> observer : getObservers()) {
            notifyObserverOfSaturation(bb, observer);
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

    void updateSource(T newSource) {
        source = newSource;

        validateSource();
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
