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

public class ImageHeapLayoutInfo {
    private final long writableOffsetInSection;
    private final long writableSize;

    private final long readOnlyRelocatableOffsetInSection;
    private final long readOnlyRelocatableSize;

    private final long imageHeapSize;

    public ImageHeapLayoutInfo(long writableOffsetInSection, long writableSize, long readOnlyRelocatableOffsetInSection, long readOnlyRelocatableSize, long imageHeapSize) {
        this.writableOffsetInSection = writableOffsetInSection;
        this.writableSize = writableSize;
        this.readOnlyRelocatableOffsetInSection = readOnlyRelocatableOffsetInSection;
        this.readOnlyRelocatableSize = readOnlyRelocatableSize;
        this.imageHeapSize = imageHeapSize;
    }

    public long getWritableOffset() {
        return writableOffsetInSection;
    }

    public long getWritableSize() {
        return writableSize;
    }

    public long getReadOnlyRelocatableOffset() {
        return readOnlyRelocatableOffsetInSection;
    }

    public long getReadOnlyRelocatableSize() {
        return readOnlyRelocatableSize;
    }

    public boolean isReadOnlyRelocatable(int offset) {
        return offset >= readOnlyRelocatableOffsetInSection && offset < readOnlyRelocatableOffsetInSection + readOnlyRelocatableSize;
    }

    public long getImageHeapSize() {
        return imageHeapSize;
    }
}
