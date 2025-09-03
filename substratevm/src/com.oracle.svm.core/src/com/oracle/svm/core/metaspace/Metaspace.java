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
package com.oracle.svm.core.metaspace;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * The metaspace is used for VM-internal objects (such as runtime-allocated {@link DynamicHub}s)
 * that have a different lifecycle than normal Java heap objects. Objects in the metaspace are
 * neither collected nor moved by the GC. Objects in the metaspace also do not count towards the
 * Java heap size.
 */
public interface Metaspace {
    @Fold
    static Metaspace singleton() {
        return ImageSingletons.lookup(Metaspace.class);
    }

    @Fold
    static boolean isSupported() {
        return !(singleton() instanceof NoMetaspace);
    }

    /**
     * Returns {@code true} if the {@link Object} reference points to a location within the address
     * range of the metaspace. Usually faster than {@link #isInAllocatedMemory}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean isInAddressSpace(Object obj);

    /**
     * Returns {@code true} if the {@link Pointer} points to a location within the address range of
     * the metaspace. Usually faster than {@link #isInAllocatedMemory}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean isInAddressSpace(Pointer obj);

    /**
     * Returns {@code true} if the {@link Object} reference points to allocated memory that is
     * located in the address range of the metaspace. Usually slower than {@link #isInAddressSpace}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean isInAllocatedMemory(Object obj);

    /**
     * Returns {@code true} if the {@link Pointer} points to allocated memory that is located in the
     * address range of the metaspace. Usually slower than {@link #isInAddressSpace}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean isInAllocatedMemory(Pointer ptr);

    /**
     * Allocates a {@link DynamicHub} and zeroes all its fields. Typically, there should be no need
     * to call this method directly and {@link DynamicHub#allocate} should be used instead.
     */
    DynamicHub allocateDynamicHub(int numVTableEntries);

    /** Allocates a byte array. */
    byte[] allocateByteArray(int length);

    /** Walks all metaspace objects. May only be called at a safepoint. */
    void walkObjects(ObjectVisitor visitor);
}
