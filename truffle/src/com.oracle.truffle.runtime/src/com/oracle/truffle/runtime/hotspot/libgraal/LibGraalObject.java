/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.hotspot.libgraal;

/**
 * Encapsulates a handle to an object in a libgraal isolate where the object's lifetime is bound to
 * the lifetime of the {@link LibGraalObject} instance. At some point after a {@link LibGraalObject}
 * is garbage collected, a call is made to release the handle, allowing the libgraal object to be
 * collected.
 */
public class LibGraalObject {

    private static volatile boolean exiting;

    static {
        if (LibGraal.isAvailable()) {
            LibGraal.registerNativeMethods(LibGraalObject.class);
            try {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        exiting = true;
                    }
                });
            } catch (IllegalStateException e) {
                // shutdown already in progress
                // catching the exception is the only way to detect this.
            }
        }
    }

    /**
     * Handle to an object in {@link #isolate}.
     */
    private final long handle;

    /**
     * The libgraal isolate containing {@link #handle}.
     */
    private final LibGraalIsolate isolate;

    /**
     * Creates a new {@link LibGraalObject}.
     *
     * @param handle handle to an object in a libgraal isolate
     */
    @SuppressWarnings("this-escape")
    protected LibGraalObject(long handle) {
        this.handle = handle;
        isolate = LibGraalScope.current().getIsolate();
        isolate.register(this, handle);
    }

    /**
     * Gets the raw JNI handle wrapped by this object.
     *
     * @throws {@link DestroyedIsolateException} if the isolate context for the handle has
     *             destroyed.
     */
    public long getHandle() {
        if (!isolate.isValid()) {
            // During VM exit the Truffle compiler threads are not stopped before the
            // LibGraalIsolate is invalidated by libgraal shutdown routine. There may still be
            // active compilations accessing LibGraal objects with invalidated LibGraalIsolate. In
            // this case we throw DestroyedIsolateException with exitVM set to true to allow
            // LibGraalTruffleRuntime to recognize this situation and ignore the exception.
            throw new DestroyedIsolateException(toString(), exiting);
        }
        return handle;
    }

    /**
     * Releases {@code handle} in the isolate denoted by {@code isolateThreadId}.
     *
     * @return {@code false} if {@code} is not a valid handle in the isolate
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.releaseHandle
    static native boolean releaseHandle(long isolateThreadId, long handle);

    @Override
    public String toString() {
        String name = getClass().getSimpleName();
        Class<?> outer = getClass().getDeclaringClass();
        while (outer != null) {
            name = outer.getSimpleName() + '.' + name;
            outer = outer.getDeclaringClass();
        }
        return String.format("%s[%d]", name, handle);
    }
}
