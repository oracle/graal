/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

/**
 * Encapsulates a handle to an object in a native image heap where the object's lifetime is bound to
 * the lifetime of the {@link NativeObject} instance. At some point, after a {@link NativeObject} is
 * garbage collected, a call is made to release the handle, allowing the corresponding object in the
 * native image heap to be collected.
 */
public class NativeObject {

    private final NativeIsolate isolate;
    private final long objectHandle;
    private final NativeObjectCleaner<NativeObject> cleanup;

    /**
     * Creates a new {@link NativeObject}.
     *
     * @param isolate an isolate in which an object referenced by the handle exists.
     * @param objectHandle a handle to an object in a native image heap
     */
    public NativeObject(NativeIsolate isolate, long objectHandle) {
        this.isolate = isolate;
        this.objectHandle = objectHandle;
        this.cleanup = new NativeObjectCleanerImpl(this).register();
    }

    /**
     * Returns an isolate in which an object referenced by this handle exists.
     */
    public final NativeIsolate getIsolate() {
        return isolate;
    }

    /**
     * Returns a handle to an object in the native image heap.
     */
    public final long getHandle() {
        return objectHandle;
    }

    /**
     * Explicitly releases object in the native image heap referenced by this handle. The use of
     * this method should be exceptional. By default, the lifetime of the object in the native image
     * heap is bound to the lifetime of the {@link NativeObject} instance.
     */
    public final void release() {
        if (isolate.cleaners.remove(cleanup)) {
            NativeIsolateThread nativeIsolateThread = isolate.enter();
            try {
                cleanup.cleanUp(nativeIsolateThread.getIsolateThreadId());
            } finally {
                nativeIsolateThread.leave();
            }
        }
    }

    private static final class NativeObjectCleanerImpl extends NativeObjectCleaner<NativeObject> {

        private final long handle;

        NativeObjectCleanerImpl(NativeObject nativeObject) {
            super(nativeObject, nativeObject.getIsolate());
            this.handle = nativeObject.getHandle();
        }

        @Override
        public void cleanUp(long isolateThread) {
            isolate.getConfig().releaseNativeObject(isolateThread, handle);
        }

        @Override
        public String toString() {
            return "NativeObject 0x" + Long.toHexString(handle);
        }
    }
}
