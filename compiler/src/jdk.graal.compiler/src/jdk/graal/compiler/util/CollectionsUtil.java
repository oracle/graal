/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.collections.Pair;

/**
 * This class contains utility methods for commonly used functional patterns for collections.
 */
public final class CollectionsUtil {

    private CollectionsUtil() {
    }

    /**
     * Creates a new unmodifiable hash map containing the given entries. The map preservers the
     * insertion order of entries.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param entries the entries to place in the map
     * @return a new hash map with the given entries
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mapOfEntries(Map.Entry<K, V>... entries) {
        Objects.requireNonNull(entries);
        Map<K, V> newMap = new EconomicHashMap<>(entries.length);
        for (Map.Entry<K, V> entry : entries) {
            newMap.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(newMap);
    }

    /**
     * Creates an unmodifiable map with a single entry.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first key
     * @param v1 the first value
     * @return an unmodifiable map with the given entry
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        Map<K, V> map = new EconomicHashMap<>(1);
        map.put(k1, v1);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates an unmodifiable map with the given entries. The map preserves the order of entries.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @return an unmodifiable map with the given entries
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new EconomicHashMap<>(2);
        map.put(k1, v1);
        map.put(k2, v2);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates an unmodifiable map with the given entries. The map preserves the order of entries.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @return an unmodifiable map with the given entries
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new EconomicHashMap<>(3);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates an unmodifiable map with the given entries. The map preserves the order of entries.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @return an unmodifiable map with the given entries
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = new EconomicHashMap<>(4);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates an unmodifiable map with the given entries. The map preserves the order of entries.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @param k5 the fifth key
     * @param v5 the fifth value
     * @return an unmodifiable map with the given entries
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = new EconomicHashMap<>(5);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates an unmodifiable map with the given entries. The map preserves the order of entries.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @param k5 the fifth key
     * @param v5 the fifth value
     * @param k6 the sixth key
     * @param v6 the sixth value
     * @return an unmodifiable map with the given entries
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        Map<K, V> map = new EconomicHashMap<>(6);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates an unmodifiable set with the given elements. The set preserves the order of elements.
     *
     * @param <E> the element type
     * @param elements the elements of the set
     * @return a set with the given elements
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> Set<E> setOf(E... elements) {
        if (elements.length == 0) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new EconomicHashSet<>(Arrays.asList(elements)));
    }

    /**
     * Creates an unmodifiable set with the given elements. The set preserves the order of elements.
     *
     * @param <E> the element type
     * @param collection the elements to be placed in the hash set
     * @return a set with the given elements
     */
    public static <E> Set<E> setCopyOf(Collection<? extends E> collection) {
        return Collections.unmodifiableSet(new EconomicHashSet<>(collection));
    }

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
        return new Iterable<>() {
            @Override
            public Iterator<T> iterator() {
                if (iterables.size() == 0) {
                    return Collections.emptyIterator();
                }
                return new Iterator<>() {
                    final Iterator<Iterable<T>> cursor = iterables.iterator();
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
     * Returns whether all elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code true} is returned and {@code predicate} is not evaluated.
     *
     * @return {@code true} if either all elements in {@code inputs} match {@code predicate} or
     *         {@code inputs} is empty, otherwise {@code false}.
     */
    public static <T> boolean allMatch(T[] inputs, Predicate<T> predicate) {
        return allMatch(Arrays.asList(inputs), predicate);
    }

    /**
     * Returns whether all elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code true} is returned and {@code predicate} is not evaluated.
     *
     * @return {@code true} if either all elements in {@code inputs} match {@code predicate} or
     *         {@code inputs} is empty, otherwise {@code false}.
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
     * Returns whether any elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code false} is returned and {@code predicate} is not
     * evaluated.
     *
     * @return {@code true} if any elements in {@code inputs} match {@code predicate}, otherwise
     *         {@code false}.
     */
    public static <T> boolean anyMatch(T[] inputs, Predicate<T> predicate) {
        return anyMatch(Arrays.asList(inputs), predicate);
    }

    /**
     * Returns whether any elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code false} is returned and {@code predicate} is not
     * evaluated.
     *
     * @return {@code true} if any elements in {@code inputs} match {@code predicate}, otherwise
     *         {@code false}.
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
     * Returns a new list consisting of the elements in {@code inputs} that match {@code predicate}.
     *
     * @return the new list.
     */
    public static <T> List<T> filterToList(List<T> inputs, Predicate<? super T> predicate) {
        return filterToList(inputs, predicate, ArrayList::new);
    }

    /**
     * Appends elements of {@code inputs} that match {@code predicate} to the list generated by
     * {@code listGenerator}.
     *
     * @return the list generated by {@code listGenerator}.
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
     * Filters {@code inputs} with {@code predicate}, applies {@code mapper} and adds them in the
     * array provided by {@code arrayGenerator}.
     *
     * @return the array provided by {@code arrayGenerator}.
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
     * Applies {@code mapper} on the elements in {@code inputs} and adds them in the array provided
     * by {@code arrayGenerator}.
     *
     * @return the array provided by {@code arrayGenerator}.
     */
    public static <T, R> R[] mapToArray(T[] inputs, Function<? super T, ? extends R> mapper, IntFunction<R[]> arrayGenerator) {
        return mapToArray(Arrays.asList(inputs), mapper, arrayGenerator);
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs} and adds them in the array provided
     * by {@code arrayGenerator}.
     *
     * @return the array provided by {@code arrayGenerator}.
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
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, "", "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, prefix, "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix} and ending with {@code suffix}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix, String suffix) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, prefix, suffix);
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter) {
        return mapAndJoin(inputs, mapper, delimiter, "", "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix) {
        return mapAndJoin(inputs, mapper, delimiter, prefix, "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix} and ending with {@code suffix}.
     *
     * @return a new String that is composed from {@code inputs}.
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

    /**
     * Combines two iterables into an iterable of pairs with as many elements as the longer
     * iterable. Elements are paired in the order of the original iterables. When one of the
     * iterators is exhausted, {@code null}s are returned in its place.
     *
     * @param lhs the first iterable
     * @param rhs the second iterable
     * @return an iterable of pairs of the input elements
     */
    public static <L, R> Iterable<Pair<L, R>> zipLongest(Iterable<L> lhs, Iterable<R> rhs) {
        Iterator<L> lhsIterator = lhs.iterator();
        Iterator<R> rhsIterator = rhs.iterator();
        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return lhsIterator.hasNext() || rhsIterator.hasNext();
            }

            @Override
            public Pair<L, R> next() {
                if (lhsIterator.hasNext() && rhsIterator.hasNext()) {
                    return Pair.create(lhsIterator.next(), rhsIterator.next());
                } else if (lhsIterator.hasNext()) {
                    return Pair.createLeft(lhsIterator.next());
                } else {
                    return Pair.createRight(rhsIterator.next());
                }
            }
        };
    }

    /**
     * Returns an iterable over all pairs of elements.
     * <p>
     * Suppose that the first iterable returns the elements {@code a1, a2, ..., an} and the second
     * iterable returns the elements {@code b1, b2, ..., bm}. Then, the method returns the pairs
     * {@code (a1, b1), (a1, b2), ..., (a1, bm), (a2, b1), (a2, b2), ... (an, bm)}.
     *
     * @param lhs the first iterable (left elements of the {@link Pair})
     * @param rhs the second iterable (right elements of the {@link Pair})
     * @return an iterable over all pairs of elements
     */
    public static <L, R> Iterable<Pair<L, R>> cartesianProduct(Iterable<L> lhs, Iterable<R> rhs) {
        return () -> new Iterator<>() {

            private L lhsLast = null;

            private final Iterator<L> lhsIterator = lhs.iterator();

            private boolean rhsReachedEnd = true;

            private Iterator<R> rhsIterator = null;

            @Override
            public boolean hasNext() {
                if (rhsReachedEnd) {
                    if (!lhsIterator.hasNext()) {
                        return false;
                    }
                    lhsLast = lhsIterator.next();
                    rhsIterator = rhs.iterator();
                    rhsReachedEnd = !rhsIterator.hasNext();
                    return !rhsReachedEnd;
                }
                return true;
            }

            @Override
            public Pair<L, R> next() {
                if (rhsReachedEnd) {
                    lhsLast = lhsIterator.next();
                    rhsIterator = rhs.iterator();
                }
                R rhsItem = rhsIterator.next();
                rhsReachedEnd = !rhsIterator.hasNext();
                return Pair.create(lhsLast, rhsItem);
            }
        };
    }
}
