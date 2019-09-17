/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.options;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Represents a mapping between {@link String} keys and values. Allows to create {@link OptionKey
 * options} to group/accumulate {@code key=value} pairs, with a common prefix; whose keys are not
 * known beforehand e.g. user defined properties.
 *
 * @param <T> the class of the map values
 *
 * @since 19.2
 */
public final class OptionMap<T> {

    private static final OptionMap<?> EMPTY = new OptionMap<>(Collections.emptyMap());

    final Map<String, T> backingMap;
    final Map<String, T> readonlyMap;

    OptionMap(Map<String, T> map) {
        this.backingMap = map;
        this.readonlyMap = Collections.unmodifiableMap(map);
    }

    /**
     * Returns an empty option map (immutable).
     * 
     * @param <T> the class of the map values
     * @return an empty option map
     * @since 19.2
     */
    @SuppressWarnings("unchecked")
    public static <T> OptionMap<T> empty() {
        return (OptionMap<T>) EMPTY;
    }

    /**
     * Returns the value to which the specified key is mapped, or {@code null} if this option map
     * contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or {@code null} if this map contains
     *         no mapping for the key
     *
     * @since 19.2
     */
    public T get(String key) {
        return readonlyMap.get(key);
    }

    /**
     * Returns an unmodifiable {@link Set} view of the mappings contained in this map.
     *
     * @return a set view of the mappings contained in this map
     *
     * @since 19.2
     */
    public Set<Map.Entry<String, T>> entrySet() {
        return readonlyMap.entrySet();
    }

    /**
     * @since 19.2
     */
    @Override
    public int hashCode() {
        return readonlyMap.hashCode();
    }

    /**
     * @since 19.2
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OptionMap) {
            return readonlyMap.equals(((OptionMap<T>) obj).readonlyMap);
        }
        return false;
    }
}
