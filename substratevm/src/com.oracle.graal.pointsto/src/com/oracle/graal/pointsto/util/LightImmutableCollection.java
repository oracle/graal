/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Implements a memory efficient immutable collection. The memory efficiency comes from the fact
 * that there is no wrapping overhead when the collection contains zero or one element. When the
 * collection contains a single entry then the value is stored as is. When the collection is empty
 * then the field is null.
 *
 * The value is not referenced by objects of this class but all operations use a
 * {@link AtomicReferenceFieldUpdater} to a storage location of type {@link Object}. This location
 * is then populated with three possible values:
 * <ol>
 * <li>No entries: the location is set to {@code null}
 * <li>One entry: the entry is stored directly in this location as is
 * <li>Multiple entries: the location points to a Object array storing the entries
 * </ol>
 *
 * This collection cannot store types which are assignable from Object[].
 */
public final class LightImmutableCollection {

    private LightImmutableCollection() {

    }

    public static <U> int size(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object value = updater.get(holder);
        if (value == null) {
            /* No elements. */
            return 0;
        } else if (value instanceof Object[]) {
            /* Multiple elements. */
            return ((Object[]) value).length;
        } else {
            /* One element. */
            return 1;
        }
    }

    public static <T> boolean isEmpty(T holder, AtomicReferenceFieldUpdater<T, Object> updater) {
        Object value = updater.get(holder);
        return value == null;
    }

    @SuppressWarnings("unchecked")
    public static <T, U> void forEach(U holder, AtomicReferenceFieldUpdater<U, Object> updater, Consumer<? super T> action) {
        Object value = updater.get(holder);
        if (value == null) {
            /* No elements. */
            return;
        }
        if (value instanceof Object[]) {
            /* Multiple elements. */
            for (T element : (T[]) value) {
                action.accept(element);
            }
        } else {
            /* Single element. */
            action.accept((T) value);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, U> boolean allMatch(U holder, AtomicReferenceFieldUpdater<U, Object> updater, Predicate<? super T> predicate) {
        Object value = updater.get(holder);
        if (value == null) {
            /* No elements. */
            return true;
        }
        if (value instanceof Object[]) {
            /* Multiple elements. */
            for (T element : (T[]) value) {
                if (!predicate.test(element)) {
                    return false;
                }
            }
            return true;
        } else {
            /* Single element. */
            return predicate.test((T) value);
        }
    }

    /**
     * If the list contains only one element, then returns the element. Otherwise, returns null.
     */
    @SuppressWarnings("unchecked")
    public static <T, U> T toSingleElement(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object value = updater.get(holder);
        if (value == null || value instanceof Object[]) {
            return null;
        }

        return (T) value;
    }

    @SuppressWarnings("unchecked")
    public static <T, U> Collection<T> toCollection(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object value = updater.get(holder);
        if (value == null) {
            /* No elements. */
            return Collections.emptySet();
        } else if (value instanceof Object[]) {
            /* Multiple elements. */
            return List.of((T[]) value);
        } else {
            /* Single element. */
            return Collections.singleton((T) value);
        }
    }

    /**
     * Initializes collection. Note this is operation can race if called multiple times
     * concurrently.
     */
    public static <T, U> void initializeNonEmpty(U holder, AtomicReferenceFieldUpdater<U, Object> updater, T element) {
        assert element != null && !(element instanceof Object[]) : element;

        /* Single element. */
        updater.set(holder, element);
    }

    /**
     * Initializes collection. Note this is operation can race if called multiple times
     * concurrently.
     */
    public static <T, U> void initializeNonEmpty(U holder, AtomicReferenceFieldUpdater<U, Object> updater, Collection<T> value) {
        assert value != null && value.size() != 0 : value;

        if (value.size() == 1) {
            /* Extract single element. */
            T element = value.iterator().next();
            assert element != null && !(element instanceof Object[]) : element;

            updater.set(holder, element);
        } else {
            updater.set(holder, value.toArray());
        }
    }
}
