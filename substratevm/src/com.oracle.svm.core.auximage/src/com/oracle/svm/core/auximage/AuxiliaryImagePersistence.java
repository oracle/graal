/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapLayouter;
import com.oracle.svm.core.genscavenge.ImageHeapInfo;
import com.oracle.svm.core.heap.AbstractPinnedObjectSupport;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.image.BasicImageHeapObjectSorter;
import com.oracle.svm.core.image.DisallowedImageHeapObjects;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapLayouter.ImageHeapLayoutCancelledException;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.os.ImageHeapProvider;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.util.Timer;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;

final class AuxiliaryImagePersistence {
    interface InternalReplacersAccess {
        void addObject(Object obj, Object referencedFrom, boolean callReplacers);

        void registerCodeWalker(byte[] array, RuntimeCodeReferenceWalker walker);
    }

    /** Marker value for an object which should be replaced by {@code null} in references. */
    private static final Object NULL_SENTINEL = new Object();

    /**
     * Marker value for an object that cannot be part of the auxiliary image heap, but is only
     * non-strongly referenced through {@link Reference} objects which we can clear instead.
     */
    private static final Object CLEAR_REFERENCE_SENTINEL = new Object();

    private AuxiliaryImagePersistence() {
    }

    static ByteBuffer persist(ImmutableAuxiliaryImage image, List<AuxiliaryImageObjectReplacer> replacers, AuxiliaryImagePersistenceCallback callback, long maximumAllowedImageSize) {
        IdentityHashCodeSupport.ensureInitialized();

        var cpuFeatures = getCPUFeatures();
        // ensure that we have a fresh object, not linked to the image heap
        var cpuFeaturesCopy = cpuFeatures == null ? null : cpuFeatures.clone();
        AuxiliaryImageMetadata meta = new AuxiliaryImageMetadata(image, cpuFeaturesCopy);
        AuxiliaryImageObjectReplacer[] replacersArray = replacers.toArray(new AuxiliaryImageObjectReplacer[0]);
        HeapSnapshotImageOperation op = new HeapSnapshotImageOperation(callback, meta, replacersArray, maximumAllowedImageSize);
        op.enqueue();
        if (op.caughtException != null) {
            if (op.caughtException instanceof ImageHeapLayoutCancelledException) {
                throw new AuxiliaryImagePersistenceCancelledException(op.caughtException);
            } else {
                throw op.caughtException;
            }
        }
        return op.getImageDataAsBuffer();
    }

    static EnumSet<?> getCPUFeatures() {
        if (!PersistedRuntimeCode.isSupportedInCurrentImage()) {
            // no JIT compilation
            return null;
        }
        return CPUFeatureAccess.singleton().determineHostCPUFeatures();
    }

    static boolean isInPrimaryImageHeap(Object obj) {
        // Persist objects outside of the image heap, even when they are in an auxiliary image heap
        return Heap.getHeap().isInPrimaryImageHeap(Word.objectToUntrackedPointer(obj));
    }

    static int getTrailingZerosBegin(ByteBuffer b) {
        int index = b.capacity();
        while (index % 8 != 0 && b.get(index - 1) == 0) {
            index--;
        }
        while (index >= 8 && b.getLong(index - 8) == 0) {
            index -= 8;
        }
        while (index >= 1 && b.get(index - 1) == 0) {
            index--;
        }
        return index;
    }

    private static final class HeapSnapshotImageOperation extends JavaVMOperation {
        private final AuxiliaryImagePersistenceControl control;
        private final AuxiliaryImageMetadata metaObj;
        private final UnsignedWord auxImageHeapOffsetInAddressSpace;
        private final long maximumAllowedImageSize;

        private final ObjectTraversal traversal = new ObjectTraversal(this::getWalker);
        private final ObjectReferencesWalker defaultRefsWalker = new DefaultObjectReferencesWalker();
        private final EconomicMap<byte[], RuntimeCodeReferenceWalker> codeRefsWalkers = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

        private final AuxiliaryImageObjectReplacer[] replacers;
        private final EconomicMap<Object, Object> replacedObjects = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        private final ReplacerAccessImpl replacersAccess = new ReplacerAccessImpl();

