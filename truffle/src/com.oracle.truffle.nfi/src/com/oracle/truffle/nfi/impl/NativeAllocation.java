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
