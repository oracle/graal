/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class AnalysisUniverse implements Universe {

    protected final HostVM hostVM;

    private static final int ESTIMATED_FIELDS_PER_TYPE = 3;
    public static final int ESTIMATED_NUMBER_OF_TYPES = 2000;
    static final int ESTIMATED_METHODS_PER_TYPE = 15;
    static final int ESTIMATED_EMBEDDED_ROOTS = 500;

    private final ConcurrentMap<ResolvedJavaType, Object> types = new ConcurrentHashMap<>(ESTIMATED_NUMBER_OF_TYPES);
    private final ConcurrentMap<ResolvedJavaField, AnalysisField> fields = new ConcurrentHashMap<>(ESTIMATED_FIELDS_PER_TYPE * ESTIMATED_NUMBER_OF_TYPES);
    private final ConcurrentMap<ResolvedJavaMethod, AnalysisMethod> methods = new ConcurrentHashMap<>(ESTIMATED_METHODS_PER_TYPE * ESTIMATED_NUMBER_OF_TYPES);
    private final ConcurrentMap<ResolvedSignature<AnalysisType>, ResolvedSignature<AnalysisType>> uniqueSignatures = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConstantPool, WrappedConstantPool> constantPools = new ConcurrentHashMap<>(ESTIMATED_NUMBER_OF_TYPES);
    private final ConcurrentHashMap<Constant, Object> embeddedRoots = new ConcurrentHashMap<>(ESTIMATED_EMBEDDED_ROOTS);
    private final ConcurrentMap<AnalysisField, Boolean> unsafeAccessedStaticFields = new ConcurrentHashMap<>();

    private boolean sealed;

    private volatile AnalysisType[] typesById = new AnalysisType[ESTIMATED_NUMBER_OF_TYPES];
    final AtomicInteger nextTypeId = new AtomicInteger();
    final AtomicInteger nextMethodId = new AtomicInteger(1);
    final AtomicInteger nextFieldId = new AtomicInteger(1);

    /**
     * True if the analysis has converged and the analysis data is valid. This is similar to
     * {@link #sealed} but in contrast to {@link #sealed}, the analysis data can be set to invalid
     * again, e.g. if features modify the universe.
     */
    boolean analysisDataValid;

    protected final SubstitutionProcessor substitutions;

    private Function<Object, Object>[] objectReplacers;

    private SubstitutionProcessor[] featureSubstitutions;
    private SubstitutionProcessor[] featureNativeSubstitutions;

    private final MetaAccessProvider originalMetaAccess;
    private final AnalysisFactory analysisFactory;
    private final AnnotationExtractor annotationExtractor;

    private final AtomicInteger numReachableTypes = new AtomicInteger();

    private AnalysisType objectClass;
    private AnalysisType cloneableClass;
    private final JavaKind wordKind;
    private AnalysisPolicy analysisPolicy;
    private ImageHeapScanner heapScanner;
    private ImageLayerLoader imageLayerLoader;
    private HeapSnapshotVerifier heapVerifier;
    private BigBang bb;
    private DuringAnalysisAccess concurrentAnalysisAccess;

    public JavaKind getWordKind() {
        return wordKind;
    }

    @SuppressWarnings("unchecked")
    public AnalysisUniverse(HostVM hostVM, JavaKind wordKind, AnalysisPolicy analysisPolicy, SubstitutionProcessor substitutions, MetaAccessProvider originalMetaAccess,
                    AnalysisFactory analysisFactory, AnnotationExtractor annotationExtractor) {
        this.hostVM = hostVM;
        this.wordKind = wordKind;
        this.analysisPolicy = analysisPolicy;
        this.substitutions = substitutions;
        this.originalMetaAccess = originalMetaAccess;
        this.analysisFactory = analysisFactory;
        this.annotationExtractor = annotationExtractor;

        sealed = false;
        objectReplacers = (Function<Object, Object>[]) new Function<?, ?>[0];
        featureSubstitutions = new SubstitutionProcessor[0];
        featureNativeSubstitutions = new SubstitutionProcessor[0];
    }

    @Override
    public HostVM hostVM() {
        return hostVM;
    }

    protected AnnotationExtractor getAnnotationExtractor() {
        return annotationExtractor;
    }

    public int getNextTypeId() {
        return nextTypeId.get();
    }

    public int getNextMethodId() {
        return nextMethodId.get();
    }

    public int getNextFieldId() {
        return nextFieldId.get();
    }

    public void seal() {
        sealed = true;
    }

    public boolean sealed() {
        return sealed;
    }

    public void setAnalysisDataValid(boolean dataIsValid) {
        if (dataIsValid) {
            collectMethodImplementations();
        }
        analysisDataValid = dataIsValid;
    }

    public AnalysisType optionalLookup(ResolvedJavaType type) {
        ResolvedJavaType actualType = substitutions.lookup(type);
        Object claim = types.get(actualType);
        if (claim instanceof AnalysisType) {
            return (AnalysisType) claim;
        }
        return null;
    }

    @Override
    public AnalysisType lookup(JavaType type) {
        JavaType result = lookupAllowUnresolved(type);
        if (result == null) {
            return null;
        } else if (result instanceof ResolvedJavaType) {
            return (AnalysisType) result;
        }
        throw new UnsupportedFeatureException("Unresolved type found. Probably there are some compilation or classpath problems. " + type.toJavaName(true));
    }

    @Override
    public JavaType lookupAllowUnresolved(JavaType rawType) {
        if (rawType == null) {
            return null;
        }
        if (!(rawType instanceof ResolvedJavaType)) {
            return rawType;
        }
        assert !(rawType instanceof AnalysisType) : "lookupAllowUnresolved does not support analysis types.";

        ResolvedJavaType hostType = (ResolvedJavaType) rawType;
        ResolvedJavaType type = substitutions.lookup(hostType);
        AnalysisType result = optionalLookup(type);
        if (result == null) {
            result = createType(type);
        }
        if (result.isInBaseLayer()) {
            /*
             * The constants can only be relinked after the type is registered as the dynamic hub is
             * not available otherwise.
             */
            getImageLayerLoader().loadAndRelinkTypeConstants(result);
        }
        assert typesById[result.getId()].equals(result) : result;
        return result;
    }

    @SuppressFBWarnings(value = {"ES_COMPARING_STRINGS_WITH_EQ"}, justification = "Bug in findbugs")
    private AnalysisType createType(ResolvedJavaType type) {
        if (!hostVM.platformSupported(type)) {
            throw new UnsupportedFeatureException("Type is not available in this platform: " + type.toJavaName(true));
        }
        if (sealed && !type.isArray()) {
            /*
             * We allow new array classes to be created at any time, since they do not affect the
             * closed world analysis.
             */
            throw AnalysisError.typeNotFound(type);
        }

        /* Run additional checks on the type. */
        hostVM.checkType(type, this);

        /*
         * We do not want multiple threads to create the AnalysisType simultaneously, because we
         * want a unique id number per type. So claim the type first, and only when the claim
         * succeeds create the AnalysisType.
         */
        Object claim = Thread.currentThread().getName();
        retry: while (true) {
            Object result = types.putIfAbsent(type, claim);
            if (result instanceof AnalysisType) {
                return (AnalysisType) result;
            } else if (result != null) {
                /*
                 * Another thread installed a claim, wait until that thread publishes the
                 * AnalysisType.
                 */
                do {
                    result = types.get(type);
                    if (result == null) {
                        /*
                         * The other thread gave up, probably because of an exception. Re-try to
                         * create the type ourself. Probably we are going to fail and throw an
                         * exception too, but that is OK.
                         */
                        continue retry;
                    } else if (result == claim) {
                        /*
                         * We are waiting for a type that we have claimed ourselves => deadlock.
                         */
                        throw JVMCIError.shouldNotReachHere("Deadlock creating new types");
                    }
                } while (!(result instanceof AnalysisType));
                return (AnalysisType) result;
            } else {
                break retry;
            }
        }

        try {
            JavaKind storageKind = originalMetaAccess.lookupJavaType(WordBase.class).isAssignableFrom(OriginalClassProvider.getOriginalType(type)) ? wordKind : type.getJavaKind();
            AnalysisType newValue = analysisFactory.createType(this, type, storageKind, objectClass, cloneableClass);

            synchronized (this) {
                /*
                 * Synchronize modifications of typesById, so that we do not lose array stores in
                 * one thread that run concurrently with Arrays.copyOf in another thread. This is
                 * the only code that writes to typesById. Reads from typesById in other parts do
                 * not need synchronization because typesById is a volatile field, i.e., reads
                 * always pick up the latest and longest array; and we never publish a type before
                 * it is registered in typesById, i.e., before the array has the appropriate length.
                 */
                if (newValue.getId() >= typesById.length) {
                    typesById = Arrays.copyOf(typesById, typesById.length * 2);
                }
                assert typesById[newValue.getId()] == null;
                typesById[newValue.getId()] = newValue;

                if (objectClass == null && newValue.isJavaLangObject()) {
                    objectClass = newValue;
                } else if (cloneableClass == null && newValue.toJavaName(true).equals(Cloneable.class.getName())) {
                    cloneableClass = newValue;
                }
            }

            /*
             * Registering the type can throw an exception. Doing it after the synchronized block
             * ensures that typesById doesn't contain any null values. This could happen since the
             * AnalysisType constructor increments the nextTypeId counter.
             */
            hostVM.registerType(newValue);

            /* Register the type as assignable with all its super types before it is published. */
            if (bb != null) {
                newValue.registerAsAssignable(bb);
            }

            /*
             * Now that our type is correctly registered in the id-to-type array, make it accessible
             * by other threads.
             */
            Object oldValue = types.put(type, newValue);
            assert oldValue == claim : oldValue + " != " + claim;
            claim = null;

            return newValue;

        } finally {
            /* In case of any exception, remove the claim so that we do not deadlock. */
            if (claim != null) {
                types.remove(type, claim);
            }
        }
    }

    @Override
    public AnalysisField lookup(JavaField field) {
        JavaField result = lookupAllowUnresolved(field);
        if (result == null) {
            return null;
        } else if (result instanceof ResolvedJavaField) {
            return (AnalysisField) result;
        }
        throw new UnsupportedFeatureException("Unresolved field found. Probably there are some compilation or classpath problems. " + field.format("%H.%n"));
    }

    @Override
    public JavaField lookupAllowUnresolved(JavaField rawField) {
        if (rawField == null) {
            return null;
        }
        if (!(rawField instanceof ResolvedJavaField)) {
            return rawField;
        }
        assert !(rawField instanceof AnalysisField) : rawField;

        ResolvedJavaField field = (ResolvedJavaField) rawField;

        field = substitutions.lookup(field);
        AnalysisField result = fields.get(field);
        if (result == null) {
            result = createField(field);
        }
        return result;
    }

    private AnalysisField createField(ResolvedJavaField field) {
        if (!hostVM.platformSupported(field)) {
            throw new UnsupportedFeatureException("Field is not available in this platform: " + field.format("%H.%n"));
        }
        if (sealed) {
            return null;
        }
        AnalysisField newValue = analysisFactory.createField(this, field);
        AnalysisField oldValue = fields.putIfAbsent(field, newValue);
        if (oldValue == null && newValue.isInBaseLayer()) {
            getImageLayerLoader().loadFieldFlags(newValue);
        }
        return oldValue != null ? oldValue : newValue;
    }

    @Override
    public AnalysisMethod lookup(JavaMethod method) {
        JavaMethod result = lookupAllowUnresolved(method);
        if (result == null) {
            return null;
        } else if (result instanceof ResolvedJavaMethod) {
            return (AnalysisMethod) result;
        }
        throw new UnsupportedFeatureException("Unresolved method found: " + (method != null ? method.format("%H.%n(%p)") : "null") +
                        ". Probably there are some compilation or classpath problems. ");
    }

    @Override
    public JavaMethod lookupAllowUnresolved(JavaMethod rawMethod) {
        if (rawMethod == null) {
            return null;
        }
        if (!(rawMethod instanceof ResolvedJavaMethod)) {
            return rawMethod;
        }
        assert !(rawMethod instanceof AnalysisMethod) : rawMethod;

        ResolvedJavaMethod method = (ResolvedJavaMethod) rawMethod;
        method = substitutions.lookup(method);
        AnalysisMethod result = methods.get(method);
        if (result == null) {
            result = createMethod(method);
        }
        return result;
    }

    private AnalysisMethod createMethod(ResolvedJavaMethod method) {
        if (!hostVM.platformSupported(method)) {
            throw new UnsupportedFeatureException("Method " + method.format("%H.%n(%p)" + " is not available in this platform."));
        }
        if (sealed) {
            return null;
        }
        AnalysisMethod newValue = analysisFactory.createMethod(this, method);
        AnalysisMethod oldValue = methods.putIfAbsent(method, newValue);
        if (oldValue == null && newValue.isInBaseLayer()) {
            getImageLayerLoader().patchBaseLayerMethod(newValue);
        }
        return oldValue != null ? oldValue : newValue;
    }

    public AnalysisMethod[] lookup(JavaMethod[] inputs) {
        List<AnalysisMethod> result = new ArrayList<>(inputs.length);
        for (JavaMethod method : inputs) {
            if (hostVM.platformSupported((ResolvedJavaMethod) method)) {
                AnalysisMethod aMethod = lookup(method);
                if (aMethod != null) {
                    result.add(aMethod);
                }
            }
        }
        return result.toArray(new AnalysisMethod[result.size()]);
    }

    @Override
    public ResolvedSignature<AnalysisType> lookup(Signature signature, ResolvedJavaType defaultAccessingClass) {
        assert !(defaultAccessingClass instanceof WrappedJavaType) : defaultAccessingClass;

        AnalysisType[] paramTypes = new AnalysisType[signature.getParameterCount(false)];
        for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = lookup(resolveSignatureType(signature.getParameterType(i, defaultAccessingClass), defaultAccessingClass));
        }
        AnalysisType returnType = lookup(resolveSignatureType(signature.getReturnType(defaultAccessingClass), defaultAccessingClass));
        ResolvedSignature<AnalysisType> key = ResolvedSignature.fromArray(paramTypes, returnType);

        return uniqueSignatures.computeIfAbsent(key, k -> k);
    }

    private ResolvedJavaType resolveSignatureType(JavaType type, ResolvedJavaType defaultAccessingClass) {
        /*
         * We must not invoke resolve() on an already resolved type because it can actually fail the
         * accessibility check when synthetic methods and synthetic signatures are involved.
         */
        if (type instanceof ResolvedJavaType resolvedType) {
            return resolvedType;
        }

        try {
            return type.resolve(defaultAccessingClass);
        } catch (LinkageError e) {
            /*
             * Type resolution fails if the parameter type is missing. Just erase the type by
             * returning the Object type.
             */
            return objectType().getWrapped();
        }
    }

    @Override
    public WrappedConstantPool lookup(ConstantPool constantPool, ResolvedJavaType defaultAccessingClass) {
        assert !(constantPool instanceof WrappedConstantPool) : constantPool;
        assert !(defaultAccessingClass instanceof WrappedJavaType) : defaultAccessingClass;
        WrappedConstantPool result = constantPools.get(constantPool);
        if (result == null) {
            WrappedConstantPool newValue = new WrappedConstantPool(this, constantPool, defaultAccessingClass);
            WrappedConstantPool oldValue = constantPools.putIfAbsent(constantPool, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        return result;
    }

    @Override
    public JavaConstant lookup(JavaConstant constant) {
        if (constant == null || constant.isNull() || constant.getJavaKind().isPrimitive()) {
            return constant;
        }
        return heapScanner.createImageHeapConstant(getHostedValuesProvider().interceptHosted(constant), ObjectScanner.OtherReason.UNKNOWN);
    }

    public boolean isTypeCreated(int typeId) {
        return typesById.length > typeId && typesById[typeId] != null;
    }

    public List<AnalysisType> getTypes() {
        /*
         * The typesById array can contain null values because the ids from the base layers are
         * reserved when they are loaded in a new layer.
         */
        return Arrays.asList(typesById).subList(0, getNextTypeId()).stream().filter(Objects::nonNull).toList();
    }

    public AnalysisType getType(int typeId) {
        AnalysisType result = typesById[typeId];
        assert result.getId() == typeId : result;
        return result;
    }

    public Collection<AnalysisField> getFields() {
        return fields.values();
    }

    public AnalysisField getField(ResolvedJavaField resolvedJavaField) {
        return fields.get(resolvedJavaField);
    }

    public Collection<AnalysisMethod> getMethods() {
        return methods.values();
    }

    public AnalysisMethod getMethod(ResolvedJavaMethod resolvedJavaMethod) {
        return methods.get(resolvedJavaMethod);
    }

    public Map<Constant, Object> getEmbeddedRoots() {
        return embeddedRoots;
    }

    /**
     * Register an embedded root, i.e., a JavaConstant embedded in a Graal graph via a ConstantNode.
     */
    public void registerEmbeddedRoot(JavaConstant root, BytecodePosition position) {
        this.heapScanner.scanEmbeddedRoot(root, position);
        this.embeddedRoots.put(root, position);
    }

    public void registerUnsafeAccessedStaticField(AnalysisField field) {
        unsafeAccessedStaticFields.put(field, true);
    }

    public Set<AnalysisField> getUnsafeAccessedStaticFields() {
        return unsafeAccessedStaticFields.keySet();
    }

    public void registerObjectReplacer(Function<Object, Object> replacer) {
        assert replacer != null;
        objectReplacers = Arrays.copyOf(objectReplacers, objectReplacers.length + 1);
        objectReplacers[objectReplacers.length - 1] = replacer;
    }

    public void registerFeatureSubstitution(SubstitutionProcessor substitution) {
        SubstitutionProcessor[] subs = featureSubstitutions;
        subs = Arrays.copyOf(subs, subs.length + 1);
        subs[subs.length - 1] = substitution;
        featureSubstitutions = subs;
    }

    public SubstitutionProcessor[] getFeatureSubstitutions() {
        return featureSubstitutions;
    }

    public void registerFeatureNativeSubstitution(SubstitutionProcessor substitution) {
        SubstitutionProcessor[] nativeSubs = featureNativeSubstitutions;
        nativeSubs = Arrays.copyOf(nativeSubs, nativeSubs.length + 1);
        nativeSubs[nativeSubs.length - 1] = substitution;
        featureNativeSubstitutions = nativeSubs;
    }

    public SubstitutionProcessor[] getFeatureNativeSubstitutions() {
        return featureNativeSubstitutions;
    }

    /**
     * Invokes all registered object replacers for an object.
     *
     * @param source The source object
     * @return The replaced object or the original source, if the source is not replaced by any
     *         registered replacer.
     */
    public Object replaceObject(Object source) {
        if (source == null) {
            return null;
        }
        Object destination = source;
        for (Function<Object, Object> replacer : objectReplacers) {
            destination = replacer.apply(destination);
        }
        return destination;
    }

    public static Set<AnalysisMethod> reachableMethodOverrides(AnalysisMethod baseMethod) {
        return getMethodImplementations(baseMethod, true);
    }

    private void collectMethodImplementations() {
        for (AnalysisMethod method : methods.values()) {
            Set<AnalysisMethod> implementations = getMethodImplementations(method, false);
            method.implementations = implementations.toArray(new AnalysisMethod[implementations.size()]);
        }
    }

    public static Set<AnalysisMethod> getMethodImplementations(AnalysisMethod method, boolean includeInlinedMethods) {
        Set<AnalysisMethod> implementations = new LinkedHashSet<>();
        if (method.wrapped.canBeStaticallyBound() || method.isConstructor()) {
            if (includeInlinedMethods ? method.isReachable() : method.isImplementationInvoked()) {
                implementations.add(method);
            }
        } else {
            collectMethodImplementations(method, method.getDeclaringClass(), implementations, includeInlinedMethods);
        }
        return implementations;
    }

    private static boolean collectMethodImplementations(AnalysisMethod method, AnalysisType holder, Set<AnalysisMethod> implementations, boolean includeInlinedMethods) {
        boolean holderOrSubtypeInstantiated = holder.isInstantiated();
        for (AnalysisType subClass : holder.getSubTypes()) {
            if (subClass.equals(holder)) {
                /* Subtypes include the holder type itself. The holder is processed below. */
                continue;
            }
            holderOrSubtypeInstantiated |= collectMethodImplementations(method, subClass, implementations, includeInlinedMethods);
        }

        AnalysisMethod resolved = method.resolveInType(holder, holderOrSubtypeInstantiated);
        if (resolved != null && (includeInlinedMethods ? resolved.isReachable() : resolved.isImplementationInvoked())) {
            implementations.add(resolved);
        }

        return holderOrSubtypeInstantiated;
    }

    /**
     * Collect and returns *all reachable* subtypes of this type, not only the immediate subtypes.
     * To access the immediate sub-types use {@link AnalysisType#getSubTypes()}.
     *
     * Since the sub-types are updated continuously as the universe is expanded this method may
     * return different results on each call, until the analysis universe reaches a stable state.
     */
    public static Set<AnalysisType> reachableSubtypes(AnalysisType baseType) {
        Set<AnalysisType> result = baseType.getAllSubtypes();
        result.removeIf(t -> !t.isReachable());
        return result;
    }

    @Override
    public SnippetReflectionProvider getSnippetReflection() {
        return bb.getSnippetReflectionProvider();
    }

    @Override
    public AnalysisType objectType() {
        return objectClass;
    }

    public void onFieldAccessed(AnalysisField field) {
        bb.onFieldAccessed(field);
    }

    public void onTypeInstantiated(AnalysisType type) {
        hostVM.onTypeInstantiated(bb, type);
        bb.onTypeInstantiated(type);
    }

    public void onTypeReachable(AnalysisType type) {
        hostVM.onTypeReachable(bb, type);
        if (bb != null) {
            bb.onTypeReachable(type);
        }
    }

    public void initializeMetaData(AnalysisType type) {
        bb.initializeMetaData(type);
    }

    public SubstitutionProcessor getSubstitutions() {
        return substitutions;
    }

    public AnalysisPolicy analysisPolicy() {
        return analysisPolicy;
    }

    public MetaAccessProvider getOriginalMetaAccess() {
        return originalMetaAccess;
    }

    public void setBigBang(BigBang bb) {
        this.bb = bb;
    }

    public BigBang getBigbang() {
        return bb;
    }

    public void setConcurrentAnalysisAccess(DuringAnalysisAccess access) {
        this.concurrentAnalysisAccess = access;
    }

    public DuringAnalysisAccess getConcurrentAnalysisAccess() {
        return concurrentAnalysisAccess;
    }

    public void setHeapScanner(ImageHeapScanner heapScanner) {
        this.heapScanner = heapScanner;
    }

    public ImageHeapScanner getHeapScanner() {
        return heapScanner;
    }

    public void setImageLayerLoader(ImageLayerLoader imageLayerLoader) {
        this.imageLayerLoader = imageLayerLoader;
    }

    public ImageLayerLoader getImageLayerLoader() {
        return imageLayerLoader;
    }

    public HostedValuesProvider getHostedValuesProvider() {
        return heapScanner.getHostedValuesProvider();
    }

    public void setHeapVerifier(HeapSnapshotVerifier heapVerifier) {
        this.heapVerifier = heapVerifier;
    }

    public HeapSnapshotVerifier getHeapVerifier() {
        return heapVerifier;
    }

    public void notifyReachableType() {
        numReachableTypes.incrementAndGet();
    }

    public int getReachableTypes() {
        return numReachableTypes.get();
    }

    public void setStartTypeId(int startTid) {
        /* No type was created yet, so the array can be overwritten without any concurrency issue */
        typesById = new AnalysisType[startTid];

        setStartId(nextTypeId, startTid, 0);
    }

    public void setStartMethodId(int startMid) {
        setStartId(nextMethodId, startMid, 1);
    }

    public void setStartFieldId(int startFid) {
        setStartId(nextFieldId, startFid, 1);
    }

    private static void setStartId(AtomicInteger nextId, int startFid, int expectedStartValue) {
        if (nextId.compareAndExchange(expectedStartValue, startFid) != expectedStartValue) {
            throw AnalysisError.shouldNotReachHere("An id was assigned before the start id was set.");
        }
    }

    public int computeNextTypeId() {
        return nextTypeId.getAndIncrement();
    }

    public int computeNextMethodId() {
        return nextMethodId.getAndIncrement();
    }

    public int computeNextFieldId() {
        return nextFieldId.getAndIncrement();
    }
}
