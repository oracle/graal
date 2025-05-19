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
package org.graalvm.nativebridge;

import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import java.util.Objects;

import static org.graalvm.jniutils.JNIUtil.DeleteGlobalRef;
import static org.graalvm.jniutils.JNIUtil.NewGlobalRef;

/**
 * Encapsulates a handle to an object in a HotSpot heap referenced from native image isolate. The
 * referenced object's lifetime is bound to the lifetime of the {@link HSPeer} instance. At some
 * point, after a {@link HSPeer} is garbage collected, a call is made to release the handle,
 * allowing the corresponding object in the HotSpot heap to be collected.
 *
 * @since 25.0
 */
public final class HSPeer extends Peer {

    private final HSIsolate isolate;

    /**
     * JNI handle to the HotSpot object.
     */
    private final JObject handle;

    /**
     * Cleaner to release JNI Global Reference.
     */
    private final CleanerImpl cleaner;

    HSPeer(JNIEnv env, HSIsolate isolate, JObject handle) {
        this.isolate = Objects.requireNonNull(isolate, "Isolate must be non-null");
        this.handle = handle.isNonNull() ? NewGlobalRef(env, handle, HSPeer.class.getSimpleName()) : handle;
        this.cleaner = new CleanerImpl(this);
        this.cleaner.register();
    }

    @Override
    CleanerImpl getCleaner() {
        return cleaner;
    }

    @Override
    public HSIsolate getIsolate() {
        return isolate;
    }

    @Override
    public long getHandle() {
        return handle.rawValue();
    }

    public JObject getJObject() {
        return handle;
    }

    @Override
    public String toString() {
        return "HSPeer{ handle = 0x" + Long.toHexString(handle.rawValue()) + '}';
    }

    private static final class CleanerImpl extends ForeignObjectCleaner<HSPeer> {

        private final JObject handle;

        CleanerImpl(HSPeer hsPeer) {
            super(hsPeer, hsPeer.isolate);
            this.handle = hsPeer.getJObject();
        }

        @Override
        public String toString() {
            return "HSPeerCleaner{ isolate = " + getIsolate() + ", handle = 0x" + Long.toHexString(handle.rawValue()) + '}';
        }

        @Override
        protected void cleanUp(IsolateThread isolateThread) {
            if (handle.isNonNull()) {
                DeleteGlobalRef(((HSIsolateThread) isolateThread).getJNIEnv(), handle);
            }
        }
    }
}
