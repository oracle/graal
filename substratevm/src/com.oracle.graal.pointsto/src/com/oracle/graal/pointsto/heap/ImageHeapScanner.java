/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.heap;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ArrayScan;
import com.oracle.graal.pointsto.ObjectScanner.EmbeddedRootScan;
import com.oracle.graal.pointsto.ObjectScanner.FieldScan;
import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Scanning is triggered when:
 * <ul>
 * <li>a static final field is marked as accessed, or</li>s
 * <li>a method is parsed and embedded roots are discovered</li>
 * </ul>
 * <p>
 * When an instance field is marked as accessed the objects of its declaring type (and all the
 * subtypes) are re-scanned.
 */
public abstract class ImageHeapScanner {

    private static final JavaConstant[] emptyConstantArray = new JavaConstant[0];

    protected final BigBang bb;
    protected final ImageHeap imageHeap;
    protected final AnalysisMetaAccess metaAccess;
    protected final AnalysisUniverse universe;
    protected final HostVM hostVM;

    protected final SnippetReflectionProvider snippetReflection;
    protected final ConstantReflectionProvider constantReflection;
    protected final ConstantReflectionProvider hostedConstantReflection;
    protected final SnippetReflectionProvider hostedSnippetReflection;

    protected ObjectScanningObserver scanningObserver;

    /** Marker object installed when encountering scanning issues like illegal objects. */
    private static final ImageHeapConstant NULL_IMAGE_HEAP_OBJECT = new ImageHeapInstance(null, JavaConstant.NULL_POINTER, 0);

    public ImageHeapScanner(BigBang bb, ImageHeap heap, AnalysisMetaAccess aMetaAccess, SnippetReflectionProvider aSnippetReflection,
                    ConstantReflectionProvider aConstantReflection, ObjectScanningObserver aScanningObserver) {
        this.bb = bb;
        imageHeap = heap;
        metaAccess = aMetaAccess;
        universe = aMetaAccess.getUniverse();
        hostVM = aMetaAccess.getUniverse().hostVM();
        snippetReflection = aSnippetReflection;
        constantReflection = aConstantReflection;
        scanningObserver = aScanningObserver;
        hostedConstantReflection = GraalAccess.getOriginalProviders().getConstantReflection();
        hostedSnippetReflection = GraalAccess.getOriginalProviders().getSnippetReflection();
    }

    public void scanEmbeddedRoot(JavaConstant root, BytecodePosition position) {
        if (isNonNullObjectConstant(root)) {
            AnalysisType type = metaAccess.lookupJavaType(root);
            EmbeddedRootScan reason = new EmbeddedRootScan(position, root);
            type.registerAsReachable(reason);
            getOrCreateConstantReachableTask(root, reason, null);
        }
    }

    public void onFieldRead(AnalysisField field) {
        assert field.isRead();
        /* Check if the value is available before accessing it. */
        if (isValueAvailable(field)) {
            AnalysisType declaringClass = field.getDeclaringClass();
            if (field.isStatic()) {
                snapshotFieldValue(field, declaringClass.getOrComputeData().getFieldValue(field));
            } else {
                /* Trigger field scanning for the already processed objects. */
                postTask(() -> onInstanceFieldRead(field, declaringClass));
            }
        }
    }

    private void onInstanceFieldRead(AnalysisField field, AnalysisType type) {
        for (AnalysisType subtype : type.getSubTypes()) {
            for (ImageHeapConstant imageHeapConstant : imageHeap.getObjects(subtype)) {
                snapshotFieldValue(field, ((ImageHeapInstance) imageHeapConstant).getFieldValue(field));
            }
            /* Subtypes include this type itself. */
            if (!subtype.equals(type)) {
                onInstanceFieldRead(field, subtype);
            }
        }
    }

