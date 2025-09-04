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

package com.oracle.svm.hosted.webimage.wasmgc.image;

import java.nio.ByteBuffer;
import java.util.stream.StreamSupport;

import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCHeapWriter;

/**
 * Manages WasmGC heap partitions.
 * <p>
 * Currently, everything except primitive arrays are assigned to the pseudo partition. Primitive
 * arrays can have their content directly serialized into a binary format.
 *
 * @see WasmGCPartition
 * @see WasmGCHeapWriter
 */
public class WasmGCHeapLayouter implements ImageHeapLayouter {

    /**
     * Partition for image heap objects which are serialized into a data segment.
     */
    private final WasmGCPartition singlePartition = new WasmGCPartition("WasmGCImageHeap", false);
    /**
     * Pseudo partitions for objects which are not serialized into a binary buffer.
     */
    private final WasmGCPartition pseudoPartition = new WasmGCPartition("WasmGCPseudoPartition", true);

    @Override
    public ImageHeapPartition[] getPartitions() {
        return new ImageHeapPartition[]{singlePartition, pseudoPartition};
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable, boolean patched) {
        if (info.getWrapped() instanceof ImageHeapPrimitiveArray) {
            singlePartition.add(info);
        } else {
            pseudoPartition.add(info);
        }
    }

    @Override
    public WasmGCImageHeapLayoutInfo layout(ImageHeap imageHeap, int pageSize, ImageHeapLayouterCallback callback) {
        layoutPseudoPartition();
        doLayout();

        long totalSize = StreamSupport.stream(imageHeap.getObjects().spliterator(), false).mapToLong(ImageHeapObject::getSize).sum();
        long serializedSize = singlePartition.getStartOffset() + singlePartition.getSize();
        return new WasmGCImageHeapLayoutInfo(serializedSize, totalSize);
    }

    private void doLayout() {
        int offset = 0;
        for (ImageHeapObject info : singlePartition.getObjects()) {
            // Only primitive arrays are supposed to be in this partition
            ImageHeapPrimitiveArray primitiveArray = (ImageHeapPrimitiveArray) info.getWrapped();
            info.setOffsetInPartition(offset);
            offset += primitiveArray.getType().getComponentType().getStorageKind().getByteCount() * primitiveArray.getLength();
        }

        singlePartition.setSize(offset);
    }

    private void layoutPseudoPartition() {
        // Approximate size of the partition (based on the SVM object layout size of the objects)
        long pseudoSize = 0;
        for (ImageHeapObject info : pseudoPartition.getObjects()) {
            info.setOffsetInPartition(0);
            pseudoSize = info.getSize();
        }

        pseudoPartition.setSize(pseudoSize);
    }

    @Override
    public void writeMetadata(ByteBuffer imageHeapBytes, long imageHeapOffsetInBuffer) {
        throw VMError.shouldNotReachHere("This method does not make sense in this backend");
    }
}
