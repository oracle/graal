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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.util.VMError;

/**
 * Each thread has several of these on which to wait. Instances are usually expensive objects
 * because they encapsulate native resources. Therefore, lazy initialization is supported using
 * {@link #initializeOnce} and {@link #detach} with three states and {@link AtomicReference atomic
 * transitions}:
 * <ol>
 * <li>uninitialized: the {@link AtomicReference} is null.
 * <li>used: A concrete platform-specific subclass of {@link ParkEvent} that allows wait and unpark
 * operations.
 * <li>detached: The thread has exited (or was manually detached from the VM). The
 * {@link DetachedParkEvent} singleton.
 * </ol>
 * The allowed transitions are uninitialized->used (wait or park is called on an active thread);
 * used->detached; and uninitialized->detached.
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
     * Resets a pending {@link #unpark()} at the time of the call. This must synchronize in a way
     * that prevents it from being reordered with regard to setting the thread's interrupted status.
     */
    protected abstract void reset();

    /* cond_wait. */
    protected abstract void condWait();

    /** cond_timedwait, similar to {@link #condWait} but with a timeout in nanoseconds. */
    protected abstract void condTimedWait(long delayNanos);

    /** Notify anyone waiting on this event. */
    protected abstract void unpark();

    /** Use up the cons-cell for this ParkEvent. */
    ParkEventConsCell consumeConsCell() {
        assert consCell != null : "Consuming null cons cell.";
        ParkEventConsCell result = consCell;
        consCell = null;
        return result;
    }

    /*
     * Since ParkEvents are immortal, they are acquired and released, rather than being allocated
     * and garbage collected.
     */

    static ParkEvent initializeOnce(AtomicReference<ParkEvent> ref, boolean isSleepEvent) {
        ParkEvent result = ref.get();
        if (result == null) {
            ParkEvent newEvent = ParkEvent.acquire();
            /*
             * Assign a *new* cons-cell for this ParkEvent, whether it was acquired from the
             * free-list or allocated.
             */
            newEvent.consCell = new ParkEventConsCell(newEvent);
            newEvent.isSleepEvent = isSleepEvent;
            newEvent.reset();

            if (ref.compareAndSet(null, newEvent)) {
                /* We won the race. */
                result = newEvent;
            } else {
                /*
                 * We lost the race. We have one extra ParkEvent now, which we put back on the
                 * queue, so that it is reused later on.
                 */
                ParkEvent.release(newEvent);
                result = ref.get();
                VMError.guarantee(result != null, "ParkEvent must never be reset to null once it has been initialized.");
            }
        }
        return result;
    }

    /** Acquire a ParkEvent, either from the free-list or by construction. */
    private static ParkEvent acquire() {
        ParkEvent result = ParkEventList.getSingleton().pop();
        if (result == null) {
            result = ImageSingletons.lookup(ParkEventFactory.class).create();
        }
        return result;
    }

    static void detach(AtomicReference<ParkEvent> ref) {
        /*
         * We must not reset the AtomicReference back to null, because a racing thread could
         * interpret that as "uninitialized" and immediately re-initialize the park event, or even
         * return the null value in some race conditions.
         */
        ParkEvent event = ref.getAndSet(DetachedParkEvent.SINGLETON);
        if (event != null && event != DetachedParkEvent.SINGLETON) {
            ParkEvent.release(event);
        }
    }

    private static void release(ParkEvent event) {
        ParkEventList.getSingleton().push(event);
    }
}

/**
 * The {@link ParkEvent} used for threads that are detached, i.e., no longer executing Java code.
 * Such threads cannot wait (because waiting can only be initiated by the thread itself), but they
 * can be unparked (because {@link #unpark} can be called for any thread in any state).
 */
final class DetachedParkEvent extends ParkEvent {

    static final ParkEvent SINGLETON = new DetachedParkEvent();

    private DetachedParkEvent() {
    }

    @Override
    protected void reset() {
        throw VMError.shouldNotReachHere("Cannot reset a DetachedParkEvent");
    }

    @Override
    protected void condWait() {
        throw VMError.shouldNotReachHere("Cannot wait on a DetachedParkEvent");
    }

    @Override
    protected void condTimedWait(long delayNanos) {
        throw VMError.shouldNotReachHere("Cannot wait on a DetachedParkEvent");
    }

    @Override
    protected void unpark() {
        /* It is an allowed no-op to unpark an already detached thread. */
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

    protected void setNext(ParkEventConsCell next) {
        this.next = next;
    }
}
