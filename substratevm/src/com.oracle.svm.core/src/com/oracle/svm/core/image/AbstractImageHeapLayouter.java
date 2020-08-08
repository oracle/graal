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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;

public abstract class AbstractImageHeapLayouter<T extends AbstractImageHeapLayouter.AbstractImageHeapPartition> implements ImageHeapLayouter {
    /** A partition holding objects with only read-only primitive values, but no references. */
    private static final int READ_ONLY_PRIMITIVE = 0;
    /** A partition holding objects with read-only references and primitive values. */
    private static final int READ_ONLY_REFERENCE = 1;
    /**
     * A pseudo-partition used during image building to consolidate objects that contain relocatable
     * references.
     * <p>
     * Collecting the relocations together means the dynamic linker has to operate on less of the
     * image heap during image startup, and it means that less of the image heap has to be
     * copied-on-write if the image heap is relocated in a new process.
     * <p>
     * A relocated reference is read-only once relocated, e.g., at runtime. The read-only relocation
     * partition does not exist as a separate partition in the generated image. Instead, the
     * read-only reference partition is resized to include the read-only relocation partition as
     * well.
     */
    private static final int READ_ONLY_RELOCATABLE = 2;
    /** A partition holding objects with writable primitive values, but no references. */
    private static final int WRITABLE_PRIMITIVE = 3;
    /** A partition holding objects with writable references and primitive values. */
    private static final int WRITABLE_REFERENCE = 4;
    /** A partition holding very large writable objects with or without references. */
    private static final int WRITABLE_HUGE = 5;
    /**
     * A partition holding very large read-only objects with or without references, but never with
     * relocatable references.
     */
    private static final int READ_ONLY_HUGE = 6;

    private static final int PARTITION_COUNT = 7;

    private final T[] partitions;

    @Override
    public T[] getPartitions() {
        return partitions;
    }

    public AbstractImageHeapLayouter() {
        this.partitions = createPartitionsArray(PARTITION_COUNT);
        this.partitions[READ_ONLY_PRIMITIVE] = createPartition("readOnlyPrimitive", false, false, false);
        this.partitions[READ_ONLY_REFERENCE] = createPartition("readOnlyReference", true, false, false);
        this.partitions[READ_ONLY_RELOCATABLE] = createPartition("readOnlyRelocatable", true, false, false);
        this.partitions[WRITABLE_PRIMITIVE] = createPartition("writablePrimitive", false, true, false);
        this.partitions[WRITABLE_REFERENCE] = createPartition("writableReference", true, true, false);
        this.partitions[WRITABLE_HUGE] = createPartition("writableHuge", true, true, true);
        this.partitions[READ_ONLY_HUGE] = createPartition("readOnlyHuge", true, false, true);
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable) {
        T partition = choosePartition(info, immutable, references, relocatable);
        info.setHeapPartition(partition);
        partition.assign(info);
    }

    @Override
    public ImageHeapLayoutInfo layout(ImageHeap imageHeap, int pageSize) {
        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        assert pageSize % objectAlignment == 0 : "Page size does not match object alignment";

        for (T partition : getPartitions()) {
            int startAlignment = objectAlignment;
            int endAlignment = objectAlignment;
            if (partition == getReadOnlyRelocatable()) {
                startAlignment = pageSize;
                endAlignment = pageSize;
            } else if (partition == getWritablePrimitive()) {
                startAlignment = pageSize;
            } else if (partition == getWritableHuge()) {
                endAlignment = pageSize;
            }
            partition.setStartAlignment(startAlignment);
            partition.setEndAlignment(endAlignment);
        }

        ImageHeapLayoutInfo layoutInfo = doLayout(imageHeap);

        for (T partition : getPartitions()) {
            assert partition.getStartOffset() % partition.getStartAlignment() == 0;
            assert (partition.getStartOffset() + partition.getSize()) % partition.getEndAlignment() == 0;
        }

        assert layoutInfo.getReadOnlyRelocatableOffset() % pageSize == 0 && layoutInfo.getReadOnlyRelocatableSize() % pageSize == 0;
        assert layoutInfo.getWritableOffset() % pageSize == 0 && layoutInfo.getWritableSize() % pageSize == 0;

        return layoutInfo;
    }

    @Override
    public void writeMetadata(ByteBuffer imageHeapBytes) {
        // For implementation in subclasses, if necessary.
    }

