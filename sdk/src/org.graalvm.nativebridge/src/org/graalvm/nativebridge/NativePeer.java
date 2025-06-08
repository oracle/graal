/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

/**
 * Encapsulates a handle to an object in a native image heap where the object's lifetime is bound to
 * the lifetime of the {@link NativePeer} instance. At some point, after a {@link NativePeer} is
 * garbage collected, a call is made to release the handle, allowing the corresponding object in the
 * native image heap to be collected.
 */
public final class NativePeer extends Peer {

    private final NativeIsolate isolate;
    private final long objectHandle;
    private final CleanerImpl cleaner;

    /**
     * Creates a new {@link NativePeer}.
     *
     * @param isolate an isolate in which an object referenced by the handle exists.
     * @param objectHandle a handle to an object in a native image heap
     */
    @SuppressWarnings("this-escape")
    NativePeer(NativeIsolate isolate, long objectHandle) {
        this.isolate = Objects.requireNonNull(isolate, "Isolate must be non-null");
        this.objectHandle = objectHandle;
        this.cleaner = new CleanerImpl(this);
        this.cleaner.register();
    }

    @Override
    CleanerImpl getCleaner() {
        return cleaner;
    }

    @Override
    public NativeIsolate getIsolate() {
        return isolate;
    }

    @Override
    public long getHandle() {
        return objectHandle;
    }

    @Override
    public String toString() {
        return "NativePeer{ isolate = " + isolate + ", handle = " + objectHandle + '}';
    }

    private static final class CleanerImpl extends ForeignObjectCleaner<NativePeer> {

        private final long handle;

        CleanerImpl(NativePeer nativePeer) {
            super(nativePeer, nativePeer.isolate);
            this.handle = nativePeer.getHandle();
        }

        @Override
        public String toString() {
            return "NativePeerCleaner{ isolate = " + getIsolate() + ", handle = " + handle + '}';
        }

        @Override
        protected void cleanUp(IsolateThread isolateThread) {
            ((NativeIsolate) getIsolate()).releaseObjectHandle.applyAsLong(((NativeIsolateThread) isolateThread).getIsolateThreadId(), handle);
        }
    }
}
