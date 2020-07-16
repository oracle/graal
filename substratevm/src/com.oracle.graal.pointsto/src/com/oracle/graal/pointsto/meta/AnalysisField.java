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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.api.DefaultUnsafePartition;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.api.UnsafePartitionKind;
import com.oracle.graal.pointsto.flow.FieldSinkTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class AnalysisField implements ResolvedJavaField, OriginalFieldProvider {

    private final int id;

    public final ResolvedJavaField wrapped;

    /** Field type flow for the static fields. */
    private FieldTypeFlow staticFieldFlow;

    /** Initial field type flow, i.e., as specified by the analysis client. */
    private FieldTypeFlow initialInstanceFieldFlow;

    /**
     * Field type flow that reflects all the types flowing in this field on its declaring type and
     * all the sub-types.
     */
    private FieldSinkTypeFlow instanceFieldFlow;

    private boolean isAccessed;
    private boolean isRead;
    private boolean isWritten;
    private boolean isUsedInComparison;
    private AtomicBoolean isUnsafeAccessed;
    private AtomicBoolean unsafeFrozenTypeState;

    /**
     * By default all instance fields are null before are initialized. It can be specified by
     * {@link HostVM} that certain fields will not be null.
     */
    private boolean canBeNull;

    private ConcurrentMap<MethodTypeFlow, Boolean> readBy;
    private ConcurrentMap<MethodTypeFlow, Boolean> writtenBy;

    protected TypeState instanceFieldTypeState;

    /** Field's position in the list of declaring type's fields, including inherited fields. */
    private int position;

    private final AnalysisType declaringClass;
    private final AnalysisType fieldType;

    public AnalysisField(AnalysisUniverse universe, ResolvedJavaField wrappedField) {
        assert !wrappedField.isInternal();

        this.position = -1;
        this.isUnsafeAccessed = new AtomicBoolean();
        this.unsafeFrozenTypeState = new AtomicBoolean();

        this.wrapped = wrappedField;
        this.id = universe.nextFieldId.getAndIncrement();

        readBy = PointstoOptions.TrackAccessChain.getValue(universe.hostVM().options()) ? new ConcurrentHashMap<>() : null;
        writtenBy = new ConcurrentHashMap<>();

        declaringClass = universe.lookup(wrappedField.getDeclaringClass());
        fieldType = getDeclaredType(universe, wrappedField);

        isUsedInComparison = false;

        if (this.isStatic()) {
            this.canBeNull = false;
            this.staticFieldFlow = new FieldTypeFlow(this, getType());
            this.initialInstanceFieldFlow = null;
        } else {
            this.canBeNull = true;
            this.instanceFieldFlow = new FieldSinkTypeFlow(this, getType());
            this.initialInstanceFieldFlow = new FieldTypeFlow(this, getType());
        }
    }

    private static AnalysisType getDeclaredType(AnalysisUniverse universe, ResolvedJavaField wrappedField) {
        ResolvedJavaType resolvedType;
        try {
            resolvedType = wrappedField.getType().resolve(universe.substitutions.resolve(wrappedField.getDeclaringClass()));
        } catch (NoClassDefFoundError e) {
            /*
             * Type resolution fails if the declared type is missing. Just erase the type by
             * returning the Object type.
             */
            return universe.objectType();
        }

        return universe.lookup(resolvedType);
    }

    public void copyAccessInfos(AnalysisField other) {
        this.isAccessed = other.isAccessed;
        this.isUnsafeAccessed = other.isUnsafeAccessed;
        this.canBeNull = other.canBeNull;
        this.isWritten = other.isWritten;
        this.isRead = other.isRead;
    }

    public int getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public JavaKind getStorageKind() {
        return fieldType.getStorageKind();

    }

    /**
     * Returns all possible types that this field can have. The result is not context sensitive,
     * i.e., it is a union of all types found in all contexts.
     */
    public TypeState getTypeState() {
        if (getType().getStorageKind() != JavaKind.Object) {
            return null;
        } else if (isStatic()) {
            return staticFieldFlow.getState();
        } else {
            return getInstanceFieldTypeState();
        }
    }

    public TypeState getInstanceFieldTypeState() {
        return instanceFieldFlow.getState();
    }

    // @formatter:off
//    public TypeState getInstanceFieldTypeState() {
//        if (instanceFieldTypeState == null) {
//
//            /* Collect the types of all instance field flows. */
//            HashSet<AnalysisType> fieldTypes = new HashSet<>();
//            boolean fieldCanBeNull = false;
//
//            fieldCanBeNull = collectInstanceFieldTypes(getDeclaringClass(), fieldTypes);
//            instanceFieldTypeState = TypeState.forExactTypes(null, new ArrayList<>(fieldTypes), fieldCanBeNull);
//
//        }
//        return instanceFieldTypeState;
//    }

//    private boolean collectInstanceFieldTypes(AnalysisType type, HashSet<AnalysisType> fieldTypes) {
//        boolean fieldCanBeNull = false;
//
//        TypeFlow<?> fieldFlow = type.getAbstractObject().getInstanceFieldFlow(null, this, false);
//        if (fieldFlow != null) {
//            TypeState mergedState = fieldFlow.getState();
//            mergedState.typesIterator().forEachRemaining(fieldTypes::add);
//            fieldCanBeNull |= mergedState.canBeNull();
//        }
//
//        for (AnalysisType subClass : type.subTypes) {
//            fieldCanBeNull |= collectInstanceFieldTypes(subClass, fieldTypes);
//        }
//        return fieldCanBeNull;
//    }
    //@formatter:on

    public FieldTypeFlow getInitialInstanceFieldFlow() {
        return initialInstanceFieldFlow;
    }

    public FieldTypeFlow getStaticFieldFlow() {
        assert Modifier.isStatic(this.getModifiers());

        return staticFieldFlow;
    }

    public FieldSinkTypeFlow getInstanceFieldFlow() {
        assert !Modifier.isStatic(this.getModifiers());

        return instanceFieldFlow;
    }

    public void cleanupAfterAnalysis() {
        staticFieldFlow = null;
        instanceFieldFlow = null;
        initialInstanceFieldFlow = null;
        readBy = null;
        writtenBy = null;
        instanceFieldTypeState = null;
    }

    public void registerAsAccessed() {
        isAccessed = true;
    }

    public void registerAsRead(MethodTypeFlow method) {
        isRead = true;
        if (readBy != null && method != null) {
            readBy.put(method, Boolean.TRUE);
        }
    }

    /**
     * Registers that the field is written.
     *
     * @param method The method where the field is written or null if the method is not known, e.g.
     *            for an unsafe accessed field.
     */
    public void registerAsWritten(MethodTypeFlow method) {
        isWritten = true;
        if (writtenBy != null && method != null) {
            writtenBy.put(method, Boolean.TRUE);
        }
    }

    public void registerAsUnsafeAccessed(AnalysisUniverse universe) {
        registerAsUnsafeAccessed(universe, DefaultUnsafePartition.get());
    }

    public void registerAsUnsafeAccessed(AnalysisUniverse universe, UnsafePartitionKind partitionKind) {

        /*
         * A field can potentially be registered as unsafe accessed multiple times. This is
         * especially true for the Graal nodes because FieldsOffsetsFeature.registerFields iterates
         * over all the unsafe accessed fields of a node, including those in the super types.
         *
         * To avoid using a hash set to keep track of the accessed fields in the analysis type we
         * only register fields as unsafe accessed with their declaring type once.
         */

        if (!isUnsafeAccessed.getAndSet(true)) {
            /*
             * The atomic boolean ensures that the field is registered as unsafe accessed with its
             * declaring class only once. However, at the end of this call the registration might
             * still be in progress. The first thread that calls this methods enters the if and
             * starts the registration, the next threads return right away, while the registration
             * might still be in progress.
             */

            registerAsWritten(null);

            if (isStatic()) {
                /* Register the static field as unsafe accessed with the analysis universe. */
                universe.registerUnsafeAccessedStaticField(this);
            } else {
                /* Register the instance field as unsafe accessed on the declaring type. */
                AnalysisType declaringType = getDeclaringClass();
                declaringType.registerUnsafeAccessedField(this, partitionKind);
            }
        }

    }

    public boolean isUnsafeAccessed() {
        return isUnsafeAccessed.get();
    }

    public void setUnsafeFrozenTypeState(boolean value) {
        unsafeFrozenTypeState.getAndSet(value);
    }

    public boolean hasUnsafeFrozenTypeState() {
        return unsafeFrozenTypeState.get();
    }

    public Set<MethodTypeFlow> getReadBy() {
        return readBy.keySet();
    }

    /**
     * Returns all methods where the field is written. It does not include the methods where the
     * field is written with unsafe access.
     */
    public Set<MethodTypeFlow> getWrittenBy() {
        return writtenBy.keySet();
    }

    public boolean isAccessed() {
        // If a field is never read, writes to it are useless as well and we can
        // eliminate the field. Unless it is volatile, where the write is a memory barrier
        // and therefore has side effects.
        return isAccessed || isRead || (isWritten && Modifier.isVolatile(getModifiers()));
    }

    public boolean isWritten() {
        return isAccessed || isWritten;
    }

    public void setCanBeNull(boolean canBeNull) {
        this.canBeNull = canBeNull;
    }

    public boolean canBeNull() {
        return canBeNull;
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    public void setPosition(int newPosition) {
        assert position == -1 || position == newPosition;
        this.position = newPosition;
    }

    public int getPosition() {
        assert position != -1 : this;
        return position;
    }

    @Override
    public AnalysisType getType() {
        return fieldType;
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public int getOffset() {
        return wrapped.getOffset();
    }

    @Override
    public AnalysisType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(this.getModifiers());
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
    public String toString() {
        return "AnalysisField<" + format("%h.%n") + " accessed: " + isAccessed + " reads: " + isRead + " written: " + isWritten + ">";
    }

    public void markAsUsedInComparison() {
        isUsedInComparison = true;
    }

    public boolean isUsedInComparison() {
        return isUsedInComparison;
    }

    @Override
    public Field getJavaField() {
        return OriginalFieldProvider.getJavaField(getDeclaringClass().universe.getOriginalSnippetReflection(), wrapped);
    }
}
