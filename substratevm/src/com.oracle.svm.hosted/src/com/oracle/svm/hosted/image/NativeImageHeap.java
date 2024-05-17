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
package com.oracle.svm.hosted.image;

import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.StringInternSupport;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.MaterializedConstantFields;
import com.oracle.svm.hosted.meta.RelocatableConstant;
import com.oracle.svm.hosted.meta.UniverseBuilder;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class keeps track of all objects that should be part of the native image heap. It should not
 * make any assumptions about the final layout of the image heap.
 */
public final class NativeImageHeap implements ImageHeap {
    /** A pseudo-partition for base layer objects, see {@link BaseLayerPartition}. */
    private static final ImageHeapPartition BASE_LAYER_PARTITION = new BaseLayerPartition();

    public final AnalysisUniverse aUniverse;
    public final HostedUniverse hUniverse;
    public final HostedMetaAccess hMetaAccess;
    public final HostedConstantReflectionProvider hConstantReflection;
    public final ObjectLayout objectLayout;
    public final DynamicHubLayout dynamicHubLayout;

    private final ImageHeapLayouter heapLayouter;
    private final int minInstanceSize;
    private final int minArraySize;

    /**
     * A Map from objects at construction-time to native image objects.
     * <p>
     * More than one host object may be represented by a single native image object.
     * <p>
     * The constants stored in the image heap are always uncompressed. The same object info is
     * returned whenever the map is queried regardless of the compressed flag value.
     */
    private final HashMap<JavaConstant, ObjectInfo> objects = new HashMap<>();

    /** Objects that must not be written to the native image heap. */
    private final Set<Object> blacklist = Collections.newSetFromMap(new IdentityHashMap<>());

    /** A map from hosted classes to classes that have hybrid layouts in the native image heap. */
    private final Map<HostedClass, HybridLayout> hybridLayouts = new HashMap<>();

    /** A Map to build what will be the String intern map in the native image heap. */
    private final Map<String, String> internedStrings = new HashMap<>();

    // Phase variables.
    private final Phase addObjectsPhase = Phase.factory();
    private final Phase internStringsPhase = Phase.factory();

    /** A queue of objects that need to be added to the native image heap, to avoid recursion. */
    private final Deque<AddObjectData> addObjectWorklist = new ArrayDeque<>();

    /** Objects that are known to be immutable in the native image heap. */
    private final Set<Object> knownImmutableObjects = Collections.newSetFromMap(new IdentityHashMap<>());

    /** For diagnostic purpose only. */
    Map<ObjectInfo, ObjectReachabilityInfo> objectReachabilityInfo = null;

    public NativeImageHeap(AnalysisUniverse aUniverse, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess, HostedConstantReflectionProvider hConstantReflection, ImageHeapLayouter heapLayouter) {
        this.aUniverse = aUniverse;
        this.hUniverse = hUniverse;
        this.hMetaAccess = hMetaAccess;
        this.hConstantReflection = hConstantReflection;

        this.objectLayout = ConfigurationValues.getObjectLayout();
        this.heapLayouter = heapLayouter;

        this.minInstanceSize = objectLayout.getMinImageHeapInstanceSize();
        this.minArraySize = objectLayout.getMinImageHeapArraySize();
        assert assertFillerObjectSizes();

        dynamicHubLayout = DynamicHubLayout.singleton();

        if (ImageHeapConnectedComponentsFeature.Options.PrintImageHeapConnectedComponents.getValue()) {
            this.objectReachabilityInfo = new IdentityHashMap<>();
        }
    }

    @Override
    public Collection<ObjectInfo> getObjects() {
        return objects.values();
    }

    public int getObjectCount() {
        return objects.size();
    }

    public ObjectInfo getObjectInfo(Object obj) {
        JavaConstant constant = hUniverse.getSnippetReflection().forObject(obj);
        VMError.guarantee(constant instanceof ImageHeapConstant, "Expected an ImageHeapConstant, found %s", constant);
        return objects.get(CompressibleConstant.uncompress(constant));
    }

    public ObjectInfo getConstantInfo(JavaConstant constant) {
        VMError.guarantee(constant instanceof ImageHeapConstant, "Expected an ImageHeapConstant, found %s", constant);
        return objects.get(CompressibleConstant.uncompress(constant));
    }

    protected HybridLayout getHybridLayout(HostedClass clazz) {
        return hybridLayouts.get(clazz);
    }

    protected boolean isBlacklisted(Object obj) {
        return blacklist.contains(obj);
    }

    public ImageHeapLayouter getLayouter() {
        return heapLayouter;
    }

    @Fold
    static boolean useHeapBase() {
        return SubstrateOptions.SpawnIsolates.getValue() && ImageSingletons.lookup(CompressEncoding.class).hasBase();
    }

