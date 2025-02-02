/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.nativeimage;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

import org.graalvm.nativeimage.ImageSingletons;

import jdk.graal.nativeimage.impl.LibGraalRuntimeSupport;

/**
 * LibGraal specific extensions to {@link org.graalvm.nativeimage}.
 *
 * @since 25
 */
public final class LibGraalRuntime {

    /**
     * Enqueues pending {@link Reference}s into their corresponding {@link ReferenceQueue}s and
     * executes pending cleaners.
     *
     * If automatic reference handling is enabled, this method is a no-op.
     */
    public static void processReferences() {
        ImageSingletons.lookup(LibGraalRuntimeSupport.class).processReferences();
    }

    /**
     * Notifies the runtime that the caller is at a point where the live set of objects is expected
     * to just have decreased significantly and now is a good time for a partial or full collection.
     *
     * @param suggestFullGC if a GC is performed, then suggests a full GC is done. This is true when
     *            the caller believes the heap occupancy is close to the minimal set of live objects
     *            for Graal (e.g. after a compilation).
     */
    public static void notifyLowMemoryPoint(boolean suggestFullGC) {
        ImageSingletons.lookup(LibGraalRuntimeSupport.class).notifyLowMemoryPoint(suggestFullGC);
    }

    /**
     * Gets an identifier for the current isolate that is guaranteed to be unique for the first
     * {@code 2^64 - 1} isolates in the process.
     *
     * @return a non-zero value
     */
    public static long getIsolateID() {
        return ImageSingletons.lookup(LibGraalRuntimeSupport.class).getIsolateID();
    }

    /**
     * Called to signal a fatal, non-recoverable error. This method does not return or throw an
     * exception. A typical implementation will delegate to an OS function that kills the process.
     * In the context of libgraal, it will call the HotSpot fatal crash routine that produces an
     * hs-err crash log.
     *
     * @param message a description of the error condition
     */
    public static void fatalError(String message) {
        ImageSingletons.lookup(LibGraalRuntimeSupport.class).fatalError(message);
    }

    private LibGraalRuntime() {
    }
}
