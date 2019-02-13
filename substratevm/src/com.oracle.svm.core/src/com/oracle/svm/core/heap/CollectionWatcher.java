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

import com.oracle.svm.core.annotate.RestrictHeapAccess;

/**
 * The abstract base class for CollectionWatchers. Clients sub-class this provide their own
 * implementations.
 * <p>
 * Each CollectionWatcher instance may be registered at most once with
 * {@link GC#registerCollectionWatcher(CollectionWatcher)}. If there is state that could be reused
 * between CollectionWatchers (e.g., the list of loaded classes), allocate it outside of the
 * CollectionWatcher instance and share it between CollectionWatcher instances.
 *
 */
public abstract class CollectionWatcher extends AllocationFreeList.Element<CollectionWatcher> {

    /** Constructor for subclasses. */
    protected CollectionWatcher() {
        super();
    }

    /**
     * Called before a collection.
     * <p>
     * A collection has been requested, so space is not available when this method is called. This
     * method runs inside the collection VMOperation, so taking locks is not allowed.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate before a collection.")
    public abstract void beforeCollection();

    /**
     * Called after a collection.
     * <p>
     * A collection has finished, but allocation is still disallowed. This method is run inside the
     * collection VMOperation, so taking locks is not allowed.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate after a collection.")
    public abstract void afterCollection();

    /**
     * Called when the collection is over.
     * <p>
     * Allocation is allowed, but too much allocation may cause another collection. Collections may
     * be caused by VMOperations, in which case this method will be called inside that enclosing
     * VMOperation, so taking locks is not allowed. More than one thread may end up calling this
     * method for the same collection.
     * <p>
     * The default implementation is to do nothing.
     */
    public void report() {
        /* Nothing to do. */
    }
}
