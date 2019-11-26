/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

public final class PolyglotCompilerOptionsScope implements Closeable {

    private static class Lazy {
        static final ThreadLocal<PolyglotCompilerOptionsScope> currentScope = new ThreadLocal<>();
    }

    private final OptionValues optionValues;
    private final PolyglotCompilerOptionsScope parent;

    private PolyglotCompilerOptionsScope(OptionValues optionValues, PolyglotCompilerOptionsScope parent) {
        Objects.requireNonNull(optionValues, "OptionValues must be non null.");
        this.optionValues = optionValues;
        this.parent = parent;
    }

    @Override
    public void close() {
        PolyglotCompilerOptionsScope current = Lazy.currentScope.get();
        if (current != this) {
            throw new IllegalStateException("Unpaired close.");
        }
        Lazy.currentScope.set(current.parent);
    }

    public static OptionValues getOptionValues() {
        PolyglotCompilerOptionsScope current = Lazy.currentScope.get();
        if (current == null) {
            throw new IllegalStateException("Not entered in scope.");
        }
        return current.optionValues;
    }

    public static PolyglotCompilerOptionsScope open(Map<String, Object> options) {
        PolyglotCompilerOptionsScope parent = Lazy.currentScope.get();
        OptionValues values = convertToOptionValues(options);
        PolyglotCompilerOptionsScope newScope = new PolyglotCompilerOptionsScope(values, parent);
        Lazy.currentScope.set(newScope);
        return newScope;
    }

    public static PolyglotCompilerOptionsScope overrideOptions(OptionKey<?> key1, Object value1, Object... extraOverrides) {
        if ((extraOverrides.length & 1) != 0) {
            throw new IllegalArgumentException("ExtraOverrides must have even size.");
        }
        PolyglotCompilerOptionsScope parent = Lazy.currentScope.get();
        EconomicMap<OptionKey<?>, Object> parsedOptions = EconomicMap.create(Equivalence.IDENTITY);
        if (parent != null) {
            for (OptionDescriptor desc : PolyglotCompilerOptions.getDescriptors()) {
                OptionKey<?> descKey = desc.getKey();
                if (parent.optionValues.hasBeenSet(descKey)) {
                    parsedOptions.put(desc.getKey(), parent.optionValues.get(desc.getKey()));
                }
            }
        }
        parsedOptions.put(key1, value1);
        for (int i = 0; i < extraOverrides.length; i += 2) {
            parsedOptions.put((OptionKey<?>) extraOverrides[i], extraOverrides[i + 1]);
        }
        OptionValuesImpl values = new OptionValuesImpl(PolyglotCompilerOptions.getDescriptors(), parsedOptions);
        PolyglotCompilerOptionsScope newScope = new PolyglotCompilerOptionsScope(values, parent);
        Lazy.currentScope.set(newScope);
        return newScope;
    }

    private static OptionValues convertToOptionValues(Map<String, Object> options) {
        EconomicMap<OptionKey<?>, Object> parsedOptions = EconomicMap.create(Equivalence.IDENTITY);
        OptionDescriptors descriptors = PolyglotCompilerOptions.getDescriptors();
        for (Map.Entry<String, Object> e : options.entrySet()) {
            final OptionDescriptor descriptor = descriptors.get(e.getKey());
            final OptionKey<?> k = descriptor != null ? descriptor.getKey() : null;
            if (k != null) {
                Object value = e.getValue();
                if (value.getClass() == String.class) {
                    value = descriptor.getKey().getType().convert((String) e.getValue());
                }
                parsedOptions.put(k, value);
            }
        }
        return new OptionValuesImpl(descriptors, parsedOptions);
    }
}
