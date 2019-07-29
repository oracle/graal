/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.util.concurrent.atomic.AtomicReference;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.thread.VMOperation;

/**
 * A list of elements that can be constructed without allocation, for use by the garbage collector.
 *
 * Elements can be prepended to a list in constant time, and can be disabled in constant time.
 * Disabled elements can be removed from the list before the list is iterated, in time proportional
 * to the length of the list.
 *
 * Prepending elements on a list is atomic. Walking a list starts from a sample, and should be run
 * single-threaded if the list might be scrubbed while it is being walked.
 */
public class AllocationFreeList<T extends AllocationFreeList.Element<T>> {

    /** The head of the list. */
    private final AtomicReference<Element<T>> head;

    /** Constructor. */
    protected AllocationFreeList() {
        super();
        head = new AtomicReference<>(null);
    }

    public static <U extends AllocationFreeList.Element<U>> AllocationFreeList<U> factory() {
        return new AllocationFreeList<>();
    }

    /**
     * Get the first enabled element of the list as a T. This method returns a sample: new elements
     * may be prepended before the result can be used.
     */
    public T getFirst() {
        Element<T> candidate = sampleHead();
        while (candidate != null && !candidate.enabled) {
            candidate = candidate.next;
        }
        // Since I only put instances of T on the list, the head is always a T.
        return Element.asT(candidate);
    }

    /** For detecting errors: Get the first element as an Object so there is no type checking. */
    public Object getFirstObject() {
        return head.get();
    }

    /** Prepend an element to the list. Many calls can race to prepend an element. */
    public void prepend(T element) {
        // Widen to Element<T> to access the private fields of Element<T>.
        final Element<T> asElement = element;
        // Sanity check the element: it should never have been on any list.
        if (asElement.getHasBeenOnList()) {
            throw PreviouslyRegisteredElementException.getPreallocatedInstance();
        }
        // Prepend the element to the head of the list.
        asElement.hasBeenOnList = true;
        asElement.enabled = true;
        Element<T> headSample;
        do {
            headSample = sampleHead();
            asElement.next = headSample;
        } while (!head.compareAndSet(headSample, asElement));
    }

    /**
     * Remove any disabled elements from the list. This method is *not* thread-safe with respect to
     * other changes in the list.
     */
    public void scrub() {
        guaranteeSingleThreaded("AllocationFreeList.scrub");
        final Element<T> headSample = sampleHead();
        if (headSample != null) {
            Element<T> newHead = null;
            Element<T> newTail = null;
            Element<T> rest = null;
            // TODO: This keeps the list in order. Is that necessary? Is it desirable?
            for (Element<T> current = headSample; current != null; current = rest) {
                // Take the current element off the list, remembering where I am.
                rest = current.next;
                current.next = null;
                if (current.enabled) {
                    // If there isn't a new list, start one.
                    if (newHead == null) {
                        newHead = current;
                    }
                    // If there is a newTail, link that to current.
                    if (newTail != null) {
                        newTail.next = current;
                    }
                    // Remember the previous enabled element.
                    newTail = current;
                }
            }
            head.set(newHead);
        }
    }

    /**
     * Return a sample of the head of the list. The list may be prepended to after the sample is
     * taken. Unless you are single-threaded the list could be scrubbed out from under you.
     */
    private Element<T> sampleHead() {
        return head.get();
    }

    /** Guarantee that I am single-threaded. */
    static void guaranteeSingleThreaded(String message) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMOperation.guaranteeInProgress(message);
        }
    }

    /** For testing, to iterate without scrubbing. */
    public T testingBackDoorGetFirst() {
        return Element.asT(sampleHead());
    }

    /**
     * An element of a AllocationFreeList has slots for the next element in the list, so
     * singly-linked lists can be assembled without allocation.
     *
     * The lifecycle of an Element is:
     * <ul>
     * <li>Creation: next: null enabled: false enlisted: false.</li>
     * <li>Prepended: next: oldHead enabled: true enlisted: true.</li>
     * <li>Removed: next: next enabled: false enlisted: true.</li>
     * </ul>
     * An Element can be put on a list exactly once, because "enlisted" only goes from false to true
     * but not back.
     * <p>
     * TODO: If I wanted to be able to move an Element from one list to another, the "enlisted"
     * field could point to the old list the element was on, and I could remove it from the old
     * list, scrub the old list, reset "enlisted", and then prepend the element to the new list.
     * Since the feature is not needed, it is not implemented.
     */
    public static class Element<T extends Element<T>> {

        /** The next element of the list. */
        private Element<T> next;

        /** Is this element enabled? */
        private boolean enabled;

        /**
         * Has this element been put on a list. This starts out false and becomes true, but then
         * never changes.
         */
        private boolean hasBeenOnList;

        /**
         * Constructor for subclasses. This might not get called so it can not do anything
         * interesting.
         */
        protected Element() {
            super();
        }

        /** Get the next enabled element of the list. */
        public T getNextElement() {
            Element<T> candidate = next;
            while (candidate != null && !candidate.enabled) {
                candidate = candidate.next;
            }
            // Since I only put instances of T on the list, the next element is always a T.
            return asT(candidate);
        }

        /** For detecting errors: Get the next element as an Object so there is no type checking. */
        public Object getNextObject() {
            return next;
        }

        /** Logically remove this element from the list. */
        public void removeElement() {
            enabled = false;
        }

        /** Narrow the given Element<T> to a T. The element might be null. */
        @SuppressWarnings("unchecked")
        private static <T extends Element<T>> T asT(Element<T> element) {
            return (T) element;
        }

        /** Has this element ever been on a list? */
        public boolean getHasBeenOnList() {
            return hasBeenOnList;
        }

        /*
         * For testing. For use when iterating an unscrubbed list.
         */

        public boolean testingBackDoorIsEnabled() {
            return enabled;
        }

        public T testingBackDoorGetNextElement() {
            return asT(next);
        }
    }

    public static class PreviouslyRegisteredElementException extends RuntimeException {

        /** A pre-allocated exception instance, for use in allocation-free code. */
        private static PreviouslyRegisteredElementException preallocatedPreviouslyRegisteredElementException = //
                        new PreviouslyRegisteredElementException("Element was previously registered.");

        /** Throw the previously allocated exception from allocation-free code. */
        public static PreviouslyRegisteredElementException getPreallocatedInstance() {
            return preallocatedPreviouslyRegisteredElementException;
        }

        /**
         * Code that can allocate can make up an exception instance with a more detailed message.
         */
        public PreviouslyRegisteredElementException(String message) {
            super(message);
        }

        /** Every exception needs one of these. */
        private static final long serialVersionUID = 2066230621024365993L;
    }
}
