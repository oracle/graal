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
package com.oracle.truffle.nfi;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicReference;

final class NativeAllocation extends PhantomReference<Object> {

    private static final Queue globalQueue = new Queue();

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
                    for (;;) {
                        try {
                            NativeAllocation alloc = (NativeAllocation) refQueue.remove();
                            alloc.queue.remove(alloc);
                            alloc.destructor.destroy();
                        } catch (InterruptedException ex) {
                            // ignore
                        }
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