        private AuxiliaryImageHeap heap;
        private byte[] imageData;
        private int imageDataLength;
        private Pointer heapDataPtr;
        private RuntimeException caughtException;

        HeapSnapshotImageOperation(AuxiliaryImagePersistenceCallback callback, AuxiliaryImageMetadata metaObj, AuxiliaryImageObjectReplacer[] replacers, long maximumAllowedImageSize) {
            super(VMOperationInfos.get(HeapSnapshotImageOperation.class, "Snapshot heap", SystemEffect.SAFEPOINT));

            this.control = new AuxiliaryImagePersistenceControl(callback);
            this.metaObj = metaObj;
            this.replacers = Arrays.copyOf(replacers, replacers.length + 2);
            this.replacers[replacers.length] = new PersistedContinuationReplacer();
            this.replacers[replacers.length + 1] = new PersistedRuntimeCodeReplacer(metaObj);
            this.maximumAllowedImageSize = maximumAllowedImageSize > 0 ? maximumAllowedImageSize : Long.MAX_VALUE;

            UnsignedWord imageHeapEndOffsetInAddressSpace = ImageHeapProvider.get().getImageHeapEndOffsetInAddressSpace();
            UnsignedWord alignment = Word.unsigned(Heap.getHeap().getImageHeapAlignment());
            this.auxImageHeapOffsetInAddressSpace = UnsignedUtils.roundUp(imageHeapEndOffsetInAddressSpace, alignment);
        }

        ByteBuffer getImageDataAsBuffer() {
            return ByteBuffer.wrap(imageData, 0, imageDataLength);
        }

        @Override
        protected void operate() {
            try {
                operate0();
            } catch (RuntimeException e) {
                caughtException = e;
            }
        }

