/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.DefaultUnsafePartition;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.flow.context.object.ConstantContextSensitiveObject;
import com.oracle.graal.pointsto.heap.TypeData;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.AtomicUtils;
import com.oracle.graal.pointsto.util.ConcurrentLightHashMap;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.UnsafePartitionKind;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public abstract class AnalysisType extends AnalysisElement implements WrappedJavaType, OriginalClassProvider, Comparable<AnalysisType> {

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<AnalysisType, ConcurrentHashMap> UNSAFE_ACCESS_FIELDS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisType.class, ConcurrentHashMap.class, "unsafeAccessedFields");

    private static final AtomicReferenceFieldUpdater<AnalysisType, AnalysisObject> UNIQUE_CONSTANT_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisType.class, AnalysisObject.class, "uniqueConstant");

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> INTERCEPTORS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisType.class, Object.class, "interceptors");

    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> subtypeReachableNotificationsUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisType.class, Object.class, "subtypeReachableNotifications");

    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> overrideReachableNotificationsUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisType.class, Object.class, "overrideReachableNotifications");

    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> instantiatedNotificationsUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisType.class, Object.class, "typeInstantiatedNotifications");

    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> objectReachableCallbacksUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisType.class, Object.class, "objectReachableCallbacks");

    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> isAllocatedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisType.class, Object.class, "isAllocated");

    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> isInHeapUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisType.class, Object.class, "isInHeap");

    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> isReachableUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisType.class, Object.class, "isReachable");

    private static final AtomicIntegerFieldUpdater<AnalysisType> isAnySubtypeInstantiatedUpdater = AtomicIntegerFieldUpdater
                    .newUpdater(AnalysisType.class, "isAnySubtypeInstantiated");

    protected final AnalysisUniverse universe;
    private final ResolvedJavaType wrapped;
    private final String qualifiedName;
    private final String unqualifiedName;

    @SuppressWarnings("unused") private volatile Object isInHeap;
    @SuppressWarnings("unused") private volatile Object isAllocated;
    @SuppressWarnings("unused") private volatile Object isReachable;
    @SuppressWarnings("unused") private volatile int isAnySubtypeInstantiated;
    private boolean reachabilityListenerNotified;
    private boolean unsafeFieldsRecomputed;
    private boolean unsafeAccessedFieldsRegistered;

    /**
     * Unsafe accessed fields for this type.
     *
     * This field can be initialized during the multithreaded analysis phase in case of the computed
     * value fields, thus we use the UNSAFE_ACCESS_FIELDS_UPDATER to initialize it.
     */
    private volatile ConcurrentHashMap<UnsafePartitionKind, Collection<AnalysisField>> unsafeAccessedFields;

    /** Immediate subtypes and this type itself. */
    private final Set<AnalysisType> subTypes;
    AnalysisType superClass;

    private final int id;

    private final JavaKind storageKind;
    private final boolean isCloneableWithAllocation;

    /** The unique context insensitive analysis object for this type. */
    private AnalysisObject contextInsensitiveAnalysisObject;
    /** Mapping from JavaConstant to the analysis ConstantObject. */
    private ConcurrentMap<JavaConstant, AnalysisObject> constantObjectsCache;
    /**
     * A unique ConstantObject per analysis type. When the size of {@link #constantObjectsCache} is
     * above a threshold all the ConstantObject recorded until that moment are merged in the
     * {@link #uniqueConstant}.
     */
    private volatile AnalysisObject uniqueConstant;

    /**
     * Cache for the resolved methods.
     *
     * Map ResolvedJavaMethod to Object and not AnalysisMethod because when the type doesn't
     * implement the method the value stored is {@link AnalysisType#NULL_METHOD}.
     */
    private final ConcurrentHashMap<ResolvedJavaMethod, Object> resolvedMethods = new ConcurrentHashMap<>();

    /**
     * Marker used in the {@link AnalysisType#resolvedMethods} map to signal that the type doesn't
     * implement a method.
     */
    private static final Object NULL_METHOD = new Object();

    private final AnalysisType componentType;
    private final AnalysisType elementalType;

    private final AnalysisType[] interfaces;

    /* isArray is an expensive operation so we eagerly compute it */
    private final boolean isArray;
    private final boolean isJavaLangObject;

    private final int dimension;

    @SuppressWarnings("unused") private volatile Object interceptors;

    public enum UsageKind {
        InHeap,
        Allocated,
        Reachable;
    }

    private final AnalysisFuture<Void> onTypeReachableTask;
    private final AnalysisFuture<Void> initializeMetaDataTask;

    /**
     * Additional information that is only available for types that are marked as reachable. It is
     * preserved after analysis.
     */
    private final AnalysisFuture<TypeData> typeData;

    /**
     * Contains reachability handlers that are notified when any of the subtypes are marked as
     * reachable. Each handler is notified only once per subtype.
     */
    @SuppressWarnings("unused") private volatile Object subtypeReachableNotifications;

    /**
     * Contains reachability handlers that are notified when any of the method override becomes
     * reachable *and* the declaring class of the override (or any subtype) is instantiated. Each
     * handler is notified only once per method override.
     */
    @SuppressWarnings("unused") private volatile Object overrideReachableNotifications;

    /**
     * The reachability handler that were registered at the time the type was marked as reachable.
     * Reachability handler that are added by the user later on are not included in this list, i.e.,
     * there is no guarantee that such handlers have finished execution before, e.g., reading values
     * of fields in that type.
     */
    List<AnalysisFuture<Void>> scheduledTypeReachableNotifications;

    /**
     * Contains callbacks that are notified when this type is marked as instantiated. Each callback
     * is called at least once, but there are no guarantees that it will be called exactly once.
     */
    @SuppressWarnings("unused") private volatile Object typeInstantiatedNotifications;

    /**
     * Contains callbacks that are executed when an object of this type is marked as reachable.
     */
    @SuppressWarnings("unused") private volatile Object objectReachableCallbacks;

    @SuppressWarnings("this-escape")
    public AnalysisType(AnalysisUniverse universe, ResolvedJavaType javaType, JavaKind storageKind, AnalysisType objectType, AnalysisType cloneableType) {
        this.universe = universe;
        this.wrapped = javaType;
        qualifiedName = wrapped.toJavaName(true);
        unqualifiedName = wrapped.toJavaName(false);

        isArray = wrapped.isArray();
        isJavaLangObject = wrapped.isJavaLangObject();
        this.storageKind = storageKind;

        if (!(isPrimitive() || isWordType())) {
            this.instantiatedTypes = new AllInstantiatedTypeFlow(this, true);
            this.instantiatedTypesNonNull = new AllInstantiatedTypeFlow(this, false);
        }

        if (universe.analysisPolicy().needsConstantCache()) {
            this.constantObjectsCache = new ConcurrentHashMap<>();
        }

        try {
            /*
             * Try to link the type. Without linking, later method resolution would fail. While most
             * types that can be linked successfully are already linked at this time, in some cases
             * we see not-yet-linked types.
             */
            link();
        } catch (Throwable ex) {
            /*
             * Ignore any linking errors. Linking can fail for example when the class path is
             * incomplete. Such classes will be marked for initialization at run time, and the
             * proper linking error will be thrown at run time.
             */
        }

        /* Ensure the super types as well as the component type (for arrays) is created too. */
        superClass = universe.lookup(wrapped.getSuperclass());
        interfaces = convertTypes(wrapped.getInterfaces());

        if (isArray()) {
            this.componentType = universe.lookup(wrapped.getComponentType());
            int dim = 0;
            AnalysisType elemType = this;
            while (elemType.isArray()) {
                elemType = elemType.getComponentType();
                dim++;
            }
            if (elemType.getSuperclass() != null) {
                elemType.getSuperclass().getArrayClass(dim);
            }
            this.elementalType = elemType;
            if (dim >= 2) {
                objectType.getArrayClass(dim - 1);
            }
            for (AnalysisType interf : elemType.getInterfaces()) {
                interf.getArrayClass(dim);
            }
            dimension = dim;
        } else {
            this.componentType = null;
            this.elementalType = this;
            dimension = 0;
        }

        /* Set id after accessing super types, so that all these types get a lower id number. */
        this.id = universe.nextTypeId.getAndIncrement();
        /*
         * Only after setting the id, the hashCode and compareTo methods work properly. So only now
         * it is allowed to put the type into a hashmap, e.g., invoke addSubType.
         */
        subTypes = ConcurrentHashMap.newKeySet();
        addSubType(this);

        /* Build subtypes. */
        if (superClass != null) {
            superClass.addSubType(this);
        }
        if (isInterface() && interfaces.length == 0) {
            objectType.addSubType(this);
        }
        for (AnalysisType interf : interfaces) {
            interf.addSubType(this);
        }

        /* Set the context insensitive analysis object so that it has access to its type id. */
        this.contextInsensitiveAnalysisObject = new AnalysisObject(universe, this);

        assert getSuperclass() == null || getId() > getSuperclass().getId();

        if (isJavaLangObject() || isInterface()) {
            this.isCloneableWithAllocation = false;
        } else {
            this.isCloneableWithAllocation = cloneableType.isAssignableFrom(this);
        }

        /* The registration task initializes the type. */
        this.onTypeReachableTask = new AnalysisFuture<>(() -> universe.onTypeReachable(this), null);
        this.initializeMetaDataTask = new AnalysisFuture<>(() -> universe.initializeMetaData(this), null);
        this.typeData = new AnalysisFuture<>(() -> {
            AnalysisError.guarantee(universe.getHeapScanner() != null, "Heap scanner is not available.");
            return universe.getHeapScanner().computeTypeData(this);
        });
    }

    private AnalysisType[] convertTypes(ResolvedJavaType[] originalTypes) {
        List<AnalysisType> result = new ArrayList<>(originalTypes.length);
        for (ResolvedJavaType originalType : originalTypes) {
            if (universe.hostVM.skipInterface(universe, originalType, wrapped)) {
                continue;
            }
            result.add(universe.lookup(originalType));
        }
        return result.toArray(new AnalysisType[result.size()]);
    }

    public AnalysisType getArrayClass(int dim) {
        AnalysisType result = this;
        for (int i = 0; i < dim; i++) {
            result = result.getArrayClass();
        }
        return result;
    }

    public int getArrayDimension() {
        return dimension;
    }

    public void cleanupAfterAnalysis() {
        instantiatedTypes = null;
        instantiatedTypesNonNull = null;
        assignableTypesState = null;
        assignableTypesNonNullState = null;
        contextInsensitiveAnalysisObject = null;
        constantObjectsCache = null;
        uniqueConstant = null;
        unsafeAccessedFields = null;
        scheduledTypeReachableNotifications = null;
    }

    public int getId() {
        return id;
    }

    public AnalysisObject getContextInsensitiveAnalysisObject() {
        return contextInsensitiveAnalysisObject;
    }

    public AnalysisObject getUniqueConstantObject() {
        return uniqueConstant;
    }

    public AnalysisObject getCachedConstantObject(PointsToAnalysis bb, JavaConstant constant, Function<JavaConstant, AnalysisObject> constantTransformer) {

        /*
         * Constant caching is only used with certain analysis policies. Ideally we would store the
         * cache in the policy, but it is simpler to store the cache for each type.
         */
        assert bb.analysisPolicy().needsConstantCache() : "The analysis policy doesn't specify the need for a constants cache.";
        assert bb.trackConcreteAnalysisObjects(this) : this;
        assert !(constant instanceof PrimitiveConstant) : "The analysis should not model PrimitiveConstant.";

        if (uniqueConstant != null) {
            // The constants have been merged, return the unique constant
            return uniqueConstant;
        }

        /* If maxConstantObjectsPerType is 0 there is no limit, i.e., we track all constants. */
        if (bb.maxConstantObjectsPerType() > 0 && constantObjectsCache.size() >= bb.maxConstantObjectsPerType()) {
            // The number of constant objects has increased above the limit,
            // merge the constants in the uniqueConstant and return it
            mergeConstantObjects(bb);
            return uniqueConstant;
        }

        /* Get the analysis ConstantObject modeling the JavaConstant. */
        return constantObjectsCache.computeIfAbsent(constant, constantTransformer);
    }

    private void mergeConstantObjects(PointsToAnalysis bb) {
        ConstantContextSensitiveObject uConstant = new ConstantContextSensitiveObject(bb, this);
        if (UNIQUE_CONSTANT_UPDATER.compareAndSet(this, null, uConstant)) {
            constantObjectsCache.values().forEach(constantObject -> {
                /*
                 * The order of the two lines below matters: setting the merged flag first, before
                 * doing the actual merging, ensures that concurrent updates to the flow are still
                 * merged correctly.
                 */
                if (constantObject instanceof ConstantContextSensitiveObject) {
                    ConstantContextSensitiveObject ct = (ConstantContextSensitiveObject) constantObject;
                    ct.setMergedWithUniqueConstantObject();
                    ct.mergeInstanceFieldsFlows(bb, uniqueConstant);
                }
            });
        }
    }

    /**
     * Stores the list of all assignable types for each analysis type. The assignable list is
     * updated whenever a new subtype is created. This is used to filter the types assignable to a
     * specific type from an input type state.
     */
    public TypeState assignableTypesState = TypeState.forNull();
    public TypeState assignableTypesNonNullState = TypeState.forEmpty();

    /**
     * Type flows containing all the instantiated sub-types. This is a sub set of the all assignable
     * types. These flows are used for uses that need to be notified when a sub-type of a specific
     * type is marked as instantiated, e.g., a saturated field access type flow needs to be notified
     * when a sub-type of its declared type is marked as instantiated.
     */
    public AllInstantiatedTypeFlow instantiatedTypes;
    public AllInstantiatedTypeFlow instantiatedTypesNonNull;

    /*
     * Returns a type flow containing all types that are assignable from this type and are also
     * instantiated.
     */
    public TypeFlow<?> getTypeFlow(@SuppressWarnings("unused") BigBang bb, boolean includeNull) {
        if (isPrimitive() || isWordType()) {
            return ((PointsToAnalysis) bb).getAnyPrimitiveSourceTypeFlow();
        } else if (includeNull) {
            return instantiatedTypes;
        } else {
            return instantiatedTypesNonNull;
        }
    }

    /**
     * Returns the assignable types. Assignable types are updated on analysis type creation. The
     * types in this list are not guaranteed to be instantiated and should only be used for filter
     * operations.
     */
    public TypeState getAssignableTypes(boolean includeNull) {
        if (includeNull) {
            return assignableTypesState;
        } else {
            return assignableTypesNonNullState;
        }
    }

    public static boolean verifyAssignableTypes(BigBang bb) {
        List<AnalysisType> allTypes = bb.getUniverse().getTypes();

        Set<String> mismatchedAssignableResults = ConcurrentHashMap.newKeySet();
        allTypes.parallelStream().filter(t -> t.instantiatedTypes != null).forEach(t1 -> {
            for (AnalysisType t2 : allTypes) {
                boolean expected;
                if (t2.isInstantiated()) {
                    expected = t1.isAssignableFrom(t2);
                } else {
                    expected = false;
                }
                boolean actual = t1.instantiatedTypes.getState().containsType(t2);

                if (actual != expected) {
                    mismatchedAssignableResults.add("assignableTypes mismatch: " +
                                    t1.toJavaName(true) + " (instantiated: " + t1.isInstantiated() + ") - " +
                                    t2.toJavaName(true) + " (instantiated: " + t2.isInstantiated() + "): " +
                                    "expected=" + expected + ", actual=" + actual);
                }
            }
        });
        if (!mismatchedAssignableResults.isEmpty()) {
            mismatchedAssignableResults.forEach(System.err::println);
            throw new AssertionError("Verification of all-instantiated type flows failed");
        }
        return true;
    }

    /**
     * @param reason the {@link BytecodePosition} where this type is marked as in-heap, or a
     *            {@link com.oracle.graal.pointsto.ObjectScanner.ScanReason}, or a {@link String}
     *            describing why this type was manually marked as in-heap
     */
    public boolean registerAsInHeap(Object reason) {
        assert isValidReason(reason) : "Registering a type as in-heap needs to provide a valid reason.";
        registerAsReachable(reason);
        if (AtomicUtils.atomicSet(this, reason, isInHeapUpdater)) {
            onInstantiated(UsageKind.InHeap);
            return true;
        }
        return false;
    }

    /**
     * @param reason the {@link BytecodePosition} where this type is marked as allocated, or a
     *            {@link com.oracle.graal.pointsto.ObjectScanner.ScanReason}, or a {@link String}
     *            describing why this type was manually marked as allocated
     */
    public boolean registerAsAllocated(Object reason) {
        assert isValidReason(reason) : "Registering a type as allocated needs to provide a valid reason.";
        registerAsReachable(reason);
        if (AtomicUtils.atomicSet(this, reason, isAllocatedUpdater)) {
            onInstantiated(UsageKind.Allocated);
            return true;
        }
        return false;
    }

    protected void onInstantiated(UsageKind usage) {
        universe.onTypeInstantiated(this, usage);
        notifyInstantiatedCallbacks();
        processMethodOverrides();
    }

    private void processMethodOverrides() {
        /*
         * Walk up the type hierarchy from this type keeping track of all processed types. For each
         * superType iterate all the override notifications and resolve the methods in all the seen
         * types, which are subtypes from the point of view of the superType itself. Thus, although
         * only *this* type may be marked as instantiated, any *intermediate* types between this
         * type and a super type that declares override notifications will be processed and any
         * overrides, if reachable, will be passed to the callback. These intermediate types, i.e.,
         * the seenSubtypes set, may not be instantiated but the overrides that they declare, if
         * reachable, could be specially invoked, e.g., via super calls. Note that the baseMethod is
         * not actually an override of itself, but the `registerMethodOverrideReachabilityHandler`
         * API explicitly includes the base method too.
         */
        Set<AnalysisType> seenSubtypes = new HashSet<>();
        forAllSuperTypes(superType -> {
            AtomicUtils.atomicMark(superType, isAnySubtypeInstantiatedUpdater);
            seenSubtypes.add(superType);
            Map<AnalysisMethod, Set<MethodOverrideReachableNotification>> overrides = ConcurrentLightHashMap.getEntries(superType, overrideReachableNotificationsUpdater);
            for (var entry : overrides.entrySet()) {
                AnalysisMethod baseMethod = entry.getKey();
                Set<MethodOverrideReachableNotification> overrideNotifications = entry.getValue();
                for (AnalysisType subType : seenSubtypes) {
                    AnalysisMethod override = baseMethod.resolveInType(subType);
                    if (override != null && override.isReachable()) {
                        overrideNotifications.forEach(n -> n.notifyCallback(universe, override));
                    }
                }
            }
        });
    }

    /**
     * Register the type as assignable with all its super types. This is a blocking call to ensure
     * that the type is registered with all its super types before it is propagated by the analysis
     * through type flows.
     */
    @SuppressWarnings("unused")
    public void registerAsAssignable(BigBang bb) {
    }

    public boolean registerAsReachable(Object reason) {
        assert isValidReason(reason) : "Registering a type as reachable needs to provide a valid reason.";
        if (!AtomicUtils.isSet(this, isReachableUpdater)) {
            /* First mark all super types as reachable. */
            forAllSuperTypes(type -> type.registerAsReachable(reason), false);
            /*
             * Only mark this type itself as reachable after marking all supertypes. This ensures
             * that, e.g., a type is never seen as reachable by another thread before all of its
             * supertypes are already marked as reachable too. Note that this does *not* guarantee
             * that the onReachable hook for all supertypes is already finished, because they can
             * still be running in another thread.
             */
            AtomicUtils.atomicSetAndRun(this, reason, isReachableUpdater, this::onReachable);
            return true;
        }
        return false;
    }

    @Override
    protected void onReachable() {
        List<AnalysisFuture<Void>> futures = new ArrayList<>();
        notifyReachabilityCallbacks(universe, futures);
        forAllSuperTypes(type -> ConcurrentLightHashSet.forEach(type, subtypeReachableNotificationsUpdater,
                        (SubtypeReachableNotification n) -> futures.add(n.notifyCallback(universe, this))));

        if (futures.size() > 0) {
            scheduledTypeReachableNotifications = futures;
        }

        universe.notifyReachableType();
        universe.hostVM.checkForbidden(this, UsageKind.Reachable);
        if (isArray()) {
            /*
             * For array types, distinguishing between "used" and "instantiated" does not provide
             * any benefits since array types do not implement new methods. Marking all used array
             * types as instantiated too allows more usages of Arrays.newInstance without the need
             * of explicit registration of types for reflection.
             */
            registerAsAllocated("All array types are marked as instantiated eagerly.");
        }
        ensureOnTypeReachableTaskDone();
    }

    public void registerSubtypeReachabilityNotification(SubtypeReachableNotification notification) {
        ConcurrentLightHashSet.addElement(this, subtypeReachableNotificationsUpdater, notification);
    }

    public void registerOverrideReachabilityNotification(AnalysisMethod declaredMethod, MethodOverrideReachableNotification notification) {
        assert declaredMethod.getDeclaringClass() == this : declaredMethod;
        Set<MethodOverrideReachableNotification> overrideNotifications = ConcurrentLightHashMap.computeIfAbsent(this,
                        overrideReachableNotificationsUpdater, declaredMethod, m -> ConcurrentHashMap.newKeySet());
        overrideNotifications.add(notification);
    }

    public Set<MethodOverrideReachableNotification> getOverrideReachabilityNotifications(AnalysisMethod method) {
        return ConcurrentLightHashMap.getOrDefault(this, overrideReachableNotificationsUpdater, method, Collections.emptySet());
    }

    public <T> void registerObjectReachableCallback(ObjectReachableCallback<T> callback) {
        ConcurrentLightHashSet.addElement(this, objectReachableCallbacksUpdater, callback);
        /* Register the callback with already discovered subtypes too. */
        for (AnalysisType subType : subTypes) {
            /* Subtypes include this type itself. */
            if (!subType.equals(this)) {
                subType.registerObjectReachableCallback(callback);
            }
        }
    }

    public <T> void notifyObjectReachable(DuringAnalysisAccess access, T object, ScanReason reason) {
        ConcurrentLightHashSet.forEach(this, objectReachableCallbacksUpdater, (ObjectReachableCallback<T> c) -> c.doCallback(access, object, reason));
    }

    public void registerInstantiatedCallback(Consumer<DuringAnalysisAccess> callback) {
        if (this.isInstantiated()) {
            /* If the type is already instantiated just trigger the callback. */
            callback.accept(universe.getConcurrentAnalysisAccess());
        } else {
            ElementNotification notification = new ElementNotification(callback);
            ConcurrentLightHashSet.addElement(this, instantiatedNotificationsUpdater, notification);
            if (this.isInstantiated()) {
                /*
                 * If the type became instantiated during registration manually trigger the
                 * callback.
                 */
                notifyInstantiatedCallback(notification);
            }
        }
    }

    private void notifyInstantiatedCallback(ElementNotification notification) {
        notification.notifyCallback(universe, this);
        ConcurrentLightHashSet.removeElement(this, instantiatedNotificationsUpdater, notification);
    }

    protected void notifyInstantiatedCallbacks() {
        ConcurrentLightHashSet.forEach(this, instantiatedNotificationsUpdater, (ElementNotification c) -> c.notifyCallback(universe, this));
        ConcurrentLightHashSet.removeElementIf(this, instantiatedNotificationsUpdater, ElementNotification::isNotified);
    }

    /**
     * Iterates all super types for this type, where a super type is defined as any type that is
     * assignable from this type, feeding each of them to the consumer.
     *
     * For a class B extends A, the array type A[] is not a superclass of the array type B[]. So
     * there is no strict need to make A[] reachable when B[] is reachable. But it turns out that
     * this is puzzling for users, and there are frameworks that instantiate such arrays
     * programmatically using Array.newInstance(). To reduce the amount of manual configuration that
     * is necessary, we mark all array types of the elemental supertypes and superinterfaces also as
     * reachable.
     *
     * Moreover, even if B extends A doesn't imply that B[] extends A[] it does imply that
     * A[].isAssignableFrom(B[]).
     *
     * NOTE: This method doesn't guarantee that a super type will only be processed once. For
     * example when java.lang.Class is processed its interface java.lang.reflect.AnnotatedElement is
     * reachable directly, but also through java.lang.GenericDeclaration, so it will be processed
     * twice.
     */
    public void forAllSuperTypes(Consumer<AnalysisType> superTypeConsumer) {
        forAllSuperTypes(superTypeConsumer, true);
    }

    protected void forAllSuperTypes(Consumer<AnalysisType> superTypeConsumer, boolean includeThisType) {
        forAllSuperTypes(elementalType, dimension, includeThisType, superTypeConsumer);
        for (int i = 0; i < dimension; i++) {
            forAllSuperTypes(this, i, false, superTypeConsumer);
        }
        if (dimension > 0 && !elementalType.isPrimitive() && !elementalType.isJavaLangObject()) {
            forAllSuperTypes(universe.objectType(), dimension, true, superTypeConsumer);
        }
        if (this.isInterface()) {
            superTypeConsumer.accept(universe.objectType());
        }
    }

    private static void forAllSuperTypes(AnalysisType elementType, int arrayDimension, boolean processType, Consumer<AnalysisType> superTypeConsumer) {
        if (elementType == null) {
            return;
        }
        if (processType) {
            superTypeConsumer.accept(elementType.getArrayClass(arrayDimension));
        }
        for (AnalysisType interf : elementType.getInterfaces()) {
            forAllSuperTypes(interf, arrayDimension, true, superTypeConsumer);
        }
        forAllSuperTypes(elementType.getSuperclass(), arrayDimension, true, superTypeConsumer);
    }

    protected synchronized void addAssignableType(BigBang bb, TypeState typeState) {
        assignableTypesState = TypeState.forUnion(((PointsToAnalysis) bb), assignableTypesState, typeState);
        assignableTypesNonNullState = assignableTypesState.forNonNull(((PointsToAnalysis) bb));
    }

    public TypeData getOrComputeData() {
        GraalError.guarantee(isReachable(), "TypeData is only available for reachable types");
        return this.typeData.ensureDone();
    }

    public void ensureOnTypeReachableTaskDone() {
        /* Run the registration and wait for it to complete, if necessary. */
        onTypeReachableTask.ensureDone();
    }

    public AnalysisFuture<Void> getInitializeMetaDataTask() {
        return initializeMetaDataTask;
    }

    public boolean getReachabilityListenerNotified() {
        return reachabilityListenerNotified;
    }

    public void setReachabilityListenerNotified(boolean reachabilityListenerNotified) {
        this.reachabilityListenerNotified = reachabilityListenerNotified;
    }

    /**
     * Says that all instance fields which hold offsets to unsafe field accesses are already
     * recomputed with the correct values from the substrate object layout and therefore don't need
     * a RecomputeFieldValue annotation.
     */
    public void registerUnsafeFieldsRecomputed() {
        unsafeFieldsRecomputed = true;
    }

    /**
     * Add the field to the collection of unsafe accessed fields declared by this type.
     *
     * A field can potentially be registered as unsafe accessed multiple times, depending on the
     * feature implementation, but we add it to the partition only once, when it is first accessed.
     * This is controlled by the isUnsafeAccessed flag in the AnalysField. Also, a field cannot be
     * part of more than one partitions.
     */
    public void registerUnsafeAccessedField(AnalysisField field, UnsafePartitionKind partitionKind) {

        unsafeAccessedFieldsRegistered = true;

        if (unsafeAccessedFields == null) {
            /* Lazily initialize the map, not all types have unsafe accessed fields. */
            UNSAFE_ACCESS_FIELDS_UPDATER.compareAndSet(this, null, new ConcurrentHashMap<>());
        }

        Collection<AnalysisField> unsafePartition = unsafeAccessedFields.get(partitionKind);
        if (unsafePartition == null) {
            /*
             * We use a thread safe collection to store an unsafe accessed fields partition. Since
             * elements can be added to it concurrently using a non thread safe collection, such as
             * an array list, can result in null being added to the list. Since we don't need index
             * access ConcurrentLinkedQueue is a good match.
             */
            Collection<AnalysisField> newPartition = new ConcurrentLinkedQueue<>();
            Collection<AnalysisField> oldPartition = unsafeAccessedFields.putIfAbsent(partitionKind, newPartition);
            unsafePartition = oldPartition != null ? oldPartition : newPartition;
        }

        assert !unsafePartition.contains(field) : "Field " + field + " already registered as unsafe accessed with " + this;
        unsafePartition.add(field);
    }

    private boolean hasUnsafeAccessedFields() {
        /*
         * Walk up the inheritance chain, as soon as we encounter a class that has unsafe accessed
         * fields we return true, otherwise we reach the top of the hierarchy and return false.
         *
         * Since unsafe accessed fields can be registered on the fly, i.e., during the analysis, we
         * cannot cache this result. If we cached the result and the result was false, i.e., no
         * unsafe accessed fields were registered yet, we would have to invalidate it when a field
         * is registered as unsafe during the analysis and then walk down the type hierarchy and
         * invalidate the cached value of all the sub-types.
         */
        return unsafeAccessedFieldsRegistered || (getSuperclass() != null && getSuperclass().hasUnsafeAccessedFields());
    }

    public List<AnalysisField> unsafeAccessedFields() {
        return unsafeAccessedFields(DefaultUnsafePartition.get());
    }

    public List<AnalysisField> unsafeAccessedFields(UnsafePartitionKind partitionKind) {
        if (!hasUnsafeAccessedFields()) {
            /*
             * Do a quick check if this type has unsafe accessed fields before constructing the data
             * structures holding all the unsafe accessed fields: the ones of this type and the ones
             * up its type hierarchy.
             */
            return Collections.emptyList();
        }
        return allUnsafeAccessedFields(partitionKind);
    }

    private List<AnalysisField> allUnsafeAccessedFields(UnsafePartitionKind partitionKind) {
        /*
         * Walk up the type hierarchy and build the unsafe partition containing all the unsafe
         * fields of the current type and all its super types. The unsafePartition collection
         * doesn't need to be thread safe since updates to it are only done on the current thread.
         *
         * The resulting list could be cached but the caching mechanism is complicated by
         * registering unsafe accessed fields during the analysis. When a field is registered as
         * unsafe on the fly it must be propagated to all the sub-types of its declaring class, but
         * we update the sub-types list only after each analysis macro-iteration. This can create
         * situations where some unsafe writes/reads to/from unsafe accessed fields will be missed.
         * Caching would still be possible, but it would be unnecessary complicated and prone to
         * race conditions.
         */
        List<AnalysisField> unsafePartition = new ArrayList<>();
        unsafePartition.addAll(unsafeAccessedFields != null && unsafeAccessedFields.containsKey(partitionKind) ? unsafeAccessedFields.get(partitionKind) : Collections.emptyList());
        if (getSuperclass() != null) {
            List<AnalysisField> superFileds = getSuperclass().allUnsafeAccessedFields(partitionKind);
            unsafePartition.addAll(superFileds);
        }

        return unsafePartition;
    }

    public boolean isInstantiated() {
        boolean instantiated = isInHeap() || isAllocated();
        assert !instantiated || isReachable() : this;
        return instantiated;
    }

    /** Returns true if this type or any of its subtypes was marked as instantiated. */
    public boolean isAnySubtypeInstantiated() {
        return AtomicUtils.isSet(this, isAnySubtypeInstantiatedUpdater);
    }

    /**
     * Returns true if all instance fields which hold offsets to unsafe field accesses are already
     * recomputed with the correct values from the substrate object layout. Which means that those
     * fields don't need a RecomputeFieldValue annotation.
     */
    public boolean unsafeFieldsRecomputed() {
        return unsafeFieldsRecomputed;
    }

    @Override
    public boolean isReachable() {
        return AtomicUtils.isSet(this, isReachableUpdater);
    }

    public Object getReachableReason() {
        return isReachable;
    }

    /**
     * The kind of the field in memory (in contrast to {@link #getJavaKind()}, which is the kind of
     * the field on the Java type system level). For example {@link WordBase word types} have a
     * {@link #getJavaKind} of {@link JavaKind#Object}, but a primitive {@link #storageKind}.
     */
    public final JavaKind getStorageKind() {
        return storageKind;
    }

    /**
     * Returns true if this type is part of the word type hierarchy, i.e, implements
     * {@link WordBase}.
     */
    public boolean isWordType() {
        /* Word types are currently the only types where kind and storageKind differ. */
        return getJavaKind() != getStorageKind();
    }

    @Override
    public ResolvedJavaType getWrapped() {
        return wrapped;
    }

    @Override
    public ResolvedJavaType unwrapTowardsOriginalType() {
        return wrapped;
    }

    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(this);
    }

    @Override
    public final String getName() {
        return wrapped.getName();
    }

    @Override
    public String toJavaName() {
        return qualifiedName;
    }

    @Override
    public String toJavaName(boolean qualified) {
        return qualified ? qualifiedName : unqualifiedName;
    }

    @Override
    public final JavaKind getJavaKind() {
        return wrapped.getJavaKind();
    }

    @Override
    public final ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public final boolean hasFinalizer() {
        /* We just ignore finalizers. */
        return false;
    }

    @Override
    public final AssumptionResult<Boolean> hasFinalizableSubclass() {
        /* We just ignore finalizers. */
        return new AssumptionResult<>(false);
    }

    @Override
    public final boolean isInitialized() {
        return universe.hostVM.isInitialized(this);
    }

    @Override
    public void initialize() {
        if (!wrapped.isInitialized()) {
            throw GraalError.shouldNotReachHere("Classes can only be initialized using methods in ClassInitializationFeature: " + toClassName()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private volatile AnalysisType arrayClass = null;

    @Override
    public final AnalysisType getArrayClass() {
        if (arrayClass == null) {
            arrayClass = universe.lookup(wrapped.getArrayClass());
        }
        return arrayClass;
    }

    @Override
    public boolean isInterface() {
        return wrapped.isInterface();
    }

    @Override
    public boolean isEnum() {
        return wrapped.isEnum();
    }

    @Override
    public boolean isInstanceClass() {
        return wrapped.isInstanceClass();
    }

    @Override
    public boolean isArray() {
        return isArray;
    }

    @Override
    public boolean isJavaLangObject() {
        return isJavaLangObject;
    }

    @Override
    public boolean isPrimitive() {
        return wrapped.isPrimitive();
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return wrapped.isAssignableFrom(OriginalClassProvider.getOriginalType(other));
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        return wrapped.isInstance(obj);
    }

    @Override
    public AnalysisType getSuperclass() {
        return superClass;
    }

    @Override
    public AnalysisType[] getInterfaces() {
        return interfaces;
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        /*
         * New classes can be loaded during the analysis, so we cannot guarantee a consistent and
         * correct result. So we need to conservatively say that there is no single implementor.
         */
        return this;
    }

    /** Get the immediate subtypes, including this type itself. */
    public Set<AnalysisType> getSubTypes() {
        return subTypes;
    }

    private void addSubType(AnalysisType subType) {
        boolean result = this.subTypes.add(subType);
        /* Register the object reachability callbacks with the newly discovered subtype. */
        if (!subType.equals(this)) {
            /* Subtypes include this type itself. */
            ConcurrentLightHashSet.forEach(this, objectReachableCallbacksUpdater, (ObjectReachableCallback<Object> callback) -> subType.registerObjectReachableCallback(callback));
        }
        assert result : "Tried to add a " + subType + " which is already registered";
    }

    /**
     * Collects and returns *all* subtypes of this type, not only the immediate subtypes, including
     * this type itself, regardless of reachability status. To access the immediate subtypes use
     * {@link AnalysisType#getSubTypes()}.
     *
     * Since the subtypes are updated continuously as the universe is expanded this method may
     * return different results on each call, until the analysis universe reaches a stable state.
     */
    public Set<AnalysisType> getAllSubtypes() {
        HashSet<AnalysisType> result = new HashSet<>();
        collectSubtypes(this, result);
        return result;
    }

    private static void collectSubtypes(AnalysisType baseType, Set<AnalysisType> result) {
        for (AnalysisType subType : baseType.getSubTypes()) {
            if (result.add(subType)) {
                collectSubtypes(subType, result);
            }
        }
    }

    @Override
    public AnalysisType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return universe.lookup(wrapped.findLeastCommonAncestor(OriginalClassProvider.getOriginalType(otherType)));
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        AssumptionResult<ResolvedJavaType> wrappedResult = wrapped.findLeafConcreteSubtype();
        if (wrappedResult != null && wrappedResult.isAssumptionFree()) {
            return new AssumptionResult<>(universe.lookup(wrappedResult.getResult()));
        }
        return null;
    }

    @Override
    public AnalysisType getComponentType() {
        return componentType;
    }

    @Override
    public AnalysisType getElementalType() {
        return elementalType;
    }

    public boolean hasSubTypes() {
        /* subTypes always includes this type itself. */
        return subTypes.size() > 1;
    }

    @Override
    public AnalysisMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        /*
         * Not needed on Substrate VM for now. We also do not have the necessary information
         * available to implement it for JIT compilation at image run time. So we want to make sure
         * that Graal is not using this method, and only resolveConcreteMethod instead.
         */
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public AnalysisMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        Object resolvedMethod = resolvedMethods.get(method);
        if (resolvedMethod == null) {
            ResolvedJavaMethod originalMethod = OriginalMethodProvider.getOriginalMethod(method);
            Object newResolvedMethod = null;
            if (originalMethod != null) {
                /*
                 * We do not want any access checks to be performed, so we use the method's
                 * declaring class as the caller type.
                 */
                ResolvedJavaType originalCallerType = originalMethod.getDeclaringClass();

                newResolvedMethod = universe.lookup(wrapped.resolveConcreteMethod(originalMethod, originalCallerType));
                if (newResolvedMethod == null) {
                    newResolvedMethod = getUniverse().getBigbang().fallbackResolveConcreteMethod(this, (AnalysisMethod) method);
                }
            }

            if (newResolvedMethod == null) {
                newResolvedMethod = NULL_METHOD;
            }
            Object oldResolvedMethod = resolvedMethods.putIfAbsent(method, newResolvedMethod);
            resolvedMethod = oldResolvedMethod != null ? oldResolvedMethod : newResolvedMethod;
        }
        return resolvedMethod == NULL_METHOD ? null : (AnalysisMethod) resolvedMethod;
    }

    /**
     * Wrapper for resolveConcreteMethod() without the callerType parameter. We ignore the
     * callerType parameter and use substMethod.getDeclaringClass() instead since we don't want any
     * access checks in the analysis.
     */
    public AnalysisMethod resolveConcreteMethod(ResolvedJavaMethod method) {
        return resolveConcreteMethod(method, null);
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        // ResolvedJavaMethod subst = universe.substitutions.resolve(((AnalysisMethod)
        // method).wrapped);
        // return universe.lookup(wrapped.findUniqueConcreteMethod(subst));
        return null;
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        /*
         * In the analysis universe, we still use the hosted field offsets, so we can just delegate
         * to the wrapped type.
         */
        return universe.lookup(wrapped.findInstanceFieldWithOffset(offset, expectedKind));
    }

    /*
     * Cache is volatile to ensure that the final contents of ResolvedJavaField[] are visible after
     * the array gets visible.
     *
     * Although all elements are of type AnalysisField, we set this array to be of type
     * ResolvedJavaField so that runtime compilation does not need to convert the array type.
     */
    private volatile ResolvedJavaField[] instanceFieldsWithSuper;
    private volatile ResolvedJavaField[] instanceFieldsWithoutSuper;

    public void clearInstanceFieldsCache() {
        instanceFieldsWithSuper = null;
        instanceFieldsWithoutSuper = null;
    }

    /**
     * Note that although this returns a ResolvedJavaField[], all instance fields are of type
     * AnalysisField and can be casted to AnalysisField without problem.
     */
    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        ResolvedJavaField[] result = includeSuperclasses ? instanceFieldsWithSuper : instanceFieldsWithoutSuper;
        if (result != null) {
            return result;
        } else {
            return initializeInstanceFields(includeSuperclasses);
        }
    }

    private ResolvedJavaField[] initializeInstanceFields(boolean includeSuperclasses) {
        List<ResolvedJavaField> list = new ArrayList<>();
        if (includeSuperclasses && getSuperclass() != null) {
            list.addAll(Arrays.asList(getSuperclass().getInstanceFields(true)));
        }
        ResolvedJavaField[] result = convertFields(interceptInstanceFields(wrapped.getInstanceFields(false)), list, includeSuperclasses);
        if (includeSuperclasses) {
            instanceFieldsWithSuper = result;
        } else {
            instanceFieldsWithoutSuper = result;
        }
        return result;
    }

    private ResolvedJavaField[] convertFields(ResolvedJavaField[] originals, List<ResolvedJavaField> list, boolean listIncludesSuperClassesFields) {
        for (ResolvedJavaField original : originals) {
            if (!original.isInternal() && universe.hostVM.platformSupported(original)) {
                try {
                    AnalysisField aField = universe.lookup(original);
                    if (aField != null) {
                        if (listIncludesSuperClassesFields || aField.isStatic()) {
                            /*
                             * If the list includes the super classes fields, register the position.
                             */
                            aField.setPosition(list.size());
                        }
                        list.add(aField);
                    }
                } catch (UnsupportedFeatureException ex) {
                    // Ignore deleted fields and fields of deleted types.
                }
            }
        }
        return list.toArray(new ResolvedJavaField[list.size()]);
    }

    /**
     * Note that although this returns a ResolvedJavaField[], all instance fields are of type
     * AnalysisField and can be casted to AnalysisField without problem.
     */
    @Override
    public ResolvedJavaField[] getStaticFields() {
        return convertFields(wrapped.getStaticFields(), new ArrayList<>(), false);
    }

    @Override
    public String getSourceFileName() {
        // getSourceFileName is not implemented for primitive types
        return wrapped.isPrimitive() ? null : wrapped.getSourceFileName();
    }

    @Override
    public String toString() {
        return "AnalysisType<" + unqualifiedName + " -> " + wrapped.toString() + ", allocated: " + (isAllocated != null) +
                        ", inHeap: " + (isInHeap != null) + ", reachable: " + (isReachable != null) + ">";
    }

    @Override
    public boolean isLocal() {
        /*
         * Meta programs and languages often get the naming of their anonymous classes wrong. This
         * makes, getSimpleName in isLocal to fail and prevents us from compiling those bytecodes.
         * Since, isLocal is not very important for anonymous classes we can ignore this failure.
         */
        try {
            return wrapped.isLocal();
        } catch (InternalError e) {
            LogUtils.warning("Unknown locality of class " + wrapped.getName() + ", assuming class is not local. To remove the warning report an issue " +
                            "to the library or language author. The issue is caused by " + wrapped.getName() + " which is not following the naming convention.");
            return false;
        }
    }

    @Override
    public boolean isMember() {
        return wrapped.isMember();
    }

    @Override
    public AnalysisType getEnclosingType() {
        return universe.lookup(wrapped.getEnclosingType());
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public AnalysisMethod[] getDeclaredMethods(boolean forceLink) {
        GraalError.guarantee(forceLink == false, "only use getDeclaredMethods without forcing to link, because linking can throw LinkageError");
        return universe.lookup(wrapped.getDeclaredMethods(forceLink));
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return getDeclaredConstructors(true);
    }

    @Override
    public AnalysisMethod[] getDeclaredConstructors(boolean forceLink) {
        GraalError.guarantee(forceLink == false, "only use getDeclaredConstructors without forcing to link, because linking can throw LinkageError");
        return universe.lookup(wrapped.getDeclaredConstructors(forceLink));
    }

    @Override
    public AnalysisMethod findMethod(String name, Signature signature) {
        for (AnalysisMethod method : getDeclaredMethods(false)) {
            if (method.getName().equals(name) && method.getSignature().equals(signature)) {
                return method;
            }
        }
        return null;
    }

    @Override
    public AnalysisMethod getClassInitializer() {
        return universe.lookup(wrapped.getClassInitializer());
    }

    @Override
    public boolean isLinked() {
        /*
         * If the wrapped type is referencing some missing types verification may fail and the type
         * will not be linked.
         */
        return wrapped.isLinked();
    }

    public boolean isInHeap() {
        return AtomicUtils.isSet(this, isInHeapUpdater);
    }

    public Object getInHeapReason() {
        return isInHeap;
    }

    public boolean isAllocated() {
        return AtomicUtils.isSet(this, isAllocatedUpdater);
    }

    public Object getAllocatedReason() {
        return isAllocated;
    }

    @Override
    public void link() {
        wrapped.link();
    }

    @Override
    public boolean hasDefaultMethods() {
        return wrapped.hasDefaultMethods();
    }

    @Override
    public boolean declaresDefaultMethods() {
        return wrapped.declaresDefaultMethods();
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return isCloneableWithAllocation;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResolvedJavaType getHostClass() {
        return universe.lookup(wrapped.getHostClass());
    }

    @Override
    public AnalysisUniverse getUniverse() {
        return universe;
    }

    @Override
    public int compareTo(AnalysisType other) {
        return Integer.compare(this.id, other.id);
    }

    @Override
    public int hashCode() {
        assert id != 0 || isJavaLangObject() : "Type id not set yet";
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    /* Value copied from java.lang.Class. */
    private static final int ANNOTATION = 0x00002000;

    /* Method copied from java.lang.Class. */
    public boolean isAnnotation() {
        return (getModifiers() & ANNOTATION) != 0;
    }

    public void addInstanceFieldsInterceptor(InstanceFieldsInterceptor interceptor) {
        ConcurrentLightHashSet.addElement(this, INTERCEPTORS_UPDATER, interceptor);
    }

    private ResolvedJavaField[] interceptInstanceFields(ResolvedJavaField[] fields) {
        ResolvedJavaField[] result = fields;
        for (Object interceptor : ConcurrentLightHashSet.getElements(this, INTERCEPTORS_UPDATER)) {
            result = ((InstanceFieldsInterceptor) interceptor).interceptInstanceFields(universe, result, this);
        }
        return result;
    }

    public interface InstanceFieldsInterceptor {
        ResolvedJavaField[] interceptInstanceFields(AnalysisUniverse universe, ResolvedJavaField[] fields, AnalysisType type);
    }
}
