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

import java.lang.ref.WeakReference;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.collections.UninterruptibleComparable;
import com.oracle.svm.core.collections.UninterruptibleLinkedList;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.jfr.JfrTicks;

/**
 * Holds information about a sampled object. This data may only be accessed while holding the
 * {@link JfrOldObjectProfiler} lock.
 */
public final class JfrOldObject implements UninterruptibleComparable, UninterruptibleLinkedList.Element {
    private final WeakReference<?> reference;

    private JfrOldObject next;
    private UnsignedWord span;
    private UnsignedWord objectSize;
    private long allocationTicks;
    private long threadId;
    private long stackTraceId;
    private UnsignedWord heapUsedAfterLastGC;
    private int arrayLength;

    JfrOldObject() {
        this.reference = new WeakReference<>(null);
    }

    @SuppressWarnings("hiding")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void initialize(Object obj, UnsignedWord span, UnsignedWord allocatedSize, long threadId, long stackTraceId, UnsignedWord heapUsedAfterLastGC, int arrayLength) {
        ReferenceInternals.setReferent(reference, obj);
        this.span = span;
        this.objectSize = allocatedSize;
        this.allocationTicks = JfrTicks.elapsedTicks();
        this.threadId = threadId;
        this.stackTraceId = stackTraceId;
        this.heapUsedAfterLastGC = heapUsedAfterLastGC;
        this.arrayLength = arrayLength;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void reset() {
        ReferenceInternals.setReferent(reference, null);
        this.span = Word.zero();
        this.objectSize = Word.zero();
        this.allocationTicks = 0L;
        this.threadId = 0L;
        this.stackTraceId = 0L;
        this.heapUsedAfterLastGC = Word.zero();
        this.arrayLength = 0;
        this.next = null;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UninterruptibleLinkedList.Element getNext() {
        return next;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setNext(UninterruptibleLinkedList.Element next) {
        this.next = (JfrOldObject) next;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getSpan() {
        return span;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void increaseSpan(UnsignedWord value) {
        span = span.add(value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getObjectSize() {
        return objectSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getAllocationTicks() {
        return allocationTicks;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getThreadId() {
        return threadId;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getStackTraceId() {
        return stackTraceId;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getHeapUsedAfterLastGC() {
        return heapUsedAfterLastGC;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getArrayLength() {
        return arrayLength;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int compareTo(UninterruptibleComparable other) {
        UnsignedWord otherSpan = ((JfrOldObject) other).span;
        if (span.aboveThan(otherSpan)) {
            return 1;
        } else if (span.belowThan(otherSpan)) {
            return -1;
        } else {
            return 0;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Object getReferent() {
        return ReferenceInternals.getReferent(reference);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isAlive() {
        return ReferenceInternals.isReferentAlive(reference);
    }
}