    /**
     * Computes the class initialization status and the snapshot of all static fields. This is an
     * expensive operation and therefore done in an asynchronous task.
     */
    public TypeData computeTypeData(AnalysisType type) {
        GraalError.guarantee(type.isReachable(), "TypeData is only available for reachable types");

        /*
         * Snapshot all static fields. This reads the raw field value of all fields regardless of
         * reachability status. The field value is processed when a field is marked as reachable, in
         * onFieldValueReachable().
         */
        AnalysisField[] staticFields = type.getStaticFields();
        TypeData data = new TypeData(staticFields.length);
        for (AnalysisField field : staticFields) {
            ValueSupplier<JavaConstant> rawFieldValue = readHostedFieldValue(field, null);
            data.setFieldTask(field, new AnalysisFuture<>(() -> {
                JavaConstant value = onFieldValueReachable(field, rawFieldValue, new FieldScan(field));
                data.setFieldValue(field, value);
                return value;
            }));
        }

        return data;
    }

    void markTypeInstantiated(AnalysisType type, ScanReason reason) {
        if (universe.sealed() && !type.isReachable()) {
            throw AnalysisError.shouldNotReachHere("Universe is sealed. New type reachable: " + type.toJavaName());
        }
        universe.getBigbang().registerTypeAsInHeap(type, reason);
    }

    JavaConstant markConstantReachable(JavaConstant constant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        if (isNonNullObjectConstant(constant)) {
            return getOrCreateConstantReachableTask(constant, reason, onAnalysisModified);
        }
        return constant;
    }

    public ImageHeapConstant toImageHeapObject(JavaConstant constant) {
        return toImageHeapObject(constant, OtherReason.RESCAN, null);
    }

