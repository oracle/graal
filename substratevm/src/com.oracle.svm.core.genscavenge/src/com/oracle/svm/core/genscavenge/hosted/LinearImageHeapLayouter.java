/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.hosted;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.image.AbstractImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;

@Platforms(value = Platform.HOSTED_ONLY.class)
public class LinearImageHeapLayouter extends AbstractImageHeapLayouter<LinearImageHeapPartition> {
    public LinearImageHeapLayouter() {
    }

    @Override
    public void initialize() {
        if (useHeapBase()) {
            /*
             * Zero designates null, so adding some explicit padding at the beginning of the native
             * image heap is the easiest approach to make object offsets strictly greater than 0.
             */
            assert this.getReadOnlyPrimitive().getSize() == 0L;
            LinearImageHeapPartition firstPartition = this.getReadOnlyPrimitive();
            firstPartition.size += ConfigurationValues.getObjectLayout().getAlignment();
        }
    }

    @Override
    protected LinearImageHeapPartition[] createPartitionsArray(int count) {
        return new LinearImageHeapPartition[count];
    }

    @Override
    protected LinearImageHeapPartition createPartition(String name, boolean containsReferences, boolean writable) {
        return new LinearImageHeapPartition(name, writable);
    }

    @Override
    public void assignPartitionRelativeOffsets(ImageHeap imageHeap) {
        for (ImageHeapObject info : imageHeap.getObjects()) {
            ImageHeapPartition partition = info.getPartition();
            partition.allocate(info);

            assert ConfigurationValues.getObjectLayout().isAligned(info.getOffsetInPartition()) : "start: " + info.getOffsetInPartition() + " must be aligned.";
            assert ConfigurationValues.getObjectLayout().isAligned(partition.getSize()) : "size: " + partition.getSize() + " must be aligned.";
        }

        initializeHeapInfo();
    }

    /**
     * Store which objects are at the boundaries of the image heap partitions. Here, we also merge
     * the read-only reference partition with the read-only relocatable partition.
     */
    private void initializeHeapInfo() {
        Object firstReadOnlyReferenceObject = getReadOnlyReference().firstObject;
        if (firstReadOnlyReferenceObject == null) {
            firstReadOnlyReferenceObject = getReadOnlyRelocatable().firstObject;
        }

        Object lastReadOnlyReferenceObject = getReadOnlyRelocatable().lastObject;
        if (lastReadOnlyReferenceObject == null) {
            lastReadOnlyReferenceObject = getReadOnlyReference().lastObject;
        }
        HeapImpl.getImageHeapInfo().initialize(getReadOnlyPrimitive().firstObject, getReadOnlyPrimitive().lastObject, firstReadOnlyReferenceObject, lastReadOnlyReferenceObject,
                        getWritablePrimitive().firstObject, getWritablePrimitive().lastObject, getWritableReference().firstObject, getWritableReference().lastObject);
    }
}
