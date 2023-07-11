/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.collections;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Concurrent lock-free pool data structure.
 * <p>
 * Supports two operations -- adding an element, and removing an element. The
 * {@link LockFreePool#add(Object)} operation adds an element into the pool. The
 * {@link LockFreePool#get()} operation returns one of the elements previously added to the pool.
 * There is no guarantee about the order in which the elements are returned by
 * {@link LockFreePool#get()}. The guarantee is that an element will only be returned by
 * {@link LockFreePool#get()} as many times as it was previously added to the pool by calling
 * {@link LockFreePool#add(Object)}. If there are no more elements to return, it will return
 * {@code null}. Both operations are lock-free and linearizable -- this data structure is intended
 * for use by multiple threads.
 *
 * The internal implementation is a simple Treiber stack.
 *
 * @param <T> Type of the elements in this pool.
 *
 * @since 23.0
 */
public class LockFreePool<T> {
    /**
     * The top-of-the-Treiber-stack pointer.
     */
    private final AtomicReference<Node<T>> head;

    /**
     * @since 23.0
     */
    public LockFreePool() {
        this.head = new AtomicReference<>();
    }

    /**
     * Returns a previously added element.
     * <p>
     * This method returns a previously added element only once to some caller. If the element was
     * added multiple times, calling this method will return that element as many times before
     * returning {@code null}.
     *
     * This method does not do any object allocations.
     *
     * @return A previously added element, or {@code null} if there are no previously added elements
     *         that have not been already returned.
     *
     * @since 23.0
     */
    public T get() {
        while (true) {
            Node<T> oldHead = head.get();
            if (oldHead == null) {
                return null;
            }
            Node<T> newHead = oldHead.tail;
            if (head.compareAndSet(oldHead, newHead)) {
                return oldHead.element;
            }
        }
    }

    /**
     * Adds an element to this pool.
     * <p>
     * An element can be added multiple times to the pool, but in this case may be returned as many
     * times by {@link LockFreePool#get()}.
     *
     * This method internally allocates objects on the heap.
     *
     * @param element An element to add to the pool.
     *
     * @since 23.0
     */
    public void add(T element) {
        while (true) {
            Node<T> oldHead = head.get();
            Node<T> newHead = new Node<>(element, oldHead);
            if (head.compareAndSet(oldHead, newHead)) {
                return;
            }
        }
    }

    /**
     * Internal wrapper node used to wrap the element and the {@code tail} pointer.
     */
    private static final class Node<E> {
        /**
         * Element stored in this node.
         */
        final E element;
        /**
         * Pointer to the tail of the linked list, or {@code null} if the end of the list.
         */
        final Node<E> tail;

        private Node(E element, Node<E> tail) {
            this.element = element;
            this.tail = tail;
        }
    }
}
