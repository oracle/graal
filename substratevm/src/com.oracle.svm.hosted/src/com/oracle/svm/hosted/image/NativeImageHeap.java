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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.type.CompressibleConstant;
import org.graalvm.compiler.core.common.type.TypedConstant;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.jdk.StringInternSupport;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.MaterializedConstantFields;
import com.oracle.svm.hosted.meta.UniverseBuilder;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class keeps track of all objects that should be part of the native image heap. It should not
 * make any assumptions about the final layout of the image heap.
 */
public final class NativeImageHeap implements ImageHeap {
    public final AnalysisUniverse aUniverse;
    public final HostedUniverse hUniverse;
    public final HostedMetaAccess hMetaAccess;
    public final HostedConstantReflectionProvider hConstantReflection;
    public final ObjectLayout objectLayout;

    private final ImageHeapLayouter heapLayouter;
    private final int minInstanceSize;
    private final int minArraySize;

    /**
     * A Map from objects at construction-time to native image objects.
     *
     * More than one host object may be represented by a single native image object.
     */
    private final HashMap<JavaConstant, ObjectInfo> objects = new HashMap<>();

    /** Objects that must not be written to the native image heap. */
    private final Set<Object> blacklist = Collections.newSetFromMap(new IdentityHashMap<>());

    /** A map from hosted classes to classes that have hybrid layouts in the native image heap. */
    private final Map<HostedClass, HybridLayout<?>> hybridLayouts = new HashMap<>();

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
        return objects.get(hUniverse.getSnippetReflection().forObject(obj));
    }

    public ObjectInfo getConstantInfo(JavaConstant constant) {
        return objects.get(maybeUnwrap(uncompress(constant)));
    }

    protected HybridLayout<?> getHybridLayout(HostedClass clazz) {
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

        HostedField internedStringsField = (HostedField) StringInternFeature.getInternedStringsField(hMetaAccess);
        boolean usesInternedStrings = internedStringsField.isAccessed();

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
            addObject(imageInternedStrings, true, HeapInclusionReason.InternedStringsTable);

            // Process any objects that were transitively added to the heap.
            processAddObjectWorklist();
        } else {
            internStringsPhase.disallow();
        }

        addObjectsPhase.disallow();
        assert addObjectWorklist.isEmpty();
    }

    private Object readObjectField(HostedField field, JavaConstant receiver) {
        /*
         * This method is only used to read the special fields of hybrid objects, which are
         * currently not maintained as separate ImageHeapConstant and therefore cannot we read via
         * the snapshot heap.
         */
        JavaConstant hostedConstant = receiver;
        if (receiver instanceof ImageHeapConstant imageHeapConstant) {
            hostedConstant = imageHeapConstant.getHostedObject();
        }
        return hUniverse.getSnippetReflection().asObject(Object.class, hConstantReflection.readFieldValue(field, hostedConstant));
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
                JavaConstant constant = aUniverse.getSnippetReflection().forObject(cur);
                for (HostedField field : hMetaAccess.lookupJavaType(constant).getInstanceFields(true)) {
                    if (field.isAccessed() && field.getStorageKind() == JavaKind.Object) {
                        Object fieldValue = aUniverse.getSnippetReflection().asObject(Object.class, hConstantReflection.readFieldValue(field, constant));
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
                throw reportIllegalType(hUniverse.getSnippetReflection().asObject(Object.class, constant), reason);
            }
        }

        JavaConstant uncompressed = maybeUnwrap(uncompress(constant));

        int identityHashCode = computeIdentityHashCode(uncompressed);
        VMError.guarantee(identityHashCode != 0, "0 is used as a marker value for 'hash code not yet computed'");

        Object objectConstant = hUniverse.getSnippetReflection().asObject(Object.class, uncompressed);
        ImageHeapScanner.maybeForceHashCodeComputation(objectConstant);
        if (objectConstant instanceof String stringConstant) {
            handleImageString(stringConstant);
        }

        final ObjectInfo existing = objects.get(uncompressed);
        if (existing == null) {
            addObjectToImageHeap(uncompressed, immutableFromParent, identityHashCode, reason);
        } else if (objectReachabilityInfo != null) {
            objectReachabilityInfo.get(existing).addReason(reason);
        }
    }

    /**
     * When an object is represented as an {@link ImageHeapConstant} we unwrap it before using it as
     * a key for {@link NativeImageHeap#objects}. This is necessary to avoid duplication of
     * {@link ObjectInfo} for the same object. Eventually, there will be a complete shadow heap with
     * only {@link ImageHeapConstant} and this code will be removed.
     */
    private static JavaConstant maybeUnwrap(JavaConstant constant) {
        if (constant instanceof ImageHeapConstant ihc && ihc.getHostedObject() != null) {
            return uncompress(ihc.getHostedObject());
        }
        return constant;
    }

    /**
     * The constants stored in the image heap, i.g., the {@link #objects} map, are always
     * uncompressed. The same object info is returned whenever the map is queried regardless of the
     * compressed flag value.
     */
    private static JavaConstant uncompress(JavaConstant constant) {
        if (constant instanceof CompressibleConstant) {
            CompressibleConstant compressible = (CompressibleConstant) constant;
            if (compressible.isCompressed()) {
                return compressible.uncompress();
            }
        }
        return constant;
    }

    private static boolean isCompressed(JavaConstant constant) {
        if (constant instanceof CompressibleConstant) {
            CompressibleConstant compressible = (CompressibleConstant) constant;
            return compressible.isCompressed();
        }
        return false;
    }

    private static int computeIdentityHashCode(JavaConstant constant) {
        return ((TypedConstant) constant).getIdentityHashCode();
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

        if (type.isInstanceClass()) {
            final HostedInstanceClass clazz = (HostedInstanceClass) type;
            // If the type has a monitor field, it has a reference field that is written.
            if (clazz.getMonitorFieldOffset() != 0) {
                written = true;
                references = true;
                // also not immutable: users of registerAsImmutable() must take precautions
            }

            HostedField hybridTypeIDSlotsField = null;
            HostedField hybridArrayField = null;
            Object hybridArray = null;
            final long size;

            if (HybridLayout.isHybrid(clazz)) {
                HybridLayout<?> hybridLayout = hybridLayouts.get(clazz);
                if (hybridLayout == null) {
                    hybridLayout = new HybridLayout<>(clazz, objectLayout, hMetaAccess);
                    hybridLayouts.put(clazz, hybridLayout);
                }

                /*
                 * The hybrid array, bit set, and typeID array are written within the hybrid object.
                 * If the hybrid object declares that they can never be duplicated, i.e. written as
                 * a separate object, we ensure that they never are duplicated. We use the blacklist
                 * to check that.
                 */
                boolean shouldBlacklist = !HybridLayout.canHybridFieldsBeDuplicated(clazz);
                hybridTypeIDSlotsField = hybridLayout.getTypeIDSlotsField();
                if (hybridTypeIDSlotsField != null && shouldBlacklist) {
                    Object typeIDSlots = readObjectField(hybridTypeIDSlotsField, constant);
                    if (typeIDSlots != null) {
                        blacklist.add(typeIDSlots);
                    }
                }

                hybridArrayField = hybridLayout.getArrayField();
                hybridArray = readObjectField(hybridArrayField, constant);
                if (hybridArray != null && shouldBlacklist) {
                    blacklist.add(hybridArray);
                    written = true;
                }

                assert hybridArray != null : "Cannot read value for field " + hybridArrayField.format("%H.%n");
                size = hybridLayout.getTotalSize(Array.getLength(hybridArray), true);
            } else {
                size = LayoutEncoding.getPureInstanceSize(hub, true).rawValue();
            }

            info = addToImageHeap(constant, clazz, size, identityHashCode, reason);
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
                    if (field.isRead() && field.isValueAvailable() &&
                                    !field.equals(hybridArrayField) &&
                                    !field.equals(hybridTypeIDSlotsField)) {
                        if (field.getJavaKind() == JavaKind.Object) {
                            assert field.hasLocation();
                            JavaConstant fieldValueConstant = hConstantReflection.readFieldValue(field, constant);
                            if (fieldValueConstant.getJavaKind() == JavaKind.Object) {
                                if (spawnIsolates()) {
                                    fieldRelocatable = hMetaAccess.isInstanceOf(fieldValueConstant, RelocatedPointer.class);
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
            try {
                recursiveAddObject(hub, false, info);
                if (hMetaAccess.isInstanceOf(constant, Object[].class)) {
                    if (constant instanceof ImageHeapConstant) {
                        relocatable = addConstantArrayElements(constant, length, false, info);
                    } else {
                        Object object = hUniverse.getSnippetReflection().asObject(Object.class, constant);
                        relocatable = addArrayElements((Object[]) object, false, info);
                    }
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
            VMError.shouldNotReachHere("Object with relocatable pointers must be explicitly immutable: " + hUniverse.getSnippetReflection().asObject(Object.class, constant));
        }
        heapLayouter.assignObjectToPartition(info, !written || immutable, references, relocatable);
    }

    private static HostedType requireType(Optional<HostedType> optionalType, Object object, Object reason) {
        if (!optionalType.isPresent() || !optionalType.get().isInstantiated()) {
            throw reportIllegalType(object, reason);
        }
        return optionalType.get();
    }

    static RuntimeException reportIllegalType(Object object, Object reason) {
        StringBuilder msg = new StringBuilder();
        msg.append("Image heap writing found a class not seen during static analysis. ");
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
        JavaConstant hostedConstant = constant;
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            hostedConstant = imageHeapConstant.getHostedObject();
            if (hostedConstant == null) {
                /* A simulated ImageHeapConstant cannot be marked as immutable. */
                return false;
            }
        }
        Object obj = hUniverse.getSnippetReflection().asObject(Object.class, hostedConstant);
        return UniverseBuilder.isKnownImmutableType(obj.getClass()) || knownImmutableObjects.contains(obj);
    }

    /** Add an object to the model of the native image heap. */
    private ObjectInfo addToImageHeap(Object object, HostedClass clazz, long size, int identityHashCode, Object reason) {
        return addToImageHeap(hUniverse.getSnippetReflection().forObject(object), clazz, size, identityHashCode, reason);
    }

    private ObjectInfo addToImageHeap(JavaConstant add, HostedClass clazz, long size, int identityHashCode, Object reason) {
        JavaConstant constant = maybeUnwrap(add);
        ObjectInfo info = new ObjectInfo(constant, size, clazz, identityHashCode, reason);
        assert !objects.containsKey(constant) && !isCompressed(constant);
        objects.put(constant, info);
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

        final Optional<HostedType> optionalType = hMetaAccess.optionalLookupJavaType(object.getClass());
        HostedType type = requireType(optionalType, object, reason);
        return addToImageHeap(object, (HostedClass) type, getSize(object, type), System.identityHashCode(object), reason);
    }

    private long getSize(Object object, HostedType type) {
        if (type.isInstanceClass()) {
            HostedInstanceClass clazz = (HostedInstanceClass) type;
            assert !HybridLayout.isHybrid(clazz);
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
                relocatable = relocatable || hMetaAccess.isInstanceOf(value, RelocatedPointer.class);
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

    private final int imageHeapOffsetInAddressSpace = Heap.getHeap().getImageHeapOffsetInAddressSpace();

    public final class ObjectInfo implements ImageHeapObject {
        private final JavaConstant constant;
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

        ObjectInfo(JavaConstant constant, long size, HostedClass clazz, int identityHashCode, Object reason) {
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
        public JavaConstant getConstant() {
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

        /**
         * Returns the index into the {@link RelocatableBuffer} to which this object is written.
         */
        public int getIndexInBuffer(long index) {
            long result = getOffset() + index;
            return NumUtil.safeToInt(result);
        }

        /**
         * If heap base addressing is enabled, this returns the heap-base relative address of this
         * object. Otherwise, this returns the offset of the object within a native image section
         * (e.g., read-only or writable).
         */
        public long getAddress() {
            /*
             * At run-time, the image heap may be mapped in a way that there is some extra space at
             * the beginning of the heap. So, all heap-base-relative addresses must be adjusted by
             * that offset.
             */
            return imageHeapOffsetInAddressSpace + getOffset();
        }

        /**
         * Similar to {@link #getAddress()} but this method is typically used to get the address of
         * a field within an object.
         */
        public long getAddress(long delta) {
            assert delta >= 0 && delta < getSize() : "Index: " + delta + " out of bounds: [0 .. " + getSize() + ").";
            return getAddress() + delta;
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
            StringBuilder result = new StringBuilder(((TypedConstant) constant).getType(hMetaAccess).toJavaName(true)).append(":").append(identityHashCode).append(" -> ");
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
            if (object.getObject().getClass().equals(DynamicHub.class) || object.getObject().getClass().equals(DynamicHubCompanion.class)) {
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
