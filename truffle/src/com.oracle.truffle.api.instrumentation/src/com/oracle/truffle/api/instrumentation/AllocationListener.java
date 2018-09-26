/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
