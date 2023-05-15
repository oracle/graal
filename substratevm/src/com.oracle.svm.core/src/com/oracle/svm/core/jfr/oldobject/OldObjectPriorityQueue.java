/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

import java.lang.ref.WeakReference;

/**
 * A priority queue view over the old object samples stored inside {@link OldObjectArray}. The
 * priority is based on a sample's span. When the queue gets full, it allows the
 * {@link OldObjectSampler} to remove samples with lower span values and keep those with higher
 * values. An old object sample's span is its allocation size initially, but it can increase as old
 * object samples are garbage collected (see {@link OldObjectSampler} for details). All of its
 * methods are marked as uninterruptible because they're accessed from the old object profiler
 * sampling and emitting methods, which are protected by a lock.
 */
final class OldObjectPriorityQueue {
    private final OldObjectArray samples;
    private int count;
    private long total;

    OldObjectPriorityQueue(OldObjectArray samples) {
        this.samples = samples;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isFull() {
        return count == samples.getCapacity();
    }

    /**
     * Inserts the specified old object into this queue.
     * <p>
     * This method does not check if the queue has enough capacity. It's up to the caller decide how
     * to deal with a full queue.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void push(OldObject sample) {
        push(sample.reference, sample.span, sample.allocationTime, sample.threadId, sample.stackTraceId, sample.heapUsedAtLastGC, sample.arrayLength);
    }

    /**
     * Inserts the old object sample data into this queue.
     * <p>
     * This method does not check if the queue has enough capacity. It's up to the caller decide how
     * to deal with a full queue.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public OldObject push(WeakReference<?> ref, long allocatedSize, long allocatedTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
        final OldObject sample = samples.getSample(count);
        sample.set(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        count++;
        moveUp(count - 1);
        total += sample.span;
        return sample;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    OldObject peek() {
        return count == 0 ? OldObject.EMPTY : samples.getSample(0);
    }

    /**
     * Removes and return the head of the queue. The head of the queue is the sample with the
     * smallest span.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    OldObject poll() {
        if (count == 0) {
            return OldObject.EMPTY;
        }

        final OldObject head = peek();
        samples.swap(0, count - 1);
        count--;
        moveDown(0);
        total -= head.span;
        return head;
    }

    /**
     * Removes a sample from the queue. It moves the sample all the way to the top to become the
     * head, then it polls it to remove it.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void remove(OldObject sample) {
        final long span = sample.span;
        sample.span = 0L;
        moveUp(samples.indexOf(sample));
        sample.span = span;
        poll();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void moveUp(final int i) {
        int current = i;
        int parent = parent(current);
        while (current > 0 && getSpanAt(current) < getSpanAt(parent)) {
            samples.swap(current, parent);
            current = parent;
            parent = parent(current);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private long getSpanAt(int index) {
        return samples.getSample(index).span;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int parent(int i) {
        return (i - 1) / 2;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void moveDown(final int i) {
        int current = i;
        do {
            int j = -1;
            int r = right(current);
            if (r < count && getSpanAt(r) < getSpanAt(current)) {
                int l = left(current);
                if (getSpanAt(l) < getSpanAt(r)) {
                    j = l;
                } else {
                    j = r;
                }
            } else {
                int l = left(current);
                if (l < count && getSpanAt(l) < getSpanAt(current)) {
                    j = l;
                }
            }

            if (j >= 0) {
                samples.swap(current, j);
            }
            current = j;
        } while (current >= 0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int left(int i) {
        return 2 * i + 1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int right(int i) {
        return 2 * i + 2;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long total() {
        return total;
    }
}