    protected ImageHeapConstant toImageHeapObject(JavaConstant constant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        assert constant != null && isNonNullObjectConstant(constant);
        return getOrCreateConstantReachableTask(constant, reason, onAnalysisModified);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ImageHeapConstant getOrCreateConstantReachableTask(JavaConstant javaConstant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        ScanReason nonNullReason = Objects.requireNonNull(reason);
        Object existingTask = imageHeap.getTask(javaConstant);
        if (existingTask == null) {
            if (universe.sealed()) {
                throw AnalysisError.shouldNotReachHere("Universe is sealed. New constant reachable: " + javaConstant.toValueString());
            }
            if (javaConstant instanceof ImageHeapConstant) {
                /* This is already an ImageHeapObject. */
                ImageHeapConstant imageHeapConstant = (ImageHeapConstant) javaConstant;
                imageHeap.setValue(javaConstant, imageHeapConstant);
                imageHeap.add((AnalysisType) imageHeapConstant.getType(metaAccess), imageHeapConstant);
                /* Ensure all the referenced objects are scanned. */
                scanImageHeapObject(imageHeapConstant, nonNullReason, onAnalysisModified);
                existingTask = javaConstant;
            } else {
                AnalysisFuture<ImageHeapConstant> newTask = new AnalysisFuture<>(() -> {
                    ImageHeapConstant imageHeapConstant = createImageHeapObject(javaConstant, nonNullReason, onAnalysisModified);
                    /* When the image heap object is created replace the future in the map. */
                    imageHeap.setValue(javaConstant, imageHeapConstant);
                    return imageHeapConstant;
                });
                existingTask = imageHeap.setTask(javaConstant, newTask);
                if (existingTask == null) {
                    /*
                     * Immediately schedule the new task. There is no need to have not-yet-reachable
                     * ImageHeapObject.
                     */
                    postTask(newTask);
                    return newTask.ensureDone();
                }
            }
        }
        return existingTask instanceof ImageHeapConstant ? (ImageHeapConstant) existingTask : ((AnalysisFuture<ImageHeapConstant>) existingTask).ensureDone();
    }

    /**
     * Scan injected heap objects, i.e., heap objects that do not originate from scanning underlying
     * hosted constants.
     */
    protected void scanImageHeapObject(ImageHeapConstant object, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        assert object.getJavaKind() == JavaKind.Object && !object.isNull();

        /*
         * Access the constant type after the replacement. Some constants may have types that should
         * not be reachable at run time and thus are replaced.
         */
        AnalysisType type = (AnalysisType) object.getType(metaAccess);

        if (type.isArray()) {
            if (!type.getComponentType().isPrimitive()) {
                ImageHeapArray array = (ImageHeapArray) object;
                ScanReason arrayReason = new ArrayScan(type, object, reason);
                for (int idx = 0; idx < array.getLength(); idx++) {
                    final JavaConstant elementValue = array.getElement(idx);
                    onArrayElementReachable(array, type, elementValue, idx, arrayReason, onAnalysisModified);
                }
            }
            markTypeInstantiated(type, reason);
        } else {
            ImageHeapInstance instance = (ImageHeapInstance) object;
            /* We are about to query the type's fields, the type must be marked as reachable. */
            markTypeInstantiated(type, reason);
            for (AnalysisField field : type.getInstanceFields(true)) {
                if (field.isRead() && isValueAvailable(field)) {
                    final JavaConstant fieldValue = instance.readFieldValue(field);
                    /* If field is read scan its value immediately. */
                    final ScanReason fieldReason = new FieldScan(field, object, reason);
                    onFieldValueReachable(field, instance, fieldValue, fieldReason, onAnalysisModified);
                } else {
                    /*
                     * If field is not read replace the constant value a future that will scan it
                     * when the field is marked as reachable.
                     */
                    final JavaConstant originalFieldValue = (JavaConstant) instance.getFieldValue(field);
                    instance.setFieldTask(field, new AnalysisFuture<>(() -> {
                        /*
                         * After scanning a field of an injected ImageHeapInstance that references a
                         * regular JavaConstant object should the field value be replaced with the
                         * snapshot version, i.e., an ImageHeapInstance, or should it keep pointing
                         * to the original value? In the long term it should be replaced, but that's
                         * not yet generally possible. First ImageHeapInstance needs to have
                         * complete support for all use cases, it needs to be a complete replacement
                         * for JavaConstant. For example, it needs to be able to efficiently
                         * represent string values and be able to extract the String object.
                         * 
                         * So for now we just reinstall the original JavaConstant value when the
                         * future is completed.
                         * 
                         * More specifically, the long term plan is that after scanning
                         * instance.field will refer to the `scannedFieldValue`, so any future read
                         * of `instance.field` will return an ImageHeapInstance. Moreover,
                         * `instance` is also reached when scanning from roots for verification. In
                         * that case, if we do set the field to the scannedFieldValue returned by
                         * `onFieldValueReachable()`, i.e., an ImageHeapInstance, then we also need
                         * to register a mapping in the heap for the snapshot: scannedFieldValue ->
                         * scannedFieldValue. Otherwise, we only have the originalFieldValue ->
                         * scannedFieldValue mapping and a lookup of scannedFieldValue will fail
                         * during verification. See the snippet below for details.
                         */
                        // @formatter:off
                        // JavaConstant scannedFieldValue = onFieldValueReachable(field, instance, fieldValue, fieldReason, onAnalysisModified);
                        // instance.setFieldValue(field, scannedFieldValue);
                        // imageHeap.setValue(value, (ImageHeapObject) scannedFieldValue);
                        // @formatter:on

                        final ScanReason fieldReason = new FieldScan(field, object, reason);
                        onFieldValueReachable(field, instance, originalFieldValue, fieldReason, onAnalysisModified);
                        /* Re-install the original constant value. */
                        instance.setFieldValue(field, originalFieldValue);

                        return originalFieldValue;
                    }));
                }
            }
        }
    }

    protected ImageHeapConstant createImageHeapObject(JavaConstant constant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull();

        Optional<JavaConstant> replaced = maybeReplace(constant, reason);
        if (replaced.isPresent()) {
            if (replaced.get().isNull()) {
                /* There was some problem during replacement, install a marker object. */
                return NULL_IMAGE_HEAP_OBJECT;
            }
            /*
             * This ensures that we have a unique ImageHeapObject for the original and replaced
             * object. As a side effect, this runs all object transformer again on the replaced
             * constant.
             */
            return toImageHeapObject(replaced.get(), reason, onAnalysisModified);
        }

        /*
         * Access the constant type after the replacement. Some constants may have types that should
         * not be reachable at run time and thus are replaced.
         */
        AnalysisType type = metaAccess.lookupJavaType(constant);

        ImageHeapConstant newImageHeapConstant;
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive()) {
                /*
                 * The shadow heap is only used for points-to analysis currently, we don't need to
                 * track individual elements for primitive arrays.
                 */
                newImageHeapConstant = new ImageHeapArray(type, constant, emptyConstantArray);
            } else {
                int length = constantReflection.readArrayLength(constant);
                newImageHeapConstant = new ImageHeapArray(type, constant, length);
                ScanReason arrayReason = new ArrayScan(type, constant, reason);
                ImageHeapArray array = (ImageHeapArray) newImageHeapConstant;
                for (int idx = 0; idx < length; idx++) {
                    final JavaConstant rawElementValue = constantReflection.readArrayElement(constant, idx);
                    JavaConstant arrayElement = onArrayElementReachable(array, type, rawElementValue, idx, arrayReason, onAnalysisModified);
                    array.setElement(idx, arrayElement);
                }
            }
            markTypeInstantiated(type, reason);
        } else {
            /*
             * We need to have the new ImageHeapInstance early so that we can reference it in the
             * lambda when the field value gets reachable. But it must not be published to any other
             * thread before all instanceFieldValues are filled in.
             */
            /* We are about to query the type's fields, the type must be marked as reachable. */
            markTypeInstantiated(type, reason);
            AnalysisField[] instanceFields = type.getInstanceFields(true);
            newImageHeapConstant = new ImageHeapInstance(type, constant, instanceFields.length);
            for (AnalysisField field : instanceFields) {
                ValueSupplier<JavaConstant> rawFieldValue;
                try {
                    rawFieldValue = readHostedFieldValue(field, universe.toHosted(constant));
                } catch (InternalError | TypeNotPresentException | LinkageError e) {
                    /* Ignore missing type errors. */
                    continue;
                }
                ImageHeapInstance finalObject = (ImageHeapInstance) newImageHeapConstant;
                finalObject.setFieldTask(field, new AnalysisFuture<>(() -> {
                    ScanReason fieldReason = new FieldScan(field, constant, reason);
                    JavaConstant value = onFieldValueReachable(field, finalObject, rawFieldValue, fieldReason, onAnalysisModified);
                    finalObject.setFieldValue(field, value);
                    return value;
                }));
            }

            AnalysisType typeFromClassConstant = (AnalysisType) constantReflection.asJavaType(constant);
            if (typeFromClassConstant != null) {
                typeFromClassConstant.registerAsReachable(reason);
            }
        }