        private void operate0() {

            heap = new AuxiliaryImageHeap(maximumAllowedImageSize);

            try (Timer _ = AuxiliaryImageTracing.persistTimers.total.start()) {
                control.poll();

                // Collect weak references and invalidated code
                try (Timer _ = AuxiliaryImageTracing.persistTimers.initialGC.start()) {
                    Heap.getHeap().getGC().collect(GCCause.JavaLangSystemGC);
                }

                // First, traverse the user-provided objects with the user-registered replacers
                try (Timer _ = AuxiliaryImageTracing.persistTimers.traverseObjMap.start()) {
                    for (AuxiliaryImageObjectReplacer replacer : replacers) {
                        control.poll();
                        replacer.prologue(replacersAccess);
                    }
                    for (Object obj : metaObj.image.map.values()) {
                        control.poll();
                        traverseReference(heap.getRootObject(), obj, true);
                    }
                    traversal.traverse((from, to) -> traverseReference(from, to, true));
                    for (AuxiliaryImageObjectReplacer replacer : replacers) {
                        control.poll();
                        replacer.epilogue(replacersAccess);
                    }
                    traversal.traverse((_, to) -> VMError.guarantee(heap.containsObject(to), "No additional objects may become reachable after the epilogue"));
                    assert traversal.isFinished();
                }

                // Second, traverse our private implementation objects without allowing replacements
                try (Timer _ = AuxiliaryImageTracing.persistTimers.traverseMetaObj.start()) {
                    control.poll();
                    traverseReference(heap.getRootObject(), metaObj, false);
                    control.poll();
                    traversal.traverse((from, to) -> traverseReference(from, to, false));
                    assert traversal.isFinished();
                }

                ImageHeapLayouter layouter = new ChunkedImageHeapLayouter(metaObj.heapInfo, auxImageHeapOffsetInAddressSpace.rawValue());

                try (Timer _ = AuxiliaryImageTracing.persistTimers.initHeap.start()) {
                    for (Iterator<Object> itr = heap.getPlainObjects().iterator(); itr.hasNext();) {
                        control.maybePoll();
                        Object obj = itr.next();
                        if (isInPrimaryImageHeap(obj)) {
                            itr.remove(); // for accurate trace output (if enabled)
                        } else {
                            assert obj != NULL_SENTINEL && obj != CLEAR_REFERENCE_SENTINEL : "should not be discovered as objects";
                            assert !replacedObjects.containsKey(obj) : "must not contain replaced objects";
                            ClassInitializationInfo init = DynamicHubIntrinsics.readHub(obj).getClassInitializationInfo();
                            if (!init.isBuildTimeInitialized()) {
                                assert !init.isInErrorState() : "should not be possible to allocate instances";
                                /*
                                 * GR-37592: we would have to initialize classes after loading an
                                 * auxiliary image, before it can be accessed, and in a sane order,
                                 * possibly the order of class initialization in the current
                                 * isolate.
                                 */
                                throw attachPathToRoot(obj, new UnsupportedOperationException("Objects of classes which are initialized at runtime currently cannot be persisted: " + obj.getClass()));
                            }

                            heap.addObjectToLayout(layouter, obj);
                        }
                    }
                }

                int auxImageHeapOffset;
                ImageHeapLayoutInfo layout;
                try (Timer _ = AuxiliaryImageTracing.persistTimers.layoutHeap.start()) {
                    control.poll();

                    /*
                     * Use the build-time page size to ensure the auxiliary image is compatible with
                     * all machines that can execute the corresponding native image. Note that if
                     * the auxiliary image contains JIT-compiled code that uses machine-specific CPU
                     * features, it might only be compatible with certain machines.
                     */
                    int pageSize = SubstrateOptions.getPageSize();
                    auxImageHeapOffset = NumUtil.roundUp(AuxiliaryImageFileHeader.getAllHeadersSize(), pageSize);
                    layout = layouter.layout(heap, pageSize, new BasicImageHeapObjectSorter(), () -> control.getCallback().shouldCancel());
                }

                if (layout.getSize() > maximumAllowedImageSize) {
                    throw new MaximumAuxiliaryImageSizeExceededException();
                }

                try (Timer _ = AuxiliaryImageTracing.persistTimers.write.start()) {
                    control.poll();

                    long imageHeapSizeInMemory = layout.getSize();
                    imageDataLength = NumUtil.safeToInt(auxImageHeapOffset + imageHeapSizeInMemory);
                    imageData = new byte[imageDataLength];
                    try (PinnedObject imageDataPin = PinnedObject.create(imageData)) {
                        Pointer imageDataPtr = imageDataPin.addressOfArrayElement(0);

                        heapDataPtr = imageDataPtr.add(auxImageHeapOffset);
                        UnsignedWord imageHeapToOriginObjectOffset = Word.zero();
                        for (ImageHeapObject info : heap.getObjects()) {
                            control.maybePoll();
                            writeObject(info);
                            if (info.getObject() == metaObj) {
                                imageHeapToOriginObjectOffset = Word.unsigned(info.getOffset());
                            }
                        }
                        VMError.guarantee(imageHeapToOriginObjectOffset.notEqual(0));

                        ByteBuffer heapBuffer = CTypeConversion.asByteBuffer(heapDataPtr, NumUtil.safeToInt(imageHeapSizeInMemory));
                        layouter.writeMetadata(heapBuffer, 0);
                        heapDataPtr = Word.nullPointer();
                        int imageHeapSizeInFile = getTrailingZerosBegin(heapBuffer);
                        assert imageHeapSizeInFile <= imageHeapSizeInMemory;
                        imageDataLength = auxImageHeapOffset + imageHeapSizeInFile;
                        assert imageDataLength <= imageData.length;

                        int auxHeaderOffset = AuxiliaryImageFileHeader.writeHeader(imageDataPtr, auxImageHeapOffset, imageHeapSizeInFile, imageHeapSizeInMemory);

                        AuxiliaryImageHeader header = (AuxiliaryImageHeader) imageDataPtr.add(auxHeaderOffset);
                        header.setPrimaryImageId(AuxiliaryImageLoader.getPrimaryImageId());
                        header.setAuxImageHeapOffsetInAddressSpace(auxImageHeapOffsetInAddressSpace);
                        header.setImageHeapToOriginObjectOffset(imageHeapToOriginObjectOffset);
                    }
                }

                if (imageDataLength > maximumAllowedImageSize) {
                    throw new MaximumAuxiliaryImageSizeExceededException();
                }
            } finally {
                AuxiliaryImageTracing.traceAfterSnapshot(heap, imageDataLength);
            }
        }

