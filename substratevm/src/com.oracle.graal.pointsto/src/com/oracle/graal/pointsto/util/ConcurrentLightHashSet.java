/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Implements a hash set that is concurrent, backed by a concurrent hash map, and memory efficient.
 * The memory efficiency comes from the fact that the map is initialized only when the set contains
 * more than two elements. When it contains a single element it is simply stored in a field. When it
 * contains two elements they are stored in a compact pair object. When the set is empty the field
 * is null. In situations where is likely that the set will contain no, one, or two elements there
 * is no memory overhead incurred by allocating the map.
 *
 * The value is not referenced by objects of this class but all operations use a
 * {@link AtomicReferenceFieldUpdater} to a storage location of type {@link Object}. This location
 * is then populated with four possible values:
 * <ol>
 * <li>No elements: the location is set to {@code null}
 * <li>One element: the single element is stored directly in this location.
 * <li>Two elements: the two elements are stored in a compact pair.
 * <li>Multiple elements: the location points to a {@link ConcurrentHashMap} with the elements as
 * the keys. The values of the map are unused.
 * </ol>
 *
 * Most state dispatches check the explicitly tagged compact states first: empty, pair, and finally
 * the backing map. The remaining default case is the single-element state, because the element
 * stored directly in the field can be an arbitrary object. The common case for this data structure
 * is a small set, and the map state is the only representation that requires another data-structure
 * access.
 */
public final class ConcurrentLightHashSet {

    private ConcurrentLightHashSet() {

    }

    /**
     * Returns the number of elements currently stored in the holder.
     */
    public static <U> int size(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object elements = updater.get(holder);
        switch (elements) {
            case null -> {
                /* No elements. */
                return 0;
            }
            case ElementsPair ignored -> {
                /* Two elements. */
                return 2;
            }
            case ConcurrentHashMap<?, ?> elementsMap -> {
                /* Multiple elements. */
                return elementsMap.size();
            }
            default -> {
                /* One element. */
                return 1;
            }
        }
    }

