/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.BuildPhaseProvider.AfterHeapLayout;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.UnknownPrimitiveField;

import jdk.graal.compiler.debug.Assertions;

/** Layout offsets and sizes. All offsets are relative to the heap base. */
public class ImageHeapLayoutInfo {
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long startOffset;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long endOffset;

    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long writableOffset;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long writableSize;

    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long readOnlyRelocatableOffset;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long readOnlyRelocatableSize;

    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long writablePatchedOffset;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) private final long writablePatchedSize;

    @SuppressWarnings("this-escape")
    public ImageHeapLayoutInfo(long startOffset, long endOffset, long writableOffset, long writableSize, long readOnlyRelocatableOffset, long readOnlyRelocatableSize, long writablePatchedOffset,
                    long writablePatchedSize) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.writableOffset = writableOffset;
        this.writableSize = writableSize;
        this.readOnlyRelocatableOffset = readOnlyRelocatableOffset;
        this.readOnlyRelocatableSize = readOnlyRelocatableSize;
        this.writablePatchedOffset = writablePatchedOffset;
        this.writablePatchedSize = writablePatchedSize;

        assert verifyAlignment();
        assert readOnlyRelocatableOffset + readOnlyRelocatableSize <= writablePatchedOffset : Assertions.errorMessage("the writable patched section is placed after the relocations",
                        readOnlyRelocatableOffset, readOnlyRelocatableSize, writablePatchedOffset);
    }

    protected boolean verifyAlignment() {
        assert startOffset % Heap.getHeap().getImageHeapAlignment() == 0;
        assert endOffset % SubstrateOptions.getPageSize() == 0;
        assert endOffset >= startOffset;
        return true;
    }

    /**
     * Returns the image heap start offset. This value is a multiple of
     * {@link Heap#getImageHeapAlignment}.
     */
    public long getStartOffset() {
        return startOffset;
    }

    /** Returns the image heap end offset. This value is a multiple of the build-time page size. */
    public long getEndOffset() {
        return endOffset;
    }

    /** Returns the image heap size. This value is a multiple of the build-time page size. */
    public long getSize() {
        return endOffset - startOffset;
    }

    public long getWritableOffset() {
        return writableOffset;
    }

    public long getWritableSize() {
        return writableSize;
    }

    public long getReadOnlyRelocatableOffset() {
        return readOnlyRelocatableOffset;
    }

    public long getReadOnlyRelocatableSize() {
        return readOnlyRelocatableSize;
    }

    public boolean isReadOnlyRelocatable(long offset) {
        return offset >= readOnlyRelocatableOffset && offset < readOnlyRelocatableOffset + readOnlyRelocatableSize;
    }

    public long getWritablePatchedOffset() {
        return writablePatchedOffset;
    }

    public long getWritablePatchedSize() {
        return writablePatchedSize;
    }
}
