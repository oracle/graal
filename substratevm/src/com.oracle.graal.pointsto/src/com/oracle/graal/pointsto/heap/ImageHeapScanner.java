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
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.ObjectScanner.ArrayScan;
import com.oracle.graal.pointsto.ObjectScanner.EmbeddedRootScan;
import com.oracle.graal.pointsto.ObjectScanner.FieldScan;
import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
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
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

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

    protected final ImageHeap imageHeap;
    protected final AnalysisMetaAccess metaAccess;
    protected final AnalysisUniverse universe;
    protected final HostVM hostVM;

    protected final SnippetReflectionProvider snippetReflection;
    protected final ConstantReflectionProvider constantReflection;
    protected final ConstantReflectionProvider hostedConstantReflection;
    protected final SnippetReflectionProvider hostedSnippetReflection;

    protected ObjectScanningObserver scanningObserver;

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
    }

    public void scanEmbeddedRoot(JavaConstant root, BytecodePosition position) {
        if (isNonNullObjectConstant(root)) {
            AnalysisType type = metaAccess.lookupJavaType(root);
            type.registerAsReachable();
            getOrCreateConstantReachableTask(root, new EmbeddedRootScan(position, root), null).ensureDone();
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

        /*
         * Snapshot all static fields. This reads the raw field value of all fields regardless of
         * reachability status. The field value is processed when a field is marked as reachable, in
         * onFieldValueReachable().
         */
        Map<ResolvedJavaField, AnalysisFuture<JavaConstant>> rawStaticFieldValues = new HashMap<>();
        for (AnalysisField field : type.getStaticFields()) {
            ValueSupplier<JavaConstant> rawFieldValue = readHostedFieldValue(field, null);
            rawStaticFieldValues.put(field, new AnalysisFuture<>(() -> onFieldValueReachable(field, rawFieldValue, new FieldScan(field))));
        }

        return new TypeData(rawStaticFieldValues);
    }

    void markTypeInstantiated(AnalysisType type) {
        if (universe.sealed() && !type.isReachable()) {
            throw AnalysisError.shouldNotReachHere("Universe is sealed. New type reachable: " + type.toJavaName());
        }
        type.registerAsInHeap();
    }

    JavaConstant markConstantReachable(JavaConstant constant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        if (isNonNullObjectConstant(constant)) {
            return getOrCreateConstantReachableTask(constant, reason, onAnalysisModified).ensureDone().getObject();
        }

        return constant;
    }

    protected ImageHeapObject toImageHeapObject(JavaConstant constant) {
        return toImageHeapObject(constant, OtherReason.RESCAN, null);
    }

    protected ImageHeapObject toImageHeapObject(JavaConstant constant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        assert constant != null && isNonNullObjectConstant(constant);
        return getOrCreateConstantReachableTask(constant, reason, onAnalysisModified).ensureDone();
    }

    protected AnalysisFuture<ImageHeapObject> getOrCreateConstantReachableTask(JavaConstant javaConstant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        ScanReason nonNullReason = Objects.requireNonNull(reason);
        AnalysisFuture<ImageHeapObject> existingTask = imageHeap.getTask(javaConstant);
        if (existingTask == null) {
            if (universe.sealed()) {
                throw AnalysisError.shouldNotReachHere("Universe is sealed. New constant reachable: " + javaConstant.toValueString());
            }
            AnalysisFuture<ImageHeapObject> newTask = new AnalysisFuture<>(() -> createImageHeapObject(javaConstant, nonNullReason, onAnalysisModified));
            existingTask = imageHeap.addTask(javaConstant, newTask);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ImageHeapObject createImageHeapObject(JavaConstant constant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull();

        Optional<JavaConstant> replaced = maybeReplace(constant, reason);
        if (replaced.isPresent()) {
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

        ImageHeapObject newImageHeapObject;
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive()) {
                /*
                 * The shadow heap is only used for points-to analysis currently, we don't need to
                 * track individual elements for primitive arrays.
                 */
                newImageHeapObject = new ImageHeapArray(constant, emptyConstantArray);
            } else {
                int length = constantReflection.readArrayLength(constant);
                JavaConstant[] arrayElements = new JavaConstant[length];
                ScanReason arrayReason = new ArrayScan(type, constant, reason);
                for (int idx = 0; idx < length; idx++) {
                    final JavaConstant rawElementValue = constantReflection.readArrayElement(constant, idx);
                    arrayElements[idx] = onArrayElementReachable(constant, type, rawElementValue, idx, arrayReason, onAnalysisModified);
                }
                newImageHeapObject = new ImageHeapArray(constant, arrayElements);
            }
            markTypeInstantiated(type);
        } else {
            /*
             * We need to have the new ImageHeapInstance early so that we can reference it in the
             * lambda when the field value gets reachable. But it must not be published to any other
             * thread before all instanceFieldValues are filled in.
             */
            /* We are about to query the type's fields, the type must be marked as reachable. */
            markTypeInstantiated(type);
            AnalysisField[] instanceFields = type.getInstanceFields(true);
            AnalysisFuture<JavaConstant>[] instanceFieldValues = new AnalysisFuture[instanceFields.length];
            for (AnalysisField field : instanceFields) {
                ScanReason fieldReason = new FieldScan(field, constant, reason);
                ValueSupplier<JavaConstant> rawFieldValue;
                try {
                    rawFieldValue = readHostedFieldValue(field, universe.toHosted(constant));
                } catch (InternalError | TypeNotPresentException | LinkageError e) {
                    /* Ignore missing type errors. */
                    continue;
                }
                instanceFieldValues[field.getPosition()] = new AnalysisFuture<>(() -> onFieldValueReachable(field, constant, rawFieldValue, fieldReason, onAnalysisModified));
            }
            newImageHeapObject = new ImageHeapInstance(constant, instanceFieldValues);
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

    JavaConstant onFieldValueReachable(AnalysisField field, ValueSupplier<JavaConstant> rawValue, ScanReason reason) {
        return onFieldValueReachable(field, null, rawValue, reason, null);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        return onFieldValueReachable(field, null, ValueSupplier.eagerValue(fieldValue), reason, onAnalysisModified);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, JavaConstant receiver, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        return onFieldValueReachable(field, receiver, ValueSupplier.eagerValue(fieldValue), reason, onAnalysisModified);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, JavaConstant receiver, ValueSupplier<JavaConstant> rawValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        AnalysisError.guarantee(field.isReachable(), "Field value is only reachable when field is reachable " + field.format("%H.%n"));

        /*
         * Check if the field value is available. If not, trying to access it is an error. This
         * forces the callers to only trigger the execution of the future task when the value is
         * ready to be materialized.
         */
        AnalysisError.guarantee(rawValue.isAvailable(), "Value not yet available for " + field.format("%H.%n"));

        JavaConstant transformedValue = transformFieldValue(field, receiver, rawValue.get());
        /* Add the transformed value to the image heap. */
        JavaConstant fieldValue = markConstantReachable(transformedValue, reason, onAnalysisModified);

        if (scanningObserver != null) {
            /* Notify the points-to analysis of the scan. */
            boolean analysisModified = notifyAnalysis(field, receiver, fieldValue, reason);
            if (analysisModified && onAnalysisModified != null) {
                onAnalysisModified.accept(reason);
            }
        }
        /* Return the transformed value, but NOT the image heap object. */
        return fieldValue;
    }

    private boolean notifyAnalysis(AnalysisField field, JavaConstant receiver, JavaConstant fieldValue, ScanReason reason) {
        boolean analysisModified = false;
        if (fieldValue.getJavaKind() == JavaKind.Object && hostVM.isRelocatedPointer(asObject(fieldValue))) {
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

    protected JavaConstant onArrayElementReachable(JavaConstant array, AnalysisType arrayType, JavaConstant rawElementValue, int elementIndex, ScanReason reason) {
        return onArrayElementReachable(array, arrayType, rawElementValue, elementIndex, reason, null);
    }

    protected JavaConstant onArrayElementReachable(JavaConstant array, AnalysisType arrayType, JavaConstant rawElementValue, int elementIndex, ScanReason reason,
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
        Object obj = snippetReflection.asObject(Object.class, rawElementValue);
        return obj instanceof WordBase;
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

    void onObjectReachable(ImageHeapObject imageHeapObject) {
        AnalysisType objectType = metaAccess.lookupJavaType(imageHeapObject.getObject());
        imageHeap.add(objectType, imageHeapObject);

        markTypeInstantiated(objectType);

        if (imageHeapObject instanceof ImageHeapInstance) {
            ImageHeapInstance imageHeapInstance = (ImageHeapInstance) imageHeapObject;
            for (AnalysisField field : objectType.getInstanceFields(true)) {
                if (field.isRead() && isValueAvailable(field)) {
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
        // Wrap the hosted constant into a substrate constant
        JavaConstant value = universe.lookup(hostedConstantReflection.readFieldValue(field.wrapped, receiver));
        return ValueSupplier.eagerValue(value);
    }

    protected boolean skipScanning() {
        return false;
    }

    public Object rescanRoot(Field reflectionField) {
        if (skipScanning()) {
            return null;
        }
        AnalysisType type = metaAccess.lookupJavaType(reflectionField.getDeclaringClass());
        if (type.isReachable()) {
            AnalysisField field = metaAccess.lookupJavaField(reflectionField);
            JavaConstant fieldValue = readHostedFieldValue(field, null).get();
            TypeData typeData = field.getDeclaringClass().getOrComputeData();
            AnalysisFuture<JavaConstant> fieldTask = patchStaticField(typeData, field, fieldValue, OtherReason.RESCAN, null);
            if (field.isRead() || field.isFolded()) {
                Object root = asObject(fieldTask.ensureDone());
                rescanCollectionElements(root);
                return root;
            }
        }
        return null;
    }

    public void rescanField(Object receiver, Field reflectionField) {
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
                AnalysisFuture<JavaConstant> fieldTask = patchInstanceField(receiverObject, field, fieldValue, OtherReason.RESCAN, null);
                if (field.isRead() || field.isFolded()) {
                    rescanCollectionElements(asObject(fieldTask.ensureDone()));
                }
            }
        }
    }

    protected AnalysisFuture<JavaConstant> patchStaticField(TypeData typeData, AnalysisField field, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> onFieldValueReachable(field, fieldValue, reason, onAnalysisModified));
        typeData.setFieldTask(field, task);
        return task;
    }

    protected AnalysisFuture<JavaConstant> patchInstanceField(ImageHeapInstance receiverObject, AnalysisField field, JavaConstant fieldValue, ScanReason reason,
                    Consumer<ScanReason> onAnalysisModified) {
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> onFieldValueReachable(field, receiverObject.getObject(), fieldValue, reason, onAnalysisModified));
        receiverObject.setFieldTask(field, task);
        return task;
    }

    /** Add the object to the image heap and, if the object is a collection, rescan its elements. */
    public void rescanObject(Object object) {
        rescanObject(object, OtherReason.RESCAN);
        rescanCollectionElements(object);
    }

    /** Add the object to the image heap. */
    public void rescanObject(Object object, ScanReason reason) {
        if (skipScanning()) {
            return;
        }
        if (object == null) {
            return;
        }
        doScan(asConstant(object), reason);
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
        if (isNonNullObjectConstant(constant)) {
            getOrCreateConstantReachableTask(constant, reason, null);
        }
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