    /**
     * Adds {@code newElement} to the holder if it is not already present.
     *
     * @return {@code true} when the holder changed, or {@code false} when an equal element was
     *         already present
     */
    @SuppressWarnings("unchecked")
    public static <T, U> boolean addElement(U holder, AtomicReferenceFieldUpdater<U, Object> updater, T newElement) {
        assert newElement != null;
        while (true) {
            Object oldElements = updater.get(holder);
            switch (oldElements) {
                case null -> {
                    /*
                     * We add the first element. Try to install the element directly in the field.
                     * No ConcurrentHashMap is necessary yet.
                     */
                    if (updater.compareAndSet(holder, oldElements, newElement)) {
                        return true;
                    }
                }
                case ElementsPair oldPair -> {
                    if (oldPair.contains(newElement)) {
                        return false;
                    }
                    ConcurrentHashMap<Object, Object> newElements = new ConcurrentHashMap<>();
                    newElements.put(oldPair.first(), Boolean.TRUE);
                    newElements.put(oldPair.second(), Boolean.TRUE);
                    newElements.put(newElement, Boolean.TRUE);
                    if (updater.compareAndSet(holder, oldElements, newElements)) {
                        return true;
                    }
                }
                case ConcurrentHashMap<?, ?> oldMap -> {
                    /*
                     * We already have multiple elements, the ConcurrentHashMap takes care of all
                     * concurrency issues so we cannot fail.
                     */
                    ConcurrentHashMap<T, Object> elementsMap = (ConcurrentHashMap<T, Object>) oldMap;
                    return elementsMap.putIfAbsent(newElement, Boolean.TRUE) == null;
                }
                default -> {
                    if (!oldElements.equals(newElement)) {
                        /*
                         * We add the second element. The first element is directly in the field, so
                         * we update the field to a compact pair.
                         */
                        ElementsPair newElements = new ElementsPair(oldElements, newElement);
                        if (updater.compareAndSet(holder, oldElements, newElements)) {
                            return true;
                        }

                    } else {
                        /*
                         * Corner case: adding an equal element again, so nothing to do.
                         */
                        assert oldElements.equals(newElement) : newElement;
                        return false;
                    }
                }
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    /**
     * Returns a collection view of the elements currently stored in the holder.
     */
    @SuppressWarnings("unchecked")
    public static <T, U> Collection<T> getElements(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object u = updater.get(holder);
        switch (u) {
            case null -> {
                /* No elements. */
                return Collections.emptySet();
            }
            case ElementsPair pair -> {
                /* Two elements. */
                return (Set<T>) pair;
            }
            case ConcurrentHashMap<?, ?> elementsMap -> {
                /* Multiple elements. */
                return ((ConcurrentHashMap<T, Object>) elementsMap).keySet();
            }
            default -> {
                /* Single element. */
                return (Set<T>) Collections.singleton(u);
            }
        }
    }

    /**
     * Applies {@code action} to each element currently stored in the holder.
     */
    @SuppressWarnings("unchecked")
    public static <T, U> void forEach(U holder, AtomicReferenceFieldUpdater<U, Object> updater, Consumer<? super T> action) {
        Object u = updater.get(holder);
        switch (u) {
            case null -> {
                /* No elements. */
                return;
            }
            case ElementsPair pair -> {
                /* Two elements. */
                action.accept((T) pair.first());
                action.accept((T) pair.second());
            }
            case ConcurrentHashMap<?, ?> elementsMap -> {
                /* Multiple elements. */
                ((ConcurrentHashMap<T, Object>) elementsMap).keySet().forEach(action);
            }
            default -> {
                /* Single element. */
                action.accept((T) u);
            }
        }
    }

    /**
     * Removes {@code element} from the holder if it is present.
     *
     * @return {@code true} when the holder changed, or {@code false} when no equal element was
     *         present
     */
    public static <T, U> boolean removeElement(U holder, AtomicReferenceFieldUpdater<U, Object> updater, T element) {
        while (true) {
            Object e = updater.get(holder);
            switch (e) {
                case null -> {
                    /* No elements. */
                    return false;
                }
                case ElementsPair pair -> {
                    Object newElements;
                    if (element.equals(pair.first())) {
                        newElements = pair.second();
                    } else if (element.equals(pair.second())) {
                        newElements = pair.first();
                    } else {
                        return false;
                    }
                    if (updater.compareAndSet(holder, e, newElements)) {
                        return true;
                    }
                }
                case ConcurrentHashMap<?, ?> map -> {
                    /*
                     * We already have multiple elements, the ConcurrentHashMap takes care of all
                     * concurrency issues so we cannot fail.
                     */
                    @SuppressWarnings("unchecked")
                    ConcurrentHashMap<T, Object> elementsMap = (ConcurrentHashMap<T, Object>) map;
                    return elementsMap.remove(element) != null;
                }
                default -> {
                    if (element.equals(e)) {
                        /*
                         * We have a match for the single element. Try to update the field directly
                         * to null to remove that element.
                         */
                        if (updater.compareAndSet(holder, e, null)) {
                            return true;
                        }

                    } else {
                        /* We have no match on the single element. Nothing to remove. */
                        return false;
                    }
                }
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    /**
     * Removes every element matching {@code filter}.
     *
     * @return {@code true} when at least one element was removed
     */
    @SuppressWarnings("unchecked")
    public static <T, U> boolean removeElementIf(U holder, AtomicReferenceFieldUpdater<U, Object> updater, Predicate<? super T> filter) {
        while (true) {
            Object e = updater.get(holder);
            switch (e) {
                case null -> {
                    /* No elements. */
                    return false;
                }
                case ElementsPair pair -> {
                    boolean removeFirst = filter.test((T) pair.first());
                    boolean removeSecond = filter.test((T) pair.second());
                    Object newElements;
                    if (removeFirst && removeSecond) {
                        newElements = null;
                    } else if (removeFirst) {
                        newElements = pair.second();
                    } else if (removeSecond) {
                        newElements = pair.first();
                    } else {
                        return false;
                    }
                    if (updater.compareAndSet(holder, e, newElements)) {
                        return true;
                    }
                }
                case ConcurrentHashMap<?, ?> elementsMap -> {
                    /*
                     * We already have multiple elements, the ConcurrentHashMap takes care of all
                     * concurrency issues so we cannot fail.
                     */
                    return ((ConcurrentHashMap<T, Object>) elementsMap).keySet().removeIf(filter);
                }
                default -> {
                    if (filter.test((T) e)) {
                        /*
                         * We have a match for the single element. Try to update the field directly
                         * to null to remove that element.
                         */
                        if (updater.compareAndSet(holder, e, null)) {
                            return true;
                        }

                    } else {
                        /* We have no match on the single element. Nothing to remove. */
                        return false;
                    }
                }
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    /**
     * Removes all elements from the holder.
     */
    public static <U> void clear(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        updater.set(holder, null);
    }

    /**
     * Compact immutable representation used when the holder contains exactly two elements.
     *
     * This object also implements the immutable {@link Set} view returned by
     * {@link #getElements(Object, AtomicReferenceFieldUpdater)} for the two-element state. Reusing
     * the pair object as the view avoids allocating a temporary collection when callers only need to
     * observe the current contents.
     */
    private static final class ElementsPair extends AbstractSet<Object> {

        private final Object first;
        private final Object second;

        ElementsPair(Object first, Object second) {
            this.first = first;
            this.second = second;
        }

        Object first() {
            return first;
        }

        Object second() {
            return second;
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < 2;
                }

                @Override
                public Object next() {
                    return switch (index++) {
                        case 0 -> first;
                        case 1 -> second;
                        default -> throw new NoSuchElementException();
                    };
                }
            };
        }

        @Override
        public int size() {
            return 2;
        }

        /**
         * Returns {@code true} when either stored element is equal to {@code element}.
         */
        @Override
        public boolean contains(Object element) {
            return first.equals(element) || second.equals(element);
        }
    }
}
