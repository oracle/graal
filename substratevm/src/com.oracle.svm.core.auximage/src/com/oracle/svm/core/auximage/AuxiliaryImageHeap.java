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

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.SmallestPossibleObject;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.shared.util.VMError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

final class AuxiliaryImageHeap implements ImageHeap {
    private final Object root = new Object();
    private final ObjectLayout objectLayout = ObjectLayout.singleton();
    private final int minArraySize = objectLayout.getMinImageHeapArraySize();
    private final int intArrayScale = objectLayout.getArrayIndexScale(JavaKind.Int);
    private final int minInstanceSize = objectLayout.getMinImageHeapInstanceSize();
    private final long maximumAllowedAuxiliaryImageSize;
    private long estimatedAuxiliaryImageSize;

    private final EconomicMap<Object, AuxiliaryImageHeapObject> objects = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

    AuxiliaryImageHeap(long sizeLimit) {
        maximumAllowedAuxiliaryImageSize = sizeLimit;
    }

    Object getRootObject() {
        return root;
    }

    void addObject(Object obj, Object from) {
        long size = LayoutEncoding.getSizeFromObjectAddOptionalIdHashField(obj).rawValue();
        AuxiliaryImageHeapObject info = new AuxiliaryImageHeapObject(obj, from, size);
        AuxiliaryImageHeapObject oldInfo = objects.put(obj, info);
        assert oldInfo == null : "Object is already present in the auxiliary image heap";

        estimatedAuxiliaryImageSize += size;
        if (estimatedAuxiliaryImageSize > maximumAllowedAuxiliaryImageSize) {
            throw new MaximumAuxiliaryImageSizeExceededException();
        }
    }

    void addObjectToLayout(ImageHeapLayouter layouter, Object obj) {
        AuxiliaryImageHeapObject info = objects.get(obj);
        assert info != null : "Object not added to the auxiliary image heap";
        DynamicHub hub = DynamicHubIntrinsics.readHub(obj);
        boolean immutable = isImmutable(obj);
        boolean relocatable = false; // never: all references are relative to a known base
        boolean containsRefs;
        if (hub.isInstanceClass()) {
            containsRefs = hub.getMonitorOffset() != 0 || !DynamicHubSupport.hasEmptyReferenceMap(hub);
        } else {
            int enc = hub.getLayoutEncoding();
            containsRefs = LayoutEncoding.isObjectArray(enc) || LayoutEncoding.isHybrid(enc);
        }
        layouter.assignObjectToPartition(info, immutable, containsRefs, relocatable, false);
    }

    private static boolean isImmutable(Object obj) {
        // GR-21784: retain sufficient analysis results to reliably decide at runtime
        return obj instanceof String || obj instanceof Byte || obj instanceof Character || obj instanceof Short || obj instanceof Integer ||
                        obj instanceof Long || obj instanceof Float || obj instanceof Double || obj instanceof Boolean || obj instanceof StoredContinuation;
    }

    boolean containsObject(Object obj) {
        return objects.containsKey(obj);
    }

    AuxiliaryImageHeapObject removeObject(Object obj) {
        return objects.removeKey(obj);
    }

    Object getReachableFromForObject(Object obj) {
        AuxiliaryImageHeapObject info = objects.get(obj);
        return info != null ? info.getReachableFrom() : null;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "This doesn't allocate except for assertions/exceptions which cause us to abort.")
    AuxiliaryImageHeapObject getObject(Object obj) {
        return objects.get(obj);
    }

    Iterable<Object> getPlainObjects() {
        return objects.getKeys();
    }

    @Override
    public Iterable<? extends AuxiliaryImageHeapObject> getObjects() {
        return objects.getValues();
    }

    @Override
    public AuxiliaryImageHeapObject addLateToImageHeap(Object object, Object reason) {
        throw VMError.shouldNotReachHere("Not supported for arbitrary objects");
    }

    @Override
    public AuxiliaryImageHeapObject addFillerObject(int size) {
        if (size >= minArraySize) {
            int arrayLength = (size - minArraySize) / intArrayScale;
            int[] fillerObj = new int[arrayLength];
            AuxiliaryImageHeapObject filler = new AuxiliaryImageHeapObject(fillerObj, size);
            assert LayoutEncoding.getSizeFromObjectAddOptionalIdHashField(filler.getObject()).equal(size);
            objects.put(fillerObj, filler);
            return filler;
        } else if (size >= minInstanceSize) {
            Object fillerObj = new SmallestPossibleObject();
            AuxiliaryImageHeapObject filler = new AuxiliaryImageHeapObject(fillerObj, minInstanceSize);
            assert LayoutEncoding.getSizeFromObjectAddOptionalIdHashField(filler.getObject()).equal(minInstanceSize);
            objects.put(fillerObj, filler);
            return filler;
        } else {
            return null;
        }
    }

    @Override
    public int countPatchAndVerifyDynamicHubs() {
        // The auxiliary image heap never contains dynamic hubs.
        return 0;
    }
}

final class AuxiliaryImageHeapObject implements ImageHeapObject {
    private final Object obj;
    private final Object reachableFrom;
    private final long size;
    private ImageHeapPartition partition;
    private long offsetInPartition = -1;

    AuxiliaryImageHeapObject(Object obj, long size) {
        this(obj, null, size);
    }

    AuxiliaryImageHeapObject(Object obj, Object reachableFrom, long size) {
        assert obj != null && size > 0;
        this.obj = obj;
        this.reachableFrom = reachableFrom;
        this.size = size;
    }

    public Object getReachableFrom() {
        return reachableFrom;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public Object getWrapped() {
        return getObject();
    }

    @Override
    public Object getObject() {
        return obj;
    }

    @Override
    public Class<?> getObjectClass() {
        return obj.getClass();
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public ResolvedJavaType getObjectType() {
        throw VMError.shouldNotReachHere("Only for native image builds, should not be reachable for auxiliary images");
    }

    @Override
    public long getOffset() {
        assert offsetInPartition >= 0;
        assert partition != null;
        return partition.getStartOffset() + offsetInPartition;
    }

    @Override
    public ImageHeapPartition getPartition() {
        return partition;
    }

    @Override
    public void setHeapPartition(ImageHeapPartition partition) {
        assert this.partition == null;
        this.partition = partition;
    }

    @Override
    public void setOffsetInPartition(long offset) {
        assert offsetInPartition == -1;
        offsetInPartition = offset;
    }
}