        private ObjectReferencesWalker getWalker(Object obj) {
            if (obj instanceof byte[] array) {
                RuntimeCodeReferenceWalker walker = codeRefsWalkers.get(array);
                if (walker != null) {
                    return walker;
                }
            }
            return defaultRefsWalker;
        }

        private void traverseReference(Object from, Object to, boolean callReplacers) {
            assert to != null && to != CLEAR_REFERENCE_SENTINEL && to != NULL_SENTINEL;
            if (heap.containsObject(to)) {
                assert !replacedObjects.containsKey(to);
                return;
            }
            Object obj = replacedObjects.get(to);
            if (obj == CLEAR_REFERENCE_SENTINEL) {
                if (isReferenceObjectReferent(from, to)) {
                    return; // safe: another reference to clear later
                }
                try {
                    checkDisallowed(to); // not a Reference object we can clear
                } catch (UnsupportedOperationException e) {
                    throw attachPathToRoot(from, e);
                }
                throw VMError.shouldNotReachHere("must be a disallowed object");
            } else if (obj != null) { // replaced
                if (obj != NULL_SENTINEL && !heap.containsObject(to)) {
                    traverseReference(from, obj, false); // replacement not yet traversed
                }
                return;
            }
            obj = to; // not replaced
            if (callReplacers) {
                for (AuxiliaryImageObjectReplacer replacer : replacers) {
                    obj = replacer.replace(obj, replacersAccess);
                    if (obj == null) {
                        obj = NULL_SENTINEL;
                        break;
                    }
                }
            }
            try {
                checkDisallowed(obj);
            } catch (UnsupportedOperationException e) {
                if (isReferenceObjectReferent(from, obj)) {
                    /*
                     * The object is not allowed in the auxiliary image heap, but it is referenced
                     * through a Reference object which we can simply clear in the auxiliary image.
                     * Users of Reference objects must expect and handle a cleared reference. Note
                     * that we cannot simply replace the Reference object itself because it could be
                     * an arbitrary subclass of Reference.
                     *
                     * This works only if the unsupported object is directly referenced. If it is
                     * part of an object graph reachable (only) via this Reference, we still fail.
                     * Supporting this case would need somewhat complex backtracking and also seems
                     * more error-prone and difficult to comprehend.
                     */
                    obj = CLEAR_REFERENCE_SENTINEL;
                } else {
                    throw attachPathToRoot(from, e);
                }
            }
            replaceObject(to, obj, false);
            if (obj == NULL_SENTINEL || obj == CLEAR_REFERENCE_SENTINEL) {
                return;
            }
            heap.addObject(obj, from);
            // Remember even image heap objects so to not offer to replace again, but filter later
            if (!isInPrimaryImageHeap(obj)) {
                traversal.addObject(obj);
            }
        }

        private UnsupportedOperationException attachPathToRoot(Object from, UnsupportedOperationException e) {
            Object root = metaObj;
            Object ref = from;
            StringBuilder b = new StringBuilder();
            while (ref != null && ref != root) {
                b.append(System.lineSeparator()).append("       <- ").append(ref.getClass().getName());
                if (ref instanceof ValidPersistedRuntimeCode) {
                    b.append(" root-name: ").append(((ValidPersistedRuntimeCode) ref).installedCode.getName());
                }
                ref = heap.getReachableFromForObject(ref);
            }
            if (b.length() > 0) {
                throw new UnsupportedOperationException(String.format("%s%n    Path to root object: %s", e.getMessage(), b.toString()), e);
            }
            throw e;
        }

        private static void checkDisallowed(Object obj) throws UnsupportedOperationException {
            DisallowedImageHeapObjects.check(obj, (msg, _, _) -> {
                throw new UnsupportedOperationException(msg);
            });
            if (AbstractPinnedObjectSupport.singleton().isPinnedSlow(obj)) {
                throw new UnsupportedOperationException("Object is pinned, but persisting and loading would move it: " + obj);
            }
            if (MonitorSupport.singleton().isLockedByAnyThread(obj)) {
                throw new UnsupportedOperationException("Object is locked, so some state might be unsafe to persist: " + obj);
            }
            // Note: locked AbstractOwnableSynchronizers reference live threads and fail
        }

