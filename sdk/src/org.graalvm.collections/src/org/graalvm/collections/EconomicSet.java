/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

/**
 * Memory efficient set data structure.
 *
 * @since 19.0
 */
public interface EconomicSet<E> extends UnmodifiableEconomicSet<E> {

    /**
     * Adds {@code element} to this set if it is not already present.
     *
     * @return {@code true} if this set did not already contain {@code element}.
     * @since 19.0
     */
    boolean add(E element);

    /**
     * Removes {@code element} from this set if it is present. This set will not contain
     * {@code element} once the call returns.
     *
     * @since 19.0
     */
    void remove(E element);

    /**
     * Removes all of the elements from this set. The set will be empty after this call returns.
     *
     * @since 19.0
     */
    void clear();

    /**
     * Adds all of the elements in {@code other} to this set if they're not already present.
     *
     * @since 19.0
     */
    default void addAll(EconomicSet<E> other) {
        addAll(other.iterator());
    }

    /**
     * Adds all of the elements in {@code values} to this set if they're not already present.
     *
     * @since 19.0
     */
    default void addAll(Iterable<E> values) {
        addAll(values.iterator());
    }

    /**
     * Adds all of the elements enumerated by {@code iterator} to this set if they're not already
     * present.
     *
     * @since 19.0
     */
    default void addAll(Iterator<E> iterator) {
        while (iterator.hasNext()) {
            add(iterator.next());
        }
    }

    /**
     * Removes from this set all of its elements that are contained in {@code other}.
     *
     * @since 19.0
     */
    default void removeAll(EconomicSet<E> other) {
        removeAll(other.iterator());
    }

    /**
     * Removes from this set all of its elements that are contained in {@code values}.
     *
     * @since 19.0
     */
    default void removeAll(Iterable<E> values) {
        removeAll(values.iterator());
    }

    /**
     * Removes from this set all of its elements that are enumerated by {@code iterator}.
     *
     * @since 19.0
     */
    default void removeAll(Iterator<E> iterator) {
        while (iterator.hasNext()) {
            remove(iterator.next());
        }
    }

    /**
     * Removes from this set all of its elements that are not contained in {@code other}.
     *
     * @since 19.0
     */
    default void retainAll(EconomicSet<E> other) {
        Iterator<E> iterator = iterator();
        while (iterator.hasNext()) {
            E key = iterator.next();
            if (!other.contains(key)) {
                iterator.remove();
            }
        }
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link Equivalence#DEFAULT} comparison strategy.
     *
     * @since 19.0
     */
    static <E> EconomicSet<E> create() {
        return EconomicSet.create(Equivalence.DEFAULT);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements.
     *
     * @since 19.0
     */
    static <E> EconomicSet<E> create(Equivalence strategy) {
        return EconomicMapImpl.create(strategy, true);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link Equivalence#DEFAULT} comparison strategy and inserts all elements of the
     * specified collection.
     *
     * @since 19.0
     */
    static <E> EconomicSet<E> create(int initialCapacity) {
        return EconomicSet.create(Equivalence.DEFAULT, initialCapacity);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link Equivalence#DEFAULT} comparison strategy and inserts all elements of the
     * specified collection.
     *
     * @since 19.0
     */
    static <E> EconomicSet<E> create(UnmodifiableEconomicSet<E> c) {
        return EconomicSet.create(Equivalence.DEFAULT, c);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and
     * initializes with the given capacity.
     *
     * @since 19.0
     */
    static <E> EconomicSet<E> create(Equivalence strategy, int initialCapacity) {
        return EconomicMapImpl.create(strategy, initialCapacity, true);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and inserts
     * all elements of the specified collection.
     *
     * @since 19.0
     */
    static <E> EconomicSet<E> create(Equivalence strategy, UnmodifiableEconomicSet<E> c) {
        return EconomicMapImpl.create(strategy, c, true);
    }
}
