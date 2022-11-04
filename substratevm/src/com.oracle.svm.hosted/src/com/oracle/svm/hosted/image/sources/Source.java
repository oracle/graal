/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.hosted.image.sources;

import java.nio.file.Path;

/**
 * A proxy for a potentially cached file, recording progress of any attempted caching operation and
 * serving as a synchronization target to co-ordinate attempts by multiple threads to cache the same
 * source.
 */
class Source {
    /**
     * The path to the cached source file in the sources cache.
     */
    private Path path;
    /**
     * The cached status of source file identified by path, initialized to UNKNOWN at creation.
     * Transitions to subsequent states only occur when synchronized on the owning instance.
     * Legitimate transitions are as follows:
     * <ul>
     * <li><code>UNKNOWN -> CACHING</code></li>
     * <li><code>CACHING -> MISSING</code></li>
     * <li><code>CACHING -> CACHED</code></li>
     * </ul>
     * <p>
     * The lookup thread which observes state <code>UNKNOWN</code> transitions to
     * <code>CACHING</code> then searches for the source and, if found, copies it into the cache.
     * Lookup threads which encounter state <code>CACHING</code> wait on a transition to a later
     * state. The first lookup thread records the outcome of the search by transitioning to
     * <code>CACHED</code> or <code>MISSING</code> and then notifies all waiting threads.
     * <p>
     * As an optimization, the lookup thread may transition to an intermediate state,
     * <code>LOCATED</code> after a source is located but before it is copied into the source cache.
     * It will notify all waiting threads after this transition, allowing them to return early.
     */
    private CacheStatus status;

    Source(Path path) {
        this.path = path;
        this.status = CacheStatus.UNKNOWN;
    }

    /*
     * n.b. the following accessors are deliberately not synchronized. In some cases we don't care
     * about racy reads. In others we do. Use with due care.
     */
    public boolean isUnknown() {
        return status == CacheStatus.UNKNOWN;
    }

    public boolean isCaching() {
        return status == CacheStatus.CACHING;
    }

    public boolean isMissing() {
        return status == CacheStatus.MISSING;
    }

    public boolean isLocated() {
        return status == CacheStatus.LOCATED;
    }

    public boolean isCached() {
        return status == CacheStatus.CACHED;
    }

    public boolean isResolved() {
        return isMissing() || isLocated() || isCached();
    }

    public Path getPath() {
        return (isMissing() ? null : path);
    }

    public synchronized void updateStatus(CacheStatus status) {
        /* state changes must be synchronized and must wake up *all* waiting threads */
        this.status = status;
        notifyAll();
    }

    /**
     * Check the status of a source file to determine whether the caller should try to cache it or
     * not. For any given source only one caller should be requested to attempt to cache the file.
     * The method is synchronized in order to avoid races to cache the source. If a cache attempt is
     * in progress at the point of call the caller will wait for that attempt to be resolved before
     * returning. A call is allowed to return at the point where the source is located but before it
     * is actually copied into the cache.
     *
     * The status transitions are relatively simple. A new source i.e. one with status UNKNOWN is
     * transitioned to CACHING before returning true. In this case the caller should attempt to
     * locate and cache the source file. That is the only transition performed by this method.
     *
     * The caller that attempts the cache operation performs all other transitions. It upgrades the
     * status to LOCATED when it finds a matching source and then to CACHED when this file is copied
     * into the cache. It can also upgrade the status from CACHING to MISSING if the file cannot be
     * found or downgrade it from LOCATED to MISSING if an attempt to copy a located source into the
     * cache fails.
     *
     * @return True if the calling thread is expected to try to cache the source otherwise false.
     */
    synchronized boolean shouldCache() {
        if (isUnknown()) {
            // first caller to reach here can promote status to CACHING
            // and return to try to popuate the cache
            updateStatus(CacheStatus.CACHING);
            return true;
        } else {
            // latecomers wait for the caching operation outcome to be posted
            while (isCaching()) {
                try {
                    wait();
                } catch (InterruptedException ie) {
                }
            }
            assert isResolved();
            return false;
        }
    }
}
