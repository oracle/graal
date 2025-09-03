/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Encapsulates a handle to an object in a foreign process where the object's lifetime is bound to
 * the lifetime of the {@link ProcessPeer} instance. At some point, after a {@link ProcessPeer} is
 * garbage collected, a message is sent to release the handle, allowing the corresponding object in
 * the foreign process to be collected.
 */
public final class ProcessPeer extends Peer {

    private final ProcessIsolate isolate;
    private final long objectHandle;
    private final CleanerImpl cleaner;

    /**
     * Creates a new {@link ProcessPeer}.
     *
     * @param isolate a foreign process in which an object referenced by the handle exists.
     * @param objectHandle a handle to an object in a native image heap
     */
    @SuppressWarnings("this-escape")
    ProcessPeer(ProcessIsolate isolate, long objectHandle) {
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
    public ProcessIsolate getIsolate() {
        return isolate;
    }

    @Override
    public long getHandle() {
        return objectHandle;
    }

    @Override
    public String toString() {
        return "ProcessPeer{ isolate = " + isolate + ", handle = " + objectHandle + '}';
    }

    private static final class CleanerImpl extends ForeignObjectCleaner<ProcessPeer> {

        private final long handle;

        CleanerImpl(ProcessPeer processPeer) {
            super(processPeer, processPeer.isolate);
            this.handle = processPeer.getHandle();
        }

        @Override
        public String toString() {
            return "ProcessPeerCleaner{ isolate = " + getIsolate() + ", handle = " + handle + '}';
        }

        @Override
        protected void cleanUp(IsolateThread isolateThread) {
            ((ProcessIsolate) getIsolate()).releaseObjectHandle.applyAsLong((ProcessIsolateThread) isolateThread, handle);
        }
    }
}
