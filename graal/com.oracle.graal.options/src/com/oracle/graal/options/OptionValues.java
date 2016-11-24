/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A context for obtaining values for {@link OptionKey}s.
 */
public class OptionValues {

    /**
     * An {@link AutoCloseable} whose {@link #close()} does not throw a checked exception.
     */
    public interface OverrideScope extends AutoCloseable {
        @Override
        void close();
    }

    public static final OptionValues GLOBAL = new OptionValues();

    private final Map<OptionKey<?>, Object> values = new HashMap<>();

    /**
     * Used to assert the invariant of stability for {@link StableOptionKey}s.
     */
    private final Map<StableOptionKey<?>, Object> stabilized = new HashMap<>();

    /**
     * Sets a value for an option in this object by parsing a given option name and value.
     *
     * @param name the option name
     * @param value the unchecked value for the option
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    public void parse(String name, Object value) {
        ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
        OptionsParser.parseOption(name, value, this, loader);
    }

    OptionValues set(OptionKey<?> key, Object value) {
        Object oldValue = decodeNull(values.put(key, encodeNull(value)));
        key.valueUpdated(this, oldValue, value);
        return this;
    }

    /**
     * Registers {@code key} as stable in this map. It should not be updated after this call.
     *
     * Note: Should only be used in an assertion.
     */
    synchronized boolean stabilize(StableOptionKey<?> key, Object value) {
        stabilized.put(key, encodeNull(value));
        return true;
    }

    /**
     * Determines if the value of {@code key} is {@linkplain #stabilize(StableOptionKey, Object)
     * stable} in this map.
     *
     * Note: Should only be used in an assertion.
     */
    synchronized boolean isStabilized(StableOptionKey<?> key) {
        return key.getDescriptor() == null;
    }

    boolean containsKey(OptionKey<?> key) {
        return key.getDescriptor() != null && values.containsKey(key);
    }

    public OptionValues(OptionValues initialValues) {
        values.putAll(initialValues.values);
    }

    public OptionValues(OptionValues initialValues, Map<OptionKey<?>, Object> overrides) {
        values.putAll(initialValues.values);
        values.putAll(overrides);
    }

    public OptionValues() {
    }

    /**
     * Gets a new {@link OptionValues} object with the same values as this object except with the
     * guarantee that the value of {@code key} in the returned object is {@code value}. That is, a
     * new {@link OptionValues} object is returned irrespective of the value for {@code key} in this
     * object.
     *
     * @return a newly created {@link OptionValues} instance where the value of {@code key} is
     *         {@code value}
     */
    public OptionValues with(OptionKey<?> key, Object value) {
        OptionValues options = new OptionValues(this);
        key.setValue(options, value);
        return options;
    }

    @SuppressWarnings("unchecked")
    <T> T get(OptionKey<T> key) {
        if (key.getDescriptor() != null) {
            Object value = values.get(key);
            if (value == null) {
                return key.getDefaultValue();
            }
            return (T) decodeNull(value);
        } else {
            // If a key has no descriptor, it cannot be in the map
            // since OptionKey.hashCode() will ensure the key has a
            // descriptor as a result of using OptionKey.getName().
            return key.getDefaultValue();
        }

    }

    private static final Object NULL = new Object();

    private static Object encodeNull(Object value) {
        return value == null ? NULL : value;
    }

    private static Object decodeNull(Object value) {
        return value == NULL ? null : value;
    }

    private static final Comparator<OptionKey<?>> OPTION_KEY_COMPARATOR = new Comparator<OptionKey<?>>() {
        @Override
        public int compare(OptionKey<?> o1, OptionKey<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    @Override
    public String toString() {
        SortedMap<OptionKey<?>, Object> sorted = new TreeMap<>(OPTION_KEY_COMPARATOR);
        sorted.putAll(values);
        return sorted.toString();
    }
}
