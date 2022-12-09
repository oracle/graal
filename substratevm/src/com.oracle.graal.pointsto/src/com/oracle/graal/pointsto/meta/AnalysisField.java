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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.graalvm.compiler.debug.GraalError;

import com.oracle.graal.pointsto.api.DefaultUnsafePartition;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaField;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AtomicUtils;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import com.oracle.svm.util.AnnotationWrapper;
import com.oracle.svm.util.UnsafePartitionKind;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class AnalysisField extends AnalysisElement implements WrappedJavaField, OriginalFieldProvider, AnnotationWrapper {

    @SuppressWarnings("rawtypes")//
    private static final AtomicReferenceFieldUpdater<AnalysisField, Object> OBSERVERS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisField.class, Object.class, "observers");

    private static final AtomicReferenceFieldUpdater<AnalysisField, Object> isAccessedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisField.class, Object.class, "isAccessed");

    private static final AtomicReferenceFieldUpdater<AnalysisField, Object> isReadUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisField.class, Object.class, "isRead");

    private static final AtomicReferenceFieldUpdater<AnalysisField, Object> isWrittenUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisField.class, Object.class, "isWritten");

    private static final AtomicReferenceFieldUpdater<AnalysisField, Object> isFoldedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisField.class, Object.class, "isFolded");

    private static final AtomicReferenceFieldUpdater<AnalysisField, Object> isUnsafeAccessedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisField.class, Object.class, "isUnsafeAccessed");

    private static final AtomicIntegerFieldUpdater<AnalysisField> unsafeFrozenTypeStateUpdater = AtomicIntegerFieldUpdater
                    .newUpdater(AnalysisField.class, "unsafeFrozenTypeState");

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

    /** The reason flags contain a {@link BytecodePosition} or a reason object. */
    @SuppressWarnings("unused") private volatile Object isRead;
    @SuppressWarnings("unused") private volatile Object isAccessed;
    @SuppressWarnings("unused") private volatile Object isWritten;
    @SuppressWarnings("unused") private volatile Object isFolded;

    private boolean isJNIAccessed;
    private boolean isUsedInComparison;
    @SuppressWarnings("unused") private volatile Object isUnsafeAccessed;
    @SuppressWarnings("unused") private volatile int unsafeFrozenTypeState;
    @SuppressWarnings("unused") private volatile Object observers;

    /**
     * By default all instance fields are null before are initialized. It can be specified by
     * {@link HostVM} that certain fields will not be null.
     */
    private boolean canBeNull;

    private ConcurrentMap<Object, Boolean> readBy;
    private ConcurrentMap<Object, Boolean> writtenBy;

    protected TypeState instanceFieldTypeState;

    /** Field's position in the list of declaring type's fields, including inherited fields. */
    protected int position;

    protected final AnalysisType declaringClass;
    protected final AnalysisType fieldType;

    public AnalysisField(AnalysisUniverse universe, ResolvedJavaField wrappedField) {
        assert !wrappedField.isInternal();

        this.position = -1;

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

    @Override
    public ResolvedJavaField getWrapped() {
        return wrapped;
    }

    public void copyAccessInfos(AnalysisField other) {
        isAccessedUpdater.set(this, other.isAccessed);
        isUnsafeAccessedUpdater.set(this, other.isUnsafeAccessed);
        this.canBeNull = other.canBeNull;
        isWrittenUpdater.set(this, other.isWritten);
        isFoldedUpdater.set(this, other.isFolded);
        isReadUpdater.set(this, other.isRead);
        notifyUpdateAccessInfo();
    }

    public void intersectAccessInfos(AnalysisField other) {
        isAccessedUpdater.set(this, this.isAccessed != null & other.isAccessed != null ? this.isAccessed : null);
        this.canBeNull = this.canBeNull && other.canBeNull;
        isWrittenUpdater.set(this, this.isWritten != null & other.isWritten != null ? this.isWritten : null);
        isFoldedUpdater.set(this, this.isFolded != null & other.isFolded != null ? this.isFolded : null);
        isReadUpdater.set(this, this.isRead != null & other.isRead != null ? this.isRead : null);
        notifyUpdateAccessInfo();
    }

    public void clearAccessInfos() {
        isAccessedUpdater.set(this, 0);
        this.canBeNull = true;
        isWrittenUpdater.set(this, 0);
        isFoldedUpdater.set(this, 0);
        isReadUpdater.set(this, null);
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

    public boolean registerAsAccessed(Object reason) {
        assert isValidReason(reason) : "Registering a field as accessed needs to provide a valid reason.";
        boolean firstAttempt = AtomicUtils.atomicSet(this, reason, isAccessedUpdater);
        notifyUpdateAccessInfo();
        if (firstAttempt) {
            onReachable();
            getUniverse().onFieldAccessed(this);
            getUniverse().getHeapScanner().onFieldRead(this);
        }
        return firstAttempt;
    }

    /**
     * @param reason the reason why this field is read, non-null
     */
    public boolean registerAsRead(Object reason) {
        assert isValidReason(reason) : "Registering a field as read needs to provide a valid reason.";
        boolean firstAttempt = AtomicUtils.atomicSet(this, reason, isReadUpdater);
        notifyUpdateAccessInfo();
        if (readBy != null) {
            readBy.put(reason, Boolean.TRUE);
        }
        if (firstAttempt) {
            onReachable();
            getUniverse().onFieldAccessed(this);
            getUniverse().getHeapScanner().onFieldRead(this);
        }
        return firstAttempt;
    }

    /**
     * Registers that the field is written.
     *
     * @param reason the reason why this field is written, non-null
     */
    public boolean registerAsWritten(Object reason) {
        assert isValidReason(reason) : "Registering a field as written needs to provide a valid reason.";
        boolean firstAttempt = AtomicUtils.atomicSet(this, reason, isWrittenUpdater);
        notifyUpdateAccessInfo();
        if (writtenBy != null && reason != null) {
            writtenBy.put(reason, Boolean.TRUE);
        }
        if (firstAttempt) {
            onReachable();
            if (Modifier.isVolatile(getModifiers()) || getStorageKind() == JavaKind.Object) {
                getUniverse().onFieldAccessed(this);
            }
        }
        return firstAttempt;
    }

    public void registerAsFolded(Object reason) {
        assert isValidReason(reason) : "Registering a field as folded needs to provide a valid reason.";
        if (AtomicUtils.atomicSet(this, reason, isFoldedUpdater)) {
            assert getDeclaringClass().isReachable();
            onReachable();
        }
    }

    public void registerAsUnsafeAccessed(Object reason) {
        registerAsUnsafeAccessed(DefaultUnsafePartition.get(), reason);
    }

    public boolean registerAsUnsafeAccessed(UnsafePartitionKind partitionKind, Object reason) {
        assert isValidReason(reason) : "Registering a field as unsafe accessed needs to provide a valid reason.";
        registerAsAccessed(reason);
        /*
         * A field can potentially be registered as unsafe accessed multiple times. This is
         * especially true for the Graal nodes because FieldsOffsetsFeature.registerFields iterates
         * over all the unsafe accessed fields of a node, including those in the super types.
         *
         * To avoid using a hash set to keep track of the accessed fields in the analysis type we
         * only register fields as unsafe accessed with their declaring type once.
         */

        if (AtomicUtils.atomicSet(this, reason, isUnsafeAccessedUpdater)) {
            /*
             * The atomic updater ensures that the field is registered as unsafe accessed with its
             * declaring class only once. However, at the end of this call the registration might
             * still be in progress. The first thread that calls this methods enters the if and
             * starts the registration, the next threads return right away, while the registration
             * might still be in progress.
             */

            registerAsWritten(reason);

            if (isStatic()) {
                /* Register the static field as unsafe accessed with the analysis universe. */
                getUniverse().registerUnsafeAccessedStaticField(this);
            } else {
                /* Register the instance field as unsafe accessed on the declaring type. */
                AnalysisType declaringType = getDeclaringClass();
                declaringType.registerUnsafeAccessedField(this, partitionKind);
            }
            return true;
        }
        notifyUpdateAccessInfo();
        return false;
    }

    public boolean isUnsafeAccessed() {
        return AtomicUtils.isSet(this, isUnsafeAccessedUpdater);
    }

    public void registerAsJNIAccessed() {
        isJNIAccessed = true;
    }

    public boolean isJNIAccessed() {
        return isJNIAccessed;
    }

    public void setUnsafeFrozenTypeState(boolean value) {
        unsafeFrozenTypeStateUpdater.set(this, value ? 1 : 0);
    }

    public boolean hasUnsafeFrozenTypeState() {
        return AtomicUtils.isSet(this, unsafeFrozenTypeStateUpdater);
    }

    public Object getReadBy() {
        return isReadUpdater.get(this);
    }

    /**
     * Returns all methods where the field is written. It does not include the methods where the
     * field is written with unsafe access.
     */
    public Set<Object> getWrittenBy() {
        return writtenBy.keySet();
    }

    private boolean isAccessedSet() {
        return AtomicUtils.isSet(this, isAccessedUpdater);
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
        return AtomicUtils.isSet(this, isAccessedUpdater) || AtomicUtils.isSet(this, isReadUpdater) ||
                        (AtomicUtils.isSet(this, isWrittenUpdater) && (Modifier.isVolatile(getModifiers()) || getStorageKind() == JavaKind.Object));
    }

    private boolean isReadSet() {
        return AtomicUtils.isSet(this, isReadUpdater);
    }

    public boolean isRead() {
        return AtomicUtils.isSet(this, isAccessedUpdater) || AtomicUtils.isSet(this, isReadUpdater);
    }

    private boolean isWrittenSet() {
        return AtomicUtils.isSet(this, isWrittenUpdater);
    }

    public boolean isWritten() {
        return AtomicUtils.isSet(this, isAccessedUpdater) || AtomicUtils.isSet(this, isWrittenUpdater);
    }

    private boolean isFoldedSet() {
        return AtomicUtils.isSet(this, isFoldedUpdater);
    }

    public boolean isFolded() {
        return AtomicUtils.isSet(this, isFoldedUpdater);
    }

    @Override
    public boolean isReachable() {
        return AtomicUtils.isSet(this, isAccessedUpdater) || AtomicUtils.isSet(this, isReadUpdater) ||
                        AtomicUtils.isSet(this, isWrittenUpdater) || AtomicUtils.isSet(this, isFoldedUpdater);
    }

    @Override
    public void onReachable() {
        notifyReachabilityCallbacks(declaringClass.getUniverse(), new ArrayList<>());
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
    public AnnotatedElement getAnnotationRoot() {
        return wrapped;
    }

    @Override
    public String toString() {
        return "AnalysisField<" + format("%h.%n") + " accessed: " + isAccessedSet() + " reads: " + isReadSet() + " written: " + isWrittenSet() + " folded: " + isFoldedSet() + ">";
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
