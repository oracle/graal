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

class NativeAllocation extends PhantomReference<Object> {

    public static void registerNativeAllocation(Object javaObject, Destructor destructor) {
        add(new NativeAllocation(javaObject, destructor));
    }

    public static abstract class Destructor {

        protected abstract void destroy();
    }

    public static class FreeDestructor extends Destructor {

        private final long address;

        public FreeDestructor(long address) {
            this.address = address;
        }

        @Override
        protected void destroy() {
            free(address);
        }
    }

    private static final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    private final Destructor destructor;

    // cyclic double linked list with sentry element
    private static final NativeAllocation first = new NativeAllocation();
    private NativeAllocation prev;
    private NativeAllocation next;

    private NativeAllocation() {
        super(null, null);
        destructor = null;
        prev = this;
        next = this;
    }

    private NativeAllocation(Object referent, Destructor destructor) {
        super(referent, queue);
        this.destructor = destructor;
    }

    static {
        Thread gc = new Thread(new Runnable() {

            @Override
            public void run() {
                for (;;) {
                    try {
                        NativeAllocation alloc = (NativeAllocation) queue.remove();
                        remove(alloc);
                        alloc.destructor.destroy();
                    } catch (InterruptedException ex) {
                        // ignore
                    }
                }
            }
        }, "nfi-gc");
        gc.setDaemon(true);
        gc.start();
    }

    private static synchronized void add(NativeAllocation allocation) {
        assert allocation.prev == null && allocation.next == null;

        NativeAllocation second = first.next;
        allocation.prev = first;
        allocation.next = second;

        first.next = allocation;
        second.prev = allocation;
    }

    private static synchronized void remove(NativeAllocation allocation) {
        allocation.prev.next = allocation.next;
        allocation.next.prev = allocation.prev;

        allocation.next = null;
        allocation.prev = null;
    }

    private static native void free(long pointer);
}
