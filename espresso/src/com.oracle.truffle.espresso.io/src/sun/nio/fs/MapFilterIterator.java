/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package sun.nio.fs;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Small utility to apply map + filter operations, in that order, to a base iterator.
 *
 * <p>
 * This file must be compatible with 21+.
 */
final class MapFilterIterator<From, To> implements Iterator<To> {

    private final Iterator<From> seed;
    private final Function<From, To> map;
    private final Predicate<To> filter;

    private MapFilterIterator(Iterator<From> seed, Function<From, To> map, Predicate<To> filter) {
        this.seed = Objects.requireNonNull(seed);
        this.map = Objects.requireNonNull(map);
        this.filter = Objects.requireNonNull(filter);
    }

    private boolean hasNext;
    private To nextElement;

    @Override
    public boolean hasNext() {
        if (hasNext) {
            return true;
        }

        while (seed.hasNext()) {
            From from = seed.next();
            To to = map.apply(from);
            if (filter.test(to)) {
                hasNext = true;
                nextElement = to;
                return true;
            }
        }

        return false;
    }

    @Override
    public To next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        To result = nextElement;
        hasNext = false;
        nextElement = null;
        return result;
    }

    static <T> Iterator<T> filter(Iterator<T> seed, Predicate<T> filter) {
        return new MapFilterIterator<>(seed, Function.identity(), filter);
    }

    static <F, T> Iterator<T> map(Iterator<F> seed, Function<F, T> map) {
        return new MapFilterIterator<>(seed, map, unused -> true);
    }

    static <F, T> Iterator<T> mapThenFilter(Iterator<F> seed, Function<F, T> map, Predicate<T> filter) {
        return new MapFilterIterator<>(seed, map, filter);
    }
}
