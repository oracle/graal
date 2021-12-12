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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ArrayScan;
import com.oracle.graal.pointsto.ObjectScanner.EmbeddedRootScan;
import com.oracle.graal.pointsto.ObjectScanner.FieldScan;
import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapObject;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Scanning is triggered when:
 * <ul>
 * <li>a static final field is marked as accessed, or</li>s
 * <li>a method that is parsed and embedded roots are discovered</li>
 * </ul>
 * <p>
 * When an instance field is marked as accessed the objects of its declaring type (and all the
 * subtypes) are re-scanned.
 */
public abstract class ImageHeapScanner {
    public static class Options {
        @Option(help = "Enable manual rescanning of image heap objects.")//
        public static final OptionKey<Boolean> EnableManualRescan = new OptionKey<>(true);
    }

    protected final ImageHeap imageHeap;
    protected final AnalysisMetaAccess metaAccess;
    protected final AnalysisUniverse universe;
    protected final HostVM hostVM;

    protected final SnippetReflectionProvider snippetReflection;
    protected final ConstantReflectionProvider constantReflection;
    protected final ConstantReflectionProvider hostedConstantReflection;
    protected final SnippetReflectionProvider hostedSnippetReflection;

    protected ObjectScanningObserver scanningObserver;
    private final boolean enableManualRescan;

    public ImageHeapScanner(ImageHeap heap, AnalysisMetaAccess aMetaAccess, SnippetReflectionProvider aSnippetReflection,
                    ConstantReflectionProvider aConstantReflection, ObjectScanningObserver aScanningObserver) {
        imageHeap = heap;
        metaAccess = aMetaAccess;
        universe = aMetaAccess.getUniverse();
        hostVM = aMetaAccess.getUniverse().hostVM();
        snippetReflection = aSnippetReflection;
        constantReflection = aConstantReflection;
        scanningObserver = aScanningObserver;
        hostedConstantReflection = GraalAccess.getOriginalProviders().getConstantReflection();
        hostedSnippetReflection = GraalAccess.getOriginalProviders().getSnippetReflection();
        enableManualRescan = Options.EnableManualRescan.getValue(hostVM.options());
    }

    public void scanEmbeddedRoot(JavaConstant root, BytecodePosition position) {
        AnalysisType type = metaAccess.lookupJavaType(root);
        type.registerAsReachable();
        getOrCreateConstantReachableTask(root, new EmbeddedRootScan(position, root)).ensureDone();
    }

    @SuppressWarnings("unused")
    public void scanFieldValue(AnalysisField field, JavaConstant receiver) {
        assert field.isReachable() && isValueAvailable(field);
        if (field.isStatic()) {
            postTask(() -> field.getDeclaringClass().getOrComputeData().readField(field));
        } else {
            postTask(() -> ((ImageHeapInstance) toImageHeapObject(receiver)).readField(field));
        }
    }

    public void onFieldRead(AnalysisField field) {
        assert field.isRead();
        /* Check if the value is available before accessing it. */
        if (isValueAvailable(field)) {
            AnalysisType declaringClass = field.getDeclaringClass();
            if (field.isStatic()) {
                postTask(() -> declaringClass.getOrComputeData().readField(field));
            } else {
                /* Trigger field scanning for the already processed objects. */
                postTask(() -> onInstanceFieldRead(field, declaringClass));
            }
        }
    }

