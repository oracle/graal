/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;

/**
 * Each thread has several of these on which to wait. Instances are usually expensive objects
 * because they encapsulate native resources. Therefore, lazy initialization is used, see
 * {@link ThreadData}.
 */
public abstract class ParkEvent {

    public interface ParkEventFactory {
        ParkEvent create();
    }

    /** Currently required by legacy code. */
    protected boolean isSleepEvent;

    /**
     * A cons-cell for putting this ParkEvent on the free list. This must be (a) allocated
     * beforehand because I need it when I can not allocate, (b) must not be reused, to avoid an ABA
     * problem.
     */
    private ParkEventConsCell consCell;

    /** Constructor for subclasses. */
    protected ParkEvent() {
    }

    /**
     * Resets a pending {@link #unpark()} at the time of the call.
     */
    protected abstract void reset();

    /* cond_wait. */
    protected abstract void condWait();

    /** cond_timedwait, similar to {@link #condWait} but with a timeout in nanoseconds. */
    protected abstract void condTimedWait(long delayNanos);

    /** Notify anyone waiting on this event. */
    protected abstract void unpark();

    /** Use up the cons-cell for this ParkEvent. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    ParkEventConsCell consumeConsCell() {
        assert consCell != null : "Consuming null cons cell.";
        ParkEventConsCell result = consCell;
        consCell = null;
        return result;
    }

    /**
     * Acquire a ParkEvent, either from the free-list or by construction. ParkEvents are immortal,
     * so they are acquired and released, rather than being allocated and garbage collected.
     */
    static ParkEvent acquire(boolean isSleepEvent) {
        ParkEvent result = ParkEventList.getSingleton().pop();
        if (result == null) {
            result = ImageSingletons.lookup(ParkEventFactory.class).create();
        }

        /* Assign a *new* cons-cell for this ParkEvent. */
        result.consCell = new ParkEventConsCell(result);
        result.isSleepEvent = isSleepEvent;
        result.reset();
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void release(ParkEvent event) {
        ParkEventList.getSingleton().push(event);
    }
}

/**
 * A free-list of ParkEvents.
 *
 * Since ParkEvents have to be immortal, they are not garbage collected. Instead, they are put back
 * on a free-list. To avoid ABA problems with multi-threaded pops from the list, I make up a new
 * cons-cell for each push to the list.
 */
final class ParkEventList {

    private static final ParkEventList SINGLETON = new ParkEventList();

    @Fold
    public static ParkEventList getSingleton() {
        return SINGLETON;
    }

    /** The free-list of ParkEvents. */
    private final AtomicReference<ParkEventConsCell> freeList;

    /** Private constructor: Only the singleton instance. */
    private ParkEventList() {
        freeList = new AtomicReference<>(null);
    }

    /** Push an element on to the free-list. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void push(ParkEvent element) {
        ParkEventConsCell sampleHead;
        /* Use up the cons-cell for each attempted push to avoid the ABA problem on pops. */
        ParkEventConsCell nextHead = element.consumeConsCell();
        do {
            sampleHead = freeList.get();
            nextHead.setNext(sampleHead);
        } while (!freeList.compareAndSet(sampleHead, nextHead));
    }

    /** Return the head of the free-list, or null. */
    public ParkEvent pop() {
        ParkEventConsCell sampleHead;
        ParkEventConsCell sampleNext;
        do {
            sampleHead = freeList.get();
            if (sampleHead == null) {
                return null;
            }
            sampleNext = sampleHead.getNext();
        } while (!freeList.compareAndSet(sampleHead, sampleNext));
        return sampleHead.getElement();
    }
}

/** A cons-cell for the free-list. */
final class ParkEventConsCell {

    /** Immutable state. */
    private final ParkEvent element;
    /** Mutable state, but only until the cons-cell is on the list. */
    private ParkEventConsCell next;

    /** Constructor. */
    ParkEventConsCell(ParkEvent element) {
        this.element = element;
        this.next = null;
    }

    protected ParkEvent getElement() {
        return element;
    }

    protected ParkEventConsCell getNext() {
        return next;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void setNext(ParkEventConsCell next) {
        this.next = next;
    }
}
