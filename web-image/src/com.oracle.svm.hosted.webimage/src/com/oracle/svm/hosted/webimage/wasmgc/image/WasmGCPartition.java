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

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCHeapWriter;

/**
 * Partition implementation for the WasmGC image heap.
 * <p>
 * A partition may be a pseudo-partition (see {@link #isPseudo}), which means objects assigned to it
 * do not get serialized into a binary image heap. Instead, they're emitted as instructions to
 * construct the object.
 *
 * @see WasmGCHeapWriter
 */
public class WasmGCPartition implements ImageHeapPartition {

    private final String name;
    /**
     * Whether this is a pseudo partition that does not store objects in a buffer.
     * <p>
     * Objects in pseudo partitions are emitted as a set of field writes. Non-pseudo partitions
     * serialize objects into a binary format.
     */
    private final boolean isPseudo;
    private final List<ImageHeapObject> objects = new ArrayList<>();

    /**
     * Size of this partition in bytes.
     * <p>
     * For pseudo partitions, this is simply an approximation since objects are not serialized to a
     * byte buffer with known size.
     */
    private long size = -1;

    public WasmGCPartition(String name, boolean isPseudo) {
        this.name = name;
        this.isPseudo = isPseudo;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isPseudo() {
        return isPseudo;
    }

    public List<ImageHeapObject> getObjects() {
        return objects;
    }

    @Override
    public long getStartOffset() {
        return 0;
    }

    public void setSize(long size) {
        assert size >= 0 : "Negative sizes are not allowed: " + size;
        assert this.size == -1 : "Size is already set";
        this.size = size;
    }

    @Override
    public long getSize() {
        assert size >= 0 : "Size is not yet set";
        return size;
    }

    public void add(ImageHeapObject obj) {
        objects.add(obj);
        obj.setHeapPartition(this);
    }
}
