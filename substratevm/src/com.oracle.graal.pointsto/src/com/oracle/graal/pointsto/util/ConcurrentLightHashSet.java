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

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Implements a hash set that is concurrent, backed by a concurrent hash map, and memory efficient.
 * The memory efficiency comes from the fact that the map is initialized only when the set contains
 * more than one element. When it contains a single element it is simply stored in a field. When the
 * set is empty the field is null. In situations where is likely that the set will contain no or
 * only one element there is no memory overhead incurred by allocating the map.
 *
 * The value is not referenced by objects of this class but all operations use a
 * {@link AtomicReferenceFieldUpdater} to a storage location of type {@link Object}. This location
 * is then populated with three possible values:
 * <ol>
 * <li>No elements: the location is set to {@code null}
 * <li>One element: the single element is stored directly in this location.
 * <li>Multiple elements: the location points to a {@link ConcurrentHashMap} with the elements as
 * the keys. The values of the map are unused.
 * </ol>
 */
public final class ConcurrentLightHashSet {

    private ConcurrentLightHashSet() {

    }

    public static int size(Object elements) {
        if (elements == null) {
            /* No elements. */
            return 0;
        } else if (elements instanceof ConcurrentHashMap) {
            /* Multiple elements. */
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<?, Object> elementsMap = (ConcurrentHashMap<?, Object>) elements;
            return elementsMap.size();
        } else {
            // assert u instanceof T;
            /* One element. */
            return 1;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, U> boolean addElement(U holder, AtomicReferenceFieldUpdater<U, Object> updater, T newElement) {
        assert newElement != null;
        while (true) {
            Object oldElements = updater.get(holder);
            if (oldElements == null) {
                /*
                 * We add the first element. Try to install the element directly in the field. No
                 * ConcurrentHashMap is necessary yet.
                 */
                if (updater.compareAndSet(holder, oldElements, newElement)) {
                    return true;
                }

            } else if (oldElements instanceof ConcurrentHashMap) {
                /*
                 * We already have multiple elements, the ConcurrentHashMap takes care of all
                 * concurrency issues so we cannot fail.
                 */
                @SuppressWarnings("unchecked")
                ConcurrentHashMap<T, Object> elementsMap = (ConcurrentHashMap<T, Object>) oldElements;
                return elementsMap.putIfAbsent(newElement, Boolean.TRUE) == null;

            } else if (oldElements != newElement) {
                /*
                 * We add the second element. The first element is directly in the field, so we
                 * update the field to a ConcurrentHashMap with two entries.
                 */
                ConcurrentHashMap<Object, Object> newElements = new ConcurrentHashMap<>();
                newElements.put(oldElements, Boolean.TRUE);
                newElements.put(newElement, Boolean.TRUE);
                if (updater.compareAndSet(holder, oldElements, newElements)) {
                    return true;
                }

            } else {
                /*
                 * Corner case: adding the first element again, so nothing to do.
                 */
                assert oldElements == newElement;
                return false;
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, U> Collection<T> getElements(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object u = updater.get(holder);
        if (u == null) {
            /* No elements. */
            return Collections.emptySet();
        } else if (u instanceof ConcurrentHashMap) {
            /* Multiple elements. */
            ConcurrentHashMap<T, Object> elementsMap = (ConcurrentHashMap<T, Object>) u;
            return elementsMap.keySet();
        } else {
            /* Single element. */
            return (Set<T>) Collections.singleton(u);
        }
    }

    public static <T, U> boolean removeElement(U holder, AtomicReferenceFieldUpdater<U, Object> updater, T element) {
        while (true) {
            Object e = updater.get(holder);
            if (e instanceof ConcurrentHashMap) {
                /*
                 * We already have multiple elements, the ConcurrentHashMap takes care of all
                 * concurrency issues so we cannot fail.
                 */
                @SuppressWarnings("unchecked")
                ConcurrentHashMap<T, Object> elementsMap = (ConcurrentHashMap<T, Object>) e;
                return elementsMap.remove(element) != null;
            } else if (element.equals(e)) {
                /*
                 * We have a match for the single element. Try to update the field directly to null
                 * to remove that element.
                 */
                if (updater.compareAndSet(holder, e, null)) {
                    return true;
                }

            } else {
                /*
                 * We have no match on the single element, or no element at all. Nothing to remove.
                 */
                return false;
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    public static <U> void clear(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        updater.set(holder, null);
    }
}
