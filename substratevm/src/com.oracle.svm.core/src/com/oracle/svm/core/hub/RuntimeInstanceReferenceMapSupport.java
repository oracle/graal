/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder.InstanceReferenceMap;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.util.ByteArrayReader;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import jdk.graal.compiler.word.Word;

/**
 * Manages and deduplicates {@link InstanceReferenceMap}s for {@link DynamicHub}s that are loaded at
 * run-time. Each {@link InstanceReferenceMap} is stored in a {@code byte[]} that lives in the
 * {@link Metaspace}.
 */
public class RuntimeInstanceReferenceMapSupport {
    private final EconomicMap<ReferenceMapHolder, ReferenceMapHolder> refMaps = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeInstanceReferenceMapSupport() {
    }

    @Fold
    public static RuntimeInstanceReferenceMapSupport singleton() {
        return ImageSingletons.lookup(RuntimeInstanceReferenceMapSupport.class);
    }

    /**
     * If no matching reference map exists, a new one is created. Otherwise, the existing reference
     * map is reused.
     *
     * @return a compressed offset, relative to the heap base, that points to an
     *         {@link InstanceReferenceMap}.
     */
    public int getOrCreateReferenceMap(DynamicHub superHub, FieldInfo... declaredInstanceFields) {
        /* Create a bitmap and mark where there are declared object fields. */
        SubstrateReferenceMap map = new SubstrateReferenceMap();
        for (var field : declaredInstanceFields) {
            if (field.hasObjectType()) {
                map.markReferenceAtOffset(field.offset(), true);
            }
        }

        /* If there are no declared object fields, reuse the reference map from the super class. */
        if (map.isEmpty()) {
            return superHub.getReferenceMapCompressedOffset();
        }

        /* Add the object fields from all super classes to the bitmap. */
        InstanceReferenceMap superMap = DynamicHubSupport.getInstanceReferenceMap(superHub);
        InstanceReferenceMapDecoder.walkReferences(Word.nullPointer(), superMap, new MarkBitmapVisitor(map), null);

        /*
         * Encode the bitmap as a reference map and check if there is already a matching one. If
         * there is none, create a new one in the metaspace.
         */
        UnsafeArrayTypeWriter buffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        long start = InstanceReferenceMapEncoder.encode(buffer, map.getOffsets());
        assert start == 0;
        return putIfAbsent(buffer.toArray());
    }

    private synchronized int putIfAbsent(byte[] newHeapMap) {
        ReferenceMapHolder newHeapMapHolder = new ReferenceMapHolder(newHeapMap);
        ReferenceMapHolder existingMetaspaceMapHolder = refMaps.get(newHeapMapHolder);
        if (existingMetaspaceMapHolder != null) {
            return toCompressedOffset(existingMetaspaceMapHolder.refMap);
        }

        /* Copy the data to the metaspace. */
        byte[] newMetaspaceMap = Metaspace.singleton().allocateByteArray(newHeapMap.length);
        System.arraycopy(newHeapMap, 0, newMetaspaceMap, 0, newHeapMap.length);

        /* Store the new reference map in the hash map. */
        ReferenceMapHolder newMetaspaceMapHolder = new ReferenceMapHolder(newMetaspaceMap);
        refMaps.put(newMetaspaceMapHolder, newMetaspaceMapHolder);
        return toCompressedOffset(newMetaspaceMapHolder.refMap);
    }

    @SuppressWarnings("unchecked")
    private static int toCompressedOffset(byte[] metaspaceRefMapArray) {
        assert Metaspace.singleton().isInAddressSpace(metaspaceRefMapArray);

        NonmovableArray<Byte> array = (NonmovableArray<Byte>) Word.objectToUntrackedPointer(metaspaceRefMapArray);
        InstanceReferenceMap metaspaceMap = NonmovableArrays.getArrayBase(array);
        return InstanceReferenceMapEncoder.computeReferenceMapCompressedOffset(metaspaceMap);
    }

    /* Remove once GR-60069 is merged. */
    public record FieldInfo(int offset, boolean hasObjectType) {
    }

    private record ReferenceMapHolder(byte[] refMap) {
        @Override
        public int hashCode() {
            return Arrays.hashCode(refMap);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ReferenceMapHolder that = (ReferenceMapHolder) o;
            return Arrays.equals(refMap, that.refMap);
        }
    }

    private record MarkBitmapVisitor(SubstrateReferenceMap map) implements ObjectReferenceVisitor {
        @Override
        public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
            Pointer pos = firstObjRef;
            Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
            while (pos.belowThan(end)) {
                visitObjectReference(pos, compressed);
                pos = pos.add(referenceSize);
            }
        }

        private void visitObjectReference(Pointer objRef, boolean compressed) {
            int offset = NumUtil.safeToInt(objRef.rawValue());
            map.markReferenceAtOffset(offset, compressed);
        }
    }
}
