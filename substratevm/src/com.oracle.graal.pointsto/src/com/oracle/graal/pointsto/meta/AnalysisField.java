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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.api.DefaultUnsafePartition;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AtomicUtils;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import com.oracle.svm.util.UnsafePartitionKind;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class AnalysisField implements ResolvedJavaField, OriginalFieldProvider {

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<AnalysisField, Object> OBSERVERS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisField.class, Object.class, "observers");

    private final int id;

    public final ResolvedJavaField wrapped;

    /** Field type flow for the static fields. */
    private FieldTypeFlow staticFieldFlow;

    /** Initial field type flow, i.e., as specified by the analysis client. */
    private FieldTypeFlow initialInstanceFieldFlow;

    /**
     * Field type flow that reflects all the types flowing in this field on its declaring type and
     * all the sub-types. It doesn't track any context-sensitive information.
     */
    private ContextInsensitiveFieldTypeFlow instanceFieldFlow;

    private AtomicBoolean isAccessed = new AtomicBoolean();
    private AtomicBoolean isRead = new AtomicBoolean();
    private AtomicBoolean isWritten = new AtomicBoolean();
    private AtomicBoolean isFolded = new AtomicBoolean();

    private boolean isJNIAccessed;
    private boolean isUsedInComparison;
    private AtomicBoolean isUnsafeAccessed;
    private AtomicBoolean unsafeFrozenTypeState;
    @SuppressWarnings("unused") private volatile Object observers;

    /**
     * By default all instance fields are null before are initialized. It can be specified by
     * {@link HostVM} that certain fields will not be null.
     */
    private boolean canBeNull;

    private ConcurrentMap<MethodTypeFlow, Boolean> readBy;
    private ConcurrentMap<MethodTypeFlow, Boolean> writtenBy;

    protected TypeState instanceFieldTypeState;

    /** Field's position in the list of declaring type's fields, including inherited fields. */
    protected int position;

    protected final AnalysisType declaringClass;
    protected final AnalysisType fieldType;

    public AnalysisField(AnalysisUniverse universe, ResolvedJavaField wrappedField) {
        assert !wrappedField.isInternal();

        this.position = -1;
        this.isUnsafeAccessed = new AtomicBoolean();
        this.unsafeFrozenTypeState = new AtomicBoolean();

        this.wrapped = wrappedField;
        this.id = universe.nextFieldId.getAndIncrement();

        boolean trackAccessChain = PointstoOptions.TrackAccessChain.getValue(universe.hostVM().options());
        readBy = trackAccessChain ? new ConcurrentHashMap<>() : null;
        writtenBy = trackAccessChain ? new ConcurrentHashMap<>() : null;

        declaringClass = universe.lookup(wrappedField.getDeclaringClass());
        fieldType = getDeclaredType(universe, wrappedField);

        isUsedInComparison = false;

        if (this.isStatic()) {
            this.canBeNull = false;
            this.staticFieldFlow = new FieldTypeFlow(this, getType());
            this.initialInstanceFieldFlow = null;
        } else {
            this.canBeNull = true;
            this.instanceFieldFlow = new ContextInsensitiveFieldTypeFlow(this, getType());
            this.initialInstanceFieldFlow = new FieldTypeFlow(this, getType());
        }
    }

    private AnalysisUniverse getUniverse() {
        /* Access the universe via the declaring class to avoid storing it here. */
        return declaringClass.getUniverse();
    }

    private static AnalysisType getDeclaredType(AnalysisUniverse universe, ResolvedJavaField wrappedField) {
        ResolvedJavaType resolvedType;
        try {
            resolvedType = wrappedField.getType().resolve(universe.substitutions.resolve(wrappedField.getDeclaringClass()));
        } catch (LinkageError e) {
            /*
             * Type resolution fails if the declared type is missing. Just erase the type by
             * returning the Object type.
             */
            return universe.objectType();
        }

        return universe.lookup(resolvedType);
    }

    public void copyAccessInfos(AnalysisField other) {
        this.isAccessed = new AtomicBoolean(other.isAccessed.get());
        this.isUnsafeAccessed = other.isUnsafeAccessed;
        this.canBeNull = other.canBeNull;
        this.isWritten = new AtomicBoolean(other.isWritten.get());
        this.isFolded = new AtomicBoolean(other.isFolded.get());
        this.isRead = new AtomicBoolean(other.isRead.get());
        notifyUpdateAccessInfo();
    }

    public void intersectAccessInfos(AnalysisField other) {
        this.isAccessed = new AtomicBoolean(this.isAccessed.get() && other.isAccessed.get());
        this.canBeNull = this.canBeNull && other.canBeNull;
        this.isWritten = new AtomicBoolean(this.isWritten.get() && other.isWritten.get());
        this.isFolded = new AtomicBoolean(this.isFolded.get() && other.isFolded.get());
        this.isRead = new AtomicBoolean(this.isRead.get() && other.isRead.get());
        notifyUpdateAccessInfo();
    }

    public void clearAccessInfos() {
        this.isAccessed.set(false);
        this.canBeNull = true;
        this.isWritten.set(false);
        this.isFolded.set(false);
        this.isRead.set(false);
        notifyUpdateAccessInfo();
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
            return interceptTypeState(staticFieldFlow.getState());
        } else {
            return getInstanceFieldTypeState();
        }
    }

    public TypeState getInstanceFieldTypeState() {
        return interceptTypeState(instanceFieldFlow.getState());
    }

    public FieldTypeFlow getInitialInstanceFieldFlow() {
        return initialInstanceFieldFlow;
    }

    public FieldTypeFlow getStaticFieldFlow() {
        assert Modifier.isStatic(this.getModifiers());

        return staticFieldFlow;
    }

    /** Get the field type flow, stripped of any context. */
    public ContextInsensitiveFieldTypeFlow getInstanceFieldFlow() {
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

    public boolean registerAsAccessed() {
        boolean firstAttempt = AtomicUtils.atomicMark(isAccessed);
        notifyUpdateAccessInfo();
        if (firstAttempt) {
            getUniverse().onFieldAccessed(this);
            getUniverse().getHeapScanner().onFieldRead(this);
        }
        return firstAttempt;
    }

    public boolean registerAsRead(MethodTypeFlow method) {
        boolean firstAttempt = AtomicUtils.atomicMark(isRead);
        notifyUpdateAccessInfo();
        if (readBy != null && method != null) {
            readBy.put(method, Boolean.TRUE);
        }
        if (firstAttempt) {
            getUniverse().onFieldAccessed(this);
            getUniverse().getHeapScanner().onFieldRead(this);
        }
        return firstAttempt;
    }

    /**
     * Registers that the field is written.
     *
     * @param method The method where the field is written or null if the method is not known, e.g.
     *            for an unsafe accessed field.
     */
    public boolean registerAsWritten(MethodTypeFlow method) {
        boolean firstAttempt = AtomicUtils.atomicMark(isWritten);
        notifyUpdateAccessInfo();
        if (writtenBy != null && method != null) {
            writtenBy.put(method, Boolean.TRUE);
        }
        if (firstAttempt && (Modifier.isVolatile(getModifiers()) || getStorageKind() == JavaKind.Object)) {
            getUniverse().onFieldAccessed(this);
        }
        return firstAttempt;
    }

    public void markFolded() {
        if (AtomicUtils.atomicMark(isFolded)) {
            getDeclaringClass().registerAsReachable();
        }
    }

    public void registerAsUnsafeAccessed() {
        registerAsUnsafeAccessed(DefaultUnsafePartition.get());
    }

    public void registerAsUnsafeAccessed(UnsafePartitionKind partitionKind) {
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
                getUniverse().registerUnsafeAccessedStaticField(this);
            } else {
                /* Register the instance field as unsafe accessed on the declaring type. */
                AnalysisType declaringType = getDeclaringClass();
                declaringType.registerUnsafeAccessedField(this, partitionKind);
            }
        }
        notifyUpdateAccessInfo();
    }

    public boolean isUnsafeAccessed() {
        return isUnsafeAccessed.get();
    }

    public void registerAsJNIAccessed() {
        isJNIAccessed = true;
    }

    public boolean isJNIAccessed() {
        return isJNIAccessed;
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

    /**
     * Returns true if the field is reachable. Fields that are read or manually registered as
     * reachable are always reachable. For fields that are write-only, more cases need to be
     * considered:
     *
     * If a primitive field is never read, writes to it are useless as well and we can eliminate the
     * field. Unless the field is volatile, because the write is a memory barrier and therefore has
     * side effects.
     *
     * Object fields must be preserved even when they are never read, because the reachability of an
     * object is an observable side effect: Removing an object field could lead to a ReferenceQueue
     * processing the no-longer-stored value object. An example is the field DirectByteBuffer.att:
     * It is never read, but it ensures that native memory is not reclaimed when only a view to a
     * DirectByteBuffer remains reachable.
     */
    public boolean isAccessed() {
        return isAccessed.get() || isRead.get() || (isWritten.get() && (Modifier.isVolatile(getModifiers()) || getStorageKind() == JavaKind.Object));
    }

    public boolean isRead() {
        return isAccessed.get() || isRead.get();
    }

    public boolean isWritten() {
        return isAccessed.get() || isWritten.get();
    }

    public boolean isFolded() {
        return isFolded.get();
    }

    public boolean isReachable() {
        return isAccessed.get() || isRead.get() || isWritten.get() || isFolded.get();
    }

    public void setCanBeNull(boolean canBeNull) {
        this.canBeNull = canBeNull;
        notifyUpdateAccessInfo();
    }

    public boolean canBeNull() {
        return canBeNull;
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    public void setPosition(int newPosition) {
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
        /*
         * The static analysis itself does not use field offsets. We could return the offset from
         * the hosting HotSpot VM, but it is safer to disallow the operation entirely. The offset
         * from the hosting VM can be accessed by explicitly calling `wrapped.getOffset()`.
         */
        throw GraalError.shouldNotReachHere();
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
        return "AnalysisField<" + format("%h.%n") + " accessed: " + isAccessed + " reads: " + isRead + " written: " + isWritten + " folded: " + isFolded + ">";
    }

    public void markAsUsedInComparison() {
        isUsedInComparison = true;
    }

    public boolean isUsedInComparison() {
        return isUsedInComparison;
    }

    @Override
    public Field getJavaField() {
        return OriginalFieldProvider.getJavaField(getUniverse().getOriginalSnippetReflection(), wrapped);
    }

    public void addAnalysisFieldObserver(AnalysisFieldObserver observer) {
        ConcurrentLightHashSet.addElement(this, OBSERVERS_UPDATER, observer);
    }

    public void removeAnalysisFieldObserver(AnalysisFieldObserver observer) {
        ConcurrentLightHashSet.removeElement(this, OBSERVERS_UPDATER, observer);
    }

    private void notifyUpdateAccessInfo() {
        for (Object observer : ConcurrentLightHashSet.getElements(this, OBSERVERS_UPDATER)) {
            ((AnalysisFieldObserver) observer).notifyUpdateAccessInfo(this);
        }
    }

    private TypeState interceptTypeState(TypeState typestate) {
        TypeState result = typestate;
        for (Object observer : ConcurrentLightHashSet.getElements(this, OBSERVERS_UPDATER)) {
            result = ((AnalysisFieldObserver) observer).interceptTypeState(this, typestate);
        }
        return result;
    }

    public interface AnalysisFieldObserver {
        void notifyUpdateAccessInfo(AnalysisField field);

        TypeState interceptTypeState(AnalysisField field, TypeState typestate);
    }
}
