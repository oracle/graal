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
package com.oracle.svm.hosted.image;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.HostedIdentityHashCodeProvider;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NativeImageInfo;
import com.oracle.svm.core.hub.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.StringInternSupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class NativeImageHeap {

    @Fold
    static boolean useHeapBase() {
        return SubstrateOptions.UseHeapBaseRegister.getValue() && ImageSingletons.lookup(CompressEncoding.class).hasBase();
    }

    @Fold
    static boolean spawnIsolates() {
        return SubstrateOptions.SpawnIsolates.getValue() && useHeapBase();
    }

    @SuppressWarnings("try")
    public void addInitialObjects(DebugContext debug) {
        try (Indent indent = debug.logAndIndent("add initial objects to heap")) {
            addObjectsPhase.allow();
            internStringsPhase.allow();

            debug.log("initial add primitive static fields");
            addObject(debug, StaticFieldsSupport.getStaticPrimitiveFields(), false, false, "primitive static fields");

            debug.log("initial add static fields");
            addStaticFields(debug);
        }
    }

    public void addTrailingObjects(DebugContext debug) {
        // Process any remaining objects on the worklist, especially that might intern strings.
        processAddObjectWorklist(debug);

        HostedField internedStringsField = (HostedField) StringInternFeature.getInternedStringsField(metaAccess);
        boolean usesInternedStrings = internedStringsField.isAccessed();

        if (usesInternedStrings) {
            /*
             * Ensure that the hub of the String[] array (used for the interned objects) is written.
             */
            addObject(debug, getMetaAccess().lookupJavaType(String[].class).getHub(), false, false, "internedStrings table");
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

            addObject(debug, imageInternedStrings, true, true, "internedStrings table");

            // Process any objects that were transitively added to the heap.
            processAddObjectWorklist(debug);
        } else {
            internStringsPhase.disallow();
        }

        addObjectsPhase.disallow();
        assert addObjectWorklist.isEmpty();
    }

    private static Object readObjectField(HostedField field, JavaConstant receiver) {
        return SubstrateObjectConstant.asObject(field.readStorageValue(receiver));
    }

    private void addStaticFields(DebugContext debug) {
        addObject(debug, StaticFieldsSupport.getStaticObjectFields(), false, false, "staticObjectFields");
        addObject(debug, StaticFieldsSupport.getStaticPrimitiveFields(), false, false, "staticPrimitiveFields");

        /*
         * We only have empty holder arrays for the static fields, so we need to add static object
         * fields manually.
         */
        for (HostedField field : getUniverse().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.isWritten() && field.isAccessed() && field.getType().getStorageKind() == JavaKind.Object) {
                addObject(debug, readObjectField(field, null), false, false, field);
            }
        }
    }

    /*
     * Methods to map native image heap partitions to native image sections.
     *
     * Make no assumptions about the partitions being adjacent in memory.
     */

    long getReadOnlySectionSize() {
        return readOnlyPrimitive.getSize() + readOnlyReference.getSize() + readOnlyRelocatable.getSize();
    }

    long getReadOnlyRelocatablePartitionOffset() {
        return readOnlyRelocatable.offsetInSection();
    }

    long getFirstRelocatablePointerOffsetInSection() {
        assert firstRelocatablePointerOffsetInSection != -1;
        return firstRelocatablePointerOffsetInSection;
    }

    long getReadOnlyRelocatablePartitionSize() {
        return readOnlyRelocatable.getSize();
    }

    void setReadOnlySection(final String sectionName, final long sectionOffset) {
        readOnlyPrimitive.setSection(sectionName, sectionOffset);
        readOnlyReference.setSection(sectionName, readOnlyPrimitive.offsetInSection(readOnlyPrimitive.getSize()));
        readOnlyRelocatable.setSection(sectionName, readOnlyReference.offsetInSection(readOnlyReference.getSize()));
    }

    long getWritableSectionSize() {
        return writablePrimitive.getSize() + writableReference.getSize();
    }

    void setWritableSection(final String sectionName, final long sectionOffset) {
        writablePrimitive.setSection(sectionName, sectionOffset);
        writableReference.setSection(sectionName, writablePrimitive.offsetInSection(writablePrimitive.getSize()));
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
    public void addObject(DebugContext debug, final Object original, final boolean parentCanonicalizable, boolean immutableFromParent, final Object reason) {
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
            throw VMError.shouldNotReachHere("DynamicHub written to the image that has not been seen as reachable during static analysis: " + original);
        }

        int identityHashCode = 0;
        if (original instanceof HostedIdentityHashCodeProvider) {
            identityHashCode = ((HostedIdentityHashCodeProvider) original).hostedIdentityHashCode();
        }
        if (identityHashCode == 0) {
            identityHashCode = System.identityHashCode(original);
        }

        // Determine if the object is canonicalizable and if so canonicalize it.
        // Canonicalization is used to construct singleton objects in the native image heap
        // from possibly-distinct objects in the host heap.
        // TODO: What I would like here is a method that takes an instance and whether its
        // ... container is canonicalizable and returns a boolean about whether the instance is
        // ... canonicalizable and the canonicalized instance. But I can not do that in Java.
        final boolean canonicalizable = isCanonicalizable(original, parentCanonicalizable);
        debug.log("canonicalizable: %b", canonicalizable);
        final Object canonical = canonicalizable ? canonicalize(original) : original;

        final ObjectInfo existing = objects.get(canonical);
        if (existing != null) {
            if (canonical != original) {
                existing.update(original, identityHashCode);
                objects.putIfAbsent(original, existing);
            }
            debug.log("already existing object");
        } else {
            addObjectToBootImageHeap(original, canonical, canonicalizable, immutableFromParent, identityHashCode, reason);
        }
    }

    /**
     * Write the model of the native image heap to the RelocatableBuffers that represent the native
     * image.
     */
    @SuppressWarnings("try")
    public void writeHeap(DebugContext debug, final RelocatableBuffer roBuffer, final RelocatableBuffer rwBuffer) {
        try (Indent perHeapIndent = debug.logAndIndent("BootImageHeap.writeHeap:")) {
            for (ObjectInfo info : objects.values()) {
                assert !blacklist.contains(info.getObject());
                writeObject(info, roBuffer, rwBuffer);
            }
            // Only static fields that are writable get written to the native image heap,
            // the read-only static fields have been inlined into the code.
            writeStaticFields(rwBuffer);
            patchPartitionBoundaries(debug, roBuffer, rwBuffer);
        }

        if (NativeImageOptions.PrintHeapHistogram.getValue()) {
            // A histogram for the whole heap.
            ObjectGroupHistogram.print(this);
            // Histograms for each partition.
            readOnlyPrimitive.printHistogram();
            readOnlyReference.printHistogram();
            readOnlyRelocatable.printHistogram();
            writablePrimitive.printHistogram();
            writableReference.printHistogram();
        }
        if (NativeImageOptions.PrintImageHeapPartitionSizes.getValue()) {
            readOnlyPrimitive.printSize();
            readOnlyReference.printSize();
            readOnlyRelocatable.printSize();
            writablePrimitive.printSize();
            writableReference.printSize();
        }
    }

    public ObjectInfo getObjectInfo(Object obj) {
        return objects.get(obj);
    }

    /** Did the host intern this String? */
    private static boolean hostInternedString(final String str) {
        /*
         * Check if we have a string that is interned in the host VM.
         *
         * I cannot just check that "str.intern() == str": if the string was not interned before,
         * then intern() returns the original object and the comparison will succeed. Instead I
         * first make a copy of the string and intern that. If the result of interning the copy
         * returns the original String, then the original String was interned before this.
         *
         * It seems like there is a corner case where this pollutes the host interned String table
         * enough to confuse later queries about whether a String was interned on the host. But
         * there is no other way to query the host interned String table.
         */
        final String internedStr = new String(str).intern();
        return (internedStr == str);
    }

    /**
     * Is an object canonicalizable? That is, can one instance be replaced by another instance? That
     * depends on the type of the object, and whether it is part of a larger data structure (the
     * "parent") that is canonicalizable.
     */
    private boolean isCanonicalizable(Object object, boolean parentCanonicalizable) {
        if (object instanceof String) {
            final String str = (String) object;
            forceHashCodeComputation(str);
            if (hostInternedString(str)) {
                /*
                 * The string is interned by the host VM, so it must also be interned in our image.
                 */
                assert (internedStrings.containsKey(str) || internStringsPhase.isAllowed()) : String.format("Should not intern string during phase: %s  str: %s.", internStringsPhase.toString(), str);
                internedStrings.put(str, str);
                return true;
            }
        }

        if (isInstance(knownNonCanonicalizableClasses, object)) {
            return false;
        } else if (isInstance(knownCanonicalizableClasses, object)) {
            return true;
        }
        return parentCanonicalizable;
    }

    /**
     * For immutable Strings in the native image heap, force eager computation of the hash field.
     */
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "eager hash field computation")
    private static void forceHashCodeComputation(final String str) {
        str.hashCode();
    }

    private static boolean isInstance(List<Class<?>> classList, Object object) {
        boolean result = false;
        for (Class<?> clazz : classList) {
            if (clazz.isInstance(object)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private Object canonicalize(final Object object) {
        final CanonicalizedObjectHolder holder = new CanonicalizedObjectHolder(object);
        final Object existing = canonicalizationMap.putIfAbsent(holder, object);
        if (existing != null) {
            return existing;
        } else {
            return object;
        }
    }

    /**
     * It has been determined that an object should be added to the model of the native image heap.
     * This is the mechanics of recursively adding the object and all its fields and array elements
     * to the model of the native image heap.
     */
    private void addObjectToBootImageHeap(final Object original, final Object canonicalObj, final boolean canonicalizable,
                    boolean immutableFromParent, final int identityHashCode, final Object reason) {

        final Optional<HostedType> optionalType = getMetaAccess().optionalLookupJavaType(canonicalObj.getClass());
        if (!optionalType.isPresent() || !optionalType.get().isInstantiated()) {
            throw UserError.abort("Image heap writing found an object whose class was not seen as instantiated during static analysis. " +
                            "Did a static field or an object referenced from a static field changed during native image generation? " +
                            "For example, a lazily initialized cache could have been initialized during image generation, " +
                            "in which case you need to force eager initialization of the cache before static analysis or reset the cache using a field value recomputation.\n" +
                            "  object: " + original + "  of class: " + original.getClass().getTypeName() + "\n" +
                            "  reachable through:\n" +
                            fillReasonStack(new StringBuilder(), reason));
        }
        final HostedType type = optionalType.get();
        final DynamicHub hub = type.getHub();
        final ObjectInfo info;

        boolean immutable = immutableFromParent || isImmutable(canonicalObj, canonicalizable);
        boolean written = false;
        boolean references = false;
        boolean relocatable = false; /* always false when !spawnIsolates() */

        if (type.isInstanceClass()) {
            final HostedInstanceClass clazz = (HostedInstanceClass) type;
            // If the type has a monitor field, it has a reference field that is written.
            if (clazz.getMonitorFieldOffset() != 0) {
                immutable = false;
                written = true;
                references = true;
            }

            final JavaConstant con = SubstrateObjectConstant.forObject(canonicalObj);
            HostedField hybridBitsetField = null;
            HostedField hybridArrayField = null;
            Object hybridArray = null;
            final long size;

            if (HybridLayout.isHybrid(clazz)) {
                HybridLayout<?> hybridLayout = hybridLayouts.get(clazz);
                if (hybridLayout == null) {
                    hybridLayout = new HybridLayout<>(clazz, layout);
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

            info = addToImageHeap(original, canonicalObj, clazz, size, identityHashCode, reason);
            recursiveAddObject(hub, canonicalizable, false, info);
            // Recursively add all the fields of the object.
            final boolean fieldsAreImmutable = canonicalObj instanceof String;
            for (HostedField field : clazz.getInstanceFields(true)) {
                if (field.isAccessed() && !field.equals(hybridArrayField) && !field.equals(hybridBitsetField)) {
                    boolean fieldRelocatable = false;
                    if (field.getJavaKind() == JavaKind.Object) {
                        assert field.hasLocation();
                        JavaConstant value = field.readValue(con);
                        if (value.getJavaKind() == JavaKind.Object) {
                            Object obj = SubstrateObjectConstant.asObject(value);
                            if (spawnIsolates()) {
                                fieldRelocatable = obj instanceof RelocatedPointer;
                            }
                            recursiveAddObject(obj, canonicalizable, fieldsAreImmutable, info);
                            references = true;
                        }
                    }
                    /*
                     * The analysis considers relocatable pointers to be written because their
                     * eventual value is assigned at runtime by the dynamic linker and it cannot be
                     * inlined. Relocatable pointers are read-only for our purposes, however.
                     */
                    relocatable = relocatable || fieldRelocatable;
                    written = written || (field.isWritten() && !field.isFinal() && !fieldRelocatable);
                }

            }
            if (hybridArray instanceof Object[]) {
                relocatable = addArrayElements((Object[]) hybridArray, relocatable, canonicalizable, info);
                references = true;
            }
        } else if (type.isArray()) {
            HostedArrayClass clazz = (HostedArrayClass) type;
            final long size = layout.getArraySize(type.getComponentType().getStorageKind(), Array.getLength(canonicalObj));
            info = addToImageHeap(original, canonicalObj, clazz, size, identityHashCode, reason);
            recursiveAddObject(hub, canonicalizable, false, info);
            if (canonicalObj instanceof Object[]) {
                relocatable = addArrayElements((Object[]) canonicalObj, false, canonicalizable, info);
                references = true;
            }
            written = true; /* How to know if any of the array elements are written? */
        } else {
            throw shouldNotReachHere();
        }

        final HeapPartition partition = choosePartition(!written || immutable, references, relocatable);
        info.assignToHeapPartition(partition, layout);
    }

    /** Determine if an object in the host heap will be immutable in the native image heap. */
    private boolean isImmutable(final Object obj, final boolean canonicalizable) {
        if (obj instanceof String) {
            // Strings need to have their hash code set or they are not immutable.
            // If the hash is 0, then it will be recomputed again (and again)
            // so the String is not immutable.
            return obj.hashCode() != 0;
        } else if (knownImmutableObjects.contains(obj)) {
            return true;
        } else {
            return canonicalizable;
        }
    }

    /** Add an object to the model of the native image heap. */
    private ObjectInfo addToImageHeap(Object original, Object canonical, HostedClass clazz, long size, int identityHashCode, Object reason) {
        ObjectInfo info = new ObjectInfo(canonical, size, clazz, identityHashCode, reason);
        assert !objects.containsKey(canonical);
        objects.put(canonical, info);
        if (canonical != original && !objects.containsKey(original)) {
            objects.put(original, objects.get(canonical));
        }
        return info;
    }

    private HeapPartition choosePartition(boolean immutable, boolean references, boolean relocatable) {
        if (SubstrateOptions.UseOnlyWritableBootImageHeap.getValue()) {
            assert !spawnIsolates();
            // Emergency use only! Alarms will sound!
            return writableReference;
        }

        if (immutable) {
            if (relocatable) {
                return readOnlyRelocatable;
            }
            return references ? readOnlyReference : readOnlyPrimitive;
        } else {
            VMError.guarantee(!relocatable, "Objects with relocatable pointers must be immutable");
            return references ? writableReference : writablePrimitive;
        }
    }

    // Deep-copy an array from the host heap to the model of the native image heap.
    private boolean addArrayElements(Object[] array, boolean otherFieldsRelocatable, boolean canonicalizable, Object reason) {
        boolean relocatable = otherFieldsRelocatable;
        for (Object element : array) {
            Object value = aUniverse.replaceObject(element);
            if (spawnIsolates()) {
                relocatable = relocatable || value instanceof RelocatedPointer;
            }
            recursiveAddObject(value, canonicalizable, false, reason);
        }
        return relocatable;
    }

    /*
     * Break recursion using a worklist, to support large object graphs that would lead to a stack
     * overflow.
     */
    private void recursiveAddObject(Object original, boolean parentCanonicalizable, boolean immutableFromParent, Object reason) {
        if (original != null) {
            addObjectWorklist.push(new AddObjectData(original, parentCanonicalizable, immutableFromParent, reason));
        }
    }

    private void processAddObjectWorklist(DebugContext debug) {
        while (!addObjectWorklist.isEmpty()) {
            AddObjectData data = addObjectWorklist.pop();
            addObject(debug, data.original, data.parentCanonicalizable, data.immutableFromParent, data.reason);
        }
    }

    private void writeStaticFields(RelocatableBuffer buffer) {
        /*
         * Write the values of static fields. The arrays for primitive and object fields are empty
         * and just placeholders. This ensures we get the latest version, since there can be
         * Features registered that change the value of static fields late in the native image
         * generation process.
         */
        ObjectInfo primitiveFields = objects.get(StaticFieldsSupport.getStaticPrimitiveFields());
        ObjectInfo objectFields = objects.get(StaticFieldsSupport.getStaticObjectFields());
        for (HostedField field : getUniverse().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.isWritten() && field.isAccessed()) {
                ObjectInfo fields = (field.getStorageKind() == JavaKind.Object) ? objectFields : primitiveFields;
                writeField(buffer, fields, field, null, null);
            }
        }
    }

    private int referenceSize() {
        return layout.getReferenceSize();
    }

    private void mustBeReferenceAligned(int index) {
        assert (index % layout.getReferenceSize() == 0) : "index " + index + " must be reference-aligned.";
    }

    private static void verifyTargetDidNotChange(Object target, Object reason, Object targetInfo) {
        if (targetInfo == null) {
            throw UserError.abort("Static field or an object referenced from a static field changed during native image generation?\n" +
                            "  object:" + target + "  of class: " + target.getClass().getTypeName() + "\n" +
                            "  reachable through:\n" +
                            fillReasonStack(new StringBuilder(), reason));
        }
    }

    private static StringBuilder fillReasonStack(StringBuilder msg, Object reason) {
        if (reason instanceof ObjectInfo) {
            ObjectInfo info = (ObjectInfo) reason;
            msg.append("    object: ").append(info.getObject()).append("  of class: ").append(info.getObject().getClass().getTypeName()).append("\n");
            return fillReasonStack(msg, info.reason);
        }
        return msg.append("    root: ").append(reason).append("\n");
    }

    private void writeField(RelocatableBuffer buffer, ObjectInfo fields, HostedField field, JavaConstant receiver, ObjectInfo info) {
        int index = fields.getIntIndexInSection(field.getLocation());
        JavaConstant value = field.readValue(receiver);
        if (value.getJavaKind() == JavaKind.Object && SubstrateObjectConstant.asObject(value) instanceof RelocatedPointer) {
            addNonDataRelocation(buffer, index, (RelocatedPointer) SubstrateObjectConstant.asObject(value));
        } else {
            write(buffer, index, value, info != null ? info : field);
        }
    }

    private void write(RelocatableBuffer buffer, int index, JavaConstant con, Object reason) {
        if (con.getJavaKind() == JavaKind.Object) {
            writeReference(buffer, index, SubstrateObjectConstant.asObject(con), reason);
        } else {
            writePrimitive(buffer, index, con);
        }
    }

    void writeReference(RelocatableBuffer buffer, int index, Object target, Object reason) {
        assert !(target instanceof WordBase) : "word values are not references";
        mustBeReferenceAligned(index);
        if (target != null) {
            ObjectInfo targetInfo = objects.get(target);
            verifyTargetDidNotChange(target, reason, targetInfo);
            if (useHeapBase()) {
                CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
                int shift = compressEncoding.getShift();
                writeReferenceValue(buffer, index, targetInfo.getOffsetInSection() >>> shift);
            } else {
                addDirectRelocationWithoutAddend(buffer, index, referenceSize(), target);
            }
        }
    }

    private void writeConstant(RelocatableBuffer buffer, int index, JavaKind kind, Object value, ObjectInfo info) {
        if (value instanceof RelocatedPointer) {
            addNonDataRelocation(buffer, index, (RelocatedPointer) value);
            return;
        }

        final JavaConstant con;
        if (value instanceof WordBase) {
            con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), ((WordBase) value).rawValue());
        } else if (value == null && kind == FrameAccess.getWordKind()) {
            con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), 0);
        } else {
            assert kind == JavaKind.Object || value != null : "primitive value must not be null";
            con = SubstrateObjectConstant.forBoxedValue(kind, value);
        }
        write(buffer, index, con, info);
    }

    private void writeDynamicHub(RelocatableBuffer buffer, int index, DynamicHub target) {
        assert target != null : "Null DynamicHub found during native image generation.";
        mustBeReferenceAligned(index);

        ObjectInfo targetInfo = objects.get(target);
        assert targetInfo != null : "Unknown object " + target.toString() + " found. Static field or an object referenced from a static field changed during native image generation?";

        if (useHeapBase()) {
            // NOTE: we do not apply a shift to the hub reference in the object header because the
            // least significant bits are used for state information
            long targetOffset = targetInfo.getOffsetInSection();
            long bits = Heap.getHeap().getObjectHeader().setBootImageOnLong(targetOffset);
            writeReferenceValue(buffer, index, bits);
        } else {
            // The address of the DynamicHub target will have to be added by the link editor.
            long objectHeaderBits = Heap.getHeap().getObjectHeader().setBootImageOnLong(0L);
            addDirectRelocationWithAddend(buffer, index, target, objectHeaderBits);
        }
    }

    private void addDirectRelocationWithoutAddend(RelocatableBuffer buffer, int index, int size, Object target) {
        assert !spawnIsolates() || index >= readOnlyRelocatable.offsetInSection() && index < readOnlyRelocatable.offsetInSection(readOnlyRelocatable.getSize());
        buffer.addDirectRelocationWithoutAddend(index, size, target);
        if (firstRelocatablePointerOffsetInSection == -1) {
            firstRelocatablePointerOffsetInSection = index;
        }
    }

    private void addDirectRelocationWithAddend(RelocatableBuffer buffer, int index, DynamicHub target, long objectHeaderBits) {
        assert !spawnIsolates() || index >= readOnlyRelocatable.offsetInSection() && index < readOnlyRelocatable.offsetInSection(readOnlyRelocatable.getSize());
        buffer.addDirectRelocationWithAddend(index, referenceSize(), objectHeaderBits, target);
        if (firstRelocatablePointerOffsetInSection == -1) {
            firstRelocatablePointerOffsetInSection = index;
        }
    }

    /**
     * Adds a relocation for a code pointer or other non-data pointers.
     */
    private void addNonDataRelocation(RelocatableBuffer buffer, int index, RelocatedPointer pointer) {
        mustBeReferenceAligned(index);
        assert pointer instanceof CFunctionPointer : "unknown relocated pointer " + pointer;
        assert pointer instanceof MethodPointer : "cannot create relocation for unknown FunctionPointer " + pointer;

        ResolvedJavaMethod method = ((MethodPointer) pointer).getMethod();
        HostedMethod hMethod = method instanceof HostedMethod ? (HostedMethod) method : universe.lookup(method);
        if (hMethod.isCodeAddressOffsetValid()) {
            // Only compiled methods inserted in vtables require relocation.
            int pointerSize = ConfigurationValues.getTarget().wordSize;
            addDirectRelocationWithoutAddend(buffer, index, pointerSize, pointer);
        }
    }

    private static void writePrimitive(RelocatableBuffer buffer, int index, JavaConstant con) {
        ByteBuffer bb = buffer.getBuffer();
        switch (con.getJavaKind()) {
            case Boolean:
                bb.put(index, (byte) con.asInt());
                break;
            case Byte:
                bb.put(index, (byte) con.asInt());
                break;
            case Char:
                bb.putChar(index, (char) con.asInt());
                break;
            case Short:
                bb.putShort(index, (short) con.asInt());
                break;
            case Int:
                bb.putInt(index, con.asInt());
                break;
            case Long:
                bb.putLong(index, con.asLong());
                break;
            case Float:
                bb.putFloat(index, con.asFloat());
                break;
            case Double:
                bb.putDouble(index, con.asDouble());
                break;
            default:
                throw shouldNotReachHere(con.getJavaKind().toString());
        }
    }

    private void writeReferenceValue(RelocatableBuffer buffer, int index, long value) {
        if (referenceSize() == Long.BYTES) {
            buffer.getBuffer().putLong(index, value);
        } else if (referenceSize() == Integer.BYTES) {
            buffer.getBuffer().putInt(index, NumUtil.safeToInt(value));
        } else {
            throw shouldNotReachHere("Unsupported reference size: " + referenceSize());
        }
    }

    private void patchPartitionBoundaries(DebugContext debug, final RelocatableBuffer roBuffer, final RelocatableBuffer rwBuffer) {
        // Figure out where the boundaries of the heap partitions are and
        // patch the objects that reference them so they will be correct at runtime.
        final NativeImageInfoPatcher patcher = new NativeImageInfoPatcher(debug, roBuffer, rwBuffer);
        patcher.patchReference("firstReadOnlyPrimitiveObject", readOnlyPrimitive.firstAllocatedObject);
        patcher.patchReference("lastReadOnlyPrimitiveObject", readOnlyPrimitive.lastAllocatedObject);

        Object firstReadOnlyReferenceObject = readOnlyReference.firstAllocatedObject;
        if (firstReadOnlyReferenceObject == null) {
            firstReadOnlyReferenceObject = readOnlyRelocatable.firstAllocatedObject;
        }
        patcher.patchReference("firstReadOnlyReferenceObject", firstReadOnlyReferenceObject);

        Object lastReadOnlyReferenceObject = readOnlyRelocatable.lastAllocatedObject;
        if (lastReadOnlyReferenceObject == null) {
            lastReadOnlyReferenceObject = readOnlyReference.lastAllocatedObject;
        }
        patcher.patchReference("lastReadOnlyReferenceObject", lastReadOnlyReferenceObject);

        patcher.patchReference("firstWritablePrimitiveObject", writablePrimitive.firstAllocatedObject);
        patcher.patchReference("lastWritablePrimitiveObject", writablePrimitive.lastAllocatedObject);

        patcher.patchReference("firstWritableReferenceObject", writableReference.firstAllocatedObject);
        patcher.patchReference("lastWritableReferenceObject", writableReference.lastAllocatedObject);
    }

    private final class NativeImageInfoPatcher {
        NativeImageInfoPatcher(DebugContext debugContext, RelocatableBuffer roBuffer, RelocatableBuffer rwBuffer) {
            staticFieldsInfo = objects.get(StaticFieldsSupport.getStaticObjectFields());
            buffer = bufferForPartition(staticFieldsInfo, roBuffer, rwBuffer);
            debug = debugContext;
        }

        void patchReference(String fieldName, Object fieldValue) {
            if (fieldValue == null) {
                debug.log("BootImageHeap.patchPartitionBoundaries: %s is null", fieldName);
                return;
            }

            try {
                final HostedField field = getMetaAccess().lookupJavaField(NativeImageInfo.class.getDeclaredField(fieldName));
                final int index = staticFieldsInfo.getIntIndexInSection(field.getLocation());
                // Overwrite the previously written null-value with the actual object location.
                writeReference(buffer, index, fieldValue, staticFieldsInfo);
            } catch (NoSuchFieldException ex) {
                throw shouldNotReachHere(ex);
            }
        }

        private final ObjectInfo staticFieldsInfo;
        private final RelocatableBuffer buffer;
        private final DebugContext debug;
    }

    private static RelocatableBuffer bufferForPartition(final ObjectInfo info, final RelocatableBuffer roBuffer, final RelocatableBuffer rwBuffer) {
        VMError.guarantee(info != null, "[BootImageHeap.bufferForPartition: info is null]");
        VMError.guarantee(info.getPartition() != null, "[BootImageHeap.bufferForPartition: info.partition is null]");

        return info.getPartition().isWritable() ? rwBuffer : roBuffer;
    }

    private void writeObject(ObjectInfo info, final RelocatableBuffer roBuffer, final RelocatableBuffer rwBuffer) {
        /*
         * Write a reference from the object to its hub. This lives at layout.getHubOffset() from
         * the object base.
         */
        final RelocatableBuffer buffer = bufferForPartition(info, roBuffer, rwBuffer);
        final int indexInSection = info.getIntIndexInSection(layout.getHubOffset());
        assert layout.isAligned(info.getOffsetInPartition());
        assert layout.isAligned(indexInSection);

        final HostedClass clazz = info.getClazz();
        final DynamicHub hub = clazz.getHub();

        writeDynamicHub(buffer, indexInSection, hub);

        if (clazz.isInstanceClass()) {
            JavaConstant con = SubstrateObjectConstant.forObject(info.getObject());

            HybridLayout<?> hybridLayout = hybridLayouts.get(clazz);
            HostedField hybridArrayField = null;
            HostedField hybridBitsetField = null;
            int maxBitIndex = -1;
            Object hybridArray = null;
            if (hybridLayout != null) {
                hybridArrayField = hybridLayout.getArrayField();
                hybridArray = readObjectField(hybridArrayField, con);

                hybridBitsetField = hybridLayout.getBitsetField();
                if (hybridBitsetField != null) {
                    BitSet bitSet = (BitSet) readObjectField(hybridBitsetField, con);
                    if (bitSet != null) {
                        /*
                         * Write the bits of the hybrid bit field. The bits are located between the
                         * array length and the instance fields.
                         */
                        int bitsPerByte = Byte.SIZE;
                        for (int bit = bitSet.nextSetBit(0); bit >= 0; bit = bitSet.nextSetBit(bit + 1)) {
                            final int index = info.getIntIndexInSection(hybridLayout.getBitFieldOffset()) + bit / bitsPerByte;
                            if (index > maxBitIndex) {
                                maxBitIndex = index;
                            }
                            int mask = 1 << (bit % bitsPerByte);
                            assert mask < (1 << bitsPerByte);
                            buffer.putByte(index, (byte) (buffer.getByte(index) | mask));
                        }
                    }
                }
            }

            /*
             * Write the regular instance fields.
             */
            for (HostedField field : clazz.getInstanceFields(true)) {
                if (!field.equals(hybridArrayField) && !field.equals(hybridBitsetField) && field.isAccessed()) {
                    assert field.getLocation() >= 0;
                    assert info.getIntIndexInSection(field.getLocation()) > maxBitIndex;
                    writeField(buffer, info, field, con, info);
                }
            }
            if (hub.getHashCodeOffset() != 0) {
                buffer.putInt(info.getIntIndexInSection(hub.getHashCodeOffset()), info.getIdentityHashCode());
            }
            if (hybridArray != null) {
                /*
                 * Write the hybrid array length and the array elements.
                 */
                int length = Array.getLength(hybridArray);
                buffer.putInt(info.getIntIndexInSection(layout.getArrayLengthOffset()), length);
                for (int i = 0; i < length; i++) {
                    final int elementIndex = info.getIntIndexInSection(hybridLayout.getArrayElementOffset(i));
                    final JavaKind elementStorageKind = hybridLayout.getArrayElementStorageKind();
                    final Object array = Array.get(hybridArray, i);
                    writeConstant(buffer, elementIndex, elementStorageKind, array, info);
                }
            }

        } else if (clazz.isArray()) {
            JavaKind kind = clazz.getComponentType().getStorageKind();
            Object array = info.getObject();
            int length = Array.getLength(array);
            buffer.putInt(info.getIntIndexInSection(layout.getArrayLengthOffset()), length);
            buffer.putInt(info.getIntIndexInSection(layout.getArrayHashCodeOffset()), info.getIdentityHashCode());
            if (array instanceof Object[]) {
                Object[] oarray = (Object[]) array;
                assert oarray.length == length;
                for (int i = 0; i < length; i++) {
                    final int elementIndex = info.getIntIndexInSection(layout.getArrayElementOffset(kind, i));
                    final Object element = aUniverse.replaceObject(oarray[i]);
                    assert (oarray[i] instanceof RelocatedPointer) == (element instanceof RelocatedPointer);
                    writeConstant(buffer, elementIndex, kind, element, info);
                }
            } else {
                for (int i = 0; i < length; i++) {
                    final int elementIndex = info.getIntIndexInSection(layout.getArrayElementOffset(kind, i));
                    final Object element = Array.get(array, i);
                    writeConstant(buffer, elementIndex, kind, element, info);
                }
            }

        } else {
            throw shouldNotReachHere();
        }
    }

    protected HostedUniverse getUniverse() {
        return universe;
    }

    protected HostedMetaAccess getMetaAccess() {
        return metaAccess;
    }

    public NativeImageHeap(AnalysisUniverse aUniverse, HostedUniverse universe, HostedMetaAccess metaAccess) {
        this.aUniverse = aUniverse;
        this.universe = universe;
        this.metaAccess = metaAccess;
        this.layout = ConfigurationValues.getObjectLayout();

        readOnlyPrimitive = HeapPartition.factory("readOnlyPrimitive", this, false);
        readOnlyReference = HeapPartition.factory("readOnlyReference", this, false);
        readOnlyRelocatable = HeapPartition.factory("readOnlyRelocatable", this, false);
        writablePrimitive = HeapPartition.factory("writablePrimitive", this, true);
        writableReference = HeapPartition.factory("writableReference", this, true);

        if (useHeapBase()) {
            /*
             * Zero designates null, so add some padding at the heap base to make object offsets
             * strictly positive
             */
            readOnlyPrimitive.incrementSize(layout.getAlignment());
        }

        // Initialize the canonicalizable and immutable class lists.
        // Some hosted classes I know are not canonicalizable.
        knownNonCanonicalizableClasses.add(Enum.class);
        knownNonCanonicalizableClasses.add(Proxy.class);
        // The classes implementing Map have lazily initialized caches, so must not be immutable.
        knownNonCanonicalizableClasses.add(Map.class);
        // ClassInitializationInfo is mutable, but referenced from the immutable DynamicHub
        knownNonCanonicalizableClasses.add(ClassInitializationInfo.class);

        // Some hosted classes I know to be canonicalizable.
        knownCanonicalizableClasses.add(DynamicHub.class);
    }

    private final HostedUniverse universe;
    private final AnalysisUniverse aUniverse;
    private final HostedMetaAccess metaAccess;
    private final ObjectLayout layout;

    /**
     * A Map from objects at construction-time to native image objects.
     *
     * More than one host object may be represented by a single native image object.
     */
    protected final Map<Object, ObjectInfo> objects = new IdentityHashMap<>();

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

    /** The canonicalization map. */
    private final Map<CanonicalizedObjectHolder, Object> canonicalizationMap = new HashMap<>();
    /** A list of classes that are known to be canonicalizable. */
    private final List<Class<?>> knownCanonicalizableClasses = new ArrayList<>();
    /** A list classes that are known not to be canonicalizable. */
    private final List<Class<?>> knownNonCanonicalizableClasses = new ArrayList<>();

    /** Objects that are known to be immutable in the native image heap. */
    private final Set<Object> knownImmutableObjects = Collections.newSetFromMap(new IdentityHashMap<>());

    private final HeapPartition readOnlyPrimitive;
    private final HeapPartition readOnlyReference;
    private final HeapPartition readOnlyRelocatable;
    private long firstRelocatablePointerOffsetInSection = -1;
    private final HeapPartition writablePrimitive;
    private final HeapPartition writableReference;

    static class AddObjectData {

        AddObjectData(Object original, boolean parentCanonicalizable, boolean immutableFromParent, Object reason) {
            super();
            this.original = original;
            this.parentCanonicalizable = parentCanonicalizable;
            this.immutableFromParent = immutableFromParent;
            this.reason = reason;
        }

        final Object original;
        final boolean parentCanonicalizable;
        final boolean immutableFromParent;
        final Object reason;
    }

    public static final class ObjectInfo {

        Object getObject() {
            return object;
        }

        HostedClass getClazz() {
            return clazz;
        }

        /**
         * The offset of an object within a partition. <em>Probably you want
         * {@link #getOffsetInSection()}</em>.
         */
        private long getOffsetInPartition() {
            return offsetInPartition;
        }

        /** The start within a native image heap section (e.g., read-only or writable). */
        public long getOffsetInSection() {
            return getPartition().offsetInSection(getOffsetInPartition());
        }

        /** An index into a object in the native image heap. E.g., a field within an object. */
        public long getIndexInSection(long index) {
            assert index >= 0 && index < getSize() : "Index: " + index + " out of bounds: [0 .. " + getSize() + ").";
            return getOffsetInSection() + index;
        }

        /** An index into a object in the native image heap. E.g., a field within an object. */
        int getIntIndexInSection(long index) {
            final long result = getIndexInSection(index);
            return NumUtil.safeToInt(result);
        }

        long getSize() {
            return size;
        }

        public HeapPartition getPartition() {
            return partition;
        }

        int getIdentityHashCode() {
            return identityHashCode;
        }

        private void setIdentityHashCode(int identityHashCode) {
            this.identityHashCode = identityHashCode;
        }

        /**
         * Maintains some consistency between different instances of objects.
         *
         * Notes on the identity hash code for classes: We map two different hosted objects (the
         * java.lang.Class and the DynamicHub) to one object at run time (the DynamicHub). This
         * means we have two hosted identity hash codes to choose from to use. It is more reasonable
         * to use the one from java.lang.Class: if, for example, a hash map that is built by a
         * static initializer has a java.lang.Class as the key, then this hash map works just fine
         * at run time since the hash codes are the same. In contrast, it is unlikely that we rely
         * on the hash code from our own DynamicHub instances. We can only access the hash code from
         * java.lang.Class when we encounter it in this method (which does the substitution to
         * DynamicHub in the above code). However, if we never encounter a reference to a certain
         * java.lang.Class, this means that this class is never referenced explicitly from a data
         * structure. Therefore, it also not possible that we care about its hash code, i.e., it is
         * fine if we use the hash code from the DynamicHub in this case.
         */
        void update(Object original, int hashCode) {
            if (getIdentityHashCode() != hashCode) {
                assert getIdentityHashCode() == System.identityHashCode(original) || getIdentityHashCode() == System.identityHashCode(object);
                if (original instanceof Class) {
                    /*
                     * The existing ObjectInfo might have had the hash code from the DynamicHub, but
                     * now we change it to the hash code from the java.lang.Class.
                     */
                    setIdentityHashCode(hashCode);
                }
            }
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

        ObjectInfo(Object object, long size, HostedClass clazz, int identityHashCode, Object reason) {
            this.object = object;
            this.clazz = clazz;
            this.partition = null;
            this.offsetInPartition = -1L;
            this.size = size;
            this.setIdentityHashCode(identityHashCode);
            this.reason = reason;
        }

        void assignToHeapPartition(HeapPartition objectPartition, ObjectLayout layout) {
            assert partition == null;
            partition = objectPartition;
            offsetInPartition = partition.allocate(this);
            assert layout.isAligned(offsetInPartition) : "start: " + offsetInPartition + " must be aligned.";
            assert layout.isAligned(size) : "size: " + size + " must be aligned.";
        }

        private final Object object;
        private final HostedClass clazz;
        private final long size;
        private int identityHashCode;
        private HeapPartition partition;
        private long offsetInPartition;
        /**
         * For debugging only: the reason why this object is in the native image heap.
         *
         * This is either another ObjectInfo, saying which object refers to this object, eventually
         * a root object which refers to this object, or is a String explaining why this object is
         * in the heap. The reason field is like a "comes from" pointer.
         */
        final Object reason;
    }

    /**
     * The native image heap comes in partitions. Each partition holds objects with different
     * properties (read-only/writable, primitives/objects).
     */
    public static final class HeapPartition {

        static HeapPartition factory(String name, NativeImageHeap heap, boolean writable) {
            return new HeapPartition(name, heap, writable);
        }

        long getSize() {
            return size;
        }

        long getCount() {
            return count;
        }

        void incrementSize(final long increment) {
            size += increment;
            count += 1L;
        }

        long allocate(ObjectInfo info) {
            Object object = info.getObject();
            lastAllocatedObject = object;
            if (firstAllocatedObject == null) {
                firstAllocatedObject = object;
            }

            long position = size;
            incrementSize(info.getSize());
            return position;
        }

        public boolean isWritable() {
            return writable;
        }

        void setSection(String name, long offset) {
            sectionName = name;
            sectionOffset = offset;
            assert heap.layout.isAligned(offset) : String.format("Partition: %s: offset: %d in section: %s must be aligned.", this.name, offsetInSection(), getSectionName());
        }

        String getSectionName() {
            assert sectionName != null : "Partition " + name + " should have a section name by now.";
            return sectionName;
        }

        long offsetInSection() {
            assert sectionOffset != INVALID_SECTION_OFFSET : "Partition " + name + " should have an offset by now.";
            return sectionOffset;
        }

        long offsetInSection(long offset) {
            return offsetInSection() + offset;
        }

        @Override
        public String toString() {
            return name;
        }

        void printHistogram() {
            final HeapHistogram histogram = new HeapHistogram();
            final Set<ObjectInfo> uniqueObjectInfo = new HashSet<>();
            long uniqueCount = 0L;
            long uniqueSize = 0L;
            long canonicalizedCount = 0L;
            long canonicalizedSize = 0L;
            for (ObjectInfo info : heap.objects.values()) {
                if (info.getPartition() == this) {
                    if (uniqueObjectInfo.add(info)) {
                        histogram.add(info, info.getSize());
                        uniqueCount += 1L;
                        uniqueSize += info.getSize();
                    } else {
                        canonicalizedCount += 1L;
                        canonicalizedSize += info.getSize();
                    }
                }
            }
            assert (getCount() == uniqueCount) : String.format("Incorrect counting: getCount(): %d  uniqueCount: %d", getCount(), uniqueCount);
            assert (getSize() == uniqueSize) : String.format("Incorrect sizing: getSize(): %d  uniqueSize: %d", getCount(), uniqueCount);
            final long nonuniqueCount = uniqueCount + canonicalizedCount;
            final long nonuniqueSize = uniqueSize + canonicalizedSize;
            final double countPercent = 100.0D * ((double) uniqueCount / (double) nonuniqueCount);
            final double sizePercent = 100.0D * ((double) uniqueSize / (double) nonuniqueSize);
            histogram.printHeadings(String.format("=== Partition: %s   count: %d / %d = %.1f%%  size: %d / %d = %.1f%% ===", //
                            name, //
                            getCount(), nonuniqueCount, countPercent, //
                            getSize(), nonuniqueSize, sizePercent));
            histogram.print();
        }

        void printSize() {
            System.out.printf("PrintImageHeapPartitionSizes:  partition: %s  size: %d\n", name, getSize());
        }

        private HeapPartition(String name, NativeImageHeap heap, boolean writable) {
            this.name = name;
            this.heap = heap;
            this.writable = writable;
            this.size = 0L;
            this.count = 0L;
            this.firstAllocatedObject = null;
            this.lastAllocatedObject = null;
            this.sectionName = null;
            this.sectionOffset = INVALID_SECTION_OFFSET;
        }

        /** For debugging, the name of this partition. */
        private final String name;
        /** The heap within which this is a partition. */
        private final NativeImageHeap heap;
        /** Whether this partition is writable. */
        private final boolean writable;
        /** The total size of the objects in this partition. */
        private long size;
        /** The number of objects in this partition. */
        private long count;

        Object firstAllocatedObject;
        Object lastAllocatedObject;

        /** The name of the native image section in which this partition lives. */
        private String sectionName;
        /**
         * The offset of this partition relative to beginning of the containing native image
         * section.
         */
        private long sectionOffset;

        private static final long INVALID_SECTION_OFFSET = -1L;
    }

    static final class CanonicalizedObjectHolder {
        @Override
        public boolean equals(Object o) {
            if (object == null) {
                return false;
            }
            if (object == ((CanonicalizedObjectHolder) o).object) {
                return true;
            } else if (object.getClass() != ((CanonicalizedObjectHolder) o).object.getClass()) {
                return false;
            } else if (object instanceof byte[]) {
                return Arrays.equals((byte[]) object, (byte[]) ((CanonicalizedObjectHolder) o).object);
            } else if (object instanceof char[]) {
                return Arrays.equals((char[]) object, (char[]) ((CanonicalizedObjectHolder) o).object);
            } else if (object instanceof int[]) {
                return Arrays.equals((int[]) object, (int[]) ((CanonicalizedObjectHolder) o).object);
            } else if (object instanceof long[]) {
                return Arrays.equals((long[]) object, (long[]) ((CanonicalizedObjectHolder) o).object);
            } else if (object instanceof Object[]) {
                return Arrays.deepEquals((Object[]) object, (Object[]) ((CanonicalizedObjectHolder) o).object);
            } else {
                return object.equals(((CanonicalizedObjectHolder) o).object);
            }
        }

        @Override
        public int hashCode() {
            if (object instanceof byte[]) {
                return Arrays.hashCode((byte[]) object);
            } else if (object instanceof char[]) {
                return Arrays.hashCode((char[]) object);
            } else if (object instanceof int[]) {
                return Arrays.hashCode((int[]) object);
            } else if (object instanceof long[]) {
                return Arrays.hashCode((long[]) object);
            } else if (object instanceof Object[]) {
                return Arrays.deepHashCode((Object[]) object);
            } else {
                return object.hashCode();
            }
        }

        CanonicalizedObjectHolder(Object object) {
            this.object = object;
        }

        final Object object;
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