    @Fold
    static boolean spawnIsolates() {
        return SubstrateOptions.SpawnIsolates.getValue() && useHeapBase();
    }

    @SuppressWarnings("try")
    public void addInitialObjects() {
        addObjectsPhase.allow();
        internStringsPhase.allow();

        addStaticFields();
    }

    public void addTrailingObjects() {
        // Process any remaining objects on the worklist, especially that might intern strings.
        processAddObjectWorklist();

        HostedField hostedField = hMetaAccess.optionalLookupJavaField(StringInternSupport.getInternedStringsField());
        boolean usesInternedStrings = hostedField != null && hostedField.isAccessed();
        if (usesInternedStrings) {
            /*
             * Ensure that the hub of the String[] array (used for the interned objects) is written.
             */
            addObject(hMetaAccess.lookupJavaType(String[].class).getHub(), false, HeapInclusionReason.InternedStringsTable);
            /*
             * We are no longer allowed to add new interned strings, because that would modify the
             * table we are about to write.
             */
            internStringsPhase.disallow();
            /*
             * By now, all interned Strings have been added to our internal interning table.
             * Populate the VM configuration with this table, and ensure it is part of the heap.
             */
            String[] imageInternedStrings = internedStrings.keySet().toArray(new String[0]);
            Arrays.sort(imageInternedStrings);
            ImageSingletons.lookup(StringInternSupport.class).setImageInternedStrings(imageInternedStrings);
            if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                HostedImageLayerBuildingSupport.singleton().getWriter().setImageInternedStrings(imageInternedStrings);
            }
            /* Manually snapshot the interned strings array. */
            aUniverse.getHeapScanner().rescanObject(imageInternedStrings, OtherReason.LATE_SCAN);

            addObject(imageInternedStrings, true, HeapInclusionReason.InternedStringsTable);

            // Process any objects that were transitively added to the heap.
            processAddObjectWorklist();
        } else {
            internStringsPhase.disallow();
        }

