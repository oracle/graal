/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal;

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
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    exiting = true;
                }
            });
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
    protected LibGraalObject(long handle) {
        this.handle = handle;
        isolate = LibGraalScope.current().getIsolate();
        isolate.register(this, handle);
    }

    /**
     * Gets the raw JNI handle wrapped by this object.
     *
     * @throw {@link DestroyedIsolateException} if the isolate context for the handle has destroyed.
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
