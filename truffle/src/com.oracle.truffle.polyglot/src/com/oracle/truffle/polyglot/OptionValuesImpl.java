/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

final class OptionValuesImpl implements OptionValues {

    private static final float FUZZY_MATCH_THRESHOLD = 0.7F;

    // TODO is this too long? Make sure to update Engine#setUseSystemProperties javadoc.
    // Just using graalvm as prefix is not good enough as it is ambiguous with host compilation
    // For example the property graalvm.compiler is an option for which compiler. The java compiler?
    // or the truffle compiler?
    static final String SYSTEM_PROPERTY_PREFIX = "polyglot.";

    private final PolyglotEngineImpl engine;
    private final OptionDescriptors descriptors;
    private final Map<OptionKey<?>, Object> values;

    OptionValuesImpl(PolyglotEngineImpl engine, OptionDescriptors descriptors) {
        this.engine = engine;
        this.descriptors = descriptors;
        this.values = new HashMap<>();
    }

    @Override
    public int hashCode() {
        int result = 31 + descriptors.hashCode();
        result = 31 * result + engine.hashCode();
        result = 31 * result + values.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OptionValues)) {
            return super.equals(obj);
        } else {
            if (this == obj) {
                return true;
            }
            OptionValues other = ((OptionValues) obj);
            if (getDescriptors().equals(other.getDescriptors())) {
                if (!hasSetOptions() && !other.hasSetOptions()) {
                    return true;
                }
                if (other instanceof OptionValuesImpl) {
                    // faster comparison that only depends on the set values
                    for (OptionKey<?> key : values.keySet()) {
                        if (hasBeenSet(key) || other.hasBeenSet(key)) {
                            if (!get(key).equals(other.get(key))) {
                                return false;
                            }
                        }
                    }
                    for (OptionKey<?> key : ((OptionValuesImpl) other).values.keySet()) {
                        if (hasBeenSet(key) || other.hasBeenSet(key)) {
                            if (!get(key).equals(other.get(key))) {
                                return false;
                            }
                        }
                    }
                    return true;
                } else {
                    // slow comparison for arbitrary option values
                    for (OptionDescriptor descriptor : getDescriptors()) {
                        OptionKey<?> key = descriptor.getKey();
                        if (hasBeenSet(key) || other.hasBeenSet(key)) {
                            if (!get(key).equals(other.get(key))) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    public void putAll(Map<String, String> providedValues) {
        for (String key : providedValues.keySet()) {
            put(key, providedValues.get(key));
        }
    }

    public void put(String key, String value) {
        OptionDescriptor descriptor = findDescriptor(key);
        values.put(descriptor.getKey(), descriptor.getKey().getType().convert(value));
    }

    private OptionValuesImpl(OptionValuesImpl copy) {
        this.engine = copy.engine;
        this.values = new HashMap<>(copy.values);
        this.descriptors = copy.descriptors;
    }

    public boolean hasBeenSet(OptionKey<?> optionKey) {
        return values.containsKey(optionKey);
    }

    OptionValuesImpl copy() {
        return new OptionValuesImpl(this);
    }

    public OptionDescriptors getDescriptors() {
        return descriptors;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(OptionKey<T> optionKey) {
        Object value = values.get(optionKey);
        if (value == null) {
            return optionKey.getDefaultValue();
        }
        return (T) value;
    }

    @Override
    public <T> void set(OptionKey<T> optionKey, T value) {
        optionKey.getType().validate(value);
        values.put(optionKey, value);
    }

    @Override
    public boolean hasSetOptions() {
        return !values.isEmpty();
    }

    private OptionDescriptor findDescriptor(String key) {
        OptionDescriptor descriptor = descriptors.get(key);
        if (descriptor == null) {
            throw failNotFound(key);
        }
        return descriptor;
    }

    private RuntimeException failNotFound(String key) {
        OptionDescriptors allOptions;
        Exception errorOptions = null;
        try {
            allOptions = engine == null ? this.descriptors : this.engine.getAllOptions();
        } catch (Exception e) {
            errorOptions = e;
            allOptions = this.descriptors;
        }
        RuntimeException error = failNotFound(allOptions, key);
        if (errorOptions != null) {
            error.addSuppressed(errorOptions);
        }

        throw error;
    }

    static RuntimeException failNotFound(OptionDescriptors allOptions, String key) {
        Iterable<OptionDescriptor> matches = fuzzyMatch(allOptions, key);
        Formatter msg = new Formatter();
        msg.format("Could not find option with name %s.", key);

        Iterator<OptionDescriptor> iterator = matches.iterator();
        if (iterator.hasNext()) {
            msg.format("%nDid you mean one of the following?");
            for (OptionDescriptor match : matches) {
                msg.format("%n    %s=<%s>", match.getName(), match.getKey().getType().getName());
            }
        }
        throw new IllegalArgumentException(msg.toString());
    }

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    static List<OptionDescriptor> fuzzyMatch(OptionDescriptors descriptors, String optionKey) {
        List<OptionDescriptor> matches = new ArrayList<>();
        for (org.graalvm.options.OptionDescriptor option : descriptors) {
            float score = stringSimiliarity(option.getName(), optionKey);
            if (score >= FUZZY_MATCH_THRESHOLD) {
                matches.add(option);
            }
        }
        return matches;
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     *
     * Ported from str_similar() in globals.cpp.
     */
    private static float stringSimiliarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }
}