        addObjectsPhase.disallow();
        assert addObjectWorklist.isEmpty();
    }

    /**
     * Bypass shadow heap reading for inlined fields. These fields are not actually present in the
     * image (their value is inlined) and are not present in the shadow heap either.
     */
    Object readInlinedField(HostedField field, JavaConstant receiver) {
        VMError.guarantee(HostedConfiguration.isInlinedField(field), "Expected an inlined field, found %s", field);
        JavaConstant hostedReceiver = ((ImageHeapInstance) receiver).getHostedObject();
        /* Use the HostedValuesProvider to get direct access to hosted values. */
        HostedValuesProvider hostedValuesProvider = aUniverse.getHostedValuesProvider();
        return hUniverse.getSnippetReflection().asObject(Object.class, hostedValuesProvider.readFieldValueWithReplacement(field.getWrapped(), hostedReceiver));
    }

    private JavaConstant readConstantField(HostedField field, JavaConstant receiver) {
        return hConstantReflection.readFieldValue(field, receiver);
    }

    private void addStaticFields() {
        addObject(StaticFieldsSupport.getStaticObjectFields(), false, HeapInclusionReason.StaticObjectFields);
        addObject(StaticFieldsSupport.getStaticPrimitiveFields(), false, HeapInclusionReason.StaticPrimitiveFields);

        /*
         * We only have empty holder arrays for the static fields, so we need to add static object
         * fields manually.
         */
        for (HostedField field : hUniverse.getFields()) {
            if (field.wrapped.isInBaseLayer()) {
                /* Base layer static field values are accessed via the base layer arrays. */
                continue;
            }
            if (Modifier.isStatic(field.getModifiers()) && field.hasLocation() && field.getType().getStorageKind() == JavaKind.Object && field.isRead()) {
                assert field.isWritten() || !field.isValueAvailable() || MaterializedConstantFields.singleton().contains(field.wrapped);
                addConstant(readConstantField(field, null), false, field);
            }
        }
    }

    public void registerAsImmutable(Object object) {
        assert addObjectsPhase.isBefore() : "Registering immutable object too late: phase: " + addObjectsPhase.toString();
        knownImmutableObjects.add(object);
    }

    public void registerAsImmutable(Object root, Predicate<Object> includeObject) {
        Deque<Object> worklist = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> registeredObjects = new IdentityHashMap<>();

        worklist.push(root);

        while (!worklist.isEmpty()) {
            Object cur = worklist.pop();
            registerAsImmutable(cur);

            if (hMetaAccess.optionalLookupJavaType(cur.getClass()).isEmpty()) {
                throw VMError.shouldNotReachHere("Type missing from static analysis: " + cur.getClass().getTypeName());
            } else if (cur instanceof Object[]) {
                for (Object element : ((Object[]) cur)) {
                    addToWorklist(aUniverse.replaceObject(element), includeObject, worklist, registeredObjects);
                }
            } else {
                JavaConstant constant = hUniverse.getSnippetReflection().forObject(cur);
                for (HostedField field : hMetaAccess.lookupJavaType(constant).getInstanceFields(true)) {
                    if (field.isAccessed() && field.getStorageKind() == JavaKind.Object) {
                        Object fieldValue = hUniverse.getSnippetReflection().asObject(Object.class, hConstantReflection.readFieldValue(field, constant));
                        addToWorklist(fieldValue, includeObject, worklist, registeredObjects);
                    }
                }
            }
        }
    }

    private static void addToWorklist(Object object, Predicate<Object> includeObject, Deque<Object> worklist, IdentityHashMap<Object, Boolean> registeredObjects) {
        if (object == null || registeredObjects.containsKey(object)) {
            return;
        } else if (object instanceof DynamicHub || object instanceof Class) {
            /* Classes are handled specially, some fields of it are immutable and some not. */
            return;
        } else if (!includeObject.test(object)) {
            return;
        }
        registeredObjects.put(object, Boolean.TRUE);
        worklist.push(object);
    }

    /**
     * If necessary, add an object to the model of the native image heap.
     *
     * Various transformations are done from objects in the hosted heap to the native image heap.
     * Not every object is added to the heap, for various reasons.
     */
    public void addObject(final Object original, boolean immutableFromParent, final Object reason) {
        addConstant(hUniverse.getSnippetReflection().forObject(original), immutableFromParent, reason);
    }

    public void addConstant(final JavaConstant constant, boolean immutableFromParent, final Object reason) {
        assert addObjectsPhase.isAllowed() : "Objects cannot be added at phase: " + addObjectsPhase.toString() + " with reason: " + reason;

        if (constant.getJavaKind().isPrimitive() || constant.isNull() || hMetaAccess.isInstanceOf(constant, WordBase.class)) {
            return;
        }

        if (hMetaAccess.isInstanceOf(constant, Class.class)) {
            DynamicHub hub = hUniverse.getSnippetReflection().asObject(DynamicHub.class, constant);
            if (hub.getClassInitializationInfo() == null) {
                /*
                 * All DynamicHub instances written into the image heap must have a
                 * ClassInitializationInfo, otherwise we can get a NullPointerException at run time.
                 * When this check fails, then the DynamicHub has not been seen during static
                 * analysis. Since many other objects are reachable from the DynamicHub
                 * (annotations, enum values, ...) this can also mean that types are used in the
                 * image that the static analysis has not seen - so this check actually protects
                 * against much more than just missing class initialization information.
                 */
                throw reportIllegalType(hub, reason, "Missing class initialization info for " + hub.getName() + " type.");
            }
        }

        JavaConstant uncompressed = CompressibleConstant.uncompress(constant);

        int identityHashCode = computeIdentityHashCode(uncompressed);
        VMError.guarantee(identityHashCode != 0, "0 is used as a marker value for 'hash code not yet computed'");

        Object objectConstant = hUniverse.getSnippetReflection().asObject(Object.class, uncompressed);
        ImageHeapScanner.maybeForceHashCodeComputation(objectConstant);
        if (objectConstant instanceof String stringConstant) {
            handleImageString(stringConstant);
        }

        final ObjectInfo existing = getConstantInfo(uncompressed);
        if (existing == null) {
            addObjectToImageHeap(uncompressed, immutableFromParent, identityHashCode, reason);
        } else if (objectReachabilityInfo != null) {
            objectReachabilityInfo.get(existing).addReason(reason);
        }
    }

    private int computeIdentityHashCode(JavaConstant constant) {
        return hConstantReflection.identityHashCode(constant);
    }

    @Override
    public int countDynamicHubs() {
        int count = 0;
        for (ObjectInfo o : getObjects()) {
            if (hMetaAccess.isInstanceOf(o.getConstant(), DynamicHub.class)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Adds an object to the image heap that tries to span {@code size} bytes. Note that there is no
     * guarantee that the created object will exactly span {@code size} bytes. If it is not possible
     * to create an object with {@code size} bytes, the next smaller object that can be allocated is
     * added to the image heap instead. If {@code size} is smaller than the smallest possible
     * object, no object is added to the image heap and null is returned.
     */
    @Override
    public ObjectInfo addFillerObject(int size) {
        if (size >= minArraySize) {
            int elementSize = objectLayout.getArrayIndexScale(JavaKind.Int);
            int arrayLength = (size - minArraySize) / elementSize;
            assert objectLayout.getArraySize(JavaKind.Int, arrayLength, true) == size;
            return addLateToImageHeap(new int[arrayLength], HeapInclusionReason.FillerObject);
        } else if (size >= minInstanceSize) {
            return addLateToImageHeap(new FillerObject(), HeapInclusionReason.FillerObject);
        } else {
            return null;
        }
    }

    private boolean assertFillerObjectSizes() {
        assert minArraySize == objectLayout.getArraySize(JavaKind.Int, 0, true);

        HostedType filler = hMetaAccess.lookupJavaType(FillerObject.class);
        UnsignedWord fillerSize = LayoutEncoding.getPureInstanceSize(filler.getHub(), true);
        assert fillerSize.equal(minInstanceSize);

        assert minInstanceSize * 2 >= minArraySize : "otherwise, we might need more than one non-array object";

        return true;
    }

    private void handleImageString(final String str) {
        if (HostedStringDeduplication.isInternedString(str)) {
            /* The string is interned by the host VM, so it must also be interned in our image. */
            assert internedStrings.containsKey(str) || internStringsPhase.isAllowed() : "Should not intern string during phase " + internStringsPhase.toString();
            internedStrings.put(str, str);
        }
    }

    /**
     * It has been determined that an object should be added to the model of the native image heap.
     * This is the mechanics of recursively adding the object and all its fields and array elements
     * to the model of the native image heap.
     */
    private void addObjectToImageHeap(final JavaConstant constant, boolean immutableFromParent, final int identityHashCode, final Object reason) {

        final HostedType type = hMetaAccess.lookupJavaType(constant);
        final DynamicHub hub = type.getHub();
        final ObjectInfo info;

        boolean immutable = immutableFromParent || isKnownImmutableConstant(constant);
        boolean written = false;
        boolean references = false;
        boolean relocatable = false; /* always false when !spawnIsolates() */

        if (!type.isInstantiated()) {
            StringBuilder msg = new StringBuilder();
            msg.append("Image heap writing found an object whose type was not marked as instantiated by the static analysis: ");
            msg.append(type.toJavaName(true)).append("  (").append(type).append(")");
            msg.append(System.lineSeparator()).append("  reachable through:").append(System.lineSeparator());
            fillReasonStack(msg, reason);
            VMError.shouldNotReachHere(msg.toString());
        }

        if (type.isInstanceClass()) {
            final HostedInstanceClass clazz = (HostedInstanceClass) type;
            // If the type has a monitor field, it has a reference field that is written.
            if (clazz.getMonitorFieldOffset() != 0) {
                written = true;
                references = true;
                // also not immutable: users of registerAsImmutable() must take precautions
            }

            Set<HostedField> ignoredFields;
            Object hybridArray;
            final long size;

            if (dynamicHubLayout.isDynamicHub(clazz)) {
                /*
                 * DynamicHubs' typeIdSlots and vTable fields are written within the object. They
                 * can never be duplicated, i.e. written as a separate object. We use the blacklist
                 * to check this.
                 */
                if (SubstrateOptions.closedTypeWorld()) {
                    Object typeIDSlots = readInlinedField(dynamicHubLayout.closedTypeWorldTypeCheckSlotsField, constant);
                    assert typeIDSlots != null : "Cannot read value for field " + dynamicHubLayout.closedTypeWorldTypeCheckSlotsField.format("%H.%n");
                    blacklist.add(typeIDSlots);
                } else {
                    if (SubstrateUtil.assertionsEnabled()) {
                        Object typeIDSlots = readInlinedField(dynamicHubLayout.closedTypeWorldTypeCheckSlotsField, constant);
                        assert typeIDSlots == null : typeIDSlots;
                    }
                }

                Object vTable = readInlinedField(dynamicHubLayout.vTableField, constant);
                hybridArray = vTable;
                assert vTable != null : "Cannot read value for field " + dynamicHubLayout.vTableField.format("%H.%n");
                blacklist.add(vTable);

                size = dynamicHubLayout.getTotalSize(Array.getLength(vTable));
                ignoredFields = dynamicHubLayout.getIgnoredFields();

            } else if (HybridLayout.isHybrid(clazz)) {
                HybridLayout hybridLayout = hybridLayouts.get(clazz);
                if (hybridLayout == null) {
                    hybridLayout = new HybridLayout(clazz, objectLayout, hMetaAccess);
                    hybridLayouts.put(clazz, hybridLayout);
                }

                /*
                 * The hybrid array is written within the hybrid object. If the hybrid object
                 * declares that they can never be duplicated, i.e. written as a separate object, we
                 * ensure that they are never duplicated. We use the blacklist to check that.
                 */
                boolean shouldBlacklist = !HybridLayout.canHybridFieldsBeDuplicated(clazz);
                HostedField hybridArrayField = hybridLayout.getArrayField();
                hybridArray = readInlinedField(hybridArrayField, constant);
                ignoredFields = Set.of(hybridArrayField);
                if (hybridArray != null && shouldBlacklist) {
                    blacklist.add(hybridArray);
                    written = true;
                }

                assert hybridArray != null : "Cannot read value for field " + hybridArrayField.format("%H.%n");
                size = hybridLayout.getTotalSize(Array.getLength(hybridArray), true);
            } else {
                ignoredFields = Set.of();
                hybridArray = null;
                size = LayoutEncoding.getPureInstanceSize(hub, true).rawValue();
            }

            info = addToImageHeap(constant, clazz, size, identityHashCode, reason);
            if (processBaseLayerConstant(constant, info)) {
                return;
            }
            try {
                recursiveAddObject(hub, false, info);
                // Recursively add all the fields of the object.
                final boolean fieldsAreImmutable = hMetaAccess.isInstanceOf(constant, String.class);
                for (HostedField field : clazz.getInstanceFields(true)) {
                    boolean fieldRelocatable = false;
                    /*
                     * Fields that are only available after heap layout, such as
                     * StringInternSupport.imageInternedStrings and all ImageHeapInfo fields will
                     * not be processed.
                     */
                    if (field.isRead() && field.isValueAvailable() && !ignoredFields.contains(field)) {
                        if (field.getJavaKind() == JavaKind.Object) {
                            assert field.hasLocation();
                            JavaConstant fieldValueConstant = hConstantReflection.readFieldValue(field, constant);
                            if (fieldValueConstant.getJavaKind() == JavaKind.Object) {
                                if (spawnIsolates()) {
                                    fieldRelocatable = fieldValueConstant instanceof RelocatableConstant;
                                }
                                recursiveAddConstant(fieldValueConstant, fieldsAreImmutable, info);
                                references = true;
                            }
                        }
                        /*
                         * The analysis considers relocatable pointers to be written because their
                         * eventual value is assigned at runtime by the dynamic linker and it cannot
                         * be inlined. Relocatable pointers are read-only for our purposes, however.
                         */
                        relocatable = relocatable || fieldRelocatable;
                    }
                    written = written || ((field.isWritten() || !field.isValueAvailable()) && !field.isFinal() && !fieldRelocatable);
                }
                if (hybridArray instanceof Object[]) {
                    relocatable = addArrayElements((Object[]) hybridArray, relocatable, info);
                    references = true;
                }
            } catch (AnalysisError.TypeNotFoundError ex) {
                throw reportIllegalType(ex.getType(), info);
            }

        } else if (type.isArray()) {
            HostedArrayClass clazz = (HostedArrayClass) type;
            int length = hConstantReflection.readArrayLength(constant);
            final long size = objectLayout.getArraySize(type.getComponentType().getStorageKind(), length, true);
            info = addToImageHeap(constant, clazz, size, identityHashCode, reason);
            if (processBaseLayerConstant(constant, info)) {
                return;
            }
            try {
                recursiveAddObject(hub, false, info);
                if (hMetaAccess.isInstanceOf(constant, Object[].class)) {
                    VMError.guarantee(constant instanceof ImageHeapConstant, "Expected an ImageHeapConstant, found %s", constant);
                    relocatable = addConstantArrayElements(constant, length, false, info);
                    references = true;
                }
                written = true; /* How to know if any of the array elements are written? */
            } catch (AnalysisError.TypeNotFoundError ex) {
                throw reportIllegalType(ex.getType(), info);
            }

        } else {
            throw shouldNotReachHereUnexpectedInput(type); // ExcludeFromJacocoGeneratedReport
        }

        if (relocatable && !isKnownImmutableConstant(constant)) {
            /* The constant comes from the base image and is immutable */
            if (!(constant instanceof ImageHeapConstant imageHeapConstant && imageHeapConstant.isInBaseLayer())) {
                VMError.shouldNotReachHere("Object with relocatable pointers must be explicitly immutable: " + hUniverse.getSnippetReflection().asObject(Object.class, constant));
            }
        }
        heapLayouter.assignObjectToPartition(info, !written || immutable, references, relocatable);
    }

    /**
     * For base layer constants reuse the base layer absolute offset and assign the object to a
     * pseudo-partition. We do not process this object recursively, i.e., following fields and array
     * elements, we only want the JavaConstant -> ObjectInfo mapping for base layer constants that
     * are reachable from regular constants in this layer.
     */
    private boolean processBaseLayerConstant(JavaConstant constant, ObjectInfo info) {
        if (((ImageHeapConstant) constant).isInBaseLayer()) {
            info.setOffsetInPartition(aUniverse.getImageLayerLoader().getObjectOffset(constant));
            info.setHeapPartition(BASE_LAYER_PARTITION);
            return true;
        }
        return false;
    }

    private static HostedType requireType(Optional<HostedType> optionalType, Object object, Object reason) {
        if (optionalType.isEmpty()) {
            throw reportIllegalType(object, reason, "Analysis type is missing for hosted object of " + object.getClass().getTypeName() + " class.");
        }
        HostedType hostedType = optionalType.get();
        if (!hostedType.isInstantiated()) {
            throw reportIllegalType(object, reason, "Type " + hostedType.toJavaName() + " was not marked instantiated.");
        }
        return hostedType;
    }

    static RuntimeException reportIllegalType(Object object, Object reason) {
        throw reportIllegalType(object, reason, "");
    }

    static RuntimeException reportIllegalType(Object object, Object reason, String problem) {
        StringBuilder msg = new StringBuilder();
        msg.append("Problem during heap layout: ").append(problem).append(" ");
        msg.append("The static analysis may have missed a type. ");
        msg.append("Did a static field or an object referenced from a static field change during native image generation? ");
        msg.append("For example, a lazily initialized cache could have been initialized during image generation, in which case ");
        msg.append("you need to force eager initialization of the cache before static analysis or reset the cache using a field ");
        msg.append("value recomputation.").append(System.lineSeparator()).append("    ");
        if (object instanceof DynamicHub) {
            msg.append("class: ").append(((DynamicHub) object).getName());
        } else if (object instanceof ResolvedJavaType) {
            msg.append("class: ").append(((ResolvedJavaType) object).toJavaName(true));
        } else {
            msg.append("object: ").append(object).append("  of class: ").append(object.getClass().getTypeName());
        }
        msg.append(System.lineSeparator()).append("  reachable through:").append(System.lineSeparator());
        fillReasonStack(msg, reason);
        throw UserError.abort("%s", msg);
    }

    private static StringBuilder fillReasonStack(StringBuilder msg, Object reason) {
        if (reason instanceof ObjectInfo) {
            ObjectInfo info = (ObjectInfo) reason;
            msg.append("    object: ").append(info.getObject()).append("  of class: ").append(info.getObject().getClass().getTypeName()).append(System.lineSeparator());
            return fillReasonStack(msg, info.getMainReason());
        }
        return msg.append("    root: ").append(reason).append(System.lineSeparator());
    }

    /**
     * Determine if a constant will be immutable in the native image heap.
     */
    private boolean isKnownImmutableConstant(JavaConstant constant) {
        if (constant instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
            /* A simulated ImageHeapConstant cannot be marked as immutable. */
            return false;
        }
        Object obj = hUniverse.getSnippetReflection().asObject(Object.class, constant);
        return UniverseBuilder.isKnownImmutableType(obj.getClass()) || knownImmutableObjects.contains(obj);
    }

    /** Add an object to the model of the native image heap. */
    private ObjectInfo addToImageHeap(Object object, HostedClass clazz, long size, int identityHashCode, Object reason) {
        return addToImageHeap(hUniverse.getSnippetReflection().forObject(object), clazz, size, identityHashCode, reason);
    }

    private ObjectInfo addToImageHeap(JavaConstant add, HostedClass clazz, long size, int identityHashCode, Object reason) {
        VMError.guarantee(add instanceof ImageHeapConstant, "Expected an ImageHeapConstant, found %s", add);
        VMError.guarantee(!CompressibleConstant.isCompressed(add), "Constants added to the image heap must be uncompressed.");
        ObjectInfo info = new ObjectInfo((ImageHeapConstant) add, size, clazz, identityHashCode, reason);
        ObjectInfo previous = objects.putIfAbsent(add, info);
        VMError.guarantee(previous == null, "Found an existing object info associated to constant %s", add);
        return info;
    }

    /**
     * This method allows adding objects to the image heap at a point in time when the image heap is
     * already considered as complete. Only the given object is added to the image heap. Referenced
     * objects are not processed recursively. Use this method with care.
     */
    @Override
    public ObjectInfo addLateToImageHeap(Object object, Object reason) {
        assert !(object instanceof DynamicHub) : "needs a different identity hashcode";
        assert !(object instanceof String) : "needs String interning";
        aUniverse.getHeapScanner().rescanObject(object, OtherReason.LATE_SCAN);

        final Optional<HostedType> optionalType = hMetaAccess.optionalLookupJavaType(object.getClass());
        HostedType type = requireType(optionalType, object, reason);
        return addToImageHeap(object, (HostedClass) type, getSize(object, type), System.identityHashCode(object), reason);
    }

    private long getSize(Object object, HostedType type) {
        if (type.isInstanceClass()) {
            HostedInstanceClass clazz = (HostedInstanceClass) type;
            assert !HostedConfiguration.isArrayLikeLayout(clazz) : type;
            return LayoutEncoding.getPureInstanceSize(clazz.getHub(), true).rawValue();
        } else if (type.isArray()) {
            return objectLayout.getArraySize(type.getComponentType().getStorageKind(), Array.getLength(object), true);
        } else {
            throw shouldNotReachHereUnexpectedInput(type); // ExcludeFromJacocoGeneratedReport
        }
    }

    // Deep-copy an array from the host heap to the model of the native image heap.
    private boolean addArrayElements(Object[] array, boolean otherFieldsRelocatable, Object reason) {
        boolean relocatable = otherFieldsRelocatable;
        for (Object element : array) {
            Object value = aUniverse.replaceObject(element);
            if (spawnIsolates()) {
                relocatable = relocatable || value instanceof RelocatedPointer;
            }
            recursiveAddObject(value, false, reason);
        }
        return relocatable;
    }

    private boolean addConstantArrayElements(JavaConstant array, int length, boolean otherFieldsRelocatable, Object reason) {
        boolean relocatable = otherFieldsRelocatable;
        for (int idx = 0; idx < length; idx++) {
            JavaConstant value = hConstantReflection.readArrayElement(array, idx);
            /* Object replacement is done as part as constant refection. */
            if (spawnIsolates()) {
                relocatable = relocatable || value instanceof RelocatableConstant;
            }
            recursiveAddConstant(value, false, reason);
        }
        return relocatable;
    }

    /*
     * Break recursion using a worklist, to support large object graphs that would lead to a stack
     * overflow.
     */
    private void recursiveAddObject(Object original, boolean immutableFromParent, Object reason) {
        if (original != null) {
            addObjectWorklist.push(new AddObjectData(hUniverse.getSnippetReflection().forObject(original), immutableFromParent, reason));
        }
    }

    private void recursiveAddConstant(JavaConstant constant, boolean immutableFromParent, Object reason) {
        if (constant.isNonNull()) {
            addObjectWorklist.push(new AddObjectData(constant, immutableFromParent, reason));
        }
    }

    private void processAddObjectWorklist() {
        while (!addObjectWorklist.isEmpty()) {
            AddObjectData data = addObjectWorklist.pop();
            addConstant(data.original, data.immutableFromParent, data.reason);
        }
    }

    static class AddObjectData {

        AddObjectData(JavaConstant original, boolean immutableFromParent, Object reason) {
            this.original = original;
            this.immutableFromParent = immutableFromParent;
            this.reason = reason;
        }

        final JavaConstant original;
        final boolean immutableFromParent;
        final Object reason;
    }

    public final class ObjectInfo implements ImageHeapObject {
        private final ImageHeapConstant constant;
        private final HostedClass clazz;
        private final long size;
        private final int identityHashCode;
        private ImageHeapPartition partition;
        private long offsetInPartition;
        /**
         * For debugging only: the reason why this object is in the native image heap.
         *
         * This is either another ObjectInfo, saying which object refers to this object, eventually
         * a root object which refers to this object, or is a String explaining why this object is
         * in the heap, or an {@link HeapInclusionReason}, or a {@link HostedField}.
         */
        private final Object reason;

        ObjectInfo(ImageHeapConstant constant, long size, HostedClass clazz, int identityHashCode, Object reason) {
            this.constant = constant;
            this.clazz = clazz;
            this.partition = null;
            this.offsetInPartition = -1L;
            this.size = size;
            this.identityHashCode = identityHashCode;

            // For diagnostic purposes only
            this.reason = reason;
            if (objectReachabilityInfo != null) {
                objectReachabilityInfo.put(this, new ObjectReachabilityInfo(this, reason));
            }
        }

        @Override
        public Object getObject() {
            return hUniverse.getSnippetReflection().asObject(Object.class, constant);
        }

        @Override
        public Class<?> getObjectClass() {
            return clazz.getJavaClass();
        }

        @Override
        public ImageHeapConstant getConstant() {
            return constant;
        }

        public HostedClass getClazz() {
            return clazz;
        }

        @Override
        public long getOffset() {
            assert offsetInPartition >= 0;
            assert partition != null;
            return partition.getStartOffset() + offsetInPartition;
        }

        @Override
        public void setOffsetInPartition(long value) {
            assert this.offsetInPartition == -1L && value >= 0;
            this.offsetInPartition = value;
        }

        @Override
        public ImageHeapPartition getPartition() {
            return partition;
        }

        @Override
        public void setHeapPartition(ImageHeapPartition value) {
            assert this.partition == null;
            this.partition = value;
        }

        @Override
        public long getSize() {
            return size;
        }

        int getIdentityHashCode() {
            return identityHashCode;
        }

        Object getMainReason() {
            return this.reason;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(constant.getType().toJavaName(true)).append(":").append(identityHashCode).append(" -> ");
            Object cur = getMainReason();
            Object prev = null;
            boolean skipped = false;
            while (cur instanceof ObjectInfo) {
                skipped = prev != null;
                prev = cur;
                cur = ((ObjectInfo) cur).getMainReason();
            }
            if (skipped) {
                result.append("... -> ");
            }
            if (prev != null) {
                result.append(prev);
            } else {
                result.append(cur);
            }
            return result.toString();
        }

    }

    protected static final class Phase {
        public static Phase factory() {
            return new Phase();
        }

        public boolean isBefore() {
            return (value == PhaseValue.BEFORE);
        }

        public void allow() {
            assert (value == PhaseValue.BEFORE) : "Can not allow while in phase " + value.toString();
            value = PhaseValue.ALLOWED;
        }

        void disallow() {
            assert (value == PhaseValue.ALLOWED) : "Can not disallow while in phase " + value.toString();
            value = PhaseValue.AFTER;
        }

        public boolean isAllowed() {
            return (value == PhaseValue.ALLOWED);
        }

        @Override
        public String toString() {
            return value.toString();
        }

        protected Phase() {
            value = PhaseValue.BEFORE;
        }

        private PhaseValue value;

        private enum PhaseValue {
            BEFORE,
            ALLOWED,
            AFTER
        }
    }

    enum HeapInclusionReason {
        InternedStringsTable,
        FillerObject,
        StaticObjectFields,
        DataSection,
        StaticPrimitiveFields,
        Resource,
    }

    final class ObjectReachabilityInfo {
        private final LinkedHashSet<Object> allReasons;
        private int objectReachabilityGroup;

        ObjectReachabilityInfo(ObjectInfo info, Object firstReason) {
            this.allReasons = new LinkedHashSet<>();
            this.allReasons.add(firstReason);
            this.objectReachabilityGroup = ObjectReachabilityGroup.getFlagForObjectInfo(info, firstReason, objectReachabilityInfo);
        }

        void addReason(Object additionalReason) {
            this.allReasons.add(additionalReason);
            this.objectReachabilityGroup |= ObjectReachabilityGroup.getByReason(additionalReason, objectReachabilityInfo);
        }

        Set<Object> getAllReasons() {
            return this.allReasons;
        }

        int getObjectReachabilityGroup() {
            return objectReachabilityGroup;
        }

        boolean objectReachableFrom(ObjectReachabilityGroup other) {
            return (this.objectReachabilityGroup & other.flag) != 0;
        }
    }

    /**
     * For diagnostic purposes only when
     * {@link ImageHeapConnectedComponentsFeature.Options#PrintImageHeapConnectedComponents} is
     * enabled.
     */
    enum ObjectReachabilityGroup {
        Resources(1 << 1, "Resources byte arrays", "resources"),
        InternedStringsTable(1 << 2, "Interned strings table", "internedStringsTable"),
        DynamicHubs(1 << 3, "Class data", "classData"),
        ImageCodeInfo(1 << 4, "Code metadata", "codeMetadata"),
        MethodOrStaticField(1 << 5, "Connected components accessed from method or a static field", "connectedComponents"),
        Other(1 << 6, "Other", "other");

        public final int flag;
        public final String description;
        public final String name;

        ObjectReachabilityGroup(int flag, String description, String name) {
            this.flag = flag;
            this.description = description;
            this.name = name;
        }

        static int getFlagForObjectInfo(ObjectInfo object, Object firstReason, Map<ObjectInfo, ObjectReachabilityInfo> additionalReasonInfoHashMap) {
            int result = 0;
            if (object.getObjectClass().equals(ImageCodeInfo.class)) {
                result |= ImageCodeInfo.flag;
            }
            if (object.getObject() != null && (object.getObject().getClass().equals(DynamicHub.class) || object.getObject().getClass().equals(DynamicHubCompanion.class))) {
                result |= DynamicHubs.flag;
            }
            result |= getByReason(firstReason, additionalReasonInfoHashMap);
            return result;
        }

        static int getByReason(Object reason, Map<ObjectInfo, ObjectReachabilityInfo> additionalReasonInfoHashMap) {
            if (reason.equals(HeapInclusionReason.InternedStringsTable)) {
                return ObjectReachabilityGroup.InternedStringsTable.flag;
            } else if (reason.equals(HeapInclusionReason.Resource)) {
                return ObjectReachabilityGroup.Resources.flag;
            } else if (reason instanceof String || reason instanceof HostedField) {
                return ObjectReachabilityGroup.MethodOrStaticField.flag;
            } else if (reason instanceof ObjectInfo) {
                ObjectInfo r = (ObjectInfo) reason;
                return additionalReasonInfoHashMap.get(r).getObjectReachabilityGroup();
            }
            return ObjectReachabilityGroup.Other.flag;
        }
    }
}

/**
 * A pseudo-partition necessary for {@link ImageHeapObject}s that refer to base layer constants,
 * i.e., they are not actually written in current layer's heap. Their offset is absolute (not
 * relative to a partition start offset) and is serialized from the base layer.
 */
final class BaseLayerPartition implements ImageHeapPartition {
    /** Zero so that the partition-relative offsets are always absolute. */
    @Override
    public long getStartOffset() {
        return 0;
    }

    @Override
    public String getName() {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public long getSize() {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }
}
