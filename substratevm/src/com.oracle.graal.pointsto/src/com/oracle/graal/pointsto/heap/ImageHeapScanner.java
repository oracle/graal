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
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier.ScanningObserver;
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

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;
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
    protected final HostedValuesProvider hostedValuesProvider;
    protected final ConstantReflectionProvider hostedConstantReflection;
    protected final SnippetReflectionProvider hostedSnippetReflection;

    protected ObjectScanningObserver scanningObserver;

    private boolean sealed;

    public ImageHeapScanner(BigBang bb, ImageHeap heap, AnalysisMetaAccess aMetaAccess, SnippetReflectionProvider aSnippetReflection,
                    ConstantReflectionProvider aConstantReflection, ObjectScanningObserver aScanningObserver, HostedValuesProvider aHostedValuesProvider) {
        this.bb = bb;
        imageHeap = heap;
        metaAccess = aMetaAccess;
        universe = aMetaAccess.getUniverse();
        hostVM = aMetaAccess.getUniverse().hostVM();
        snippetReflection = aSnippetReflection;
        constantReflection = aConstantReflection;
        hostedValuesProvider = aHostedValuesProvider;
        scanningObserver = aScanningObserver;
        hostedConstantReflection = GraalAccess.getOriginalProviders().getConstantReflection();
        hostedSnippetReflection = GraalAccess.getOriginalProviders().getSnippetReflection();
    }

    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    public void seal() {
        this.sealed = true;
    }

    public void scanEmbeddedRoot(JavaConstant root, BytecodePosition position) {
        if (isNonNullObjectConstant(root)) {
            EmbeddedRootScan reason = new EmbeddedRootScan(position, root);
            ImageHeapConstant value = getOrCreateImageHeapConstant(root, reason);
            markReachable(value, reason);
        }
    }

    public void onFieldRead(AnalysisField field) {
        assert field.isRead() : field;
        /* Check if the value is available before accessing it. */
        AnalysisType declaringClass = field.getDeclaringClass();
        if (field.isStatic()) {
            FieldScan reason = new FieldScan(field);
            if (field.isInBaseLayer()) {
                /*
                 * For base layer static fields we don't want to scan the constant value, but
                 * instead inject its type state in the field flow. This will be propagated to any
                 * corresponding field loads.
                 * 
                 * GR-52421: the field state needs to be serialized from the base layer analysis
                 */
                if (field.getJavaKind().isObject()) {
                    AnalysisType fieldType = field.getType();
                    if (fieldType.isArray() || (fieldType.isInstanceClass() && !fieldType.isAbstract())) {
                        fieldType.registerAsInstantiated(field);
                    }
                    bb.injectFieldTypes(field, fieldType);
                }
                return;
            }
            if (isValueAvailable(field)) {
                JavaConstant fieldValue = readStaticFieldValue(field);
                markReachable(fieldValue, reason);
                notifyAnalysis(field, null, fieldValue, reason);
            } else if (field.canBeNull()) {
                notifyAnalysis(field, null, JavaConstant.NULL_POINTER, reason);
            }
        } else {
            /* Trigger field scanning for the already processed objects. */
            postTask(() -> onInstanceFieldRead(field, declaringClass));
        }
    }

    private void onInstanceFieldRead(AnalysisField field, AnalysisType type) {
        for (AnalysisType subtype : type.getSubTypes()) {
            for (ImageHeapConstant imageHeapConstant : imageHeap.getReachableObjects(subtype)) {
                FieldScan reason = new FieldScan(field, imageHeapConstant);
                ImageHeapInstance imageHeapInstance = (ImageHeapInstance) imageHeapConstant;
                updateInstanceField(field, imageHeapInstance, reason, null);
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
        if (universe.sealed()) {
            AnalysisError.guarantee(type.isReachable(), "The type %s should have been reachable during analysis.", type);
            AnalysisError.guarantee(type.isInstantiated(), "The type %s should have been instantiated during analysis.", type);
        } else {
            type.registerAsInstantiated(reason);
        }
    }

    public JavaConstant getImageHeapConstant(JavaConstant constant) {
        if (isNonNullObjectConstant(constant)) {
            return (ImageHeapConstant) imageHeap.getSnapshot(constant);
        }
        return constant;
    }

    /** Create an {@link ImageHeapConstant} from a raw hosted object. */
    public JavaConstant createImageHeapConstant(Object object, ScanReason reason) {
        /*
         * First, get the hosted constant representation and pre-process the object if necessary,
         * e.g., transform RelocatedPointer into RelocatableConstant and WordBase into Integer.
         */
        JavaConstant hostedConstant = hostedValuesProvider.forObject(object);
        /* Then create an {@link ImageHeapConstant} from a hosted constant. */
        return createImageHeapConstant(hostedConstant, reason);
    }

    /**
     * Create an {@link ImageHeapConstant} from a hosted constant, if that constant represents an
     * object, otherwise return the input content.
     */
    public JavaConstant createImageHeapConstant(JavaConstant constant, ScanReason reason) {
        if (isNonNullObjectConstant(constant)) {
            return getOrCreateImageHeapConstant(constant, reason);
        }
        return constant;
    }

    public ImageHeapConstant toImageHeapObject(JavaConstant constant, ScanReason reason) {
        assert constant != null && isNonNullObjectConstant(constant) : constant;
        return markReachable(getOrCreateImageHeapConstant(constant, reason), reason, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ImageHeapConstant getOrCreateImageHeapConstant(JavaConstant javaConstant, ScanReason reason) {
        ScanReason nonNullReason = Objects.requireNonNull(reason);
        Object existingTask = imageHeap.getSnapshot(javaConstant);
        if (existingTask == null) {
            AnalysisFuture<ImageHeapConstant> newTask;
            ImageLayerLoader imageLayerLoader = universe.getImageLayerLoader();
            if (hostVM.useBaseLayer() && imageLayerLoader.hasValueForConstant(javaConstant)) {
                ImageHeapConstant value = imageLayerLoader.getValueForConstant(javaConstant);
                ensureFieldPositionsComputed(value, nonNullReason);
                AnalysisError.guarantee(value.getHostedObject().equals(javaConstant));
                newTask = new AnalysisFuture<>(() -> {
                    imageHeap.setValue(javaConstant, value);
                    return value;
                });
            } else {
                checkSealed(reason, "Trying to create a new ImageHeapConstant for %s after the ImageHeapScanner is sealed.", javaConstant);
                newTask = new AnalysisFuture<>(() -> {
                    ImageHeapConstant imageHeapConstant = createImageHeapObject(javaConstant, nonNullReason);
                    /* When the image heap object is created replace the future in the map. */
                    imageHeap.setValue(javaConstant, imageHeapConstant);
                    return imageHeapConstant;
                });
            }
            existingTask = imageHeap.setTask(javaConstant, newTask);
            if (existingTask == null) {
                return newTask.ensureDone();
            }
        }
        return existingTask instanceof ImageHeapConstant ? (ImageHeapConstant) existingTask : ((AnalysisFuture<ImageHeapConstant>) existingTask).ensureDone();
    }

    private static void ensureFieldPositionsComputed(ImageHeapConstant baseLayerConstant, ScanReason reason) {
        AnalysisType objectType = baseLayerConstant.getType();
        objectType.registerAsReachable(reason);
        objectType.getStaticFields();
        objectType.getInstanceFields(true);
    }

    private void checkSealed(ScanReason reason, String format, Object... args) {
        if (sealed && reason != OtherReason.LATE_SCAN) {
            throw AnalysisError.sealedHeapError(HeapSnapshotVerifier.formatReason(bb, reason, format, args));
        }
    }

    /**
     * Create the ImageHeapConstant object wrapper, capture the hosted state of fields and arrays,
     * and install a future that can process them.
     */
    protected ImageHeapConstant createImageHeapObject(JavaConstant constant, ScanReason reason) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull() : constant;

        Optional<JavaConstant> replaced = maybeReplace(constant, reason);
        if (replaced.isPresent()) {
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
        AnalysisType type = universe.lookup(GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(constant));

        if (type.isArray()) {
            Integer length = hostedValuesProvider.readArrayLength(constant);
            if (type.getComponentType().isPrimitive()) {
                return new ImageHeapPrimitiveArray(type, constant, snippetReflection.asObject(Object.class, constant), length);
            } else {
                return createImageHeapObjectArray(constant, type, length, reason);
            }
        } else {
            return createImageHeapInstance(constant, type, reason);
        }
    }

    private ImageHeapArray createImageHeapObjectArray(JavaConstant constant, AnalysisType type, int length, ScanReason reason) {
        ImageHeapObjectArray array = new ImageHeapObjectArray(type, constant, length);
        /* Read hosted array element values only when the array is initialized. */
        array.constantData.hostedValuesReader = new AnalysisFuture<>(() -> {
            checkSealed(reason, "Trying to materialize an ImageHeapObjectArray for %s after the ImageHeapScanner is sealed.", constant);
            type.registerAsReachable(reason);
            ScanReason arrayReason = new ArrayScan(type, array, reason);
            Object[] elementValues = new Object[length];
            for (int idx = 0; idx < length; idx++) {
                final JavaConstant rawElementValue = hostedValuesProvider.readArrayElement(constant, idx);
                int finalIdx = idx;
                elementValues[idx] = new AnalysisFuture<>(() -> {
                    JavaConstant arrayElement = createImageHeapConstant(rawElementValue, arrayReason);
                    array.setElement(finalIdx, arrayElement);
                    return arrayElement;
                });
            }
            array.setElementValues(elementValues);
        });
        return array;
    }

    public void linkBaseLayerValue(ImageHeapConstant constant, Object reason) {
        JavaConstant hostedValue = constant.getHostedObject();
        Object existingSnapshot = imageHeap.getSnapshot(hostedValue);
        if (existingSnapshot != null) {
            AnalysisError.guarantee(existingSnapshot == constant || existingSnapshot instanceof AnalysisFuture<?> task && task.ensureDone() == constant,
                            "Found unexpected snapshot value for base layer value. Reason: %s.", reason);
        } else {
            imageHeap.setValue(hostedValue, constant);
        }
    }

    private ImageHeapInstance createImageHeapInstance(JavaConstant constant, AnalysisType type, ScanReason reason) {
        ImageHeapInstance instance = new ImageHeapInstance(type, constant);
        /* Read hosted field values only when the receiver is initialized. */
        instance.constantData.hostedValuesReader = new AnalysisFuture<>(() -> {
            checkSealed(reason, "Trying to materialize an ImageHeapInstance for %s after the ImageHeapScanner is sealed.", constant);
            /* If this is a Class constant register the corresponding type as reachable. */
            AnalysisType typeFromClassConstant = (AnalysisType) constantReflection.asJavaType(instance);
            if (typeFromClassConstant != null) {
                typeFromClassConstant.registerAsReachable(reason);
            }
            /* We are about to query the type's fields, the type must be marked as reachable. */
            type.registerAsReachable(reason);
            ResolvedJavaField[] instanceFields = type.getInstanceFields(true);
            Object[] hostedFieldValues = new Object[instanceFields.length];
            for (ResolvedJavaField javaField : instanceFields) {
                AnalysisField field = (AnalysisField) javaField;
                ValueSupplier<JavaConstant> rawFieldValue;
                try {
                    rawFieldValue = readHostedFieldValue(field, constant);
                } catch (InternalError | TypeNotPresentException | LinkageError e) {
                    /* Ignore missing type errors. */
                    continue;
                }
                hostedFieldValues[field.getPosition()] = new AnalysisFuture<>(() -> {
                    ScanReason fieldReason = new FieldScan(field, instance, reason);
                    JavaConstant value = createFieldValue(field, instance, rawFieldValue, fieldReason);
                    instance.setFieldValue(field, value);
                    return value;
                });
            }
            instance.setFieldValues(hostedFieldValues);
        });
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
                    return Optional.of(hostedValuesProvider.validateReplacedConstant(universe.getHostedValuesProvider().forObject(replaced)));
                }
            } catch (UnsupportedFeatureException e) {
                /* Enhance the unsupported feature message with the object trace and rethrow. */
                StringBuilder backtrace = new StringBuilder();
                ObjectScanner.buildObjectBacktrace(bb, reason, backtrace);
                throw new UnsupportedFeatureException(e.getMessage() + System.lineSeparator() + backtrace);
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
        // Static field can be read for constant folding before being marked as reachable.
        AnalysisError.guarantee(field.isStatic() || field.isReachable(), "Field value is only reachable when field is reachable: %s", field);
        JavaConstant fieldValue = createFieldValue(field, receiver, rawValue, reason);
        markReachable(fieldValue, reason, onAnalysisModified);
        notifyAnalysis(field, receiver, fieldValue, reason, onAnalysisModified);
        return fieldValue;
    }

    protected JavaConstant createFieldValue(AnalysisField field, ValueSupplier<JavaConstant> rawValue, ScanReason reason) {
        return createFieldValue(field, null, rawValue, reason);
    }

    @SuppressWarnings("unused")
    protected JavaConstant createFieldValue(AnalysisField field, ImageHeapInstance receiver, ValueSupplier<JavaConstant> rawValue, ScanReason reason) {
        /*
         * Check if the field value is available. If not, trying to access it is an error. This
         * forces the callers to only trigger the execution of the future task when the value is
         * ready to be materialized.
         */
        AnalysisError.guarantee(rawValue.isAvailable(), "Value not yet available for %s", field);
        return createImageHeapConstant(rawValue.get(), reason);
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
        if (fieldValue.getJavaKind() == JavaKind.Object && hostVM.isRelocatedPointer(fieldValue)) {
            analysisModified = scanningObserver.forRelocatedPointerFieldValue(receiver, field, fieldValue, reason);
        } else if (fieldValue.isNull()) {
            analysisModified = scanningObserver.forNullFieldValue(receiver, field, reason);
        } else if (fieldValue.getJavaKind() == JavaKind.Object) {
            analysisModified = scanningObserver.forNonNullFieldValue(receiver, field, fieldValue, reason);
        } else if (bb.trackPrimitiveValues() && fieldValue.getJavaKind().isNumericInteger()) {
            analysisModified = scanningObserver.forPrimitiveFieldValue(receiver, field, fieldValue, reason);
        }
        return analysisModified;
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
            if (elementValue.getJavaKind() != JavaKind.Object) {
                /*
                 * WordBase values (except RelocatedPointer) are transformed to their raw Int value,
                 * however an array of such values will still have an Object type.
                 */
                return;
            }
            /* Notify the points-to analysis of the scan. */
            boolean analysisModified = notifyAnalysis(array, arrayType, elementValue, elementIndex, reason);
            if (analysisModified && onAnalysisModified != null) {
                onAnalysisModified.accept(reason);
            }
        }
    }

    private boolean isNonNullObjectConstant(JavaConstant constant) {
        return constant.getJavaKind() == JavaKind.Object && constant.isNonNull() && !universe.hostVM().isRelocatedPointer(constant);
    }

    private boolean notifyAnalysis(JavaConstant array, AnalysisType arrayType, JavaConstant elementValue, int elementIndex, ScanReason reason) {
        boolean analysisModified;
        if (elementValue.isNull()) {
            analysisModified = scanningObserver.forNullArrayElement(array, arrayType, elementIndex, reason);
        } else {
            if (universe.hostVM().isRelocatedPointer(elementValue)) {
                return false;
            }
            AnalysisType elementType = metaAccess.lookupJavaType(elementValue);
            analysisModified = scanningObserver.forNonNullArrayElement(array, arrayType, elementValue, elementType, elementIndex, reason);
        }
        return analysisModified;
    }

    protected JavaConstant markReachable(JavaConstant constant, ScanReason reason) {
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
            maybeRunInExecutor(unused -> onObjectReachable(imageHeapConstant, reason, onAnalysisModified));
        }
        return imageHeapConstant;
    }

    protected void onObjectReachable(ImageHeapConstant imageHeapConstant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        AnalysisType objectType = metaAccess.lookupJavaType(imageHeapConstant);
        imageHeap.addReachableObject(objectType, imageHeapConstant);

        AnalysisType type = imageHeapConstant.getType();
        Object object = bb.getSnippetReflectionProvider().asObject(Object.class, imageHeapConstant);
        /* Simulated constants don't have a backing object and don't need to be processed. */
        if (object != null) {
            try {
                type.notifyObjectReachable(universe.getConcurrentAnalysisAccess(), object, reason);
            } catch (UnsupportedFeatureException e) {
                /* Enhance the unsupported feature message with the object trace and rethrow. */
                StringBuilder backtrace = new StringBuilder();
                ObjectScanner.buildObjectBacktrace(bb, reason, backtrace);
                throw new UnsupportedFeatureException(e.getMessage() + System.lineSeparator() + backtrace);
            }
        }

        markTypeInstantiated(objectType, reason);
        if (imageHeapConstant instanceof ImageHeapObjectArray imageHeapArray) {
            AnalysisType arrayType = imageHeapArray.getType();
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

    public boolean isValueAvailable(@SuppressWarnings("unused") AnalysisField field) {
        return true;
    }

    protected String formatReason(String message, ScanReason reason) {
        return message + ' ' + reason;
    }

    /**
     * Redirect static fields reading. The implementors can overwrite this and provide additional
     * sources for static fields values.
     */
    public JavaConstant readStaticFieldValue(AnalysisField field) {
        return field.getDeclaringClass().getOrComputeData().readFieldValue(field);
    }

    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver) {
        return hostedValuesProvider.readFieldValue(field, receiver);
    }

    public void rescanRoot(Field reflectionField) {
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
        rescanField(receiver, reflectionField, OtherReason.RESCAN);
    }

    public void rescanField(Object receiver, Field reflectionField, ScanReason reason) {
        maybeRunInExecutor(unused -> {
            AnalysisType type = metaAccess.lookupJavaType(reflectionField.getDeclaringClass());
            if (type.isReachable()) {
                AnalysisField field = metaAccess.lookupJavaField(reflectionField);
                assert !field.isStatic() : field;
                if (!field.isReachable()) {
                    return;
                }
                JavaConstant receiverConstant = asConstant(receiver);
                Optional<JavaConstant> replaced = maybeReplace(receiverConstant, reason);
                if (replaced.isPresent()) {
                    if (replaced.get().isNull()) {
                        /* There was some problem during replacement, bailout. */
                        return;
                    }
                    receiverConstant = replaced.get();
                }
                JavaConstant fieldValue = readHostedFieldValue(field, receiverConstant).get();
                if (fieldValue != null) {
                    ImageHeapInstance receiverObject = (ImageHeapInstance) toImageHeapObject(receiverConstant, reason);
                    JavaConstant fieldSnapshot = receiverObject.readFieldValue(field);
                    JavaConstant unwrappedSnapshot = ScanningObserver.maybeUnwrapSnapshot(fieldSnapshot, fieldValue instanceof ImageHeapConstant);

                    if (fieldSnapshot instanceof ImageHeapConstant ihc && ihc.isInBaseLayer() && ihc.getHostedObject() == null) {
                        /*
                         * We cannot verify a base layer constant which doesn't have a backing
                         * hosted object. Since the hosted object is missing the constant would be
                         * replaced with the new hosted object reachable from the field, which would
                         * be wrong.
                         */
                        return;
                    }

                    if (!Objects.equals(unwrappedSnapshot, fieldValue)) {
                        AnalysisFuture<JavaConstant> fieldTask = patchInstanceField(receiverObject, field, fieldValue, reason, null);
                        if (field.isRead() || field.isFolded()) {
                            JavaConstant constant = fieldTask.ensureDone();
                            ensureReaderInstalled(constant);
                            rescanCollectionElements(constant);
                        }
                    } else {
                        ScanningObserver.patchPrimitiveArrayValue(bb, fieldSnapshot, fieldValue);
                    }
                }
            }
        });
    }

    /**
     * For image heap constants created during verification, i.e., either correct values set lazily
     * but for which the {@link ImageHeapConstant} was not yet created or values created by patching
     * a wrong snapshot, we need to manually ensure that the readers are installed since the
     * verification will continue expanding them.
     */
    void ensureReaderInstalled(JavaConstant constant) {
        if (isNonNullObjectConstant(constant)) {
            ((ImageHeapConstant) constant).ensureReaderInstalled();
        }
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
            JavaConstant value = onArrayElementReachable(arrayObject, arrayObject.getType(), elementValue, index, reason, onAnalysisModified);
            arrayObject.setElement(index, value);
            return value;
        });
        arrayObject.setElementTask(index, task);
        return task;
    }

    /**
     * Returns true if the provided {@code object} was seen as reachable by the static analysis.
     */
    public boolean isObjectReachable(Object object) {
        var javaConstant = asConstant(Objects.requireNonNull(object));
        Object existingTask = imageHeap.getSnapshot(javaConstant);
        if (existingTask instanceof ImageHeapConstant imageHeapConstant) {
            return imageHeapConstant.isReachable();
        }
        return false;
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
            rescanCollectionElements(snippetReflection.asObject(Object.class, constant));
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

    private JavaConstant asConstant(Object object) {
        return hostedValuesProvider.forObject(object);
    }

    public void cleanupAfterAnalysis() {
        scanningObserver = null;
    }

    protected abstract Class<?> getClass(String className);

    public HostedValuesProvider getHostedValuesProvider() {
        return hostedValuesProvider;
    }

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
