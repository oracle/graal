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

/**
 * An allocator of pinned objects.
 *
 * The lifecycle of a PinnedAllocator is
 * <ul>
 * <li>The PinnedAllocator is not itself pinned.</li>
 * <li>The PinnedAllocator is opened by calling open(). The separation between creation and opening
 * is to allow a PinnedAllocator to be created (e.g., during native image generation), but not
 * opened until sometime later (e.g., at runtime).</li>
 * <li>One or more calls are made to allocatePinnedObject(Class) or allocatePinnedArray(Class, int)
 * to allocate objects (or arrays) that are pinned in memory. Pinned objects will not be moved by
 * the collector, nor will they be collected.</li>
 * <li>The PinnedAllocator is closed by calling close() (possibly implicitly in a try-with-resource
 * statement). Only one PinnedAllocator may be open at a time. (This is a convenience for
 * efficiency, not really a requirement of the idea of pinned allocation.). Objects created by the
 * PinnedAllocator continue to be pinned.</li>
 * <li>The objects allocated by this PinnedAllocator are unpinned by calling release(). The objects
 * allocated by the PinnedAllocator become unpinned, and subject to collection if they are not
 * reachable.</li>
 * </ul>
 * If a client forgets to call release(), the PinnedAllocator and all the Objects it has allocated
 * will remain reachable, and the Objects it has allocated will remained pinned. <em>Don't do
 * that.</em> Calling release() on an open (that is, not closed) PinnedAllocator closes the
 * PinnedAllocator and then then releases it.
 *
 * TODO: There could be a convenience implementation of this interface that opens the
 * PinnedAllocator when it is created, and releases it when it is closed.
 */
public abstract class PinnedAllocator implements AutoCloseable {

    /** A PinnedAllocator can not allocate objects until opened. */
    public abstract PinnedAllocator open();

    /**
     * Allocate an Object of the given class. The returned object is initialized with zero, but no
     * constructors are executed.
     *
     * The class parameter must be a compile time constant.
     *
     * This method has the same behavior as {@link sun.misc.Unsafe#allocateInstance(Class)}.
     */
    /* Implementation note: intrinsified to a pinned new instance allocation node. */
    public final native <T> T newInstance(Class<T> instanceClass);

    /**
     * Allocate an array with the given element type.
     *
     * The class parameter must be a compile time constant.
     *
     * This method has the same behavior as {@link java.lang.reflect.Array#newInstance(Class, int)};
     */
    /* Implementation note: intrinsified to a pinned new array allocation node. */
    public final native Object newArray(Class<?> componentType, int length);

    /** Close this PinnedAllocator to further allocation. */
    @Override
    public abstract void close();

    /** Release the Objects allocated by this PinnedAllocator. */
    public abstract void release();

    /**
     * Signals that allocation has failed because of a lifecycle error, i.e., rather than an
     * out-of-memory error.
     */
    public static final class PinnedAllocationLifecycleError extends Error {
        private static final long serialVersionUID = -8612330353184391480L;

        public PinnedAllocationLifecycleError(String message) {
            super(message);
        }
    }
}
