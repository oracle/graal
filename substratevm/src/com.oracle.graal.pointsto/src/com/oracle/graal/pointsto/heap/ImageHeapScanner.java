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
import org.graalvm.compiler.core.common.SuppressFBWarnings;
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
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
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
            EmbeddedRootScan reason = new EmbeddedRootScan(position, root);
            ImageHeapConstant value = getOrCreateImageHeapConstant(root, reason);
            markReachable(value, reason);
        }
    }

    public void onFieldRead(AnalysisField field) {
        assert field.isRead();
        /* Check if the value is available before accessing it. */
        FieldScan reason = new FieldScan(field);
        AnalysisType declaringClass = field.getDeclaringClass();
        if (field.isStatic()) {
            if (isValueAvailable(field)) {
                JavaConstant fieldValue = declaringClass.getOrComputeData().readFieldValue(field);
                markReachable(fieldValue, reason);
                notifyAnalysis(field, null, fieldValue, reason);
            } else if (field.canBeNull()) {
                notifyAnalysis(field, null, JavaConstant.NULL_POINTER, reason);
            }
        } else {
            /* Trigger field scanning for the already processed objects. */
            postTask(() -> onInstanceFieldRead(field, declaringClass, reason));
        }
    }

    private void onInstanceFieldRead(AnalysisField field, AnalysisType type, FieldScan reason) {
        for (AnalysisType subtype : type.getSubTypes()) {
            for (ImageHeapConstant imageHeapConstant : imageHeap.getReachableObjects(subtype)) {
                ImageHeapInstance imageHeapInstance = (ImageHeapInstance) imageHeapConstant;
                updateInstanceField(field, imageHeapInstance, reason, null);
            }
            /* Subtypes include this type itself. */
            if (!subtype.equals(type)) {
                onInstanceFieldRead(field, subtype, reason);
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
        ResolvedJavaField[] staticFields = type.getStaticFields();
        TypeData data = new TypeData(staticFields.length);
        for (ResolvedJavaField javaField : staticFields) {
            AnalysisField field = (AnalysisField) javaField;
            ValueSupplier<JavaConstant> rawFieldValue = readHostedFieldValue(field, null);
            data.setFieldTask(field, new AnalysisFuture<>(() -> {
                JavaConstant value = createFieldValue(field, rawFieldValue, new FieldScan(field));
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

    public JavaConstant createImageHeapConstant(JavaConstant constant, ScanReason reason) {
        if (isNonNullObjectConstant(constant)) {
            return getOrCreateImageHeapConstant(constant, reason);
        }
        return constant;
    }

    public ImageHeapConstant toImageHeapObject(JavaConstant constant, ScanReason reason) {
        assert constant != null && isNonNullObjectConstant(constant);
        return markReachable(getOrCreateImageHeapConstant(constant, reason), reason, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ImageHeapConstant getOrCreateImageHeapConstant(JavaConstant javaConstant, ScanReason reason) {
        ScanReason nonNullReason = Objects.requireNonNull(reason);
        Object existingTask = imageHeap.getSnapshot(javaConstant);
        if (existingTask == null) {
            if (universe.sealed()) {
                throw AnalysisError.shouldNotReachHere("Universe is sealed. New constant reachable: " + javaConstant.toValueString());
            }
            AnalysisFuture<ImageHeapConstant> newTask = new AnalysisFuture<>(() -> {
                ImageHeapConstant imageHeapConstant = createImageHeapObject(javaConstant, nonNullReason);
                /* When the image heap object is created replace the future in the map. */
                imageHeap.setValue(javaConstant, imageHeapConstant);
                return imageHeapConstant;
            });
            existingTask = imageHeap.setTask(javaConstant, newTask);
            if (existingTask == null) {
                return newTask.ensureDone();
            }
        }
        return existingTask instanceof ImageHeapConstant ? (ImageHeapConstant) existingTask : ((AnalysisFuture<ImageHeapConstant>) existingTask).ensureDone();
    }

    /**
     * Create the ImageHeapConstant object wrapper, capture the hosted state of fields and arrays,
     * and install a future that can process them.
     */
    protected ImageHeapConstant createImageHeapObject(JavaConstant constant, ScanReason reason) {
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
            return getOrCreateImageHeapConstant(replaced.get(), reason);
        }

        /*
         * Access the constant type after the replacement. Some constants may have types that should
         * not be reachable at run time and thus are replaced.
         */
        AnalysisType type = metaAccess.lookupJavaType(constant);

        ImageHeapConstant newImageHeapConstant;
        if (type.isArray()) {
            Integer length = constantReflection.readArrayLength(constant);
            if (type.getComponentType().isPrimitive()) {
                newImageHeapConstant = new ImageHeapPrimitiveArray(type, constant, asObject(constant), length);
            } else {
                newImageHeapConstant = createImageHeapObjectArray(constant, type, length, reason);
            }
        } else {
            newImageHeapConstant = createImageHeapInstance(constant, type, reason);
            AnalysisType typeFromClassConstant = (AnalysisType) constantReflection.asJavaType(constant);
            if (typeFromClassConstant != null) {
                typeFromClassConstant.registerAsReachable(reason);
            }
        }
        return newImageHeapConstant;
    }

    private ImageHeapArray createImageHeapObjectArray(JavaConstant constant, AnalysisType type, int length, ScanReason reason) {
        ImageHeapObjectArray array = new ImageHeapObjectArray(type, constant, length);
        ScanReason arrayReason = new ArrayScan(type, constant, reason);
        for (int idx = 0; idx < length; idx++) {
            final JavaConstant rawElementValue = constantReflection.readArrayElement(constant, idx);
            int finalIdx = idx;
            array.setElementTask(idx, new AnalysisFuture<>(() -> {
                JavaConstant arrayElement = createImageHeapConstant(rawElementValue, arrayReason);
                array.setElement(finalIdx, arrayElement);
                return arrayElement;
            }));
        }
        return array;
    }

    private ImageHeapInstance createImageHeapInstance(JavaConstant constant, AnalysisType type, ScanReason reason) {
        /* We are about to query the type's fields, the type must be marked as reachable. */
        type.registerAsReachable(reason);
        ResolvedJavaField[] instanceFields = type.getInstanceFields(true);
        ImageHeapInstance instance = new ImageHeapInstance(type, constant, instanceFields.length);
        for (ResolvedJavaField javaField : instanceFields) {
            AnalysisField field = (AnalysisField) javaField;
            ValueSupplier<JavaConstant> rawFieldValue;
            try {
                rawFieldValue = readHostedFieldValue(field, universe.toHosted(constant));
            } catch (InternalError | TypeNotPresentException | LinkageError e) {
                /* Ignore missing type errors. */
                continue;
            }
            instance.setFieldTask(field, new AnalysisFuture<>(() -> {
                ScanReason fieldReason = new FieldScan(field, constant, reason);
                JavaConstant value = createFieldValue(field, instance, rawFieldValue, fieldReason);
                instance.setFieldValue(field, value);
                return value;
            }));
        }
        return instance;
    }

    private Optional<JavaConstant> maybeReplace(JavaConstant constant, ScanReason reason) {
        Object unwrapped = snippetReflection.asObject(Object.class, constant);
        if (unwrapped == null) {
            throw GraalError.shouldNotReachHere(formatReason("Could not unwrap constant", reason)); // ExcludeFromJacocoGeneratedReport
        } else if (unwrapped instanceof ImageHeapConstant) {
            throw GraalError.shouldNotReachHere(formatReason("Double wrapping of constant. Most likely, the reachability analysis code itself is seen as reachable.", reason)); // ExcludeFromJacocoGeneratedReport
        }
        maybeForceHashCodeComputation(unwrapped);

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

    public static void maybeForceHashCodeComputation(Object constant) {
        if (constant instanceof String stringConstant) {
            forceHashCodeComputation(stringConstant);
        } else if (constant instanceof Enum<?> enumConstant) {
            /*
             * Starting with JDK 21, Enum caches the identity hash code in a separate hash field. We
             * want to allow Enum values to be manually marked as immutable objects, so we eagerly
             * initialize the hash field. This is safe because Enum.hashCode() is a final method,
             * i.e., cannot be overwritten by the user.
             */
            forceHashCodeComputation(enumConstant);
        }
    }

    /**
     * For immutable Strings and other objects in the native image heap, force eager computation of
     * the hash field.
     */
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "eager hash field computation")
    private static void forceHashCodeComputation(Object object) {
        object.hashCode();
    }

    JavaConstant onFieldValueReachable(AnalysisField field, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        return onFieldValueReachable(field, null, ValueSupplier.eagerValue(fieldValue), reason, onAnalysisModified);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, ImageHeapInstance receiver, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        return onFieldValueReachable(field, receiver, ValueSupplier.eagerValue(fieldValue), reason, onAnalysisModified);
    }

    JavaConstant onFieldValueReachable(AnalysisField field, ImageHeapInstance receiver, ValueSupplier<JavaConstant> rawValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        AnalysisError.guarantee(field.isReachable(), "Field value is only reachable when field is reachable: %s", field);
        JavaConstant fieldValue = createFieldValue(field, receiver, rawValue, reason);
        markReachable(fieldValue, reason, onAnalysisModified);
        notifyAnalysis(field, receiver, fieldValue, reason, onAnalysisModified);
        return fieldValue;
    }

    protected JavaConstant createFieldValue(AnalysisField field, ValueSupplier<JavaConstant> rawValue, ScanReason reason) {
        return createFieldValue(field, null, rawValue, reason);
    }

    protected JavaConstant createFieldValue(AnalysisField field, ImageHeapInstance receiver, ValueSupplier<JavaConstant> rawValue, ScanReason reason) {
        /*
         * Check if the field value is available. If not, trying to access it is an error. This
         * forces the callers to only trigger the execution of the future task when the value is
         * ready to be materialized.
         */
        AnalysisError.guarantee(rawValue.isAvailable(), "Value not yet available for %s", field);

        JavaConstant transformedValue;
        try {
            transformedValue = transformFieldValue(field, receiver, rawValue.get());
        } catch (UnsupportedFeatureException e) {
            ObjectScanner.unsupportedFeatureDuringFieldScan(universe.getBigbang(), field, receiver, e, reason);
            transformedValue = JavaConstant.NULL_POINTER;
        }
        assert transformedValue != null : field.getDeclaringClass().toJavaName() + "::" + field.getName();

        return createImageHeapConstant(transformedValue, reason);
    }

    private void notifyAnalysis(AnalysisField field, ImageHeapInstance receiver, JavaConstant fieldValue, ScanReason reason) {
        notifyAnalysis(field, receiver, fieldValue, reason, null);
    }

    private void notifyAnalysis(AnalysisField field, ImageHeapInstance receiver, JavaConstant fieldValue, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        if (scanningObserver != null) {
            /* Notify the points-to analysis of the scan. */
            boolean analysisModified = doNotifyAnalysis(field, receiver, fieldValue, reason);
            if (analysisModified && onAnalysisModified != null) {
                onAnalysisModified.accept(reason);
            }
        }
    }

    private boolean doNotifyAnalysis(AnalysisField field, JavaConstant receiver, JavaConstant fieldValue, ScanReason reason) {
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
        JavaConstant elementValue = createImageHeapConstant(rawElementValue, reason);
        markReachable(elementValue, reason, onAnalysisModified);
        notifyAnalysis(array, arrayType, elementIndex, reason, onAnalysisModified, elementValue);
        return elementValue;
    }

    private void notifyAnalysis(ImageHeapArray array, AnalysisType arrayType, int elementIndex, ScanReason reason, Consumer<ScanReason> onAnalysisModified, JavaConstant elementValue) {
        if (scanningObserver != null && arrayType.getComponentType().getJavaKind() == JavaKind.Object) {
            /* Notify the points-to analysis of the scan. */
            boolean analysisModified = notifyAnalysis(array, arrayType, elementValue, elementIndex, reason);
            if (analysisModified && onAnalysisModified != null) {
                onAnalysisModified.accept(reason);
            }
        }
    }

    private boolean isNonNullObjectConstant(JavaConstant constant) {
        return constant.getJavaKind() == JavaKind.Object && constant.isNonNull() && !isWordType(constant, metaAccess);
    }

    public static boolean isWordType(JavaConstant rawElementValue, UniverseMetaAccess metaAccess) {
        return metaAccess.isInstanceOf(rawElementValue, WordBase.class);
    }

    private boolean notifyAnalysis(JavaConstant array, AnalysisType arrayType, JavaConstant elementValue, int elementIndex, ScanReason reason) {
        boolean analysisModified;
        if (elementValue.isNull()) {
            analysisModified = scanningObserver.forNullArrayElement(array, arrayType, elementIndex, reason);
        } else {
            if (isWordType(elementValue, metaAccess)) {
                return false;
            }
            AnalysisType elementType = metaAccess.lookupJavaType(elementValue);
            analysisModified = scanningObserver.forNonNullArrayElement(array, arrayType, elementValue, elementType, elementIndex, reason);
        }
        return analysisModified;
    }

    private JavaConstant markReachable(JavaConstant constant, ScanReason reason) {
        return markReachable(constant, reason, null);
    }

    private JavaConstant markReachable(JavaConstant constant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        if (isNonNullObjectConstant(constant)) {
            return markReachable((ImageHeapConstant) constant, reason, onAnalysisModified);
        }
        return constant;
    }

    private ImageHeapConstant markReachable(ImageHeapConstant imageHeapConstant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        if (imageHeapConstant.markReachable(reason)) {
            /* Follow all the array elements and reachable field values asynchronously. */
            postTask(() -> onObjectReachable(imageHeapConstant, reason, onAnalysisModified));
        }
        return imageHeapConstant;
    }

    protected void onObjectReachable(ImageHeapConstant imageHeapConstant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        AnalysisType objectType = metaAccess.lookupJavaType(imageHeapConstant);
        imageHeap.addReachableObject(objectType, imageHeapConstant);

        markTypeInstantiated(objectType, reason);
        if (imageHeapConstant instanceof ImageHeapObjectArray imageHeapArray) {
            AnalysisType arrayType = (AnalysisType) imageHeapArray.getType(metaAccess);
            for (int idx = 0; idx < imageHeapArray.getLength(); idx++) {
                JavaConstant elementValue = imageHeapArray.readElementValue(idx);
                ArrayScan arrayScanReason = new ArrayScan(arrayType, imageHeapArray, reason, idx);
                markReachable(elementValue, arrayScanReason, onAnalysisModified);
                notifyAnalysis(imageHeapArray, arrayType, idx, arrayScanReason, onAnalysisModified, elementValue);
            }
        } else if (imageHeapConstant instanceof ImageHeapInstance imageHeapInstance) {
            for (ResolvedJavaField javaField : objectType.getInstanceFields(true)) {
                AnalysisField field = (AnalysisField) javaField;
                if (field.isRead()) {
                    updateInstanceField(field, imageHeapInstance, new FieldScan(field, imageHeapInstance, reason), onAnalysisModified);
                }
            }
        }
    }

    private void updateInstanceField(AnalysisField field, ImageHeapInstance imageHeapInstance, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        if (isValueAvailable(field)) {
            JavaConstant fieldValue = imageHeapInstance.readFieldValue(field);
            markReachable(fieldValue, reason, onAnalysisModified);
            notifyAnalysis(field, imageHeapInstance, fieldValue, reason, onAnalysisModified);
        } else if (field.canBeNull()) {
            notifyAnalysis(field, imageHeapInstance, JavaConstant.NULL_POINTER, reason, onAnalysisModified);
        }
    }

    public boolean isValueAvailable(AnalysisField field) {
        return field.isValueAvailable();
    }

    protected String formatReason(String message, ScanReason reason) {
        return message + ' ' + reason;
    }

    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver) {
        // Wrap the hosted constant into a substrate constant
        JavaConstant value = universe.lookup(hostedConstantReflection.readFieldValue(field.wrapped, receiver));
        return ValueSupplier.eagerValue(value);
    }

    public JavaConstant readFieldValue(AnalysisField field, JavaConstant receiver) {
        return constantReflection.readFieldValue(field, receiver);
    }

    protected boolean skipScanning() {
        return false;
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
                    ImageHeapInstance receiverObject = (ImageHeapInstance) toImageHeapObject(receiverConstant, OtherReason.RESCAN);
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

    protected AnalysisFuture<JavaConstant> patchArrayElement(ImageHeapObjectArray arrayObject, int index, JavaConstant elementValue, ScanReason reason,
                    Consumer<ScanReason> onAnalysisModified) {
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> {
            JavaConstant value = onArrayElementReachable(arrayObject, (AnalysisType) arrayObject.getType(metaAccess), elementValue, index, reason, onAnalysisModified);
            arrayObject.setElement(index, value);
            return value;
        });
        arrayObject.setElementTask(index, task);
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
        JavaConstant value = createImageHeapConstant(constant, reason);
        markReachable(value, reason, null);
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

    /**
     * When a re-scanning is triggered while the analysis is running in parallel, it is necessary to
     * do the re-scanning in a separate executor task to avoid deadlocks. For example,
     * lookupJavaField might need to wait for the reachability handler to be finished that actually
     * triggered the re-scanning. We reuse the analysis executor, whose lifetime is controlled by
     * the analysis engine.
     *
     * In the (legacy) Feature.duringAnalysis state, the executor is not running and we must not
     * schedule new tasks, because that would be treated as "the analysis has not finished yet". So
     * in that case we execute the task directly.
     */
    private void maybeRunInExecutor(CompletionExecutor.DebugContextRunnable task) {
        if (bb.executorIsStarted()) {
            bb.postTask(task);
        } else {
            task.run(null);
        }
    }

    /**
     * Post the task to the analysis executor. Its lifetime is controlled by the analysis engine or
     * the heap verifier such that all heap scanning tasks are also completed when analysis reaches
     * a stable state or heap verification is completed.
     */
    private void postTask(Runnable task) {
        bb.postTask(debug -> task.run());
    }
}
