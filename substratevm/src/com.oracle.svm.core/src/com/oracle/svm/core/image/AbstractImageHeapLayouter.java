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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;

@Platforms(value = Platform.HOSTED_ONLY.class)
public abstract class AbstractImageHeapLayouter<T extends ImageHeapPartition> implements ImageHeapLayouter {
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
    private static final int PARTITION_COUNT = 5;

    private final T[] partitions;

    @Override
    public T[] getPartitions() {
        return partitions;
    }

    public AbstractImageHeapLayouter() {
        this.partitions = createPartitionsArray(PARTITION_COUNT);
        this.partitions[READ_ONLY_PRIMITIVE] = createPartition("readOnlyPrimitive", false, false);
        this.partitions[READ_ONLY_REFERENCE] = createPartition("readOnlyReference", true, false);
        this.partitions[READ_ONLY_RELOCATABLE] = createPartition("readOnlyRelocatable", true, false);
        this.partitions[WRITABLE_PRIMITIVE] = createPartition("writablePrimitive", false, true);
        this.partitions[WRITABLE_REFERENCE] = createPartition("writableReference", true, true);
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable) {
        ImageHeapPartition partition = choosePartition(immutable, references, relocatable);
        info.setHeapPartition(partition);
    }

    @Override
    public ImageHeapLayout layoutPartitionsAsContiguousHeap(String sectionName, int pageSize) {
        VMError.guarantee(SubstrateOptions.SpawnIsolates.getValue());

        // the read only relocatable values must be located in their own page(s)
        getReadOnlyPrimitive().addPadding(computePadding(getReadOnlyPrimitive().getSize() + getReadOnlyReference().getSize(), pageSize));
        getReadOnlyRelocatable().addPadding(computePadding(getReadOnlyRelocatable().getSize(), pageSize));

        getReadOnlyPrimitive().setSection(sectionName, 0);
        getReadOnlyReference().setSection(sectionName, getReadOnlyPrimitive().getOffsetInSection() + getReadOnlyPrimitive().getSize());
        getReadOnlyRelocatable().setSection(sectionName, getReadOnlyReference().getOffsetInSection() + getReadOnlyReference().getSize());
        getWritablePrimitive().setSection(sectionName, getReadOnlyRelocatable().getOffsetInSection() + getReadOnlyRelocatable().getSize());
        getWritableReference().setSection(sectionName, getWritablePrimitive().getOffsetInSection() + getWritablePrimitive().getSize());

        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        assert getReadOnlyPrimitive().getOffsetInSection() == 0;
        assert getReadOnlyReference().getOffsetInSection() % objectAlignment == 0;
        assert getReadOnlyRelocatable().getOffsetInSection() % pageSize == 0;
        assert getWritablePrimitive().getOffsetInSection() % pageSize == 0;
        assert getWritableReference().getOffsetInSection() % objectAlignment == 0;

        return createLayout();
    }

    @Override
    public ImageHeapLayout layoutPartitionsAsSeparatedHeap(String readOnlySectionName, long readOnlySectionOffset, String writableSectionName, long writableSectionOffset) {
        VMError.guarantee(!SubstrateOptions.SpawnIsolates.getValue());

        getReadOnlyPrimitive().setSection(readOnlySectionName, readOnlySectionOffset);
        getReadOnlyReference().setSection(readOnlySectionName, getReadOnlyPrimitive().getOffsetInSection() + getReadOnlyPrimitive().getSize());
        getReadOnlyRelocatable().setSection(readOnlySectionName, getReadOnlyReference().getOffsetInSection() + getReadOnlyReference().getSize());

        getWritablePrimitive().setSection(writableSectionName, writableSectionOffset);
        getWritableReference().setSection(writableSectionName, getWritablePrimitive().getOffsetInSection() + getWritablePrimitive().getSize());

        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        assert getReadOnlyPrimitive().getOffsetInSection() % objectAlignment == 0;
        assert getReadOnlyReference().getOffsetInSection() % objectAlignment == 0;
        assert getReadOnlyRelocatable().getOffsetInSection() % objectAlignment == 0;

        assert getWritablePrimitive().getOffsetInSection() % objectAlignment == 0;
        assert getWritableReference().getOffsetInSection() % objectAlignment == 0;

        return createLayout();
    }

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

    private static long computePadding(long offset, int alignment) {
        long remainder = offset % alignment;
        return remainder == 0 ? 0 : alignment - remainder;
    }

