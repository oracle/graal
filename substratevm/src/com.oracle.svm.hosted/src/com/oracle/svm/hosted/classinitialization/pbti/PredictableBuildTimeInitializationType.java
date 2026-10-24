/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization.pbti;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.util.InterceptedFieldType;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.InitKind;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Type after {@link com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor substitution}.
 * This is intentionally separate from {@link com.oracle.graal.pointsto.meta.AnalysisType} to
 * prevent type and field lookups from expanding the analysis/hosted universe.
 *
 * <p>Implements {@link InterceptedFieldType shared interface} to access fields via the {@link com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport}</p>
 */
public final class PredictableBuildTimeInitializationType implements ResolvedJavaType, WrappedJavaType, OriginalClassProvider, InterceptedFieldType {

    private final PredictableBuildTimeInitializationLookupLayer lookupLayer;
    private final ResolvedJavaType wrapped;
    private final JavaKind storageKind;

    private final ResolvedJavaType superType;
    private PredictableBuildTimeInitializationField[] instanceObjectFields;
    private PredictableBuildTimeInitializationField[] staticObjectFields;
    private final Class<?> javaClass;
    private final String qualifiedName;

    PredictableBuildTimeInitializationType(PredictableBuildTimeInitializationLookupLayer lookupLayer, ResolvedJavaType wrapped, JavaKind storageKind) {
        this.lookupLayer = lookupLayer;
        this.wrapped = wrapped;

        this.superType = lookupLayer.lookup(wrapped.getSuperclass());
        this.javaClass = OriginalClassProvider.getJavaClass(this);
        this.qualifiedName = wrapped.toJavaName();

        this.storageKind = storageKind;
    }

    @Override
    public ResolvedJavaType getWrapped() {
        return wrapped;
    }

    @Override
    public ResolvedJavaType unwrapTowardsOriginalType() {
        return wrapped;
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public String toJavaName() {
        return qualifiedName;
    }

    @Override
    public String toJavaName(boolean qualified) {
        /* Only qualified is cached, unqualified is not expected. */
        return qualified ? qualifiedName : wrapped.toJavaName(false);
    }

    @Override
    public boolean hasFinalizer() {
        return wrapped.hasFinalizer();
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        return wrapped.hasFinalizableSubclass();
    }

    @Override
    public boolean isInterface() {
        return wrapped.isInterface();
    }

    @Override
    public boolean isInstanceClass() {
        return wrapped.isInstanceClass();
    }

    @Override
    public boolean isPrimitive() {
        return wrapped.isPrimitive();
    }

    @Override
    public boolean isLeaf() {
        return wrapped.isLeaf();
    }

    @Override
    public boolean isEnum() {
        return wrapped.isEnum();
    }

    @Override
    public boolean isRecord() {
        return wrapped.isRecord();
    }

    @Override
    public List<? extends ResolvedJavaRecordComponent> getRecordComponents() {
        return wrapped.getRecordComponents();
    }

    @Override
    public boolean isInitialized() {
        InitKind kind = ClassInitializationSupport.singleton().computedInitKindFor(javaClass);
        /* Check that when isInitialized is called, class is already initialized. */
        assert kind != null;
        return kind == InitKind.BUILD_TIME;
    }

    @Override
    public void initialize() {
        assert ClassInitializationSupport.singleton().maybeInitializeAtBuildTime(this);
    }

    @Override
    public boolean isLinked() {
        return wrapped.isLinked();
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
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return wrapped.isAssignableFrom(OriginalClassProvider.getOriginalType(other));
    }

    @Override
    public boolean isJavaLangObject() {
        return wrapped.isJavaLangObject();
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        return wrapped.isInstance(obj);
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return superType;
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return lookupLayer.lookup(wrapped.getInterfaces());
    }

    @Override
    public ResolvedJavaType[] getDeclaredTypes() {
        return lookupLayer.lookup(wrapped.getDeclaredTypes());
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        return lookupLayer.lookup(wrapped.getSingleImplementor());
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return lookupLayer.lookup(wrapped.findLeastCommonAncestor(OriginalClassProvider.getOriginalType(otherType)));
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        AssumptionResult<ResolvedJavaType> wrappedResult = wrapped.findLeafConcreteSubtype();
        if (wrappedResult != null && wrappedResult.isAssumptionFree()) {
            return new AssumptionResult<>(lookupLayer.lookup(wrappedResult.getResult()));
        }
        return null;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return lookupLayer.lookup(wrapped.getComponentType());
    }

    @Override
    public ResolvedJavaType getElementalType() {
        return lookupLayer.lookup(wrapped.getElementalType());
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        return lookupLayer.lookup(wrapped.getArrayClass());
    }

    @Override
    public boolean isHidden() {
        return wrapped.isHidden();
    }

    @Override
    public List<? extends JavaType> getPermittedSubclasses() {
        return lookupLayer.lookup(wrapped.getPermittedSubclasses());
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return wrapped.resolveMethod(method, callerType);
    }

    @Override
    public ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return wrapped.resolveConcreteMethod(method, callerType);
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return wrapped.findUniqueConcreteMethod(method);
    }

