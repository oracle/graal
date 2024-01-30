/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.collections;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.PriorityQueue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.BasedOnJDKClass;
import com.oracle.svm.core.util.VMError;

/**
 * An uninterruptible priority queue, based on the corresponding JDK class. Uses the object identity
 * when it tests objects for equality.
 */
@BasedOnJDKClass(PriorityQueue.class)
public class UninterruptiblePriorityQueue {
    private final UninterruptibleComparable[] queue;

    private int size;

    public UninterruptiblePriorityQueue(UninterruptibleComparable[] queue) {
        this.queue = queue;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isFull() {
        return size == queue.length;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean add(UninterruptibleComparable e) {
        return offer(e);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean offer(UninterruptibleComparable e) {
        assert e != null;
        int i = size;
        if (i >= queue.length) {
            throw VMError.shouldNotReachHere("offer() must not be called without checking capacity.");
        }
        siftUp(i, e, queue);
        size = i + 1;
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UninterruptibleComparable poll() {
        final UninterruptibleComparable[] es;
        final UninterruptibleComparable result;

        if ((result = ((es = queue)[0])) != null) {
            final int n;
            final UninterruptibleComparable x = es[(n = --size)];
            es[n] = null;
            if (n > 0) {
                siftDown(0, x, es, n);
            }
        }
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UninterruptibleComparable peek() {
        return queue[0];
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean remove(UninterruptibleComparable o) {
        int i = indexOf(o);
        if (i == -1) {
            return false;
        } else {
            removeAt(i);
            return true;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private UninterruptibleComparable removeAt(int i) {
        // assert i >= 0 && i < size;
        final UninterruptibleComparable[] es = queue;
        int s = --size;
        if (s == i) { // removed last element
            es[i] = null;
        } else {
            UninterruptibleComparable moved = es[s];
            es[s] = null;
            siftDown(i, moved, es, size);
            if (es[i] == moved) {
                siftUp(i, moved, es);
                if (es[i] != moved) {
                    return moved;
                }
            }
        }
        return null;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private int indexOf(UninterruptibleComparable o) {
        if (o != null) {
            for (int i = 0, n = size; i < n; i++) {
                if (o == queue[i]) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("all")
    private static void siftUp(int k, UninterruptibleComparable x, UninterruptibleComparable[] es) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            UninterruptibleComparable e = es[parent];
            if (x.compareTo(e) >= 0) {
                break;
            }
            es[k] = e;
            k = parent;
        }
        es[k] = x;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("all")
    private static void siftDown(int k, UninterruptibleComparable x, UninterruptibleComparable[] es, int n) {
        // assert n > 0;
        int half = n >>> 1;           // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            UninterruptibleComparable c = es[child];
            int right = child + 1;
            if (right < n && c.compareTo(es[right]) > 0) {
                c = es[child = right];
            }
            if (x.compareTo(c) <= 0) {
                break;
            }
            es[k] = c;
            k = child;
        }
        es[k] = x;
    }
}
