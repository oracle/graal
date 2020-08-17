/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * An object header is a reference-sized collection of bits in each object instance. The object
 * header holds a reference to a {@link DynamicHub}, which identifies the class of the instance. It
 * may also contain a couple of reserved bits that encode internal state information (e.g., for the
 * GC).
 *
 * During garbage collection, the object header may hold a forwarding reference to the new location
 * of this instance if the object has been moved by the collector.
 */
public abstract class ObjectHeader {
    @Platforms(Platform.HOSTED_ONLY.class)
    protected ObjectHeader() {
    }

    /**
     * Returns a mask where all reserved bits are set.
     */
    public abstract int getReservedBitsMask();

    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract long encodeAsImageHeapObjectHeader(ImageHeapObject obj, long hubOffsetFromHeapBase);

    public abstract Word encodeAsTLABObjectHeader(DynamicHub hub);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract DynamicHub dynamicHubFromObjectHeader(UnsignedWord header);

    public static DynamicHub readDynamicHubFromObject(Object o) {
        return KnownIntrinsics.readHub(o);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract DynamicHub readDynamicHubFromPointer(Pointer ptr);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract void initializeHeaderOfNewObject(Pointer objectPointer, DynamicHub hub, HeapKind heapKind, boolean isArray);

    public enum HeapKind {
        Unmanaged,
        ImageHeap,
    }
}
