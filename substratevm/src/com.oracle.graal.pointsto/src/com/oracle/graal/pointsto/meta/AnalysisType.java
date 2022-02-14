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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.util.GuardedAnnotationAccess;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.PointsToAnalysis.ConstantObjectsProfiler;
import com.oracle.graal.pointsto.api.DefaultUnsafePartition;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.flow.context.object.ConstantContextSensitiveObject;
import com.oracle.graal.pointsto.heap.TypeData;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.AtomicUtils;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import com.oracle.svm.util.UnsafePartitionKind;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public abstract class AnalysisType implements WrappedJavaType, OriginalClassProvider, Comparable<AnalysisType> {

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<AnalysisType, ConcurrentHashMap> UNSAFE_ACCESS_FIELDS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisType.class, ConcurrentHashMap.class, "unsafeAccessedFields");

    private static final AtomicReferenceFieldUpdater<AnalysisType, ConstantContextSensitiveObject> UNIQUE_CONSTANT_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisType.class, ConstantContextSensitiveObject.class, "uniqueConstant");

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<AnalysisType, Object> INTERCEPTORS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisType.class, Object.class, "interceptors");

    protected final AnalysisUniverse universe;
    private final ResolvedJavaType wrapped;

    private final AtomicBoolean isInHeap = new AtomicBoolean();
    private final AtomicBoolean isAllocated = new AtomicBoolean();
    private final AtomicBoolean isReachable = new AtomicBoolean();
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
    private ConcurrentMap<Constant, ConstantContextSensitiveObject> constantObjectsCache;
    /**
     * A unique ConstantObject per analysis type. When the size of {@link #constantObjectsCache} is
     * above a threshold all the ConstantObject recorded until that moment are merged in the
     * {@link #uniqueConstant}.
     */
    private volatile ConstantContextSensitiveObject uniqueConstant;

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

    private final int dimension;

    @SuppressWarnings("unused") private volatile Object interceptors;

    public enum UsageKind {
        InHeap,
        Allocated,
        Reachable;
    }

    private final AnalysisFuture<Void> initializationTask;
    /**
     * Additional information that is only available for types that are marked as reachable.
     */
    final AnalysisFuture<TypeData> typeData;

    AnalysisType(AnalysisUniverse universe, ResolvedJavaType javaType, JavaKind storageKind, AnalysisType objectType, AnalysisType cloneableType) {
        this.universe = universe;
        this.wrapped = javaType;
        isArray = wrapped.isArray();
        this.storageKind = storageKind;
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
        /* Set the context insensitive analysis object so that it has access to its type id. */
        this.contextInsensitiveAnalysisObject = new AnalysisObject(universe, this);

        assert getSuperclass() == null || getId() > getSuperclass().getId();

        if (isJavaLangObject() || isInterface()) {
            this.isCloneableWithAllocation = false;
        } else {
            this.isCloneableWithAllocation = cloneableType.isAssignableFrom(this);
        }

        /* The registration task initializes the type. */
        this.initializationTask = new AnalysisFuture<>(() -> universe.initializeType(this), null);
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

    public void cleanupAfterAnalysis() {
        instantiatedTypes = null;
        instantiatedTypesNonNull = null;
        assignableTypesState = null;
        assignableTypesNonNullState = null;
        contextInsensitiveAnalysisObject = null;
        constantObjectsCache = null;
        uniqueConstant = null;
        unsafeAccessedFields = null;
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

    public AnalysisObject getCachedConstantObject(PointsToAnalysis bb, JavaConstant constant) {

        /*
         * Constant caching is only used we certain analysis policies. Ideally we would store the
         * cache in the policy, but it is simpler to store the cache for each type.
         */
        assert bb.analysisPolicy().needsConstantCache() : "The analysis policy doesn't specify the need for a constants cache.";
        assert bb.trackConcreteAnalysisObjects(this);
        assert !(constant instanceof PrimitiveConstant) : "The analysis should not model PrimitiveConstant.";

        if (uniqueConstant != null) {
            // The constants have been merged, return the unique constant
            return uniqueConstant;
        }

        if (constantObjectsCache.size() >= PointstoOptions.MaxConstantObjectsPerType.getValue(bb.getOptions())) {
            // The number of constant objects has increased above the limit,
            // merge the constants in the uniqueConstant and return it
            mergeConstantObjects(bb);
            return uniqueConstant;
        }

        // Get the analysis ConstantObject modeling the JavaConstant
        AnalysisObject result = constantObjectsCache.get(constant);
        if (result == null) {
            // Create a ConstantObject to model each JavaConstant
            ConstantContextSensitiveObject newValue = new ConstantContextSensitiveObject(bb, this, constant);
            ConstantContextSensitiveObject oldValue = constantObjectsCache.putIfAbsent(constant, newValue);
            result = oldValue != null ? oldValue : newValue;

            if (PointstoOptions.ProfileConstantObjects.getValue(bb.getOptions())) {
                ConstantObjectsProfiler.registerConstant(this);
                ConstantObjectsProfiler.maybeDumpConstantHistogram();
            }
        }

        return result;
    }

    private void mergeConstantObjects(PointsToAnalysis bb) {
        ConstantContextSensitiveObject uConstant = new ConstantContextSensitiveObject(bb, this, null);
        if (UNIQUE_CONSTANT_UPDATER.compareAndSet(this, null, uConstant)) {
            constantObjectsCache.values().stream().forEach(constantObject -> {
                /*
                 * The order of the two lines below matters: setting the merged flag first, before
                 * doing the actual merging, ensures that concurrent updates to the flow are still
                 * merged correctly.
                 */
                constantObject.setMergedWithUniqueConstantObject();
                constantObject.mergeInstanceFieldsFlows(bb, uniqueConstant);
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
    public AllInstantiatedTypeFlow instantiatedTypes = new AllInstantiatedTypeFlow(this, true);
    public AllInstantiatedTypeFlow instantiatedTypesNonNull = new AllInstantiatedTypeFlow(this, false);

    /*
     * Returns a type flow containing all types that are assignable from this type and are also
     * instantiated.
     */
    public AllInstantiatedTypeFlow getTypeFlow(@SuppressWarnings("unused") BigBang bb, boolean includeNull) {
        if (includeNull) {
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

    public boolean registerAsInHeap() {
        registerAsReachable();
        if (AtomicUtils.atomicMark(isInHeap)) {
            universe.onTypeInstantiated(this, UsageKind.InHeap);
            return true;
        }
        return false;
    }

    /**
     * @param node For future use and debugging
     */
    public boolean registerAsAllocated(Node node) {
        registerAsReachable();
        if (AtomicUtils.atomicMark(isAllocated)) {
            universe.onTypeInstantiated(this, UsageKind.Allocated);
            return true;
        }
        return false;
    }

    /**
     * Register the type as assignable with all its super types. This is a blocking call to ensure
     * that the type is registered with all its super types before it is propagated by the analysis
     * through type flows.
     */
    public void registerAsAssignable(BigBang bb) {
        TypeState typeState = TypeState.forType(((PointsToAnalysis) bb), this, true);
        /*
         * Register the assignable type with its super types. Skip this type, it can lead to a
         * deadlock when this is called when the type is created.
         */
        forAllSuperTypes(t -> t.addAssignableType(bb, typeState), false);
        /* Register the type as assignable to itself. */
        this.addAssignableType(bb, typeState);
    }

    public boolean registerAsReachable() {
        if (!isReachable.get()) {
            forAllSuperTypes(AnalysisType::markReachable);
            return true;
        }
        return false;
    }

    private void markReachable() {
        if (AtomicUtils.atomicMark(isReachable)) {
            universe.notifyReachableType();
            universe.hostVM.checkForbidden(this, UsageKind.Reachable);
            if (isArray()) {
                /*
                 * For array types, distinguishing between "used" and "instantiated" does not
                 * provide any benefits since array types do not implement new methods. Marking all
                 * used array types as instantiated too allows more usages of Arrays.newInstance
                 * without the need of explicit registration of types for reflection.
                 */
                registerAsAllocated(null);
            }
            ensureInitialized();
        }
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

    private void forAllSuperTypes(Consumer<AnalysisType> superTypeConsumer, boolean includeThisType) {
        forAllSuperTypes(elementalType, dimension, includeThisType, superTypeConsumer);
        for (int i = 0; i < dimension; i++) {
            forAllSuperTypes(this, i, false, superTypeConsumer);
        }
        if (dimension > 0 && !elementalType.isPrimitive() && !elementalType.isJavaLangObject()) {
            forAllSuperTypes(universe.objectType(), dimension, true, superTypeConsumer);
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

    private synchronized void addAssignableType(BigBang bb, TypeState typeState) {
        assignableTypesState = TypeState.forUnion(((PointsToAnalysis) bb), assignableTypesState, typeState);
        assignableTypesNonNullState = assignableTypesState.forNonNull(((PointsToAnalysis) bb));
    }

    public TypeData getOrComputeData() {
        GraalError.guarantee(isReachable.get(), "TypeData is only available for reachable types");
        return this.typeData.ensureDone();
    }

    public void ensureInitialized() {
        /* Run the registration and wait for it to complete, if necessary. */
        initializationTask.ensureDone();
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
        boolean instantiated = isInHeap.get() || isAllocated.get();
        assert !instantiated || isReachable.get();
        return instantiated;
    }

    /**
     * Returns true if all instance fields which hold offsets to unsafe field accesses are already
     * recomputed with the correct values from the substrate object layout. Which means that those
     * fields don't need a RecomputeFieldValue annotation.
     */
    public boolean unsafeFieldsRecomputed() {
        return unsafeFieldsRecomputed;
    }

    public boolean isReachable() {
        return isReachable.get();
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
        return universe.substitutions.resolve(wrapped);
    }

    public ResolvedJavaType getWrappedWithoutResolve() {
        return wrapped;
    }

    @Override
    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(universe.getOriginalSnippetReflection(), wrapped);
    }

    @Override
    public final String getName() {
        return wrapped.getName();
    }

    @Override
    public final JavaKind getJavaKind() {
        return wrapped.getJavaKind();
    }

    @Override
    public final AnalysisType resolve(ResolvedJavaType accessingClass) {
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
            throw GraalError.shouldNotReachHere("Classes can only be initialized using methods in ClassInitializationFeature: " + toClassName());
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
    public boolean isPrimitive() {
        return wrapped.isPrimitive();
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        ResolvedJavaType subst = universe.substitutions.resolve(((AnalysisType) other).wrapped);
        return wrapped.isAssignableFrom(subst);
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        return wrapped.isInstance(universe.toHosted(obj));
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
        this.subTypes.add(subType);
    }

    @Override
    public AnalysisType findLeastCommonAncestor(ResolvedJavaType otherType) {
        ResolvedJavaType subst = universe.substitutions.resolve(((AnalysisType) otherType).wrapped);
        return universe.lookup(wrapped.findLeastCommonAncestor(subst));
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
    public ResolvedJavaType getElementalType() {
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
        throw GraalError.unimplemented();
    }

    @Override
    public AnalysisMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        Object resolvedMethod = resolvedMethods.get(method);
        if (resolvedMethod == null) {
            ResolvedJavaMethod substMethod = universe.substitutions.resolve(((AnalysisMethod) method).wrapped);
            /*
             * We do not want any access checks to be performed, so we use the method's declaring
             * class as the caller type.
             */
            ResolvedJavaType substCallerType = substMethod.getDeclaringClass();

            Object newResolvedMethod = universe.lookup(wrapped.resolveConcreteMethod(substMethod, substCallerType));
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
     * Cache is volatile to ensure that the final contents of AnalysisField[] are visible after the
     * array gets visible.
     */
    private volatile AnalysisField[] instanceFieldsWithSuper;
    private volatile AnalysisField[] instanceFieldsWithoutSuper;

    public void clearInstanceFieldsCache() {
        instanceFieldsWithSuper = null;
        instanceFieldsWithoutSuper = null;
    }

    @Override
    public AnalysisField[] getInstanceFields(boolean includeSuperclasses) {
        AnalysisField[] result = includeSuperclasses ? instanceFieldsWithSuper : instanceFieldsWithoutSuper;
        if (result != null) {
            return result;
        } else {
            return initializeInstanceFields(includeSuperclasses);
        }
    }

    private AnalysisField[] initializeInstanceFields(boolean includeSuperclasses) {
        List<AnalysisField> list = new ArrayList<>();
        if (includeSuperclasses && getSuperclass() != null) {
            list.addAll(Arrays.asList(getSuperclass().getInstanceFields(true)));
        }
        AnalysisField[] result = convertFields(interceptInstanceFields(wrapped.getInstanceFields(false)), list, includeSuperclasses);
        if (includeSuperclasses) {
            instanceFieldsWithSuper = result;
        } else {
            instanceFieldsWithoutSuper = result;
        }
        return result;
    }

    private AnalysisField[] convertFields(ResolvedJavaField[] original, List<AnalysisField> list, boolean listIncludesSuperClassesFields) {
        for (int i = 0; i < original.length; i++) {
            if (!original[i].isInternal()) {
                try {
                    AnalysisField aField = universe.lookup(original[i]);
                    if (aField != null) {
                        if (listIncludesSuperClassesFields) {
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
        return list.toArray(new AnalysisField[list.size()]);
    }

    @Override
    public AnalysisField[] getStaticFields() {
        registerAsReachable();
        return convertFields(wrapped.getStaticFields(), new ArrayList<>(), false);
    }

    @Override
    public Annotation[] getAnnotations() {
        return GuardedAnnotationAccess.getAnnotations(wrapped);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return GuardedAnnotationAccess.getDeclaredAnnotations(wrapped);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return GuardedAnnotationAccess.getAnnotation(wrapped, annotationClass);
    }

    @Override
    public String getSourceFileName() {
        // getSourceFileName is not implemented for primitive types
        return wrapped.isPrimitive() ? null : wrapped.getSourceFileName();
    }

    @Override
    public String toString() {
        return "AnalysisType<" + toJavaName(true) + ", allocated: " + isAllocated + ", inHeap: " + isInHeap + ", reachable: " + isReachable + ">";
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
            universe.hostVM().warn("unknown locality of class " + wrapped.getName() + ", assuming class is not local. To remove the warning report an issue " +
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
        ResolvedJavaType wrappedEnclosingType;
        try {
            wrappedEnclosingType = wrapped.getEnclosingType();
        } catch (LinkageError e) {
            /* Ignore LinkageError thrown by enclosing type resolution. */
            return null;
        }
        return universe.lookup(wrappedEnclosingType);
    }

    @Override
    public AnalysisMethod[] getDeclaredConstructors() {
        return universe.lookup(wrapped.getDeclaredConstructors());
    }

    @Override
    public AnalysisMethod[] getDeclaredMethods() {
        return universe.lookup(wrapped.getDeclaredMethods());
    }

    @Override
    public AnalysisMethod findMethod(String name, Signature signature) {
        for (AnalysisMethod method : getDeclaredMethods()) {
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
        return isInHeap.get();
    }

    public boolean isAllocated() {
        return isAllocated.get();
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

    AnalysisUniverse getUniverse() {
        return universe;
    }

    @Override
    public int compareTo(AnalysisType other) {
        return Integer.compare(this.id, other.id);
    }

    @Override
    public int hashCode() {
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