    private void onInstanceFieldRead(AnalysisField field, AnalysisType type) {
        for (AnalysisType subtype : type.getSubTypes()) {
            for (ImageHeapObject imageHeapObject : imageHeap.getObjects(subtype)) {
                postTask(((ImageHeapInstance) imageHeapObject).getFieldTask(field));
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

        /* Decide if the type should be initialized at build time or at run time: */
        boolean initializeAtRunTime = shouldInitializeAtRunTime(type);

        /*
         * Snapshot all static fields. This reads the raw field value of all fields regardless of
         * reachability status. The field value is processed when a field is marked as reachable, in
         * onFieldValueReachable().
         */
        Map<ResolvedJavaField, AnalysisFuture<JavaConstant>> rawStaticFieldValues = new HashMap<>();
        for (AnalysisField field : type.getStaticFields()) {
            ValueSupplier<JavaConstant> rawFieldValue = readHostedFieldValue(field, null);
            rawStaticFieldValues.put(field, new AnalysisFuture<>(() -> onFieldValueReachable(field, null, rawFieldValue, new FieldScan(field))));
        }

        return new TypeData(initializeAtRunTime, rawStaticFieldValues);
    }

    protected boolean shouldInitializeAtRunTime(@SuppressWarnings("unused") AnalysisType type) {
        return false;
    }

    void markTypeInstantiated(AnalysisType type) {
        if (universe.sealed() && !type.isReachable()) {
            throw AnalysisError.shouldNotReachHere("Universe is sealed. New type reachable: " + type.toJavaName());
        }
        type.registerAsInHeap();
    }

    AnalysisFuture<ImageHeapObject> markConstantReachable(Constant constant, ScanReason reason) {
        if (!(constant instanceof JavaConstant)) {
            /*
             * The bytecode parser sometimes embeds low-level VM constants for types into the
             * high-level graph. Since these constants are the result of type lookups, these types
             * are already marked as reachable. Eventually, the bytecode parser should be changed to
             * only use JavaConstant.
             */
            return null;
        }

        JavaConstant javaConstant = (JavaConstant) constant;
        if (javaConstant.getJavaKind() == JavaKind.Object && javaConstant.isNonNull()) {
            if (!hostVM.platformSupported(asObject(javaConstant).getClass())) {
                return null;
            }
            return getOrCreateConstantReachableTask(javaConstant, reason);
        }

        return null;
    }

    public ImageHeapObject toImageHeapObject(JavaConstant constant) {
        return toImageHeapObject(constant, OtherReason.SCAN);
    }

    public ImageHeapObject toImageHeapObject(JavaConstant constant, ScanReason reason) {
        assert constant != null && constant.getJavaKind() == JavaKind.Object && constant.isNonNull();
        return getOrCreateConstantReachableTask(constant, reason).ensureDone();
    }

    public void scan(JavaConstant javaConstant, ScanReason reason) {
        assert javaConstant.getJavaKind() == JavaKind.Object && javaConstant.isNonNull();
        postTask(getOrCreateConstantReachableTask(javaConstant, reason));
    }

    protected AnalysisFuture<ImageHeapObject> getOrCreateConstantReachableTask(JavaConstant javaConstant, ScanReason reason) {
        ScanReason nonNullReason = Objects.requireNonNull(reason);
        AnalysisFuture<ImageHeapObject> existingTask = imageHeap.heapObjects.get(javaConstant);
        if (existingTask == null) {
            /*
             * TODO - this still true? is it ok to seal the heap?
             *
             * The constants can be generated at any stage, not only before/during analysis. For
             * example `com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.Templates.
             * getProfilingData()` generates `AllocationProfilingData` during lowering.
             */
            if (universe.sealed()) {
                throw AnalysisError.shouldNotReachHere("Universe is sealed. New constant reachable: " + javaConstant.toValueString());
            }
            AnalysisFuture<ImageHeapObject> newTask = new AnalysisFuture<>(() -> createImageHeapObject(javaConstant, nonNullReason));
            existingTask = imageHeap.heapObjects.putIfAbsent(javaConstant, newTask);
            if (existingTask == null) {
                /*
                 * Immediately schedule the new task. There is no need to have not-yet-reachable
                 * ImageHeapObject.
                 */
                postTask(newTask);
                return newTask;
            }
        }
        return existingTask;
    }

    protected ImageHeapObject createImageHeapObject(JavaConstant constant, ScanReason reason) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull();

        Optional<JavaConstant> replaced = maybeReplace(constant, reason);
        if (replaced.isPresent()) {
            /*
             * This ensures that we have a unique ImageHeapObject for the original and replaced
             * object. As a side effect, this runs all object transformer again on the replaced
             * constant.
             */
            return toImageHeapObject(replaced.get(), reason);
        }

        if (!hostVM.platformSupported(asObject(constant).getClass())) {
            return null;
        }

        /*
         * Access the constant type after the replacement. Some constants may have types that should
         * not be reachable at run time and thus are replaced.
         */
        AnalysisType type = metaAccess.lookupJavaType(constant);

        ImageHeapObject newImageHeapObject;
        if (type.isArray()) {
            int length = constantReflection.readArrayLength(constant);
            JavaConstant[] arrayElements = new JavaConstant[length];
            ScanReason arrayReason = new ArrayScan(type, constant, reason);
            for (int idx = 0; idx < length; idx++) {
                final JavaConstant rawElementValue = constantReflection.readArrayElement(constant, idx);
                arrayElements[idx] = onArrayElementReachable(constant, type, rawElementValue, idx, arrayReason);
            }
            newImageHeapObject = new ImageHeapArray(constant, type, arrayElements);
            markTypeInstantiated(type);
        } else {
            Map<ResolvedJavaField, AnalysisFuture<JavaConstant>> instanceFieldValues = new HashMap<>();
            /*
             * We need to have the new ImageHeapInstance early so that we can reference it in the
             * lambda when the field value gets reachable. But it must not be published to any other
             * thread before all instanceFieldValues are filled in.
             */
            newImageHeapObject = new ImageHeapInstance(constant, type, instanceFieldValues);
            /* We are about to query the type's fields, the type must be marked as reachable. */
            markTypeInstantiated(type);
            for (AnalysisField field : type.getInstanceFields(true)) {
                ScanReason fieldReason = new FieldScan(field, constant, reason);
                ValueSupplier<JavaConstant> rawFieldValue;
                try {
                    rawFieldValue = readHostedFieldValue(field, universe.toHosted(constant));
                } catch (InternalError | TypeNotPresentException | LinkageError e) {
                    /* Ignore missing type errors. */
                    continue;
                }
                instanceFieldValues.put(field, new AnalysisFuture<>(() -> onFieldValueReachable(field, constant, rawFieldValue, fieldReason)));
            }
        }

        /*
         * Following all the array elements and reachable field values can be done asynchronously.
         */
        postTask(() -> onObjectReachable(newImageHeapObject));
        return newImageHeapObject;
    }

    private Optional<JavaConstant> maybeReplace(JavaConstant constant, ScanReason reason) {
        Object unwrapped = unwrapObject(constant);
        if (unwrapped == null) {
            throw GraalError.shouldNotReachHere(formatReason("Could not unwrap constant", reason));
        } else if (unwrapped instanceof ImageHeapObject) {
            throw GraalError.shouldNotReachHere(formatReason("Double wrapping of constant. Most likely, the reachability analysis code itself is seen as reachable.", reason));
        }

        /* Run all registered object replacers. */
        if (constant.getJavaKind() == JavaKind.Object) {
            Object replaced = universe.replaceObject(unwrapped);
            if (replaced != unwrapped) {
                JavaConstant replacedConstant = universe.getSnippetReflection().forObject(replaced);
                return Optional.of(replacedConstant);
            }
        }
        return Optional.empty();
    }

    protected Object unwrapObject(JavaConstant constant) {
        return snippetReflection.asObject(Object.class, constant);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, JavaConstant receiver, JavaConstant fieldValue, ScanReason reason) {
        return onFieldValueReachable(field, receiver, ValueSupplier.eagerValue(fieldValue), reason);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, JavaConstant receiver, ValueSupplier<JavaConstant> rawValue, ScanReason reason) {
        AnalysisError.guarantee(field.isReachable(), "Field value is only reachable when field is reachable " + field.format("%H.%n"));

        /*
         * Check if the field value is available. If not, trying to access it is an error. This
         * forces the callers to only trigger the execution of the future task when the value is
         * ready to be materialized.
         */
        AnalysisError.guarantee(rawValue.isAvailable(), "Value not yet available for " + field.format("%H.%n"));

        /* Attempting to materialize the value before it is available may result in an error. */
        // TODO why run the transformer here, it is run by markConstantReachable anyway
        JavaConstant transformedValue = transformFieldValue(field, receiver, rawValue.get());

        if (scanningObserver != null) {
            if (transformedValue.getJavaKind() == JavaKind.Object && hostVM.isRelocatedPointer(asObject(transformedValue))) {
                scanningObserver.forRelocatedPointerFieldValue(receiver, field, transformedValue, reason);
            } else if (transformedValue.isNull()) {
                scanningObserver.forNullFieldValue(receiver, field, reason);
            } else {
                // TODO this adds the transformedValue in the heap, should we do this here?
                // This will also run the replacer again; transformFieldValue already run the
                // replacer
                AnalysisFuture<ImageHeapObject> objectFuture = markConstantReachable(transformedValue, reason);
                /* Notify the points-to analysis of the scan. */
                if (objectFuture != null) {
                    /* Add the transformed value to the image heap. */
                    scanningObserver.forNonNullFieldValue(receiver, field, objectFuture.ensureDone().object, reason);
                }
            }
        }
        /* Return the transformed value, but NOT the image heap object. */
        return transformedValue;
    }

    @SuppressWarnings("unused")
    protected JavaConstant transformFieldValue(AnalysisField field, JavaConstant receiverConstant, JavaConstant originalValueConstant) {
        return originalValueConstant;
    }

    protected JavaConstant onArrayElementReachable(JavaConstant array, AnalysisType arrayType, JavaConstant rawElementValue, int elementIndex, ScanReason reason) {
        AnalysisFuture<ImageHeapObject> objectFuture = markConstantReachable(rawElementValue, reason);
        if (scanningObserver != null && arrayType.getComponentType().getJavaKind() == JavaKind.Object) {
            if (objectFuture == null) {
                scanningObserver.forNullArrayElement(array, arrayType, elementIndex, reason);
            } else {
                ImageHeapObject element = objectFuture.ensureDone();
                AnalysisType elementType = constantType(element.object);
                markTypeInstantiated(elementType);
                /* Process the array element. */
                scanningObserver.forNonNullArrayElement(array, arrayType, element.object, elementType, elementIndex, reason);
                return element.object;
            }
        }
        return null;
    }

    void onObjectReachable(ImageHeapObject imageHeapObject) {
        imageHeap.add(imageHeapObject.type, imageHeapObject);

        markTypeInstantiated(imageHeapObject.type);

        if (imageHeapObject instanceof ImageHeapInstance) {
            ImageHeapInstance imageHeapInstance = (ImageHeapInstance) imageHeapObject;
            for (AnalysisField field : imageHeapObject.type.getInstanceFields(true)) {
                if (field.isReachable() && field.isRead() && isValueAvailable(field)) {
                    postTask(imageHeapInstance.getFieldTask(field));
                }
            }
        }
    }

    public boolean isValueAvailable(@SuppressWarnings("unused") AnalysisField field) {
        return true;
    }

    protected String formatReason(String message, ScanReason reason) {
        return message + ' ' + reason;
    }

    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver) {
        assert !field.isStatic() || !shouldInitializeAtRunTime(field.getDeclaringClass());
        // Wrap the hosted constant into a substrate constant
        JavaConstant value = universe.lookup(hostedConstantReflection.readFieldValue(field.wrapped, receiver));
        return ValueSupplier.eagerValue(value);
    }

