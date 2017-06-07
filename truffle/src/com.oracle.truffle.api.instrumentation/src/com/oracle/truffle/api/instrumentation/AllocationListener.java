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
package com.oracle.truffle.api.instrumentation;

/**
 * Listener to be notified about guest language value allocations. Calls to this listener are
 * initiated by {@link AllocationReporter}.
 * <p>
 * Use
 * {@link Instrumenter#attachAllocationListener(com.oracle.truffle.api.instrumentation.AllocationEventFilter, com.oracle.truffle.api.instrumentation.AllocationListener)}
 * to register an implementation of this listener. Use {@link EventBinding#dispose()} to unregister.
 * <p>
 * The listener gets called {@link #onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)
 * before} the actual allocation and right
 * {@link #onReturnValue(com.oracle.truffle.api.instrumentation.AllocationEvent) after} it. The
 * calls to these methods are always in pairs, unless the programs crashes in between. Nested
 * allocations are supported, several calls to <code>onEnter</code> prior every sub-value allocation
 * can be followed by the appropriate number of <code>onReturnValue</code> calls after the
 * sub-values are allocated, in the opposite order.
 *
 * @since 0.27
 * @see Instrumenter#attachAllocationListener(com.oracle.truffle.api.instrumentation.AllocationEventFilter,
 *      com.oracle.truffle.api.instrumentation.AllocationListener)
 * @see AllocationReporter
 */
public interface AllocationListener {

    /**
     * Notifies about an intent to allocate or re-allocate a guest language value. This method is
     * called prior to the actual allocation and is followed by a call to
     * {@link #onReturnValue(com.oracle.truffle.api.instrumentation.AllocationEvent)} after the
     * successful allocation.
     *
     * @param event the event describing the intended allocation
     * @since 0.27
     */
    void onEnter(AllocationEvent event);

    /**
     * Notifies about an allocated guest language value. This method is called after a preceding
     * {@link #onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)} call and right after
     * the allocation is performed. When
     * {@link #onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent) onEnter} provided a
     * non-<code>null</code> value in the event, it was re-allocated and the same value instance is
     * in this event.
     *
     * @param event the event describing the finished allocation
     * @since 0.27
     */
    void onReturnValue(AllocationEvent event);

}