    protected abstract ImageHeapLayoutInfo doLayout(ImageHeap imageHeap);

    protected T getReadOnlyPrimitive() {
        return getPartitions()[READ_ONLY_PRIMITIVE];
    }

    protected T getReadOnlyReference() {
        return getPartitions()[READ_ONLY_REFERENCE];
    }

    protected T getReadOnlyRelocatable() {
        return getPartitions()[READ_ONLY_RELOCATABLE];
    }

    protected T getWritablePrimitive() {
        return getPartitions()[WRITABLE_PRIMITIVE];
    }

    protected T getWritableReference() {
        return getPartitions()[WRITABLE_REFERENCE];
    }

    protected T getWritableHuge() {
        return getPartitions()[WRITABLE_HUGE];
    }

    protected T getReadOnlyHuge() {
        return getPartitions()[READ_ONLY_HUGE];
    }

    /** The size in bytes at and above which an object should be assigned to the huge partitions. */
    protected long getHugeObjectThreshold() {
        // Do not use huge partitions by default, they remain empty and should not consume space
        return Long.MAX_VALUE;
    }

    private T choosePartition(@SuppressWarnings("unused") ImageHeapObject info, boolean immutable, boolean hasReferences, boolean hasRelocatables) {
        if (immutable) {
            if (hasRelocatables) {
                VMError.guarantee(info.getSize() < getHugeObjectThreshold(), "Objects with relocatable pointers cannot be huge objects");
                return getReadOnlyRelocatable();
            }
            if (info.getSize() >= getHugeObjectThreshold()) {
                VMError.guarantee(!(info.getObject() instanceof DynamicHub), "Class metadata (dynamic hubs) cannot be huge objects");
                return getReadOnlyHuge();
            }
            return hasReferences ? getReadOnlyReference() : getReadOnlyPrimitive();
        } else {
            assert !(info.getObject() instanceof DynamicHub) : "Class metadata (dynamic hubs) cannot be writable";
            if (info.getSize() >= getHugeObjectThreshold()) {
                return getWritableHuge();
            }
            return hasReferences ? getWritableReference() : getWritablePrimitive();
        }
    }

    protected ImageHeapLayoutInfo createDefaultLayoutInfo() {
        long writableBegin = getWritablePrimitive().getStartOffset();
        long writableEnd = getWritableHuge().getStartOffset() + getWritableHuge().getSize();
        long writableSize = writableEnd - writableBegin;
        long imageHeapSize = getReadOnlyHuge().getStartOffset() + getReadOnlyHuge().getSize();
        return new ImageHeapLayoutInfo(writableBegin, writableSize, getReadOnlyRelocatable().getStartOffset(), getReadOnlyRelocatable().getSize(), imageHeapSize);
    }

    protected abstract T[] createPartitionsArray(int count);

    protected abstract T createPartition(String name, boolean containsReferences, boolean writable, boolean hugeObjects);

    /**
     * The native image heap comes in partitions. Each partition holds objects with different
     * properties (read-only/writable, primitives/objects).
     */
    public abstract static class AbstractImageHeapPartition implements ImageHeapPartition {
        private final String name;
        private final boolean writable;

        private int startAlignment = -1;
        private int endAlignment = -1;
        private final List<ImageHeapObject> objects = new ArrayList<>();

        public AbstractImageHeapPartition(String name, boolean writable) {
            this.name = name;
            this.writable = writable;
        }

        public void assign(ImageHeapObject obj) {
            assert obj.getPartition() == this;
            objects.add(obj);
        }

        public void setStartAlignment(int alignment) {
            assert this.startAlignment == -1 : "Start alignment already assigned: " + this.startAlignment;
            this.startAlignment = alignment;
        }

        public final int getStartAlignment() {
            assert startAlignment >= 0 : "Start alignment not yet assigned";
            return startAlignment;
        }

        public void setEndAlignment(int endAlignment) {
            assert this.endAlignment == -1 : "End alignment already assigned: " + this.endAlignment;
            this.endAlignment = endAlignment;
        }

        public final int getEndAlignment() {
            assert endAlignment >= 0 : "End alignment not yet assigned";
            return endAlignment;
        }

        public List<ImageHeapObject> getObjects() {
            return objects;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isWritable() {
            return writable;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
