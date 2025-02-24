/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;

/**
 * This map lives across multiple layers through the {@link LayeredImageHeapMapStore}. It has a
 * different behavior at build time and run time. At build time, the map from the current layer is
 * accessed and interacted with. At run time, the maps from all layers are iterated over and handled
 * together.
 */
public class LayeredImageHeapMap<K, V> implements EconomicMap<K, V> {
    private final Equivalence strategy;
    private final String mapKey;
    /**
     * This boolean stores the order in which the maps are iterated over and determines how values
     * are overwritten across layers. If true, the first map to be checked is the base layer one and
     * if false, it is the application layer map. The first map to be checked is the one whose value
     * will have priority over the others.
     */
    private final boolean fromBaseToApp;

    public LayeredImageHeapMap(Equivalence strategy, String mapKey) {
        this(strategy, mapKey, false);
    }

    public LayeredImageHeapMap(Equivalence strategy, String mapKey, boolean fromBaseToApp) {
        this.strategy = strategy;
        this.mapKey = mapKey;
        this.fromBaseToApp = fromBaseToApp;

        var previousMap = LayeredImageHeapMapStore.currentLayer().getImageHeapMapStore().putIfAbsent(mapKey, EconomicMap.create(strategy));
        if (previousMap != null) {
            throw VMError.shouldNotReachHere("The LayeredImageHeapMap with key %s was added twice", mapKey);
        }
    }

    @Override
    public V put(K key, V value) {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().put(key, value);
        } else {
            var singletons = LayeredImageHeapMapStore.layeredSingletons();
            /*
             * The value at run time needs to overwrite any other value, so the first map to be
             * checked is used.
             */
            return getRuntimeMap(singletons[fromBaseToApp ? 0 : singletons.length - 1]).put(key, value);
        }
    }

    @Override
    public void clear() {
        if (SubstrateUtil.HOSTED) {
            getCurrentMap().clear();
        } else {
            for (var singleton : LayeredImageHeapMapStore.layeredSingletons()) {
                getRuntimeMap(singleton).clear();
            }
        }
    }

    @Override
    public V removeKey(K key) {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().removeKey(key);
        } else {
            /*
             * The returned value needs to be the first value to be found. However, all the other
             * values still need to be removed as we would still find an older value otherwise.
             */
            List<LayeredImageHeapMapStore> singletons = Arrays.stream(LayeredImageHeapMapStore.layeredSingletons()).toList();
            V previousValue = null;
            for (var singleton : fromBaseToApp ? singletons : singletons.reversed()) {
                V value = getRuntimeMap(singleton).removeKey(key);
                if (value != null && previousValue == null) {
                    previousValue = value;
                }
            }
            return previousValue;
        }
    }

    @Override
    public V get(K key) {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().get(key);
        } else {
            List<LayeredImageHeapMapStore> singletons = Arrays.stream(LayeredImageHeapMapStore.layeredSingletons()).toList();
            for (var singleton : fromBaseToApp ? singletons : singletons.reversed()) {
                EconomicMap<K, V> singletonMap = getRuntimeMap(singleton);
                if (singletonMap.containsKey(key)) {
                    return singletonMap.get(key);
                }
            }
        }
        return null;
    }

    @Override
    public boolean containsKey(K key) {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().containsKey(key);
        } else {
            for (var singleton : LayeredImageHeapMapStore.layeredSingletons()) {
                if (getRuntimeMap(singleton).containsKey(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int size() {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().size();
        } else {
            int size = 0;
            for (var singleton : LayeredImageHeapMapStore.layeredSingletons()) {
                size += getRuntimeMap(singleton).size();
            }
            return size;
        }
    }

    @Override
    public boolean isEmpty() {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().isEmpty();
        } else {
            for (var singleton : LayeredImageHeapMapStore.layeredSingletons()) {
                if (!getRuntimeMap(singleton).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Iterable<V> getValues() {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().getValues();
        } else {
            return getIterator(UnmodifiableMapCursor::getValue);
        }
    }

    @Override
    public Iterable<K> getKeys() {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().getKeys();
        } else {
            return getIterator(UnmodifiableMapCursor::getKey);
        }
    }

    private <E> Iterable<E> getIterator(Function<MapCursor<K, V>, E> getElement) {
        var cursor = getEntries();
        List<E> elements = new ArrayList<>();
        while (cursor.advance()) {
            elements.add(getElement.apply(cursor));
        }
        return elements;
    }

    @Override
    public MapCursor<K, V> getEntries() {
        if (SubstrateUtil.HOSTED) {
            return getCurrentMap().getEntries();
        } else {
            /*
             * In case of duplicate keys, the first key found need to have priority over the other
             * keys. The keys need to be stored to ensure each of them is processed once.
             */
            List<LayeredImageHeapMapStore> singletons = Arrays.stream(LayeredImageHeapMapStore.layeredSingletons()).toList();
            singletons = fromBaseToApp ? singletons : singletons.reversed();
            Iterator<MapCursor<K, V>> cursors = singletons.stream().map(singleton -> getRuntimeMap(singleton).getEntries()).iterator();
            return new MapCursor<>() {
                private MapCursor<K, V> current = cursors.next();
                private final EconomicSet<K> keys = EconomicSet.create(strategy);

                @Override
                public void remove() {
                    current.remove();
                }

                @Override
                public boolean advance() {
                    boolean advance = current.advance();
                    if (!advance) {
                        if (cursors.hasNext()) {
                            current = cursors.next();
                            if (keys.add(current.getKey())) {
                                return true;
                            }
                        }
                    }
                    return advance;
                }

                @Override
                public K getKey() {
                    return current.getKey();
                }

                @Override
                public V getValue() {
                    return current.getValue();
                }

                @Override
                public V setValue(V newValue) {
                    return current.setValue(newValue);
                }
            };
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (SubstrateUtil.HOSTED) {
            getCurrentMap().replaceAll(function);
        } else {
            for (var singleton : LayeredImageHeapMapStore.layeredSingletons()) {
                getRuntimeMap(singleton).replaceAll(function);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unchecked")
    private EconomicMap<K, V> getCurrentMap() {
        return (EconomicMap<K, V>) LayeredImageHeapMapStore.currentLayer().getImageHeapMapStore().get(mapKey);
    }

    @SuppressWarnings("unchecked")
    private EconomicMap<K, V> getRuntimeMap(LayeredImageHeapMapStore singleton) {
        return (EconomicMap<K, V>) singleton.getImageHeapMapStore().get(mapKey);
    }
}
