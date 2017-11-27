/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.impl.ThreadingSupport;

/**
 * Functionality related to execution in threads.
 */
public final class Threading {
    /**
     * Registers a callback handler that is called by the current thread approximately at the
     * provided interval. Only one callback can be active per thread. Every thread can have its own
     * (or no) callback with a different interval. No guarantees are made about the actual interval.
     * For example, when the thread is waiting for a lock or executing native code, no callback can
     * be done. Specifying {@code null} for the callback clears the thread's current callback (in
     * this case, the values of {@code interval} and {@code unit} are ignored).
     */
    public static void registerRecurringCallback(long interval, TimeUnit unit, Runnable callback) {
        ImageSingletons.lookup(ThreadingSupport.class).registerRecurringCallback(interval, unit, callback);
    }
}
