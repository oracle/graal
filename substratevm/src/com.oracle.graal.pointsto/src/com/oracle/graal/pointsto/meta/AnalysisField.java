/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaField;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.AtomicUtils;
import com.oracle.svm.common.meta.GuaranteeFolded;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class AnalysisField extends AnalysisElement implements WrappedJavaField, OriginalFieldProvider {

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
    private final int id;
    /** Marks a field loaded from a base layer. */
    private final boolean isInBaseLayer;

    public final ResolvedJavaField wrapped;

    /**
     * Initial field type flow, i.e., as specified by the analysis client. It can be used to inject
     * specific types into a field that the analysis would not see on its own, and to inject the
     * null value into a field.
     */
    protected FieldTypeFlow initialFlow;
    /**
     * Field type flow that reflects all the types flowing in this field on its declaring type and
     * all the sub-types. It does not track any context-sensitive information.
     */
    protected FieldTypeFlow sinkFlow;

    /** The reason flags contain a {@link BytecodePosition} or a reason object. */
    @SuppressWarnings("unused") private volatile Object isRead;
    @SuppressWarnings("unused") private volatile Object isAccessed;
    @SuppressWarnings("unused") private volatile Object isWritten;
    @SuppressWarnings("unused") private volatile Object isFolded;
    @SuppressWarnings("unused") private volatile Object isUnsafeAccessed;

    private ConcurrentMap<Object, Boolean> readBy;
    private ConcurrentMap<Object, Boolean> writtenBy;

    /** Field's position in the list of declaring type's fields, including inherited fields. */
    protected int position;

    protected final AnalysisType declaringClass;
    protected final AnalysisType fieldType;

    /**
     * Marks a field whose value is computed during image building, in general derived from other
     * values. The actual meaning of the field value is out of scope of the static analysis, but the
     * value is stored here to allow fast access.
     */
    protected Object fieldValueInterceptor;

    /**
     * When building layered images, for static fields we must keep track of what layer's static
     * fields array the field is assigned in. This also impacts when the underlying value can be
     * read and/or constant folded.
     */
    private final boolean isLayeredStaticField;

    @SuppressWarnings("this-escape")
    public AnalysisField(AnalysisUniverse universe, ResolvedJavaField wrappedField) {
        super(universe.hostVM.enableTrackAcrossLayers());
        assert !wrappedField.isInternal() : wrappedField;

        this.position = -1;

        this.wrapped = wrappedField;

        boolean trackAccessChain = universe.analysisPolicy().trackAccessChain();
        readBy = trackAccessChain ? new ConcurrentHashMap<>() : null;
        writtenBy = trackAccessChain ? new ConcurrentHashMap<>() : null;

        declaringClass = universe.lookup(wrappedField.getDeclaringClass());
        fieldType = getDeclaredType(universe, wrappedField);

        initialFlow = new FieldTypeFlow(this, getType());
        if (this.isStatic()) {
            /* There is never any context-sensitivity for static fields. */
            sinkFlow = initialFlow;
        } else {
            /*
             * Regardless of the context-sensitivity policy, there is always this single type flow
             * that accumulates all types.
             */
            sinkFlow = new ContextInsensitiveFieldTypeFlow(this, getType());
        }

        if (universe.hostVM().buildingExtensionLayer() && declaringClass.isInBaseLayer()) {
            int fid = universe.getImageLayerLoader().lookupHostedFieldInBaseLayer(this);
            if (fid != -1) {
                /*
                 * This id is the actual link between the corresponding field from the base layer
                 * and this new field.
                 */
                id = fid;
                isInBaseLayer = true;
            } else {
                id = universe.computeNextFieldId();
                isInBaseLayer = false;
            }
        } else {
            id = universe.computeNextFieldId();
            isInBaseLayer = false;
        }
        isLayeredStaticField = isStatic() && universe.hostVM.buildingImageLayer();
    }

    @Override
    protected AnalysisUniverse getUniverse() {
        /* Access the universe via the declaring class to avoid storing it here. */
        return declaringClass.getUniverse();
    }

    private static AnalysisType getDeclaredType(AnalysisUniverse universe, ResolvedJavaField wrappedField) {
        ResolvedJavaType resolvedType;
        try {
            resolvedType = wrappedField.getType().resolve(OriginalClassProvider.getOriginalType(wrappedField.getDeclaringClass()));
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

    public int getId() {
        return id;
    }

    public boolean isInBaseLayer() {
        return isInBaseLayer;
    }

    public boolean installableInLayer() {
        if (isLayeredStaticField) {
            return getUniverse().hostVM.installableInLayer(this);
        } else {
            return true;
        }
    }

    public boolean preventConstantFolding() {
        if (isLayeredStaticField) {
            return getUniverse().hostVM.preventConstantFolding(this);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return id;
    }

    public JavaKind getStorageKind() {
        return fieldType.getStorageKind();

    }

    public FieldTypeFlow getInitialFlow() {
        return initialFlow;
    }

    public FieldTypeFlow getSinkFlow() {
        return sinkFlow;
    }

    public FieldTypeFlow getStaticFieldFlow() {
        assert Modifier.isStatic(this.getModifiers()) : this;
        return sinkFlow;
    }

    public void cleanupAfterAnalysis() {
        initialFlow = null;
        sinkFlow = null;
        readBy = null;
        writtenBy = null;
    }

    public boolean registerAsAccessed(Object reason) {
        checkGuaranteeFolded();
        getDeclaringClass().registerAsReachable(this);

        assert isValidReason(reason) : "Registering a field as accessed needs to provide a valid reason.";
        return AtomicUtils.atomicSetAndRun(this, reason, isAccessedUpdater, () -> {
            onReachable(reason);
            getUniverse().onFieldAccessed(this);
            getUniverse().getHeapScanner().onFieldRead(this);
        });
    }

    /**
     * @param reason the reason why this field is read, non-null
     */
    public boolean registerAsRead(Object reason) {
        checkGuaranteeFolded();
        getDeclaringClass().registerAsReachable(this);

        assert isValidReason(reason) : "Registering a field as read needs to provide a valid reason.";
        if (readBy != null) {
            readBy.put(reason, Boolean.TRUE);
        }
        return AtomicUtils.atomicSetAndRun(this, reason, isReadUpdater, () -> {
            onReachable(reason);
            getUniverse().onFieldAccessed(this);
            getUniverse().getHeapScanner().onFieldRead(this);
        });
    }

    /**
     * Registers that the field is written.
     *
     * @param reason the reason why this field is written, non-null
     */
    public boolean registerAsWritten(Object reason) {
        checkGuaranteeFolded();
        getDeclaringClass().registerAsReachable(this);

        assert isValidReason(reason) : "Registering a field as written needs to provide a valid reason.";
        if (writtenBy != null) {
            writtenBy.put(reason, Boolean.TRUE);
        }
        return AtomicUtils.atomicSetAndRun(this, reason, isWrittenUpdater, () -> {
            onReachable(reason);
            if (Modifier.isVolatile(getModifiers()) || getStorageKind() == JavaKind.Object) {
                getUniverse().onFieldAccessed(this);
            }
        });
    }

    public void injectDeclaredType() {
        BigBang bb = getUniverse().getBigbang();
        if (getStorageKind().isObject()) {
            bb.injectFieldTypes(this, List.of(this.getType()), true);
        } else if (bb.trackPrimitiveValues() && getStorageKind().isPrimitive()) {
            ((PointsToAnalysisField) this).saturatePrimitiveField();
        }
    }

    public boolean isGuaranteeFolded() {
        return getAnnotation(GuaranteeFolded.class) != null;
    }

    public void checkGuaranteeFolded() {
        AnalysisError.guarantee(!isGuaranteeFolded(), "A field that is guaranteed to always be folded is seen as accessed: %s. ", this);
    }

    public void registerAsFolded(Object reason) {
        getDeclaringClass().registerAsReachable(this);

        assert isValidReason(reason) : "Registering a field as folded needs to provide a valid reason.";
        AtomicUtils.atomicSetAndRun(this, reason, isFoldedUpdater, () -> {
            assert getDeclaringClass().isReachable() : this;
            onReachable(reason);
        });
    }

    public boolean registerAsUnsafeAccessed(Object reason) {
        checkGuaranteeFolded();
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

        return AtomicUtils.atomicSetAndRun(this, reason, isUnsafeAccessedUpdater, () -> {
            /*
             * The atomic updater ensures that the field is registered as unsafe accessed with its
             * declaring class only once. However, at the end of this call the registration might
             * still be in progress. The first thread that calls this methods enters the if and
             * starts the registration, the next threads return right away, while the registration
             * might still be in progress.
             */

            registerAsWritten(reason);

            if (getUniverse().analysisPolicy().useConservativeUnsafeAccess()) {
                /*
                 * With conservative unsafe access we don't need to track unsafe accessed fields.
                 * Instead, fields marked as unsafe-accessed are injected all instantiated subtypes
                 * of their declared type. Moreover, all unsafe loads are pre-saturated.
                 */
                injectDeclaredType();
            } else {
                if (isStatic()) {
                    /* Register the static field as unsafe accessed with the analysis universe. */
                    getUniverse().registerUnsafeAccessedStaticField(this);
                } else {
                    /* Register the instance field as unsafe accessed on the declaring type. */
                    AnalysisType declaringType = getDeclaringClass();
                    declaringType.registerUnsafeAccessedField(this);
                }
            }
        });
    }

    public boolean isUnsafeAccessed() {
        return AtomicUtils.isSet(this, isUnsafeAccessedUpdater);
    }

    public Object getReadBy() {
        return isReadUpdater.get(this);
    }

    public Object getAccessedReason() {
        return isAccessed;
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

    public boolean isRead() {
        return AtomicUtils.isSet(this, isAccessedUpdater) || AtomicUtils.isSet(this, isReadUpdater);
    }

    public Object getReadReason() {
        return isRead;
    }

    public boolean isWritten() {
        return AtomicUtils.isSet(this, isAccessedUpdater) || AtomicUtils.isSet(this, isWrittenUpdater);
    }

    public Object getWrittenReason() {
        return isWritten;
    }

    public boolean isFolded() {
        return AtomicUtils.isSet(this, isFoldedUpdater);
    }

    public Object getFoldedReason() {
        return isFolded;
    }

    @Override
    public boolean isReachable() {
        return AtomicUtils.isSet(this, isAccessedUpdater) || AtomicUtils.isSet(this, isReadUpdater) ||
                        AtomicUtils.isSet(this, isWrittenUpdater) || AtomicUtils.isSet(this, isFoldedUpdater);
    }

    @Override
    public void onReachable(Object reason) {
        registerAsTrackedAcrossLayers(reason);
        notifyReachabilityCallbacks(declaringClass.getUniverse(), new ArrayList<>());
    }

    @Override
    protected void onTrackedAcrossLayers(Object reason) {
        AnalysisError.guarantee(!getUniverse().sealed(), "Field %s was marked as tracked after the universe was sealed", this);
    }

    public Object getFieldValueInterceptor() {
        return fieldValueInterceptor;
    }

    public void setFieldValueInterceptor(Object fieldValueInterceptor) {
        this.fieldValueInterceptor = fieldValueInterceptor;
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    public void setPosition(int newPosition) {
        AnalysisError.guarantee(position == -1 || newPosition == position, "Position already set for field %s, old position: %d, new position: %d", this, position, newPosition);
        this.position = newPosition;
    }

    public int getPosition() {
        AnalysisError.guarantee(position != -1, "Unknown position for field %s", this);
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
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
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
    public String toString() {
        return "AnalysisField<" + format("%h.%n") + " -> " + wrapped.toString() + ", accessed: " + (isAccessed != null) +
                        ", read: " + (isRead != null) + ", written: " + (isWritten != null) + ", folded: " + isFolded() + ">";
    }

    @Override
    public ResolvedJavaField unwrapTowardsOriginalField() {
        return wrapped;
    }

    @Override
    public JavaConstant getConstantValue() {
        return getUniverse().lookup(getWrapped().getConstantValue());
    }

    /**
     * Ensure that all reachability handlers that were present at the time the declaring type was
     * marked as reachable are executed before accessing field values. This allows a field value
     * transformer to be installed reliably in a reachability handler.
     */
    public void beforeFieldValueAccess() {
        declaringClass.registerAsReachable(this);

        declaringClass.forAllSuperTypes(type -> {
            type.ensureOnTypeReachableTaskDone();

            List<AnalysisFuture<Void>> notifications = type.scheduledTypeReachableNotifications;
            if (notifications != null) {
                for (var notification : notifications) {
                    notification.ensureDone();
                }
                /*
                 * Now we know all the handlers have been executed, no checks are necessary anymore.
                 */
                type.scheduledTypeReachableNotifications = null;
            }
        });
    }
}