    protected boolean skipScanning() {
        return false;
    }

    public Object rescanRoot(Field reflectionField) {
        if (!enableManualRescan) {
            return null;
        }
        if (skipScanning()) {
            return null;
        }
        AnalysisType type = metaAccess.lookupJavaType(reflectionField.getDeclaringClass());
        if (type.isReachable()) {
            AnalysisField field = metaAccess.lookupJavaField(reflectionField);
            JavaConstant fieldValue = readHostedFieldValue(field, null).get();
            TypeData typeData = field.getDeclaringClass().getOrComputeData();
            AnalysisFuture<JavaConstant> fieldTask = patchStaticField(typeData, field, fieldValue, OtherReason.RESCAN);
            if (field.isRead()) {
                Object root = asObject(fieldTask.ensureDone());
                rescanCollectionElements(root);
                return root;
            }
        }
        return null;
    }

    public void rescanField(Object receiver, Field reflectionField) {
        if (!enableManualRescan) {
            return;
        }
        if (skipScanning()) {
            return;
        }
        AnalysisType type = metaAccess.lookupJavaType(reflectionField.getDeclaringClass());
        if (type.isReachable()) {
            AnalysisField field = metaAccess.lookupJavaField(reflectionField);
            assert !field.isStatic();
            JavaConstant receiverConstant = asConstant(receiver);
            Optional<JavaConstant> replaced = maybeReplace(receiverConstant, OtherReason.RESCAN);
            if (replaced.isPresent()) {
                receiverConstant = replaced.get();
            }
            JavaConstant fieldValue = readHostedFieldValue(field, universe.toHosted(receiverConstant)).get();
            if (fieldValue != null) {
                ImageHeapInstance receiverObject = (ImageHeapInstance) toImageHeapObject(receiverConstant);
                AnalysisFuture<JavaConstant> fieldTask = patchInstanceField(receiverObject, field, fieldValue, OtherReason.RESCAN);
                if (field.isRead()) {
                    rescanCollectionElements(asObject(fieldTask.ensureDone()));
                }
            }
        }
    }

