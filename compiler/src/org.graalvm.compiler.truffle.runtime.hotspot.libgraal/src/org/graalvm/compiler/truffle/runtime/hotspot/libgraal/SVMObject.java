/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM;

/**
 * Encapsulates a handle to an object in the SVM heap where the lifetime of the latter is bound to
 * the lifetime of a {@link SVMObject} instance. At some point after a {@link SVMObject} is garbage
 * collected, a {@link HotSpotToSVM} call is made to release the handle, allowing the SVM object to
 * be collected by the SVM garbage collector.
 *
 * The SVM handles are created by
 * {@code org.graalvm.compiler.truffle.compiler.hotspot.libgraal.SVMObjectHandles}.
 */
class SVMObject {

    /**
     * Handle to the SVM object.
     */
    protected final long handle;

    /**
     * Creates a new {@link SVMObject}.
     *
     * @param handle handle to an SVM object
     */
    SVMObject(long handle) {
        cleanHandles();
        this.handle = handle;
        Cleaner cref = new Cleaner(this, handle);
        CLEANERS.add(cref);
    }

    /**
     * Processes {@link #CLEANERS_QUEUE} to release any handles whose {@link SVMObject} objects are
     * now unreachable.
     */
    private static void cleanHandles() {
        Cleaner cleaner;
        while ((cleaner = (Cleaner) CLEANERS_QUEUE.poll()) != null) {
            CLEANERS.remove(cleaner);
            cleaner.clean();
        }
    }

    /**
     * Strong references to the {@link WeakReference} objects.
     */
    private static final Set<Cleaner> CLEANERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Queue into which a {@link Cleaner} is enqueued when its {@link SVMObject} referent becomes
     * unreachable.
     */
    private static final ReferenceQueue<SVMObject> CLEANERS_QUEUE = new ReferenceQueue<>();

    private static final class Cleaner extends WeakReference<SVMObject> {
        private final long handle;

        Cleaner(SVMObject referent, long handle) {
            super(referent, CLEANERS_QUEUE);
            this.handle = handle;
        }

        void clean() {
            HotSpotToSVMCalls.releaseHandle(getIsolateThread(), handle);
        }
    }

    @Override
    public String toString() {
        return String.format("%s[0x%x]", getClass().getSimpleName(), handle);
    }
}