        private void replaceObject(Object original, Object replacement, boolean checkDisallowed) {
            if (replacement != original) {
                if (checkDisallowed) {
                    checkDisallowed(replacement);
                }
                if (replacedObjects.containsKey(replacement)) {
                    throw new IllegalArgumentException("Replacement object has itself been replaced before");
                }
                Object existing = replacedObjects.putIfAbsent(original, replacement);
                if (existing != null) {
                    throw new IllegalArgumentException("Object has been replaced before");
                }
            }
        }

        private static boolean isReferenceObjectReferent(Object from, Object to) {
            return from instanceof Reference && ReferenceInternals.getReferent((Reference<?>) from) == to;
        }

        private void writeObject(ImageHeapObject info) {
            UnsignedWord offsetInAuxHeap = Word.unsigned(info.getOffset()).subtract(auxImageHeapOffsetInAddressSpace);
            writeObjectSafely(getWalker(info.getObject()), info, offsetInAuxHeap, System.identityHashCode(info.getObject()));
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while inspecting an object.")
        private void writeObjectSafely(ObjectReferencesWalker refsWalker, ImageHeapObject info, UnsignedWord offsetInAuxHeap, int identityHashCode) {
            Object obj = info.getObject();

            int identityHashOffset = LayoutEncoding.getIdentityHashOffset(obj);
            Pointer imageDataObjectPtr = heapDataPtr.add(offsetInAuxHeap);
            copyObjectData(obj, imageDataObjectPtr, identityHashOffset);

            ObjectLayout ol = ObjectLayout.singleton();
            writeHub(info, offsetInAuxHeap.add(ol.getHubOffset()), DynamicHubIntrinsics.readHub(obj));

            Pointer identityHashPtr = imageDataObjectPtr.add(identityHashOffset);
            IdentityHashCodeSupport.writeIdentityHashCodeToImageHeap(identityHashPtr, identityHashCode);

            objRefPatcher.setCurrentObjectOffsetInAuxHeap(offsetInAuxHeap);
            refsWalker.walkReferencesOf(obj, objRefPatcher);
        }

        private void writeHub(ImageHeapObject info, UnsignedWord offsetInAuxHeap, DynamicHub hub) {
            ObjectLayout ol = ObjectLayout.singleton();
            ObjectHeader oh = Heap.getHeap().getObjectHeader();

            Word hubDelta = Word.objectToUntrackedWord(hub).subtract(KnownIntrinsics.heapBase());
            long encoding = oh.encodeHubPointerForImageHeap(info, hubDelta.rawValue());
            writeValue(offsetInAuxHeap, encoding, ol.getHubSize());
        }

        /**
         * Copies the object data. The object header and the identity hashcode field in the target
         * are set to zero.
         */
        private static void copyObjectData(Object source, Pointer target, int identityHashOffset) {
            ObjectLayout ol = ObjectLayout.singleton();
            int headerSize = ol.getFirstFieldOffset();
            assert headerSize <= ol.getArrayLengthOffset();

            // The object currently might not have a (trailing) identity hash code field, while the
            // size we reserved for it includes it, so use the current size for copying.
            UnsignedWord bytesToCopy = LayoutEncoding.getMomentarySizeFromObject(source).subtract(headerSize);
            JavaMemoryUtil.copy(Word.objectToUntrackedPointer(source).add(headerSize), target.add(headerSize), bytesToCopy);

            /* Set object header and identity hashcode to zero. */
            JavaMemoryUtil.fill(target, Word.unsigned(headerSize), (byte) 0);
            target.writeInt(identityHashOffset, 0);
        }

        private void writeReference(UnsignedWord offsetInAuxHeap, long value) {
            writeValue(offsetInAuxHeap, value, ObjectLayout.singleton().getReferenceSize());
        }

        private void writeValue(UnsignedWord offsetInAuxHeap, long value, int size) {
            switch (size) {
                case Integer.BYTES:
                    heapDataPtr.writeInt(offsetInAuxHeap, NumUtil.safeToUInt(value));
                    break;
                case Long.BYTES:
                    heapDataPtr.writeLong(offsetInAuxHeap, value);
                    break;
                default:
                    throw VMError.shouldNotReachHereUnexpectedInput(size); // ExcludeFromJacocoGeneratedReport
            }
        }

        private final ObjectReferencePatcher objRefPatcher = new ObjectReferencePatcher();

        private final class ObjectReferencePatcher extends ObjectReferencesWalkerVisitor {
            private UnsignedWord currentObjectOffsetInAuxHeap;

            void setCurrentObjectOffsetInAuxHeap(UnsignedWord offsetInAuxHeap) {
                this.currentObjectOffsetInAuxHeap = offsetInAuxHeap;
            }

            @Override
            public void visitRegularReferences(Object obj, int firstOffset, boolean compressed, int referenceSize, int count) {
                Pointer objPtr = Word.objectToUntrackedPointer(obj);
                for (int i = 0; i < count; i++) {
                    int offset = firstOffset + i * referenceSize;
                    Object target = ReferenceAccess.singleton().readObjectAt(objPtr.add(offset), compressed);
                    writeTarget(obj, offset, compressed, target, false);
                }
            }

            @Override
            public void visitReferenceInCode(Object obj, int offset, boolean compressed, Object target) {
                writeTarget(obj, offset, compressed, target, false);
            }

            @Override
            public void visitReferenceObjectReferent(Object obj, int offset, boolean compressed, Object target) {
                writeTarget(obj, offset, compressed, target, true);
            }

            @Override
            public void visitObjectMonitor(Object obj, int offset, boolean compressed) {
                writeNull(obj, offset, compressed);
            }

            @Override
            public void visitReferenceObjectDiscoveredLink(Object obj, int offset, boolean compressed) {
                writeNull(obj, offset, compressed);
            }

            private void writeTarget(Object obj, int offset, boolean compressed, Object ref, boolean isRefObjReferent) {
                if (ref == null) {
                    if (!compressed) { // must overwrite uncompressed null == heap base address
                        writeNull(obj, offset, compressed);
                    }
                    return;
                }
                Object target = mapGetOrDefault(replacedObjects, ref, ref);
                if (target == NULL_SENTINEL) {
                    writeNull(obj, offset, compressed);
                } else if (target == CLEAR_REFERENCE_SENTINEL) {
                    VMError.guarantee(isRefObjReferent);
                    // We should also enqueue this Reference object in its ReferenceQueue if it is
                    // part of the auxiliary image, so that data structures like WeakHashMap in the
                    // auxiliary image can be cleaned of cleared references once the image is loaded
                    writeNull(obj, offset, compressed);
                } else if (!compressed || ref != target || !isInPrimaryImageHeap(target)) {
                    assert ref == target || !(obj instanceof ImageHeapInfo) : "ImageHeapInfo range information must be final";
                    /*
                     * Some references in stored continuations will be uncompressed because they are
                     * in saved stack frames. We store these references in compressed form and
                     * uncompress them when loading the image, see class PersistedContinuations.
                     */
                    assert compressed || obj.getClass() == StoredContinuation.class : "Regular heap references must be compressed";

                    UnsignedWord targetOffsetFromImageHeap;
                    if (isInPrimaryImageHeap(target)) {
                        targetOffsetFromImageHeap = Word.objectToUntrackedPointer(target).subtract(KnownIntrinsics.heapBase());
                    } else {
                        targetOffsetFromImageHeap = Word.unsigned(heap.getObject(target).getOffset());
                    }
                    UnsignedWord compressedTargetRef = targetOffsetFromImageHeap.unsignedShiftRight(ReferenceAccess.singleton().getCompressionShift());
                    writeReference(currentObjectOffsetInAuxHeap.add(offset), compressedTargetRef.rawValue());
                }
            }

            void writeNull(Object obj, int offset, boolean compressed) {
                assert compressed || obj.getClass() == StoredContinuation.class : "Regular heap references must be compressed";
                writeReference(currentObjectOffsetInAuxHeap.add(offset), 0);
            }

            @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "This doesn't allocate except for assertions/exceptions which cause us to abort.")
            private <K, V> V mapGetOrDefault(EconomicMap<K, V> map, K key, V defaultValue) {
                V value = map.get(key);
                return (value != null) ? value : defaultValue;
            }
        }

