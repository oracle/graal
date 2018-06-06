/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.MemoryMXBean;
import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public abstract class Heap {

    /**
     * Retuns the singleton {@link Heap} implementation that is created during image generation.
     */
    @Fold
    // Lookup is @Fold, so inlining is safe.
    public static Heap getHeap() {
        return ImageSingletons.lookup(Heap.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected Heap() {
    }

    public abstract void suspendAllocation();

    public abstract void resumeAllocation();

    public abstract void disableAllocation(IsolateThread vmThread);

    /** Allocation is disallowed if ... */
    public abstract boolean isAllocationDisallowed();

    /** Create a PinnedAllocator. */
    public abstract PinnedAllocator createPinnedAllocator();

    /*
     * Collection methods.
     */

    public abstract GC getGC();

    /*
     * Other interface methods for the rest of the virtual machine.
     */

    /** Walk all the Objects in the Heap, passing each to the visitor. */
    public abstract void walkObjects(ObjectVisitor visitor);

    /** Return a list of all the classes in the heap. */
    public abstract List<Class<?>> getClassList();

    /**
     * Get the ObjectHeader implementation that this Heap uses.
     *
     * TODO: This is used during native image generation to put appropriate headers on Objects in
     * the native image heap. Is there any reason to expose the whole ObjectHeader interface, since
     * only setBootImageOnLong(0L) is used then, to get the native image object header bits?
     *
     * TODO: Would an "Unsigned getBootImageObjectHeaderBits()" method be sufficient?
     */
    public abstract ObjectHeader getObjectHeader();

    /** Get the MemoryMXBean for this heap. */
    public abstract MemoryMXBean getMemoryMXBean();
}