    protected AnalysisFuture<JavaConstant> patchStaticField(TypeData typeData, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> onFieldValueReachable(field, null, fieldValue, reason));
        typeData.setFieldTask(field, task);
        return task;
    }

    protected AnalysisFuture<JavaConstant> patchInstanceField(ImageHeapInstance receiverObject, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> onFieldValueReachable(field, receiverObject.object, fieldValue, reason));
        receiverObject.setFieldTask(field, task);
        return task;
    }

    /** Trigger rescanning of constants. */
    public void rescanObject(Object object) {
        if (!enableManualRescan) {
            return;
        }
        if (skipScanning()) {
            return;
        }
        if (object == null) {
            return;
        }
        doScan(asConstant(object));
        rescanCollectionElements(object);
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
        if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
            getOrCreateConstantReachableTask(constant, OtherReason.SCAN);
        }
    }

    public void scanHub(AnalysisType type) {
        /* Initialize dynamic hub metadata before scanning it. */
        universe.onTypeScanned(type);
        metaAccess.lookupJavaType(java.lang.Class.class).registerAsReachable();
        /* We scan the original class here, the scanner does the replacement to DynamicHub. */
        getOrCreateConstantReachableTask(asConstant(type.getJavaClass()), ObjectScanner.OtherReason.HUB);
    }

    protected AnalysisType analysisType(Object constant) {
        return metaAccess.lookupJavaType(constant.getClass());
    }

    protected AnalysisType constantType(JavaConstant constant) {
        return metaAccess.lookupJavaType(constant);
    }

    protected Object asObject(JavaConstant constant) {
        return snippetReflection.asObject(Object.class, constant);
    }

    public JavaConstant asConstant(Object object) {
        return snippetReflection.forObject(object);
    }

    public void cleanupAfterAnalysis() {
        scanningObserver = null;
        imageHeap.heapObjects = null;
        imageHeap.typesToObjects = null;
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
        ((PointsToAnalysis) universe.getBigbang()).postTask(debug -> future.ensureDone());
    }

    public void postTask(Runnable task) {
        ((PointsToAnalysis) universe.getBigbang()).postTask(debug -> task.run());
    }
}
