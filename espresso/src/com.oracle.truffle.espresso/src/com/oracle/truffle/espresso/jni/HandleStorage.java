/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jni;

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;

public abstract class HandleStorage<T, REF> {
    protected static final int DEFAULT_INITIAL_CAPACITY = 16;
    private volatile int nextHandle = 1;
    private REF[] handles;
    private HandlesStack stack;
    private final boolean implicitFree;

    @SuppressWarnings("unchecked")
    HandleStorage(boolean implicitFree) {
        this.implicitFree = implicitFree;
        handles = (REF[]) new Object[DEFAULT_INITIAL_CAPACITY];
        stack = new HandlesStack();
    }

    @CompilerDirectives.TruffleBoundary
    public synchronized long handlify(T object) {
        Objects.requireNonNull(object);
        int handle = findFreeIndex();
        handles[handle] = toREF(object);
        return handle;
    }

    private synchronized int findFreeIndex() {
        int handle = stack.pop();
        if (handle != -1) {
            return handle;
        }
        if (nextHandle < handles.length) {
            return nextHandle++;
        }
        // nextHandle overflows so try to collect
        handle = collect();
        if (handle != -1) {
            return handle;
        }
        // resize is needed
        handles = Arrays.copyOf(handles, handles.length << 1);
        return nextHandle++;
    }

    /**
     * Returns the object associated with a handle. This operation is performance-critical,
     * shouldn't block.
     *
     * @param handle handle, must be > 0 and fit in an integer
     * @return the object associated with the handle or null
     */
    @SuppressWarnings("unchecked")
    public final T getObject(long handle) {
        if (invalidHandle(handle)) {
            return null;
        }
        return deREF(handles[(int) handle]);
    }

    /**
     * Explicitly frees the resources associated with a handle.
     *
     * @throws UnsupportedOperationException if the storage is in implicitFree mode
     */
    public final void freeHandle(long handle) {
        if (implicitFree) {
            throw new UnsupportedOperationException("ExplicitFree method should not be called in implicitFree mode!");
        }
        if (invalidHandle(handle)) {
            return;
        }
        synchronized (this) {
            Object object = handles[(int) handle];
            if (object != null) {
                handles[(int) handle] = null;
                stack.push((int) handle);
            }
        }
    }

    /**
     * Collects all unused handles if in implicitFree mode.
     *
     * @return the next free handle or -1 if no free handle is available.
     */
    private synchronized int collect() {
        if (implicitFree) {
            for (int i = 1; i < handles.length; i++) {
                REF ref = handles[i];
                if (ref == null || deREF(ref) == null) {
                    handles[i] = null;
                    stack.push(i);
                }
            }
            return stack.pop();
        }
        return -1;
    }

    private boolean invalidHandle(long handle) {
        return (handle <= 0 || handle >= handles.length);
    }

    /**
     * Abstract method for obtaining a storgae Refrence from the object to store.
     */
    abstract REF toREF(T object);

    /**
     * Abstract method for obtaining the object from the storage reference to store.
     */
    abstract T deREF(REF ref);

    public static final class HandlesStack {
        private static final int MINIMUM_CAPACITY = 8;

        private int[] stack;
        private volatile int head = 0;

        public HandlesStack() {
            this(MINIMUM_CAPACITY);
        }

        private HandlesStack(int capacity) {
            if (capacity < MINIMUM_CAPACITY) {
                stack = new int[MINIMUM_CAPACITY];
            } else {
                stack = new int[capacity];
            }
        }

        public synchronized void push(int handle) {
            if (head >= stack.length) {
                stack = Arrays.copyOf(stack, stack.length << 1);
            }
            stack[head++] = handle;
        }

        public synchronized int pop() {
            if (head == 0) {
                return -1;
            }
            int halfLength = stack.length >> 1;
            if (halfLength >= MINIMUM_CAPACITY && head < (halfLength >> 1)) {
                // conservative downsizing
                stack = Arrays.copyOf(stack, halfLength);
            }
            return stack[--head];
        }
    }
}
