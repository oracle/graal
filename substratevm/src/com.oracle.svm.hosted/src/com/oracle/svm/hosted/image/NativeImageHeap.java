/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.jdk.StringInternSupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
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
    private final HostedUniverse universe;
    private final AnalysisUniverse aUniverse;
    private final HostedMetaAccess metaAccess;
    private final ObjectLayout objectLayout;
    private final ImageHeapLayouter heapLayouter;
    private final int minInstanceSize;
    private final int minArraySize;

    /**
     * A Map from objects at construction-time to native image objects.
     *
     * More than one host object may be represented by a single native image object.
     */
    protected final IdentityHashMap<Object, ObjectInfo> objects = new IdentityHashMap<>();

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

    public NativeImageHeap(AnalysisUniverse aUniverse, HostedUniverse universe, HostedMetaAccess metaAccess, ImageHeapLayouter heapLayouter) {
        this.aUniverse = aUniverse;
        this.universe = universe;
        this.metaAccess = metaAccess;

        this.objectLayout = ConfigurationValues.getObjectLayout();
        this.heapLayouter = heapLayouter;

        this.minInstanceSize = objectLayout.getMinimumInstanceObjectSize();
        this.minArraySize = objectLayout.getMinimumArraySize();
        assert assertFillerObjectSizes();
    }

    @Override
    public Collection<ObjectInfo> getObjects() {
        return objects.values();
    }

    public int getObjectCount() {
        return objects.size();
    }

    public ObjectInfo getObjectInfo(Object obj) {
        return objects.get(obj);
    }

    protected HostedUniverse getUniverse() {
        return universe;
    }

    protected HostedMetaAccess getMetaAccess() {
        return metaAccess;
    }

    protected AnalysisUniverse getAnalysisUniverse() {
        return aUniverse;
    }

    protected HybridLayout<?> getHybridLayout(HostedClass clazz) {
        return hybridLayouts.get(clazz);
    }

    protected boolean isBlacklisted(Object obj) {
        return blacklist.contains(obj);
    }

    protected ObjectLayout getObjectLayout() {
        return objectLayout;
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

        HostedField internedStringsField = (HostedField) StringInternFeature.getInternedStringsField(metaAccess);
        boolean usesInternedStrings = internedStringsField.isAccessed();

        if (usesInternedStrings) {
            /*
             * Ensure that the hub of the String[] array (used for the interned objects) is written.
             */
            addObject(getMetaAccess().lookupJavaType(String[].class).getHub(), false, "internedStrings table");
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

            addObject(imageInternedStrings, true, "internedStrings table");

            // Process any objects that were transitively added to the heap.
            processAddObjectWorklist();
        } else {
            internStringsPhase.disallow();
        }

        addObjectsPhase.disallow();
        assert addObjectWorklist.isEmpty();
    }

    private static Object readObjectField(HostedField field, JavaConstant receiver) {
        return SubstrateObjectConstant.asObject(field.readStorageValue(receiver));
    }

    private void addStaticFields() {
        addObject(StaticFieldsSupport.getStaticObjectFields(), false, "staticObjectFields");
        addObject(StaticFieldsSupport.getStaticPrimitiveFields(), false, "staticPrimitiveFields");

        /*
         * We only have empty holder arrays for the static fields, so we need to add static object
         * fields manually.
         */
        for (HostedField field : getUniverse().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.hasLocation() && field.getType().getStorageKind() == JavaKind.Object) {
                assert field.isWritten() || MaterializedConstantFields.singleton().contains(field.wrapped);
                addObject(readObjectField(field, null), false, field);
            }
        }
    }

    public void registerAsImmutable(Object object) {
        assert addObjectsPhase.isBefore() : "Registering immutable object too late: phase: " + addObjectsPhase.toString();
        knownImmutableObjects.add(object);
    }

    /**
     * If necessary, add an object to the model of the native image heap.
     *
     * Various transformations are done from objects in the hosted heap to the native image heap.
     * Not every object is added to the heap, for various reasons.
     */
    public void addObject(final Object original, boolean immutableFromParent, final Object reason) {
        assert addObjectsPhase.isAllowed() : "Objects cannot be added at phase: " + addObjectsPhase.toString() + " with reason: " + reason;

        if (original == null || original instanceof WordBase) {
            return;
        }
        if (original instanceof Class) {
            throw VMError.shouldNotReachHere("Must not have Class in native image heap: " + original);
        }
        if (original instanceof DynamicHub && ((DynamicHub) original).getClassInitializationInfo() == null) {
            /*
             * All DynamicHub instances written into the image heap must have a
             * ClassInitializationInfo, otherwise we can get a NullPointerException at run time.
             * When this check fails, then the DynamicHub has not been seen during static analysis.
             * Since many other objects are reachable from the DynamicHub (annotations, enum values,
             * ...) this can also mean that types are used in the image that the static analysis has
             * not seen - so this check actually protects against much more than just missing class
             * initialization information.
             */
            throw reportIllegalType(original, reason);
        }

        int identityHashCode;
        if (original instanceof DynamicHub) {
            /*
             * We need to use the identity hash code of the original java.lang.Class object and not
             * of the DynamicHub, so that hash maps that are filled during image generation and use
             * Class keys still work at run time.
             */
            identityHashCode = System.identityHashCode(universe.hostVM().lookupType((DynamicHub) original).getJavaClass());
        } else {
            identityHashCode = System.identityHashCode(original);
        }
        VMError.guarantee(identityHashCode != 0, "0 is used as a marker value for 'hash code not yet computed'");

        if (original instanceof String) {
            handleImageString((String) original);
        }

        final ObjectInfo existing = objects.get(original);
        if (existing == null) {
            addObjectToBootImageHeap(original, immutableFromParent, identityHashCode, reason);
        }
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
            assert objectLayout.getArraySize(JavaKind.Int, arrayLength) == size;
            return addLateToImageHeap(new int[arrayLength], "Filler object");
        } else if (size >= minInstanceSize) {
            return addLateToImageHeap(new FillerObject(), "Filler object");
        } else {
            return null;
        }
    }

    private boolean assertFillerObjectSizes() {
        assert minArraySize == objectLayout.getArraySize(JavaKind.Int, 0);

        HostedType filler = metaAccess.lookupJavaType(FillerObject.class);
        UnsignedWord fillerSize = LayoutEncoding.getInstanceSize(filler.getHub().getLayoutEncoding());
        assert fillerSize.equal(minInstanceSize);

        assert minInstanceSize * 2 >= minArraySize : "otherwise, we might need more than one non-array object";

        return true;
    }

    private void handleImageString(final String str) {
        forceHashCodeComputation(str);
        if (HostedStringDeduplication.isInternedString(str)) {
            /* The string is interned by the host VM, so it must also be interned in our image. */
            assert internedStrings.containsKey(str) || internStringsPhase.isAllowed() : "Should not intern string during phase " + internStringsPhase.toString();
            internedStrings.put(str, str);
        }
    }

    /**
     * For immutable Strings in the native image heap, force eager computation of the hash field.
     */
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "eager hash field computation")
    private static void forceHashCodeComputation(final String str) {
        str.hashCode();
    }

    /**
     * It has been determined that an object should be added to the model of the native image heap.
     * This is the mechanics of recursively adding the object and all its fields and array elements
     * to the model of the native image heap.
     */
    private void addObjectToBootImageHeap(final Object object, boolean immutableFromParent, final int identityHashCode, final Object reason) {

        final Optional<HostedType> optionalType = getMetaAccess().optionalLookupJavaType(object.getClass());
        final HostedType type = requireType(optionalType, object, reason);
        final DynamicHub hub = type.getHub();
        final ObjectInfo info;

        boolean immutable = immutableFromParent || isKnownImmutable(object);
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

            final JavaConstant con = SubstrateObjectConstant.forObject(object);
            HostedField hybridBitsetField = null;
            HostedField hybridArrayField = null;
            Object hybridArray = null;
            final long size;

            if (HybridLayout.isHybrid(clazz)) {
                HybridLayout<?> hybridLayout = hybridLayouts.get(clazz);
                if (hybridLayout == null) {
                    hybridLayout = new HybridLayout<>(clazz, objectLayout);
                    hybridLayouts.put(clazz, hybridLayout);
                }

                /*
                 * The hybrid array and bit set are written within the hybrid object. So they may
                 * not be written as separate objects. We use the blacklist to check that.
                 */
                hybridBitsetField = hybridLayout.getBitsetField();
                if (hybridBitsetField != null) {
                    Object bitSet = readObjectField(hybridBitsetField, con);
                    if (bitSet != null) {
                        blacklist.add(bitSet);
                    }
                }

                hybridArrayField = hybridLayout.getArrayField();
                hybridArray = readObjectField(hybridArrayField, con);
                if (hybridArray != null) {
                    blacklist.add(hybridArray);
                    written = true;
                }

                size = hybridLayout.getTotalSize(Array.getLength(hybridArray));
            } else {
                size = LayoutEncoding.getInstanceSize(hub.getLayoutEncoding()).rawValue();
            }

            info = addToImageHeap(object, clazz, size, identityHashCode, reason);
            try {
                recursiveAddObject(hub, false, info);
                // Recursively add all the fields of the object.
                final boolean fieldsAreImmutable = object instanceof String;
                for (HostedField field : clazz.getInstanceFields(true)) {
                    if (field.isAccessed() && !field.equals(hybridArrayField) && !field.equals(hybridBitsetField)) {
                        boolean fieldRelocatable = false;
                        if (field.getJavaKind() == JavaKind.Object) {
                            assert field.hasLocation();
                            JavaConstant fieldValueConstant = field.readValue(con);
                            if (fieldValueConstant.getJavaKind() == JavaKind.Object) {
                                Object fieldValue = SubstrateObjectConstant.asObject(fieldValueConstant);
                                if (spawnIsolates()) {
                                    fieldRelocatable = fieldValue instanceof RelocatedPointer;
                                }
                                recursiveAddObject(fieldValue, fieldsAreImmutable, info);
                                references = true;
                            }
                        }
                        /*
                         * The analysis considers relocatable pointers to be written because their
                         * eventual value is assigned at runtime by the dynamic linker and it cannot
                         * be inlined. Relocatable pointers are read-only for our purposes, however.
                         */
                        relocatable = relocatable || fieldRelocatable;
                        written = written || (field.isWritten() && !field.isFinal() && !fieldRelocatable);
                    }

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
            final long size = objectLayout.getArraySize(type.getComponentType().getStorageKind(), Array.getLength(object));
            info = addToImageHeap(object, clazz, size, identityHashCode, reason);
            try {
                recursiveAddObject(hub, false, info);
                if (object instanceof Object[]) {
                    relocatable = addArrayElements((Object[]) object, false, info);
                    references = true;
                }
                written = true; /* How to know if any of the array elements are written? */
            } catch (AnalysisError.TypeNotFoundError ex) {
                throw reportIllegalType(ex.getType(), info);
            }

        } else {
            throw shouldNotReachHere();
        }

        if (relocatable && !isKnownImmutable(object)) {
            VMError.shouldNotReachHere("Object with relocatable pointers must be explicitly immutable: " + object);
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
        throw UserError.abort(msg.toString());
    }

    private static StringBuilder fillReasonStack(StringBuilder msg, Object reason) {
        if (reason instanceof ObjectInfo) {
            ObjectInfo info = (ObjectInfo) reason;
            msg.append("    object: ").append(info.getObject()).append("  of class: ").append(info.getObject().getClass().getTypeName()).append(System.lineSeparator());
            return fillReasonStack(msg, info.reason);
        }
        return msg.append("    root: ").append(reason).append(System.lineSeparator());
    }

    /** Determine if an object in the host heap will be immutable in the native image heap. */
    private boolean isKnownImmutable(final Object obj) {
        if (obj instanceof String) {
            // Strings need to have their hash code set or they are not immutable.
            // If the hash is 0, then it will be recomputed again (and again)
            // so the String is not immutable.
            return obj.hashCode() != 0;
        }
        return UniverseBuilder.isKnownImmutableType(obj.getClass()) || knownImmutableObjects.contains(obj);
    }

    /** Add an object to the model of the native image heap. */
    private ObjectInfo addToImageHeap(Object object, HostedClass clazz, long size, int identityHashCode, Object reason) {
        ObjectInfo info = new ObjectInfo(object, size, clazz, identityHashCode, reason);
        assert !objects.containsKey(object);
        objects.put(object, info);
        return info;
    }

    /**
     * This method allows adding objects to the image heap at a point in time when the image heap is
     * already considered as complete. Only the given object is added to the image heap. Referenced
     * objects are not processed recursively. Use this method with care.
     */
    @Override
    public ObjectInfo addLateToImageHeap(Object object, String reason) {
        assert !(object instanceof DynamicHub) : "needs a different identity hashcode";
        assert !(object instanceof String) : "needs String interning";

        final Optional<HostedType> optionalType = getMetaAccess().optionalLookupJavaType(object.getClass());
        HostedType type = requireType(optionalType, object, reason);
        return addToImageHeap(object, (HostedClass) type, getSize(object, type), System.identityHashCode(object), reason);
    }

    private long getSize(Object object, HostedType type) {
        if (type.isInstanceClass()) {
            HostedInstanceClass clazz = (HostedInstanceClass) type;
            assert !HybridLayout.isHybrid(clazz);
            return LayoutEncoding.getInstanceSize(clazz.getHub().getLayoutEncoding()).rawValue();
        } else if (type.isArray()) {
            return objectLayout.getArraySize(type.getComponentType().getStorageKind(), Array.getLength(object));
        } else {
            throw shouldNotReachHere();
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

    /*
     * Break recursion using a worklist, to support large object graphs that would lead to a stack
     * overflow.
     */
    private void recursiveAddObject(Object original, boolean immutableFromParent, Object reason) {
        if (original != null) {
            addObjectWorklist.push(new AddObjectData(original, immutableFromParent, reason));
        }
    }

    private void processAddObjectWorklist() {
        while (!addObjectWorklist.isEmpty()) {
            AddObjectData data = addObjectWorklist.pop();
            addObject(data.original, data.immutableFromParent, data.reason);
        }
    }

    static class AddObjectData {

        AddObjectData(Object original, boolean immutableFromParent, Object reason) {
            this.original = original;
            this.immutableFromParent = immutableFromParent;
            this.reason = reason;
        }

        final Object original;
        final boolean immutableFromParent;
        final Object reason;
    }

    public static final class ObjectInfo implements ImageHeapObject {
        private final Object object;
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
         * in the heap. The reason field is like a "comes from" pointer.
         */
        final Object reason;

        ObjectInfo(Object object, long size, HostedClass clazz, int identityHashCode, Object reason) {
            this.object = object;
            this.clazz = clazz;
            this.partition = null;
            this.offsetInPartition = -1L;
            this.size = size;
            this.identityHashCode = identityHashCode;
            this.reason = reason;
        }

        @Override
        public Object getObject() {
            return object;
        }

        public HostedClass getClazz() {
            return clazz;
        }

        /**
         * The offset of an object within a partition. <em>Probably you want
         * {@link #getAddress()}</em>.
         */
        @Override
        public long getOffsetInPartition() {
            assert offsetInPartition >= 0;
            return offsetInPartition;
        }

        @Override
        public void setOffsetInPartition(long value) {
            assert this.offsetInPartition == -1L && value >= 0;
            this.offsetInPartition = value;
        }

        /**
         * Returns the index into the {@link RelocatableBuffer} to which this object is written.
         */
        public int getIndexInBuffer(long index) {
            long result = getPartition().getStartOffset() + getOffsetInPartition() + index;
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
            return Heap.getHeap().getImageHeapOffsetInAddressSpace() + getPartition().getStartOffset() + getOffsetInPartition();
        }

        /**
         * Similar to {@link #getAddress()} but this method is typically used to get the address of
         * a field within an object.
         */
        public long getAddress(long offset) {
            assert offset >= 0 && offset < getSize() : "Index: " + offset + " out of bounds: [0 .. " + getSize() + ").";
            return getAddress() + offset;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public ImageHeapPartition getPartition() {
            return partition;
        }

        int getIdentityHashCode() {
            return identityHashCode;
        }

        @Override
        public void setHeapPartition(ImageHeapPartition value) {
            assert this.partition == null;
            this.partition = value;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(getObject().getClass().getName()).append(" -> ");
            Object cur = reason;
            Object prev = null;
            boolean skipped = false;
            while (cur instanceof ObjectInfo) {
                skipped = prev != null;
                prev = cur;
                cur = ((ObjectInfo) cur).reason;
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
}
