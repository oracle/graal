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

import com.oracle.svm.core.image.AbstractImageHeapLayouter.AbstractImageHeapPartition;
import com.oracle.svm.core.image.ImageHeapObject;

/**
 * An unstructured image heap partition that just contains a linear sequence of image heap objects.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class LinearImageHeapPartition extends AbstractImageHeapPartition {
    long size;
    Object firstObject;
    Object lastObject;

    LinearImageHeapPartition(String name, boolean writable) {
        super(name, writable);
        this.size = 0L;
        this.firstObject = null;
        this.lastObject = null;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void allocate(ImageHeapObject info) {
        assert info.getPartition() == this;
        if (firstObject == null) {
            firstObject = info.getObject();
        }
        lastObject = info.getObject();

        info.setOffsetInPartition(size);
        size += info.getSize();
    }

    @Override
    public void addPadding(long padding) {
        assert padding >= 0;
        this.size += padding;
    }
}
