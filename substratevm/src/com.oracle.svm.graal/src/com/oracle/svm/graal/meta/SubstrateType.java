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
package com.oracle.svm.graal.meta;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateType implements SharedType {
    private final JavaKind kind;
    private final DynamicHub hub;

    /**
     * All instance fields (including the fields of superclasses) for this type.
     *
     * If it is not known if the type has an instance field (because the type metadata was created
     * at image runtime), it is null.
     */
    @UnknownObjectField(canBeNull = true)//
    SubstrateField[] rawAllInstanceFields;

    @UnknownObjectField(canBeNull = true)//
    protected DynamicHub uniqueConcreteImplementation;

    public SubstrateType(JavaKind kind, DynamicHub hub) {
        this.kind = kind;
        this.hub = hub;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setRawAllInstanceFields(SubstrateField[] allInstanceFields) {
        if (allInstanceFields.length == 0) {
            /*
             * We cannot use null as the marker value, because null means
             * "no field information available" for instances created at run time.
             */
            this.rawAllInstanceFields = SubstrateField.EMPTY_ARRAY;
        } else {
            this.rawAllInstanceFields = allInstanceFields;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateField[] getRawAllInstanceFields() {
        return rawAllInstanceFields;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setTypeCheckData(DynamicHub uniqueConcreteImplementation) {
        this.uniqueConcreteImplementation = uniqueConcreteImplementation;
    }

    /**
     * The kind of the field in memory (in contrast to {@link #getJavaKind()}, which is the kind of
     * the field on the Java type system level). For example {@link WordBase word types} have a
     * {@link #getJavaKind} of {@link JavaKind#Object}, but a primitive {@link #getStorageKind}.
     */
    @Override
    public final JavaKind getStorageKind() {
        if (WordBase.class.isAssignableFrom(DynamicHub.toClass(hub))) {
            return ConfigurationValues.getWordKind();
        } else {
            return getJavaKind();
        }
    }

    @Override
    public DynamicHub getHub() {
        return hub;
    }

    @Override
    public String getName() {
        return MetaUtil.toInternalName(hub.getName());
    }

    @Override
    public JavaKind getJavaKind() {
        return kind;
        // return Kind.fromJavaClass(hub.asClass());
    }

    @Override
    public int getTypeID() {
        return hub.getTypeID();
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        return new AssumptionResult<>(false);
    }

    @Override
    public boolean isInterface() {
        return hub.isInterface();
    }

    @Override
    public boolean isInstanceClass() {
        return hub.isInstanceClass();
    }

    @Override
    public boolean isArray() {
        return DynamicHub.toClass(hub).isArray();
    }

    @Override
    public boolean isPrimitive() {
        return hub.isPrimitive();
    }

    @Override
    public boolean isEnum() {
        throw VMError.unimplemented("Enum support not implemented");
    }

    @Override
    public int getModifiers() {
        return hub.getModifiers();
    }

    @Override
    public boolean isInitialized() {
        return hub.isInitialized();
    }

    @Override
    public void initialize() {
        hub.ensureInitialized();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return DynamicHub.toClass(hub).isAssignableFrom(DynamicHub.toClass(((SubstrateType) other).hub));
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        if (obj.getJavaKind() == JavaKind.Object && !obj.isNull()) {
            DynamicHub objHub = KnownIntrinsics.readHub(SubstrateObjectConstant.asObject(obj));
            return DynamicHub.toClass(hub).isAssignableFrom(DynamicHub.toClass(objHub));
        }
        return false;
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        ResolvedJavaType result = getSingleImplementor();
        if (result == null) {
            return null;
        } else {
            return new AssumptionResult<>(result);
        }
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        if (uniqueConcreteImplementation == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaType(DynamicHub.toClass(uniqueConcreteImplementation));
    }

    @Override
    public SubstrateType getSuperclass() {
        DynamicHub superHub = hub.getSuperHub();
        if (superHub == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(superHub);
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        DynamicHub[] hubs = hub.getInterfaces();
        SubstrateType[] result = new SubstrateType[hubs.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(hubs[i]);
        }
        return result;
    }

    private SubstrateType getSuperType() {
        if (isArray() || isInterface()) {
            return SubstrateMetaAccess.singleton().lookupJavaType(Object.class);
        } else {
            return getSuperclass();
        }
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        if (this.isPrimitive() || otherType.isPrimitive()) {
            return null;
        } else {
            SubstrateType t1 = this;
            SubstrateType t2 = (SubstrateType) otherType;
            while (true) {
                if (t1.isAssignableFrom(t2)) {
                    return t1;
                }
                if (t2.isAssignableFrom(t1)) {
                    return t2;
                }
                t1 = t1.getSuperType();
                t2 = t2.getSuperType();
            }
        }
    }

    @Override
    public boolean isJavaLangObject() {
        return DynamicHub.toClass(hub) == Object.class;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        if (hub.getComponentHub() == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(hub.getComponentHub());
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        if (hub.getArrayHub() == null) {
            /*
             * Returning null is not ideal because it can lead to a subsequent NullPointerException.
             * But it matches the behavior of HostedType, which also returns null.
             */
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(hub.getArrayHub());
    }

    @Override
    public SubstrateField[] getInstanceFields(boolean includeSuperclasses) {
        if (rawAllInstanceFields == null) {
            /*
             * The type was created at run time from the Class, so we do not have field information.
             * If we need the fields for a type, the type has to be created during image generation.
             */
            throw VMError.shouldNotReachHere("No instance fields for " + hub.getName() + " available");
        }

        SubstrateType superclass = getSuperclass();
        if (includeSuperclasses || superclass == null) {
            return rawAllInstanceFields;

        } else {
            int totalCount = getInstanceFieldCount();
            int superCount = superclass.getInstanceFieldCount();
            assert totalCount >= superCount;

            if (totalCount == superCount) {
                return SubstrateField.EMPTY_ARRAY;
            } else if (superCount == 0) {
                return rawAllInstanceFields;
            } else {
                assert Arrays.equals(superclass.getInstanceFields(true),
                                Arrays.copyOf(rawAllInstanceFields, superCount)) : "Superclass fields must be the first elements of the fields defined in this class";
                return Arrays.copyOfRange(rawAllInstanceFields, superCount, totalCount);
            }
        }
    }

    public int getInstanceFieldCount() {
        return rawAllInstanceFields.length;
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Annotation[] getAnnotations() {
        return DynamicHub.toClass(getHub()).getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return DynamicHub.toClass(getHub()).getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return DynamicHub.toClass(getHub()).getAnnotation(annotationClass);
    }

    @Override
    public SubstrateField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        assert offset >= 0;

        if (rawAllInstanceFields == null) {
            /*
             * The type was created at run time from the Class, so we do not have field information.
             * The type's superclass however might not be created at run time thus having fields we
             * need to look into.
             */
            if (getSuperclass() != null) {
                return getSuperclass().findInstanceFieldWithOffset(offset, expectedKind);
            }

        } else {
            for (SubstrateField field : rawAllInstanceFields) {
                if (fieldMatches(field, offset)) {
                    return field;
                }
            }
        }

        /* No match found. */
        return null;
    }

    /**
     * Determines whether a field of this type matches the expected offset.
     *
     * NOTE: We do not check anything except the offset as Truffle stores type void into types int
     * and object as well as type long into integers.
     */
    private static boolean fieldMatches(SubstrateField field, long offset) {
        return field.getLocation() == offset;
    }

    @Override
    public String getSourceFileName() {
        return hub.getSourceFileName();
    }

    @Override
    public boolean isLocal() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean isMember() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        Class<?> enclosingClass = DynamicHub.toClass(hub).getEnclosingClass();
        if (enclosingClass == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaType(enclosingClass);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return getDeclaredConstructors(true);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean isLinked() {
        return hub.isLinked();
    }

    @Override
    public void link() {
        if (!isLinked()) {
            throw new LinkageError(String.format("Cannot link new type at run time: %s", this));
        }
    }

    @Override
    public boolean hasDefaultMethods() {
        return hub.hasDefaultMethods();
    }

    @Override
    public boolean declaresDefaultMethods() {
        return hub.declaresDefaultMethods();
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return SubstrateMetaAccess.singleton().lookupJavaType(Cloneable.class).isAssignableFrom(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResolvedJavaType getHostClass() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int hashCode() {
        return hub.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof SubstrateType && ((SubstrateType) obj).hub == hub);
    }

    @Override
    public String toString() {
        return "SubstrateType<" + toJavaName(true) + ">";
    }
}
