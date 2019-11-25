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
package com.oracle.svm.core.image;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.image.AbstractImageHeapLayouter.ImageHeapLayout;

/**
 * This class is responsible for computing and storing the layout of the native image heap. A native
 * image heap consist of multiple {@link ImageHeapPartition}s. Every object in the native image
 * heap, is assigned to a position within a {@link ImageHeapPartition}.
 */
@Platforms(value = Platform.HOSTED_ONLY.class)
public interface ImageHeapLayouter {
    void initialize();

    /**
     * Returns all native image heap partitions.
     */
    ImageHeapPartition[] getPartitions();

    /**
     * Assign an object to the most suitable partition.
     */
    void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable);

    /**
     * Determines in which order image heap objects are placed in image heap partitions. After that,
     * every image heap object has a partition-relative address (however, objects don't have an
     * absolute address yet as neither the size nor the sequence of the heap partitions is fixed
     * yet).
     */
    void assignPartitionRelativeOffsets(ImageHeap imageHeap);

    /**
     * This method places all heap partitions as one contiguous memory block in one section. After
     * calling that method, all native image heap objects are assigned their final address. This
     * address must not change anymore.
     */
    ImageHeapLayout layoutPartitionsAsContiguousHeap(String heapSectionName, int pageSize);

    /**
     * This method layouts read-only and writable data as two separate memory blocks so that the
     * data can be put in different sections of the native image. After calling that method, all
     * native image heap objects are assigned their final address. This address must not change
     * anymore.
     */
    ImageHeapLayout layoutPartitionsAsSeparatedHeap(String roDataSectionName, long roConstantsEndOffset, String rwDataSectionName, long rwGlobalsEndOffset);
}