        final class ReplacerAccessImpl implements InternalReplacersAccess, AuxiliaryImageObjectReplacer.EpilogueAccess {
            @Override
            public void replaceLate(Object original, Object replacement) {
                if (original == replacement) {
                    return;
                }
                for (Object value : replacedObjects.getValues()) {
                    if (value == original) {
                        throw new IllegalArgumentException("Object that has replaced another object cannot itself be replaced");
                    }
                }
                replaceObject(original, replacement, true);
                AuxiliaryImageHeapObject info = heap.removeObject(original);
                Object originalFrom = info != null ? info.getReachableFrom() : null;
                boolean seenOriginal = (originalFrom != null);
                boolean seenReplacement = heap.containsObject(replacement);
                if (seenOriginal && !seenReplacement) {
                    // In the epilogue phase: never call replacers.
                    traverseReference(originalFrom, replacement, false);
                }
            }

            @Override
            public void addObject(Object obj, Object referencedFrom, boolean callReplacers) {
                traverseReference(referencedFrom, obj, callReplacers);
            }

            @Override
            public void registerCodeWalker(byte[] array, RuntimeCodeReferenceWalker walker) {
                ObjectReferencesWalker existing = codeRefsWalkers.putIfAbsent(array, walker);
                if (existing != null) {
                    throw new IllegalArgumentException("Custom walker has been registered before");
                }
            }
        }
    }
}