    @Override
    public PredictableBuildTimeInitializationField[] getInstanceFields(boolean includeSuperclasses) {
        assert includeSuperclasses; /* There is no need to filter these out */
        if (instanceObjectFields == null) {
            instanceObjectFields = convertFields(wrapped.getInstanceFields(true));
        }
        return instanceObjectFields;
    }

    @Override
    public PredictableBuildTimeInitializationField[] getStaticFields() {
        if (staticObjectFields == null) {
            staticObjectFields = convertFields(wrapped.getStaticFields());
        }
        return staticObjectFields;
    }

    /**
     * Similar implementation to {@code com.oracle.graal.pointsto.meta.AnalysisType#convertFields}.
     */
    private PredictableBuildTimeInitializationField[] convertFields(ResolvedJavaField[] rawFields) {
        PredictableBuildTimeInitializationField[] result = new PredictableBuildTimeInitializationField[rawFields.length];

        int index = 0;
        for (ResolvedJavaField rawField : rawFields) {
            if (!rawField.isInternal() && lookupLayer.hostVM().platformSupported(rawField)) {
                try {
                    PredictableBuildTimeInitializationField field = lookupLayer.lookup(rawField);
                    if (field != null && field.getStorageKind().isObject()) {
                        /* PBTI only cares about objects */
                        result[index++] = field;
                    }
                } catch (UnsupportedFeatureException ex) {
                    // Ignore deleted fields and fields of deleted types.
                }
            }
        }
        return index == result.length ? result : Arrays.copyOf(result, index);
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        ResolvedJavaField field = wrapped.findInstanceFieldWithOffset(offset, expectedKind);
        return lookupLayer.lookup(field);
    }

    @Override
    public String getSourceFileName() {
        return wrapped.getSourceFileName();
    }

    @Override
    public boolean isLocal() {
        return wrapped.isLocal();
    }

    @Override
    public boolean isMember() {
        return wrapped.isMember();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        return lookupLayer.lookup(wrapped.getEnclosingType());
    }

    @Override
    public ResolvedJavaMethod getEnclosingMethod() {
        return wrapped.getEnclosingMethod();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return wrapped.getDeclaredConstructors();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        return wrapped.getDeclaredConstructors(forceLink);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return wrapped.getDeclaredMethods();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        return wrapped.getDeclaredMethods(forceLink);
    }

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        return wrapped.getAllMethods(forceLink);
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        return wrapped.getClassInitializer();
    }

    @Override
    public ResolvedJavaMethod findMethod(String name, Signature signature) {
        return wrapped.findMethod(name, signature);
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return wrapped.isCloneableWithAllocation();
    }

    @Override
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        return lookupLayer.lookup(wrapped.lookupType(unresolvedJavaType, resolve));
    }

    @Override
    public ResolvedJavaField resolveField(UnresolvedJavaField unresolvedJavaField, ResolvedJavaType accessingClass) {
        return wrapped.resolveField(unresolvedJavaField, accessingClass);
    }

    @Override
    public boolean isArray() {
        return wrapped.isArray();
    }

    @Override
    public JavaKind getJavaKind() {
        return wrapped.getJavaKind();
    }

    @Override
    public JavaKind getStorageKind() {
        return storageKind;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public boolean isSynchronized() {
        return wrapped.isSynchronized();
    }

    @Override
    public boolean isStatic() {
        return wrapped.isStatic();
    }

    @Override
    public boolean isFinalFlagSet() {
        return wrapped.isFinalFlagSet();
    }

    @Override
    public boolean isPublic() {
        return wrapped.isPublic();
    }

    @Override
    public boolean isPackagePrivate() {
        return wrapped.isPackagePrivate();
    }

    @Override
    public boolean isPrivate() {
        return wrapped.isPrivate();
    }

    @Override
    public boolean isProtected() {
        return wrapped.isProtected();
    }

    @Override
    public boolean isTransient() {
        return wrapped.isTransient();
    }

    @Override
    public boolean isStrict() {
        return wrapped.isStrict();
    }

    @Override
    public boolean isVolatile() {
        return wrapped.isVolatile();
    }

    @Override
    public boolean isNative() {
        return wrapped.isNative();
    }

    @Override
    public boolean isAbstract() {
        return wrapped.isAbstract();
    }

    @Override
    public boolean isConcrete() {
        return wrapped.isConcrete();
    }

    @Override
    public String toString() {
        return "PBTIType<" + wrapped.toJavaName(true) + ">";
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PredictableBuildTimeInitializationType pbtiType) {
            return pbtiType.wrapped == wrapped;
        }
        return false;
    }
}
