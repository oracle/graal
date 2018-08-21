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
package com.oracle.truffle.nfi.impl;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to manage {@link Destructor destructors} for native allocations.
 *
 * @see NativeAllocation.Queue#registerNativeAllocation
 */
public final class NativeAllocation extends PhantomReference<Object> {

    private static final Queue globalQueue = new Queue();

    /**
     * Returns a global default {@link Queue}. Most users of this class usually want to use this
     * global queue.
     * <p>
     * Note however that the {@link Destructor} object will be kept alive by the {@link Queue} until
     * after the {@code javaObject} dies, so care must be taken with potential references from the
     * {@link Destructor} back to the {@code javaObject}. Such a reference cycle will keep the
     * {@code javaObject} alive until the {@link Queue} dies. In that case, a local {@link Queue}
     * must be used to prevent memory leaks.
     */
    public static Queue getGlobalQueue() {
        return globalQueue;
    }

    public abstract static class Destructor {

        protected abstract void destroy();
    }

    public static class FreeDestructor extends Destructor {

        private final long address;

        FreeDestructor(long address) {
            this.address = address;
        }

        @Override
        protected void destroy() {
            free(address);
        }
    }

    public static final class Queue {

        // cyclic double linked list with sentry element
        private final NativeAllocation first = new NativeAllocation(this);

        /**
         * Register a native {@link Destructor} that should be called when some managed object dies.
         * The {@link Destructor#destroy} method will be called after the {@code javaObject} becomes
         * unreachable from GC roots.
         * <p>
         * This will only happen if the {@link Queue} is still alive when the {@code javaObject}
         * dies. If the {@link Queue} dies before or at the same time as the {@code javaObject}, the
         * {@link Destructor#destroy} method might not be called.
         *
         * @see NativeAllocation#getGlobalQueue
         */
        public void registerNativeAllocation(Object javaObject, Destructor destructor) {
            add(new NativeAllocation(javaObject, destructor, this));
        }

        private synchronized void add(NativeAllocation allocation) {
            assert allocation.prev == null && allocation.next == null;

            NativeAllocation second = first.next;
            allocation.prev = first;
            allocation.next = second;

            first.next = allocation;
            second.prev = allocation;
        }

        private synchronized void remove(NativeAllocation allocation) {
            assert allocation.queue == this;
            allocation.prev.next = allocation.next;
            allocation.next.prev = allocation.prev;

            allocation.next = null;
            allocation.prev = null;
        }
    }

    private static final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();

    private final Destructor destructor;

    private NativeAllocation prev;
    private NativeAllocation next;

    private final Queue queue;

    private NativeAllocation(Queue queue) {
        super(null, null);
        this.destructor = null;
        this.prev = this;
        this.next = this;
        this.queue = queue;
    }

    private NativeAllocation(Object referent, Destructor destructor, Queue queue) {
        super(referent, refQueue);
        this.destructor = destructor;
        this.queue = queue;
    }

    private static final AtomicReference<Thread> gcThread = new AtomicReference<>(null);

    static void ensureGCThreadRunning() {
        Thread thread = gcThread.get();
        if (thread == null) {
            thread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        for (;;) {
                            NativeAllocation alloc = (NativeAllocation) refQueue.remove();
                            alloc.queue.remove(alloc);
                            alloc.destructor.destroy();
                        }
                    } catch (InterruptedException ex) {
                        /* Happens on isolate tear down. We simply finish running this thread. */
                    }
                }
            }, "nfi-gc");
            if (gcThread.compareAndSet(null, thread)) {
                thread.setDaemon(true);
                thread.start();
            } else {
                Thread other = gcThread.get();
                // nothing to do, another thread already started the GC thread
                assert other != null && other != thread;
            }
        }
    }

    private static native void free(long pointer);
}
