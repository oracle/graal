/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.libgraal;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

import org.graalvm.nativeimage.ImageSingletons;

import org.graalvm.nativeimage.libgraal.impl.LibGraalRuntimeSupport;

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
     */
    public static long getIsolateID() {
        return ImageSingletons.lookup(LibGraalRuntimeSupport.class).getIsolateID();
    }

    /**
     * Called to signal a fatal, non-recoverable error. This method does not return or throw an
     * exception but calls the HotSpot fatal crash routine that produces an hs-err crash log.
     *
     * @param message a description of the error condition
     */
    public static void fatalError(String message) {
        ImageSingletons.lookup(LibGraalRuntimeSupport.class).fatalError(message);
    }

    private LibGraalRuntime() {
    }
}