final class AuxiliaryImageFileHeader {
    private static final byte[] AUX_HEADER_SEGMENT_NAME = "GraalVM Auxiliary Native Image\0".getBytes(StandardCharsets.US_ASCII);
    private static final short ELF_FILE_HEADER_SIZE = 0x40;
    private static final short ELF_PROGRAM_HEADER_SIZE = 0x38;
    private static final int ELF_HEADERS_SIZE = ELF_FILE_HEADER_SIZE + ELF_PROGRAM_HEADER_SIZE + ELF_PROGRAM_HEADER_SIZE;
    private static final int AUX_HEADER_SEGMENT_OFFSET_IN_FILE = ELF_HEADERS_SIZE;
    private static final int AUX_HEADER_STRUCTURE_OFFSET_IN_SEGMENT = 4 + 4 + 4 + NumUtil.roundUp(AUX_HEADER_SEGMENT_NAME.length, 4);

    static final int AUX_IMAGE_HEAP_OFFSET_IN_FILE_WORD_OFFSET_IN_FILE = ELF_FILE_HEADER_SIZE + ELF_PROGRAM_HEADER_SIZE + 4 + 4;
    static final int AUX_IMAGE_HEAP_SIZE_IN_FILE_WORD_OFFSET_IN_FILE = AUX_IMAGE_HEAP_OFFSET_IN_FILE_WORD_OFFSET_IN_FILE + 8 + 8 + 8;
    static final int AUX_IMAGE_HEAP_SIZE_IN_MEMORY_WORD_OFFSET_IN_FILE = AUX_IMAGE_HEAP_SIZE_IN_FILE_WORD_OFFSET_IN_FILE + 8;
    static final int AUX_HEADER_STRUCTURE_OFFSET_IN_FILE = AUX_HEADER_SEGMENT_OFFSET_IN_FILE + AUX_HEADER_STRUCTURE_OFFSET_IN_SEGMENT;

    private static int getAuxHeaderSegmentSize() {
        return AUX_HEADER_STRUCTURE_OFFSET_IN_SEGMENT + NumUtil.roundUp(SizeOf.get(AuxiliaryImageHeader.class), 4);
    }

