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

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.util.GuardedAnnotationAccess;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
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
    private final ConcurrentMap<Signature, WrappedSignature> signatures = new ConcurrentHashMap<>(ESTIMATED_METHODS_PER_TYPE * ESTIMATED_NUMBER_OF_TYPES);
    private final ConcurrentMap<ConstantPool, WrappedConstantPool> constantPools = new ConcurrentHashMap<>(ESTIMATED_NUMBER_OF_TYPES);
    private final ConcurrentHashMap<JavaConstant, BytecodePosition> embeddedRoots = new ConcurrentHashMap<>(ESTIMATED_EMBEDDED_ROOTS);
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
    private final SnippetReflectionProvider originalSnippetReflection;
    private final SnippetReflectionProvider snippetReflection;

    private AnalysisType objectClass;
    private final JavaKind wordKind;
    private final Platform platform;
    private AnalysisPolicy analysisPolicy;

    public JavaKind getWordKind() {
        return wordKind;
    }

    @SuppressWarnings("unchecked")
    public AnalysisUniverse(HostVM hostVM, JavaKind wordKind, Platform platform, AnalysisPolicy analysisPolicy, SubstitutionProcessor substitutions, MetaAccessProvider originalMetaAccess,
                    SnippetReflectionProvider originalSnippetReflection,
                    SnippetReflectionProvider snippetReflection) {
        this.hostVM = hostVM;
        this.wordKind = wordKind;
        this.platform = platform;
        this.analysisPolicy = analysisPolicy;
        this.substitutions = substitutions;
        this.originalMetaAccess = originalMetaAccess;
        this.originalSnippetReflection = originalSnippetReflection;
        this.snippetReflection = snippetReflection;

        sealed = false;
        objectReplacers = (Function<Object, Object>[]) new Function<?, ?>[0];
        featureSubstitutions = new SubstitutionProcessor[0];
        featureNativeSubstitutions = new SubstitutionProcessor[0];
    }

    @Override
    public HostVM hostVM() {
        return hostVM;
    }

    public int getNextTypeId() {
        return nextTypeId.get();
    }

    public int getNextMethodId() {
        return nextMethodId.get();
    }

    public void seal() {
        sealed = true;
    }

    public boolean sealed() {
        return sealed;
    }

    public void setAnalysisDataValid(BigBang bb, boolean dataIsValid) {
        if (dataIsValid) {
            buildSubTypes();
            collectMethodImplementations(bb);
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
        assert typesById[result.getId()].equals(result);
        return result;
    }

    @SuppressFBWarnings(value = {"ES_COMPARING_STRINGS_WITH_EQ"}, justification = "Bug in findbugs")
    private AnalysisType createType(ResolvedJavaType type) {
        if (!platformSupported(type)) {
            throw new UnsupportedFeatureException("type is not available in this platform: " + type.toJavaName(true));
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
            JavaKind storageKind = getStorageKind(type, originalMetaAccess);
            AnalysisType newValue = new AnalysisType(this, type, storageKind, objectClass);
            hostVM.registerType(newValue);

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

                if (newValue.isJavaLangObject()) {
                    assert objectClass == null;
                    objectClass = newValue;
                }
            }
            /*
             * Now that our type is correctly registered in the id-to-type array, make it accessible
             * by other threads.
             */
            Object oldValue = types.put(type, newValue);
            assert oldValue == claim;
            claim = null;

            ResolvedJavaType enclosingType = null;
            try {
                enclosingType = newValue.getWrapped().getEnclosingType();
            } catch (NoClassDefFoundError e) {
                /* Ignore NoClassDefFoundError thrown by enclosing type resolution. */
            }
            /* If not being currently constructed by this thread. */
            if (enclosingType != null && !types.containsKey(enclosingType)) {
                /* Make sure that the enclosing type is also in the universe. */
                newValue.getEnclosingType();
            }

            return newValue;

        } finally {
            /* In case of any exception, remove the claim so that we do not deadlock. */
            if (claim != null) {
                types.remove(type, claim);
            }
        }
    }

    public JavaKind getStorageKind(ResolvedJavaType type, MetaAccessProvider metaAccess) {
        if (metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(substitutions.resolve(type))) {
            return wordKind;
        }
        return type.getJavaKind();
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
        assert !(rawField instanceof AnalysisField);

        ResolvedJavaField field = (ResolvedJavaField) rawField;

        if (!sealed) {
            /*
             * Lookup the field declaring class eagerly to trigger computation of automatic
             * substitutions. There might be an automatic substitution for the current field and we
             * want to register it before the analysis field is created.
             */
            lookup(field.getDeclaringClass());
        }

        field = substitutions.lookup(field);
        AnalysisField result = fields.get(field);
        if (result == null) {
            result = createField(field);
        }
        return result;
    }

    private AnalysisField createField(ResolvedJavaField field) {
        if (!platformSupported(field)) {
            throw new UnsupportedFeatureException("field is not available in this platform: " + field.format("%H.%n"));
        }
        if (sealed) {
            return null;
        }
        AnalysisField newValue = new AnalysisField(this, field);
        AnalysisField oldValue = fields.putIfAbsent(field, newValue);
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
        throw new UnsupportedFeatureException("Unresolved method found. Probably there are some compilation or classpath problems. " + method.format("%H.%n(%p)"));
    }

    @Override
    public JavaMethod lookupAllowUnresolved(JavaMethod rawMethod) {
        if (rawMethod == null) {
            return null;
        }
        if (!(rawMethod instanceof ResolvedJavaMethod)) {
            return rawMethod;
        }
        assert !(rawMethod instanceof AnalysisMethod);

        ResolvedJavaMethod method = (ResolvedJavaMethod) rawMethod;
        method = substitutions.lookup(method);
        AnalysisMethod result = methods.get(method);
        if (result == null) {
            result = createMethod(method);
        }
        return result;
    }

    private AnalysisMethod createMethod(ResolvedJavaMethod method) {
        if (!platformSupported(method)) {
            throw new UnsupportedFeatureException("Method " + method.format("%H.%n(%p)" + " is not available in this platform."));
        }
        if (sealed) {
            return null;
        }
        AnalysisMethod newValue = new AnalysisMethod(this, method);
        AnalysisMethod oldValue = methods.putIfAbsent(method, newValue);
        return oldValue != null ? oldValue : newValue;
    }

    public AnalysisMethod[] lookup(JavaMethod[] inputs) {
        List<AnalysisMethod> result = new ArrayList<>(inputs.length);
        for (JavaMethod method : inputs) {
            if (platformSupported((ResolvedJavaMethod) method)) {
                AnalysisMethod aMethod = lookup(method);
                if (aMethod != null) {
                    result.add(aMethod);
                }
            }
        }
        return result.toArray(new AnalysisMethod[result.size()]);
    }

    @Override
    public WrappedSignature lookup(Signature signature, WrappedJavaType defaultAccessingClass) {
        assert !(signature instanceof WrappedSignature);
        WrappedSignature result = signatures.get(signature);
        if (result == null) {
            WrappedSignature newValue = new WrappedSignature(this, signature, defaultAccessingClass);
            WrappedSignature oldValue = signatures.putIfAbsent(signature, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        return result;
    }

    @Override
    public WrappedConstantPool lookup(ConstantPool constantPool, WrappedJavaType defaultAccessingClass) {
        assert !(constantPool instanceof WrappedConstantPool);
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
        if (constant == null) {
            return null;
        } else if (constant.getJavaKind().isObject() && !constant.isNull()) {
            return snippetReflection.forObject(originalSnippetReflection.asObject(Object.class, constant));
        } else {
            return constant;
        }
    }

    public JavaConstant toHosted(JavaConstant constant) {
        if (constant == null) {
            return null;
        } else if (constant.getJavaKind().isObject() && !constant.isNull()) {
            return originalSnippetReflection.forObject(getSnippetReflection().asObject(Object.class, constant));
        } else {
            return constant;
        }
    }

    public List<AnalysisType> getTypes() {
        return Collections.unmodifiableList(Arrays.asList(typesById).subList(0, getNextTypeId()));
    }

    public AnalysisType getType(int typeId) {
        AnalysisType result = typesById[typeId];
        assert result.getId() == typeId;
        return result;
    }

    public Collection<AnalysisField> getFields() {
        return fields.values();
    }

    public Collection<AnalysisMethod> getMethods() {
        return methods.values();
    }

    public Map<JavaConstant, BytecodePosition> getEmbeddedRoots() {
        return embeddedRoots;
    }

    /**
     * Register an embedded root, i.e., a JavaConstant embedded in a Graal graph via a ConstantNode.
     */
    public void registerEmbeddedRoot(JavaConstant root, BytecodePosition position) {
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

    public void buildSubTypes() {
        Map<AnalysisType, Set<AnalysisType>> allSubTypes = new HashMap<>();
        AnalysisType objectType = null;
        for (AnalysisType type : getTypes()) {
            allSubTypes.put(type, new HashSet<>());
            if (type.isInstanceClass() && type.getSuperclass() == null) {
                objectType = type;
            }
        }
        assert objectType != null;

        for (AnalysisType type : getTypes()) {
            if (type.getSuperclass() != null) {
                allSubTypes.get(type.getSuperclass()).add(type);
            }
            if (type.isInterface() && type.getInterfaces().length == 0) {
                allSubTypes.get(objectType).add(type);
            }
            for (AnalysisType interf : type.getInterfaces()) {
                allSubTypes.get(interf).add(type);
            }
        }

        for (AnalysisType type : getTypes()) {
            Set<AnalysisType> subTypesSet = allSubTypes.get(type);
            type.subTypes = subTypesSet.toArray(new AnalysisType[subTypesSet.size()]);
        }
    }

    private void collectMethodImplementations(BigBang bb) {
        for (AnalysisMethod method : methods.values()) {

            Set<AnalysisMethod> implementations = getMethodImplementations(bb, method);
            method.implementations = implementations.toArray(new AnalysisMethod[implementations.size()]);
        }
    }

    public Set<AnalysisMethod> getMethodImplementations(BigBang bb, AnalysisMethod method) {
        Set<AnalysisMethod> implementations = new LinkedHashSet<>();
        if (method.wrapped.canBeStaticallyBound() || method.isConstructor()) {
            if (method.isImplementationInvoked()) {
                implementations.add(method);
            }
        } else {
            try {
                collectMethodImplementations(method, method.getDeclaringClass(), implementations);
            } catch (UnsupportedFeatureException ex) {
                String message = String.format("Error while collecting implementations of %s : %s%n", method.format("%H.%n(%p)"), ex.getMessage());
                bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, message, null, ex.getCause());
            }
        }
        return implementations;
    }

    private boolean collectMethodImplementations(AnalysisMethod method, AnalysisType holder, Set<AnalysisMethod> implementations) {
        boolean holderOrSubtypeInstantiated = holder.isInstantiated();
        for (AnalysisType subClass : holder.subTypes) {
            holderOrSubtypeInstantiated |= collectMethodImplementations(method, subClass, implementations);
        }

        /*
         * If the holder and all subtypes are not instantiated, then we do not need to resolve the
         * method. The method cannot be marked as invoked.
         */
        if (holderOrSubtypeInstantiated || method.isIntrinsicMethod()) {
            AnalysisMethod aResolved = holder.resolveConcreteMethod(method, null);
            if (aResolved != null) {
                /*
                 * aResolved == null means that the method in the base class was called, but never
                 * with this holder.
                 */
                if (aResolved.isImplementationInvoked()) {
                    implementations.add(aResolved);
                }
            }
        }
        return holderOrSubtypeInstantiated;
    }

    public Set<AnalysisType> getSubtypes(AnalysisType baseType) {
        LinkedHashSet<AnalysisType> result = new LinkedHashSet<>();
        result.add(baseType);
        collectSubtypes(baseType, result);
        return result;
    }

    private void collectSubtypes(AnalysisType baseType, Set<AnalysisType> result) {
        for (AnalysisType subType : baseType.subTypes) {
            if (result.contains(subType)) {
                continue;
            }
            result.add(subType);
            collectSubtypes(subType, result);
        }
    }

    @Override
    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    public SnippetReflectionProvider getOriginalSnippetReflection() {
        return originalSnippetReflection;
    }

    @Override
    public ResolvedJavaMethod resolveSubstitution(ResolvedJavaMethod method) {
        return substitutions.resolve(method);
    }

    @Override
    public AnalysisType objectType() {
        return objectClass;
    }

    public SubstitutionProcessor getSubstitutions() {
        return substitutions;
    }

    public AnalysisPolicy analysisPolicy() {
        return analysisPolicy;
    }

    public boolean platformSupported(AnnotatedElement element) {
        if (element instanceof ResolvedJavaType) {
            Package p = OriginalClassProvider.getJavaClass(getOriginalSnippetReflection(), (ResolvedJavaType) element).getPackage();
            if (p != null && !platformSupported(p)) {
                return false;
            }
        }

        Platforms platformsAnnotation = GuardedAnnotationAccess.getAnnotation(element, Platforms.class);
        if (platform == null || platformsAnnotation == null) {
            return true;
        }
        for (Class<? extends Platform> platformGroup : platformsAnnotation.value()) {
            if (platformGroup.isInstance(platform)) {
                return true;
            }
        }
        return false;
    }

    public MetaAccessProvider getOriginalMetaAccess() {
        return originalMetaAccess;
    }

    public Platform getPlatform() {
        return platform;
    }
}
