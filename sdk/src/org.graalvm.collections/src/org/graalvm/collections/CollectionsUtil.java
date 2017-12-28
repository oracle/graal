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
package org.graalvm.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This class contains utility methods for commonly used functional patterns for collections.
 */
public class CollectionsUtil {

    /**
     * Concatenates two iterables into a single iterable. The iterator exposed by the returned
     * iterable does not support {@link Iterator#remove()} even if the input iterables do.
     *
     * @throws NullPointerException if {@code a} or {@code b} is {@code null}
     */
    public static <T> Iterable<T> concat(Iterable<T> a, Iterable<T> b) {
        List<Iterable<T>> l = Arrays.asList(a, b);
        return concat(l);
    }

    /**
     * Concatenates multiple iterables into a single iterable. The iterator exposed by the returned
     * iterable does not support {@link Iterator#remove()} even if the input iterables do.
     *
     * @throws NullPointerException if {@code iterables} or any of its elements are {@code null}
     */
    public static <T> Iterable<T> concat(List<Iterable<T>> iterables) {
        for (Iterable<T> iterable : iterables) {
            Objects.requireNonNull(iterable);
        }
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                if (iterables.size() == 0) {
                    return Collections.emptyIterator();
                }
                return new Iterator<T>() {
                    Iterator<Iterable<T>> cursor = iterables.iterator();
                    Iterator<T> currentIterator = cursor.next().iterator();

                    private void advance() {
                        while (!currentIterator.hasNext() && cursor.hasNext()) {
                            currentIterator = cursor.next().iterator();
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        advance();
                        return currentIterator.hasNext();
                    }

                    @Override
                    public T next() {
                        advance();
                        return currentIterator.next();
                    }
                };
            }

        };
    }

    /**
     * Iterative evaluate the given predicate on all values in the given array. When evaluating the
     * predicate on a value returns {@code false}, the predicate won't be evaluated for the
     * subsequent values.
     *
     * @return {@code true} if all values match the predicate, otherwise {@code false}.
     */
    public static <T> boolean allMatch(T[] inputs, Predicate<T> predicate) {
        return allMatch(Arrays.asList(inputs), predicate);
    }

    /**
     * Iterative evaluate the given predicate on all values from the given {@code Iterable}. When
     * evaluating the predicate on a value returns {@code false}, the predicate won't be evaluated
     * for the subsequent values.
     *
     * @return {@code true} if all values match the predicate, otherwise {@code false}.
     */
    public static <T> boolean allMatch(Iterable<T> inputs, Predicate<T> predicate) {
        for (T t : inputs) {
            if (!predicate.test(t)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Iterative evaluate the given predicate on all values in the given array. When evaluating the
     * predicate on a value returns {@code true}, the predicate won't be evaluated for the
     * subsequent values.
     *
     * @return {@code true} if any value matches the predicate, otherwise {@code false}.
     */
    public static <T> boolean anyMatch(T[] inputs, Predicate<T> predicate) {
        return anyMatch(Arrays.asList(inputs), predicate);
    }

    /**
     * Iterative evaluate the given predicate on all values from the given {@code Iterable}. When
     * evaluating the predicate on a value returns {@code true}, the predicate won't be evaluated
     * for the subsequent values.
     *
     * @return {@code true} if any value matches the predicate, otherwise {@code false}.
     */
    public static <T> boolean anyMatch(Iterable<T> inputs, Predicate<T> predicate) {
        for (T t : inputs) {
            if (predicate.test(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a new list consisting of the elements of the given list that match the given
     * predicate.
     *
     * @return the new list.
     */
    public static <T> List<T> filterToList(List<T> inputs, Predicate<? super T> predicate) {
        return filterToList(inputs, predicate, ArrayList::new);
    }

    /**
     * Appends elements of the given list that match the given predicate to the list generated by
     * the given supplier.
     *
     * @return the list generated by the given supplier.
     */
    public static <T> List<T> filterToList(List<T> inputs, Predicate<? super T> predicate, Supplier<List<T>> listGenerator) {
        List<T> resultList = listGenerator.get();
        for (T t : inputs) {
            if (predicate.test(t)) {
                resultList.add(t);
            }
        }
        return resultList;
    }

    /**
     * Filters the inputs, maps them given the mapping function and adds them in the array provided
     * by the generator.
     *
     * @return the array provided by the generator.
     */
    public static <T, R> R[] filterAndMapToArray(T[] inputs, Predicate<? super T> predicate, Function<? super T, ? extends R> mapper, IntFunction<R[]> arrayGenerator) {
        List<R> resultList = new ArrayList<>();
        for (T t : inputs) {
            if (predicate.test(t)) {
                resultList.add(mapper.apply(t));
            }
        }
        return resultList.toArray(arrayGenerator.apply(resultList.size()));
    }

    /**
     * Maps the inputs given the mapping function and adds them in the array provided by the
     * generator.
     *
     * @return the array provided by the generator.
     */
    public static <T, R> R[] mapToArray(T[] inputs, Function<? super T, ? extends R> mapper, IntFunction<R[]> arrayGenerator) {
        return mapToArray(Arrays.asList(inputs), mapper, arrayGenerator);
    }

    /**
     * Maps the inputs from a collection given the mapping function and adds them in the array
     * provided by the generator.
     *
     * @return the array provided by the generator.
     */
    public static <T, R> R[] mapToArray(Collection<T> inputs, Function<? super T, ? extends R> mapper, IntFunction<R[]> arrayGenerator) {
        R[] result = arrayGenerator.apply(inputs.size());
        int idx = 0;
        for (T t : inputs) {
            result[idx++] = mapper.apply(t);
        }
        return result;
    }

    /**
     * Maps the inputs from an array given the mapping function, joins them together separated by a
     * delimiter.
     *
     * @return a new String that is composed from the inputs.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, "", "");
    }

    /**
     * Maps the inputs from an array given the mapping function, joins them together separated by a
     * delimiter and starting with a supplied prefix.
     *
     * @return a new String that is composed from the inputs.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, prefix, "");
    }

    /**
     * Maps the inputs from an array given the mapping function, joins them together separated by a
     * delimiter and starting with a supplied prefix and ending with a supplied suffix.
     *
     * @return a new String that is composed from the inputs.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix, String suffix) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, prefix, suffix);
    }

    /**
     * Maps the inputs from an {@code Iterable} given the mapping function, joins them together
     * separated by a delimiter.
     *
     * @return a new String that is composed from the inputs.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter) {
        return mapAndJoin(inputs, mapper, delimiter, "", "");
    }

    /**
     * Maps the inputs from an {@code Iterable} given the mapping function, joins them together
     * separated by a delimiter and starting with a supplied prefix.
     *
     * @return a new String that is composed from the inputs.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix) {
        return mapAndJoin(inputs, mapper, delimiter, prefix, "");
    }

    /**
     * Maps the inputs from an {@code Iterable} given the mapping function, joins them together
     * separated by a delimiter and starting with a supplied prefix and ending with a supplied
     * suffix.
     *
     * @return a new String that is composed from the inputs.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix, String suffix) {
        StringBuilder strb = new StringBuilder();
        String sep = "";
        for (T t : inputs) {
            strb.append(sep).append(prefix).append(mapper.apply(t)).append(suffix);
            sep = delimiter;
        }
        return strb.toString();
    }

}
