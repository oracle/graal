/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat Inc. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.collections.UninterruptibleLinkedList;
import com.oracle.svm.core.collections.UninterruptiblePriorityQueue;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;
import com.oracle.svm.core.thread.JavaThreads;

/**
 * A sampler that tracks old objects that are potential memory leaks.
 */
final class JfrOldObjectSampler {
    /** Priority queue with a fixed size. The first object has the smallest span. */
    private final UninterruptiblePriorityQueue queue;
    /** Contains the same items as the priority queue but in insertion order. */
    private final UninterruptibleLinkedList usedList;
    /** Holds pre-allocated instances that can be added to the priority queue. */
    private final UninterruptibleLinkedList freeList;

    private UnsignedWord totalAllocated;
    private UnsignedWord totalInQueue;

    JfrOldObjectSampler(int queueSize) {
        this.queue = new UninterruptiblePriorityQueue(new JfrOldObject[queueSize]);
        this.usedList = new UninterruptibleLinkedList();

        this.freeList = new UninterruptibleLinkedList();
        for (int i = 0; i < queueSize; i++) {
            freeList.append(new JfrOldObject());
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean sample(Object obj, UnsignedWord allocatedSize, int arrayLength) {
        totalAllocated = totalAllocated.add(allocatedSize);
        UnsignedWord span = totalAllocated.subtract(totalInQueue);
        if (queue.isFull()) {
            assert freeList.isEmpty();

            JfrOldObject peek = getObjectWithSmallestSpan();
            if (peek.getSpan().aboveThan(span)) {
                /*
                 * Sample will not fit, try to scavenge. HotSpot code scavenges when it gets
                 * notified that a sample was GC'd, but such mechanism doesn't exist in Native
                 * Image. So, instead just attempt to scavenge when the queue is full.
                 */
                int numDead = scavenge();
                if (numDead == 0) {
                    /* Sample will not fit and all objects still in use, return early. */
                    return false;
                }
            } else {
                /* New element has a higher priority, evict the sample with the smallest span. */
                evict();
            }
        }

        store(obj, span, allocatedSize, arrayLength);
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private int scavenge() {
        int numDead = 0;
        JfrOldObject cur = getOldestObject();
        while (cur != null) {
            JfrOldObject next = (JfrOldObject) cur.getNext();
            if (!cur.isAlive()) {
                remove(cur);
                numDead++;
            }
            cur = next;
        }
        return numDead;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void remove(JfrOldObject sample) {
        JfrOldObject next = (JfrOldObject) sample.getNext();
        if (next != null) {
            /*
             * To keep samples evenly distributed over time, the weight of a sample that is removed
             * should be redistributed. We redistribute it to the next (i.e., younger) sample.
             */
            queue.remove(next);
            next.increaseSpan(sample.getSpan());
            queue.add(next);
        } else {
            /*
             * No younger element, we can't redistribute the weight. The next sample should absorb
             * the extra span.
             */
            totalInQueue = totalInQueue.subtract(sample.getSpan());
            assert totalInQueue.aboveOrEqual(0);
        }
        queue.remove(sample);
        release(sample);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void evict() {
        JfrOldObject sample = (JfrOldObject) queue.poll();
        release(sample);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void release(JfrOldObject sample) {
        usedList.remove(sample);
        freeList.append(sample);
        sample.reset();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void store(Object obj, UnsignedWord span, UnsignedWord allocatedSize, int arrayLength) {
        Thread thread = JavaThreads.getCurrentThreadOrNull();
        long threadId = thread == null ? 0L : JavaThreads.getThreadId(thread);
        long stackTraceId = thread == null ? 0L : SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample);
        UnsignedWord heapUsedAfterLastGC = Heap.getHeap().getUsedMemoryAfterLastGC();

        JfrOldObject sample = (JfrOldObject) freeList.pop();
        sample.initialize(obj, span, allocatedSize, threadId, stackTraceId, heapUsedAfterLastGC, arrayLength);
        queue.add(sample);
        usedList.append(sample);
        totalInQueue = totalInQueue.add(span);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void emit(long cutoff, @SuppressWarnings("unused") boolean emitAll, @SuppressWarnings("unused") boolean skipBFS) {
        /* We don't support paths-to-gc-roots yet. */
        if (cutoff <= 0) {
            emitUnchained();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void emitUnchained() {
        long startTicks = JfrTicks.elapsedTicks();

        JfrOldObject cur = getOldestObject();
        while (cur != null) {
            Object obj = cur.getReferent();
            if (obj != null) {
                long objectId = SubstrateJVM.getOldObjectRepository().serializeOldObject(obj);
                UnsignedWord objectSize = cur.getObjectSize();
                long allocationTicks = cur.getAllocationTicks();
                long threadId = cur.getThreadId();
                long stackTraceId = cur.getStackTraceId();
                UnsignedWord heapUsedAfterLastGC = cur.getHeapUsedAfterLastGC();
                int arrayLength = cur.getArrayLength();

                OldObjectSampleEvent.emit(startTicks, objectId, objectSize, allocationTicks, threadId, stackTraceId, heapUsedAfterLastGC, arrayLength);
            }

            cur = (JfrOldObject) cur.getNext();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    JfrOldObject getOldestObject() {
        return (JfrOldObject) usedList.getHead();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private JfrOldObject getObjectWithSmallestSpan() {
        return (JfrOldObject) queue.peek();
    }
}
