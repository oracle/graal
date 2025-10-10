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
package com.oracle.svm.hosted.gc.shenandoah;

import java.util.ArrayList;

import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;

public class ShenandoahImageHeapPartition implements ImageHeapPartition {
    private final String name;
    private final ArrayList<ImageHeapObject> objects = new ArrayList<>();
    private final boolean writable;

    private long size = -1L;

    ShenandoahImageHeapPartition(String name, boolean writable) {
        this.name = name;
        this.writable = writable;
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
    public long getStartOffset() {
        /* All image heap objects have absolute offsets. */
        return 0;
    }

    public void add(ImageHeapObject info) {
        objects.add(info);
        info.setHeapPartition(this);
    }

    public ArrayList<ImageHeapObject> getObjects() {
        return objects;
    }

    @Override
    public long getSize() {
        assert size >= 0 : size;
        return size;
    }

    public void setSize(long startOffset, long endOffset) {
        assert startOffset >= 0;
        assert endOffset >= startOffset;
        this.size = endOffset - startOffset;
    }
}
