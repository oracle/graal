/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.options;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class OptionValuesImpl implements OptionValues {
    private static final Object NULL = new Object();
    private final OptionDescriptors descriptors;
    private final EconomicMap<OptionKey<?>, Object> values;

    public OptionValuesImpl(
                    final OptionDescriptors descriptors,
                    final UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        this.descriptors = descriptors;
        this.values = EconomicMap.create();
        final UnmodifiableMapCursor<OptionKey<?>, Object> cursor = values.getEntries();
        while (cursor.advance()) {
            this.values.put(cursor.getKey(), maskNull(cursor.getValue()));
        }
    }

    public OptionValuesImpl(
                    final OptionValuesImpl owner,
                    final UnmodifiableEconomicMap<OptionKey<?>, Object> overrides) {
        this.descriptors = owner.descriptors;
        this.values = EconomicMap.create();
        UnmodifiableMapCursor<OptionKey<?>, Object> cursor = owner.values.getEntries();
        while (cursor.advance()) {
            this.values.put(cursor.getKey(), maskNull(cursor.getValue()));
        }
        cursor = overrides.getEntries();
        while (cursor.advance()) {
            this.values.put(cursor.getKey(), maskNull(cursor.getValue()));
        }
    }

    private <T> boolean contains(OptionKey<T> optionKey) {
        for (OptionDescriptor descriptor : descriptors) {
            if (descriptor.getKey() == optionKey) {
                return true;
            }
        }
        return false;
    }

    @Override
    public OptionDescriptors getDescriptors() {
        return descriptors;
    }

    @SuppressWarnings("deprecation")
    @Override
    @TruffleBoundary
    public <T> void set(OptionKey<T> optionKey, T value) {
        throw new UnsupportedOperationException("OptionValues#set() is no longer supported");
    }

    @Override
    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public <T> T get(OptionKey<T> optionKey) {
        assert contains(optionKey);
        Object value = values.get(optionKey);
        if (value == null) {
            value = optionKey.getDefaultValue();
        }
        return (T) unmaskNull(value);
    }

    @Override
    @TruffleBoundary
    public boolean hasBeenSet(OptionKey<?> optionKey) {
        assert contains(optionKey);
        return values.containsKey(optionKey);
    }

    @Override
    @TruffleBoundary
    public boolean hasSetOptions() {
        return !values.isEmpty();
    }

    private static Object maskNull(final Object value) {
        return value == null ? NULL : value;
    }

    private static Object unmaskNull(final Object value) {
        return value == NULL ? null : value;
    }
}
