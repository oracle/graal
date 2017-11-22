/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

/**
 * Listener to be notified about changes of threads in guest language application.
 * <p>
 * Use
 * {@link DebuggerSession#setThreadsListener(com.oracle.truffle.api.debug.DebugThreadsListener, boolean)}
 * to register an implementation of this listener.
 * <p>
 * The listener gets called when a thread is initialized for use in a {@link DebugContext context}
 * or thread-related resources are disposed in a context. The notification calls do not say anything
 * about the actual life-time of the thread as such, which may live before the initialization and
 * may continue to live after the disposal.
 *
 * @see DebuggerSession#setThreadsListener(com.oracle.truffle.api.debug.DebugThreadsListener,
 *      boolean)
 * @since 0.30
 */
public interface DebugThreadsListener {

    /**
     * Notifies about initialization of a thread to be used for a guest language execution in a
     * {@link DebugContext}.
     *
     * @param context the context the thread is initialized in
     * @param thread the initialized thread
     * @since 0.30
     */
    void threadInitialized(DebugContext context, Thread thread);

    /**
     * Notifies about disposal of thread-related resources that were used for a guest language
     * execution in a {@link DebugContext}.
     *
     * @param context the context the thread is disposed from
     * @param thread the thread
     * @since 0.30
     */
    void threadDisposed(DebugContext context, Thread thread);

}