    private ImageHeapPartition choosePartition(boolean immutable, boolean references, boolean relocatable) {
        if (SubstrateOptions.UseOnlyWritableBootImageHeap.getValue()) {
            if (!useHeapBase()) {
                return getWritableReference();
            }
        }

        if (immutable) {
            if (relocatable) {
                return getReadOnlyRelocatable();
            }
            return references ? getReadOnlyReference() : getReadOnlyPrimitive();
        } else {
            return references ? getWritableReference() : getWritablePrimitive();
        }
    }

    private ImageHeapLayout createLayout() {
        long readOnlySectionSize = getReadOnlyPrimitive().getSize() + getReadOnlyReference().getSize() + getReadOnlyRelocatable().getSize();
        long writableSectionSize = getWritablePrimitive().getSize() + getWritableReference().getSize();
        long readOnlyRelocatableOffsetInSection = getReadOnlyRelocatable().getOffsetInSection();
        long readOnlyRelocatableSize = getReadOnlyRelocatable().getSize();
        return new ImageHeapLayout(getReadOnlyPrimitive().getOffsetInSection(), readOnlySectionSize, getWritablePrimitive().getOffsetInSection(), writableSectionSize,
                        readOnlyRelocatableOffsetInSection, readOnlyRelocatableSize);
    }

    @Fold
    protected static boolean useHeapBase() {
        return SubstrateOptions.SpawnIsolates.getValue() && ImageSingletons.lookup(CompressEncoding.class).hasBase();
    }

    protected abstract T[] createPartitionsArray(int count);

    protected abstract T createPartition(String name, boolean containsReferences, boolean writable);

    public static class ImageHeapLayout {
        private final long readOnlyOffsetInSection;
        private final long readOnlySize;

        private final long writableOffsetInSection;
        private final long writableSize;

        private final long readOnlyRelocatableOffsetInSection;
        private final long readOnlyRelocatableSize;

        public ImageHeapLayout(long readOnlyOffsetInSection, long readOnlySize, long writableOffsetInSection, long writableSize, long readOnlyRelocatableOffsetInSection,
                        long readOnlyRelocatableSize) {
            this.readOnlyOffsetInSection = readOnlyOffsetInSection;
            this.readOnlySize = readOnlySize;
            this.writableOffsetInSection = writableOffsetInSection;
            this.writableSize = writableSize;
            this.readOnlyRelocatableOffsetInSection = readOnlyRelocatableOffsetInSection;
            this.readOnlyRelocatableSize = readOnlyRelocatableSize;
        }

        public long getReadOnlyOffsetInSection() {
            return readOnlyOffsetInSection;
        }

        public long getReadOnlySize() {
            return readOnlySize;
        }

        public long getWritableOffsetInSection() {
            return writableOffsetInSection;
        }

        public long getWritableSize() {
            return writableSize;
        }

        public long getReadOnlyRelocatableOffsetInSection() {
            return readOnlyRelocatableOffsetInSection;
        }

        public long getReadOnlyRelocatableSize() {
            return readOnlyRelocatableSize;
        }

        public boolean isReadOnlyRelocatable(int offset) {
            return offset >= readOnlyRelocatableOffsetInSection && offset < readOnlyRelocatableOffsetInSection + readOnlyRelocatableSize;
        }

        public long getImageHeapSize() {
            return getReadOnlySize() + getWritableSize();
        }
    }

    /**
     * The native image heap comes in partitions. Each partition holds objects with different
     * properties (read-only/writable, primitives/objects).
     */
    @Platforms(value = Platform.HOSTED_ONLY.class)
    public abstract static class AbstractImageHeapPartition implements ImageHeapPartition {
        private static final long INVALID_SECTION_OFFSET = -1L;

        private final String name;
        private final boolean writable;

        private String sectionName;
        private long offsetInSection;

        public AbstractImageHeapPartition(String name, boolean writable) {
            this.name = name;
            this.writable = writable;
            this.sectionName = null;
            this.offsetInSection = INVALID_SECTION_OFFSET;
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
        public void setSection(String sectionName, long offsetInSection) {
            this.sectionName = sectionName;
            this.offsetInSection = offsetInSection;
        }

        @Override
        public String getSectionName() {
            assert sectionName != null : "Partition " + name + " should have a section name by now.";
            return sectionName;
        }

        @Override
        public long getOffsetInSection() {
            assert offsetInSection != INVALID_SECTION_OFFSET : "Partition " + name + " should have an offset by now.";
            return offsetInSection;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
