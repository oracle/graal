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
package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.image.AbstractImageHeapLayouter.AbstractImageHeapPartition;
import com.oracle.svm.core.image.ImageHeapObject;

/**
 * An unstructured image heap partition that just contains a linear sequence of image heap objects.
 */
public class LinearImageHeapPartition extends AbstractImageHeapPartition {
    Object firstObject;
    Object lastObject;

    long startOffset = -1;
    long endOffset = -1;

    LinearImageHeapPartition(String name, boolean writable) {
        super(name, writable);
    }

    void allocateObjects(LinearImageHeapAllocator allocator) {
        allocator.align(getStartAlignment());
        startOffset = allocator.getPosition();
        for (ImageHeapObject info : getObjects()) {
            allocate(info, allocator);
        }
        allocator.align(getEndAlignment());
        endOffset = allocator.getPosition();
    }

    private void allocate(ImageHeapObject info, LinearImageHeapAllocator allocator) {
        assert info.getPartition() == this;
        if (firstObject == null) {
            firstObject = info.getObject();
        }
        long offsetInPartition = allocator.allocate(info.getSize()) - startOffset;
        assert ConfigurationValues.getObjectLayout().isAligned(offsetInPartition) : "start: " + offsetInPartition + " must be aligned.";
        info.setOffsetInPartition(offsetInPartition);
        lastObject = info.getObject();
    }

    @Override
    public void assign(ImageHeapObject obj) {
        assert startOffset == -1 && endOffset == -1 : "Adding objects late is not supported";
        super.assign(obj);
    }

    @Override
    public long getStartOffset() {
        assert startOffset >= 0 : "Start offset not yet set";
        return startOffset;
    }

    public long getEndOffset() {
        assert endOffset >= 0 : "End offset not yet set";
        return endOffset;
    }

    @Override
    public long getSize() {
        return getEndOffset() - getStartOffset();
    }
}