        /*
         * Following all the array elements and reachable field values can be done asynchronously.
         */
        postTask(() -> onObjectReachable(newImageHeapConstant, reason));
        return newImageHeapConstant;
    }

    private Optional<JavaConstant> maybeReplace(JavaConstant constant, ScanReason reason) {
        Object unwrapped = unwrapObject(constant);
        if (unwrapped == null) {
            throw GraalError.shouldNotReachHere(formatReason("Could not unwrap constant", reason));
        } else if (unwrapped instanceof ImageHeapConstant) {
            throw GraalError.shouldNotReachHere(formatReason("Double wrapping of constant. Most likely, the reachability analysis code itself is seen as reachable.", reason));
        }

        /* Run all registered object replacers. */
        if (constant.getJavaKind() == JavaKind.Object) {
            try {
                Object replaced = universe.replaceObject(unwrapped);
                if (replaced != unwrapped) {
                    JavaConstant replacedConstant = universe.getSnippetReflection().forObject(replaced);
                    return Optional.of(replacedConstant);
                }
            } catch (UnsupportedFeatureException e) {
                ObjectScanner.unsupportedFeatureDuringConstantScan(universe.getBigbang(), constant, e, reason);
                return Optional.of(JavaConstant.NULL_POINTER);
            }

        }
        return Optional.empty();
    }

    protected Object unwrapObject(JavaConstant constant) {
        return snippetReflection.asObject(Object.class, constant);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, ValueSupplier<JavaConstant> rawValue, ScanReason reason) {
        return onFieldValueReachable(field, null, rawValue, reason, null);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        return onFieldValueReachable(field, null, ValueSupplier.eagerValue(fieldValue), reason, onAnalysisModified);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, ImageHeapInstance receiver, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        return onFieldValueReachable(field, receiver, ValueSupplier.eagerValue(fieldValue), reason, onAnalysisModified);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, ImageHeapInstance receiver, ValueSupplier<JavaConstant> rawValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        AnalysisError.guarantee(field.isReachable(), "Field value is only reachable when field is reachable " + field.format("%H.%n"));

        /*
         * Check if the field value is available. If not, trying to access it is an error. This
         * forces the callers to only trigger the execution of the future task when the value is
         * ready to be materialized.
         */
        AnalysisError.guarantee(rawValue.isAvailable(), "Value not yet available for " + field.format("%H.%n"));

        JavaConstant transformedValue;
        try {
            transformedValue = transformFieldValue(field, receiver, rawValue.get());
        } catch (UnsupportedFeatureException e) {
            ObjectScanner.unsupportedFeatureDuringFieldScan(universe.getBigbang(), field, receiver, e, reason);
            transformedValue = JavaConstant.NULL_POINTER;
        }
        /* Add the transformed value to the image heap. */
        JavaConstant fieldValue = markConstantReachable(transformedValue, reason, onAnalysisModified);

        if (scanningObserver != null) {
            /* Notify the points-to analysis of the scan. */
            boolean analysisModified = notifyAnalysis(field, receiver, fieldValue, reason);
            if (analysisModified && onAnalysisModified != null) {
                onAnalysisModified.accept(reason);
            }
        }
        return fieldValue;
    }

    private boolean notifyAnalysis(AnalysisField field, JavaConstant receiver, JavaConstant fieldValue, ScanReason reason) {
        boolean analysisModified = false;
        if (fieldValue.getJavaKind() == JavaKind.Object && hostVM.isRelocatedPointer(metaAccess, fieldValue)) {
            analysisModified = scanningObserver.forRelocatedPointerFieldValue(receiver, field, fieldValue, reason);
        } else if (fieldValue.isNull()) {
            analysisModified = scanningObserver.forNullFieldValue(receiver, field, reason);
        } else if (fieldValue.getJavaKind() == JavaKind.Object) {
            analysisModified = scanningObserver.forNonNullFieldValue(receiver, field, fieldValue, reason);
        }
        return analysisModified;
    }

    @SuppressWarnings("unused")
    protected JavaConstant transformFieldValue(AnalysisField field, JavaConstant receiverConstant, JavaConstant originalValueConstant) {
        return originalValueConstant;
    }

    protected JavaConstant onArrayElementReachable(ImageHeapArray array, AnalysisType arrayType, JavaConstant rawElementValue, int elementIndex, ScanReason reason,
                    Consumer<ScanReason> onAnalysisModified) {
        JavaConstant elementValue = markConstantReachable(rawElementValue, reason, onAnalysisModified);
        if (scanningObserver != null && arrayType.getComponentType().getJavaKind() == JavaKind.Object) {
            /* Notify the points-to analysis of the scan. */
            boolean analysisModified = notifyAnalysis(array, arrayType, elementValue, elementIndex, reason);
            if (analysisModified && onAnalysisModified != null) {
                onAnalysisModified.accept(reason);
            }
        }
        return elementValue;
    }

    private boolean isNonNullObjectConstant(JavaConstant constant) {
        return constant.getJavaKind() == JavaKind.Object && constant.isNonNull() && !isWordType(constant);
    }

    private boolean isWordType(JavaConstant rawElementValue) {
        return metaAccess.isInstanceOf(rawElementValue, WordBase.class);
    }

    private boolean notifyAnalysis(JavaConstant array, AnalysisType arrayType, JavaConstant elementValue, int elementIndex, ScanReason reason) {
        boolean analysisModified;
        if (elementValue.isNull()) {
            analysisModified = scanningObserver.forNullArrayElement(array, arrayType, elementIndex, reason);
        } else {
            if (isWordType(elementValue)) {
                return false;
            }
            AnalysisType elementType = metaAccess.lookupJavaType(elementValue);
            analysisModified = scanningObserver.forNonNullArrayElement(array, arrayType, elementValue, elementType, elementIndex, reason);
        }
        return analysisModified;
    }

    protected void onObjectReachable(ImageHeapConstant imageHeapConstant, ScanReason reason) {
        AnalysisType objectType = metaAccess.lookupJavaType(imageHeapConstant);
        imageHeap.add(objectType, imageHeapConstant);

        markTypeInstantiated(objectType, reason);

        if (imageHeapConstant instanceof ImageHeapInstance) {
            ImageHeapInstance imageHeapInstance = (ImageHeapInstance) imageHeapConstant;
            for (AnalysisField field : objectType.getInstanceFields(true)) {
                if (field.isRead() && isValueAvailable(field)) {
                    snapshotFieldValue(field, imageHeapInstance.getFieldValue(field));
                }
            }
        }
    }

    public boolean isValueAvailable(@SuppressWarnings("unused") AnalysisField field) {
        return true;
    }

    /**
     * Trigger execution of the field task, if not yet executed. Note that if the task is already
     * executed there is nothing to do since on execution the task replaces itself with its result
     * in the corresponding map.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void snapshotFieldValue(AnalysisField field, Object fieldTask) {
        if (fieldTask instanceof AnalysisFuture<?>) {
            AnalysisError.guarantee(field.isReachable(), "Field value snapshot computed for field not reachable " + field.format("%H.%n"));
            postTask((AnalysisFuture<JavaConstant>) fieldTask);
        }
    }

    protected String formatReason(String message, ScanReason reason) {
        return message + ' ' + reason;
    }

    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver) {
        // Wrap the hosted constant into a substrate constant
        JavaConstant value = universe.lookup(hostedConstantReflection.readFieldValue(field.wrapped, receiver));
        return ValueSupplier.eagerValue(value);
    }

    protected boolean skipScanning() {
        return false;
    }

    /**
     * When a re-scanning is triggered while the analysis is running in parallel, it is necessary to
     * do the re-scanning in a separate executor task to avoid deadlocks. For example,
     * lookupJavaField might need to wait for the reachability handler to be finished that actually
     * triggered the re-scanning.
     *
     * In the (legacy) Feature.duringAnalysis state, the executor is not running and we must not
     * schedule new tasks, because that would be treated as "the analysis has not finsihed yet". So
     * in that case we execute the task directly.
     */
    private void maybeRunInExecutor(CompletionExecutor.DebugContextRunnable task) {
        if (bb.executorIsStarted()) {
            bb.postTask(task);
        } else {
            task.run(null);
        }
    }

    public void rescanRoot(Field reflectionField) {
        if (skipScanning()) {
            return;
        }

        maybeRunInExecutor(unused -> {
            AnalysisType type = metaAccess.lookupJavaType(reflectionField.getDeclaringClass());
            if (type.isReachable()) {
                AnalysisField field = metaAccess.lookupJavaField(reflectionField);
                JavaConstant fieldValue = readHostedFieldValue(field, null).get();
                TypeData typeData = field.getDeclaringClass().getOrComputeData();
                AnalysisFuture<JavaConstant> fieldTask = patchStaticField(typeData, field, fieldValue, OtherReason.RESCAN, null);
                if (field.isRead() || field.isFolded()) {
                    rescanCollectionElements(fieldTask.ensureDone());
                }
            }
        });
    }

    public void rescanField(Object receiver, Field reflectionField) {
        if (skipScanning()) {
            return;
        }
        maybeRunInExecutor(unused -> {
            AnalysisType type = metaAccess.lookupJavaType(reflectionField.getDeclaringClass());
            if (type.isReachable()) {
                AnalysisField field = metaAccess.lookupJavaField(reflectionField);
                assert !field.isStatic();
                JavaConstant receiverConstant = asConstant(receiver);
                Optional<JavaConstant> replaced = maybeReplace(receiverConstant, OtherReason.RESCAN);
                if (replaced.isPresent()) {
                    if (replaced.get().isNull()) {
                        /* There was some problem during replacement, bailout. */
                        return;
                    }
                    receiverConstant = replaced.get();
                }
                JavaConstant fieldValue = readHostedFieldValue(field, universe.toHosted(receiverConstant)).get();
                if (fieldValue != null) {
                    ImageHeapInstance receiverObject = (ImageHeapInstance) toImageHeapObject(receiverConstant);
                    AnalysisFuture<JavaConstant> fieldTask = patchInstanceField(receiverObject, field, fieldValue, OtherReason.RESCAN, null);
                    if (field.isRead() || field.isFolded()) {
                        rescanCollectionElements(fieldTask.ensureDone());
                    }
                }
            }
        });
    }

    protected AnalysisFuture<JavaConstant> patchStaticField(TypeData typeData, AnalysisField field, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> {
            JavaConstant value = onFieldValueReachable(field, fieldValue, reason, onAnalysisModified);
            typeData.setFieldValue(field, value);
            return value;
        });
        typeData.setFieldTask(field, task);
        return task;
    }

    protected AnalysisFuture<JavaConstant> patchInstanceField(ImageHeapInstance receiverObject, AnalysisField field, JavaConstant fieldValue, ScanReason reason,
                    Consumer<ScanReason> onAnalysisModified) {
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> {
            JavaConstant value = onFieldValueReachable(field, receiverObject, fieldValue, reason, onAnalysisModified);
            receiverObject.setFieldValue(field, value);
            return value;
        });
        receiverObject.setFieldTask(field, task);
        return task;
    }

    /**
     * Add the object to the image heap and, if the object is a collection, rescan its elements.
     */
    public void rescanObject(Object object) {
        rescanObject(object, OtherReason.RESCAN);
    }

    /**
     * Add the object to the image heap.
     */
    public void rescanObject(Object object, ScanReason reason) {
        if (skipScanning()) {
            return;
        }
        if (object == null) {
            return;
        }

        maybeRunInExecutor(unused -> {
            doScan(asConstant(object), reason);
            rescanCollectionElements(object);
        });
    }

    private void rescanCollectionElements(JavaConstant constant) {
        if (isNonNullObjectConstant(constant)) {
            rescanCollectionElements(asObject(((ImageHeapConstant) constant).getHostedObject()));
        }
    }

    private void rescanCollectionElements(Object object) {
        if (object instanceof Object[]) {
            Object[] array = (Object[]) object;
            for (Object element : array) {
                doScan(asConstant(element));
            }
        } else if (object instanceof Collection) {
            Collection<?> collection = (Collection<?>) object;
            collection.forEach(e -> doScan(asConstant(e)));
        } else if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            map.forEach((k, v) -> {
                doScan(asConstant(k));
                doScan(asConstant(v));
            });
        } else if (object instanceof EconomicMap) {
            rescanEconomicMap((EconomicMap<?, ?>) object);
        }
    }

    protected void rescanEconomicMap(EconomicMap<?, ?> object) {
        MapCursor<?, ?> cursor = object.getEntries();
        while (cursor.advance()) {
            doScan(asConstant(cursor.getKey()));
            doScan(asConstant(cursor.getValue()));
        }
    }

    void doScan(JavaConstant constant) {
        doScan(constant, OtherReason.RESCAN);
    }

    void doScan(JavaConstant constant, ScanReason reason) {
        markConstantReachable(constant, reason, null);
    }

    protected Object asObject(JavaConstant constant) {
        return snippetReflection.asObject(Object.class, constant);
    }

    public JavaConstant asConstant(Object object) {
        return snippetReflection.forObject(object);
    }

    public void cleanupAfterAnalysis() {
        scanningObserver = null;
    }

    public ObjectScanningObserver getScanningObserver() {
        return scanningObserver;
    }

    protected abstract Class<?> getClass(String className);

    protected AnalysisType lookupJavaType(String className) {
        return metaAccess.lookupJavaType(getClass(className));
    }

    protected AnalysisField lookupJavaField(String className, String fieldName) {
        return metaAccess.lookupJavaField(ReflectionUtil.lookupField(getClass(className), fieldName));
    }

    public void postTask(AnalysisFuture<?> future) {
        if (future.isDone()) {
            return;
        }
        universe.getBigbang().postTask(debug -> future.ensureDone());
    }

    public void postTask(Runnable task) {
        universe.getBigbang().postTask(debug -> task.run());
    }
}