    static int getAllHeadersSize() {
        return AUX_HEADER_SEGMENT_OFFSET_IN_FILE + getAuxHeaderSegmentSize();
    }

    static int writeHeader(Pointer buffer, long auxImageHeapOffset, long imageHeapSizeInFile, long imageHeapSizeInMemory) {
        VMError.guarantee(SubstrateTarget.getWordSize() == 8, "supports only ELF64");
        ByteBuffer b = CTypeConversion.asByteBuffer(buffer, getAllHeadersSize());
        assert AuxiliaryImagePersistence.getTrailingZerosBegin(b) == 0 : "expecting all zeros where header will be written";
        b.put(new byte[]{
                        0x7f, 'E', 'L', 'F',
                        2, // 64-bit
                        (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) ? (byte) 1 : (byte) 2,
                        1, // version byte
                        0, // OS ABI: none/SysV
                        0, // ABI version: (unused)
                        0, 0, 0, 0, 0, 0, 0, // (padding)
        });
        b.putShort((short) 0); // object file type: none
        b.putShort((short) 0); // instruction set: none
        b.putInt(1); // version
        b.putLong(0); // entry point: none
        b.putLong(ELF_FILE_HEADER_SIZE); // program header table offset: after this file header
        b.putLong(0); // section header table offset: none
        b.putInt(0); // flags: none
        b.putShort(ELF_FILE_HEADER_SIZE); // size of this header
        b.putShort(ELF_PROGRAM_HEADER_SIZE); // size of a program header table entry
        b.putShort((short) 2); // number of program header table entries
        b.putShort((short) 0); // size of a section header table entry
        b.putShort((short) 0); // number of a section header table entries
        b.putShort((short) 0); // index of section header table entry with section names
        assert b.position() == ELF_FILE_HEADER_SIZE : b.position();

        // Aux header structure program header
        b.putInt(0x4); // type: PT_NOTE
        b.putInt(0); // flags (unused)
        b.putLong(ELF_HEADERS_SIZE); // offset in file
        b.putLong(0); // virtual address: (not loaded)
        b.putLong(0); // physical address: (not loaded)
        b.putLong(getAuxHeaderSegmentSize()); // size in file
        b.putLong(0); // size in memory: (not loaded)
        b.putLong(0); // alignment: none
        assert b.position() == ELF_FILE_HEADER_SIZE + ELF_PROGRAM_HEADER_SIZE : b.position();

        // Aux heap program header
        b.putInt(0x1); // type: PT_LOAD
        b.putInt(0); // flags (unused)
        assert b.position() == AUX_IMAGE_HEAP_OFFSET_IN_FILE_WORD_OFFSET_IN_FILE : b.position();
        b.putLong(auxImageHeapOffset); // offset in file
        b.putLong(0); // virtual address: unset (determined from aux header when loading)
        b.putLong(0); // physical address: unset (not necessary)
        assert b.position() == AUX_IMAGE_HEAP_SIZE_IN_FILE_WORD_OFFSET_IN_FILE : b.position();
        b.putLong(imageHeapSizeInFile); // size in file
        assert b.position() == AUX_IMAGE_HEAP_SIZE_IN_MEMORY_WORD_OFFSET_IN_FILE : b.position();
        b.putLong(imageHeapSizeInMemory); // size in memory
        b.putLong(0); // alignment: none (predetermined)
        assert b.position() == ELF_HEADERS_SIZE : b.position();

        // Aux header segment (PT_NOTE)
        assert b.position() == AUX_HEADER_SEGMENT_OFFSET_IN_FILE : b.position();
        b.putInt(AUX_HEADER_SEGMENT_NAME.length); // name size (without padding)
        b.putInt(SizeOf.get(AuxiliaryImageHeader.class)); // descriptor (data) size
        b.putInt(0); // type
        b.put(AUX_HEADER_SEGMENT_NAME);
        for (int i = 0; (AUX_HEADER_SEGMENT_NAME.length + i) % 4 != 0; i++) {
            b.put((byte) 0);
        }
        assert b.position() == AUX_HEADER_STRUCTURE_OFFSET_IN_FILE : b.position();
        return b.position(); // descriptor (data)
    }
}
