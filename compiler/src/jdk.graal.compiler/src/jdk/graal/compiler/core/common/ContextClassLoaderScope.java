/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common;

/**
 * Utility to use in a try-with-resource statement override the
 * {@linkplain Thread#getContextClassLoader() context class loader} in a scoped way.
 */
public class ContextClassLoaderScope implements AutoCloseable {
    private final ClassLoader previous;
    private final Thread thread;

    /**
     * @param cl the class loader to use as the context class loader for the current thread within
     *            the scope of this object. If {@code null}, no change to the context class loader
     *            is made.
     */
    public ContextClassLoaderScope(ClassLoader cl) {
        thread = Thread.currentThread();
        if (cl != null) {
            previous = thread.getContextClassLoader();
            thread.setContextClassLoader(cl);
        } else {
            previous = null;
        }

    }

    @Override
    public void close() {
        if (previous != null) {
            if (thread != Thread.currentThread()) {
                throw new IllegalStateException("Cannot (re)set context class loader in a different thread");
            }
            thread.setContextClassLoader(previous);
        }
    }
}
